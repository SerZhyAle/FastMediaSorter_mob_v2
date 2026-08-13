# Phase 02 - Document host print

**Strategic spec:** [`../S0613_standalone-document-text-print-send-to.md`](../S0613_standalone-document-text-print-send-to.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-22
**Completed:** 2026-06-22

---

## Objective

Make `DocumentStandaloneActivity` a print host: declare `SharePrintHost` + `DocumentPrintHost` so the Print receiver appears in its «Отправить в..» menu for PDF and Office, routing through the shared `DocumentPrintManager`.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`DocumentPrintHost` + host-agnostic `DocumentPrintManager` exist).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/DocumentStandaloneActivity.kt` | Modified (731 LOC - backup to `temp/`, >500) | ≤ 785 |

> No layout/XML edit (no new UI - the receiver renders inside the existing Send-to menu). No new strings (print messages reused from `DocumentPrintManager`).

---

## Steps

### Step 2.1 - Declare print-host interfaces, inject capabilities, add the manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/DocumentStandaloneActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Backup the file to `temp/` first (>500 LOC). Add `com.sza.fastmediasorter.core.share.SharePrintHost` and `com.sza.fastmediasorter.ui.player.helpers.DocumentPrintHost` to the class's implemented interfaces. Add `@Inject lateinit var mediaCapabilities: com.sza.fastmediasorter.core.capability.MediaCapabilities` (field injection - `MediaCapabilities` is already Hilt-provided, used by `PlayerActivity`). Add a lazy `private val documentPrintManager by lazy { com.sza.fastmediasorter.ui.player.helpers.DocumentPrintManager(host = this, mediaCapabilities = mediaCapabilities) }`. Do not alter the overflow menu - `R.id.menu_print` must stay in the hidden-items list (print is reached via Send-to, not the overflow; ADR-1).

**Verification:**

- `Grep` - `SharePrintHost` and `DocumentPrintHost` both present in the class declaration region.
- `Grep` - `@Inject lateinit var mediaCapabilities` present.
- `Grep` - `DocumentPrintManager(host = this` present.
- `Grep` - `R.id.menu_print` still present in the hidden-items `listOf(..)` (overflow unchanged).

**Status:** `[x]` done

---

### Step 2.2 - Implement the `DocumentPrintHost` seam members

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/DocumentStandaloneActivity.kt`
**Depends on:** Step 2.1

**Prompt for developer:**

> Implement the four seam members. `override val printHostActivity get() = this`. `override val printNetworkFileManager get() = networkFileManager` (the existing lazy member). `override fun printOfficeDocument(): Boolean = if (officeViewerHostDelegate.isInitialized()) officeViewerHost.print() else false` - only print when the internal Office viewer was actually built (internal render path, noLegal); when it was never created (market external handoff, nothing rendered) return false, matching in-app behavior. `override fun showPrintMessage(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }` - Toast is the standalone error surface (the existing `showToastError` uses LENGTH_SHORT; print messages use LENGTH_LONG to match the player's Snackbar duration).

**Verification:**

- `Grep` - `override val printHostActivity`, `override val printNetworkFileManager`, `override fun printOfficeDocument()`, `override fun showPrintMessage(` each present.
- `Grep` - `officeViewerHostDelegate.isInitialized()` present inside `printOfficeDocument`.

**Status:** `[x]` done

---

### Step 2.3 - Implement `SharePrintHost.printMediaFile`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/DocumentStandaloneActivity.kt`
**Depends on:** Step 2.1

**Prompt for developer:**

> Implement `override fun printMediaFile(mediaFile: MediaFile): Boolean { documentPrintManager.printCurrentFile(mediaFile); return true }` - mirrors `PlayerActivity.printMediaFile` (fire-and-forget async dispatch; the menu gate already guaranteed this host can print). The shared `btnShareCmd -> fileOperations.shareCurrentFile() -> sendToMenuManager.show(activity, ..)` path makes the Print receiver appear automatically once the class is a `SharePrintHost`.

**Verification:**

- `Grep` - `override fun printMediaFile(mediaFile: MediaFile): Boolean` present.
- `Grep` - `documentPrintManager.printCurrentFile(mediaFile)` present.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (`a.ps1 dq`).
- [ ] `Grep -n "Log\.d\("` in the file - zero hits.
- [ ] Overflow `R.id.menu_print` remains hidden (no isolated print item - ADR-1, criterion C3).
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

Document host now prints PDF/Office via Send-to. EPUB is not offered (not in `ShareTargetModule.printTarget().applicableTypes` - no code path). Text host (Phase 03) follows the same shape minus the Office branch.

---

## Rollback Plan

Revert phase commit(s) - only this activity changed; no data migration or layout surface touched.
