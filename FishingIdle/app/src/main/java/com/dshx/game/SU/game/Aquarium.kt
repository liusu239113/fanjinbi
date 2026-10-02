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
 * ## 缸位结构
 *
 * 不是"一个大缸随便放"，而是**多缸 + 每缸有限位**：
 *  - 开局只有 **1 个缸**、缸里只能放 **1 条**（容量 1）
 *  - 缸位可以扩容，**每个缸最多 10 条**
 *  - 最多可以建 **3 个缸**（总容量上限 3 × 10 = 30）
 *
 * 两个升级方向互相独立、价格都很贵：先扩容（同一缸多放几条），
 * 还是先开新缸（多一个 10 位的空间）。玩家要自己权衡。
 *
 * 加成口径：**挂机收益**（自动钓手 / 鹈鹕 / 拖网 / 离线结算）。
 * 手动钓鱼不加成 —— 否则玩家会发现"越挂越赚"，点击循环就没意义了。
 */
object Aquarium {

    /** 最多几个水族馆。 */
    const val MAX_TANKS = 3

    /** 每个水族馆最多几个缸位。 */
    const val SLOTS_PER_TANK_MAX = 10

    /** 开局缸数。 */
    const val BASE_TANKS = 1

    /** 开局每个缸的缸位数。 */
    const val BASE_SLOTS_PER_TANK = 1

    /**
     * 加成上限。
     *
     * 单条鱼最高约 +25%（王者传说），满配 30 条理论上能到 +700%。
     * 但那是"集满所有顶级鱼"的终局状态，中间过程是缓慢爬升的；
     * 这里再压一道总上限，防止后期数值失控。
     */
    const val MAX_BONUS = 4.0

    /** 单条鱼加成上限。 */
    private const val SINGLE_CAP = 0.25

    /** 稀有度权重 1.0（常见、普通体型）的基准加成。 */
    private const val BASE_PER_RARITY = 0.012

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
        return base.coerceAtMost(SINGLE_CAP)
    }

    // ---------------- 容量 ----------------

    /** 已建成的缸数。 */
    fun tanks(state: GameState): Int =
        state.aquariumTanks.coerceIn(BASE_TANKS, MAX_TANKS)

    /** 每个缸的缸位数。 */
    fun slotsPerTank(state: GameState): Int =
        state.aquariumSlots.coerceIn(BASE_SLOTS_PER_TANK, SLOTS_PER_TANK_MAX)

    /** 总容量 = 缸数 × 每缸缸位。 */
    fun capacity(state: GameState): Int = tanks(state) * slotsPerTank(state)

    /** 是否已满。 */
    fun isFull(state: GameState): Boolean = state.aquarium.size >= capacity(state)

    /** 还能不能扩容缸位。 */
    fun canExpand(state: GameState): Boolean = slotsPerTank(state) < SLOTS_PER_TANK_MAX

    /** 还能不能新建水族馆。 */
    fun canAddTank(state: GameState): Boolean = tanks(state) < MAX_TANKS

    /**
     * 某个缸当前展示的展品。
     *
     * 展品是一个扁平列表，按 [slotsPerTank] 切块分给各个缸 ——
     * 这样不必为每条鱼单独记"它在哪个缸"，扩容时也不会出现"某个缸空了"的怪状态。
     */
    fun tankContent(state: GameState, tankIndex: Int): List<StoredFish> {
        val per = slotsPerTank(state)
        val from = tankIndex * per
        if (from >= state.aquarium.size) return emptyList()
        return state.aquarium.subList(from, (from + per).coerceAtMost(state.aquarium.size))
    }

    // ---------------- 价格 ----------------

    /**
     * 价格锚点：当前一条常见鱼的价值 × 地图倍率。
     *
     * 与开箱同一套思路 —— 绝对数字会被玩家的升级曲线甩开，
     * 只有锚定"玩家现在的收入规模"，缸位才会一直是笔要掂量的投入。
     */
    private fun valueScale(state: GameState): Double =
        state.catchValue(Rarity.COMMON) * state.currentMap.valueMultiplier

    /** 扩容一个缸位的价格（每缸从 n 扩到 n+1）。 */
    fun expandPrice(state: GameState): Double {
        val n = slotsPerTank(state)
        return EXPAND_BASE * valueScale(state) * EXPAND_GROWTH.pow((n - BASE_SLOTS_PER_TANK).toDouble())
    }

    /** 新建一个水族馆的价格。比扩容贵得多：多一个缸等于多 10 个潜在缸位。 */
    fun addTankPrice(state: GameState): Double {
        val n = tanks(state)
        return TANK_BASE * valueScale(state) * TANK_GROWTH.pow((n - BASE_TANKS).toDouble())
    }

    /** 扩容基数（× 当前渔获价值）。 */
    private const val EXPAND_BASE = 2_000_000.0
    private const val EXPAND_GROWTH = 2.5

    /** 新建缸的基数（× 当前渔获价值）。 */
    private const val TANK_BASE = 50_000_000.0
    private const val TANK_GROWTH = 5.0

    // ---------------- 操作 ----------------

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

    /** 花金币扩容一个缸位。返回是否成功。 */
    fun expand(state: GameState): Boolean {
        if (!canExpand(state)) return false
        val price = expandPrice(state)
        if (state.money < price) return false
        state.money -= price
        state.aquariumSlots++
        return true
    }

    /** 花金币新建一个水族馆。返回是否成功。 */
    fun addTank(state: GameState): Boolean {
        if (!canAddTank(state)) return false
        val price = addTankPrice(state)
        if (state.money < price) return false
        state.money -= price
        state.aquariumTanks++
        return true
    }

    /** 免费扩容一个缸位（看广告奖励）。已满级返回 false。 */
    fun applyFreeExpand(state: GameState): Boolean {
        if (!canExpand(state)) return false
        state.aquariumSlots++
        return true
    }

    /** 把总加成格式化成 "+37%"。 */
    fun bonusText(state: GameState): String {
        val pct = (totalBonus(state) * 100)
        return "+${pct.toInt()}%"
    }

    /** 全部展品带来的总加成（已压总上限）。 */
    fun totalBonus(state: GameState): Double =
        state.aquariumBonus.coerceAtMost(MAX_BONUS)
}
