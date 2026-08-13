# S1336 - CameraSettingsDialogFragment crashes when the system recreates it

**Status:** Archived
**Priority:** 55

<!-- discovered by /spec-tech S1331 survey - 2026-07-31, parked per CLAUDE.md 3.1 -->

## 0. Raw capture

Found while surveying every `DialogFragment` that holds a host-supplied callback in a field, for
S1331. Six of the seven lose the callback silently. This one **throws**.

`ui/cameracapture/CameraSettingsDialogFragment.kt` holds three values as `lateinit var`, all assigned
by the caller after construction: `callbacks`, `capabilities` and `initialSettings`. The
`FragmentManager` rebuilds a restored fragment through its no-argument constructor, so none of the
three is assigned, and `onCreateDialog` reaches `draft = initialSettings.copy()` and throws
`UninitializedPropertyAccessException`.

## 1. Why it is parked rather than folded into S1331

S1331 converts five dialogs to the `FragmentResult` API by one repeated template: a one-shot result
travels back as a `Bundle`. This dialog does not fit that template.

Its `Callbacks` interface carries a **live preview stream** - it fires on every slider movement so the
camera preview updates while the user drags. `FragmentResult` is a poor carrier for that: it is
designed for a single result at dismissal, not for a continuous feed. Folding it in would either force
a bad shape onto the plan's other four conversions or leave this one half-converted.

It also needs to restore `capabilities` and `initialSettings`, not just re-deliver a callback, so the
fix shape is "make the fragment reconstruct its own inputs from `arguments`", which is a different
change from "stop holding a lambda".

## 2. Why the priority is above S1331's

S1331 fixes silent losses; this is a crash. The exposure is narrower - `CameraCaptureActivity` is
locked to portrait and declares `configChanges` for `keyboardHidden`, so rotation cannot trigger it -
but the triggers that remain are ordinary: a theme change, a language change, a font-size change,
"don't keep activities", and process death while the dialog is open.

## 3. Scope sketch (to be settled at Approval)

- Whether `capabilities` and `initialSettings` move into `arguments` as primitives, or the fragment
  re-reads them from a shared ViewModel it can obtain after recreation.
- How the live-preview feed survives: a `ViewModel`-hosted `StateFlow` the host collects is the
  obvious candidate, since it re-attaches by itself, but that is a design call.
- Whether the final "apply" result should additionally go through `FragmentResult` for consistency
  with the five dialogs S1331 converts.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1331 (`bugfix-dialog-callbacks-lost-on-recreate`) - the survey that found this;
  its plan lists this dialog under Out of Scope with the reasoning above. S1214 - shipped the
  `FragmentResult` reference conversion the sibling dialogs follow.
- **Scope:** `ui/cameracapture/CameraSettingsDialogFragment.kt` and its single call site. No new
  user-visible strings expected.
- **Flavors:** builds that ship camera capture; read the gate rather than assuming.
- **Correction carried from the S1331 survey:** the premise "the callback is lost after a rotation" is
  wrong for every dialog in this family. Their hosts declare `configChanges` for orientation, and this
  one is portrait-locked outright, so rotation recreates nothing. The real triggers are theme and
  language changes, font-size changes, "don't keep activities" and process death. Any device test
  written around rotation will pass while the defect is still there.

---

## Goal

Диалог настроек камеры падает с `UninitializedPropertyAccessException`, когда `FragmentManager`
восстанавливает его через пустой конструктор после пересоздания хоста (смена темы, языка, размера
шрифта, "не сохранять активности", смерть процесса) - три поля (`callbacks`, `capabilities`,
`initialSettings`) остаются неинициализированными. Вместо того чтобы протаскивать их через
`arguments`/`ViewModel` (что для `capabilities` означало бы сериализацию тридцати полей камеры),
фрагмент сам запрашивает актуальный снимок у хоста в `onAttach`/`onCreateDialog`:
`CameraCaptureActivity` реализует новый однометодный `CameraSettingsDialogFragment.Host`, отдающий
уже существующий `CameraSettingsCallbackHandler`, а тот умеет вернуть текущие `capabilities`,
`initialSettings` (то же вычисление, что раньше делал `show()`) и `rotationBucket`. Это одновременно
чинит падение и делает восстановленный диалог показывающим уже применённые, а не дефолтные,
настройки - источник данных живые менеджеры сессии, а не замороженный снимок на момент первого
открытия.

## Phase 1 - Reconstruct dialog inputs from the host instead of injected fields

- [x] `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraSettingsDialogFragment.kt` -
  extend `Callbacks` with `currentCameraCapabilities()`, `currentCameraSettingsState()`,
  `cameraRotationBucket()`; add a nested `Host` interface (`fun cameraSettingsCallbacks(): Callbacks`);
  add `override fun onAttach(context: Context)` resolving `callbacks` from `context as? Host` (`error(..)`
  otherwise) and `rotationBucketState` from `callbacks.cameraRotationBucket()`; make `capabilities`,
  `initialSettings`, `callbacks`, `rotationBucketState` private; populate `capabilities`/`initialSettings`/
  `draft` inside `onCreateDialog` by calling `callbacks`, not from externally-injected fields.
  - **Verification:** `Grep "interface Host"` and `Grep "override fun onAttach"` each match once in the
    file; no `lateinit var` of `callbacks`/`capabilities`/`initialSettings` remains settable from outside
    the class.
- [x] `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraSettingsCallbackHandler.kt` -
  implement the 3 new `Callbacks` methods (`currentCameraCapabilities()` returns
  `flowManager.currentCapabilities`; `currentCameraSettingsState()` is the exact `CameraSettingsState(..)`
  construction moved out of `show()`, unchanged field-for-field; `cameraRotationBucket()` returns the
  existing `rotationBucket` constructor param); simplify `show()` to
  `CameraSettingsDialogFragment().show(fragmentManager, CameraSettingsDialogFragment.TAG)` with no field
  injection.
  - **Verification:** `Grep "override fun currentCameraCapabilities"` matches; `show()` body no longer
    contains `.apply {`.
- [x] `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` - add
  `CameraSettingsDialogFragment.Host` to the class's implemented-interface list; implement
  `override fun cameraSettingsCallbacks(): CameraSettingsDialogFragment.Callbacks = settingsCallbackHandler`.
  One delegate method, mirroring the existing `CameraCaptureFlowManager.Host` pattern so ADR-1 (callback
  logic stays off the Activity, only a getter lives there) still holds.
  - **Verification:** `Grep "CameraSettingsDialogFragment.Host"` matches the class header.
- **Verification:** `standard debug` compiles clean. The functional check (theme/language switch and
  "don't keep activities" survive without a crash, live preview keeps tracking, applied settings show
  correctly after recreation) is device-test gated per section 4 below - both triggers are drivable on
  the connected emulator via `adb shell` (dark-mode toggle, `always_finish_activities`), no real device
  required.

## 4. Verification

- Open the camera settings dialog, switch the system theme (or the app language), and confirm the
  dialog survives instead of throwing.
- Confirm the live preview still tracks slider movement after that recreation, not just on first open.
- Confirm the applied settings are the ones shown, not the defaults.

## Last Audit

**Date:** 2026-08-02
**Mode:** strategic (compact spec, Simple path)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 12 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

Device-run evidence: `temp/S1336/mobile_test_scenario_20260802_0710.md` (emulator-5554, Pixel 9 /
Android 15, build `2.60.7262.102-DEBUG`). The on-device pass caught and fixed two real regressions
the static build could not see, both in code this ticket touched:

1. `onAttach` read `host.cameraSettingsCallbacksFlow`/`.cameraSettingsCallbacks` too eagerly - a
   config-change relaunch dispatches `FragmentManager.dispatchCreate()` (and so `onAttach`) from
   inside `Activity.onCreate()`/`super.onCreate()`, before `BaseActivity`'s deferred
   `binding.root.post {}` has run `setupViews()`/`initializeHelperManagers()`. Reproduced live via
   `cmd uimode night yes` while the dialog was open -
   `UninitializedPropertyAccessException: lateinit property settingsCallbackHandler` from
   `CameraCaptureActivity.getCameraSettingsCallbacks` via `CameraSettingsDialogFragment.onAttach`.
   Fixed by moving the host read from `onAttach` into `onCreateDialog`, gated by a
   `StateFlow<Callbacks?>` the fragment awaits instead of reading synchronously.
2. Once the crash was gone, capabilities-dependent rows (Aspect ratio, Resolution, Exposure, White
   balance) rendered empty after recreation - the flow's readiness signal fired the moment the
   handler object was *constructed*, before `flowManager.currentCapabilities` had ever been updated
   from `CameraRuntimeCapabilities.NONE`. Fixed by moving the signal to `renderCapabilities()`
   (first real capabilities report), which also matches when the settings button itself first
   becomes visible.

Re-tested after both fixes: no crash, every row present, persisted settings (aspect ratio,
resolution, white balance, self-timer) show their actually-applied values, `Timber.d("S1336: ..")`
confirms `recreated=true` (genuine `FragmentManager` restore, not a fresh `show()`). Exposure resets
to 0 after recreation - expected, since exposure compensation is a live CameraX session control never
persisted for any camera-session teardown (recreation or otherwise), out of this ticket's scope.

`always_finish_activities=1` did not force a real Activity destroy when returning via Recents on this
emulator/launcher (Quickstep gesture nav) - same `Camera@` object identity before/after, no
process/Activity restart evidence. `cmd uimode night yes/no` reliably forced the same
`FragmentManager`-restore code path instead, which is what the crash and both fixes above actually
live on - theme, language, font-size, "don't keep activities" and process death all funnel through
the identical `Activity.onCreate(savedInstanceState)` restore, so this evidence covers the class of
triggers, not only the one exercised.

### Manual / on-device

- [x] Dialog survives theme switch instead of throwing - verified on-device 2026-08-02.
- [x] Live preview still tracks slider movement after recreation - verified on-device 2026-08-02.
- [x] Applied (persisted) settings shown correctly after recreation, not defaults - verified
  on-device 2026-08-02.
