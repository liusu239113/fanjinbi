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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dshx.game.SU.game.Assets
import com.dshx.game.SU.game.Auction
import com.dshx.game.SU.game.AuctionResult
import com.dshx.game.SU.game.Bestiary
import com.dshx.game.SU.game.StoredFish
import com.dshx.game.SU.game.formatNumber

/**
 * 拍卖行弹窗：把一条鱼交给多个 AI 买家竞价。
 *
 * 玩家看得到每个买家的身份与出价，自己决定「成交」还是「流拍」——
 * 这才是仓库里真正的玩法，而不是"按行情价卖掉"。
 */
@Composable
fun AuctionDialog(
    fish: StoredFish,
    result: AuctionResult,
    assets: Assets,
    onSettle: () -> Unit,
    onPass: () -> Unit,
) {
    val species = Bestiary.speciesById(fish.speciesId)
    val icon = remember(fish.speciesId) {
        species?.let { assets.firstFrame(it.sprite, 320)?.asImageBitmap() }
    }

    Dialog(
        onDismissRequest = onPass,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xCC000000))
                .clickableNoRipple { onPass() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .width(330.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(UITheme.PanelBg)
                    .border(2.dp, UITheme.Gold, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("拍 卖 行", color = UITheme.GoldLight, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                // 拍品
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .width(72.dp).height(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(UITheme.DeepWater),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (icon != null) {
                            Image(
                                icon, contentDescription = species?.name,
                                modifier = Modifier.fillMaxSize().padding(3.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "${species?.name ?: ""} · ${fish.size.label}",
                            color = UITheme.Cream, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "行情价 🪙${formatNumber(result.marketPrice)}",
                            color = UITheme.TextDim, fontSize = 11.sp,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "买家出价",
                    color = UITheme.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))

                // 出价列表：最高价高亮
                val top = result.topBid
                result.bids.forEach { bid ->
                    val isTop = bid === top
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 5.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isTop) UITheme.Gold.copy(alpha = 0.20f) else UITheme.SlotBg
                            )
                            .border(
                                if (isTop) 2.dp else 1.dp,
                                if (isTop) UITheme.Gold else UITheme.TextDim.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            bid.bidder,
                            color = if (isTop) UITheme.GoldLight else UITheme.TextNormal,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        val pct = ((bid.factor - 1.0) * 100).toInt()
                        Text(
                            "🪙${formatNumber(result.marketPrice * bid.factor)}",
                            color = if (bid.factor >= 1.0) UITheme.TextGood else UITheme.TextBad,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            if (pct >= 0) "+$pct%" else "$pct%",
                            color = if (bid.factor >= 1.0) UITheme.TextGood else UITheme.TextBad,
                            fontSize = 10.sp,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                val topAmount = result.topAmount * (1.0 - Auction.FEE_RATE)
                Text(
                    "最高成交 🪙${formatNumber(topAmount)}（扣 ${(Auction.FEE_RATE * 100).toInt()}% 手续费）",
                    color = UITheme.GoldLight, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                if (result.premiumPct > 0) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "比行情价高 ${result.premiumPct}%，是个好价",
                        color = UITheme.TextGood, fontSize = 11.sp,
                    )
                } else {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "低于行情价，可以流拍再等",
                        color = UITheme.TextDim, fontSize = 11.sp,
                    )
                }

                Spacer(Modifier.height(12.dp))
                GameButton(
                    "成交 · 🪙${formatNumber(topAmount)}",
                    onSettle,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    accent = UITheme.Gold, fontSize = 15,
                )
                Spacer(Modifier.height(7.dp))
                GameButton(
                    "流拍（不卖）", onPass,
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    accent = UITheme.WaterTop, fontSize = 13,
                )
            }
        }
    }
}
