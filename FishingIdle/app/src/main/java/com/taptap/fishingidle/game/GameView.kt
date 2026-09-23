package com.taptap.fishingidle.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * 游戏视图。使用普通 View + Canvas 绘制（比 Compose Canvas 在每帧大量绘制时更可控），
 * 通过 Choreographer 驱动固定步长更新。
 */
@SuppressLint("ViewConstructor")
class GameView(
    context: Context,
    private val world: World,
    private val assets: Assets,
    private val settings: Settings,
    private val onSfx: (String) -> Unit,
) : View(context) {

    private val renderer = GameRenderer(assets, world, world.gameState, settings)

    private var lastFrameNanos = 0L
    private var accumulator = 0f
    private var running = false

    /** 需要暂停更新（打开菜单/商店面板）时为 true。 */
    var paused = false

    /** 悬停的鱼（用于智能浮标），由触摸位置推算。 */
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    init {
        setWillNotDraw(false)
        isClickable = true
        isFocusable = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        renderer.onSizeChanged(w.toFloat(), h.toFloat())
    }

    fun start() {
        running = true
        lastFrameNanos = 0L
        postInvalidateOnAnimation()
    }

    fun stop() {
        running = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = ViewTransform(width.toFloat(), height.toFloat())

        val now = System.nanoTime()
        if (lastFrameNanos == 0L) lastFrameNanos = now
        var delta = (now - lastFrameNanos) / 1_000_000_000f
        lastFrameNanos = now
        // 防止切后台回来时一次性推进过多
        if (delta > 0.25f) delta = 0.25f

        if (!paused) {
            accumulator += delta
            var steps = 0
            while (accumulator >= FIXED_STEP && steps < 5) {
                world.update(FIXED_STEP)
                accumulator -= FIXED_STEP
                steps++
            }
            // 处理自动重抛
            if (world.pendingAutoRecast) {
                world.pendingAutoRecast = false
                if (!world.bobber.isActive) {
                    world.castLine(
                        Space.POND_L + (Space.POND_R - Space.POND_L) * 0.5f,
                        Space.POND_T + (Space.POND_B - Space.POND_T) * 0.5f,
                    )
                }
            }
            flushSounds()
        }

        renderer.render(canvas, t)

        if (running) postInvalidateOnAnimation()
    }

    private fun flushSounds() {
        if (world.pendingSounds.isEmpty()) return
        for (s in world.pendingSounds) onSfx(s)
        world.pendingSounds.clear()
    }

    // ---------------- 输入 ----------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (paused) return false
        val t = ViewTransform(width.toFloat(), height.toFloat())
        val wx = t.toWorldX(event.x)
        val wy = t.toWorldY(event.y)
        lastTouchX = wx
        lastTouchY = wy

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                world.hoverFish = findFishAt(wx, wy)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                world.hoverFish = findFishAt(wx, wy)
            }
            MotionEvent.ACTION_UP -> {
                handleTap(wx, wy)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTap(wx: Float, wy: Float) {
        // 优先判定浮标
        if (world.bobber.isActive) {
            world.onTap(wx, wy)
            return
        }
        // 没有浮标：点哪抛哪
        if (wy > Space.SURFACE_Y + 40f) {
            world.castLine(wx, wy)
        }
    }

    /** 命中检测：返回离点击点最近且在阈值内的鱼。 */
    private fun findFishAt(wx: Float, wy: Float): Fish? {
        var best: Fish? = null
        var bestDist = 90f
        for (f in world.fishes) {
            if (f.state != FishState.SWIMMING) continue
            val d = hypot(f.x - wx, f.y - wy)
            if (d < bestDist) {
                bestDist = d
                best = f
            }
        }
        return best
    }

    companion object {
        const val FIXED_STEP = 1f / 60f
    }
}
