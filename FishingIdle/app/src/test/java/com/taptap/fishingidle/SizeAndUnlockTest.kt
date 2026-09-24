package com.taptap.fishingidle

import com.taptap.fishingidle.game.BobberState
import com.taptap.fishingidle.game.Content
import com.taptap.fishingidle.game.DailyQuests
import com.taptap.fishingidle.game.FishSize
import com.taptap.fishingidle.game.GameState
import com.taptap.fishingidle.game.Rarity
import com.taptap.fishingidle.game.SkillTree
import com.taptap.fishingidle.game.SpeciesLore
import com.taptap.fishingidle.game.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 体型系统 / 解锁弹窗 / 解锁预告 / 技能树后期分支 的回归测试。 */
class SizeAndUnlockTest {

    // ---------------- 体型 ----------------

    @Test
    fun `体型越大越少见也越值钱`() {
        val tiers = FishSize.entries
        for (i in 1 until tiers.size) {
            assertTrue(
                "${tiers[i].label} 应该比 ${tiers[i - 1].label} 值钱",
                tiers[i].valueMul > tiers[i - 1].valueMul,
            )
            assertTrue(
                "${tiers[i].label} 应该比 ${tiers[i - 1].label} 少见",
                tiers[i].weight < tiers[i - 1].weight,
            )
            assertTrue("体型要真的更大", tiers[i].scale > tiers[i - 1].scale)
        }
        // 王者体型必须是"概率非常低"
        assertTrue("王者体型概率要低于 2%", FishSize.KING.weight < 0.02)
    }

    @Test
    fun `体型纪录只认更大的`() {
        val s = GameState()
        assertTrue("第一次拿到大只就是纪录", s.recordSize("jiyu", FishSize.BIG))
        assertFalse("再钓到普通不算纪录", s.recordSize("jiyu", FishSize.NORMAL))
        assertFalse("同样大不算新纪录", s.recordSize("jiyu", FishSize.BIG))
        assertTrue("更大才是纪录", s.recordSize("jiyu", FishSize.HUGE))
        assertEquals(FishSize.HUGE, s.bestSizeOf("jiyu"))
        assertEquals("没记录的鱼种按普通算", FishSize.NORMAL, s.bestSizeOf("liyu"))
    }

    @Test
    fun `普通体型不弹纪录提示`() {
        val state = GameState()
        val world = World(state)
        // 一路钓到第一条鱼：只应该弹"图鉴解锁"，普通体型不该占弹窗
        var guard = 0
        while (state.totalCatches == 0L && guard < 200_000) {
            world.update(1f / 60f)
            world.autoCastOnce()
            if (world.bobber.state == BobberState.BITE) {
                world.onTap(world.bobber.x, world.bobber.y)
            }
            guard++
        }
        assertEquals("没钓上鱼，测试前提不成立", 1L, state.totalCatches)
        assertEquals("新鱼种要排进解锁弹窗", 1, world.pendingNewSpecies.size)
        assertTrue(
            "普通体型不该刷纪录弹窗",
            world.pendingNewRecords.all { it.second != FishSize.NORMAL },
        )
    }

    // ---------------- 解锁提示 ----------------

    /**
     * 被藏起来的后期条目必须都配了"怎么解锁"，否则玩家又会找不到入口。
     */
    @Test
    fun `藏起来的后期条目都有解锁提示`() {
        val fresh = GameState()
        val hiddenLate = (Content.lateGame + Content.lateGame2)
            .filter { !it.visibleWhen(fresh) }
        assertTrue("后期条目应该都是逐级解锁的", hiddenLate.isNotEmpty())
        for (def in hiddenLate) {
            assertNotNull(
                "${def.name}(${def.id}) 被藏起来了却没写解锁提示，玩家找不到入口",
                Content.unlockHints[def.id],
            )
        }
    }

    /** 图鉴里每种鱼都要有资料，不能出现空白详情页。 */
    @Test
    fun `每种鱼都有图鉴资料`() {
        for (sp in com.taptap.fishingidle.game.Bestiary.allSpecies) {
            val lore = SpeciesLore.of(sp)
            assertTrue("${sp.name} 的资料太短了", lore.length >= 8)
        }
    }

    // ---------------- 技能树后期分支 ----------------

    @Test
    fun `技能树后期分支前置连得上`() {
        val chain = listOf("deep_diver", "treasure_hunter", "drone_ai", "king_slayer")
        // 这一串挂在已有的「采珠人」下面，链条要能一直连到它
        var prev: String? = "pearl_diver"
        for (id in chain) {
            val def = SkillTree.byId(id)
            assertNotNull("缺少技能节点 $id", def)
            assertEquals("$id 的前置不对", prev, def!!.requires)
            prev = id
        }
        // 最后一个节点要真的能改到鱼王有关的数值
        val s = GameState()
        SkillTree.byId("king_slayer")!!.effect(s, 4)
        assertTrue("鱼王克星要能提升拉力", s.skillKingPower > 0.5)
    }

    /** 后期技能要真的作用到对应系统上。 */
    @Test
    fun `后期技能作用到对应系统`() {
        val base = GameState()
        val world0 = World(base)
        repeat(3) { base.buy(Content.byId("common_fish")!!) }   // 需要先有钱买东西才谈得上

        val skilled = GameState()
        SkillTree.byId("deep_diver")!!.effect(skilled, 8)
        SkillTree.byId("treasure_hunter")!!.effect(skilled, 8)
        SkillTree.byId("drone_ai")!!.effect(skilled, 10)
        val world1 = World(skilled)

        assertTrue(
            "深海打捞要缩短潜水员周期",
            world1.diverCycle() < world0.diverCycle(),
        )
        assertTrue("寻宝达人要减少宝箱间隔与提高奖励", skilled.skillChestSpeed > 0.0 && skilled.skillChestValue > 0.0)
        assertTrue("无人机编队要缩短自动抛竿间隔", world1.autoCastIntervalScale() < 1f)
    }

    // ---------------- 每日任务 ----------------

    @Test
    fun `每日任务包含后期玩法目标`() {
        val ids = DailyQuests.pool.map { it.id }
        assertTrue("缺少开宝箱的每日任务", ids.contains("chest2"))
        assertTrue("缺少鱼王的每日任务", ids.contains("king1"))

        val progress = com.taptap.fishingidle.game.DailyProgress()
        progress.chests = 2
        progress.kings = 1
        val chestQuest = DailyQuests.pool.first { it.id == "chest2" }
        val kingQuest = DailyQuests.pool.first { it.id == "king1" }
        assertTrue("开箱数要计入任务进度", chestQuest.track(progress) >= chestQuest.goal)
        assertTrue("鱼王数要计入任务进度", kingQuest.track(progress) >= kingQuest.goal)
    }

    /** 稀有度只是数据，别让测试失去意义 —— 顺便确认底层稀有度表还在。 */
    @Test
    fun `稀有度基础价值递增`() {
        val order = Rarity.entries.map { it.baseValue }
        for (i in 1 until order.size) assertTrue(order[i] > order[i - 1])
    }
}
