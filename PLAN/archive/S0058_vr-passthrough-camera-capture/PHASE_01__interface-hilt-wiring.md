# Phase 01 — Interface + Hilt Wiring

**Strategic spec:** [`../S0058_vr-passthrough-camera-capture.md`](../S0058_vr-passthrough-camera-capture.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, all subsequent
**Steps done:** 6 / 6
**Started:** 2026-05-05
**Completed:** 2026-05-05

---

## Objective

Introduce `BrowsePassthroughCaptureProvider` interface and its optional Hilt binding in the main source set; wire `BrowseActivity` and `BrowseManagerInitializer` so the VR provider (empty Optional on non-VR) is consulted for button visibility and capture dispatch. No VR-specific code yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.
- [ ] `BrowseCameraCaptureManager` and `BrowseManagerInitializer` read and understood (already done during spec-tech).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowsePassthroughCaptureProvider.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/BrowsePassthroughOptionalModule.kt` | New | ≤ 25 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` | Modified | ≤ 420 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt` | Modified | current + ≤ 10 |

> `BrowseManagerInitializer.kt` is large — take a timestamped backup in `temp/` before editing.

---

## Steps

### Step 01.1 — Create `BrowsePassthroughCaptureProvider` interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowsePassthroughCaptureProvider.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a new file `BrowsePassthroughCaptureProvider.kt` in the `ui/browse/managers` package. Define a Kotlin `interface BrowsePassthroughCaptureProvider` with two methods:
>
> ```kotlin
> fun isAvailable(context: Context): Boolean
> fun launch(activity: FragmentActivity, resource: MediaResource, onFileSaved: (name: String) -> Unit)
> ```
>
> Import `android.content.Context`, `androidx.fragment.app.FragmentActivity`, and `com.sza.fastmediasorter.domain.model.MediaResource`. No implementations in this file.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowsePassthroughCaptureProvider.kt` exists.
- `Grep` — `interface BrowsePassthroughCaptureProvider` matches exactly once in that file.
- `Grep` — `fun isAvailable` present in that file.
- `Grep` — `fun launch` present in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 4/4 PASS. Files: ui/browse/managers/BrowsePassthroughCaptureProvider.kt (new, 9 LOC). Dev log recorded.

---

### Step 01.2 — Create `BrowsePassthroughOptionalModule` Hilt module

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/di/BrowsePassthroughOptionalModule.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create a Hilt module at `di/BrowsePassthroughOptionalModule.kt` in the `di` package. It must:
>
> 1. Be annotated `@Module` + `@InstallIn(SingletonComponent::class)`.
> 2. Be an `interface` (not `abstract class`) with a single `@BindsOptionalOf` method returning `BrowsePassthroughCaptureProvider`.
>
> Example:
> ```kotlin
> @Module
> @InstallIn(SingletonComponent::class)
> interface BrowsePassthroughOptionalModule {
>     @BindsOptionalOf
>     fun optionalPassthroughCaptureProvider(): BrowsePassthroughCaptureProvider
> }
> ```
>
> Imports: `dagger.BindsOptionalOf`, `dagger.Module`, `dagger.hilt.InstallIn`, `dagger.hilt.components.SingletonComponent`.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/di/BrowsePassthroughOptionalModule.kt` exists.
- `Grep` — `@BindsOptionalOf` present in that file.
- `Grep` — `BrowsePassthroughCaptureProvider` present in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 2/2 PASS. Files: di/BrowsePassthroughOptionalModule.kt (new, 13 LOC). Dev log recorded.

---

### Step 01.3 — Inject `Optional<BrowsePassthroughCaptureProvider>` into `BrowseActivity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `BrowseActivity`, add a Hilt-injected field for the optional passthrough provider. Add after the existing `@Inject` fields (around line 70):
>
> ```kotlin
> @Inject lateinit var passthroughCaptureProvider: java.util.Optional<BrowsePassthroughCaptureProvider>
> ```
>
> Add the import for `BrowsePassthroughCaptureProvider` (same package as `BrowseCameraCaptureManager`). Do NOT yet wire it to `onCameraCaptureClicked()` — that is Step 01.6.

**Verification:**

- `Grep` — `passthroughCaptureProvider` present in `BrowseActivity.kt`.
- `Grep` — `java.util.Optional` import present in `BrowseActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 2/2 PASS. Files: ui/browse/BrowseActivity.kt (modified). Dev log recorded.

---

### Step 01.4 — Add `passthroughProvider` parameter to `BrowseManagerInitializer`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Back up `BrowseManagerInitializer.kt` to `temp/BrowseManagerInitializer_<timestamp>.kt` before editing.
>
> Add a new constructor parameter at the end of the `BrowseManagerInitializer` parameter list (after `isSkipAvailabilityCheck: Boolean`):
>
> ```kotlin
> private val passthroughProvider: BrowsePassthroughCaptureProvider? = null,
> ```
>
> Add the import for `BrowsePassthroughCaptureProvider`. Do NOT yet change the camera visibility logic — that is Step 01.5.
>
> In `BrowseActivity.setupViews()` where `BrowseManagerInitializer` is constructed, pass the new argument:
> ```kotlin
> passthroughProvider = passthroughCaptureProvider.orElse(null),
> ```

**Verification:**

- `Grep` — `passthroughProvider` appears in `BrowseManagerInitializer.kt` constructor declaration.
- `Grep` — `passthroughProvider = passthroughCaptureProvider.orElse(null)` present in `BrowseActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 2/2 PASS. Files: BrowseManagerInitializer.kt + BrowseActivity.kt (modified). Backup: temp/BrowseManagerInitializer_20260505_174040.kt. Dev log recorded.

---

### Step 01.5 — Update camera visibility check to short-circuit on passthrough availability

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> In `BrowseManagerInitializer`, locate the block that computes `isCameraVisible` (around line 593). Replace:
>
> ```kotlin
> val isCameraVisible = isCameraVisibleByState &&
>     viewModel.state.value.resource?.let { res ->
>         BrowseCameraCaptureManager.hasCameraHandler(activity, res)
>     } ?: false
> ```
>
> with:
>
> ```kotlin
> val isCameraVisible = isCameraVisibleByState &&
>     viewModel.state.value.resource?.let { res ->
>         passthroughProvider?.isAvailable(activity) == true ||
>             BrowseCameraCaptureManager.hasCameraHandler(activity, res)
>     } ?: false
> ```
>
> This ensures `hasCameraHandler()` (and its warning log) is skipped when passthrough is available.

**Verification:**

- `Grep` — `passthroughProvider?.isAvailable` present in `BrowseManagerInitializer.kt`.
- `Grep` — `BrowseCameraCaptureManager.hasCameraHandler` still present (not removed, just short-circuited).

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 2/2 PASS. Files: BrowseManagerInitializer.kt (modified). Dev log recorded.

---

### Step 01.6 — Route `onCameraCaptureClicked()` to passthrough when provider is present

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> In `BrowseActivity.onCameraCaptureClicked()`, replace the current body with:
>
> ```kotlin
> internal fun onCameraCaptureClicked() {
>     val resource = viewModel.state.value.resource
>     Timber.i(
>         "S0022-CAM: BrowseActivity.onCameraCaptureClicked resource=%s",
>         resource?.let { "{id=${it.id}, type=${it.type}, name=${it.name}}" } ?: "NULL",
>     )
>     if (resource == null) {
>         Timber.w("S0022-CAM: BrowseActivity.onCameraCaptureClicked ABORT — viewModel resource is null")
>         return
>     }
>     val passthrough = passthroughCaptureProvider.orElse(null)
>     if (passthrough != null) {
>         Timber.i("S0058: routing camera capture to passthrough provider")
>         passthrough.launch(this, resource) { fileName -> onCapturedFileSaved(fileName) }
>     } else {
>         cameraCaptureManager.launch(resource)
>     }
> }
> ```

**Verification:**

- `Grep` — `passthroughCaptureProvider.orElse(null)` present in `BrowseActivity.kt`.
- `Grep` — `passthrough.launch(this, resource)` present in `BrowseActivity.kt`.
- `Grep` — `cameraCaptureManager.launch(resource)` still present in `BrowseActivity.kt` (else branch).

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 3/3 PASS. Files: BrowseActivity.kt (modified). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `BrowsePassthroughCaptureProvider` interface is in main, optional binding declared.
- `BrowseActivity` and `BrowseManagerInitializer` ready to receive a VR implementation.
- On non-VR builds the `Optional` is empty — existing behaviour fully preserved.
- Phase 02 provides the concrete `VrBrowsePassthroughCaptureManager` and Hilt binding in the VR source set.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed (VR build still shows no capture button; non-VR builds unchanged).
