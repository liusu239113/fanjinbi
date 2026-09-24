package com.dshx.game.SU.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dshx.game.SU.game.Assets
import com.dshx.game.SU.game.OfflineEarnings
import com.dshx.game.SU.game.formatNumber

/**
 * 离线收益结算弹窗。
 * 玩家离开期间自动钓手仍在工作，回来时一次性结算。
 */
@Composable
fun OfflinePanel(
    result: OfflineEarnings.Result,
    assets: Assets,
    onClaim: () -> Unit,
) {
    Scrim(onClaim) {
        WoodPanel(
            Modifier
                .fillMaxWidth(0.86f)
                .padding(8.dp),
            assets = assets,
            cornerPx = 110,
        ) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("🎣", fontSize = 40.sp)
                Text(
                    "钓手们的收获",
                    color = UITheme.GoldLight,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "你离开了 ${formatDuration(result.seconds)}",
                    color = UITheme.TextDim,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(2.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatBlock("金币", "🪙${formatNumber(result.money)}", UITheme.GoldLight)
                    StatBlock("渔获", "${result.catches} 条", UITheme.TextGood)
                }

                Text(
                    "离线期间按 ${(OfflineEarnings.EFFICIENCY * 100).toInt()}% 效率结算，" +
                        "最多累计 ${OfflineEarnings.MAX_HOURS.toInt()} 小时",
                    color = UITheme.TextDim,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )

                GameButton(
                    "收下",
                    onClaim,
                    modifier = Modifier.fillMaxWidth(),
                    accent = UITheme.Gold,
                    fontSize = 17,
                )
            }
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = UITheme.TextDim, fontSize = 11.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = color, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

/** 把秒数格式化成"3 小时 12 分钟"。 */
fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return when {
        h > 0 -> "${h} 小时 ${m} 分钟"
        m > 0 -> "${m} 分钟"
        else -> "${seconds} 秒"
    }
}
