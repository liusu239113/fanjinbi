package com.dshx.game.SU.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
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

/**
 * 首启隐私政策同意页。
 *
 * 合规红线：**用户点「同意」之前不初始化任何 SDK**（TapTap 登录/防沉迷、广告），
 * 也不读取任何设备标识。「同意」之后才依次启动广告 SDK 与 TapTap SDK。
 */
@Composable
fun PrivacyGate(
    onOpenPolicy: () -> Unit,
    onDecline: () -> Unit,
    onAccept: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF06030F).copy(alpha = 0.99f))
            .clickableNoRipple { },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(UITheme.PanelBg)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "隐私政策与用户协议",
                color = UITheme.Gold,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "钓鱼人生：放置大师模拟",
                color = UITheme.TextDim,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(10.dp))

            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                PrivacySection("一、我们收集的信息", listOf(
                    "设备型号、操作系统版本（用于适配和优化）",
                    "设备标识符：广告标识符 OAID、设备标识 AndroidID（用于广告展示、效果统计与反作弊）",
                    "网络类型（WiFi/移动数据，用于广告加载）",
                    "游戏存档数据（仅存储在本地设备，不上传服务器）",
                ))
                PrivacySection("二、信息使用目的", listOf(
                    "提供游戏服务、保存游戏进度",
                    "展示广告以支持游戏免费运营",
                    "优化应用性能、修复线上问题",
                ))
                PrivacySection("三、第三方 SDK 及其收集的信息", listOf(
                    "TapTap 登录 SDK：获取 AndroidID、设备型号与系统版本，用于账号登录与身份鉴权",
                    "TapTap 防沉迷 SDK：获取实名认证信息（姓名、身份证号、年龄段），用于未成年人保护（法定要求）",
                    "移动安全联盟 OAID SDK：读取设备标识符 OAID（系统支持时），用于生成广告标识",
                    "Tosin / TopOn 聚合广告 SDK 及各广告平台：获取 OAID、AndroidID、设备型号与系统版本、网络状态、设备 IP，用于广告展示、效果归因与反作弊",
                ))
                PrivacySection("四、我们已关闭的采集", listOf(
                    "本游戏已关闭 IMEI、设备序列号、MAC 地址、定位、已安装应用列表、录音等敏感信息的采集；",
                    "不申请「读取手机状态」「读写外部存储」「定位」「读取应用列表」等敏感权限；",
                    "在您点击「同意」之前，本应用不会初始化任何第三方 SDK，也不会读取任何设备标识。",
                ))
                PrivacySection("五、您的权利", listOf(
                    "您可以随时在「关于与隐私」中查看完整政策；",
                    "不同意不会初始化任何 SDK，也不会读取任何设备标识；",
                    "点击下方「不同意并退出」将直接退出游戏。",
                ))
            }

            Spacer(Modifier.height(12.dp))
            GameButton(
                text = "查看完整《隐私政策》",
                onClick = onOpenPolicy,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                accent = UITheme.WaterTop,
                fontSize = 13,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameButton(
                    text = "不同意并退出",
                    onClick = onDecline,
                    modifier = Modifier.weight(1f).height(46.dp),
                    accent = UITheme.TextDim,
                    fontSize = 14,
                )
                GameButton(
                    text = "同意并继续",
                    onClick = onAccept,
                    modifier = Modifier.weight(1f).height(46.dp),
                    accent = UITheme.Gold,
                    fontSize = 14,
                )
            }
        }
    }
}

@Composable
private fun PrivacySection(title: String, items: List<String>) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(title, color = UITheme.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        items.forEach { item ->
            Row(Modifier.fillMaxWidth().padding(bottom = 3.dp)) {
                Text("· ", color = UITheme.WaterTop, fontSize = 11.sp)
                Text(
                    item,
                    color = UITheme.TextNormal,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * TapTap 登录闸门 + 防沉迷拦截页。
 *
 * 三层门控（顺序不可换）：隐私政策 -> TapTap 登录 -> 防沉迷认证。
 * 只有防沉迷回调 LOGIN_SUCCESS(500) 才会解除门控进入游戏。
 */
@Composable
fun LoginGate(
    busy: Boolean,
    message: String,
    onLogin: () -> Unit,
    onRetryCompliance: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF06030F).copy(alpha = 0.97f))
            .clickableNoRipple { },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(UITheme.PanelBg)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("钓 鱼 人 生", color = UITheme.Cream, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("放 置 大 师 模 拟", color = UITheme.Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                "登录后可同步防沉迷状态、参与排行",
                color = UITheme.TextDim,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))

            GameButton(
                text = if (busy) "登 录 中…" else "TapTap 登 录",
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !busy,
                accent = UITheme.Gold,
                fontSize = 16,
            )
            Spacer(Modifier.height(10.dp))
            GameButton(
                text = "已登录过？重新校验防沉迷",
                onClick = onRetryCompliance,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                accent = UITheme.WaterTop,
                fontSize = 13,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "未成年人将按国家规定限制游戏时段与时长",
                color = UITheme.TextDim,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            )
            if (message.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    message,
                    color = UITheme.GoldLight,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 防沉迷校验中（中性提示）。校验期间同样阻断游戏。 */
@Composable
fun ComplianceCheckingGate() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF06030F).copy(alpha = 0.97f))
            .clickableNoRipple { },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(UITheme.PanelBg)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("健 康 系 统 校 验 中", color = UITheme.WaterTop, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "正在向防沉迷服务确认账号状态",
                color = UITheme.TextDim,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Text("校验未通过前无法进入游戏", color = UITheme.TextDim, fontSize = 10.sp)
        }
    }
}

/**
 * 防沉迷拦截页（宵禁 / 时长上限 / 年龄限制 / 实名未完成 / 网络异常）。
 *
 * 这里**没有任何可以就地解除拦截的按钮**：
 * 「重新校验」只是重新发起一次认证，拦截状态保持到 SDK 回调为止；
 * 「切换账号」会退出当前账号回到登录页。
 */
@Composable
fun ComplianceBlockedGate(
    message: String,
    onRetry: () -> Unit,
    onSwitchAccount: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF06030F).copy(alpha = 0.98f))
            .clickableNoRipple { },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(UITheme.PanelBg)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("健 康 游 戏 提 示", color = UITheme.TextBad, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Text(
                message,
                color = UITheme.TextNormal,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "本游戏严格遵守国家关于未成年人游戏时段与时长的规定",
                color = UITheme.TextDim,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            GameButton(
                text = "重 新 校 验",
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                accent = UITheme.WaterTop,
                fontSize = 15,
            )
            Spacer(Modifier.height(8.dp))
            GameButton(
                text = "切 换 账 号",
                onClick = onSwitchAccount,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                accent = UITheme.TextDim,
                fontSize = 13,
            )
        }
    }
}

/**
 * 激励视频加载浮层。
 * 广告 SDK 从 load 到真正播放有一段时间（弱网下更久），
 * 没有反馈玩家会以为「点了没反应」而反复点。
 *
 * ⚠️ 用 `Dialog` 承载，而不是普通 Composable —— 这一点是必须的：
 * 广告入口大多在商店 / 礼包 / 转生 / 仓库这些 Compose `Dialog` 里，
 * 每个 `Dialog` 都是独立的 window 层，永远盖在 Activity 视图之上。
 * 浮层若只是 Activity 视图里的一个 Box，就会被弹窗整个压住，
 * 表现就是「点了广告什么反应都没有」。Dialog 之间按弹出顺序叠，
 * 浮层最后弹，所以在最上面。
 */
@Composable
fun AdLoadingOverlay(
    slowHint: Boolean,
    modifier: Modifier = Modifier,
) {
    Dialog(
        // 不能点掉：广告已经请求出去了，关掉浮层只会让玩家以为取消成功
        onDismissRequest = { },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickableNoRipple { },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .width(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(UITheme.PanelBg)
                    .border(2.dp, UITheme.Gold.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("广 告 加 载 中", color = UITheme.WaterTop, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                DotsPulse()
                if (slowHint) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "暂时没有广告填充，稍后再试",
                        color = UITheme.TextDim,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * 三个呼吸跳动的圆点。
 * 之前是静态文本 "● ● ●"，看起来像卡死了；这里让它动起来，
 * 玩家一眼就知道「还在加载」而不是「已经死了」。
 */
@Composable
private fun DotsPulse() {
    val transition = rememberInfiniteTransition(label = "ad_dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            // 每个点比前一个慢 1/3 个周期，形成依次亮起的流水感
            val t = (phase - i * 0.34f + 3f) % 3f
            val lit = (1f - (t / 1.2f)).coerceIn(0f, 1f)
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(9.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(UITheme.Gold.copy(alpha = 0.28f + 0.72f * lit)),
            )
        }
    }
}
