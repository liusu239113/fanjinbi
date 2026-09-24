package com.taptap.fishingidle.game

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * 世界尺寸。宽度远大于一屏 —— 钓场是一条横向延展的河流，
 * 镜头可以左右拖动，这样鱼群、钓手才有地方铺开。
 */
object Space {
    /** 世界总宽度（世界单位）。约等于 3~4 屏。 */
    const val W = 3000f

    /** 世界高度，正好铺满一屏高度。 */
    const val H = 1500f

    /** 水面线。上方是天空与船，下方是水下的钓场。 */
    const val SURFACE_Y = 330f

    /**
     * 船底（龙骨）所在的水位线。
     *
     * 主角与**所有**帮手都以这条线对齐 —— 之前主角按立绘中心画、
     * 帮手按另一套偏移画，两者相差二十多个世界单位，看起来就是一高一低。
     * 现在两边都按"立绘里的船底像素贴这条线"来画。
     */
    const val BOAT_WATERLINE = SURFACE_Y + 16f

    /** 鱼群活动区域。 */
    const val POND_L = 90f
    const val POND_R = W - 90f
    const val POND_T = SURFACE_Y + 130f
    const val POND_B = H - 120f

    /**
     * 抛竿离最近鱼的最大有效距离，超出则没有鱼来咬钩。
     *
     * 这个值直接决定"看起来有没有鱼"：手机竖屏下屏幕约 675 世界单位宽，
     * 取 420 的话半屏内的鱼都算数，玩家会觉得"空地方也能钓上鱼"。
     * 收到 190 大约占屏宽 28%，必须真的抛到鱼边上才行。
     */
    const val MAX_BITE_RANGE = 190f
}

/**
 * 船只立绘的几何参数（世界单位）。
 *
 * 渲染层用这些值把立绘摆到水位线上，[World] 用同一份数据算竿尖坐标，
 * 两边共用一处定义 —— 否则线、水花、船三者很容易各按各的偏移画，对不齐。
 *
 * 比例值都是从素材里量出来的（见 tools/measure_boat.py）：
 * 换船体素材必须同步更新这里，不然船会沉进水里或浮在半空。
 */
object BoatArt {
    /** 帮手立绘高度（世界单位）。 */
    const val HEIGHT = 170f

    /**
     * 主角立绘高度（世界单位），比帮手矮一截。
     *
     * 主角素材是 384×256（3:2）、帮手是 192×192（1:1）。两张按**同一高度**画出来，
     * 主角那条船会宽出整整 50%，摆在帮手旁边完全不是一个体量，看着很别扭。
     * 按这个高度画，主角船的宽度才和帮手船接近（约 186 : 170 世界单位）。
     */
    const val PLAYER_HEIGHT = 124f

    /** 船底（龙骨最低点）像素所在高度占立绘高度的比例。 */
    const val PLAYER_HULL_FRAC = 0.902f
    const val HELPER_HULL_FRAC = 0.807f

    /** 竿尖在立绘中的位置（比例坐标，朝右时；朝左按镜像取）。 */
    const val PLAYER_ROD_TIP_X = 0.924f
    const val PLAYER_ROD_TIP_Y = 0.258f
    const val HELPER_ROD_TIP_X = 0.901f
    const val HELPER_ROD_TIP_Y = 0.266f

    /** 主角立绘宽高比（384×256）。 */
    const val PLAYER_ASPECT = 384f / 256f
    /** 帮手立绘宽高比（192×192）。 */
    const val HELPER_ASPECT = 1f

    /** 以立绘中心绘制时，中心相对水位线的 y 偏移（朝上的负方向）。 */
    fun centerOffsetY(hullFrac: Float, height: Float = HEIGHT): Float = -(hullFrac - 0.5f) * height

    /** 主角竿尖相对船中心的偏移（按主角自己的立绘高度算）。 */
    fun rodTipOffset(boatFacing: Float): Pair<Float, Float> = Pair(
        (PLAYER_ROD_TIP_X - 0.5f) * PLAYER_HEIGHT * PLAYER_ASPECT * boatFacing,
        centerOffsetY(PLAYER_HULL_FRAC, PLAYER_HEIGHT) + (PLAYER_ROD_TIP_Y - 0.5f) * PLAYER_HEIGHT,
    )
}

enum class FishState { SWIMMING, APPROACHING, BITING, HOOKED, CAUGHT, ESCAPED }

class Fish(val species: Species, var x: Float, var y: Float) {
    /** 该鱼所属的稀有度档位，决定经济曲线。 */
    val kind: Rarity get() = species.rarity

    var vx = 0f
    var vy = 0f
    var state = FishState.SWIMMING
        set(value) {
            field = value
            stateTime = 0f
        }
    var facing = 1f
    var wiggle = Random.nextFloat() * 6.2832f
    var stateTime = 0f

    /** 入水时就定下来的体型档：河里能直接看出大小差异。 */
    val size: FishSize = FishSize.roll()

    var scale = species.scale * size.scale * (0.94f + Random.nextFloat() * 0.12f)
    var targetX = 0f
    var targetY = 0f

    /** 已被某个钓手预定为目标时非空，避免多个钓手抢同一条鱼。 */
    var claimedBy: Any? = null

    fun update(dt: Float) {
        wiggle += dt * (6f + kind.ordinal * 0.6f)
        stateTime += dt

        when (state) {
            FishState.SWIMMING -> {
                if (Random.nextFloat() < dt * 0.8f) {
                    val speed = Content.swimSpeed(species.rarity)
                    val ang = Random.nextFloat() * 6.2832f
                    vx = cos(ang) * speed
                    vy = sin(ang) * speed * 0.55f
                }
                x += vx * dt
                y += vy * dt
                bounceInPond()
                if (abs(vx) > 0.5f) facing = if (vx > 0f) 1f else -1f
            }

            FishState.APPROACHING -> {
                val dx = targetX - x
                val dy = targetY - y
                val d = hypot(dx, dy)
                if (d < 24f) {
                    state = FishState.BITING
                } else {
                    val speed = Content.swimSpeed(kind) * 2.4f
                    x += dx / d * speed * dt
                    y += dy / d * speed * dt
                    facing = if (dx > 0f) 1f else -1f
                }
            }

            FishState.BITING -> {
                x += sin(wiggle * 2f) * 10f * dt
                y += cos(wiggle * 1.7f) * 8f * dt
            }

            FishState.HOOKED -> {
                y -= 170f * dt
                x += (targetX - x) * 2.6f * dt
                if (y < Space.SURFACE_Y + 20f) y = Space.SURFACE_Y + 20f
            }

            FishState.CAUGHT -> {
                x += vx * dt
                y += vy * dt
                vx *= 0.95f
                vy -= 40f * dt
            }

            FishState.ESCAPED -> {
                x += vx * dt
                y += vy * dt
                vx *= 0.95f
                vy *= 0.95f
                // 逃脱的鱼必须回到可钓状态，否则鱼群会被逐渐掏空，
                // 玩家在没有鱼之后再也无法获得任何收益。
                if (stateTime >= ESCAPE_RECOVER_TIME) {
                    state = FishState.SWIMMING
                    vx = 0f
                    vy = 0f
                }
            }
        }
    }

    fun setTarget(tx: Float, ty: Float) {
        targetX = tx
        targetY = ty
    }

    private fun bounceInPond() {
        if (x < Space.POND_L) { x = Space.POND_L; vx = abs(vx) }
        if (x > Space.POND_R) { x = Space.POND_R; vx = -abs(vx) }
        if (y < Space.POND_T) { y = Space.POND_T; vy = abs(vy) }
        if (y > Space.POND_B) { y = Space.POND_B; vy = -abs(vy) }
    }

    companion object {
        /** 逃脱后多久重新回到可钓状态（秒）。 */
        const val ESCAPE_RECOVER_TIME = 1.2f
    }
}

/** 浮标状态机。 */
enum class BobberState { IDLE, FLYING, FLOATING, BITE, REELING, DONE }

class Bobber {
    var x = 0f
    var y = 0f
    var state = BobberState.IDLE
        set(value) {
            field = value
            stateTime = 0f
        }
    var stateTime = 0f

    private var startX = 0f
    private var startY = 0f
    var targetX = 0f
    var targetY = 0f

    var biteTimer = 0f
    var reelProgress = 0f
    var biteWindow = 0f
    var hookedFish: Fish? = null

    fun cast(sx: Float, sy: Float, tx: Float, ty: Float) {
        startX = sx; startY = sy
        targetX = tx; targetY = ty
        x = sx; y = sy
        state = BobberState.FLYING
        reelProgress = 0f
        biteWindow = 0f
        hookedFish = null
    }

    /** 推进状态机，返回本帧事件。 */
    fun update(dt: Float, reelSpeed: Double, taps: Int): BobberEvent? {
        stateTime += dt
        when (state) {
            BobberState.FLYING -> {
                val t = (stateTime / FLY_DURATION).coerceIn(0f, 1f)
                x = startX + (targetX - startX) * t
                val arc = sin(t * 3.1416f) * 180f
                y = startY + (targetY - startY) * t - arc
                if (t >= 1f) {
                    x = targetX; y = targetY
                    state = BobberState.FLOATING
                    return BobberEvent.Landed
                }
            }

            BobberState.FLOATING -> {
                y = targetY + sin(stateTime * 3.4f) * 5f
                if (hookedFish == null) {
                    // 没有鱼来咬钩：空等一会儿后静默收回，避免玩家被卡住
                    if (stateTime >= EMPTY_WAIT) {
                        state = BobberState.DONE
                        return BobberEvent.Empty
                    }
                } else {
                    biteTimer -= dt
                    if (biteTimer <= 0f) {
                        state = BobberState.BITE
                        biteWindow = BITE_WINDOW
                        return BobberEvent.Bite
                    }
                }
            }

            BobberState.BITE -> {
                y = targetY + 16f + sin(stateTime * 22f) * 8f
                biteWindow -= dt
                if (biteWindow <= 0f) {
                    state = BobberState.DONE
                    return BobberEvent.Missed
                }
            }

            BobberState.REELING -> {
                val duration = Content.reelDuration(hookedFish?.kind ?: Rarity.COMMON) /
                    reelSpeed.toFloat().coerceAtLeast(0.2f)
                reelProgress += (1f / duration) * dt + taps * TAP_BONUS
                y = targetY + 16f + sin(stateTime * 14f) * 10f
                if (reelProgress >= 1f) {
                    reelProgress = 1f
                    state = BobberState.DONE
                    return BobberEvent.Reeled
                }
            }

            BobberState.IDLE, BobberState.DONE -> Unit
        }
        return null
    }

    /** 玩家点击：咬钩→开始收线。返回 true 表示点击被消费。 */
    fun onTap(): Boolean = when (state) {
        BobberState.BITE -> { state = BobberState.REELING; true }
        BobberState.REELING -> true
        else -> false
    }

    val isActive: Boolean get() = state != BobberState.DONE && state != BobberState.IDLE

    companion object {
        const val BITE_WINDOW = 2.2f
        const val TAP_BONUS = 0.055f
        const val FLY_DURATION = 0.42f

        /** 没有鱼咬钩时的空等上限（秒）。 */
        const val EMPTY_WAIT = 2.2f
    }
}

enum class BobberEvent { Landed, Bite, Missed, Reeled, Empty }

/**
 * 自动钓手：一条漂在水面上的小船，会划到目标鱼上方抛线把它钓上来。
 * 船始终停在水位线 [Space.BOAT_WATERLINE] 上，钓线垂到水下的鱼身上 ——
 * 与玩家自己的钓法一致。
 *
 * 「并行作业」升级（[GameState.helperParallel]）解锁后，一条船可以同时
 * 照看多条鱼：每条鱼各占一条钓线，一起收线、一起入账。
 */
class Helper(var x: Float) {
    /** 船底（龙骨）所在的水位线，渲染层按它对齐立绘。 */
    var y = Space.BOAT_WATERLINE

    /** 当前照看的鱼，与 [lineProgress] 一一对应。 */
    val targets = mutableListOf<Fish>()
    /** 各条钓线的收线进度 0..1，满了就把鱼钓上来。 */
    private val lineProgress = mutableListOf<Float>()

    var facing = 1f
    var wiggle = Random.nextFloat() * 6.2832f

    /**
     * 抛竿帧动画计时（秒）。< 0 表示不播放。
     * 新认领一条鱼时归零，渲染层据此播放"甩竿"的逐帧动画。
     */
    var castAnim = -1f

    /** 划船时的上下浮动相位。 */
    private var bobPhase = Random.nextFloat() * 6.2832f

    fun update(dt: Float, gameState: GameState, world: World) {
        wiggle += dt * 3.2f
        bobPhase += dt * 2.4f
        y = Space.BOAT_WATERLINE + sin(bobPhase) * 5f
        if (castAnim >= 0f) {
            castAnim += dt
            if (castAnim > CAST_ANIM_TIME) castAnim = -1f
        }

        // 目标被别的钓手带走 / 逃脱后，把这条线清掉重新找
        for (i in targets.indices.reversed()) {
            val f = targets[i]
            if (f.state != FishState.SWIMMING) {
                f.claimedBy = null
                targets.removeAt(i)
                lineProgress.removeAt(i)
            }
        }

        // 补足到当前容量（1 + 并行作业等级）
        val capacity = 1 + gameState.helperParallel
        if (targets.size < capacity) {
            val fresh = world.claimTargetsForHelper(this, capacity - targets.size)
            if (fresh.isNotEmpty()) {
                targets.addAll(fresh)
                repeat(fresh.size) { lineProgress.add(0f) }
                castAnim = 0f
            }
        }

        if (targets.isEmpty()) return

        // 朝当前所有目标的中位位置划过去，尽量站在鱼群中间
        val avgX = targets.sumOf { it.x.toDouble() }.toFloat() / targets.size
        val dx = avgX - x
        val speed = 260f *
            (gameState.helperEfficiency * gameState.skillHelperMultiplier +
                gameState.helperSpeed).toFloat()
        if (abs(dx) >= 24f) {
            x += (if (dx > 0f) 1f else -1f) * speed * dt
            x = x.coerceIn(Space.POND_L, Space.POND_R)
            facing = if (dx > 0f) 1f else -1f
        }

        // 收线：每条线独立推进，效率越高收得越快
        val gain = dt / (CATCH_TIME / (gameState.helperEfficiency * gameState.skillHelperMultiplier).toFloat())
        for (i in targets.indices.reversed()) {
            val f = targets[i]
            val p = lineProgress[i] + gain
            if (p >= 1f) {
                world.helperCatch(this, f)
                f.claimedBy = null
                targets.removeAt(i)
                lineProgress.removeAt(i)
            } else {
                lineProgress[i] = p
            }
        }
    }

    companion object {
        /** 抛竿动画总时长（秒），与渲染层的帧数对应。 */
        const val CAST_ANIM_TIME = 0.55f

        /** 钓上一条鱼所需的时间（秒，未计效率加成）。 */
        const val CATCH_TIME = 2.4f
    }
}

/**
 * 鹈鹕：后期解锁的特殊单位。
 *
 * 和普通钓手的区别在于**它专挑大鱼**：锁定水面下最值钱的那条鱼俯冲下去叼走，
 * 不受稀有度限制。钓手负责稳定产出，鹈鹕负责给你惊喜。
 */
class Pelican(var x: Float) {
    /** 巡航高度（水面上方）。 */
    private val cruiseY = Space.SURFACE_Y - 200f

    /** 当前立绘中心的世界 y。 */
    var y = cruiseY

    var facing = 1f

    /** 扇翅相位，渲染层据此在动画帧之间切换。 */
    var flap = Random.nextFloat() * 6.2832f

    /**
     * 俯冲进度（秒），< 0 表示不在俯冲。
     * 俯冲期间会从巡航高度一路扎到鱼身上。
     */
    var dive = -1f
        private set

    private var target: Fish? = null
    private var cooldown = Random.nextFloat() * 2f

    /** 是否正在俯冲（渲染层据此加速扇翅、贴水花）。 */
    val diving: Boolean get() = dive >= 0f

    fun update(dt: Float, world: World) {
        flap += dt * (if (diving) 15f else 6.5f)
        if (cooldown > 0f) cooldown -= dt

        // 目标被别人钓走了就换一条
        val t = target
        if (t != null && t.state != FishState.SWIMMING && dive < 0f) {
            t.claimedBy = null
            target = null
        }

        // ---- 俯冲中 ----
        if (dive >= 0f) {
            dive += dt
            val fish = target
            if (fish != null) {
                val p = (dive / DIVE_TIME).coerceIn(0f, 1f)
                y = cruiseY + (fish.y - cruiseY) * p
                x += (fish.x - x) * 0.35f
                if (p > 0.5f) facing = if (fish.x >= x) 1f else -1f
            }
            if (dive >= DIVE_TIME) {
                if (fish != null && fish.state == FishState.SWIMMING) world.pelicanCatch(this, fish)
                fish?.claimedBy = null
                target = null
                dive = -1f
                cooldown = CATCH_COOLDOWN
            }
            return
        }

        // ---- 巡航 ----
        y = cruiseY + sin(flap * 0.5f) * 24f
        if (cooldown > 0f) return

        if (target == null) target = world.claimTargetForPelican()
        val fish = target
        if (fish == null) {
            // 没鱼可叼就来回巡游，别杵在原地
            x += facing * CRUISE_SPEED * dt
            if (x <= Space.POND_L) { x = Space.POND_L; facing = 1f }
            if (x >= Space.POND_R) { x = Space.POND_R; facing = -1f }
            return
        }

        val dx = fish.x - x
        if (abs(dx) > 14f) {
            x += (if (dx > 0f) 1f else -1f) * FLY_SPEED * dt
            facing = if (dx > 0f) 1f else -1f
        } else {
            dive = 0f // 到鱼头顶了，扎下去
        }
    }

    companion object {
        /** 一次俯冲用时（秒）。 */
        const val DIVE_TIME = 0.55f

        /** 叼完一条后歇多久（秒）。 */
        const val CATCH_COOLDOWN = 5.5f

        /** 巡航飞行速度（世界单位/秒）。 */
        const val FLY_SPEED = 330f

        /** 没目标时的闲逛速度。 */
        const val CRUISE_SPEED = 90f
    }
}

/**
 * 潜水员：定期潜到水底捞一颗珍珠上来。
 *
 * 它不产金币 —— 产的是**跨轮次的资产**（珍珠可以带进转生），
 * 所以周期故意拉得长（[DIVE_CYCLE] 秒），而且真的会潜下去再浮上来，让玩家看得见。
 */
class Diver {
    /** 下潜深度 0（水面）~ 1（水底）。 */
    var depth = 0f
        private set

    var facing = 1f

    /** 本次下潜是否已经结算过珍珠，避免一个周期发两次。 */
    private var rewarded = true

    /** 本次下潜是否已经播过入水声。 */
    private var diveAnnounced = false
    private var phase = 0.85f

    fun update(dt: Float, world: World) {
        phase += dt / world.diverCycle()
        if (phase >= 1f) {
            phase -= 1f
            rewarded = false
        }
        depth = when {
            phase < 0.22f -> phase / 0.22f                 // 下潜
            phase < 0.38f -> 1f                            // 在底部摸珍珠
            phase < 0.60f -> 1f - (phase - 0.38f) / 0.22f  // 上浮
            else -> 0f                                     // 水面休息
        }
        if (!rewarded && phase >= 0.38f) {
            rewarded = true
            world.diverSurfacePearl()
        }
        // 刚下水的那一帧给个入水声（一个周期只响一次）
        if (!diveAnnounced && phase < 0.22f) {
            diveAnnounced = true
            world.pendingSounds.add("diver")
        } else if (phase >= 0.60f) {
            diveAnnounced = false
        }
    }

    companion object {
        /** 一个完整下潜周期（秒）。 */
        const val DIVE_CYCLE = 180f
    }
}

/**
 * 鱼王：时不时现身的大鱼，**靠连点**把它拉上来。
 *
 * 和普通钓鱼区分开：普通钓鱼是"抛竿 → 等咬钩 → 点收线"，
 * 鱼王是自己游过来、要玩家一直戳它，拉力条满了才算拽上岸。
 * 点得慢了它会跑（到期就溜）。
 */
class FishKing(var x: Float, var y: Float) {
    var life = LIFE
        private set

    /** 拉力进度 0..1。 */
    var progress = 0f

    var bob = 0f
    var dir = 1f
    var vx = 0f

    /** 被拽上来了。 */
    var caught = false

    /** 溜掉了。 */
    var escaped = false

    val alive: Boolean get() = life > 0f && !caught && !escaped
    val done: Boolean get() = !alive

    fun update(dt: Float) {
        if (!alive) return
        life -= dt
        bob += dt
        x += vx * dt
        if (x < Space.POND_L + 120f) { x = Space.POND_L + 120f; vx = -vx; dir = 1f }
        if (x > Space.POND_R - 120f) { x = Space.POND_R - 120f; vx = -vx; dir = -1f }
        // 拉力会慢慢回退：不连点就拉不上来
        progress = (progress - dt * PROGRESS_DECAY).coerceAtLeast(0f)
        if (life <= 0f) escaped = true
    }

    companion object {
        /** 鱼王在河面停留多久（秒）。 */
        const val LIFE = 22f

        /** 点击判定的半径（世界单位），大鱼给宽一点。 */
        const val TAP_RADIUS = 190f

        /** 点一下加多少拉力。 */
        const val TAP_POWER = 0.075f

        /** 拉力每秒回退多少。 */
        const val PROGRESS_DECAY = 0.16f
    }
}

/** 河面浮出的沉船宝箱：点开得一大笔金币，小概率开出珍珠。 */
class Chest(var x: Float, var y: Float) {
    /** 剩余存在时间（秒）。 */
    var life = LIFE
        private set

    /** 上下浮动相位。 */
    var bob = 0f

    val alive: Boolean get() = life > 0f

    fun update(dt: Float) {
        life -= dt
        bob += dt
    }

    companion object {
        /** 宝箱浮在水面停留多久（秒）。 */
        const val LIFE = 16f

        /** 点击判定的半径（世界单位）。 */
        const val TAP_RADIUS = 150f
    }
}

/** 游戏世界：持有所有实体并推进模拟。 */
class World(val gameState: GameState) {

    val fishes = mutableListOf<Fish>()
    val helpers = mutableListOf<Helper>()

    /** 后期单位：鹈鹕（买了才有）。 */
    val pelicans = mutableListOf<Pelican>()

    /** 后期单位：潜水员（买了才有）。 */
    var diver: Diver? = null
        private set

    /** 河面浮出的沉船宝箱（点了才有收成）。 */
    var chest: Chest? = null
        private set

    /** 距离下一次浮出宝箱还有多久（秒）。 */
    private var chestTimer = CHEST_INTERVAL

    /** 现身的鱼王（没现身时为 null）。 */
    var king: FishKing? = null
        private set

    /** 距离下一次鱼王现身还有多久（秒）。 */
    private var kingTimer = KING_INTERVAL

    /** 拖网倒计时（秒）。 */
    private var netTimer = NET_INTERVAL

    /** 撒网特效剩余时间（秒），< 0 表示没有特效。 */
    var netEffect = -1f
        private set
    var netEffectX = 0f
        private set
    var netEffectY = 0f
        private set
    var netEffectCount = 0
        private set
    val floatingTexts = mutableListOf<FloatingText>()
    val particles = mutableListOf<Particle>()
    val bobber = Bobber()

    /**
     * 环境气泡。让水下看起来一直在动，而不是一张静止的贴图。
     * 数量按世界宽度铺开，位置随机但只在初始化时生成一次。
     */
    val bubbles = mutableListOf<Bubble>()

    init {
        spawnAmbientBubbles()
    }

    private fun spawnAmbientBubbles() {
        val count = (Space.W / 130f).toInt()
        repeat(count) {
            bubbles.add(
                Bubble(
                    x = Random.nextFloat() * Space.W,
                    y = Random.nextFloat() * (Space.POND_B - Space.POND_T) + Space.POND_T,
                    radius = 2.5f + Random.nextFloat() * 5.5f,
                    speed = 18f + Random.nextFloat() * 34f,
                    phase = Random.nextFloat() * 6.2832f,
                )
            )
        }
    }

    /** 本帧待播放的音效事件，由 UI 层消费。 */
    val pendingSounds = mutableListOf<String>()

    var time = 0f

    /** 镜头横向位置（世界坐标）。玩家的船跟随镜头。 */
    var cameraX = Space.W / 2f
        private set

    /** 当前视野宽度（世界单位），由渲染层回填。 */
    var viewHalfWidth = 500f

    /** 收线期间累计的点击加力，每帧清零。 */
    private var tapsThisFrame = 0
    var hoverFish: Fish? = null
    var autoReelCooldown = 0f
    var pendingAutoRecast = false

    /** 空闲巡航的目的地（世界 x）；为 null 表示不巡航。 */
    private var cruiseX: Float? = null

    /**
     * 当前钓场地图。**必须在 init 之前声明** —— init 里的 syncFishCount()
     * 会读它来抽鱼种，声明在 init 之后的话此刻还是 null。
     */
    var currentMap: FishingMap = gameState.currentMap

    init {
        syncFishCount()
        syncHelperCount()
        syncSpecialUnits()
    }

    // ---------------- 镜头 ----------------

    fun scrollCamera(dx: Float) {
        val half = viewHalfWidth
        val minX = half
        val maxX = (Space.W - half).coerceAtLeast(half)
        cameraX = (cameraX + dx).coerceIn(minX, maxX)
    }

    /**
     * 玩家船的水平位置。**独立于镜头** —— 玩家可以用左右按钮
     * 把船划到河面任意位置，镜头只在船靠近边缘时跟随。
     */
    var boatX = Space.W / 2f
        private set

    /**
     * 玩家船的朝向：1 = 朝右（立绘原始方向），-1 = 朝左。
     * 按左右键划船时翻转立绘，竿尖与鱼线随之镜像。
     */
    var boatFacing = 1f
        private set

    /**
     * 玩家抛竿动画计时（秒）。< 0 表示不播放。
     * [castLine] 时归零，渲染层据此播放甩竿的逐帧动画。
     */
    var castAnim = -1f
        private set

    /** 船的移动速度（世界单位/秒）。 */
    private val boatSpeed = 520f

    /**
     * 玩家当前是否在按左/右键（由 UI 按钮设置）。
     * 命名带 is 前缀，避免 Kotlin 自动生成的 setMoveLeft/setMoveRight
     * 与下面的控制方法在 JVM 层撞签名。
     */
    private var isMovingLeft = false
    private var isMovingRight = false

    /**
     * 划船。
     *
     * 关键：镜头**只在船快要划出屏幕时才跟**。
     * 如果每帧都让镜头居中到船上，船就会永远钉在屏幕中央，
     * 看起来像"整片水面在动"而不是"船在动"。
     */
    fun moveBoat(dt: Float) {
        var dir = 0f
        if (isMovingLeft) dir -= 1f
        if (isMovingRight) dir += 1f
        if (dir == 0f) return
        boatFacing = dir
        boatX = (boatX + dir * boatSpeed * dt).coerceIn(boatMinX(), boatMaxX())
        keepBoatOnScreen()
    }

    /**
     * 船与屏幕边缘的留白（世界单位）。
     *
     * 占视野半宽的三成 —— 镜头跟得紧一点：船一往外走就把镜头带过去，
     * 而不是等船贴到屏幕边才开始推。
     */
    private fun followMargin(): Float = (viewHalfWidth * BOAT_EDGE_RATIO).coerceAtLeast(60f)

    /**
     * 船能划到的最左 / 最右位置。
     *
     * 镜头最多只能推到 `[half, W - half]`（再往外就露出世界外面的空白了），
     * 所以船也不能越过"镜头推到头时还能完整看到"的位置 ——
     * 否则船会顶在河的两端半条出画，玩起来就是"镜头不跟了、边上也过不去"。
     */
    private fun boatMinX(): Float = maxOf(Space.POND_L, followMargin())

    private fun boatMaxX(): Float = minOf(Space.POND_R, Space.W - followMargin())

    /**
     * 让船保持在视野内：船接近屏幕边缘时推动镜头。
     * [margin] 是船距屏幕边缘的留白（世界单位）。
     */
    private fun keepBoatOnScreen(margin: Float = followMargin()) {
        val half = viewHalfWidth
        val leftEdge = cameraX - half + margin
        val rightEdge = cameraX + half - margin
        if (boatX < leftEdge) {
            cameraX -= (leftEdge - boatX)
        } else if (boatX > rightEdge) {
            cameraX += (boatX - rightEdge)
        }
        val minX = half
        val maxX = (Space.W - half).coerceAtLeast(half)
        cameraX = cameraX.coerceIn(minX, maxX)
    }

    /** 船此刻在不在动（玩家按键或挂机巡航；渲染层据此放大水花、拖出尾迹）。 */
    fun isBoatMoving(): Boolean = isMovingLeft || isMovingRight || cruiseX != null

    fun setMoveLeft(pressed: Boolean) {
        isMovingLeft = pressed
    }

    fun setMoveRight(pressed: Boolean) {
        isMovingRight = pressed
    }

    /** 松开所有方向键（面板打开、切后台时调用，避免船一直漂）。 */
    fun stopMoving() {
        isMovingLeft = false
        isMovingRight = false
    }

    /** 点击水面把船划过去（点哪走哪）。 */
    fun sailTo(targetX: Float) {
        val clamped = targetX.coerceIn(boatMinX(), boatMaxX())
        if (abs(clamped - boatX) > 2f) boatFacing = if (clamped > boatX) 1f else -1f
        boatX = clamped
        keepBoatOnScreen()
    }

    // ---------------- 数量同步 ----------------

    private fun countOf(rarity: Rarity) = fishes.count { it.kind == rarity }

    /**
     * 让鱼群数量与 GameState 一致（多退少补）。
     *
     * 新鱼从**当前地图的鱼种池**里按权重抽取，所以换地图后
     * 钓场里游的就都是那片水域该有的鱼。
     */
    fun syncFishCount() {
        for (rarity in Rarity.entries) {
            val want = gameState.ownedCount(rarity)
            val have = countOf(rarity)
            if (have < want) {
                repeat(want - have) {
                    val species = rollSpecies(rarity) ?: return@repeat
                    fishes.add(
                        Fish(
                            species,
                            Random.nextFloat() * (Space.POND_R - Space.POND_L) + Space.POND_L,
                            Random.nextFloat() * (Space.POND_B - Space.POND_T) + Space.POND_T,
                        )
                    )
                }
            } else if (have > want) {
                var toRemove = have - want
                val iter = fishes.iterator()
                while (iter.hasNext() && toRemove > 0) {
                    val f = iter.next()
                    if (f.kind == rarity && f.state == FishState.SWIMMING) {
                        iter.remove(); toRemove--
                    }
                }
            }
        }
    }

    /**
     * 在当前地图里按稀有度抽一个鱼种。
     *
     * 两个加成共同作用：
     * - 技能「深渊直觉」提高稀有档位的**出现频率**
     * - 升级「幸运鱼钩」让同档内更容易抽到高价值的那几种
     */
    private fun rollSpecies(rarity: Rarity): Species? {
        val pool = currentMap.species.filter { it.rarity == rarity }
        if (pool.isEmpty()) return null

        val luck = gameState.luckyHook
        if (pool.size == 1) return pool.first()

        // 幸运鱼钩：按该鱼种自身的价值倍率加权，越值钱的越容易抽到
        val weighted = pool.flatMap { sp ->
            val w = if (luck > 0.0) {
                (1.0 + luck * sp.valueMul * rarity.ordinal).toInt().coerceAtLeast(1)
            } else {
                1
            }
            List(w) { sp }
        }
        return weighted.random()
    }

    /** 切换地图：清空现有鱼群，按新地图重新铺满。 */
    fun switchMap(map: FishingMap) {
        currentMap = map
        fishes.clear()
        syncFishCount()
    }

    /** 后期单位跟着购买状态走（买了鹈鹕 / 潜水员就放出来）。 */
    fun syncSpecialUnits() {
        if (gameState.pelicanOwned) {
            if (pelicans.isEmpty()) pelicans.add(Pelican(Space.W / 2f))
        } else {
            pelicans.clear()
        }
        if (gameState.diverOwned) {
            if (diver == null) diver = Diver()
        } else {
            diver = null
        }
        if (!gameState.treasureOwned) chest = null
    }

    fun syncHelperCount() {
        val want = gameState.helpers
        while (helpers.size < want) {
            // 沿河面均匀铺开，避免全挤在一处
            val slot = helpers.size
            val spread = (Space.POND_R - Space.POND_L)
            val x = Space.POND_L + (spread * ((slot * 0.618f) % 1f))
            helpers.add(Helper(x))
        }
        while (helpers.size > want) helpers.removeAt(helpers.size - 1)
    }

    // ---------------- 玩家操作 ----------------

    /** 玩家竿尖的世界坐标（浮标起飞点）。 */
    fun rodTipX(): Float {
        val (dx, _) = BoatArt.rodTipOffset(boatFacing)
        return boatX + dx
    }

    fun rodTipY(): Float {
        val (_, dy) = BoatArt.rodTipOffset(boatFacing)
        return Space.BOAT_WATERLINE + dy
    }

    /** 落点附近 [Space.MAX_BITE_RANGE] 内是否有可钓的鱼。 */
    fun hasFishNear(x: Float, y: Float): Boolean = fishes.any {
        it.state == FishState.SWIMMING && hypot(it.x - x, it.y - y) <= Space.MAX_BITE_RANGE
    }

    /** 玩家抛到没鱼的地方时给个反馈，而不是静默什么都不发生。 */
    fun notifyNoFish(x: Float, y: Float) {
        spawnText(x, y, "这里没有鱼", Palette.TEXT_BAD, 0.9f)
    }

    /**
     * 手动抛竿。
     *
     * 只有落点附近 [Space.MAX_BITE_RANGE] 内**有鱼**时才会有鱼来咬钩，
     * 否则空竿收回 —— 这样"抛到鱼群边上"才有意义。
     */
    fun castLine(tx: Float, ty: Float): Boolean {
        if (bobber.isActive) return false
        // 鱼探仪：落点自动吸附到附近最近的那条鱼 —— 手抖也不会空竿
        val snapped = if (gameState.fishFinderOwned) {
            fishes.asSequence()
                .filter { it.state == FishState.SWIMMING }
                .filter { hypot(it.x - tx, it.y - ty) <= FINDER_SNAP_RADIUS }
                .minByOrNull { hypot(it.x - tx, it.y - ty) }
        } else {
            null
        }
        val cx = (snapped?.x ?: tx).coerceIn(Space.POND_L, Space.POND_R)
        val cy = (snapped?.y ?: ty).coerceIn(Space.POND_T, Space.POND_B)

        // 挑落点附近的空闲鱼，太远的够不着。
        // 技能「深渊直觉」（skillRareWeightBonus）让稀有鱼更抢食：
        // 稀有度越高，等效距离越近，于是更容易选中它。
        val fish = fishes
            .asSequence()
            .filter { it.state == FishState.SWIMMING }
            .filter { hypot(it.x - cx, it.y - cy) <= Space.MAX_BITE_RANGE }
            .minByOrNull {
                hypot(it.x - cx, it.y - cy) /
                    (1.0 + gameState.skillRareWeightBonus * it.kind.ordinal).toFloat()
            }

        fish?.let {
            it.setTarget(cx, cy)
            it.state = FishState.APPROACHING
        }

        // 浮标从**竿尖**飞出去，而不是从船肚子——线必须接在竿尖上
        bobber.cast(rodTipX(), rodTipY(), cx, cy)
        bobber.hookedFish = fish
        castAnim = 0f
        if (fish != null) {
            val range = Content.biteDelay(fish.kind)
            val wait = range.start + Random.nextFloat() * (range.endInclusive - range.start)
            // 声呐：稀有鱼更早咬钩
            bobber.biteTimer = wait / sonarBiteSpeed(fish.kind)
        }
        pendingSounds.add("cast")
        // 声呐：稀有及以上的鱼咬钩时来一声探测音
        if (fish != null && gameState.sonarOwned && fish.kind != Rarity.COMMON) {
            pendingSounds.add("sonar")
        }
        return true
    }

    /** 潜水员一个完整下潜周期的实际秒数（技能「深海打捞」会缩短）。 */
    internal fun diverCycle(): Float =
        (Diver.DIVE_CYCLE * (1.0 - gameState.skillDiverSpeed)).toFloat().coerceAtLeast(20f)

    /** 声呐对咬钩速度的加成：只对稀有及以上的鱼生效。 */
    internal fun sonarBiteSpeed(kind: Rarity): Float =
        if (gameState.sonarOwned && kind != Rarity.COMMON) SONAR_BITE_SPEED else 1f

    /**
     * 把已钓上来的鱼"放回"水里。
     * 鱼群是钓场的常驻居民，数量由 GameState 决定；被钓起只是播放一段上岸动画，
     * 随后重新入水，这样玩家的钓场规模不会被慢慢掏空。
     */
    private fun returnToPond(f: Fish) {
        f.state = FishState.SWIMMING
        f.claimedBy = null
        f.x = Random.nextFloat() * (Space.POND_R - Space.POND_L) + Space.POND_L
        f.y = Random.nextFloat() * (Space.POND_B - Space.POND_T) * 0.8f + Space.POND_T
        val ang = Random.nextFloat() * 6.2832f
        val speed = Content.swimSpeed(f.kind)
        f.vx = cos(ang) * speed
        f.vy = sin(ang) * speed * 0.55f
    }

    /**
     * 点击屏幕推进钓鱼流程。
     *
     * 咬钩/收线阶段**不再要求点中浮标**：手机上浮标只有几十像素，
     * 要求精确点击会让玩家频繁失手，误以为游戏坏了。
     * 只要浮标处于活动状态，点哪里都算数。
     */
    fun onTap(wx: Float, wy: Float): Boolean {
        // 鱼王最优先：它只存在十几秒，点到就该算数
        king?.let { k ->
            if (k.alive && hypot(wx - k.x, wy - k.y) <= FishKing.TAP_RADIUS) {
                return tapKing(k)
            }
        }
        // 宝箱其次：点到了就直接开箱（它比浮标大，也不跟收线抢操作）
        val c = chest
        if (c != null && hypot(wx - c.x, wy - c.y) <= Chest.TAP_RADIUS) {
            openChest(c)
            chest = null
            return true
        }
        if (!bobber.isActive) return false
        when (bobber.state) {
            BobberState.BITE, BobberState.REELING -> {
                if (bobber.onTap()) {
                    tapsThisFrame++
                    if (bobber.state == BobberState.REELING) pendingSounds.add("reel")
                    return true
                }
            }
            else -> Unit
        }
        return false
    }

    // ---------------- 钓手 ----------------

    /**
     * 为指定钓手认领 [count] 条鱼：优先近的、可钓的、没被其他钓手预定的。
     * 「并行作业」升级后一次可以认领多条。
     */
    fun claimTargetsForHelper(helper: Helper, count: Int): List<Fish> {
        if (count <= 0) return emptyList()
        val reachable = fishes.asSequence()
            .filter { f ->
                f.state == FishState.SWIMMING && f.claimedBy == null &&
                    helperCatchable(f.kind)
            }
            .sortedBy { hypot(it.x - helper.x, it.y - helper.y) }
            .take(count)
            .toList()
        reachable.forEach { it.claimedBy = helper }
        return reachable
    }

    /** 钓手当前是否能钓这个稀有度（由「钓手进阶」类升级解锁）。 */
    private fun helperCatchable(kind: Rarity): Boolean = when (kind) {
        Rarity.COMMON -> true
        Rarity.RARE -> gameState.helperCanRare
        Rarity.EPIC -> gameState.helperCanEpic
        Rarity.LEGEND -> gameState.helperCanLegend
    }

    /**
     * 潜水员浮上来交货：一颗珍珠。
     *
     * 珍珠是转生货币（能带进下一轮），所以这里是后期唯一"不产金币"的产出来源 ——
     * 它让挂机收益之外多了一条"挂久了能换长期强度"的路。
     */
    fun diverSurfacePearl() {
        gameState.pearls += 1
        spawnText(boatX, Space.BOAT_WATERLINE - 150f, "珍珠 +1", Palette.TEXT_GOLD, 1.25f)
        spawnCoinBurst(boatX, Space.BOAT_WATERLINE - 60f)
        pendingSounds.add("pearl")
    }

    /**
     * 沉船宝箱：隔一段时间在船附近浮出一个，点在它身上才能开。
     *
     * 这是河里唯一的"要动手"的后期内容 —— 挂机挂久了也有个值得看一眼的理由。
     */
    private fun updateChest(dt: Float) {
        val c = chest
        if (c != null) {
            c.update(dt)
            if (!c.alive) chest = null
        }
        if (!gameState.treasureOwned) return

        chestTimer -= dt
        if (chest == null && chestTimer <= 0f) {
            chestTimer = CHEST_INTERVAL * chestIntervalScale()
            val x = (boatX + (Random.nextFloat() * 2f - 1f) * CHEST_SPAWN_SPREAD)
                .coerceIn(Space.POND_L + 60f, Space.POND_R - 60f)
            val y = Space.POND_T + 120f + Random.nextFloat() * 380f
            chest = Chest(x, y)
            spawnText(x, y - 70f, "浮出宝箱！点它", Palette.TEXT_GOLD, 1.15f)
            spawnSplash(x, y, Rarity.EPIC)
            pendingSounds.add("bite")
        }
    }

    /**
     * 鱼王：声呐解锁后才有机会撞见（先用声呐找到它，才谈得上钓它）。
     * 每隔 [KING_INTERVAL] 秒现身一次，在河面上慢慢游，等玩家连点。
     */
    private fun updateKing(dt: Float) {
        val k = king
        if (k != null) {
            k.update(dt)
            if (k.done) {
                if (k.escaped) {
                    spawnText(k!!.x, k!!.y - 40f, "鱼王跑了…", Palette.TEXT_BAD, 1.4f)
                    pendingSounds.add("fail")
                }
                king = null
            }
        }
        if (!gameState.sonarOwned) return

        kingTimer -= dt
        if (king == null && kingTimer <= 0f) {
            kingTimer = KING_INTERVAL
            val x = (boatX + (Random.nextFloat() * 2f - 1f) * 500f)
                .coerceIn(Space.POND_L + 150f, Space.POND_R - 150f)
            val y = Space.POND_T + 200f + Random.nextFloat() * 320f
            king = FishKing(x, y).apply {
                vx = (Random.nextFloat() * 2f - 1f) * 90f
                dir = if (vx >= 0f) 1f else -1f
            }
            spawnText(x, y - 90f, "鱼王现身！连点它", Palette.TEXT_GOLD, 1.6f)
            spawnSplash(x, y, Rarity.LEGEND)
            pendingSounds.add("legend")
        }
    }

    /** 点鱼王：加拉力，满了就拽上岸。返回是否点中了。 */
    private fun tapKing(k: FishKing): Boolean {
        k.progress = (k.progress + FishKing.TAP_POWER * kingTapScale()).coerceAtMost(1f)
        if (k.progress < 1f) return true

        k.caught = true
        gameState.kingsCaught++
        gameState.dailyProgress.kings++
        val value = gameState.catchValue(Rarity.LEGEND) * KING_VALUE_MULT * kingRewardScale()
        gameState.money += value
        val pearls = KING_PEARLS
        gameState.pearls += pearls

        spawnText(k.x, k.y - 50f, "+${formatNumber(value)}", Palette.TEXT_GOLD, 1.8f)
        spawnText(k.x, k.y - 120f, "珍珠 +$pearls", Palette.TEXT_GOLD, 1.5f)
        spawnCoinBurst(k.x, k.y)
        spawnSplash(k.x, k.y, Rarity.LEGEND)
        pendingSounds.add("legend")
        pendingSounds.add("pearl")
        king = null
        return true
    }

    /** 技能「鱼王克星」：点一下的拉力倍率。 */
    private fun kingTapScale(): Float = (1.0 + gameState.skillKingPower).toFloat()

    /** 技能「鱼王克星」：鱼王奖励倍率。 */
    private fun kingRewardScale(): Double = 1.0 + gameState.skillKingPower * 1.5

    /** 点开宝箱：一大笔金币（按当前鱼价折算），小概率再开出一颗珍珠。 */
    private fun openChest(c: Chest) {
        gameState.chestsOpened++
        gameState.dailyProgress.chests++
        val value = gameState.catchValue(Rarity.COMMON) * CHEST_VALUE_MULT *
            (1.0 + gameState.skillChestValue)
        // 开箱算"打捞收入"，不计进渔获数/连击 —— 宝箱不该刷连击成就
        gameState.money += value
        gameState.recordEarning(Rarity.EPIC, Source.TREASURE, value)

        spawnText(c.x, c.y - 40f, "+${formatNumber(value)}", Palette.TEXT_GOLD, 1.5f)
        spawnCoinBurst(c.x, c.y)
        spawnSplash(c.x, c.y, Rarity.LEGEND)
        pendingSounds.add("chest")

        if (Random.nextFloat() < CHEST_PEARL_CHANCE) {
            gameState.pearls += 1
            spawnText(c.x, c.y - 110f, "珍珠 +1", Palette.TEXT_GOLD, 1.3f)
            pendingSounds.add("pearl")
        }
        val newly = Achievements.checkUnlocks(gameState)
        if (newly.isNotEmpty()) pendingAchievements.addAll(newly)
    }

    /**
     * 鹈鹕挑目标：整条河里**最值钱**的那条空闲鱼。
     *
     * 不按稀有度设门槛 —— 它就是用来叼大鱼的，这也是它贵的原因。
     */
    fun claimTargetForPelican(): Fish? {
        val pick = fishes.asSequence()
            .filter { it.state == FishState.SWIMMING }
            .filter { it.claimedBy == null }
            .maxByOrNull { gameState.catchValue(it.species, currentMap) }
            ?: return null
        pick.claimedBy = BIRDS_CLAIM
        return pick
    }

    /** 鹈鹕把鱼叼走：立即结算，随后鱼回到水里。 */
    fun pelicanCatch(pelican: Pelican, fish: Fish) {
        val value = gameState.catchValue(fish.species, currentMap)
        pendingSounds.add("pelican")
        award(fish, value, Source.PELICAN, fish.x, fish.y)
        spawnSplash(fish.x, fish.y, fish.kind)
        returnToPond(fish)
    }

    /**
     * 拖网：每 [NET_INTERVAL] 秒把船附近最靠前的几条鱼一网打尽。
     *
     * 这是"后期爽点"：攒够钱买下去，收成从一条一条变成一网一网。
     */
    private fun updateNetSweep(dt: Float) {
        if (netEffect >= 0f) netEffect -= dt
        if (!gameState.netOwned) return

        netTimer -= dt
        if (netTimer > 0f) return
        netTimer = NET_INTERVAL

        val targets = fishes.asSequence()
            .filter { it.state == FishState.SWIMMING }
            .filter { hypot(it.x - boatX, it.y - Space.BOAT_WATERLINE) <= NET_RADIUS }
            .sortedBy { hypot(it.x - boatX, it.y - Space.BOAT_WATERLINE) }
            .take(NET_MAX_FISH)
            .toList()
        if (targets.isEmpty()) return

        netEffect = NET_EFFECT_TIME
        netEffectX = targets.sumOf { it.x.toDouble() }.toFloat() / targets.size
        netEffectY = targets.sumOf { it.y.toDouble() }.toFloat() / targets.size
        netEffectCount = targets.size

        for (f in targets) {
            val value = gameState.catchValue(f.species, currentMap)
            award(f, value, Source.NET, f.x, f.y)
            spawnSplash(f.x, f.y, f.kind)
            f.claimedBy = null
            returnToPond(f)
        }
        pendingSounds.add("net")
    }

    /** 钓手钓上一条鱼，立即结算，随后鱼回到水里。 */
    fun helperCatch(helper: Helper, fish: Fish) {
        val value = gameState.catchValue(fish.species, currentMap)
        award(fish, value, Source.HELPER, fish.x, fish.y)
        spawnSplash(fish.x, fish.y, fish.kind)
        returnToPond(fish)
    }

    // ---------------- 主循环 ----------------

    fun update(dt: Float) {
        time += dt

        // 玩家划船
        moveBoat(dt)

        // 抛竿帧动画推进（播完自动回到静止立绘）
        if (castAnim >= 0f) {
            castAnim += dt
            if (castAnim > Helper.CAST_ANIM_TIME) castAnim = -1f
        }

        for (f in fishes) f.update(dt)

        val ev = bobber.update(dt, currentReelSpeed(), tapsThisFrame)
        tapsThisFrame = 0
        ev?.let { handleBobberEvent(it) }

        // 自动收线：浮标一下沉就替玩家把线收起来。
        // 之前这里只做了"悬停自动抛竿"，手机上没有悬停这回事，
        // 玩家买了升级还是得每竿手动点一下。
        if (gameState.autoReelUnlocked && bobber.state == BobberState.BITE) {
            if (bobber.onTap()) pendingSounds.add("reel")
        }

        // 清理失效预定：目标已不再空闲时释放，让钓手重新选目标
        fishes.forEach { if (it.claimedBy != null && it.state != FishState.SWIMMING) it.claimedBy = null }

        for (h in helpers) h.update(dt, gameState, this)
        for (p in pelicans) p.update(dt, this)
        diver?.update(dt, this)
        updateNetSweep(dt)
        updateChest(dt)
        updateKing(dt)

        // 自动抛竿（智能浮标）与自动重抛（自动重抛升级）：
        // 手上没竿、冷却也过了，就自己找条鱼下竿。
        // 这两条路以前分散在 View 层和这里各写一半，现在统一在 World 里推进。
        autoReelCooldown -= dt
        if (bobber.isActive) {
            pendingAutoRecast = false
        } else if (autoReelCooldown <= 0f) {
            val byUpgrade = gameState.autoCastUnlocked
            if (byUpgrade || pendingAutoRecast) {
                pendingAutoRecast = false
                if (autoCastOnce()) {
                    autoReelCooldown = autoRecastDelay(
                        if (byUpgrade) AUTO_CAST_INTERVAL * autoCastIntervalScale()
                        else AUTO_RECAST_INTERVAL,
                    )
                }
            }
        }

        // 空闲巡航：挂机时船边没鱼就自己开过去，别干等着
        cruiseToFish(dt)

        // 清理已完成的鱼（游出画面的）
        fishes.removeAll { it.state == FishState.CAUGHT && it.y < Space.POND_T - 200f }

        for (t in floatingTexts) t.update(dt)
        floatingTexts.removeAll { it.dead }
        for (p in particles) p.update(dt)
        particles.removeAll { it.dead }
        for (b in bubbles) b.update(dt, time)
    }

    /**
     * 自动重抛 / 自动抛竿的间隔。
     * 「自动绞盘」升级（[GameState.autoReelSpeed]）会把它缩短 —— 这个升级
     * 以前只写在商店描述里、实际没接线，现在真的生效。
     */
    private fun autoRecastDelay(base: Float): Float =
        (base / (1.0 + gameState.autoReelSpeed).toFloat()).coerceAtLeast(0.05f)

    /**
     * 自动抛竿一竿：优先抛向玩家点/悬停的那条鱼（桌面端的悬停手感），
     * 否则自己挑一条船附近有鱼的鱼。返回是否真的抛了出去。
     */
    fun autoCastOnce(): Boolean {
        if (bobber.isActive) return false
        // 悬停/点中的那条鱼只在船附近时才认 —— 手机上 hoverFish 会一直留着，
        // 不然自动抛竿会永远盯着很久以前点过的那条鱼
        val hovered = hoverFish?.takeIf {
            it.state == FishState.SWIMMING && abs(it.x - boatX) <= autoCastRange()
        }
        val target = hovered ?: pickAutoCastTarget() ?: return false
        return castLine(target.x, target.y)
    }

    /**
     * 空闲巡航：买了智能浮标、手上没竿、船附近又没鱼时，慢慢把船划过去。
     *
     * 自动抛竿只在船附近找鱼（[AUTO_CAST_RANGE]），而鱼是散布在整条河里的 ——
     * 不巡航的话，挂机会停在没鱼的河段上一直空转，看起来像卡死。
     */
    private fun cruiseToFish(dt: Float) {
        // 玩家自己在划就让他划（这里不能查 isBoatMoving —— 巡航本身也算"在动"）
        if (!gameState.autoCastUnlocked || bobber.isActive || isMovingLeft || isMovingRight) {
            cruiseX = null
            return
        }
        if (pickAutoCastTarget() != null) {
            cruiseX = null
            return
        }
        val target = fishes.asSequence()
            .filter { it.state == FishState.SWIMMING }
            .minByOrNull { abs(it.x - boatX) } ?: return
        cruiseX = target.x
        val d = target.x - boatX
        if (abs(d) < 24f) {
            cruiseX = null
            return
        }
        val dir = if (d > 0f) 1f else -1f
        boatFacing = dir
        boatX = (boatX + dir * boatSpeed * CRUISE_SPEED_RATIO * dt).coerceIn(boatMinX(), boatMaxX())
        keepBoatOnScreen()
    }

    /** 自动抛竿的范围（无人机买了之后看得更远）。 */
    internal fun autoCastRange(): Float =
        AUTO_CAST_RANGE * (if (gameState.droneOwned) DRONE_RANGE_MULT else 1f)

    /** 自动抛竿的间隔倍率（无人机 + 技能「无人机编队」）。 */
    internal fun autoCastIntervalScale(): Float {
        val drone = if (gameState.droneOwned) DRONE_INTERVAL_MULT else 1f
        return (drone * (1f - gameState.skillDroneSpeed).toFloat()).coerceAtLeast(0.15f)
    }

    /** 宝箱间隔倍率（技能「寻宝达人」）。 */
    private fun chestIntervalScale(): Float =
        (1f - gameState.skillChestSpeed).toFloat().coerceAtLeast(0.2f)

    /** 自动抛竿的目标：船附近几条最近的鱼里随机挑一条，免得每次都钓同一条。 */
    private fun pickAutoCastTarget(): Fish? {
        val near = fishes.asSequence()
            .filter { it.state == FishState.SWIMMING }
            .filter { abs(it.x - boatX) <= autoCastRange() }
            .sortedBy { hypot(it.x - boatX, it.y - Space.BOAT_WATERLINE) }
            .take(4)
            .toList()
        if (near.isEmpty()) return null
        return near.random()
    }

    private fun currentReelSpeed(): Double {
        val kind = bobber.hookedFish?.kind ?: Rarity.COMMON
        return gameState.reelSpeed(kind)
    }

    private fun handleBobberEvent(ev: BobberEvent) {
        when (ev) {
            BobberEvent.Landed -> pendingSounds.add("splash")
            BobberEvent.Bite -> pendingSounds.add("bite")

            BobberEvent.Empty -> {
                // 没有鱼咬钩，静默收回，不打断玩家
            }

            BobberEvent.Missed -> {
                val f = bobber.hookedFish
                f?.let {
                    it.state = FishState.ESCAPED
                    it.vx = if (Random.nextBoolean()) 90f else -90f
                    it.vy = 60f
                }
                pendingSounds.add("fail")
                spawnText(bobber.x, bobber.y - 40f, "跑掉了…", Palette.TEXT_BAD, 0.9f)
                if (gameState.combo > 0) {
                    spawnText(bobber.x, bobber.y - 80f, "连击中断", Palette.TEXT_BAD, 0.75f)
                }
                gameState.onCatchFail()
                bobber.hookedFish = null
            }

            BobberEvent.Reeled -> {
                val f = bobber.hookedFish
                if (f != null) {
                    // 鱼的个体逃跑系数（escapeMul）也要算进去，
                    // 否则越大的鱼和普通鱼一样容易上岸，体型差别白做了
                    val chance = Content.escapeChance(f.kind) * f.species.escapeMul *
                        (1.0 - gameState.skillEscapeReduce).toFloat() * 0.35f
                    if (Random.nextFloat() < chance) {
                        f.state = FishState.ESCAPED
                        f.vx = if (Random.nextBoolean()) 110f else -110f
                        f.vy = 70f
                        pendingSounds.add("fail")
                        spawnText(bobber.x, bobber.y - 40f, "线断了！", Palette.TEXT_BAD, 1.0f)
                        gameState.onCatchFail()
                    } else {
                        val value = gameState.catchValue(f.species, currentMap)
                        award(f, value, Source.MANUAL, bobber.x, bobber.y)
                        spawnSplash(bobber.x, bobber.y, f.kind)
                        returnToPond(f)

                        if (gameState.chainReaction) triggerChain(bobber.x, bobber.y, f)
                        if (Random.nextDouble() < gameState.autoReelChance) {
                            autoReelCooldown = autoRecastDelay(0.35f)
                            pendingAutoRecast = true
                        }
                    }
                }
                bobber.hookedFish = null
            }
        }
    }

    /** 连锁反应：惊动周围鱼群，最多 8 条。 */
    private fun triggerChain(x: Float, y: Float, origin: Fish) {
        val nearby = fishes
            .filter { it !== origin && it.state == FishState.SWIMMING && hypot(it.x - x, it.y - y) < 340f }
            .sortedBy { hypot(it.x - x, it.y - y) }
            .take(8)
        for (f in nearby) {
            val value = gameState.catchValue(f.species, currentMap)
            f.state = FishState.CAUGHT
            f.vy = -90f
            f.vx = (f.x - x) * 0.8f
            award(f, value, Source.CHAIN, f.x, f.y)
            spawnSplash(f.x, f.y, f.kind)
        }
    }

    /** 本帧新解锁的成就，供 UI 弹提示。 */
    val pendingAchievements = mutableListOf<AchievementDef>()

    /** 本帧新解锁的鱼种（图鉴解锁弹窗）。 */
    val pendingNewSpecies = mutableListOf<Species>()

    /** 本帧刷新的体型纪录（鱼种 + 新体型）。 */
    val pendingNewRecords = mutableListOf<Pair<Species, FishSize>>()

    companion object {
        /** 自动抛竿两竿之间的间隔（秒，未计「自动绞盘」加速）。 */
        const val AUTO_CAST_INTERVAL = 0.9f

        /** 自动重抛两竿之间的间隔（秒）。 */
        const val AUTO_RECAST_INTERVAL = 0.35f

        /** 自动抛竿只在船附近这个范围内找鱼。 */
        const val AUTO_CAST_RANGE = 620f

        /** 船停在屏幕边缘时保留的留白，占视野半宽的比例。 */
        const val BOAT_EDGE_RATIO = 0.30f

        /** 空闲巡航的速度，占正常划船速度的比例。 */
        const val CRUISE_SPEED_RATIO = 0.55f

        /** 拖网两次撒网之间的间隔（秒）。 */
        const val NET_INTERVAL = 45f

        /** 拖网的覆盖半径（世界单位）。 */
        const val NET_RADIUS = 620f

        /** 一网最多捞几条。 */
        const val NET_MAX_FISH = 4

        /** 撒网特效时长（秒）。 */
        const val NET_EFFECT_TIME = 0.9f

        /** 鹈鹕预定目标用的标记（和钓手的预定共用 claimedBy 字段）。 */
        private val BIRDS_CLAIM = Any()

        /** 声呐对稀有鱼咬钩速度的倍率。 */
        const val SONAR_BITE_SPEED = 1.35f

        /** 鱼探仪把落点吸到鱼身上的最大距离（世界单位）。 */
        const val FINDER_SNAP_RADIUS = 220f

        /** 无人机的自动抛竿范围倍率。 */
        const val DRONE_RANGE_MULT = 1.7f

        /** 无人机的自动抛竿间隔倍率（越小越快）。 */
        const val DRONE_INTERVAL_MULT = 0.65f

        /** 宝箱两次浮出之间的间隔（秒）。 */
        const val CHEST_INTERVAL = 100f

        /** 宝箱离船多远处浮出（世界单位，左右随机）。 */
        const val CHEST_SPAWN_SPREAD = 420f

        /** 宝箱金币 = 当前小鱼价值 × 这个倍数。 */
        const val CHEST_VALUE_MULT = 200.0

        /** 宝箱开出珍珠的概率。 */
        const val CHEST_PEARL_CHANCE = 0.25f

        /** 鱼王两次现身之间的间隔（秒）。 */
        const val KING_INTERVAL = 240f

        /** 鱼王奖励 = 当前巨口鱼价值 × 这个倍数。 */
        const val KING_VALUE_MULT = 60.0

        /** 拽上来一条鱼王给几颗珍珠。 */
        const val KING_PEARLS = 2L
    }

    /**
     * 结算一次渔获：加钱、记录、生成表现。
     *
     * 手动收线才有连击加成与成就推进 —— 自动钓手是挂机收益，不该刷连击。
     */
    private fun award(fish: Fish, value: Double, source: Source, x: Float, y: Float) {
        val manual = source == Source.MANUAL || source == Source.CHAIN
        var finalValue = value

        if (manual) {
            finalValue *= gameState.comboMultiplier
        }

        // 体型加成：大只/巨大/王者 明显更值钱
        finalValue *= fish.size.valueMul

        // 图鉴收集：钓到就记一笔（首次发现要弹中央解锁提示）
        if (gameState.recordSpecies(fish.species.id)) {
            pendingNewSpecies.add(fish.species)
        }
        // 体型纪录：刷新了就弹一次提示
        if (gameState.recordSize(fish.species.id, fish.size)) {
            pendingNewRecords.add(Pair(fish.species, fish.size))
        }

        gameState.money += finalValue
        // 统一记账：累计统计 + 连击 + 每日任务进度。
        // 手动与自动钓手都要计入每日任务，否则挂机就完不成任务。
        gameState.onCatchRecorded(fish.kind, finalValue)
        gameState.recordEarning(fish.kind, source, finalValue)
        gameState.recordCatch(finalValue)

        val typical = gameState.catchValue(Rarity.COMMON).coerceAtLeast(1.0)
        val scale = (finalValue / typical).toFloat().let {
            (0.9f + kotlin.math.ln(1f + it) * 0.22f).coerceIn(0.9f, 2.4f)
        }
        spawnText(x, y - 30f, "+${formatNumber(finalValue)}", Palette.TEXT_GOLD, scale)

        // 连击提示
        if (manual && gameState.combo >= 3) {
            spawnText(
                x, y - 78f,
                "${gameState.combo} 连击  ×${String.format("%.2f", gameState.comboMultiplier)}",
                Palette.TEXT_GOOD, 0.85f,
            )
        }

        spawnCoinBurst(x, y)
        // 稀有收获给更响的反馈：巨口鱼单独一档
        pendingSounds.add(
            when {
                fish.kind == Rarity.LEGEND -> "legend"
                finalValue >= 100 -> "success"
                else -> "coin"
            },
        )

        // 成就检查
        val newly = Achievements.checkUnlocks(gameState)
        if (newly.isNotEmpty()) {
            pendingAchievements.addAll(newly)
            pendingSounds.add("success")
        }
    }

    // ---------------- 表现 ----------------

    fun spawnText(x: Float, y: Float, text: String, color: Int, scale: Float) {
        if (floatingTexts.size > 90) floatingTexts.removeAt(0)
        floatingTexts.add(FloatingText(x, y, text, color, scale))
    }

    fun spawnSplash(x: Float, y: Float, kind: Rarity) {
        val color = when (kind) {
            Rarity.COMMON -> Palette.SPLASH
            Rarity.RARE -> Palette.SPLASH_GREEN
            Rarity.EPIC -> Palette.SPLASH_GOLD
            Rarity.LEGEND -> Palette.SPLASH_PURPLE
        }
        repeat(9) {
            particles.add(
                Particle(
                    x, y,
                    (Random.nextFloat() - 0.5f) * 260f,
                    -Random.nextFloat() * 200f - 40f,
                    4f + Random.nextFloat() * 6f,
                    color,
                    0.5f + Random.nextFloat() * 0.35f,
                )
            )
        }
    }

    fun spawnCoinBurst(x: Float, y: Float) {
        repeat(6) {
            particles.add(
                Particle(
                    x, y,
                    (Random.nextFloat() - 0.5f) * 200f,
                    -Random.nextFloat() * 240f - 60f,
                    3f + Random.nextFloat() * 4f,
                    Palette.TEXT_GOLD,
                    0.55f + Random.nextFloat() * 0.3f,
                )
            )
        }
    }
}
