package com.dshx.game.SU.game

import kotlin.random.Random

/**
 * 装备系统。
 *
 * 四个部位各管一件事，**改变手感**而不是再叠一层数值：
 *  - 鱼竿 ROD：收线速度（越高越快把鱼拉上来）
 *  - 鱼线 LINE：抗脱钩（降低"线断了"的概率）
 *  - 浮标 BOBBER：稀有鱼偏好（高稀有度鱼更愿意咬你的钩）
 *  - 鱼饵 BAIT：咬钩速度（缩短等待）
 *
 * 每件装备有稀有度 + 随机词条，靠**开箱**获得（金币箱 / 广告箱）。
 * 这是长线追求的主轴：数值成长有上限，装备词条可以一直刷。
 */
enum class GearSlot(val displayName: String, val desc: String) {
    ROD("鱼竿", "收线速度"),
    LINE("鱼线", "抗脱钩"),
    BOBBER("浮标", "稀有鱼偏好"),
    BAIT("鱼饵", "咬钩速度"),
}

/** 装备稀有度：决定词条数量与数值区间。 */
enum class GearRarity(val displayName: String, val affixCount: Int, val power: Double, val weight: Double) {
    COMMON("普通", 1, 1.0, 60.0),
    RARE("精良", 2, 1.8, 27.0),
    EPIC("史诗", 3, 3.0, 10.0),
    LEGEND("传说", 4, 5.0, 3.0),
    ;

    companion object {
        /** 按权重抽一个稀有度。[luck] 越大越容易出高阶（广告箱可以调高）。 */
        fun roll(rnd: Random = Random, luck: Double = 0.0): GearRarity {
            val entries = entries.toList()
            val weights = entries.map { it.weight * (1.0 + luck * it.ordinal) }
            val total = weights.sum()
            var r = rnd.nextDouble() * total
            for (i in entries.indices) {
                r -= weights[i]
                if (r <= 0) return entries[i]
            }
            return COMMON
        }
    }
}

/** 一条词条：属性 + 数值。 */
class Affix(val stat: GearStat, val value: Double)

/** 装备能提供的属性。 */
enum class GearStat(val displayName: String, val isPercent: Boolean) {
    /** 收线速度加成（百分比）。 */
    REEL_SPEED("收线速度", true),

    /** 脱钩概率降低（百分比）。 */
    ESCAPE_REDUCE("抗脱钩", true),

    /** 稀有鱼偏好（等效距离权重）。 */
    RARE_BIAS("稀有偏好", false),

    /** 咬钩速度加成（百分比）。 */
    BITE_SPEED("咬钩速度", true),

    /** 全局收益加成（百分比）。 */
    VALUE_BONUS("渔获价值", true),

    /** 连击加成（每层额外百分比）。 */
    COMBO_BONUS("连击加成", true),
}

/**
 * 一件装备。
 *
 * [affixes] 由稀有度决定条数，数值随稀有度档位放大。
 */
class GearItem(
    val slot: GearSlot,
    val rarity: GearRarity,
    val name: String,
    val affixes: List<Affix>,
    /** 列表 key，全局唯一（同 [StoredFish.seq] 的道理）。 */
    var seq: Long = 0L,
) {
    /** 某属性的合计值。 */
    fun stat(stat: GearStat): Double = affixes.filter { it.stat == stat }.sumOf { it.value }

    /**
     * 图标资源名（art/ 下不含扩展名）。
     * 按"部位 + 稀有度"取图：4 个部位 × 5 档稀有度，共 20 张。
     */
    val iconName: String
        get() {
            val part = when (slot) {
                GearSlot.ROD -> "rod"
                GearSlot.LINE -> "line"
                GearSlot.BOBBER -> "bobber"
                GearSlot.BAIT -> "bait_gear"
            }
            val tier = when (rarity) {
                GearRarity.COMMON -> "common"
                GearRarity.RARE -> "rare"
                GearRarity.EPIC -> "epic"
                GearRarity.LEGEND -> "legend"
            }
            return "icon_${part}_$tier"
        }

    /** 一句话描述全部词条。 */
    fun affixText(): String = affixes.joinToString(" / ") { a ->
        val v = if (a.stat.isPercent) "${(a.value * 100).toInt()}%" else "%.2f".format(a.value)
        "${a.stat.displayName} +$v"
    }

    companion object {
        /**
         * 每个部位的主属性：开箱时优先保证这一条，让装备"名副其实"。
         * 鱼竿一定带收线、鱼线一定带抗脱钩，否则玩家会觉得装备是乱的。
         */
        private val PRIMARY = mapOf(
            GearSlot.ROD to GearStat.REEL_SPEED,
            GearSlot.LINE to GearStat.ESCAPE_REDUCE,
            GearSlot.BOBBER to GearStat.RARE_BIAS,
            GearSlot.BAIT to GearStat.BITE_SPEED,
        )

        /** 各部位的名称池，让开出来的装备有名字而不是"鱼竿#3"。 */
        private val NAMES = mapOf(
            GearSlot.ROD to listOf("竹节竿", "玻璃钢竿", "碳素竿", "龙纹竿", "星辉竿"),
            GearSlot.LINE to listOf("尼龙线", "编织线", "氟碳线", "蛟筋线", "天蚕丝"),
            GearSlot.BOBBER to listOf("木浮标", "羽毛漂", "夜光漂", "灵犀漂", "龙睛漂"),
            GearSlot.BAIT to listOf("蚯蚓饵", "红虫饵", "香腥饵", "秘制饵", "龙涎饵"),
        )

        /** 数值区间：主属性给得高，副属性低一些。 */
        private fun rollValue(stat: GearStat, rarity: GearRarity, primary: Boolean, rnd: Random): Double {
            val base = when (stat) {
                GearStat.REEL_SPEED -> 0.06
                GearStat.ESCAPE_REDUCE -> 0.05
                GearStat.RARE_BIAS -> 0.15
                GearStat.BITE_SPEED -> 0.06
                GearStat.VALUE_BONUS -> 0.05
                GearStat.COMBO_BONUS -> 0.004
            }
            val mul = rarity.power * (if (primary) 1.6 else 1.0)
            return base * mul * (0.85 + rnd.nextDouble() * 0.3)
        }

        /** 开一件装备。[luck] 影响稀有度（广告箱更高）。 */
        fun roll(rnd: Random = Random, luck: Double = 0.0): GearItem {
            val slot = GearSlot.entries[rnd.nextInt(GearSlot.entries.size)]
            val rarity = GearRarity.roll(rnd, luck)
            val primary = PRIMARY.getValue(slot)

            // 主属性 + 若干随机副属性（不重复）
            val stats = mutableListOf(primary)
            val pool = GearStat.entries.filter { it != primary }.shuffled(rnd)
            stats += pool.take((rarity.affixCount - 1).coerceAtLeast(0))

            val affixes = stats.map { rollValue(it, rarity, it == primary, rnd) }
                .mapIndexed { i, v -> Affix(stats[i], v) }

            val names = NAMES.getValue(slot)
            val name = "${rarity.displayName}${names[rarity.ordinal.coerceAtMost(names.size - 1)]}"

            return GearItem(slot, rarity, name, affixes)
        }
    }
}

/**
 * 玩家的装备栏：每个部位一件。
 *
 * 全部加成在这里汇总，[GameState] 与 [World] 只读这里的结果 ——
 * 这样"装备生效"只有一处真相，不会出现"某个属性忘了接线"。
 */
class GearLoadout {
    /** 已装备的装备：部位 → 装备。 */
    val equipped: MutableMap<GearSlot, GearItem> = mutableMapOf()

    /** 仓库里未装备的装备。 */
    val bag: MutableList<GearItem> = mutableListOf()

    /** 列表 key 分配器（跨读档单调递增，避免撞 key 闪退）。 */
    private var seqCounter = 0L

    fun nextSeq(): Long = ++seqCounter

    /** 重排所有装备的 key（读档后调用）。 */
    fun reindex() {
        for (g in bag) g.seq = nextSeq()
        for (g in equipped.values) g.seq = nextSeq()
    }

    /** 某属性的总加成（已装备的才生效）。 */
    fun total(stat: GearStat): Double = equipped.values.sumOf { it.stat(stat) }

    /** 装备一件（从背包移入）。返回被替换下来的旧装备（可能为 null）。 */
    fun equip(item: GearItem): GearItem? {
        bag.remove(item)
        val old = equipped[item.slot]
        equipped[item.slot] = item
        if (old != null) bag.add(old)
        return old
    }

    /** 卸下某部位。 */
    fun unequip(slot: GearSlot) {
        equipped.remove(slot)?.let { bag.add(it) }
    }

    /** 收进背包。 */
    fun add(item: GearItem) {
        if (item.seq <= 0L) item.seq = nextSeq()
        bag.add(item)
    }

    /** 分解一件装备，返还金币。 */
    fun salvage(item: GearItem): Double {
        val gain = salvageValue(item)
        bag.remove(item)
        return gain
    }

    companion object {
        /** 分解价值：按稀有度给。 */
        fun salvageValue(item: GearItem): Double = when (item.rarity) {
            GearRarity.COMMON -> 500.0
            GearRarity.RARE -> 5_000.0
            GearRarity.EPIC -> 50_000.0
            GearRarity.LEGEND -> 500_000.0
        }
    }
}
