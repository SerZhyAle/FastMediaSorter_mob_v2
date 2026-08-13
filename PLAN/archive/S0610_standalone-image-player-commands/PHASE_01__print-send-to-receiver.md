# Phase 01 - Print as a Send-to receiver in the standalone image host

**Strategic spec:** [`../S0610_standalone-image-player-commands.md`](../S0610_standalone-image-player-commands.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent phase
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-22
**Completed:** 2026-06-22

---

## Objective

Make the standalone image host print through the unified «Send to..» menu (implement `SharePrintHost`) and remove the
duplicate isolated overflow print item for that host. No copy/move work here.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt` | Modified | ≤ 1000 |

> No layout edits in this phase. The shared `overflow_menu_standalone_player.xml` is not edited - the item is hidden per-host in code (other hosts still use it).

---

## Steps

### Step 01.1 - Implement `SharePrintHost` on the standalone image host

**Files:** `app_v2/.../ui/player/standalone/PhotoVideoStandaloneActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `com.sza.fastmediasorter.core.share.SharePrintHost` to the class's implemented interfaces. Implement `printMediaFile(mediaFile)`: render the displayed image and dispatch the existing bitmap print path (the current `printCurrentImage()` body via `androidx.print.PrintHelper`). Return `false` when no bitmap is available (so the «Send to..» dispatch fails cleanly), `true` when a print job is dispatched. Reuse the existing print code - do not introduce a second print mechanism.

**Verification:**

- `Grep` - `SharePrintHost` appears in the class declaration of `PhotoVideoStandaloneActivity`.
- `Grep` - `override fun printMediaFile(` present exactly once in that file.
- `Grep` - `PrintHelper` still present (bitmap print path reused, not duplicated).

**Status:** `[x]` done

**Step Log:**

- 2026-06-22 - Verification 3/3 PASS. Files: PhotoVideoStandaloneActivity.kt (SharePrintHost + printMediaFile override).

---

### Step 01.2 - Drop the isolated overflow print item for this host

**Files:** `app_v2/.../ui/player/standalone/PhotoVideoStandaloneActivity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In the overflow popup setup, set `menu_print` to `isVisible = false` for the image host, and remove the `R.id.menu_print -> { printCurrentImage(); true }` click branch. Print is now reachable only via the unified «Send to..» (the `btnShareCmd` path). Leave the `menu_print` item in the shared XML untouched (other hosts reference it).

**Verification:**

- `Grep` - `R.id.menu_print -> { printCurrentImage()` returns zero hits in `PhotoVideoStandaloneActivity.kt`.
- `Grep` - `findItem(R.id.menu_print).isVisible = false` present in that file.
- `Grep` - `<item` with `menu_print` still present in `res/menu/overflow_menu_standalone_player.xml` (shared item kept).

**Status:** `[x]` done

**Step Log:**

- 2026-06-22 - Verification 3/3 PASS. Files: PhotoVideoStandaloneActivity.kt (overflow print item hidden + click branch removed). Shared menu XML kept.

---

### Step 01.3 - Verify Print receiver surfaces in «Send to..»

**Files:** `app_v2/.../ui/player/standalone/PhotoVideoStandaloneActivity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Confirm no further wiring is needed: `SendToMenuManager.receiversFor(...)` already gates Print via `PrintShareTargetHandler.isSupportedBy(activity) = activity is SharePrintHost`. With Step 01.1 the host now satisfies the gate, so the share button's menu includes Print for images. If `printCurrentImage()` becomes unused after Step 01.2, fold its body into `printMediaFile(...)` rather than leaving a dead private method.

**Verification:**

- `Grep` - `printCurrentImage` either removed or referenced only from `printMediaFile` (no orphaned private method).
- Build compiles - run `/build`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-22 - Verification 2/2 PASS (printCurrentImage removed; printMediaFile single source). Compile check at phase gate.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for the modified file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Print is single-sourced through «Send to..» for the standalone image host. Copy/Move phases are independent of this.

---

## Rollback Plan

Revert the phase commit - no data migration or persistent surface changed; the overflow print item can be restored by re-adding the hidden item's visibility + click branch.
