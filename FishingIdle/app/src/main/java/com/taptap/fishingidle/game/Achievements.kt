package com.taptap.fishingidle.game

/** 成就定义。progress 取自 GameState，达到 goal 即解锁。 */
class AchievementDef(
    val id: String,
    val name: String,
    val desc: String,
    val goal: Double,
    val reward: Double,
    val progress: (GameState) -> Double,
    val rewardKind: RewardKind = RewardKind.MONEY,
)

enum class RewardKind { MONEY, MULTIPLIER }

/**
 * 全部成就。
 *
 * ## 奖励量级怎么定
 *
 * 成就是**奖金**，不是暴发户。奖励按"达成它时玩家手头大概有多少钱"来定，
 * 大致等于那个阶段一两件升级的钱，绝不出现一次成就直接买下一整队钓手的情况。
 * 之前的数值完全没按这个来 —— 15 连击给 1.5 万、40 连击给 40 万，
 * 而开局钓手才 500 一个，等于成就一弹出来就能连买六名钓手、几分钟内全升级点满。
 *
 * 参考锚点：钓手价格 500 / 875 / 1531 / 2679 / 4688 / 8204…，
 * 中期升级（收益加成、线轮）在 10 万上下，后期升级到百万级。
 */
object Achievements {

    val all: List<AchievementDef> = listOf(
        AchievementDef(
            "first_catch", "初次下水", "钓上你的第一条鱼",
            1.0, 30.0, { it.totalCatches.toDouble() },
        ),
        AchievementDef(
            "catch_50", "小有名气", "累计钓上 50 条鱼",
            50.0, 300.0, { it.totalCatches.toDouble() },
        ),
        AchievementDef(
            "catch_500", "钓鱼老手", "累计钓上 500 条鱼",
            500.0, 3_000.0, { it.totalCatches.toDouble() },
        ),
        AchievementDef(
            "catch_5000", "渔场传奇", "累计钓上 5000 条鱼",
            5_000.0, 40_000.0, { it.totalCatches.toDouble() },
        ),
        AchievementDef(
            "earn_10k", "第一桶金", "累计收入达到 1 万",
            10_000.0, 1_000.0, { it.totalMoney },
        ),
        AchievementDef(
            "earn_1m", "腰缠万贯", "累计收入达到 100 万",
            1_000_000.0, 20_000.0, { it.totalMoney },
        ),
        AchievementDef(
            "earn_1b", "富可敌国", "累计收入达到 10 亿",
            1e9, 3e6, { it.totalMoney },
        ),
        AchievementDef(
            "helper_5", "小有班底", "雇佣 5 名自动钓手",
            5.0, 800.0, { it.helpers.toDouble() },
        ),
        AchievementDef(
            "helper_20", "船队统领", "雇佣 20 名自动钓手",
            20.0, 25_000.0, { it.helpers.toDouble() },
        ),
        AchievementDef(
            "common_50", "鱼群兴旺", "拥有 50 条小鱼",
            50.0, 4_000.0, { it.commonFish.toDouble() },
        ),
        AchievementDef(
            "rare_10", "鲤鱼成群", "拥有 10 条鲤鱼",
            10.0, 6_000.0, { it.rareFish.toDouble() },
        ),
        AchievementDef(
            "epic_5", "锦鲤满塘", "拥有 5 条锦鲤",
            5.0, 25_000.0, { it.epicFish.toDouble() },
        ),
        AchievementDef(
            "legend_1", "深海来客", "捕获一条巨口鱼",
            1.0, 50_000.0, { it.legendFish.toDouble() },
        ),
        AchievementDef(
            "combo_15", "手速惊人", "达成 15 连击",
            15.0, 800.0, { it.bestCombo.toDouble() },
        ),
        AchievementDef(
            "combo_40", "神乎其技", "达成 40 连击",
            40.0, 12_000.0, { it.bestCombo.toDouble() },
        ),
        AchievementDef(
            "single_1k", "大丰收", "单次渔获达到 1000 金币",
            1_000.0, 2_000.0, { it.highestCatch },
        ),
        AchievementDef(
            "single_100k", "惊天一钓", "单次渔获达到 10 万金币",
            100_000.0, 120_000.0, { it.highestCatch },
        ),
    )

    fun byId(id: String): AchievementDef? = all.firstOrNull { it.id == id }

    /** 返回本次新解锁的成就。 */
    fun checkUnlocks(state: GameState): List<AchievementDef> {
        val newly = mutableListOf<AchievementDef>()
        for (def in all) {
            if (state.unlockedAchievements.contains(def.id)) continue
            if (def.progress(state) >= def.goal) {
                state.unlockedAchievements.add(def.id)
                state.money += def.reward
                newly.add(def)
            }
        }
        return newly
    }

    fun progressOf(def: AchievementDef, state: GameState): Double =
        (def.progress(state) / def.goal).coerceIn(0.0, 1.0)
}
