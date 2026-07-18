package com.example

import android.webkit.WebView
import java.util.Locale

internal const val UKRNET_SENDMSG_URL = "https://mail.ukr.net/touch/u0/sendmsg/"

internal fun String?.isUkrnetTouchUrl(): Boolean {
    val value = this?.lowercase(Locale.getDefault()) ?: return false
    return value.contains("mail.ukr.net/touch/u0/")
}

internal fun String?.isSendMsgUrl(): Boolean {
    val value = this?.lowercase(Locale.getDefault()) ?: return false
    return value.contains("mail.ukr.net/touch/u0/sendmsg/")
}

internal fun WebView?.forceSendMsg(log: (String) -> Unit, source: String) {
    val view = this ?: return
    val currentUrl = view.url ?: return
    if (!currentUrl.isUkrnetTouchUrl() || currentUrl.isSendMsgUrl()) return
    log("[ComposeGuardian] $source -> возвращаемся на sendmsg")
    view.post {
        try { view.stopLoading() } catch (_: Throwable) {}
        view.loadUrl(UKRNET_SENDMSG_URL)
    }
}