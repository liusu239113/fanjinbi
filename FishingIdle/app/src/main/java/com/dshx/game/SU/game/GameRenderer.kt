package com.dshx.game.SU.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
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
        /**
         * 船的立绘高度（世界单位），与逻辑层共用 [BoatArt.HEIGHT]。
         *
         * 按**高度**而不是宽度统一尺寸 —— 玩家与帮手的素材宽高比不同，
         * 按宽度统一会把正方形那张放大成"巨型船+巨型竿"。
         */
        const val BOAT_WORLD_H = BoatArt.HEIGHT

        /** 主角立绘高度：比帮手矮，两条船看起来才是一个体量。 */
        const val PLAYER_BOAT_WORLD_H = BoatArt.PLAYER_HEIGHT

        /** 主角船下的水花宽度（世界单位），随主角立绘一起缩。 */
        const val PLAYER_SPLASH_W = 150f

        /**
         * 抛竿动画帧的目标高度（世界单位）。
         *
         * 这两个数是按"动画里的船体看起来和静止立绘一样大"量出来的：
         * 甩竿会把画面撑大，帧高直接照抄立绘高会让船在抛竿的一瞬间缩小。
         * 换抛竿素材要重新量（tools/measure_boat.py 会给建议值）。
         */
        const val PLAYER_CAST_H = 134f
        const val HELPER_CAST_H = 140f

        /** 鹈鹕的绘制高度（世界单位）—— 比船大一圈，才有"天上飞的大鸟"的体量。 */
        const val PELICAN_WORLD_H = 150f

        /** 渔网特效的宽度（世界单位），要能罩住一网鱼。 */
        const val NET_WORLD_W = 300f

        /** 无人机悬停时的绘制高度（世界单位）。 */
        const val DRONE_WORLD_H = 110f

        /** 潜水员的绘制高度（世界单位）。 */
        const val DIVER_WORLD_H = 120f

        /** 宝箱的绘制宽度（世界单位）。 */
        const val CHEST_WORLD_W = 120f

        /** 无人机悬停在船上方多高（世界单位）。 */
        const val DRONE_HOVER_H = 300f
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
    private var bmpHelper: Bitmap? = null
    /** 玩家立绘：钓手 + 小船 + 鱼竿一体。 */
    private var bmpPlayerBoat: Bitmap? = null
    /** 主角船底的水花（帮手立绘自带，主角单独一张）。 */
    private var bmpPlayerSplash: Bitmap? = null

    /** 抛竿逐帧动画：主角与帮手各一套。 */
    private var playerCastFrames: List<Bitmap> = emptyList()
    private var helperCastFrames: List<Bitmap> = emptyList()

    /** 每种鱼的逐帧动画。key 为精灵名（不含 _anim 后缀）。 */
    private val fishFrames = HashMap<String, List<Bitmap>>()
    /** 每种鱼每帧的播放时长（秒）。 */
    private val fishFrameDuration = HashMap<String, Float>()

    /** 水下装饰：海草用帧动画，随机分布在水底。 */
    private var seaweedFrames: List<Bitmap> = emptyList()
    private var seaweedSpots: List<Triple<Float, Float, Float>> = emptyList()

    /** 天空的云（当前水域的那一套）。 */
    private var cloudFrames: List<Bitmap> = emptyList()

    /** 当前已加载的环境素材属于哪张水域。 */
    private var envMapId: String = ""

    /**
     * 最近一次摆放船只得到的屏幕矩形（left/top/width/height）。
     * 钓线要先取竿尖坐标再画船，所以摆放与描画分成两步。
     */
    private val boatRect = FloatArray(4)

    /**
     * 抛竿动画**每一帧**的船底比例高度与竿尖位置。
     *
     * 逐帧动画每格的留白都不一样（裁到内容包围盒再补边），
     * 用同一个比例会让船每帧上下跳；竿尖更是每帧都在动。
     */
    /** 鹈鹕的飞行帧（后期单位）。 */
    private var pelicanFrames: List<Bitmap> = emptyList()

    /** 拖网特效用的网。 */
    private var bmpNet: Bitmap? = null

    /** 后期第二梯队：无人机 / 潜水员 / 宝箱。 */
    private var droneFrames: List<Bitmap> = emptyList()
    private var diverFrames: List<Bitmap> = emptyList()
    private var bmpChest: Bitmap? = null

    private var playerCastHull: FloatArray = floatArrayOf(BoatArt.PLAYER_HULL_FRAC)
    private var helperCastHull: FloatArray = floatArrayOf(BoatArt.HELPER_HULL_FRAC)
    private var playerCastTips: List<FloatArray> = emptyList()
    private var helperCastTips: List<FloatArray> = emptyList()

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
        // 浮漂：用小号素材，别盖住整片水域
        bmpBobber = assets.scaled("bobber_small", (30 * t.scale).toInt().coerceAtLeast(12))
        // 主角与帮手各自按**自己的立绘高度**画：
        // 主角素材更宽（384×256），照帮手的高度直接画会宽出 50%，
        // 船比帮手大一圈。PLAYER_BOAT_WORLD_H 就是为这个单列的。
        bmpPlayerBoat = assets.scaledToHeight(
            "player_boat", (PLAYER_BOAT_WORLD_H * t.scale).toInt().coerceAtLeast(20),
        )
        bmpHelper = assets.scaledToHeight(
            "helper_boat", (BOAT_WORLD_H * t.scale).toInt().coerceAtLeast(28),
        )
        // 主角船底的水花：和帮手立绘里自带的那圈同一套视觉
        bmpPlayerSplash = assets.scaled("boat_splash", (PLAYER_SPLASH_W * t.scale).toInt().coerceAtLeast(24))
        // 抛竿逐帧动画（素材缺失时退回静止立绘，不会画出空白）。
        // 目标高度按"船体看上去和静止立绘一样大"折算出来（量自素材）：
        // 动画帧里竿子甩出去会把画面撑大，按帧高直接等于立绘高会显得船变小。
        playerCastFrames = loadAnimation(
            "player_cast", 0, (PLAYER_CAST_H * t.scale).toInt().coerceAtLeast(24),
        )
        helperCastFrames = loadAnimation(
            "helper_cast", 0, (HELPER_CAST_H * t.scale).toInt().coerceAtLeast(20),
        )
        // 后期单位：鹈鹕按高度缩放；渔网按宽度缩放（要能罩住几条鱼）
        pelicanFrames = loadAnimation(
            "pelican", 0, (PELICAN_WORLD_H * t.scale).toInt().coerceAtLeast(24),
        )
        bmpNet = assets.scaled("fishing_net", (NET_WORLD_W * t.scale).toInt().coerceAtLeast(24))
        droneFrames = loadAnimation(
            "drone", 0, (DRONE_WORLD_H * t.scale).toInt().coerceAtLeast(20),
        )
        diverFrames = loadAnimation(
            "diver", 0, (DIVER_WORLD_H * t.scale).toInt().coerceAtLeast(20),
        )
        bmpChest = assets.scaled("chest", (CHEST_WORLD_W * t.scale).toInt().coerceAtLeast(24))
        // 动画帧的画布尺幅和静止立绘不同，船底比例与竿尖都得逐帧量，
        // 否则一抛竿船体就会上下跳、线也不再接在竿尖上
        playerCastHull = if (playerCastFrames.isEmpty()) {
            floatArrayOf(BoatArt.PLAYER_HULL_FRAC)
        } else {
            FloatArray(playerCastFrames.size) { hullFracOf(playerCastFrames[it]) }
        }
        helperCastHull = if (helperCastFrames.isEmpty()) {
            floatArrayOf(BoatArt.HELPER_HULL_FRAC)
        } else {
            FloatArray(helperCastFrames.size) { hullFracOf(helperCastFrames[it]) }
        }
        playerCastTips = playerCastFrames.map { tipFracOf(it) }
        helperCastTips = helperCastFrames.map { tipFracOf(it) }
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
            // base 是**整张图集**的目标宽度（4 帧并排），单帧要除以列数
            val frames = loadAnimation(sprite, (base / 4f * t.scale).toInt().coerceAtLeast(24))
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
        // 环境素材随水域切换重载（换成当前水域的云 / 水草 / 河床）
        envMapId = ""
        ensureEnv(t)

        waterShader = null
        riverbedShader = null
    }

    /**
     * 换水域时重载环境素材。
     *
     * 之前所有水域共用同一张云、同一丛水草、同一条河床 ——
     * 解锁新地图后水下看着和村口小河一模一样。现在每张水域都有自己的
     * 云朵、水草帧动画、河床与水体配色，解锁新区域立刻能看出区别。
     */
    private fun ensureEnv(t: ViewTransform) {
        val map = world.currentMap
        if (map.env.id == envMapId) return
        envMapId = map.env.id

        // 云：一张图集里有好几朵不同形状的云，轮流用（单帧目标约 200 世界单位宽）
        cloudFrames = loadAnimation(
            map.env.cloud,
            (200 * t.scale).toInt().coerceAtLeast(36),
        ).ifEmpty {
            listOfNotNull(
                assets.scaled("cloud_c", (170 * t.scale).toInt().coerceAtLeast(36)),
                assets.scaled("cloud_c", (120 * t.scale).toInt().coerceAtLeast(28)),
            )
        }

        // 水草：每张水域一套独立的帧动画（单帧目标宽约 33 世界单位）
        seaweedFrames = loadAnimation(
            map.env.seaweed,
            (33 * t.scale).toInt().coerceAtLeast(24),
        ).ifEmpty { loadAnimation("seaweed", (33 * t.scale).toInt().coerceAtLeast(24)) }
        buildSeaweedSpots(map.env.id)

        // 水体与河床共用同一套底纹，靠每张图的染色（waterTint/bedTint）
        // 和渐变配色区分 —— 既省一份素材，又保证风格统一
        bmpRiverbed = assets.scaled("riverbed_side", (512 * t.scale).toInt().coerceIn(256, 1024))
        bmpWater = assets.scaled("water_tile", (512 * t.scale).toInt().coerceIn(256, 768))
        waterShader = null
        riverbedShader = null
    }

    /**
     * 把 `<name>_anim.png` 精灵表切成单帧。
     *
     * 之前这里直接读 `<name>.png`（单帧图），而实际素材是 `_anim` 后缀的
     * 动画图集，文件名对不上 → 加载失败 → 每张图退化成洋红方块。
     *
     * [targetFrameW] / [targetFrameH] 是**单帧**的目标像素尺寸（至少给一个）：
     * 整张图集会按同样比例缩放再切格 —— 注意别按整张表的宽度算，
     * 4 列的图集按表宽缩放会让每帧只有预期的四分之一大。
     */
    private fun loadAnimation(name: String, targetFrameW: Int, targetFrameH: Int = 0): List<Bitmap> {
        val cfg = assets.frameConfig("${name}_anim") ?: return emptyList()
        val (cols, rows) = cfg
        if (cols <= 0 || rows <= 0) return emptyList()
        val sheet = if (targetFrameH > 0) {
            assets.scaledToHeight("${name}_anim", targetFrameH * rows)
        } else {
            assets.scaled("${name}_anim", targetFrameW * cols)
        } ?: return emptyList()
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

    /** 给平铺纹理套一层乘法染色（白色 = 保持原色）。 */
    private fun applyTint(color: Int) {
        tilePaint.colorFilter =
            if (color == Color.WHITE) null else PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
    }

    /** 取颜色的 RGB 配上指定 alpha。 */
    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    /** 取当前应显示的帧。 */
    private fun frameAt(frames: List<Bitmap>, duration: Float, phase: Float): Bitmap? {
        if (frames.isEmpty()) return null
        val cycle = duration * frames.size
        val t = ((world.time / cycle) + phase) % 1f
        return frames[(t * frames.size).toInt().coerceIn(0, frames.size - 1)]
    }

    /**
     * 沿水底给水草撒点。
     *
     * 随机种子按水域区分：同一张图每次进来布局一致（不会每次重进都跳位置），
     * 不同图之间布局不同。密度按水域配置微调，芦苇荡密、急流滩稀。
     */
    private fun buildSeaweedSpots(mapId: String) {
        buildSpots(mapId, spacing = 320f)
    }

    private fun buildSpots(mapId: String, spacing: Float) {
        if (seaweedFrames.isEmpty()) {
            seaweedSpots = emptyList()
            return
        }

        // 沿水底均匀撒点，加点随机抖动避免看起来像栅栏
        val spots = mutableListOf<Triple<Float, Float, Float>>()
        val count = (Space.W / spacing).toInt()
        var seed = 20260924L
        for (ch in mapId) seed = seed * 31 + ch.code
        val rnd = kotlin.random.Random(seed)
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
        // 换水域后立刻把环境素材换成新图的（云 / 水草 / 河床）
        ensureEnv(t)

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
        drawSonarMarks(canvas, t, camX)
        drawDiver(canvas, t, camX)
        drawNetEffect(canvas, t, camX)
        drawChest(canvas, t, camX)
        drawHelpers(canvas, t, camX)
        drawBobberAndLine(canvas, t, camX)
        drawPelicans(canvas, t, camX)
        drawDrone(canvas, t, camX)
        drawPlayerBoat(canvas, t, camX)
        drawFinderLabels(canvas, t, camX)
        drawParticles(canvas, t, camX)
        drawFloatingTexts(canvas, t, camX)

        canvas.restore()
    }

    // ---------------- 天空 ----------------

    private fun drawSky(canvas: Canvas, t: ViewTransform, camX: Float) {
        val env = world.currentMap.env
        val top = t.toScreenY(0f)
        val surfaceY = t.toScreenY(Space.SURFACE_Y)
        val shader = LinearGradient(
            0f, top, 0f, surfaceY,
            intArrayOf(env.skyTop, env.skyBottom),
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
        val env = world.currentMap.env
        val surfaceY = t.toScreenY(Space.SURFACE_Y)
        val bottom = t.screenH

        // 水体基础渐变（每张水域一套配色）
        val shader = LinearGradient(
            0f, surfaceY, 0f, bottom,
            intArrayOf(env.waterTop, env.waterMid, env.waterBottom),
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
            applyTint(env.waterTint)
            tilePaint.alpha = 42
            canvas.drawRect(0f, surfaceY, t.screenW, bottom, tilePaint)
            tilePaint.alpha = 255
            tilePaint.shader = null
            applyTint(Color.WHITE)
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
            applyTint(env.bedTint)
            tilePaint.alpha = 170
            val bedH = bed.height * t.scale
            canvas.drawRect(0f, bottom - bedH, t.screenW, bottom, tilePaint)
            tilePaint.alpha = 255
            tilePaint.shader = null
            applyTint(Color.WHITE)
        }

        // 水面高光线
        strokePaint.color = withAlpha(env.beam, 150)
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
        fillPaint.color = withAlpha(env.beam, 30)
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
            fillPaint.color = withAlpha(env.beam, alpha)
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

    // ---------------- 船只摆放（主角与帮手共用）----------------

    /**
     * 量一张立绘里"船底"（木色船体最低点）的比例高度。
     *
     * 为什么不直接取"最下面的不透明像素"：帮手立绘里船底还带一圈蓝色水花，
     * 取最低像素会量到水花下沿，船就被抬高了一截。这里只认木色船体，
     * 水花和水面高光都会被跳过。
     *
     * 逐帧动画的图集裁切方式与静止立绘不同，帧内留白也不一样，
     * 所以每个动画帧都要各量一次 —— 否则一抛竿船就会上下跳。
     */
    private fun hullFracOf(bmp: Bitmap): Float {
        val w = bmp.width
        val row = IntArray(w)
        for (y in bmp.height - 1 downTo 0) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val c = row[x]
                if (Color.alpha(c) <= 12) continue
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                // 木色：暖色、红明显大于蓝
                if (r > b + 25 && r > 90 && g > b) return (y + 1f) / bmp.height
            }
        }
        return bottomContentFrac(bmp)
    }

    /** 兜底：整张图最下面的不透明像素。 */
    private fun bottomContentFrac(bmp: Bitmap): Float {
        val w = bmp.width
        val row = IntArray(w)
        for (y in bmp.height - 1 downTo 0) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                if (Color.alpha(row[x]) > 12) return (y + 1f) / bmp.height
            }
        }
        return 0.9f
    }

    /**
     * 量竿尖在立绘里的位置：取"最靠上、且偏右"的内容像素。
     * 抛竿动画的每一帧竿尖角度都不同，只能逐帧量。
     */
    private fun tipFracOf(bmp: Bitmap): FloatArray {
        val w = bmp.width
        val h = bmp.height
        val row = IntArray(w)
        var bestScore = Float.MAX_VALUE
        var bestX = 0.9f
        var bestY = 0.25f
        for (y in 0 until h) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                if (Color.alpha(row[x]) <= 40) continue
                val score = y.toFloat() / h - 0.55f * (x.toFloat() / w)
                if (score < bestScore) {
                    bestScore = score
                    bestX = (x + 1f) / w
                    bestY = y.toFloat() / h
                }
            }
        }
        return floatArrayOf(bestX, bestY)
    }

    /**
     * 算出立绘在屏幕上的矩形（只算不画，供钓线先取竿尖坐标）。
     * 结果写进 [boatRect]，顺序是 left / top / width / height。
     */
    private fun layoutBoat(
        bmp: Bitmap,
        worldX: Float,
        waterY: Float,
        hullFrac: Float,
        t: ViewTransform,
        camX: Float,
    ) {
        val w = bmp.width * t.scale
        val h = bmp.height * t.scale
        // 立绘里的船底像素正好落在水位线上 —— 主角和帮手用的是同一套规则
        val cx = t.toScreenX(worldX - camX)
        val cy = t.toScreenY(waterY) - (hullFrac - 0.5f) * h
        boatRect[0] = cx - w / 2f
        boatRect[1] = cy - h / 2f
        boatRect[2] = w
        boatRect[3] = h
    }

    /** [boatRect] 内某个比例点的屏幕 x（朝左时按镜像取）。 */
    private fun anchorX(fracX: Float, facing: Float): Float {
        val fx = if (facing < 0f) 1f - fracX else fracX
        return boatRect[0] + boatRect[2] * fx
    }

    /** [boatRect] 内某个比例点的屏幕 y。 */
    private fun anchorY(fracY: Float): Float = boatRect[1] + boatRect[3] * fracY

    // ---------------- 钓手（水面的小船）----------------

    private fun drawHelpers(canvas: Canvas, t: ViewTransform, camX: Float) {
        val bmp = bmpHelper ?: return
        val halfView = t.screenW / t.scale / 2f
        val castFrames = helperCastFrames
        for (h in world.helpers) {
            if (abs(h.x - camX) > halfView + 220f) continue

            // 抛竿时播逐帧动画（甩竿），平时用静止立绘
            val playing = castFrames.isNotEmpty() && h.castAnim >= 0f
            val idx = if (playing) castFrameIndex(h.castAnim, castFrames.size) else -1
            val frame = if (idx >= 0) castFrames[idx] else bmp
            val hullFrac = if (idx >= 0) helperCastHull[idx] else BoatArt.HELPER_HULL_FRAC

            layoutBoat(frame, h.x, h.y, hullFrac, t, camX)
            val tip = if (idx >= 0) helperCastTips.getOrNull(idx) else null
            val tipX = anchorX(tip?.get(0) ?: BoatArt.HELPER_ROD_TIP_X, h.facing)
            val tipY = anchorY(tip?.get(1) ?: BoatArt.HELPER_ROD_TIP_Y)

            // 钓线：从**竿尖**垂到每条目标鱼（并行作业时会有好几条）
            for (f in h.targets) {
                strokePaint.color = Color.argb(170, 240, 240, 240)
                strokePaint.strokeWidth = 1.8f * t.scale
                canvas.drawLine(tipX, tipY, t.toScreenX(f.x - camX), t.toScreenY(f.y), strokePaint)
            }

            SpriteDraw.draw(
                canvas, frame, boatRect[0] + boatRect[2] / 2f, boatRect[1] + boatRect[3] / 2f,
                scale = t.scale,
                alpha = 240,
                flipX = h.facing < 0f,
                rotation = sin(h.wiggle * 0.6f) * 3.5f,
            )

            // 竿尖上一个小金点：表示这条船正在作业
            if (h.targets.isNotEmpty()) {
                paint.color = Palette.TEXT_GOLD
                paint.alpha = 220
                canvas.drawCircle(tipX, tipY, 4f * t.scale, paint)
                paint.alpha = 255
            }
        }
    }

    // ---------------- 后期单位：鹈鹕与拖网 ----------------

    /** 鹈鹕：在水面上方盘旋，俯冲时扎向鱼。 */
    private fun drawPelicans(canvas: Canvas, t: ViewTransform, camX: Float) {
        val frames = pelicanFrames
        if (frames.isEmpty() || world.pelicans.isEmpty()) return
        val halfView = t.screenW / t.scale / 2f
        val waterScreenY = t.toScreenY(Space.BOAT_WATERLINE)
        for (p in world.pelicans) {
            if (abs(p.x - camX) > halfView + 260f) continue
            val cx = t.toScreenX(p.x - camX)
            val cy = t.toScreenY(p.y)

            // 水面上的影子：有了它鹈鹕才像"在天上"而不是贴在水面
            paint.color = Color.argb(58, 0, 0, 0)
            canvas.drawOval(
                RectF(
                    cx - 40f * t.scale, waterScreenY - 7f * t.scale,
                    cx + 40f * t.scale, waterScreenY + 9f * t.scale,
                ),
                paint,
            )
            paint.color = Color.WHITE

            // 扇翅：每 0.3 秒换一帧，俯冲时扇得更快
            val idx = (p.flap / 0.30f).toInt().mod(frames.size)
            SpriteDraw.draw(
                canvas, frames[idx], cx, cy,
                scale = t.scale,
                flipX = p.facing < 0f,
            )
        }
    }

    /** 无人机：悬停在船的正上方，桨叶一直在转。 */
    private fun drawDrone(canvas: Canvas, t: ViewTransform, camX: Float) {
        val frames = droneFrames
        if (frames.isEmpty() || !world.gameState.droneOwned) return
        val cx = t.toScreenX(world.boatX - camX)
        val cy = t.toScreenY(Space.BOAT_WATERLINE) -
            (DRONE_HOVER_H + sin(world.time * 1.7f) * 10f) * t.scale
        val idx = (world.time / 0.11f).toInt().mod(frames.size)
        SpriteDraw.draw(canvas, frames[idx], cx, cy, scale = t.scale)
    }

    /** 潜水员：从船边下潜到水底，摸到珍珠再浮上来。 */
    private fun drawDiver(canvas: Canvas, t: ViewTransform, camX: Float) {
        val frames = diverFrames
        val d = world.diver ?: return
        if (frames.isEmpty()) return
        val cx = t.toScreenX(world.boatX - 26f - camX)
        val surfaceY = t.toScreenY(Space.BOAT_WATERLINE)
        val bottomY = t.toScreenY(Space.POND_B - 80f)
        val cy = surfaceY + (bottomY - surfaceY) * d.depth
        // 气泡：越深越少，视觉上像在往下游
        val idx = (world.time / 0.16f).toInt().mod(frames.size)
        SpriteDraw.draw(canvas, frames[idx], cx, cy, scale = t.scale, alpha = 230)
    }

    /** 沉船宝箱：浮在水里上下漂，快消失时开始闪。 */
    private fun drawChest(canvas: Canvas, t: ViewTransform, camX: Float) {
        val bmp = bmpChest ?: return
        val c = world.chest ?: return
        val halfView = t.screenW / t.scale / 2f
        if (abs(c.x - camX) > halfView + 200f) return

        val cx = t.toScreenX(c.x - camX)
        val cy = t.toScreenY(c.y) + sin(c.bob * 1.6f) * 8f * t.scale

        // 一圈金色光晕：告诉玩家"这个能点"
        val pulse = (sin(c.bob * 3.2f) * 0.5f + 0.5f)
        strokePaint.color = Color.argb((70 + 90 * pulse).toInt(), 255, 214, 120)
        strokePaint.strokeWidth = 3f * t.scale
        canvas.drawCircle(cx, cy, (Chest.TAP_RADIUS * 0.55f) * t.scale, strokePaint)

        // 快消失了就闪烁提醒
        val blink = c.life < 4f && ((c.bob * 6f).toInt() % 2 == 0)
        SpriteDraw.draw(canvas, bmp, cx, cy, scale = t.scale, alpha = if (blink) 110 else 255)
    }

    /** 声呐标记：给稀有及以上的鱼加一个稀有度颜色的光点。 */
    private fun drawSonarMarks(canvas: Canvas, t: ViewTransform, camX: Float) {
        if (!world.gameState.sonarOwned) return
        val halfView = t.screenW / t.scale / 2f
        for (f in world.fishes) {
            if (f.kind == Rarity.COMMON) continue
            if (f.state != FishState.SWIMMING) continue
            if (abs(f.x - camX) > halfView + 80f) continue
            val cx = t.toScreenX(f.x - camX)
            val cy = t.toScreenY(f.y) - 30f * t.scale
            val color = rarityColor[f.kind] ?: continue
            val pulse = (sin(world.time * 4f + f.wiggle) * 0.5f + 0.5f)
            paint.color = color
            paint.alpha = (120 + 120 * pulse).toInt()
            canvas.drawCircle(cx, cy, (5f + 3f * pulse) * t.scale, paint)
            paint.alpha = 255
        }
    }

    /** 鱼探仪：在每条鱼头顶标出它值多少钱。 */
    private fun drawFinderLabels(canvas: Canvas, t: ViewTransform, camX: Float) {
        if (!world.gameState.fishFinderOwned) return
        val halfView = t.screenW / t.scale / 2f
        for (f in world.fishes) {
            if (f.state != FishState.SWIMMING) continue
            if (abs(f.x - camX) > halfView + 80f) continue
            val value = world.gameState.catchValue(f.species, world.currentMap)
            textPaint.textSize = 11f * t.scale
            textPaint.color = Palette.TEXT_GOLD
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(
                formatNumber(value),
                t.toScreenX(f.x - camX),
                t.toScreenY(f.y) - 16f * t.scale,
                textPaint,
            )
            textPaint.textAlign = Paint.Align.LEFT
        }
    }

    /** 拖网特效：网从天上落下来罩住这一网鱼，然后淡出。 */
    private fun drawNetEffect(canvas: Canvas, t: ViewTransform, camX: Float) {
        val net = bmpNet ?: return
        if (world.netEffect < 0f) return

        val p = 1f - (world.netEffect / World.NET_EFFECT_TIME).coerceIn(0f, 1f)
        val fall = (p / 0.45f).coerceIn(0f, 1f)          // 0→1 落网
        val fade = if (p < 0.6f) 1f else 1f - ((p - 0.6f) / 0.4f).coerceIn(0f, 1f)
        val cx = t.toScreenX(world.netEffectX - camX)
        val cy = t.toScreenY(world.netEffectY) - (1f - fall) * 200f * t.scale
        SpriteDraw.draw(
            canvas, net, cx, cy,
            scale = t.scale * (0.8f + 0.2f * fall),
            alpha = (255 * fade).toInt().coerceIn(0, 255),
        )
    }

    // ---------------- 浮标与钓线 ----------------

    private fun drawBobberAndLine(canvas: Canvas, t: ViewTransform, camX: Float) {
        val b = world.bobber
        if (!b.isActive) return

        // 线的起点是**竿尖**：先按当前立绘（含抛竿动画帧）摆一次位置，
        // 从里面取竿尖的屏幕坐标。之前这里写死成"船右边 40、水面上方 120"，
        // 船一翻身或者换素材，线就从船身上穿过去了。
        val idx = playerCastIndex()
        val frame = if (idx >= 0) playerCastFrames[idx] else bmpPlayerBoat
        val rodX: Float
        val rodY: Float
        if (frame != null) {
            layoutBoat(
                frame, world.boatX, playerWaterY(),
                if (idx >= 0) playerCastHull[idx] else BoatArt.PLAYER_HULL_FRAC, t, camX,
            )
            val tip = if (idx >= 0) playerCastTips.getOrNull(idx) else null
            rodX = anchorX(tip?.get(0) ?: BoatArt.PLAYER_ROD_TIP_X, world.boatFacing)
            rodY = anchorY(tip?.get(1) ?: BoatArt.PLAYER_ROD_TIP_Y)
        } else {
            rodX = t.toScreenX(world.boatX - camX)
            rodY = t.toScreenY(Space.BOAT_WATERLINE - 100f)
        }

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
     * 玩家的船：**带钓手立绘的侧视小船**，船底贴在水位线上。
     *
     * 用 [bmpPlayerBoat]（人物+船+竿一体的立绘），而不是
     * 「空船 + 单独一根放大鱼竿」—— 后者会画出一根巨大的竿，
     * 且和帮手的立绘风格对不上。
     *
     * 与帮手共用 [layoutBoat] 的对齐规则，并按 [World.boatFacing] 左右翻转。
     */
    private fun drawPlayerBoat(canvas: Canvas, t: ViewTransform, camX: Float) {
        val bmp = bmpPlayerBoat ?: return
        val facing = world.boatFacing
        val waterY = playerWaterY()

        // 水花先画（在船底下），船再压上去 —— 和帮手立绘下面那圈同一个效果
        drawPlayerSplash(canvas, t, camX, waterY)

        // 抛竿时播逐帧动画，其余时间用静止立绘
        val idx = playerCastIndex()
        val frame = if (idx >= 0) playerCastFrames[idx] else bmp
        val hullFrac = if (idx >= 0) playerCastHull[idx] else BoatArt.PLAYER_HULL_FRAC

        layoutBoat(frame, world.boatX, waterY, hullFrac, t, camX)
        SpriteDraw.draw(
            canvas, frame, boatRect[0] + boatRect[2] / 2f, boatRect[1] + boatRect[3] / 2f,
            scale = t.scale,
            flipX = facing < 0f,
        )
    }

    /** 抛竿动画当前该播第几帧。 */
    private fun castFrameIndex(animTime: Float, frameCount: Int): Int =
        ((animTime / Helper.CAST_ANIM_TIME) * frameCount)
            .toInt().coerceIn(0, frameCount - 1)

    /** 主角当前该显示的抛竿帧下标；不在抛竿动画中则返回 -1。 */
    private fun playerCastIndex(): Int {
        if (playerCastFrames.isEmpty() || world.castAnim < 0f) return -1
        return castFrameIndex(world.castAnim, playerCastFrames.size)
    }

    /** 主角船当前的水位线（带轻微起伏，让船看着是浮着的）。 */
    private fun playerWaterY(): Float =
        Space.BOAT_WATERLINE + sin(world.time * 1.5f) * 3f

    /** 主角船底的水花：静止时轻轻荡，划船时更宽更亮。 */
    private fun drawPlayerSplash(canvas: Canvas, t: ViewTransform, camX: Float, waterY: Float) {
        val splash = bmpPlayerSplash ?: return
        val cx = t.toScreenX(world.boatX - camX)
        val cy = t.toScreenY(waterY)
        val moving = world.isBoatMoving()
        val breathe = 1f + sin(world.time * 2.4f) * 0.06f
        SpriteDraw.draw(
            canvas, splash, cx, cy,
            scale = t.scale * breathe * (if (moving) 1.12f else 1f),
            alpha = if (moving) 255 else 225,
        )

        // 划船时在船尾拖出一串涟漪，表现"正在破水前进"
        if (moving) {
            val dir = world.boatFacing
            for (i in 1..3) {
                val p = ((world.time * 1.6f + i * 0.33f) % 1f)
                val rx = cx - dir * (30f + p * 90f) * t.scale
                val ry = cy + (i - 1) * 3f * t.scale
                strokePaint.color = Color.argb(((1f - p) * 120).toInt(), 220, 250, 255)
                strokePaint.strokeWidth = 2f * t.scale
                canvas.drawOval(
                    RectF(
                        rx - 26f * p * t.scale - 8f * t.scale,
                        ry - 7f * p * t.scale - 3f * t.scale,
                        rx + 26f * p * t.scale + 8f * t.scale,
                        ry + 7f * p * t.scale + 3f * t.scale,
                    ), strokePaint
                )
            }
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
