package com.dshx.game.SU.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dshx.game.SU.BuildConfig
import com.dshx.game.SU.game.Assets
import com.dshx.game.SU.game.GameState
import com.dshx.game.SU.game.formatNumber

/**
 * 启动页：黄昏钓场背景图 + 游戏 LOGO + 开始/继续。
 *
 * 背景与 LOGO 都是美术资源，不再用文字标题 —— 文字标题在大屏上显得空，
 * 而且不同 ROM 的字体差异会让观感跑偏。
 *
 * 底部只留一行**动态版本号**（读 BuildConfig.VERSION_NAME），
 * 不再写操作提示 —— 那类文字放在启动页只会干扰主视觉。
 */
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
    val bg = remember { assets.raw("menu_bg")?.asImageBitmap() }
    val logo = remember { assets.scaled("game_logo", 900)?.asImageBitmap() }

    Box(Modifier.fillMaxSize().background(UITheme.DeepWater)) {
        // 背景图铺满（居中裁剪，不拉伸变形）
        if (bg != null) {
            Image(
                bg, contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // 顶部压一层暗色渐变，保证 LOGO 与按钮在任何背景上都清晰
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0x99000000),
                            0.35f to Color(0x33000000),
                            0.75f to Color(0x88000000),
                            1f to Color(0xDD000000),
                        )
                    )
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            // 游戏 LOGO
            if (logo != null) {
                Image(
                    logo, contentDescription = "钓鱼人生：放置大师模拟",
                    modifier = Modifier.fillMaxWidth(0.92f),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    "钓鱼人生",
                    color = UITheme.GoldLight,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(20.dp))

            // 存档概览（只在有存档时显示，让"继续"更有分量）
            if (hasSave) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "🪙 ${formatNumber(state.money)}",
                        color = UITheme.GoldLight,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "图鉴 ${state.caughtSpecies.size} 种 · 钓手 ${state.helpers} 名 · " +
                            "转生 ${state.prestigeCount} 次",
                        color = UITheme.Cream.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(18.dp))
            } else {
                Spacer(Modifier.height(18.dp))
            }

            // 有存档 → 主按钮是「继续游戏」，这正是玩家最想要的那一个
            if (hasSave) {
                MenuButton("继续游戏", UITheme.Gold, onContinue)
                Spacer(Modifier.height(10.dp))
                MenuButton("重新开始", UITheme.WaterTop, onReset)
            } else {
                MenuButton("开始游戏", UITheme.Gold, onStart)
            }
            Spacer(Modifier.height(10.dp))
            MenuButton("设置", UITheme.WaterTop, onSettings)

            Spacer(Modifier.weight(1f))

            // 底部：只留动态版本号
            Text(
                "v${BuildConfig.VERSION_NAME}",
                color = UITheme.Cream.copy(alpha = 0.55f),
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(14.dp))
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
