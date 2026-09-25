package com.dshx.game.SU.game

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 存档读写。
 *
 * 普通属性（价格曲线、收益、解锁标记）靠**重放购买记录**重建，不用存；
 * 但跨轮进度必须显式存下来：珍珠、技能等级、转生次数、图鉴收集、
 * 已解锁水域、成就、体型纪录、统计数字 —— 这些重放不出来。
 */
class SaveManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("fishing_idle_save", Context.MODE_PRIVATE)

    fun save(state: GameState, settings: Settings) {
        val root = JSONObject()
        root.put("version", SAVE_VERSION)
        // 记录离开时间，下次启动据此结算离线收益
        root.put("lastSeen", System.currentTimeMillis())
        root.put("speedTier", state.availableSpeed())
        root.put("selectedSpeed", state.currentSpeed())
        root.put("speedUntilMillis", if (state.availableSpeed() > 1) state.speedUntilMillis else 0L)
        root.put("money", state.money)
        root.put("totalMoney", state.totalMoney)
        root.put("highestMoney", state.highestMoney)
        root.put("highestCatch", state.highestCatch)

        val purchases = JSONObject()
        for ((k, v) in state.purchases) purchases.put(k, v)
        root.put("purchases", purchases)

        // ---- 跨轮进度 ----
        // 这几项以前**完全没写进存档**：珍珠、技能等级、转生次数、图鉴收集、
        // 已解锁水域、成就、统计全部只存在内存里，重启就回退到初始值 ——
        // 玩家会觉得"转生白转了、图鉴白收集了"。这里全部补齐。
        root.put("pearls", state.pearls)
        root.put("prestigeCount", state.prestigeCount)
        val skills = JSONObject()
        for ((k, v) in state.skillLevels) skills.put(k, v)
        root.put("skillLevels", skills)
        root.put("bestCombo", state.bestCombo)
        root.put("totalCatches", state.totalCatches)
        root.put("chestsOpened", state.chestsOpened)
        root.put("kingsCaught", state.kingsCaught)
        root.put("currentMapId", state.currentMapId)

        root.put("unlockedAchievements", JSONArray().apply {
            state.unlockedAchievements.forEach { put(it) }
        })
        root.put("caughtSpecies", JSONArray().apply {
            state.caughtSpecies.forEach { put(it) }
        })
        root.put("dexClaimed", JSONArray().apply {
            state.dexClaimed.forEach { put(it) }
        })
        root.put("unlockedMaps", JSONArray().apply {
            state.unlockedMaps.forEach { put(it) }
        })
        root.put("bestSize", JSONObject().apply {
            for ((k, v) in state.bestSize) put(k, v)
        })

        // ---- 换装 ----
        root.put("ownedCharacters", JSONArray().apply {
            state.ownedCharacters.forEach { put(it) }
        })
        root.put("currentCharacterId", state.currentCharacterId)
        root.put("currentHelperId", state.currentHelperId)

        // ---- 仓库 ----
        // 仓库里的鱼是玩家的资产，必须存；行情与鱼贩计时是临时的，不存。
        root.put("warehouseSpecies", JSONArray().apply {
            state.warehouse.forEach { put(it.speciesId) }
        })
        root.put("warehouseSize", JSONArray().apply {
            state.warehouse.forEach { put(it.sizeOrdinal) }
        })
        root.put("warehouseValue", JSONArray().apply {
            state.warehouse.forEach { put(it.baseValue) }
        })
        root.put("warehouseStoredAt", JSONArray().apply {
            state.warehouse.forEach { put(it.storedAt) }
        })
        root.put("warehouseFirstCatch", JSONArray().apply {
            state.warehouse.forEach { put(it.firstCatch) }
        })
        root.put("warehouseUpgrades", state.warehouseUpgrades)
        root.put("warehouseEarned", state.warehouseEarned)
        root.put("merchantVisits", state.merchantVisits)

        // 每日任务进度
        root.put("dailyDay", state.dailyDayIndex)
        root.put("dailyCatches", state.dailyProgress.catches)
        root.put("dailyMoney", state.dailyProgress.moneyEarned)
        root.put("dailyBestCombo", state.dailyProgress.bestCombo)
        root.put("dailyRare", state.dailyProgress.rareCatches)
        root.put("dailyMaps", state.dailyProgress.mapChanges)
        root.put("dailyHelpers", state.dailyProgress.helpersBought)
        root.put("dailyChests", state.dailyProgress.chests)
        root.put("dailyKings", state.dailyProgress.kings)
        root.put("dailyCasts", state.dailyProgress.casts)
        root.put("dailyPurchases", state.dailyProgress.anyPurchase)
        root.put("dailyNewSpecies", state.dailyProgress.newSpecies)
        root.put("dailyStored", state.dailyProgress.stored)
        root.put("dailySold", state.dailyProgress.sold)
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
            data.speedTier = root.optInt("speedTier", 1)
            data.selectedSpeed = root.optInt("selectedSpeed", 1)
            data.speedUntilMillis = root.optLong("speedUntilMillis", 0L)
            data.dailyDayIndex = root.optLong("dailyDay", 0L)
            data.dailyCatches = root.optLong("dailyCatches", 0L)
            data.dailyMoney = root.optDouble("dailyMoney", 0.0)
            data.dailyBestCombo = root.optInt("dailyBestCombo", 0)
            data.dailyRareCatches = root.optLong("dailyRare", 0L)
            data.dailyMapChanges = root.optInt("dailyMaps", 0)
            data.dailyHelpersBought = root.optInt("dailyHelpers", 0)
            data.dailyChests = root.optInt("dailyChests", 0)
            data.dailyKings = root.optInt("dailyKings", 0)
            data.dailyCasts = root.optInt("dailyCasts", 0)
            data.dailyPurchases = root.optInt("dailyPurchases", 0)
            data.dailyNewSpecies = root.optInt("dailyNewSpecies", 0)
            data.dailyStored = root.optInt("dailyStored", 0)
            data.dailySold = root.optInt("dailySold", 0)
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

            // ---- 跨轮进度（老存档没有这些字段时走默认值，不会崩）----
            data.pearls = root.optLong("pearls", 0L)
            data.prestigeCount = root.optInt("prestigeCount", 0)
            root.optJSONObject("skillLevels")?.let { obj ->
                for (key in obj.keys()) data.skillLevels[key] = obj.optInt(key, 0)
            }
            data.bestCombo = root.optInt("bestCombo", 0)
            data.totalCatches = root.optLong("totalCatches", 0L)
            data.chestsOpened = root.optLong("chestsOpened", 0L)
            data.kingsCaught = root.optLong("kingsCaught", 0L)
            data.currentMapId = root.optString("currentMapId", "creek")
            root.optJSONArray("unlockedAchievements")?.let { arr ->
                for (i in 0 until arr.length()) data.unlockedAchievements.add(arr.optString(i))
            }
            root.optJSONArray("caughtSpecies")?.let { arr ->
                for (i in 0 until arr.length()) data.caughtSpecies.add(arr.optString(i))
            }
            root.optJSONArray("dexClaimed")?.let { arr ->
                for (i in 0 until arr.length()) data.dexClaimed.add(arr.optInt(i, 0))
            }
            root.optJSONArray("unlockedMaps")?.let { arr ->
                for (i in 0 until arr.length()) data.unlockedMaps.add(arr.optString(i))
            }
            root.optJSONObject("bestSize")?.let { obj ->
                for (key in obj.keys()) data.bestSize[key] = obj.optInt(key, 0)
            }

            // ---- 换装 ----
            root.optJSONArray("ownedCharacters")?.let { arr ->
                for (i in 0 until arr.length()) data.ownedCharacters.add(arr.optString(i))
            }
            data.currentCharacterId = root.optString("currentCharacterId", "")
            data.currentHelperId = root.optString("currentHelperId", "")

            // ---- 仓库 ----
            val speciesArr = root.optJSONArray("warehouseSpecies")
            val sizeArr = root.optJSONArray("warehouseSize")
            val valueArr = root.optJSONArray("warehouseValue")
            val atArr = root.optJSONArray("warehouseStoredAt")
            val firstArr = root.optJSONArray("warehouseFirstCatch")
            if (speciesArr != null) {
                for (i in 0 until speciesArr.length()) {
                    data.warehouseSpecies.add(speciesArr.optString(i))
                    data.warehouseSize.add(sizeArr?.optInt(i, 0) ?: 0)
                    data.warehouseValue.add(valueArr?.optDouble(i, 0.0) ?: 0.0)
                    data.warehouseStoredAt.add(atArr?.optLong(i, 0L) ?: 0L)
                    data.warehouseFirstCatch.add(firstArr?.optBoolean(i, false) ?: false)
                }
            }
            data.warehouseUpgrades = root.optInt("warehouseUpgrades", 0)
            data.warehouseEarned = root.optDouble("warehouseEarned", 0.0)
            data.merchantVisits = root.optInt("merchantVisits", 0)

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
