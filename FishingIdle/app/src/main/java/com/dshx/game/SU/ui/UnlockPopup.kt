package com.dshx.game.SU.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dshx.game.SU.game.Assets
import com.dshx.game.SU.game.FishSize
import com.dshx.game.SU.game.Rarity
import com.dshx.game.SU.game.Species
import com.dshx.game.SU.game.SpeciesLore

/** 中央弹窗要展示什么：图鉴解锁，或刷新体型纪录。 */
class UnlockPopup(val species: Species, val record: FishSize?)

/**
 * 解锁弹窗：屏幕正中浮出一张卡片，顶部是金色卷轴横幅，里面是鱼、名字和资料。
 *
 * 两种用法共用这套美术 —— 第一次钓到某个鱼种（图鉴解锁），
 * 以及钓到刷新该鱼种最大体型的鱼（新纪录）。
 * 点任意处关闭，调用方也会在几秒后自动收掉。
 *
 * ⚠️ 必须挂在**游戏根 Box** 下（见 MainActivity），不要塞进 HUD 的 Column。
 * 放在 Column 里时 fillMaxSize 会吃掉「剩余全部高度」，
 * 遮罩从 HUD 底一直铺到屏幕底，底部的划船/功能按钮会被整个挤出屏幕 ——
 * 这正是之前「一大块黑色遮罩」的原因。
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
        // 鱼图标：用该鱼种精灵图的第一帧，和游戏里看到的是同一套素材。
        // 320px 是**单帧**的预缩放宽度（firstFrame 内部先裁帧再缩放），
        // 足够清晰。
        val fish = remember(p.species.id) { assets.firstFrame(p.species.sprite, 320)?.asImageBitmap() }
        val banner = remember { assets.scaled("unlock_banner", 760)?.asImageBitmap() }

        val rarityColor = when (p.species.rarity) {
            Rarity.COMMON -> UITheme.RarityCommon
            Rarity.RARE -> UITheme.RarityRare
            Rarity.EPIC -> UITheme.RarityEpic
            Rarity.LEGEND -> UITheme.RarityLegend
        }

        // 面板轻微呼吸，静止画面里也有"活着"的感觉
        val pulse by animateFloatAsState(
            targetValue = if (isRecord) 1.05f else 1.0f,
            animationSpec = tween(600),
            label = "popupPulse",
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickableNoRipple { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(320.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(UITheme.PanelBg)
                    .border(
                        3.dp,
                        if (isRecord) UITheme.GoldLight else rarityColor,
                        RoundedCornerShape(16.dp),
                    ),
            ) {
                // 顶部金色卷轴横幅：只取原图最上面的横梁那一截当装饰条。
                // 原图是"画框"造型（中间是空的），整张铺上来内容会被红梁横穿，
                // 所以这里裁掉下半部分，只留顶部的红色横幅。
                if (banner != null) {
                    Image(
                        bitmap = banner,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                    )
                } else {
                    Box(Modifier.fillMaxWidth().height(6.dp).background(UITheme.Gold))
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        if (isRecord) "★ 新纪录 ★" else "图鉴解锁",
                        color = if (isRecord) UITheme.GoldLight else UITheme.RarityRare,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(10.dp))

                    // 鱼：放在深水底色块上，浅色鱼身也能看清
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(112.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(UITheme.DeepWater),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (fish != null) {
                            // ⚠️ 必须给**确定宽度**（fillMaxWidth）+ ContentScale.Fit。
                            // 只给 height 时，Image 会按位图固有尺寸渲染 ——
                            // 而每帧只有几十像素宽，鱼就缩成一个点，
                            // 跟「图鉴 / 商店详情」里的写法也不一致。
                            Image(
                                bitmap = fish,
                                contentDescription = p.species.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp)
                                    .scale(pulse),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            Text("🐟", fontSize = 40.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    Text(
                        p.species.name,
                        color = UITheme.GoldLight,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (isRecord) "${p.record!!.label}体型 · ${p.species.rarity.displayName}"
                        else "${p.species.rarity.displayName} · #${p.species.dexNo}",
                        color = if (isRecord) UITheme.GoldLight else rarityColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(10.dp))

                    Text(
                        SpeciesLore.of(p.species),
                        color = UITheme.TextNormal,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("点击任意处关闭", color = UITheme.TextDim, fontSize = 10.sp)
                }
            }
        }
    }
}
