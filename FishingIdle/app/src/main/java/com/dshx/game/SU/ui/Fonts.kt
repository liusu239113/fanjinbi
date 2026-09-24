package com.dshx.game.SU.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.dshx.game.SU.R

/**
 * 全局游戏字体。
 *
 * 用 res/font/game_font.ttf（Lenovo-XiaoxinChaokuGB），
 * Compose 侧所有 Text 都通过 [AppFontFamily] 渲染。
 * 游戏内 Canvas 绘制的文字见 GameRenderer，它用 Typeface 单独加载同一份资源。
 */
val AppFontFamily: FontFamily = FontFamily(
    Font(R.font.game_font, FontWeight.Normal),
    Font(R.font.game_font, FontWeight.Medium),
    Font(R.font.game_font, FontWeight.SemiBold),
    Font(R.font.game_font, FontWeight.Bold),
)
