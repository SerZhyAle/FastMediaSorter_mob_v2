# Phase 02 - Widget service delegation

**Strategic spec:** [`../S0526_widget-dictaphone-resource-destination.md`](../S0526_widget-dictaphone-resource-destination.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-06-19
**Completed:** 2026-06-19

---

## Objective

Make the Quick Audio Recorder widget service a Hilt entry point that delegates the finished recording to `MicRecordingSaver` (selected destination, network upload with local fallback), staying foreground until the suspend save completes, and notifying the user of a fallback via a system notification.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/QuickAudioRecorderService.kt` | Modified | ≤ 360 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |

> `QuickAudioRecorderService` is in `src/main` and present only where `SUPPORT_MIC_RECORDING` is enabled (existing capability gating); no new flavor source sets are introduced.

---

## Steps

### Step 02.1 - Add widget result strings

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a key `quick_recorder_saved_to` (EN/RU/UK, one lockstep `set-android-string.ps1 -Action add` call) reporting the actual save location, e.g. EN "Recording saved to %1$s". Cyrillic literals must be authored via a UTF-8 `.ps1` or run from pwsh directly (never passed as bash→pwsh args). Text must follow `docs/COMMUNICATION_POLICY.md` §2 (result message) and §6 tone checklist. The existing `save_fallback_resource_unavailable` string (S0522) is reused for the fallback notice.

**Verification:**

- `Grep` - `name="quick_recorder_saved_to"` present in all three `strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "quick_recorder_saved_to"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 02.2 - Make the service a Hilt entry point and inject dependencies

**Files:** `widget/QuickAudioRecorderService.kt`
**Depends on:** Phase 01 Step 01.1

**Prompt for developer:**

> Annotate `QuickAudioRecorderService` with `@AndroidEntryPoint` and add `@Inject` fields: `MicRecordingSaver`, `SaveFallbackNotifier`, and the network upload strategies `LocalToFtpStrategy`/`LocalToSmbStrategy`/`LocalToSftpStrategy` plus `CloudOperationStrategy` (same set BrowseActivity uses to build its mic upload lambda). No manifest change is required (the service is already declared); confirm Hilt generates without a module since all injected types are constructor-injectable singletons.

**Verification:**

- `Grep` - `@AndroidEntryPoint` present in `QuickAudioRecorderService.kt`.
- `Grep` - `MicRecordingSaver` and `SaveFallbackNotifier` injected.
- `Grep -n "Log\.d\("` - zero hits in the file.

**Status:** `[x]` done

---

### Step 02.3 - Delegate save on stop with foreground-safe completion

**Files:** `widget/QuickAudioRecorderService.kt`
**Depends on:** Step 02.1, Step 02.2

**Prompt for developer:**

> Replace the direct private-dir write: keep recording to a temp file, but on stop hand the finished file to `micRecordingSaver.save(tempFile, name, browsedResource = null, upload = { f, n, res -> .. })` where the upload lambda routes by `res.type` through the injected strategies (mirror BrowseActivity's mic `onUploadFile`). Run the save on a service `CoroutineScope` and keep the service in the foreground until it completes (do not `stopSelf()` before the suspend save returns); then post the result: a Toast with `quick_recorder_saved_to` (actual location), and when `result.fallbackReason != null` call `saveFallbackNotifier.notify(reason, folderLabel, resourceName, background = true)`. Finally stop foreground and `stopSelf()`. On failure keep the existing error toast. Preserve audio-focus-loss → stop-and-save behaviour.

**Verification:**

- `Grep` - `micRecordingSaver.save(` present in `QuickAudioRecorderService.kt`.
- `Grep` - `background = true` present (background notification).
- `Grep` - `getExternalFilesDir` still used only for the temp recording path, not as the final destination (no direct final write).
- `Grep -n "Log\.d\("` - zero hits in the file.

**Status:** `[x]` done

---

### Step 02.4 - Compile

**Files:** (verification only)
**Depends on:** Step 02.3

**Prompt for developer:**

> Build `standard` to confirm the Hilt-enabled service and delegation compile.

**Verification:**

- `.\a.ps1 fc` - code + resources compile clean.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (`.\a.ps1 fc`).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Widget recordings now route through the shared mic saver with network upload + local fallback + background notification. Final phase records the capability and regenerates the catalog.

---

## Rollback Plan

Revert phase commit(s) and remove the added string key. The recorder's temp-write path is unchanged; only the final routing reverts to the prior private-dir behaviour.
