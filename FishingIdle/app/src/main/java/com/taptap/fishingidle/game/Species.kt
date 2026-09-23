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
    RARE("稀有", 20.0, 34f),
    EPIC("史诗", 300.0, 9f),
    LEGEND("传说", 5000.0, 2f),
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
    val totalWeight: Float get() = species.sumOf { it.rarity.weight.toDouble() }.toFloat()

    fun roll(rng: Random): Species {
        var r = rng.nextFloat() * totalWeight
        for (s in species) {
            r -= s.rarity.weight
            if (r <= 0f) return s
        }
        return species.last()
    }
}

/** 全部鱼种与地图。共 6 张图 × 9 种 = 54 种鱼。 */
object Bestiary {

    private fun sp(
        id: String, name: String, rarity: Rarity, sprite: String,
        mul: Double = 1.0, scale: Double = 1.0, escape: Double = 1.0,
        tint: FishTint = FishTint.NONE,
    ): Species = Species(id, name, rarity, sprite, mul, scale.toFloat(), escape.toFloat(), tint)

    // ---------------- 1. 村口小河 ----------------
    val VILLAGE_CREEK = FishingMap(
        "creek", "村口小河", "水浅鱼小，适合练手。",
        1.0, 0.0,
        listOf(
            sp("baitiao", "白条", Rarity.COMMON, "fish_common", 1.0, 0.85, tint = FishTint.SILVER),
            sp("jiyu", "鲫鱼", Rarity.COMMON, "fw_jiyu", 1.5, 0.95),
            sp("niqiu", "泥鳅", Rarity.COMMON, "fw_niqiu", 2.0, 0.80),
            sp("caoyu", "草鱼", Rarity.RARE, "fish_rare", 1.0, 1.00),
            sp("liyu", "鲤鱼", Rarity.RARE, "fw_liyu", 1.5, 1.05),
            sp("huangsang", "黄颡鱼", Rarity.RARE, "fw_huangsang", 2.1, 0.95),
            sp("qingyu", "青鱼", Rarity.EPIC, "fw_qingyu", 1.0, 1.10),
            sp("heiyu", "黑鱼", Rarity.EPIC, "fw_heiyu", 1.6, 1.15),
            sp("jinli", "金鲫", Rarity.LEGEND, "fw_jiyu", 1.0, 1.20, tint = FishTint.GOLDEN),
        ),
    )

    // ---------------- 2. 芦苇荡 ----------------
    val REED_MARSH = FishingMap(
        "marsh", "芦苇荡", "水草丰茂，鱼肥水美。",
        7.0, 6_000.0,
        listOf(
            sp("jiyu2", "肥鲫", Rarity.COMMON, "fw_jiyu", 1.0, 1.05),
            sp("niqiu2", "大泥鳅", Rarity.COMMON, "fw_niqiu", 1.3, 0.95),
            sp("baitiao2", "银白条", Rarity.COMMON, "fish_common", 1.6, 0.90, tint = FishTint.PALE),
            sp("lianyu", "鲢鱼", Rarity.RARE, "fw_jiyu", 1.2, 1.10, tint = FishTint.PALE),
            sp("luyu", "鲈鱼", Rarity.RARE, "fw_luyu", 1.8, 1.00),
            sp("liyu2", "红鲤", Rarity.RARE, "fw_liyu", 2.3, 1.05, tint = FishTint.CRIMSON),
            sp("qingyu2", "大青鱼", Rarity.EPIC, "fw_qingyu", 1.0, 1.20),
            sp("manyu", "河鳗", Rarity.EPIC, "fw_eel", 1.5, 1.00),
            sp("daliyu", "大鲤鱼", Rarity.LEGEND, "fw_daliyu", 1.0, 1.25),
        ),
    )

    // ---------------- 3. 深山碧潭 ----------------
    val DEEP_POOL = FishingMap(
        "pool", "深山碧潭", "潭深水冷，藏着年岁久远的老鱼。",
        50.0, 150_000.0,
        listOf(
            sp("jiyu3", "潭鲫", Rarity.COMMON, "fw_jiyu", 1.0, 1.10, tint = FishTint.JADE),
            sp("niqiu3", "潭鳅", Rarity.COMMON, "fw_niqiu", 1.4, 0.95, tint = FishTint.DARK),
            sp("lianyu2", "花鲢", Rarity.COMMON, "fw_jiyu", 1.9, 1.10, tint = FishTint.PALE),
            sp("luyu2", "潭鲈", Rarity.RARE, "fw_luyu", 1.0, 1.05, tint = FishTint.JADE),
            sp("heiyu2", "黑鱼王", Rarity.RARE, "fw_heiyu", 1.7, 1.25),
            sp("qingyu3", "青鱼王", Rarity.EPIC, "fw_qingyu", 1.0, 1.30),
            sp("manyu2", "潭鳗", Rarity.EPIC, "fw_eel", 1.6, 1.05, tint = FishTint.DARK),
            sp("nianyu", "鲶鱼", Rarity.EPIC, "fw_nianyu", 2.3, 1.20),
            sp("cuili", "翠鳞鲤", Rarity.LEGEND, "fw_liyu", 1.0, 1.25, tint = FishTint.JADE),
        ),
    )

    // ---------------- 4. 急流险滩 ----------------
    val RAPIDS = FishingMap(
        "rapids", "急流险滩", "水流湍急，只有强健的鱼能立足。",
        380.0, 4_000_000.0,
        species = listOf(
            sp("jiyu4", "溪鲫", Rarity.COMMON, "fw_jiyu", 1.0, 1.00, tint = FishTint.AZURE),
            sp("baitiao3", "急流白条", Rarity.COMMON, "fish_common", 1.5, 0.85, tint = FishTint.SILVER),
            sp("niqiu4", "溪鳅", Rarity.COMMON, "fw_niqiu", 2.0, 0.90),
            sp("luyu3", "急流鲈", Rarity.RARE, "fw_luyu", 1.0, 1.05),
            sp("huangsang2", "大黄颡", Rarity.RARE, "fw_huangsang", 1.5, 1.05),
            sp("xunyu", "中华鲟", Rarity.EPIC, "fw_xunyu", 1.0, 1.35),
            sp("heiyu3", "江黑鱼", Rarity.EPIC, "fw_heiyu", 1.5, 1.20, tint = FishTint.DARK),
            sp("manyu3", "溪鳗", Rarity.EPIC, "fw_eel", 2.0, 1.10),
            sp("arowana", "金龙鱼", Rarity.LEGEND, "fw_arowana", 1.0, 1.25),
        ),
    )

    // ---------------- 5. 月牙湖 ----------------
    val CRESCENT_LAKE = FishingMap(
        "lake", "月牙湖", "月圆之夜，湖底会浮起金光。",
        2_600.0, 100_000_000.0,
        listOf(
            sp("jiyu5", "湖鲫", Rarity.COMMON, "fw_jiyu", 1.0, 1.05, tint = FishTint.SILVER),
            sp("baitiao4", "月华白条", Rarity.COMMON, "fish_common", 1.7, 0.85, tint = FishTint.AZURE),
            sp("niqiu5", "湖鳅", Rarity.COMMON, "fw_niqiu", 2.2, 0.90, tint = FishTint.SILVER),
            sp("lianyu3", "湖鲢", Rarity.RARE, "fw_jiyu", 1.0, 1.15, tint = FishTint.PALE),
            sp("luyu4", "月鲈", Rarity.RARE, "fw_luyu", 1.6, 1.10, tint = FishTint.AZURE),
            sp("qingyu4", "湖青鱼", Rarity.EPIC, "fw_qingyu", 1.0, 1.25, tint = FishTint.AZURE),
            sp("manyu4", "湖鳗", Rarity.EPIC, "fw_eel", 1.6, 1.15, tint = FishTint.PURPLE),
            sp("xunyu2", "湖鲟", Rarity.LEGEND, "fw_xunyu", 1.0, 1.40, tint = FishTint.SILVER),
            sp("yuelong", "月光龙鱼", Rarity.LEGEND, "fw_arowana", 1.8, 1.30, tint = FishTint.PALE),
        ),
    )

    // ---------------- 6. 龙渊秘境 ----------------
    val DRAGON_ABYSS = FishingMap(
        "abyss", "龙渊秘境", "传说中龙潜之渊，凡鱼皆已成精。",
        18_000.0, 2_500_000_000.0,
        listOf(
            sp("jiyu6", "灵鲫", Rarity.COMMON, "fw_jiyu", 1.0, 1.10, tint = FishTint.PURPLE),
            sp("niqiu6", "玉泥鳅", Rarity.COMMON, "fw_niqiu", 1.8, 0.95, tint = FishTint.JADE),
            sp("baitiao5", "幽光白条", Rarity.COMMON, "fish_common", 2.4, 0.90, tint = FishTint.PURPLE),
            sp("luyu5", "渊鲈", Rarity.RARE, "fw_luyu", 1.0, 1.15, tint = FishTint.PURPLE),
            sp("huangsang3", "渊黄颡", Rarity.RARE, "fw_huangsang", 1.7, 1.10, tint = FishTint.CRIMSON),
            sp("xunyu3", "渊鲟", Rarity.EPIC, "fw_xunyu", 1.0, 1.45, tint = FishTint.PURPLE),
            sp("heiyu4", "墨龙鱼", Rarity.EPIC, "fw_heiyu", 1.8, 1.30, tint = FishTint.DARK),
            sp("longli", "龙鳞鱼", Rarity.LEGEND, "fw_arowana", 1.0, 1.35, tint = FishTint.CRIMSON),
            sp("dragoncarp", "龙渊鲤", Rarity.LEGEND, "fw_daliyu", 2.0, 1.40, tint = FishTint.PURPLE),
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
