package com.dshx.game.SU.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dshx.game.SU.game.Assets
import com.dshx.game.SU.game.GameState
import com.dshx.game.SU.game.formatNumber

/**
 * 金币旁的「广告礼包」入口。
 *
 * 放在金币条正下方 —— 玩家每次看金币都会扫到它，转化率最高的一类广告位。
 * 图标用美术资源（ad_gift），不用 emoji：emoji 在不同 ROM 上渲染不一致，
 * 也跟游戏画风不搭。
 */
@Composable
fun AdGiftButton(
    assets: Assets,
    badge: Int,
    enabled: Boolean,
    ready: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val icon = remember { assets.raw("ad_gift")?.asImageBitmap() }
    val border = if (enabled) UITheme.Gold else UITheme.TextDim.copy(alpha = 0.5f)

    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(UITheme.DeepWater.copy(alpha = 0.9f))
            .border(2.dp, border, RoundedCornerShape(10.dp))
            .pressable(enabled) { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            if (icon != null) {
                Image(
                    icon, contentDescription = "广告礼包",
                    modifier = Modifier.size(26.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(Modifier.size(26.dp).background(UITheme.Gold, RoundedCornerShape(6.dp)))
            }
            // 有可领次数时挂一个数字角标，比单纯红点信息量大
            if (enabled && badge > 0) {
                Box(
                    Modifier
                        .size(14.dp)
                        .background(UITheme.TextBad, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (badge > 9) "9+" else "$badge",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                "广告礼包",
                color = if (enabled) UITheme.GoldLight else UITheme.TextDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                when {
                    !ready -> "接入中"
                    enabled -> "$badge 项可领"
                    else -> "稍后再来"
                },
                color = UITheme.TextDim,
                fontSize = 9.sp,
            )
        }
    }
}

/** 广告礼包弹窗里的一个条目。 */
class AdGift(
    val id: String,
    /** 图标资源名（art/ 下的文件名，不含扩展名）。 */
    val icon: String,
    val title: String,
    val desc: String,
    /** 已领完 / 条件不满足时置灰，点击只提示。 */
    val locked: Boolean = false,
    val action: () -> Unit,
)

/**
 * 广告礼包面板：金币旁那个图标点开后弹出。
 *
 * 一次给多个选择比单个按钮的触发率高得多 —— 玩家总会挑一个看起来最划算的。
 * 列表可滚动，条目多也不怕撑爆屏幕。
 */
@Composable
fun AdGiftDialog(
    gifts: List<AdGift>,
    statusText: String,
    assets: Assets,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xCC000000))
                .clickableNoRipple { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(UITheme.PanelBg)
                    .border(3.dp, UITheme.Gold, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("广告礼包", color = UITheme.GoldLight, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text("看一段广告，免费领取", color = UITheme.TextDim, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))

                Column(
                    Modifier
                        .height(360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    gifts.forEach { gift ->
                        AdGiftRow(gift, assets)
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    statusText,
                    color = UITheme.TextDim,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                GameButton(
                    text = "关闭",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    accent = UITheme.WaterTop,
                    fontSize = 14,
                )
            }
        }
    }
}

@Composable
private fun AdGiftRow(gift: AdGift, assets: Assets) {
    val icon = remember(gift.icon) { assets.raw(gift.icon)?.asImageBitmap() }
    val dim = gift.locked

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (dim) UITheme.SlotBg.copy(alpha = 0.5f) else UITheme.SlotBg)
            .border(
                1.5.dp,
                if (dim) UITheme.TextDim.copy(alpha = 0.3f) else UITheme.GoldDark.copy(alpha = 0.7f),
                RoundedCornerShape(10.dp),
            )
            .pressable(!dim) { gift.action() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(UITheme.DeepWater),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    icon, contentDescription = gift.title,
                    modifier = Modifier.size(32.dp).alpha(if (dim) 0.35f else 1f),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                gift.title,
                color = if (dim) UITheme.TextDim else UITheme.GoldLight,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                gift.desc,
                color = if (dim) UITheme.TextDim.copy(alpha = 0.7f) else UITheme.TextNormal,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (dim) UITheme.TextDim.copy(alpha = 0.3f) else UITheme.Gold)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                if (dim) "已领" else "领取",
                color = if (dim) UITheme.TextDim else UITheme.Ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 需要 alpha 修饰符时的辅助（避免重复 import）。 */

/**
 * 限时 buff 状态条：有激活中的 buff 时显示，带倒计时进度条。
 *
 * 让玩家随时看得见「buff 还剩多久」，是促使他下次再点广告的最强动机。
 * 多个 buff 时纵向排开。
 */
@Composable
fun BuffStrip(
    entries: List<Triple<String, Float, Float>>,
    assets: Assets,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entries.forEach { (iconName, remain, total) ->
            val icon = remember(iconName) { assets.raw(iconName)?.asImageBitmap() }
            val frac = if (total > 0f) (remain / total).coerceIn(0f, 1f) else 0f
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(UITheme.TextGood.copy(alpha = 0.22f))
                    .border(1.5.dp, UITheme.TextGood.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Image(
                        icon, contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Column {
                    Text(
                        buffLabel(iconName),
                        color = UITheme.TextGood,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier
                            .width(110.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(UITheme.DeepWater),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(frac)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(UITheme.TextGood),
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    formatBuffTime(remain),
                    color = UITheme.TextGood,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** buff 的中文名（用图标资源名反查）。 */
fun buffLabel(iconName: String): String = when (iconName) {
    "ad_double" -> "双倍收益"
    "ad_bait" -> "稀有诱饵"
    "ad_speed" -> "钓手加速"
    "ad_time" -> "收线加速"
    "ad_dex" -> "图鉴加成翻倍"
    else -> "加成中"
}

/** 秒数格式化成 mm:ss，超过一小时显示 h:mm:ss。 */
fun formatBuffTime(seconds: Float): String {
    val s = seconds.toInt().coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** 把奖励数量格式化成礼包描述里的一行。 */
fun giftDesc(amount: Double, unit: String = "金币"): String = "立得 ${formatNumber(amount)} $unit"

/** 以当前金币为基准算礼包量（避免礼包在后期变得毫无意义）。 */
fun giftBase(state: GameState): Double = (state.money * 0.08).coerceAtLeast(50.0)
