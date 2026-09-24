package com.dshx.game.SU.ads

import android.app.Application
import android.util.Log

/**
 * Tosin 广告 SDK 初始化。
 *
 * 必须在用户同意隐私政策之后调用 —— 见 AdPrivacy / 首启隐私页。
 * 未配置 appId / 广告位时直接跳过（[AdSdkConfig.isConfigured]）。
 */
object TosinAdInitializer {

    private const val TAG = "TosinAdInit"

    @Volatile
    var isSdkInitialized: Boolean = false
        private set

    /** 最近一次失败的原因（成功时清空）。UI 直接展示给玩家/开发者。 */
    @Volatile
    var lastError: String = ""
        private set

    @Volatile
    private var initializing = false

    /** 上次发起初始化的时间：失败后隔一小段时间才允许再试，避免连点把 SDK 打爆。 */
    @Volatile
    private var lastAttemptMs: Long = 0L

    private const val RETRY_COOLDOWN_MS = 3000L

    /**
     * 初始化 SDK；[onDone] 参数为是否可用。
     * 成功后幂等；失败后允许重试（有 3 秒冷却）。
     */
    fun init(application: Application, onDone: (Boolean) -> Unit = {}) {
        if (isSdkInitialized) {
            onDone(true)
            return
        }
        if (!AdSdkConfig.isConfigured) {
            lastError = "未配置广告应用 ID 或激励视频广告位 ID"
            Log.e(TAG, lastError)
            onDone(false)
            return
        }
        if (initializing) {
            // 正在初始化，等回调即可
            onDone(false)
            return
        }
        val now = System.currentTimeMillis()
        if (lastError.isNotEmpty() && now - lastAttemptMs < RETRY_COOLDOWN_MS) {
            // 刚失败过，冷却中
            onDone(false)
            return
        }
        initializing = true
        lastAttemptMs = now
        try {
            with(application) {
                val config = com.tosin.sdk.initsdk.init.TosinInitConfig.Builder()
                    .appId(AdSdkConfig.appId)
                    .isDebug(AdSdkConfig.isDebug)
                    // 隐私合规：关掉敏感信息采集（上架审核点名的就是 IMEI）
                    .customController(object : com.tosin.sdk.initsdk.init.CustomController() {
                        override fun canUsePhoneState(): Boolean = false
                        override fun canUseMacAddress(): Boolean = false
                        override fun canReadLocation(): Boolean = false
                        override fun canGetInstallPackages(): Boolean = false
                        override fun canUsePermissionRecordAudio(): Boolean = false
                        override fun canUseOaid(): Boolean = true
                        override fun canUseAndroidId(): Boolean = true
                        override fun canUseWifiState(): Boolean = true
                    })
                    .build()

                Log.i(TAG, "init start appId=" + AdSdkConfig.appId)
                com.tosin.sdk.initsdk.init.TosinSDK.instance.init(
                    this,
                    config,
                    object : com.tosin.sdk.initsdk.init.InitListener {
                        override fun onInitSuccess() {
                            isSdkInitialized = true
                            lastError = ""
                            initializing = false
                            Log.i(TAG, "init success")
                            onDone(true)
                        }

                        override fun onInitFail(fail: String?) {
                            initializing = false
                            // 关键：把 SDK 给的原因留下来，别再吞掉
                            lastError = "广告 SDK 初始化失败：" + (fail ?: "SDK 未返回原因")
                            Log.e(TAG, lastError)
                            onDone(false)
                        }
                    }
                )
            }
        } catch (t: Throwable) {
            initializing = false
            lastError = "广告 SDK 初始化异常：" + (t.message ?: t.javaClass.simpleName)
            Log.e(TAG, lastError, t)
            onDone(false)
        }
    }

    /** 供 UI 强制重试（会清掉冷却）。 */
    fun forceRetry(application: Application, onDone: (Boolean) -> Unit = {}) {
        lastAttemptMs = 0L
        lastError = ""
        initializing = false
        init(application, onDone)
    }
}
