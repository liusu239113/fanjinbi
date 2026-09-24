package com.taptap.fishingidle

import com.taptap.fishingidle.game.Bestiary
import com.taptap.fishingidle.game.Content
import com.taptap.fishingidle.game.GameState
import com.taptap.fishingidle.game.Prestige
import com.taptap.fishingidle.game.Rarity
import com.taptap.fishingidle.game.SkillTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 转生与技能树测试。
 *
 * 这类"重置一部分、保留一部分"的逻辑最容易漏掉某个字段，
 * 所以这里把「该清的」和「该留的」都逐条断言。
 */
class PrestigeTest {

    /**
     * 通过真实加钱累积 totalMoney。
     * totalMoney 是只读的（只能随收益增长），测试也不该绕过这个约束。
     */
    private fun GameState.earn(amount: Double) {
        money += amount
    }

    /** 造一个买过东西、且累计收入足够转生的存档。 */
    private fun richState(): GameState = GameState().apply {
        earn(1e12)
        repeat(20) { buy(Content.byId("common_fish")!!) }
        repeat(10) { buy(Content.byId("helper")!!) }
        repeat(5) { buy(Content.byId("value_add_common")!!) }
        buy(Content.byId("auto_reel_unlock")!!)
        buy(Content.byId("chain_reaction")!!)
    }

    @Test
    fun `收入不足时无法转生`() {
        val s = GameState()
        s.earn(100.0)
        assertEquals(0L, s.pendingPearls())
        assertFalse(s.canPrestige())
        assertEquals(0L, s.doPrestige())
        assertEquals(0, s.prestigeCount)
    }

    @Test
    fun `达到门槛后可以转生并得到珍珠`() {
        val s = GameState()
        s.earn(Prestige.MIN_TOTAL_FOR_PRESTIGE)
        assertTrue(s.canPrestige())
        val gain = s.pendingPearls()
        assertTrue("应至少得到 1 颗珍珠", gain >= 1)

        val got = s.doPrestige()
        assertEquals(gain, got)
        assertEquals(gain, s.pearls)
        assertEquals(1, s.prestigeCount)
    }

    @Test
    fun `转生收益递减：收入翻十倍不会得到十倍珍珠`() {
        val a = Prestige.pearlsFor(1e8)
        val b = Prestige.pearlsFor(1e9)
        assertTrue("应该变多", b > a)
        assertTrue("但远不到十倍 (a=$a b=$b)", b < a * 10)
    }

    @Test
    fun `转生清空本轮进度`() {
        val s = richState()
        s.caughtSpecies.add("jiyu")
        s.unlockedMaps.add(Bestiary.maps[1].id)
        s.currentMapId = Bestiary.maps[1].id

        val before = s.purchases.size
        assertTrue("前置条件：应该买过东西", before > 0)

        s.doPrestige()

        // 该清的
        assertEquals("金币应清空", 0.0, s.money, 0.001)
        assertEquals("累计收入应清空", 0.0, s.totalMoney, 0.001)
        assertEquals("购买记录应清空", 0, s.purchases.size)
        assertEquals("鱼群应回到初始数量", GameState.INITIAL_COMMON_FISH, s.commonFish)
        assertEquals("鲤鱼应清空", 0, s.rareFish)
        assertEquals("钓手应清空", 0, s.helpers)
        assertEquals("升级加成应清空", 0.0, s.commonValueAdd, 0.001)
        assertEquals("倍率应回到 1", 1.0, s.commonValueMul, 0.001)
        assertFalse("解锁标记应清空", s.autoReelUnlocked)
        assertFalse("连锁应清空", s.chainReaction)
        assertEquals("地图应回到第一张", Bestiary.maps.first().id, s.currentMapId)
        assertEquals("已解锁地图应只剩第一张", 1, s.unlockedMaps.size)
        assertEquals("连击应清零", 0, s.combo)
    }

    @Test
    fun `转生保留珍珠与技能`() {
        val s = richState()
        s.doPrestige()
        val pearlsAfter = s.pearls
        assertTrue(pearlsAfter > 0)

        // 投一级技能
        val def = SkillTree.byId("bait_mastery")!!
        assertTrue(SkillTree.levelUp(s, def))
        val levelAfter = s.skillLevel(def.id)
        assertEquals(1, levelAfter)
        val pearlsLeft = s.pearls

        // 再转生一次
        s.earn(1e9)
        val secondGain = s.pendingPearls()
        assertTrue("第二次也应得到珍珠", secondGain > 0)
        s.doPrestige()

        assertEquals("珍珠应累加而非清零", pearlsLeft + secondGain, s.pearls)
        assertEquals("技能等级应保留", levelAfter, s.skillLevel(def.id))
        assertEquals("转生次数应累加", 2, s.prestigeCount)
    }

    @Test
    fun `转生保留图鉴与成就`() {
        val s = richState()
        s.caughtSpecies.add("jiyu")
        s.caughtSpecies.add("liyu")
        s.unlockedAchievements.add("first_catch")
        val speciesCount = s.caughtSpecies.size

        s.doPrestige()

        assertEquals("图鉴应保留", speciesCount, s.caughtSpecies.size)
        assertTrue("成就应保留", s.unlockedAchievements.contains("first_catch"))
    }

    @Test
    fun `技能提升收益并实际生效`() {
        val s = GameState()
        val before = s.catchValue(Rarity.COMMON)

        s.pearls = 100
        val def = SkillTree.byId("bait_mastery")!!
        assertTrue(SkillTree.levelUp(s, def))

        val after = s.catchValue(Rarity.COMMON)
        assertTrue("鱼饵精通应提升收益 ($before -> $after)", after > before)
        assertEquals("每级 +25%", before * 1.25, after, 0.001)
    }

    @Test
    fun `技能提升收线速度`() {
        val s = GameState()
        val before = s.reelSpeed(Rarity.COMMON)
        s.pearls = 100
        assertTrue(SkillTree.levelUp(s, SkillTree.byId("quick_hands")!!))
        assertTrue("快手应提升收线速度", s.reelSpeed(Rarity.COMMON) > before)
    }

    @Test
    fun `珍珠不足时无法升级技能`() {
        val s = GameState()
        s.pearls = 0
        assertFalse(SkillTree.levelUp(s, SkillTree.byId("bait_mastery")!!))
        assertEquals(0, s.skillLevel("bait_mastery"))
    }

    @Test
    fun `前置技能未解锁时无法升级`() {
        val s = GameState()
        s.pearls = 1000
        // head_start 需要 bait_mastery 至少 1 级
        val headStart = SkillTree.byId("head_start")!!
        assertFalse(SkillTree.unlocked(s, headStart))
        assertFalse(SkillTree.levelUp(s, headStart))

        assertTrue(SkillTree.levelUp(s, SkillTree.byId("bait_mastery")!!))
        assertTrue("解锁前置后应可升级", SkillTree.unlocked(s, headStart))
        assertTrue(SkillTree.levelUp(s, headStart))
    }

    @Test
    fun `技能达到上限后不能再升`() {
        val s = GameState()
        s.pearls = 1_000_000
        val def = SkillTree.byId("bait_mastery")!!
        repeat(def.maxLevel) { assertTrue(SkillTree.levelUp(s, def)) }
        assertFalse("满级后应失败", SkillTree.levelUp(s, def))
        assertEquals(def.maxLevel, s.skillLevel(def.id))
    }

    @Test
    fun `技能升级费用逐级递增`() {
        val s = GameState()
        s.pearls = 1_000_000
        val def = SkillTree.byId("bait_mastery")!!
        val costs = mutableListOf<Long>()
        repeat(3) {
            val lv = s.skillLevel(def.id)
            costs.add(SkillTree.nextCost(def, lv))
            SkillTree.levelUp(s, def)
        }
        assertTrue("费用应递增: $costs", costs[0] < costs[1] && costs[1] < costs[2])
    }

    @Test
    fun `开局红利技能给转生后启动资金`() {
        val s = GameState()
        s.pearls = 100
        SkillTree.levelUp(s, SkillTree.byId("bait_mastery")!!)
        SkillTree.levelUp(s, SkillTree.byId("head_start")!!)

        val startMoney = s.skillStartMoney
        assertTrue("应有启动资金", startMoney > 0)

        s.earn(1e9)
        s.doPrestige()
        assertEquals("转生后应带着启动资金", startMoney, s.money, 0.001)
    }

    @Test
    fun `所有技能定义合法`() {
        val ids = SkillTree.all.map { it.id }
        assertEquals("技能 id 应唯一", ids.size, ids.distinct().size)

        SkillTree.all.forEach { def ->
            assertTrue("${def.name} 最大等级应 > 0", def.maxLevel > 0)
            assertTrue("${def.name} 费用应 > 0", def.costPerLevel > 0)
            def.requires?.let {
                assertNotNull("${def.name} 的前置 $it 不存在", SkillTree.byId(it))
            }
        }
    }

    @Test
    fun `技能树不存在循环依赖`() {
        // 从每个节点向上追溯前置，不应回到自己
        SkillTree.all.forEach { start ->
            var cur: String? = start.requires
            var hops = 0
            while (cur != null && hops < 20) {
                assertTrue("检测到循环依赖: ${start.id}", cur != start.id)
                cur = SkillTree.byId(cur)?.requires
                hops++
            }
            assertTrue("前置链过深: ${start.id}", hops < 20)
        }
    }

    @Test
    fun `转生后技能加成仍然生效`() {
        val s = GameState()
        s.pearls = 100
        SkillTree.levelUp(s, SkillTree.byId("bait_mastery")!!)
        s.earn(1e9)
        s.doPrestige()

        // 转生清空了普通升级，但技能加成应该还在
        val base = GameState().catchValue(Rarity.COMMON)
        val withSkill = s.catchValue(Rarity.COMMON)
        assertTrue("技能加成应在转生后保留 ($base -> $withSkill)", withSkill > base)
    }
}
