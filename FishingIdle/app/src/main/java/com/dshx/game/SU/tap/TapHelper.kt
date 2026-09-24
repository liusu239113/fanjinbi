package com.dshx.game.SU.tap

import android.app.Activity
import android.content.Context
import android.util.Log
import com.taptap.sdk.compliance.option.TapTapComplianceOptions
import com.taptap.sdk.core.TapTapRegion
import com.taptap.sdk.core.TapTapSdk
import com.taptap.sdk.core.TapTapSdkOptions
import com.taptap.sdk.kit.internal.callback.TapTapCallback
import com.taptap.sdk.kit.internal.exception.TapTapException
import com.taptap.sdk.login.Scopes
import com.taptap.sdk.login.TapTapAccount
import com.taptap.sdk.login.TapTapLogin

/**
 * TapTap 登录（账号部分）。防沉迷认证见 [ComplianceManager]。
 *
 * 时序（与参考项目一致）：
 *   隐私同意 -> TapHelper.init（TapTapSdk.init）
 *            -> TapHelper.login（拉起登录）
 *            -> 登录成功 -> ComplianceManager.startup(openId)
 *            -> 收到 LOGIN_SUCCESS(500) 才放行
 *
 * 必须在**用户同意隐私政策之后**再调用 [init]，否则 SDK 会在同意前读取设备标识。
 */
object TapHelper {

    private const val TAG = "TapHelper"
    private const val PREF = "fishing_tap"

    /** 登录态变化回调（openId 为 null 表示已退出/切换账号）。 */
    interface Listener {
        fun onLoginChanged(openId: String?)
    }

    @Volatile
    var listener: Listener? = null

    @Volatile
    private var initialized = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun savedOpenId(ctx: Context): String? = prefs(ctx).getString("open_id", null)

    private fun saveOpenId(ctx: Context, openId: String?) {
        if (openId == null) prefs(ctx).edit().remove("open_id").apply()
        else prefs(ctx).edit().putString("open_id", openId).apply()
    }

    /** 清掉本地登录记录（切换账号 / 退出登录时用）。 */
    fun clearOpenId(ctx: Context) {
        saveOpenId(ctx, null)
    }

    /** 初始化 TapTap SDK（幂等；隐私同意后才可调用）。未配置凭据时跳过。 */
    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        if (!TapConfig.isConfigured) {
            Log.w(TAG, "TapTap 凭据未配置，跳过初始化")
            return
        }
        try {
            TapTapSdk.init(
                context.applicationContext,
                TapTapSdkOptions(
                    clientId = TapConfig.CLIENT_ID,
                    clientToken = TapConfig.CLIENT_TOKEN,
                    region = TapTapRegion.CN,
                    enableLog = false
                ),
                options = arrayOf(
                    TapTapComplianceOptions(
                        showSwitchAccount = true,
                        useAgeRange = false
                    )
                )
            )
            initialized = true
            Log.i(TAG, "TapTap SDK initialized")
        } catch (t: Throwable) {
            initialized = false
            Log.e(TAG, "TapTap SDK init failed", t)
        }
    }

    fun isInitialized(): Boolean = initialized

    /** 当前已登录账号的 openId（未登录为 null）。 */
    fun currentOpenId(): String? = try {
        TapTapLogin.getCurrentTapAccount()?.openId
    } catch (t: Throwable) {
        null
    }

    /**
     * 拉起 TapTap 登录。[onDone] 参数：(是否成功, 提示语)。
     * 登录成功会自动启动防沉迷认证；放行与否由 [ComplianceManager] 的回调决定。
     * 回调在主线程。
     */
    fun login(activity: Activity, onDone: (Boolean, String) -> Unit) {
        if (!TapConfig.isConfigured) {
            onDone(false, "TapTap 登录尚未配置（缺少 client id）")
            return
        }
        try {
            TapTapLogin.loginWithScopes(
                activity,
                arrayOf(Scopes.SCOPE_PUBLIC_PROFILE),
                object : TapTapCallback<TapTapAccount> {
                    override fun onSuccess(result: TapTapAccount) {
                        val openId = result.openId
                        Log.i(TAG, "login success openId=$openId")
                        saveOpenId(activity, openId)
                        listener?.onLoginChanged(openId)
                        // 登录成功后立刻做防沉迷校验（回调里才会放行）
                        ComplianceManager.startup(activity, openId ?: "")
                        onDone(true, "登录成功，正在校验防沉迷…")
                    }

                    override fun onCancel() {
                        onDone(false, "登录已取消")
                    }

                    override fun onFail(exception: TapTapException) {
                        Log.e(TAG, "login failed: ${exception.message}")
                        onDone(false, "登录失败：" + (exception.message ?: "未知错误"))
                    }
                }
            )
        } catch (t: Throwable) {
            // 设备未安装 TapTap 客户端等情况，SDK 内部可能直接抛异常
            Log.e(TAG, "loginWithScopes crashed", t)
            onDone(false, "无法拉起 TapTap 登录（是否未安装客户端？）")
        }
    }

    /** 退出登录：清本地记录 + 重置防沉迷状态。 */
    fun logout(context: Context) {
        clearOpenId(context)
        ComplianceManager.exit()
        listener?.onLoginChanged(null)
    }
}
