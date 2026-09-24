package com.taptap.fishingidle.game

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/** 存档读写。只保存购买次数与金钱类数值，其余靠重放重建。 */
class SaveManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("fishing_idle_save", Context.MODE_PRIVATE)

    fun save(state: GameState, settings: Settings) {
        val root = JSONObject()
        root.put("version", SAVE_VERSION)
        // 记录离开时间，下次启动据此结算离线收益
        root.put("lastSeen", System.currentTimeMillis())
        root.put("money", state.money)
        root.put("totalMoney", state.totalMoney)
        root.put("highestMoney", state.highestMoney)
        root.put("highestCatch", state.highestCatch)

        val purchases = JSONObject()
        for ((k, v) in state.purchases) purchases.put(k, v)
        root.put("purchases", purchases)

        // 每日任务进度
        root.put("dailyDay", state.dailyDayIndex)
        root.put("dailyCatches", state.dailyProgress.catches)
        root.put("dailyMoney", state.dailyProgress.moneyEarned)
        root.put("dailyBestCombo", state.dailyProgress.bestCombo)
        root.put("dailyRare", state.dailyProgress.rareCatches)
        root.put("dailyMaps", state.dailyProgress.mapChanges)
        root.put("dailyHelpers", state.dailyProgress.helpersBought)
        val doneArr = org.json.JSONArray()
        for (d in state.dailyDone) doneArr.put(d)
        root.put("dailyDone", doneArr)

        val opts = JSONObject()
        opts.put("master", settings.masterVolume.toDouble())
        opts.put("sfx", settings.sfxVolume.toDouble())
        opts.put("bgm", settings.bgmVolume.toDouble())
        opts.put("hideText", settings.hideFloatingText)
        root.put("options", opts)

        prefs.edit().putString(KEY, root.toString()).apply()
    }

    fun load(state: GameState, settings: Settings): Boolean {
        val raw = prefs.getString(KEY, null) ?: return false
        return try {
            val root = JSONObject(raw)
            val data = SaveData()
            data.lastSeenMillis = root.optLong("lastSeen", 0L)
            data.dailyDayIndex = root.optLong("dailyDay", 0L)
            data.dailyCatches = root.optLong("dailyCatches", 0L)
            data.dailyMoney = root.optDouble("dailyMoney", 0.0)
            data.dailyBestCombo = root.optInt("dailyBestCombo", 0)
            data.dailyRareCatches = root.optLong("dailyRare", 0L)
            data.dailyMapChanges = root.optInt("dailyMaps", 0)
            data.dailyHelpersBought = root.optInt("dailyHelpers", 0)
            root.optJSONArray("dailyDone")?.let { arr ->
                for (i in 0 until arr.length()) data.dailyDone.add(arr.optString(i))
            }
            data.money = root.optDouble("money", 0.0)
            data.totalMoney = root.optDouble("totalMoney", 0.0)
            data.highestMoney = root.optDouble("highestMoney", 0.0)
            data.highestCatch = root.optDouble("highestCatch", 0.0)

            root.optJSONObject("purchases")?.let { p ->
                for (key in p.keys()) data.purchases[key] = p.optInt(key, 0)
            }
            state.loadFrom(data)

            root.optJSONObject("options")?.let { o ->
                settings.masterVolume = o.optDouble("master", 0.8).toFloat()
                settings.sfxVolume = o.optDouble("sfx", 0.8).toFloat()
                settings.bgmVolume = o.optDouble("bgm", 0.5).toFloat()
                settings.hideFloatingText = o.optBoolean("hideText", false)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 是否存在有效存档（用于主菜单区分"开始游戏"与"继续游戏"）。 */
    fun hasSave(): Boolean = prefs.contains(KEY)

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "save_json"
        const val SAVE_VERSION = 1
    }
}

/** 玩家设置。 */
class Settings {
    var masterVolume: Float = 0.8f
    var sfxVolume: Float = 0.8f
    var bgmVolume: Float = 0.5f
    var hideFloatingText: Boolean = false
}
