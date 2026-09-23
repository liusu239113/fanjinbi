package com.taptap.fishingidle.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.sin

/**
 * 钓场渲染器。全部使用 Canvas 绘制，位图在首次使用时按目标尺寸预缩放。
 *
 * 镜头：世界宽 3000、高 1500，画面按屏幕宽度等比缩放后横向平移，
 * 平移量由 [World.cameraX] 决定，玩家左右拖动即可浏览整条河。
 */
class GameRenderer(
    private val assets: Assets,
    private val world: World,
    private val gameState: GameState,
    private val settings: Settings,
    /** 全局游戏字体，与 UI 保持一致。 */
    private val gameTypeface: Typeface? = null,
) {
    private companion object {
        /** 玩家小船在世界坐标下的宽度。按世界单位给，避免随屏幕像素放大。 */
        const val BOAT_WORLD_W = 260f
    }

    // 稀有度颜色在构造时查表一次，避免每帧对每条鱼做 Map 查找
    private val rarityColor: Map<Rarity, Int> = mapOf(
        Rarity.COMMON to Palette.SPLASH,
        Rarity.RARE to Palette.SPLASH_GREEN,
        Rarity.EPIC to Palette.SPLASH_GOLD,
        Rarity.LEGEND to Palette.SPLASH_PURPLE,
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // 用与 UI 同一套游戏字体（res/font/game_font.ttf），
        // 否则飘字/提示会跟界面字体不一致。
        typeface = gameTypeface ?: Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var bmpWater: Bitmap? = null
    private var bmpRiverbed: Bitmap? = null
    private var bmpBobber: Bitmap? = null
    private var bmpRod: Bitmap? = null
    private var bmpHelper: Bitmap? = null

    /** 每种鱼的逐帧动画。key 为精灵名（不含 _anim 后缀）。 */
    private val fishFrames = HashMap<String, List<Bitmap>>()
    /** 每种鱼每帧的播放时长（秒）。 */
    private val fishFrameDuration = HashMap<String, Float>()

    /** 船的逐帧动画（随波起伏）。 */
    private var boatFrames: List<Bitmap> = emptyList()
    private var boatFrameDuration = 0.12f

    /** 水下装饰：海草用帧动画，随机分布在水底。 */
    private var seaweedFrames: List<Bitmap> = emptyList()
    private var seaweedSpots: List<Triple<Float, Float, Float>> = emptyList()

    /** 天空的云（两种形状轮流用）。 */
    private var cloudFrames: List<Bitmap> = emptyList()

    private var waterShader: Shader? = null
    private var riverbedShader: Shader? = null
    private var lastScreenW = 0f
    private var lastScreenH = 0f

    private var shakeX = 0f
    private var shakeY = 0f

    fun onSizeChanged(w: Float, h: Float) {
        lastScreenW = w
        lastScreenH = h
        val t = ViewTransform(w, h)
        bmpWater = assets.scaled("water_tile", (512 * t.scale).toInt().coerceIn(256, 768))
        // 河床用侧视条带素材（原来那张俯视的 riverbed 拉出来是绿色竖条）
        bmpRiverbed = assets.scaled("riverbed_side", (512 * t.scale).toInt().coerceIn(256, 1024))
        bmpBobber = assets.scaled("bobber", (60 * t.scale).toInt().coerceAtLeast(16))
        bmpRod = assets.scaled("rod", (110 * t.scale).toInt().coerceAtLeast(24))
        bmpHelper = assets.scaled("helper_boat", (96 * t.scale).toInt().coerceAtLeast(20))
        fishFrames.clear()
        fishFrameDuration.clear()
        // 一张精灵可能被多个稀有度档复用，取其中最高的档决定目标尺寸
        val spriteRarity = Bestiary.allSpecies
            .groupBy { it.sprite }
            .mapValues { (_, list) -> list.maxByOrNull { it.rarity.ordinal }!!.rarity }
        for ((sprite, rarity) in spriteRarity) {
            val base = when (rarity) {
                Rarity.COMMON -> 130f
                Rarity.RARE -> 155f
                Rarity.EPIC -> 185f
                Rarity.LEGEND -> 220f
            }
            val frames = loadAnimation(sprite, (base * t.scale).toInt().coerceAtLeast(24))
            if (frames.isEmpty()) continue
            fishFrames[sprite] = frames
            // 小鱼摆尾快、大鱼慢，看起来更自然
            fishFrameDuration[sprite] = when (rarity) {
                Rarity.COMMON -> 0.070f
                Rarity.RARE -> 0.085f
                Rarity.EPIC -> 0.100f
                Rarity.LEGEND -> 0.115f
            }
        }
        // 船：侧视小船，逐帧随波起伏。尺寸按世界单位给，不再被屏幕像素放大
        boatFrames = loadAnimation("boat", (BOAT_WORLD_W * t.scale).toInt().coerceAtLeast(32))
        boatFrameDuration = 0.13f

        loadSeaweed(t)

        // 云：两种形状，按屏幕宽度预缩放
        cloudFrames = listOfNotNull(
            assets.scaled("cloud_a", (300 * t.scale).toInt().coerceAtLeast(48)),
            assets.scaled("cloud_b", (260 * t.scale).toInt().coerceAtLeast(40)),
        )

        waterShader = null
        riverbedShader = null
    }

    /**
     * 把 `<name>_anim.png` 精灵表按配置切成单帧。
     *
     * 之前这里直接读 `<name>.png`（单帧图），而实际素材是 `_anim` 后缀的
     * 动画图集，文件名对不上 → 加载失败 → 每张图退化成洋红方块。
     */
    private fun loadAnimation(name: String, targetW: Int): List<Bitmap> {
        val sheet = assets.scaled("${name}_anim", targetW) ?: return emptyList()
        val cfg = assets.frameConfig("${name}_anim") ?: return emptyList()
        val (cols, rows) = cfg
        if (cols <= 0 || rows <= 0) return emptyList()
        val fw = sheet.width / cols
        val fh = sheet.height / rows
        if (fw <= 0 || fh <= 0) return emptyList()
        val frames = ArrayList<Bitmap>(cols * rows)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x = c * fw
                val y = r * fh
                if (x + fw <= sheet.width && y + fh <= sheet.height) {
                    frames.add(Bitmap.createBitmap(sheet, x, y, fw, fh))
                }
            }
        }
        return frames
    }

    /** 取当前应显示的帧。 */
    private fun frameAt(frames: List<Bitmap>, duration: Float, phase: Float): Bitmap? {
        if (frames.isEmpty()) return null
        val cycle = duration * frames.size
        val t = ((world.time / cycle) + phase) % 1f
        return frames[(t * frames.size).toInt().coerceIn(0, frames.size - 1)]
    }

    /** 把水草精灵表切成单帧，并沿水底撒点。 */
    private fun loadSeaweed(t: ViewTransform) {
        seaweedFrames = loadAnimation("seaweed", (130 * t.scale).toInt().coerceAtLeast(32))
        if (seaweedFrames.isEmpty()) return

        // 沿水底均匀撒点，加点随机抖动避免看起来像栅栏
        val spots = mutableListOf<Triple<Float, Float, Float>>()
        val count = (Space.W / 320f).toInt()
        val rnd = kotlin.random.Random(20260924)
        for (i in 0 until count) {
            val x = Space.POND_L + (Space.POND_R - Space.POND_L) * (i + 0.5f) / count +
                (rnd.nextFloat() - 0.5f) * 160f
            // 让水草根部扎在河床上
            val y = Space.H - 60f + (rnd.nextFloat() - 0.5f) * 20f
            val scale = 0.75f + rnd.nextFloat() * 0.6f
            spots.add(Triple(x, y, scale))
        }
        seaweedSpots = spots
    }

    fun render(canvas: Canvas, t: ViewTransform) {
        shakeX = sin(world.time * 1.7f) * 2.2f
        shakeY = sin(world.time * 2.3f) * 1.6f

        val camX = world.cameraX

        canvas.save()
        canvas.translate(shakeX, shakeY)

        drawSky(canvas, t, camX)
        drawWater(canvas, t, camX)
        drawSeaweed(canvas, t, camX)
        drawBubbles(canvas, t, camX)
        drawFish(canvas, t, camX)
        drawHelpers(canvas, t, camX)
        drawBobberAndLine(canvas, t, camX)
        drawPlayerBoat(canvas, t, camX)
        drawParticles(canvas, t, camX)
        drawFloatingTexts(canvas, t, camX)

        canvas.restore()
    }

    // ---------------- 天空 ----------------

    private fun drawSky(canvas: Canvas, t: ViewTransform, camX: Float) {
        val top = t.toScreenY(0f)
        val surfaceY = t.toScreenY(Space.SURFACE_Y)
        val shader = LinearGradient(
            0f, top, 0f, surfaceY,
            intArrayOf(Color.rgb(26, 58, 72), Color.rgb(64, 126, 138)),
            null, Shader.TileMode.CLAMP
        )
        fillPaint.shader = shader
        canvas.drawRect(0f, top, t.screenW, surfaceY, fillPaint)
        fillPaint.shader = null

        // 云朵：用真实素材，随镜头视差平移
        if (cloudFrames.isEmpty()) return
        val parallax = camX * 0.22f
        val spacing = 900f
        val count = 8
        for (i in 0 until count) {
            val bmp = cloudFrames[i % cloudFrames.size]
            val wx = i * spacing - parallax
            val wrapped = ((wx % (spacing * count)) + spacing * count) % (spacing * count) - spacing
            val cx = t.toScreenX(wrapped)
            if (cx < -400f || cx > t.screenW + 400f) continue
            val cy = t.toScreenY(70f + (i % 3) * 62f)
            SpriteDraw.draw(
                canvas, bmp, cx, cy,
                scale = t.scale * (0.75f + (i % 3) * 0.22f),
                alpha = 120,
            )
        }
    }

    // ---------------- 水体 ----------------

    private fun drawWater(canvas: Canvas, t: ViewTransform, camX: Float) {
        val surfaceY = t.toScreenY(Space.SURFACE_Y)
        val bottom = t.screenH

        // 水体基础渐变
        val shader = LinearGradient(
            0f, surfaceY, 0f, bottom,
            intArrayOf(Palette.WATER_TOP, Palette.WATER_MID, Palette.WATER_BOTTOM),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        fillPaint.shader = shader
        canvas.drawRect(0f, surfaceY, t.screenW, bottom, fillPaint)
        fillPaint.shader = null

        // 水纹：BitmapShader 平铺。
        // 关键点：矩阵必须用「平移 + 缩放」一起设，只设平移的话
        // 屏幕宽度变化后平铺尺寸与画布对不上，会切出一道道竖直分块。
        val water = bmpWater
        if (water != null) {
            val shader = android.graphics.BitmapShader(
                water, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT
            )
            val m = Matrix()
            // 世界坐标 → 屏幕坐标：先按相机平移，再按缩放铺开
            m.postScale(t.scale, t.scale)
            m.postTranslate(
                -camX * t.scale + world.time * 10f,
                sin(world.time * 0.6f) * 5f,
            )
            shader.setLocalMatrix(m)
            tilePaint.shader = shader
            tilePaint.alpha = 42
            canvas.drawRect(0f, surfaceY, t.screenW, bottom, tilePaint)
            tilePaint.alpha = 255
            tilePaint.shader = null
        }

        // 水底河床：只贴在屏幕最底部一条，保持原始宽高比横向平铺，
        // 不再纵向拉伸（之前拉成绿色竖条就是这个原因）。
        val bed = bmpRiverbed
        if (bed != null) {
            val shader = android.graphics.BitmapShader(
                bed, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP
            )
            val m = Matrix()
            m.postScale(t.scale, t.scale)
            m.postTranslate(-camX * t.scale % (bed.width * t.scale), 0f)
            shader.setLocalMatrix(m)
            tilePaint.shader = shader
            tilePaint.alpha = 170
            val bedH = bed.height * t.scale
            canvas.drawRect(0f, bottom - bedH, t.screenW, bottom, tilePaint)
            tilePaint.alpha = 255
            tilePaint.shader = null
        }

        // 水面高光线
        strokePaint.color = Color.argb(150, 200, 245, 255)
        strokePaint.strokeWidth = 5f * t.scale
        val path = Path()
        path.moveTo(0f, surfaceY)
        var px = 0f
        while (px <= t.screenW) {
            val wx = t.toWorldX(px) + camX
            val wy = surfaceY + sin(wx / 220f + world.time * 1.6f) * 7f * t.scale
            path.lineTo(px, wy)
            px += 14f * t.scale
        }
        canvas.drawPath(path, strokePaint)

        // 水面亮带
        fillPaint.color = Color.argb(30, 190, 240, 255)
        canvas.drawRect(0f, surfaceY, t.screenW, surfaceY + 46f * t.scale, fillPaint)

        // 水下光柱：几道斜向的柔和光带，缓慢左右摆动
        val beamCount = 5
        for (i in 0 until beamCount) {
            val baseX = ((i + 0.5f) / beamCount) * Space.W
            val phase = i * 1.7f
            val sway = sin(world.time * 0.45f + phase) * 90f
            val topX = t.toScreenX(baseX + sway - camX)
            val width = (110f + (i % 3) * 55f) * t.scale
            val bottomY = t.screenH
            val alpha = (14 + (i % 2) * 6)
            fillPaint.color = Color.argb(alpha, 200, 245, 255)
            val path = Path()
            path.moveTo(topX - width * 0.35f, surfaceY)
            path.lineTo(topX + width * 0.35f, surfaceY)
            path.lineTo(topX + width * 0.75f, bottomY)
            path.lineTo(topX - width * 0.10f, bottomY)
            path.close()
            canvas.drawPath(path, fillPaint)
        }
    }

    // ---------------- 水下装饰 ----------------

    /**
     * 水草：逐帧播放精灵表，让水下持续有动静。
     * 每丛水草错开相位，避免整片同时摆动。
     */
    private fun drawSeaweed(canvas: Canvas, t: ViewTransform, camX: Float) {
        val frames = seaweedFrames
        if (frames.isEmpty()) return
        val halfView = t.screenW / t.scale / 2f
        val cycle = 8f / 6f   // 8 帧、6fps 播完一轮

        for ((i, spot) in seaweedSpots.withIndex()) {
            val (wx, wy, s) = spot
            if (abs(wx - camX) > halfView + 220f) continue
            val phase = i * 0.37f
            val idx = (((world.time / cycle) + phase) % 1f * frames.size).toInt()
                .coerceIn(0, frames.size - 1)
            val bmp = frames[idx]
            SpriteDraw.draw(
                canvas, bmp,
                t.toScreenX(wx - camX), t.toScreenY(wy),
                scale = s * t.scale,
                alpha = 150,
            )
        }
    }

    /** 上浮的环境气泡，配合水草一起制造"水下是活的"感觉。 */
    private fun drawBubbles(canvas: Canvas, t: ViewTransform, camX: Float) {
        val halfView = t.screenW / t.scale / 2f
        for (b in world.bubbles) {
            val bx = b.x + b.swayX(world.time)
            if (abs(bx - camX) > halfView + 60f) continue
            val sx = t.toScreenX(bx - camX)
            val sy = t.toScreenY(b.y)
            // 气泡：半透明圆 + 高光点
            paint.color = Palette.SPLASH
            paint.alpha = 90
            canvas.drawCircle(sx, sy, b.radius * t.scale, paint)
            strokePaint.color = Palette.SPLASH
            strokePaint.strokeWidth = 1.2f * t.scale
            strokePaint.alpha = 130
            canvas.drawCircle(sx, sy, b.radius * t.scale, strokePaint)
            strokePaint.alpha = 255
        }
        paint.alpha = 255
    }

    // ---------------- 鱼 ----------------

    private fun drawFish(canvas: Canvas, t: ViewTransform, camX: Float) {
        val halfView = t.screenW / t.scale / 2f
        for (f in world.fishes) {
            // 视锥裁剪：镜头外 ± 半屏的鱼不画
            if (abs(f.x - camX) > halfView + 260f) continue

            val frames = fishFrames[f.species.sprite] ?: continue
            val bmp = frameAt(
                frames,
                fishFrameDuration[f.species.sprite] ?: 0.09f,
                f.wiggle * 0.16f,   // 用各自的 wiggle 做相位，整池鱼不会同步摆尾
            ) ?: continue
            val depth = ((f.y - Space.POND_T) / (Space.POND_B - Space.POND_T)).coerceIn(0f, 1f)
            val depthScale = 0.72f + depth * 0.55f
            val alpha = (150 + (depth * 105)).toInt().coerceIn(60, 255)

            val cx = t.toScreenX(f.x - camX)
            val cy = t.toScreenY(f.y)

            if (f.kind == Rarity.LEGEND || f.kind == Rarity.EPIC) {
                val glow = rarityColor[f.kind] ?: Palette.TEXT_GOLD
                paint.alpha = (40 * (0.6f + 0.4f * sin(world.time * 2f + f.wiggle))).toInt()
                paint.color = glow
                canvas.drawCircle(cx, cy, bmp.width * 0.62f * f.scale * depthScale, paint)
                paint.alpha = 255
            }

            val rotation = when (f.state) {
                FishState.BITING -> sin(f.wiggle * 5f) * 26f
                FishState.HOOKED -> sin(f.wiggle * 6f) * 34f
                else -> sin(f.wiggle) * 5f
            }

            SpriteDraw.draw(
                canvas, bmp, cx, cy,
                scale = f.scale * depthScale * t.scale,
                alpha = alpha,
                flipX = f.facing < 0f,
                rotation = rotation,
                tintMatrix = f.species.tint.matrix(),
            )

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

    // ---------------- 钓手（水面的小船）----------------

    private fun drawHelpers(canvas: Canvas, t: ViewTransform, camX: Float) {
        val bmp = bmpHelper ?: return
        val halfView = t.screenW / t.scale / 2f
        for (h in world.helpers) {
            if (abs(h.x - camX) > halfView + 200f) continue
            val cx = t.toScreenX(h.x - camX)
            val cy = t.toScreenY(h.y)

            // 钓线垂到水下的目标鱼
            if (h.state == HelperState.CASTING) {
                strokePaint.color = Color.argb(180, 240, 240, 240)
                strokePaint.strokeWidth = 2f * t.scale
                canvas.drawLine(cx, cy + 18f * t.scale, t.toScreenX(h.lineX - camX), t.toScreenY(h.lineY), strokePaint)
            }

            SpriteDraw.draw(
                canvas, bmp, cx, cy,
                scale = t.scale,
                alpha = 240,
                flipX = h.facing < 0f,
                rotation = sin(h.wiggle * 0.6f) * 3.5f,
            )

            if (h.state == HelperState.CASTING) {
                paint.color = Palette.TEXT_GOLD
                paint.alpha = 220
                canvas.drawCircle(cx, cy - bmp.height * 0.55f, 6f * t.scale, paint)
                paint.alpha = 255
            }
        }
    }

    // ---------------- 浮标与钓线 ----------------

    private fun drawBobberAndLine(canvas: Canvas, t: ViewTransform, camX: Float) {
        val b = world.bobber
        if (!b.isActive) return

        val rodX = t.toScreenX(world.boatX - camX + 40f)
        val rodY = t.toScreenY(Space.SURFACE_Y - 120f)
        val bx = t.toScreenX(b.x - camX)
        val by = t.toScreenY(b.y)

        strokePaint.color = Color.argb(190, 245, 245, 245)
        strokePaint.strokeWidth = 2.4f * t.scale
        canvas.drawLine(rodX, rodY, bx, by, strokePaint)

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

        val bob = if (b.state == BobberState.BITE) sin(world.time * 24f) * 5f * t.scale else 0f
        SpriteDraw.draw(canvas, bmpBobber, bx, by + bob, scale = t.scale)

        if (b.state == BobberState.BITE) {
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 40f * t.scale
            textPaint.color = Palette.TEXT_BAD
            textPaint.setShadowLayer(4f * t.scale, 0f, 2f * t.scale, Palette.INK)
            canvas.drawText("点击收线！", bx, by - 62f * t.scale + bob, textPaint)
            textPaint.clearShadowLayer()
        }
    }

    // ---------------- 玩家的船（停在水面）----------------

    /**
     * 玩家的船：**侧视**小船，骑在水面线上。
     *
     * 之前这里用的是俯视贴图、还按屏幕像素放大到 150px，结果船巨大且方向不对。
     * 现在统一用侧视动画，尺寸按世界单位 [BOAT_WORLD_W] 换算，
     * 保证在任何分辨率下都占画面里固定的比例。
     */
    private fun drawPlayerBoat(canvas: Canvas, t: ViewTransform, camX: Float) {
        val bmp = frameAt(boatFrames, boatFrameDuration, 0f) ?: return
        val cx = t.toScreenX(world.boatX - camX)
        // 船底正好压在水面线上，吃水线以下被水色盖住
        val cy = t.toScreenY(Space.SURFACE_Y) - bmp.height * t.scale * 0.34f
        SpriteDraw.draw(canvas, bmp, cx, cy, scale = t.scale)

        // 鱼竿从船头伸出，跟着船体轻微摆动
        val rod = bmpRod
        if (rod != null) {
            SpriteDraw.draw(
                canvas, rod,
                cx + bmp.width * t.scale * 0.34f,
                cy - bmp.height * t.scale * 0.28f,
                scale = t.scale * 0.62f,
                rotation = -20f + sin(world.time * 1.4f) * 2.5f,
            )
        }
    }

    // ---------------- 粒子与文字 ----------------

    private fun drawParticles(canvas: Canvas, t: ViewTransform, camX: Float) {
        val halfView = t.screenW / t.scale / 2f
        for (p in world.particles) {
            if (abs(p.x - camX) > halfView + 100f) continue
            paint.color = p.color
            paint.alpha = (p.alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(t.toScreenX(p.x - camX), t.toScreenY(p.y), p.radius * t.scale, paint)
        }
        paint.alpha = 255
    }

    private fun drawFloatingTexts(canvas: Canvas, t: ViewTransform, camX: Float) {
        if (settings.hideFloatingText) return
        textPaint.textAlign = Paint.Align.CENTER
        val halfView = t.screenW / t.scale / 2f
        for (ft in world.floatingTexts) {
            if (abs(ft.x - camX) > halfView + 120f) continue
            val pop = if (ft.progress < 0.22f) {
                0.5f + (ft.progress / 0.22f) * 0.6f
            } else {
                1.1f - (ft.progress - 0.22f) * 0.12f
            }
            textPaint.textSize = 30f * ft.scale * pop * t.scale
            textPaint.color = ft.color
            textPaint.alpha = (ft.alpha * 255).toInt().coerceIn(0, 255)
            textPaint.setShadowLayer(5f * t.scale, 0f, 3f * t.scale, Palette.INK)
            canvas.drawText(ft.text, t.toScreenX(ft.x - camX), t.toScreenY(ft.y), textPaint)
        }
        textPaint.clearShadowLayer()
        textPaint.alpha = 255
    }
}
