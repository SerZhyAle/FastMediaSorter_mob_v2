# Phase 04 — WebView-auth dialog: handle `intent://` and custom-scheme redirects

**Strategic spec:** [`../S0144_fix-link-download-auth-ux.md`](../S0144_fix-link-download-auth-ux.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none
**Blocks:** Phase 05
**Steps done:** 1 / 1
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Stop the in-app WebView-auth dialog from failing with `net::ERR_UNKNOWN_URL_SCHEME` when a site (e.g. Instagram) redirects to `intent://…` or an app-scheme URL: intercept navigation, never hand non-`http(s)` schemes to the engine, and load the `browser_fallback_url` from an `intent:` URI when present.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Coordinate with S0141 (same file) — apply on top of its changes, do not revert the full-screen sizing or `WebChromeClient` additions.
- [ ] Coordinate with S0140 — if it lands an `intent://` / `market://` parser helper, reuse it instead of duplicating parsing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthDialogFragment.kt` | Modified | ≤ 240 |

---

## Steps

### Step 04.1 — Override `shouldOverrideUrlLoading` to gate non-http(s) navigation

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthDialogFragment.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In the `WebViewClient` anonymous object inside `configureWebView`, add `override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean`. Read the request URL; if its scheme is `http` or `https`, return `false` (let the WebView load it normally). Otherwise:
> - If the scheme is `intent`, parse it with `Intent.parseUri(url, Intent.URI_INTENT_SCHEME)`; if the parsed intent carries a `browser_fallback_url` extra, call `view?.loadUrl(fallbackUrl)`; either way return `true` so the engine never tries to navigate to `intent://` itself. Wrap the parse in a `runCatching` — a malformed `intent:` URI must not crash the dialog.
> - For any other non-`http(s)` scheme (app schemes like `instagram://`, `market://`, `mailto:`, `tel:`, etc.) return `true` without navigating.
>
> On every interception emit `LinkDownloadTrace.verbose("webview-auth blocked-redirect scheme=<scheme> host=<host>")` (no full URL with query — host only) and add a single `Timber.d("S0144: webview-auth redirect intercepted")` at the top of the override. Do not change `harvestAndDismiss`, `onReceivedError`, or `onStart`. Keep `Log.d` out — Timber only.

**Verification:**

- `Grep` — `override fun shouldOverrideUrlLoading` present in the file.
- `Grep` — `Intent.parseUri` present in the file.
- `Grep` — `browser_fallback_url` present in the file.
- `Grep` — `Timber.d("S0144:` present in the file.
- `Grep` — `LinkDownloadTrace.verbose("webview-auth blocked-redirect` present in the file.
- `Grep -n "Log\.d\("` — zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Verification 6/6 PASS (override 1, parseUri 1, browser_fallback_url 1, S0144 tag 1, blocked-redirect trace 1, Log.d 0). Files: WebViewAuthDialogFragment.kt. Dev log recorded. Note: applied on top of S0141 changes (full-screen sizing + WebChromeClient retained).

---

## Phase Done Criteria

- [x] Step 04.1 is `[x] done`.
- [x] Project compiles — `build-debug.PS1` → BUILD SUCCESSFUL (2026-05-10).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for the modified file.
- [ ] On-device smoke (deferred — covered by `BlockNeedUserTest`): opening Instagram via the auth dialog lands on the login page, no `ERR_UNKNOWN_URL_SCHEME`.

---

## Handoff Notes to Next Phase

The WebView-auth dialog now survives `intent://` redirects. Phase 05 adds a fragment-result signal to this same dialog and triggers it proactively from the share flow.

---

## Rollback Plan

Revert phase commit — single-file change, no data migration. S0141 changes remain intact.
