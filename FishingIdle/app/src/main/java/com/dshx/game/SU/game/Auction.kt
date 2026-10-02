package com.dshx.game.SU.game

import kotlin.math.pow
import kotlin.random.Random

/**
 * 拍卖行。
 *
 * 仓库原来只有「按行情价卖掉」，玩家没什么决策 —— 卖不卖都行，等于没有玩法。
 * 拍卖把同一条鱼交给**一屋子 AI 买家竞价**，而且**有过程**：买家逐个亮相、
 * 一轮轮加价，价格是现场长出来的，不是开局就摊在玩家脸上。
 *
 * 玩家要在「现在就落槌」与「再等一轮」之间做判断：
 * 等下去可能更高，但耐心耗尽的买家会退场 —— 最高价随时可能掉下来。
 * 这就是拍卖真正的博弈点。
 *
 * 成交价由两头共同决定，所以**波动极大**：
 *  - 鱼这一头：稀有度、体型、鱼种自身的价值倍率
 *  - 人这一头：玩家的转生声望、装备的渔获价值加成、当前角色的档次
 * 同一条鱼在不同账号上拍出的价可以差好几倍 —— 这是刻意的。
 */

/** 买家类型：各有出价风格、立绘与耐心。 */
enum class BidderKind(
    val displayName: String,
    /** 立绘素材名（`art/<sprite>.png`）。 */
    val sprite: String,
    /** 首次出价区间（相对行情价）。 */
    val openMin: Double,
    val openMax: Double,
    /** 每轮加价幅度区间（相对行情价）。 */
    val stepMin: Double,
    val stepMax: Double,
    /** 在场耐心：能参与几轮竞价。 */
    val patience: Int,
    /** 抽中权重。 */
    val weight: Double,
) {
    /** 鱼贩：稳妥，出价接近行情，耐心最好。 */
    MERCHANT("鱼贩", "bidder_merchant", 0.55, 0.80, 0.03, 0.09, 6, 40.0),

    /** 酒楼采购：量大，起价稍高，但没什么耐心。 */
    RESTAURANT("酒楼采购", "bidder_restaurant", 0.62, 0.92, 0.04, 0.12, 5, 28.0),

    /** 收藏家：对稀罕货出手大方，愿意等。 */
    COLLECTOR("收藏家", "bidder_collector", 0.78, 1.10, 0.06, 0.16, 6, 22.0),

    /** 鉴定师：只在鱼足够好时出现，出价最高，但最没耐心。 */
    APPRAISER("鉴定师", "bidder_appraiser", 0.95, 1.30, 0.09, 0.24, 4, 10.0),
    ;

    companion object {
        /** 按权重抽一个买家类型。[renown] 越大越容易招来高阶买家。 */
        fun roll(rnd: Random = Random, renown: Double = 0.0): BidderKind {
            val weights = entries.map { it.weight * (1.0 + renown * it.ordinal * 0.45) }
            val total = weights.sum()
            var r = rnd.nextDouble() * total
            for (i in entries.indices) {
                r -= weights[i]
                if (r <= 0) return entries[i]
            }
            return MERCHANT
        }
    }
}

/** 一个买家在本场拍卖里的状态。 */
class BidderState(
    val kind: BidderKind,
    /** 有辨识度的名字，让竞价看起来像"人"在抢。 */
    val name: String,
    /** 心理上限（倍率）：到这个价就不再跟了。 */
    val ceiling: Double,
    /** 首次出价（倍率）。 */
    val opening: Double,
    /** 还能在场几轮。 */
    var patience: Int,
) {
    /** 当前出价倍率；0 = 还没出过价。 */
    var bid: Double = 0.0

    /** 是否仍在场。退场后他此前的出价也一并作废。 */
    var inRoom: Boolean = true

    /** 是否已经喊到自己的心理上限。 */
    val atCeiling: Boolean get() = bid >= ceiling - 1e-9
}

/** 一轮竞价里发生的事，供 UI 播报。 */
sealed class BidEvent {
    /** 某个买家把价抬到了 [to]。 */
    class Raise(val bidder: BidderState, val to: Double) : BidEvent()

    /** 某个买家耐心耗尽、退出拍卖 —— 他的出价也随之作废。 */
    class Leave(val bidder: BidderState) : BidEvent()

    /** 本轮没人加价（大家都在观望或已到顶）。 */
    object Quiet : BidEvent()
}

/**
 * 一场拍卖的完整状态。
 *
 * 由 UI 按节拍调用 [advance] 推进：每一拍算一轮，返回这一轮发生了什么。
 * 玩家随时可以 [settle] 落槌 —— 拿当前最高价，但也就放弃了后面可能的加价。
 */
class AuctionSession(
    /** 被拍卖的鱼在仓库里的下标（成交时按它定位）。 */
    val index: Int,
    /** 行情价（不含任何溢价），用于对照展示。 */
    val marketPrice: Double,
    /** 品质：稀有度 + 体型 + 鱼种价值，决定能招来什么买家、能抬多高。 */
    val quality: Double,
    /** 玩家的"声望"：转生次数 + 装备渔获加成 + 角色档次，影响买家档次与上限。 */
    val renown: Double,
    val bidders: MutableList<BidderState>,
) {
    /** 已经推进了几轮。 */
    var round: Int = 0
        private set

    /**
     * 上一轮**开始前**各买家的出价。
     * UI 拿它和当前出价对比，把"刚抬价的那位"高亮出来 —— 这是过程感的关键。
     * 必须在 [advance] 里、改动任何出价之前快照，否则比出来永远相等。
     */
    var previousBids: Map<String, Double> = emptyMap()
        private set

    /** 本场已经播报过的事件，UI 可回看。 */
    val log: MutableList<BidEvent> = mutableListOf()

    /** 仍在场的买家。 */
    val activeBidders: List<BidderState> get() = bidders.filter { it.inRoom }

    /** 当前最高出价者；没人出价时为 null。 */
    val topBidder: BidderState? get() = activeBidders.maxByOrNull { it.bid }?.takeIf { it.bid > 0.0 }

    /** 当前最高价（倍率）。 */
    val topFactor: Double get() = topBidder?.bid ?: 0.0

    /** 当前成交金额（未扣手续费）。 */
    val topAmount: Double get() = marketPrice * topFactor

    /** 相对行情价的溢价百分比。 */
    val premiumPct: Int get() = ((topFactor - 1.0) * 100).toInt()

    /** 是否还能继续竞价。 */
    val canContinue: Boolean get() = round < MAX_ROUNDS && activeBidders.size >= 2

    /** 是否已经有可成交的价。 */
    val hasBid: Boolean get() = topBidder != null

    /**
     * 推进一轮竞价。
     *
     * 规则：还在场的买家各自决定是否加价；喊到心理上限的、或耐心耗尽的
     * 会在本轮结束时退场。**退场会带走他的出价** —— 所以最高价可能不升反降，
     * 这正是"再等一轮"的风险所在。
     */
    fun advance(rnd: Random = Random): List<BidEvent> {
        // 先快照本轮开始前的出价，UI 靠它判断"谁刚抬了价"
        previousBids = bidders.associate { it.name to it.bid }
        round++
        val events = mutableListOf<BidEvent>()

        for (b in activeBidders) {
            // 耐心耗尽：本轮不再加价，结束就退场
            if (b.patience <= 0) continue
            // 已经到顶：不再加价，消耗一点耐心
            if (b.atCeiling) {
                b.patience--
                continue
            }

            val next = if (b.bid <= 0.0) {
                b.opening
            } else {
                b.bid + b.kind.stepMin + rnd.nextDouble() * (b.kind.stepMax - b.kind.stepMin)
            }
            val capped = next.coerceAtMost(b.ceiling)
            if (capped > b.bid + 1e-9) {
                b.bid = capped
                events.add(BidEvent.Raise(b, capped))
            } else {
                b.patience--
            }
        }

        // 耐心归零的买家退场（出价一并作废）
        for (b in bidders) {
            if (!b.inRoom) continue
            if (b.patience <= 0) {
                b.inRoom = false
                events.add(BidEvent.Leave(b))
            }
        }

        if (events.isEmpty()) events.add(BidEvent.Quiet)
        log += events
        return events
    }

    /** 落槌：把鱼卖掉并扣除手续费。返回实际到手金额。 */
    fun settle(state: GameState): Double {
        val fish = state.warehouse.getOrNull(index) ?: return 0.0
        val gain = Warehouse.marketValue(fish, System.currentTimeMillis()) * topFactor *
            (1.0 - FEE_RATE)
        state.warehouse.removeAt(index)
        state.money += gain
        return gain
    }

    companion object {
        /** 一场拍卖最多几轮，防止买家互相加价停不下来。 */
        const val MAX_ROUNDS = 9

        /** 成交手续费比例。 */
        const val FEE_RATE = 0.05

        /** 同时进场的买家数量。 */
        const val BIDDER_COUNT = 4

        /**
         * 为一条鱼开一场拍卖。
         *
         * [renown] 是玩家的"声望"（转生 + 装备 + 角色），它同时抬高
         * 买家档次与心理上限 —— 同一个账号越到后期，同一条鱼拍得越贵。
         */
        fun open(
            fish: StoredFish,
            marketPrice: Double,
            index: Int,
            renown: Double,
            rnd: Random = Random,
        ): AuctionSession {
            val species = Bestiary.speciesById(fish.speciesId)
            val rarityRank = (species?.rarity?.ordinal ?: 0).toDouble()
            // 品质 = 稀有度 + 体型 + 鱼种自身价值（对数压缩，避免个别鱼种碾压一切）
            val speciesBoost = species?.let { ln1p(it.valueMul) * 0.35 } ?: 0.0
            val quality = rarityRank + fish.size.ordinal * 0.5 + speciesBoost

            // 稀有鱼才招得来高阶买家：低品质硬塞一条好鱼会被玩家当 bug
            val canAppraise = quality >= 3.0
            val canCollect = quality >= 1.5

            val bidders = mutableListOf<BidderState>()
            repeat(BIDDER_COUNT) {
                var kind = BidderKind.roll(rnd, renown)
                if (kind == BidderKind.APPRAISER && !canAppraise) kind = BidderKind.MERCHANT
                if (kind == BidderKind.COLLECTOR && !canCollect) kind = BidderKind.RESTAURANT

                // 起价：买家风格 + 品质加成（好鱼自己会说话）
                val qualityBonus = (quality * 0.045).coerceAtMost(0.40)
                val opening = kind.openMin + rnd.nextDouble() * (kind.openMax - kind.openMin) + qualityBonus
                // 心理上限：起价之上再留一段空间，声望越高留得越多。
                // 这一步是"价格幅度非常大"的来源：
                // 常见鱼大约落在 1.0~1.7 倍行情价，传说+王者+高声望可冲到 3 倍以上。
                val headroom = (0.25 + rnd.nextDouble() * 0.60) *
                    (1.0 + quality * 0.10) * (1.0 + renown * 0.22)
                val ceiling = opening + headroom
                bidders.add(
                    BidderState(
                        kind = kind,
                        name = bidderName(kind, rnd),
                        ceiling = ceiling,
                        opening = opening,
                        patience = kind.patience + if (canAppraise) 1 else 0,
                    ),
                )
            }
            return AuctionSession(index, marketPrice, quality, renown, bidders)
        }

        private fun ln1p(x: Double): Double = kotlin.math.ln(1.0 + x.coerceAtLeast(0.0))

        /** 给买家起个有辨识度的名字（让竞价看起来像"人"在抢）。 */
        private fun bidderName(kind: BidderKind, rnd: Random): String {
            val surnames = listOf("老张", "王老板", "李掌柜", "陈师傅", "周老板", "赵先生")
            return "${kind.displayName}·${surnames[rnd.nextInt(surnames.size)]}"
        }
    }
}

// ---------------------------------------------------------------------------
// 兼容层：旧的"一次算出结果"接口。
//
// 新玩法是逐轮竞价（AuctionSession），但存档/测试仍依赖这组纯函数接口，
// 保留它们可以让"一次拍卖的数学"独立于 UI 被验证。
// ---------------------------------------------------------------------------

/** 一个买家的出价。 */
class Bid(val bidder: String, val factor: Double, val kind: BidderKind)

/** 一次拍卖的结果（兼容层用）。 */
class AuctionResult(
    val index: Int,
    val bids: List<Bid>,
    val marketPrice: Double,
) {
    val topBid: Bid? get() = bids.maxByOrNull { it.factor }
    val topAmount: Double get() = marketPrice * (topBid?.factor ?: 1.0)
    val premiumPct: Int get() = (((topBid?.factor ?: 1.0) - 1.0) * 100).toInt()
}

object Auction {
    const val BIDDER_COUNT = AuctionSession.BIDDER_COUNT
    const val FEE_RATE = AuctionSession.FEE_RATE

    /**
     * 一次性跑完一场拍卖（不经过逐轮过程），取最高出价。
     * 新 UI 走 [AuctionSession]；这里保留给纯逻辑校验与存档兼容。
     */
    fun runAuction(
        fish: StoredFish,
        marketPrice: Double,
        index: Int,
        rnd: Random = Random,
    ): AuctionResult {
        val session = AuctionSession.open(fish, marketPrice, index, renown = 0.0, rnd = rnd)
        repeat(AuctionSession.MAX_ROUNDS) { if (session.canContinue) session.advance(rnd) }
        val bids = session.bidders.map { Bid(it.name, maxOf(it.bid, it.opening), it.kind) }
        return AuctionResult(index, bids.sortedByDescending { it.factor }, marketPrice)
    }

    /** 结算一次拍卖（兼容层）。 */
    fun settle(state: GameState, result: AuctionResult, now: Long): Double {
        val fish = state.warehouse.getOrNull(result.index) ?: return 0.0
        val gross = Warehouse.marketValue(fish, now) * (result.topBid?.factor ?: 1.0)
        val gain = gross * (1.0 - FEE_RATE)
        state.warehouse.removeAt(result.index)
        state.money += gain
        return gain
    }
}
