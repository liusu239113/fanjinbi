package com.dshx.game.SU.tap

/** TapTap 开放平台凭据（来自开发者后台；换包/换账号时只改这里）。 */
object TapConfig {
    const val CLIENT_ID = "i1u52u53dpgd1ozdug"
    const val CLIENT_TOKEN = "NG69jaY6HfAo08xasQj0Tx27jdmzjsule183TuGD"

    /** 凭据是否已配置。 */
    val isConfigured: Boolean get() = CLIENT_ID.isNotBlank() && CLIENT_TOKEN.isNotBlank()
}
