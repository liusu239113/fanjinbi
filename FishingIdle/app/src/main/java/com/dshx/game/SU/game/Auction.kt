package com.dshx.game.SU.game

import kotlin.math.pow
import kotlin.random.Random

/**
 * 拍卖行。
 *
 * 仓库原来只有「按行情价卖掉」，玩家没什么决策 —— 卖不卖都行，等于没有玩法。
 * 拍卖把同一条鱼交给**多个 AI 买家竞价**：出价有高有低，还有"鉴定师抬价"
 * "收藏家抢拍"这类特殊角色，玩家要在"现在收下"和"再等等"之间做判断。
 *
 * 每次拍卖：对一条鱼生成 [BIDDER_COUNT] 个买家的出价，取最高价作为成交价。
 * 出价围绕行情价波动，稀有度/体型越好，溢价空间越大。
 */

/** 一个买家的出价。 */
class Bid(
    /** 买家名字。 */
    val bidder: String,
    /** 出价倍率（相对行情价）。 */
    val factor: Double,
    /** 买家的身份标签，决定文案与波动区间。 */
    val kind: BidderKind,
)

/** 买家类型：各有不同的出价风格，让竞价有"人味"。 */
enum class BidderKind(val displayName: String, val minFactor: Double, val maxFactor: Double, val weight: Double) {
    /** 鱼贩：稳妥，出价接近行情。 */
    MERCHANT("鱼贩", 0.85, 1.15, 45.0),

    /** 酒楼采购：量大，偶尔压价。 */
    RESTAURANT("酒楼采购", 0.80, 1.25, 30.0),

    /** 收藏家：对稀有鱼出手大方。 */
    COLLECTOR("收藏家", 1.00, 1.60, 18.0),

    /** 鉴定师：只在鱼足够好时出现，出价最高。 */
    APPRAISER("鉴定师", 1.30, 2.20, 7.0),
    ;

    companion object {
        /** 按权重抽一个买家类型。 */
        fun roll(rnd: Random = Random): BidderKind {
            val total = entries.sumOf { it.weight }
            var r = rnd.nextDouble() * total
            for (e in entries) {
                r -= e.weight
                if (r <= 0) return e
            }
            return MERCHANT
        }
    }
}

/** 一次拍卖的结果。 */
class AuctionResult(
    /** 被拍卖的鱼在仓库里的下标（拍卖时锁定）。 */
    val index: Int,
    /** 全部买家的出价，已按高低排序。 */
    val bids: List<Bid>,
    /** 这条鱼的行情价（用于对比展示）。 */
    val marketPrice: Double,
) {
    /** 最高出价。 */
    val topBid: Bid? get() = bids.maxByOrNull { it.factor }

    /** 最高成交金额。 */
    val topAmount: Double get() = marketPrice * (topBid?.factor ?: 1.0)

    /** 相对行情价的溢价百分比。 */
    val premiumPct: Int get() = (((topBid?.factor ?: 1.0) - 1.0) * 100).toInt()
}

object Auction {

    /** 每次拍卖的买家数量。 */
    const val BIDDER_COUNT = 4

    /**
     * 拍卖一次的**手续费**比例：成交后抽成。
     * 这样"拍卖"不是纯白拿 —— 高溢价对应高抽成，玩家仍愿意用，
     * 但不会完全取代鱼贩的即时收购。
     */
    const val FEE_RATE = 0.05

    /**
     * 对一条鱼发起拍卖。
     *
     * 稀有度越高、体型越大，越容易引来高阶买家 —— 一条王者巨口鱼
     * 值得鉴定师出手，普通小鱼只有鱼贩愿意收。
     */
    fun runAuction(
        fish: StoredFish,
        marketPrice: Double,
        index: Int,
        rnd: Random = Random,
    ): AuctionResult {
        val species = Bestiary.speciesById(fish.speciesId)
        val rarityRank = species?.rarity?.ordinal ?: 0
        // 品质系数：稀有度 + 体型，决定"能不能招来好买家"
        val quality = rarityRank + fish.size.ordinal * 0.5

        val bids = mutableListOf<Bid>()
        repeat(BIDDER_COUNT) {
            var kind = BidderKind.roll(rnd)
            // 品质不够时，高阶买家不会出现（低概率硬塞一条好鱼会被玩家当 bug）
            if (kind == BidderKind.APPRAISER && quality < 3.0) kind = BidderKind.MERCHANT
            if (kind == BidderKind.COLLECTOR && quality < 1.5) kind = BidderKind.RESTAURANT

            // 品质加成：好鱼更容易被抬价
            val qualityBonus = (quality * 0.04).coerceAtMost(0.35)
            val base = kind.minFactor + rnd.nextDouble() * (kind.maxFactor - kind.minFactor)
            val factor = base + qualityBonus
            bids.add(Bid(bidderName(kind, rnd), factor, kind))
        }
        return AuctionResult(index, bids.sortedByDescending { it.factor }, marketPrice)
    }

    /** 结算一次拍卖：把鱼卖掉并扣除手续费。返回实际到手金额。 */
    fun settle(
        state: GameState,
        result: AuctionResult,
        now: Long,
    ): Double {
        val fish = state.warehouse.getOrNull(result.index) ?: return 0.0
        val gross = Warehouse.marketValue(fish, now) * (result.topBid?.factor ?: 1.0)
        val gain = gross * (1.0 - FEE_RATE)
        state.warehouse.removeAt(result.index)
        state.money += gain
        return gain
    }

    /** 给买家起个有辨识度的名字（让竞价看起来像"人"在抢）。 */
    private fun bidderName(kind: BidderKind, rnd: Random): String {
        val surnames = listOf("老张", "王老板", "李掌柜", "陈师傅", "周老板", "赵先生")
        val prefix = when (kind) {
            BidderKind.MERCHANT -> "鱼贩"
            BidderKind.RESTAURANT -> "酒楼"
            BidderKind.COLLECTOR -> "收藏家"
            BidderKind.APPRAISER -> "鉴定师"
        }
        return "$prefix·${surnames[rnd.nextInt(surnames.size)]}"
    }
}
