package com.mazkiplay.cinema

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat

class MainActivity : AppCompatActivity() {

    companion object {
        const val HOST = "appassets.androidplatform.net"
        const val START_URL = "https://$HOST/assets/index.html"
        const val UA_TAG = " M4zk1pL4yCinema/7.0"
        private const val TAG = "CinemaWeb"
    }

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var bar: ProgressBar
    private lateinit var errorBox: View

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val assetLoader: WebViewAssetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .addPathHandler("/res/", WebViewAssetLoader.ResourcesPathHandler(this))
            .build()
    }

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val cb = fileCallback ?: return@registerForActivityResult
            fileCallback = null
            var uris: Array<Uri>? = null
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                if (data != null) {
                    val clip = data.clipData
                    uris = if (clip != null) {
                        Array(clip.itemCount) { clip.getItemAt(it).uri }
                    } else {
                        data.data?.let { arrayOf(it) }
                    }
                }
            }
            cb.onReceiveValue(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        webView = findViewById(R.id.web)
        bar = findViewById(R.id.bar)
        errorBox = findViewById(R.id.errorBox)

        findViewById<View>(R.id.errorRetry).setOnClickListener {
            errorBox.visibility = View.GONE
            webView.reload()
        }

        setupWebView()
        setupBack()

        if (savedInstanceState == null) webView.loadUrl(START_URL)
        else webView.restoreState(savedInstanceState)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)

        webView.setBackgroundColor(0xFF08070D.toInt())
        webView.isScrollbarFadingEnabled = true

        val ws = webView.settings
        ws.javaScriptEnabled = true
        ws.domStorageEnabled = true
        ws.databaseEnabled = true
        ws.mediaPlaybackRequiresUserGesture = false
        ws.javaScriptCanOpenWindowsAutomatically = true
        ws.setSupportMultipleWindows(false)
        ws.loadWithOverviewMode = true
        ws.useWideViewPort = true
        ws.allowFileAccess = true
        ws.allowContentAccess = true
        ws.cacheMode = WebSettings.LOAD_DEFAULT
        ws.userAgentString = ws.userAgentString + UA_TAG
        ws.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        ws.setGeolocationEnabled(true)
        ws.setOffscreenPreRaster(true)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(AppBridge(this, webView), "AndroidBridge")

        webView.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): android.webkit.WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url
                if (url.host == HOST) return false
                return openExternal(url)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                bar.visibility = View.VISIBLE
                errorBox.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                bar.visibility = View.GONE
                injectViewport()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    bar.visibility = View.GONE
                    errorBox.visibility = View.VISIBLE
                    Log.w(TAG, "main frame error: ${error?.description}")
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                bar.progress = newProgress
                bar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.let { runOnUiThread { it.grant(it.resources) } }
            }

            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                val intent = try {
                    params?.createIntent()
                } catch (e: Exception) {
                    null
                } ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                intent.putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf("text/vtt", "text/plain", "application/x-subrip", "video/*")
                )
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                return try {
                    filePicker.launch(intent)
                    true
                } catch (e: ActivityNotFoundException) {
                    fileCallback = null
                    false
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                root.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                webView.visibility = View.GONE
                hideSystemBars()
            }

            override fun onHideCustomView() {
                customView?.let { root.removeView(it) }
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                webView.visibility = View.VISIBLE
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                showSystemBars()
            }

            override fun onConsoleMessage(msg: android.webkit.ConsoleMessage?): Boolean {
                Log.d(TAG, "[${msg?.messageLevel}] ${msg?.message()} @${msg?.lineNumber()}")
                return true
            }
        }

        webView.setDownloadListener { url, _, contentDisposition, mime, _ ->
            AppBridge(this, webView).download(url, URLUtil.guessFileName(url, contentDisposition, mime))
        }
    }

    private fun setupBack() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript("(window.__back && window.__back()) || 0") { raw ->
                    val handled = raw?.trim()?.trim('"') == "1"
                    if (!handled) {
                        if (webView.canGoBack()) webView.goBack() else moveTaskToBack(true)
                    }
                }
            }
        })
    }

    /** Kirim info inset ke halaman supaya padding aman-notch akurat. */
    private fun injectViewport() {
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val js = "document.documentElement.style.setProperty('--safe-top','${bars.top}px');" +
                    "document.documentElement.style.setProperty('--safe-bottom','${bars.bottom}px');"
            webView.evaluateJavascript(js, null)
            insets
        }
        root.requestApplyInsets()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showSystemBars() {
        WindowInsetsControllerCompat(window, root).show(WindowInsetsCompat.Type.systemBars())
    }

    private fun openExternal(uri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Tidak ada aplikasi untuk membuka tautan", Toast.LENGTH_SHORT).show()
            true
        }
    }

    fun keepAwake(on: Boolean) {
        runOnUiThread {
            if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun enqueueDownload(rawUrl: String, fileName: String) {
        runOnUiThread {
            try {
                val name = fileName.ifBlank { "video_${System.currentTimeMillis()}.mp4" }
                val req = DownloadManager.Request(Uri.parse(rawUrl))
                    .setTitle(name)
                    .setDescription("M4zk1pL4y Cinema")
                    .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                    .addRequestHeader("Referer", "https://jambudio336-hue.github.io/")
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                Toast.makeText(this, "Mengunduh: $name", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "download gagal", e)
                Toast.makeText(this, "Gagal mengunduh, coba Simpan File", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toast(msg: String) {
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    fun share(text: String, url: String) {
        runOnUiThread {
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "M4zk1pL4y Cinema")
                putExtra(Intent.EXTRA_TEXT, "$text\n$url")
            }
            startActivity(Intent.createChooser(i, "Bagikan drama"))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() {
        webView.onPause()
        webView.pauseTimers()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.resumeTimers()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.removeJavascriptInterface("AndroidBridge")
        webView.destroy()
        super.onDestroy()
    }
}
