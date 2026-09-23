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
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale as ComposeContentScale
import com.taptap.fishingidle.game.Assets

/**
 * 木质面板容器。
 * 优先使用资源里的九宫格木纹图（四角护角不拉伸），
 * 资源缺失时退回程序化渐变，保证任何情况下界面都可用。
 */
@Composable
fun WoodPanel(
    modifier: Modifier = Modifier,
    assets: Assets? = null,
    content: @Composable () -> Unit,
) {
    val panelBmp = assets?.let { a ->
        a.raw("wood_panel")?.asImageBitmap()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(UITheme.WoodLight, UITheme.Wood, UITheme.WoodDark)
                )
            )
            .border(3.dp, UITheme.Ink, RoundedCornerShape(14.dp))
            .border(1.5.dp, UITheme.GoldDark, RoundedCornerShape(12.dp))
    ) {
        if (panelBmp != null) {
            Image(
                bitmap = panelBmp,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ComposeContentScale.FillBounds,
                alpha = 0.55f,
            )
        }
        content()
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
            .clickableNoRipple(enabled) { onClick() }
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
