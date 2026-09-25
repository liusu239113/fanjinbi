package com.dshx.game.SU

import com.dshx.game.SU.game.Content
import com.dshx.game.SU.game.FishSize
import com.dshx.game.SU.game.GameState
import com.dshx.game.SU.game.SaveData
import com.dshx.game.SU.game.SkillTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 存档往返测试。
 *
 * 这里守的是一个**真实踩过的坑**：跨轮进度（珍珠、技能等级、转生次数、图鉴收集、
 * 已解锁水域、成就、统计）原本根本没写进存档，只在内存里活得下去 ——
 * 玩家一重启就全回退，"转生白转了、图鉴白收集了"。
 */
class SaveRoundTripTest {

    private val roundTripFields = listOf(
        "pearls", "prestigeCount", "skillLevels", "bestCombo", "totalCatches",
        "unlockedAchievements", "caughtSpecies", "bestSize",
        "chestsOpened", "kingsCaught", "unlockedMaps", "currentMapId",
        "speedTier", "selectedSpeed", "speedUntilMillis",
    )

    /** 攒一份"什么都动过"的状态，往返一圈必须一模一样。 */
    private fun richState(): GameState = GameState().apply {
        money = 12345.0
        repeat(6) { Content.byId("common_fish")?.let { buy(it) } }
        Content.byId("helper")?.let { buy(it) }
        pearls = 17
        prestigeCount = 2
        SkillTree.levelUp(this, SkillTree.byId("bait_mastery")!!)
        unlockedAchievements.add("first_catch")
        unlockedAchievements.add("combo_15")
        caughtSpecies.add("fish_common")
        caughtSpecies.add("fw_jiyu")
        bestSize["fish_common"] = FishSize.HUGE.ordinal
        bestSize["fw_jiyu"] = FishSize.KING.ordinal
        chestsOpened = 9
        kingsCaught = 3
        unlockedMaps.add("marsh")
        currentMapId = "marsh"
        unlockSpeed(System.currentTimeMillis())
    }

    @Test
    fun `跨轮进度往返一圈不丢`() {
        val original = richState()
        val data = original.toSave()
        val restored = GameState().apply { loadFrom(data) }

        assertEquals(original.pearls, restored.pearls)
        assertEquals(original.prestigeCount, restored.prestigeCount)
        assertEquals(original.skillLevels.toMap(), restored.skillLevels.toMap())
        assertEquals(original.unlockedAchievements.toSet(), restored.unlockedAchievements.toSet())
        assertEquals(original.caughtSpecies.toSet(), restored.caughtSpecies.toSet())
        assertEquals(original.bestSize.toMap(), restored.bestSize.toMap())
        assertEquals(original.chestsOpened, restored.chestsOpened)
        assertEquals(original.kingsCaught, restored.kingsCaught)
        assertEquals(original.unlockedMaps.toSet(), restored.unlockedMaps.toSet())
        assertEquals(original.currentMapId, restored.currentMapId)
        assertEquals(original.speedTier, restored.speedTier)
        assertEquals(original.selectedSpeed, restored.selectedSpeed)
        assertEquals(original.speedUntilMillis, restored.speedUntilMillis)
        assertEquals(original.money, restored.money, 0.001)
    }

    /** 体型纪录要能读出对应的档位。 */
    @Test
    fun `体型纪录能读回来`() {
        val s = richState()
        val restored = GameState().apply { loadFrom(s.toSave()) }
        assertEquals(FishSize.HUGE, restored.bestSizeOf("fish_common"))
        assertEquals(FishSize.KING, restored.bestSizeOf("fw_jiyu"))
        assertEquals("没记录的鱼种按普通算", FishSize.NORMAL, restored.bestSizeOf("fw_niqiu"))
    }

    /**
     * 反射扫描 [SaveData] 的每个字段，要求它必须在 SaveManager 源码里出现过。
     *
     * 只用往返测试守不住这个坑 —— 往返走的是 toSaveData/loadFrom，
     * 而漏掉的恰恰是 SaveManager 那一层。所以这里直接读源码做检查：
     * 以后再加字段忘了写进存档，这条会立刻红。
     */
    @Test
    fun `SaveData 的每个字段都得在 SaveManager 里出现过`() {
        // 路径由类自己的包名推出来，换包名/换目录都不用改这里
        val pkgPath = com.dshx.game.SU.game.SaveManager::class.java.packageName.replace('.', '/')
        val src = File("src/main/java/$pkgPath/SaveManager.kt")
        assertTrue("找不到 SaveManager.kt，测试路径要跟着改", src.exists())
        val text = src.readText()

        val missing = roundTripFields.filter { !text.contains(it) }
        assertTrue("这些跨轮字段没写进存档：$missing", missing.isEmpty())

        // 顺便确认 SaveData 里没有"新加的字段却没人管"——
        // 这里列的是 SaveManager 明确负责的字段，新增字段要么进这个表，要么说明为什么不用存。
        // 跳过编译器生成的合成字段（例如 $stable），只看真正的属性
        val declared = SaveData::class.java.declaredFields
            .filterNot { it.isSynthetic || it.name.startsWith("$") }
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.name }
        val ignored = setOf("lastSeenMillis", "dailyDayIndex", "dailyCatches", "dailyMoney",
            "dailyBestCombo", "dailyRareCatches", "dailyMapChanges", "dailyHelpersBought",
            "dailyDone", "masterVolume", "sfxVolume", "bgmVolume", "hideFloatingText")
        val unhandled = declared.filter { it !in roundTripFields && it !in ignored && !text.contains(it) }
        assertTrue("SaveData 新增了字段但 SaveManager 没存：$unhandled", unhandled.isEmpty())
    }
}
