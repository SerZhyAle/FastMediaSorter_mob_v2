# Phase 05 — VR Migration

**Strategic spec:** [`../spec_player-keybinding-remapping.md`](../spec_player-keybinding-remapping.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03 (BT-keyboard fallback), Phase 04 (BT-mouse fallback)
**Blocks:** Phase 07
**Steps done:** 3 / 4
**Started:** 2026-04-25
**Completed:** 2026-04-25

---

## Objective

Move the `XrInputEventType → PlaybackCommand` map from `VrControllerInputManager`'s inline `when` into the Defaults Map File asset (Phase 02 already contains these rows). The engine becomes a thin consumer of `KeyBindingManager.resolve(InputTrigger.VrEvent(..), InputSurface.VR_PLAYER)`. **The C++ OpenXR edge-detection layer is NOT touched.** This phase is explicitly Kotlin-only.

---

## Prerequisites

- [ ] Phase 02 is `✅ Done`; `InputTrigger.VrEvent` subclass exists.
- [ ] `default_bindings.json` from Phase 02 contains ≥ 10 rows with `flavor_gate = "vr_only"` (strategic §6.8 listed 14 VR event types).
- [ ] `testVr` source set is green pre-migration — the existing `VrControllerInputManagerTest.kt` passes (baseline).
- [ ] Meta Quest 3 is available for on-device smoke test (user owns one — see memory).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrControllerInputManager.kt` | Modified | ≤ 500 |
| `app_v2/src/testVr/java/com/sza/fastmediasorter/vr/helpers/VrControllerInputManagerTest.kt` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/input/InputTrigger.kt` | Modified | ≤ 250 |

---

## Steps

### Step 05.1 — Finalise `InputTrigger.VrEvent` encoding

**Files:** `domain/input/InputTrigger.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Confirm that the `VrEvent(xrEventType: Int)` subclass exists (added in Phase 02 Step 02.1). Extend:
>
> - `serialize()` for `VrEvent` emits `vr:<int>`.
> - `fromXrInputEvent(type: Int): InputTrigger.VrEvent = VrEvent(type)` helper for the engine.
>
> VR analog-grip events (`ZOOM_START` / `ZOOM_STEP` / `ZOOM_END` per strategic §6.8) stay encoded as discrete `Int` codes — they are **not** `GamepadAxis` triggers. Native-side thresholding is already applied (C++). Document this with one line of KDoc on the `VrEvent` subclass: "The wrapped value is a raw `XrInputEventType` code; axis-like events are pre-thresholded by the C++ bridge."

**Verification:**

- `Grep "fromXrInputEvent"` matches exactly once in `InputTrigger.kt`.
- `Grep "class VrEvent"` matches exactly once.
- `Grep "vr:"` across `InputTrigger.kt` indicates both serialize + deserialize handle the `vr:` prefix.

**Status:** `[x]` done

---

### Step 05.2 — Migrate VrControllerInputManager

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrControllerInputManager.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Create a timestamped backup of `VrControllerInputManager.kt` in `temp/` before editing (file is > 500 LOC).
>
> Constructor-inject `KeyBindingManager` via Hilt in the `vr` sourceset (confirm the DI module from Phase 02 installs in the `SingletonComponent` — it does, so no extra module needed).
>
> Replace the inline `when (type)` block in `onInputEvent(type, hand, value, source)` with:
>
> ```kotlin
> val trigger = InputTrigger.fromXrInputEvent(type)
> val commandId = keyBindingManager.resolve(trigger, InputSurface.VR_PLAYER) ?: return
> dispatchVrCommand(commandId, hand, value)
> ```
>
> `dispatchVrCommand(commandId, hand, value)` is a new private method performing the `CommandId → PlaybackCommand` mapping (the inverse of what the asset file encodes). Preserve:
>
> - The thread hop to `mainHandler` (strategic §9.4) — stays above the resolver call site.
> - The volume rate-limit (strategic §9.1) — wraps the volume `CommandId` dispatch.
> - The hand / value arguments — they are "payload" for specific commands (`VolumeStep` uses value direction, `ZOOM_STEP` uses scaled value).
>
> `onKeyEvent(event)` (BT keyboard passthrough) and `onMotionEvent(event)` (BT mouse passthrough) — both delegate to the K1/M1 handlers that already consume `KeyBindingManager` post Phase 03/04. No change required here beyond confirming they still route correctly.

**Verification:**

- Backup exists at `temp/VrControllerInputManager.kt.<timestamp>.backup`.
- `Grep -c "XrInputEventType\." app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrControllerInputManager.kt` returns ≤ 2 (allowed: import + the helper's signature / fallback default — no direct `when` branches).
- `Grep "keyBindingManager.resolve"` matches ≥ 1.
- `Grep "fun dispatchVrCommand"` matches exactly once.
- `Grep "mainHandler.post"` still matches — thread hop preserved.
- `Grep` for the volume rate-limit literal (from Phase 01 `debounce-literals.md`) still matches.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x]` done

---

### Step 05.3 — Update `VrControllerInputManagerTest`

**Files:** `app_v2/src/testVr/java/com/sza/fastmediasorter/vr/helpers/VrControllerInputManagerTest.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Update existing test cases to pass a fake `KeyBindingManager` seeded from the real `default_bindings.json` for `vr_only` rows. Add three new cases:
>
> 1. An override binding maps `XrInputEventType.PAUSE_TOGGLE` → `CommandId.SYSTEM_EXIT_PLAYER` — confirm exit path, not pause path, fires.
> 2. Volume rate-limit fires only once for two `VOLUME_UP` events 50 ms apart.
> 3. An unknown `XrInputEventType` code is silently dropped (no crash, no dispatch).

**Verification:**

- `Grep -c "@Test"` returns previous value + 3.
- `./gradlew.bat :app_v2:testVrDebugUnitTest --tests "*.VrControllerInputManagerTest"` exits 0.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x]` done

---

### Step 05.4 — On-device smoke test on Meta Quest 3

**Files:** none (checklist-only)
**Depends on:** Step 05.2

**Prompt for developer:**

> Build and install `vrDebug` on Meta Quest 3 via `adb install -r`. Manual checks:
>
> 1. Press right-controller A: Pause/Play toggles.
> 2. Press right-controller B: Exit player.
> 3. Press menu button: Controls overlay opens.
> 4. Grip left trigger and turn thumbstick: seek forward/backward.
> 5. Push right thumbstick up/down repeatedly: volume changes at the expected rate (not every frame).
> 6. Use a BT keyboard paired with headset: `Space` pauses (BT keyboard fallback through K1, verified Phase 03).
> 7. Pair a BT mouse: right-click shows context menu (BT mouse fallback through M1, verified Phase 04).
>
> Log results in the phase Handoff Notes below. If any behaviour regresses vs. the pre-migration baseline, flip phase `Status:` to `⛔ Blocked` and file a Blockers Log entry in `INDEX.md` with the specific event + expected vs. observed.

**Verification:**

- The seven checks above are each ticked in the developer's notes (pasted into Handoff Notes of this file).
- No `⛔ Blocked` entry opens in `INDEX.md` for Phase 05.

**Status:** `[manual — deferred to human]`

---

## Phase Done Criteria

- [ ] Every `Step 05.*` is `[x] done`.
- [ ] `/build` reports green for `assembleVrDebug` (and `vrUnlicensedDebug` if still in flavor list — see `app_v2/build.gradle.kts`).
- [ ] `testVrDebugUnitTest` task green.
- [ ] On-device smoke check (Step 05.4) passed.
- [ ] Grep for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entries added for every "Files Touched" file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] **C++ layer untouched.** `git diff --stat app_v2/src/vr/cpp/` and `app_v2/src/main/cpp/` show zero changes during this phase.

---

## Handoff Notes to Next Phase

SPEC-PATCH (05.2a): `InputSurface.VR_PLAYER` in spec → actual enum value is `InputSurface.VR`.
SPEC-PATCH (05.2b): `POINTER_CLICK_DOWN/UP` are special-cased before the resolver; they feed `onPointerEvent` (UI layer), not the command bus — no `PlaybackCommand` equivalent exists.
MANUAL-REQUIRED (05.4): On-device smoke test deferred — 7 checks listed in Step 05.4.

- All five input engines (K, G, M, R, V) now consume `KeyBindingManager`. Phase 06 can build the UI with confidence that every written override propagates everywhere.
- VR analog grip (`ZOOM_*`) remains a discrete per-event `CommandId` — not a generic axis. If Phase 06 ever surfaces "VR grip binding", the row carries `trigger = VrEvent(<zoom_*>)` with the three stages as separate `CommandId`s (already seeded by Phase 02's defaults JSON).
- The C++ bridge still owns edge-detection and thresholding — future VR input changes that require new event types happen in both places: (a) add enum value in `XrInputEventType.kt` + its C++ twin, (b) add a `CommandId` constant + a default row in `default_bindings.json`.

---

## Rollback Plan

- Revert the phase commits. C++ layer is explicitly untouched — nothing to rebuild natively.
- If a regression appears on-device post-merge: restore `VrControllerInputManager.kt` from `temp/` backup; the `vr` DI wiring from Phase 02 remains in place (idle).
