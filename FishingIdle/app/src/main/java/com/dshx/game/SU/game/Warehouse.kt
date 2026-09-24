package com.dshx.game.SU.game

import kotlin.math.abs
import kotlin.random.Random

/**
 * 渔获仓库。
 *
 * 设计目标（对应「翡翠赌石」那套感觉）：
 *  - **新鱼种**和**破纪录**的鱼默认不进钱包，而是进仓库 —— 它们有收藏/升值价值。
 *  - 普通重复的鱼直接折算成金币，不占格子（否则仓库瞬间被塞满）。
 *  - 每条鱼有**行情价**，按天波动；鱼贩子每 [MERCHANT_INTERVAL] 秒来一次，
 *    出价随机（可能高出行情 40%，也可能只给 60%）—— 赌的就是这一下。
 *  - 仓库容量有限（初始 [BASE_CAPACITY] 格），满了必须先卖或扩容。
 *
 * 存档：仓库内容必须存（[StoredFish] 列表），行情种子与鱼贩计时不用存。
 */
class StoredFish(
    /** 鱼种 id。 */
    val speciesId: String,
    /** 入库时的体型。 */
    val sizeOrdinal: Int,
    /** 入库时的估值（金币）。 */
    val baseValue: Double,
    /** 入库时间戳（毫秒），用于展示"存放了多久"。 */
    val storedAt: Long,
    /**
     * 是否"新鱼种首次入库"（图鉴首捕）。
     * 首捕与破纪录的鱼标一个金框，卖之前给玩家一个提醒。
     */
    val firstCatch: Boolean,
) {
    val size: FishSize get() = FishSize.entries.getOrElse(sizeOrdinal) { FishSize.NORMAL }

    /** 存放天数（不足一天按 1 算，用于展示）。 */
    fun daysStored(now: Long): Int =
        (((now - storedAt) / 86_400_000L).toInt() + 1).coerceAtLeast(1)
}

/** 一次鱼贩报价。 */
class MerchantOffer(
    /** 报价系数：1.0 = 行情价。 */
    val factor: Double,
    /** 鱼贩想要第几条（null = 全收）。 */
    val index: Int?,
) {
    val isGood: Boolean get() = factor >= 1.0
}

/**
 * 仓库逻辑。全部是纯函数 + 少量状态，方便单测。
 */
object Warehouse {

    /** 初始容量（格）。 */
    const val BASE_CAPACITY = 12

    /** 每次扩容增加的格数。 */
    const val CAPACITY_STEP = 6

    /** 容量上限。 */
    const val MAX_CAPACITY = 60

    /** 鱼贩到访间隔（秒）。 */
    const val MERCHANT_INTERVAL = 600f

    /** 鱼贩停留时长（秒）。 */
    const val MERCHANT_STAY = 45f

    /**
     * 行情价倍率：按"天"做种子，同一天内价格稳定，跨天波动。
     * 范围 [0.7, 1.3]。
     */
    fun marketFactor(now: Long): Double {
        val day = now / 86_400_000L
        val rnd = Random(day * 104_729L)
        return 0.7 + rnd.nextDouble() * 0.6
    }

    /** 某条鱼当前的市场价。 */
    fun marketValue(fish: StoredFish, now: Long): Double =
        fish.baseValue * marketFactor(now)

    /** 全部存货的市场总价。 */
    fun marketTotal(list: List<StoredFish>, now: Long): Double =
        list.sumOf { marketValue(it, now) }

    /** 容量上限（含买过的扩容）。 */
    fun capacity(state: GameState): Int =
        (BASE_CAPACITY + state.warehouseUpgrades * CAPACITY_STEP).coerceAtMost(MAX_CAPACITY)

    /** 是否已满。 */
    fun isFull(state: GameState): Boolean =
        state.warehouse.size >= capacity(state)

    /**
     * 入库。返回是否真的放进了仓库。
     * 仓库满时返回 false —— 调用方应当把这笔钱直接折算给玩家，不能凭空吞掉。
     */
    fun store(state: GameState, fish: StoredFish): Boolean {
        if (isFull(state)) return false
        state.warehouse.add(fish)
        return true
    }

    /** 卖掉第 [index] 条，按给定倍率结算。返回实际到手金额。 */
    fun sell(state: GameState, index: Int, factor: Double, now: Long): Double {
        if (index !in state.warehouse.indices) return 0.0
        val fish = state.warehouse.removeAt(index)
        val gain = marketValue(fish, now) * factor
        state.money += gain
        return gain
    }

    /** 全部卖出。返回总金额。 */
    fun sellAll(state: GameState, factor: Double, now: Long): Double {
        val gain = marketTotal(state.warehouse, now) * factor
        state.warehouse.clear()
        state.money += gain
        return gain
    }

    /**
     * 生成一次鱼贩报价。
     *
     * 30% 概率"捡漏"（0.6~0.85 倍，压价），
     * 55% 概率正常（0.9~1.15），
     * 15% 概率"大赚"（1.3~1.6 倍）。
     * 这种偏态分布才有赌的感觉：多数时候一般，偶尔一次特别爽。
     */
    fun rollOffer(rnd: Random = Random): MerchantOffer {
        val r = rnd.nextDouble()
        val factor = when {
            r < 0.30 -> 0.60 + rnd.nextDouble() * 0.25
            r < 0.85 -> 0.90 + rnd.nextDouble() * 0.25
            else -> 1.30 + rnd.nextDouble() * 0.30
        }
        return MerchantOffer(factor, null)
    }

    /**
     * 鱼贩偏好：他更想收**值钱**的那条，所以指定收购时挑估值最高的。
     * 40% 概率只收一条（且是好货），其余全收。
     */
    fun pickTarget(list: List<StoredFish>, offer: MerchantOffer): Int? {
        if (offer.index != null) return offer.index
        if (list.isEmpty()) return null
        return list.indices.maxByOrNull { list[it].baseValue }
    }

    /** 扩容价格：随已扩容次数递增。 */
    fun upgradePrice(state: GameState): Double =
        200_000.0 * Math.pow(1.9, state.warehouseUpgrades.toDouble())

    /** 还能不能再扩容。 */
    fun canUpgrade(state: GameState): Boolean =
        capacity(state) < MAX_CAPACITY

    /** 尝试扩容。返回是否成功。 */
    fun upgrade(state: GameState): Boolean {
        if (!canUpgrade(state)) return false
        val price = upgradePrice(state)
        if (state.money < price) return false
        state.money -= price
        state.warehouseUpgrades++
        return true
    }

    /** 按入库时间排序的副本（最早的在前面），用于九宫格展示。 */
    fun sorted(list: List<StoredFish>): List<StoredFish> =
        list.sortedBy { it.storedAt }

    /** 相对行情价的涨跌描述。 */
    fun trendText(now: Long): String {
        val f = marketFactor(now)
        val pct = ((f - 1.0) * 100).toInt()
        return when {
            abs(pct) < 5 -> "行情平稳"
            pct > 0 -> "行情上涨 +$pct%"
            else -> "行情下跌 $pct%"
        }
    }
}
