# S0141 — bugfix: WebView-auth dialog renders with zero size

**Ticket:** S0141
**Status:** Verified
**Priority:** 90
**Date:** 2026-05-10
**Tier:** 1 — Quick Win (critical bugfix)

---

## Problem

`WebViewAuthDialogFragment` (S0116 pillar L) opens as a `DialogFragment` without explicitly sizing its window. Android applies the default `wrap_content` window size to the dialog; the embedded `WebView` has no intrinsic dimension before any page is laid out, so the dialog collapses to the height of the bottom button row. User sees only "Cancel / Save authorization" buttons, no WebView, cannot authenticate to any domain. Verified on `instagram.com` URL — page never appears.

---

## Approach

- `WebViewAuthDialogFragment.kt` — add `onStart()` that calls `dialog?.window?.setLayout(MATCH_PARENT, MATCH_PARENT)` so the dialog window expands full-screen and the WebView gets non-zero measured height. Also set `WebChromeClient` so JS-confirm/alert dialogs and progress events do not silently break login flows on JS-heavy login pages. Also add `onReceivedError` logging to surface load failures via `LinkDownloadTrace.verbose`. Insert one `Timber.d("S0141: webview-auth dialog full-screen sized")` at `onStart` entry.

---

## Done criteria

- Opening "+ Add authorization" with `https://www.instagram.com/accounts/login/` shows the actual login page filling the screen (not just the bottom button row).
- WebView accepts touch input and JS-rendered login form interacts normally.
- Logcat shows `S0141: webview-auth dialog full-screen sized` exactly once per dialog open.
- Cancel/Save buttons remain visible at the bottom of the dialog.

---

## Notes

- Long-term UX improvements (Save disabled until ≥1 cookie present, hint-bar telling user to log in first, blocking empty-session save) are tracked in S0140 phase 7 — out of scope here. This bugfix only restores the WebView's visibility.

---

## Last Audit

**Date:** 2026-05-10  
**Result:** Verified ✅

**Approach coverage:**
- `onStart()` calls `dialog?.window?.setLayout(MATCH_PARENT, MATCH_PARENT)` — present at lines 86-89. ✅
- `WebChromeClient` with `onProgressChanged` wired to `refreshSaveButtonState` — present. ✅
- `onReceivedError` logs main-frame failures via `LinkDownloadTrace.verbose` — present, gated on `request?.isForMainFrame`. ✅
- No stale `Timber.d("S0141:` tags remain in any `.kt` file. ✅

**Done criteria:**
- Full-screen login page (instagram.com) — confirmed by user during `BlockNeedUserTest` phase. ✅
- Touch input and JS login form interaction — confirmed on-device. ✅
- Logcat probe fired exactly once per open — confirmed during device test; tag removed per protocol after exit from `BlockNeedUserTest`. ✅
- Cancel/Save buttons visible — layout `dialog_webview_auth.xml` unchanged; both IDs (`btnWebviewAuthCancel`, `btnWebviewAuthSave`) confirmed present. ✅

**Build:** `assembleStandardDebug` — PASS (1m 11s).

**Action items:** none.
