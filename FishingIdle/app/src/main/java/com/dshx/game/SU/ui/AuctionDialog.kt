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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.dshx.game.SU.game.AuctionSession
import com.dshx.game.SU.game.Bestiary
import com.dshx.game.SU.game.StoredFish
import com.dshx.game.SU.game.formatNumber
import kotlinx.coroutines.delay

/**
 * 拍卖行**场景**（不是弹窗列表）。
 *
 * 独立的一屏：拍卖场背景 + 一屋子买家立绘，出价一轮一轮现场长出来。
 * 玩家看着价格往上走，自己决定什么时候落槌 —— 等得越久可能越高，
 * 但耐心耗尽的买家会退场、把出价一起带走，最高价随时可能掉下来。
 *
 * 之前那版把 4 个买家的最终出价一次性列出来，玩家没有任何"过程"可看，
 * 也没有决策空间 —— 那不是拍卖，只是换了个名字的卖出按钮。
 */
@Composable
fun AuctionDialog(
    fish: StoredFish,
    session: AuctionSession,
    assets: Assets,
    /** 每次推进/结算后 +1，用来触发重组。 */
    revision: Int,
    onAdvance: () -> Unit,
    onSettle: () -> Unit,
    onPass: () -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION") revision
    val species = Bestiary.speciesById(fish.speciesId)
    val fishIcon = remember(fish.speciesId) {
        species?.let { assets.firstFrame(it.sprite, 220)?.asImageBitmap() }
    }
    val hallBg = remember { assets.raw("auction_hall")?.asImageBitmap() }

    // 逐轮推进：每 1.1 秒拍一次，玩家随时可以落槌打断。
    // 用 round 当 key，每推进一轮就重新起一次计时。
    LaunchedEffect(session.round, session.canContinue) {
        if (session.canContinue) {
            delay(1100)
            onAdvance()
        }
    }

    // 谁刚抬了价：拿 session 记下的"本轮开始前的出价"对比当前值。
    // 这个快照由 AuctionSession.advance 维护，不能在这里用 remember 自己存 ——
    // 重组时机不可控，会永远比出相等的结果。
    val justRaised = remember(session.round) {
        session.bidders
            .filter { it.bid > (session.previousBids[it.name] ?: 0.0) + 1e-9 }
            .map { it.name }
            .toSet()
    }

    Dialog(
        onDismissRequest = onPass,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            // ---- 拍卖场背景 ----
            if (hallBg != null) {
                Image(
                    hallBg, contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxSize().background(UITheme.WoodDark))
            }
            // 压一层暗罩：立绘与数字要看得清
            Box(Modifier.fillMaxSize().background(Color(0xB0140A06)))

            Column(
                Modifier.fillMaxSize().padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ---- 标题 ----
                Text(
                    "拍 卖 行",
                    color = UITheme.GoldLight, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                )
                Text(
                    if (session.canContinue) "第 ${session.round} 轮竞价中…" else "竞价结束",
                    color = UITheme.TextDim, fontSize = 11.sp,
                )

                Spacer(Modifier.height(10.dp))

                // ---- 拍品 ----
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xCC241208))
                        .border(2.dp, UITheme.Gold.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(58.dp).clip(RoundedCornerShape(8.dp))
                            .background(UITheme.DeepWater),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (fishIcon != null) {
                            Image(
                                fishIcon, contentDescription = species?.name,
                                modifier = Modifier.fillMaxSize().padding(2.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${species?.name ?: ""} · ${fish.size.label}",
                            color = UITheme.Cream, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "行情价 🪙${formatNumber(session.marketPrice)}",
                            color = UITheme.TextDim, fontSize = 11.sp,
                        )
                    }
                    // 品质：决定能招来什么档次的买家
                    Column(horizontalAlignment = Alignment.End) {
                        Text("品相", color = UITheme.TextDim, fontSize = 10.sp)
                        Text(
                            qualityLabel(session.quality),
                            color = UITheme.GoldLight, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ---- 买家席 ----
                Text(
                    "买家席",
                    color = UITheme.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    session.bidders.forEach { b ->
                        BidderCard(
                            bidder = b,
                            assets = assets,
                            marketPrice = session.marketPrice,
                            justRaised = b.name in justRaised,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // ---- 当前最高价 ----
                val top = session.topBidder
                if (top != null) {
                    val pct = session.premiumPct
                    Text(
                        top.name,
                        color = UITheme.Cream, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "🪙${formatNumber(session.topAmount)}",
                        color = UITheme.GoldLight, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (pct >= 0) "高于行情 $pct%" else "低于行情 $pct%",
                        color = if (pct >= 0) UITheme.TextGood else UITheme.TextBad,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    )
                } else {
                    Text(
                        "等待买家出价…",
                        color = UITheme.TextDim, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(14.dp))

                // ---- 决策：落槌 / 再等一轮 ----
                val netAmount = session.topAmount * (1.0 - AuctionSession.FEE_RATE)
                GameButton(
                    if (session.hasBid)
                        "落槌成交 · 🪙${formatNumber(netAmount)}"
                    else "等待出价…",
                    onSettle,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = session.hasBid,
                    accent = UITheme.Gold,
                    fontSize = 16,
                )
                Spacer(Modifier.height(7.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GameButton(
                        if (session.canContinue) "再等一轮" else "已无买家",
                        onAdvance,
                        modifier = Modifier.weight(1f).height(40.dp),
                        enabled = session.canContinue,
                        accent = UITheme.WaterTop,
                        fontSize = 13,
                    )
                    GameButton(
                        "离场（不卖）", onPass,
                        modifier = Modifier.weight(1f).height(40.dp),
                        accent = Color(0xFF7A5A4A),
                        fontSize = 13,
                    )
                }
                Text(
                    "成交扣 ${(AuctionSession.FEE_RATE * 100).toInt()}% 手续费 · 买家退场后其出价作废",
                    color = UITheme.TextDim, fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
    }
}

/** 品相文案：把品质数值翻译成玩家能懂的档次。 */
private fun qualityLabel(q: Double): String = when {
    q >= 6.0 -> "传世"
    q >= 4.0 -> "上品"
    q >= 2.5 -> "佳品"
    q >= 1.2 -> "寻常"
    else -> "普通"
}

/** 一位买家的席位卡：立绘 + 名字 + 当前出价。 */
@Composable
private fun BidderCard(
    bidder: com.dshx.game.SU.game.BidderState,
    assets: Assets,
    marketPrice: Double,
    justRaised: Boolean,
    modifier: Modifier = Modifier,
) {
    val portrait = remember(bidder.kind.sprite) {
        assets.raw(bidder.kind.sprite)?.asImageBitmap()
    }
    val out = !bidder.inRoom
    // 刚抬价的那位描金边、亮一档；退场的压暗
    val accent = when {
        out -> UITheme.TextDim.copy(alpha = 0.35f)
        justRaised -> UITheme.GoldLight
        bidder.bid > 0 -> UITheme.Gold.copy(alpha = 0.7f)
        else -> UITheme.TextDim.copy(alpha = 0.5f)
    }

    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (out) Color(0x99100804) else Color(0xCC1A0E06))
            .border(if (justRaised) 2.5.dp else 1.5.dp, accent, RoundedCornerShape(10.dp))
            .padding(4.dp)
            .alpha(if (out) 0.45f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().height(62.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (portrait != null) {
                Image(
                    portrait, contentDescription = bidder.kind.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Text(
            bidder.kind.displayName,
            color = if (out) UITheme.TextDim else UITheme.Cream,
            fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        )
        Text(
            bidder.name.substringAfter("·", ""),
            color = UITheme.TextDim, fontSize = 8.sp, maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            when {
                out -> "退场"
                bidder.bid > 0 -> "🪙${formatNumber(marketPrice * bidder.bid)}"
                else -> "观望"
            },
            color = when {
                out -> UITheme.TextDim
                bidder.bid > 0 -> UITheme.GoldLight
                else -> UITheme.TextDim
            },
            fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        )
    }
}
