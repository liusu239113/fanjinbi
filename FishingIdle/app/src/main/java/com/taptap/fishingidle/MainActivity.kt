package com.taptap.fishingidle

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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.taptap.fishingidle.game.Assets
import com.taptap.fishingidle.game.Attribute
import com.taptap.fishingidle.game.AudioManager
import com.taptap.fishingidle.game.DailyQuests
import com.taptap.fishingidle.game.DailyTracker
import com.taptap.fishingidle.game.FishingMap
import com.taptap.fishingidle.game.GameState
import com.taptap.fishingidle.game.GameView
import com.taptap.fishingidle.game.OfflineEarnings
import com.taptap.fishingidle.game.SaveManager
import com.taptap.fishingidle.game.Settings
import com.taptap.fishingidle.game.SkillTree
import com.taptap.fishingidle.game.World
import com.taptap.fishingidle.ui.AchievementToast
import com.taptap.fishingidle.ui.AppFontFamily
import com.taptap.fishingidle.ui.BottomBar
import com.taptap.fishingidle.ui.CatchStrip
import com.taptap.fishingidle.ui.MainMenu
import com.taptap.fishingidle.ui.MenuPanel
import com.taptap.fishingidle.ui.PrestigePanel
import com.taptap.fishingidle.ui.MoneyBar
import com.taptap.fishingidle.ui.MoveButtons
import com.taptap.fishingidle.ui.OfflinePanel
import com.taptap.fishingidle.ui.ResetConfirmDialog
import com.taptap.fishingidle.ui.ShopPanel
import com.taptap.fishingidle.ui.UITheme
import androidx.compose.foundation.background

class MainActivity : ComponentActivity() {

    private lateinit var assets: Assets
    private lateinit var audio: AudioManager
    private lateinit var saveManager: SaveManager
    private lateinit var gameState: GameState
    private lateinit var settings: Settings
    private lateinit var world: World

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

        setContent {
            // 全局套用游戏字体：所有 Text 默认都用 AppFontFamily，
            // 各组件里就不必逐个指定 fontFamily 了。
            CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = AppFontFamily)) {
                FishingGameScreen()
            }
        }
    }

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

        val context = LocalContext.current

        // 以 ~20fps 刷新 HUD，足够顺滑又不必每帧重组
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(50)
                revision++
            }
        }

        // 成就提示：轮流展示本帧新解锁的成就，每条停留 2.6 秒
        var toast by remember { mutableStateOf<com.taptap.fishingidle.game.AchievementDef?>(null) }
        LaunchedEffect(Unit) {
            while (true) {
                if (toast == null && world.pendingAchievements.isNotEmpty()) {
                    val def = world.pendingAchievements.removeAt(0)
                    toast = def
                    audio.play("sfx_success", 1.0f)
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
                    // 直接入账，弹窗只是告知
                    gameState.money += result.money
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
            return@FishingGameScreen
        }

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

                Spacer(Modifier.height(8.dp))
                AchievementToast(toast)

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
                            audio.play("sfx_success", 1.0f)
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
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        saveManager.save(gameState, settings)
        audio.release()
    }
}
