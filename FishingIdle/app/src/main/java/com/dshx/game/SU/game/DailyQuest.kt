package com.dshx.game.SU.game

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

    // ---- 新增：陪伴型任务的进度 ----
    /** 今日抛竿次数。 */
    var casts: Int = 0
    /** 今日任意购买次数（商店买任何东西都算）。 */
    var anyPurchase: Int = 0
    /** 今日新解锁的鱼种数。 */
    var newSpecies: Int = 0
    /** 今日存入仓库的鱼数。 */
    var stored: Int = 0
    /** 今日卖出的鱼数。 */
    var sold: Int = 0

    fun reset() {
        catches = 0
        moneyEarned = 0.0
        bestCombo = 0
        rareCatches = 0
        mapChanges = 0
        helpersBought = 0
        chests = 0
        kings = 0
        casts = 0
        anyPurchase = 0
        newSpecies = 0
        stored = 0
        sold = 0
    }
}

/** 任务池与结算。 */
object DailyQuests {

    /**
     * 全部任务模板，每天从中抽 3 条。
     *
     * **难度校准**（这是重点）：
     *  - 所有任务都要保证**半小时到一小时**能完成，不看玩家进度。
     *  - 老版本有「切换 2 次水域」「雇佣 3 名钓手」这种前期根本做不到的，
     *    以及「今日赚 1000 万」这种只有后期才够得着的 —— 全部删掉或大幅下调。
     *  - 奖励也相应下调：任务给的是**阶段性小奖励**，不该顶掉一整天的经营收入。
     *  - 目标是"每天上线随便玩玩就能拿满"，而不是"逼玩家肝"。
     */
    val pool: List<DailyQuest> = listOf(
        // ---- 钓鱼数量：最直观，任何阶段都能做 ----
        DailyQuest("catch20", "小试身手", "钓上 20 条鱼", 20.0, 3_000.0) { it.catches.toDouble() },
        DailyQuest("catch60", "渐入佳境", "钓上 60 条鱼", 60.0, 12_000.0) { it.catches.toDouble() },
        DailyQuest("catch120", "勤学苦练", "钓上 120 条鱼", 120.0, 30_000.0) { it.catches.toDouble() },

        // ---- 稀有鱼：不再要求 15 条那么高 ----
        DailyQuest("rare3", "寻珍觅宝", "钓上 3 条稀有以上的鱼", 3.0, 15_000.0) { it.rareCatches.toDouble() },
        DailyQuest("rare8", "慧眼识珠", "钓上 8 条稀有以上的鱼", 8.0, 45_000.0) { it.rareCatches.toDouble() },

        // ---- 连击：手速类，几分钟就能达成 ----
        DailyQuest("combo8", "一气呵成", "达成 8 连击", 8.0, 8_000.0) { it.bestCombo.toDouble() },
        DailyQuest("combo18", "炉火纯青", "达成 18 连击", 18.0, 28_000.0) { it.bestCombo.toDouble() },

        // ---- 收入：按"当天能赚多少"给，不再动辄千万 ----
        DailyQuest("earn_s", "小有积蓄", "今日赚到 2 万金币", 20_000.0, 6_000.0) { it.moneyEarned },
        DailyQuest("earn_m", "日进斗金", "今日赚到 20 万金币", 200_000.0, 25_000.0) { it.moneyEarned },
        DailyQuest("earn_l", "财源广进", "今日赚到 200 万金币", 2_000_000.0, 80_000.0) { it.moneyEarned },

        // ---- 日常操作类：随手就能完成的"陪伴型"任务 ----
        DailyQuest("cast_any", "抛竿不辍", "抛竿 15 次", 15.0, 5_000.0) { it.casts.toDouble() },
        DailyQuest("visit_map", "四处探索", "切换 1 次水域", 1.0, 6_000.0) { it.mapChanges.toDouble() },
        DailyQuest("buy_any", "添置家当", "在商店购买 1 次", 1.0, 8_000.0) { it.anyPurchase.toDouble() },

        // ---- 后期玩法的每日目标：挂在进度字段上，没买这些功能时天然是 0，不会误导 ----
        DailyQuest("chest1", "海底捞金", "今日打开 1 个沉船宝箱", 1.0, 40_000.0) { it.chests.toDouble() },
        DailyQuest("king1", "王见王", "今日拽上 1 条鱼王", 1.0, 90_000.0) { it.kings.toDouble() },
        DailyQuest("dex1", "图鉴新页", "今日解锁 1 个新鱼种", 1.0, 60_000.0) { it.newSpecies.toDouble() },
        DailyQuest("store1", "入库收藏", "今日有 1 条鱼存入仓库", 1.0, 25_000.0) { it.stored.toDouble() },
        DailyQuest("sell1", "做笔买卖", "今日卖出 1 条仓库里的鱼", 1.0, 25_000.0) { it.sold.toDouble() },
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
        state.dailyProgress.anyPurchase++
    }

    /** 任何商店购买都记一笔（「添置家当」任务）。 */
    fun onAnyPurchase(state: GameState) {
        state.dailyProgress.anyPurchase++
    }

    fun onMapChanged(state: GameState) {
        state.dailyProgress.mapChanges++
    }

    /** 抛竿一次。 */
    fun onCast(state: GameState) {
        state.dailyProgress.casts++
    }

    /** 首次钓到某鱼种。 */
    fun onNewSpecies(state: GameState) {
        state.dailyProgress.newSpecies++
    }

    /** 有鱼存入仓库。 */
    fun onStored(state: GameState) {
        state.dailyProgress.stored++
    }

    /** 卖出一条仓库里的鱼。 */
    fun onSold(state: GameState, count: Int = 1) {
        state.dailyProgress.sold += count
    }
}
