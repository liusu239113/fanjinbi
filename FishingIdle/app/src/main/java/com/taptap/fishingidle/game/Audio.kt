package com.taptap.fishingidle.game

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import java.util.concurrent.ConcurrentHashMap

/**
 * 音频管理：SoundPool 播放短音效，MediaPlayer 循环 BGM。
 * 同名音效有最小间隔限制，避免几十条鱼同时上钩时音效炸裂。
 */
class AudioManager(context: Context, private val settings: Settings) {

    private val soundPool: SoundPool
    private val soundIds = ConcurrentHashMap<String, Int>()
    private val lastPlayed = ConcurrentHashMap<String, Long>()

    private var bgmPlayer: MediaPlayer? = null
    private var bgmName: String? = null

    init {
        soundPool = SoundPool.Builder()
            .setMaxStreams(12)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }

    /** 预加载所有音效。 */
    fun preload(context: Context, names: List<String>) {
        for (name in names) {
            try {
                context.assets.openFd("audio/$name.mp3").use { fd ->
                    val id = soundPool.load(fd, 1)
                    if (id != 0) soundIds[name] = id
                }
            } catch (e: Exception) {
                // 缺少音效不致命，静默跳过
            }
        }
    }

    fun play(name: String, volumeScale: Float = 1f, minIntervalMs: Long = 45) {
        val id = soundIds[name] ?: return
        val now = System.currentTimeMillis()
        val last = lastPlayed[name] ?: 0L
        if (now - last < minIntervalMs) return
        lastPlayed[name] = now

        val vol = (settings.masterVolume * settings.sfxVolume * volumeScale).coerceIn(0f, 1f)
        if (vol <= 0.001f) return
        soundPool.play(id, vol, vol, 1, 0, 1f)
    }

    fun playBgm(context: Context, name: String) {
        if (bgmName == name && bgmPlayer?.isPlaying == true) return
        stopBgm()
        try {
            val afd = context.assets.openFd("audio/$name.mp3")
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mp.isLooping = true
            mp.setVolume(bgmVolume(), bgmVolume())
            mp.prepare()
            mp.start()
            bgmPlayer = mp
            bgmName = name
        } catch (e: Exception) {
            bgmPlayer = null
            bgmName = null
        }
    }

    fun stopBgm() {
        try {
            bgmPlayer?.stop()
        } catch (_: Exception) {
        }
        bgmPlayer?.release()
        bgmPlayer = null
        bgmName = null
    }

    fun refreshVolumes() {
        val v = bgmVolume()
        try {
            bgmPlayer?.setVolume(v, v)
        } catch (_: Exception) {
        }
    }

    private fun bgmVolume(): Float =
        (settings.masterVolume * settings.bgmVolume).coerceIn(0f, 1f)

    fun release() {
        stopBgm()
        soundPool.release()
        soundIds.clear()
    }

    companion object {
        val SFX = listOf(
            "sfx_cast", "sfx_splash", "sfx_bite", "sfx_reel", "sfx_success",
            "sfx_fail", "sfx_buy", "sfx_cant_buy", "sfx_coin", "sfx_click",
        )
    }
}
