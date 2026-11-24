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
    fun playVideo(
        dramaId: String,
        title: String,
        cover: String,
        ep: Int,
        qualitiesJson: String,
        url: String,
        startSec: Int,
        autoplay: Int,
        quality: String
    ) {
        val intent = Intent(activity, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URL, url)
            putExtra(PlayerActivity.EXTRA_TITLE, title)
            putExtra(PlayerActivity.EXTRA_EP, ep)
            putExtra(PlayerActivity.EXTRA_START, startSec.coerceAtLeast(0) * 1000L)
            putExtra(PlayerActivity.EXTRA_QUALITIES, qualitiesJson)
            putExtra(PlayerActivity.EXTRA_AUTOPLAY, autoplay != 0)
            putExtra(PlayerActivity.EXTRA_QUALITY, quality)
        }
        activity.runOnUiThread {
            if (!activity.isFinishing) activity.startActivity(intent)
        }
    }

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

    /** Token API disediakan dari konfigurasi build, bukan dari HTML/repository. */
    @JavascriptInterface
    fun meloloToken(): String = BuildConfig.MELOLO_TOKEN

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
