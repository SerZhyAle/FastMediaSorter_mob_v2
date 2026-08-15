# Phase 03 - Enable-all orchestrator

**Strategic spec:** [`../S0409_welcome-enable-all.md`](../S0409_welcome-enable-all.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-12
**Completed:** 2026-06-12

---

## Objective

Introduce `WelcomeEnableAllManager`: a host-attached coordinator that runs the full enable-all sequence
as a rotation-safe state machine - profile OTHER, settings, sequential permissions, sequential
default-player dialogs, completion - without any UI trigger yet.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomeEnableAllManager.kt` | New | ≤ 320 |

---

## Steps

### Step 03.1 - Create the manager shell with launcher + saved state

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomeEnableAllManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `WelcomeEnableAllManager` with `@Inject constructor` taking `ApplyEnableAllSettingsUseCase`,
> `MediaCapabilities`, `CapabilityAvailability`, `InstallSourceProvider`, `DeliverableDownloadRunner`,
> `SettingsRepository`, `@ApplicationContext Context`, and `@ApplicationScope CoroutineScope`. Add
> `fun attach(activity: FragmentActivity)` that registers one `ActivityResultLauncher<Intent>` on the
> Activity's `activityResultRegistry` with a stable key (mirror `WelcomePermissionsManager.attach`); its
> result callback advances the default-player stage (Step 03.2). Hold the sequence state in fields: an
> in-progress flag and the current default-player type index. Add `onSaveInstanceState(Bundle)` /
> `onRestoreInstanceState(Bundle?)` that persist and restore those fields, mirroring
> `WelcomePermissionsManager`.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomeEnableAllManager.kt` exists.
- `Grep` - `class WelcomeEnableAllManager` matches once.
- `Grep` - `fun attach(` present.
- `Grep` - `activityResultRegistry` present.
- `Grep` - `fun onSaveInstanceState(` and `fun onRestoreInstanceState(` present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-12 - Verification PASS. Files: ui/welcome/helpers/WelcomeEnableAllManager.kt (New). Refinement: permissionsManager + onFinished moved into attach() (re-wired every setupViews) for rotation-safety (strategic crit. 8); start() takes only applyProfileOther.

---

### Step 03.2 - Implement the sequence

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomeEnableAllManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add `fun start(permissionsManager: WelcomePermissionsManager, applyProfileOther: () -> Unit,
> onFinished: () -> Unit)` running these stages in order:
> 1. Invoke `applyProfileOther()` (the Activity passes a lambda that calls `viewModel.onProfileSelected(
>    DeviceProfileType.OTHER)` then `viewModel.saveDeviceProfile(isSkipped = false)`), then run
>    `ApplyEnableAllSettingsUseCase` on the application scope. Sequence the settings write strictly after
>    the profile save so the whitelist wins over the OTHER preset (research 03).
> 2. After settings, call `permissionsManager.runGrantAll(onComplete = { beginDefaultPlayerStage() })`.
> 3. `beginDefaultPlayerStage()`: if `!mediaCapabilities.supportsDefaultPlayer`, skip straight to
>    completion (strategic criterion 9). Otherwise build the applicable MIME list gated by capabilities
>    (`audio/*` if `supportsAudio`, `video/*` if `supportsVideo`, `image/*` if `supportsImages`,
>    `application/pdf` if `supportsDocuments`), reset the index, and launch the first type via
>    `DefaultPlayerManager.applyPrimaryPlayerState(activity, true)` then
>    `DefaultPlayerHelper.openChooserOrFallbackForResult(activity, launcher, mime)`. The launcher callback
>    (Step 03.1) increments the index and launches the next type; when the list is exhausted it calls
>    `onFinished()`.
> 4. Separately, enqueue OCR/translation deliverables where available (reuse the
>    `CapabilityAvailability.isOcrAvailable(context) && !installSource.isPlayInstall()` /
>    `isTranslationAvailable()` gates from `WelcomeFunctionalityController`) via
>    `DeliverableDownloadRunner.enqueue`, and flip `enableOcr`/`enableTranslation` to true only on
>    `DownloadProgress.Installed` (enable-only-after-install, S0386). Never block the sequence on these
>    downloads. The `Timber.d("S0409:` BlockNeedUserTest probe is inserted at `start()` in the
>    final-phase debug-tag step (after all code edits, before the final build), not here - the
>    ticket-log gate rejects ticket-id logs while the spec is still In Progress.

**Verification:**

- `Grep` - `fun start(` matches once.
- `Grep` - `runGrantAll` present (trailing-lambda call site).
- `Grep` - `openChooserOrFallbackForResult(` present.
- `Grep` - `supportsDefaultPlayer` present (lite skip path).
- `Grep` - `DownloadProgress.Installed` present (enable-only-after-install).
- (S0409 probe tag inserted in the final-phase debug-tag step, not this phase.)

**Status:** `[x] done`

**Step Log:**

- 2026-06-12 - Verification PASS (start, runGrantAll, openChooserOrFallbackForResult, supportsDefaultPlayer, DownloadProgress.Installed). Refinement: helper already calls applyPrimaryPlayerState internally, so the manager calls only openChooserOrFallbackForResult. OCR/translation enqueued + enabled-on-install via terminal-state collect. S0409 probe deferred to final-phase tag step (ticket-log gate forbids it while In Progress).

---

### Step 03.3 - Bind the manager in DI for Activity field injection

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomeEnableAllManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Confirm the manager is constructor-injectable with no missing bindings so it can be field-injected into
> `WelcomeActivity` exactly like `WelcomePermissionsManager` (all constructor deps are already provided
> elsewhere - `DeliverableDownloadRunner`, `CapabilityAvailability`, `InstallSourceProvider`,
> `MediaCapabilities`, `SettingsRepository`, `@ApplicationScope`). No new `@Module` is expected; if a
> dependency turns out unbound, add the binding in the existing welcome/capability Hilt module rather
> than a new one. Resolve by building with `.\a.ps1 fk`.

**Verification:**

- `.\a.ps1 fk` compiles (Hilt graph resolves; no missing-binding error for `WelcomeEnableAllManager`).

**Status:** `[x] done`

**Step Log:**

- 2026-06-12 - `.\a.ps1 fk` BUILD SUCCESSFUL (kaptStandardDebugKotlin ran; constructor-injectable, no new @Module needed).
- 2026-06-13 - Post-delivery fix (owner device-test): `allFiles=true` alone did not materialize the browsable "All Files" resource. The OTHER profile's preset never implies allFiles, so `saveDeviceProfile` skips `EnsureAllFilesPredefinedResourceUseCase`. Injected that use case into the orchestrator and call it right after the settings write. Verified on device: `Created predefined All Files resource: id=1`.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`WelcomeEnableAllManager` is field-injectable and host-attached. Phase 04 injects it into
`WelcomeActivity`, calls `attach()` in `setupViews`, delegates `onSaveInstanceState`/`onRestoreInstanceState`,
and wires the page-0 button to `start(permissionsManager, applyProfileOther, ::completeWelcomeFlow)`.

---

## Rollback Plan

Revert phase commit(s) - new file only; nothing references it until Phase 04.
