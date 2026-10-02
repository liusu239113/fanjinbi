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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
 * 结构：开局 **1 个缸、只能放 1 条**；缸位可扩容（每缸最多 10 条）；
 * 最多可建 **3 个缸**。两个升级方向（扩容 / 新建缸）价格都很贵，互相权衡。
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
    /** 扩容一个缸位。 */
    onExpand: () -> Unit,
    /** 新建一个水族馆。 */
    onAddTank: () -> Unit,
    /** 看广告免费扩容一个缸位。 */
    onAdExpand: () -> Unit,
    onClose: () -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION") revision
    val now = System.currentTimeMillis()
    val tanks = Aquarium.tanks(state)
    val per = Aquarium.slotsPerTank(state)
    val cap = Aquarium.capacity(state)
    val bonusPct = (Aquarium.totalBonus(state) * 100).toInt()

    var tankIdx by remember { mutableIntStateOf(0) }
    // 缸数可能在别处变化（新建/读档），夹一下避免越界
    val cur = tankIdx.coerceIn(0, (tanks - 1).coerceAtLeast(0))
    var picking by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // ---- 顶部：加成与容量 ----
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 同 WarehousePanel：SectionTitle 内部 fillMaxWidth，
            // 左列不带 weight 会吃光整行、把右侧文字压成 0 宽逐字换行，
            // 头部行被撑高后标题看起来就"悬在下面"。
            Column(Modifier.weight(1f)) {
                SectionTitle("水族馆")
                Spacer(Modifier.height(2.dp))
                Text(
                    "${state.aquarium.size} / $cap 条 · $tanks 个缸",
                    color = if (state.aquarium.size >= cap) UITheme.TextBad else UITheme.TextNormal,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
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
        Spacer(Modifier.height(4.dp))

        // ---- 缸切换 ----
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 0 until tanks) {
                val count = Aquarium.tankContent(state, i).size
                TankTab(
                    label = "水族馆 ${i + 1}",
                    sub = "$count/$per",
                    selected = i == cur,
                    onClick = { tankIdx = i; picking = false },
                )
            }
            if (Aquarium.canAddTank(state)) {
                // 新建缸入口就放在缸标签后面 —— 玩家看到"还能多一个缸"最直接
                TankTab(
                    label = "＋ 新建",
                    sub = "🪙${formatNumber(Aquarium.addTankPrice(state))}",
                    selected = false,
                    enabled = state.money >= Aquarium.addTankPrice(state),
                    onClick = onAddTank,
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // ---- 当前缸：背景 + 会游动的展品 ----
        Tank(
            fish = Aquarium.tankContent(state, cur),
            assets = assets,
            tankIndex = cur,
            onTap = { localIdx -> onTakeBack(cur * per + localIdx) },
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
            modifier = Modifier.fillMaxWidth().height(40.dp),
            enabled = !Aquarium.isFull(state) && state.warehouse.isNotEmpty(),
            accent = UITheme.Gold,
            fontSize = 13,
        )
        if (Aquarium.isFull(state)) {
            Text(
                "缸位已满，先扩容或取回一条",
                color = UITheme.TextBad, fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
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
                if (Aquarium.canExpand(state))
                    "扩容缸位 · 🪙${formatNumber(Aquarium.expandPrice(state))}"
                else "缸位已满",
                onExpand,
                modifier = Modifier.weight(1f).height(42.dp),
                enabled = Aquarium.canExpand(state) &&
                    state.money >= Aquarium.expandPrice(state),
                accent = UITheme.WaterTop,
                fontSize = 11,
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
        val maxed = !Aquarium.canExpand(state)
        AdActionRow(
            assets = assets,
            iconName = "ad_gift",
            title = "水族赞助 · 免费扩容缸位",
            desc = when {
                maxed -> "缸位已满级"
                !adReady -> "广告接入中"
                else -> "白得 1 个缸位，不花金币"
            },
            enabled = adReady && !maxed,
            onClick = onAdExpand,
        )
    }
}

/** 缸标签：显示第几个缸、已放几条。 */
@Composable
private fun TankTab(
    label: String,
    sub: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val bg = when {
        selected -> UITheme.Gold
        !enabled -> UITheme.WoodDark.copy(alpha = 0.5f)
        else -> UITheme.WoodDark
    }
    Column(
        Modifier
            .width(86.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(2.dp, UITheme.Ink, RoundedCornerShape(8.dp))
            .pressable(enabled) { onClick() }
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            color = if (selected) UITheme.Ink else UITheme.Cream,
            fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        )
        Text(
            sub,
            color = if (selected) UITheme.Ink.copy(alpha = 0.8f) else UITheme.TextDim,
            fontSize = 9.sp, maxLines = 1,
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
    tankIndex: Int,
    onTap: (Int) -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
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
                    "这个缸还空着\n从仓库挑一条鱼放进来",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 17.sp,
                )
            }
            return@BoxWithConstraints
        }

        // 全局统一的动画时钟：所有鱼共用一个 0→1 的循环，相位各自错开。
        // 顺带解决"入缸/取回后不刷新"—— 缸内每帧重组，展示始终是最新的。
        val clock = rememberInfiniteTransition(label = "tank$tankIndex")
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
            // 用钓场那套摆尾动画（每帧宽约 40dp）；没有动画就退回静态首帧
            val frames = remember(sprite) {
                assets.allFrames(sprite, 40).ifEmpty {
                    listOfNotNull(assets.firstFrame(sprite, 40))
                }.map { it.asImageBitmap() }
            }
            if (frames.isEmpty()) return@forEachIndexed

            // 摆尾：每帧停留时间随鱼而异，整缸不会同步
            val frameIdx = ((phase * frames.size * 2f).toInt() + i) % frames.size

            // 横向游动：在缸宽 60% 的范围内来回，相位错开
            val span = 0.60f
            val raw = (phase + i * 0.17f) % 1f
            val tri = if (raw < 0.5f) raw * 2f else (1f - raw) * 2f
            val frac = 0.10f + tri * span
            val goingRight = raw < 0.5f

            // 纵向轨道：按序号铺开，落在水体范围内（避开缸顶灯与缸底砂石）
            val lane = if (fish.size <= 1) 0.5f else (i % 5) / 4f
            val yFrac = 0.30f + lane * 0.42f

            val x = tankW * frac
            val y = tankH * yFrac

            Box(
                Modifier
                    .offset(x = x - 20.dp, y = y - 15.dp)
                    .size(width = 40.dp, height = 30.dp)
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
