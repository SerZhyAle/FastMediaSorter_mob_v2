# TASK ML-010: EpubViewerManager.release() Missing WebViewDatabase Cleanup

**Priority**: MEDIUM  
**Area**: WebView / Data Retention  
**Component**: `EpubViewerManager`  
**Effort**: 1h  

---

## Problem

`EpubViewerManager.release()` (line 1125) properly calls `webView.destroy()`, but does **not** clear the `WebViewDatabase` which persists HTTP auth credentials and form data across WebView instances:

```kotlin
// EpubViewerManager.kt:1125–1141
fun release() {
    closeEpubBook()
    webView?.let { wv ->
        try {
            (wv.parent as? android.view.ViewGroup)?.removeView(wv)
            wv.removeAllViews()
            wv.clearCache(true)   // ✅ clears disk+memory cache
            wv.destroy()          // ✅ destroys WebView instance
        } catch (e: Exception) { ... }
    }
    webView = null
    // ❌ Missing: WebViewDatabase credential cleanup
}
```

`WebViewDatabase` stores:
- HTTP Basic/Digest auth credentials (host → username/password)
- Form autocompletion data

These persist across WebView recreations (e.g., on Activity rotation, theme change, or file reopen). If the EPUB's HTTP server uses authentication (e.g., local proxy for network EPUBs), credentials are retained indefinitely.

While this is not a traditional "memory leak," it is a **data retention risk** — credentials persist beyond the intended session lifetime.

**File**:
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/EpubViewerManager.kt` — L1125–1141

---

## Fix

Add `WebViewDatabase` cleanup in `release()`:

```kotlin
fun release() {
    closeEpubBook()
    webView?.let { wv ->
        try {
            (wv.parent as? android.view.ViewGroup)?.removeView(wv)
            wv.removeAllViews()
            wv.clearCache(true)
            wv.destroy()
            Timber.d("EpubViewerManager: WebView properly destroyed")
        } catch (e: Exception) {
            Timber.e(e, "EpubViewerManager: Error destroying WebView")
        }
    }
    
    // Clear WebView database credentials (persists across WebView instances)
    try {
        val webViewDb = android.webkit.WebViewDatabase.getInstance(context)
        webViewDb.clearHttpAuthUsernamePassword()
        Timber.d("EpubViewerManager: Cleared WebViewDatabase credentials")
    } catch (e: Exception) {
        Timber.w(e, "EpubViewerManager: Failed to clear WebViewDatabase")
    }
    
    webView = null
    Timber.d("EpubViewerManager: Released")
}
```

Ensure `context` is accessible in the `release()` method — confirm `EpubViewerManager` holds a `Context` reference (it does, passed in constructor).

---

## Test Plan

1. Open an EPUB that triggers HTTP auth (or mock one via local proxy)
2. Close and reopen the EPUB
3. Verify no pre-filled credentials from previous session in auth dialog
4. After `release()`, confirm: `WebViewDatabase.getInstance(context).hasHttpAuthUsernamePassword()` returns `false`

---

## Acceptance Criteria

- [ ] `WebViewDatabase.clearHttpAuthUsernamePassword()` called in `release()`
- [ ] Wrapped in try/catch to prevent crash if database is unavailable
- [ ] Timber log added for debugging
- [ ] No HTTP auth credentials persisted after `EpubViewerManager.release()`
