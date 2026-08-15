# Phase 02 - Adopt the branded icon at every call site

**Strategic spec:** [`../S1399_notification-small-icon-unified-branding.md`](../S1399_notification-small-icon-unified-branding.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Point all thirteen small-icon call sites at the single owner, in dependency-free groups, so no notification
family is left behind and the flavor-gated ones stay compilable where their source set is absent.

---

## The complete call-site inventory

Established by the S1399 research pass with file:line citations. Thirteen sites, eleven classes, four
drawables. Anything not on this list is not a small-icon call site.

**Group A - the reported defect (music note off-label), `src/main`:**

- `worker/ScheduledOperationsWorker.kt:122` and `:172` - progress, plus the All-Files-access advisory.
- `worker/NetworkFilesSyncWorker.kt:168` - carries the admission `// Use generic or sync icon`.
- `worker/DuplicateDetectionWorker.kt:124`.

**Group B - audio playback (music note, on-label but in scope per ADR-3), `src/main`:**

- `ui/player/MediaNotificationManager.kt:69` - the Media3 `MediaNotification.Provider`.
- `ui/player/AudioPlaybackService.kt:330` - the cold-start placeholder before Media3 takes over.

**Group C - transfers and downloads, `src/main`:**

- `worker/LinkDownloadWorker.kt:172`, `:218`, `:413`.
- `worker/DeliverableDownloadWorker.kt:126`, `:144`, `:155`.
- `worker/BrowseFileTransferWorker.kt:493`, `:531`.
- `core/save/SaveFallbackNotifier.kt:60`.

**Group D - flavor-gated source sets:**

- `src/screenCapture/.../ScreenCaptureService.kt:307`, `OverlayHostService.kt:146`, `ScreenVideoRecordingService.kt:425` - compiled only in standard and noLegal.
- `src/main/.../widget/QuickAudioRecorderService.kt:352` - uses `ic_widget_quick_audio_recorder_idle`, a widget glyph, not a notification one.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/ScheduledOperationsWorker.kt` | Modified | unchanged |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/NetworkFilesSyncWorker.kt` | Modified | unchanged |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/DuplicateDetectionWorker.kt` | Modified | unchanged |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/MediaNotificationManager.kt` | Modified | unchanged |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt` | Modified | unchanged |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadWorker.kt` | Modified | unchanged |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/DeliverableDownloadWorker.kt` | Modified | unchanged |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/BrowseFileTransferWorker.kt` | Modified | unchanged |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/save/SaveFallbackNotifier.kt` | Modified | unchanged |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/QuickAudioRecorderService.kt` | Modified | unchanged |
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenCaptureService.kt` | Modified | unchanged |
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/OverlayHostService.kt` | Modified | unchanged |
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenVideoRecordingService.kt` | Modified | unchanged |

---

## Steps

### Step 02.1 - Fix the reported defect first (Group A)

**Files:** the three Group A workers
**Depends on:** - start of phase

**Prompt for developer:**

> Point the four Group A call sites at the owner from phase 01, and delete the `// Use generic or sync icon` comment in `NetworkFilesSyncWorker` - it documented the absence of a default, which no longer exists. Change nothing else in these files.

**Why:**

Strategic §1 names these three workers as the reported symptom: a music note shown while the app moves files on a schedule, chosen only because no default existed.

**Verification:**

- `Grep` - no `ic_notification_audio` reference remains in the three Group A files.
- `Grep` - the stale `Use generic or sync icon` comment is gone.
- `.\a.ps1 fk` passes.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 3\3 PASS. Two of the four Group A call sites were already repointed before
  this run - `ScheduledOperationsWorker.kt:127` and `:173` already read `NotificationIcons.STATUS_BAR`,
  which is what the DRIFT verdict on this ticket was reporting. This run repointed the remaining two,
  `NetworkFilesSyncWorker.kt:168` and `DuplicateDetectionWorker.kt:124`, and deleted the
  `// Use generic or sync icon` comment with the first of them.
- Grep over `worker/` returns zero hits for `ic_notification_audio` and zero for the stale comment.
  `.\a.ps1 fk` exit 0.
- The `com.sza.fastmediasorter.R` import stays in both edited files on purpose: each still resolves
  string resources through it, so this is not an orphaned import under Rule 20.

---

### Step 02.2 - Audio playback and transfers (Groups B and C)

**Files:** the two Group B classes and the four Group C classes
**Depends on:** Step 02.1

**Prompt for developer:**

> Point the nine remaining `src/main` call sites at the same owner. In `MediaNotificationManager` the icon is supplied to the Media3 notification provider rather than to a `NotificationCompat.Builder` - set it through the provider's own small-icon setter, and do not restructure the provider.

**Why:**

Strategic ADR-3 extends the change to every family including playback, because the owner's "повсеместно" is what stops the next author from re-asking "is this one about music" - the very choice that produced the defect.

**Verification:**

- `Grep` - no `ic_notification_audio` or `ic_notification_cloud_download` reference remains in any of the nine.
- `.\a.ps1 fk` passes.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 2\2 PASS. Grep over `app_v2/src/main/java` returns zero hits for
  `ic_notification_audio` and zero for `ic_notification_cloud_download`; `.\a.ps1 fk` exit 0.
- **Count correction, not a scope change.** The prompt says "the nine remaining `src/main` call sites";
  the actual count is eleven, across the six classes the step names. The inventory at the top of this
  phase lists all eleven correctly - `LinkDownloadWorker` and `DeliverableDownloadWorker` carry three
  each, `BrowseFileTransferWorker` two, the other three classes one each - so "nine" is an arithmetic
  slip in the prompt sentence, not a different set of files. All eleven were repointed.
- `MediaNotificationManager` was set through the Media3 provider's own `setSmallIcon`, as the prompt
  requires; the provider itself was not restructured.
- `com.sza.fastmediasorter.R` stays imported in all six files - each still resolves string resources,
  and `BrowseFileTransferWorker` additionally uses `R.drawable.ic_delete` for a notification action.
- Backups of the two files over 500 LOC taken before editing, per Rule 5:
  `temp/S1399/AudioPlaybackService.kt.20260808-1627.bak`,
  `temp/S1399/BrowseFileTransferWorker.kt.20260808-1627.bak`.

---

### Step 02.3 - The flavor-gated services (Group D)

**Files:** the three `src/screenCapture` services and `QuickAudioRecorderService`
**Depends on:** Step 02.2

**Prompt for developer:**

> Point the four Group D call sites at the same owner. The owner lives in `src/main`, so the `src/screenCapture` services can import it; confirm that by compiling a flavor that mounts the set and one that does not.

**Why:**

Strategic §3.2 records that `src/screenCapture` compiles only in standard and noLegal, so this is the one group whose change cannot be proven by the default build alone.

**Verification:**

- `Grep` - no `ic_notification_screen_capture` or `ic_widget_quick_audio_recorder_idle` reference remains on a notification small-icon call.
- `.\a.ps1 fk` passes (standard - mounts the set).
- `.\a.ps1 fkn` passes (noLegal - also mounts it).

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 3\3 PASS. `.\a.ps1 fk` exit 0 (standard) and `.\a.ps1 fkn` exit 0 (noLegal),
  which is what proves a `src/screenCapture` class can reach an owner that lives in `src/main` - the one
  claim the default build alone could not settle.
- Grep over `app_v2/src` now returns **zero** `setSmallIcon(R.drawable` occurrences in any source set, so
  no small-icon call site anywhere names a drawable literal.
- `ic_notification_screen_capture` still has two references and they are correct to keep: the
  quick-settings tile in `standardEdgeTile` (`AndroidManifest.xml:15`, `ScreenshotGestureTileService.kt:27`).
  A tile icon is not a notification small icon, so it is out of scope, exactly as the 02.4 sweep recorded.
- Bare `ic_widget_quick_audio_recorder_idle` now has zero references - the three remaining grep hits are
  all `_idle_accent`, a different drawable. This confirms the 02.4 sweep's correction against the original
  plan text, and 02.4 owns the deletion.
- Backup of the one file over 500 LOC taken before editing, per Rule 5:
  `temp/S1399/ScreenVideoRecordingService.kt.20260808-1630.bak`.

---

### Step 02.4 - Retire whatever the change orphaned

**Files:** `app_v2/src/main/res/drawable/`, `app_v2/src/screenCapture/res/drawable/`
**Depends on:** Step 02.3

**Prompt for developer:**

> Check each of the four previously-used drawables for remaining references. Delete the ones with none; keep any still used by a non-notification surface and say where. `ic_widget_quick_audio_recorder_idle` is a widget glyph and is expected to survive.

**Reference sweep run 2026-08-08, before the step - the plan's expectation is inverted on two of the four.**
Evidence is a whole-repo grep, not the `.kt` inventory above, which is why the plan missed it:

- `ic_notification_audio` - references only at the Group A/B call sites. **Orphaned by this phase; delete.**
- `ic_notification_cloud_download` - references only at the Group C call sites. **Orphaned; delete.**
- `ic_notification_screen_capture` - **survives, and not for the reason the plan assumed.** Beyond the
  three Group D services it is the quick-settings tile icon in two places the inventory never listed:
  `app_v2/src/standardEdgeTile/AndroidManifest.xml:15` (`android:icon`) and
  `ScreenshotGestureTileService.kt:27` (`Icon.createWithResource`). A tile icon is not a notification
  small icon, so it is out of this ticket's scope and must not be repointed. Keep the drawable.
- `ic_widget_quick_audio_recorder_idle` - **the plan is wrong: it does NOT survive.** Every widget
  surface uses the *accent* variant instead - `widget_quick_audio_recorder.xml:17`,
  `widget_quick_audio_recorder_info.xml:13` and `QuickAudioRecorderWidgetProvider.kt:50` all name
  `ic_widget_quick_audio_recorder_idle_accent`. The plain `_idle` drawable's single remaining reference
  is the notification small icon at `QuickAudioRecorderService.kt:352` that step 02.3 repoints, so
  CLAUDE.md Rule 20 requires deleting it here. `_idle_accent` is untouched.
- Also repoint two stale prose references left behind by the deletions, since a comment naming a deleted
  file is the dead weight Rule 20 is about: the fix hint in `scripts/quality/assert-fgs-notifications.ps1:78`
  cites `ic_notification_audio` / `ic_notification_cloud_download` as the examples to copy, and the header
  comment of the new `ic_notification_app_logo.xml` names `ic_notification_cloud_download.xml` as a
  sibling fork. Both should name a file that still exists.

**Why:**

CLAUDE.md Rule 20 requires orphaned resources to go in the same change that orphans them, and a drawable kept "just in case" is exactly how the wrong icon gets picked again.

**Verification:**

- `Grep` - every drawable retained still has at least one live reference, and each deleted one has zero.
- `.\a.ps1 fc` passes - resources still link.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 2\2 PASS. `.\a.ps1 fc` exit 0, so resources still link with the three
  drawables gone. The reference counts, measured over `app_v2/src` after the deletions: deleted
  `ic_notification_audio` 0, `ic_notification_cloud_download` 0, bare
  `ic_widget_quick_audio_recorder_idle` 0; retained `ic_notification_app_logo` 1 (its owner),
  `ic_notification_screen_capture` 3, `ic_widget_quick_audio_recorder_idle_accent` 3.
- Deleted: `main/res/drawable/ic_notification_audio.xml`,
  `main/res/drawable/ic_notification_cloud_download.xml`,
  `main/res/drawable/ic_widget_quick_audio_recorder_idle.xml`.
- Kept: `screenCapture/res/drawable/ic_notification_screen_capture.xml`. Note its path - it lives in the
  flavor source set, not `main`, which the sweep above did not say. Its two live callers are the
  quick-settings tile, a surface this ticket does not touch.
- Both stale prose references repointed. `assert-fgs-notifications.ps1` Fix A now names
  `NotificationIcons.STATUS_BAR` rather than two files that no longer exist, which also makes the hint
  point at the seam instead of at a drawable to copy. The `ic_notification_app_logo.xml` header now names
  only `ic_notification_screen_capture.xml` as the surviving fork precedent.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` and `.\a.ps1 fkn` both exit 0.
- [x] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

**Compile evidence, 2026-08-08.** Both flavor checks were re-run *after* step 02.4 deleted the three
drawables, not only after 02.3 - a resource deletion is exactly the change a pre-deletion green build
cannot vouch for. `.\a.ps1 fc` exit 0 (standard: Kotlin plus `processStandardDebugResources`, so the
resource table is proven to still link) and `.\a.ps1 fkn` exit 0 (noLegal).

**Phase-boundary audit, 2026-08-08.** Layers 2-4 have no surface here: every edit is a one-argument
substitution inside an already-existing notification builder, so no lifecycle, coroutine, listener or
Room behaviour is touched. Layer 1 findings: none at P0/P1.

- Layer 1: the twelve call sites now read a constant from `core/notification`, which is a dependency
  from `ui`/`worker`/`screencapture` toward `core` - the direction the layering already runs. No class
  gained a responsibility and no file changed shape.
- Rule 20 (dead weight): the three drawables this phase orphaned were deleted inside the same phase, and
  the two prose references that named them were repointed. `ic_notification_screen_capture` was kept with
  its reason recorded in step 02.4.
- P3, not fixed here, and not introduced here: `NetworkFilesSyncWorker.kt:102` is a 131-char `Timber.i`
  line that the scoped detekt preflight surfaces because the file is in the changed set. It is baselined
  pre-existing debt on a line this phase never edited, the detekt gate itself is green, and rewriting it
  would be the surrounding refactor `/spec-dev` forbids inside a step.
- UI gate (S1338): `Files Touched` reaches `ui/player/`, so the gate applies. Its placement condition is
  satisfied - strategic §3.3 carries an explicit UI placement contract sourced from the owner's verbatim
  §0 ("иконка в трее .. всегда будет символ оригинальная иконка нашей программы", "и используем
  повсеместно"), and nothing moves or appears; only the icon resource inside existing notifications
  changes. Screenshot deferred (no device attached this session); this phase's own Done Criteria do not
  demand one, and §3.3 assigns the on-device check to acceptance, which phase 03 hands to
  `BlockNeedUserTest`.

---

## Handoff Notes to Next Phase

Every notification now shows the branded icon. Nothing yet prevents a fourteenth call site from hardcoding
its own, which is phase 03's whole job.

---

## Rollback Plan

Revert the phase commit; the drawables deleted in 02.4 come back with it. No stored state is involved.
