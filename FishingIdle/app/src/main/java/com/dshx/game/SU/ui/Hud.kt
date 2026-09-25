package com.dshx.game.SU.ui

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.dshx.game.SU.game.AchievementDef
import com.dshx.game.SU.game.BobberState
import com.dshx.game.SU.game.Rarity
import com.dshx.game.SU.game.GameState
import com.dshx.game.SU.game.World
import com.dshx.game.SU.game.formatNumber

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
 *
 * [badgeCount] > 0 时在「商店」按钮上挂一个红点角标：
 * 有未领的每日任务 / 图鉴收集奖励时提示玩家进去领。
 */
@Composable
fun BottomBar(
    world: World,
    revision: Int,
    canPrestige: Boolean,
    badgeCount: Int,
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
        // 商店按钮 + 红点：有东西可领时挂一个数字角标
        Box(contentAlignment = Alignment.TopEnd) {
            GameButton("商店", onOpenShop, accent = UITheme.Gold, fontSize = 14)
            if (badgeCount > 0) {
                Box(
                    Modifier
                        .size(18.dp)
                        .background(UITheme.TextBad, RoundedCornerShape(9.dp))
                        .border(1.5.dp, UITheme.Ink, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (badgeCount > 9) "9+" else "$badgeCount",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private fun hintText(world: World): String {
    val auto = world.gameState.autoCastUnlocked
    return when (world.bobber.state) {
        BobberState.IDLE ->
            if (auto) "自动抛竿中…" else "拖动浏览河面 · 点击水面抛竿"
        BobberState.FLYING -> "浮标飞行中…"
        BobberState.FLOATING ->
            if (world.bobber.hookedFish != null) "有鱼靠近了，盯紧浮标…" else "这片水域没有鱼，换个位置"
        BobberState.BITE -> "有鱼咬钩！快点击收线"
        BobberState.REELING ->
            if (world.gameState.autoReelUnlocked) "自动收线中…" else "收线中…连续点击加速"
        BobberState.DONE -> "准备下一竿"
    }
}

@Composable
fun SpeedControl(
    state: GameState,
    revision: Int,
    onSelect: (Int) -> Unit,
    onLocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_EXPRESSION") revision
    val available = state.availableSpeed()
    val selected = state.currentSpeed()
    val remaining = state.speedRemainingMillis()

    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(UITheme.DeepWater.copy(alpha = 0.88f))
            .border(1.dp, UITheme.GoldDark, RoundedCornerShape(8.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        (1..3).forEach { tier ->
            val unlocked = tier <= available
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (tier == selected) UITheme.Gold else UITheme.WoodDark)
                    .pressable { if (unlocked) onSelect(tier) else onLocked() }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (unlocked) "${tier}×" else "🔒${tier}×",
                    color = if (tier == selected) UITheme.Ink else UITheme.Cream,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (remaining > 0L) {
            Text(
                "%02d:%02d".format(remaining / 60_000, (remaining / 1_000) % 60),
                color = UITheme.TextGood,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun SpeedUnlockDialog(
    adReady: Boolean,
    onDismiss: () -> Unit,
    onUnlock: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(UITheme.PanelBg)
                .border(2.dp, UITheme.GoldDark, RoundedCornerShape(14.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("解锁游戏倍速", color = UITheme.GoldLight, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "看完 1 条广告，同时解锁 2× / 3×\n持续 20 分钟，下线照常计时",
                color = UITheme.Cream,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            GameButton(
                if (adReady) "看广告解锁" else "广告暂不可用",
                onUnlock,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                enabled = adReady,
                accent = UITheme.Gold,
                fontSize = 14,
            )
            GameButton(
                "取消",
                onDismiss,
                modifier = Modifier.fillMaxWidth().height(38.dp),
                accent = UITheme.WaterTop,
                fontSize = 13,
            )
        }
    }
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
