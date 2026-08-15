# Phase 05 - Player focus navigation (D-pad moves the frame)

**Strategic spec:** [`../S0819_tv-dpad-focus-visibility.md`](../S0819_tv-dpad-focus-visibility.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (spec -> BlockNeedUserTest)
**Depends on:** Phase 01, 02
**Blocks:** -
**Steps done:** 5 / 5
**Started:** 2026-07-01
**Completed:** 2026-07-01

---

## Objective

In the player, make D-pad direction keys move Android focus (and thus the travelling frame) between visible transport controls instead of driving transport directly; transport stays reachable via the on-screen buttons. Fix the initial-focus target and keep controls visible while navigating.

> **Highest-risk phase.** Ends in `BlockNeedUserTest` - requires on-device D-pad verification. Owner decision (2026-07-01): arrows move the frame between player controls.

---

## Prerequisites

- [x] Phase 02 ✅ Done (frame renders on real focus).
- [x] Confirm exact paths at start: `PlayerActivity.kt`, `helpers/PlayerKeyboardHandler.kt`, `helpers/PlayerUiStateCoordinator.kt`, `assets/input/default_bindings.json`, `res/layout/activity_player_unified.xml`, `res/layout/custom_player_controls.xml`, `res/layout/custom_player_controls_large.xml`, and their `res/layout-land/` counterparts.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/assets/input/default_bindings.json` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerKeyboardHandler.kt` | Modified | ≤ 369 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1262 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerUiStateCoordinator.kt` | Modified | ≤ 500 |
| `app_v2/src/main/res/layout/custom_player_controls.xml` | Modified | n/a |
| `app_v2/src/main/res/layout/custom_player_controls_large.xml` | Modified | n/a |
| `app_v2/src/main/res/layout-land/custom_player_controls.xml` (if present) | Modified | n/a |
| `app_v2/src/main/res/layout-land/custom_player_controls_large.xml` (if present) | Modified | n/a |

> **Landscape parity (Rule 11):** before editing any `res/layout/*.xml` above, `Glob` its `res/layout-land/` counterpart and edit both. If a counterpart is absent, note "landscape variant absent - not needed" in the step evidence.

---

## Steps

### Step 05.1 - Free D-pad direction keys for focus in the player

**Files:** `assets/input/default_bindings.json`, `helpers/PlayerKeyboardHandler.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Goal: a bare D-pad direction press (no modifier) in the player, while controls are visible and the device is in non-touch mode, must fall through to Android focus traversal, not be consumed as transport. In `default_bindings.json`, remove the `KEYCODE_DPAD_LEFT/RIGHT/UP/DOWN` triggers from `navigation.previous_file`, `navigation.next_file`, `audio.volume_up`, `audio.volume_down` (keep those commands and any non-D-pad triggers such as media/prev-next keys; the on-screen buttons remain the transport path). In `PlayerKeyboardHandler`, ensure that when a bare `KEYCODE_DPAD_*` direction resolves to no command, the handler returns `false` so the platform performs focus movement. Do NOT change OK/Center (`KEYCODE_DPAD_CENTER`/`ENTER`) - it must still activate the focused control. Persisted user-custom bindings that explicitly map D-pad to transport keep winning (opt-in) - acceptable.

**Verification:**

- `Grep` - no `KEYCODE_DPAD_LEFT` / `KEYCODE_DPAD_RIGHT` / `KEYCODE_DPAD_UP` / `KEYCODE_DPAD_DOWN` under `navigation.previous_file`/`next_file`/`audio.volume_*` blocks in `default_bindings.json`.
- `Grep` - `PlayerKeyboardHandler` returns `false` for an unresolved bare DPAD direction (no consume).
- Project compiles.

**Status:** `[x]` done

**Step Log (2026-07-01):**
- `default_bindings.json`: removed bare D-pad keyboard triggers only (`InputTrigger` encodes `key:<code>:<modifiers>`, bare press => modifiers 0):
  - `navigation.next_file`: `["key:22:0", "key:93:0", "key:166:0"]` -> `["key:93:0", "key:166:0"]` (dropped DPAD_RIGHT).
  - `navigation.previous_file`: `["key:21:0", "key:92:0", "key:167:0"]` -> `["key:92:0", "key:167:0"]` (dropped DPAD_LEFT).
  - `audio.volume_up`: `["key:19:0"]` -> `[]` (dropped DPAD_UP; `gamepad_axis` trigger kept).
  - `audio.volume_down`: `["key:20:0"]` -> `[]` (dropped DPAD_DOWN; `gamepad_axis` trigger kept).
- Shift+D-pad seek bindings untouched (`navigation.seek_forward_30s`=`key:22:1`, `seek_backward_30s`=`key:21:1`) - modifiers 1, not bare.
- `PlayerKeyboardHandler.handleKeyDown` (`:169-182`) already returns `false` on a resolver miss for a non-`KEYCODE_UNKNOWN` key; scan-code fallback maps only PAGE/HOME/END, never D-pad. No consume logic added (verify-only, per prompt).
- Verify: `python json.load` OK; four commands hold no `key:19:0/20:0/21:0/22:0`. PASS.

---

### Step 05.2 - Make player transport controls focusable with a visible focus stroke

**Files:** `res/layout/custom_player_controls.xml`, `res/layout/custom_player_controls_large.xml` (+ `layout-land/` counterparts if present)
**Depends on:** Step 05.1

**Prompt for developer:**

> On each transport button in both control layouts, set `android:focusable="true"`, `android:focusableInTouchMode="false"`, and `android:foreground="@drawable/focus_button_background"` (the existing S0289 stroke selector - parity with `playbackButtonRow`). Add explicit `android:nextFocusLeft` / `nextFocusRight` (and up/down where a second row exists) so traversal order is deterministic; bring `custom_player_controls_large.xml` to parity with the small variant (researcher flagged it missing `nextFocus*`). No hardcoded hex - reuse `?attr/`/`@color/`/`@drawable/` only. Edit the `layout-land/` counterpart identically if it exists.

**Verification:**

- `Grep` - `@drawable/focus_button_background` present in both `custom_player_controls.xml` and `custom_player_controls_large.xml`.
- `Grep` - `nextFocusLeft` / `nextFocusRight` present in `custom_player_controls_large.xml`.
- `Grep -n "#[0-9a-fA-F]\{6,8\}"` - zero raw-hex hits in the edited layout files.
- Landscape counterpart edited or explicitly noted absent.

**Status:** `[x]` done

**Step Log (2026-07-01):**
- Both `custom_player_controls.xml` and `custom_player_controls_large.xml` are the ExoPlayer PlayerView controller layout (`app:controller_layout_id="?attr/customPlayerControlsLayout"`); single `exoPlayerButtonRow` (one row, no second row -> no up/down nextFocus needed).
- On the 7 visible transport buttons (`exo_repeat`, `exo_prev_file`, `btnRewind10`, `exo_play_pause`, `btnForward30`, `exo_next_file`, `btnPlaybackControl`) added `focusable=true`, `focusableInTouchMode=false`, `foreground=@drawable/focus_button_background`, and a deterministic single-row wrap-around `nextFocusLeft`/`nextFocusRight` chain (`btnPlaybackControl` <-> `exo_repeat`). `btnPictureInPicture` stays `gone`, excluded from the chain.
- Large variant brought to parity (previously had zero `nextFocus*`).
- Landscape: no `res/layout-land/custom_player_controls*.xml` exists (Glob = only the two `res/layout/` files) -> **landscape variant absent - not needed** (Rule 11).
- Verify: `focus_button_background` x7 each; `nextFocusLeft|Right` = 14 in large; `focusable/focusableInTouchMode` x7 each; raw-hex = NONE; both XML well-formed. PASS.

---

### Step 05.3 - Keep controls visible while navigating in non-touch mode

**Files:** `ui/player/helpers/PlayerUiStateCoordinator.kt`, `ui/player/PlayerActivity.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> When the device is in non-touch mode (D-pad/gamepad), the controls overlay must stay visible while the user navigates (otherwise the frame has nothing to land on). Reset/suspend the controls auto-hide timer on focus movement among player controls, or force controls visible while `!isInTouchMode`. Do not change touch-mode behavior (auto-hide stays as-is for touch users).

**Verification:**

- `Grep` - a non-touch guard (`isInTouchMode` / equivalent) gates the auto-hide reset in the player UI-state path.
- Project compiles.

**Status:** `[x]` done

**Step Log (2026-07-01):**
- Gated at the single auto-hide sink `PlayerActivity.scheduleHideControls()` (`:728`): after clearing any pending `hideControlsRunnable`, `if (!binding.root.isInTouchMode) return` - so the ~35 callers (`PlayerControlsSetupManager`, `PlayerNavigationManager`, `SearchControlsManager`, etc.) all inherit the non-touch keep-visible behaviour with one edit. Touch mode is unchanged (auto-hide still schedules).
- `PlayerUiStateCoordinator` visibility line (`shouldShowControls`) left untouched: it derives from `state.showControls`, and blocking the hide there keeps `showControls` true in non-touch so the overlay stays rendered.
- Chose the sink over per-caller guards to avoid missing any of the many `scheduleHideControls()` sites (lower risk).
- Verify: `Grep` -> `!binding.root.isInTouchMode` guard present at `:733`. PASS. (Compile via central build.)

---

### Step 05.4 - Fix the initial-focus target

**Files:** `ui/player/PlayerActivity.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> `getInitialFocusView()` currently returns `btnPlayPause`, which lives in a `gone`-by-default overlay, so initial focus is often a no-op. On non-touch open, ensure the controls overlay is visible and return an always-visible focusable transport control as the initial focus target (mirror the `StandalonePlayerActivity.getInitialFocusView()` fallback pattern). The frame must appear on a real control the moment the player opens with a remote.

**Verification:**

- `Grep` - `getInitialFocusView` returns a control that is visible on open (not the `gone` overlay child), with a non-touch visibility guard.
- Project compiles.

**Status:** `[x]` done

**Step Log (2026-07-01):**
- `getInitialFocusView()` still returns `binding.btnPlayPause`, but on a non-touch open (`!binding.root.isInTouchMode`) and non-touch-zone media (`!useTouchZones`, i.e. video/audio/docs) it first makes the overlay reachable: `if (!showControls) viewModel.toggleControls()` (aligns `PlayerState` so the following `updateUI` keeps it and S0819's auto-hide guard leaves it up) and `binding.controlsOverlay.isVisible = true` (synchronous, so the immediate `requestFocus()` in `BaseActivity` at `:172` finds a visible/focusable target on the first pass).
- Image/touch-zone media path unchanged: the overlay is intentionally suppressed there (`getUseTouchZones()` true -> `shouldShowControls` false), so we do not force-show it - no regression, the phase does not require fixing image focus.
- `getInitialFocusView()` is only ever called from the non-touch branch (`shouldRequestInitialFocus()` == `isNonTouchInputActive()`), so touch users never hit this side effect. `androidx.core.view.isVisible` already imported (`:20`).
- Differs from `StandalonePlayerActivity` (returns always-visible `btnBack` in a shown `topCommandPanel`): here both `topCommandPanel` and `controlsOverlay` are gone-by-default, so the overlay is made visible rather than picking a different anchor.
- Verify: `Grep` -> guard `!binding.root.isInTouchMode && !useTouchZones` before `controlsOverlay.isVisible = true`; returns `btnPlayPause`. PASS. (Compile via central build.)

---

### Step 05.5 - Insert on-device verification probes (S0819)

**Files:** `core/ui/focus/FocusFrameController.kt`, `ui/player/helpers/PlayerKeyboardHandler.kt`
**Depends on:** Step 05.4

**Prompt for developer:**

> This spec is about to enter `BlockNeedUserTest`. Insert exactly one `Timber.d("S0819: <desc>")` probe at each changed flow entry: (a) in `FocusFrameController` where the frame moves to a new focused view (confirms tracking), (b) in `PlayerKeyboardHandler` where a bare DPAD direction now falls through to focus traversal (confirms the player rebinding). One tag per flow entry, not per line. These are temporary - removed by `/spec-check` when the spec leaves `BlockNeedUserTest`. Never use the `S0819:` prefix in any permanent `Timber.i/w/e`.

**Verification:**

- `Grep` - exactly two `Timber.d("S0819:` lines across the two files.
- Project compiles (this is the final phase build - validates code + probes in one pass).

**Status:** `[x]` done

**Step Log (2026-07-01):**
- Probe (a) `FocusFrameController.onFocusChanged` (`:82`): `Timber.d("S0819: frame moved to focused view %s", newFocus.javaClass.simpleName)` - in the branch that calls `moveToView` (an actual focus change), NOT in `moveToView` itself (that also runs from the per-pre-draw re-sync -> would spam every frame). One tag per flow entry. Added `import timber.log.Timber`.
- Probe (b) `PlayerKeyboardHandler.handleKeyDown` (`:185`): `Timber.d("S0819: player DPAD %d fell through to focus traversal", keyCode)` - guarded by a new `isBareDpadDirection(keyCode)` helper so it fires only for the four D-pad directions right before the final `return false` (not for every unrelated unresolved key). `Timber` already imported.
- Verify: exactly two `Timber.d("S0819:` (1 per file); zero `S0819` in any permanent `Timber.i/w/e`; none elsewhere in `src/main`; both probe lines <=120 (92 / 86). PASS. (Compile via central build.)

---

## Phase Done Criteria

- [x] Every `Step 05.*` is `[x] done`.
- [~] Project compiles - `/build` standard debug (includes the S0819 probes). **Deferred to central build** (developer builds centrally; per-file gates green: detekt PASS [scoped], neuroslop PASS, ticket-log PASS on all 3 `.kt`).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Landscape counterparts edited or explicitly noted absent for every touched layout (no `res/layout-land/custom_player_controls*.xml` exists -> absent, not needed).
- [x] Dev log entry added (one per logical change via `post-change.ps1`).

**Closure notes (2026-07-01):**
- Spec transitioned `In Progress -> BlockNeedUserTest` (`update.ps1`) with a device-test `-StatusNote` - required so the two S0819 probes satisfy the CLAUDE.md §2 invariant and clear the `ticket-log-audit` gate.
- Detekt-clean-first: the `-ScopeToFile` gate initially failed on a pre-existing `SpacingBetweenDeclarationsWithAnnotations` finding at `PlayerActivity.kt` `@Inject castControllerFactory` (parallel-ticket WIP not in the detekt baseline, surfaced because the wear detekt.xml parse failure degraded scope from diff-lines to whole-file). Resolved with a one-line blank-line separator (Rule 7 lint hygiene in a file already owned by this phase); did not adopt the parallel feature. Re-run: detekt PASS [scoped].
- `listener-symmetry +2` gate is advisory-SKIP and not attributed to this change - verified: none of the 3 touched files register a listener/observer/receiver.

---

## Handoff Notes to Next Phase

- Player D-pad now moves the frame between controls; transport via on-screen buttons + OK. Needs on-device confirmation (frame visible + moves + transport still reachable + not shown in touch).
- After this phase the spec goes to `BlockNeedUserTest`; Phase 06 runs the docs/catalog cleanup and the capability record.

---

## Rollback Plan

Revert the phase commit - restores D-pad-as-transport bindings and the prior initial-focus target. The app-wide overlay (Phases 01-03) keeps working on every other screen; only player navigation reverts.
