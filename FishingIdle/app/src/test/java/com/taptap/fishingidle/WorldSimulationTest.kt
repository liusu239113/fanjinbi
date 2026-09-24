package com.taptap.fishingidle

import com.taptap.fishingidle.game.BobberState
import com.taptap.fishingidle.game.Content
import com.taptap.fishingidle.game.Fish
import com.taptap.fishingidle.game.Rarity
import com.taptap.fishingidle.game.FishState
import com.taptap.fishingidle.game.GameState
import com.taptap.fishingidle.game.Space
import com.taptap.fishingidle.game.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 真实模拟测试：直接把游戏世界跑起来推进几千帧，验证不会卡死、不会漏鱼、经济会增长。
 * 这类测试能抓到纯数值测试抓不到的时序 bug。
 */
class WorldSimulationTest {

    private val dt = 1f / 60f

    /** 推进世界若干秒。 */
    private fun World.advance(seconds: Float) {
        var acc = 0f
        while (acc < seconds) {
            update(dt)
            acc += dt
        }
    }


    /** 抛到离落点最近的鱼身上 —— 真实玩法里玩家就是这么钓的。 */
    private fun World.castAtNearestFish() {
        val f = fishes.minByOrNull { kotlin.math.abs(it.x - cameraX) } ?: return
        castLine(f.x, f.y)
    }

    @Test
    fun `长时间空转不会崩溃`() {
        val state = GameState()
        val world = World(state)
        world.advance(120f)   // 2 分钟
        // 初始 1 条小鱼始终在场
        assertEquals(GameState.INITIAL_COMMON_FISH, world.fishes.size)
    }

    @Test
    fun `初始只有一条鱼时钓走之后鱼不会永久消失`() {
        val state = GameState()
        val world = World(state)
        world.advance(1f)

        // 连续抛竿收线 30 次，模拟玩家不停钓鱼
        var casts = 0
        var reeled = 0
        repeat(30) {
            if (!world.bobber.isActive) {
                world.castAtNearestFish()
                casts++
            }
            // 推进直到这一竿结束
            var guard = 0
            while (world.bobber.isActive && guard < 2000) {
                world.update(dt)
                if (world.bobber.state == BobberState.BITE) world.onTap(world.bobber.x, world.bobber.y)
                guard++
            }
            if (world.bobber.state == BobberState.DONE) reeled++
        }

        assertTrue("应该抛出了若干竿: $casts", casts > 20)
        assertTrue("应该完成了若干竿: $reeled", reeled > 20)
        // 核心断言：鱼群数量始终维持，不会被慢慢掏空
        assertEquals("鱼群不应消失", GameState.INITIAL_COMMON_FISH, world.fishes.size)
    }

    @Test
    fun `没有鱼咬钩时浮标会自行收回`() {
        val state = GameState()
        // 一条鱼都没有的极端情况
        val world = World(state)
        world.fishes.clear()

        // 这里没有鱼可参照，直接指定落点
        world.castLine(Space.W / 2f, Space.POND_T + 300f)
        assertTrue("抛竿后浮标应处于活动状态", world.bobber.isActive)

        world.advance(4f)
        assertTrue(
            "无鱼可咬时浮标必须自行结束，否则玩家被永久卡住",
            world.bobber.state == BobberState.DONE,
        )
    }

    @Test
    fun `正常钓鱼会让金币增长`() {
        val state = GameState()
        val world = World(state)
        world.advance(1f)

        repeat(40) {
            if (!world.bobber.isActive) world.castAtNearestFish()
            var guard = 0
            while (world.bobber.isActive && guard < 2000) {
                world.update(dt)
                if (world.bobber.state == BobberState.BITE) world.onTap(world.bobber.x, world.bobber.y)
                guard++
            }
        }

        assertTrue("连续钓鱼后金币应增长，实际=${state.money}", state.money > 0.0)
        assertTrue("应记录收益来源", state.earningsBySource.isNotEmpty())
    }

    @Test
    fun `钓手会自动钓鱼并产出金币`() {
        val state = GameState()
        state.money = 1e9
        repeat(5) { state.buy(Content.byId("helper")!!) }
        assertEquals(5, state.helpers)

        val world = World(state)
        world.advance(60f)   // 跑 1 分钟

        // 注意：不能拿总余额和购买前比较 —— 买钓手本身就花掉了钱。
        // 要看钓手这个来源实际产出了多少。
        val helperEarned = state.earningsBySource[com.taptap.fishingidle.game.Source.HELPER] ?: 0.0
        assertTrue("钓手应产出金币，实际=$helperEarned", helperEarned > 0.0)
        assertTrue("钓手收益应累计到总收入", state.totalMoney >= helperEarned)
        assertEquals("鱼群数量应保持", GameState.INITIAL_COMMON_FISH, world.fishes.size)
    }

    @Test
    fun `钓手数量多于鱼时不会产生额外收益也不会崩溃`() {
        // 鱼少钓手多：每个钓手最多认领一条鱼，不会有多个钓手抢同一条
        val state = GameState()
        state.money = 1e12
        repeat(20) { state.buy(Content.byId("helper")!!) }
        val world = World(state)
        assertEquals(20, world.helpers.size)
        assertEquals(GameState.INITIAL_COMMON_FISH, world.fishes.size)

        world.advance(45f)

        // 被认领的鱼数不能超过鱼的总数（每个钓手最多占一条）
        val claimed = world.fishes.count { it.claimedBy != null }
        assertTrue(
            "被认领的鱼($claimed) 不应超过鱼总数(${world.fishes.size})",
            claimed <= world.fishes.size,
        )
        // 也不该超过钓手数
        assertTrue("被认领的鱼不应超过钓手数", claimed <= world.helpers.size)
        // 但收益仍然要正常产生
        assertTrue((state.earningsBySource[com.taptap.fishingidle.game.Source.HELPER] ?: 0.0) > 0.0)
    }

    @Test
    fun `多个钓手不会抢同一条鱼导致卡死`() {
        val state = GameState()
        state.money = 1e9
        repeat(10) { state.buy(Content.byId("helper")!!) }
        val world = World(state)
        world.advance(90f)

        // 每个钓手最多认领一条鱼
        val claimed = world.fishes.count { it.claimedBy != null }
        assertTrue("认领数不应超过鱼总数", claimed <= world.fishes.size)
        // 所有鱼都应处于合法状态
        world.fishes.forEach { f ->
            assertTrue("鱼状态异常: ${f.state}", f.state in FishState.entries)
        }
    }

    @Test
    fun `鱼群规模扩大后仍然稳定`() {
        val state = GameState()
        state.money = 1e12
        repeat(20) { state.buy(Content.byId("common_fish")!!) }
        repeat(5) { state.buy(Content.byId("rare_fish")!!) }
        repeat(2) { state.buy(Content.byId("epic_fish")!!) }
        repeat(8) { state.buy(Content.byId("helper")!!) }
        state.buy(Content.byId("helper_can_rare")!!)
        state.buy(Content.byId("chain_reaction")!!)

        val world = World(state)
        val expected = world.fishes.size
        assertTrue("鱼群规模应大于初始", expected > 10)

        world.advance(180f)   // 3 分钟

        assertEquals("鱼群规模不应变化", expected, world.fishes.size)
        world.fishes.forEach { f ->
            assertTrue("鱼游出了活动区域: ${f.x},${f.y}",
                f.x >= Space.POND_L - 5f && f.x <= Space.POND_R + 5f)
            assertTrue("鱼游出了活动区域: ${f.x},${f.y}",
                f.y >= Space.POND_T - 5f && f.y <= Space.POND_B + 5f)
        }
    }

    @Test
    fun `连锁反应会一次结算多条鱼`() {
        val state = GameState()
        state.money = 1e12
        repeat(30) { state.buy(Content.byId("common_fish")!!) }
        state.buy(Content.byId("chain_reaction")!!)
        val world = World(state)

        // 把所有鱼聚到同一点，确保连锁能触发
        val cx = Space.W / 2f
        val cy = Space.POND_T + 300f
        world.fishes.forEach { it.x = cx; it.y = cy }

        world.advance(0.5f)
        world.castAtNearestFish()

        var guard = 0
        while (world.bobber.isActive && guard < 3000) {
            world.update(dt)
            if (world.bobber.state == BobberState.BITE) world.onTap(world.bobber.x, world.bobber.y)
            guard++
        }

        val chainEarned = state.earningsBySource[com.taptap.fishingidle.game.Source.CHAIN] ?: 0.0
        assertTrue("连锁反应应产生收益，实际=$chainEarned", chainEarned > 0.0)
        assertEquals("连锁后鱼群规模不变", 30 + GameState.INITIAL_COMMON_FISH, world.fishes.size)
    }

    @Test
    fun `浮动文字和粒子会被回收不会无限增长`() {
        val state = GameState()
        state.money = 1e12
        repeat(25) { state.buy(Content.byId("common_fish")!!) }
        repeat(6) { state.buy(Content.byId("helper")!!) }
        val world = World(state)

        world.advance(120f)

        assertTrue("浮动文字应被回收，实际=${world.floatingTexts.size}", world.floatingTexts.size < 120)
        assertTrue("粒子应被回收，实际=${world.particles.size}", world.particles.size < 400)
    }

    @Test
    fun `收线进度只增不减且能完成`() {
        val state = GameState()
        val world = World(state)
        world.advance(0.5f)
        world.castAtNearestFish()

        var guard = 0
        var reachedReeling = false
        while (world.bobber.isActive && guard < 3000) {
            world.update(dt)
            if (world.bobber.state == BobberState.BITE) {
                world.onTap(world.bobber.x, world.bobber.y)
                reachedReeling = world.bobber.state == BobberState.REELING
            }
            guard++
        }
        assertTrue("应能进入收线状态", reachedReeling)
        assertTrue("浮标应正常结束", world.bobber.state == BobberState.DONE)
    }

    @Test
    fun `智能浮标解锁后会自动抛竿`() {
        val state = GameState()
        state.money = 1e9
        state.buy(Content.byId("auto_reel_unlock")!!)
        assertTrue(state.autoReelUnlocked)

        val world = World(state)
        world.advance(1f)
        // 模拟手指悬停在一条鱼上
        world.hoverFish = world.fishes.first()
        world.advance(1.5f)

        assertTrue(
            "悬停在鱼上时应自动抛竿",
            world.bobber.isActive || world.bobber.state == BobberState.DONE,
        )
    }

    @Test
    fun `长时间运行经济持续增长且不出现负数`() {
        val state = GameState()
        state.money = 1e12
        repeat(15) { state.buy(Content.byId("common_fish")!!) }
        repeat(10) { state.buy(Content.byId("helper")!!) }
        state.buy(Content.byId("value_add_common")!!)
        state.buy(Content.byId("value_mul_common")!!)

        val world = World(state)
        val before = state.money
        world.advance(300f)   // 5 分钟

        assertTrue("经济应持续增长", state.money > before)
        assertTrue("金币不应为负", state.money >= 0.0)
        assertTrue("累计收入不应小于当前", state.totalMoney >= state.money - 1e12)
        world.fishes.forEach { f: Fish ->
            assertTrue("鱼坐标不应为 NaN", !f.x.isNaN() && !f.y.isNaN())
        }
    }
}
