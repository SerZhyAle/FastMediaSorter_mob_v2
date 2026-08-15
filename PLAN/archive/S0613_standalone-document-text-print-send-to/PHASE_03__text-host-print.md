# Phase 03 - Text host print

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

Make `TextStandaloneActivity` a print host: declare `SharePrintHost` + `DocumentPrintHost` so the Print receiver appears in its «Отправить в..» menu for text, routing through the shared `DocumentPrintManager` (TEXT branch = ephemeral WebView print adapter).

---

## Prerequisites

- [ ] Phase 01 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/TextStandaloneActivity.kt` | Modified (522 LOC - backup to `temp/`, >500) | ≤ 565 |

> No layout/XML edit. No new strings (reused).

---

## Steps

### Step 3.1 - Declare print-host interfaces, inject capabilities, add the manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/TextStandaloneActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Backup the file to `temp/` first (>500 LOC). Add `com.sza.fastmediasorter.core.share.SharePrintHost` and `com.sza.fastmediasorter.ui.player.helpers.DocumentPrintHost` to the class's implemented interfaces. Add `@Inject lateinit var mediaCapabilities: com.sza.fastmediasorter.core.capability.MediaCapabilities`. Add a lazy `private val documentPrintManager by lazy { com.sza.fastmediasorter.ui.player.helpers.DocumentPrintManager(host = this, mediaCapabilities = mediaCapabilities) }`. Leave the overflow menu unchanged - `R.id.menu_print` stays hidden (ADR-1).

**Verification:**

- `Grep` - `SharePrintHost` and `DocumentPrintHost` both present in the class declaration region.
- `Grep` - `@Inject lateinit var mediaCapabilities` present.
- `Grep` - `DocumentPrintManager(host = this` present.
- `Grep` - `R.id.menu_print` still present in the hidden-items `listOf(..)`.

**Status:** `[x]` done

---

### Step 3.2 - Implement the `DocumentPrintHost` seam members

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/TextStandaloneActivity.kt`
**Depends on:** Step 3.1

**Prompt for developer:**

> Implement the four seam members. `override val printHostActivity get() = this`. `override val printNetworkFileManager get() = networkFileManager` (existing lazy member). `override fun printOfficeDocument(): Boolean = false` - this host only ever renders TEXT; Office never reaches it, so there is no internal Office print path. `override fun showPrintMessage(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }`.

**Verification:**

- `Grep` - `override val printHostActivity`, `override val printNetworkFileManager`, `override fun printOfficeDocument(): Boolean = false`, `override fun showPrintMessage(` each present.

**Status:** `[x]` done

---

### Step 3.3 - Implement `SharePrintHost.printMediaFile`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/TextStandaloneActivity.kt`
**Depends on:** Step 3.1

**Prompt for developer:**

> Implement `override fun printMediaFile(mediaFile: MediaFile): Boolean { documentPrintManager.printCurrentFile(mediaFile); return true }`. Add the `com.sza.fastmediasorter.domain.model.MediaFile` import (the file currently imports `MediaResource`/`MediaType` but not `MediaFile`). The existing `btnShareCmd -> shareCurrentFile() -> sendToMenuManager.show(activity, ..)` path surfaces the Print receiver once the class is a `SharePrintHost`.

**Verification:**

- `Grep` - `override fun printMediaFile(mediaFile: MediaFile): Boolean` present.
- `Grep` - `documentPrintManager.printCurrentFile(mediaFile)` present.
- `Grep` - `import com.sza.fastmediasorter.domain.model.MediaFile` present.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (`a.ps1 dq`).
- [ ] `Grep -n "Log\.d\("` in the file - zero hits.
- [ ] Overflow `R.id.menu_print` remains hidden (criterion C3).
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

Both standalone document and text hosts now print via Send-to. Phase 04 regenerates the catalog, records the capability, and runs closure gates.

---

## Rollback Plan

Revert phase commit(s) - only this activity changed.
