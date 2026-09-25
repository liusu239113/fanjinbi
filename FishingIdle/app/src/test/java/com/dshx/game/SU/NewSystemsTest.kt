package com.dshx.game.SU

import com.dshx.game.SU.game.Characters
import com.dshx.game.SU.game.DexReward
import com.dshx.game.SU.game.GameState
import com.dshx.game.SU.game.KingKind
import com.dshx.game.SU.game.RodSkill
import com.dshx.game.SU.game.StoredFish
import com.dshx.game.SU.game.Warehouse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 换装 / 仓库 / 图鉴里程碑 / 鱼王类型 的回归测试。
 *
 * 这几块都是新增系统，最容易出的问题是"数值配错但界面看着正常"，
 * 所以这里守住最关键的不变量。
 */
class NewSystemsTest {

    // ---------------- 换装 ----------------

    @Test
    fun `角色素材与技能都不重复`() {
        val ids = Characters.all.map { it.id }
        assertEquals("角色 id 不能重复", ids.size, ids.distinct().size)

        val sprites = Characters.all.map { it.sprite }
        assertEquals("每个角色必须有独立素材", sprites.size, sprites.distinct().size)

        // 初始角色必须默认拥有，否则玩家开局没有角色可用
        assertTrue("默认主角要预置", Characters.defaultPlayer.owned)
        assertTrue("默认帮手要预置", Characters.defaultHelper.owned)
    }

    @Test
    fun `主角都有鱼竿技能且帮手只做辅助`() {
        Characters.players.forEach { p ->
            assertNotNull("${p.name} 缺鱼竿技能", p.rodSkill)
        }
        // 帮手不该抢主角的核心玩法技能
        Characters.helpers.forEach { h ->
            assertTrue(
                "${h.name} 不该用主角专属技能 ${h.rodSkill}",
                h.rodSkill == RodSkill.BALANCED || h.rodSkill == RodSkill.HELPER_BOOST ||
                    h.rodSkill == RodSkill.LUCKY_ROD,
            )
        }
    }

    @Test
    fun `解锁角色要扣钱_重复解锁不生效`() {
        val s = GameState()
        s.money = 1_000_000.0
        val def = Characters.players.first { !it.owned }

        assertTrue("钱够应当能解锁", s.unlockCharacter(def))
        val after = s.money
        assertTrue("应当扣掉解锁费", after < 1_000_000.0)
        assertFalse("已拥有不能再解锁", s.unlockCharacter(def))
        assertEquals("重复解锁不该再扣钱", after, s.money, 0.001)
    }

    @Test
    fun `换装后当前角色生效且分主角帮手`() {
        val s = GameState()
        s.money = 1e12
        val helper = Characters.helpers.first { !it.owned }
        s.unlockCharacter(helper)
        s.equipCharacter(helper)
        assertEquals("帮手要切到 currentHelperId", helper.id, s.currentHelperId)
        assertEquals("主角不该被帮手顶掉", Characters.defaultPlayer.id, s.currentCharacterId)
    }

    // ---------------- 仓库 ----------------

    @Test
    fun `仓库满时入库失败_不吞玩家渔获`() {
        val s = GameState()
        val cap = Warehouse.capacity(s)
        repeat(cap) { i ->
            assertTrue(
                "第 $i 条应当能入库",
                Warehouse.store(s, StoredFish("baitiao", 0, 100.0, 0L, false)),
            )
        }
        assertTrue("到容量上限了", Warehouse.isFull(s))
        assertFalse(
            "满了必须返回 false，让调用方折现",
            Warehouse.store(s, StoredFish("baitiao", 0, 100.0, 0L, false)),
        )
    }

    @Test
    fun `扩容增加容量并扣钱`() {
        val s = GameState()
        s.money = 1e12
        val before = Warehouse.capacity(s)
        val price = Warehouse.upgradePrice(s)
        assertTrue(Warehouse.upgrade(s))
        assertEquals("容量要增加", before + Warehouse.CAPACITY_STEP, Warehouse.capacity(s))
        assertEquals("要扣掉扩容费", 1e12 - price, s.money, 0.001)
    }

    @Test
    fun `卖出仓库的鱼按倍率结算`() {
        val s = GameState()
        val now = System.currentTimeMillis()
        Warehouse.store(s, StoredFish("baitiao", 0, 1000.0, now, false))
        val market = Warehouse.marketValue(s.warehouse[0], now)

        val before = s.money
        val gain = Warehouse.sell(s, 0, 1.5, now)
        assertEquals("按 1.5 倍结算", market * 1.5, gain, 0.001)
        assertEquals("钱要入账", before + gain, s.money, 0.001)
        assertTrue("卖完仓库该空了", s.warehouse.isEmpty())
    }

    @Test
    fun `行情倍率在合理区间且按天稳定`() {
        val day = 20_000L * 86_400_000L
        val f1 = Warehouse.marketFactor(day)
        val f2 = Warehouse.marketFactor(day + 3_600_000L) // 同一天
        assertTrue("行情要在 0.7~1.3 之间", f1 in 0.7..1.3)
        assertEquals("同一天行情应当稳定", f1, f2, 0.0001)
    }

    @Test
    fun `鱼贩报价有高低起伏`() {
        // 跑足够多次，必须同时出现压价和大赚，否则"赌"的感觉不成立
        var low = 0
        var high = 0
        val rnd = kotlin.random.Random(42)
        repeat(2000) {
            val o = Warehouse.rollOffer(rnd)
            if (o.factor < 1.0) low++
            if (o.factor > 1.2) high++
        }
        assertTrue("应当有压价的情况", low > 100)
        assertTrue("应当有高价收购的情况", high > 50)
    }

    // ---------------- 图鉴里程碑 ----------------

    @Test
    fun `图鉴里程碑达成后可领取且不重复领`() {
        val s = GameState()
        val first = DexReward.milestones.first()
        // 还没集齐：不能领
        assertFalse("没达成不能领", DexReward.claim(s, first))

        // 集齐到第一档
        repeat(first.species) { i -> s.caughtSpecies.add("sp_$i") }
        assertTrue("达成后应当出现在可领列表", DexReward.claimable(s).contains(first))

        val before = s.money
        assertTrue("应当能领取", DexReward.claim(s, first))
        assertEquals("奖励要入账", before + first.money, s.money, 0.001)
        assertFalse("不能重复领", DexReward.claim(s, first))
        assertFalse("领过就不在可领列表", DexReward.claimable(s).contains(first))
    }

    @Test
    fun `全部领取一次拿到所有已达成里程碑`() {
        val s = GameState()
        // 集齐到第 3 档
        val third = DexReward.milestones[2]
        repeat(third.species) { i -> s.caughtSpecies.add("sp_$i") }

        val before = s.money
        val got = DexReward.claimAll(s)
        val expected = DexReward.milestones.take(3).sumOf { it.money }
        assertEquals("应当一次领完前三档", expected, got, 0.001)
        assertEquals("钱要入账", before + expected, s.money, 0.001)
        assertTrue("领完不该还有可领的", DexReward.claimable(s).isEmpty())
    }

    // ---------------- 鱼王 ----------------

    @Test
    fun `鱼王类型各有独立素材与差异化的难度奖励`() {
        val kinds = KingKind.entries
        assertTrue("鱼王至少要有 3 种", kinds.size >= 3)

        val sprites = kinds.map { it.sprite }
        assertEquals("每种鱼王必须有独立素材", sprites.size, sprites.distinct().size)

        val rewards = kinds.map { it.rewardMult }
        assertEquals("奖励倍率不能重复", rewards.size, rewards.distinct().size)

        // 奖励越高的鱼王，停留时间应当越短（难度与收益挂钩）
        kinds.forEach { k ->
            if (k.rewardMult > 2.0) {
                assertTrue("${k.displayName} 奖励高，停留时间就该短", k.lifeMult < 1.0f)
            }
        }
    }
}
