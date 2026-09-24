package com.taptap.fishingidle.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taptap.fishingidle.game.Assets
import com.taptap.fishingidle.game.Content
import com.taptap.fishingidle.game.Bestiary
import com.taptap.fishingidle.game.DailyQuests
import com.taptap.fishingidle.game.DexReward
import com.taptap.fishingidle.game.FishingMap
import com.taptap.fishingidle.game.Rarity
import com.taptap.fishingidle.game.Species
import com.taptap.fishingidle.game.World
import com.taptap.fishingidle.game.GameState
import com.taptap.fishingidle.game.PurchasableDef
import com.taptap.fishingidle.game.Source
import com.taptap.fishingidle.game.formatNumber

/**
 * 商店面板：鱼苗 / 升级 / 水域 / 图鉴 / 统计 四个页签。
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
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_EXPRESSION") revision
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("鱼苗", "升级", "水域", "任务", "图鉴", "统计")

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
                        0 -> ItemList(Content.fishItems, state, assets, purchase, "鱼苗", revision, justBought)
                        1 -> ItemList(Content.upgrades, state, assets, purchase, "升级", revision, justBought)
                        2 -> MapList(state, assets, unlock, revision)
                        3 -> DailyQuestList(state, revision)
                        4 -> FishDex(state, assets, revision)
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

    if (visible.isEmpty()) {
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
        items(visible, key = { it.id }) { def ->
            ShopItemRow(def, state, assets, onBuy, def.id == justBought)
        }
        item { Spacer(Modifier.height(8.dp)) }
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
) {
    @Suppress("UNUSED_EXPRESSION") revision
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
private fun DailyQuestList(state: GameState, revision: Int) {
    @Suppress("UNUSED_EXPRESSION") revision
    val quests = state.todayQuests

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
private fun FishDex(state: GameState, assets: Assets, revision: Int) {
    @Suppress("UNUSED_EXPRESSION") revision
    val caught = state.caughtSpecies
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
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
                SpeciesRow(sp, caught.contains(sp.id), assets)
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SpeciesRow(sp: Species, isCaught: Boolean, assets: Assets) {
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
            .padding(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(UITheme.DeepWater),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    icon, contentDescription = sp.name,
                    modifier = Modifier.size(36.dp).alpha(if (isCaught) 1f else 0.18f),
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
            Text(
                "${sp.rarity.displayName} · ${sp.tint.display}",
                color = UITheme.TextDim,
                fontSize = 10.sp,
            )
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
