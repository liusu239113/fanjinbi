package com.taptap.fishingidle.game

/**
 * 全局游戏状态。所有成长数值都通过「重放购买次数」重建，
 * 这样存档只需要保存 purchases 计数，与原版设计一致。
 */
class GameState {

    // ---- 金钱 ----
    var money: Double = 0.0
        set(value) {
            val delta = value - field
            if (delta > 0) totalMoney += delta
            field = value
            if (value > highestMoney) highestMoney = value
        }

    var totalMoney: Double = 0.0
        private set
    var highestMoney: Double = 0.0
        private set
    var highestCatch: Double = 0.0
        private set

    // ---- 鱼群数量 ----
    // 初始就放一批小鱼，否则开局水面上只有一两条，钓场看着空荡荡
    var commonFish: Int = 6
    var rareFish: Int = 0
    var epicFish: Int = 0
    var legendFish: Int = 0
    var helpers: Int = 0

    // ---- 收益加成 ----
    var commonValueAdd: Double = 0.0
    var rareValueAdd: Double = 0.0
    var epicValueAdd: Double = 0.0
    var legendValueAdd: Double = 0.0

    var commonValueMul: Double = 1.0
    var rareValueMul: Double = 1.0
    var epicValueMul: Double = 1.0
    var legendValueMul: Double = 1.0

    // ---- 收线速度倍率 ----
    var commonReelSpeed: Double = 1.0
    var rareReelSpeed: Double = 1.0
    var epicReelSpeed: Double = 1.0
    var legendReelSpeed: Double = 1.0

    // ---- 解锁标记 ----
    var autoReelChance: Double = 0.0        // 自动收线概率（对应原版重翻）
    var autoReelUnlocked: Boolean = false   // 悬停自动收线
    var helperCanRare: Boolean = false
    var helperCanEpic: Boolean = false
    var helperCanLegend: Boolean = false
    var chainReaction: Boolean = false
    var helperEfficiency: Double = 1.0

    // ---- 进阶成长（后期主要数值来源）----
    /** 所有鱼的价值倍率加成。 */
    var rarityMul: Double = 0.0
    /** 当前水域的额外价值倍率。 */
    var mapBonus: Double = 0.0
    /** 钓手划船速度加成。 */
    var helperSpeed: Double = 0.0
    /** 每名钓手可同时照看的鱼数（在基础上叠加）。 */
    var helperParallel: Int = 0
    /** 连击每层额外加成。 */
    var comboPower: Double = 0.0
    /** 脱钩时保留的连击比例 0~1。 */
    var comboKeep: Double = 0.0
    /** 自动重抛的收线速度加成。 */
    var autoReelSpeed: Double = 0.0
    /** 高稀有度鱼出现概率加成。 */
    var luckyHook: Double = 0.0

    // ---- 转生 ----
    /** 珍珠：转生货币，用于升级技能树，转生不会清空。 */
    var pearls: Long = 0

    /** 已转生次数。 */
    var prestigeCount: Int = 0

    /** 技能等级：技能 id → 已投入级数。转生不清空。 */
    val skillLevels: MutableMap<String, Int> = mutableMapOf()

    // ---- 技能树算出的加成（由 SkillTree.applyAll 维护）----
    var skillValueBonus: Double = 0.0
    var skillReelBonus: Double = 0.0
    var skillEscapeReduce: Double = 0.0
    var skillStartMoney: Double = 0.0
    var skillHelperBonus: Double = 0.0
    var skillFishCapacity: Int = 0
    var skillComboStep: Double = 0.04
    var skillPearlBonus: Double = 0.0
    var skillRareWeightBonus: Double = 0.0
    var skillGlobalBonus: Double = 0.0

    /** 技能带来的总收益倍率。 */
    val skillValueMultiplier: Double
        get() = (1.0 + skillValueBonus) * (1.0 + skillGlobalBonus)

    /** 技能带来的总收线速度倍率。 */
    val skillReelMultiplier: Double
        get() = (1.0 + skillReelBonus) * (1.0 + skillGlobalBonus)

    /** 技能带来的钓手效率倍率。 */
    val skillHelperMultiplier: Double
        get() = (1.0 + skillHelperBonus) * (1.0 + skillGlobalBonus)

    fun skillLevel(id: String): Int = skillLevels[id] ?: 0

    // ---- 购买记录（成长数据的唯一真相）----
    val purchases: MutableMap<String, Int> = mutableMapOf()

    // ---- 会话统计（不存档）----
    val earningsBySource: MutableMap<Source, Double> = mutableMapOf()
    val earningsByFish: MutableMap<Rarity, Double> = mutableMapOf()

    // ---- 进度与成就 ----
    /** 累计钓上来的鱼数量（成就用）。 */
    var totalCatches: Long = 0
        private set

    /** 已解锁成就 id。 */
    val unlockedAchievements: MutableSet<String> = mutableSetOf()

    /** 已解锁的水域 id。 */
    val unlockedMaps: MutableSet<String> = mutableSetOf("creek")

    /** 当前所在水域 id。 */
    var currentMapId: String = "creek"

    /** 已钓到过的鱼种 id（图鉴收集进度）。 */
    val caughtSpecies: MutableSet<String> = mutableSetOf()

    /** 刚首次钓到、还没弹过提示的鱼种，由 UI 消费。 */
    val pendingDexUnlocks: MutableList<String> = mutableListOf()

    /** 上次离开的时间戳（毫秒）。启动时据此结算离线收益。 */
    var lastSeenMillis: Long = 0L

    // ---- 每日任务 ----
    /** 当前是第几天（UTC 天数），用于判断是否要重置。 */
    var dailyDayIndex: Long = DailyQuests.currentDayIndex()
    /** 当日进度。 */
    val dailyProgress: DailyProgress = DailyProgress()
    /** 当日已完成的任务 id。 */
    val dailyDone: MutableSet<String> = mutableSetOf()

    /** 今日的三个任务。 */
    val todayQuests: List<DailyQuest> get() = DailyQuests.forDay(dailyDayIndex)

    /** 今日未完成的任务数（用于 HUD 红点）。 */
    val pendingQuestCount: Int
        get() = todayQuests.count { !dailyDone.contains(it.id) }

    /** 当前水域。 */
    val currentMap: FishingMap
        get() = Bestiary.mapById(currentMapId) ?: Bestiary.maps.first()

    /** 当前连击数：连续成功收线不脱钩会累加，脱钩清零。 */
    var combo: Int = 0
    /** 历史最高连击。 */
    var bestCombo: Int = 0
        private set

    /**
     * 连击收益加成。步长 = 技能「连击之势」+ 升级「行云流水」，上限 30 层。
     */
    val comboMultiplier: Double
        get() = 1.0 + (combo.coerceAtMost(30) * (skillComboStep + comboPower))

    /**
     * 全局价值倍率。
     * 三个来源相乘：进阶升级（稀有度/水域）× 图鉴收集进度。
     * 图鉴加成是**永久**的，转生也不清空，这是长线收集的动力。
     */
    val globalValueMultiplier: Double
        get() = (1.0 + rarityMul) * (1.0 + mapBonus) * DexReward.multiplier(this)

    // ---- 转生 ----

    /** 本次转生可获得多少珍珠。 */
    fun pendingPearls(): Long {
        val base = Prestige.pearlsFor(totalMoney)
        if (base <= 0) return 0
        return (base * (1.0 + skillPearlBonus)).toLong().coerceAtLeast(1)
    }

    /** 是否可以转生。 */
    fun canPrestige(): Boolean = pendingPearls() > 0

    /**
     * 执行转生：清空金币、鱼群、钓手、普通升级与地图进度，
     * 换取珍珠并保留技能树。返回本次获得的珍珠数。
     */
    fun doPrestige(): Long {
        val gain = pendingPearls()
        if (gain <= 0) return 0

        pearls += gain
        prestigeCount++

        // 重置本轮进度
        money = skillStartMoney          // 技能「开局红利」给启动资金
        totalMoney = 0.0
        highestMoney = 0.0
        highestCatch = 0.0
        combo = 0
        purchases.clear()
        unlockedMaps.clear()
        unlockedMaps.add(Bestiary.maps.first().id)
        currentMapId = Bestiary.maps.first().id
        resetAttributes()
        // 技能效果在重置后再套一遍（resetAttributes 不会动技能字段）
        SkillTree.applyAll(this)
        money = skillStartMoney
        return gain
    }

    fun recordEarning(kind: Rarity, source: Source, amount: Double) {
        earningsBySource[source] = (earningsBySource[source] ?: 0.0) + amount
        earningsByFish[kind] = (earningsByFish[kind] ?: 0.0) + amount
    }

    fun recordCatch(amount: Double) {
        if (amount > highestCatch) highestCatch = amount
    }

    /** 成功钓上一条鱼：累计计数 + 连击推进。 */
    fun onCatchSuccess() {
        totalCatches++
        combo++
        if (combo > bestCombo) bestCombo = combo
    }

    /**
     * 脱钩/超时。「稳如磐石」可以让玩家保留一部分连击层数，
     * 减少手滑一次的惩罚。
     */
    fun onCatchFail() {
        combo = (combo * comboKeep).toInt().coerceAtLeast(0)
    }

    /**
     * 某稀有度档位的单次渔获价值。
     * 对应原版 (base + additional) * multiplier，再乘上技能树加成。
     */
    fun catchValue(kind: Rarity): Double {
        val base = when (kind) {
            Rarity.COMMON -> (BASE_COMMON + commonValueAdd) * commonValueMul
            Rarity.RARE -> (BASE_RARE + rareValueAdd) * rareValueMul
            Rarity.EPIC -> (BASE_EPIC + epicValueAdd) * epicValueMul
            Rarity.LEGEND -> (BASE_LEGEND + legendValueAdd) * legendValueMul
        }
        return base * skillValueMultiplier * globalValueMultiplier
    }

    /**
     * 具体鱼种的价值 = 稀有度基础价值 × 该鱼种倍率 × 地图倍率。
     * 地图倍率是长线成长的主轴。
     */
    fun catchValue(sp: Species, map: FishingMap = currentMap): Double =
        catchValue(sp.rarity) * sp.valueMul * map.valueMultiplier

    /** 解锁一张地图。返回是否成功。 */
    fun unlockMap(map: FishingMap): Boolean {
        if (unlockedMaps.contains(map.id)) {
            currentMapId = map.id
            return true
        }
        if (money < map.unlockCost) return false
        money -= map.unlockCost
        unlockedMaps.add(map.id)
        currentMapId = map.id
        return true
    }

    /** 钓手当前是否能钓到该稀有度的鱼（由「钓手进阶」类升级解锁）。 */
    fun helperCanCatch(rarity: Rarity): Boolean = when (rarity) {
        Rarity.COMMON -> true
        Rarity.RARE -> helperCanRare
        Rarity.EPIC -> helperCanEpic
        Rarity.LEGEND -> helperCanLegend
    }

    /** 离线收益结算后记账：计入总收入与渔获数，但不算连击。 */
    fun recordOfflineEarnings(catches: Int) {
        totalCatches += catches.toLong()
    }

    /**
     * 一次成功的渔获后的统一记账入口。
     * 把「累计统计」「连击」「每日任务进度」集中处理，
     * 避免各处调用点漏埋。
     */
    fun onCatchRecorded(rarity: Rarity, value: Double) {
        onCatchSuccess()
        DailyTracker.onCatch(this, rarity, value)
        DailyTracker.onCombo(this)
    }

    /** 记录钓到某个鱼种。返回是否为**首次**发现（用于弹图鉴提示）。 */
    fun recordSpecies(id: String): Boolean {
        val isNew = caughtSpecies.add(id)
        if (isNew) {
            // 图鉴进度提升会立刻反映到全局倍率，这里记一笔待弹出的提示
            pendingDexUnlocks.add(id)
        }
        return isNew
    }

    /** 某鱼种收线耗时倍率（越大越快），含技能加成。 */
    fun reelSpeed(kind: Rarity): Double {
        val base = when (kind) {
            Rarity.COMMON -> commonReelSpeed
            Rarity.RARE -> rareReelSpeed
            Rarity.EPIC -> epicReelSpeed
            Rarity.LEGEND -> legendReelSpeed
        }
        return base * skillReelMultiplier
    }

    /** 该鱼种是否已解锁（拥有至少一条）。 */
    fun isUnlocked(kind: Rarity): Boolean = when (kind) {
        Rarity.COMMON -> commonFish > 0
        Rarity.RARE -> rareFish > 0
        Rarity.EPIC -> epicFish > 0
        Rarity.LEGEND -> legendFish > 0
    }

    fun ownedCount(kind: Rarity): Int = when (kind) {
        Rarity.COMMON -> commonFish
        Rarity.RARE -> rareFish
        Rarity.EPIC -> epicFish
        Rarity.LEGEND -> legendFish
    }

    // ---- 购买 ----

    /** 应用一次购买效果。重放存档时也会走这里。 */
    fun applyPurchase(def: PurchasableDef) {
        purchases[def.id] = (purchases[def.id] ?: 0) + 1
        val amount = def.increaseAmount
        when (def.attribute) {
            Attribute.COMMON_FISH -> commonFish += amount.toInt()
            Attribute.RARE_FISH -> rareFish += amount.toInt()
            Attribute.EPIC_FISH -> epicFish += amount.toInt()
            Attribute.LEGEND_FISH -> legendFish += amount.toInt()
            Attribute.HELPER -> helpers += amount.toInt()

            Attribute.COMMON_VALUE_ADD -> commonValueAdd += amount
            Attribute.RARE_VALUE_ADD -> rareValueAdd += amount
            Attribute.EPIC_VALUE_ADD -> epicValueAdd += amount
            Attribute.LEGEND_VALUE_ADD -> legendValueAdd += amount

            Attribute.COMMON_VALUE_MUL -> commonValueMul += amount
            Attribute.RARE_VALUE_MUL -> rareValueMul += amount
            Attribute.EPIC_VALUE_MUL -> epicValueMul += amount
            Attribute.LEGEND_VALUE_MUL -> legendValueMul += amount

            Attribute.COMMON_REEL_SPEED -> commonReelSpeed += amount
            Attribute.RARE_REEL_SPEED -> rareReelSpeed += amount
            Attribute.EPIC_REEL_SPEED -> epicReelSpeed += amount
            Attribute.LEGEND_REEL_SPEED -> legendReelSpeed += amount

            Attribute.HELPER_EFFICIENCY -> helperEfficiency += amount
            Attribute.AUTO_REEL_CHANCE -> autoReelChance += amount

            Attribute.AUTO_REEL_UNLOCK -> autoReelUnlocked = true
            Attribute.HELPER_CAN_RARE -> helperCanRare = true
            Attribute.HELPER_CAN_EPIC -> helperCanEpic = true
            Attribute.HELPER_CAN_LEGEND -> helperCanLegend = true
            Attribute.CHAIN_REACTION -> chainReaction = true

            // 进阶成长
            Attribute.RARITY_MUL -> rarityMul += amount
            Attribute.MAP_BONUS -> mapBonus += amount
            Attribute.HELPER_SPEED -> helperSpeed += amount
            Attribute.HELPER_PARALLEL -> helperParallel += amount.toInt()
            Attribute.COMBO_POWER -> comboPower += amount
            Attribute.COMBO_KEEP -> comboKeep += amount
            Attribute.AUTO_REEL_SPEED -> autoReelSpeed += amount
            Attribute.LUCKY_HOOK -> luckyHook += amount
        }
    }

    fun owned(defId: String): Int = purchases[defId] ?: 0

    /** 购买：校验 → 扣钱 → 应用效果。返回是否成功。 */
    fun buy(def: PurchasableDef): Boolean {
        val ownedCount = owned(def.id)
        if (def.isMaxed(ownedCount)) return false
        if (!def.buyableWhen(this)) return false
        val p = def.price(ownedCount)
        if (money < p) return false
        money -= p
        applyPurchase(def)
        return true
    }

    // ---- 存档 ----

    /** 把当日进度同步到存档对象。 */
    private fun SaveData.syncDaily(from: GameState) {
        dailyDayIndex = from.dailyDayIndex
        dailyDone = from.dailyDone.toMutableSet()
        dailyCatches = from.dailyProgress.catches
        dailyMoney = from.dailyProgress.moneyEarned
        dailyBestCombo = from.dailyProgress.bestCombo
        dailyRareCatches = from.dailyProgress.rareCatches
        dailyMapChanges = from.dailyProgress.mapChanges
        dailyHelpersBought = from.dailyProgress.helpersBought
    }

    /** 从存档恢复当日进度。 */
    private fun GameState.restoreDaily(from: SaveData) {
        dailyDayIndex = from.dailyDayIndex
        dailyDone.clear()
        dailyDone.addAll(from.dailyDone)
        dailyProgress.catches = from.dailyCatches
        dailyProgress.moneyEarned = from.dailyMoney
        dailyProgress.bestCombo = from.dailyBestCombo
        dailyProgress.rareCatches = from.dailyRareCatches
        dailyProgress.mapChanges = from.dailyMapChanges
        dailyProgress.helpersBought = from.dailyHelpersBought
    }

    fun toSave(): SaveData = SaveData().also {
        it.money = money
        it.totalMoney = totalMoney
        it.highestMoney = highestMoney
        it.highestCatch = highestCatch
        it.purchases = purchases.toMutableMap()
        it.pearls = pearls
        it.prestigeCount = prestigeCount
        it.skillLevels = skillLevels.toMutableMap()
        it.bestCombo = bestCombo
        it.totalCatches = totalCatches
        it.unlockedAchievements = unlockedAchievements.toMutableSet()
        it.caughtSpecies = caughtSpecies.toMutableSet()
        it.unlockedMaps = unlockedMaps.toMutableSet()
        it.currentMapId = currentMapId
        it.syncDaily(this)
    }

    /**
     * 彻底重置到全新开局：金钱归零、购买清空、图鉴与成就清空，
     * 只保留玩家设置（音量等由 Settings 单独管理）。
     */
    fun resetAll() {
        money = 0.0
        totalMoney = 0.0
        highestMoney = 0.0
        highestCatch = 0.0
        purchases.clear()
        pearls = 0
        prestigeCount = 0
        skillLevels.clear()
        bestCombo = 0
        combo = 0
        totalCatches = 0
        unlockedAchievements.clear()
        caughtSpecies.clear()
        unlockedMaps.clear()
        unlockedMaps.add(Bestiary.maps.first().id)
        currentMapId = Bestiary.maps.first().id
        resetAttributes()
        SkillTree.applyAll(this)
    }

    /**
     * 从存档重建。
     *
     * 顺序很重要：先恢复跨轮进度（珍珠/技能/图鉴），再套用技能加成，
     * 最后重放购买 —— 因为购买产生的属性会与技能倍率相乘，
     * 顺序反了会导致数值对不上。
     */
    fun loadFrom(data: SaveData) {
        lastSeenMillis = data.lastSeenMillis
        restoreDaily(data)
        // 跨天则清空当日进度
        DailyQuests.rolloverIfNeeded(this)
        pearls = data.pearls
        prestigeCount = data.prestigeCount
        skillLevels.clear()
        skillLevels.putAll(data.skillLevels)
        bestCombo = data.bestCombo
        totalCatches = data.totalCatches
        unlockedAchievements.clear()
        unlockedAchievements.addAll(data.unlockedAchievements)
        caughtSpecies.clear()
        caughtSpecies.addAll(data.caughtSpecies)
        unlockedMaps.clear()
        if (data.unlockedMaps.isEmpty()) {
            unlockedMaps.add(Bestiary.maps.first().id)
        } else {
            unlockedMaps.addAll(data.unlockedMaps)
        }
        currentMapId = data.currentMapId.ifEmpty { Bestiary.maps.first().id }

        money = data.money
        totalMoney = data.totalMoney
        highestMoney = data.highestMoney
        highestCatch = data.highestCatch

        resetAttributes()
        purchases.clear()

        // 按定义顺序重放购买，重建所有属性
        for (def in Content.purchasables) {
            val count = data.purchases[def.id] ?: 0
            repeat(count) { applyPurchase(def) }
        }

        // 技能加成最后套，保证它作用在重放后的属性上
        SkillTree.applyAll(this)
    }

    private fun resetAttributes() {
        commonFish = INITIAL_COMMON_FISH
        rareFish = 0; epicFish = 0; legendFish = 0; helpers = 0
        commonValueAdd = 0.0; rareValueAdd = 0.0; epicValueAdd = 0.0; legendValueAdd = 0.0
        commonValueMul = 1.0; rareValueMul = 1.0; epicValueMul = 1.0; legendValueMul = 1.0
        commonReelSpeed = 1.0; rareReelSpeed = 1.0; epicReelSpeed = 1.0; legendReelSpeed = 1.0
        helperEfficiency = 1.0
        rarityMul = 0.0; mapBonus = 0.0; helperSpeed = 0.0; helperParallel = 0
        comboPower = 0.0; comboKeep = 0.0; autoReelSpeed = 0.0; luckyHook = 0.0
        autoReelChance = 0.0
        autoReelUnlocked = false
        helperCanRare = false; helperCanEpic = false; helperCanLegend = false
        chainReaction = false
    }

    companion object {
        /** 开局送的小鱼数量，让钓场一开始就有生气。 */
        const val INITIAL_COMMON_FISH = 6

        const val BASE_COMMON = 1.0
        const val BASE_RARE = 20.0
        const val BASE_EPIC = 300.0
        const val BASE_LEGEND = 5000.0
    }
}
