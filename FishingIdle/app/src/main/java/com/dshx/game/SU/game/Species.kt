package com.dshx.game.SU.game

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

/**
 * 鱼的体型档。
 *
 * 同一条鱼也会有大有小：体型越少见、价值越高。体型在**入水时就抽好**
 * （不是钓上来才决定），所以河里能直接看出大小差异；
 * 钓到刷新该鱼种最大体型的鱼时，会像解锁图鉴那样弹一次中央提示。
 */
enum class FishSize(val label: String, val scale: Float, val valueMul: Double, val weight: Double) {
    NORMAL("普通", 1.00f, 1.0, 0.790),
    BIG("大只", 1.18f, 2.5, 0.165),
    HUGE("巨大", 1.40f, 8.0, 0.040),
    KING("王者", 1.68f, 30.0, 0.005),
    ;

    companion object {
        /** 按权重抽一个体型（王者 0.5%）。 */
        fun roll(): FishSize {
            val total = entries.sumOf { it.weight }
            var r = kotlin.random.Random.nextDouble() * total
            for (e in entries) {
                r -= e.weight
                if (r <= 0.0) return e
            }
            return NORMAL
        }
    }
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
 * 一张水域的环境外观。
 *
 * 每张图的天空、水体、云、水草、河床都不一样 —— 解锁新水域之后，
 * 画面本身要能让人一眼看出"换地方了"。配色走代码（渐变 + 染色），
 * 云与水草是各自的帧动画素材（`cloud_<id>_anim` / `seaweed_<id>_anim`）。
 */
class MapEnv(
    /** 与地图 id 相同，渲染层用它判断该不该重载环境素材。 */
    val id: String,
    /** 云朵图集名（不含 _anim 后缀）。 */
    val cloud: String,
    /** 水草图集名（不含 _anim 后缀）。 */
    val seaweed: String,
    /** 天空渐变：上 → 水面。 */
    val skyTop: Int,
    val skyBottom: Int,
    /** 水体渐变：水面 → 中层 → 河床。 */
    val waterTop: Int,
    val waterMid: Int,
    val waterBottom: Int,
    /** 水下光柱颜色。 */
    val beam: Int,
    /** 水体平铺纹理的染色（PorterDuff.MULTIPLY，白色=原色）。 */
    val waterTint: Int,
    /** 河床染色（同上）。 */
    val bedTint: Int,
    /** 水草间距（世界单位），越小越密。 */
    val seaweedSpacing: Float = 320f,
)

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
    val env: MapEnv,
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

    /**
     * 0xRRGGBB → ARGB 颜色值。
     * 这里刻意不调 android.graphics.Color.rgb —— 游戏模型层不依赖 Android 图形库，
     * 否则纯 JVM 单测里拿到的是桩返回值（全是 0），配色断言根本测不出问题。
     */
    private fun rgb(v: Int) = 0xFF shl 24 or v

    /** 1. 村口小河：白天、明亮、水色偏青绿。 */
    val ENV_CREEK = MapEnv(
        "creek", "cloud_creek", "seaweed_creek",
        skyTop = rgb(0x1A3A48), skyBottom = rgb(0x407E8A),
        waterTop = rgb(0x2E6E78), waterMid = rgb(0x1E5460), waterBottom = rgb(0x103038),
        beam = rgb(0xC8F5FF), waterTint = rgb(0xFFFFFF), bedTint = rgb(0xFFFFFF),
        seaweedSpacing = 320f,
    )

    /** 2. 芦苇荡：黄昏、水色浑浊偏黄绿、水草密。 */
    val ENV_MARSH = MapEnv(
        "marsh", "cloud_marsh", "seaweed_marsh",
        skyTop = rgb(0x4A3C2A), skyBottom = rgb(0xB99A5C),
        waterTop = rgb(0x5E7A5A), waterMid = rgb(0x3C5A44), waterBottom = rgb(0x1E3228),
        beam = rgb(0xFFE9A8), waterTint = rgb(0xD8E0A8), bedTint = rgb(0xC8B480),
        seaweedSpacing = 240f,
    )

    /** 3. 深山碧潭：冷青、幽深、光柱偏冷。 */
    val ENV_POOL = MapEnv(
        "pool", "cloud_pool", "seaweed_pool",
        skyTop = rgb(0x14282E), skyBottom = rgb(0x2E5C60),
        waterTop = rgb(0x286068), waterMid = rgb(0x123C4A), waterBottom = rgb(0x081E2A),
        beam = rgb(0xA8E8F0), waterTint = rgb(0xB8D8E0), bedTint = rgb(0x88A8A0),
        seaweedSpacing = 340f,
    )

    /** 4. 急流险滩：灰蓝冷冽、水面亮、水草稀疏。 */
    val ENV_RAPIDS = MapEnv(
        "rapids", "cloud_rapids", "seaweed_rapids",
        skyTop = rgb(0x30404E), skyBottom = rgb(0x7A9AAE),
        waterTop = rgb(0x6090A0), waterMid = rgb(0x306074), waterBottom = rgb(0x18384A),
        beam = rgb(0xE0F4FF), waterTint = rgb(0xC0DCF0), bedTint = rgb(0xA0A8A0),
        seaweedSpacing = 460f,
    )

    /** 5. 月牙湖：月夜紫蓝、水面泛银光。 */
    val ENV_LAKE = MapEnv(
        "lake", "cloud_lake", "seaweed_lake",
        skyTop = rgb(0x101436), skyBottom = rgb(0x464884),
        waterTop = rgb(0x303A7C), waterMid = rgb(0x18225A), waterBottom = rgb(0x0A1030),
        beam = rgb(0xD8E4FF), waterTint = rgb(0xA8B4F0), bedTint = rgb(0x8890C8),
        seaweedSpacing = 300f,
    )

    /** 6. 龙渊秘境：深紫墨色、幽光、水草泛紫。 */
    val ENV_ABYSS = MapEnv(
        "abyss", "cloud_abyss", "seaweed_abyss",
        skyTop = rgb(0x180A24), skyBottom = rgb(0x461A54),
        waterTop = rgb(0x3A1A4C), waterMid = rgb(0x200E32), waterBottom = rgb(0x0C0618),
        beam = rgb(0xE0B0FF), waterTint = rgb(0xC090E0), bedTint = rgb(0x9070B0),
        seaweedSpacing = 280f,
    )

    // ---------------- 长线经济曲线 ----------------
    //
    // 关键指标是**每张图的解锁耗时** = unlockCost ÷ 上一张图的 valueMultiplier。
    // 旧配置下这个值恒等于 1.15e6，六张图完全一样 —— 于是每张图耗时相同，
    // 一路买下去半小时就能推到最后一张图，玩家反馈"不用转生就通关了"。
    //
    // 现在改成逐级拉长（≈ ×2.6/图）：村口小河 → 芦苇荡约 4 分钟起步，
    // 最后一张龙渊秘境要熬到上百小时量级，中途必须靠转生 + 技能树滚雪球。
    // 改 unlockCost 时务必保持相邻两图的耗时递增，BestiaryTest 会守住这条。

    /** 1. 村口小河 —— 水浅鱼小，适合练手。 */
    val VILLAGE_CREEK = FishingMap(
        "creek", "村口小河", "水浅鱼小，适合练手。",
        1.0, 0.0,
        env = ENV_CREEK,
        species = listOf(
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
        env = ENV_MARSH,
        species = listOf(
            sp("maisui", "麦穗鱼", Rarity.COMMON, "fy_maisui", 1.0, 0.78),
            sp("moroko", "中华鳑鮍", Rarity.COMMON, "fy_moroko", 1.5, 0.80),
            sp("pangpi", "高体鳑鮍", Rarity.COMMON, "fy_pangpi", 2.0, 0.80),
            sp("bianyu", "鳊鱼", Rarity.RARE, "fy_bianyu", 1.0, 1.00),
            sp("wuchang", "武昌鱼", Rarity.RARE, "fy_wuchang", 1.5, 1.02),
            sp("lingyu", "鲮鱼", Rarity.RARE, "fy_lingyu", 2.1, 1.00),
            sp("guiyu", "鳜鱼", Rarity.EPIC, "fy_guiyu", 1.0, 1.12),
            sp("hualu", "蒙古鲌", Rarity.EPIC, "fz_hualu", 1.6, 1.10),
            sp("hongli", "红鲤", Rarity.LEGEND, "fz_hongli", 1.0, 1.20),
        ),
    )

    /** 3. 深山碧潭 —— 潭深水冷，藏着年岁久远的老鱼。 */
    val DEEP_POOL = FishingMap(
        "pool", "深山碧潭", "潭深水冷，藏着年岁久远的老鱼。",
        14_700_000.0, 1.634e10,
        env = ENV_POOL,
        species = listOf(
            sp("huaqiu", "花鳅", Rarity.COMMON, "fz_huaqiu", 1.0, 0.85),
            sp("dalinqiu", "大鳞泥鳅", Rarity.COMMON, "fz_dalinqiu", 1.5, 0.90),
            sp("tongyu", "铜鱼", Rarity.COMMON, "fy_tongyu", 2.0, 1.00),
            sp("shatang", "沙塘鳢", Rarity.RARE, "fy_shatang", 1.0, 1.05),
            sp("changwen", "长吻鮠", Rarity.RARE, "fy_changwen", 1.5, 1.08),
            sp("huzi", "胡子鲶", Rarity.RARE, "fy_huzi", 2.1, 1.10),
            sp("nianyu", "大口鲶", Rarity.EPIC, "fw_nianyu", 1.0, 1.20),
            sp("eel", "河鳗", Rarity.EPIC, "fw_eel", 1.6, 1.10),
            sp("jingli", "镜鲤", Rarity.LEGEND, "fz_jingli", 1.0, 1.25),
        ),
    )

    /** 4. 急流险滩 —— 水流湍急，只有强健的鱼能立足。 */
    val RAPIDS = FishingMap(
        "rapids", "急流险滩", "水流湍急，只有强健的鱼能立足。",
        5.66e10, 2.314e14,
        env = ENV_RAPIDS,
        species = listOf(
            sp("qiaozui", "翘嘴鲌", Rarity.COMMON, "fy_qiaozui", 1.0, 1.00),
            sp("hongqi", "红鳍鲌", Rarity.COMMON, "fy_hongqi", 1.5, 0.95),
            sp("huangwei", "黄尾鲴", Rarity.COMMON, "fz_huangwei", 2.0, 0.98),
            sp("gouyu", "狗鱼", Rarity.RARE, "fy_gouyu", 1.0, 1.10),
            sp("shengyu", "鳡鱼", Rarity.RARE, "fy_shengyu", 1.5, 1.15),
            sp("huangshan", "黄鳝", Rarity.RARE, "fy_huangshan", 2.1, 0.95),
            sp("xunyu", "中华鲟", Rarity.EPIC, "fw_xunyu", 1.0, 1.35),
            sp("baixun", "白鲟", Rarity.EPIC, "fy_baixun", 1.6, 1.40),
            sp("yanzhi", "胭脂鱼", Rarity.LEGEND, "fy_yanzhi", 1.0, 1.25),
        ),
    )

    /** 5. 月牙湖 —— 月圆之夜，湖底会浮起金光。 */
    val CRESCENT_LAKE = FishingMap(
        "lake", "月牙湖", "月圆之夜，湖底会浮起金光。",
        2.17e14, 3.297e18,
        env = ENV_LAKE,
        species = listOf(
            sp("ziyu", "鲻鱼", Rarity.COMMON, "fy_ziyu", 1.0, 0.98),
            sp("bailian", "白鲢", Rarity.COMMON, "fz_bailian", 1.5, 1.05),
            sp("huanyu", "赤眼鳟", Rarity.COMMON, "fz_huanyu", 2.0, 1.08),
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
        8.35e17, 4.677e22,
        env = ENV_ABYSS,
        species = listOf(
            sp("wuli", "乌鲤", Rarity.COMMON, "fz_wuli", 1.0, 1.10),
            sp("daheiyu", "斑鳢", Rarity.COMMON, "fz_daheiyu", 1.5, 1.20),
            sp("junian", "欧鲶", Rarity.COMMON, "fz_junian", 2.0, 1.25),
            sp("baishan", "花鳗鲡", Rarity.RARE, "fz_baishan", 1.0, 1.05),
            sp("daliyu", "荷包红鲤", Rarity.RARE, "fw_daliyu", 1.5, 1.25),
            sp("juli", "湄公河巨鲤", Rarity.RARE, "fz_juli", 2.1, 1.30),
            sp("xunwang", "达氏鳇", Rarity.EPIC, "fz_xunwang", 1.0, 1.45),
            sp("jinlong", "红龙鱼", Rarity.EPIC, "fz_jinlong", 1.6, 1.35),
            sp("epic", "巨骨舌鱼", Rarity.LEGEND, "fish_epic", 1.0, 1.40),
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
