# Phase 06 - Remaining surfaces

**Strategic spec:** [`../S1456_untracked-dialogs-outside-settings-helpers.md`](../S1456_untracked-dialogs-outside-settings-helpers.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 07
**Steps done:** 4 / 4
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Bind the long tail: the launcher and screen-capture flavor source sets, the shared `ui/dialog` family and every remaining screen (24 files, 36 sites measured 2026-08-09).

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] `assert-untracked-dialogs.ps1 -List` re-read; edit what it prints.
- [ ] `CODE.LOCK` acquired immediately before each step and released right after.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/**` (4 files) | Modified | ≤ 1500 each |
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/*.kt` (2 files) | Modified | ≤ 1500 each |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/*.kt` (4 files) | Modified | ≤ 1500 each |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/ResourceProfileDialog.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/profile/DeviceProfilePickerDialogFragment.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/**` (12 remaining screen files) | Modified | ≤ 1500 each |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/db/DatabaseResetNotice.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraQuickCaptureLaunchManager.kt` | Modified | ≤ 1500 |

---

## Steps

### Step 06.1 - Bind the launcher dialogs

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`, `ui/launcher/gadget/AudioNowPlayingGadget.kt`, `ui/launcher/helpers/LauncherResourceActionManager.kt`, `ui/launcher/menu/LauncherStartMenuFragment.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Bind the five builder chains in these four files: `this` in the activity and the fragment, the host each helper and gadget already holds otherwise.
> Re-read `LauncherHomeActivity.kt` from disk immediately before editing - a sibling session was rewriting it on 2026-08-09 - and verify the `launcherEnabled` source set compiles.

**Why:**

Strategic §7 records the concurrent edits to this exact file, and §1 records that the leak reaches flavor source sets that a `standard` compile does not cover.

**Verification:**

- `assert-untracked-dialogs.ps1 -List` prints no row under `app_v2/src/launcherEnabled/`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 06.2 - Bind the screen-capture consent dialogs

**Files:** `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenCaptureConsentActivity.kt`, `ScreenVideoRecordingConsentActivity.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Bind the two builder chains in these consent activities with `.showBoundTo(this)`. The `screenCapture` source set is mounted by `noLegal`, so compile that flavor rather than `standard`.

**Why:**

Strategic §1 counts these two sites, and they live in a source set no `standard` compile reads.

**Verification:**

- `assert-untracked-dialogs.ps1 -List` prints no row under `app_v2/src/screenCapture/`.
- `.\a.ps1 fkn` exits 0.

**Status:** `[x]` done

---

### Step 06.3 - Bind the shared dialog family

**Files:** `ui/dialog/FileOperationDestinationDialog.kt`, `ScheduledOperationDialog.kt`, `ScrollableTextDialog.kt`, `TooltipDialog.kt`, `ui/common/ResourceProfileDialog.kt`, `ui/profile/DeviceProfilePickerDialogFragment.kt`
**Depends on:** Step 06.2

**Prompt for developer:**

> Bind the six builder chains in these six files. Each is called from several screens, so the owner comes from the caller: add it as a parameter of the show function and update every call site in the same step.

**Why:**

Strategic §5 takes the owner from the host that is about to die, and a dialog shared by several screens has no host of its own to bind to.

**Verification:**

- `assert-untracked-dialogs.ps1 -List` prints no row under `ui/dialog/`, `ui/common/` or `ui/profile/`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 06.4 - Bind the remaining screens

**Files:** `ui/applaunchpanel/edit/EditAppLaunchPanelActivity.kt`, `ui/cameraocr/CameraOcrTranslateActivity.kt`, `ui/companionimport/CompanionConfigImportActivity.kt`, `ui/delivery/ExtensionsManagerFragment.kt`, `ui/duplicates/DuplicatesFragment.kt`, `ui/keybinding/helpers/ResetConfirmationDialog.kt`, `ui/resourceeditor/ResourceEditorFragment.kt`, `ui/resourceimport/ResourceImportActivity.kt`, `ui/welcome/WelcomeActivity.kt`, `ui/welcome/helpers/WelcomeGesturesManager.kt`, `widget/CameraQuickCaptureLaunchManager.kt`, `core/db/DatabaseResetNotice.kt`
**Depends on:** Step 06.3

**Prompt for developer:**

> Bind the twenty-three remaining builder chains in these twelve files: `this` in an activity or fragment, the host already held otherwise, a new parameter where neither exists.
> `DatabaseResetNotice` and `CameraQuickCaptureLaunchManager` are raised outside a screen: bind them to the activity that triggers them, and if none is available leave a `TODO(phase-06)` marker and report the site rather than binding to an application context.

**Why:**

Strategic §11 requires no shipped source set to keep a builder chain ending in a bare `.show()`, and these twelve files are the remainder after the family sweeps.

**Verification:**

- `assert-untracked-dialogs.ps1 -List` prints no row for these twelve files.
- `Grep` - `TODO(phase-06)` returns zero hits, or every hit is reported in the phase Handoff Notes.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `assert-untracked-dialogs.ps1 -List` prints nothing at all.
- [ ] `pwsh -NoProfile -File scripts/quality/assert-untracked-dialogs.ps1 -UpdateBaseline` ratchets the baseline to `0`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] Dev log entry added for the phase via `.\scripts\add_to_dev_log.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

## Step Log

- 2026-08-09 - 06.1 to 06.4 PASS. 35 edits across 23 files through `temp/S1456/apply-binding.ps1` with `temp/S1456/step-06.csv`; three import blocks reordered. No `TODO(phase-06)` marker was needed - every site had a real host in scope.
- 2026-08-09 - One owner was wrong in the table: `core/db/DatabaseResetNotice.showNotice` takes `activity` while the class also holds a `context` property, so the generated call did not compile. Corrected to `activity` - the parameter is the host that is actually about to die.
- 2026-08-09 - `.\a.ps1 fk` and `.\a.ps1 fkn` both BUILD SUCCESSFUL; the noLegal compile is what covers `src/screenCapture` and the launcher source set. Baseline ratcheted 35 -> **0**: no shipped source set carries a builder chain ending in a bare `.show()` any more.
- 2026-08-09 - `post-change.ps1 -ScopeToFile` over 22 of the 23 files: `post-change: PASS`, exit 0.
- 2026-08-09 - The 23rd file closes RED and stays that way. `LauncherHomeActivity.kt` fails the scoped detekt gate on `LargeClass - 630/600`, which this ticket did not introduce: the class body gained no line here (the edit is one import plus two call swaps), the finding is absent from `config/detekt/baseline-app_v2.xml`, and the class grew past the threshold in the S1428 session that was rewriting it earlier the same day. The debt is already ticketed as **S1541 launcherhomeactivity-detekt-debt**; splitting a 630-line Activity inside a file a sibling session is actively editing is that ticket's work, not this one's. The file's change is journaled to `dev/CHANGELOG.md` by hand, since a failed closure writes no row.
- 2026-08-09 - Phase-boundary audit, Layers 1-3: no P0/P1 from this phase's edits.

---
## Handoff Notes to Next Phase

Any site that could not be bound to a real host is listed here by path and line, and Phase 07 decides whether it is a follow-up ticket or a documented exemption.

---

## Rollback Plan

Revert the phase commits - dialog ownership only, no data migration and no user-facing surface change.
