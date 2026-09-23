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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taptap.fishingidle.game.Assets
import com.taptap.fishingidle.game.GameState
import com.taptap.fishingidle.game.PurchasableDef
import com.taptap.fishingidle.game.Content
import com.taptap.fishingidle.game.formatNumber

/**
 * 商店面板：鱼苗 / 升级 / 统计 三个页签。
 * 以底部抽屉形式呈现 —— 竖屏手机上单手可及，且不遮挡上方的钓场。
 */
@Composable
fun ShopPanel(
    state: GameState,
    assets: Assets,
    onBuy: (PurchasableDef) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("鱼苗", "升级", "统计")

    Box(modifier.fillMaxSize()) {
        // 点击上半屏空白处关闭
        Box(
            Modifier
                .fillMaxSize()
                .background(UITheme.DeepWater.copy(alpha = 0.55f))
                .clickableNoRipple { onClose() }
        )

        Column(
            Modifier
                .fillMaxHeight(0.72f)
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        ) {
            WoodPanel(Modifier.fillMaxSize(), assets = assets) {
                Column(Modifier.fillMaxSize().padding(12.dp)) {
                    // 标题栏 + 页签
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        tabs.forEachIndexed { i, name ->
                            TabButton(name, i == tab, Modifier.weight(1f)) { tab = i }
                        }
                        Spacer(Modifier.width(4.dp))
                        GameButton("✕", onClose, accent = UITheme.WaterTop, fontSize = 14)
                    }

                    Spacer(Modifier.height(10.dp))

                    when (tab) {
                        0 -> ItemList(Content.fishItems, state, assets, onBuy, "鱼苗")
                        1 -> ItemList(Content.upgrades, state, assets, onBuy, "升级")
                        else -> StatsView(state)
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

/** 可购买项列表。已满足购买条件的排前面，未解锁的沉底。 */
@Composable
private fun ItemList(
    defs: List<PurchasableDef>,
    state: GameState,
    assets: Assets,
    onBuy: (PurchasableDef) -> Unit,
    emptyHint: String,
) {
    val visible = remember(state.purchases.size, state.money, defs) {
        val list = defs.filter { it.visibleWhen(state) }
        // 可买的排前面，已满级的排最后
        list.sortedWith(
            compareBy(
                { def ->
                    val owned = state.owned(def.id)
                    when {
                        def.isMaxed(owned) -> 2
                        state.money >= def.price(owned) -> 0
                        else -> 1
                    }
                },
                { def -> def.price(state.owned(def.id)) },
            )
        )
    }

    if (visible.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("还没有可用的$emptyHint\n继续钓鱼攒金币吧", color = UITheme.TextDim, fontSize = 14.sp)
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(visible, key = { it.id }) { def ->
            ShopItemRow(def, state, assets, onBuy)
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ShopItemRow(
    def: PurchasableDef,
    state: GameState,
    assets: Assets,
    onBuy: (PurchasableDef) -> Unit,
) {
    val owned = state.owned(def.id)
    val maxed = def.isMaxed(owned)
    val price = def.price(owned)
    val affordable = state.money >= price
    val buyable = !maxed && affordable && def.buyableWhen(state)

    val icon = remember(def.icon) {
        assets.scaled(def.icon, 96)?.asImageBitmap()
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    maxed -> UITheme.SlotBgOwned
                    buyable -> Color(0xFF35505A)
                    else -> UITheme.SlotBg
                }
            )
            .border(
                2.dp,
                when {
                    maxed -> UITheme.TextGood.copy(alpha = 0.6f)
                    buyable -> UITheme.Gold
                    else -> Color(0xFF4A565A)
                },
                RoundedCornerShape(10.dp),
            )
            .clickableNoRipple(buyable) { onBuy(def) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 图标
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(UITheme.DeepWater)
                .border(1.5.dp, UITheme.GoldDark.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    icon, contentDescription = def.name,
                    modifier = Modifier.size(34.dp).alpha(if (maxed) 0.55f else 1f),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        Spacer(Modifier.width(9.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    def.name,
                    color = if (maxed) UITheme.TextGood else UITheme.Cream,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (def.maxPurchases > 1) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "$owned/${def.maxPurchases}",
                        color = UITheme.TextDim,
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                describeEffect(def, state),
                color = UITheme.TextDim,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            )
        }

        Spacer(Modifier.width(8.dp))

        // 价格 / 状态
        Column(horizontalAlignment = Alignment.End) {
            if (maxed) {
                Text("已满级", color = UITheme.TextGood, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🪙", fontSize = 12.sp)
                    Spacer(Modifier.width(3.dp))
                    Text(
                        formatNumber(price),
                        color = if (affordable) UITheme.GoldLight else UITheme.TextBad,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (!affordable) {
                    Text("金币不足", color = UITheme.TextBad, fontSize = 10.sp)
                }
            }
        }
    }
}

/** 把 {n}/{v} 占位符替换成实际数值，让描述随进度变化。 */
private fun describeEffect(def: PurchasableDef, state: GameState): String {
    val n = formatNumber(def.increaseAmount)
    val v = when (def.id) {
        "common_fish" -> formatNumber(state.catchValue(com.taptap.fishingidle.game.FishKind.COMMON))
        "rare_fish" -> formatNumber(state.catchValue(com.taptap.fishingidle.game.FishKind.RARE))
        "epic_fish" -> formatNumber(state.catchValue(com.taptap.fishingidle.game.FishKind.EPIC))
        "legend_fish" -> formatNumber(state.catchValue(com.taptap.fishingidle.game.FishKind.LEGEND))
        else -> n
    }
    return def.desc.replace("{n}", n).replace("{v}", v)
}

/** 统计视图。 */
@Composable
private fun StatsView(state: GameState) {
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
                com.taptap.fishingidle.game.Source.entries.forEach { src ->
                    val amount = state.earningsBySource[src] ?: 0.0
                    if (amount > 0) {
                        StatRow(
                            src.displayName,
                            "${formatNumber(amount)}  (${(amount / total * 100).toInt()}%)",
                        )
                    }
                }
                if (state.earningsBySource.isEmpty()) {
                    Text("暂无数据", color = UITheme.TextDim, fontSize = 12.sp)
                }
            }
        }

        item {
            Column {
                SectionTitle("单次渔获价值")
                Spacer(Modifier.height(6.dp))
                com.taptap.fishingidle.game.FishKind.entries.forEach { kind ->
                    StatRow(kind.displayName, formatNumber(state.catchValue(kind)))
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}
