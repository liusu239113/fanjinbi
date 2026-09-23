package com.taptap.fishingidle.game

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** 设计空间尺寸，渲染时等比缩放到屏幕。 */
object Space {
    const val W = 1000f
    const val H = 1500f

    /** 水面线：上方是船，下方是水下钓场。 */
    const val SURFACE_Y = 300f

    /** 鱼群活动区域。 */
    const val POND_L = 80f
    const val POND_R = W - 80f
    const val POND_T = SURFACE_Y + 110f
    const val POND_B = H - 110f
}

enum class FishState { SWIMMING, APPROACHING, BITING, HOOKED, CAUGHT, ESCAPED }

class Fish(val kind: FishKind, var x: Float, var y: Float) {
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
    var scale = kind.scale * (0.9f + Random.nextFloat() * 0.25f)
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
                    val speed = Content.swimSpeed(kind)
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
                    val speed = Content.swimSpeed(kind) * 2.2f
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
                // 被拉向小船
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
                val duration = Content.reelDuration(hookedFish?.kind ?: FishKind.COMMON) /
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

    /** 玩家点击浮标：咬钩→开始收线。返回 true 表示点击被消费。 */
    fun onTap(): Boolean = when (state) {
        BobberState.BITE -> { state = BobberState.REELING; true }
        BobberState.REELING -> true
        else -> false
    }

    val isActive: Boolean get() = state != BobberState.DONE && state != BobberState.IDLE

    companion object {
        const val BITE_WINDOW = 1.5f
        const val TAP_BONUS = 0.055f
        const val FLY_DURATION = 0.42f

        /** 没有鱼咬钩时的空等上限（秒）。 */
        const val EMPTY_WAIT = 2.2f
    }
}

enum class BobberEvent { Landed, Bite, Missed, Reeled, Empty }

/** 自动钓手状态。 */
enum class HelperState { IDLE, MOVING, CASTING }

class Helper(var x: Float, var y: Float) {
    var state = HelperState.IDLE
        set(value) {
            field = value
            stateTime = 0f
        }
    var stateTime = 0f
    var idleDuration = 0.6f
    var targetFish: Fish? = null
    var facing = 1f
    var wiggle = Random.nextFloat() * 6.2832f
    var lineX = x
    var lineY = y

    fun update(dt: Float, gameState: GameState, world: World) {
        stateTime += dt
        wiggle += dt * 3.2f
        when (state) {
            HelperState.IDLE -> {
                if (stateTime >= idleDuration) {
                    val t = world.pickTargetForHelper(this)
                    if (t != null) {
                        targetFish = t
                        state = HelperState.MOVING
                    } else {
                        stateTime = 0f
                        idleDuration = 0.5f
                    }
                }
            }

            HelperState.MOVING -> {
                val f = targetFish
                if (f == null || f.state != FishState.SWIMMING) {
                    world.releaseClaim(this)
                    targetFish = null
                    state = HelperState.IDLE
                    idleDuration = 0.3f
                } else {
                    val dx = f.x - x
                    val dy = f.y - y
                    val d = hypot(dx, dy)
                    val speed = 215f * gameState.helperEfficiency.toFloat()
                    if (d < 42f) {
                        state = HelperState.CASTING
                        lineX = f.x; lineY = f.y
                    } else {
                        x += dx / d * speed * dt
                        y += dy / d * speed * dt
                        facing = if (dx > 0f) 1f else -1f
                    }
                }
            }

            HelperState.CASTING -> {
                val f = targetFish
                if (f != null) { lineX = f.x; lineY = f.y }
                if (stateTime >= 0.7f) {
                    if (f != null && f.state == FishState.SWIMMING) {
                        world.helperCatch(this, f)
                    }
                    state = HelperState.IDLE
                    idleDuration = (0.35f + Random.nextFloat() * 0.5f) /
                        gameState.helperEfficiency.toFloat()
                }
            }
        }
    }
}

/** 游戏世界：持有所有实体并推进模拟。 */
class World(val gameState: GameState) {

    val fishes = mutableListOf<Fish>()
    val helpers = mutableListOf<Helper>()
    val floatingTexts = mutableListOf<FloatingText>()
    val particles = mutableListOf<Particle>()
    val bobber = Bobber()

    /** 本帧待播放的音效事件，由 UI 层消费。 */
    val pendingSounds = mutableListOf<String>()

    var time = 0f
    /** 手动抛竿的落点。 */
    var castX = Space.W / 2f
    var castY = Space.POND_T + 220f
    /** 收线期间累计的点击加力，每帧清零。 */
    private var tapsThisFrame = 0
    /** 自动收线（智能浮标）的悬停目标。 */
    var hoverFish: Fish? = null
    var autoReelCooldown = 0f

    init {
        syncFishCount()
        syncHelperCount()
    }

    // ---------------- 数量同步 ----------------

    private fun countOf(kind: FishKind) = fishes.count { it.kind == kind }

    /** 让鱼群数量与 GameState 一致（多退少补）。 */
    fun syncFishCount() {
        for (kind in FishKind.entries) {
            val want = gameState.ownedCount(kind)
            val have = countOf(kind)
            if (have < want) {
                repeat(want - have) {
                    fishes.add(
                        Fish(
                            kind,
                            Random.nextFloat() * (Space.POND_R - Space.POND_L) + Space.POND_L,
                            Random.nextFloat() * (Space.POND_B - Space.POND_T) + Space.POND_T,
                        )
                    )
                }
            } else if (have > want) {
                // 移除多余的（优先移除空闲的）
                var toRemove = have - want
                val iter = fishes.iterator()
                while (iter.hasNext() && toRemove > 0) {
                    val f = iter.next()
                    if (f.kind == kind && f.state == FishState.SWIMMING) {
                        iter.remove(); toRemove--
                    }
                }
            }
        }
    }

    fun syncHelperCount() {
        val want = gameState.helpers
        while (helpers.size < want) {
            helpers.add(
                Helper(
                    Random.nextFloat() * (Space.POND_R - Space.POND_L) + Space.POND_L,
                    Random.nextFloat() * (Space.POND_B - Space.POND_T) * 0.5f + Space.POND_T,
                )
            )
        }
        while (helpers.size > want) helpers.removeAt(helpers.size - 1)
    }

    // ---------------- 玩家操作 ----------------

    /** 手动抛竿。返回是否成功。 */
    fun castLine(tx: Float, ty: Float): Boolean {
        if (bobber.isActive) return false
        castX = tx.coerceIn(Space.POND_L, Space.POND_R)
        castY = ty.coerceIn(Space.POND_T, Space.POND_B)

        // 随机挑一条空闲的鱼咬钩
        val candidates = fishes.filter { it.state == FishState.SWIMMING }
        val fish = if (candidates.isEmpty()) null else candidates.random()
        fish?.let {
            it.setTarget(castX, castY)
            it.state = FishState.APPROACHING
        }

        bobber.cast(Space.W / 2f, Space.SURFACE_Y - 40f, castX, castY)
        bobber.hookedFish = fish
        if (fish != null) {
            val range = Content.biteDelay(fish.kind)
            bobber.biteTimer = range.start + Random.nextFloat() * (range.endInclusive - range.start)
        }
        pendingSounds.add("cast")
        return true
    }

    /**
     * 把已钓上来的鱼"放回"水里。
     * 鱼群是钓场的常驻居民，数量由 GameState 决定；被钓起只是播放一段上岸动画，
     * 随后在同位置重新入水，这样玩家的钓场规模不会被慢慢掏空。
     */
    private fun returnToPond(f: Fish) {
        f.state = FishState.SWIMMING
        f.claimedBy = null
        f.x = Random.nextFloat() * (Space.POND_R - Space.POND_L) + Space.POND_L
        f.y = Random.nextFloat() * (Space.POND_B - Space.POND_T) * 0.8f + Space.POND_T
        val ang = Random.nextFloat() * 6.2832f
        val speed = Content.swimSpeed(f.kind)
        f.vx = kotlin.math.cos(ang) * speed
        f.vy = kotlin.math.sin(ang) * speed * 0.55f
    }

    /** 点击屏幕：命中浮标则推进状态；否则（智能浮标已解锁）收线悬停的鱼。 */
    fun onTap(wx: Float, wy: Float): Boolean {
        if (bobber.isActive) {
            val d = hypot(wx - bobber.x, wy - bobber.y)
            if (d < 130f || bobber.state == BobberState.REELING) {
                if (bobber.onTap()) {
                    tapsThisFrame++
                    if (bobber.state == BobberState.REELING) pendingSounds.add("reel")
                    return true
                }
            }
            return false
        }
        return false
    }

    // ---------------- 钓手 ----------------

    /** 为指定钓手挑一个目标：优先近的、可钓的、没被其他钓手预定的鱼。 */
    fun pickTargetForHelper(helper: Helper): Fish? {
        val reachable = fishes.filter { f ->
            f.state == FishState.SWIMMING && f.claimedBy == null && when (f.kind) {
                FishKind.COMMON -> true
                FishKind.RARE -> gameState.helperCanRare
                FishKind.EPIC -> gameState.helperCanEpic
                FishKind.LEGEND -> gameState.helperCanLegend
            }
        }
        val target = reachable.minByOrNull { hypot(it.x - helper.x, it.y - helper.y) } ?: return null
        target.claimedBy = helper
        return target
    }

    /** 释放钓手对目标的预定。 */
    fun releaseClaim(helper: Helper) {
        fishes.forEach { if (it.claimedBy === helper) it.claimedBy = null }
    }

    /** 钓手钓上一条鱼，立即结算，随后鱼回到水里。 */
    fun helperCatch(helper: Helper, fish: Fish) {
        val value = gameState.catchValue(fish.kind)
        award(fish, value, Source.HELPER, fish.x, fish.y)
        spawnSplash(fish.x, fish.y, fish.kind)
        returnToPond(fish)
    }

    // ---------------- 主循环 ----------------

    fun update(dt: Float) {
        time += dt

        for (f in fishes) f.update(dt)

        // 浮标状态机
        val ev = bobber.update(dt, currentReelSpeed(), tapsThisFrame)
        tapsThisFrame = 0
        ev?.let { handleBobberEvent(it) }

        // 清理失效预定：目标已不再空闲时释放，让钓手重新选目标
        fishes.forEach { if (it.claimedBy != null && it.state != FishState.SWIMMING) it.claimedBy = null }

        // 钓手
        for (h in helpers) h.update(dt, gameState, this)

        // 自动收线：悬停在小鱼上时自动抛竿（智能浮标）
        autoReelCooldown -= dt
        if (gameState.autoReelUnlocked && !bobber.isActive && autoReelCooldown <= 0f) {
            val target = hoverFish
            if (target != null && target.state == FishState.SWIMMING) {
                castLine(target.x, target.y)
                autoReelCooldown = 0.4f
            }
        }

        // 清理已完成的鱼（游出画面的）
        fishes.removeAll { it.state == FishState.CAUGHT && it.y < Space.POND_T - 200f }

        // 浮动文字与粒子
        for (t in floatingTexts) t.update(dt)
        floatingTexts.removeAll { it.dead }
        for (p in particles) p.update(dt)
        particles.removeAll { it.dead }
    }

    private fun currentReelSpeed(): Double {
        val kind = bobber.hookedFish?.kind ?: FishKind.COMMON
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
                bobber.hookedFish = null
            }

            BobberEvent.Reeled -> {
                val f = bobber.hookedFish
                if (f != null) {
                    // 稀有鱼有概率在收线最后一刻挣脱
                    if (Random.nextFloat() < Content.escapeChance(f.kind) * 0.35f) {
                        f.state = FishState.ESCAPED
                        f.vx = if (Random.nextBoolean()) 110f else -110f
                        f.vy = 70f
                        pendingSounds.add("fail")
                        spawnText(bobber.x, bobber.y - 40f, "线断了！", Palette.TEXT_BAD, 1.0f)
                    } else {
                        val value = gameState.catchValue(f.kind)
                        award(f, value, Source.MANUAL, bobber.x, bobber.y)
                        spawnSplash(bobber.x, bobber.y, f.kind)
                        returnToPond(f)

                        if (gameState.chainReaction) triggerChain(bobber.x, bobber.y, f)
                        if (Random.nextDouble() < gameState.autoReelChance) {
                            autoReelCooldown = 0.35f
                            pendingAutoRecast = true
                        }
                    }
                }
                bobber.hookedFish = null
            }
        }
    }

    var pendingAutoRecast = false

    /** 连锁反应：惊动周围鱼群，最多 8 条。 */
    private fun triggerChain(x: Float, y: Float, origin: Fish) {
        val nearby = fishes
            .filter { it !== origin && it.state == FishState.SWIMMING && hypot(it.x - x, it.y - y) < 340f }
            .sortedBy { hypot(it.x - x, it.y - y) }
            .take(8)
        for (f in nearby) {
            val value = gameState.catchValue(f.kind)
            f.state = FishState.CAUGHT
            f.vy = -90f
            f.vx = (f.x - x) * 0.8f
            award(f, value, Source.CHAIN, f.x, f.y)
            spawnSplash(f.x, f.y, f.kind)
        }
    }

    /** 结算一次渔获：加钱、记录、生成表现。 */
    private fun award(fish: Fish, value: Double, source: Source, x: Float, y: Float) {
        gameState.money += value
        gameState.recordEarning(fish.kind, source, value)
        gameState.recordCatch(value)

        // 用"典型渔获价值"做归一化，而不是历史最高值 —— 后者会随进度膨胀，
        // 导致浮动文字越来越小。
        val typical = gameState.catchValue(FishKind.COMMON).coerceAtLeast(1.0)
        val scale = (value / typical).toFloat().let {
            // 对数压缩，避免大鱼文字夸张到撑满屏幕
            (0.9f + kotlin.math.ln(1f + it) * 0.22f).coerceIn(0.9f, 2.4f)
        }
        spawnText(x, y - 30f, "+${formatNumber(value)}", Palette.TEXT_GOLD, scale)
        spawnCoinBurst(x, y)
        pendingSounds.add(if (value >= 100) "success" else "coin")
    }

    // ---------------- 表现 ----------------

    fun spawnText(x: Float, y: Float, text: String, color: Int, scale: Float) {
        if (floatingTexts.size > 90) floatingTexts.removeAt(0)
        floatingTexts.add(FloatingText(x, y, text, color, scale))
    }

    fun spawnSplash(x: Float, y: Float, kind: FishKind) {
        val color = when (kind) {
            FishKind.COMMON -> Palette.SPLASH
            FishKind.RARE -> Palette.SPLASH_GREEN
            FishKind.EPIC -> Palette.SPLASH_GOLD
            FishKind.LEGEND -> Palette.SPLASH_PURPLE
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
