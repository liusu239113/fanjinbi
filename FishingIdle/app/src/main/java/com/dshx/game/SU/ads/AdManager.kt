package com.dshx.game.SU.ads

import android.app.Activity
import android.util.Log
import com.tosin.sdk.loadAd.model.AdError
import com.tosin.sdk.loadAd.rewardvideo.RewardVideoAd
import com.tosin.sdk.loadAd.rewardvideo.RewardVideoListener
import com.tosin.sdk.loadAd.rewardvideo.config.RewardVideoConfig

/**
 * 激励视频广告管理（单例）。
 *
 * 两件事：
 *  1. [preload] 预加载 —— 把「点广告」到「真出广告」的等待从数秒压到几乎为零。
 *     这是提升完播率最直接的一招：弱网下现加载现播，玩家等不及就退出了。
 *  2. [showRewardVideo] 展示并回调是否发奖。
 *
 * 预加载没成功时自动退回「现加载现播」，行为与旧版一致 ——
 * 不会因为预加载失败而播不出广告。
 */
class AdManager private constructor() {

    companion object {
        private const val TAG = "AdManager"

        @Volatile
        private var instance: AdManager? = null

        fun getInstance(): AdManager = instance ?: synchronized(this) {
            instance ?: AdManager().also { instance = it }
        }
    }

    /** 预加载好、还没展示的广告。 */
    private var readyAd: RewardVideoAd? = null

    /** 正在展示的广告。 */
    private var activeAd: RewardVideoAd? = null

    /** 本次展示的发奖回调。 */
    private var pendingResult: ((Boolean) -> Unit)? = null

    private var loading = false
    private var showing = false
    private var rewarded = false

    /** 当前是否具备展示条件（SDK 已初始化 + 广告位已配置）。 */
    fun isReady(): Boolean =
        TosinAdInitializer.isSdkInitialized && AdSdkConfig.rewardVideoId.isNotEmpty()

    /** 是否已经预加载好一条（UI 可据此提示「秒开」）。 */
    fun isPreloaded(): Boolean = readyAd != null

    private fun newAd(activity: Activity): RewardVideoAd = RewardVideoAd(
        activity,
        RewardVideoConfig.Builder().codeId(AdSdkConfig.rewardVideoId).build()
    )

    /**
     * 预加载一条激励视频。幂等：已有就绪的广告或正在加载时直接返回。
     * [onReady] 参数为「当前是否已有可秒开的广告」。
     */
    @Synchronized
    fun preload(activity: Activity, onReady: (Boolean) -> Unit = {}) {
        if (!isReady()) {
            onReady(false)
            return
        }
        if (readyAd != null || loading || showing) {
            onReady(readyAd != null)
            return
        }
        try {
            loading = true
            val ad = newAd(activity)
            ad.loadRewardVideo(RewardListener(activity, ad, autoShow = false))
        } catch (t: Throwable) {
            loading = false
            Log.e(TAG, "preload failed", t)
            onReady(false)
        }
    }

    /**
     * 展示一条激励视频。无论成功失败，[onResult] 都会被调用一次：
     * true = 看完并满足发奖条件；false = 加载失败 / 中途关闭 / 未配置。
     */
    @Synchronized
    fun showRewardVideo(activity: Activity, onResult: (Boolean) -> Unit) {
        if (!isReady()) {
            onResult(false)
            return
        }
        if (showing) {
            // 已经在播了，避免重复弹窗
            onResult(false)
            return
        }
        val pre = readyAd
        readyAd = null
        try {
            if (pre == null) {
                // 没预加载成功：退回现加载现播
                val ad = newAd(activity)
                activeAd = ad
                pendingResult = onResult
                rewarded = false
                ad.loadRewardVideo(RewardListener(activity, ad, autoShow = true))
            } else {
                activeAd = pre
                pendingResult = onResult
                rewarded = false
                showing = true
                pre.showAd(activity)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "showRewardVideo failed", t)
            settle(false)
        }
    }

    private fun settle(ok: Boolean) {
        if (!showing && pendingResult == null) return
        showing = false
        val cb = pendingResult
        pendingResult = null
        val ad = activeAd
        activeAd = null
        rewarded = false
        try {
            ad?.destory()
        } catch (t: Throwable) {
        }
        if (ad != null && ad === readyAd) readyAd = null
        cb?.invoke(ok)
    }

    /**
     * 广告回调。预加载（autoShow=false）与现加载现播（autoShow=true）共用，
     * 区别只在于「加载成功后是立刻展示还是留着等玩家点」。
     */
    private inner class RewardListener(
        private val activity: Activity,
        private val ad: RewardVideoAd,
        private val autoShow: Boolean
    ) : RewardVideoListener {

        override fun onLoadSuccess() {
            loading = false
            if (autoShow) {
                showing = true
                try {
                    ad.showAd(activity)
                } catch (t: Throwable) {
                    Log.e(TAG, "showAd failed", t)
                    settle(false)
                }
            } else {
                readyAd = ad
            }
        }

        override fun onLoadFail(adError: AdError?) {
            loading = false
            if (ad === readyAd) readyAd = null
            try {
                ad.destory()
            } catch (t: Throwable) {
            }
            // 预加载失败不该走发奖回调（此时没有 pendingResult，settle 会直接返回）
            settle(false)
        }

        override fun onRewardVerify() {
            rewarded = true
        }

        override fun onVideoComplete() {}

        override fun onAdClose() {
            val got = rewarded
            settle(got)
            // 看完一条立刻补下一条：把下一次点击的等待也省掉
            preload(activity)
        }

        override fun onADShowError(fail: String) {
            settle(false)
        }

        override fun onExposure() {}

        override fun onADShow() {}

        override fun onADClick() {}
    }

    fun destroy() {
        try {
            readyAd?.destory()
        } catch (t: Throwable) {
        }
        readyAd = null
        try {
            activeAd?.destory()
        } catch (t: Throwable) {
        }
        activeAd = null
    }
}
