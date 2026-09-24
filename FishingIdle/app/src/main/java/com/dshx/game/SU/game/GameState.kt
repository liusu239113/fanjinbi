package com.dshx.game.SU.game

/**
 * 全局游戏状态。所有成长数值都通过「重放购买次数」重建，
 * 这样存档只需要保存 purchases 计数，与原版设计一致。
 */
class GameState {

    // ---- 金钱 ----
    var money: Double = 0.0
        set(value) {
            val delta = value - field
            if (delta > 0) totalMoney += delta
            field = value
            if (value > highestMoney) highestMoney = value
        }

    var totalMoney: Double = 0.0
        private set
    var highestMoney: Double = 0.0
        private set
    var highestCatch: Double = 0.0
        private set

    // ---- 鱼群数量 ----
    // 初始就放一批小鱼，否则开局水面上只有一两条，钓场看着空荡荡
    var commonFish: Int = 6
    var rareFish: Int = 0
    var epicFish: Int = 0
    var legendFish: Int = 0
    var helpers: Int = 0

    // ---- 收益加成 ----
    var commonValueAdd: Double = 0.0
    var rareValueAdd: Double = 0.0
    var epicValueAdd: Double = 0.0
    var legendValueAdd: Double = 0.0

    var commonValueMul: Double = 1.0
    var rareValueMul: Double = 1.0
    var epicValueMul: Double = 1.0
    var legendValueMul: Double = 1.0

    // ---- 收线速度倍率 ----
    var commonReelSpeed: Double = 1.0
    var rareReelSpeed: Double = 1.0
    var epicReelSpeed: Double = 1.0
    var legendReelSpeed: Double = 1.0

    // ---- 解锁标记 ----
    var autoReelChance: Double = 0.0        // 自动重抛概率（对应原版重翻）
    var autoReelUnlocked: Boolean = false   // 自动收线：咬钩后自动开始收线
    var autoCastUnlocked: Boolean = false   // 自动抛竿：空闲时自己找鱼下竿
    var pelicanOwned: Boolean = false       // 后期单位：鹈鹕（专叼大鱼）
    var netOwned: Boolean = false           // 后期技能：拖网（一次捞一网）
    var sonarOwned: Boolean = false         // 声呐：稀有鱼显形 + 咬钩更快
    var fishFinderOwned: Boolean = false    // 鱼探仪：抛竿落点自动吸鱼
    var droneOwned: Boolean = false         // 无人机：强化自动抛竿
    var diverOwned: Boolean = false         // 潜水员：定期下潜捞珍珠
    var treasureOwned: Boolean = false      // 沉船宝藏：河面定时浮出宝箱
    var helperCanRare: Boolean = false
    var helperCanEpic: Boolean = false
    var helperCanLegend: Boolean = false
    var chainReaction: Boolean = false
    var helperEfficiency: Double = 1.0

    // ---- 进阶成长（后期主要数值来源）----
    /** 所有鱼的价值倍率加成。 */
    var rarityMul: Double = 0.0
    /** 当前水域的额外价值倍率。 */
    var mapBonus: Double = 0.0
    /** 钓手划船速度加成。 */
    var helperSpeed: Double = 0.0
    /** 每名钓手可同时照看的鱼数（在基础上叠加）。 */
    var helperParallel: Int = 0
    /** 连击每层额外加成。 */
    var comboPower: Double = 0.0
    /** 脱钩时保留的连击比例 0~1。 */
    var comboKeep: Double = 0.0
    /** 自动重抛的收线速度加成。 */
    var autoReelSpeed: Double = 0.0
    /** 高稀有度鱼出现概率加成。 */
    var luckyHook: Double = 0.0

    // ---- 转生 ----
    /** 珍珠：转生货币，用于升级技能树，转生不会清空。 */
    var pearls: Long = 0

    /** 已转生次数。 */
    var prestigeCount: Int = 0

    /** 技能等级：技能 id → 已投入级数。转生不清空。 */
    val skillLevels: MutableMap<String, Int> = mutableMapOf()

    // ---- 技能树算出的加成（由 SkillTree.applyAll 维护）----
    var skillValueBonus: Double = 0.0
    var skillReelBonus: Double = 0.0
    var skillEscapeReduce: Double = 0.0
    var skillStartMoney: Double = 0.0
    var skillHelperBonus: Double = 0.0
    var skillFishCapacity: Int = 0
    var skillComboStep: Double = 0.04
    var skillPearlBonus: Double = 0.0
    var skillRareWeightBonus: Double = 0.0

    // ---- 技能树：后期内容分支 ----
    /** 「深海打捞」：潜水员下潜周期缩短比例。 */
    var skillDiverSpeed: Double = 0.0
    /** 「寻宝达人」：宝箱间隔缩短比例。 */
    var skillChestSpeed: Double = 0.0
    /** 「寻宝达人」：宝箱金币加成。 */
    var skillChestValue: Double = 0.0
    /** 「无人机编队」：自动抛竿间隔再缩短比例。 */
    var skillDroneSpeed: Double = 0.0
    /** 「鱼王克星」：鱼王拉力与奖励加成。 */
    var skillKingPower: Double = 0.0
    var skillGlobalBonus: Double = 0.0

    /** 技能带来的总收益倍率。 */
    val skillValueMultiplier: Double
        get() = (1.0 + skillValueBonus) * (1.0 + skillGlobalBonus)

    /** 技能带来的总收线速度倍率（含广告的收线加速 buff）。 */
    val skillReelMultiplier: Double
        get() = (1.0 + skillReelBonus) * (1.0 + skillGlobalBonus) * reelRushMultiplier

    /** 技能带来的钓手效率倍率（含广告的钓手加速 buff）。 */
    val skillHelperMultiplier: Double
        get() = (1.0 + skillHelperBonus) * (1.0 + skillGlobalBonus) * helperRushMultiplier

    fun skillLevel(id: String): Int = skillLevels[id] ?: 0

    // ---- 购买记录（成长数据的唯一真相）----
    val purchases: MutableMap<String, Int> = mutableMapOf()

    // ---- 会话统计（不存档）----
    val earningsBySource: MutableMap<Source, Double> = mutableMapOf()
    val earningsByFish: MutableMap<Rarity, Double> = mutableMapOf()

    // ---- 进度与成就 ----
    /** 累计钓上来的鱼数量（成就用）。 */
    var totalCatches: Long = 0
        private set

    /** 已解锁成就 id。 */
    val unlockedAchievements: MutableSet<String> = mutableSetOf()

    /** 已解锁的水域 id。 */
    val unlockedMaps: MutableSet<String> = mutableSetOf("creek")

    /** 当前所在水域 id。 */
    var currentMapId: String = "creek"

    /** 已钓到过的鱼种 id（图鉴收集进度）。 */
    val caughtSpecies: MutableSet<String> = mutableSetOf()

    /** 每个鱼种钓到过的最大体型（存 ordinal）。 */
    val bestSize: MutableMap<String, Int> = mutableMapOf()

    /** 后期玩法计数。 */
    var chestsOpened: Long = 0
    var kingsCaught: Long = 0

    /** 刚首次钓到、还没弹过提示的鱼种，由 UI 消费。 */
    val pendingDexUnlocks: MutableList<String> = mutableListOf()

    /** 上次离开的时间戳（毫秒）。启动时据此结算离线收益。 */
    var lastSeenMillis: Long = 0L

    // ---- 换装（角色）----

    /** 已拥有的角色 id。 */
    val ownedCharacters: MutableSet<String> = mutableSetOf(
        Characters.defaultPlayer.id,
        Characters.defaultHelper.id,
    )

    /** 当前使用的角色 id。 */
    var currentCharacterId: String = Characters.defaultPlayer.id

    /** 当前使用的帮手形象 id。 */
    var currentHelperId: String = Characters.defaultHelper.id

    /** 当前主角。 */
    val currentCharacter: CharacterDef
        get() = Characters.byId(currentCharacterId) ?: Characters.defaultPlayer

    /** 当前帮手形象。 */
    val currentHelper: CharacterDef
        get() = Characters.byId(currentHelperId) ?: Characters.defaultHelper

    /** 是否已拥有某角色。 */
    fun ownsCharacter(id: String): Boolean = ownedCharacters.contains(id)

    /**
     * 解锁一个角色。金币不够返回 false。
     * 珍珠角色走 [unlockCharacterWithPearls]。
     */
    fun unlockCharacter(def: CharacterDef): Boolean {
        if (ownsCharacter(def.id)) return false
        if (def.pearlPrice > 0) {
            if (pearls < def.pearlPrice) return false
            pearls -= def.pearlPrice
        } else {
            if (money < def.price) return false
            money -= def.price
        }
        ownedCharacters.add(def.id)
        return true
    }

    /** 切换使用的角色（必须已拥有）。 */
    fun equipCharacter(def: CharacterDef): Boolean {
        if (!ownsCharacter(def.id)) return false
        if (def.isHelper) currentHelperId = def.id else currentCharacterId = def.id
        return true
    }

    // ---- 仓库（渔获收藏 / 行情 / 鱼贩）----

    /** 仓库里的鱼。新鱼种与破纪录的鱼入库，普通重复鱼直接折算金币。 */
    val warehouse: MutableList<StoredFish> = mutableListOf()

    /** 已购买的扩容次数。 */
    var warehouseUpgrades: Int = 0

    /** 鱼贩下一次到访的倒计时（秒）。不存档，重启就重置。 */
    var merchantTimer: Float = Warehouse.MERCHANT_INTERVAL

    /** 当前鱼贩报价；null = 没有鱼贩在。 */
    var merchantOffer: MerchantOffer? = null

    /** 鱼贩剩余停留时间（秒）。 */
    var merchantStay: Float = 0f

    /** 累计卖出渔获的金额（统计用）。 */
    var warehouseEarned: Double = 0.0

    /** 鱼贩来过的次数（统计用）。 */
    var merchantVisits: Int = 0

    // ---- 每日任务 ----
    /** 当前是第几天（UTC 天数），用于判断是否要重置。 */
    var dailyDayIndex: Long = DailyQuests.currentDayIndex()
    /** 当日进度。 */
    val dailyProgress: DailyProgress = DailyProgress()
    /** 当日已完成的任务 id。 */
    val dailyDone: MutableSet<String> = mutableSetOf()

    /** 今日的三个任务。 */
    val todayQuests: List<DailyQuest> get() = DailyQuests.forDay(dailyDayIndex)

    /** 今日未完成的任务数（用于 HUD 红点）。 */
    val pendingQuestCount: Int
        get() = todayQuests.count { !dailyDone.contains(it.id) }

    /** 当前水域。 */
    val currentMap: FishingMap
        get() = Bestiary.mapById(currentMapId) ?: Bestiary.maps.first()

    /** 当前连击数：连续成功收线不脱钩会累加，脱钩清零。 */
    var combo: Int = 0
    /** 历史最高连击。 */
    var bestCombo: Int = 0
        private set

    /**
     * 连击收益加成。步长 = 技能「连击之势」+ 升级「行云流水」，上限 30 层。
     * 角色「大力收线」会让连击加成翻倍。
     */
    val comboMultiplier: Double
        get() = 1.0 + (combo.coerceAtMost(30) * (skillComboStep + comboPower) *
            characterComboBonus)

    /**
     * 全局价值倍率。
     * 三个来源相乘：进阶升级（稀有度/水域）× 图鉴收集进度。
     * 图鉴加成是**永久**的，转生也不清空，这是长线收集的动力。
     */
    val globalValueMultiplier: Double
        get() = (1.0 + rarityMul) * (1.0 + mapBonus) * DexReward.multiplier(this) *
            prestigeMultiplier * doubleIncomeMultiplier

    // ---- 广告限时 buff（看广告获得的加成）----
    //
    // 统一用一个「类型 → 剩余秒数」的表管理，[tickBuffs] 每帧推进。
    // 全部**不存档**：限时加成重启后清零，否则等于把 10 分钟 buff 变成永久 buff。
    // 离线期间照样流逝（[OfflineEarnings] 结算时按离开时长扣），这是设计意图 ——
    // 「下线也计时」才逼玩家在线时把 buff 用足，也是广告复投的动力。

    private val buffs: MutableMap<String, Float> = mutableMapOf()
    private val buffTotals: MutableMap<String, Float> = mutableMapOf()

    /** buff 类型常量。 */
    object Buff {
        /** 双倍收益：所有渔获 ×2。 */
        const val DOUBLE_INCOME = "double_income"

        /** 稀有鱼诱饵：高稀有度鱼出现率大增。 */
        const val RARE_LURE = "rare_lure"

        /** 钓手加速：自动钓手产出 ×2。 */
        const val HELPER_RUSH = "helper_rush"

        /** 收线加速：收线速度 ×2。 */
        const val REEL_RUSH = "reel_rush"

        /** 图鉴加成翻倍：图鉴收集加成 ×2。 */
        const val DEX_BOOST = "dex_boost"
    }

    /** 某类 buff 剩余秒数。 */
    fun buffRemain(kind: String): Float = buffs[kind] ?: 0f

    /** 某类 buff 总时长（用于画进度条）。 */
    fun buffTotal(kind: String): Float = buffTotals[kind] ?: 0f

    /** 某类 buff 是否激活。 */
    fun buffActive(kind: String): Boolean = buffRemain(kind) > 0f

    /** 是否有任何 buff 在跑（HUD 用来决定要不要显示 buff 条）。 */
    val anyBuffActive: Boolean get() = buffs.values.any { it > 0f }

    /** 当前所有激活中的 buff（类型 → 剩余秒），按剩余时间倒序。 */
    fun activeBuffs(): List<Pair<String, Float>> =
        buffs.entries.filter { it.value > 0f }
            .sortedByDescending { it.value }
            .map { it.key to it.value }

    /**
     * 激活/延长一段 buff。
     * 同类 buff 取**较长的剩余时间**而不是叠加 —— 叠加会让连看多条广告
     * 滚出一个超长 buff，数值直接失控。
     */
    fun activateBuff(kind: String, seconds: Float) {
        buffs[kind] = maxOf(buffs[kind] ?: 0f, seconds)
        buffTotals[kind] = maxOf(buffTotals[kind] ?: 0f, seconds)
    }

    /** 推进所有 buff 计时。 */
    fun tickBuffs(dt: Float) {
        if (buffs.isEmpty()) return
        val it = buffs.iterator()
        while (it.hasNext()) {
            val e = it.next()
            val left = e.value - dt
            if (left <= 0f) {
                it.remove()
                buffTotals.remove(e.key)
            } else {
                e.setValue(left)
            }
        }
    }

    /**
     * 离线期间扣掉 buff 时长。
     * 玩家下线时 buff 照样在流逝 —— 不这么做的话，睡前看一条 10 分钟广告，
     * 早上起来还是满的，等于白送永久 buff。
     */
    fun decayBuffsOffline(elapsedSeconds: Long) {
        tickBuffs(elapsedSeconds.toFloat())
    }

    /** 双倍收益倍率。 */
    val doubleIncomeMultiplier: Double
        get() = if (buffActive(Buff.DOUBLE_INCOME)) 2.0 else 1.0

    /** 钓手加速倍率。 */
    val helperRushMultiplier: Double
        get() = if (buffActive(Buff.HELPER_RUSH)) 2.0 else 1.0

    /** 收线加速倍率。 */
    val reelRushMultiplier: Double
        get() = if (buffActive(Buff.REEL_RUSH)) 2.0 else 1.0

    /** 稀有鱼诱饵加成（叠加到 luckyHook 上）。 */
    val rareLureBonus: Double
        get() = if (buffActive(Buff.RARE_LURE)) 2.0 else 0.0

    /** 图鉴加成倍率（激活时图鉴带来的加成本身翻倍）。 */
    val dexBoostMultiplier: Double
        get() = if (buffActive(Buff.DEX_BOOST)) 2.0 else 1.0

    /**
     * 看广告拿到的「离线收益翻倍」是否已就绪。
     * 不存档：单次消费型道具。
     */
    var offlineDoubleReady: Boolean = false

    /** 看广告拿到的「转生珍珠 +50%」是否已就绪（转生时消费）。 */
    var prestigeBoostReady: Boolean = false

    /** 消费掉翻倍权益，返回本次是否应该翻倍。 */
    fun consumeOfflineDouble(): Boolean {
        val v = offlineDoubleReady
        offlineDoubleReady = false
        return v
    }

    /** 消费掉转生加成权益。 */
    fun consumePrestigeBoost(): Boolean {
        val v = prestigeBoostReady
        prestigeBoostReady = false
        return v
    }

    // ---- 转生 ----

    /**
     * 转生次数带来的永久收益加成：每转生一次 +10%。
     *
     * 光靠珍珠换技能，玩家要花掉珍珠才看得到变化，"清空一切"的代价显得很亏；
     * 这条加成是转生**当场**就到手、且不会被任何操作回收的，让转生本身有意义。
     */
    val prestigeMultiplier: Double
        get() = 1.0 + prestigeCount * PRESTIGE_VALUE_STEP

    /** 本次转生可获得多少珍珠（含看广告的转生加速，由调用方先消费）。 */
    fun pendingPearls(): Long {
        val base = Prestige.pearlsFor(totalMoney)
        if (base <= 0) return 0
        val boost = if (prestigeBoostReady) 1.5 else 1.0
        return (base * (1.0 + skillPearlBonus) * boost).toLong().coerceAtLeast(1)
    }

    /** 是否可以转生。 */
    fun canPrestige(): Boolean = pendingPearls() > 0

    /**
     * 执行转生：清空金币、鱼群、钓手、普通升级与地图进度，
     * 换取珍珠并保留技能树。返回本次获得的珍珠数。
     */
    fun doPrestige(): Long {
        val gain = pendingPearls()
        if (gain <= 0) return 0

        pearls += gain
        prestigeCount++

        // 重置本轮进度
        money = skillStartMoney          // 技能「开局红利」给启动资金
        totalMoney = 0.0
        highestMoney = 0.0
        highestCatch = 0.0
        combo = 0
        purchases.clear()
        unlockedMaps.clear()
        unlockedMaps.add(Bestiary.maps.first().id)
        currentMapId = Bestiary.maps.first().id
        resetAttributes()
        // 技能效果在重置后再套一遍（resetAttributes 不会动技能字段）
        SkillTree.applyAll(this)
        money = skillStartMoney
        return gain
    }

    fun recordEarning(kind: Rarity, source: Source, amount: Double) {
        earningsBySource[source] = (earningsBySource[source] ?: 0.0) + amount
        earningsByFish[kind] = (earningsByFish[kind] ?: 0.0) + amount
    }

    fun recordCatch(amount: Double) {
        if (amount > highestCatch) highestCatch = amount
    }

    /** 成功钓上一条鱼：累计计数 + 连击推进。 */
    fun onCatchSuccess() {
        totalCatches++
        combo++
        if (combo > bestCombo) bestCombo = combo
    }

    /**
     * 脱钩/超时。「稳如磐石」可以让玩家保留一部分连击层数，
     * 减少手滑一次的惩罚。
     */
    fun onCatchFail() {
        combo = (combo * comboKeep).toInt().coerceAtLeast(0)
    }

    /**
     * 某稀有度档位的单次渔获价值。
     * 对应原版 (base + additional) * multiplier，再乘上技能树加成。
     */
    fun catchValue(kind: Rarity): Double {
        val base = when (kind) {
            Rarity.COMMON -> (BASE_COMMON + commonValueAdd) * commonValueMul
            Rarity.RARE -> (BASE_RARE + rareValueAdd) * rareValueMul
            Rarity.EPIC -> (BASE_EPIC + epicValueAdd) * epicValueMul
            Rarity.LEGEND -> (BASE_LEGEND + legendValueAdd) * legendValueMul
        }
        return base * skillValueMultiplier * globalValueMultiplier
    }

    /**
     * 具体鱼种的价值 = 稀有度基础价值 × 该鱼种倍率 × 地图倍率。
     * 地图倍率是长线成长的主轴。
     */
    fun catchValue(sp: Species, map: FishingMap = currentMap): Double =
        catchValue(sp.rarity) * sp.valueMul * map.valueMultiplier

    /** 解锁一张地图。返回是否成功。 */
    fun unlockMap(map: FishingMap): Boolean {
        if (unlockedMaps.contains(map.id)) {
            currentMapId = map.id
            return true
        }
        if (money < map.unlockCost) return false
        money -= map.unlockCost
        unlockedMaps.add(map.id)
        currentMapId = map.id
        return true
    }

    /** 钓手当前是否能钓到该稀有度的鱼（由「钓手进阶」类升级解锁）。 */
    fun helperCanCatch(rarity: Rarity): Boolean = when (rarity) {
        Rarity.COMMON -> true
        Rarity.RARE -> helperCanRare
        Rarity.EPIC -> helperCanEpic
        Rarity.LEGEND -> helperCanLegend
    }

    /** 离线收益结算后记账：计入总收入与渔获数，但不算连击。 */
    fun recordOfflineEarnings(catches: Int) {
        totalCatches += catches.toLong()
    }

    /**
     * 一次成功的渔获后的统一记账入口。
     * 把「累计统计」「连击」「每日任务进度」集中处理，
     * 避免各处调用点漏埋。
     */
    fun onCatchRecorded(rarity: Rarity, value: Double) {
        onCatchSuccess()
        DailyTracker.onCatch(this, rarity, value)
        DailyTracker.onCombo(this)
    }

    /** 每个鱼种的最佳体型（没有记录时是 NORMAL）。 */
    fun bestSizeOf(speciesId: String): FishSize =
        FishSize.entries.getOrElse(bestSize[speciesId] ?: 0) { FishSize.NORMAL }

    /** 记录一次体型。返回是否**刷新了纪录**（用于弹提示）。 */
    fun recordSize(speciesId: String, size: FishSize): Boolean {
        val old = bestSize[speciesId] ?: 0
        if (size.ordinal <= old) return false
        bestSize[speciesId] = size.ordinal
        return true
    }

    /** 记录钓到某个鱼种。返回是否为**首次**发现（用于弹图鉴提示）。 */
    fun recordSpecies(id: String): Boolean {
        val isNew = caughtSpecies.add(id)
        if (isNew) {
            // 图鉴进度提升会立刻反映到全局倍率，这里记一笔待弹出的提示
            pendingDexUnlocks.add(id)
        }
        return isNew
    }

    /** 某鱼种收线耗时倍率（越大越快），含技能加成与角色鱼竿技能。 */
    fun reelSpeed(kind: Rarity): Double {
        val base = when (kind) {
            Rarity.COMMON -> commonReelSpeed
            Rarity.RARE -> rareReelSpeed
            Rarity.EPIC -> epicReelSpeed
            Rarity.LEGEND -> legendReelSpeed
        }
        return base * skillReelMultiplier * characterReelBonus
    }

    /**
     * 角色鱼竿技能带来的收线加成。
     * 「均衡」小幅提速；「大力收线」大幅提速。
     */
    val characterReelBonus: Double
        get() = when (currentCharacter.rodSkill) {
            RodSkill.BALANCED -> 1.0 + World.BALANCED_REEL_BONUS
            RodSkill.POWER_REEL -> 1.0 + World.POWER_REEL_BONUS
            else -> 1.0
        }

    /** 角色鱼竿技能带来的连击加成倍率（「大力收线」翻倍）。 */
    val characterComboBonus: Double
        get() = if (currentCharacter.rodSkill == RodSkill.POWER_REEL) 2.0 else 1.0

    /** 帮手形象带来的产出加成（「勤勉」）。 */
    val helperCharacterBonus: Double
        get() = if (currentHelper.rodSkill == RodSkill.HELPER_BOOST) {
            1.0 + World.HELPER_BOOST_BONUS
        } else {
            1.0
        }

    /** 该鱼种是否已解锁（拥有至少一条）。 */
    fun isUnlocked(kind: Rarity): Boolean = when (kind) {
        Rarity.COMMON -> commonFish > 0
        Rarity.RARE -> rareFish > 0
        Rarity.EPIC -> epicFish > 0
        Rarity.LEGEND -> legendFish > 0
    }

    fun ownedCount(kind: Rarity): Int = when (kind) {
        Rarity.COMMON -> commonFish
        Rarity.RARE -> rareFish
        Rarity.EPIC -> epicFish
        Rarity.LEGEND -> legendFish
    }

    // ---- 购买 ----

    /** 应用一次购买效果。重放存档时也会走这里。 */
    fun applyPurchase(def: PurchasableDef) {
        purchases[def.id] = (purchases[def.id] ?: 0) + 1
        val amount = def.increaseAmount
        when (def.attribute) {
            Attribute.COMMON_FISH -> commonFish += amount.toInt()
            Attribute.RARE_FISH -> rareFish += amount.toInt()
            Attribute.EPIC_FISH -> epicFish += amount.toInt()
            Attribute.LEGEND_FISH -> legendFish += amount.toInt()
            Attribute.HELPER -> helpers += amount.toInt()

            Attribute.COMMON_VALUE_ADD -> commonValueAdd += amount
            Attribute.RARE_VALUE_ADD -> rareValueAdd += amount
            Attribute.EPIC_VALUE_ADD -> epicValueAdd += amount
            Attribute.LEGEND_VALUE_ADD -> legendValueAdd += amount

            Attribute.COMMON_VALUE_MUL -> commonValueMul += amount
            Attribute.RARE_VALUE_MUL -> rareValueMul += amount
            Attribute.EPIC_VALUE_MUL -> epicValueMul += amount
            Attribute.LEGEND_VALUE_MUL -> legendValueMul += amount

            Attribute.COMMON_REEL_SPEED -> commonReelSpeed += amount
            Attribute.RARE_REEL_SPEED -> rareReelSpeed += amount
            Attribute.EPIC_REEL_SPEED -> epicReelSpeed += amount
            Attribute.LEGEND_REEL_SPEED -> legendReelSpeed += amount

            Attribute.HELPER_EFFICIENCY -> helperEfficiency += amount
            Attribute.AUTO_REEL_CHANCE -> autoReelChance += amount

            Attribute.AUTO_REEL_UNLOCK -> autoReelUnlocked = true
            Attribute.AUTO_CAST_UNLOCK -> autoCastUnlocked = true
            Attribute.PELICAN -> pelicanOwned = true
            Attribute.NET_SWEEP -> netOwned = true
            Attribute.SONAR -> sonarOwned = true
            Attribute.FISH_FINDER -> fishFinderOwned = true
            Attribute.DRONE -> droneOwned = true
            Attribute.DIVER -> diverOwned = true
            Attribute.TREASURE -> treasureOwned = true
            Attribute.HELPER_CAN_RARE -> helperCanRare = true
            Attribute.HELPER_CAN_EPIC -> helperCanEpic = true
            Attribute.HELPER_CAN_LEGEND -> helperCanLegend = true
            Attribute.CHAIN_REACTION -> chainReaction = true

            // 进阶成长
            Attribute.RARITY_MUL -> rarityMul += amount
            Attribute.MAP_BONUS -> mapBonus += amount
            Attribute.HELPER_SPEED -> helperSpeed += amount
            Attribute.HELPER_PARALLEL -> helperParallel += amount.toInt()
            Attribute.COMBO_POWER -> comboPower += amount
            Attribute.COMBO_KEEP -> comboKeep += amount
            Attribute.AUTO_REEL_SPEED -> autoReelSpeed += amount
            Attribute.LUCKY_HOOK -> luckyHook += amount
        }
    }

    fun owned(defId: String): Int = purchases[defId] ?: 0

    /** 购买：校验 → 扣钱 → 应用效果。返回是否成功。 */
    fun buy(def: PurchasableDef): Boolean {
        val ownedCount = owned(def.id)
        if (def.isMaxed(ownedCount, this)) return false
        if (!def.buyableWhen(this)) return false
        val p = def.price(ownedCount)
        if (money < p) return false
        money -= p
        applyPurchase(def)
        return true
    }

    // ---- 存档 ----

    /** 把当日进度同步到存档对象。 */
    private fun SaveData.syncDaily(from: GameState) {
        dailyDayIndex = from.dailyDayIndex
        dailyDone = from.dailyDone.toMutableSet()
        dailyCatches = from.dailyProgress.catches
        dailyMoney = from.dailyProgress.moneyEarned
        dailyBestCombo = from.dailyProgress.bestCombo
        dailyRareCatches = from.dailyProgress.rareCatches
        dailyMapChanges = from.dailyProgress.mapChanges
        dailyHelpersBought = from.dailyProgress.helpersBought
        dailyChests = from.dailyProgress.chests
        dailyKings = from.dailyProgress.kings
        dailyCasts = from.dailyProgress.casts
        dailyPurchases = from.dailyProgress.anyPurchase
        dailyNewSpecies = from.dailyProgress.newSpecies
        dailyStored = from.dailyProgress.stored
        dailySold = from.dailyProgress.sold
    }

    /** 从存档恢复当日进度。 */
    private fun GameState.restoreDaily(from: SaveData) {
        dailyDayIndex = from.dailyDayIndex
        dailyDone.clear()
        dailyDone.addAll(from.dailyDone)
        dailyProgress.catches = from.dailyCatches
        dailyProgress.moneyEarned = from.dailyMoney
        dailyProgress.bestCombo = from.dailyBestCombo
        dailyProgress.rareCatches = from.dailyRareCatches
        dailyProgress.mapChanges = from.dailyMapChanges
        dailyProgress.helpersBought = from.dailyHelpersBought
        dailyProgress.chests = from.dailyChests
        dailyProgress.kings = from.dailyKings
        dailyProgress.casts = from.dailyCasts
        dailyProgress.anyPurchase = from.dailyPurchases
        dailyProgress.newSpecies = from.dailyNewSpecies
        dailyProgress.stored = from.dailyStored
        dailyProgress.sold = from.dailySold
    }

    fun toSave(): SaveData = SaveData().also {
        it.money = money
        it.totalMoney = totalMoney
        it.highestMoney = highestMoney
        it.highestCatch = highestCatch
        it.purchases = purchases.toMutableMap()
        it.pearls = pearls
        it.prestigeCount = prestigeCount
        it.skillLevels = skillLevels.toMutableMap()
        it.bestCombo = bestCombo
        it.totalCatches = totalCatches
        it.unlockedAchievements = unlockedAchievements.toMutableSet()
        it.caughtSpecies = caughtSpecies.toMutableSet()
        it.bestSize = bestSize.toMutableMap()
        it.chestsOpened = chestsOpened
        it.kingsCaught = kingsCaught
        it.unlockedMaps = unlockedMaps.toMutableSet()
        it.currentMapId = currentMapId
        it.syncDaily(this)
    }

    /**
     * 彻底重置到全新开局：金钱归零、购买清空、图鉴与成就清空，
     * 只保留玩家设置（音量等由 Settings 单独管理）。
     */
    fun resetAll() {
        money = 0.0
        totalMoney = 0.0
        highestMoney = 0.0
        highestCatch = 0.0
        purchases.clear()
        pearls = 0
        prestigeCount = 0
        skillLevels.clear()
        bestCombo = 0
        combo = 0
        totalCatches = 0
        unlockedAchievements.clear()
        caughtSpecies.clear()
        unlockedMaps.clear()
        unlockedMaps.add(Bestiary.maps.first().id)
        currentMapId = Bestiary.maps.first().id
        resetAttributes()
        SkillTree.applyAll(this)
    }

    /**
     * 从存档重建。
     *
     * 顺序很重要：先恢复跨轮进度（珍珠/技能/图鉴），再套用技能加成，
     * 最后重放购买 —— 因为购买产生的属性会与技能倍率相乘，
     * 顺序反了会导致数值对不上。
     */
    fun loadFrom(data: SaveData) {
        lastSeenMillis = data.lastSeenMillis
        restoreDaily(data)
        // 跨天则清空当日进度
        DailyQuests.rolloverIfNeeded(this)
        pearls = data.pearls
        prestigeCount = data.prestigeCount
        skillLevels.clear()
        skillLevels.putAll(data.skillLevels)
        bestCombo = data.bestCombo
        totalCatches = data.totalCatches
        unlockedAchievements.clear()
        unlockedAchievements.addAll(data.unlockedAchievements)
        caughtSpecies.clear()
        caughtSpecies.addAll(data.caughtSpecies)
        bestSize.clear()
        bestSize.putAll(data.bestSize)
        chestsOpened = data.chestsOpened
        kingsCaught = data.kingsCaught
        unlockedMaps.clear()
        if (data.unlockedMaps.isEmpty()) {
            unlockedMaps.add(Bestiary.maps.first().id)
        } else {
            unlockedMaps.addAll(data.unlockedMaps)
        }
        currentMapId = data.currentMapId.ifEmpty { Bestiary.maps.first().id }

        // ---- 换装 ----
        ownedCharacters.clear()
        ownedCharacters.add(Characters.defaultPlayer.id)
        ownedCharacters.add(Characters.defaultHelper.id)
        ownedCharacters.addAll(data.ownedCharacters)
        currentCharacterId = data.currentCharacterId.ifEmpty { Characters.defaultPlayer.id }
        currentHelperId = data.currentHelperId.ifEmpty { Characters.defaultHelper.id }

        // ---- 仓库 ----
        warehouse.clear()
        for (i in data.warehouseSpecies.indices) {
            warehouse.add(
                StoredFish(
                    speciesId = data.warehouseSpecies.getOrElse(i) { "" },
                    sizeOrdinal = data.warehouseSize.getOrElse(i) { 0 },
                    baseValue = data.warehouseValue.getOrElse(i) { 0.0 },
                    storedAt = data.warehouseStoredAt.getOrElse(i) { 0L },
                    firstCatch = data.warehouseFirstCatch.getOrElse(i) { false },
                )
            )
        }
        warehouseUpgrades = data.warehouseUpgrades
        warehouseEarned = data.warehouseEarned
        merchantVisits = data.merchantVisits
        merchantTimer = Warehouse.MERCHANT_INTERVAL
        merchantOffer = null
        merchantStay = 0f

        money = data.money
        totalMoney = data.totalMoney
        highestMoney = data.highestMoney
        highestCatch = data.highestCatch

        resetAttributes()
        purchases.clear()

        // 按定义顺序重放购买，重建所有属性
        for (def in Content.purchasables) {
            val count = data.purchases[def.id] ?: 0
            repeat(count) { applyPurchase(def) }
        }

        // 技能加成最后套，保证它作用在重放后的属性上
        SkillTree.applyAll(this)
    }

    private fun resetAttributes() {
        commonFish = INITIAL_COMMON_FISH
        rareFish = 0; epicFish = 0; legendFish = 0; helpers = 0
        commonValueAdd = 0.0; rareValueAdd = 0.0; epicValueAdd = 0.0; legendValueAdd = 0.0
        commonValueMul = 1.0; rareValueMul = 1.0; epicValueMul = 1.0; legendValueMul = 1.0
        commonReelSpeed = 1.0; rareReelSpeed = 1.0; epicReelSpeed = 1.0; legendReelSpeed = 1.0
        helperEfficiency = 1.0
        rarityMul = 0.0; mapBonus = 0.0; helperSpeed = 0.0; helperParallel = 0
        comboPower = 0.0; comboKeep = 0.0; autoReelSpeed = 0.0; luckyHook = 0.0
        autoReelChance = 0.0
        autoReelUnlocked = false
        autoCastUnlocked = false
        pelicanOwned = false
        netOwned = false
        sonarOwned = false
        fishFinderOwned = false
        droneOwned = false
        diverOwned = false
        treasureOwned = false
        helperCanRare = false; helperCanEpic = false; helperCanLegend = false
        chainReaction = false
    }

    companion object {
        /** 开局送的小鱼数量，让钓场一开始就有生气。 */
        const val INITIAL_COMMON_FISH = 6

        /** 每次转生永久增加的全局收益比例。 */
        const val PRESTIGE_VALUE_STEP = 0.10

        const val BASE_COMMON = 1.0
        const val BASE_RARE = 20.0
        const val BASE_EPIC = 300.0
        const val BASE_LEGEND = 5000.0
    }
}
