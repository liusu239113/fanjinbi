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

    /**
     * 原始位图。素材不存在时返回 null —— 调用方一律判空处理。
     * 不要把 null 塞进缓存（HashMap 可以存 null，但会让 getOrPut 反复重算）。
     */
    fun raw(name: String): Bitmap? {
        cache[name]?.let { return it }
        val bmp = try {
            appContext.assets.open("art/$name.png").use { input: InputStream ->
                BitmapFactory.decodeStream(
                    input, null,
                    BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
                )
            }
        } catch (e: Exception) {
            null
        }
        if (bmp != null) cache[name] = bmp
        return bmp
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

    /**
     * 读取动画图集的配置（列数、行数）。
     * 由 tools/build_animations.py 生成，用于把精灵表切成单帧。
     */
    fun frameConfig(name: String): Pair<Int, Int>? {
        return try {
            appContext.assets.open("art/${name}.json").use { input ->
                val text = input.bufferedReader().readText()
                val cols = Regex("\"columns\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toInt()
                val rows = Regex("\"rows\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toInt()
                if (cols != null && rows != null) cols to rows else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 取某个动画图集的**第一帧**，用作 UI 图标（图鉴、商店条目等）。
     *
     * 精灵的原始素材是 `art/<name>_anim.png`（一张多帧图集），
     * UI 直接按整张图显示会糊成一片，必须切出单帧。
     * 若该动画不存在则退回同名的单帧图，最后才给兜底色块。
     */
    fun firstFrame(name: String, targetW: Int): Bitmap? {
        val key = "$name#frame0@$targetW"
        cache[key]?.let { return it }

        val cfg = frameConfig("${name}_anim")
        val sheet = scaled("${name}_anim", targetW)
        if (cfg != null && sheet != null) {
            val (cols, rows) = cfg
            val fw = sheet.width / cols
            val fh = sheet.height / rows
            if (fw > 0 && fh > 0) {
                val frame = Bitmap.createBitmap(sheet, 0, 0, fw, fh)
                cache[key] = frame
                return frame
            }
        }
        // 退回单帧图（老素材），再不行才用兜底色块
        val fallback = raw(name)
        if (fallback != null) cache[key] = fallback
        return fallback
    }

    fun evict() {
        cache.clear()
    }
}

/**
 * 屏幕 ↔ 世界坐标转换。
 *
 * 世界是 3000×1500 的横向长条，屏幕只显示其中一段。
 * 缩放以**高度**为基准：保证世界高度正好铺满一屏（水面、河床位置固定），
 * 横向能看多宽则取决于屏幕宽高比 —— 宽屏看到更多，窄屏看到更少。
 *
 * 传入的 x 坐标一律是**镜头相对坐标**（世界 x - cameraX）。
 */
class ViewTransform(val screenW: Float, val screenH: Float) {
    val scale = screenH / Space.H
    val offsetX = 0f
    val offsetY = 0f

    fun toScreenX(camRelativeX: Float) = offsetX + camRelativeX * scale
    fun toScreenY(y: Float) = offsetY + y * scale

    /** 屏幕 x 转世界 x（不含镜头偏移，调用方需自行加上 cameraX）。 */
    fun toWorldX(sx: Float) = (sx - offsetX) / scale
    fun toWorldY(sy: Float) = (sy - offsetY) / scale

    /** 当前视野宽度（世界单位）。 */
    val worldViewWidth: Float get() = screenW / scale
}

/** 精灵绘制封装：以中心点绘制，支持水平翻转与透明度。 */
object SpriteDraw {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val tintedPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /**
     * [tintMatrix] 是 4x5 颜色矩阵；传 null 表示原色。
     * 用 ColorMatrixColorFilter 换色不产生新位图，几十种鱼共用一张精灵。
     */
    fun draw(
        canvas: Canvas,
        bmp: Bitmap?,
        cx: Float,
        cy: Float,
        scale: Float = 1f,
        alpha: Int = 255,
        flipX: Boolean = false,
        rotation: Float = 0f,
        tintMatrix: FloatArray? = null,
    ) {
        if (bmp == null || alpha <= 0) return
        val paint = if (tintMatrix != null) {
            // 每次按需构造滤镜：ColorMatrixColorFilter 不可变，无法就地改矩阵。
            // 调用点按精灵缓存了 matrix 数组，这里的分配开销可以忽略。
            tintedPaint.colorFilter = android.graphics.ColorMatrixColorFilter(tintMatrix)
            tintedPaint
        } else {
            this.paint
        }
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
