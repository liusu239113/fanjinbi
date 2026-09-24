package com.taptap.fishingidle

import com.taptap.fishingidle.game.Achievements
import com.taptap.fishingidle.game.Content
import com.taptap.fishingidle.game.Prestige
import com.taptap.fishingidle.game.Rarity
import com.taptap.fishingidle.game.GameState
import com.taptap.fishingidle.game.SaveData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 经济系统测试。重点验证「存档只存购买次数、属性靠重放重建」这一核心机制，
 * 这是从原 Godot 工程移植过来最容易出错的地方。
 */
class EconomyTest {

    private fun def(id: String) = Content.byId(id)!!

    @Test
    fun `初始状态正确`() {
        val s = GameState()
        assertEquals(0.0, s.money, 0.001)
        assertEquals(GameState.INITIAL_COMMON_FISH, s.commonFish)
        assertEquals(0, s.rareFish)
        assertEquals(1.0, s.catchValue(Rarity.COMMON), 0.001)
    }

    @Test
    fun `价格公式与原版一致`() {
        // small_coin: base=1.3, mult=2  → floor(1.3^n * 2)
        val d = def("common_fish")
        assertEquals(2.0, d.price(0), 0.001)     // floor(2)      = 2
        assertEquals(2.0, d.price(1), 0.001)     // floor(2.6)    = 2
        assertEquals(3.0, d.price(2), 0.001)     // floor(3.38)   = 3
        assertEquals(4.0, d.price(3), 0.001)     // floor(4.394)  = 4
        assertEquals(5.0, d.price(4), 0.001)     // floor(5.7122) = 5
        assertEquals(7.0, d.price(5), 0.001)     // floor(7.4259) = 7

        // medium_coin: base=1.3, mult=200 → floor(1.3^n * 200)
        val m = def("rare_fish")
        assertEquals(200.0, m.price(0), 0.001)
        assertEquals(260.0, m.price(1), 0.001)
        assertEquals(338.0, m.price(2), 0.001)

        // helper: base=1.75, mult=500（门槛比早期版本高，总数收到 20 名）
        val h = def("helper")
        assertEquals(500.0, h.price(0), 0.001)
        assertEquals(875.0, h.price(1), 0.001)     // floor(1.75 * 500)
        assertEquals(1531.0, h.price(2), 0.001)    // floor(1.75^2 * 500)
        assertEquals(20, h.maxPurchases)
    }

    @Test
    fun `钓手要先自己钓上几条鱼才会出现`() {
        val s = GameState()
        assertFalse("开局就有钓手可买的话，等于一进游戏就能挂机", def("helper").visibleWhen(s))
        repeat(8) { s.onCatchSuccess() }
        assertTrue(def("helper").visibleWhen(s))
    }

    @Test
    fun `钓手上限是20名`() {
        val s = GameState()
        s.money = 1e30
        val h = def("helper")
        repeat(20) { assertTrue("第 ${it + 1} 名应该买得起", s.buy(h)) }
        assertEquals(20, s.helpers)
        assertFalse("第 21 名不该再买得到", s.buy(h))
        assertEquals(20, s.helpers)
    }

    @Test
    fun `鱼群聚集技能会提高鱼苗购买上限`() {
        val s = GameState()
        s.money = 1e30
        val d = def("common_fish")
        assertEquals(100, d.effectiveMax(s))
        // 技能「鱼群聚集」10 级 → 每种鱼 +100 容量，以前完全没接线
        s.skillFishCapacity = 100
        assertEquals(200, d.effectiveMax(s))
        repeat(200) { assertTrue(s.buy(d)) }
        assertFalse(s.buy(d))
        assertEquals(GameState.INITIAL_COMMON_FISH + 200, s.commonFish)
    }

    /** 把状态里所有会变的字段拼成一个指纹，用来判断一次购买到底动没动东西。 */
    private fun fingerprint(s: GameState): String = buildString {
        append(s.money)
        append(s.commonFish).append(s.rareFish).append(s.epicFish).append(s.legendFish)
        append(s.helpers)
        append(s.commonValueAdd).append(s.rareValueAdd)
        append(s.epicValueAdd).append(s.legendValueAdd)
        append(s.commonValueMul).append(s.rareValueMul).append(s.epicValueMul).append(s.legendValueMul)
        append(s.commonReelSpeed).append(s.rareReelSpeed).append(s.epicReelSpeed).append(s.legendReelSpeed)
        append(s.helperEfficiency).append(s.autoReelChance)
        append(s.autoReelUnlocked).append(s.autoCastUnlocked)
        append(s.pelicanOwned).append(s.netOwned)
        append(s.sonarOwned).append(s.fishFinderOwned).append(s.droneOwned)
        append(s.diverOwned).append(s.treasureOwned)
        append(s.helperCanRare).append(s.helperCanEpic).append(s.helperCanLegend)
        append(s.chainReaction)
        append(s.rarityMul).append(s.mapBonus).append(s.helperSpeed).append(s.helperParallel)
        append(s.comboPower).append(s.comboKeep).append(s.autoReelSpeed).append(s.luckyHook)
        for (kind in Rarity.entries) append(s.catchValue(kind)).append(s.reelSpeed(kind))
    }

    /**
     * 成就只能是奖金，不能是暴发户。
     *
     * 第一轮（还没转生）就拿得到的成就，奖励必须低于「前 6 名钓手的总价」——
     * 之前 15 连击给 1.5 万、40 连击给 40 万，成就弹一次就能连买六名钓手，
     * 前期经济直接崩掉。这条测试把量级钉死。
     */
    @Test
    fun `早期成就奖励买不起一整队钓手`() {
        val state = GameState()
        val squad = (0 until 6).sumOf { Content.byId("helper")!!.price(it) }
        val early = listOf(
            "first_catch", "catch_50", "catch_500", "earn_10k", "helper_5",
            "common_50", "rare_10", "combo_15", "combo_40", "single_1k",
        )
        for (id in early) {
            val def = requireNotNull(Achievements.byId(id)) { "成就 $id 不存在" }
            assertTrue(
                "${def.name} 奖励 ${def.reward} 够买一整队钓手了（前 6 名共 $squad）",
                def.reward < squad,
            )
        }
    }

    /** 成就奖励也不能离谱到超过转生门槛的量级（一次成就直接跨过一整轮）。 */
    @Test
    fun `没有成就一次给出超过转生门槛的奖励`() {
        for (def in Achievements.all) {
            assertTrue(
                "${def.name} 奖励 ${def.reward} 太夸张了",
                def.reward <= Prestige.MIN_TOTAL_FOR_PRESTIGE * 5,
            )
        }
    }

    /**
     * 每个商店条目都必须真的改变点什么。
     *
     * 「买了没效果」这种 bug 光看代码很难发现 —— 之前「并行作业」「鱼群聚集」
     * 「自动绞盘」三条升级就是这样挂在那里白卖的。这条测试直接对每条购买项
     * 取一次状态指纹，买了没变化就报错。
     */
    @Test
    fun `每个商店条目买了都真的有效果`() {
        assertEquals("购买项数量变了，记得补这条测试", 40, Content.purchasables.size)
        for (def in Content.purchasables) {
            val s = GameState()
            s.money = 1e30
            val before = fingerprint(s)
            assertTrue("买不了：${def.name}(${def.id})", s.buy(def))
            assertNotEquals("买了没任何变化：${def.name}(${def.id})", before, fingerprint(s))
        }
    }

    /**
     * 自动化不能开局就给。
     *
     * 之前「自动收线」1200 金币、开局就可见 —— 玩家还没玩明白点击循环，
     * 游戏就自己玩完了，整个前期玩法直接作废。
     */
    @Test
    fun `自动化升级不该开局就买得到`() {
        val fresh = GameState()
        val reel = def("auto_reel_unlock")
        val cast = def("auto_cast_unlock")
        assertFalse("开局就不该看到自动收线", reel.visibleWhen(fresh))
        assertFalse("开局就不该看到智能浮标", cast.visibleWhen(fresh))

        val mid = GameState().apply { repeat(40) { onCatchSuccess() } }
        assertTrue("钓够 40 条后应该开放自动收线", reel.visibleWhen(mid))
        assertFalse("没买自动收线就不该露出挂机升级", cast.visibleWhen(mid))

        val later = GameState().apply {
            repeat(200) { onCatchSuccess() }
            autoReelUnlocked = true
        }
        assertTrue("买了自动收线又钓够 150 条，才轮到智能浮标", cast.visibleWhen(later))
        assertTrue("挂机升级要明显比自动收线贵", cast.price(0) > reel.price(0) * 10)
    }

    /**
     * 每个商店条目要有自己的图标。
     *
     * 之前 33 个条目共用 6 张图标（四个鱼饵是同一张、四个线轮是同一张…），
     * 商店一眼看过去全是重复图，玩家根本分不清买了什么。
     */
    @Test
    fun `商店条目的图标不能重复`() {
        val byIcon = Content.purchasables.groupBy { it.icon }
        val dup = byIcon.filterValues { it.size > 1 }
        assertTrue(
            "这些条目还在共用图标：" + dup.entries.joinToString("; ") {
                "${it.key} → ${it.value.joinToString("/") { d -> d.name }}"
            },
            dup.isEmpty(),
        )
    }

    @Test
    fun `一次性解锁价格恒定`() {
        val d = def("auto_reel_unlock")
        assertEquals(6000.0, d.price(0), 0.001)
        assertEquals(6000.0, d.price(5), 0.001)
        assertEquals(1, d.maxPurchases)
        assertEquals(250000.0, def("auto_cast_unlock").price(0), 0.001)
        val cast = def("auto_cast_unlock")
        assertEquals(1, cast.maxPurchases)
    }

    @Test
    fun `金币不足时购买失败`() {
        val s = GameState()
        s.money = 1.0
        assertFalse(s.buy(def("common_fish")))
        assertEquals(1.0, s.money, 0.001)
        assertEquals(GameState.INITIAL_COMMON_FISH, s.commonFish)
    }

    @Test
    fun `购买扣除金币并生效`() {
        val s = GameState()
        s.money = 100.0
        assertTrue(s.buy(def("common_fish")))
        assertEquals(98.0, s.money, 0.001)
        assertEquals(GameState.INITIAL_COMMON_FISH + 1, s.commonFish)
        assertEquals(1, s.owned("common_fish"))
    }

    @Test
    fun `达到购买上限后不能再买`() {
        val s = GameState()
        s.money = 1e30
        val d = def("auto_reel_unlock")
        assertTrue(s.buy(d))
        assertFalse(s.buy(d))
        assertEquals(1, s.owned("auto_reel_unlock"))
    }

    @Test
    fun `可见性条件正确`() {
        val s = GameState()
        // 没有鲤鱼时，锦鲤不可见
        assertFalse(def("epic_fish").visibleWhen(s))
        s.rareFish = 1
        assertTrue(def("epic_fish").visibleWhen(s))
        // 巨口鱼需要锦鲤
        assertFalse(def("legend_fish").visibleWhen(s))
        s.epicFish = 1
        assertTrue(def("legend_fish").visibleWhen(s))
    }

    @Test
    fun `收益加成与倍率叠加正确`() {
        val s = GameState()
        s.money = 1e9
        // 小鱼：base 1 + add 1*N，再乘倍率
        repeat(3) { s.buy(def("value_add_common")) }
        assertEquals(4.0, s.catchValue(Rarity.COMMON), 0.001)  // (1+3)*1

        repeat(2) { s.buy(def("value_mul_common")) }
        // (1 + 3) * (1 + 0.2*2) = 4 * 1.4 = 5.6
        assertEquals(5.6, s.catchValue(Rarity.COMMON), 0.001)
    }

    @Test
    fun `存档重放能完整重建所有属性`() {
        val original = GameState()
        original.money = 1e9
        // 买一堆东西，覆盖数量、加成、倍率、速度、解锁标记各类属性
        repeat(5) { original.buy(def("common_fish")) }
        repeat(2) { original.buy(def("rare_fish")) }
        original.buy(def("epic_fish"))
        repeat(3) { original.buy(def("helper")) }
        repeat(4) { original.buy(def("value_add_common")) }
        repeat(2) { original.buy(def("value_mul_rare")) }
        original.buy(def("reel_speed_rare"))
        original.buy(def("auto_reel_unlock"))
        original.buy(def("helper_can_rare"))
        original.buy(def("chain_reaction"))
        repeat(2) { original.buy(def("auto_reel_chance")) }
        original.buy(def("helper_efficiency"))
        original.money = 12345.678

        // 存档 → 读档
        val saved = original.toSave()
        val restored = GameState()
        restored.loadFrom(saved)

        // 金钱类
        assertEquals(original.money, restored.money, 0.001)
        assertEquals(original.totalMoney, restored.totalMoney, 0.001)
        assertEquals(original.highestMoney, restored.highestMoney, 0.001)

        // 鱼群与钓手数量
        assertEquals(original.commonFish, restored.commonFish)
        assertEquals(original.rareFish, restored.rareFish)
        assertEquals(original.epicFish, restored.epicFish)
        assertEquals(original.legendFish, restored.legendFish)
        assertEquals(original.helpers, restored.helpers)

        // 数值加成
        assertEquals(original.commonValueAdd, restored.commonValueAdd, 0.001)
        assertEquals(original.rareValueMul, restored.rareValueMul, 0.001)
        assertEquals(original.rareReelSpeed, restored.rareReelSpeed, 0.001)
        assertEquals(original.helperEfficiency, restored.helperEfficiency, 0.001)
        assertEquals(original.autoReelChance, restored.autoReelChance, 0.001)

        // 解锁标记
        assertEquals(original.autoReelUnlocked, restored.autoReelUnlocked)
        assertEquals(original.helperCanRare, restored.helperCanRare)
        assertEquals(original.chainReaction, restored.chainReaction)

        // 派生值必须完全一致
        for (kind in Rarity.entries) {
            assertEquals(
                "渔获价值不一致: $kind",
                original.catchValue(kind), restored.catchValue(kind), 0.001,
            )
            assertEquals(
                "收线速度不一致: $kind",
                original.reelSpeed(kind), restored.reelSpeed(kind), 0.001,
            )
        }

        // 购买记录本身
        assertEquals(original.purchases, restored.purchases)
    }

    @Test
    fun `读档后继续购买价格正确`() {
        val s = GameState()
        s.money = 1e9
        repeat(6) { s.buy(def("common_fish")) }

        val restored = GameState()
        restored.loadFrom(s.toSave())
        restored.money = 1e9

        // 第 7 次购买应按 n=6 计价，而不是从头开始
        // floor(1.3^6 * 2) = floor(9.6536) = 9
        assertEquals(9.0, def("common_fish").price(restored.owned("common_fish")), 0.001)
        assertEquals(6, restored.owned("common_fish"))
        restored.buy(def("common_fish"))
        assertEquals(7, restored.owned("common_fish"))
        // 初始就有 1 条，买了 7 次 → 共 8 条
        assertEquals(GameState.INITIAL_COMMON_FISH + 7, restored.commonFish)
    }

    @Test
    fun `空存档不会崩溃`() {
        val s = GameState()
        s.loadFrom(SaveData())
        assertEquals(GameState.INITIAL_COMMON_FISH, s.commonFish)
        assertEquals(0.0, s.money, 0.001)
    }

    @Test
    fun `小鱼收益随升级从1增长到255`() {
        val s = GameState()
        // 满级总价约 1.3e14，必须给足金币，否则会因余额不足提前中断
        s.money = 1e30
        repeat(50) { assertTrue(s.buy(def("value_add_common"))) }   // +1 × 50
        repeat(20) { assertTrue(s.buy(def("value_mul_common"))) }   // +0.2 × 20 = ×5
        // (1 + 50) * 5 = 255
        assertEquals(255.0, s.catchValue(Rarity.COMMON), 0.001)
    }

    @Test
    fun `金币耗尽时购买会提前中断而不是超支`() {
        val s = GameState()
        s.money = 1e12
        var bought = 0
        while (s.buy(def("value_add_common"))) bought++
        // 每次购买都必须真的扣了钱，余额不能变负
        assertTrue("余额不应为负: ${s.money}", s.money >= 0.0)
        assertTrue("应当买到若干次: $bought", bought > 0)
        assertEquals(bought, s.owned("value_add_common"))
    }
}
