package com.taptap.fishingidle.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taptap.fishingidle.game.Assets
import com.taptap.fishingidle.game.FishSize
import com.taptap.fishingidle.game.Species
import com.taptap.fishingidle.game.SpeciesLore

/** 中央弹窗要展示什么：图鉴解锁，或刷新体型纪录。 */
class UnlockPopup(val species: Species, val record: FishSize?)

/**
 * 解锁弹窗：屏幕正中浮出一张木质卷轴，里面是鱼、名字和资料。
 *
 * 两种用法共用这套美术 —— 第一次钓到某个鱼种（图鉴解锁），
 * 以及钓到刷新该鱼种最大体型的鱼（新纪录）。
 * 点任意处关闭，调用方也会在几秒后自动收掉。
 */
@Composable
fun UnlockPopupCard(
    popup: UnlockPopup?,
    assets: Assets,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = popup != null,
        enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.86f),
        exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.92f),
        modifier = modifier,
    ) {
        val p = popup ?: return@AnimatedVisibility
        val isRecord = p.record != null
        // 鱼图标：用该鱼种精灵图的第一帧，和游戏里看到的是同一套素材
        val fish = remember(p.species.id) { assets.firstFrame(p.species.sprite, 240)?.asImageBitmap() }
        val banner = remember { assets.scaled("unlock_banner", 760)?.asImageBitmap() }

        // 面板轻微呼吸，静止画面里也有"活着"的感觉
        val pulse by animateFloatAsState(
            targetValue = if (isRecord) 1.04f else 1.0f,
            animationSpec = tween(600),
            label = "popupPulse",
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (banner != null) {
                    Image(
                        bitmap = banner,
                        contentDescription = null,
                        modifier = Modifier.width(340.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    // 素材缺失时的兜底底板，保证文字可读
                    Box(
                        Modifier
                            .width(320.dp)
                            .height(210.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(UITheme.PanelBg),
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .width(300.dp)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        if (isRecord) "★ 新纪录 ★" else "图鉴解锁",
                        color = if (isRecord) UITheme.GoldLight else UITheme.RarityRare,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))

                    if (fish != null) {
                        Image(
                            bitmap = fish,
                            contentDescription = p.species.name,
                            modifier = Modifier.height(if (isRecord) (88f * pulse).dp else (78f * pulse).dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Spacer(Modifier.height(6.dp))

                    Text(
                        p.species.name,
                        color = UITheme.GoldLight,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (isRecord) "${p.record!!.label}体型 · ${p.species.rarity.displayName}"
                        else p.species.rarity.displayName,
                        color = if (isRecord) UITheme.GoldLight else UITheme.TextDim,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))

                    Text(
                        SpeciesLore.of(p.species),
                        color = UITheme.TextNormal,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("点击任意处关闭", color = UITheme.TextDim, fontSize = 10.sp)
                }
            }
        }
    }
}
