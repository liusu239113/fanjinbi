package com.dshx.game.SU.game

import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/**
 * 可购买项。价格公式与原版一致：price(n) = floor(base^n * multiplier + flatOffset)
 * 其中 n 为已购买次数。
 */
data class PurchasableDef(
    val id: String,
    val attribute: Attribute,
    val name: String,
    val desc: String,
    val icon: String,
    val priceBase: Double,
    val priceMultiplier: Double,
    val flatOffset: Double = 0.0,
    val increaseAmount: Double = 1.0,
    val maxPurchases: Int = 1,
    /** 满足该条件才在商店可见。参数为当前游戏状态。 */
    val visibleWhen: (GameState) -> Boolean = { true },
    /** 满足该条件才能购买。 */
    val buyableWhen: (GameState) -> Boolean = { true },
) {
    fun price(owned: Int): Double {
        if (priceBase <= 0.0) return flatOffset
        return floor(priceBase.pow(owned) * priceMultiplier + flatOffset)
    }

    /**
     * 实际购买上限。
     *
     * 技能「鱼群聚集」（[GameState.skillFishCapacity]）会额外放大鱼苗类的容量 ——
     * 这个技能以前只写进状态、没人读，等于白买。
     */
    fun effectiveMax(state: GameState): Int = when (attribute) {
        Attribute.COMMON_FISH, Attribute.RARE_FISH,
        Attribute.EPIC_FISH, Attribute.LEGEND_FISH,
        -> maxPurchases + state.skillFishCapacity
        else -> maxPurchases
    }

    fun isMaxed(owned: Int, state: GameState): Boolean = owned >= effectiveMax(state)
}

/** 购买项影响的属性。沿用原版枚举语义。 */
enum class Attribute {
    COMMON_FISH, RARE_FISH, EPIC_FISH, LEGEND_FISH,
    HELPER,
    COMMON_VALUE_ADD, RARE_VALUE_ADD, EPIC_VALUE_ADD, LEGEND_VALUE_ADD,
    COMMON_VALUE_MUL, RARE_VALUE_MUL, EPIC_VALUE_MUL, LEGEND_VALUE_MUL,
    COMMON_REEL_SPEED, RARE_REEL_SPEED, EPIC_REEL_SPEED, LEGEND_REEL_SPEED,
    HELPER_EFFICIENCY,
    AUTO_REEL_CHANCE,
    AUTO_REEL_UNLOCK,
    AUTO_CAST_UNLOCK,
    HELPER_CAN_RARE,
    HELPER_CAN_EPIC,
    HELPER_CAN_LEGEND,
    CHAIN_REACTION,
    // 进阶成长
    RARITY_MUL,
    MAP_BONUS,
    HELPER_SPEED,
    HELPER_PARALLEL,
    COMBO_POWER,
    COMBO_KEEP,
    AUTO_REEL_SPEED,
    LUCKY_HOOK,
    // 后期内容：特殊单位与主动技能
    PELICAN,
    NET_SWEEP,
    SONAR,
    FISH_FINDER,
    DRONE,
    DIVER,
    TREASURE,
}

/** 收益来源，用于统计面板。 */
enum class Source(val displayName: String) {
    MANUAL("手动收线"),
    HELPER("自动钓手"),
    CHAIN("鱼群骚动"),
    AUTO("自动收线"),
    PELICAN("鹈鹕叼鱼"),
    NET("拖网捕捞"),
    TREASURE("沉船宝箱"),
}

/** 浮动文字。 */
class FloatingText(
    var x: Float,
    var y: Float,
    val text: String,
    val color: Int,
    val scale: Float,
) {
    var age = 0f
    val lifetime = 1.6f
    var dead = false

    fun update(dt: Float) {
        age += dt
        y -= 46f * dt
        if (age >= lifetime) dead = true
    }

    /** 0→1 的生命进度。 */
    val progress: Float get() = (age / lifetime).coerceIn(0f, 1f)

    /** 淡出系数。 */
    val alpha: Float get() = if (progress < 0.6f) 1f else (1f - (progress - 0.6f) / 0.4f).coerceIn(0f, 1f)
}

/** 环境气泡：缓慢上浮，到水面后从水底重新冒出。 */
class Bubble(
    var x: Float,
    var y: Float,
    val radius: Float,
    val speed: Float,
    val phase: Float,
) {
    fun update(dt: Float, time: Float) {
        y -= speed * dt
        // 左右轻微摆动，避免笔直上浮显得死板
        if (y < Space.POND_T) {
            y = Space.POND_B
            x = kotlin.random.Random.nextFloat() * Space.W
        }
    }

    /** 横向摆动的当前偏移。 */
    fun swayX(time: Float): Float = sin(time * 1.6f + phase) * 6f
}

/** 水花 / 金币爆开粒子。 */
class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    val color: Int,
    val lifetime: Float,
) {
    var age = 0f
    var dead = false

    fun update(dt: Float) {
        age += dt
        x += vx * dt
        y += vy * dt
        vy += 260f * dt      // 重力
        vx *= 0.99f
        if (age >= lifetime) dead = true
    }

    val alpha: Float get() = (1f - age / lifetime).coerceIn(0f, 1f)
}

/** 存档数据。只保存购买次数与金钱类数值，属性靠重放购买来重建（与原版一致）。 */
class SaveData {
    var money: Double = 0.0
    var totalMoney: Double = 0.0
    var highestMoney: Double = 0.0
    var highestCatch: Double = 0.0
    var purchases: MutableMap<String, Int> = mutableMapOf()

    // ---- 转生进度（跨轮保留）----
    var pearls: Long = 0
    var prestigeCount: Int = 0
    var skillLevels: MutableMap<String, Int> = mutableMapOf()
    var bestCombo: Int = 0
    var totalCatches: Long = 0
    var unlockedAchievements: MutableSet<String> = mutableSetOf()
    var caughtSpecies: MutableSet<String> = mutableSetOf()

    /** 每个鱼种钓到过的最大体型（存 FishSize.ordinal）。 */
    var bestSize: MutableMap<String, Int> = mutableMapOf()

    /** 后期玩法的计数：开过的宝箱、拽上来的鱼王。 */
    var chestsOpened: Long = 0
    var kingsCaught: Long = 0
    var unlockedMaps: MutableSet<String> = mutableSetOf("creek")
    var currentMapId: String = "creek"
    var masterVolume: Float = 0.8f
    var sfxVolume: Float = 0.8f
    var bgmVolume: Float = 0.5f
    var hideFloatingText: Boolean = false

    /** 上次离开时的时间戳（毫秒），用于结算离线收益。 */
    var lastSeenMillis: Long = 0L

    // ---- 每日任务 ----
    var dailyDayIndex: Long = 0L
    var dailyDone: MutableSet<String> = mutableSetOf()
    var dailyCatches: Long = 0
    var dailyMoney: Double = 0.0
    var dailyBestCombo: Int = 0
    var dailyRareCatches: Long = 0
    var dailyMapChanges: Int = 0
    var dailyHelpersBought: Int = 0
    var dailyChests: Int = 0
    var dailyKings: Int = 0
}
