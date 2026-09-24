package com.taptap.fishingidle.game

/**
 * 游戏内容定义。价格曲线完全沿用原版 balancing 表：
 * price(n) = floor(base^n * multiplier + flatOffset)
 */
object Content {

    // ---------------- 鱼群（商店「鱼苗」页）----------------

    val fishItems: List<PurchasableDef> = listOf(
        PurchasableDef(
            id = "common_fish", attribute = Attribute.COMMON_FISH,
            name = "小鱼", desc = "在钓场多放一条小鱼。基础收益 {v} 金币。",
            icon = "fish_common", priceBase = 1.3, priceMultiplier = 2.0,
            maxPurchases = 100,
        ),
        PurchasableDef(
            id = "rare_fish", attribute = Attribute.RARE_FISH,
            name = "鲤鱼", desc = "放入一条鲤鱼。基础收益 {v} 金币。",
            icon = "fish_rare", priceBase = 1.3, priceMultiplier = 200.0,
            maxPurchases = 50,
        ),
        PurchasableDef(
            id = "epic_fish", attribute = Attribute.EPIC_FISH,
            name = "锦鲤", desc = "放入一条锦鲤。基础收益 {v} 金币。",
            icon = "fish_epic", priceBase = 1.3, priceMultiplier = 5000.0,
            maxPurchases = 30,
            visibleWhen = { it.rareFish > 0 },
        ),
        PurchasableDef(
            id = "legend_fish", attribute = Attribute.LEGEND_FISH,
            name = "传说鱼苗", desc = "放入一条中华鲟级别的名贵鱼。基础收益 {v} 金币。",
            icon = "fw_xunyu", priceBase = 1.3, priceMultiplier = 80000.0,
            maxPurchases = 20,
            visibleWhen = { it.epicFish > 0 },
        ),
        PurchasableDef(
            id = "helper", attribute = Attribute.HELPER,
            name = "自动钓手",
            desc = "雇一名钓手替你钓鱼。最多 20 名，升级可让他一次照看多条鱼。",
            // 钓手是挂机收益的来源，也是整个放置循环的核心：
            // 起步价抬高、曲线拉陡（1.75^n × 500），并把总数收到 20 名 ——
            // 之前 1.6^n × 120 起步只要 120 金币，开局几分钟就能雇满一队。
            icon = "icon_helper", priceBase = 1.75, priceMultiplier = 500.0,
            maxPurchases = 20,
            // 先自己动手钓上几条鱼，钓手才会出现（别一进游戏就挂机）
            visibleWhen = { it.totalCatches >= 8 },
        ),
    )

    // ---------------- 升级（商店「升级」页）----------------

    val upgrades: List<PurchasableDef> = listOf(
        // --- 单次收益加成 ---
        PurchasableDef(
            id = "value_add_common", attribute = Attribute.COMMON_VALUE_ADD,
            name = "鱼饵改良", desc = "每条小鱼收益 +{n} 金币。",
            icon = "icon_bait_common", priceBase = 1.8, priceMultiplier = 40.0,
            increaseAmount = 1.0, maxPurchases = 50,
            visibleWhen = { it.commonFish > 0 },
        ),
        PurchasableDef(
            id = "value_add_rare", attribute = Attribute.RARE_VALUE_ADD,
            name = "鲤鱼鱼饵", desc = "每条鲤鱼收益 +{n} 金币。",
            icon = "icon_bait_rare", priceBase = 1.8, priceMultiplier = 200.0,
            increaseAmount = 5.0, maxPurchases = 50,
            visibleWhen = { it.rareFish > 0 },
        ),
        PurchasableDef(
            id = "value_add_epic", attribute = Attribute.EPIC_VALUE_ADD,
            name = "锦鲤鱼饵", desc = "每条锦鲤收益 +{n} 金币。",
            icon = "icon_bait_epic", priceBase = 1.8, priceMultiplier = 800.0,
            increaseAmount = 30.0, maxPurchases = 50,
            visibleWhen = { it.epicFish > 0 },
        ),
        PurchasableDef(
            id = "value_add_legend", attribute = Attribute.LEGEND_VALUE_ADD,
            name = "深海鱼饵", desc = "每条巨口鱼收益 +{n} 金币。",
            icon = "icon_bait_legend", priceBase = 1.8, priceMultiplier = 12000.0,
            increaseAmount = 150.0, maxPurchases = 50,
            visibleWhen = { it.legendFish > 0 },
        ),

        // --- 收益倍率 ---
        PurchasableDef(
            id = "value_mul_common", attribute = Attribute.COMMON_VALUE_MUL,
            name = "小鱼收益 ×", desc = "小鱼收益倍率 +{n}。",
            icon = "icon_mul_common", priceBase = 1.9, priceMultiplier = 30.0,
            increaseAmount = 0.2, maxPurchases = 20,
            visibleWhen = { it.commonFish >= 20 },
        ),
        PurchasableDef(
            id = "value_mul_rare", attribute = Attribute.RARE_VALUE_MUL,
            name = "鲤鱼收益 ×", desc = "鲤鱼收益倍率 +{n}。",
            icon = "icon_mul_rare", priceBase = 1.9, priceMultiplier = 150.0,
            increaseAmount = 0.2, maxPurchases = 20,
            visibleWhen = { it.rareFish >= 10 },
        ),
        PurchasableDef(
            id = "value_mul_epic", attribute = Attribute.EPIC_VALUE_MUL,
            name = "锦鲤收益 ×", desc = "锦鲤收益倍率 +{n}。",
            icon = "icon_mul_epic", priceBase = 1.9, priceMultiplier = 600.0,
            increaseAmount = 0.2, maxPurchases = 20,
            visibleWhen = { it.epicFish >= 5 },
        ),
        PurchasableDef(
            id = "value_mul_legend", attribute = Attribute.LEGEND_VALUE_MUL,
            name = "巨口鱼收益 ×", desc = "巨口鱼收益倍率 +{n}。",
            icon = "icon_mul_legend", priceBase = 1.9, priceMultiplier = 9000.0,
            increaseAmount = 0.2, maxPurchases = 20,
            visibleWhen = { it.legendFish >= 5 },
        ),

        // --- 收线速度 ---
        PurchasableDef(
            id = "reel_speed_common", attribute = Attribute.COMMON_REEL_SPEED,
            name = "快收线轮", desc = "小鱼收线速度 +{n}。",
            icon = "icon_reel_common", priceBase = 1.5, priceMultiplier = 500.0,
            increaseAmount = 0.1, maxPurchases = 20,
            visibleWhen = { it.commonFish >= 20 },
            buyableWhen = { it.commonFish >= 2 },
        ),
        PurchasableDef(
            id = "reel_speed_rare", attribute = Attribute.RARE_REEL_SPEED,
            name = "碳素线轮", desc = "鲤鱼收线速度 +{n}。",
            icon = "icon_reel_rare", priceBase = 1.5, priceMultiplier = 1500.0,
            increaseAmount = 0.15, maxPurchases = 20,
            visibleWhen = { it.rareFish >= 10 },
        ),
        PurchasableDef(
            id = "reel_speed_epic", attribute = Attribute.EPIC_REEL_SPEED,
            name = "液压绞盘", desc = "锦鲤收线速度 +{n}。",
            icon = "icon_reel_epic", priceBase = 1.5, priceMultiplier = 4000.0,
            increaseAmount = 0.2, maxPurchases = 20,
            visibleWhen = { it.epicFish >= 5 },
        ),
        PurchasableDef(
            id = "reel_speed_legend", attribute = Attribute.LEGEND_REEL_SPEED,
            name = "深渊绞盘", desc = "巨口鱼收线速度 +{n}。",
            icon = "icon_reel_legend", priceBase = 1.5, priceMultiplier = 60000.0,
            increaseAmount = 0.2, maxPurchases = 20,
            visibleWhen = { it.legendFish >= 5 },
        ),

        // --- 自动收线 / 重抛 ---
        PurchasableDef(
            id = "auto_reel_chance", attribute = Attribute.AUTO_REEL_CHANCE,
            name = "自动重抛", desc = "钓上鱼后有 {n} 概率立刻再来一竿，不用重新抛。",
            icon = "icon_recast", priceBase = 1.9, priceMultiplier = 150.0,
            increaseAmount = 0.05, maxPurchases = 15,
            visibleWhen = { it.rareFish > 0 },
        ),
        PurchasableDef(
            id = "auto_reel_unlock", attribute = Attribute.AUTO_REEL_UNLOCK,
            name = "自动收线",
            desc = "浮标一下沉就自动收线，不用再点屏幕（收线快慢仍看线轮等级）。",
            icon = "icon_auto_reel", priceBase = 0.0, priceMultiplier = 0.0, flatOffset = 6_000.0,
            maxPurchases = 1,
            // 先自己动手钓够 40 条再谈自动化。太早给自动化，点击玩法直接没了。
            visibleWhen = { it.totalCatches >= 40 },
        ),
        PurchasableDef(
            id = "auto_cast_unlock", attribute = Attribute.AUTO_CAST_UNLOCK,
            name = "智能浮标",
            desc = "空闲时自动朝附近的鱼抛竿（船边没鱼还会自己开过去），放手也能一直钓。",
            icon = "icon_auto_cast", priceBase = 0.0, priceMultiplier = 0.0, flatOffset = 250_000.0,
            maxPurchases = 1,
            // 挂机收益是中期目标，不是开局福利：必须先买自动收线，再钓够 150 条
            visibleWhen = { it.autoReelUnlocked && it.totalCatches >= 150 },
        ),

        // --- 钓手强化 ---
        PurchasableDef(
            id = "helper_efficiency", attribute = Attribute.HELPER_EFFICIENCY,
            name = "钓手训练", desc = "钓手工作效率 +{n}。",
            icon = "icon_train", priceBase = 1.9, priceMultiplier = 30.0,
            increaseAmount = 0.15, maxPurchases = 20,
            visibleWhen = { it.helpers > 0 },
        ),
        PurchasableDef(
            id = "helper_can_rare", attribute = Attribute.HELPER_CAN_RARE,
            name = "钓手进阶", desc = "钓手可以钓取鲤鱼。",
            icon = "icon_crew_rare", priceBase = 0.0, priceMultiplier = 0.0, flatOffset = 4_000.0,
            maxPurchases = 1,
            visibleWhen = { it.rareFish > 0 && it.helpers > 0 },
        ),
        PurchasableDef(
            id = "helper_can_epic", attribute = Attribute.HELPER_CAN_EPIC,
            name = "钓手大师", desc = "钓手可以钓取锦鲤。",
            icon = "icon_crew_epic", priceBase = 0.0, priceMultiplier = 0.0, flatOffset = 80_000.0,
            maxPurchases = 1,
            visibleWhen = { it.epicFish > 0 && it.helpers > 0 },
        ),
        PurchasableDef(
            id = "helper_can_legend", attribute = Attribute.HELPER_CAN_LEGEND,
            name = "深海搭档", desc = "钓手可以钓取巨口鱼。",
            icon = "icon_crew_legend", priceBase = 0.0, priceMultiplier = 0.0, flatOffset = 1_500_000.0,
            maxPurchases = 1,
            visibleWhen = { it.legendFish > 0 && it.helpers > 0 },
        ),

        // --- 连锁 ---
        PurchasableDef(
            id = "chain_reaction", attribute = Attribute.CHAIN_REACTION,
            name = "鱼群骚动", desc = "钓上鱼时惊动周围鱼群，连锁收线。",
            icon = "icon_school", priceBase = 0.0, priceMultiplier = 0.0, flatOffset = 60_000.0,
            maxPurchases = 1,
            visibleWhen = { it.epicFish > 0 },
        ),

        // --- 进阶：稀有度收益倍率（后期主要成长）---
        PurchasableDef(
            id = "rarity_mul", attribute = Attribute.RARITY_MUL,
            name = "鉴赏眼光", desc = "所有鱼的价值 +{n} 倍率。",
            icon = "icon_rarity", priceBase = 2.0, priceMultiplier = 25000.0,
            increaseAmount = 0.15, maxPurchases = 40,
            visibleWhen = { it.epicFish > 0 },
        ),
        PurchasableDef(
            id = "map_bonus", attribute = Attribute.MAP_BONUS,
            name = "水域探索", desc = "当前水域价值倍率 +{n}。",
            icon = "icon_explore", priceBase = 2.1, priceMultiplier = 500000.0,
            increaseAmount = 0.25, maxPurchases = 40,
            visibleWhen = { it.legendFish > 0 },
        ),

        // --- 进阶：钓手规模 ---
        PurchasableDef(
            id = "helper_speed", attribute = Attribute.HELPER_SPEED,
            name = "钓手轮班", desc = "钓手划船速度 +{n}。",
            icon = "icon_shift", priceBase = 1.9, priceMultiplier = 8000.0,
            increaseAmount = 0.2, maxPurchases = 25,
            visibleWhen = { it.helpers >= 3 },
        ),
        PurchasableDef(
            id = "helper_parallel", attribute = Attribute.HELPER_PARALLEL,
            name = "并行作业", desc = "每名钓手可同时多照看 {n} 条鱼。",
            // 钓手总数收到 20 之后，这条升级是"扩大挂机产出"的正路，
            // 但也得真的接线（以前买了完全没用）
            icon = "icon_parallel", priceBase = 2.2, priceMultiplier = 800000.0,
            increaseAmount = 1.0, maxPurchases = 3,
            visibleWhen = { it.helpers >= 10 },
        ),

        // --- 进阶：连击强化 ---
        PurchasableDef(
            id = "combo_power", attribute = Attribute.COMBO_POWER,
            name = "行云流水", desc = "连击收益加成每层 +{n}。",
            icon = "icon_combo", priceBase = 1.9, priceMultiplier = 60000.0,
            increaseAmount = 0.02, maxPurchases = 25,
            visibleWhen = { it.bestCombo >= 10 },
        ),
        PurchasableDef(
            id = "combo_keep", attribute = Attribute.COMBO_KEEP,
            name = "稳如磐石", desc = "脱钩时保留 {n} 的连击层数。",
            icon = "icon_combo_keep", priceBase = 2.0, priceMultiplier = 300000.0,
            increaseAmount = 0.25, maxPurchases = 4,
            visibleWhen = { it.bestCombo >= 20 },
        ),

        // --- 进阶：自动化 ---
        PurchasableDef(
            id = "auto_reel_speed", attribute = Attribute.AUTO_REEL_SPEED,
            name = "自动绞盘", desc = "自动重抛的间隔缩短（每级 +{n} 速率）。",
            icon = "icon_winch", priceBase = 1.9, priceMultiplier = 400000.0,
            increaseAmount = 0.3, maxPurchases = 20,
            visibleWhen = { it.autoReelChance > 0.0 },
        ),
        PurchasableDef(
            id = "lucky_hook", attribute = Attribute.LUCKY_HOOK,
            name = "幸运鱼钩", desc = "钓到高稀有度鱼的概率 +{n}。",
            icon = "icon_lucky", priceBase = 2.1, priceMultiplier = 1_200_000.0,
            increaseAmount = 0.12, maxPurchases = 30,
            visibleWhen = { it.rareFish > 0 },
        ),
    )

    /**
     * 后期专属：不是数值加成，而是**换一种玩法**的东西。
     * 数值堆到中后期容易腻，这里给两条新路子：一只自己叼大鱼的鹈鹕、一张能捞一网的拖网。
     */
    val lateGame: List<PurchasableDef> = listOf(
        PurchasableDef(
            id = "pelican", attribute = Attribute.PELICAN,
            name = "鹈鹕",
            desc = "雇一只鹈鹕在水面盘旋，每几秒俯冲叼走一条鱼 —— 专挑最值钱的那条，不分稀有度。",
            icon = "icon_pelican", priceBase = 0.0, priceMultiplier = 0.0, flatOffset = 800_000.0,
            maxPurchases = 1,
            visibleWhen = { it.helpers >= 8 && it.totalCatches >= 300 },
        ),
        PurchasableDef(
            id = "net_sweep", attribute = Attribute.NET_SWEEP,
            name = "拖网",
            desc = "每 45 秒自动撒一次网，把船附近最多 $NET_MAX_FISH 条鱼一起捞上来。",
            icon = "icon_net", priceBase = 0.0, priceMultiplier = 0.0, flatOffset = 1_800_000.0,
            maxPurchases = 1,
            visibleWhen = { it.helpers >= 10 && it.totalCatches >= 600 },
        ),
    )

    /** 一网最多捞几条（与 [World.NET_MAX_FISH] 是同一个数）。 */
    private const val NET_MAX_FISH = 4

    /**
     * 后期第二梯队：把「看得到 / 够得着 / 打捞得到」也变成可买的玩法。
     * 全部排在拖网之后，避免中期的数值成长被这些辅助功能抢戏。
     */
    val lateGame2: List<PurchasableDef> = listOf(
        PurchasableDef(
            id = "sonar", attribute = Attribute.SONAR,
            name = "声呐",
            desc = "河面标出稀有鱼的位置（稀有度颜色），稀有鱼咬钩速度 +35%。",
            icon = "icon_sonar", priceBase = 0.0, priceMultiplier = 0.0, flatOffset = 2_500_000.0,
            maxPurchases = 1,
            visibleWhen = { it.netOwned },
        ),
        PurchasableDef(
            id = "fish_finder", attribute = Attribute.FISH_FINDER,
            name = "鱼探仪",
            desc = "每条鱼头顶标出收益；抛竿落点会自动吸附到附近的鱼，不再空竿。",
            icon = "icon_finder", priceBase = 0.0, priceMultiplier = 0.0, flatOffset = 4_000_000.0,
            maxPurchases = 1,
            visibleWhen = { it.sonarOwned },
        ),
        PurchasableDef(
            id = "drone", attribute = Attribute.DRONE,
            name = "无人机",
            desc = "悬停在船上方，自动抛竿的范围 +70%、间隔缩短 35%。",
            icon = "icon_drone", priceBase = 0.0, priceMultiplier = 0.0, flatOffset = 8_000_000.0,
            maxPurchases = 1,
            visibleWhen = { it.fishFinderOwned },
        ),
        PurchasableDef(
            id = "diver", attribute = Attribute.DIVER,
            name = "潜水员",
            desc = "每 3 分钟潜下水底捞一颗珍珠上来。珍珠是转生货币，永久保留。",
            icon = "icon_diver", priceBase = 0.0, priceMultiplier = 0.0, flatOffset = 15_000_000.0,
            maxPurchases = 1,
            visibleWhen = { it.droneOwned },
        ),
        PurchasableDef(
            id = "treasure", attribute = Attribute.TREASURE,
            name = "沉船宝藏",
            desc = "河面上时不时浮出沉船的宝箱，点开就是一大笔金币，小概率开出珍珠。",
            icon = "icon_chest", priceBase = 0.0, priceMultiplier = 0.0, flatOffset = 25_000_000.0,
            maxPurchases = 1,
            visibleWhen = { it.diverOwned },
        ),
    )

    /** 全部购买项，顺序即重放顺序（读档按这个顺序重放购买重建属性）。 */
    val purchasables: List<PurchasableDef> = fishItems + upgrades + lateGame + lateGame2

    fun byId(id: String): PurchasableDef? = purchasables.firstOrNull { it.id == id }

    // ---------------- 鱼种基础参数 ----------------

    /** 咬钩等待时间范围（秒），越大越稀有。 */
    fun biteDelay(kind: Rarity): ClosedFloatingPointRange<Float> = when (kind) {
        Rarity.COMMON -> 0.9f..1.8f
        Rarity.RARE -> 1.4f..2.4f
        Rarity.EPIC -> 2.0f..3.2f
        Rarity.LEGEND -> 2.6f..4.0f
    }

    /** 收线时长（秒，未计速度倍率）。 */
    fun reelDuration(kind: Rarity): Float = when (kind) {
        Rarity.COMMON -> 1.0f
        Rarity.RARE -> 1.5f
        Rarity.EPIC -> 2.1f
        Rarity.LEGEND -> 2.8f
    }

    /** 脱钩概率：越稀有的鱼越容易跑掉。 */
    fun escapeChance(kind: Rarity): Float = when (kind) {
        Rarity.COMMON -> 0.22f
        Rarity.RARE -> 0.30f
        Rarity.EPIC -> 0.38f
        Rarity.LEGEND -> 0.45f
    }

    /** 鱼在水里游动速度（像素/秒）。 */
    fun swimSpeed(kind: Rarity): Float = when (kind) {
        Rarity.COMMON -> 26f
        Rarity.RARE -> 21f
        Rarity.EPIC -> 17f
        Rarity.LEGEND -> 13f
    }
}
