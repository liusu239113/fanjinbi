package com.dshx.game.SU.game

/**
 * 激励视频广告的接入位。
 *
 * 游戏侧只依赖这一个对象：广告 SDK 初始化完成时调用 [install]，
 * 把「拉取并播放」的实现塞进来即可 —— UI 与发奖逻辑一行都不用改。
 * 未接入时 [isReady] 为 false，界面会显示「接入中」并给出替代获取途径，
 * 不会出现「点了没反应」。
 */
object RewardAds {

    /** 由广告 SDK 侧注入：参数是广告位用途，回调可能在非主线程触发。 */
    private var handler: ((String, (Boolean) -> Unit) -> Unit)? = null

    /**
     * 广告是否真的可用（SDK 初始化成功 + 广告位已配置）。
     * 由接入方在初始化回调里设置；不设置时 UI 一律显示「接入中」。
     */
    @Volatile
    var available: Boolean = false

    /** 广告不可用时的原因（SDK 初始化失败 / 未配置），UI 直接展示给玩家。 */
    @Volatile
    var lastError: String = ""

    /** 给 UI 用的一句话状态：可用，还是到底卡在哪。 */
    fun statusText(): String = when {
        isReady() -> "可看广告"
        lastError.isNotEmpty() -> lastError
        else -> "广告接入中"
    }

    fun isReady(): Boolean = handler != null && available

    fun install(impl: (String, (Boolean) -> Unit) -> Unit) {
        handler = impl
    }

    /** 请求播放一条激励视频；[onResult] 参数为「是否发奖」。未接入时立即回调 false。 */
    fun request(placement: String, onResult: (Boolean) -> Unit) {
        val h = handler
        if (h == null) {
            onResult(false)
            return
        }
        try {
            h(placement, onResult)
        } catch (t: Throwable) {
            onResult(false)
        }
    }

    /** 广告位用途。同一个激励视频位可复用，这里按用途分开统计。 */
    const val PLACEMENT_GOLD_GIFT = "gold_gift"          // 金币旁的广告礼包
    const val PLACEMENT_COOP_BUFF = "coop_buff"          // 合作 buff
    const val PLACEMENT_DAILY_GIFT = "daily_gift"        // 每日赠礼
    const val PLACEMENT_OFFLINE_DOUBLE = "offline_double" // 离线收益翻倍
    const val PLACEMENT_DEX_REWARD = "dex_reward"        // 图鉴收集奖励翻倍
    const val PLACEMENT_PRESTIGE_BOOST = "prestige_boost" // 转生前看广告加成
    const val PLACEMENT_SHOP_REFRESH = "shop_refresh"    // 免费刷新商店
}
