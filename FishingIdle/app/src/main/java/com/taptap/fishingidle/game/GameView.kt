package com.taptap.fishingidle.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot

/**
 * 游戏视图。普通 View + Canvas 绘制，Choreographer 驱动固定步长更新。
 *
 * 交互分工：
 * - 单指快速点击 → 抛竿 / 咬钩 / 收线
 * - 单指横向拖动 → 左右平移镜头（浏览整条河）
 *
 * 判定靠位移阈值与时长区分，不额外占用手势系统。
 */
@SuppressLint("ViewConstructor")
class GameView(
    context: Context,
    private val world: World,
    private val assets: Assets,
    private val settings: Settings,
    private val onSfx: (String) -> Unit,
) : View(context) {

    private val renderer = GameRenderer(
        assets, world, world.gameState, settings,
        gameTypeface = loadGameTypeface(context),
    )

    private var lastFrameNanos = 0L
    private var accumulator = 0f
    private var running = false

    /** 需要暂停更新（打开菜单/商店/回主菜单）时为 true。 */
    var paused = false

    // ---- 手势状态 ----
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var lastX = 0f
    private var dragging = false
    /** 本次手势累计的横向位移，用于区分"点击"与"拖动"。 */
    private var totalDragX = 0f

    init {
        setWillNotDraw(false)
        isClickable = true
        isFocusable = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        renderer.onSizeChanged(w.toFloat(), h.toFloat())
        // 回填视野宽度，供镜头边界计算使用
        world.viewHalfWidth = w.toFloat() / (h.toFloat() / Space.H) / 2f
    }

    fun start() {
        running = true
        lastFrameNanos = 0L
        postInvalidateOnAnimation()
    }

    fun stop() {
        running = false
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val t = ViewTransform(width.toFloat(), height.toFloat())

        val now = System.nanoTime()
        if (lastFrameNanos == 0L) lastFrameNanos = now
        var delta = (now - lastFrameNanos) / 1_000_000_000f
        lastFrameNanos = now
        if (delta > 0.25f) delta = 0.25f

        if (!paused) {
            accumulator += delta
            var steps = 0
            while (accumulator >= FIXED_STEP && steps < 5) {
                world.update(FIXED_STEP)
                accumulator -= FIXED_STEP
                steps++
            }
            if (world.pendingAutoRecast) {
                world.pendingAutoRecast = false
                if (!world.bobber.isActive) {
                    world.castLine(
                        world.cameraX,
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

    /** 屏幕坐标 → 世界坐标。 */
    private fun worldX(sx: Float, t: ViewTransform) = t.toWorldX(sx) + world.cameraX
    private fun worldY(sy: Float, t: ViewTransform) = t.toWorldY(sy)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (paused) return false
        val t = ViewTransform(width.toFloat(), height.toFloat())

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y
                lastX = event.x
                downTime = System.currentTimeMillis()
                dragging = false
                totalDragX = 0f
                world.hoverFish = findFishAt(worldX(event.x, t), worldY(event.y, t))
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                lastX = event.x
                totalDragX += abs(dx)
                if (!dragging && totalDragX > DRAG_THRESHOLD_PX) {
                    dragging = true
                }
                if (dragging) {
                    // 手指向右拖 → 镜头向左移（内容跟手）
                    world.scrollCamera(-dx / t.scale)
                } else {
                    world.hoverFish = findFishAt(worldX(event.x, t), worldY(event.y, t))
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val elapsed = System.currentTimeMillis() - downTime
                val moved = hypot(event.x - downX, event.y - downY)
                if (!dragging && moved < TAP_SLOP_PX && elapsed < TAP_TIME_MS) {
                    handleTap(worldX(event.x, t), worldY(event.y, t))
                }
                dragging = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTap(wx: Float, wy: Float) {
        // 浮标活动中：点击推进咬钩/收线
        if (world.bobber.isActive) {
            if (world.onTap(wx, wy)) return
        }
        // 水面以下：先看落点附近有没有鱼
        if (wy > Space.SURFACE_Y + 40f) {
            world.sailTo(wx)
            if (world.hasFishNear(wx, wy)) {
                world.castLine(wx, wy)
            } else {
                // 附近没鱼就不抛竿，只给个提示 —— 避免"空地方也能咬钩"
                world.notifyNoFish(wx, wy)
            }
        }
    }

    /** 命中检测：返回离点击点最近且在阈值内的鱼。 */
    private fun findFishAt(wx: Float, wy: Float): Fish? {
        var best: Fish? = null
        var bestDist = 110f
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

    /**
     * 加载游戏字体。
     * 放在 res/font 下的字体可以用 ResourcesCompat 直接取，不必手动读 assets。
     */
    private fun loadGameTypeface(context: Context): Typeface? = try {
        androidx.core.content.res.ResourcesCompat.getFont(
            context, com.taptap.fishingidle.R.font.game_font
        )
    } catch (e: Exception) {
        null
    }

    companion object {
        const val FIXED_STEP = 1f / 60f

        /** 超过这个横向位移就判定为拖动镜头而非点击。 */
        const val DRAG_THRESHOLD_PX = 22f
        const val TAP_SLOP_PX = 26f
        const val TAP_TIME_MS = 320L
    }
}
