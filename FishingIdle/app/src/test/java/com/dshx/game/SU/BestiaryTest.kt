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
     * 旧配置下这个值恒等于 1.15e6，六张图一模一样 —— 于是每张图耗时相同，
     * 玩家一路买下去半小时就推到最后一张图，反馈"不用转生就通关了"。
     * 这里守住两件事：耗时必须逐图明显变长，且总时长不能短到能一口气通关。
     */
    @Test
    fun `每张地图的解锁耗时逐图递增且总量足够长线`() {
        val maps = Bestiary.maps
        val costs = (1 until maps.size).map { i ->
            maps[i].unlockCost / maps[i - 1].valueMultiplier
        }
        costs.zipWithNext().forEachIndexed { i, (a, b) ->
            assertTrue(
                "第 ${i + 2} 张图的解锁耗时应明显高于第 ${i + 1} 张（$a → $b）",
                b > a * 1.5,
            )
        }
        val total = costs.sum()
        assertTrue("总解锁耗时应至少是第一张图的 30 倍，实际 ${total / costs.first()}", total > costs.first() * 30)
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
