package com.dshx.game.SU.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dshx.game.SU.game.GameState
import com.dshx.game.SU.game.formatNumber

/**
 * 金币旁的「广告礼包」入口。
 *
 * 放在金币条正下方、连击条上方 —— 玩家每次看金币都会扫到它，
 * 是转化率最高的一类广告位。
 *
 * 三种状态：
 *  - 有免费次数（金框 + 红点角标）：最显眼，点击直接看广告
 *  - 今日已用完：灰掉并显示倒计时提示
 *  - 广告未就绪：显示「接入中」，不误导玩家
 */
@Composable
fun AdGiftButton(
    leftToday: Int,
    ready: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val enabled = leftToday > 0 && ready
    val border = when {
        enabled -> UITheme.Gold
        else -> UITheme.TextDim.copy(alpha = 0.5f)
    }
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(UITheme.DeepWater.copy(alpha = 0.88f))
            .border(2.dp, border, RoundedCornerShape(10.dp))
            .pressable(enabled) { onClick() }
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🎁", fontSize = 15.sp)
        Spacer(Modifier.width(5.dp))
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
                    leftToday <= 0 -> "今日已领完"
                    else -> "还剩 $leftToday 次"
                },
                color = UITheme.TextDim,
                fontSize = 9.sp,
            )
        }
        // 有次数时挂一个红点，抓住视觉
        if (enabled) {
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .size(7.dp)
                    .background(UITheme.TextBad, RoundedCornerShape(4.dp)),
            )
        }
    }
}

/** 广告礼包弹窗里的一个可选礼包。 */
class AdGift(
    val id: String,
    val icon: String,
    val title: String,
    val desc: String,
    val action: () -> Unit,
)

/**
 * 广告礼包面板：金币旁那个图标点开后弹出。
 *
 * 里面是若干「看广告领东西」的礼包，含**合作 buff**（限时收益加成）。
 * 一次弹窗里给多个选择，比单个按钮的广告触发率高得多 ——
 * 玩家总会挑一个看起来最划算的。
 */
@Composable
fun AdGiftDialog(
    gifts: List<AdGift>,
    statusText: String,
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
                    .padding(horizontal = 22.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(UITheme.PanelBg)
                    .border(3.dp, UITheme.Gold, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("广告礼包", color = UITheme.GoldLight, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(
                    "看一段广告，免费领取",
                    color = UITheme.TextDim,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(12.dp))

                gifts.forEach { gift ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(UITheme.SlotBg)
                            .border(1.5.dp, UITheme.GoldDark.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .pressable { gift.action() }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(gift.icon, fontSize = 22.sp)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                gift.title,
                                color = UITheme.GoldLight,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(gift.desc, color = UITheme.TextDim, fontSize = 10.sp, lineHeight = 14.sp)
                        }
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(UITheme.Gold)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text("领取", color = UITheme.Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Text(
                    statusText,
                    color = UITheme.TextDim,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
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

/**
 * 合作 buff 状态条：有激活中的 buff 时显示在金币条下方，带倒计时。
 *
 * 让玩家随时看得见「buff 还剩多久」，是促使他下次再点广告的最强动机。
 */
@Composable
fun BuffStrip(
    label: String,
    remainSeconds: Float,
    totalSeconds: Float,
    modifier: Modifier = Modifier,
) {
    val frac = (remainSeconds / totalSeconds).coerceIn(0f, 1f)
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(UITheme.TextGood.copy(alpha = 0.20f))
            .border(1.5.dp, UITheme.TextGood.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⚡", fontSize = 13.sp)
        Spacer(Modifier.width(5.dp))
        Column {
            Text(label, color = UITheme.TextGood, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier
                    .width(96.dp)
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
            "${remainSeconds.toInt()}s",
            color = UITheme.TextGood,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 把奖励数量格式化成礼包描述里的一行，避免各处重复拼字符串。 */
fun giftDesc(amount: Double, unit: String = "金币"): String = "立得 ${formatNumber(amount)} $unit"

/** 兼容旧调用点：把 GameState 的当前金币换算成礼包基准量。 */
fun giftBase(state: GameState): Double = (state.money * 0.08).coerceAtLeast(50.0)
