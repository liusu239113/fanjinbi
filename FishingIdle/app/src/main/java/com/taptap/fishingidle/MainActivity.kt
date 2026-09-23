package com.taptap.fishingidle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.taptap.fishingidle.game.Assets
import com.taptap.fishingidle.game.AudioManager
import com.taptap.fishingidle.game.GameState
import com.taptap.fishingidle.game.GameView
import com.taptap.fishingidle.game.SaveManager
import com.taptap.fishingidle.game.Settings
import com.taptap.fishingidle.game.World
import com.taptap.fishingidle.ui.BottomBar
import com.taptap.fishingidle.ui.CatchStrip
import com.taptap.fishingidle.ui.MenuPanel
import com.taptap.fishingidle.ui.MoneyBar
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
            FishingGameScreen()
        }
    }

    @Composable
    private fun FishingGameScreen() {
        var showShop by remember { mutableStateOf(false) }
        var showMenu by remember { mutableStateOf(false) }
        var showReset by remember { mutableStateOf(false) }
        var gameViewRef by remember { mutableStateOf<GameView?>(null) }
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

        // 生命周期：切后台暂停游戏并保存
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        gameViewRef?.paused = true
                        audio.stopBgm()
                        saveManager.save(gameState, settings)
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        if (!showShop && !showMenu) gameViewRef?.paused = false
                        audio.playBgm(context, "bgm_main")
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // 打开面板时暂停世界模拟
        LaunchedEffect(showShop, showMenu, showReset) {
            gameViewRef?.paused = showShop || showMenu || showReset
        }

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

                Spacer(Modifier.weight(1f))

                BottomBar(
                    world = world,
                    onOpenShop = {
                        audio.play("sfx_click", 0.6f)
                        showShop = true
                    },
                    onOpenMenu = {
                        audio.play("sfx_click", 0.6f)
                        showMenu = true
                    },
                )
            }

            // 商店
            if (showShop) {
                ShopPanel(
                    state = gameState,
                    assets = assets,
                    onBuy = { def ->
                        val ok = gameState.buy(def)
                        if (ok) {
                            audio.play("sfx_buy", 0.9f)
                            world.syncFishCount()
                            world.syncHelperCount()
                            saveManager.save(gameState, settings)
                        } else {
                            audio.play("sfx_cant_buy", 0.7f)
                        }
                        revision++
                    },
                    onClose = {
                        audio.play("sfx_click", 0.6f)
                        showShop = false
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
                        saveManager.clear()
                        gameState.loadFrom(com.taptap.fishingidle.game.SaveData())
                        world.fishes.clear()
                        world.helpers.clear()
                        world.syncFishCount()
                        world.syncHelperCount()
                        saveManager.save(gameState, settings)
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
