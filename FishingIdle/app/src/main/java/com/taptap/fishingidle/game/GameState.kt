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
    var commonFish: Int = 1
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

    /** 当前水域。 */
    val currentMap: FishingMap
        get() = Bestiary.mapById(currentMapId) ?: Bestiary.maps.first()

    /** 当前连击数：连续成功收线不脱钩会累加，脱钩清零。 */
    var combo: Int = 0
    /** 历史最高连击。 */
    var bestCombo: Int = 0
        private set

    /** 连击带来的收益加成。步长由技能「连击之势」提升，默认每层 +4%，上限 30 层。 */
    val comboMultiplier: Double
        get() = 1.0 + (combo.coerceAtMost(30) * skillComboStep)

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

    /** 脱钩/超时：连击清零。 */
    fun onCatchFail() {
        combo = 0
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
        return base * skillValueMultiplier
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

    /** 记录钓到某个鱼种。 */
    fun recordSpecies(id: String) {
        caughtSpecies.add(id)
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
    }

    /**
     * 从存档重建。
     *
     * 顺序很重要：先恢复跨轮进度（珍珠/技能/图鉴），再套用技能加成，
     * 最后重放购买 —— 因为购买产生的属性会与技能倍率相乘，
     * 顺序反了会导致数值对不上。
     */
    fun loadFrom(data: SaveData) {
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
        commonFish = 1; rareFish = 0; epicFish = 0; legendFish = 0; helpers = 0
        commonValueAdd = 0.0; rareValueAdd = 0.0; epicValueAdd = 0.0; legendValueAdd = 0.0
        commonValueMul = 1.0; rareValueMul = 1.0; epicValueMul = 1.0; legendValueMul = 1.0
        commonReelSpeed = 1.0; rareReelSpeed = 1.0; epicReelSpeed = 1.0; legendReelSpeed = 1.0
        helperEfficiency = 1.0
        autoReelChance = 0.0
        autoReelUnlocked = false
        helperCanRare = false; helperCanEpic = false; helperCanLegend = false
        chainReaction = false
    }

    companion object {
        const val BASE_COMMON = 1.0
        const val BASE_RARE = 20.0
        const val BASE_EPIC = 300.0
        const val BASE_LEGEND = 5000.0
    }
}
