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

    /** 游戏倍速：一条广告同时解锁 2× 和 3×，20 分钟真实时间。 */
    const val PLACEMENT_SPEED = "game_speed"

    /** 免费雇佣一名永久钓手（每日一次，需要先解锁钓手）。 */
    const val PLACEMENT_FREE_HELPER = "free_helper"

    /** 升级免单：当前最接近的可见未解锁升级免费购入一档（每日一次）。 */
    const val PLACEMENT_FREE_UPGRADE = "free_upgrade"

    // ---------------- 广告位用途 ----------------
    // 同一个激励视频位可复用，这里按用途分开统计，方便后台看哪类转化最好。

    /** 双倍收益：10 分钟内所有渔获翻倍（下线也计时）。 */
    const val PLACEMENT_DOUBLE_INCOME = "double_income"

    /** 离线收益翻倍：下次离线结算翻倍。 */
    const val PLACEMENT_OFFLINE_DOUBLE = "offline_double"

    /** 立即到账一笔金币（按当前收益水平给）。 */
    const val PLACEMENT_GOLD_DROP = "gold_drop"

    /** 稀有鱼诱饵：3 分钟内高稀有度鱼出现率大增。 */
    const val PLACEMENT_RARE_LURE = "rare_lure"

    /** 钓手加速：5 分钟内自动钓手产出 +100%。 */
    const val PLACEMENT_HELPER_RUSH = "helper_rush"

    /** 自动收线：5 分钟内收线速度 +100%。 */
    const val PLACEMENT_REEL_RUSH = "reel_rush"

    /** 珍珠礼包：直接给珍珠（转生货币，永久保留）。 */
    const val PLACEMENT_PEARL = "pearl_gift"

    /** 沉船打捞：立刻在河面浮出一个必定出珍珠的宝箱。 */
    const val PLACEMENT_CHEST_KEY = "chest_key"

    /** 图鉴加成翻倍：2 分钟内图鉴收集加成翻倍。 */
    const val PLACEMENT_DEX_BOOST = "dex_boost"

    /** 每日签到加领：每日一次的额外签到奖励。 */
    const val PLACEMENT_DAILY_BONUS = "daily_bonus"

    /** 转生加持：本次转生获得的珍珠 +50%（转生前看）。 */
    const val PLACEMENT_PRESTIGE_BOOST = "prestige_boost"

    /**
     * 仓库免费扩容：白送一次扩容，省掉金币。
     * 入口在仓库面板底部 —— 玩家正盯着"仓库已满"发愁时，转化率最高。
     */
    const val PLACEMENT_WAREHOUSE_FREE_UPGRADE = "warehouse_free_upgrade"

    /**
     * 声呐探鱼王：鱼王本来是「声呐解锁后随机撞见」，
     * 广告买的是**确定性** —— 立刻探到一条并让它现身。
     * 入口在水域页（和「买水域」同一页，但全页只有这一个广告按钮）。
     */
    const val PLACEMENT_KING_SONAR = "king_sonar"

    /**
     * 钓协借调：角色都很贵，先试后买。
     * 广告换一段限时试用权（期间可自由切换、真实生效），到期自动换回原角色。
     * 入口在角色页。
     */
    const val PLACEMENT_CHARACTER_TRIAL = "character_trial"
}
