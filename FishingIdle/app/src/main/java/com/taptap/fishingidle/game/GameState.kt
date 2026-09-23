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

    // ---- 购买记录（成长数据的唯一真相）----
    val purchases: MutableMap<String, Int> = mutableMapOf()

    // ---- 会话统计（不存档）----
    val earningsBySource: MutableMap<Source, Double> = mutableMapOf()
    val earningsByFish: MutableMap<FishKind, Double> = mutableMapOf()

    fun recordEarning(kind: FishKind, source: Source, amount: Double) {
        earningsBySource[source] = (earningsBySource[source] ?: 0.0) + amount
        earningsByFish[kind] = (earningsByFish[kind] ?: 0.0) + amount
    }

    fun recordCatch(amount: Double) {
        if (amount > highestCatch) highestCatch = amount
    }

    /** 某鱼种单次渔获价值。对应原版 (base + additional) * multiplier。 */
    fun catchValue(kind: FishKind): Double = when (kind) {
        FishKind.COMMON -> (BASE_COMMON + commonValueAdd) * commonValueMul
        FishKind.RARE -> (BASE_RARE + rareValueAdd) * rareValueMul
        FishKind.EPIC -> (BASE_EPIC + epicValueAdd) * epicValueMul
        FishKind.LEGEND -> (BASE_LEGEND + legendValueAdd) * legendValueMul
    }

    /** 某鱼种收线耗时倍率（越大越快）。 */
    fun reelSpeed(kind: FishKind): Double = when (kind) {
        FishKind.COMMON -> commonReelSpeed
        FishKind.RARE -> rareReelSpeed
        FishKind.EPIC -> epicReelSpeed
        FishKind.LEGEND -> legendReelSpeed
    }

    /** 该鱼种是否已解锁（拥有至少一条）。 */
    fun isUnlocked(kind: FishKind): Boolean = when (kind) {
        FishKind.COMMON -> commonFish > 0
        FishKind.RARE -> rareFish > 0
        FishKind.EPIC -> epicFish > 0
        FishKind.LEGEND -> legendFish > 0
    }

    fun ownedCount(kind: FishKind): Int = when (kind) {
        FishKind.COMMON -> commonFish
        FishKind.RARE -> rareFish
        FishKind.EPIC -> epicFish
        FishKind.LEGEND -> legendFish
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
    }

    /** 从存档重建：先恢复金钱，再重放所有购买，最后按购买次数重建鱼群数量。 */
    fun loadFrom(data: SaveData) {
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
