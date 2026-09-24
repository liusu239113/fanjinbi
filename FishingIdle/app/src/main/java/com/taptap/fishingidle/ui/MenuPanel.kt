package com.taptap.fishingidle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taptap.fishingidle.game.GameState
import com.taptap.fishingidle.game.Settings
import com.taptap.fishingidle.game.formatNumber

/**
 * 设置面板。主菜单和游戏内共用同一套内容。
 *
 * [showResume] 为 true 时显示"继续游戏"（游戏内打开）；
 * 为 false 时显示"返回"（主菜单打开）。
 */
@Composable
fun MenuPanel(
    state: GameState,
    settings: Settings,
    assets: com.taptap.fishingidle.game.Assets,
    showResume: Boolean = true,
    onVolumeChanged: () -> Unit,
    onReset: () -> Unit,
    onExitToMainMenu: () -> Unit = {},
    onClose: () -> Unit,
) {
    Scrim(onClose) {
        WoodPanel(
            Modifier
                .fillMaxWidth(0.92f)
                .padding(8.dp),
            assets = assets,
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "设置",
                    color = UITheme.GoldLight,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )

                SectionTitle("音量")
                // Settings 是普通对象，滑块必须持有本地状态才会跟着手指移动
                VolumeSlider("总音量", settings.masterVolume) {
                    settings.masterVolume = it; onVolumeChanged()
                }
                VolumeSlider("音效", settings.sfxVolume) {
                    settings.sfxVolume = it; onVolumeChanged()
                }
                VolumeSlider("音乐", settings.bgmVolume) {
                    settings.bgmVolume = it; onVolumeChanged()
                }

                SectionTitle("显示")
                var hideText by remember { mutableStateOf(settings.hideFloatingText) }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("隐藏金币浮动文字", color = UITheme.TextNormal, fontSize = 14.sp)
                    Switch(
                        checked = hideText,
                        onCheckedChange = {
                            hideText = it
                            settings.hideFloatingText = it
                            onVolumeChanged()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = UITheme.Gold,
                            checkedTrackColor = UITheme.GoldDark,
                            uncheckedThumbColor = UITheme.TextDim,
                            uncheckedTrackColor = UITheme.SlotBg,
                        ),
                    )
                }

                SectionTitle("怎么玩")
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    HintLine("点击水面抛竿，浮标落水后等鱼咬钩")
                    HintLine("浮标下沉时点击收线，连续点击收得更快")
                    HintLine("越稀有的鱼越容易挣脱，升级线轮能提速")
                    HintLine("购买鱼苗增加鱼群，雇佣钓手自动钓鱼")
                    HintLine("升级「自动收线」「智能浮标」后可以彻底放手")
                }

                Spacer(Modifier.height(2.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GameButton(
                        "重置存档", onReset,
                        modifier = Modifier.weight(1f),
                        accent = Color(0xFFB0503C),
                    )
                    GameButton(
                        if (showResume) "继续游戏" else "返回", onClose,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (showResume) {
                    GameButton(
                        "返回主菜单", onExitToMainMenu,
                        modifier = Modifier.fillMaxWidth(),
                        accent = UITheme.WaterTop,
                    )
                }
            }
        }
    }
}

@Composable
private fun VolumeSlider(label: String, initial: Float, onChange: (Float) -> Unit) {
    // 本地状态保证滑块跟手；写入外部 Settings 由 onChange 负责
    var value by remember { mutableFloatStateOf(initial) }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = UITheme.TextNormal, fontSize = 14.sp, modifier = Modifier.width(64.dp))
        Slider(
            value = value,
            onValueChange = {
                value = it
                onChange(it)
            },
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = UITheme.Gold,
                activeTrackColor = UITheme.GoldDark,
                inactiveTrackColor = UITheme.SlotBg,
            ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${(value * 100).toInt()}",
            color = UITheme.TextDim, fontSize = 12.sp,
            modifier = Modifier.width(30.dp),
        )
    }
}

@Composable
private fun HintLine(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("•", color = UITheme.Gold, fontSize = 13.sp)
        Spacer(Modifier.width(6.dp))
        Text(text, color = UITheme.TextDim, fontSize = 13.sp, lineHeight = 17.sp)
    }
}

/** 重置确认弹窗。 */
@Composable
fun ResetConfirmDialog(
    assets: com.taptap.fishingidle.game.Assets,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Scrim(onDismiss) {
        WoodPanel(Modifier.fillMaxWidth(0.8f).padding(8.dp), assets = assets) {
            Column(
                Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("确定要重置吗？", color = UITheme.TextBad, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(
                    "所有鱼群、升级和金币都会清空，且无法恢复。",
                    color = UITheme.TextDim,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GameButton("取消", onDismiss, accent = UITheme.WaterTop)
                    GameButton("确认重置", onConfirm, accent = Color(0xFFB0503C))
                }
            }
        }
    }
}
