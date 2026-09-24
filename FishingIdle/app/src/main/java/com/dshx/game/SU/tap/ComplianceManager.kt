package com.dshx.game.SU.tap

import android.app.Activity
import android.util.Log
import com.taptap.sdk.compliance.TapTapCompliance
import com.taptap.sdk.compliance.TapTapComplianceCallback
import com.taptap.sdk.compliance.constants.ComplianceMessage

/**
 * TapTap 防沉迷（合规认证）。
 *
 * 接法与参考项目一致：
 *   1. 隐私政策同意后 -> TapTapSdk.init（见 TapHelper.init）
 *   2. 注册回调 -> register(listener)
 *   3. 登录成功 -> startup(activity, openId)
 *   4. 只有收到 LOGIN_SUCCESS(500) 才算通过，其余一律不放行
 *   5. 退出登录 -> exit()
 *
 * 合规红线：拦截状态下不允许存在任何「就地解除」的路径。
 * 重新校验只能重新发起认证，必须等 SDK 回调才能放行。
 */
object ComplianceManager {

    private const val TAG = "ComplianceManager"

    /** SDK 的 ComplianceMessage 没有导出 AGE_LIMIT 常量，这里按官方文档补上。 */
    private const val CODE_AGE_LIMIT = 1100

    /** 防沉迷判定结果。回调可能不在主线程，调用方自行切主线程。 */
    interface Listener {
        /** code=500：唯一允许进入游戏的结果。 */
        fun onLoginSuccess()

        /** code=1000：退出认证，回登录页。 */
        fun onExited()

        /** code=1001：切换账号，回登录页。 */
        fun onSwitchAccount()

        /** code=1030：宵禁时段，不可进入。 */
        fun onPeriodRestrict()

        /** code=1050：今日可玩时长已用完，不可进入。 */
        fun onDurationLimit()

        /** code=1100：年龄限制，不可进入。 */
        fun onAgeLimit()

        /** code=9002：实名过程中关闭窗口，不可跳过。 */
        fun onRealNameStop()

        /** code=1200 及其它异常。 */
        fun onError(message: String)
    }

    @Volatile
    private var listener: Listener? = null

    @Volatile
    private var registered = false

    /** 最近一次收到的 code。真机排查「为什么进不去」时看这条日志。 */
    @Volatile
    var lastCode: Int = -1
        private set

    /**
     * 注册防沉迷回调（幂等）。
     * 必须在 TapTapSdk.init 之后调用；重复调用只会更新 listener，不会重复注册。
     */
    @Synchronized
    fun register(l: Listener) {
        listener = l
        if (registered) return
        if (!TapHelper.isInitialized()) {
            Log.w(TAG, "TapTap SDK 未初始化，跳过防沉迷回调注册")
            return
        }
        try {
            TapTapCompliance.registerComplianceCallback(
                callback = object : TapTapComplianceCallback {
                    override fun onComplianceResult(code: Int, extra: Map<String, Any>?) {
                        lastCode = code
                        Log.i(TAG, "onComplianceResult code=$code extra=$extra")
                        val cb = listener ?: return
                        when (code) {
                            ComplianceMessage.LOGIN_SUCCESS -> cb.onLoginSuccess()
                            ComplianceMessage.EXITED -> cb.onExited()
                            ComplianceMessage.SWITCH_ACCOUNT -> cb.onSwitchAccount()
                            ComplianceMessage.PERIOD_RESTRICT -> cb.onPeriodRestrict()
                            ComplianceMessage.DURATION_LIMIT -> cb.onDurationLimit()
                            ComplianceMessage.REAL_NAME_STOP -> cb.onRealNameStop()
                            ComplianceMessage.INVALID_CLIENT_OR_NETWORK_ERROR ->
                                cb.onError("防沉迷服务连接失败，请检查网络后重试")
                            CODE_AGE_LIMIT -> cb.onAgeLimit()
                            else -> {
                                // 未知 code 只记录、不拦截：SDK 版本升级可能新增信息类 code，
                                // 一律拦掉会把正常玩家挡在门外。已知的限制类 code 上面已全部覆盖。
                                Log.w(TAG, "unhandled compliance code=$code (not blocking)")
                            }
                        }
                    }
                }
            )
            registered = true
        } catch (t: Throwable) {
            registered = false
            Log.e(TAG, "registerComplianceCallback failed", t)
        }
    }

    /** 开始防沉迷认证。[userId] 用 TapTap openId（为空则跳过）。 */
    fun startup(activity: Activity, userId: String) {
        if (userId.isBlank()) {
            Log.w(TAG, "startup skipped: empty userId")
            return
        }
        if (!TapHelper.isInitialized()) {
            Log.w(TAG, "TapTap SDK 未初始化，跳过防沉迷认证")
            return
        }
        Log.i(TAG, "startup userId=$userId")
        try {
            TapTapCompliance.startup(activity, userId)
        } catch (t: Throwable) {
            Log.e(TAG, "startup failed", t)
        }
    }

    /** 退出登录时调用，重置防沉迷状态。 */
    fun exit() {
        Log.i(TAG, "exit")
        try {
            TapTapCompliance.exit()
        } catch (t: Throwable) {
            Log.e(TAG, "exit failed", t)
        }
    }

    /**
     * 年龄段：-1 未知 / 0(0-7岁) / 8(8-15岁) / 16(16-17岁) / 18(成年)。
     * 仅用于展示，不作为放行依据。
     */
    fun getAgeRange(): Int = try {
        TapTapCompliance.getAgeRange()
    } catch (t: Throwable) {
        -1
    }

    /** 今日剩余可玩时长（秒），-1 表示未知。 */
    fun getRemainingTime(): Int = try {
        TapTapCompliance.getRemainingTime()
    } catch (t: Throwable) {
        -1
    }
}
