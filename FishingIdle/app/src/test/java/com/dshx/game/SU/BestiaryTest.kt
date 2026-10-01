package com.dshx.game.SU

import com.dshx.game.SU.game.Bestiary
import com.dshx.game.SU.game.GameState
import com.dshx.game.SU.game.Rarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 图鉴数据完整性测试。
 * 54 种鱼、6 张地图的配置一旦有重复 ID、缺素材、经济断档，都在这里拦住。
 */
class BestiaryTest {

    @Test
    fun `鱼种数量达到 50 种以上`() {
        assertTrue(
            "鱼种总数应 >= 50，实际 ${Bestiary.totalSpecies}",
            Bestiary.totalSpecies >= 50,
        )
    }

    @Test
    fun `地图数量与每图鱼种数符合设计`() {
        assertEquals(6, Bestiary.maps.size)
        Bestiary.maps.forEach { map ->
            assertEquals("${map.name} 应有 9 种鱼", 9, map.species.size)
        }
    }

    @Test
    fun `鱼种 id 全局唯一`() {
        val ids = Bestiary.allSpecies.map { it.id }
        val dup = ids.groupBy { it }.filter { it.value.size > 1 }.keys
        assertTrue("存在重复鱼种 id: $dup", dup.isEmpty())
    }

    @Test
    fun `鱼种名称在图内唯一`() {
        Bestiary.maps.forEach { map ->
            val names = map.species.map { it.name }
            val dup = names.groupBy { it }.filter { it.value.size > 1 }.keys
            assertTrue("${map.name} 内名称重复: $dup", dup.isEmpty())
        }
    }

    @Test
    fun `每个鱼种引用的精灵素材都存在`() {
        val artDir = File("src/main/assets/art")
        assertTrue("找不到素材目录 ${artDir.absolutePath}", artDir.isDirectory)

        val missing = Bestiary.allSpecies
            .map { it.sprite }
            .distinct()
            .filter { !File(artDir, "${it}_anim.png").exists() }
        assertTrue("缺少动画图集: $missing", missing.isEmpty())
    }

    @Test
    fun `每张地图都覆盖四个稀有度`() {
        Bestiary.maps.forEach { map ->
            Rarity.entries.forEach { r ->
                assertTrue(
                    "${map.name} 缺少 $r 档位的鱼",
                    map.species.any { it.rarity == r },
                )
            }
        }
    }

    @Test
    fun `地图倍率与解锁费用严格递增`() {
        val maps = Bestiary.maps
        for (i in 1 until maps.size) {
            assertTrue(
                "${maps[i].name} 的倍率应高于 ${maps[i - 1].name}",
                maps[i].valueMultiplier > maps[i - 1].valueMultiplier,
            )
            assertTrue(
                "${maps[i].name} 的解锁费用应高于 ${maps[i - 1].name}",
                maps[i].unlockCost > maps[i - 1].unlockCost,
            )
        }
        assertEquals("首图应免费", 0.0, maps.first().unlockCost, 0.001)
    }

    /**
     * 每张图的解锁耗时 = unlockCost ÷ 上一张图的 valueMultiplier。
     *
     * 这个值衡量"攒多久才买得起下一张图"，与鱼的绝对价值无关。
     * 历史问题：最初它恒等于 1.15e6（六张图一样，半小时通关）；
     * 后来改成均匀 ×3.7 仍偏快，玩家反馈"商店随便买几下就到下一个水域"。
     *
     * 这里守住三条设计意图：
     *  1. 相邻跨度必须**逐级拉大**（不是均匀翻倍）—— 越往后墙越硬；
     *  2. 总时长要够长，必须靠转生滚雪球而不是"多点几下"；
     *  3. 首图门槛不动，保证开局手感。
     */
    @Test
    fun `每张地图的解锁耗时逐图递增且跨度逐级拉大`() {
        val maps = Bestiary.maps
        val costs = (1 until maps.size).map { i ->
            maps[i].unlockCost / maps[i - 1].valueMultiplier
        }

        // 1. 每次跨度本身也要递增：后一段的倍数 > 前一段
        val steps = costs.zipWithNext { a, b -> b / a }
        steps.zipWithNext().forEachIndexed { i, (prev, next) ->
            assertTrue(
                "跨度应逐级拉大：第 ${i + 2} 段 ×$prev 不应小于第 ${i + 1} 段 ×$next",
                next > prev,
            )
        }
        assertTrue("每段跨度都应明显（至少 ×3），实际 $steps", steps.all { it >= 3.0 })

        // 2. 总量足够长线
        val total = costs.sum()
        assertTrue(
            "总解锁耗时应至少是第一张图的 1000 倍，实际 ${total / costs.first()}",
            total > costs.first() * 1000,
        )

        // 3. 首图门槛保持原值（开局手感不变）
        assertEquals("芦苇荡的解锁价应保持 1.15e6", 1_150_000.0, maps[1].unlockCost, 1.0)
    }

    @Test
    fun `后一张地图的常见鱼价值高于前一张的传说鱼`() {
        // 保证换图始终是明显变强，不会出现"新图不如老图"的断层
        val maps = Bestiary.maps
        for (i in 1 until maps.size) {
            val prevLegend = maps[i - 1].species
                .filter { it.rarity == Rarity.LEGEND }
                .maxOf { it.rarity.baseValue * it.valueMul * maps[i - 1].valueMultiplier }
            val nextCommon = maps[i].species
                .filter { it.rarity == Rarity.COMMON }
                .minOf { it.rarity.baseValue * it.valueMul * maps[i].valueMultiplier }
            assertTrue(
                "${maps[i].name} 的常见鱼($nextCommon) 应高于 ${maps[i - 1].name} 的传说鱼($prevLegend)",
                nextCommon > prevLegend,
            )
        }
    }

    @Test
    fun `按权重抽鱼始终能抽到`() {
        val rnd = kotlin.random.Random(42)
        Bestiary.maps.forEach { map ->
            repeat(300) {
                assertNotNull(map.roll(rnd))
            }
        }
    }

    @Test
    fun `稀有度越高出现频率越低`() {
        val rnd = kotlin.random.Random(7)
        val counts = mutableMapOf<Rarity, Int>()
        val map = Bestiary.VILLAGE_CREEK
        repeat(20_000) {
            val s = map.roll(rnd)
            counts[s.rarity] = (counts[s.rarity] ?: 0) + 1
        }
        val common = counts[Rarity.COMMON] ?: 0
        val legend = counts[Rarity.LEGEND] ?: 0
        assertTrue("常见鱼应远多于传说鱼 (常见=$common 传说=$legend)", common > legend * 5)
    }

    @Test
    fun `图鉴编号从 1 开始且连续`() {
        val nos = Bestiary.allSpecies.map { it.dexNo }
        assertEquals(1, nos.min())
        assertEquals(Bestiary.totalSpecies, nos.max())
        assertEquals(Bestiary.totalSpecies, nos.distinct().size)
    }

    @Test
    fun `初始只解锁第一张地图`() {
        val s = GameState()
        assertEquals(1, s.unlockedMaps.size)
        assertTrue(s.unlockedMaps.contains(Bestiary.maps.first().id))
        assertEquals(Bestiary.maps.first().id, s.currentMapId)
    }

    @Test
    fun `金币不足时无法解锁新地图`() {
        val s = GameState()
        val second = Bestiary.maps[1]
        s.money = second.unlockCost - 1
        assertFalse(s.unlockMap(second))
        assertFalse(s.unlockedMaps.contains(second.id))
    }

    @Test
    fun `金币充足时解锁并切换地图`() {
        val s = GameState()
        val second = Bestiary.maps[1]
        s.money = second.unlockCost
        assertTrue(s.unlockMap(second))
        assertTrue(s.unlockedMaps.contains(second.id))
        assertEquals(second.id, s.currentMapId)
        assertEquals(0.0, s.money, 0.001)
    }

    @Test
    fun `已解锁的地图再次进入不再扣费`() {
        val s = GameState()
        val second = Bestiary.maps[1]
        s.money = second.unlockCost
        s.unlockMap(second)
        s.money = 100.0
        assertTrue(s.unlockMap(second))
        assertEquals("重复进入不应扣费", 100.0, s.money, 0.001)
    }

    @Test
    fun `鱼种价值随地图倍率放大`() {
        val s = GameState()
        val sp = Bestiary.VILLAGE_CREEK.species.first()
        val v1 = s.catchValue(sp, Bestiary.VILLAGE_CREEK)
        val v6 = s.catchValue(sp, Bestiary.DRAGON_ABYSS)
        assertTrue("末图价值应远高于首图", v6 > v1 * 1000)
    }

    @Test
    fun `钓到鱼种会记入图鉴`() {
        val s = GameState()
        assertEquals(0, s.caughtSpecies.size)
        s.recordSpecies("jiyu")
        s.recordSpecies("jiyu")
        s.recordSpecies("liyu")
        assertEquals("重复钓到同一种只记一次", 2, s.caughtSpecies.size)
    }
}
