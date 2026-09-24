package com.dshx.game.SU.game

/**
 * 角色（换装系统）。
 *
 * 每个角色是一套**独立的外观素材 + 一条专属鱼竿技能**：
 * 不是单纯的皮肤，换角色等于换一套玩法手感。
 *
 * 鱼竿技能都在 [World] 里兑现，见 [RodSkill] 各枚举的说明。
 */
class CharacterDef(
    val id: String,
    val name: String,
    val title: String,
    /** 立绘/动画素材前缀（对应 art/<sprite>_cast_anim.png）。 */
    val sprite: String,
    val desc: String,
    val rodSkill: RodSkill,
    /** 解锁价格（金币）；0 = 初始拥有。 */
    val price: Double,
    /** 是否需要珍珠（后期角色）。 */
    val pearlPrice: Long = 0,
    /** 默认是否已拥有。 */
    val owned: Boolean = false,
    /** 是否帮手（用于帮手换装列表）。 */
    val isHelper: Boolean = false,
    /** 专属浮漂颜色（ARGB），换角色时浮漂也跟着换。 */
    val bobberColor: Int = 0xFFFF5A4E.toInt(),
)

/**
 * 鱼竿技能：不同角色的核心差异。
 *
 * 都做成"改变手感"而不是"纯数值加成"，否则换角色只是换个数字。
 */
enum class RodSkill(val displayName: String, val desc: String) {
    /** 均衡：无特殊效果，但收线略快（基准角色）。 */
    BALANCED("均衡", "收线速度 +10%，没有短板"),

    /** 多线齐发：一次抛竿会同时下 3 个浮漂，每个都可能中鱼。 */
    MULTI_LINE("多线齐发", "一次抛出 3 根鱼线，同时钓 3 个点"),

    /** 范围吸引：抛竿落点会把一大片鱼全部吸过来。 */
    AREA_ATTRACT("范围诱鱼", "落点周围大范围的鱼全部被吸引过来"),

    /** 极速出杆：抛竿冷却大幅缩短，几乎可以连抛。 */
    FAST_CAST("极速出杆", "抛竿间隔 -60%，几乎可以连续抛竿"),

    /** 大力收线：收线速度大幅提升，且连击加成更高。 */
    POWER_REEL("大力收线", "收线速度 +45%，连击加成翻倍"),

    /** 幸运竿：高稀有度鱼出现率大幅提升。 */
    LUCKY_ROD("幸运竿", "稀有鱼出现率大幅提升"),

    /** 自动钓手加成：作为帮手时的特性，提升挂机产出。 */
    HELPER_BOOST("勤勉", "作为自动钓手时产出 +25%"),
}

/**
 * 全部角色。
 *
 * 主角 4 个（含初始），帮手 3 个。价格随强度递增，后期角色用珍珠解锁，
 * 让转生有新的追求。
 */
object Characters {

    val all: List<CharacterDef> = listOf(
        // ---------------- 主角 ----------------
        CharacterDef(
            id = "boy_default",
            name = "小渔",
            title = "初出茅庐",
            sprite = "player_cast",
            desc = "村口长大的孩子，第一根鱼竿是爷爷留下的。什么都会一点。",
            rodSkill = RodSkill.BALANCED,
            price = 0.0,
            owned = true,
        ),
        CharacterDef(
            id = "girl_linna",
            name = "林娜",
            title = "双马尾钓手",
            sprite = "player2_cast",
            desc = "镇上的钓鱼冠军，手上那根红竿能同时照看三个漂。",
            rodSkill = RodSkill.MULTI_LINE,
            price = 250_000.0,
            bobberColor = 0xFFFF4D4D.toInt(),
        ),
        CharacterDef(
            id = "old_chen",
            name = "老陈",
            title = "退休船长",
            sprite = "player3_cast",
            desc = "在海上漂了四十年。他说鱼不是钓上来的，是哄上来的。",
            rodSkill = RodSkill.AREA_ATTRACT,
            price = 2_500_000.0,
            bobberColor = 0xFF4DA6FF.toInt(),
        ),
        CharacterDef(
            id = "speed_yan",
            name = "阿炎",
            title = "路亚狂人",
            sprite = "player4_cast",
            desc = "一天抛两千竿的男人。他说钓鱼就是不停地抛，直到鱼烦了。",
            rodSkill = RodSkill.FAST_CAST,
            price = 12_000_000.0,
            bobberColor = 0xFF5AE06A.toInt(),
        ),

        // ---------------- 帮手 ----------------
        CharacterDef(
            id = "helper_default",
            name = "阿水",
            title = "学徒",
            sprite = "helper_cast",
            desc = "来钓场打杂的少年，手脚还算勤快。",
            rodSkill = RodSkill.BALANCED,
            price = 0.0,
            owned = true,
            isHelper = true,
            bobberColor = 0xFF8A8A8A.toInt(),
        ),
        CharacterDef(
            id = "helper_yun",
            name = "小云",
            title = "麻利姑娘",
            sprite = "helper2_cast",
            desc = "手最快的一个，别人收一竿她能收两竿。",
            rodSkill = RodSkill.HELPER_BOOST,
            price = 800_000.0,
            isHelper = true,
            bobberColor = 0xFFB06AE0.toInt(),
        ),
        CharacterDef(
            id = "helper_mu",
            name = "老木",
            title = "木匠",
            sprite = "helper3_cast",
            desc = "自己削的竹竿，据说沾了手气。他钓上来的鱼总是偏大。",
            rodSkill = RodSkill.LUCKY_ROD,
            price = 5_000_000.0,
            isHelper = true,
            bobberColor = 0xFFC8A05A.toInt(),
        ),
    )

    /** 主角列表（不含帮手）。 */
    val players: List<CharacterDef> = all.filter { !it.isHelper }

    /** 帮手列表。 */
    val helpers: List<CharacterDef> = all.filter { it.isHelper }

    fun byId(id: String): CharacterDef? = all.firstOrNull { it.id == id }

    /** 默认主角。 */
    val defaultPlayer: CharacterDef get() = players.first()

    /** 默认帮手。 */
    val defaultHelper: CharacterDef get() = helpers.first()
}
