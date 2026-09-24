package com.taptap.fishingidle.game

import kotlin.random.Random

/**
 * 稀有度。决定基础价值与出现权重。
 * 基础值沿用原版金币的经济曲线（1 / 20 / 300 / 5000）。
 */
enum class Rarity(
    val displayName: String,
    val baseValue: Double,
    val weight: Float,
) {
    COMMON("常见", 1.0, 100f),
    RARE("稀有", 15.0, 34f),
    EPIC("史诗", 120.0, 9f),
    LEGEND("传说", 1000.0, 2f),
}

/**
 * 单个鱼种。
 *
 * 价值 = [Rarity.baseValue] × [valueMul] × 地图倍率 × 玩家升级倍率。
 *
 * [tint] 是色调偏移，用来在同一张精灵上做出不同外观：
 * 一张基础精灵配上不同色调就是一个新鱼种，这样几十种鱼不必几十张图。
 */
class Species(
    val id: String,
    val name: String,
    val rarity: Rarity,
    val sprite: String,
    val valueMul: Double = 1.0,
    val scale: Float = 1.0f,
    val escapeMul: Float = 1.0f,
    val tint: FishTint = FishTint.NONE,
) {
    /** 图鉴里的编号。 */
    val dexNo: Int get() = Bestiary.allSpecies.indexOf(this) + 1
}

/** 精灵色调变体。用 ColorMatrix 在渲染时套用，零额外内存。 */
enum class FishTint(val display: String) {
    NONE("原色"),
    GOLDEN("金"),
    SILVER("银"),
    DARK("墨"),
    JADE("翠"),
    CRIMSON("赤"),
    AZURE("青"),
    PURPLE("紫"),
    PALE("白"),
    ;

    /** 返回 4x5 颜色矩阵（Android ColorMatrix 顺序）。 */
    fun matrix(): FloatArray = when (this) {
        NONE -> floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        GOLDEN -> floatArrayOf(
            1.15f, 0.12f, 0f, 0f, 12f,
            0.08f, 1.00f, 0f, 0f, 6f,
            0f, 0.05f, 0.55f, 0f, -10f,
            0f, 0f, 0f, 1f, 0f,
        )
        SILVER -> floatArrayOf(
            0.95f, 0f, 0f, 0f, 22f,
            0f, 0.98f, 0f, 0f, 24f,
            0f, 0f, 1.05f, 0f, 28f,
            0f, 0f, 0f, 1f, 0f,
        )
        DARK -> floatArrayOf(
            0.62f, 0f, 0f, 0f, 0f,
            0f, 0.62f, 0f, 0f, 0f,
            0f, 0f, 0.70f, 0f, 4f,
            0f, 0f, 0f, 1f, 0f,
        )
        JADE -> floatArrayOf(
            0.70f, 0f, 0f, 0f, -6f,
            0f, 1.15f, 0f, 0f, 14f,
            0f, 0f, 0.85f, 0f, 4f,
            0f, 0f, 0f, 1f, 0f,
        )
        CRIMSON -> floatArrayOf(
            1.25f, 0f, 0f, 0f, 14f,
            0f, 0.60f, 0f, 0f, -6f,
            0f, 0f, 0.55f, 0f, -6f,
            0f, 0f, 0f, 1f, 0f,
        )
        AZURE -> floatArrayOf(
            0.62f, 0f, 0f, 0f, -8f,
            0.05f, 0.95f, 0f, 0f, 6f,
            0f, 0.10f, 1.30f, 0f, 20f,
            0f, 0f, 0f, 1f, 0f,
        )
        PURPLE -> floatArrayOf(
            0.95f, 0f, 0.18f, 0f, 10f,
            0f, 0.62f, 0.10f, 0f, 0f,
            0.12f, 0f, 1.25f, 0f, 22f,
            0f, 0f, 0f, 1f, 0f,
        )
        PALE -> floatArrayOf(
            0.80f, 0f, 0f, 0f, 48f,
            0f, 0.82f, 0f, 0f, 46f,
            0f, 0f, 0.85f, 0f, 46f,
            0f, 0f, 0f, 1f, 0f,
        )
    }
}

/**
 * 钓场地图。地图倍率是长线成长主轴 —— 越后面的水域所有鱼价值越高，
 * 同时解锁新的稀有鱼种。
 */
class FishingMap(
    val id: String,
    val name: String,
    val desc: String,
    val valueMultiplier: Double,
    val unlockCost: Double,
    val species: List<Species>,
) {
    /**
     * 抽一条鱼。[luckBonus] 来自「幸运鱼钩」升级：
     * 它会放大高稀有度的权重，让稀有种更容易出现。
     */
    fun roll(rng: Random, luckBonus: Double = 0.0): Species {
        val total = species.sumOf { s ->
            (s.rarity.weight * (1.0 + luckBonus * s.rarity.ordinal)).toDouble()
        }.toFloat()
        if (total <= 0f) return species.first()

        var r = rng.nextFloat() * total
        for (s in species) {
            val w = (s.rarity.weight * (1.0 + luckBonus * s.rarity.ordinal)).toFloat()
            r -= w
            if (r <= 0f) return s
        }
        return species.last()
    }
}

/**
 * 全部鱼种与地图。6 张水域 × 每图 9 种 = 54 种鱼。
 *
 * 每种鱼都有**独立的外观素材**（54 张不同的动画图集），
 * 不存在多张地图共用同一张图、只换名字的情况。
 */
object Bestiary {

    private fun sp(
        id: String, name: String, rarity: Rarity, sprite: String,
        mul: Double = 1.0, scale: Double = 1.0, escape: Double = 1.0,
        tint: FishTint = FishTint.NONE,
    ): Species = Species(id, name, rarity, sprite, mul, scale.toFloat(), escape.toFloat(), tint)

    /** 1. 村口小河 —— 水浅鱼小，适合练手。 */
    val VILLAGE_CREEK = FishingMap(
        "creek", "村口小河", "水浅鱼小，适合练手。",
        1.0, 0.0,
        listOf(
            sp("baitiao", "白条", Rarity.COMMON, "fish_common", 1.0, 0.85),
            sp("jiyu", "鲫鱼", Rarity.COMMON, "fw_jiyu", 1.5, 0.95),
            sp("niqiu", "泥鳅", Rarity.COMMON, "fw_niqiu", 2.0, 0.80),
            sp("caoyu", "草鱼", Rarity.RARE, "fish_rare", 1.0, 1.00),
            sp("liyu", "鲤鱼", Rarity.RARE, "fw_liyu", 1.5, 1.05),
            sp("huangsang", "黄颡鱼", Rarity.RARE, "fw_huangsang", 2.1, 0.95),
            sp("qingyu", "青鱼", Rarity.EPIC, "fw_qingyu", 1.0, 1.10),
            sp("heiyu", "黑鱼", Rarity.EPIC, "fw_heiyu", 1.6, 1.15),
            sp("jinji", "金鲫", Rarity.LEGEND, "fz_jinji", 1.0, 1.20),
        ),
    )

    /** 2. 芦苇荡 —— 水草丰茂，鱼肥水美。 */
    val REED_MARSH = FishingMap(
        "marsh", "芦苇荡", "水草丰茂，鱼肥水美。",
        3_840.0, 1_150_000.0,
        listOf(
            sp("maisui", "麦穗鱼", Rarity.COMMON, "fy_maisui", 1.0, 0.78),
            sp("moroko", "中华鳑鮍", Rarity.COMMON, "fy_moroko", 1.5, 0.80),
            sp("pangpi", "鳑鮍", Rarity.COMMON, "fy_pangpi", 2.0, 0.80),
            sp("bianyu", "鳊鱼", Rarity.RARE, "fy_bianyu", 1.0, 1.00),
            sp("wuchang", "武昌鱼", Rarity.RARE, "fy_wuchang", 1.5, 1.02),
            sp("lingyu", "鲮鱼", Rarity.RARE, "fy_lingyu", 2.1, 1.00),
            sp("guiyu", "鳜鱼", Rarity.EPIC, "fy_guiyu", 1.0, 1.12),
            sp("hualu", "花鲈", Rarity.EPIC, "fz_hualu", 1.6, 1.10),
            sp("hongli", "红鲤", Rarity.LEGEND, "fz_hongli", 1.0, 1.20),
        ),
    )

    /** 3. 深山碧潭 —— 潭深水冷，藏着年岁久远的老鱼。 */
    val DEEP_POOL = FishingMap(
        "pool", "深山碧潭", "潭深水冷，藏着年岁久远的老鱼。",
        14_700_000.0, 4_420_000_000.0,
        listOf(
            sp("huaqiu", "花鳅", Rarity.COMMON, "fz_huaqiu", 1.0, 0.85),
            sp("dalinqiu", "大鳞泥鳅", Rarity.COMMON, "fz_dalinqiu", 1.5, 0.90),
            sp("tongyu", "铜鱼", Rarity.COMMON, "fy_tongyu", 2.0, 1.00),
            sp("shatang", "沙塘鳢", Rarity.RARE, "fy_shatang", 1.0, 1.05),
            sp("changwen", "长吻鮠", Rarity.RARE, "fy_changwen", 1.5, 1.08),
            sp("huzi", "胡子鲶", Rarity.RARE, "fy_huzi", 2.1, 1.10),
            sp("nianyu", "鲶鱼", Rarity.EPIC, "fw_nianyu", 1.0, 1.20),
            sp("eel", "河鳗", Rarity.EPIC, "fw_eel", 1.6, 1.10),
            sp("jingli", "镜鲤", Rarity.LEGEND, "fz_jingli", 1.0, 1.25),
        ),
    )

    /** 4. 急流险滩 —— 水流湍急，只有强健的鱼能立足。 */
    val RAPIDS = FishingMap(
        "rapids", "急流险滩", "水流湍急，只有强健的鱼能立足。",
        5.66e10, 1.70e13,
        listOf(
            sp("qiaozui", "翘嘴鲌", Rarity.COMMON, "fy_qiaozui", 1.0, 1.00),
            sp("hongqi", "红鳍鲌", Rarity.COMMON, "fy_hongqi", 1.5, 0.95),
            sp("huangwei", "黄尾鲴", Rarity.COMMON, "fz_huangwei", 2.0, 0.98),
            sp("gouyu", "狗鱼", Rarity.RARE, "fy_gouyu", 1.0, 1.10),
            sp("shengyu", "生鱼", Rarity.RARE, "fy_shengyu", 1.5, 1.15),
            sp("huangshan", "黄鳝", Rarity.RARE, "fy_huangshan", 2.1, 0.95),
            sp("xunyu", "中华鲟", Rarity.EPIC, "fw_xunyu", 1.0, 1.35),
            sp("baixun", "白鲟", Rarity.EPIC, "fy_baixun", 1.6, 1.40),
            sp("yanzhi", "胭脂鱼", Rarity.LEGEND, "fy_yanzhi", 1.0, 1.25),
        ),
    )

    /** 5. 月牙湖 —— 月圆之夜，湖底会浮起金光。 */
    val CRESCENT_LAKE = FishingMap(
        "lake", "月牙湖", "月圆之夜，湖底会浮起金光。",
        2.17e14, 6.52e16,
        listOf(
            sp("ziyu", "鲻鱼", Rarity.COMMON, "fy_ziyu", 1.0, 0.98),
            sp("bailian", "白鲢", Rarity.COMMON, "fz_bailian", 1.5, 1.05),
            sp("huanyu", "鲩鱼", Rarity.COMMON, "fz_huanyu", 2.0, 1.08),
            sp("yongyu", "鳙鱼", Rarity.RARE, "fy_yongyu", 1.0, 1.15),
            sp("hailu", "海鲈", Rarity.RARE, "fz_hailu", 1.5, 1.10),
            sp("luyu", "鲈鱼", Rarity.RARE, "fw_luyu", 2.1, 1.05),
            sp("arowana", "金龙鱼", Rarity.EPIC, "fw_arowana", 1.0, 1.25),
            sp("hudie", "蝴蝶锦鲤", Rarity.EPIC, "fz_hudie", 1.6, 1.30),
            sp("jinli", "黄金锦鲤", Rarity.LEGEND, "fz_jinli", 1.0, 1.28),
        ),
    )

    /** 6. 龙渊秘境 —— 传说中龙潜之渊，凡鱼皆已成精。 */
    val DRAGON_ABYSS = FishingMap(
        "abyss", "龙渊秘境", "传说中龙潜之渊，凡鱼皆已成精。",
        8.35e17, 2.50e20,
        listOf(
            sp("wuli", "乌鲤", Rarity.COMMON, "fz_wuli", 1.0, 1.10),
            sp("daheiyu", "大黑鱼", Rarity.COMMON, "fz_daheiyu", 1.5, 1.20),
            sp("junian", "巨鲶", Rarity.COMMON, "fz_junian", 2.0, 1.25),
            sp("baishan", "白鳝", Rarity.RARE, "fz_baishan", 1.0, 1.05),
            sp("daliyu", "大鲤鱼", Rarity.RARE, "fw_daliyu", 1.5, 1.25),
            sp("juli", "巨鲤", Rarity.RARE, "fz_juli", 2.1, 1.30),
            sp("xunwang", "白鲟王", Rarity.EPIC, "fz_xunwang", 1.0, 1.45),
            sp("jinlong", "金龙王鱼", Rarity.EPIC, "fz_jinlong", 1.6, 1.35),
            sp("epic", "远古巨鱼", Rarity.LEGEND, "fish_epic", 1.0, 1.40),
        ),
    )

    val maps: List<FishingMap> = listOf(
        VILLAGE_CREEK, REED_MARSH, DEEP_POOL, RAPIDS, CRESCENT_LAKE, DRAGON_ABYSS,
    )

    val allSpecies: List<Species> = maps.flatMap { it.species }

    fun speciesById(id: String): Species? = allSpecies.firstOrNull { it.id == id }

    fun mapById(id: String): FishingMap? = maps.firstOrNull { it.id == id }

    val totalSpecies: Int get() = allSpecies.size
}
