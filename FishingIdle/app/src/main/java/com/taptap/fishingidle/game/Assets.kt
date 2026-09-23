package com.taptap.fishingidle.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.LruCache
import java.io.InputStream

/**
 * 位图资源管理。所有精灵在加载时就按目标绘制尺寸预缩放，
 * 避免每帧 drawBitmap 做缩放（这是 Canvas 2D 游戏最大的性能陷阱）。
 */
class Assets(context: Context) {

    private val appContext = context.applicationContext
    private val cache = HashMap<String, Bitmap>()

    /** 原始位图，用于需要保持原始比例的场合。 */
    fun raw(name: String): Bitmap? = cache.getOrPut(name) {
        try {
            appContext.assets.open("art/$name.png").use { input: InputStream ->
                BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }) ?: return@getOrPut createFallback()
            }
        } catch (e: Exception) {
            createFallback()
        }
    }

    /** 预缩放到指定宽度的位图，带独立缓存键。 */
    fun scaled(name: String, targetW: Int): Bitmap? {
        if (targetW <= 0) return raw(name)
        val key = "$name@$targetW"
        cache[key]?.let { return it }
        val src = raw(name) ?: return null
        val ratio = targetW.toFloat() / src.width
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        val out = Bitmap.createScaledBitmap(src, targetW, h, true)
        cache[key] = out
        return out
    }

    private fun createFallback(): Bitmap =
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).also {
            it.eraseColor(android.graphics.Color.MAGENTA)
        }

    fun evict() {
        cache.clear()
    }
}

/**
 * 缩放绘制辅助：把设计空间 (1000x1500) 映射到屏幕。
 * 保持宽高比，按宽度铺满，纵向居中裁切。
 */
class ViewTransform(val screenW: Float, val screenH: Float) {
    val scale = screenW / Space.W
    val offsetX = 0f
    val offsetY = (screenH - Space.H * scale) / 2f

    fun toScreenX(x: Float) = offsetX + x * scale
    fun toScreenY(y: Float) = offsetY + y * scale

    fun toWorldX(sx: Float) = (sx - offsetX) / scale
    fun toWorldY(sy: Float) = (sy - offsetY) / scale

    /** 世界坐标点是否在屏幕可见范围内。 */
    fun isVisible(y: Float) = toScreenY(y) > -200f && toScreenY(y) < screenH + 200f
}

/** 精灵绘制封装：以中心点绘制，支持水平翻转与透明度。 */
object SpriteDraw {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    fun draw(
        canvas: Canvas,
        bmp: Bitmap?,
        cx: Float,
        cy: Float,
        scale: Float = 1f,
        alpha: Int = 255,
        flipX: Boolean = false,
        rotation: Float = 0f,
    ) {
        if (bmp == null || alpha <= 0) return
        paint.alpha = alpha
        val w = bmp.width * scale
        val h = bmp.height * scale
        val left = cx - w / 2f
        val top = cy - h / 2f

        val needTransform = flipX || rotation != 0f
        if (needTransform) {
            canvas.save()
            canvas.rotate(rotation, cx, cy)
            if (flipX) {
                canvas.scale(-1f, 1f, cx, cy)
            }
            canvas.drawBitmap(bmp, null, RectF(left, top, left + w, top + h), paint)
            canvas.restore()
        } else {
            canvas.drawBitmap(bmp, null, RectF(left, top, left + w, top + h), paint)
        }
    }

    /** 九宫格绘制：边角不拉伸，中间区域平铺/拉伸。 */
    fun drawNinePatch(
        canvas: Canvas,
        bmp: Bitmap?,
        insetX: Int,
        insetY: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        alpha: Int = 255,
    ) {
        if (bmp == null) return
        paint.alpha = alpha
        val w = right - left
        val h = bottom - top
        val ix = insetX.toFloat()
        val iy = insetY.toFloat()
        val srcW = bmp.width.toFloat()
        val srcH = bmp.height.toFloat()

        // 9 个切片的源矩形与目标矩形
        val srcXs = floatArrayOf(0f, ix, srcW - ix, srcW)
        val srcYs = floatArrayOf(0f, iy, srcH - iy, srcH)
        val dstXs = floatArrayOf(left, left + ix, right - ix, right)
        val dstYs = floatArrayOf(top, top + iy, bottom - iy, bottom)

        for (r in 0 until 3) {
            for (c in 0 until 3) {
                val s = Rect(
                    srcXs[c].toInt(), srcYs[r].toInt(),
                    srcXs[c + 1].toInt(), srcYs[r + 1].toInt()
                )
                val d = RectF(dstXs[c], dstYs[r], dstXs[c + 1], dstYs[r + 1])
                if (d.width() > 0f && d.height() > 0f) {
                    canvas.drawBitmap(bmp, s, d, paint)
                }
            }
        }
    }
}
