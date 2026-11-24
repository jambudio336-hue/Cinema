package com.mazkiplay.cinema

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Rational
import android.widget.Button
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import org.json.JSONArray

class PlayerActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_EP = "ep"
        const val EXTRA_START = "start"
        const val EXTRA_QUALITIES = "qualities"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_AUTOPLAY = "autoplay"

        private const val UA =
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        private const val TAG = "CinemaPlayer"
    }

    private lateinit var playerView: PlayerView
    private lateinit var player: ExoPlayer
    private lateinit var qualityButton: Button
    private var episode = 1
    private var durationMs = 0L
    private var retries = 0
    private var currentUrl = ""
    private var qualities: JSONArray? = null
    private val attemptedUrls = mutableSetOf<String>()
    private val ui = Handler(Looper.getMainLooper())

    private val progressTick = object : Runnable {
        override fun run() {
            reportProgress()
            ui.postDelayed(this, 3000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_player)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        playerView = findViewById(R.id.pview)
        qualityButton = findViewById(R.id.pqual)
        episode = intent.getIntExtra(EXTRA_EP, 1)
        currentUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        val name = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        findViewById<TextView>(R.id.ptitle).text = "$name — Ep $episode"
        qualityButton.text = intent.getStringExtra(EXTRA_QUALITY).orEmpty().ifBlank { "Auto" }
        qualities = runCatching {
            JSONArray(intent.getStringExtra(EXTRA_QUALITIES).orEmpty())
        }.getOrNull()

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(UA)
            // Hindari header Referer/Origin TikTok yang tidak cocok dengan host stream.
            .setDefaultRequestProperties(mapOf("Accept" to "*/*"))
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
        playerView.player = player

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    retries = 0
                    durationMs = player.duration.takeIf { it > 0L } ?: 0L
                } else if (state == Player.STATE_ENDED) {
                    reportProgress()
                    MainActivity.instance?.evalJs(
                        "window.__nativeEnded && window.__nativeEnded($episode)"
                    )
                    finish()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Playback error: ${error.errorCodeName}", error)
                if (retries < 1 && !isFinishing) {
                    retries++
                    val resumeAt = player.currentPosition.coerceAtLeast(0L)
                    ui.postDelayed(
                        { if (!isFinishing) play(currentUrl, resumeAt) },
                        800L
                    )
                } else if (!isFinishing && tryNextQuality()) {
                    retries = 0
                } else {
                    Toast.makeText(
                        this@PlayerActivity,
                        "Video gagal diputar: ${error.errorCodeName}. Coba kualitas lain atau periksa koneksi.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })

        findViewById<ImageButton>(R.id.pback).setOnClickListener { closePlayer() }
        qualityButton.setOnClickListener { showQualityMenu() }
        findViewById<Button>(R.id.pout).setOnClickListener {
            runCatching {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure {
                Toast.makeText(this, "Tidak ada pemutar eksternal", Toast.LENGTH_SHORT).show()
            }
        }

        hideSystemBars()
        play(currentUrl, intent.getLongExtra(EXTRA_START, 0L))
        ui.post(progressTick)
    }

    private fun play(url: String, startMs: Long) {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (uri == null || uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) {
            Toast.makeText(this, "URL video tidak valid", Toast.LENGTH_LONG).show()
            return
        }
        currentUrl = url
        attemptedUrls.add(url)
        val path = uri.path.orEmpty().lowercase()
        val item = MediaItem.Builder().setUri(uri).apply {
            when {
                path.endsWith(".m3u8") -> setMimeType(MimeTypes.APPLICATION_M3U8)
                path.endsWith(".mpd") -> setMimeType(MimeTypes.APPLICATION_MPD)
            }
        }.build()
        player.setMediaItem(item, startMs.coerceAtLeast(0L))
        player.prepare()
        player.playWhenReady = true
    }

    /** Pindah otomatis ke URL kualitas lain jika stream pilihan gagal. */
    private fun tryNextQuality(): Boolean {
        val list = qualities ?: return false
        for (index in 0 until list.length()) {
            val item = list.optJSONObject(index) ?: continue
            val url = item.optString("url").trim()
            if (url.isBlank() || url in attemptedUrls) continue
            val label = item.optString("label", "Auto")
            qualityButton.text = label
            Log.w(TAG, "Fallback stream ke kualitas $label")
            play(url, 0L)
            Toast.makeText(this, "Mencoba kualitas $label…", Toast.LENGTH_SHORT).show()
            return true
        }
        return false
    }

    private fun showQualityMenu() {
        val list = qualities ?: return
        if (list.length() == 0) {
            Toast.makeText(this, "Kualitas video tidak tersedia", Toast.LENGTH_SHORT).show()
            return
        }
        val menu = PopupMenu(this, qualityButton)
        for (index in 0 until list.length()) {
            val item = list.optJSONObject(index) ?: continue
            menu.menu.add(0, index, index, item.optString("label", "Auto"))
        }
        menu.setOnMenuItemClickListener { selected ->
            val item = list.optJSONObject(selected.itemId)
            val url = item?.optString("url").orEmpty()
            if (url.isNotBlank()) {
                val position = player.currentPosition.coerceAtLeast(0L)
                qualityButton.text = item?.optString("label").orEmpty().ifBlank { "Auto" }
                retries = 0
                attemptedUrls.clear()
                play(url, position)
            }
            true
        }
        menu.show()
    }

    private fun reportProgress() {
        if (!::player.isInitialized) return
        val duration = durationMs.takeIf { it > 0L } ?: player.duration.takeIf { it > 0L } ?: 0L
        if (duration <= 0L) return
        val positionSec = player.currentPosition.coerceAtLeast(0L) / 1000L
        val durationSec = duration / 1000L
        MainActivity.instance?.evalJs(
            "window.__nativeProgress && window.__nativeProgress($episode,$positionSec,$durationSec)"
        )
    }

    private fun closePlayer() {
        reportProgress()
        val position = player.currentPosition.coerceAtLeast(0L) / 1000L
        val duration = durationMs.coerceAtLeast(0L) / 1000L
        MainActivity.instance?.evalJs(
            "window.__nativeClosed && window.__nativeClosed($episode,$position,$duration)"
        )
        finish()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, findViewById(R.id.proot)).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player.isPlaying) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                )
            }
        }
        super.onUserLeaveHint()
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        closePlayer()
    }

    override fun onStop() {
        reportProgress()
        if (!isInPictureInPictureMode) player.pause()
        super.onStop()
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        if (::player.isInitialized) {
            playerView.player = null
            player.release()
        }
        super.onDestroy()
    }
}
