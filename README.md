# M4zk1pL4y Cinema

Web app + APK WebView. `index.html` di root dipakai GitHub Pages,
salinan di `app/src/main/assets/` dipakai APK (disinkronkan otomatis oleh workflow).

## Build
Push ke `main` → Actions → unduh artifact `M4zk1pL4yCinema-apk`.
Atau lokal: `gradle :app:assembleDebug`.

## Catatan teknis
- Halaman dimuat via `WebViewAssetLoader` (origin `https://appassets.androidplatform.net`) → localStorage, fetch, dan CORS berperilaku normal.
- `mediaPlaybackRequiresUserGesture=false` supaya autoplay tidak ditolak.
- Fullscreen HTML memakai `onShowCustomView` (landscape + immersive).
- Unduhan lewat `DownloadManager` + fallback blob/share.
- Tombol back fisik diarahkan ke `window.__back()`.
