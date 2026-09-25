package com.dshx.game.SU

import com.dshx.game.SU.game.Bestiary
import com.dshx.game.SU.game.DailyQuests
import com.dshx.game.SU.game.DexReward
import com.dshx.game.SU.game.GameState
import com.dshx.game.SU.game.OfflineEarnings
import com.dshx.game.SU.game.Rarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 长线系统的测试：图鉴收集奖励、离线收益、每日任务。
 * 这三块是"能撑几小时"的关键，数值一旦出错很难在游戏里发现。
 */
class DepthSystemsTest {

    // ---------------- 图鉴收集奖励 ----------------

    @Test
    fun `未收集任何鱼时图鉴倍率为1`() {
        val s = GameState()
        assertEquals(1.0, DexReward.multiplier(s), 0.001)
    }

    @Test
    fun `每收集一种鱼提升全局倍率`() {
        val s = GameState()
        val before = s.catchValue(Rarity.COMMON)

        repeat(10) { s.recordSpecies("test_$it") }

        val after = s.catchValue(Rarity.COMMON)
        // 10 种 × 1.5% = 1.15×
        assertEquals(1.15, after / before, 0.001)
    }

    @Test
    fun `重复收集同一种鱼不重复加成`() {
        val s = GameState()
        repeat(5) { s.recordSpecies("same") }
        assertEquals(1, s.caughtSpecies.size)
        assertEquals(1.0 + DexReward.PER_SPECIES_BONUS, DexReward.multiplier(s), 0.001)
    }

    @Test
    fun `集齐一张地图额外给加成`() {
        val s = GameState()
        val map = Bestiary.VILLAGE_CREEK
        map.species.forEach { s.recordSpecies(it.id) }

        assertTrue(DexReward.isMapComplete(s, map))
        assertEquals(1, DexReward.completedMaps(s))

        val expected = 1.0 +
            map.species.size * DexReward.PER_SPECIES_BONUS +
            DexReward.PER_MAP_COMPLETE_BONUS
        assertEquals(expected, DexReward.multiplier(s), 0.001)
    }

    @Test
    fun `收集满全部鱼种时倍率可观`() {
        val s = GameState()
        Bestiary.allSpecies.forEach { s.recordSpecies(it.id) }

        val mul = DexReward.multiplier(s)
        val expected = 1.0 +
            Bestiary.totalSpecies * DexReward.PER_SPECIES_BONUS +
            Bestiary.maps.size * DexReward.PER_MAP_COMPLETE_BONUS +
            DexReward.ALL_COMPLETE_BONUS
        assertEquals(expected, mul, 0.001)
        assertTrue("全收集应有明显提升，实际 $mul", mul > 2.5)
    }

    @Test
    fun `图鉴加成在转生后依然保留`() {
        val s = GameState()
        s.money = 1e12
        Bestiary.VILLAGE_CREEK.species.forEach { s.recordSpecies(it.id) }
        val before = DexReward.multiplier(s)

        s.doPrestige()

        assertEquals("转生不应清空图鉴", before, DexReward.multiplier(s), 0.001)
    }

    @Test
    fun `首次记录返回true重复返回false`() {
        val s = GameState()
        assertTrue(s.recordSpecies("abc"))
        assertFalse(s.recordSpecies("abc"))
    }

    // ---------------- 离线收益 ----------------

    @Test
    fun `没有钓手就没有离线收益`() {
        val r = OfflineEarnings.settle(3600, 10.0, 0)
        assertEquals(0.0, r.money, 0.001)
        assertFalse(r.isMeaningful)
    }

    @Test
    fun `离线时间过短不弹结算`() {
        val r = OfflineEarnings.settle(30, 10.0, 5)
        assertFalse("30 秒不应弹窗", r.isMeaningful)
    }

    @Test
    fun `离线收益按折扣效率结算`() {
        val r = OfflineEarnings.settle(3600, 10.0, 2)
        // 3600 秒 × 0.55 离线效率 × 0.65 基础速度 × 10/秒 × 2 钓手
        assertEquals(3600 * OfflineEarnings.EFFICIENCY * GameState.BASE_GAME_SPEED * 10.0 * 2, r.money, 1.0)
        assertTrue(r.isMeaningful)
    }

    @Test
    fun `离线收益有上限不会无限累积`() {
        val oneDay = OfflineEarnings.settle(86_400, 10.0, 1)
        val maxed = OfflineEarnings.settle((OfflineEarnings.MAX_HOURS * 3600).toLong(), 10.0, 1)
        assertEquals("超过上限后收益应封顶", maxed.money, oneDay.money, 1.0)
    }

    @Test
    fun `钓手越多离线收益越高`() {
        val few = OfflineEarnings.settle(7200, 10.0, 1)
        val many = OfflineEarnings.settle(7200, 10.0, 10)
        assertEquals(10.0, many.money / few.money, 0.01)
    }

    @Test
    fun `每秒收益估算在合理范围`() {
        val s = GameState()
        val perSec = OfflineEarnings.perHelperPerSecond(s)
        // 开局常见鱼价值 1，平均 2.5 秒一条 → 约 0.4/秒
        assertTrue("开局每秒收益应 > 0，实际 $perSec", perSec > 0.0)
        assertTrue("开局每秒收益不应过高，实际 $perSec", perSec < 5.0)
    }

    // ---------------- 每日任务 ----------------

    @Test
    fun `同一天抽到的任务固定不变`() {
        val a = DailyQuests.forDay(100L).map { it.id }
        val b = DailyQuests.forDay(100L).map { it.id }
        assertEquals(a, b)
    }

    @Test
    fun `不同天抽到的任务会变化`() {
        val ids = (1L..30L).map { DailyQuests.forDay(it).map { q -> q.id } }
        assertTrue("30 天里应出现多种组合", ids.distinct().size > 1)
    }

    @Test
    fun `每天固定三条任务且不重复`() {
        for (day in 1L..50L) {
            val q = DailyQuests.forDay(day)
            assertEquals(DailyQuests.DAILY_COUNT, q.size)
            assertEquals("同一天不应重复", q.size, q.map { it.id }.distinct().size)
        }
    }

    @Test
    fun `达到目标后自动结算并发奖`() {
        val s = GameState()
        // 找到今天的"钓上 30 条鱼"类任务，直接把进度打满
        // 把所有进度字段都打满：任务池里任何一条抽中都能完成
        s.dailyProgress.catches = 10_000
        s.dailyProgress.moneyEarned = 1e12
        s.dailyProgress.bestCombo = 999
        s.dailyProgress.rareCatches = 999
        s.dailyProgress.helpersBought = 99
        s.dailyProgress.mapChanges = 99
        s.dailyProgress.chests = 99
        s.dailyProgress.kings = 99
        s.dailyProgress.casts = 999
        s.dailyProgress.anyPurchase = 99
        s.dailyProgress.newSpecies = 99
        s.dailyProgress.stored = 99
        s.dailyProgress.sold = 99

        val moneyBefore = s.money
        val done = DailyQuests.claimCompleted(s)

        assertEquals(DailyQuests.DAILY_COUNT, done.size)
        assertTrue("应发放奖励", s.money > moneyBefore)
        assertEquals(DailyQuests.DAILY_COUNT, s.dailyDone.size)
    }

    @Test
    fun `已完成的任务不会重复发奖`() {
        val s = GameState()
        // 把所有进度字段都打满：任务池里任何一条抽中都能完成
        s.dailyProgress.catches = 10_000
        s.dailyProgress.moneyEarned = 1e12
        s.dailyProgress.bestCombo = 999
        s.dailyProgress.rareCatches = 999
        s.dailyProgress.helpersBought = 99
        s.dailyProgress.mapChanges = 99
        s.dailyProgress.chests = 99
        s.dailyProgress.kings = 99
        s.dailyProgress.casts = 999
        s.dailyProgress.anyPurchase = 99
        s.dailyProgress.newSpecies = 99
        s.dailyProgress.stored = 99
        s.dailyProgress.sold = 99

        DailyQuests.claimCompleted(s)
        val afterFirst = s.money
        val second = DailyQuests.claimCompleted(s)

        assertTrue("第二次不应再发奖", second.isEmpty())
        assertEquals(afterFirst, s.money, 0.001)
    }

    @Test
    fun `跨天后进度重置`() {
        val s = GameState()
        s.dailyProgress.catches = 500
        s.dailyDone.add("catch30")
        s.dailyDayIndex = 1L   // 一个很旧的日子

        DailyQuests.rolloverIfNeeded(s)

        assertEquals(0L, s.dailyProgress.catches)
        assertTrue(s.dailyDone.isEmpty())
        assertEquals(DailyQuests.currentDayIndex(), s.dailyDayIndex)
    }

    @Test
    fun `任务进度在0到1之间`() {
        val s = GameState()
        val q = DailyQuests.forDay(s.dailyDayIndex).first()
        assertEquals(0f, DailyQuests.progress(s, q), 0.001f)

        s.dailyProgress.catches = 999_999
        s.dailyProgress.moneyEarned = 1e15
        s.dailyProgress.bestCombo = 9999
        s.dailyProgress.rareCatches = 9999
        s.dailyProgress.helpersBought = 999
        s.dailyProgress.mapChanges = 999

        assertTrue(DailyQuests.progress(s, q) in 0f..1f)
    }

    @Test
    fun `每日任务统计会被渔获钩子驱动`() {
        val s = GameState()
        assertTrue(s.dailyProgress.catches == 0L)

        s.onCatchRecorded(Rarity.COMMON, 100.0)
        assertEquals(1L, s.dailyProgress.catches)
        assertEquals(100.0, s.dailyProgress.moneyEarned, 0.001)

        s.onCatchRecorded(Rarity.EPIC, 50.0)
        assertEquals(2L, s.dailyProgress.catches)
        assertEquals("稀有鱼应单独计数", 1L, s.dailyProgress.rareCatches)
    }
}
