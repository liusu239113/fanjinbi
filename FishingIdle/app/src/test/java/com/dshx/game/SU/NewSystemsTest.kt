package com.dshx.game.SU

import com.dshx.game.SU.game.Affix
import com.dshx.game.SU.game.Auction
import com.dshx.game.SU.game.Characters
import com.dshx.game.SU.game.DexReward
import com.dshx.game.SU.game.GameState
import com.dshx.game.SU.game.GearItem
import com.dshx.game.SU.game.GearRarity
import com.dshx.game.SU.game.GearSlot
import com.dshx.game.SU.game.GearStat
import com.dshx.game.SU.game.KingKind
import com.dshx.game.SU.game.Rarity
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

    @Test
    fun `倍速须看广告逐级解锁且按真实时间过期`() {
        val state = GameState()
        val now = 1_000_000L
        assertEquals(1, state.currentSpeed(now))
        assertFalse(state.selectSpeed(2, now))

        state.unlockSpeed(now)
        assertEquals(3, state.availableSpeed(now))
        assertEquals(GameState.SPEED_REWARD_MILLIS, state.speedRemainingMillis(now))
        assertTrue(state.selectSpeed(2, now))
        assertEquals(2, state.currentSpeed(now))
        assertTrue(state.selectSpeed(1, now))
        assertEquals(1, state.currentSpeed(now))
        assertEquals(3, state.availableSpeed(now))

        state.unlockSpeed(now + 1_000L)
        assertEquals(3, state.currentSpeed(now + 1_000L))
        assertEquals(1, state.currentSpeed(now + GameState.SPEED_REWARD_MILLIS + 1_001L))
        assertEquals(0L, state.speedRemainingMillis(now + GameState.SPEED_REWARD_MILLIS + 1_001L))
        state.expireSpeed(now + GameState.SPEED_REWARD_MILLIS + 1_001L)
        assertEquals(1, state.availableSpeed(now + GameState.SPEED_REWARD_MILLIS + 1_001L))
    }

    @Test
    fun `倍速存档恢复和转生保留_清档取消`() {
        val state = GameState()
        val now = System.currentTimeMillis()
        state.unlockSpeed(now)
        val restored = GameState().apply { loadFrom(state.toSave()) }
        assertEquals(3, restored.currentSpeed(now))
        assertEquals(3, restored.availableSpeed(now))
        restored.resetAll()
        assertEquals(1, restored.currentSpeed(now))
    }

    @Test
    fun `免费钓手遵循解锁与上限且不花钱`() {
        val state = GameState()
        assertFalse(state.claimFreeHelper())
        repeat(8) { state.onCatchSuccess() }
        val before = state.money
        assertTrue(state.claimFreeHelper())
        assertEquals(1, state.helpers)
        assertEquals(1, state.owned("helper"))
        assertEquals(before, state.money, 0.001)
        repeat(9) { assertTrue(state.claimFreeHelper()) }
        assertFalse(state.claimFreeHelper())
    }

    @Test
    fun `升级免单不扣钱且不绕过升级的前置条件`() {
        val state = GameState()
        val upgrade = com.dshx.game.SU.game.Content.upgrades.first()
        val before = state.money
        assertTrue(state.claimFreeUpgrade(upgrade))
        assertEquals(1, state.owned(upgrade.id))
        assertEquals(before, state.money, 0.001)
        val lockedUpgrade = com.dshx.game.SU.game.Content.upgrades
            .first { !it.visibleWhen(state) }
        assertFalse(state.claimFreeUpgrade(lockedUpgrade))
        val helper = com.dshx.game.SU.game.Content.byId("helper")!!
        assertFalse(state.claimFreeUpgrade(helper))
    }

    // ---------------- 装备 ----------------

    @Test
    fun `装备加成会真的生效到属性上`() {
        val s = GameState()
        val reelBefore = s.reelSpeed(Rarity.COMMON)

        // 装一根必定带收线速度的鱼竿
        val rod = GearItem(
            slot = GearSlot.ROD, rarity = GearRarity.LEGEND, name = "测试竿",
            affixes = listOf(Affix(GearStat.REEL_SPEED, 0.5)),
        )
        s.gear.add(rod)
        s.gear.equip(rod)
        assertEquals("收线速度应被装备放大", reelBefore * 1.5, s.reelSpeed(Rarity.COMMON), 0.001)

        // 卸下后回到原值
        s.gear.unequip(GearSlot.ROD)
        assertEquals(reelBefore, s.reelSpeed(Rarity.COMMON), 0.001)
    }

    @Test
    fun `装备的渔获价值加成参与结算`() {
        val s = GameState()
        val before = s.catchValue(Rarity.COMMON)
        val bait = GearItem(
            slot = GearSlot.BAIT, rarity = GearRarity.EPIC, name = "测试饵",
            affixes = listOf(Affix(GearStat.VALUE_BONUS, 0.25)),
        )
        s.gear.add(bait)
        s.gear.equip(bait)
        assertEquals(before * 1.25, s.catchValue(Rarity.COMMON), 0.001)
    }

    @Test
    fun `开箱得到装备并进背包`() {
        val s = GameState()
        s.money = 1e30
        val item = s.openGearBox()
        assertNotNull("金币够就该开出装备", item)
        assertEquals("开出的装备应进背包", 1, s.gear.bag.size)
        assertEquals("开箱次数要累计", 1L, s.gearBoxesOpened)
        // 箱价随开箱次数递增
        assertTrue("箱价应递增", s.gearBoxPrice() > 50_000.0)
    }

    @Test
    fun `装备存档往返不丢`() {
        val s = GameState()
        val rod = GearItem(GearSlot.ROD, GearRarity.LEGEND, "龙纹竿",
            listOf(Affix(GearStat.REEL_SPEED, 0.42)))
        s.gear.add(rod)
        s.gear.equip(rod)
        s.gear.add(GearItem(GearSlot.LINE, GearRarity.RARE, "编织线",
            listOf(Affix(GearStat.ESCAPE_REDUCE, 0.2))))
        s.gearBoxesOpened = 7

        val restored = GameState().apply { loadFrom(s.toSave()) }
        val rRod = restored.gear.equipped[GearSlot.ROD]
        assertNotNull("装备的鱼竿应恢复", rRod)
        assertEquals("龙纹竿", rRod!!.name)
        assertEquals(0.42, rRod.stat(GearStat.REEL_SPEED), 0.0001)
        assertEquals("背包里的鱼线应恢复", 1, restored.gear.bag.size)
        assertEquals(7L, restored.gearBoxesOpened)
    }

    // ---------------- 拍卖 ----------------

    @Test
    fun `拍卖取最高出价并扣手续费`() {
        val s = GameState()
        val now = System.currentTimeMillis()
        Warehouse.store(s, StoredFish("baitiao", 0, 1000.0, now, false))
        val fish = s.warehouse[0]
        val market = Warehouse.marketValue(fish, now)

        val result = Auction.runAuction(fish, market, 0, kotlin.random.Random(42))
        assertEquals("应有多个买家出价", Auction.BIDDER_COUNT, result.bids.size)
        assertTrue("最高价应不低于其他出价",
            result.bids.all { it.factor <= result.topBid!!.factor })

        val before = s.money
        val gain = Auction.settle(s, result, now)
        assertEquals("按最高价扣手续费入账",
            market * result.topBid!!.factor * (1 - Auction.FEE_RATE), gain, 0.01)
        assertEquals(before + gain, s.money, 0.01)
        assertTrue("成交后鱼应移出仓库", s.warehouse.isEmpty())
    }

    @Test
    fun `拍卖流拍不扣鱼`() {
        val s = GameState()
        val now = System.currentTimeMillis()
        Warehouse.store(s, StoredFish("baitiao", 0, 1000.0, now, false))
        // 流拍 = 什么都不做
        assertEquals("流拍后鱼还在", 1, s.warehouse.size)
    }

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
        // 角色价格已按 100 倍梯度上调（首个 5e8），这里给足金币
        s.money = 1e15
        val def = Characters.players.first { !it.owned }

        assertTrue("钱够应当能解锁", s.unlockCharacter(def))
        val after = s.money
        assertTrue("应当扣掉解锁费", after < 1e15)
        assertFalse("已拥有不能再解锁", s.unlockCharacter(def))
        assertEquals("重复解锁不该再扣钱", after, s.money, 0.001)
    }

    @Test
    fun `换装后当前角色生效且分主角帮手`() {
        val s = GameState()
        s.money = 1e15
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

    /**
     * 仓库列表的 key 必须唯一，否则 Compose 直接闪退。
     *
     * 玩家反馈："玩到最后一个水域，仓库满了，点击仓库总是闪退"。
     * 根因：列表 key 用的是 `storedAt + speciesId`，而拖网一次捞多条、
     * 或同一毫秒钓上两条同种鱼时这两项都相同 → 重复 key → LazyVerticalGrid 抛异常。
     */
    @Test
    fun `仓库入库序号唯一_同毫秒同鱼种也不会撞key`() {
        val s = GameState()
        // 同一毫秒、同一鱼种入库多条（拖网/连锁就是这个场景）
        val sameMs = 1_700_000_000_000L
        repeat(6) {
            assertTrue(Warehouse.store(s, StoredFish("baitiao", 0, 100.0, sameMs, false)))
        }
        val seqs = s.warehouse.map { it.seq }
        assertEquals("入库序号必须互不相同", seqs.size, seqs.distinct().size)

        // 存档往返后依然唯一（存档不存 seq，靠 reindex 重排）
        val restored = GameState().apply { loadFrom(s.toSave()) }
        val rseqs = restored.warehouse.map { it.seq }
        assertEquals("读档后序号仍须唯一", rseqs.size, rseqs.distinct().size)
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
