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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.dshx.game.SU.game.Bestiary
import com.dshx.game.SU.game.GameState
import com.dshx.game.SU.game.MerchantOffer
import com.dshx.game.SU.game.StoredFish
import com.dshx.game.SU.game.Warehouse
import com.dshx.game.SU.game.formatNumber

/**
 * 仓库面板：九宫格展示收藏的鱼，可单卖 / 全卖 / 扩容。
 *
 * 玩法核心是**行情**：价格按天波动，鱼贩子每 10 分钟来一次并给出随机报价
 * （可能压价也可能大赚），所以"什么时候卖"本身就是决策。
 */
@Composable
fun WarehousePanel(
    state: GameState,
    assets: Assets,
    revision: Int,
    onSell: (Int) -> Unit,
    onSellAll: () -> Unit,
    onUpgrade: () -> Unit,
    onClose: () -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION") revision
    val now = System.currentTimeMillis()
    val cap = Warehouse.capacity(state)
    val stored = Warehouse.sorted(state.warehouse)
    val offer = state.merchantOffer
    val marketTotal = Warehouse.marketTotal(state.warehouse, now)

    Column(Modifier.fillMaxSize()) {
        // ---- 顶部：容量 / 行情 ----
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                SectionTitle("渔获仓库")
                Spacer(Modifier.height(3.dp))
                Text(
                    "${state.warehouse.size} / $cap 格",
                    color = if (state.warehouse.size >= cap) UITheme.TextBad else UITheme.TextNormal,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Warehouse.trendText(now),
                    color = if (Warehouse.marketFactor(now) >= 1.0) UITheme.TextGood else UITheme.TextBad,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                )
                Text(
                    "市值 🪙${formatNumber(marketTotal)}",
                    color = UITheme.GoldLight, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // ---- 鱼贩子报价条 ----
        if (offer != null) {
            MerchantBar(offer = offer, remain = state.merchantStay)
            Spacer(Modifier.height(8.dp))
        } else {
            Text(
                "鱼贩子还有 ${formatBuffTime(state.merchantTimer)} 到访",
                color = UITheme.TextDim, fontSize = 11.sp,
            )
            Spacer(Modifier.height(8.dp))
        }

        // ---- 九宫格 ----
        Box(Modifier.weight(1f)) {
            if (stored.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("仓库是空的", color = UITheme.TextDim, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "钓到新鱼种或刷新体型纪录时，\n那条鱼会自动存进这里",
                            color = UITheme.TextDim, fontSize = 11.sp,
                            textAlign = TextAlign.Center, lineHeight = 16.sp,
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    items(stored, key = { it.storedAt.toString() + it.speciesId }) { fish ->
                        WarehouseSlot(
                            fish = fish,
                            now = now,
                            offerFactor = offer?.factor,
                            assets = assets,
                            onClick = { onSell(state.warehouse.indexOf(fish)) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ---- 底部操作 ----
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GameButton(
                "全部卖出",
                onSellAll,
                modifier = Modifier.weight(1f).height(42.dp),
                enabled = state.warehouse.isNotEmpty(),
                accent = UITheme.Gold,
                fontSize = 13,
            )
            GameButton(
                if (Warehouse.canUpgrade(state))
                    "扩容 · 🪙${formatNumber(Warehouse.upgradePrice(state))}"
                else "已满级",
                onUpgrade,
                modifier = Modifier.weight(1f).height(42.dp),
                enabled = Warehouse.canUpgrade(state) &&
                    state.money >= Warehouse.upgradePrice(state),
                accent = UITheme.WaterTop,
                fontSize = 12,
            )
            GameButton(
                "关闭", onClose,
                modifier = Modifier.height(42.dp),
                accent = UITheme.WaterTop,
                fontSize = 13,
            )
        }
    }
}

/** 鱼贩报价条：明确写出"比行情高/低多少"，这是玩家决策的全部依据。 */
@Composable
private fun MerchantBar(offer: MerchantOffer, remain: Float) {
    val pct = ((offer.factor - 1.0) * 100).toInt()
    val color = if (offer.isGood) UITheme.TextGood else UITheme.TextBad
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.18f))
            .border(2.dp, color.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🧑🌾", fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (offer.isGood) "鱼贩子出价不错！" else "鱼贩子在压价",
                color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            )
            Text(
                "全部按行情价 ${if (pct >= 0) "+" else ""}$pct% 收购 · 还剩 ${remain.toInt()}s",
                color = UITheme.TextNormal, fontSize = 10.sp,
            )
        }
    }
}

/** 九宫格里的一个格子。点一下 = 卖掉这条。 */
@Composable
private fun WarehouseSlot(
    fish: StoredFish,
    now: Long,
    offerFactor: Double?,
    assets: Assets,
    onClick: () -> Unit,
) {
    val species = Bestiary.speciesById(fish.speciesId)
    val icon = remember(fish.speciesId) {
        species?.let { assets.firstFrame(it.sprite, 160)?.asImageBitmap() }
    }
    val rarityColor = when (species?.rarity) {
        com.dshx.game.SU.game.Rarity.COMMON -> UITheme.RarityCommon
        com.dshx.game.SU.game.Rarity.RARE -> UITheme.RarityRare
        com.dshx.game.SU.game.Rarity.EPIC -> UITheme.RarityEpic
        com.dshx.game.SU.game.Rarity.LEGEND -> UITheme.RarityLegend
        null -> UITheme.RarityCommon
    }
    val price = Warehouse.marketValue(fish, now) * (offerFactor ?: 1.0)

    Column(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(UITheme.SlotBg)
            .border(
                if (fish.firstCatch) 2.5.dp else 1.5.dp,
                if (fish.firstCatch) UITheme.GoldLight else rarityColor.copy(alpha = 0.6f),
                RoundedCornerShape(9.dp),
            )
            .pressable { onClick() }
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(UITheme.DeepWater),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    icon, contentDescription = species?.name,
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            species?.name ?: fish.speciesId,
            color = rarityColor, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            fish.size.label + (if (fish.firstCatch) " · 首捕" else ""),
            color = UITheme.TextDim, fontSize = 8.sp, maxLines = 1,
        )
        Text(
            "🪙${formatNumber(price)}",
            color = UITheme.GoldLight, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** 卖出一条鱼的确认弹窗（避免误点卖掉了首捕的鱼）。 */
@Composable
fun SellConfirmDialog(
    fish: StoredFish,
    price: Double,
    assets: Assets,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val species = Bestiary.speciesById(fish.speciesId)
    val icon = remember(fish.speciesId) {
        species?.let { assets.firstFrame(it.sprite, 320)?.asImageBitmap() }
    }
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
                    .width(300.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(UITheme.PanelBg)
                    .border(2.dp, UITheme.Gold.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("卖鱼", color = UITheme.GoldLight, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(UITheme.DeepWater),
                    contentAlignment = Alignment.Center,
                ) {
                    if (icon != null) {
                        Image(
                            icon, contentDescription = species?.name,
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "${species?.name ?: ""} · ${fish.size.label}",
                    color = UITheme.TextNormal, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "售出可得 🪙${formatNumber(price)}",
                    color = UITheme.GoldLight, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                )
                if (fish.firstCatch) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "这是该鱼种的首次捕获，卖掉后图鉴记录仍在",
                        color = UITheme.TextDim, fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameButton(
                        "再等等", onDismiss,
                        modifier = Modifier.weight(1f).height(42.dp),
                        accent = UITheme.WaterTop, fontSize = 14,
                    )
                    GameButton(
                        "卖掉", onConfirm,
                        modifier = Modifier.weight(1f).height(42.dp),
                        accent = UITheme.Gold, fontSize = 14,
                    )
                }
            }
        }
    }
}
