package com.dshx.game.SU.game

/**
 * 离线收益结算。
 *
 * 放置游戏的标配：离开期间自动钓手仍在替你钓鱼。
 * 收益速率 = 当前钓手每秒的期望产出 × 一个保守折扣，
 * 并设上限时长，避免挂机一夜直接毕业。
 */
object OfflineEarnings {

    /** 最多累计 8 小时。 */
    const val MAX_HOURS = 8.0

    /** 离线效率折扣：只按在线效率的 55% 结算，鼓励在线玩。 */
    const val EFFICIENCY = 0.55

    /** 每秒收益低于这个值就不值得弹结算窗。 */
    private const val MIN_MEANINGFUL = 1.0

    class Result(
        val seconds: Long,
        val money: Double,
        val catches: Int,
    ) {
        val isMeaningful: Boolean get() = money >= MIN_MEANINGFUL && seconds >= 60
    }

    /**
     * 估算离线期间的收益。
     *
     * @param elapsedSeconds 距上次离开的真实秒数
     * @param perHelperPerSecond 单个钓手每秒的期望产出（由调用方按当前地图算）
     * @param helperCount 钓手数量
     */
    fun settle(
        elapsedSeconds: Long,
        perHelperPerSecond: Double,
        helperCount: Int,
    ): Result {
        if (elapsedSeconds <= 0 || helperCount <= 0 || perHelperPerSecond <= 0.0) {
            return Result(elapsedSeconds, 0.0, 0)
        }
        val capped = elapsedSeconds.coerceAtMost((MAX_HOURS * 3600).toLong())
        val effective = capped * EFFICIENCY
        val money = perHelperPerSecond * helperCount * effective
        // 每个钓手平均 2.5 秒一条鱼，用来估算"钓到多少条"
        val catches = (effective / 2.5 * helperCount).toInt()
        return Result(elapsedSeconds, money, catches)
    }

    /**
     * 单个钓手每秒的期望产出。
     *
     * 关键：只能算**钓手当前真的能钓到**的鱼。
     * 开局钓手只会钓常见鱼（helperCanRare/Epic/Legend 都是 false），
     * 若把传说鱼也算进期望，会得出 19 金币/秒 这种离谱数字，
     * 离线收益直接爆炸。
     */
    fun perHelperPerSecond(state: GameState): Double {
        val map = state.currentMap
        val reachable = map.species.filter { state.helperCanCatch(it.rarity) }
        if (reachable.isEmpty()) return 0.0

        val totalWeight = reachable.sumOf { it.rarity.weight.toDouble() }
        if (totalWeight <= 0.0) return 0.0

        val expectedValue = reachable.sumOf { sp ->
            state.catchValue(sp, map) * (sp.rarity.weight / totalWeight)
        }

        // 钓手效率同时受「钓手训练」升级与技能树影响
        val efficiency = (state.helperEfficiency * state.skillHelperMultiplier)
            .coerceAtLeast(0.1)
        // 平均 2.5 秒一条鱼，效率越高越快
        return expectedValue * efficiency / 2.5
    }
}
