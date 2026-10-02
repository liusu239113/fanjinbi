package com.dshx.game.SU.game

import kotlin.math.pow

/**
 * 水族馆（展缸）。
 *
 * 仓库里的鱼原本只有「卖掉」一条出路，玩家没什么可想的。水族馆给了第二条路：
 * **把鱼养起来**。展出的鱼不能再卖，但按「稀有度 × 体型 × 鱼种」永久提升挂机收益。
 *
 * 于是每一次钓到好鱼都要做一个真正的选择：
 *  - 卖掉：立刻拿到一笔钱，但这笔钱会被后面的通胀吃掉
 *  - 展出：放弃这笔钱，换一条长期复利（转生后依然生效）
 *
 * 缸位有限、扩容越来越贵，所以不可能"全都要"——取舍是这套系统的核心。
 *
 * 加成口径：**挂机收益**（自动钓手 / 鹈鹕 / 拖网 / 离线结算）。
 * 手动钓鱼不加成 —— 否则玩家会发现"越挂越赚"，点击循环就没意义了。
 */
object Aquarium {

    /** 初始缸位。 */
    const val BASE_SLOTS = 6

    /** 每次扩容增加的缸位。 */
    const val SLOT_STEP = 3

    /** 缸位上限。 */
    const val MAX_SLOTS = 30

    /** 首档扩容价。 */
    const val UPGRADE_BASE = 1_000_000.0

    /** 扩容价增长底数。 */
    const val UPGRADE_GROWTH = 2.1

    /**
     * 加成上限。
     *
     * 单条鱼最高约 +24%（王者传说），满缸 30 条理论上能到 +700%。
     * 但那是"集满所有顶级鱼"的终局状态，中间过程是缓慢爬升的；
     * 这里再压一道总上限，防止后期数值失控。
     */
    const val MAX_BONUS = 4.0

    /** 稀有度权重：越稀有的鱼，展出价值越高。 */
    private fun rarityWeight(rarity: Rarity): Double = when (rarity) {
        Rarity.COMMON -> 1.0
        Rarity.RARE -> 3.5
        Rarity.EPIC -> 10.0
        Rarity.LEGEND -> 26.0
    }

    /** 体型权重：同种鱼里，越大越值得养。 */
    private fun sizeWeight(size: FishSize): Double = when (size) {
        FishSize.NORMAL -> 1.0
        FishSize.BIG -> 1.35
        FishSize.HUGE -> 1.85
        FishSize.KING -> 2.6
    }

    /**
     * 一条鱼的展出加成（0.05 = +5% 挂机收益）。
     *
     * 三个因子相乘：稀有度（档位差 26 倍）× 体型（差 2.6 倍）× 鱼种自身价值倍率
     * （对数压缩，避免个别高倍鱼种一家独大）。首捕的鱼再给一点额外奖励，
     * 让"第一次钓到的鱼"比同款的重复鱼更值得留。
     */
    fun bonusOf(fish: StoredFish): Double {
        val species = Bestiary.speciesById(fish.speciesId)
        val rarity = rarityWeight(species?.rarity ?: Rarity.COMMON)
        val size = sizeWeight(fish.size)
        val speciesMul = species?.valueMul ?: 1.0
        val speciesFactor = 1.0 + kotlin.math.ln(1.0 + speciesMul) * 0.30
        val firstCatchBonus = if (fish.firstCatch) 1.20 else 1.0

        val base = BASE_PER_RARITY * rarity * size * speciesFactor * firstCatchBonus
        // 单条鱼最多 +25%，防止某一条极品鱼直接撑爆整个加成
        return base.coerceAtMost(SINGLE_CAP)
    }

    /** 稀有度权重 1.0（常见、普通体型）的基准加成。 */
    private const val BASE_PER_RARITY = 0.012

    /** 单条鱼加成上限。 */
    private const val SINGLE_CAP = 0.25

    /** 全部展品带来的总加成（已压总上限）。 */
    fun totalBonus(state: GameState): Double =
        state.aquariumBonus.coerceAtMost(MAX_BONUS)

    /** 缸位上限（含扩容）。 */
    fun slots(state: GameState): Int =
        (BASE_SLOTS + state.aquariumUpgrades * SLOT_STEP).coerceAtMost(MAX_SLOTS)

    /** 是否已满。 */
    fun isFull(state: GameState): Boolean = state.aquarium.size >= slots(state)

    /** 还能不能扩容。 */
    fun canUpgrade(state: GameState): Boolean = slots(state) < MAX_SLOTS

    /** 扩容价：随已扩容次数指数增长。 */
    fun upgradePrice(state: GameState): Double =
        UPGRADE_BASE * UPGRADE_GROWTH.pow(state.aquariumUpgrades.toDouble())

    /**
     * 把仓库里第 [index] 条鱼放进鱼缸。
     * 返回是否成功（仓库下标无效 / 缸满 / 已在缸里都会失败）。
     */
    fun exhibit(state: GameState, index: Int): Boolean {
        if (isFull(state)) return false
        val fish = state.warehouse.getOrNull(index) ?: return false
        // 同一件展品不能重复摆（seq 全局唯一）
        if (state.aquarium.any { it.seq == fish.seq }) return false
        state.warehouse.removeAt(index)
        state.aquarium.add(fish)
        return true
    }

    /** 把鱼缸里第 [index] 条鱼取回仓库。仓库满则失败。 */
    fun takeBack(state: GameState, index: Int): Boolean {
        if (Warehouse.isFull(state)) return false
        val fish = state.aquarium.getOrNull(index) ?: return false
        state.aquarium.removeAt(index)
        state.warehouse.add(fish)
        return true
    }

    /** 花金币扩容。返回是否成功。 */
    fun upgrade(state: GameState): Boolean {
        if (!canUpgrade(state)) return false
        val price = upgradePrice(state)
        if (state.money < price) return false
        state.money -= price
        state.aquariumUpgrades++
        return true
    }

    /** 免费扩容一次（看广告奖励）。已满级返回 false。 */
    fun applyUpgrade(state: GameState): Boolean {
        if (!canUpgrade(state)) return false
        state.aquariumUpgrades++
        return true
    }

    /** 把总加成格式化成 "+37%"。 */
    fun bonusText(state: GameState): String {
        val pct = (totalBonus(state) * 100)
        return "+${pct.toInt()}%"
    }
}
