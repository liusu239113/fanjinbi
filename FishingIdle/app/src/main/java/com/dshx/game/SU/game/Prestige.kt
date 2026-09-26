package com.dshx.game.SU.game

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * 转生（对应原版的威望 / Sacrifice）。
 *
 * 转生会清空金币、鱼群、钓手与全部普通升级，但换取**珍珠**；
 * 珍珠用于升级永久生效的技能树，让下一轮成长快得多。
 * 这是放置游戏突破数值墙的标准手段，也是原版最核心的长线机制。
 */
object Prestige {

    /** 转生门槛：累计收入达到这个数才能转生。 */
    const val MIN_TOTAL_FOR_PRESTIGE = 1_000_000.0

    /** 平方根区间的上界（相对门槛的倍数）：再往上改走对数增长。 */
    private const val LINEAR_RATIO_CAP = 100.0

    /**
     * 本次转生能获得多少珍珠。
     *
     * 前期保持原来的平方根增长，保证"第一次转生够点技能"的手感不变：
     * 100 万 → 3 颗，400 万 → 6 颗，900 万 → 9 颗，1 亿 → 30 颗。
     *
     * 超过门槛 100 倍之后改成**对数增长**。原来的公式一路开方，
     * 后期会失控 —— 玩家累计收入到 1.97e28 时一次转生能拿 4.2e11 颗珍珠，
     * 技能树瞬间被点满，长线成长直接消失（技能树全点满只要几千颗）。
     * 现在同样收入只给约 2900 颗，仍有明显进步，但不会一次毕业。
     */
    fun pearlsFor(totalMoney: Double): Long {
        if (totalMoney < MIN_TOTAL_FOR_PRESTIGE) return 0
        val ratio = totalMoney / MIN_TOTAL_FOR_PRESTIGE
        val raw = if (ratio <= LINEAR_RATIO_CAP) {
            3.0 * ratio.pow(0.5)
        } else {
            30.0 * (1.0 + log10(ratio / LINEAR_RATIO_CAP)).pow(1.5)
        }
        return floor(raw).toLong().coerceAtLeast(1)
    }

    /** 距离下一次转生的进度 0..1。 */
    fun progress(totalMoney: Double): Float =
        (totalMoney / MIN_TOTAL_FOR_PRESTIGE).coerceIn(0.0, 1.0).toFloat()
}

/** 技能树节点。每个节点可投入若干级，永久生效（转生不清空）。 */
class SkillDef(
    val id: String,
    val name: String,
    val desc: String,
    val maxLevel: Int,
    /** 每级消耗的珍珠。 */
    val costPerLevel: Long,
    /** 前置技能 id。 */
    val requires: String? = null,
    val effect: (GameState, Int) -> Unit,
)

/** 全部技能。 */
object SkillTree {

    val all: List<SkillDef> = listOf(
        SkillDef(
            "bait_mastery", "鱼饵精通",
            "所有鱼的基础价值 +{v}%",
            maxLevel = 20, costPerLevel = 1,
            effect = { s, lv -> s.skillValueBonus = lv * 0.25 },
        ),
        SkillDef(
            "quick_hands", "快手",
            "收线速度 +{v}%",
            maxLevel = 15, costPerLevel = 1,
            effect = { s, lv -> s.skillReelBonus = lv * 0.20 },
        ),
        SkillDef(
            "lucky_hook", "幸运鱼钩",
            "脱钩概率 -{v}%（最多 -60%）",
            maxLevel = 12, costPerLevel = 2,
            effect = { s, lv -> s.skillEscapeReduce = (lv * 0.05).coerceAtMost(0.60) },
        ),
        SkillDef(
            "head_start", "开局红利",
            "转生后初始金币 +{v}",
            maxLevel = 15, costPerLevel = 1,
            requires = "bait_mastery",
            effect = { s, lv -> s.skillStartMoney = lv * 5_000.0 },
        ),
        SkillDef(
            "crew_chief", "船队统领",
            "自动钓手效率 +{v}%",
            maxLevel = 15, costPerLevel = 2,
            requires = "bait_mastery",
            effect = { s, lv -> s.skillHelperBonus = lv * 0.30 },
        ),
        SkillDef(
            "school_density", "鱼群聚集",
            "每种鱼的最大数量 +{v}",
            maxLevel = 10, costPerLevel = 3,
            requires = "head_start",
            effect = { s, lv -> s.skillFishCapacity = lv * 10 },
        ),
        SkillDef(
            "combo_flow", "连击之势",
            "每层连击加成从 4% 提升到 {v}%",
            maxLevel = 10, costPerLevel = 3,
            requires = "quick_hands",
            effect = { s, lv -> s.skillComboStep = 0.04 + lv * 0.02 },
        ),
        SkillDef(
            "pearl_diver", "采珠人",
            "转生获得的珍珠 +{v}%",
            maxLevel = 10, costPerLevel = 4,
            requires = "crew_chief",
            effect = { s, lv -> s.skillPearlBonus = lv * 0.15 },
        ),
        SkillDef(
            "deep_instinct", "深渊直觉",
            "抛竿时稀有鱼更抢食（等效距离 -{v}%）",
            maxLevel = 8, costPerLevel = 5,
            requires = "school_density",
            effect = { s, lv -> s.skillRareWeightBonus = lv * 0.12 },
        ),
        // ---- 后期内容分支：把第二梯队的功能也接进长线成长 ----
        SkillDef(
            "deep_diver", "深海打捞",
            "潜水员下潜周期 -{v}%",
            maxLevel = 8, costPerLevel = 3,
            requires = "pearl_diver",
            effect = { s, lv -> s.skillDiverSpeed = (lv * 0.09).coerceAtMost(0.72) },
        ),
        SkillDef(
            "treasure_hunter", "寻宝达人",
            "宝箱出现更快，奖励 +{v}%",
            maxLevel = 8, costPerLevel = 4,
            requires = "deep_diver",
            effect = { s, lv ->
                s.skillChestSpeed = (lv * 0.07).coerceAtMost(0.56)
                s.skillChestValue = lv * 0.35
            },
        ),
        SkillDef(
            "drone_ai", "无人机编队",
            "自动抛竿间隔再 -{v}%",
            maxLevel = 10, costPerLevel = 4,
            requires = "treasure_hunter",
            effect = { s, lv -> s.skillDroneSpeed = (lv * 0.04).coerceAtMost(0.40) },
        ),
        SkillDef(
            "king_slayer", "鱼王克星",
            "鱼王拉力 +{v}%（奖励同步提高）",
            maxLevel = 8, costPerLevel = 5,
            requires = "drone_ai",
            effect = { s, lv -> s.skillKingPower = lv * 0.25 },
        ),
        SkillDef(
            "old_sailor", "老船长",
            "所有收益 +{v}%（对技能树全属性的总加成）",
            maxLevel = 5, costPerLevel = 12,
            requires = "pearl_diver",
            effect = { s, lv -> s.skillGlobalBonus = lv * 0.50 },
        ),
    )

    fun byId(id: String): SkillDef? = all.firstOrNull { it.id == id }

    /** 节点是否已解锁（前置满足）。 */
    fun unlocked(state: GameState, def: SkillDef): Boolean {
        val req = def.requires ?: return true
        return state.skillLevel(req) > 0
    }

    /** 该节点升下一级要花多少珍珠。 */
    fun nextCost(def: SkillDef, currentLevel: Int): Long =
        def.costPerLevel * (currentLevel + 1)

    /** 投入一级。返回是否成功。 */
    fun levelUp(state: GameState, def: SkillDef): Boolean {
        if (!unlocked(state, def)) return false
        val lv = state.skillLevel(def.id)
        if (lv >= def.maxLevel) return false
        val cost = nextCost(def, lv)
        if (state.pearls < cost) return false
        state.pearls -= cost
        state.skillLevels[def.id] = lv + 1
        applyAll(state)
        return true
    }

    /** 把所有技能效果重新套用到 GameState 上（幂等）。 */
    fun applyAll(state: GameState) {
        // 先清空，再按当前等级重算，避免叠加错误
        state.skillValueBonus = 0.0
        state.skillReelBonus = 0.0
        state.skillEscapeReduce = 0.0
        state.skillStartMoney = 0.0
        state.skillHelperBonus = 0.0
        state.skillFishCapacity = 0
        state.skillComboStep = 0.04
        state.skillPearlBonus = 0.0
        state.skillRareWeightBonus = 0.0
        state.skillGlobalBonus = 0.0
        for (def in all) {
            val lv = state.skillLevels[def.id] ?: 0
            if (lv > 0) def.effect(state, lv)
        }
    }

    /** 已投入的珍珠总数（用于展示）。 */
    fun totalInvested(state: GameState): Long =
        all.sumOf { def ->
            val lv = state.skillLevels[def.id] ?: 0
            (1..lv).sumOf { def.costPerLevel * it.toLong() }
        }
}
