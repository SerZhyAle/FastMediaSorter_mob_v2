# Phase 01 — Image-toolbar VR button gate removal + user-initiated stereo detection

**Strategic spec:** [`../S0238_image-player-vr-entry-button.md`](../S0238_image-player-vr-entry-button.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** —
**Blocks:** —
**Steps done:** 4 / 4
**Started:** 2026-05-17
**Completed:** 2026-05-17

---

## Objective

Make the existing `btn3dVrCmd` VR-entry icon visible for image/gif media types in the player command panel, and run a user-initiated stereo auto-detection at click time so that filename-less stereo photos still reach immersive with the right layer.

The button itself, its safe-view binding, click listener, callback, and planner entry already exist (verified by `Grep` over `btn3dVrCmd` and `PlayerCommand.VR_3D`). Only two `MediaType.VIDEO` gates and one missing detection call separate the user from the feature.

---

## Prerequisites

- [ ] Catalog up-to-date: `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` already ran on the current tree (touched files mtimes match).
- [ ] Strategic spec §4 read.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt` | Modified | +35 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/StereoDetectorUserInitiatedTest.kt` | New | +90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` | Modified | +5 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` | Modified | +3 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt` | Modified | +25 |

> No layout / drawable / strings / Hilt changes needed — all already wired.

---

## Steps

### Step 01.1 — Add `userInitiated` overload to `StereoDetector` `[x] done`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt`
**Depends on:** —

**Prompt for developer:**

> Extend `StereoDetector.detectForImage` with a new optional parameter `userInitiated: Boolean = false`. Preserve the existing 3-argument call sites exactly — they must keep `userInitiated = false` behaviour byte-for-byte.
>
> Final signature:
>
> ```kotlin
> fun detectForImage(
>     path: String,
>     width: Int? = null,
>     height: Int? = null,
>     userInitiated: Boolean = false,
> ): StereoMode
> ```
>
> Implementation outline:
>
> 1. Run the existing cascade unchanged (filename → PhotoSphere XMP → `detectFromDimensions`). Capture the result in a local `passive: StereoMode`.
> 2. If `userInitiated == false` → return `passive` as today. **The current public behaviour does not change.**
> 3. If `userInitiated == true` AND `passive != StereoMode.UNKNOWN` AND `passive != StereoMode.MONO` → return `passive` (a strong signal from filename/XMP/AR wins).
> 4. Else (`userInitiated == true` and we are about to surface `UNKNOWN`/`MONO`) — apply the aggressive table:
>
>    | Condition (with `width`, `height` known) | Result |
>    |------------------------------------------|--------|
>    | `aspect ≥ 1.6f` and `width ≥ 1024` | `StereoMode.SBS_FULL` |
>    | `aspect ≤ 0.7f` and `height ≥ 1024` | `StereoMode.OU` |
>    | `aspect in 0.9f..1.1f` and `width ≥ 1024` | `StereoMode.OU` |
>    | anything else (including null w/h) | `StereoMode.MONO` |
>
>    where `aspect = width.toFloat() / height.toFloat()`.
>
> Place the aggressive branch in a `private fun aggressiveDimensionGuess(width: Int?, height: Int?): StereoMode` helper at the bottom of `StereoDetector` (between `detectFromDimensions` and `detectFromAspectRatio`). Reuse the same `isNear` helper if helpful, but the new rule is a straight `when`/`if` ladder — keep it readable.
>
> Emit exactly one `Timber.d` line when the aggressive branch returns non-MONO:
>
> ```kotlin
> Timber.d(
>     "VR_AUDIT/12: detectForImage result=%s source=user-initiated-tap filename=%s w=%s h=%s",
>     result, path, width, height,
> )
> ```
>
> Do not touch `detectFromAspectRatio` (its conservative behaviour is required by the passive path). Do not touch any other method. KDoc the new parameter: explain that `userInitiated = true` is used by the player VR-toolbar tap and biases toward stereo when the cascade is silent.

**Verification:**

- `Grep -n "userInitiated" app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt` returns ≥ 2 hits (declaration + at least one usage).
- `Grep -n "source=user-initiated-tap" app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt` returns exactly one hit.
- Compile preflight (variant-agnostic): `.\build-debug.PS1` exit 0 (or `assembleStandardDebug` if `build-debug.PS1` defaults differ — kotlin-only change must compile under both flavor sets).
- `Grep -n "detectFromAspectRatio" app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt` — body unchanged from current state (file diff scope of step ≤ 35 LOC).

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — Edit applied (+52 LOC in `StereoDetector.kt`, `userInitiated` overload + `detectForImagePassive` extraction + `aggressiveDimensionGuess` helper). Grep predicates PASS (3 hits `userInitiated`, 1 hit `source=user-initiated-tap`, `detectFromAspectRatio` body unchanged). First build attempt FAILED on `kaptStandardDebugKotlin` due to stale kapt stubs left over from unrelated WIP TextNoteStaging refactor — resolved with `gradlew :app_v2:clean` + re-run. Final `.\build-debug.PS1` exit 0 (37s, `assembleStandardDebug` PASS).

---

### Step 01.2 — Unit tests for `userInitiated` mode `[x] done`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/StereoDetectorUserInitiatedTest.kt` (new)
**Depends on:** Step 01.1

**Prompt for developer:**

> Create a new JUnit4 test class `StereoDetectorUserInitiatedTest` in `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/`. Mirror the structure of the existing `StereoDetectorPhotoSphereTest.kt` (same package, same import style, JUnit4 `@Test` annotations, `org.junit.Assert.assertEquals`).
>
> Required cases:
>
> 1. `userInitiated=true, plain panorama 4096×1024, no tokens → SBS_FULL` — confirms aggressive SBS branch.
> 2. `userInitiated=true, 2048×2048 square, no tokens → OU` — confirms aggressive OU square branch.
> 3. `userInitiated=true, 1024×2048 portrait, no tokens → OU` — confirms aggressive OU portrait branch.
> 4. `userInitiated=true, 4032×3024 (regular DSLR landscape, aspect ≈ 1.33), no tokens → MONO` — DSLR photo must not be falsely labelled stereo.
> 5. `userInitiated=true, "renamed_sbs_panorama.jpg" 1024×512 → SBS_FULL` — filename token still wins over dimensions (and over aggressive AR rule).
> 6. `userInitiated=false (default), 4096×1024, no tokens → MONO` — passive path is unchanged (regression guard).
> 7. `userInitiated=true, width=null → MONO` — null-safety guard (no panic).
>
> Construct the detector via `StereoDetector()` (default constructor — same as the photo-sphere test). For filename-only cases use a non-existent path; the detector treats missing file as no PhotoSphere XMP.
>
> No mocks, no Robolectric — pure JVM unit test.

**Verification:**

- `Grep -n "@Test" app_v2/src/test/java/com/sza/fastmediasorter/ui/player/StereoDetectorUserInitiatedTest.kt` returns exactly 7 hits.
- `.\build-debug.PS1` exit 0 (compile guard).
- Run the new tests in isolation. Open Bash: `"/c/Program Files/PowerShell/7/pwsh.exe" -File ./build-debug.PS1` is for compile only; for unit-test execution use `cmd /c "gradlew.bat :app_v2:testStandardDebugUnitTest --tests com.sza.fastmediasorter.ui.player.StereoDetectorUserInitiatedTest"` — must report `7 tests, 0 failures`. If `testStandardDebugUnitTest` overall is red on pre-existing tests, the per-class XML report at `app_v2/build/reports/tests/testStandardDebugUnitTest/classes/com.sza.fastmediasorter.ui.player.StereoDetectorUserInitiatedTest.html` must show `0 failures` for this class specifically (pre-existing test failures are documented project-wide and are not in scope of this step — see project memory note `project_catalog_scan_source_sets`).

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — Created `StereoDetectorUserInitiatedTest.kt` with 7 cases. Initial run showed two cases (panorama and regression) failed because `4096×1024` is already classified `EQUIRECT_360_SBS` by the conservative cascade (the AR is exactly 4.0 and width hits the spherical-SBS floor). Switched both cases to `2400×800` (AR=3.0, between the EQUIRECT_360_SBS narrow window and the flat SBS 3.2..3.8 band; conservative returns MONO). Fixed two pre-existing test sources broken by WIP `LocalStagingRegistry` refactor (`CloudFileOperationHandlerTest.kt` +4 mock params, `RestoreDeletedUseCaseTest.kt` +1 mock param for `LocalOperationStrategy`). Final: `testStandardDebugUnitTest --tests StereoDetectorUserInitiatedTest` BUILD SUCCESSFUL, 7 tests, 0 failures. Grep @Test count = 7.

---

### Step 01.3 — Remove `MediaType.VIDEO` gates from VR button visibility and planner `[x] done`

**Files:**

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` (line ~450 — visibility binding)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` (line ~261 — planner add)

**Depends on:** Step 01.1 (the detector change does not strictly block the gate change, but landing them together avoids a half-state where the button is visible but uses stale detection)

**Prompt for developer:**

> Two single-line edits.
>
> **(a) `CommandPanelController.kt`** — find the line
>
> ```kotlin
> safeViews.btn3dVrCmd.isVisible = currentFile.type == MediaType.VIDEO
> ```
>
> Replace with:
>
> ```kotlin
> safeViews.btn3dVrCmd.isVisible = currentFile.type in VR_BUTTON_MEDIA_TYPES
> ```
>
> Where `VR_BUTTON_MEDIA_TYPES` is a new file-private constant at the top of the file (right after imports, before the class declaration):
>
> ```kotlin
> private val VR_BUTTON_MEDIA_TYPES = setOf(MediaType.VIDEO, MediaType.IMAGE, MediaType.GIF)
> ```
>
> **(b) `CommandPanelLayoutPlanner.kt`** — find the line
>
> ```kotlin
> if (BuildConfig.SUPPORT_VR_PLAYER && file.type == MediaType.VIDEO) add(PlayerCommand.VR_3D)
> ```
>
> Replace with:
>
> ```kotlin
> if (BuildConfig.SUPPORT_VR_PLAYER && file.type in VR_BUTTON_MEDIA_TYPES) add(PlayerCommand.VR_3D)
> ```
>
> Define a parallel file-private constant in `CommandPanelLayoutPlanner.kt` (same name, same value). Do NOT export the constant or share it across files — the duplication is intentional and keeps each file self-contained.
>
> Update KDoc nowhere — the planner's enum already documents `VR_3D` as VR-flavor only; the media-type predicate is implementation detail. Existing surrounding code untouched.

**Verification:**

- `Grep -n "VR_BUTTON_MEDIA_TYPES" app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` returns ≥ 2 hits (declaration + usage).
- `Grep -n "VR_BUTTON_MEDIA_TYPES" app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` returns ≥ 2 hits.
- `Grep -n "btn3dVrCmd.isVisible = currentFile.type == MediaType.VIDEO" app_v2/src/main` returns zero hits (old gate gone).
- `Grep -n "file.type == MediaType.VIDEO" app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` returns zero hits at the VR_3D add site (other unrelated `MediaType.VIDEO` checks elsewhere in the planner may remain).
- `.\build-debug.PS1` exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — Added file-private `VR_BUTTON_MEDIA_TYPES = setOf(VIDEO, IMAGE, GIF)` to both `CommandPanelController.kt` and `CommandPanelLayoutPlanner.kt`. Replaced the `currentFile.type == MediaType.VIDEO` gate (controller) and the planner add condition. Grep predicates PASS: 2 hits each file, 0 hits for old gate. `.\build-debug.PS1` exit 0 (1m 40s).

---

### Step 01.4 — Re-run stereo detection in `on3dVrToggleClicked` for image/gif `[x] done`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt`
**Depends on:** Step 01.1, Step 01.3

**Prompt for developer:**

> Inject a `StereoDetector` instance (matching the construction pattern used by `PlayerMediaLoaderManager.kt:140` — currently `private val stereoDetector = com.sza.fastmediasorter.ui.player.StereoDetector()`). Place the field at the top of `PlayerCommandPanelCallbackImpl` next to other private state.
>
> Replace the body of `on3dVrToggleClicked()` (currently a one-liner delegating to `activity.handle3dVrToggleClicked()`):
>
> ```kotlin
> override fun on3dVrToggleClicked() {
>     val state = activity.viewModel.state.value
>     val file = state.currentFile
>     if (file != null && file.type in setOf(MediaType.IMAGE, MediaType.GIF)) {
>         val detected = stereoDetector.detectForImage(
>             path = file.path,
>             width = file.width,
>             height = file.height,
>             userInitiated = true,
>         )
>         if (detected != StereoMode.UNKNOWN &&
>             detected != StereoMode.AUTO &&
>             detected != activity.viewModel.stereoMode.value) {
>             activity.viewModel.setAutoDetectedStereoMode(detected)
>             Timber.i(
>                 "PlayerCommandPanelCallback: user-initiated VR-tap re-detected stereo for image " +
>                     "path=%s detected=%s",
>                 file.path, detected,
>             )
>         }
>     }
>     activity.handle3dVrToggleClicked()
> }
> ```
>
> Import resolution: `MediaType` from `com.sza.fastmediasorter.domain.model`, `StereoMode` from `com.sza.fastmediasorter.domain.model`, `Timber` from `timber.log`. Resolve existing imports first — most are likely already present.
>
> Do NOT touch the VIDEO path. Do NOT add detection in `handle3dVrToggleClicked` itself (that path is also reached from `PlaybackControlDialog.btnApplyAnd3D` where the user already configured stereo explicitly in the dialog).

**Verification:**

- `Grep -n "userInitiated = true" app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt` returns exactly one hit.
- `Grep -n "setAutoDetectedStereoMode" app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt` returns exactly one hit.
- `Grep -n "handle3dVrToggleClicked" app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt` returns exactly one hit (the existing delegation remains the last statement of the method).
- `.\build-debug.PS1` exit 0.
- `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` — modified files appear with fresh mtime in `dev/CATALOG/app_v2.jsonl`.
- `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2` — `dev/CATALOG/app_v2.md` regenerated.

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — Added `StereoDetector` field via `StereoDetector()` constructor (matches `PlayerMediaLoaderManager` pattern). Rewrote `on3dVrToggleClicked` body: for `MediaType.IMAGE`/`GIF` it runs `detectForImage(... userInitiated = true)`, persists result via `setAutoDetectedStereoMode` if it differs from current, logs at INFO level; then delegates to `activity.handle3dVrToggleClicked()` unchanged. Video path untouched (the existing `PlaybackControlDialog.btnApplyAnd3D` flow remains the canonical video entry). Grep predicates PASS (1 hit each for `userInitiated = true`, `setAutoDetectedStereoMode`, `handle3dVrToggleClicked`). `.\build-debug.PS1` exit 0 (1m 54s). Catalog scan+render regenerated `dev/CATALOG/app_v2.jsonl` (1139 files, 1386 records) and `.md`.

---

## Phase Done Criteria

- [x] Step 01.1 `[x] done`.
- [x] Step 01.2 `[x] done`.
- [x] Step 01.3 `[x] done`.
- [x] Step 01.4 `[x] done`.
- [x] Project compiles — `.\build-debug.PS1` exit 0 (final pass on Step 01.4).
- [x] `testStandardDebugUnitTest --tests StereoDetectorUserInitiatedTest` — 7 / 7 PASS (re-run 2026-05-17 20:32, 19 s).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (1139 files, 1386 records) and `.md` rendered.
- [x] Dev log entries written: `StereoDetector.kt` (01.1), test file + 2 side-fix tests (01.2), `CommandPanelController.kt` + `CommandPanelLayoutPlanner.kt` (01.3), `PlayerCommandPanelCallbackImpl.kt` (01.4).

---

## Handoff Notes to Next Phase

No next phase — this is the only phase of S0238. After all 4 steps are done, `/spec-dev` flips strategic status: first to `Implemented`, then to `BlockNeedUserTest` (on-device verification required), inserting a single `Timber.d("S0238: image-toolbar VR tap entry")` tag in `PlayerCommandPanelCallbackImpl.on3dVrToggleClicked` so the operator can confirm the path is exercised in logcat.

---

## Rollback Plan

- Step 01.4: revert `PlayerCommandPanelCallbackImpl.on3dVrToggleClicked` body to the previous one-liner.
- Step 01.3: revert the two gate lines + remove the two `VR_BUTTON_MEDIA_TYPES` constants.
- Step 01.2: delete the new test file.
- Step 01.1: revert the overload (named-arg compatibility preserved — no call-site rollback needed).

No data, schema, manifest, or Hilt graph change. Rollback is mechanical.

---

## Change Log

- 2026-05-17 — Phase authored. Original 5-step plan compressed to 4 steps after code-research revealed `btn3dVrCmd` already fully wired — only `MediaType.VIDEO` gate removal + user-initiated detection are needed.
