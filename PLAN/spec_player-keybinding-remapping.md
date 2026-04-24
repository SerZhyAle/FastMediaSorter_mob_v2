# Specification: PLAYER-KEYBINDING — Custom Playback Controls Remapping

**Status:** Tactical (Strategic Level)
**Date:** 2026-04-24
**Tier:** 4 — Strategic (8h+, high risk)
**Roadmap entry:** Ad-hoc — user request 2026-04-24. Full remapping of keyboard, mouse, gamepad and VR controller bindings for `PlayerActivity` and `VrPlayerActivity`.
**Tactical plan:** [`PLAN/spec_player-keybinding-remapping/INDEX.md`](spec_player-keybinding-remapping/INDEX.md)

> **Scope of this document:** STRATEGIC specification only. Deliberately excludes tactical decisions (concrete class names, database schema, Room migrations, Hilt modules, file paths, line budgets, implementation step ordering). Those belong to a follow-up tactical spec authored once the strategic shape is frozen.

---

## 1. Problem Statement

All key, button, mouse and VR-controller bindings are currently **hardcoded inline** at the point of dispatch (switch-style blocks inside input handlers of the player stack). This creates three structural problems:

1. **No single source of truth for defaults.** The same logical action (e.g. "Pause/Play") is re-declared across multiple handlers (keyboard, gamepad, VR), making baseline changes error-prone and inconsistent.
2. **Users cannot adapt controls to their habits.** Muscle memory varies widely — YouTube-style `J/K/L`, VLC-style `Space/Arrows`, media-remote layouts, left-handed VR players, non-standard Bluetooth remotes that emit unusual `KeyEvent` codes. One fixed layout cannot serve them all.
3. **No recovery path.** When a user experiments with a mapping and dislikes the result, there is no way to restore factory defaults — neither for a single command, nor for a feature group, nor globally.

The feature must solve all three at once: centralise the defaults, make every binding reassignable through UI, and provide a granular "Return to Default" escape hatch.

---

## 2. Strategic Goals

The four load-bearing pillars of this feature (each is detailed in its own section below):

1. **Defaults Map File** — a single in-project asset defining every baseline binding, grouped by functionality. The app reads defaults from this file only; no binding is hardcoded at the dispatch site.
2. **Functional Groups Taxonomy** — all remappable commands are organised into named groups (Playback, Navigation, View, System, VR-only ..). This taxonomy is the backbone of both the defaults file and the remapping UI.
3. **Fullscreen Remapping Dialog** — a dedicated "Controls & Keybindings" entry in app settings opens a fullscreen UI that exposes every command, lets the user reassign any of them, and visualises the current state per group.
4. **Hierarchical Reset ("Return to Default")** — the user can restore defaults at three granularities: single command, one functional group, or everything at once.

Secondary goals:

- **Universal input coverage** — keyboard keys, keyboard modifiers, mouse buttons, mouse wheel, gamepad buttons, gamepad analog axes, VR controller buttons, VR controller analog axes and triggers are all first-class citizens.
- **Account-wide consistency** — mappings are persisted per-account and apply across all hardware connected to the device.
- **Graceful coexistence with unknown hardware** — an unrecognised input event must be capturable and bindable even if its human-readable label is unknown.

Non-goals for this spec (see section 14): cloud sync of profiles, sharing presets between users, OS-level shortcut remapping, remapping outside the player, hand-tracking gesture bindings.

---

## 3. Pillar I — Defaults Map File (Single Source of Truth)

### 3.1 Purpose

Replace every inline `when (keyCode)` / `when (buttonId)` branch scattered across input handlers with **lookup against a declarative file**. The file is the only place defaults are defined; the runtime merges it with user overrides into a resolved binding table.

### 3.2 Strategic Requirements

- **Format:** Declarative, human-readable, version-controlled alongside the source. Must be editable by developers without recompiling dispatch logic. Format family (JSON / XML / Kotlin object) is a tactical choice — the **strategic requirement is that the file is a data asset, not executable code embedded in a handler**.
- **Structure:** Flat list of entries, each entry carries — at minimum — a stable action identifier, the functional group it belongs to, a human-readable label key (for localisation), and one or more default input triggers per device category.
- **Versioning:** The file carries an internal schema version. When the app ships a new default that did not exist before, existing user overrides remain intact and the new command appears in UI with its factory default already assigned.
- **Localisation separation:** The file stores label *keys* (string resource references), never translated strings. Translation lives in the standard `values/` / `values-ru/` / `values-uk/` pipeline.
- **No behaviour in the file:** The file describes *what* is bound to *what* — never *how* the action executes. Dispatch handlers own execution.

### 3.3 What Counts as a "Default Trigger"

A default trigger is the factory mapping. The file must be able to express, per action:

- A keyboard key (with optional modifier state).
- A mouse button or wheel direction.
- A gamepad button.
- A gamepad analog axis with direction and activation threshold.
- A VR controller button, trigger or analog axis.
- The absence of a default on a given device category (an action can be keyboard-only, VR-only, etc.).

The schema must accommodate **two default triggers per device category per action** (e.g. both `Space` and `K` default to Pause on keyboard). Anything beyond two is a user override.

---

## 4. Pillar II — Functional Groups Taxonomy

### 4.1 Why Groups

A flat list of 50+ commands is unusable. Grouping by *what the command does to the playback experience* makes the settings screen scannable, enables group-level reset, and gives the defaults file a predictable structure.

### 4.2 Proposed Groups (strategic — exact membership is tactical)

| Group | Purpose | Example commands |
| ----- | ------- | ---------------- |
| **Playback Core** | Transport actions that change whether and at what rate media plays | Pause/Play, Stop, Speed Up, Speed Down, Reset Speed |
| **Navigation & Seeking** | Moving within the current file and between files | Seek ±5 s, Seek ±30 s, Next File, Previous File, Jump to Start/End |
| **View & Zoom** | How the content is shown | Zoom In/Out, Reset Zoom, Rotate, Fit to Screen, Toggle Fullscreen |
| **Audio & Subtitles** | Secondary tracks | Volume Up/Down, Mute, Cycle Audio Track, Cycle Subtitle Track |
| **System & UI** | Overlay visibility and meta-commands | Toggle Controls, Toggle Info Overlay, Take Screenshot, Exit Player |
| **Sorting & Actions** | File-management commands accessible from the player | Mark for Move, Delete, Rename, Favourite |
| **VR-Only Immersive** | Commands that exist only in VR context | Recenter View, Toggle 3D Mode, Switch Projection, Controller Ray Toggle |

The taxonomy is **an open set** — new groups can be added later without breaking existing user overrides, provided group identifiers are stable strings.

### 4.3 Group-Level Properties

Each group carries, at minimum:

- A stable identifier (used by the defaults file and the reset-per-group operation).
- A localised display name (via string resource).
- A suggested display order in the UI.
- Optional flavor gating (e.g. *VR-Only Immersive* is only visible in the `vr` flavor; *Audio & Subtitles* is hidden in the `photos` flavor).

---

## 5. Pillar III — Settings Integration & Fullscreen Remapping Dialog

### 5.1 Entry Point

A dedicated section inside the app's Settings screen — labelled "Controls & Keybindings" (or equivalent) — opens the remapping UI. It is **one tap deep** from the main settings list, not buried inside another category.

### 5.2 Fullscreen Presentation

The remapping UI occupies the full screen (Activity or fullscreen DialogFragment — the choice is tactical). The rationale:

- Binding tables with action names plus two trigger slots per device category are wide.
- Capture mode ("press any key now") benefits from an unambiguous full-screen modal so the user is not confused about which window owns input focus.
- VR remapping requires the headset UI to own all input channels while the user probes controllers.

### 5.3 Strategic UX Requirements

- **Grouped display:** The UI shows commands grouped by the taxonomy from Pillar II. Groups are collapsible; default state shows all groups expanded.
- **Per-row affordances:** Each command row shows its current binding(s), a "Remap" affordance, and an individual "Reset to default" affordance.
- **Per-group affordances:** Each group header carries a "Reset this group" affordance.
- **Global affordance:** A top-level "Reset all controls" affordance is always reachable (destructive confirmation required).
- **Capture mode:** Tapping "Remap" enters an unambiguous modal state ("Press the key, button or combination to assign .."). The modal shows the captured raw event (e.g. `KeyCode 21`) in addition to the human-readable name, so unknown hardware is still bindable.
- **Search / filter:** The user can filter the list by action name or by currently-bound key — important when the list is long.
- **Conflict surfacing:** When the same trigger is assigned to more than one command, every affected row is visibly flagged; the exact resolution policy is an Ambiguity Gate item (section 10).

### 5.4 Flavor Behaviour of the Settings Entry

- `standard`, `lite`, `photos`, `legacy`, `vr` — all show the entry.
- The *rows inside* the UI are filtered by flavor capability: `photos` does not show audio/video-specific groups; only `vr` shows the VR-Only Immersive group.

---

## 6. Pillar IV — Hierarchical Reset ("Return to Default")

Three levels of granularity are **all required** — this is explicit user demand, not an optional nicety.

| Level | Scope | Typical use case |
| ----- | ----- | ---------------- |
| **Single command** | Restores the default trigger(s) for one action only | "I experimented with Pause and want it back on Space" |
| **Group** | Restores all defaults for every action in one functional group | "I broke my Navigation layout, reset just that" |
| **Global** | Restores every default for every group | "Start over clean" |

### 6.1 Strategic Requirements

- **Reset is defined as "restore what the Defaults Map File says today"** — not "restore what was there yesterday". There is no undo history; there is only "factory default per the current app build".
- **Reset never fails silently.** The user receives visible confirmation that bindings changed.
- **Global reset requires a destructive confirmation dialog.** Single and group resets may be instant (optionally undoable via a toast/snackbar action — tactical choice).
- **Reset operates through the same persistence layer as normal overrides** — it must not leave the in-memory cache and on-disk state out of sync.

---

## 7. Universal Input Element Coverage

The remapping engine treats all these input sources as first-class:

| Device category | Examples of bindable elements |
| --------------- | ----------------------------- |
| Keyboard | Letter/digit keys, function keys, arrow keys, media keys, modifier+key combinations |
| Mouse | Left/Middle/Right button, Back/Forward side buttons, wheel up, wheel down, wheel click |
| Gamepad | Face buttons, shoulder buttons, triggers (analog with threshold), D-pad, analog stick deflection (axis + direction + threshold) |
| VR controller | Trigger, grip, thumbstick click, menu/system buttons, thumbstick deflection on either axis with threshold |

The strategic requirement: **the conceptual data model must express any of the above uniformly**, so capture mode, storage, lookup and UI rendering are one code path per concern — not six.

---

## 8. Conceptual Data Model (Strategic)

> No concrete schemas, no Kotlin code. Only the *shape of information* that must flow through the system.

Three conceptual entities:

1. **Command** — an abstract action the player can perform ("Pause/Play", "Seek +5s"). Identified by a stable string. Carries a group identifier, a localisation key, and flags for flavor gating.
2. **Binding** — the association of one Command with one concrete input trigger on one device category. A Command may have multiple Bindings (e.g. keyboard `Space` and gamepad A button both trigger Pause/Play).
3. **Trigger** — the input pattern itself: device category, element identifier, optional modifier state, optional analog threshold with direction.

Two sources of Bindings:

- **Default Bindings** loaded from the Defaults Map File at app start.
- **Override Bindings** stored per user, persisted on device.

The runtime produces a **resolved binding table** by merging defaults with overrides. The merge policy (full replacement per command vs. additive) is an Ambiguity Gate item (section 10).

---

## 9. Strategic Architecture Principles

These are design *constraints* the tactical spec must honour — not an implementation plan.

1. **Separation between configuration and dispatch.** The Defaults Map File is data. Dispatch handlers never inspect the file; they consult a resolved in-memory lookup.
2. **Hot path must not touch disk.** Input arrives at display-refresh rates (60–120 Hz). Resolution from event to Command must be a memory lookup — not a database query, not a file read, not a coroutine hop.
3. **Persistence is a boundary, not a call site.** The layer that persists overrides is invoked from the settings UI and at startup only. Dispatch handlers must not import anything from that layer.
4. **Capture is its own concern.** The mechanism that intercepts raw input to record a new binding is distinct from the mechanism that routes input to Commands during playback. They must not share state machines.
5. **Unknown hardware is a supported case, not an error.** The pipeline must cope with an event whose human-readable label is unknown: capture it, store it, dispatch from it.
6. **Flavor gating is a UI concern, not a data concern.** The Defaults Map File describes every command the codebase can execute; the UI filters what the current flavor exposes. This keeps the data file stable across flavors.
7. **Schema evolution without user-data loss.** Adding, removing or renaming commands in a future release must not silently destroy the user's overrides for commands that still exist.

---

## 10. UI Ambiguity Gate (blockers for tactical phase)

The following decisions are **open** and must be resolved in writing before any implementation starts. The tactical spec must answer each.

- [ ] **Merge policy for overrides.** When a user sets a custom binding for a command, do custom bindings *replace* all defaults for that command, or *add to* them? (Affects the reset model and the "how many bindings per command" UX.)
- [ ] **Max bindings per command per device category.** One? Two? Unlimited? Recommended cap: two.
- [ ] **Conflict policy.** If the same trigger resolves to two commands, do we (a) block the assignment at capture time, (b) allow it and flag visually, or (c) silently use the most recently assigned? Choose one and document.
- [ ] **Capture timeout.** Does capture mode auto-cancel after N seconds, or wait indefinitely until the user taps Cancel? If timed, what value?
- [ ] **Unrecognised trigger display.** For an input whose label is unknown, show a raw code (`Key [10045]`), a friendly fallback (`Unknown keyboard key`), or both?
- [ ] **Modifier capture policy.** For keyboard, do we capture plain keys only by default and require an explicit modifier gesture, or always capture the full modifier state when the user presses the key?
- [ ] **Analog threshold UX.** For gamepad/VR analog axes, does the user see and adjust the activation threshold, or is it fixed globally (e.g. `±0.7`)?
- [ ] **Reset confirmation granularity.** Confirmation dialog for global reset — confirmed. What about group reset? Single-command reset? Recommend: none for single, none for group, confirmation for global.
- [ ] **Undo window.** After a reset or an override change, is there a time-limited undo (e.g. snackbar for 5 s), or is every change immediately permanent?
- [ ] **Per-profile support.** Is there exactly one binding set per device/account, or multiple named profiles the user can switch between? Strong recommendation: single profile in v1, profiles are out of scope.

No implementation task proceeds until every item above has an explicit answer.

---

## 11. Performance & Reliability Constraints

- **Input-to-Command resolution must be strictly <1 ms** on the main thread, measured end-to-end from event receipt to Command dispatch. No database or file I/O on the hot path.
- **All bindings are loaded once** at player entry (and on user override changes) into an in-memory lookup. Changes made from the settings UI are pushed into the lookup without a player restart.
- **Persistence writes are off-main-thread** and must not block UI interaction.
- **Atomicity of reset.** A group-level or global reset must either complete entirely or leave the prior state intact — never a partial state.
- **Survive process death.** Overrides persist across process restarts. The in-memory lookup rebuilds from persisted data on next launch with no user action required.

---

## 12. Flavor & Platform Scope

### 12.1 Product Flavor Impact

| Flavor | Affected? | Notes |
| ------ | :-------: | ----- |
| `standard` | ✅ | Full feature — all groups except VR-Only |
| `lite` | ✅ | Same as standard; audio/video groups active per flavor capability |
| `photos` | ✅ | Audio/video/subtitle groups hidden; rest active |
| `legacy` | ✅ | Full feature; API 23 compatibility constraints inherited from the flavor |
| `vr` | ✅ | Full feature plus the VR-Only Immersive group is exposed |

No new `BuildConfig` flag is required at the strategic level. Visibility of VR-specific rows can be gated by the existing `BuildConfig.SUPPORT_VR_PLAYER` flag declared in `app_v2/build.gradle.kts`.

### 12.2 Android API Level Notes

No API-level forks are expected at the strategic level. Input event APIs (`KeyEvent`, `MotionEvent`) used here are stable from API 23 upward. Any storage-format choices must respect the `legacy` flavor's `minSdk 23` baseline.

### 12.3 Wear OS Impact

No Wear OS changes required. The Wear companion does not host the media player or expose remappable controls.

---

## 13. Dependencies

- **Player stack.** `PlayerActivity`, `VrPlayerActivity` and their input-dispatch helpers — these are the call sites whose inline mappings are replaced by lookups.
- **Settings stack.** The main settings screen gains one new entry pointing to the fullscreen remapping UI.
- **Localisation pipeline.** All command labels and group names flow through the standard EN/RU/UK `strings.xml` resources.
- **No external specs.** This spec is **self-contained**: it intentionally does not depend on any of the previously drafted VR specs (some of which have since been removed from `PLAN/`). When the `vr` flavor's player surfaces VR-specific commands, their canonical list is owned by *this* spec's Defaults Map File — not by a separate VR spec.

---

## 14. Out of Scope (future items)

- Cloud synchronisation of overrides across devices.
- Shareable / importable / exportable preset files.
- Named profiles ("Gaming", "Couch", "VR") with a switcher.
- Remapping outside the player (file browser navigation, global shortcuts, MainActivity actions).
- OpenXR hand-tracking gesture bindings (separate spec).
- Remapping of OS-level media keys that never reach the player activity.
- Community-sourced or vendor-supplied default presets.
- Macro bindings (one trigger → sequence of commands).
- Conditional bindings (same trigger → different command depending on player state).

---

## 15. Deferred to Tactical Phase

Items deliberately left out of this strategic document; they are required inputs to the follow-up tactical spec but must not be decided here:

- Concrete file format for the Defaults Map File (JSON vs. XML vs. Kotlin-object).
- Exact persistence mechanism for user overrides (Room table vs. DataStore vs. SharedPreferences).
- Concrete class names, package placement, Hilt module wiring, and line-budget breakdown.
- Database schema version bump and migration plan (if Room is chosen).
- Full enumeration of command identifiers across all groups (requires cross-referencing the actual player input code and is a tactical-phase deliverable).
- Exact layout of the fullscreen remapping UI (RecyclerView vs. Compose, specific widgets, visual design).
- Telemetry / analytics of remapping usage (out of strategic scope).

The tactical spec authored next must explicitly address all items in sections 10 and 15.
