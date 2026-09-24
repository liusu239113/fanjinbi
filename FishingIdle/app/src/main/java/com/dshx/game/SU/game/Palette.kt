package com.dshx.game.SU.game

import android.graphics.Color

/** 全局配色。取自参考图的低饱和暖色 + 深绿水面风格。 */
object Palette {
    val DEEP_WATER = Color.rgb(18, 48, 58)
    val WATER_TOP = Color.rgb(46, 110, 120)
    val WATER_MID = Color.rgb(30, 84, 96)
    val WATER_BOTTOM = Color.rgb(16, 44, 54)

    val WOOD = Color.rgb(110, 66, 42)
    val WOOD_DARK = Color.rgb(74, 42, 26)
    val GOLD = Color.rgb(232, 180, 70)
    val GOLD_DARK = Color.rgb(176, 126, 40)

    val INK = Color.rgb(28, 22, 18)
    val CREAM = Color.rgb(245, 236, 214)

    val TEXT_GOLD = Color.rgb(255, 214, 92)
    val TEXT_BAD = Color.rgb(255, 122, 108)
    val TEXT_GOOD = Color.rgb(150, 226, 140)
    val TEXT_NORMAL = Color.rgb(238, 232, 214)

    val SPLASH = Color.rgb(150, 214, 230)
    val SPLASH_GREEN = Color.rgb(150, 220, 170)
    val SPLASH_GOLD = Color.rgb(255, 212, 110)
    val SPLASH_PURPLE = Color.rgb(196, 150, 240)

    val PANEL_BG = Color.rgb(38, 30, 24)
    val PANEL_EDGE = Color.rgb(232, 180, 70)
    val OVERLAY = Color.argb(190, 10, 18, 22)

    val RARITY: Map<Rarity, Int> = mapOf(
        Rarity.COMMON to Color.rgb(196, 210, 220),
        Rarity.RARE to Color.rgb(150, 214, 150),
        Rarity.EPIC to Color.rgb(255, 206, 110),
        Rarity.LEGEND to Color.rgb(198, 156, 246),
    )
}

/** 大数字格式化：1234 → 1.23K，1.2e6 → 1.20M … */
fun formatNumber(v: Double): String {
    val abs = kotlin.math.abs(v)
    return when {
        abs < 1_000 -> if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)
        abs < 1_000_000 -> String.format("%.2fK", v / 1_000)
        abs < 1_000_000_000 -> String.format("%.2fM", v / 1_000_000)
        abs < 1e12 -> String.format("%.2fB", v / 1_000_000_000)
        abs < 1e15 -> String.format("%.2fT", v / 1e12)
        abs < 1e18 -> String.format("%.2fQa", v / 1e15)
        else -> String.format("%.2e", v)
    }
}

/** 百分比格式化。 */
fun formatPercent(v: Double): String = String.format("%.0f%%", v * 100)
