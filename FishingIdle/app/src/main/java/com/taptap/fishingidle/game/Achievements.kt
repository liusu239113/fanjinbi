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

/** 全部成就。 */
object Achievements {

    val all: List<AchievementDef> = listOf(
        AchievementDef(
            "first_catch", "初次下水", "钓上你的第一条鱼",
            1.0, 50.0, { it.totalCatches.toDouble() },
        ),
        AchievementDef(
            "catch_50", "小有名气", "累计钓上 50 条鱼",
            50.0, 500.0, { it.totalCatches.toDouble() },
        ),
        AchievementDef(
            "catch_500", "钓鱼老手", "累计钓上 500 条鱼",
            500.0, 8_000.0, { it.totalCatches.toDouble() },
        ),
        AchievementDef(
            "catch_5000", "渔场传奇", "累计钓上 5000 条鱼",
            5_000.0, 250_000.0, { it.totalCatches.toDouble() },
        ),
        AchievementDef(
            "earn_10k", "第一桶金", "累计收入达到 1 万",
            10_000.0, 2_000.0, { it.totalMoney },
        ),
        AchievementDef(
            "earn_1m", "腰缠万贯", "累计收入达到 100 万",
            1_000_000.0, 120_000.0, { it.totalMoney },
        ),
        AchievementDef(
            "earn_1b", "富可敌国", "累计收入达到 10 亿",
            1e9, 5e7, { it.totalMoney },
        ),
        AchievementDef(
            "helper_5", "小有班底", "雇佣 5 名自动钓手",
            5.0, 1_000.0, { it.helpers.toDouble() },
        ),
        AchievementDef(
            "helper_20", "船队统领", "雇佣 20 名自动钓手",
            20.0, 60_000.0, { it.helpers.toDouble() },
        ),
        AchievementDef(
            "common_50", "鱼群兴旺", "拥有 50 条小鱼",
            50.0, 20_000.0, { it.commonFish.toDouble() },
        ),
        AchievementDef(
            "rare_10", "鲤鱼成群", "拥有 10 条鲤鱼",
            10.0, 30_000.0, { it.rareFish.toDouble() },
        ),
        AchievementDef(
            "epic_5", "锦鲤满塘", "拥有 5 条锦鲤",
            5.0, 200_000.0, { it.epicFish.toDouble() },
        ),
        AchievementDef(
            "legend_1", "深海来客", "捕获一条巨口鱼",
            1.0, 500_000.0, { it.legendFish.toDouble() },
        ),
        AchievementDef(
            "combo_15", "手速惊人", "达成 15 连击",
            15.0, 15_000.0, { it.bestCombo.toDouble() },
        ),
        AchievementDef(
            "combo_40", "神乎其技", "达成 40 连击",
            40.0, 400_000.0, { it.bestCombo.toDouble() },
        ),
        AchievementDef(
            "single_1k", "大丰收", "单次渔获达到 1000 金币",
            1_000.0, 10_000.0, { it.highestCatch },
        ),
        AchievementDef(
            "single_100k", "惊天一钓", "单次渔获达到 10 万金币",
            100_000.0, 1_000_000.0, { it.highestCatch },
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
