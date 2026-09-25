package com.dshx.game.SU.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.dshx.game.SU.game.Assets
import com.dshx.game.SU.game.GameState
import com.dshx.game.SU.game.Prestige
import com.dshx.game.SU.game.RewardAds
import com.dshx.game.SU.game.SkillDef
import com.dshx.game.SU.game.SkillTree
import com.dshx.game.SU.game.formatNumber

/**
 * 转生与技能树面板。
 *
 * 转生清空本轮金币/鱼群/升级，换取珍珠；珍珠投入技能树永久生效。
 * 这是突破数值墙、让放置循环可持续的核心机制。
 */
@Composable
fun PrestigePanel(
    state: GameState,
    assets: Assets,
    /** HUD 那套刷新计数：面板开着时珍珠 / 技能等级要实时变，不能等重开。 */
    revision: Int,
    onPrestige: () -> Unit,
    onLevelUp: (SkillDef) -> Unit,
    /** 「转生加持」：看完广告让下一次转生的珍珠 +50%。 */
    onPrestigeBoost: () -> Unit,
    onClose: () -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION") revision
    var tab by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(UITheme.DeepWater.copy(alpha = 0.55f))
                .clickableNoRipple { onClose() }
        )

        Column(
            Modifier
                .fillMaxHeight(0.82f)
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        ) {
            WoodPanel(Modifier.fillMaxSize(), assets = assets, cornerPx = 110) {
                Column(Modifier.fillMaxSize().padding(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TabChip("转生", tab == 0, Modifier.weight(1f)) { tab = 0 }
                        TabChip("技能树", tab == 1, Modifier.weight(1f)) { tab = 1 }
                        Spacer(Modifier.width(3.dp))
                        GameButton("✕", onClose, accent = UITheme.WaterTop, fontSize = 14)
                    }

                    Spacer(Modifier.height(10.dp))

                    if (tab == 0) {
                        PrestigeTab(state, assets, onPrestige, onPrestigeBoost)
                    } else {
                        SkillTab(state, onLevelUp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabChip(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) UITheme.Gold else UITheme.WoodDark)
            .border(2.dp, UITheme.Ink, RoundedCornerShape(8.dp))
            .clickableNoRipple { onClick() }
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) UITheme.Ink else UITheme.Cream,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PrestigeTab(
    state: GameState,
    assets: Assets,
    onPrestige: () -> Unit,
    onPrestigeBoost: () -> Unit,
) {
    val pending = state.pendingPearls()
    val canPrestige = pending > 0
    val progress = Prestige.progress(state.totalMoney)

    Column(Modifier.fillMaxSize()) {
        WoodPanel(Modifier.fillMaxWidth(), assets = null, cornerPx = 80) {
            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("珍珠", color = UITheme.TextDim, fontSize = 12.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    "🦪 ${state.pearls}",
                    color = UITheme.GoldLight,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "已转生 ${state.prestigeCount} 次",
                    color = UITheme.TextDim,
                    fontSize = 12.sp,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionTitle("本次转生可得")
        Spacer(Modifier.height(6.dp))
        Text(
            if (canPrestige) "🦪 $pending 颗珍珠" else "还不能转生",
            color = if (canPrestige) UITheme.TextGood else UITheme.TextDim,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        // 把"转生到底换来了什么"写在按钮上面 —— 只说珍珠颗数太抽象，
        // 玩家看不到永久加成，就会觉得转生是白清空。
        val nowMul = state.prestigeMultiplier
        val nextMul = nowMul + GameState.PRESTIGE_VALUE_STEP
        Text(
            "永久收益 ×${"%.2f".format(nowMul)} → ×${"%.2f".format(nextMul)}",
            color = UITheme.TextGood,
            fontSize = 12.sp,
        )

        Spacer(Modifier.height(10.dp))

        // 进度条
        SectionTitle("进度")
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(UITheme.SlotBg)
                .border(2.dp, UITheme.GoldDark.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(UITheme.Gold)
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "累计收入 ${formatNumber(state.totalMoney)}",
                color = UITheme.TextDim, fontSize = 11.sp,
            )
            Text(
                "目标 ${formatNumber(Prestige.MIN_TOTAL_FOR_PRESTIGE)}",
                color = UITheme.TextDim, fontSize = 11.sp,
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            "转生会清空金币、鱼群、钓手、全部普通升级与水域进度，\n" +
                "换来：每转生一次收益永久 +10%，外加珍珠（可永久升级技能树，\n" +
                "珍珠与图鉴、成就都保留）。",
            color = UITheme.TextDim,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )

        // 「转生加持」：转生是本作最重的抉择（清空一切），
        // 在这里卖"这一转多拿 50% 珍珠"是典型的刚需广告 ——
        // 玩家自己会算这笔账，不需要劝。
        Spacer(Modifier.height(10.dp))
        AdActionRow(
            assets = assets,
            iconName = "ad_boost",
            title = "转生加持 · 本次珍珠 +50%",
            desc = when {
                state.prestigeBoostReady -> "加持已就绪，本次转生立即生效"
                RewardAds.isReady() -> "只对下一次转生生效，用完即止"
                else -> "广告接入中"
            },
            // 已就绪时不用再点（避免重复看广告），但保持可读
            enabled = RewardAds.isReady() && !state.prestigeBoostReady,
            onClick = onPrestigeBoost,
        )

        Spacer(Modifier.height(14.dp))

        GameButton(
            text = if (canPrestige) "转生（获得 $pending 珍珠）" else "收入不足，无法转生",
            onClick = onPrestige,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = canPrestige,
            accent = if (canPrestige) UITheme.Gold else Color(0xFF4A4A4A),
            fontSize = 16,
        )
    }
}

@Composable
private fun SkillTab(state: GameState, onLevelUp: (SkillDef) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🦪 ${state.pearls} 颗珍珠可用",
                    color = UITheme.GoldLight, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("已投入 ${SkillTree.totalInvested(state)}",
                    color = UITheme.TextDim, fontSize = 11.sp)
            }
        }

        items(SkillTree.all, key = { it.id }) { def ->
            SkillRow(state, def, onLevelUp)
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SkillRow(state: GameState, def: SkillDef, onLevelUp: (SkillDef) -> Unit) {
    val level = state.skillLevel(def.id)
    val unlocked = SkillTree.unlocked(state, def)
    val maxed = level >= def.maxLevel
    val cost = SkillTree.nextCost(def, level)
    val affordable = state.pearls >= cost
    val canBuy = unlocked && !maxed && affordable

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    maxed -> UITheme.SlotBgOwned
                    !unlocked -> Color(0xFF2A2F33)
                    canBuy -> Color(0xFF33505C)
                    else -> UITheme.SlotBg
                }
            )
            .border(
                2.dp,
                when {
                    maxed -> UITheme.TextGood.copy(alpha = 0.6f)
                    !unlocked -> Color(0xFF44494D)
                    canBuy -> UITheme.Gold
                    else -> Color(0xFF4A565A)
                },
                RoundedCornerShape(10.dp),
            )
            .clickableNoRipple(canBuy) { onLevelUp(def) }
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    def.name,
                    color = when {
                        maxed -> UITheme.TextGood
                        unlocked -> UITheme.Cream
                        else -> UITheme.TextDim
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(5.dp))
                Text("$level/${def.maxLevel}", color = UITheme.TextDim, fontSize = 11.sp)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                if (unlocked) {
                    def.desc.replace("{v}", effectValue(def, level + 1))
                } else {
                    "需要先解锁「${SkillTree.byId(def.requires ?: "")?.name ?: "前置技能"}」"
                },
                color = UITheme.TextDim,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            )
        }

        Spacer(Modifier.width(8.dp))

        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when {
                        maxed -> UITheme.TextGood.copy(alpha = 0.18f)
                        canBuy -> UITheme.Gold
                        else -> Color(0xFF3A4348)
                    }
                )
                .padding(horizontal = 9.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (maxed) {
                Text("满级", color = UITheme.TextGood, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "🦪$cost",
                        color = if (canBuy) UITheme.Ink else UITheme.TextBad,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (unlocked && !affordable) {
                        Text("珍珠不足", color = UITheme.TextBad, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

/** 把技能描述里的 {v} 换成升到下一级后的实际数值。 */
private fun effectValue(def: SkillDef, nextLevel: Int): String = when (def.id) {
    "bait_mastery" -> "${nextLevel * 25}"
    "quick_hands" -> "${nextLevel * 20}"
    "lucky_hook" -> "${(nextLevel * 5).coerceAtMost(60)}"
    "head_start" -> formatNumber(nextLevel * 5_000.0)
    "crew_chief" -> "${nextLevel * 30}"
    "school_density" -> "${nextLevel * 10}"
    "combo_flow" -> "${(4 + nextLevel * 2)}"
    "pearl_diver" -> "${nextLevel * 15}"
    "deep_instinct" -> "${nextLevel * 12}"
    "old_sailor" -> "${nextLevel * 50}"
    else -> "$nextLevel"
}
