# Specification: PLAYER-KEYBINDING — Phase 1: Preparation & Inventory

**Status:** Draft (Tactical — Phase 1 of 7)
**Date:** 2026-04-24
**Tier:** 3 — Moderate (medium risk)
**Parent spec:** [PLAN/spec_player-keybinding-remapping.md](PLAN/spec_player-keybinding-remapping.md) (strategic)

> **Scope of this phase:** research, audit and cataloguing only. **No production code changes.** No new classes, no DB schema, no Defaults Map File. The only output is a set of machine-readable and human-readable artefacts that Phase 2 consumes as input. Any activity outside the deliverables below is out of scope.

---

## 0. Phase Map (the whole 7-phase plan, so reviewers see the trajectory)

| # | Phase | Outcome | Spec |
|---|-------|---------|------|
| **1** | **Preparation & Inventory** (this spec) | Verified engine inventory, action catalogue, trigger catalogue, refactor order | current |
| 2 | Foundation | `KeyBindingManager` core, unified `CommandId` model, Defaults Map File asset, persistence layer, binding-resolution cache | future |
| 3 | Keyboard migration | `KeyboardShortcutHandler` + `PlayerKeyboardHandler` + `KeyboardNavigationHandler` + `DialogKeyboardDelegate` consume `KeyBindingManager` instead of inline `when` trees | future |
| 4 | Gamepad + mouse + media-button migration | `GamepadInputManager`, `MouseEventHandler`, `MediaButtonRestartReceiver`, `AudioPlaybackService.MediaSession.Callback` consume the manager | future |
| 5 | VR migration | `VrControllerInputManager` consumes the manager; OpenXR `XrInputEventType` mapping moves out of C++ edge-detection into the data layer | future |
| 6 | Remapping UI | Fullscreen settings screen, capture mode, grouped list, per-row / per-group / global reset | future |
| 7 | Hierarchical reset + conflict policy + polish | Destructive-confirmation dialog, conflict visualiser, string resources EN/RU/UK, FEATURES doc entries | future |

Touch-gesture remapping (Phase "5b") is explicitly deferred — see section 10.

---

## 1. Goals of Phase 1

The following six artefacts are the **only** Phase 1 deliverables:

1. **Engine Inventory** (§4) — every class that currently listens to hardware input and converts it to an app-level action.
2. **Command-Model Inventory** (§5) — every existing enum/sealed-class that already abstracts a "logical action", so Phase 2 can unify them into a single `CommandId` namespace instead of four.
3. **Trigger Catalogue** (§6) — every hardcoded `KEYCODE_*` / `BUTTON_*` / `AXIS_*` / `XrInputEventType.*` currently live, with the action it fires on each surface.
4. **Candidate Additions** (§7) — commands the codebase can execute but that have no hardware binding today, ranked by expected user value.
5. **Refactor Order** (§8) — engine-by-engine table with priority and rationale, which is the direct input to Phases 3–5.
6. **Cross-cutting Concerns** (§9) — debouncing, rate-limiting, deadzones, surface profiles, thread hops, view-capability gating — things that must be preserved across the refactor and that would silently regress if forgotten.

**Non-goals of Phase 1:**

- No code edits in `app_v2/`.
- No Defaults Map File draft.
- No DB schema.
- No UI mockups.
- No decisions on tactical UX (those belong in the strategic spec's §10 Ambiguity Gate and must be answered before Phase 6 starts, not Phase 1).

---

## 2. Why This Phase Exists (risk framing)

The app has grown multiple parallel input pipelines over time. A naive "introduce `KeyBindingManager`" refactor without this phase would collide with at least three concrete hazards:

1. **Silent coverage loss.** There are more engines than a single developer remembers. Missing even one (e.g. `MediaButtonRestartReceiver` or `DialogKeyboardDelegate`) leaves a corner of the app immune to remapping and inconsistent after the feature ships.
2. **Duplicate-command ambiguity.** The same user-visible action is implemented against different internal command models — `PlaybackCommand.TogglePausePlay` (player/VR) versus `InputAction.PlayPause` (UI) versus the gamepad's own dispatch. Without a canonical mapping between these, Phase 2 cannot define stable `actionId` strings.
3. **Hidden behavioural contracts.** Specific engines do non-obvious things that are required but not documented in the code itself: media-button debouncing windows, gamepad rate limiting at 100–120 ms, analog deadzones, left-stick Y-axis inversion. A migration that ignores these regresses the hot path. They must be listed explicitly before refactor.

---

## 3. Method & Verification Rules

Phase 1 work MUST follow these rules so Phase 2 can trust the artefacts:

1. **Every engine entry cites a verified file path.** If the path does not resolve via `ls`, it is removed from the artefact.
2. **Line numbers are advisory, not authoritative.** Numbers given here are snapshots; Phase 2 MUST re-grep before refactoring (the codebase is actively evolving — see recent "Wave" decompositions in the git log).
3. **"Hardcoded" means a literal `KeyEvent.KEYCODE_*` / `MotionEvent.AXIS_*` / `KeyEvent.BUTTON_*` constant appearing inside a `when`/`if` branch or an `== ` comparison.** Symbolic constants (e.g. `DEADZONE`, `SEEK_RATE_MS`) are recorded separately as "Cross-cutting Concerns" (§9), not as triggers (§6).
4. **Every command entry cites the sealed/enum declaration file.** No command string is invented.
5. **The Trigger Catalogue (§6) is the ground truth for the Defaults Map File produced in Phase 2.** Anything missing here will be missing in defaults.

---

## 4. Deliverable A — Engine Inventory

Seventeen engines are in scope. All paths are relative to `app_v2/src/{sourceset}/java/com/sza/fastmediasorter/`.

### 4.1 Keyboard engines (main source set)

| # | Engine | Path | Entry point | Host(s) | Refactor priority |
|---|--------|------|------------|---------|:-----------------:|
| K1 | `KeyboardShortcutHandler` | `util/KeyboardShortcutHandler.kt` | `handleKeyEvent(keyCode, event)` | All activities (shared semantic parser) | **Critical** |
| K2 | `PlayerKeyboardHandler` | `ui/player/helpers/PlayerKeyboardHandler.kt` | `handleKeyDown(keyCode, event)`, `handlePointerEvent()` | `PlayerActivity`, `StandalonePlayerActivity` | **High** |
| K3 | `KeyboardNavigationHandler` | `ui/main/helpers/KeyboardNavigationHandler.kt` | `handleKeyDown(keyCode, event)` | `MainActivity` | Medium |
| K4 | `DialogKeyboardDelegate` | `ui/dialog/DialogKeyboardDelegate.kt` | wires `Dialog.setOnKeyListener` | All `DialogFragment` / `Dialog` | Medium |

### 4.2 Gamepad / joystick (main source set)

| # | Engine | Path | Entry point | Host(s) | Refactor priority |
|---|--------|------|------------|---------|:-----------------:|
| G1 | `GamepadInputManager` | `core/input/GamepadInputManager.kt` | `handleKeyEvent(event, surface)`, `handleMotionEvent(event, surface)` | `PlayerActivity`, `MainActivity`, `BrowseActivity` | **Critical** |

### 4.3 Mouse / pointer (main source set)

| # | Engine | Path | Entry point | Host(s) | Refactor priority |
|---|--------|------|------------|---------|:-----------------:|
| M1 | `MouseEventHandler` | `ui/common/MouseEventHandler.kt` | `handleMotionEvent(view, event)`, `handleGenericMotionEvent(view, event)` | `PlayerActivity`, `BrowseActivity`, `MainActivity`, adapter item listeners | High |

### 4.4 Touch / gesture (main source set — **deferred**, see §10)

| # | Engine | Path | Entry point | Host(s) | Refactor priority |
|---|--------|------|------------|---------|:-----------------:|
| T1 | `PlayerGestureManager` | `ui/player/helpers/PlayerGestureManager.kt` | `onTouchEvent(event)` | `PlayerActivity` | Deferred |
| T2 | `VideoTouchDelegate` | `ui/player/helpers/VideoTouchDelegate.kt` | `onTouchEvent(event)` | `PlayerActivity` (video) | Deferred |
| T3 | `StandaloneVideoTouchDelegate` | `ui/player/helpers/StandaloneVideoTouchDelegate.kt` | `onTouchEvent(event)` | `StandalonePlayerActivity` | Deferred |
| T4 | `TouchZoneGestureManager` | `ui/player/helpers/TouchZoneGestureManager.kt` | `onTouchEvent(event)` | `PlayerActivity` (images/docs) | Deferred |
| T5 | `EpubViewerManager` | `ui/player/helpers/EpubViewerManager.kt` | `onTouchEvent()`, `onGenericMotionEvent()` | `PlayerActivity` (EPUB) | Deferred |
| T6 | `TextViewerManager` | `ui/player/helpers/TextViewerManager.kt` | `onTouchEvent()`, `onGenericMotionEvent()` | `PlayerActivity` (text) | Deferred |
| T7 | `PdfViewerManager` | `ui/player/helpers/PdfViewerManager.kt` | `onTouchEvent()`, `onGenericMotionEvent()` | `PlayerActivity` (PDF) | Deferred |
| T8 | `VerticalSeekBar` | `ui/player/VerticalSeekBar.kt` | `onTouchEvent()` override | `PlayerActivity` command panel | Deferred |

Rationale for deferring touch: gestures (pinch, swipe, zone-tap) are already *user-configurable through context*, not through a key map; unifying them requires a separate data model (`GesturePattern` with direction + zone + velocity thresholds) which the strategic spec explicitly lists as out of scope for v1. Keep the existing delegates untouched until a follow-up spec activates "5b".

### 4.5 Media button / remote (main source set)

| # | Engine | Path | Entry point | Host(s) | Refactor priority |
|---|--------|------|------------|---------|:-----------------:|
| R1 | `MediaButtonRestartReceiver` | `ui/player/MediaButtonRestartReceiver.kt` | `onReceive(context, intent)` BroadcastReceiver | System-wide (manifest-registered) | Medium |
| R2 | `AudioPlaybackService` (MediaSession callback) | `ui/player/AudioPlaybackService.kt` | `MediaSession.Callback` (Media3) | Background audio service | Medium |

### 4.6 VR (vr source set, `app_v2/src/vr/…`)

| # | Engine | Path | Entry point | Host(s) | Refactor priority |
|---|--------|------|------------|---------|:-----------------:|
| V1 | `VrControllerInputManager` | `vr/helpers/VrControllerInputManager.kt` | `onInputEvent(type, hand, value, source)` from `XrInputCallback`; also `onKeyEvent`, `onMotionEvent` for BT keyboard/mouse passthrough in VR | `VrPlayerActivity` | **Critical** |

**Important VR note:** the C++ layer (OpenXR bridge, `vr/openxr/OpenXrNative.kt` + `vr/cpp/`) performs *edge-detection* on controller buttons before they reach Kotlin. After Phase 5 the C++ side must still emit its numeric `XrInputEventType` codes; the **mapping from those codes to a `CommandId` must move from `VrControllerInputManager`'s `when` block into the data layer**. The C++ side is out of scope for all phases — do not touch it.

### 4.7 Focus / spatial navigation (not an input engine, but related)

| # | Component | Path | Role |
|---|-----------|------|------|
| F1 | `FocusManager` | `ui/common/FocusManager.kt` | Translates `InputAction.MoveFocus(direction)` into actual `View.requestFocus()` calls. Not a refactor target — it consumes actions, it does not map keys. |

**Active dispatchers in scope: 8** (K1–K4, G1, M1, R2, V1). Plus 1 receiver filter (R1) that delegates to the dispatchers via intent restart — total 9 entries.
**Touch engines deferred: 8** (T1–T8).

---

## 5. Deliverable B — Command-Model Inventory

There are **four parallel command models** in the codebase today. Phase 2's first design decision is how to reconcile them. Phase 1 only enumerates what exists.

| Model | Declaration file | Kind | Consumers | Granularity |
|-------|-----------------|------|-----------|:-----------:|
| `InputAction` | `ui/common/input/InputAction.kt` | `sealed interface` | `KeyboardShortcutHandler`, `KeyboardNavigationHandler`, `DialogKeyboardDelegate`, `FocusManager`, `MouseEventHandler` | UI-wide |
| `GamepadAction` | `domain/model/GamepadAction.kt` | `sealed class` (nested `PlayerAction`, `BrowserAction`) | `GamepadInputManager` | Gamepad-only |
| `PlaybackCommand` | `ui/player/contracts/PlaybackCommandModel.kt` | `sealed class` | `PlayerActivity`, `VrControllerInputManager`, VR `PlaybackCommand*CommandOverride` classes | Player/VR |
| `XrInputEventType` | `vr/openxr/XrInputEventType.kt` | `Int` constants (shared with C++) | `VrControllerInputManager` | OpenXR native |

### 5.1 Existing surface profile

Surface filtering already exists and should be preserved:

| Enum | Declaration | Values (abridged) |
|------|-------------|------------------|
| `InputSurface` | `ui/common/input/InputSurface.kt` | `MAIN`, `BROWSE`, `PLAYER`, `VR_PLAYER`, `SETTINGS`, `ADD_RESOURCE`, `CLOUD_PICKER`, `DUPLICATES`, `RESOURCE_EDITOR`, `RECEIVE_SHARE`, `WIDGET_CONFIG`, `WELCOME`, `DIALOG` |
| `FocusDirection` | declared inline in `ui/common/input/InputAction.kt` | `UP`, `DOWN`, `LEFT`, `RIGHT`, `FIRST`, `LAST`, `NEXT`, `PREVIOUS` |

**Phase 1 recommendation for Phase 2 (non-binding):** introduce a single `CommandId: String` namespace (e.g. `"playback.pause_play"`, `"browse.delete"`, `"vr.recenter"`). Each of the four existing models maps to a subset of that namespace via thin adapters. This keeps the existing dispatch sites working unchanged during migration and lets each engine migrate independently. The concrete proposal is Phase 2's job; this note is only to avoid blocking the reader from imagining the endpoint.

---

## 6. Deliverable C — Trigger Catalogue

The complete list of hardcoded input triggers live in the codebase today, grouped by engine. This table becomes the raw source for the Defaults Map File in Phase 2.

### 6.1 KeyboardShortcutHandler (K1) — global semantic parser

| Trigger (with modifiers) | Surface(s) | Resolves to `InputAction` |
|---|---|---|
| `F1` | any | `ShowHelp` |
| `F2` / `Ctrl+R` | MAIN, BROWSE, DUPLICATES, PLAYER | `RenameSelection` |
| `F3` / `Ctrl+Q` | BROWSE, DUPLICATES, PLAYER | `ViewCurrent` / `ShowInfo` |
| `F4` / `Ctrl+E` | BROWSE, DUPLICATES, PLAYER | `EditCurrent` |
| `F5` | MAIN | `CopySelection` |
| `F5` / `Ctrl+F5` / `Ctrl+Shift+R` | BROWSE, CLOUD_PICKER | `RefreshCurrent` |
| `F6` / `Ctrl+X` | BROWSE, DUPLICATES, PLAYER | `MoveSelection` |
| `F7` / `Ctrl+Shift+N` / `Ctrl+G` | BROWSE | `CreateFolder` |
| `F8` / `Del` / `Ctrl+D` | BROWSE, DUPLICATES, PLAYER | `DeleteSelection` |
| `F9` / `Ctrl+M` | any | `ShowContextMenu` |
| `F10` / `Alt+F4` / `Escape` | any | `ExitSurface` |
| `Tab` / `Shift+Tab` | any | `MoveFocus(NEXT / PREVIOUS)` |
| `Arrow keys` | MAIN, BROWSE, SETTINGS, DIALOG | `MoveFocus(UP/DOWN/LEFT/RIGHT)` |
| `PageUp` / `PageDown` | MAIN, BROWSE | `PageJump(±N)` |
| `Home` / `End` | MAIN, BROWSE, PLAYER | `MoveFocus(FIRST/LAST)` |
| `Enter` / `DPAD_CENTER` | MAIN, BROWSE | `OpenCurrent` |
| `Backspace` / `Del` | BROWSE | `BackOneLevel` |
| `Space` | BROWSE, DUPLICATES | `ToggleSelection` |
| `Space` / `Enter` / `DPAD_CENTER` | PLAYER | `PlayPause` |
| `Ctrl+A` | BROWSE, DUPLICATES | `SelectAll` |
| `NumPad +` / `-` / `*` | BROWSE, DUPLICATES | `SelectAll` / `ClearSelection` / `InvertSelection` |
| `Shift+Arrow` | BROWSE, DUPLICATES | `RangeExtendUp/Down` |
| `Ctrl+C` | BROWSE, DUPLICATES | `CopySelection` |
| `Ctrl+V` | BROWSE | `PasteClipboard` (not yet executed — see §7) |
| `Ctrl+S` | PLAYER, RESOURCE_EDITOR | `SaveCurrent` |
| `Ctrl+I` | PLAYER | `ShowInfo` |
| `Ctrl+B` | PLAYER, MAIN | `ToggleFavourite` |
| `Ctrl+F` | any | `SearchRequested` |
| `Ctrl+Z` / `Ctrl+Y` | any | `UndoRequested` / `RedoRequested` |
| `Ctrl+P` | PLAYER | `ShowPlaybackControls` |
| `M` | PLAYER | `ToggleMute` |
| `F` | PLAYER | `ToggleFullscreen` |
| `[` / `]` | PLAYER | `SeekBy(-N / +N)` |
| `,` / `.` | PLAYER | `FrameStep(false / true)` |
| `PROG_RED` | BROWSE, DUPLICATES | `DeleteSelection` |
| `PROG_GREEN` | BROWSE | `CopySelection` |
| `PROG_YELLOW` | BROWSE | `MoveSelection` |
| `PROG_BLUE` | BROWSE | `RenameSelection` |
| `MEDIA_PLAY_PAUSE` / `HEADSETHOOK` | PLAYER | `PlayPause` |
| `MEDIA_NEXT` / `MEDIA_SKIP_FORWARD` | PLAYER | `NextTrack` |
| `MEDIA_PREVIOUS` / `MEDIA_SKIP_BACKWARD` | PLAYER | `PreviousTrack` |
| `MEDIA_FAST_FORWARD` / `MEDIA_REWIND` | PLAYER | `SeekBy(±N)` |
| `CHANNEL_UP` / `CHANNEL_DOWN` | PLAYER | `NextTrack` / `PreviousTrack` |
| `BOOKMARK` | PLAYER | `ToggleFavourite` |

### 6.2 PlayerKeyboardHandler (K2) — legacy raw-key path (coexists with K1)

Overlaps with K1 for `PLAYER` surface. Additional codes:

| Trigger | Action | Notes |
|---|---|---|
| `PageUp` / `PageDown` | PDF prev / next page, else file prev / next | Media-type-aware branch |
| `MOVE_HOME` / `MOVE_END` | PDF/EPUB/TEXT home/end | Media-type-aware |
| `DPAD_LEFT` / `DPAD_RIGHT` | File prev / next | |
| `Ctrl+Z` | Undo | Has a dedicated branch |

Phase 3 should retire this duplication by routing K2 through K1.

### 6.3 KeyboardNavigationHandler (K3) — MainActivity extras

| Trigger | Action |
|---|---|
| `KEYCODE_PLUS` / `KEYCODE_INSERT` | Add resource (not in InputAction today — see §7) |

### 6.4 DialogKeyboardDelegate (K4)

| Trigger | Action |
|---|---|
| `Enter` | `DialogPrimary` |
| `Escape` | `DialogDismiss` |
| `Space` | context-specific (toggle) |
| `Tab` | focus navigation |

### 6.5 GamepadInputManager (G1)

**Player surface:**

| Trigger | Action |
|---|---|
| `BUTTON_A` | `PlayerAction.PlayPause` |
| `BUTTON_B` | `PlayerAction.Exit` |
| `BUTTON_X` | `PlayerAction.Next` |
| `BUTTON_Y` | `PlayerAction.Prev` |
| `BUTTON_L1` | `PlayerAction.Seek(-N)` (rate-limited) |
| `BUTTON_R1` | `PlayerAction.Seek(+N)` (rate-limited) |
| `BUTTON_START` | `PlayerAction.ToggleHud` |
| `BUTTON_SELECT` | `PlayerAction.ToggleHints` |
| `AXIS_Y` (left stick, inverted) | `PlayerAction.Volume(±1)` |
| `AXIS_Z` / `AXIS_RZ` (right stick) | `PlayerAction.Seek(scaled)` |

**Browser surface:**

| Trigger | Action |
|---|---|
| `BUTTON_A` | `BrowserAction.Select` |
| `BUTTON_B` | `BrowserAction.Back` |
| `BUTTON_X` | `BrowserAction.MultiSelect` |
| `BUTTON_Y` | `BrowserAction.ContextMenu` |
| `BUTTON_L1` / `BUTTON_R1` | `BrowserAction.SwitchTab(±1)` |
| `BUTTON_START` | `BrowserAction.Search` |

### 6.6 MouseEventHandler (M1)

| Trigger | Action |
|---|---|
| left-click (ACTION_DOWN) | single / double-click → open |
| `BUTTON_SECONDARY` (right) | `ShowContextMenuAt(x, y)` |
| `BUTTON_TERTIARY` (middle) | `ToggleFavourite` |
| `BUTTON_BACK` (XButton1) | `MouseNavigateBack` |
| `BUTTON_FORWARD` (XButton2) | `MouseNavigateForward` |
| `AXIS_VSCROLL` | `ScrollWheel(deltaY, withShift, withCtrl)` |
| `AXIS_HSCROLL` | `ScrollWheel(deltaX, …)` |
| hover enter / exit | tooltip surfaces |

### 6.7 MediaButtonRestartReceiver (R1) & AudioPlaybackService MediaSession (R2)

Media button keycodes: `MEDIA_PLAY`, `MEDIA_PAUSE`, `MEDIA_PLAY_PAUSE`, `MEDIA_NEXT`, `MEDIA_PREVIOUS`. In R1 these start the service; in R2 they dispatch Media3 `MediaSession` callbacks. Both must migrate to a shared resolver so a user-assigned media-button binding propagates to both.

### 6.8 VrControllerInputManager (V1)

OpenXR native events (defined in `vr/openxr/XrInputEventType.kt`) → `PlaybackCommand`:

| `XrInputEventType` | `PlaybackCommand` |
|---|---|
| `PAUSE_TOGGLE` | `TogglePausePlay` |
| `EXIT` | `Exit` |
| `FILE_OPS` | `OpenFileOps` |
| `MENU` | `OpenControls` |
| `SEEK_BACKWARD` | `SeekBackward` |
| `SEEK_FORWARD` | `SeekForward` |
| `FILE_PREV` | `PreviousFile` |
| `FILE_NEXT` | `NextFile` |
| `VOLUME_UP` | `VolumeStep(+1)` (rate-limited) |
| `VOLUME_DOWN` | `VolumeStep(-1)` (rate-limited) |
| `RECENTER` | `Recenter` |
| `TOGGLE_IMMERSIVE` | `ToggleImmersiveMode` |
| `CHEATSHEET` | `ShowCheatsheet` |
| `ZOOM_START` / `ZOOM_STEP` / `ZOOM_END` | zoom grip delta (analog) |

V1 also accepts `KeyEvent` and `MotionEvent` fallbacks for BT keyboards/mice paired to the headset — those are re-routed to `onCommand`.

---

## 7. Deliverable D — Candidate Additions (Unbound Commands)

Commands that already exist in the codebase (as methods on managers or as sealed-class variants) but have **no default hardware binding today**. Each is a potential candidate for the Defaults Map File. Ranked by expected user value.

### 7.1 High value (strong user demand / parity with other players)

1. **Playback speed** (`+` / `-` / `=` by convention) — speed up, speed down, reset speed. Supported by ExoPlayer under the hood; no key binding exists.
2. **Subtitle track cycle** — ExoPlayer tracks exist; no binding.
3. **Audio track cycle** — same.
4. **Subtitle delay ±** — user-requested but no current mechanism.
5. **Chapter / bookmark next / previous** — for long documents and audio books.

### 7.2 Medium value

6. **Slideshow mode toggle** (images) — feature exists in background, not bound.
7. **Toggle repeat mode** — player supports it, no key.
8. **Screenshot / save frame** — currently `Save Frame` command exists in VR only; desktop/phone users have no path.
9. **Pan / scroll overlay in images** — not bindable today.
10. **Rotate image ±90°** — exists in menus only.

### 7.3 Low value (nice-to-have)

11. **Pinned zoom presets** (50% / 100% / 200%).
12. **Toggle aspect ratio** (stretch / fit / fill).
13. **Bookmark current position** (for long video/audio).

### 7.4 Dialog-level candidates

14. **Enter+Shift** inside rename dialog → commit and move to next.
15. **Ctrl+Enter** inside rename dialog → commit and close.

Phase 6 UI must present these as rows with "no default assigned" state so users can bind any of them without the developer having to pre-assign defaults.

### 7.5 Fleeting references (already in `InputAction`, not yet executed)

- `PasteClipboard` (`Ctrl+V`) — parsed by K1 but no consumer handler. Either wire it in Phase 3 or mark it inactive in the Defaults Map File.

---

## 8. Deliverable E — Refactor Order

Phase-by-phase sequencing. Each row answers "which engine is refactored in which phase, and what is the single point of control replaced".

| Order | Engine | Phase | Single point of control replaced | Risk level |
|:---:|--------|:---:|--------------------------------|:---------:|
| 1 | K1 `KeyboardShortcutHandler` | 3 | The surface-to-action `when` tree becomes a `keyBindingManager.resolveKeyAction(keyCode, modifiers, surface)` lookup. | Medium (high test coverage cushions risk) |
| 2 | K2 `PlayerKeyboardHandler` | 3 | Legacy raw-key branch deleted; all keys flow through K1. | Medium |
| 3 | K3 `KeyboardNavigationHandler` | 3 | `KEYCODE_PLUS`/`INSERT` join the map file. | Low |
| 4 | K4 `DialogKeyboardDelegate` | 3 | `Enter`/`Escape`/`Tab`/`Space` mapped via manager with `DIALOG` surface. | Low |
| 5 | G1 `GamepadInputManager` | 4 | Button-to-`GamepadAction` map externalised; rate limiting stays inside the engine. | Medium |
| 6 | M1 `MouseEventHandler` | 4 | Button-to-action map externalised; wheel + gesture still in-engine. | Low |
| 7 | R1 + R2 media buttons | 4 | Shared resolver for media keycodes. | Low |
| 8 | V1 `VrControllerInputManager` | 5 | `XrInputEventType → PlaybackCommand` map moves to data layer. BT keyboard/mouse fallback in V1 reuses K1+M1 resolvers. | **High** (C++ boundary; must not touch the native side) |
| — | Touch engines T1–T8 | deferred ("5b") | out of scope for v1 | N/A |

---

## 9. Deliverable F — Cross-cutting Concerns

Behavioural contracts that are not visible in the Trigger Catalogue but must survive the refactor.

### 9.1 Debouncing

- **PlayerKeyboardHandler (K2)** debounces media-button events (repeated `MEDIA_PLAY_PAUSE` at high rate from some BT remotes). The window must be preserved in Phase 3; record its exact value before migrating.
- **GamepadInputManager (G1)** rate-limits analog-axis-driven actions at ≈100–120 ms for seek, ≈150 ms for volume.
- **VrControllerInputManager (V1)** rate-limits volume steps; also pinch-down guards in hand-tracking path.

### 9.2 Deadzones

- **G1** uses `DEADZONE = 0.15f` for gamepad analog sticks. Any resolver that maps axes to commands MUST honour this.
- VR analog axes have their own thresholds at the C++ layer; nothing in Kotlin needs to replicate them.

### 9.3 Axis inversion / scaling

- **G1**: left-stick Y is inverted for intuitive volume control (push up = louder).
- **G1**: right-stick Y seek is *scaled by deflection* — larger tilt, larger step. The map file schema must allow expressing "axis trigger with proportional output" to preserve this.

### 9.4 Thread affinity

- **V1** receives events on the `xr-render-thread` (C++ callback). It hops to `mainHandler` before touching UI/ViewModel state. Any new resolver code called from V1 MUST be safe to call from the main thread only, or explicitly document its thread guarantee.
- **G1** and **M1** are called from the main thread (Activity `dispatchKeyEvent` / `dispatchGenericMotionEvent`).

### 9.5 Surface profile coupling

- K1, G1, and V1 all branch on a surface concept. K1/G1 share `InputSurface`; V1 hardcodes `VR_PLAYER`. The resolver introduced in Phase 2 MUST accept surface as an input; surface-switching on the fly (e.g. opening a dialog inside the player) must remain atomic.

### 9.6 View-capability gating

- **K2** branches by media type (`PDF`, `EPUB`, `TEXT`, `VIDEO`, `IMAGE`) for same keycodes (e.g. `PageUp` means "PDF prev page" or "previous file" depending on active viewer).
- This is *command-level polymorphism*, not a key-level concern. Represent it in Phase 2 by letting a single `CommandId` dispatch through the active viewer's handler — **do not** encode the polymorphism in the binding file.

### 9.7 Consume-return semantics

- `dispatchKeyEvent` callers expect a `Boolean` return: `true` means "consumed, do not pass to super". The resolver MUST preserve this: an action whose default is "no-op on this surface" must NOT accidentally swallow the event (otherwise we break TalkBack, IME, etc.).

### 9.8 Accessibility

- `KeyboardShortcutHandler` is the shortest path to `TalkBack`-visible focus changes via `FocusManager`. Any slow-down introduced by Phase 2's resolver (e.g. synchronous disk access) will be felt during screen-reader navigation. The `<1ms` resolver budget from the strategic spec is a TalkBack requirement, not a gaming requirement.

---

## 10. Phase-1 Work Breakdown

The concrete actions to complete this phase. All are research/writing; no production code changes.

| # | Task | Output location | Done-when |
|:---:|------|-----------------|-----------|
| 1 | Verify every engine path resolves. | inline in §4 | every path returns a file via `ls` |
| 2 | Re-grep every hardcoded `KEYCODE_*` / `BUTTON_*` / `AXIS_*` literal across all engines and compare against §6. | `temp/phase1/trigger-catalogue-raw.txt` | count matches the table row count ±0 |
| 3 | Re-grep every call to `onCommand(…)` / `dispatch(…)` / `fire(…)` across engines and extract the emitted `PlaybackCommand` / `InputAction` / `GamepadAction` variants. | `temp/phase1/emitted-actions.txt` | every emitted variant either has a row in §5, or appears in `temp/phase1/emitted-actions.txt` under a `NEEDS_PHASE_2_REVIEW:` prefix line |
| 4 | Confirm the debounce / rate-limit constants in K2, G1, V1 by reading the files — record the literal values. | append to §9.1 | each engine has a numeric value |
| 5 | Walk the `ui/player/helpers/` directory for any *new* engines added since this spec was drafted (codebase is evolving — see recent "Wave" decompositions in `git log`). | addendum to §4 | no new engine without a row |
| 6 | Produce a unified **candidate `CommandId` list** — every distinct action across `InputAction`, `GamepadAction`, `PlaybackCommand`, `XrInputEventType`, plus candidate additions from §7. | `temp/phase1/commandid-candidates.md` | entries unique, grouped by strategic taxonomy (Playback Core / Navigation / View / Audio / System / Sorting / VR-Only) |
| 7 | Produce a **default-binding spreadsheet** (CSV or markdown table): one row per `CommandId`, columns for keyboard-default-1, keyboard-default-2, gamepad-default, mouse-default, vr-default, notes. | `temp/phase1/defaults-seed.md` | covers every `CommandId` from task 6; empty cells explicitly marked "—" |
| 8 | Log findings in `dev/CHANGELOG.md` via `scripts/add_to_dev_log.ps1`. | dev log | one entry per artefact file |

All outputs land in `temp/phase1/` (CLAUDE.md §Strict-Rules-1: no writes to project root).

---

## 11. Exit Criteria (gate to Phase 2)

Phase 1 is complete when **all** of the following hold:

1. §4 engine inventory is verified — every path resolves.
2. §6 trigger catalogue includes every `KEYCODE_*` grep hit in the engines listed.
3. §7 candidate additions are reviewed and prioritised by the product owner.
4. §8 refactor order is signed off.
5. §9 cross-cutting-concerns list has *literal values* for every debounce / rate-limit / deadzone mentioned.
6. The four output files in `temp/phase1/` exist and are reviewed.
7. Strategic spec §10 Ambiguity Gate items that block Phase 2 specifically (merge policy, max bindings per command, conflict policy) have a non-empty resolution line in `PLAN/spec_player-keybinding-remapping.md` §10 (no `?` or `TBD` tokens in the resolution column).

Phase 2 MUST NOT start until points 1, 6 and 7 are true. Points 2–5 may be iterated during Phase 2 if findings emerge.

---

## 12. Handoff to Phase 2

Phase 2 receives from Phase 1:

- The verified engine inventory (§4) → targets for `KeyBindingManager` injection in later phases.
- The unified `CommandId` candidate list (task 6 output) → seed for the canonical `CommandId` namespace.
- The defaults-seed spreadsheet (task 7 output) → the row structure of the Defaults Map File asset.
- Cross-cutting constants (§9) → guard-rails for the resolver performance budget and for axis-to-command mapping schema.
- Candidate additions (§7) → rows to add to the defaults file with empty defaults so the Phase 6 UI can expose them without further product work.

Phase 2 is responsible for turning these artefacts into **code and a committed asset file**; Phase 1 does not do that.

---

## 13. Risks Specific to Phase 1

| Risk | Likelihood | Mitigation |
|------|:----------:|------------|
| Engines added to the codebase between this spec and Phase 2 start escape the inventory. | Medium | Task 5 (§10) — re-walk `ui/player/helpers/` and any `*InputManager*` / `*KeyboardHandler*` class added since this spec. |
| Hardcoded triggers missed because they live outside an `if`/`when` (e.g. set up in XML `android:onKey` attributes). | Low | Grep `android:onKey`, `android:onClick`, `OnKeyListener` usages in `res/layout/` and wire them into §6. |
| `InputAction` / `PlaybackCommand` / `GamepadAction` drift during Phase 1 itself if other work streams merge. | Low | If Phase 1 stretches across multiple work sessions, re-run task 3 before Phase 2. |
| Candidate additions (§7) turn out to be infeasible (e.g. no ExoPlayer API for subtitle delay on the version pinned). | Medium | Each candidate carries a "feasibility" column in `temp/phase1/commandid-candidates.md`; infeasible ones are moved to §14 (future spec). |

---

## 14. Out of Scope for Phase 1 (forwarded to later phases or future specs)

- Choosing the Defaults Map File format (Phase 2).
- Choosing the persistence store for overrides (Phase 2).
- Designing the capture-mode UI (Phase 6).
- Designing the fullscreen remapping layout (Phase 6).
- Handling touch gestures (Phase "5b" — separate future spec).
- Reconciling hand-tracking microgestures with discrete commands (separate spec `spec_vr-hand-tracking.md`).
- Telemetry / analytics on remapping usage.
- Presets, profiles, import/export (strategic non-goal §14 in parent spec).

---

## 15. Phase 1 Completion Checklist

- [ ] §4 engine paths verified (task 1)
- [ ] `temp/phase1/trigger-catalogue-raw.txt` produced (task 2)
- [ ] `temp/phase1/emitted-actions.txt` produced (task 3)
- [ ] Debounce / rate-limit literals filled in §9.1 (task 4)
- [ ] Addendum to §4 if new engines found (task 5)
- [ ] `temp/phase1/commandid-candidates.md` produced (task 6)
- [ ] `temp/phase1/defaults-seed.md` produced (task 7)
- [ ] Dev-log entries for each output file (task 8)
- [ ] Strategic §10 Ambiguity Gate items that block Phase 2 have written answers
- [ ] Refactor order (§8) signed off by product owner
- [ ] `.\scripts\add_to_dev_log.ps1` run for this spec file

---

## Revision History

- **2026-04-25** — by `/spec-update` (`claude-opus-4-7`, focus: language, structure, verifiability, consistency, completeness, style)
  - ACCEPT applied: 2 findings (removed `4–8h` time estimates from §header Tier line and §13 risk-mitigation cell — violation of project rule against time estimates in `PLAN/spec_*.md`).
  - REVIEW applied: 3 findings — §4.7 engine-count arithmetic clarified (8 dispatchers + 1 receiver = 9), §10 Task 3 Done-when given a static predicate (`NEEDS_PHASE_2_REVIEW:` prefix in `temp/phase1/emitted-actions.txt`), §11 Exit Criteria item 7 given a static predicate (no `?`/`TBD` tokens in strategic §10 resolution column).
  - DISCUSS proposed: 3 items — see "Proposed Structural Changes" below.

---

## Proposed Structural Changes

### Proposal P-1 — Move tactical phase file under parent-spec folder  (proposed 2026-04-25 by `claude-opus-4-7`)

**Status:** Proposed
**Summary:** This file lives at the strategic-spec path but self-describes as a tactical Phase 1 document; per `/spec-tech` convention it should live under the parent's tactical folder.
**Affected section:** file path itself (and any inbound links).
**Rationale:** The `/spec-tech` template places tactical phase files at `PLAN/spec_<short-name>/PHASE_NN__*.md` with a sibling `INDEX.md`. Keeping a tactical file at strategic-path level (`PLAN/spec_*.md`) breaks the discoverability contract — `/spec-check`, `/spec-fix` and other tools key off the folder layout.
**Suggested edit:**
> current path: `PLAN/spec_player-keybinding-phase1-preparation.md`
→
> proposed path: `PLAN/spec_player-keybinding-remapping/PHASE_01__preparation.md`

**Next step:** user or another model to decide. Move cascades into the strategic spec's link list and any external references (search for `spec_player-keybinding-phase1-preparation` repo-wide before relocation).

### Proposal P-2 — Author tactical INDEX.md for parent spec  (proposed 2026-04-25 by `claude-opus-4-7`)

**Status:** Proposed
**Summary:** Phase 1 file claims "Phase 1 of 7" but no `INDEX.md` enumerates the seven phases or tracks their statuses; the §0 Phase Map duplicates content that canonically belongs in `INDEX.md`.
**Affected section:** parent spec folder (currently absent).
**Rationale:** `/spec-tech` mandates an `INDEX.md` per tactical plan. Without it, status alignment between strategic spec, tactical INDEX, and phase-file headers cannot be checked, and phase ordering / blocker tracking has no single source of truth.
**Suggested edit:**
> create `PLAN/spec_player-keybinding-remapping/INDEX.md` via `/spec-tech` invocation; migrate §0 Phase Map content into the INDEX status table.

**Next step:** run `/spec-tech player-keybinding-remapping` to generate the INDEX skeleton, then either keep §0 here as a short pointer or remove it entirely once INDEX is canonical.

### Proposal P-3 — Cross-file: stray Russian letter in parent spec header  (proposed 2026-04-25 by `claude-opus-4-7`)

**Status:** Proposed
**Summary:** `PLAN/spec_player-keybinding-remapping.md` line 1 begins with `р# Specification:` — a stray Cyrillic `р` precedes the `#` heading marker.
**Affected section:** different file — not edited by this pass per `/spec-update` cross-file boundary rule.
**Rationale:** Breaks the H1 heading and visibly garbles the title in any markdown renderer. Trivial fix, but outside the scope of this `/spec-update` target.
**Suggested edit:**
> `р# Specification: PLAYER-KEYBINDING — Custom Playback Controls Remapping`
→
> `# Specification: PLAYER-KEYBINDING — Custom Playback Controls Remapping`

**Next step:** run `/spec-update player-keybinding-remapping` to apply (single ACCEPT-grade edit), or fix manually.
