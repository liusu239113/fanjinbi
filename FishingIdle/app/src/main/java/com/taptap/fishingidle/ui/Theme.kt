package com.taptap.fishingidle.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit

/** UI 配色。与游戏内 Palette 保持一致的低饱和暖色 + 深水色调。 */
object UITheme {
    val DeepWater = Color(0xFF12303A)
    val WaterTop = Color(0xFF2E6E78)
    val WaterMid = Color(0xFF1E5460)

    val Wood = Color(0xFF6E422A)
    val WoodDark = Color(0xFF4A2A1A)
    val WoodLight = Color(0xFF8B5636)

    val Gold = Color(0xFFE8B446)
    val GoldDark = Color(0xFFB07E28)
    val GoldLight = Color(0xFFFFD65C)

    val Ink = Color(0xFF1C1612)
    val Cream = Color(0xFFF5ECD6)

    val TextNormal = Color(0xFFEEE8D6)
    val TextDim = Color(0xFF9AA8A8)
    val TextBad = Color(0xFFFF7A6C)
    val TextGood = Color(0xFF96E28C)

    val RarityCommon = Color(0xFFC4D2DC)
    val RarityRare = Color(0xFF96D696)
    val RarityEpic = Color(0xFFFFCE6E)
    val RarityLegend = Color(0xFFC69CF6)

    val PanelBg = Color(0xEE1A2428)
    val SlotBg = Color(0xFF2A3639)
    val SlotBgOwned = Color(0xFF3A4A2E)
}

/** 常用字号。 */
object FontSize {
    val title: TextUnit = 26.sp
    val header: TextUnit = 20.sp
    val body: TextUnit = 15.sp
    val small: TextUnit = 13.sp
    val tiny: TextUnit = 11.sp
    val money: TextUnit = 34.sp
}

val Bold = FontWeight.Bold
val SemiBold = FontWeight.SemiBold
