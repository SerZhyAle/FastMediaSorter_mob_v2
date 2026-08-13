# Phase 01 - Shared mic-recording saver

**Strategic spec:** [`../S0526_widget-dictaphone-resource-destination.md`](../S0526_widget-dictaphone-resource-destination.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-06-19
**Completed:** 2026-06-19

---

## Objective

Extract the mic-recording destination-resolution + write/upload + S0522 fallback into a single Activity-free `MicRecordingSaver` (mirroring `CameraCaptureSaver`), and route the Browse mic flow through it with unchanged behaviour. No widget change yet.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.
- [ ] S0522 (shared fallback policy/notifier, reachability) present in code.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/capture/MicRecordingSaver.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseMicRecordingManager.kt` | Modified | ≤ 380 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` | Modified | ≤ 900 |

---

## Steps

### Step 01.1 - Add MicRecordingSaver

**Files:** `data/capture/MicRecordingSaver.kt` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Create `@Singleton class MicRecordingSaver @Inject constructor(..)` mirroring `CameraCaptureSaver`. Inject `SettingsRepository`, `ResourceRepository`, `LocalDestinationClassifier`, `LocalDestinationWriter`, `NetworkStateMonitor`, `StatsSink`. Expose a suspend `save(tempFile: File, name: String, browsedResource: MediaResource?, upload: suspend (File, String, MediaResource) -> Boolean): Result` where `Result` carries `savedPath: String?` and `fallbackReason: SaveFallbackReason?`. Logic, lifted verbatim from `BrowseMicRecordingManager`: resolve the target via the configured `micRecordingDestinationResourceId` (or `browsedResource`) using `CaptureDestinationPolicy.isUsableTarget`; for a LOCAL target write through `LocalDestinationClassifier`/`LocalDestinationWriter`; for a network target, when `networkStateMonitor.canReach(type)` is false OR `upload(..)` returns false, write to the local mic fallback (`CaptureDestinationPolicy.resolveMicDestination(null)`) and set `fallbackReason = ResourceUnavailable`; when no usable target, write to the public mic fallback (silent, no reason). Record `StatsEvent.Capture(CaptureKind.VOICE)` on success. Timber only.

**Verification:**

- `Glob` - `data/capture/MicRecordingSaver.kt` exists.
- `Grep` - `class MicRecordingSaver` matches once with `@Singleton`.
- `Grep` - `fun save(` and `canReach(` and `SaveFallbackReason` present.
- `Grep -n "Log\.d\("` - zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - PASS 4/4. Created MicRecordingSaver (resolve + write/upload + S0522 fallback + stats).

---

### Step 01.2 - Route Browse mic flow through MicRecordingSaver

**Files:** `ui/browse/managers/BrowseMicRecordingManager.kt`, `ui/browse/BrowseActivity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `micRecordingSaver: MicRecordingSaver` to `BrowseMicRecordingManager`'s constructor and inject it in `BrowseActivity` (it is `@Singleton @Inject`, so add an `@Inject` field and pass it where the manager is constructed). Replace the manager's `save(..)` body so it delegates to `micRecordingSaver.save(tempFile, name, browsedResource, onUploadFile)`; keep the recorder lifecycle, filename dialog, snackbar, and the foreground `saveFallbackNotifier.notify(..)` call (driven by the returned `fallbackReason`, `background = false`). Remove the now-duplicated `resolveMicSaveResource`, `writeToDevice`, and inline fallback from the manager. Behaviour must stay identical to S0522.

**Verification:**

- `Grep` - `micRecordingSaver` referenced in both `BrowseMicRecordingManager.kt` and `BrowseActivity.kt`.
- `Grep` - `private suspend fun writeToDevice(` returns zero hits in `BrowseMicRecordingManager.kt` (moved into the saver).
- `Grep` - `saveFallbackNotifier` `.notify(` still present in `BrowseMicRecordingManager.kt` (foreground notice retained).
- `Grep -n "Log\.d\("` - zero hits in touched files.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - PASS. BrowseMicRecordingManager delegates to saver; removed writeToDevice/resolveMicSaveResource + 5 dead deps; S0522 tag relocated; BrowseActivity injects+passes saver.

---

### Step 01.3 - Compile the Browse mic flow

**Files:** (verification only)
**Depends on:** Step 01.2

**Prompt for developer:**

> Build the `standard` flavor to confirm the extraction compiles and the manager delegates cleanly.

**Verification:**

- `.\a.ps1 fc` - code + resources compile clean.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - PASS. a.ps1 fc BUILD SUCCESSFUL (standard).

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fc`).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new class added).

---

## Handoff Notes to Next Phase

`MicRecordingSaver.save(tempFile, name, browsedResource?, upload)` is the single mic-save backend. The widget (Phase 02) passes `browsedResource = null` and builds its own `upload` lambda from injected network strategies; it reads `fallbackReason` to post a background notification.

---

## Rollback Plan

Revert phase commit(s). Until the widget consumes it, the saver is only used by Browse; reverting restores the prior in-manager logic.
