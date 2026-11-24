package com.mazkiplay.cinema

import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView

class AppBridge(
    private val activity: MainActivity,
    private val webView: WebView
) {

    @JavascriptInterface
    fun download(url: String, name: String) = activity.enqueueDownload(url, name)

    @JavascriptInterface
    fun openPlayer(url: String) {
        try {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            activity.toast("Tidak ada pemutar video eksternal")
        }
    }

    @JavascriptInterface
    fun openUrl(url: String) {
        try {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            activity.toast("Gagal membuka tautan")
        }
    }

    @JavascriptInterface
    fun keepAwake(on: Boolean) = activity.keepAwake(on)

    @JavascriptInterface
    fun toast(msg: String) = activity.toast(msg)

    @JavascriptInterface
    fun share(text: String, url: String) = activity.share(text, url)

    @JavascriptInterface
    fun version(): String = BuildConfig.VERSION_NAME

    @JavascriptInterface
    fun reload() {
        activity.runOnUiThread { webView.reload() }
    }

    @JavascriptInterface
    fun exitApp() {
        activity.runOnUiThread { activity.finish() }
    }
}
