package com.dshx.game.SU.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dshx.game.SU.game.Aquarium
import com.dshx.game.SU.game.Assets
import com.dshx.game.SU.game.Bestiary
import com.dshx.game.SU.game.GameState
import com.dshx.game.SU.game.Rarity
import com.dshx.game.SU.game.RewardAds
import com.dshx.game.SU.game.StoredFish
import com.dshx.game.SU.game.Warehouse
import com.dshx.game.SU.game.formatNumber

/**
 * 水族馆（展缸）。
 *
 * 仓库里的鱼有两条出路：**卖掉换钱**，或**放进这里永久展出**。
 * 展出的鱼不能再卖，但按「稀有度 × 体型 × 鱼种」给永久挂机收益加成。
 *
 * 这是仓库真正的取舍点 —— 卖是即时收益，养是长期复利（转生后依然生效）。
 * 缸位有限、扩容越来越贵，所以不可能"全都要"。
 */
@Composable
fun AquariumPanel(
    state: GameState,
    assets: Assets,
    revision: Int,
    /** 把仓库第 index 条鱼放进缸。 */
    onExhibit: (Int) -> Unit,
    /** 把缸里第 index 条鱼取回仓库。 */
    onTakeBack: (Int) -> Unit,
    onUpgrade: () -> Unit,
    onAdUpgrade: () -> Unit,
    onClose: () -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION") revision
    val now = System.currentTimeMillis()
    val slots = Aquarium.slots(state)
    val bonusPct = (Aquarium.totalBonus(state) * 100).toInt()
    var picking by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // ---- 顶部：加成与缸位 ----
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                SectionTitle("水族馆")
                Spacer(Modifier.height(3.dp))
                Text(
                    "${state.aquarium.size} / $slots 缸位",
                    color = if (state.aquarium.size >= slots) UITheme.TextBad else UITheme.TextNormal,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("挂机收益", color = UITheme.TextDim, fontSize = 10.sp)
                Text(
                    "+$bonusPct%",
                    color = UITheme.TextGood, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "展出的鱼不再能卖，但永久提升钓手/鹈鹕/拖网的产出（转生后依然生效）。",
            color = UITheme.TextDim, fontSize = 10.sp, lineHeight = 14.sp,
        )
        Spacer(Modifier.height(8.dp))

        // ---- 鱼缸本体：背景 + 展品 ----
        // 用固定高度而不是 aspectRatio：面板里还有页签/按钮/广告条，
        // 按比例撑高在小屏上会把底部按钮挤出屏幕。
        Box(
            Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(3.dp, UITheme.WoodDark, RoundedCornerShape(12.dp)),
        ) {
            val tank = remember { assets.raw("aquarium_bg")?.asImageBitmap() }
            if (tank != null) {
                Image(
                    tank, contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxSize().background(UITheme.DeepWater))
            }
            if (state.aquarium.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "鱼缸还空着\n从仓库挑几条鱼放进来",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 17.sp,
                    )
                }
            } else {
                LazyVerticalGrid(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.aquarium, key = { it.seq }) { fish ->
                        ExhibitCell(fish, assets) { onTakeBack(state.aquarium.indexOf(fish)) }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ---- 展品总览 ----
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "展品估值 🪙${formatNumber(state.aquariumWorth(now))}",
                color = UITheme.GoldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            )
            Text(
                "点击展品可取回仓库",
                color = UITheme.TextDim, fontSize = 10.sp,
            )
        }

        Spacer(Modifier.height(8.dp))

        // ---- 选鱼入缸 ----
        GameButton(
            if (picking) "收起候选" else "从仓库选鱼入缸",
            { picking = !picking },
            modifier = Modifier.fillMaxWidth().height(42.dp),
            enabled = !Aquarium.isFull(state) && state.warehouse.isNotEmpty(),
            accent = UITheme.Gold,
            fontSize = 14,
        )
        if (Aquarium.isFull(state)) {
            Text(
                "缸位已满，先扩容或取回一条",
                color = UITheme.TextBad, fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                textAlign = TextAlign.Center,
            )
        }

        if (picking) {
            Spacer(Modifier.height(6.dp))
            val sorted = Warehouse.sorted(state.warehouse)
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(sorted, key = { it.seq }) { fish ->
                    CandidateCell(fish, assets) {
                        onExhibit(state.warehouse.indexOf(fish))
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))

        // ---- 底部：扩容 / 关闭 ----
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GameButton(
                if (Aquarium.canUpgrade(state))
                    "扩容 · 🪙${formatNumber(Aquarium.upgradePrice(state))}"
                else "已满级",
                onUpgrade,
                modifier = Modifier.weight(1f).height(42.dp),
                enabled = Aquarium.canUpgrade(state) &&
                    state.money >= Aquarium.upgradePrice(state),
                accent = UITheme.WaterTop,
                fontSize = 12,
            )
            GameButton(
                "关闭", onClose,
                modifier = Modifier.height(42.dp),
                accent = UITheme.WaterTop,
                fontSize = 13,
            )
        }

        Spacer(Modifier.height(7.dp))
        val adReady = RewardAds.isReady()
        val maxed = !Aquarium.canUpgrade(state)
        AdActionRow(
            assets = assets,
            iconName = "ad_gift",
            title = "水族赞助 · 免费扩容",
            desc = when {
                maxed -> "缸位已满级"
                !adReady -> "广告接入中"
                else -> "白得 ${Aquarium.SLOT_STEP} 个缸位，不花金币"
            },
            enabled = adReady && !maxed,
            onClick = onAdUpgrade,
        )
    }
}

/** 缸里的一个展品。点一下取回仓库。 */
@Composable
private fun ExhibitCell(fish: StoredFish, assets: Assets, onClick: () -> Unit) {
    val species = Bestiary.speciesById(fish.speciesId)
    val icon = remember(fish.speciesId) {
        species?.let { assets.firstFrame(it.sprite, 160)?.asImageBitmap() }
    }
    val color = rarityColorOf(species?.rarity)
    Column(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0x66000000))
            .border(1.5.dp, color.copy(alpha = 0.8f), RoundedCornerShape(7.dp))
            .pressable { onClick() }
            .padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(
                    icon, contentDescription = species?.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Text(
            "+${(Aquarium.bonusOf(fish) * 100).toInt()}%",
            color = UITheme.TextGood, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        )
    }
}

/** 候选（仓库里的鱼）。点一下放进缸。 */
@Composable
private fun CandidateCell(fish: StoredFish, assets: Assets, onClick: () -> Unit) {
    val species = Bestiary.speciesById(fish.speciesId)
    val icon = remember(fish.speciesId) {
        species?.let { assets.firstFrame(it.sprite, 160)?.asImageBitmap() }
    }
    val color = rarityColorOf(species?.rarity)
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(UITheme.SlotBg)
            .border(
                if (fish.firstCatch) 2.5.dp else 1.5.dp,
                if (fish.firstCatch) UITheme.GoldLight else color.copy(alpha = 0.6f),
                RoundedCornerShape(8.dp),
            )
            .pressable { onClick() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().height(42.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(UITheme.DeepWater),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    icon, contentDescription = species?.name,
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            species?.name ?: fish.speciesId,
            color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        )
        Text(
            fish.size.label,
            color = UITheme.TextDim, fontSize = 8.sp, maxLines = 1,
        )
        Text(
            "养 +${(Aquarium.bonusOf(fish) * 100).toInt()}%",
            color = UITheme.TextGood, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        )
    }
}

private fun rarityColorOf(rarity: Rarity?): Color = when (rarity) {
    Rarity.COMMON -> UITheme.RarityCommon
    Rarity.RARE -> UITheme.RarityRare
    Rarity.EPIC -> UITheme.RarityEpic
    Rarity.LEGEND -> UITheme.RarityLegend
    null -> UITheme.RarityCommon
}
