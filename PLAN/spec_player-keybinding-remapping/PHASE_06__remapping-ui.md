# Phase 06 — Remapping UI (Settings Entry + Fullscreen Dialog + Capture)

**Strategic spec:** [`../spec_player-keybinding-remapping.md`](../spec_player-keybinding-remapping.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 07
**Steps done:** 6 / 7
**Started:** 2026-04-25
**Completed:** 2026-04-25

---

## Objective

Ship the user-facing remapping surface: a "Controls & Keybindings" entry in `SettingsActivity`, a fullscreen `KeybindingRemapActivity` with grouped list, capture-mode modal, search/filter and per-row "Reset to default". No reset-cascade, no conflict visualiser — those are Phase 07.

---

## Prerequisites

- [ ] Phase 02 is `✅ Done` — `InputBindingRepository`, `KeyBindingManager`, Defaults Map File all in place.
- [ ] Strategic §10 items resolved in writing: **capture timeout**, **unrecognised trigger display**, **modifier capture policy**, **analog threshold UX**, **undo window**, **per-profile support**. All block Phase 06 UX decisions.
- [ ] Trilingual resource files (`values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`) reachable — any new string is added to all three.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/KeybindingRemapActivity.kt` | New | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/KeybindingRemapViewModel.kt` | New | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/KeybindingListAdapter.kt` | New | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/CaptureDialogFragment.kt` | New | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/helpers/KeybindingRowLabelFormatter.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/input/usecase/SetBindingUseCase.kt` | New | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/input/usecase/ResetBindingUseCase.kt` | New | ≤ 100 |
| `app_v2/src/main/res/layout/activity_keybinding_remap.xml` | New | ≤ 150 |
| `app_v2/src/main/res/layout/item_keybinding_row.xml` | New | ≤ 100 |
| `app_v2/src/main/res/layout/dialog_capture_keybinding.xml` | New | ≤ 80 |
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |
| `app_v2/src/main/AndroidManifest.xml` | Modified | — |

> `KeybindingRemapActivity.kt` must stay ≤ 500 LOC. Move business logic into `KeybindingRemapViewModel` and display helpers into `helpers/`.

---

## Steps

### Step 06.1 — Use-case layer for set / reset binding

**Files:** `domain/input/usecase/SetBindingUseCase.kt`, `domain/input/usecase/ResetBindingUseCase.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Thin wrappers around `InputBindingRepository`. Both are `@Inject`-constructor use cases:
>
> - `SetBindingUseCase(repo)` exposes `suspend operator fun invoke(commandId: CommandId, device: String, slot: Int, trigger: InputTrigger)`.
> - `ResetBindingUseCase(repo)` exposes `suspend operator fun invoke(commandId: CommandId)` (single-command reset) — delegates to `repo.clearOverride(commandId, device = <all>)`. Group and global reset live in Phase 07.
>
> No business logic beyond the forwarding call + a Timber.d("Set binding …" / "Reset binding …") log.

**Verification:**

- `Glob` — both files exist.
- `Grep "class SetBindingUseCase"` matches exactly once.
- `Grep "class ResetBindingUseCase"` matches exactly once.
- `Grep "Timber.d"` matches ≥ 1 in each file; `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x]` done

---

### Step 06.2 — KeybindingRemapViewModel

**Files:** `ui/keybinding/KeybindingRemapViewModel.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> `@HiltViewModel class KeybindingRemapViewModel @Inject constructor(private val repo: InputBindingRepository, private val setBinding: SetBindingUseCase, private val resetBinding: ResetBindingUseCase) : ViewModel()`.
>
> State exposed via `StateFlow<RemapUiState>`:
>
> ```kotlin
> data class RemapUiState(
>     val rows: List<KeybindingRow>,
>     val filter: String = "",
>     val expandedGroups: Set<CommandGroup> = CommandGroup.values().toSet(),
>     val pendingCapture: CaptureRequest? = null
> )
> data class KeybindingRow(
>     val commandId: CommandId,
>     val group: CommandGroup,
>     val labelKey: String,
>     val bindings: Map<String, List<InputTrigger>>,  // device -> triggers
>     val hasOverride: Boolean
> )
> data class CaptureRequest(val commandId: CommandId, val device: String, val slot: Int)
> ```
>
> Collects `repo.observeResolvedBindings()` + `DefaultsMapLoader.loadDefaults()` (or metadata thereof) in `init`. Exposes:
>
> - `fun onFilterChanged(query: String)`
> - `fun onGroupToggle(group: CommandGroup)`
> - `fun onRemapRequested(commandId, device, slot)` — sets `pendingCapture`.
> - `fun onCaptureCompleted(trigger: InputTrigger)` — calls `setBinding`; clears `pendingCapture`.
> - `fun onCaptureCancelled()` — clears `pendingCapture`.
> - `fun onResetRowRequested(commandId)` — calls `resetBinding`.
>
> Filtering matches either the row label (localised string) OR any trigger's human-readable label. Case-insensitive contains.

**Verification:**

- `Grep "class KeybindingRemapViewModel"` matches exactly once.
- `Grep -c "fun on"` returns ≥ 5 (at least 5 public intent handlers).
- `Grep "StateFlow<RemapUiState>"` matches exactly once.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x]` done

---

### Step 06.3 — Row label formatter helper

**Files:** `ui/keybinding/helpers/KeybindingRowLabelFormatter.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Translates `InputTrigger` → human-readable string. Rules (matches strategic §10 "unrecognised trigger display" resolution):
>
> - `Key(keyCode, modifiers)` → `"Ctrl+Shift+K"` using `KeyEvent.keyCodeToString(keyCode)` trimmed of the `KEYCODE_` prefix. If the keycode is unknown / unnamed, fall back to the convention from strategic §10 resolution (raw code vs. friendly fallback vs. both).
> - `GamepadButton(button)` → `"Gamepad A"` / `"Gamepad L1"` / etc. Map via a lookup table inside the helper; unknown → `"Gamepad [<int>]"`.
> - `MouseButton(button)` → `"Mouse Right"` / `"Mouse Back"` / etc.
> - `GamepadAxis(axis, direction, threshold)` → `"Left Stick ↑"` / `"Right Stick ↓"` with a trailing `"(>${threshold})"` suffix.
> - `VrEvent(type)` → `"VR: Pause"` — map the `Int` type via a lookup using the same semantic names from `XrInputEventType.kt`.
>
> All display strings flow through `resources.getString(...)` with `strings.xml` keys — never hardcoded. Each key added to all three `strings.xml` files.

**Verification:**

- `Grep "class KeybindingRowLabelFormatter"` matches exactly once.
- `Grep -c "resources.getString"` in the file returns ≥ 5.
- `Grep "keycode_unknown_raw\|keycode_unknown_label"` appears in all three `strings.xml` files (one key per style, exact key name derived from strategic §10 resolution).

**Status:** `[x]` done

---

### Step 06.4 — KeybindingRemapActivity + RecyclerView adapter

**Files:** `ui/keybinding/KeybindingRemapActivity.kt`, `ui/keybinding/KeybindingListAdapter.kt`, `res/layout/activity_keybinding_remap.xml`, `res/layout/item_keybinding_row.xml`, `AndroidManifest.xml`
**Depends on:** Steps 06.2, 06.3

**Prompt for developer:**

> 1. Register `<activity android:name=".ui.keybinding.KeybindingRemapActivity" />` in `AndroidManifest.xml` with the standard theme.
> 2. `activity_keybinding_remap.xml`: fullscreen layout with a top `SearchView`, a `RecyclerView` below, and a floating "Reset all" button at the bottom (Phase 07 wires this up — leave `android:onClick` pointing to a placeholder method that toasts "Not implemented in Phase 06").
> 3. `item_keybinding_row.xml`: row layout with command label, a device-category icon strip, trigger-label chips, a pencil "Remap" icon, and a reset icon.
> 4. `KeybindingListAdapter`: groups rows by `CommandGroup`; group headers are sticky and clickable (collapses the group). Row types: `HEADER` and `ROW`.
> 5. `KeybindingRemapActivity`: consume `viewModel.state`, bind to adapter, handle `SearchView` edits (call `viewModel.onFilterChanged`), handle adapter callbacks (row click → `onRemapRequested`; reset icon click → `onResetRowRequested`). Show `CaptureDialogFragment` when `state.pendingCapture != null`. Dismiss the dialog on `onCaptureCompleted` / `onCaptureCancelled`.
> 6. Activity line budget ≤ 500: if the event-plumbing helpers push the file over, extract them to `ui/keybinding/helpers/KeybindingRemapEventBinder.kt` per CLAUDE.md §Strict-Rules-3 (Activity logic prohibited).

**Verification:**

- `Glob` — all five files exist.
- `Grep "android:name=\".ui.keybinding.KeybindingRemapActivity\""` matches exactly once in `AndroidManifest.xml`.
- `Grep "class KeybindingRemapActivity"` matches exactly once.
- `wc -l` on `KeybindingRemapActivity.kt` returns ≤ 500.
- `Grep "viewModel.state"` matches ≥ 1 in activity (collected via `collectOnLifecycle` helper).
- `Grep -n "Log\.d\("` in all five files returns zero hits.

**Status:** `[x]` done

---

### Step 06.5 — CaptureDialogFragment (capture mode)

**Files:** `ui/keybinding/CaptureDialogFragment.kt`, `res/layout/dialog_capture_keybinding.xml`
**Depends on:** Step 06.4

**Prompt for developer:**

> Full-screen `DialogFragment` (per strategic §5.2). Listens to:
>
> - `View.setOnKeyListener` on the dialog's content view — handles keyboard capture.
> - `View.setOnGenericMotionListener` — handles gamepad buttons, gamepad axes, mouse buttons.
> - For VR: route through `VrControllerInputManager` temporarily via a dedicated "capture mode" flag on the manager (Phase 05 may need a small addendum — flag pausing of dispatch while capture is active). Implement behind `BuildConfig.SUPPORT_VR_PLAYER` guard so non-VR builds do not depend on `vr` sourceset.
>
> Capture rules per strategic §10 resolution:
>
> - **Modifier capture:** follow the policy decided in §10 (plain-keys-only default OR always-with-modifiers).
> - **Timeout:** if the strategic §10 resolution chose timed capture, start a `CountDownTimer` of the resolved duration; auto-dismiss on expiry.
> - **Analog threshold:** follow strategic §10 — either record the axis crossing the global deadzone, or render a slider for user to tune.
> - **Unknown trigger display:** use `KeybindingRowLabelFormatter` from Step 06.3 to render the current captured trigger live.
>
> On commit (positive button), call `setFragmentResult` with the serialized `InputTrigger`. The hosting activity forwards to `viewModel.onCaptureCompleted(trigger)`.

**Verification:**

- `Glob` — both files exist.
- `Grep "class CaptureDialogFragment"` matches exactly once.
- `Grep "setOnKeyListener"` matches ≥ 1.
- `Grep "setOnGenericMotionListener"` matches ≥ 1.
- `Grep "BuildConfig.SUPPORT_VR_PLAYER"` matches ≥ 1 (VR guard).
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x]` done

---

### Step 06.6 — Settings entry

**Files:** `ui/settings/SettingsActivity.kt`, `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 06.4

**Prompt for developer:**

> 1. Add string key `settings_controls_keybindings_title` to all three `strings.xml` files — EN "Controls & Keybindings", RU "Управление и клавиши", UK "Керування та клавіші". Apply Author Style: `..` not `...` in any accompanying explanatory string.
> 2. Add one-tap entry in `SettingsActivity` that launches `KeybindingRemapActivity`. Entry placement: top level, **not** nested — per strategic §5.1 "one tap deep". Follow existing settings entry conventions (likely a `Preference` row).

**Verification:**

- `Grep "settings_controls_keybindings_title" app_v2/src/main/res/values/strings.xml` matches exactly once.
- Same key matches exactly once in `values-ru/strings.xml` and `values-uk/strings.xml` (trilingual invariant).
- `Grep "KeybindingRemapActivity" app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt` matches ≥ 1 (entry launches the activity).
- `Grep "\\.\\.\\."` in `values/strings.xml` — if the added string uses ellipsis, must be `..` per CLAUDE.md Author Style.

**Status:** `[x]` done

---

### Step 06.7 — Smoke test on device

**Files:** none (checklist)
**Depends on:** Step 06.6

**Prompt for developer:**

> Build `standardDebug` via `/build` skill. Install and exercise:
>
> 1. Open Settings → "Controls & Keybindings" — fullscreen list appears.
> 2. Scroll groups — each collapses/expands; search "pause" filters to playback rows.
> 3. Tap "Remap" on Pause/Play → capture dialog opens; press `P` → dialog closes; row now shows `P` under keyboard column.
> 4. Exit settings; open player; confirm `P` now pauses (override is live without restart — hot-reload invariant from strategic §11).
> 5. Tap per-row reset on that row → `Space` is restored.
> 6. Change device language to Russian — labels are translated.
>
> If any step fails, flip phase to `⛔ Blocked` and file a Blockers Log entry in `INDEX.md`.

**Verification:**

- Six checks above each noted in developer log / commit message.
- No `⛔ Blocked` entry opens for Phase 06.

**Status:** `[manual — deferred to human]`

---

## Phase Done Criteria

- [ ] Every `Step 06.*` is `[x] done`.
- [ ] `/build` reports green for all non-VR flavors plus `vrDebug`.
- [ ] Trilingual parity: every new string key exists in `values/`, `values-ru/`, `values-uk/`; `Grep -c "<string name=\"settings_controls_keybindings_title\""` returns exactly 1 in each of the three files.
- [ ] `KeybindingRemapActivity.kt` ≤ 500 LOC.
- [ ] `Grep -n "Log\.d\("` returns zero hits across all "Files Touched".
- [ ] Grep for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entries added for every "Files Touched" file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated — new classes have `role` + `status` set.

---

## Handoff Notes to Next Phase

- The "Reset all" button from Step 06.4 is a placeholder. Phase 07 wires it up and adds the destructive-confirmation dialog.
- Per-group headers need a "Reset this group" affordance — Phase 07 adds the icon + dialog.
- Capture mode does not currently check for conflicts (two commands bound to the same trigger) — Phase 07 adds the conflict visualiser and decides commit/cancel semantics per strategic §10.
- The undo-snackbar (strategic §10 "undo window") is deferred to Phase 07 if the resolution chose "time-limited"; skipped entirely if "immediate-commit".

---

## Rollback Plan

- Revert the phase commits. The manifest entry, strings, activities are additive — nothing in the existing app is modified structurally beyond the one new settings row.
- If the new activity crashes post-merge: comment out the `SettingsActivity` entry as a hotfix; full revert in the next release.
