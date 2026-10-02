package com.dshx.game.SU.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dshx.game.SU.game.Assets
import com.dshx.game.SU.game.GameState
import com.dshx.game.SU.game.GearItem
import com.dshx.game.SU.game.GearLoadout
import com.dshx.game.SU.game.GearRarity
import com.dshx.game.SU.game.GearSlot
import com.dshx.game.SU.game.RewardAds
import com.dshx.game.SU.game.formatNumber

/**
 * 装备面板。
 *
 * 四个部位各管一件事（收线/抗脱钩/稀有偏好/咬钩），开箱获得、可装备可分解。
 * 这是长线追求的主轴：数值成长有上限，装备词条可以一直刷。
 */
@Composable
fun GearPanel(
    state: GameState,
    assets: Assets,
    revision: Int,
    onOpenBox: () -> Unit,
    onOpenBoxByAd: () -> Unit,
    onEquip: (GearItem) -> Unit,
    onUnequip: (GearSlot) -> Unit,
    onSalvage: (GearItem) -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION") revision
    val bag = state.gear.bag
    val price = state.gearBoxPrice()
    val affordable = state.money >= price

    Column(Modifier.fillMaxSize()) {
        SectionTitle("装备")
        Spacer(Modifier.height(4.dp))
        Text(
            "开箱获得装备，四个部位各管一种手感。刷词条是长线追求。",
            color = UITheme.TextDim, fontSize = 10.sp, lineHeight = 14.sp,
        )
        Spacer(Modifier.height(8.dp))

        // ---- 已装备的四个部位 ----
        GearSlot.entries.forEach { slot ->
            val item = state.gear.equipped[slot]
            EquippedRow(slot, item, assets, onUnequip)
            Spacer(Modifier.height(5.dp))
        }

        Spacer(Modifier.height(4.dp))

        // ---- 开箱 ----
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameButton(
                if (affordable) "开箱 · 🪙${formatNumber(price)}" else "金币不足",
                onOpenBox,
                modifier = Modifier.weight(1f).height(44.dp),
                enabled = affordable,
                accent = UITheme.Gold,
                fontSize = 13,
            )
        }
        Spacer(Modifier.height(6.dp))
        val adReady = RewardAds.isReady()
        AdActionRow(
            assets = assets,
            iconName = "ad_gift",
            title = "看广告开箱 · 必出好货",
            desc = when {
                !adReady -> "广告接入中"
                else -> "免费开一件，且更容易出史诗/传说"
            },
            enabled = adReady,
            onClick = onOpenBoxByAd,
        )

        Spacer(Modifier.height(10.dp))

        // ---- 背包 ----
        Text(
            "背包（${bag.size}）",
            color = UITheme.GoldLight, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(5.dp))

        if (bag.isEmpty()) {
            // 空背包：给一块**固定高度**的提示区，别用 weight(1f)。
            // 外层 Column 不可滚动，weight(1f) 会把这块撑满剩余空间，
            // 提示文字又居中，结果"背包（N）"与提示之间空出一大片。
            Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                Text(
                    "背包是空的\n开箱试试手气",
                    color = UITheme.TextDim, fontSize = 12.sp, textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(bag, key = { it.seq }) { item ->
                    BagRow(
                        item = item,
                        assets = assets,
                        onEquip = { onEquip(item) },
                        onSalvage = { onSalvage(item) },
                    )
                }
            }
        }
    }
}

/** 一个已装备部位。 */
@Composable
private fun EquippedRow(
    slot: GearSlot,
    item: GearItem?,
    assets: Assets,
    onUnequip: (GearSlot) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(UITheme.SlotBg)
            .border(1.5.dp, rarityColor(item?.rarity).copy(alpha = 0.7f), RoundedCornerShape(9.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GearIcon(item?.iconName, assets, 42.dp)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    slot.displayName,
                    color = UITheme.TextDim, fontSize = 10.sp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    item?.name ?: "未装备",
                    color = if (item != null) rarityColor(item.rarity) else UITheme.TextDim,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
            }
            if (item != null) {
                Text(item.affixText(), color = UITheme.TextNormal, fontSize = 10.sp)
            } else {
                Text("（${slot.desc}）", color = UITheme.TextDim, fontSize = 10.sp)
            }
        }
        if (item != null) {
            GameButton(
                "卸下", { onUnequip(slot) },
                modifier = Modifier.height(30.dp),
                accent = UITheme.WaterTop, fontSize = 11,
            )
        }
    }
}

/** 背包里的一件装备。 */
@Composable
private fun BagRow(
    item: GearItem,
    assets: Assets,
    onEquip: () -> Unit,
    onSalvage: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(UITheme.SlotBg)
            .border(1.5.dp, rarityColor(item.rarity).copy(alpha = 0.6f), RoundedCornerShape(9.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GearIcon(item.iconName, assets, 40.dp)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${item.slot.displayName} · ${item.name}",
                color = rarityColor(item.rarity), fontSize = 13.sp, fontWeight = FontWeight.Bold,
            )
            Text(item.affixText(), color = UITheme.TextNormal, fontSize = 10.sp, lineHeight = 13.sp)
        }
        Spacer(Modifier.width(6.dp))
        // 两个按钮固定宽度 + 足够高度：之前"分解 🪙5.00K"太长，
        // 24dp 高度会把文字上下裁掉（截图里"分解"被切一半）。
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            GameButton(
                "装备", onEquip,
                modifier = Modifier.width(76.dp).height(30.dp),
                accent = UITheme.Gold, fontSize = 11,
            )
            GameButton(
                "分解 ${formatNumber(GearLoadout.salvageValue(item))}", onSalvage,
                modifier = Modifier.width(76.dp).height(28.dp),
                accent = UITheme.TextDim, fontSize = 9,
            )
        }
    }
}

/**
 * 装备图标。按「部位 + 稀有度」取 art/ 下的图；取不到时退化成色块，
 * 不让界面因为缺素材而空一块。
 */
@Composable
private fun GearIcon(name: String?, assets: Assets, size: androidx.compose.ui.unit.Dp) {
    val bmp = remember(name) { name?.let { assets.raw(it)?.asImageBitmap() } }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(7.dp))
            .background(UITheme.DeepWater)
            .border(1.dp, UITheme.GoldDark.copy(alpha = 0.5f), RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(
                bmp, contentDescription = name,
                modifier = Modifier.fillMaxSize().padding(2.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/** 稀有度 → 颜色。 */
private fun rarityColor(rarity: GearRarity?): Color = when (rarity) {
    GearRarity.COMMON -> UITheme.RarityCommon
    GearRarity.RARE -> UITheme.RarityRare
    GearRarity.EPIC -> UITheme.RarityEpic
    GearRarity.LEGEND -> UITheme.RarityLegend
    null -> UITheme.TextDim
}

/** 开箱结果弹窗：把开出来的装备亮给玩家看。 */
@Composable
fun GearRewardDialog(
    item: GearItem,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(UITheme.PanelBg)
                .border(2.dp, rarityColor(item.rarity), RoundedCornerShape(14.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "开出了 ${item.rarity.displayName} 装备！",
                color = rarityColor(item.rarity),
                fontSize = 17.sp, fontWeight = FontWeight.Bold,
            )
            Text(
                "${item.slot.displayName} · ${item.name}",
                color = UITheme.GoldLight, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            )
            Text(
                item.affixText(),
                color = UITheme.TextNormal, fontSize = 12.sp,
                textAlign = TextAlign.Center, lineHeight = 17.sp,
            )
            Spacer(Modifier.height(4.dp))
            GameButton(
                "收下", onDismiss,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                accent = UITheme.Gold, fontSize = 14,
            )
        }
    }
}
