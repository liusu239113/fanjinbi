package com.taptap.fishingidle

import com.taptap.fishingidle.game.Bestiary
import com.taptap.fishingidle.game.BoatArt
import com.taptap.fishingidle.game.BobberState
import com.taptap.fishingidle.game.Chest
import com.taptap.fishingidle.game.Content
import com.taptap.fishingidle.game.Diver
import com.taptap.fishingidle.game.Fish
import com.taptap.fishingidle.game.Rarity
import com.taptap.fishingidle.game.FishState
import com.taptap.fishingidle.game.GameState
import com.taptap.fishingidle.game.Source
import com.taptap.fishingidle.game.Space
import com.taptap.fishingidle.game.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 真实模拟测试：直接把游戏世界跑起来推进几千帧，验证不会卡死、不会漏鱼、经济会增长。
 * 这类测试能抓到纯数值测试抓不到的时序 bug。
 */
class WorldSimulationTest {
    private val DiveCycle = Diver.DIVE_CYCLE
    private val ChestInterval = World.CHEST_INTERVAL


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
    fun `并行作业升级真的会让一条船照看多条鱼`() {
        val state = GameState()
        state.money = 1e12
        repeat(20) { state.buy(Content.byId("common_fish")!!) }
        repeat(5) { state.buy(Content.byId("helper")!!) }
        state.buy(Content.byId("helper_parallel")!!)
        assertEquals(1, state.helperParallel)

        val world = World(state)
        world.advance(30f)

        val most = world.helpers.maxOfOrNull { it.targets.size } ?: 0
        assertTrue("买了「并行作业」就该有条船同时照看多条鱼，实际最多=$most", most >= 2)
        // 每条线各自对应一条被认领的鱼，不能出现重复占用
        val claimed = world.fishes.count { it.claimedBy != null }
        val lines = world.helpers.sumOf { it.targets.size }
        assertEquals("认领数与钓线数应一致", lines, claimed)
    }

    @Test
    fun `主角与帮手的船底都贴在同一条水位线上`() {
        // 渲染层按"立绘里的船底像素落在这条线"摆放，两边用的是同一套公式：
        // 这里直接验证该不变量，避免又把主角和帮手摆成一高一低。
        for (frac in listOf(BoatArt.PLAYER_HULL_FRAC, BoatArt.HELPER_HULL_FRAC)) {
            val hullBottomOffset = BoatArt.centerOffsetY(frac) + (frac - 0.5f) * BoatArt.HEIGHT
            assertEquals("船底应正好落在水位线上", 0f, hullBottomOffset, 0.001f)
        }
        // 竿尖必须高于水位线，否则鱼线会从船身里穿出来
        val (_, tipDy) = BoatArt.rodTipOffset(1f)
        assertTrue("竿尖应在水位线之上，实际偏移=$tipDy", tipDy < -20f)
    }

    @Test
    fun `每张水域都有自己的环境素材与配色`() {
        val ids = Bestiary.maps.map { it.env.id }
        assertEquals("水域 id 不能重复", ids.size, ids.distinct().size)
        val clouds = Bestiary.maps.map { it.env.cloud }
        val weeds = Bestiary.maps.map { it.env.seaweed }
        assertEquals("每张水域的云朵素材都要独立", clouds.size, clouds.distinct().size)
        assertEquals("每张水域的水草素材都要独立", weeds.size, weeds.distinct().size)
        val skies = Bestiary.maps.map { it.env.skyTop }
        assertEquals("每张水域的天空配色都要不同", skies.size, skies.distinct().size)
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

        val cx = Space.W / 2f
        val cy = Space.POND_T + 300f

        // 关键：整个收线过程都要把鱼按在落点附近。
        // 鱼每帧会随机游动，若只在开始时聚一次，收线几秒后早就散开，
        // 连锁半径内可能一条都不剩 —— 那会让这个测试时过时不过。
        var guard = 0
        var casts = 0
        while (guard < 6000 && casts < 3) {
            world.fishes.forEach { it.x = cx; it.y = cy }
            if (!world.bobber.isActive) {
                world.castLine(cx, cy)
                casts++
            }
            world.update(dt)
            if (world.bobber.state == BobberState.BITE) {
                world.onTap(world.bobber.x, world.bobber.y)
            }
            guard++
        }

        val chainEarned = state.earningsBySource[com.taptap.fishingidle.game.Source.CHAIN] ?: 0.0
        assertTrue("连锁反应应产生收益，实际=$chainEarned", chainEarned > 0.0)
        assertEquals(
            "连锁后鱼群规模不变",
            30 + GameState.INITIAL_COMMON_FISH, world.fishes.size,
        )
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

    /**
     * 船划到河的两端时不能半条出画。
     *
     * 镜头最多只能推到 `[half, W-half]`；如果船还能往更外面划，镜头就追不上了 ——
     * 玩家看到的就是"船卡在屏幕边上、镜头不跟了、这边过不去"。
     */
    @Test
    fun `船划到两端也不会跑出视野`() {
        val state = GameState()
        val world = World(state)
        world.viewHalfWidth = 340f           // 一屏约 680 世界单位
        val visible = 60f                    // 至少留这么多白边

        world.setMoveLeft(true)
        repeat(60 * 12) { world.update(dt) }  // 往左狂划 12 秒
        world.setMoveLeft(false)
        assertTrue("应该能划到左端附近，实际 ${world.boatX}", world.boatX < 200f)
        assertTrue(
            "往左划到底时船出画了：boat=${world.boatX} camera=${world.cameraX}",
            world.boatX >= world.cameraX - world.viewHalfWidth + visible,
        )

        world.setMoveRight(true)
        repeat(60 * 24) { world.update(dt) }  // 再往右划到头
        world.setMoveRight(false)
        assertTrue("应该能划到右端附近，实际 ${world.boatX}", world.boatX > Space.W - 200f)
        assertTrue(
            "往右划到底时船出画了：boat=${world.boatX} camera=${world.cameraX}",
            world.boatX <= world.cameraX + world.viewHalfWidth - visible,
        )
    }

    @Test
    fun `自动收线解锁后咬钩即自动收线`() {
        val state = GameState()
        state.money = 1e9
        state.buy(Content.byId("auto_reel_unlock")!!)
        assertTrue(state.autoReelUnlocked)

        val world = World(state)
        world.advance(0.5f)
        world.castAtNearestFish()

        // 全程不给任何点击，只推进时间
        var sawReeling = false
        var guard = 0
        while (guard < 4000 && world.bobber.isActive) {
            world.update(dt)
            if (world.bobber.state == BobberState.REELING) sawReeling = true
            guard++
        }

        assertTrue("咬钩后应自动进入收线，不需要玩家点屏幕", sawReeling)
        assertTrue("自动收线也要真的把鱼钓上来", state.money > 0.0)
    }

    @Test
    fun `智能浮标解锁后能全程挂机`() {
        val state = GameState()
        state.money = 1e9
        state.buy(Content.byId("auto_cast_unlock")!!)
        state.buy(Content.byId("auto_reel_unlock")!!)
        assertTrue(state.autoCastUnlocked)

        val world = World(state)
        // 完全不碰屏幕，只跑时间
        world.advance(60f)

        assertTrue("自动抛竿应能自己下竿", world.bobber.state != BobberState.IDLE)
        assertTrue("挂机一分钟应该有渔获，实际=${state.money}", state.money > 0.0)
        assertTrue("挂机渔获应计入统计", state.totalCatches > 0)
    }

    /**
     * 鱼都在远处时，挂了智能浮标的船要自己开过去 ——
     * 不然自动抛竿只在船附近找鱼，挂机会停在没鱼的河段上一直空转。
     */
    @Test
    fun `船边没鱼时会自己巡航过去`() {
        val state = GameState()
        state.autoCastUnlocked = true
        val world = World(state)
        world.viewHalfWidth = 340f
        world.fishes.forEach { it.x = 2600f; it.y = 1100f }   // 鱼全赶到右半边
        world.sailTo(200f)                                    // 船停在最左边
        val start = world.boatX

        repeat(60 * 25) { world.update(dt) }

        assertTrue(
            "船该自己开向鱼群：起点 $start，现在 ${world.boatX}",
            world.boatX > start + 300f,
        )
    }

    @Test
    fun `没买自动抛竿时不会自己下竿`() {
        val state = GameState()
        val world = World(state)
        world.advance(30f)
        assertTrue(
            "没买「智能浮标」就不该自动抛竿，否则升级没意义",
            world.bobber.state == BobberState.IDLE,
        )
        assertEquals(0.0, state.money, 0.001)
    }

    /**
     * 开局十分钟的收入不该够养一整队钓手。
     *
     * 这是"数值别失控"的总闸：自动化直接给（单独衡量收入速度本身），
     * 玩家每 5 秒把钱花在最便宜的鱼苗和新增钓手上，成就奖金也照常结算。
     * 之前成就一次给 1.5 万 ~ 40 万，这条会直接挂。
     */
    @Test
    fun `开局十分钟的收入买不起一整队钓手`() {
        val state = GameState()
        state.autoReelUnlocked = true
        state.autoCastUnlocked = true
        val world = World(state)

        var frames = 0
        repeat(10 * 60 * 60) {
            world.update(dt)
            frames++
            if (frames % 300 == 0) {          // 每 5 秒花一次钱
                Content.byId("common_fish")?.let { state.buy(it) }
                Content.byId("helper")?.let { state.buy(it) }
            }
        }

        assertTrue(
            "十分钟就雇到 ${state.helpers} 名钓手（累计收入 ${state.totalMoney}），太快了",
            state.helpers < 6,
        )
    }

    /**
     * 鹈鹕：买了才会出现，而且真的会自己去叼鱼（不计入点击、算挂机收益）。
     */
    @Test
    fun `鹈鹕会自己叼走最值钱的鱼`() {
        val state = GameState()
        state.pelicanOwned = true
        state.helpers = 0
        val world = World(state)
        world.syncSpecialUnits()
        assertEquals("买了就该放一只出来", 1, world.pelicans.size)

        world.advance(40f)

        assertTrue("鹈鹕四十秒内至少该叼上几条，实际 ${state.totalCatches}", state.totalCatches >= 1)
        assertTrue("叼上来的鱼要算钱", state.money > 0.0)
        val pelicanIncome = state.earningsBySource[Source.PELICAN] ?: 0.0
        assertTrue("收益要记在「鹈鹕叼鱼」名下，实际 $pelicanIncome", pelicanIncome > 0.0)
    }

    /** 没买鹈鹕时河面上不该有鸟。 */
    @Test
    fun `没买鹈鹕就没有鹈鹕`() {
        val state = GameState()
        val world = World(state)
        world.syncSpecialUnits()
        world.advance(10f)
        assertTrue(world.pelicans.isEmpty())
    }

    /**
     * 拖网：买了之后每隔一段时间自动捞一网，一次捞起好几条。
     */
    @Test
    fun `拖网会一次捞起好几条鱼`() {
        val state = GameState()
        state.netOwned = true
        val world = World(state)
        // 鱼都赶到船附近，保证一网能罩住
        world.fishes.forEach { it.x = world.boatX + 60f; it.y = Space.BOAT_WATERLINE + 200f }

        world.advance(46f) // 拖网间隔 45 秒

        val netIncome = state.earningsBySource[Source.NET] ?: 0.0
        assertTrue("拖网该有收成，实际 $netIncome", netIncome > 0.0)
        assertTrue(
            "一网该捞起多条鱼，实际 ${state.totalCatches} 条",
            state.totalCatches >= 2,
        )
    }

    /** 没买拖网就没有网。 */
    @Test
    fun `没买拖网就不会撒网`() {
        val state = GameState()
        val world = World(state)
        world.advance(120f)
        assertEquals(0.0, state.earningsBySource[Source.NET] ?: 0.0, 0.001)
    }


    // ---------------- 后期第二梯队：声呐 / 鱼探仪 / 无人机 / 潜水员 / 宝箱 ----------------

    /** 声呐只加速稀有鱼的咬钩，小鱼不受影响。 */
    @Test
    fun `声呐让稀有鱼更快咬钩`() {
        val plain = World(GameState())
        val sonar = World(GameState().apply { sonarOwned = true })
        assertEquals(1f, plain.sonarBiteSpeed(Rarity.RARE), 0.001f)
        assertTrue("稀有鱼该更快咬钩", sonar.sonarBiteSpeed(Rarity.RARE) > 1.2f)
        assertTrue("巨口鱼也该更快", sonar.sonarBiteSpeed(Rarity.LEGEND) > 1.2f)
        assertEquals("小鱼不受声呐影响", 1f, sonar.sonarBiteSpeed(Rarity.COMMON), 0.001f)
    }

    /** 鱼探仪把落点吸到附近的鱼身上：本来够不着的距离也能中。 */
    @Test
    fun `鱼探仪让落点吸附到鱼身上`() {
        fun castOnce(finder: Boolean): Fish? {
            val state = GameState().apply { fishFinderOwned = finder }
            val world = World(state)
            // 只留一条鱼，放在落点 200 单位外（超过咬钩距离 190，但在吸附半径 220 内）
            val fish = world.fishes.first()
            fish.x = 1500f
            fish.y = 1000f
            world.fishes.drop(1).forEach { it.x = 200f; it.y = 300f }
            world.castLine(fish.x + 200f, fish.y)
            return world.bobber.hookedFish
        }

        assertNull("没鱼探仪时这么远够不着", castOnce(false))
        assertNotNull("买了鱼探仪落点会被吸过去", castOnce(true))
    }

    /** 无人机把自动抛竿的范围和间隔都改掉。 */
    @Test
    fun `无人机强化自动抛竿`() {
        val plain = World(GameState())
        val drone = World(GameState().apply { droneOwned = true })
        assertEquals(World.AUTO_CAST_RANGE, plain.autoCastRange(), 1f)
        assertEquals(World.AUTO_CAST_RANGE * World.DRONE_RANGE_MULT, drone.autoCastRange(), 1f)
        assertEquals(1f, plain.autoCastIntervalScale(), 0.001f)
        assertTrue("买了无人机间隔该更短", drone.autoCastIntervalScale() < 0.8f)
    }

    /** 潜水员一个周期（3 分钟）会捞一颗珍珠上来，珍珠是转生货币。 */
    @Test
    fun `潜水员定期捞珍珠上来`() {
        val state = GameState().apply { diverOwned = true }
        val world = World(state)
        world.syncSpecialUnits()
        assertNotNull("买了就该有潜水员", world.diver)

        repeat((DiveCycle * 60).toInt() + 120) { world.update(dt) }

        assertTrue("一个周期后该有珍珠，实际 ${state.pearls}", state.pearls >= 1L)
    }

    /** 没买潜水员就没有珍珠。 */
    @Test
    fun `没买潜水员就没有珍珠`() {
        val state = GameState()
        val world = World(state)
        repeat(60 * 400) { world.update(dt) }
        assertEquals(0L, state.pearls)
    }

    /** 宝箱要自己点开才有收成；点了给一大笔钱。 */
    @Test
    fun `宝箱浮出来后点开才有钱`() {
        val state = GameState().apply { treasureOwned = true }
        val world = World(state)
        world.advance(ChestInterval + 2f)

        val c = world.chest
        assertNotNull("该浮出宝箱了", c)
        val before = state.money
        assertEquals(0.0, before, 0.001)

        world.onTap(c!!.x, c.y)

        assertTrue("开箱该给一大笔金币，实际 ${state.money}", state.money > before + 100.0)
        assertNull("开完就没了", world.chest)
    }

    /** 没买「沉船宝藏」就不会浮宝箱。 */
    @Test
    fun `没买沉船宝藏就不会浮宝箱`() {
        val state = GameState()
        val world = World(state)
        world.advance(ChestInterval + 10f)
        assertNull(world.chest)
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
