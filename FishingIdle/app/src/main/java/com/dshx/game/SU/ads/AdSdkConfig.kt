package com.dshx.game.SU.ads

/**
 * 广告 SDK 配置（Tosin / TopOn 聚合）。
 *
 * appId 与广告位 ID 由广告后台分配，拿到后**只改这里一处**即可。
 * 未配置时 [isConfigured] 为 false，广告初始化会被跳过，
 * 游戏内「看广告」入口会显示「接入中」，不会出现点了没反应或误报错误。
 */
object AdSdkConfig {

    /** Tosin SDK App ID（广告后台分配）。 */
    var appId: Long = 2103057147818065921L

    /** 激励视频广告位 ID。 */
    var rewardVideoId: String = "2103058323594735618"

    /**
     * 隐私政策链接：走外部浏览器打开。
     * 与 TapTap 后台登记的地址保持一致。
     */
    var privacyPolicyUrl: String = "http://yanyususu.online:5555/diaoyu.html"

    /** 是否打印 SDK 调试日志（正式包关闭）。 */
    var isDebug: Boolean = false

    /** 配置齐全才允许初始化 SDK。 */
    val isConfigured: Boolean get() = appId != 0L && rewardVideoId.isNotEmpty()

    fun configure(
        appId: Long,
        rewardVideoId: String,
        privacyPolicyUrl: String = this.privacyPolicyUrl,
        isDebug: Boolean = false
    ) {
        this.appId = appId
        this.rewardVideoId = rewardVideoId
        this.privacyPolicyUrl = privacyPolicyUrl
        this.isDebug = isDebug
    }
}
