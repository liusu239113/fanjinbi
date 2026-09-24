package com.dshx.game.SU

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dshx.game.SU.game.Assets
import com.dshx.game.SU.game.Attribute
import com.dshx.game.SU.game.AudioManager
import com.dshx.game.SU.game.DailyQuests
import com.dshx.game.SU.game.DailyTracker
import com.dshx.game.SU.game.FishingMap
import com.dshx.game.SU.game.GameState
import com.dshx.game.SU.game.GameView
import com.dshx.game.SU.game.OfflineEarnings
import com.dshx.game.SU.game.RewardAds
import com.dshx.game.SU.game.SaveManager
import com.dshx.game.SU.game.Settings
import com.dshx.game.SU.game.SkillTree
import com.dshx.game.SU.game.World
import com.dshx.game.SU.ads.AdDaily
import com.dshx.game.SU.ui.AdGift
import com.dshx.game.SU.ui.AdGiftButton
import com.dshx.game.SU.ui.AdGiftDialog
import com.dshx.game.SU.ui.AdLoadingOverlay
import com.dshx.game.SU.ui.AchievementToast
import com.dshx.game.SU.ui.UnlockPopupCard
import com.dshx.game.SU.ui.AppFontFamily
import com.dshx.game.SU.ui.BottomBar
import com.dshx.game.SU.ui.BuffStrip
import com.dshx.game.SU.ui.CatchStrip
import com.dshx.game.SU.ui.ComplianceBlockedGate
import com.dshx.game.SU.ui.ComplianceCheckingGate
import com.dshx.game.SU.ui.LoginGate
import com.dshx.game.SU.ui.PrivacyGate
import com.dshx.game.SU.ui.MainMenu
import com.dshx.game.SU.ui.MenuPanel
import com.dshx.game.SU.ui.PrestigePanel
import com.dshx.game.SU.ui.MoneyBar
import com.dshx.game.SU.ui.MoveButtons
import com.dshx.game.SU.ui.OfflinePanel
import com.dshx.game.SU.ui.ResetConfirmDialog
import com.dshx.game.SU.ui.ShopPanel
import com.dshx.game.SU.ui.UITheme
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip

class MainActivity : ComponentActivity() {

    private lateinit var assets: Assets
    private lateinit var audio: AudioManager
    private lateinit var saveManager: SaveManager
    private lateinit var gameState: GameState
    private lateinit var settings: Settings
    private lateinit var world: World

    // ---- 广告 / 登录 / 防沉迷 门控状态 ----
    // 三层门控顺序固定：隐私政策 -> TapTap 登录 -> 防沉迷认证。
    // 只有防沉迷回调 LOGIN_SUCCESS(500) 才会把 complianceChecking 清掉。
    //
    // 全部用 Compose 的 mutableStateOf：这些值由 SDK 回调（可能在子线程）
    // 改写，用普通 var 的话 UI 不会重组 —— 门控永远不消失。
    private var privacyAccepted by mutableStateOf(false)
    private var loginGate by mutableStateOf(false)
    private var complianceChecking by mutableStateOf(false)
    private var complianceBlocked by mutableStateOf(false)
    private var complianceMsg by mutableStateOf("")
    private var loginBusy by mutableStateOf(false)
    private var loginMsg by mutableStateOf("")

    // 广告加载浮层：点广告到真正播放之间给玩家反馈，避免"点了没反应"
    private var adLoading by mutableStateOf(false)
    private var adLoadingSince = 0L

    /** 轻量提示（相当于 Toast），由 [showToast] 写入、游戏内顶部展示。 */
    private var toastText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全屏沉浸：隐藏状态栏与导航栏，游戏画面铺满整屏
        enableImmersiveMode()

        assets = Assets(this)
        settings = Settings()
        audio = AudioManager(this, settings)
        saveManager = SaveManager(this)
        gameState = GameState()

        // 读取存档：恢复金币 + 重放购买重建所有属性
        saveManager.load(gameState, settings)
        world = World(gameState)

        audio.preload(this, AudioManager.SFX)
        audio.playBgm(this, "bgm_main")

        // 首启：没同意过隐私政策就先弹隐私页，同意之后才初始化任何 SDK。
        // 与参考项目一致：未同意时**不要**去动 loginGate —— 隐私页在最上层，
        // 它下面的登录页本来就被盖住；等玩家点「同意」再由 setupTap() 决定
        // 是进登录页还是直接续校验。
        privacyAccepted = com.dshx.game.SU.ads.AdPrivacy.isAccepted(this)
        if (privacyAccepted) {
            setupAds()
            setupTap()
        }

        setContent {
            // 全局套用游戏字体：所有 Text 默认都用 AppFontFamily，
            // 各组件里就不必逐个指定 fontFamily 了。
            CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = AppFontFamily)) {
                FishingGameScreen()
            }
        }
    }

    // ---------------- 广告 / TapTap / 防沉迷 接线 ----------------

    /** 同意隐私政策之后才能调用：初始化广告 SDK 并把播放实现注入 RewardAds。 */
    private fun setupAds() {
        com.dshx.game.SU.ads.AdBridge.setup(this)
    }

    /**
     * 初始化 TapTap（登录 + 防沉迷）。必须在同意隐私政策之后调用。
     */
    private fun setupTap() {
        com.dshx.game.SU.tap.TapHelper.init(this)
        com.dshx.game.SU.tap.TapHelper.listener =
            object : com.dshx.game.SU.tap.TapHelper.Listener {
                override fun onLoginChanged(openId: String?) {
                    runOnUiThread {
                        if (openId.isNullOrEmpty()) {
                            loginGate = true
                            complianceChecking = false
                            complianceBlocked = false
                        } else {
                            // 登录成功也不能直接放行，先进校验中状态
                            loginGate = false
                            complianceChecking = true
                            complianceBlocked = false
                            complianceMsg = ""
                        }
                    }
                }
            }
        com.dshx.game.SU.tap.ComplianceManager.register(complianceListener)
        val openId = com.dshx.game.SU.tap.TapHelper.currentOpenId()
            ?: com.dshx.game.SU.tap.TapHelper.savedOpenId(this)
        if (openId.isNullOrEmpty()) {
            loginGate = true
        } else {
            startComplianceCheck(openId)
        }
    }

    /** 发起一次防沉迷校验。校验期间保持阻断，只有 SDK 回调才解除。 */
    private fun startComplianceCheck(openId: String) {
        complianceChecking = true
        complianceBlocked = false
        complianceMsg = ""
        com.dshx.game.SU.tap.ComplianceManager.register(complianceListener)
        com.dshx.game.SU.tap.ComplianceManager.startup(this, openId)
    }

    private val complianceListener = object : com.dshx.game.SU.tap.ComplianceManager.Listener {
        override fun onLoginSuccess() {
            runOnUiThread {
                complianceChecking = false
                complianceBlocked = false
                complianceMsg = ""
                loginGate = false
            }
        }

        override fun onExited() = block(null, backToLogin = true)
        override fun onSwitchAccount() = block(null, backToLogin = true)

        override fun onPeriodRestrict() = block(
            "根据国家新闻出版署规定，未成年人仅可在周五、周六、周日及法定节假日的 20:00-21:00 游玩。当前时段无法进入游戏。"
        )

        override fun onDurationLimit() = block("今日可游戏时长已用完，请明天再来。")
        override fun onAgeLimit() = block("根据相关规定，该账号当前无法进入游戏。")
        override fun onRealNameStop() = block("需要完成实名认证才能进入游戏，请重新校验并完成认证。")

        override fun onError(message: String) = block(message)

        /** 统一处理"不放行"：要么回登录页，要么停在拦截页。 */
        private fun block(message: String?, backToLogin: Boolean = false) {
            runOnUiThread {
                complianceChecking = false
                if (backToLogin) {
                    complianceBlocked = false
                    complianceMsg = ""
                    loginGate = true
                } else {
                    complianceMsg = message ?: "当前账号无法进入游戏"
                    complianceBlocked = true
                }
            }
        }
    }

    /**
     * 统一的激励视频入口：未接入 / 未配置 / 中途关闭都会给出明确提示，
     * 只有真正看完（onRewardVerify）才会执行 [onReward]。
     */
    private fun requestAd(placement: String, onReward: () -> Unit) {
        if (!RewardAds.isReady()) {
            audio.play("sfx_cant_buy", 0.7f)
            // 没准备好：顺手再拉一次初始化（内部有冷却），并把真正的原因说清楚
            setupAds()
            val why = RewardAds.lastError
            showToast(if (why.isEmpty()) "广告还没准备好，稍后再试" else why)
            return
        }
        audio.play("sfx_click", 0.6f)
        adLoading = true
        adLoadingSince = System.currentTimeMillis()
        RewardAds.request(placement) { ok ->
            runOnUiThread {
                adLoading = false
                if (ok) {
                    audio.play("sfx_success", 0.9f)
                    onReward()
                } else {
                    audio.play("sfx_fail", 0.7f)
                    showToast("广告未完成，未发放奖励")
                }
                // 播完立刻预热下一条，保持「点开即看」（转化率的关键）
                com.dshx.game.SU.ads.AdBridge.preload(this)
            }
        }
    }

    private fun showToast(msg: String) {
        toastText = msg
    }

    /** 门控是否正在阻断游戏（隐私未同意 / 未登录 / 防沉迷校验中或未通过）。 */
    private val gateBlocking: Boolean
        get() = !privacyAccepted || loginGate || complianceChecking || complianceBlocked

    /**
     * 开启全屏沉浸模式，并在用户从边缘划出系统栏后自动重新隐藏。
     * 注意：不要用 WindowCompat.setDecorFitsSystemWindows(true)，
     * 那样内容会被系统栏顶下去，游戏画面就不是铺满的了。
     */
    private fun enableImmersiveMode() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // 部分 ROM 在切换前后会重新显示系统栏，这里补一次
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    /**
     * 清空存档并把运行时状态恢复到初始。
     *
     * 注意两点：
     * 1. 清完**不要**立刻 save()，否则等于把空档又写回去、
     *    主菜单会以为还有存档。
     * 2. 调用方必须刷新 [saveEpoch]，让 hasSave 重新求值。
     */
    private fun doReset() {
        saveManager.clear()
        gameState.resetAll()
        world.fishes.clear()
        world.helpers.clear()
        world.floatingTexts.clear()
        world.particles.clear()
        world.syncFishCount()
        world.syncHelperCount()
        world.syncSpecialUnits()
    }

    @Composable
    private fun FishingGameScreen() {
        // 启动先进主菜单，而不是直接进游戏
        var inGame by remember { mutableStateOf(false) }
        var showShop by remember { mutableStateOf(false) }
        var showMenu by remember { mutableStateOf(false) }
        var showReset by remember { mutableStateOf(false) }
        var showPrestige by remember { mutableStateOf(false) }
        var gameViewRef by remember { mutableStateOf<GameView?>(null) }
        // 每次重置存档后 +1，强制 hasSave 重新求值。
        // 用 remember{} 缓存布尔值会导致「清档后主菜单仍显示旧存档」。
        var saveEpoch by remember { mutableIntStateOf(0) }
        val hasSave = remember(saveEpoch) { saveManager.hasSave() }

        // 离线收益：进入游戏时结算一次。用 remember 保证只算一次，
        // 之后切后台回来走 ON_RESUME 的实时累计，不再重复弹窗。
        var offlineResult by remember { mutableStateOf<OfflineEarnings.Result?>(null) }
        var offlineChecked by remember { mutableStateOf(false) }
        // HUD 刷新计数。GameState 是普通 var，必须靠它变化来驱动重组，
        // 否则金币数字不会更新。
        var revision by remember { mutableIntStateOf(0) }

        // 广告礼包弹窗
        var showAdGift by remember { mutableStateOf(false) }

        val context = LocalContext.current

        // 以 ~20fps 刷新 HUD，足够顺滑又不必每帧重组
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(50)
                revision++
            }
        }

        // 合作 buff 倒计时：跟 HUD 同一节奏推进（50ms 一步）
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(50)
                if (gameState.buffActive) gameState.tickBuff(0.05f)
            }
        }

        // 轻量提示 2 秒后自动消失
        LaunchedEffect(toastText) {
            if (toastText != null) {
                kotlinx.coroutines.delay(2000)
                toastText = null
            }
        }

        // 成就提示：轮流展示本帧新解锁的成就，每条停留 2.6 秒
        var toast by remember { mutableStateOf<com.dshx.game.SU.game.AchievementDef?>(null) }
        // 图鉴解锁 / 体型新纪录的中央弹窗（一次只弹一个）
        var unlockPopup by remember { mutableStateOf<com.dshx.game.SU.ui.UnlockPopup?>(null) }
        LaunchedEffect(Unit) {
            while (true) {
                // 无人机悬停的持续音：买了才响（这个方法自己防重入）
                if (gameState.droneOwned) {
                    audio.playLoop("sfx_drone", 0.3f)
                } else {
                    audio.stopLoop("sfx_drone")
                }
                // 新鱼种 / 新体型纪录：中央弹窗优先于成就条
                if (unlockPopup == null && world.pendingNewSpecies.isNotEmpty()) {
                    unlockPopup = com.dshx.game.SU.ui.UnlockPopup(
                        world.pendingNewSpecies.removeAt(0), null,
                    )
                    audio.play("sfx_legend", 1.0f)
                    kotlinx.coroutines.delay(3400)
                    unlockPopup = null
                    kotlinx.coroutines.delay(200)
                } else if (unlockPopup == null && world.pendingNewRecords.isNotEmpty()) {
                    val (sp, tier) = world.pendingNewRecords.removeAt(0)
                    unlockPopup = com.dshx.game.SU.ui.UnlockPopup(sp, tier)
                    audio.play("sfx_achievement", 1.0f)
                    kotlinx.coroutines.delay(3400)
                    unlockPopup = null
                    kotlinx.coroutines.delay(200)
                } else if (toast == null && world.pendingAchievements.isNotEmpty()) {
                    val def = world.pendingAchievements.removeAt(0)
                    toast = def
                    audio.play("sfx_achievement", 1.0f)
                    kotlinx.coroutines.delay(2600)
                    toast = null
                    kotlinx.coroutines.delay(260)
                } else {
                    kotlinx.coroutines.delay(120)
                }
                // 每日任务：跨天重置 + 达标自动结算
                DailyQuests.rolloverIfNeeded(gameState)
                val done = DailyQuests.claimCompleted(gameState)
                if (done.isNotEmpty()) {
                    audio.play("sfx_success", 1.0f)
                    saveManager.save(gameState, settings)
                    revision++
                }
            }
        }

        // 生命周期：切后台暂停游戏并保存
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        // 切后台：暂停世界、停 BGM、并把音效通道整体静音，
                        // 否则退到桌面后仍会听到钓鱼音效。
                        gameViewRef?.paused = true
                        audio.muted = true
                        audio.stopBgm()
                        saveManager.save(gameState, settings)
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        audio.muted = false
                        if (!showShop && !showMenu && !showReset && inGame) {
                            gameViewRef?.paused = false
                        }
                        audio.playBgm(context, "bgm_main")
                        // 回到前台时系统栏可能被重新显示，这里再收一次
                        enableImmersiveMode()
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // 进入游戏时结算一次离线收益
        LaunchedEffect(inGame) {
            if (!inGame || offlineChecked) return@LaunchedEffect
            offlineChecked = true
            val last = gameState.lastSeenMillis
            if (last > 0L) {
                val elapsed = (System.currentTimeMillis() - last) / 1000
                val perSec = OfflineEarnings.perHelperPerSecond(gameState)
                val result = OfflineEarnings.settle(elapsed, perSec, gameState.helpers)
                if (result.isMeaningful) {
                    // 直接入账，弹窗只是告知。
                    // 看过广告的「离线收益翻倍」在这里兑现（单次消费）。
                    val doubled = gameState.consumeOfflineDouble()
                    val gain = if (doubled) result.money * 2 else result.money
                    gameState.money += gain
                    gameState.recordOfflineEarnings(result.catches)
                    offlineResult = result
                    revision++
                }
            }
        }

        // 打开面板或回到主菜单时暂停世界模拟
        LaunchedEffect(showShop, showMenu, showReset, showPrestige, inGame) {
            gameViewRef?.paused = showShop || showMenu || showReset || showPrestige || !inGame
        }

        // 整个界面（主菜单 or 游戏内）套在一个 Box 里，
        // 三层门控作为它的兄弟节点盖在最上层 —— 关键：**不能**放在
        // `if (!inGame) { ... return }` 之后，那样首启停在主菜单时
        // 隐私页永远不会显示、SDK 也永远不会初始化。
        Box(Modifier.fillMaxSize()) {

        // ---------------- 主菜单 ----------------
        // 用 key 把主菜单和游戏内两套 UI 隔离开：
        // 否则两者共享 showMenu/showReset 状态，从游戏内返回主菜单时
        // 面板会被另一分支的渲染逻辑吃掉，表现为"返回失灵"。
        if (!inGame) {
            key("main_menu") {
            MainMenu(
                state = gameState,
                assets = assets,
                hasSave = hasSave,
                onStart = {
                    audio.play("sfx_click", 0.7f)
                    inGame = true
                },
                onContinue = {
                    audio.play("sfx_click", 0.7f)
                    inGame = true
                },
                onSettings = {
                    audio.play("sfx_click", 0.6f)
                    showMenu = true
                },
                onReset = {
                    audio.play("sfx_click", 0.6f)
                    showReset = true
                },
            )

            if (showMenu) {
                MenuPanel(
                    state = gameState,
                    settings = settings,
                    assets = assets,
                    showResume = false,
                    onVolumeChanged = {
                        audio.refreshVolumes()
                        saveManager.save(gameState, settings)
                    },
                    onReset = { showReset = true },
                    onOpenPrivacy = {
                        if (!com.dshx.game.SU.ads.AdPrivacy.openPolicy(context)) {
                            showToast("隐私政策链接尚未配置")
                        }
                    },
                    onExitToMainMenu = {
                        audio.play("sfx_click", 0.6f)
                        saveManager.save(gameState, settings)
                        showMenu = false
                        inGame = false
                    },
                    onClose = {
                        audio.play("sfx_click", 0.6f)
                        saveManager.save(gameState, settings)
                        showMenu = false
                    },
                )
            }

            if (showReset) {
                ResetConfirmDialog(
                    assets = assets,
                    onConfirm = {
                        doReset()
                        // 必须刷新 saveEpoch，否则主菜单仍会认为有存档
                        saveEpoch++
                        showReset = false
                        showMenu = false
                        revision++
                    },
                    onDismiss = { showReset = false },
                )
            }
            }   // key("main_menu")
        } else {
        // ---------------- 游戏内 ----------------
        Box(Modifier.fillMaxSize().background(UITheme.DeepWater)) {
            // 游戏画面
            AndroidView(
                factory = { ctx ->
                    GameView(
                        context = ctx,
                        world = world,
                        assets = assets,
                        settings = settings,
                        onSfx = { name ->
                            when (name) {
                                "cast" -> audio.play("sfx_cast", 0.8f)
                                "splash" -> audio.play("sfx_splash", 0.6f)
                                "bite" -> audio.play("sfx_bite", 0.9f)
                                "reel" -> audio.play("sfx_reel", 0.4f, minIntervalMs = 120)
                                "success" -> audio.play("sfx_success", 0.85f)
                                "fail" -> audio.play("sfx_fail", 0.7f)
                                "coin" -> audio.play("sfx_coin", 0.55f, minIntervalMs = 90)
                            }
                        },
                    ).also { view ->
                        gameViewRef = view
                        view.start()
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // HUD
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    MoneyBar(gameState, revision)
                    CatchStrip(gameState, revision)
                }

                // 金币旁的「广告礼包」入口 + 合作 buff 倒计时。
                // 紧贴金币条下方，是转化率最高的一类广告位。
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AdGiftButton(
                        leftToday = (DAILY_GIFT_LIMIT - AdDaily.used(context, GIFT_DAILY_KEY))
                            .coerceAtLeast(0),
                        ready = RewardAds.isReady(),
                        onClick = { showAdGift = true },
                    )
                    if (gameState.buffActive) {
                        BuffStrip(
                            label = "合作加成 +50% 收益",
                            remainSeconds = gameState.buffRemain,
                            totalSeconds = gameState.buffTotal,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                AchievementToast(toast)

                // 轻量提示（广告未完成 / 领取成功等），2 秒后自动消失
                val tt = toastText
                if (tt != null) {
                    Text(
                        tt,
                        color = UITheme.GoldLight,
                        fontSize = 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .background(UITheme.Ink.copy(alpha = 0.85f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }

                Spacer(Modifier.weight(1f))

                // 左右划船按钮，贴着屏幕两侧
                MoveButtons(world = world, revision = revision)
                Spacer(Modifier.height(10.dp))

                BottomBar(
                    world = world,
                    revision = revision,
                    canPrestige = gameState.canPrestige(),
                    onOpenShop = {
                        audio.play("sfx_click", 0.6f)
                        showShop = true
                    },
                    onOpenPrestige = {
                        audio.play("sfx_click", 0.6f)
                        showPrestige = true
                    },
                    onOpenMenu = {
                        audio.play("sfx_click", 0.6f)
                        showMenu = true
                    },
                )
            }

            // 商店。onBuy / onUnlockMap 返回是否成功，面板据此给出购买反馈。
            if (showShop) {
                ShopPanel(
                    state = gameState,
                    assets = assets,
                    world = world,
                    // revision 必须在**面板打开期间持续变化**，
                    // 否则商店里的数量/价格不会实时刷新（要退出去再进来才更新）
                    revision = revision,
                    onUnlockMap = { map ->
                        val ok = gameState.unlockMap(map)
                        if (ok) {
                            audio.play("sfx_buy", 0.95f)
                            world.switchMap(map)
                            DailyTracker.onMapChanged(gameState)
                            saveManager.save(gameState, settings)
                        } else {
                            audio.play("sfx_cant_buy", 0.7f)
                        }
                        revision++
                        ok
                    },
                    onBuy = { def ->
                        val ok = gameState.buy(def)
                        if (ok) {
                            if (def.attribute == Attribute.HELPER) {
                                DailyTracker.onHelperBought(gameState)
                            }
                            audio.play("sfx_buy", 0.9f)
                            world.syncFishCount()
                            world.syncHelperCount()
                            world.syncSpecialUnits()
                            saveManager.save(gameState, settings)
                        } else {
                            audio.play("sfx_cant_buy", 0.7f)
                        }
                        revision++
                        ok
                    },
                    onClose = {
                        audio.play("sfx_click", 0.6f)
                        showShop = false
                    },
                )
            }

            // 转生 / 技能树
            // 离线收益结算弹窗
            offlineResult?.let { result ->
                OfflinePanel(
                    result = result,
                    assets = assets,
                    onClaim = {
                        audio.play("sfx_success", 0.95f)
                        offlineResult = null
                        saveManager.save(gameState, settings)
                    },
                )
            }

            if (showPrestige) {
                PrestigePanel(
                    state = gameState,
                    assets = assets,
                    revision = revision,
                    onPrestige = {
                        val gained = gameState.doPrestige()
                        if (gained > 0) {
                            audio.play("sfx_prestige", 1.0f)
                            world.switchMap(gameState.currentMap)
                            saveManager.save(gameState, settings)
                        }
                        showPrestige = false
                        revision++
                    },
                    onLevelUp = { def ->
                        if (SkillTree.levelUp(gameState, def)) {
                            audio.play("sfx_buy", 0.9f)
                            saveManager.save(gameState, settings)
                        } else {
                            audio.play("sfx_cant_buy", 0.7f)
                        }
                        revision++
                    },
                    onClose = {
                        audio.play("sfx_click", 0.6f)
                        showPrestige = false
                    },
                )
            }

            // 菜单
            if (showMenu) {
                MenuPanel(
                    state = gameState,
                    settings = settings,
                    assets = assets,
                    onVolumeChanged = {
                        audio.refreshVolumes()
                        saveManager.save(gameState, settings)
                    },
                    onReset = { showReset = true },
                    onOpenPrivacy = {
                        if (!com.dshx.game.SU.ads.AdPrivacy.openPolicy(context)) {
                            showToast("隐私政策链接尚未配置")
                        }
                    },
                    // 之前这里没传回调，MenuPanel 内部默认是空实现，
                    // 「返回主菜单」点了完全没反应。现在真的回得去了。
                    onExitToMainMenu = {
                        audio.play("sfx_click", 0.6f)
                        saveManager.save(gameState, settings)
                        showMenu = false
                        showShop = false
                        showPrestige = false
                        showReset = false
                        world.stopMoving()
                        gameViewRef?.paused = true
                        inGame = false
                        revision++
                    },
                    onClose = {
                        audio.play("sfx_click", 0.6f)
                        saveManager.save(gameState, settings)
                        showMenu = false
                    },
                )
            }

            if (showReset) {
                ResetConfirmDialog(
                    assets = assets,
                    onConfirm = {
                        doReset()
                        // 必须刷新 saveEpoch，否则主菜单仍会认为有存档
                        saveEpoch++
                        showReset = false
                        showMenu = false
                        revision++
                    },
                    onDismiss = { showReset = false },
                )
            }

            // 图鉴解锁 / 体型新纪录的中央弹窗。
            // 必须挂在游戏根 Box 下（而不是 HUD 的 Column 里）：
            // 放 Column 里时 fillMaxSize 会吃掉剩余全部高度，遮罩铺满屏幕下半部分，
            // 底部的划船/商店按钮会被挤出屏幕 —— 那正是「黑色遮罩特别大」的原因。
            // 放在最后 = 盖在所有面板之上。
            UnlockPopupCard(
                popup = unlockPopup,
                assets = assets,
                onDismiss = { unlockPopup = null },
            )

            // 广告礼包：金币旁那个图标点开的面板
            if (showAdGift) {
                AdGiftDialog(
                    gifts = buildAdGifts(context),
                    statusText = if (RewardAds.isReady()) "看完广告立即到账"
                    else RewardAds.statusText(),
                    onDismiss = { showAdGift = false },
                )
            }

            // 广告加载浮层：从点击到真正播放之间给反馈
            if (adLoading) {
                AdLoadingOverlay(
                    slowHint = System.currentTimeMillis() - adLoadingSince > 8000,
                )
            }
        }
        }   // 游戏内 Box / if-else 结束

        // ---------------- 三层门控（盖在最上层） ----------------
        // 顺序固定：隐私政策 -> TapTap 登录 -> 防沉迷认证。
        if (!privacyAccepted) {
            PrivacyGate(
                onOpenPolicy = {
                    if (!com.dshx.game.SU.ads.AdPrivacy.openPolicy(context)) {
                        showToast("隐私政策链接尚未配置")
                    }
                },
                onDecline = { finishAffinity() },
                onAccept = {
                    com.dshx.game.SU.ads.AdPrivacy.accept(context, true)
                    privacyAccepted = true
                    // 同意之后才初始化 SDK：先广告，再 TapTap 登录 + 防沉迷
                    setupAds()
                    setupTap()
                    audio.play("sfx_success", 0.9f)
                },
            )
        } else if (loginGate) {
            LoginGate(
                busy = loginBusy,
                message = loginMsg,
                onLogin = {
                    if (loginBusy) return@LoginGate
                    loginBusy = true
                    loginMsg = "正在拉起 TapTap…"
                    // 注意：这里在外层 Box 的 BoxScope 里，`this` 已被遮蔽成 BoxScope，
                    // 必须显式写 this@MainActivity 才能拿到 Activity。
                    com.dshx.game.SU.tap.TapHelper.login(this@MainActivity) { _, msg ->
                        runOnUiThread {
                            loginBusy = false
                            loginMsg = msg
                            // 登录成功也不能在这里放行：放行必须等防沉迷回调
                        }
                    }
                },
                onRetryCompliance = {
                    val uid = com.dshx.game.SU.tap.TapHelper.currentOpenId()
                        ?: com.dshx.game.SU.tap.TapHelper.savedOpenId(this@MainActivity)
                    if (uid.isNullOrEmpty()) {
                        loginMsg = "本地没有登录记录，请先登录"
                    } else {
                        loginMsg = "正在重新校验…"
                        startComplianceCheck(uid)
                    }
                },
            )
        } else if (complianceChecking) {
            ComplianceCheckingGate()
        } else if (complianceBlocked) {
            ComplianceBlockedGate(
                message = complianceMsg,
                onRetry = {
                    val uid = com.dshx.game.SU.tap.TapHelper.currentOpenId()
                        ?: com.dshx.game.SU.tap.TapHelper.savedOpenId(this@MainActivity)
                    if (uid.isNullOrEmpty()) {
                        complianceBlocked = false
                        loginGate = true
                    } else {
                        startComplianceCheck(uid)
                    }
                },
                onSwitchAccount = {
                    com.dshx.game.SU.tap.TapHelper.logout(this@MainActivity)
                    loginBusy = false
                    loginMsg = ""
                    complianceBlocked = false
                    complianceMsg = ""
                    loginGate = true
                },
            )
        }
        }   // 外层 Box：门控盖在最上层
    }

    /**
     * 广告礼包内容。
     *
     * 一次给多个选择比单个按钮的触发率高得多 —— 玩家总会挑一个看起来最划算的。
     * 含**合作 buff**（限时 +50% 收益），是复访率最高的一类激励。
     */
    private fun buildAdGifts(context: android.content.Context): List<AdGift> {
        val gold = (gameState.money * 0.12).coerceAtLeast(200.0)
        val used = AdDaily.used(context, GIFT_DAILY_KEY)
        val left = (DAILY_GIFT_LIMIT - used).coerceAtLeast(0)
        val list = mutableListOf<AdGift>()

        if (left > 0) {
            list += AdGift(
                id = "gift_gold",
                icon = "🪙",
                title = "金币礼包",
                desc = "立得 ${com.dshx.game.SU.game.formatNumber(gold)} 金币 · 今日还剩 $left 次",
                action = {
                    requestAd(RewardAds.PLACEMENT_GOLD_GIFT) {
                        gameState.money += gold
                        AdDaily.markUsed(context, GIFT_DAILY_KEY)
                        saveManager.save(gameState, settings)
                        showToast("金币礼包 +${com.dshx.game.SU.game.formatNumber(gold)}")
                    }
                },
            )
        } else {
            list += AdGift(
                id = "gift_gold_done",
                icon = "🪙",
                title = "金币礼包",
                desc = "今日已领完，明天再来",
                action = { showToast("今日礼包已领完") },
            )
        }

        // 合作 buff：限时 +50% 收益。已激活时显示为"续时长"，动机更明确。
        val buffLabel = if (gameState.buffActive) {
            "合作加成 +50% 收益（剩余 ${gameState.buffRemain.toInt()}s）"
        } else {
            "合作加成 +50% 收益，持续 5 分钟"
        }
        list += AdGift(
            id = "gift_buff",
            icon = "⚡",
            title = "合作 Buff",
            desc = buffLabel,
            action = {
                requestAd(RewardAds.PLACEMENT_COOP_BUFF) {
                    gameState.activateBuff(300f)
                    showToast("合作加成已激活：+50% 收益 5 分钟")
                }
            },
        )

        // 离线收益翻倍：只在有离线收益待领时才出现
        list += AdGift(
            id = "gift_offline",
            icon = "🌙",
            title = "离线收益翻倍",
            desc = "下次离线结算的金币翻一倍",
            action = {
                requestAd(RewardAds.PLACEMENT_OFFLINE_DOUBLE) {
                    gameState.offlineDoubleReady = true
                    showToast("离线收益翻倍已就绪，下次结算生效")
                }
            },
        )

        return list
    }

    private companion object {
        /** 每日广告礼包次数上限。 */
        const val DAILY_GIFT_LIMIT = 5
        const val GIFT_DAILY_KEY = "gift_daily"
    }

    override fun onDestroy() {
        super.onDestroy()
        saveManager.save(gameState, settings)
        audio.release()
    }
}
