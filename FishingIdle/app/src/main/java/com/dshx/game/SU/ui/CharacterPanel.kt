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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.dshx.game.SU.game.CharacterDef
import com.dshx.game.SU.game.Characters
import com.dshx.game.SU.game.GameState
import com.dshx.game.SU.game.RewardAds
import com.dshx.game.SU.game.RodSkill
import com.dshx.game.SU.game.formatNumber

/**
 * 换装面板：用金币解锁/切换角色。
 *
 * 每个角色带一条**专属鱼竿技能**，不是纯皮肤 —— 换角色等于换一套手感。
 * 主角与帮手分成两组展示。
 */
@Composable
fun CharacterPanel(
    state: GameState,
    assets: Assets,
    revision: Int,
    onUnlock: (CharacterDef) -> Boolean,
    onEquip: (CharacterDef) -> Unit,
    /** 「钓协借调」：看完广告给这个角色一段限时试用权。 */
    onTrial: (CharacterDef) -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION") revision
    var detail by remember { mutableStateOf<CharacterDef?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 试用状态条：正在试用时显示剩余时间，让玩家知道"还有多久"
        if (state.trialActive) {
            item {
                val trialDef = Characters.byId(state.trialCharacterId.orEmpty())
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(UITheme.TextGood.copy(alpha = 0.18f))
                        .border(2.dp, UITheme.TextGood.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🎣", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "钓协借调中 · ${trialDef?.name ?: ""}",
                            color = UITheme.TextGood, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "剩余 ${formatBuffTime(state.trialRemain)}，到期自动换回原角色",
                            color = UITheme.TextDim, fontSize = 10.sp,
                        )
                    }
                }
            }
        }

        item {
            Column {
                SectionTitle("主角")
                Spacer(Modifier.height(4.dp))
                Text(
                    "不同角色有各自的鱼竿技能，换人就是换玩法",
                    color = UITheme.TextDim, fontSize = 11.sp,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        items(Characters.players, key = { it.id }) { def ->
            CharacterRow(
                def = def,
                state = state,
                assets = assets,
                equipped = state.currentCharacterId == def.id,
                onClick = { detail = def },
            )
        }

        item {
            Column(Modifier.padding(top = 10.dp)) {
                SectionTitle("帮手形象")
                Spacer(Modifier.height(4.dp))
                Text(
                    "只影响自动钓手的外观与特性",
                    color = UITheme.TextDim, fontSize = 11.sp,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        items(Characters.helpers, key = { it.id }) { def ->
            CharacterRow(
                def = def,
                state = state,
                assets = assets,
                equipped = state.currentHelperId == def.id,
                onClick = { detail = def },
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    detail?.let { def ->
        CharacterDetail(
            def = def,
            state = state,
            assets = assets,
            onUnlock = { if (onUnlock(def)) detail = null },
            onEquip = { onEquip(def); detail = null },
            onTrial = { onTrial(def); detail = null },
            onClose = { detail = null },
        )
    }
}

@Composable
private fun CharacterRow(
    def: CharacterDef,
    state: GameState,
    assets: Assets,
    equipped: Boolean,
    onClick: () -> Unit,
) {
    val owned = state.ownsCharacter(def.id)
    // 立绘用甩竿动画的第一帧
    val icon = remember(def.sprite) { assets.firstFrame(def.sprite, 200)?.asImageBitmap() }
    val border = when {
        equipped -> UITheme.TextGood
        owned -> UITheme.GoldDark
        else -> UITheme.TextDim.copy(alpha = 0.35f)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (equipped) UITheme.SlotBgOwned else UITheme.SlotBg)
            .border(2.dp, border, RoundedCornerShape(10.dp))
            .pressable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(UITheme.DeepWater),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    icon, contentDescription = def.name,
                    modifier = Modifier.fillMaxSize().padding(3.dp)
                        .alpha(if (owned) 1f else 0.35f),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    def.name,
                    color = if (owned) UITheme.GoldLight else UITheme.TextDim,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                Pill(def.rodSkill.displayName, UITheme.RarityEpic)
                if (equipped) {
                    Spacer(Modifier.width(5.dp))
                    Pill("使用中", UITheme.TextGood)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(def.title, color = UITheme.TextDim, fontSize = 10.sp)
            Text(
                def.rodSkill.desc,
                color = UITheme.TextNormal, fontSize = 10.sp, lineHeight = 13.sp,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            when {
                equipped -> "使用中"
                owned -> "切换"
                else -> "🪙${formatNumber(def.price)}"
            },
            color = if (owned) UITheme.GoldLight else UITheme.Gold,
            fontSize = 11.sp, fontWeight = FontWeight.Bold,
        )
    }
}

/** 角色详情：大图 + 技能说明 + 解锁/切换按钮。 */
@Composable
private fun CharacterDetail(
    def: CharacterDef,
    state: GameState,
    assets: Assets,
    onUnlock: () -> Unit,
    onEquip: () -> Unit,
    onTrial: () -> Unit,
    onClose: () -> Unit,
) {
    val owned = state.ownsCharacter(def.id)
    val trialing = state.isTrial(def)
    // "可用"= 已拥有或在试用中，决定能不能点「切换使用」
    val usable = state.canUseCharacter(def.id)
    val equipped = if (def.isHelper) {
        state.currentHelperId == def.id
    } else {
        state.currentCharacterId == def.id
    }
    val icon = remember(def.sprite) { assets.firstFrame(def.sprite, 400)?.asImageBitmap() }
    val canAfford = state.money >= def.price

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xCC000000))
                .clickableNoRipple { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .width(320.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(UITheme.PanelBg)
                    .border(2.dp, UITheme.Gold.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    def.name,
                    color = UITheme.GoldLight, fontSize = 21.sp, fontWeight = FontWeight.Bold,
                )
                Text(def.title, color = UITheme.TextDim, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(UITheme.DeepWater),
                    contentAlignment = Alignment.Center,
                ) {
                    if (icon != null) {
                        Image(
                            icon, contentDescription = def.name,
                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                                .alpha(if (owned) 1f else 0.4f),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))

                // 鱼竿技能
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(UITheme.DeepWater.copy(alpha = 0.6f))
                        .padding(10.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("鱼竿技能", color = UITheme.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Pill(def.rodSkill.displayName, UITheme.RarityEpic)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            def.rodSkill.desc,
                            color = UITheme.TextNormal, fontSize = 11.sp, lineHeight = 15.sp,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                Text(
                    def.desc,
                    color = UITheme.TextDim, fontSize = 11.sp, lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))

                when {
                    equipped -> GameButton(
                        "使用中", onClose,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        accent = UITheme.TextGood, fontSize = 15,
                    )
                    usable -> GameButton(
                        "切换使用", onEquip,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        accent = UITheme.Gold, fontSize = 15,
                    )
                    else -> GameButton(
                        "解锁 · 🪙${formatNumber(def.price)}",
                        onUnlock,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        enabled = canAfford,
                        accent = if (canAfford) UITheme.Gold else UITheme.TextDim,
                        fontSize = 14,
                    )
                }

                // 「钓协借调」：买不起时的替代路径 —— 先试后买。
                // 已经拥有 / 正在试用 / 已装备时不显示，避免重复。
                if (!owned && !trialing && !equipped) {
                    Spacer(Modifier.height(8.dp))
                    AdActionRow(
                        assets = assets,
                        iconName = "ad_borrow",
                        title = "钓协借调 · 限时体验",
                        desc = if (RewardAds.isReady()) "先白用 3 分钟，技能真实生效"
                        else "广告接入中",
                        enabled = RewardAds.isReady(),
                        onClick = onTrial,
                    )
                }

                Spacer(Modifier.height(8.dp))
                GameButton(
                    "关闭", onClose,
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    accent = UITheme.WaterTop, fontSize = 13,
                )
            }
        }
    }
}
