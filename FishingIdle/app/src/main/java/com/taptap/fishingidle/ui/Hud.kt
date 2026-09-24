package com.taptap.fishingidle.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taptap.fishingidle.game.AchievementDef
import com.taptap.fishingidle.game.BobberState
import com.taptap.fishingidle.game.Rarity
import com.taptap.fishingidle.game.GameState
import com.taptap.fishingidle.game.World
import com.taptap.fishingidle.game.formatNumber

/**
 * 顶部金币栏。
 *
 * [revision] 每次变化都会让本组件重新读取 [state].money。
 * 注意：不能依赖 Compose 的自动状态追踪 —— GameState 用的是普通 var，
 * 所有依赖它的组件都必须显式接收并在函数体内读取这个计数，
 * 否则会被 Compose 按作用域跳过，出现"金币不实时刷新"的问题。
 */
@Composable
fun MoneyBar(state: GameState, revision: Int, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_EXPRESSION") revision

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(UITheme.DeepWater.copy(alpha = 0.85f))
                .border(2.5.dp, UITheme.GoldDark, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🪙", fontSize = 20.sp)
            Spacer(Modifier.width(7.dp))
            Text(
                formatNumber(state.money),
                color = UITheme.GoldLight,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // 连击条：有连击时才出现
        if (state.combo >= 2) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(UITheme.TextGood.copy(alpha = 0.20f))
                    .border(1.5.dp, UITheme.TextGood.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${state.combo} 连击",
                    color = UITheme.TextGood,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "×${String.format("%.2f", state.comboMultiplier)}",
                    color = UITheme.GoldLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * 底部操作栏：商店按钮 + 状态提示。
 * [revision] 作用同 [MoneyBar] —— 提示依赖 world.bobber.state 这个普通属性。
 */
@Composable
fun BottomBar(
    world: World,
    revision: Int,
    canPrestige: Boolean,
    onOpenShop: () -> Unit,
    onOpenPrestige: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_EXPRESSION") revision

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(UITheme.DeepWater.copy(alpha = 0.85f))
                .border(2.dp, UITheme.GoldDark.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                hintText(world),
                color = UITheme.Cream,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Start,
            )
        }

        GameButton("菜单", onOpenMenu, accent = UITheme.WaterTop, fontSize = 14)
        // 转生可做时高亮，给玩家一个明确的行动提示
        GameButton(
            if (canPrestige) "转生!" else "转生",
            onOpenPrestige,
            accent = if (canPrestige) UITheme.TextGood else UITheme.WaterTop,
            fontSize = 14,
        )
        GameButton("商店", onOpenShop, accent = UITheme.Gold, fontSize = 14)
    }
}

private fun hintText(world: World): String = when (world.bobber.state) {
    BobberState.IDLE -> "拖动浏览河面 · 点击水面抛竿"
    BobberState.FLYING -> "浮标飞行中…"
    BobberState.FLOATING ->
        if (world.bobber.hookedFish != null) "有鱼靠近了，盯紧浮标…" else "这片水域没有鱼，换个位置"
    BobberState.BITE -> "有鱼咬钩！快点击收线"
    BobberState.REELING -> "收线中…连续点击加速"
    BobberState.DONE -> "准备下一竿"
}

/** 渔获图鉴条：显示已解锁的鱼种。[revision] 作用同 [MoneyBar]。 */
@Composable
fun CatchStrip(state: GameState, revision: Int, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_EXPRESSION") revision

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(UITheme.DeepWater.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Rarity.entries.forEach { rarity ->
            if (state.isUnlocked(rarity)) {
                val color = when (rarity) {
                    Rarity.COMMON -> UITheme.RarityCommon
                    Rarity.RARE -> UITheme.RarityRare
                    Rarity.EPIC -> UITheme.RarityEpic
                    Rarity.LEGEND -> UITheme.RarityLegend
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(color, RoundedCornerShape(4.dp)))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${rarity.displayName}×${state.ownedCount(rarity)}",
                        color = color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * 屏幕左右两侧的划船按钮。
 *
 * 按住即持续移动 —— 用 pointerInput 监听按下/抬起，
 * 而不是 onClick，这样长按能一直划。
 */
@Composable
fun MoveButtons(
    world: World,
    revision: Int,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_EXPRESSION") revision

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MoveButton("◀", world::setMoveLeft)
        MoveButton("▶", world::setMoveRight)
    }
}

@Composable
private fun MoveButton(label: String, onHold: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(UITheme.DeepWater.copy(alpha = 0.75f))
            .border(3.dp, UITheme.GoldDark, RoundedCornerShape(32.dp))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent(PointerEventPass.Main)
                        val pressed = down.changes.any { it.pressed }
                        onHold(pressed)
                        // 抬手时确保松开
                        if (!pressed) onHold(false)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = UITheme.GoldLight,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 成就解锁提示条。 */
@Composable
fun AchievementToast(achievement: AchievementDef?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = achievement != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        val def = achievement ?: return@AnimatedVisibility
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(UITheme.Gold.copy(alpha = 0.95f))
                .border(3.dp, UITheme.Ink, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🏆", fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "成就解锁 · ${def.name}",
                    color = UITheme.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${def.desc}  奖励 🪙${formatNumber(def.reward)}",
                    color = UITheme.Ink.copy(alpha = 0.78f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}
