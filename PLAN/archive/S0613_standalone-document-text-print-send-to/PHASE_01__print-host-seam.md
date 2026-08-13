# Phase 01 - Print host seam

**Strategic spec:** [`../S0613_standalone-document-text-print-send-to.md`](../S0613_standalone-document-text-print-send-to.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 5 / 5
**Started:** 2026-06-22
**Completed:** 2026-06-22

---

## Objective

Decouple the document/text print mechanism from `PlayerActivity`: introduce a `DocumentPrintHost` seam, make `DocumentPrintManager` and the share-fallback depend on it, and have `PlayerActivity` adopt it. No behavior change to in-app printing.

---

## Prerequisites

- [ ] Strategic §6 research items Resolved (all are).
- [ ] Working tree on the active branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DocumentPrintHost.kt` | New | ≤ 35 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PrintShareFallbackManager.kt` | New (rename of `PlayerPrintFallbackManager.kt`) | ≤ 55 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerPrintFallbackManager.kt` | Deleted (renamed) | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DocumentPrintManager.kt` | Modified | ≤ 360 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified (1191 LOC - backup to `temp/` first, >500 LOC) | ≤ 1210 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | ≤ +1 |

> Backup / split thresholds: `PlayerActivity.kt` (1191) is >500 LOC - take a timestamped backup in `temp/` before editing. No file crosses 1500.

---

## Steps

### Step 1.1 - Create the `DocumentPrintHost` seam interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DocumentPrintHost.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create interface `DocumentPrintHost` in package `com.sza.fastmediasorter.ui.player.helpers`. It is the host contract `DocumentPrintManager` needs, replacing its hard `PlayerActivity` dependency. Expose exactly: `val printHostActivity: androidx.appcompat.app.AppCompatActivity` (source of system services, UI-thread dispatch, `lifecycleScope`, and child-view `Context`); `val printNetworkFileManager: NetworkFileManager` (materialises any resource to a local `File`); `fun printOfficeDocument(): Boolean` (true when an internal Office print path handled the job; hosts without an Office viewer return false); `fun showPrintMessage(message: String)` (surface a print status/error - Snackbar in the player, Toast in standalone hosts). KDoc one line per member, explaining WHY each exists, not restating the type.

**Verification:**

- `Glob` - `DocumentPrintHost.kt` exists.
- `Grep` - `interface DocumentPrintHost` matches once.
- `Grep` - `val printHostActivity`, `val printNetworkFileManager`, `fun printOfficeDocument(): Boolean`, `fun showPrintMessage(message: String)` each present.

**Status:** `[x]` done

---

### Step 1.2 - Rename `PlayerPrintFallbackManager` to `PrintShareFallbackManager`, take an `Activity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PrintShareFallbackManager.kt` (new), `.../PlayerPrintFallbackManager.kt` (delete)
**Depends on:** - start of phase

**Prompt for developer:**

> Rename class `PlayerPrintFallbackManager` to `PrintShareFallbackManager` (file too) - it is no longer player-only once the print mechanism is host-agnostic. Change the constructor parameter type from `PlayerActivity` to `android.app.Activity` (the body only uses `FileProvider.getUriForFile`, `packageName`, `getString`, `startActivity` - all `Context`/`Activity`). Update the internal `Timber.e` tag string to the new class name. Drop the `PlayerActivity` import.

**Verification:**

- `Glob` - `PrintShareFallbackManager.kt` exists; `PlayerPrintFallbackManager.kt` does not.
- `Grep` - `class PrintShareFallbackManager(` matches once.
- `Grep` - `private val activity: Activity` present; `PlayerActivity` absent in the file.

**Status:** `[x]` done

---

### Step 1.3 - Refactor `DocumentPrintManager` onto the seam

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DocumentPrintManager.kt`
**Depends on:** Step 1.1, Step 1.2

**Prompt for developer:**

> Change the constructor to `class DocumentPrintManager(private val host: DocumentPrintHost, private val mediaCapabilities: MediaCapabilities)`. Replace every `activity.*` usage: contexts passed to `PrintHelper(..)`, `WebView(..)`, and `PrintShareFallbackManager(..)` become `host.printHostActivity`; `activity.lifecycleScope` -> `host.printHostActivity.lifecycleScope`; `activity.isFinishing/isDestroyed`, `activity.getString(..)`, `activity.runOnUiThread { }`, `activity.getSystemService(..)` -> `host.printHostActivity.*`; `activity.networkFileManager` -> `host.printNetworkFileManager`; `activity.officeDocumentViewerManager.print()` -> `host.printOfficeDocument()`. Replace the private `showSnackbar(message)` (which used `activity.activityBinding.root`) with `private fun showMessage(message: String) = host.showPrintMessage(message)` and update all call sites; remove the now-unused `Snackbar` and `PlayerActivity` imports. `printFallbackManager` becomes `PrintShareFallbackManager(host.printHostActivity)`. Keep the dispatch logic, type `when`, size guard, and async structure byte-for-byte identical - only the host accessors change.

**Verification:**

- `Grep` - `class DocumentPrintManager(` followed by `host: DocumentPrintHost` (declaration line).
- `Grep` - `import com.sza.fastmediasorter.ui.player.PlayerActivity` returns zero hits in this file.
- `Grep` - `private val activity` returns zero hits (the `PlayerActivity` field is gone).
- `Grep` - `host.printHostActivity` present (host accessors adopted).
- `Grep` - `PrintShareFallbackManager(host.printHostActivity)` present.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

---

### Step 1.4 - `PlayerActivity` implements `DocumentPrintHost`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** Step 1.1

**Prompt for developer:**

> Backup `PlayerActivity.kt` to `temp/` first (>500 LOC). Add `com.sza.fastmediasorter.ui.player.helpers.DocumentPrintHost` to the class's implemented interfaces (next to the existing `SharePrintHost`). Implement: `override val printHostActivity: AppCompatActivity get() = this`; `override val printNetworkFileManager get() = networkFileManager` (existing member, line ~169); `override fun printOfficeDocument(): Boolean = officeDocumentViewerManager.print()` (existing member, line ~200); `override fun showPrintMessage(message: String) { Snackbar.make(activityBinding.root, message, Snackbar.LENGTH_LONG).show() }` - this preserves the exact Snackbar surface the old `DocumentPrintManager.showSnackbar` had. Do not change the existing `printMediaFile` override.

**Verification:**

- `Grep` - `DocumentPrintHost` present in `PlayerActivity.kt` (class declaration).
- `Grep` - `override val printHostActivity`, `override fun printOfficeDocument()`, `override fun showPrintMessage(` each present.
- `Grep` - `Snackbar.make(activityBinding.root` present (message surface preserved).

**Status:** `[x]` done

---

### Step 1.5 - Update the construction site in `PlayerManagerInitializer`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`
**Depends on:** Step 1.3, Step 1.4

**Prompt for developer:**

> At the `DocumentPrintManager(activity = activity, mediaCapabilities = activity.mediaCapabilities)` construction (line ~279), change the first argument label to `host = activity` (the `activity` is a `PlayerActivity`, now a `DocumentPrintHost`). Leave `mediaCapabilities` unchanged.

**Verification:**

- `Grep` - `DocumentPrintManager(host = activity` present.
- `Grep` - `DocumentPrintManager(activity = activity` returns zero hits.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (`a.ps1 dq`).
- [ ] `Grep` - no `private val activity` field remains in `DocumentPrintManager.kt` (host seam fully adopted).
- [ ] In-app print path is byte-equivalent in behavior (same dispatch, same Snackbar surface via `PlayerActivity.showPrintMessage`).
- [ ] Dev log entry added (batched - one logical change for the seam).

---

## Handoff Notes to Next Phase

`DocumentPrintHost` + `DocumentPrintManager(host, mediaCapabilities)` are now the reusable seam. Phases 02/03 instantiate `DocumentPrintManager(host = this, mediaCapabilities = <injected>)` and implement the four seam members + `SharePrintHost.printMediaFile`.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed; the in-app print path is the only runtime behavior touched and is preserved.
