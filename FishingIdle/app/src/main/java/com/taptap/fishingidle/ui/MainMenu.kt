package com.taptap.fishingidle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taptap.fishingidle.game.Assets
import com.taptap.fishingidle.game.GameState
import com.taptap.fishingidle.game.formatNumber

/** 启动页：标题 + 开始/继续游戏 + 设置。 */
@Composable
fun MainMenu(
    state: GameState,
    assets: Assets,
    hasSave: Boolean,
    onStart: () -> Unit,
    onContinue: () -> Unit,
    onSettings: () -> Unit,
    onReset: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0E2830),
                        Color(0xFF16414D),
                        Color(0xFF0A1E26),
                    )
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "钓鱼大师",
                color = UITheme.GoldLight,
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "FISHING IDLE",
                color = UITheme.TextDim,
                fontSize = 14.sp,
                letterSpacing = 6.sp,
            )

            Spacer(Modifier.height(28.dp))

            // 存档概览
            if (hasSave) {
                WoodPanel(Modifier.fillMaxWidth(), assets = assets, cornerPx = 96) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("存档进度", color = UITheme.TextDim, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "🪙 ${formatNumber(state.money)}",
                            color = UITheme.GoldLight,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "小鱼 ${state.commonFish} · 鲤鱼 ${state.rareFish} · " +
                                "锦鲤 ${state.epicFish} · 钓手 ${state.helpers}",
                            color = UITheme.TextDim,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            } else {
                Spacer(Modifier.height(20.dp))
            }

            if (hasSave) {
                MenuButton("继续游戏", UITheme.Gold, onContinue)
                Spacer(Modifier.height(12.dp))
                MenuButton("重新开始", UITheme.WaterTop, onReset)
            } else {
                MenuButton("开始游戏", UITheme.Gold, onStart)
            }

            Spacer(Modifier.height(12.dp))
            MenuButton("设置", UITheme.WaterTop, onSettings)

            Spacer(Modifier.height(28.dp))
            Text(
                "点击水面抛竿 · 浮标下沉时点击收线",
                color = UITheme.TextDim,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MenuButton(text: String, accent: Color, onClick: () -> Unit) {
    GameButton(
        text = text,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        accent = accent,
        fontSize = 19,
    )
}
