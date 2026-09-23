package com.taptap.fishingidle.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taptap.fishingidle.game.BobberState
import com.taptap.fishingidle.game.GameState
import com.taptap.fishingidle.game.World
import com.taptap.fishingidle.game.formatNumber

/**
 * 顶部金币栏。
 *
 * [revision] 是外部驱动的刷新计数：GameState 用的是普通 var 而非 Compose State，
 * 必须靠这个每次变化的参数让本组件不可跳过，从而重新读取最新的金币值。
 */
@Composable
fun MoneyBar(state: GameState, revision: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(UITheme.DeepWater.copy(alpha = 0.82f))
            .border(2.5.dp, UITheme.GoldDark, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🪙", fontSize = 22.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            formatNumber(state.money),
            color = UITheme.GoldLight,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 底部操作栏：商店按钮 + 状态提示。 */
@Composable
fun BottomBar(
    world: World,
    onOpenShop: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 状态提示
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(UITheme.DeepWater.copy(alpha = 0.82f))
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

        GameButton("菜单", onOpenMenu, accent = UITheme.WaterTop, fontSize = 15)
        GameButton("商店", onOpenShop, accent = UITheme.Gold, fontSize = 15)
    }
}

private fun hintText(world: World): String = when (world.bobber.state) {
    BobberState.IDLE -> "点击水面抛竿"
    BobberState.FLYING -> "浮标飞行中…"
    BobberState.FLOATING -> "等待鱼儿咬钩…"
    BobberState.BITE -> "有鱼咬钩！快点击收线"
    BobberState.REELING -> "收线中…连续点击加速"
    BobberState.DONE -> "准备下一竿"
}

/** 渔获图鉴条：显示已解锁的鱼种。[revision] 作用同 [MoneyBar]。 */
@Composable
fun CatchStrip(state: GameState, revision: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(UITheme.DeepWater.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.taptap.fishingidle.game.FishKind.entries.forEach { kind ->
            if (state.isUnlocked(kind)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val color = when (kind) {
                        com.taptap.fishingidle.game.FishKind.COMMON -> UITheme.RarityCommon
                        com.taptap.fishingidle.game.FishKind.RARE -> UITheme.RarityRare
                        com.taptap.fishingidle.game.FishKind.EPIC -> UITheme.RarityEpic
                        com.taptap.fishingidle.game.FishKind.LEGEND -> UITheme.RarityLegend
                    }
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(color, RoundedCornerShape(4.dp))
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${kind.displayName}×${state.ownedCount(kind)}",
                        color = color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
