package com.dshx.game.SU.ads

import android.app.Activity
import com.dshx.game.SU.game.RewardAds

/**
 * 把广告 SDK 接到游戏侧的 [RewardAds] 接入位上。
 *
 * 游戏逻辑只认 RewardAds；换广告平台只需要改这一个文件。
 * 调用时机：**用户同意隐私政策之后**（合规要求）。
 */
object AdBridge {

    /** 用户同意隐私政策后调用：初始化 SDK、注入播放实现、预热第一条广告。 */
    fun setup(activity: Activity) {
        RewardAds.install { _, callback ->
            AdManager.getInstance().showRewardVideo(activity, callback)
        }
        TosinAdInitializer.init(activity.application) { ok ->
            // 初始化成功才把 available 置 true：UI 上「看广告」入口据此显示可用/接入中
            RewardAds.available = ok && AdSdkConfig.rewardVideoId.isNotEmpty()
            // 失败原因带出去，别让玩家（和开发者）只看到一句"没准备好"
            RewardAds.lastError = when {
                RewardAds.available -> ""
                ok -> "激励视频广告位 ID 未配置"
                else -> TosinAdInitializer.lastError.ifEmpty { "广告 SDK 初始化未完成" }
            }
            if (RewardAds.available) {
                // 初始化完立刻预热一条：玩家第一次点广告就是秒开，
                // 而不是先盯着「广告加载中」等几秒（那是完播率最大的漏斗）。
                AdManager.getInstance().preload(activity)
            }
        }
    }

    /** 广告当前是否真的可用。 */
    fun isReady(): Boolean = RewardAds.available && AdManager.getInstance().isReady()

    /** 预热下一条。离开战斗/商店等场景后调用，保持「点开即看」。 */
    fun preload(activity: Activity) {
        if (isReady()) AdManager.getInstance().preload(activity)
    }
}
