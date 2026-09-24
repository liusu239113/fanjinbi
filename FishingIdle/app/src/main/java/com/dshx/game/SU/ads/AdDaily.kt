package com.dshx.game.SU.ads

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 每日广告次数的轻量计数。
 *
 * 放在 SharedPreferences 而不是游戏存档里：它是「这台机器每天」的额度，
 * 不属于某个存档槽，也不该因为读档回到过去被重置。
 */
object AdDaily {

    private const val PREF = "fishing_ad_daily"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun today(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    /** [key] 今天已领取的次数（跨天自动归零）。 */
    fun used(ctx: Context, key: String): Int {
        val p = prefs(ctx)
        if (p.getString(key + "_day", "") == today()) return p.getInt(key + "_n", 0)
        return 0
    }

    /** 记一次领取，返回记完之后今天已领的次数。 */
    fun markUsed(ctx: Context, key: String): Int {
        val n = used(ctx, key) + 1
        prefs(ctx).edit().putString(key + "_day", today()).putInt(key + "_n", n).apply()
        return n
    }
}
