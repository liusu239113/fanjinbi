package com.taptap.fishingidle.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.sin

/**
 * 钓场渲染器。全部使用 Canvas 绘制，位图在首次使用时按目标尺寸预缩放。
 */
class GameRenderer(
    private val assets: Assets,
    private val world: World,
    private val gameState: GameState,
    private val settings: Settings,
) {
    // 稀有度颜色在构造时查表一次，避免每帧对每条鱼做 Map 查找
    private val rarityColor: Map<FishKind, Int> = mapOf(
        FishKind.COMMON to Palette.SPLASH,
        FishKind.RARE to Palette.SPLASH_GREEN,
        FishKind.EPIC to Palette.SPLASH_GOLD,
        FishKind.LEGEND to Palette.SPLASH_PURPLE,
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // 预缩放的位图缓存
    private var bmpWater: Bitmap? = null
    private var bmpBoat: Bitmap? = null
    private var bmpBobber: Bitmap? = null
    private var bmpRod: Bitmap? = null
    private var helperBoat: Bitmap? = null
    private val bmpFish = HashMap<FishKind, Bitmap>()
    private var waterShader: Shader? = null
    private var lastScreenW = 0f
    private var lastScreenH = 0f

    /** 相机轻微晃动，增加活力。 */
    private var shakeX = 0f
    private var shakeY = 0f

    fun onSizeChanged(w: Float, h: Float) {
        lastScreenW = w
        lastScreenH = h
        val t = ViewTransform(w, h)
        val targetWaterW = (Space.W * t.scale * 1.5f).toInt()
        bmpWater = assets.scaled("water_tile", targetWaterW.coerceIn(256, 1024))
        bmpBoat = assets.scaled("boat", (170 * t.scale).toInt().coerceAtLeast(32))
        bmpBobber = assets.scaled("bobber", (74 * t.scale).toInt().coerceAtLeast(16))
        bmpRod = assets.scaled("rod", (120 * t.scale).toInt().coerceAtLeast(24))
        helperBoat = assets.scaled("boat", (96 * t.scale).toInt().coerceAtLeast(20))
        bmpFish.clear()
        for (kind in FishKind.entries) {
            val base = when (kind) {
                FishKind.COMMON -> 128f
                FishKind.RARE -> 155f
                FishKind.EPIC -> 190f
                FishKind.LEGEND -> 235f
            }
            bmpFish[kind] = assets.scaled(kind.sprite, (base * t.scale).toInt().coerceAtLeast(24))
                ?: continue
        }
    }

    fun render(canvas: Canvas, t: ViewTransform) {
        // 相机抖动
        shakeX = sin(world.time * 1.7f) * 2.2f
        shakeY = sin(world.time * 2.3f) * 1.6f

        canvas.save()
        canvas.translate(shakeX, shakeY)

        drawWater(canvas, t)
        drawFish(canvas, t)
        drawHelpers(canvas, t)
        drawBobberAndLine(canvas, t)
        drawBoat(canvas, t)
        drawParticles(canvas, t)
        drawFloatingTexts(canvas, t)

        canvas.restore()
    }

    // ---------------- 水面 ----------------

    private fun drawWater(canvas: Canvas, t: ViewTransform) {
        val top = t.toScreenY(0f)
        val bottom = t.toScreenY(Space.H)

        if (waterShader == null) {
            waterShader = LinearGradient(
                0f, top, 0f, bottom,
                intArrayOf(Palette.WATER_TOP, Palette.WATER_MID, Palette.WATER_BOTTOM),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        fillPaint.shader = waterShader
        canvas.drawRect(0f, top, t.screenW, bottom, fillPaint)
        fillPaint.shader = null

        // 平铺水纹
        val water = bmpWater
        if (water != null) {
            paint.alpha = 46
            val tile = water.width.toFloat()
            var y = t.toScreenY(Space.SURFACE_Y)
            var row = 0
            while (y < bottom) {
                var x = if (row % 2 == 0) -tile / 2f else 0f
                while (x < t.screenW) {
                    canvas.drawBitmap(water, x + (world.time * 6f) % tile, y, paint)
                    x += tile
                }
                y += tile
                row++
            }
            paint.alpha = 255
        }

        // 水面高光线
        val surfaceY = t.toScreenY(Space.SURFACE_Y)
        strokePaint.color = Color.argb(150, 200, 245, 255)
        strokePaint.strokeWidth = 5f * t.scale
        val path = Path()
        var px = 0f
        path.moveTo(0f, surfaceY)
        while (px <= t.screenW) {
            val wy = surfaceY + sin(px / (60f * t.scale) + world.time * 1.6f) * 7f * t.scale
            path.lineTo(px, wy)
            px += 12f * t.scale
        }
        canvas.drawPath(path, strokePaint)

        // 水面之上的天空渐变
        val skyShader = LinearGradient(
            0f, top, 0f, surfaceY,
            intArrayOf(Color.rgb(28, 62, 76), Color.rgb(58, 118, 130)),
            null, Shader.TileMode.CLAMP
        )
        fillPaint.shader = skyShader
        canvas.drawRect(0f, top, t.screenW, surfaceY, fillPaint)
        fillPaint.shader = null
    }

    // ---------------- 鱼 ----------------

    private fun drawFish(canvas: Canvas, t: ViewTransform) {
        // 只绘制可见区域内的鱼
        val topVisible = t.toWorldY(0f) - 200f
        val bottomVisible = t.toWorldY(t.screenH) + 200f

        for (f in world.fishes) {
            if (f.y < topVisible || f.y > bottomVisible) continue
            val bmp = bmpFish[f.kind] ?: continue

            // 深度感：越靠下的鱼越大、越暗
            val depth = ((f.y - Space.POND_T) / (Space.POND_B - Space.POND_T)).coerceIn(0f, 1f)
            val depthScale = 0.72f + depth * 0.55f
            val alpha = (150 + (depth * 105)).toInt().coerceIn(60, 255)

            val cx = t.toScreenX(f.x)
            val cy = t.toScreenY(f.y)

            // 稀有鱼的光晕
            if (f.kind == FishKind.LEGEND || f.kind == FishKind.EPIC) {
                val glow = rarityColor[f.kind] ?: Palette.TEXT_GOLD
                paint.alpha = (40 * (0.6f + 0.4f * sin(world.time * 2f + f.wiggle))).toInt()
                paint.color = glow
                canvas.drawCircle(cx, cy, bmp.width * 0.62f * f.scale * depthScale, paint)
                paint.alpha = 255
            }

            // 咬钩/上钩状态：剧烈摇摆
            val rotation = when (f.state) {
                FishState.BITING -> sin(f.wiggle * 5f) * 26f
                FishState.HOOKED -> sin(f.wiggle * 6f) * 34f
                else -> sin(f.wiggle) * 5f
            }

            val flip = f.facing < 0f
            SpriteDraw.draw(
                canvas, bmp, cx, cy,
                scale = f.scale * depthScale * t.scale,
                alpha = alpha,
                flipX = flip,
                rotation = rotation,
            )

            // 咬钩提示：感叹号
            if (f.state == FishState.BITING) {
                drawExclamation(canvas, cx, cy - bmp.height * 0.6f * f.scale * depthScale * t.scale, t)
            }
        }
    }

    private fun drawExclamation(canvas: Canvas, x: Float, y: Float, t: ViewTransform) {
        val bounce = abs(sin(world.time * 9f)) * 8f * t.scale
        val s = 26f * t.scale
        fillPaint.color = Palette.TEXT_BAD
        strokePaint.color = Palette.INK
        strokePaint.strokeWidth = 3f * t.scale
        val rect = RectF(x - s * 0.22f, y - bounce - s, x + s * 0.22f, y - bounce - s * 0.3f)
        canvas.drawRoundRect(rect, s * 0.2f, s * 0.2f, fillPaint)
        canvas.drawRoundRect(rect, s * 0.2f, s * 0.2f, strokePaint)
        canvas.drawCircle(x, y - bounce, s * 0.2f, fillPaint)
        canvas.drawCircle(x, y - bounce, s * 0.2f, strokePaint)
    }

    // ---------------- 钓手 ----------------

    private fun drawHelpers(canvas: Canvas, t: ViewTransform) {
        val bmp = helperBoat ?: return
        for (h in world.helpers) {
            val cx = t.toScreenX(h.x)
            val cy = t.toScreenY(h.y)

            // 钓线
            if (h.state == HelperState.CASTING) {
                strokePaint.color = Color.argb(200, 240, 240, 240)
                strokePaint.strokeWidth = 2f * t.scale
                canvas.drawLine(cx, cy, t.toScreenX(h.lineX), t.toScreenY(h.lineY), strokePaint)
            }

            val bob = sin(h.wiggle) * 3f * t.scale
            SpriteDraw.draw(canvas, bmp, cx, cy + bob, scale = t.scale, alpha = 235)

            // 工作指示
            if (h.state == HelperState.CASTING) {
                paint.color = Palette.TEXT_GOLD
                paint.alpha = 220
                canvas.drawCircle(cx, cy - bmp.height * 0.62f, 6f * t.scale, paint)
                paint.alpha = 255
            }
        }
    }

    // ---------------- 浮标与钓线 ----------------

    private fun drawBobberAndLine(canvas: Canvas, t: ViewTransform) {
        val b = world.bobber
        if (!b.isActive) return

        val rodX = t.toScreenX(Space.W / 2f)
        val rodY = t.toScreenY(Space.SURFACE_Y - 90f)
        val bx = t.toScreenX(b.x)
        val by = t.toScreenY(b.y)

        // 钓线
        strokePaint.color = Color.argb(190, 245, 245, 245)
        strokePaint.strokeWidth = 2.4f * t.scale
        canvas.drawLine(rodX, rodY, bx, by, strokePaint)

        // 落点涟漪
        if (b.state == BobberState.FLOATING || b.state == BobberState.BITE) {
            val ripplePhase = (world.time * 1.3f) % 1f
            for (i in 0..1) {
                val p = (ripplePhase + i * 0.5f) % 1f
                strokePaint.color = Color.argb(((1f - p) * 90).toInt(), 220, 250, 255)
                strokePaint.strokeWidth = 2f * t.scale
                canvas.drawOval(
                    RectF(
                        bx - 40f * t.scale * p - 12f * t.scale,
                        by - 14f * t.scale * p - 4f * t.scale,
                        bx + 40f * t.scale * p + 12f * t.scale,
                        by + 14f * t.scale * p + 4f * t.scale,
                    ), strokePaint
                )
            }
        }

        // 收线进度环
        if (b.state == BobberState.REELING) {
            val r = 42f * t.scale
            strokePaint.color = Color.argb(90, 0, 0, 0)
            strokePaint.strokeWidth = 7f * t.scale
            canvas.drawCircle(bx, by, r, strokePaint)
            strokePaint.color = Palette.TEXT_GOLD
            canvas.drawArc(
                RectF(bx - r, by - r, bx + r, by + r),
                -90f, 360f * b.reelProgress, false, strokePaint
            )
        }

        // 浮标本体
        val bob = if (b.state == BobberState.BITE) sin(world.time * 24f) * 5f * t.scale else 0f
        SpriteDraw.draw(canvas, bmpBobber, bx, by + bob, scale = t.scale)

        // 收线提示文字
        if (b.state == BobberState.BITE) {
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 40f * t.scale
            textPaint.color = Palette.TEXT_BAD
            textPaint.setShadowLayer(4f * t.scale, 0f, 2f * t.scale, Palette.INK)
            canvas.drawText("点击收线！", bx, by - 62f * t.scale + bob, textPaint)
            textPaint.clearShadowLayer()
        }
    }

    // ---------------- 船 ----------------

    private fun drawBoat(canvas: Canvas, t: ViewTransform) {
        val bmp = bmpBoat ?: return
        val cx = t.toScreenX(Space.W / 2f)
        val bob = sin(world.time * 1.4f) * 6f * t.scale
        val cy = t.toScreenY(Space.SURFACE_Y - 150f) + bob
        SpriteDraw.draw(canvas, bmp, cx, cy, scale = t.scale)

        // 鱼竿
        val rod = bmpRod
        if (rod != null) {
            SpriteDraw.draw(
                canvas, rod,
                cx + 46f * t.scale, cy - 8f * t.scale,
                scale = t.scale * 0.9f,
                rotation = -22f + sin(world.time * 1.4f) * 2.5f,
            )
        }
    }

    // ---------------- 粒子与文字 ----------------

    private fun drawParticles(canvas: Canvas, t: ViewTransform) {
        for (p in world.particles) {
            paint.color = p.color
            paint.alpha = (p.alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(t.toScreenX(p.x), t.toScreenY(p.y), p.radius * t.scale, paint)
        }
        paint.alpha = 255
    }

    private fun drawFloatingTexts(canvas: Canvas, t: ViewTransform) {
        if (settings.hideFloatingText) return
        textPaint.textAlign = Paint.Align.CENTER
        for (ft in world.floatingTexts) {
            val pop = if (ft.progress < 0.22f) {
                0.5f + (ft.progress / 0.22f) * 0.6f
            } else {
                1.1f - (ft.progress - 0.22f) * 0.12f
            }
            val size = 30f * ft.scale * pop * t.scale
            textPaint.textSize = size
            textPaint.color = ft.color
            textPaint.alpha = (ft.alpha * 255).toInt().coerceIn(0, 255)
            textPaint.setShadowLayer(5f * t.scale, 0f, 3f * t.scale, Palette.INK)
            canvas.drawText(ft.text, t.toScreenX(ft.x), t.toScreenY(ft.y), textPaint)
        }
        textPaint.clearShadowLayer()
        textPaint.alpha = 255
    }
}
