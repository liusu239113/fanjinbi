package com.taptap.fishingidle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.taptap.fishingidle.game.Assets

/**
 * 木质面板容器。
 *
 * 木纹图按九宫格切片绘制：四角护角保持原始像素尺寸，只有中间区域被拉伸。
 * 直接把整张图 FillBounds 缩放会把护角压扁（面板越扁越明显），
 * 所以这里用 Canvas 手工切 9 块。
 */
@Composable
fun WoodPanel(
    modifier: Modifier = Modifier,
    assets: Assets? = null,
    cornerPx: Int = 128,
    content: @Composable () -> Unit,
) {
    val panelBitmap = assets?.let { a -> a.raw("ui_panel")?.asImageBitmap() }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            // 程序化渐变兜底：资源缺失时界面依然可用
            .background(
                Brush.verticalGradient(
                    listOf(UITheme.WoodLight, UITheme.Wood, UITheme.WoodDark)
                )
            )
    ) {
        // 木纹面板：整张图按九宫格铺 —— 四角护角保持原始像素，
        // 只有中间的木纹被拉伸。绝不用 FillBounds 整图缩放，
        // 那会把护角压扁（面板越扁越明显）。
        //
        // matchParentSize 而不是 fillMaxSize：前者不参与父容器尺寸计算，
        // 只跟随已有尺寸。用 fillMaxSize 会在 Column 里把面板撑到整屏，
        // 把后面的按钮全挤出屏幕（主菜单点不动就是这个原因）。
        if (panelBitmap != null) {
            Canvas(Modifier.matchParentSize()) {
                drawNinePatch(panelBitmap, cornerPx)
            }
        }
        content()
    }
}

/**
 * 九宫格绘制：角块 1:1 保留，边块单向拉伸，中心块双向拉伸。
 * 角块尺寸按密度换算成像素，避免高 DPI 屏上护角显得过小。
 */
private fun DrawScope.drawNinePatch(bitmap: ImageBitmap, cornerPx: Int) {
    val w = size.width
    val h = size.height
    val srcW = bitmap.width.toFloat()
    val srcH = bitmap.height.toFloat()

    // 源图角块尺寸不能超过源图的一半，目标角块不能超过目标尺寸的一半
    val srcCx = cornerPx.toFloat().coerceAtMost(srcW / 2f - 1f)
    val srcCy = cornerPx.toFloat().coerceAtMost(srcH / 2f - 1f)
    val dstCx = srcCx.coerceAtMost(w / 2f)
    val dstCy = srcCy.coerceAtMost(h / 2f)

    val srcXs = floatArrayOf(0f, srcCx, srcW - srcCx, srcW)
    val srcYs = floatArrayOf(0f, srcCy, srcH - srcCy, srcH)
    val dstXs = floatArrayOf(0f, dstCx, w - dstCx, w)
    val dstYs = floatArrayOf(0f, dstCy, h - dstCy, h)

    for (r in 0 until 3) {
        for (c in 0 until 3) {
            val sw = srcXs[c + 1] - srcXs[c]
            val sh = srcYs[r + 1] - srcYs[r]
            val dw = dstXs[c + 1] - dstXs[c]
            val dh = dstYs[r + 1] - dstYs[r]
            if (sw <= 0f || sh <= 0f || dw <= 0f || dh <= 0f) continue
            drawImage(
                image = bitmap,
                srcOffset = IntOffset(srcXs[c].toInt(), srcYs[r].toInt()),
                srcSize = IntSize(sw.toInt().coerceAtLeast(1), sh.toInt().coerceAtLeast(1)),
                dstOffset = IntOffset(dstXs[c].toInt(), dstYs[r].toInt()),
                dstSize = IntSize(dw.toInt().coerceAtLeast(1), dh.toInt().coerceAtLeast(1)),
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}

/** 通用按钮。 */
@Composable
fun GameButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = UITheme.Gold,
    fontSize: Int = 15,
) {
    val bg = if (enabled) accent else Color(0xFF4A4A4A)
    val fg = if (enabled) UITheme.Ink else Color(0xFF8A8A8A)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(2.5.dp, UITheme.Ink, RoundedCornerShape(10.dp))
            // 按下时缩放 + 压暗，给玩家即时的触感反馈
            .pressable(enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

/** 小标签（稀有度等）。 */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.22f))
            .border(1.dp, color.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/** 分区标题。 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 4.dp, height = 16.dp)
                .background(UITheme.Gold, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = UITheme.Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** 属性行：名称 + 数值。 */
@Composable
fun StatRow(label: String, value: String, valueColor: Color = UITheme.TextNormal) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = UITheme.TextDim, fontSize = 13.sp)
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** 全屏遮罩。 */
@Composable
fun Scrim(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(UITheme.DeepWater.copy(alpha = 0.86f))
            .clickableNoRipple(true) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
