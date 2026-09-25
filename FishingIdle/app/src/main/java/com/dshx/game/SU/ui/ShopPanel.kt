package com.dshx.game.SU.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dshx.game.SU.game.Assets
import com.dshx.game.SU.game.CharacterDef
import com.dshx.game.SU.game.Characters
import com.dshx.game.SU.game.Content
import com.dshx.game.SU.game.Bestiary
import com.dshx.game.SU.game.DailyQuests
import com.dshx.game.SU.game.DailyTracker
import com.dshx.game.SU.game.DexReward
import com.dshx.game.SU.game.FishSize
import com.dshx.game.SU.game.FishingMap
import com.dshx.game.SU.game.KingKind
import com.dshx.game.SU.game.Rarity
import com.dshx.game.SU.game.RewardAds
import com.dshx.game.SU.game.Species
import com.dshx.game.SU.game.SpeciesLore
import com.dshx.game.SU.game.World
import com.dshx.game.SU.game.GameState
import com.dshx.game.SU.game.PurchasableDef
import com.dshx.game.SU.game.Source
import com.dshx.game.SU.game.Warehouse
import com.dshx.game.SU.game.formatNumber

/**
 * 商店面板：鱼苗 / 升级 / 水域 / 任务 / 图鉴 / 角色 / 仓库 / 统计 八个页签。
 * 以底部抽屉形式呈现 —— 竖屏手机上单手可及，且不遮挡上方的钓场。
 *
 * [revision] 是 HUD 那套刷新计数：GameState 用的是普通 var，不接这个计数
 * 组件会被 Compose 跳过，表现为"买完东西面板不更新，必须退出去再进来"。
 */
@Composable
fun ShopPanel(
    state: GameState,
    assets: Assets,
    world: World,
    revision: Int,
    onBuy: (PurchasableDef) -> Boolean,
    onUnlockMap: (FishingMap) -> Boolean,
    onUnlockCharacter: (CharacterDef) -> Boolean,
    onEquipCharacter: (CharacterDef) -> Unit,
    onSellFish: (Int) -> Unit,
    onSellAll: () -> Unit,
    onUpgradeWarehouse: () -> Unit,
    /** 仓库页「看广告免费扩容」——由宿主走激励视频，看完再免费扩一次。 */
    onAdUpgradeWarehouse: () -> Unit,
    /** 水域页「声呐探测」——看完广告立刻让一条鱼王现身。 */
    onKingSonar: () -> Unit,
    /** 角色页「钓协借调」——看完广告换取某角色的限时试用权。 */
    onTrialCharacter: (CharacterDef) -> Unit,
    /** 鱼苗页「免费钓手」——广告后永久获得一名自动钓手，每日一次。 */
    onFreeHelper: () -> Unit,
    freeHelperClaimedToday: Boolean,
    /** 升级页每日一次的免费升一级。 */
    onFreeUpgrade: () -> Unit,
    freeUpgradeClaimedToday: Boolean,
    /** 升级页「渔市分红」——一笔意外之财（资金链路）。 */
    onLottery: () -> Unit,
    /** 任务页「每日加领」——每日一次的额外签到奖励。 */
    onDailyBonus: () -> Unit,
    /** 图鉴页「稀有鱼诱饵」——确定性提升稀有度，加速集齐图鉴。 */
    onRareLure: () -> Unit,
    onClaimDex: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_EXPRESSION") revision
    var tab by remember { mutableIntStateOf(0) }
    // 「后期」不再单独占一页：鹈鹕 / 拖网 / 声呐 / 鱼探仪 / 无人机 / 潜水员 / 宝藏
    // 这些内容本身就是"继续买升级"，拆出去只会让玩家在「升级」页找不到
    // 下一步该买什么。现在它们按依赖链接在普通升级后面，同页展示。
    //
    // 页签从 9 个减到 8 个：竖屏一行排 8 个按钮，"升级"两个字才不会被挤到换行。
    val tabs = listOf("鱼苗", "升级", "水域", "任务", "图鉴", "角色", "仓库", "统计")

    // 购买反馈条：成功/失败都在面板顶部闪一下，1.4 秒后自动收起
    var flash by remember { mutableStateOf<String?>(null) }
    var flashOk by remember { mutableStateOf(true) }
    var flashId by remember { mutableIntStateOf(0) }
    // 刚买过的条目高亮一下，让玩家看清是哪一条生效了
    var justBought by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(flashId) {
        if (flashId > 0) {
            kotlinx.coroutines.delay(1400)
            flash = null
            justBought = null
        }
    }
    val purchase: (PurchasableDef) -> Unit = { def ->
        val ok = onBuy(def)
        flash = if (ok) "已购买 · ${def.name}" else "金币不足 · ${def.name}"
        flashOk = ok
        justBought = if (ok) def.id else null
        flashId++
    }
    val unlock: (FishingMap) -> Unit = { map ->
        val ok = onUnlockMap(map)
        flash = if (ok) "已解锁 · ${map.name}" else "金币不足 · ${map.name}"
        flashOk = ok
        flashId++
    }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(UITheme.DeepWater.copy(alpha = 0.55f))
                .clickableNoRipple { onClose() }
        )

        Column(
            Modifier
                .fillMaxHeight(0.78f)
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        ) {
            WoodPanel(Modifier.fillMaxSize(), assets = assets, cornerPx = 110) {
                Column(Modifier.fillMaxSize().padding(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        tabs.forEachIndexed { i, name ->
                            TabButton(name, i == tab, Modifier.weight(1f)) { tab = i }
                        }
                        Spacer(Modifier.width(3.dp))
                        GameButton("✕", onClose, accent = UITheme.WaterTop, fontSize = 14)
                    }

                    // 购买反馈条（固定高度，避免出现/消失时列表上下跳）
                    Box(Modifier.fillMaxWidth().height(26.dp), contentAlignment = Alignment.Center) {
                        val text = flash
                        if (text != null) {
                            Text(
                                text,
                                color = if (flashOk) UITheme.TextGood else UITheme.TextBad,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    when (tab) {
                        0 -> ItemList(Content.fishItems, state, assets, purchase, "鱼苗", revision, justBought,
                            headerAd = {
                                val helper = Content.byId("helper")
                                val unlocked = helper?.visibleWhen(state) == true
                                val full = helper?.isMaxed(state.owned("helper"), state) == true
                                val usedToday = freeHelperClaimedToday
                                AdActionRow(
                                    assets = assets,
                                    iconName = "icon_helper",
                                    title = "免费雇佣 1 名永久钓手",
                                    desc = when {
                                        !unlocked -> "钓到 8 条鱼解锁自动钓手"
                                        full -> "钓手已经满员"
                                        usedToday -> "今天已招募，明天再来"
                                        !RewardAds.isReady() -> "广告暂不可用"
                                        else -> "看完广告直接入队，替你自动钓鱼 · 每日一次"
                                    },
                                    enabled = unlocked && !full && !usedToday && RewardAds.isReady(),
                                    onClick = onFreeHelper,
                                )
                            },
                        )
                        // 升级页是**纯金币消耗页**，所以这里的广告点选"资金"链路：
                        // 渔市分红 = 一笔意外之财，直接解决"想买但钱不够"。
                        //
                        // 后期装备（鹈鹕/拖网/声呐/鱼探仪/无人机/潜水员/宝藏）也在这里：
                        // 它们本质就是升级，只是解锁得晚。排在普通升级后面，
                        // 顺序仍按 Content 里的依赖链（拖网→声呐→鱼探仪→…）。
                        // 下一项可解锁升级的差额直接写在按钮上，比抽象的「渔市分红」更容易理解。
                        1 -> ItemList(
                            Content.upgrades + Content.lateGame + Content.lateGame2,
                            state, assets, purchase, "升级", revision, justBought,
                            headerAd = {
                                val nextUpgrade = (Content.upgrades + Content.lateGame + Content.lateGame2)
                                    .asSequence()
                                    .filter { it.visibleWhen(state) && it.buyableWhen(state) &&
                                        !it.isMaxed(state.owned(it.id), state) && it.price(state.owned(it.id)) > state.money }
                                    .minByOrNull { it.price(state.owned(it.id)) }
                                val gap = nextUpgrade?.let { (it.price(state.owned(it.id)) - state.money).coerceAtLeast(200.0) }
                                val freeUpgrade = (Content.upgrades + Content.lateGame + Content.lateGame2)
                                    .asSequence()
                                    .filter { it.visibleWhen(state) && it.buyableWhen(state) &&
                                        !it.isMaxed(state.owned(it.id), state) }
                                    .minByOrNull { it.price(state.owned(it.id)) }
                                if (freeUpgrade != null) {
                                    AdActionRow(
                                        assets = assets,
                                        iconName = "ad_boost",
                                        title = "升级免单 · 免费升「${freeUpgrade.name}」一级",
                                        desc = when {
                                            freeUpgradeClaimedToday -> "今日已领取，明天继续"
                                            !RewardAds.isReady() -> "广告暂不可用"
                                            else -> "原价 🪙${formatNumber(freeUpgrade.price(state.owned(freeUpgrade.id)))} · 今日限领 1 次"
                                        },
                                        enabled = !freeUpgradeClaimedToday && RewardAds.isReady(),
                                        onClick = onFreeUpgrade,
                                    )
                                    Spacer(Modifier.height(7.dp))
                                }
                                AdActionRow(
                                    assets = assets,
                                    iconName = "ad_gold",
                                    title = if (nextUpgrade == null) "渔市分红 · 领一笔奖金"
                                        else "看广告 · 补齐「${nextUpgrade.name}」的金币",
                                    desc = if (!RewardAds.isReady()) "广告暂不可用"
                                    else if (gap == null) "看完立得金币，继续升级钓场"
                                    else "看完立得 🪙${formatNumber(gap)}，可购买「${nextUpgrade?.name ?: "升级"}」",
                                    enabled = RewardAds.isReady(),
                                    onClick = { onLottery() },
                                )
                            },
                        )
                        2 -> MapList(state, assets, unlock, revision, onKingSonar)
                        3 -> DailyQuestList(state, assets, revision, onDailyBonus)
                        4 -> FishDex(state, assets, revision, onClaimDex, onRareLure)
                        5 -> CharacterPanel(
                            state = state, assets = assets, revision = revision,
                            onUnlock = { def ->
                                val ok = onUnlockCharacter(def)
                                flash = if (ok) "已解锁 · ${def.name}" else "金币不足 · ${def.name}"
                                flashOk = ok
                                flashId++
                                ok
                            },
                            onEquip = { def ->
                                onEquipCharacter(def)
                                flash = "已切换为 · ${def.name}"
                                flashOk = true
                                flashId++
                            },
                            onTrial = { def ->
                                onTrialCharacter(def)
                                flash = "钓协借调中 · ${def.name}"
                                flashOk = true
                                flashId++
                            },
                        )
                        6 -> WarehousePanel(
                            state = state, assets = assets, revision = revision,
                            onSell = { idx ->
                                val now = System.currentTimeMillis()
                                val f = state.warehouse.getOrNull(idx)
                                val gain = Warehouse.sell(state, idx, state.merchantOffer?.factor ?: 1.0, now)
                                state.warehouseEarned += gain
                                if (f != null) {
                                    DailyTracker.onSold(state)
                                    flash = "已卖出 · 🪙${formatNumber(gain)}"
                                    flashOk = true
                                    flashId++
                                }
                            },
                            onSellAll = {
                                val now = System.currentTimeMillis()
                                val n = state.warehouse.size
                                val gain = Warehouse.sellAll(state, state.merchantOffer?.factor ?: 1.0, now)
                                state.warehouseEarned += gain
                                if (n > 0) DailyTracker.onSold(state, n)
                                flash = "全部卖出 · 🪙${formatNumber(gain)}"
                                flashOk = gain > 0
                                flashId++
                            },
                            onUpgrade = {
                                val ok = Warehouse.upgrade(state)
                                flash = if (ok) "仓库已扩容 · ${Warehouse.capacity(state)} 格"
                                else "金币不足 · 扩容"
                                flashOk = ok
                                flashId++
                            },
                            onAdUpgrade = onAdUpgradeWarehouse,
                            onClose = onClose,
                        )
                        else -> StatsView(state, revision)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) UITheme.Gold else UITheme.WoodDark)
            .border(2.dp, UITheme.Ink, RoundedCornerShape(8.dp))
            .clickableNoRipple { onClick() }
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) UITheme.Ink else UITheme.Cream,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 可购买项列表。可买的排前面，已满级的沉底。 */
@Composable
private fun ItemList(
    defs: List<PurchasableDef>,
    state: GameState,
    assets: Assets,
    onBuy: (PurchasableDef) -> Unit,
    emptyHint: String,
    revision: Int,
    justBought: String?,
    /** 可选的页内广告入口（整页最多 1 个，放在列表最上面）。 */
    headerAd: (@Composable () -> Unit)? = null,
) {
    // 排序只看"是否满级 + 价格"，**不看当前金币** ——
    // 钓手一直在赚钱，用金币参与排序会让列表一边看一边乱跳。
    // 买得起买不起只影响每行的配色。
    val visible = remember(revision, state.purchases.size, defs) {
        defs.filter { it.visibleWhen(state) }
            .sortedWith(
                compareBy(
                    { def -> if (def.isMaxed(state.owned(def.id), state)) 1 else 0 },
                    { def -> def.price(state.owned(def.id)) },
                )
            )
    }

    // 还没解锁的下一步：灰条列在最后，最多两条，让玩家知道该往哪走
    val locked = remember(revision, state.purchases.size, defs) {
        defs.filter { !it.visibleWhen(state) && Content.unlockHints.containsKey(it.id) }
            .take(2)
    }

    // 空列表时也要能露出广告入口（比如升级页前期全锁着），
    // 否则玩家在一个"什么都没有"的页面上找不到任何出路。
    if (visible.isEmpty() && headerAd == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "还没有可用的$emptyHint\n继续钓鱼攒金币吧",
                color = UITheme.TextDim,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (headerAd != null) {
            item { headerAd() }
        }
        if (visible.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "还没有可用的$emptyHint\n继续钓鱼攒金币吧",
                        color = UITheme.TextDim,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        items(visible, key = { it.id }) { def ->
            ShopItemRow(def, state, assets, onBuy, def.id == justBought)
        }
        if (locked.isNotEmpty()) {
            item {
                Text(
                    "即将解锁",
                    color = UITheme.TextDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                )
            }
            items(locked, key = { "locked_" + it.id }) { def ->
                LockedItemRow(def = def, hint = Content.unlockHints[def.id].orEmpty(), assets = assets)
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

/** 还没解锁的条目的灰条：显示图标、名字和"怎么解锁"。 */
@Composable
private fun LockedItemRow(def: PurchasableDef, hint: String, assets: Assets) {
    val icon = remember(def.icon) { assets.firstFrame(def.icon, 96)?.asImageBitmap() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(UITheme.SlotBg.copy(alpha = 0.5f))
            .border(1.5.dp, UITheme.TextDim.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(UITheme.DeepWater),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(icon, contentDescription = null, modifier = Modifier.size(32.dp).alpha(0.25f))
            }
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text("🔒 ${def.name}", color = UITheme.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(hint, color = UITheme.TextDim.copy(alpha = 0.8f), fontSize = 10.sp)
        }
    }
}

/**
 * 单条商品。
 * 布局：图标 | 名称+进度 / 描述 | 价格按钮。
 * 价格做成独立的按钮块，和条目其余部分视觉分离，避免之前挤成一团。
 */
@Composable
private fun ShopItemRow(
    def: PurchasableDef,
    state: GameState,
    assets: Assets,
    onBuy: (PurchasableDef) -> Unit,
    justBought: Boolean,
) {
    val owned = state.owned(def.id)
    val maxed = def.isMaxed(owned, state)
    val price = def.price(owned)
    val affordable = state.money >= price
    val buyable = !maxed && affordable && def.buyableWhen(state)

    val icon = remember(def.icon) { assets.firstFrame(def.icon, 96)?.asImageBitmap() }

    val borderColor = when {
        justBought -> UITheme.TextGood
        maxed -> UITheme.TextGood.copy(alpha = 0.65f)
        buyable -> UITheme.Gold
        else -> Color(0xFF4A565A)
    }
    val bgColor = when {
        justBought -> Color(0xFF3E6A44)
        maxed -> UITheme.SlotBgOwned
        buyable -> Color(0xFF33505C)
        else -> UITheme.SlotBg
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(if (justBought) 3.dp else 2.dp, borderColor, RoundedCornerShape(10.dp))
            // 整行可点 + 按下有缩放反馈
            .pressable(buyable, pressedScale = 0.98f) { onBuy(def) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 图标
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(UITheme.DeepWater)
                .border(1.5.dp, UITheme.GoldDark.copy(alpha = 0.55f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    icon, contentDescription = def.name,
                    modifier = Modifier.size(36.dp).alpha(if (maxed) 0.5f else 1f),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        Spacer(Modifier.width(9.dp))

        // 名称 + 描述
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    def.name,
                    color = if (maxed) UITheme.TextGood else UITheme.Cream,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (def.maxPurchases > 1) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "$owned/${def.effectiveMax(state)}",
                        color = if (justBought) UITheme.TextGood else UITheme.TextDim,
                        fontSize = 11.sp,
                        fontWeight = if (justBought) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                describeEffect(def, state),
                color = UITheme.TextDim,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(8.dp))

        // 价格按钮
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when {
                        maxed -> UITheme.TextGood.copy(alpha = 0.18f)
                        buyable -> UITheme.Gold
                        else -> Color(0xFF3A4348)
                    }
                )
                .padding(horizontal = 9.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (maxed) {
                Text("满级", color = UITheme.TextGood, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "🪙${formatNumber(price)}",
                        color = if (buyable) UITheme.Ink else UITheme.TextBad,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!affordable) {
                        Text("金币不足", color = UITheme.TextBad, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

/** 把 {n}/{v} 占位符替换成实际数值，让描述随进度变化。 */
private fun describeEffect(def: PurchasableDef, state: GameState): String {
    val n = formatNumber(def.increaseAmount)
    val v = when (def.id) {
        "common_fish" -> formatNumber(state.catchValue(Rarity.COMMON))
        "rare_fish" -> formatNumber(state.catchValue(Rarity.RARE))
        "epic_fish" -> formatNumber(state.catchValue(Rarity.EPIC))
        "legend_fish" -> formatNumber(state.catchValue(Rarity.LEGEND))
        else -> n
    }
    return def.desc.replace("{n}", n).replace("{v}", v)
}

/** 水域列表：解锁新地图，每张图的鱼价值成倍提升。 */
@Composable
private fun MapList(
    state: GameState,
    assets: Assets,
    onUnlock: (FishingMap) -> Unit,
    revision: Int,
    onKingSonar: () -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION") revision
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 「声呐探测」是水域页**唯一**的广告入口（硬约束：单页最多 1 个）。
        // 鱼王本来是随机 + 要等，这里卖的是确定性。
        item {
            AdActionRow(
                assets = assets,
                iconName = "ad_sonar",
                title = "声呐探测 · 锁定鱼王",
                desc = when {
                    !state.sonarOwned -> "装上声呐才能探鱼王（先去「升级」页买）"
                    RewardAds.isReady() -> "立刻探到一条鱼王并让它现身"
                    else -> "广告接入中"
                },
                enabled = RewardAds.isReady() && state.sonarOwned,
                onClick = onKingSonar,
            )
        }
        items(Bestiary.maps, key = { it.id }) { map ->
            val unlocked = state.unlockedMaps.contains(map.id)
            val current = state.currentMapId == map.id
            val affordable = state.money >= map.unlockCost
            val canUnlock = !unlocked && affordable

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            current -> Color(0xFF3A5A32)
                            unlocked -> UITheme.SlotBgOwned
                            canUnlock -> Color(0xFF33505C)
                            else -> UITheme.SlotBg
                        }
                    )
                    .border(
                        2.dp,
                        when {
                            current -> UITheme.TextGood
                            unlocked -> UITheme.TextGood.copy(alpha = 0.5f)
                            canUnlock -> UITheme.Gold
                            else -> Color(0xFF4A565A)
                        },
                        RoundedCornerShape(10.dp),
                    )
                    .pressable(canUnlock || (unlocked && !current), pressedScale = 0.98f) {
                        if (unlocked || canUnlock) onUnlock(map)
                    }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            map.name,
                            color = if (unlocked) UITheme.Cream else UITheme.TextDim,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(6.dp))
                        if (current) Pill("当前", UITheme.TextGood)
                        else if (unlocked) Pill("已解锁", UITheme.TextGood.copy(alpha = 0.7f))
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(map.desc, color = UITheme.TextDim, fontSize = 11.sp, lineHeight = 14.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "价值 ×${formatNumber(map.valueMultiplier)}  ·  ${map.species.size} 种鱼",
                        color = UITheme.GoldLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                current -> UITheme.TextGood.copy(alpha = 0.2f)
                                canUnlock -> UITheme.Gold
                                else -> Color(0xFF3A4348)
                            }
                        )
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        current -> Text("所在", color = UITheme.TextGood, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        unlocked -> Text("前往", color = UITheme.TextGood, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "🪙${formatNumber(map.unlockCost)}",
                                color = if (canUnlock) UITheme.Ink else UITheme.TextBad,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            if (!affordable) Text("金币不足", color = UITheme.TextBad, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

/** 每日任务列表。每天 3 条，完成后自动入账。 */
@Composable
private fun DailyQuestList(
    state: GameState,
    assets: Assets,
    revision: Int,
    onDailyBonus: () -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION") revision
    val quests = state.todayQuests

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 任务页的广告点是「每日加领」：和任务一样是"每天来一趟"的节奏，
        // 是留存钩子而不是爆发式收益。
        item {
            AdActionRow(
                assets = assets,
                iconName = "ad_daily",
                title = "每日加领 · 额外一份",
                desc = if (RewardAds.isReady()) "今日签到奖励再领一份（每天一次）"
                else "广告接入中",
                enabled = RewardAds.isReady(),
                onClick = onDailyBonus,
            )
        }
        item {
            Column {
                SectionTitle("今日任务")
                Spacer(Modifier.height(4.dp))
                Text(
                    "每天 0 点刷新 · 完成 ${state.dailyDone.size}/${quests.size}",
                    color = UITheme.TextDim,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(6.dp))
                // 图鉴收集进度的永久加成，给玩家一个长期目标
                val dexMul = DexReward.multiplier(state)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(UITheme.TextGood.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("📖", fontSize = 15.sp)
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "图鉴加成 ×${String.format("%.2f", dexMul)}",
                            color = UITheme.TextGood,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        val (label, _) = DexReward.nextMilestone(state)
                        Text(label, color = UITheme.TextDim, fontSize = 10.sp)
                    }
                    Text(
                        "${state.caughtSpecies.size}/${Bestiary.totalSpecies}",
                        color = UITheme.TextGood, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        items(quests, key = { it.id }) { q ->
            val done = state.dailyDone.contains(q.id)
            val progress = DailyQuests.progress(state, q)
            val current = q.track(state.dailyProgress)

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (done) UITheme.SlotBgOwned else UITheme.SlotBg)
                    .border(
                        2.dp,
                        if (done) UITheme.TextGood.copy(alpha = 0.7f) else Color(0xFF4A565A),
                        RoundedCornerShape(10.dp),
                    )
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(30.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (done) "✅" else "⬜", fontSize = 20.sp)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        q.name,
                        color = if (done) UITheme.TextGood else UITheme.Cream,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(q.desc, color = UITheme.TextDim, fontSize = 11.sp)
                    Spacer(Modifier.height(5.dp))
                    // 进度条
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF2A3438)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (done) UITheme.TextGood else UITheme.Gold),
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${formatNumber(current)} / ${formatNumber(q.goal)}",
                        color = UITheme.TextDim, fontSize = 10.sp,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "🪙${formatNumber(q.reward)}",
                    color = if (done) UITheme.TextGood else UITheme.GoldLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

/** 鱼类图鉴：54 种鱼的收集册，按地图分组。 */
@Composable
private fun FishDex(
    state: GameState,
    assets: Assets,
    revision: Int,
    onClaimDex: () -> Unit,
    onRareLure: () -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION") revision
    val caught = state.caughtSpecies
    // 点某一条鱼 → 展开资料页
    var detail by remember { mutableStateOf<Species?>(null) }
    val lureLeft = state.buffRemain(GameState.Buff.RARE_LURE)
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 图鉴页的核心痛点是"差几种就集齐"，而稀有度完全随机 ——
        // 这里卖的正是确定性：稀有鱼诱饵。
        item {
            AdActionRow(
                assets = assets,
                iconName = "ad_bait",
                title = "稀有鱼诱饵 · 提升稀有度",
                desc = if (lureLeft > 0f) "诱饵生效中，剩余 ${formatBuffTime(lureLeft)}"
                else if (RewardAds.isReady()) "撒下诱饵，稀有鱼出现率大增"
                else "广告接入中",
                enabled = RewardAds.isReady(),
                onClick = onRareLure,
            )
        }
        item {
            Column {
                SectionTitle("收集进度")
                Spacer(Modifier.height(4.dp))
                Text(
                    "${caught.size} / ${Bestiary.totalSpecies} 种",
                    color = UITheme.GoldLight,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))

                // 可领取的收集里程碑：这是"点领取"的爽点所在
                val claimable = DexReward.claimable(state)
                if (claimable.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(UITheme.Gold.copy(alpha = 0.22f))
                            .border(2.dp, UITheme.Gold, RoundedCornerShape(10.dp))
                            .pressable { onClaimDex() }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("🎁", fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "有 ${claimable.size} 个收集奖励可领",
                                color = UITheme.GoldLight,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "合计 🪙${formatNumber(claimable.sumOf { it.money })}" +
                                    if (claimable.any { it.pearls > 0 }) " + 珍珠" else "",
                                color = UITheme.TextNormal,
                                fontSize = 10.sp,
                            )
                        }
                        Text("领取 ›", color = UITheme.GoldLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                } else {
                    // 没有可领的：显示下一个里程碑的进度，给个盼头
                    DexReward.nextLocked(state)?.let { next ->
                        val have = caught.size
                        val frac = (have.toFloat() / next.species).coerceIn(0f, 1f)
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                "下一档奖励：集齐 ${next.species} 种",
                                color = UITheme.TextDim, fontSize = 10.sp,
                            )
                            Spacer(Modifier.height(3.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(UITheme.DeepWater),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(frac)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(UITheme.Gold),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        Bestiary.maps.forEach { map ->
            item(key = "hdr_${map.id}") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(width = 4.dp, height = 15.dp).background(UITheme.Gold, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(7.dp))
                    Text(map.name, color = UITheme.Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${map.species.count { caught.contains(it.id) }}/${map.species.size}",
                        color = UITheme.TextDim, fontSize = 11.sp,
                    )
                }
            }
            items(map.species, key = { it.id }) { sp ->
                SpeciesRow(
                    sp = sp,
                    isCaught = caught.contains(sp.id),
                    assets = assets,
                    bestSize = state.bestSizeOf(sp.id),
                    onClick = { detail = sp },
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    detail?.let { sp ->
        SpeciesDetail(
            sp = sp,
            isCaught = caught.contains(sp.id),
            bestSize = state.bestSizeOf(sp.id),
            inWater = state.currentMap.species.any { it.id == sp.id },
            currentMapName = state.currentMap.name,
            assets = assets,
            onClose = { detail = null },
        )
    }
}

/**
 * 单条鱼的资料页。
 *
 * 没钓到的鱼也允许点开 —— 显示剪影、只露稀有度和出没水域，
 * 留个"还差什么"的钩子，比整条灰掉更有收集欲。
 *
 * ⚠️ 用 Dialog 承载，不能直接当 [FishDex] 的兄弟节点平铺：
 * 面板底部抽屉的高度是 78% 屏高，把 fillMaxSize 的详情页排在 LazyColumn
 * 后面时会被排到抽屉可视区之外，表现为"点了没反应"。
 * Dialog 自带独立窗口层，一定盖在面板之上。
 */
@Composable
private fun SpeciesDetail(
    sp: Species,
    isCaught: Boolean,
    bestSize: FishSize,
    inWater: Boolean,
    currentMapName: String,
    assets: Assets,
    onClose: () -> Unit,
) {
    val fish = remember(sp.sprite) { assets.firstFrame(sp.sprite, 320)?.asImageBitmap() }
    val rarity = when (sp.rarity) {
        Rarity.COMMON -> UITheme.RarityCommon
        Rarity.RARE -> UITheme.RarityRare
        Rarity.EPIC -> UITheme.RarityEpic
        Rarity.LEGEND -> UITheme.RarityLegend
    }
    val maps = Bestiary.maps.filter { m -> m.species.any { it.id == sp.id } }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xCC000000))
                .clickableNoRipple { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .width(320.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(UITheme.PanelBg)
                    .border(2.dp, rarity.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "#${sp.dexNo} · ${if (isCaught) sp.name else "？？？"}",
                    color = if (isCaught) rarity else UITheme.TextDim,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                // 鱼图：撑满整行宽度，按高度适配，别缩成一个小点
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(UITheme.DeepWater),
                    contentAlignment = Alignment.Center,
                ) {
                    if (fish != null) {
                        Image(
                            fish, contentDescription = sp.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp)
                                .alpha(if (isCaught) 1f else 0.22f),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                DetailRow("稀有度", "${sp.rarity.displayName} · ${sp.tint.display}")
                DetailRow("最大体型", if (isCaught) bestSize.label else "未记录")
                DetailRow("体价值", "×${String.format("%.2f", sp.valueMul)}")
                // 说清楚是**游戏里的哪几张地图**，不能只写"这片水域"
                DetailRow("出没水域", maps.joinToString("、") { it.name })
                DetailRow(
                    "当前所在",
                    "「${currentMapName}」" + if (inWater) " · 有" else " · 没有",
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (isCaught) SpeciesLore.of(sp)
                    else "还没钓到过它。去「${maps.firstOrNull()?.name ?: "水域"}」碰碰运气。",
                    color = UITheme.TextNormal,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                GameButton(
                    text = "关闭", onClick = onClose,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    accent = UITheme.Gold, fontSize = 15,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = UITheme.TextDim, fontSize = 12.sp)
        Text(value, color = UITheme.GoldLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SpeciesRow(
    sp: Species,
    isCaught: Boolean,
    assets: Assets,
    bestSize: FishSize,
    onClick: () -> Unit,
) {
    val icon = remember(sp.sprite) { assets.firstFrame(sp.sprite, 128)?.asImageBitmap() }
    val rarity = when (sp.rarity) {
        Rarity.COMMON -> UITheme.RarityCommon
        Rarity.RARE -> UITheme.RarityRare
        Rarity.EPIC -> UITheme.RarityEpic
        Rarity.LEGEND -> UITheme.RarityLegend
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(UITheme.SlotBg)
            .border(1.5.dp, rarity.copy(alpha = if (isCaught) 0.7f else 0.2f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(UITheme.DeepWater),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    icon, contentDescription = sp.name,
                    modifier = Modifier.size(50.dp).alpha(if (isCaught) 1f else 0.18f),
                    contentScale = ContentScale.Fit,
                    colorFilter = if (isCaught) null else null,
                )
            }
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (isCaught) sp.name else "？？？",
                color = if (isCaught) rarity else UITheme.TextDim,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${sp.rarity.displayName} · ${sp.tint.display}",
                    color = UITheme.TextDim,
                    fontSize = 10.sp,
                )
                // 体型纪录徽章：钓到过"大只"以上才显示
                if (isCaught && bestSize != FishSize.NORMAL) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        bestSize.label,
                        color = UITheme.GoldLight,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(UITheme.Gold.copy(alpha = 0.22f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
        }
        Text(
            "#${sp.dexNo}",
            color = if (isCaught) rarity else UITheme.TextDim.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 统计视图。 */
@Composable
private fun StatsView(state: GameState, revision: Int) {
    @Suppress("UNUSED_EXPRESSION") revision
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                SectionTitle("财务")
                Spacer(Modifier.height(6.dp))
                StatRow("当前金币", formatNumber(state.money), UITheme.GoldLight)
                StatRow("累计收入", formatNumber(state.totalMoney))
                StatRow("历史最高", formatNumber(state.highestMoney), UITheme.GoldLight)
                StatRow("单次最高渔获", formatNumber(state.highestCatch), UITheme.TextGood)
            }
        }

        item {
            Column {
                SectionTitle("钓场规模")
                Spacer(Modifier.height(6.dp))
                StatRow("小鱼", "${state.commonFish} 条")
                StatRow("鲤鱼", "${state.rareFish} 条")
                StatRow("锦鲤", "${state.epicFish} 条")
                StatRow("巨口鱼", "${state.legendFish} 条")
                StatRow("自动钓手", "${state.helpers} 名")
                StatRow("珍珠", "${state.pearls} 颗", UITheme.GoldLight)
                StatRow("已转生", "${state.prestigeCount} 次")
                StatRow("打开宝箱", "${state.chestsOpened} 个")
                StatRow("拽上鱼王", "${state.kingsCaught} 条", UITheme.GoldLight)
                StatRow(
                    "体型纪录",
                    "${state.bestSize.count { it.value > 0 }} 种有纪录",
                )
            }
        }

        item {
            Column {
                SectionTitle("仓库与角色")
                Spacer(Modifier.height(6.dp))
                StatRow("仓库容量", "${state.warehouse.size} / ${Warehouse.capacity(state)} 格")
                StatRow(
                    "仓库市值",
                    formatNumber(Warehouse.marketTotal(state.warehouse, System.currentTimeMillis())),
                    UITheme.GoldLight,
                )
                StatRow("卖鱼累计收入", formatNumber(state.warehouseEarned))
                StatRow("鱼贩到访", "${state.merchantVisits} 次")
                StatRow("当前角色", state.currentCharacter.name, UITheme.GoldLight)
                StatRow("已解锁角色", "${state.ownedCharacters.size} / ${Characters.all.size} 人")
                StatRow("鱼王类型", "${KingKind.entries.size} 种")
            }
        }

        item {
            Column {
                SectionTitle("收益构成")
                Spacer(Modifier.height(6.dp))
                val total = state.earningsBySource.values.sum().coerceAtLeast(1.0)
                Source.entries.forEach { src ->
                    val amount = state.earningsBySource[src] ?: 0.0
                    if (amount > 0) {
                        StatRow(src.displayName, "${formatNumber(amount)}  (${(amount / total * 100).toInt()}%)")
                    }
                }
                if (state.earningsBySource.isEmpty()) {
                    Text("暂无数据", color = UITheme.TextDim, fontSize = 12.sp)
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}
