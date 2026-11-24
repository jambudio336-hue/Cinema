package com.mazkiplay.cinema

import android.app.Application
import android.webkit.WebView

class CinemaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
    }
}
