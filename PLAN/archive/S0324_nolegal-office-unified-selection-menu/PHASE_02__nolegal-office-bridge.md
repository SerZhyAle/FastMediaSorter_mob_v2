# Phase 02 - noLegal Office Bridge

**Strategic spec:** [`../S0324_nolegal-office-unified-selection-menu.md`](../S0324_nolegal-office-unified-selection-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-01
**Completed:** 2026-06-01

---

## Objective

Enable selected-text capture only inside noLegal app-generated Office HTML and expose the unified selection menu.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentEngineBridge.kt` | Modified | ≤ 290 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerManager.kt` | Modified | ≤ 230 |

---

## Steps

### Step 02.1 - Inject Office selection script into generated HTML

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentEngineBridge.kt`
**Depends on:** Phase 01

**Prompt for developer:**

> Update `OfficeDocumentEngineBridge.wrapDocument()` so app-generated Office HTML registers a `selectionchange` listener that calls `OfficeSelectionBridge.onSelectionChanged(window.getSelection().toString())` when the bridge exists. Keep all document-derived text escaped and do not render external scripts.

**Verification:**

- `Grep` - `OfficeSelectionBridge.onSelectionChanged` exists in `OfficeDocumentEngineBridge.kt`.
- `Grep` - `document.addEventListener('selectionchange'` exists in `OfficeDocumentEngineBridge.kt`.
- `Grep` - `escape(text)` still exists in `OfficeDocumentEngineBridge.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-01 - Verification 3/3 PASS. Expected bridge call: 1, actual: 1. Expected selectionchange listener: 1, actual: 1. Expected escaped document text helper: >=1, actual: 3. Files: `OfficeDocumentEngineBridge.kt`. Dev log recorded.

---

### Step 02.2 - Add noLegal Office WebView selection bridge

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add an `OfficeSelectionBridge` with a single `@JavascriptInterface onSelectionChanged(text: String)` method. Enable JavaScript in `createWebView()` only for this app-generated Office HTML surface, keep `allowFileAccess = false` and `allowContentAccess = false`, inject the bridge as `OfficeSelectionBridge`, and return a `DocumentSelectionActionModeCallback` that shows Translate according to `BuildConfig.ENABLE_TRANSLATION`, searches with `openGoogleSearch`, and delegates translation to `OfficeDocumentViewerHost.Callback.onTranslateSelection`.

**Verification:**

- `Grep` - `@JavascriptInterface` exists in `OfficeDocumentViewerManager.kt`.
- `Grep` - `addJavascriptInterface(selectionBridge, "OfficeSelectionBridge")` exists in `OfficeDocumentViewerManager.kt`.
- `Grep` - `javaScriptEnabled = true` exists in `OfficeDocumentViewerManager.kt`.
- `Grep` - `allowFileAccess = false` exists in `OfficeDocumentViewerManager.kt`.
- `Grep` - `override fun getSelectionActionModeCallback()` exists in `OfficeDocumentViewerManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-01 - Verification 5/5 PASS. Expected `@JavascriptInterface`: 1, actual: 1. Expected `addJavascriptInterface`: 1, actual: 1. Expected `javaScriptEnabled = true`: 1, actual: 1. Expected `allowFileAccess = false`: 1, actual: 1. Expected selection callback override: 1, actual: 1. Files: `OfficeDocumentViewerManager.kt`. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Kotlin catalog sync passes via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `rg -n "Log\.d\(" <touched kotlin files>` returns zero hits.

---

## Handoff Notes to Next Phase

Office WebView selected text is available synchronously through the same action callback used by EPUB.

---

## Rollback Plan

Revert phase commit(s) - no data migration or persistent state change.
