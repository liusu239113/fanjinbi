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

    /** 鱼群活动区域。 */
    const val POND_L = 90f
    const val POND_R = W - 90f
    const val POND_T = SURFACE_Y + 130f
    const val POND_B = H - 120f

    /** 抛竿离最近鱼的最大有效距离，超出则没有鱼来咬钩。 */
    const val MAX_BITE_RANGE = 420f
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
    var scale = species.scale * (0.9f + Random.nextFloat() * 0.25f)
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

/** 自动钓手状态。 */
enum class HelperState { IDLE, ROWING, CASTING }

/**
 * 自动钓手：一条漂在水面上的小船，会划到目标鱼上方抛线把它钓上来。
 * 船始终在水面（SURFACE_Y），钓线垂到水下的鱼身上 —— 与玩家自己的钓法一致。
 */
class Helper(var x: Float) {
    /** 船始终浮在水面。 */
    var y = Space.SURFACE_Y - 24f

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
    /** 钓线终点（目标鱼的位置）。 */
    var lineX = x
    var lineY = Space.SURFACE_Y
    /** 划船时的上下浮动相位。 */
    private var bobPhase = Random.nextFloat() * 6.2832f

    fun update(dt: Float, gameState: GameState, world: World) {
        stateTime += dt
        wiggle += dt * 3.2f
        bobPhase += dt * 2.4f
        y = Space.SURFACE_Y - 24f + sin(bobPhase) * 5f

        when (state) {
            HelperState.IDLE -> {
                if (stateTime >= idleDuration) {
                    val t = world.pickTargetForHelper(this)
                    if (t != null) {
                        targetFish = t
                        state = HelperState.ROWING
                    } else {
                        stateTime = 0f
                        idleDuration = 0.5f
                    }
                }
            }

            HelperState.ROWING -> {
                val f = targetFish
                if (f == null || f.state != FishState.SWIMMING) {
                    world.releaseClaim(this)
                    targetFish = null
                    state = HelperState.IDLE
                    idleDuration = 0.3f
                } else {
                    val dx = f.x - x
                    val speed = 260f * gameState.helperEfficiency.toFloat()
                    if (abs(dx) < 24f) {
                        state = HelperState.CASTING
                        lineX = f.x
                        lineY = f.y
                    } else {
                        x += (if (dx > 0f) 1f else -1f) * speed * dt
                        x = x.coerceIn(Space.POND_L, Space.POND_R)
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

    /**
     * 当前钓场地图。**必须在 init 之前声明** —— init 里的 syncFishCount()
     * 会读它来抽鱼种，声明在 init 之后的话此刻还是 null。
     */
    var currentMap: FishingMap = gameState.currentMap

    init {
        syncFishCount()
        syncHelperCount()
    }

    // ---------------- 镜头 ----------------

    fun scrollCamera(dx: Float) {
        val half = viewHalfWidth
        val minX = half
        val maxX = (Space.W - half).coerceAtLeast(half)
        cameraX = (cameraX + dx).coerceIn(minX, maxX)
    }

    /** 玩家船的位置：始终跟随镜头水平中心。 */
    val boatX: Float get() = cameraX

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
                    val pool = currentMap.species.filter { it.rarity == rarity }
                    if (pool.isEmpty()) return@repeat
                    fishes.add(
                        Fish(
                            pool.random(),
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

    /** 切换地图：清空现有鱼群，按新地图重新铺满。 */
    fun switchMap(map: FishingMap) {
        currentMap = map
        fishes.clear()
        syncFishCount()
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

    /**
     * 手动抛竿。
     *
     * 只有落点附近 [Space.MAX_BITE_RANGE] 内**有鱼**时才会有鱼来咬钩，
     * 否则空竿收回 —— 这样"抛到鱼群边上"才有意义。
     */
    fun castLine(tx: Float, ty: Float): Boolean {
        if (bobber.isActive) return false
        val cx = tx.coerceIn(Space.POND_L, Space.POND_R)
        val cy = ty.coerceIn(Space.POND_T, Space.POND_B)

        // 挑落点附近的空闲鱼，太远的够不着
        val fish = fishes
            .filter { it.state == FishState.SWIMMING }
            .filter { hypot(it.x - cx, it.y - cy) <= Space.MAX_BITE_RANGE }
            .minByOrNull { hypot(it.x - cx, it.y - cy) }

        fish?.let {
            it.setTarget(cx, cy)
            it.state = FishState.APPROACHING
        }

        bobber.cast(boatX, Space.SURFACE_Y - 70f, cx, cy)
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

    /** 为指定钓手挑一个目标：优先近的、可钓的、没被其他钓手预定的鱼。 */
    fun pickTargetForHelper(helper: Helper): Fish? {
        val reachable = fishes.filter { f ->
            f.state == FishState.SWIMMING && f.claimedBy == null && when (f.kind) {
                Rarity.COMMON -> true
                Rarity.RARE -> gameState.helperCanRare
                Rarity.EPIC -> gameState.helperCanEpic
                Rarity.LEGEND -> gameState.helperCanLegend
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
        val value = gameState.catchValue(fish.species, currentMap)
        award(fish, value, Source.HELPER, fish.x, fish.y)
        spawnSplash(fish.x, fish.y, fish.kind)
        returnToPond(fish)
    }

    // ---------------- 主循环 ----------------

    fun update(dt: Float) {
        time += dt

        for (f in fishes) f.update(dt)

        val ev = bobber.update(dt, currentReelSpeed(), tapsThisFrame)
        tapsThisFrame = 0
        ev?.let { handleBobberEvent(it) }

        // 清理失效预定：目标已不再空闲时释放，让钓手重新选目标
        fishes.forEach { if (it.claimedBy != null && it.state != FishState.SWIMMING) it.claimedBy = null }

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

        for (t in floatingTexts) t.update(dt)
        floatingTexts.removeAll { it.dead }
        for (p in particles) p.update(dt)
        particles.removeAll { it.dead }
        for (b in bubbles) b.update(dt, time)
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
                    if (Random.nextFloat() < Content.escapeChance(f.kind) * 0.35f) {
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
                            autoReelCooldown = 0.35f
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

    /**
     * 结算一次渔获：加钱、记录、生成表现。
     *
     * 手动收线才有连击加成与成就推进 —— 自动钓手是挂机收益，不该刷连击。
     */
    private fun award(fish: Fish, value: Double, source: Source, x: Float, y: Float) {
        val manual = source == Source.MANUAL || source == Source.CHAIN
        var finalValue = value

        if (manual) {
            gameState.onCatchSuccess()
            finalValue *= gameState.comboMultiplier
        }

        // 图鉴收集：钓到就记一笔
        gameState.recordSpecies(fish.species.id)

        gameState.money += finalValue
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
        pendingSounds.add(if (finalValue >= 100) "success" else "coin")

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
