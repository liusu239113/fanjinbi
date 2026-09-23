package com.taptap.fishingidle

import com.taptap.fishingidle.game.Content
import com.taptap.fishingidle.game.Rarity
import com.taptap.fishingidle.game.GameState
import com.taptap.fishingidle.game.SaveData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(1, s.commonFish)
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

        // helper: base=1.45, mult=10
        val h = def("helper")
        assertEquals(10.0, h.price(0), 0.001)
        assertEquals(14.0, h.price(1), 0.001)    // floor(14.5)
        assertEquals(21.0, h.price(2), 0.001)    // floor(21.025)
    }

    @Test
    fun `一次性解锁价格恒定`() {
        val d = def("auto_reel_unlock")
        assertEquals(1000.0, d.price(0), 0.001)
        assertEquals(1000.0, d.price(5), 0.001)
        assertEquals(1, d.maxPurchases)
    }

    @Test
    fun `金币不足时购买失败`() {
        val s = GameState()
        s.money = 1.0
        assertFalse(s.buy(def("common_fish")))
        assertEquals(1.0, s.money, 0.001)
        assertEquals(1, s.commonFish)
    }

    @Test
    fun `购买扣除金币并生效`() {
        val s = GameState()
        s.money = 100.0
        assertTrue(s.buy(def("common_fish")))
        assertEquals(98.0, s.money, 0.001)
        assertEquals(2, s.commonFish)
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
        assertEquals(8, restored.commonFish)
    }

    @Test
    fun `空存档不会崩溃`() {
        val s = GameState()
        s.loadFrom(SaveData())
        assertEquals(1, s.commonFish)
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
