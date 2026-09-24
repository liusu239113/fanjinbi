package com.taptap.fishingidle.game

import kotlin.math.max

/**
 * 每日任务。
 *
 * 放置游戏只有"数值一直涨"容易腻，每日任务提供**短期目标**：
 * 每天刷 3 条，完成后给金币奖励，次日 0 点重置。
 *
 * 进度用"当日增量"统计，不依赖绝对累计值 —— 否则老玩家一上线
 * 任务就全部自动完成了。
 */
class DailyQuest(
    val id: String,
    val name: String,
    val desc: String,
    val goal: Double,
    val reward: Double,
    /** 该任务追踪的当日增量。 */
    val track: (DailyProgress) -> Double,
)

/** 当日进度统计，每天重置。 */
class DailyProgress {
    var catches: Long = 0
    var moneyEarned: Double = 0.0
    var bestCombo: Int = 0
    var rareCatches: Long = 0
    var mapChanges: Int = 0
    var helpersBought: Int = 0

    /** 后期玩法：今日开箱数、今日拽上来的鱼王数。 */
    var chests: Int = 0
    var kings: Int = 0

    fun reset() {
        catches = 0
        moneyEarned = 0.0
        bestCombo = 0
        rareCatches = 0
        mapChanges = 0
        helpersBought = 0
        chests = 0
        kings = 0
    }
}

/** 任务池与结算。 */
object DailyQuests {

    /** 全部任务模板，每天从中抽 3 条。 */
    val pool: List<DailyQuest> = listOf(
        DailyQuest("catch30", "小试身手", "钓上 30 条鱼", 30.0, 5_000.0) { it.catches.toDouble() },
        DailyQuest("catch150", "勤学苦练", "钓上 150 条鱼", 150.0, 40_000.0) { it.catches.toDouble() },
        DailyQuest("catch500", "废寝忘食", "钓上 500 条鱼", 500.0, 300_000.0) { it.catches.toDouble() },
        DailyQuest("rare15", "寻珍觅宝", "钓上 15 条稀有以上的鱼", 15.0, 80_000.0) { it.rareCatches.toDouble() },
        DailyQuest("combo15", "一气呵成", "达成 15 连击", 15.0, 25_000.0) { it.bestCombo.toDouble() },
        DailyQuest("combo30", "炉火纯青", "达成 30 连击", 30.0, 150_000.0) { it.bestCombo.toDouble() },
        DailyQuest("earn", "小有积蓄", "今日赚到 10 万金币", 100_000.0, 20_000.0) { it.moneyEarned },
        DailyQuest("earn_big", "日进斗金", "今日赚到 1000 万金币", 1e7, 500_000.0) { it.moneyEarned },
        DailyQuest("hire", "招兵买马", "今日雇佣 3 名钓手", 3.0, 60_000.0) { it.helpersBought.toDouble() },
        DailyQuest("explore", "四处探索", "切换 2 次水域", 2.0, 30_000.0) { it.mapChanges.toDouble() },
        // 后期玩法的每日目标：挂在进度字段上，没买这些功能时天然是 0，不会误导
        DailyQuest("chest2", "海底捞金", "今日打开 2 个沉船宝箱", 2.0, 120_000.0) { it.chests.toDouble() },
        DailyQuest("king1", "王见王", "今日拽上 1 条鱼王", 1.0, 300_000.0) { it.kings.toDouble() },
    )

    const val DAILY_COUNT = 3

    /** 按日期种子抽当天的任务，保证同一天进出游戏看到的是同一批。 */
    fun forDay(dayIndex: Long): List<DailyQuest> {
        val rnd = kotlin.random.Random(dayIndex * 7919L)
        return pool.shuffled(rnd).take(DAILY_COUNT)
    }

    /** 当天是否已完成。 */
    fun isDone(state: GameState, quest: DailyQuest): Boolean =
        state.dailyDone.contains(quest.id)

    /** 任务进度 0..1。 */
    fun progress(state: GameState, quest: DailyQuest): Float =
        (quest.track(state.dailyProgress) / quest.goal).coerceIn(0.0, 1.0).toFloat()

    /**
     * 结算所有可领取的任务，返回新完成的任务。
     * 奖励直接入账。
     */
    fun claimCompleted(state: GameState): List<DailyQuest> {
        val today = forDay(state.dailyDayIndex)
        val newly = mutableListOf<DailyQuest>()
        for (q in today) {
            if (state.dailyDone.contains(q.id)) continue
            if (q.track(state.dailyProgress) >= q.goal) {
                state.dailyDone.add(q.id)
                state.money += q.reward
                newly.add(q)
            }
        }
        return newly
    }

    /** 到新的一天就重置进度与完成记录。 */
    fun rolloverIfNeeded(state: GameState) {
        val today = currentDayIndex()
        if (state.dailyDayIndex != today) {
            state.dailyDayIndex = today
            state.dailyDone.clear()
            state.dailyProgress.reset()
        }
    }

    /** 当前天序号（UTC 天数）。 */
    fun currentDayIndex(): Long = System.currentTimeMillis() / 86_400_000L
}

/** 统计钩子：由 World / 商店在相应事件里调用。 */
object DailyTracker {
    fun onCatch(state: GameState, rarity: Rarity, value: Double) {
        state.dailyProgress.catches++
        state.dailyProgress.moneyEarned += value
        if (rarity != Rarity.COMMON) state.dailyProgress.rareCatches++
    }

    fun onCombo(state: GameState) {
        state.dailyProgress.bestCombo = max(state.dailyProgress.bestCombo, state.combo)
    }

    fun onHelperBought(state: GameState) {
        state.dailyProgress.helpersBought++
    }

    fun onMapChanged(state: GameState) {
        state.dailyProgress.mapChanges++
    }
}
