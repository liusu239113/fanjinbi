package com.dshx.game.SU.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
 * 缸里的鱼用钓场那套**逐帧动画**（摆尾）+ 横向游动 ——
 * 它们本来就是活鱼，不该在缸里变成一排静止图标。
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

        // ---- 鱼缸本体：背景 + 会游动的展品 ----
        Tank(
            fish = state.aquarium,
            assets = assets,
            slots = slots,
            onTap = { idx -> onTakeBack(idx) },
        )

        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "展品估值 🪙${formatNumber(state.aquariumWorth(now))}",
                color = UITheme.GoldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            )
            Text("点击展品可取回仓库", color = UITheme.TextDim, fontSize = 10.sp)
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

/**
 * 鱼缸：水族箱背景 + 每条展品在自己的轨道上游动。
 *
 * 布局是**纯相对定位**（百分比），所以缸体高度由调用方给多少就适配多少，
 * 不会因为改成固定高度就把鱼挤出缸外。
 */
@Composable
private fun Tank(
    fish: List<StoredFish>,
    assets: Assets,
    slots: Int,
    onTap: (Int) -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(3.dp, UITheme.WoodDark, RoundedCornerShape(12.dp)),
    ) {
        val tankW = maxWidth
        val tankH = maxHeight

        val bg = remember { assets.raw("aquarium_bg")?.asImageBitmap() }
        if (bg != null) {
            Image(
                bg, contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.fillMaxSize().background(UITheme.DeepWater))
        }

        if (fish.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "鱼缸还空着\n从仓库挑几条鱼放进来",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 17.sp,
                )
            }
            return@BoxWithConstraints
        }

        // 全局统一的动画时钟：所有鱼共用一个 0→1 的循环，相位各自错开
        val clock = rememberInfiniteTransition(label = "tank")
        val phase by clock.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 5200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "swim",
        )

        fish.forEachIndexed { i, f ->
            val species = Bestiary.speciesById(f.speciesId)
            val sprite = species?.sprite ?: return@forEachIndexed
            // 用钓场那套摆尾动画（每帧宽约 44dp）；没有动画就退回静态首帧
            val frames = remember(sprite) {
                assets.allFrames(sprite, 44).ifEmpty {
                    listOfNotNull(assets.firstFrame(sprite, 44))
                }.map { it.asImageBitmap() }
            }
            if (frames.isEmpty()) return@forEachIndexed

            // 摆尾：每帧停留时间随鱼而异，整缸不会同步
            val frameIdx = ((phase * frames.size * 2f).toInt() + i) % frames.size

            // 横向游动：在缸宽 62% 的范围内来回，奇偶条反向，相位错开
            val span = 0.62f
            val raw = (phase + i * 0.17f) % 1f
            val tri = if (raw < 0.5f) raw * 2f else (1f - raw) * 2f
            val frac = 0.08f + tri * span
            val goingRight = raw < 0.5f

            // 纵向轨道：按序号铺开，落在水体范围内（避开缸顶灯与缸底砂石）
            val lane = if (fish.size <= 1) 0.5f else (i % 5) / 4f
            val yFrac = 0.30f + lane * 0.42f

            val x = tankW * frac
            val y = tankH * yFrac

            Box(
                Modifier
                    .offset(x = x - 22.dp, y = y - 16.dp)
                    .size(width = 44.dp, height = 32.dp)
                    .pressable { onTap(i) },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    frames[frameIdx],
                    contentDescription = species?.name,
                    // 朝左游时水平翻转，看起来才是"在游"而不是倒退
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { if (!goingRight) scaleX = -1f },
                    contentScale = ContentScale.Fit,
                )
            }
        }
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
