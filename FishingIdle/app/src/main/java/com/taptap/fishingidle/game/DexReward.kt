package com.taptap.fishingidle.game

/**
 * 图鉴收集奖励。
 *
 * 图鉴原本只是"记录一下"，收集了没有任何回报 —— 这是放置游戏最核心的
 * 长线动力之一。这里把收集进度换算成**永久加成**：每解锁一个鱼种，
 * 全局收益永久提升；每集齐一张地图，额外给一笔大加成。
 *
 * 加成在 [GameState.dexMultiplier] 里参与价值计算，转生也不会清空。
 */
object DexReward {

    /** 每收集 1 个鱼种，全局价值 +1.5%。 */
    const val PER_SPECIES_BONUS = 0.015

    /** 集齐一张地图的全部鱼种，额外 +25%。 */
    const val PER_MAP_COMPLETE_BONUS = 0.25

    /** 集齐所有地图，额外 +100%（终局目标）。 */
    const val ALL_COMPLETE_BONUS = 1.0

    /** 某张地图已收集的数量。 */
    fun caughtInMap(state: GameState, map: FishingMap): Int =
        map.species.count { state.caughtSpecies.contains(it.id) }

    /** 某张地图是否集齐。 */
    fun isMapComplete(state: GameState, map: FishingMap): Boolean =
        caughtInMap(state, map) == map.species.size

    /** 已集齐的地图数量。 */
    fun completedMaps(state: GameState): Int =
        Bestiary.maps.count { isMapComplete(state, it) }

    /**
     * 图鉴带来的总价值倍率。
     * 例：收集 30 种 + 集齐 2 张图 = 1 + 30×1.5% + 2×25% = 1.95×
     */
    fun multiplier(state: GameState): Double {
        val speciesBonus = state.caughtSpecies.size * PER_SPECIES_BONUS
        val mapBonus = completedMaps(state) * PER_MAP_COMPLETE_BONUS
        val allBonus = if (completedMaps(state) == Bestiary.maps.size) ALL_COMPLETE_BONUS else 0.0
        return 1.0 + speciesBonus + mapBonus + allBonus
    }

    /** 距离下一个里程碑还差多少（用于 UI 进度条）。 */
    fun nextMilestone(state: GameState): Pair<String, Float> {
        val caught = state.caughtSpecies.size
        val total = Bestiary.totalSpecies
        // 下一个 5 的倍数作为小里程碑
        val next = ((caught / 5) + 1) * 5
        if (next <= total) {
            val label = "再收集 ${next - caught} 种，收益再 +${"%.1f".format(5 * PER_SPECIES_BONUS * 100)}%"
            val progress = (caught % 5) / 5f
            return label to progress
        }
        // 已经收满，转向地图集齐进度
        val doneMaps = completedMaps(state)
        if (doneMaps < Bestiary.maps.size) {
            val nextMap = Bestiary.maps.first { !isMapComplete(state, it) }
            val have = caughtInMap(state, nextMap)
            val need = nextMap.species.size
            return "集齐「${nextMap.name}」还差 ${need - have} 种（+25% 收益）" to (have / need.toFloat())
        }
        return "图鉴已全收集！" to 1f
    }
}
