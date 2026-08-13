# Phase 02 — Controller Ray Native

**Strategic spec:** [`../spec_vr-immersive-controls-panel.md`](../spec_vr-immersive-controls-panel.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none — parallel to Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 0 / 7
**Started:** —
**Completed:** —

---

## Objective

Add Touch controller aim-ray rendering in `OpenXrNative.cpp`: read the aim-action-space pose each frame, draw a GL line + cursor disk, and emit a new `onControllerPointerMove` callback with NDC hit coordinates against the UI composition plane. Create `VrControllerRayManager` in Kotlin (NDC → `MotionEvent` dispatch, mirrors `VrHandRayManager`). Wire into `VrPlayerActivity` / `OpenXrSessionManager`.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. _(none)_
- [ ] Research Q2 resolved: confirm that the Touch controller aim-action space (`/user/hand/*/input/aim/pose`) is already bound in the `setupActionSet` call in `OpenXrNative.cpp`. If missing, add action binding in Step 2.3.
- [ ] Research Q3 resolved: confirm quad-layer render order in `xrEndFrame` array (ray quad must appear after video layer).
- [ ] Working tree is clean or on a feature branch.
- [ ] `OpenXrNative.cpp` backed up (3030 lines — **mandatory** before any edit).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/cpp/OpenXrNative.cpp` | Modified | ≤ 3200 (was 3030) |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/XrInputCallback.kt` | Modified | ≤ 60 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrNative.kt` | Modified | ≤ 150 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrControllerRayManager.kt` | **New** | ≤ 200 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/di/VrModule.kt` | Modified | ≤ 80 |

---

## Steps

### Step 2.1 — Backup OpenXrNative.cpp (mandatory)

**Files:** `temp/` (write only)
**Depends on:** — start of phase

**Prompt for developer:**

> Copy `app_v2/src/vr/cpp/OpenXrNative.cpp` to `temp/OpenXrNative_BACKUP_<YYYYMMDD_HHmm>.cpp` before any native edits. Verify the copy exists and has the same line count as the original.

**Verification:**

- `Glob` — at least one file matching `temp/OpenXrNative_BACKUP_*.cpp` exists.

**Status:** `[ ]` not done

---

### Step 2.2 — Add onControllerPointerMove to XrInputCallback

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/XrInputCallback.kt`
**Depends on:** Step 2.1

**Prompt for developer:**

> Add a new default method to `XrInputCallback`:
>
> ```kotlin
> /**
>  * Controller aim-ray hit-test result against the UI composition plane.
>  * Emitted every frame while Touch controllers are the active input source.
>  * NDC semantics identical to [onPointerMove] for hands.
>  *
>  * @param hand one of [XrHand] constants.
>  * @param ndcX horizontal NDC ∈ [-1,1] or outside when ray misses the plane.
>  * @param ndcY vertical NDC ∈ [-1,1] or outside when ray misses the plane.
>  */
> fun onControllerPointerMove(hand: Int, ndcX: Float, ndcY: Float) {}
> ```
>
> Do not modify the existing `onPointerMove` signature.

**Verification:**

- `Grep` — `fun onControllerPointerMove` exists in `XrInputCallback.kt`.
- `Grep` — `fun onPointerMove` still exists unchanged in `XrInputCallback.kt`.
- File size — `XrInputCallback.kt` ≤ 60 lines.

**Status:** `[ ]` not done

---

### Step 2.3 — Native: emit controller NDC and render ray

**Files:** `app_v2/src/vr/cpp/OpenXrNative.cpp`
**Depends on:** Step 2.1

**Prompt for developer:**

> In `OpenXrNative.cpp`, inside the per-frame render loop (alongside where hand tracking aim pose is read), add:
>
> 1. **Locate controller aim space each frame** — for each hand, call `xrLocateSpace(g_controllerAimSpace[hand], g_stageSpace, frameState.predictedDisplayTime, &location)`. Confirm `g_controllerAimSpace` is already created during `setupActionSet`; if not, add the action binding for `/user/hand/left|right/input/aim/pose`.
>
> 2. **Project to UI plane** — compute NDC hit against the same UI composition plane used for hand tracking (flat plane at `g_uiPlaneDistance` meters in front of headset). If the aim ray misses the plane (ray is parallel or behind), emit `ndcX = 2.0f, ndcY = 2.0f` (off-plane sentinel).
>
> 3. **Emit callback** — call the JNI `onControllerPointerMove(hand, ndcX, ndcY)` method on the cached `XrInputCallback` jobject (look up the method ID for `onControllerPointerMove(IFF)V` analogously to how `onPointerMove(IFF)V` is looked up).
>
> 4. **Render ray GL primitive** — draw a `GL_LINES` primitive from the controller grip position to the hit point (or 2 m forward if miss). Use a thin white line with alpha ≈ 0.7. Add cursor disk (small `GL_TRIANGLE_FAN`) at hit point when the ray hits the plane. The GL primitives are drawn after the video texture quad but before HUD submission.
>
> Guard all code with `if (g_controllerRayEnabled)` boolean flag. Default enabled.
>
> Use `LOG_D` / `LOG_W` macros (not `LOGE` directly) for logging. Match the existing logging style in the file.

**Verification:**

- `Grep` — `onControllerPointerMove` found in `OpenXrNative.cpp`.
- `Grep` — `g_controllerRayEnabled` flag defined and checked.
- `Grep` — `GL_LINES` or equivalent ray draw call exists.
- `Grep` — `onControllerPointerMove(IFF)V` method ID lookup exists.

**Status:** `[ ]` not done

---

### Step 2.4 — Expose JNI toggle for controller ray visibility

**Files:** `app_v2/src/vr/cpp/OpenXrNative.cpp`, `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrNative.kt`
**Depends on:** Step 2.3

**Prompt for developer:**

> In `OpenXrNative.cpp`: add `JNIEXPORT void JNICALL Java_..._nativeSetControllerRayEnabled(JNIEnv*, jclass, jboolean enabled)` that sets `g_controllerRayEnabled = enabled`.
>
> In `OpenXrNative.kt`: add `@JvmStatic external fun nativeSetControllerRayEnabled(enabled: Boolean)`.

**Verification:**

- `Grep` — `nativeSetControllerRayEnabled` found in `OpenXrNative.kt`.
- `Grep` — `nativeSetControllerRayEnabled` JNI implementation found in `OpenXrNative.cpp`.

**Status:** `[ ]` not done

---

### Step 2.5 — Create VrControllerRayManager

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrControllerRayManager.kt`
**Depends on:** Step 2.2

**Prompt for developer:**

> Create `VrControllerRayManager(private val activity: Activity)`. Model it after `VrHandRayManager`:
> - Implement `onControllerPointerMove(hand: Int, ndcX: Float, ndcY: Float)` — same NDC→pixel mapping and `MotionEvent` dispatch logic as `VrHandRayManager.onPointerMove`.
> - Implement `onControllerClick(hand: Int, down: Boolean)` — same click-latch logic as `VrHandRayManager.onPointerClick`.
> - Implement `release()` — remove root from decor, nullify references.
> - **Do not** add a visual cursor dot: the GL ray rendered natively (Step 2.3) serves as the cursor. The Kotlin class only forwards `MotionEvent`s.
> - Use Timber for logging. No `Log.d`.

**Verification:**

- `Glob` — `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrControllerRayManager.kt` exists.
- `Grep` — `class VrControllerRayManager` in that file.
- `Grep` — `fun onControllerPointerMove` in that file.
- `Grep` — `fun onControllerClick` in that file.
- `Grep` — `Log\.d(` returns zero hits in that file.
- File size — `VrControllerRayManager.kt` ≤ 200 lines.

**Status:** `[ ]` not done

---

### Step 2.6 — Wire VrControllerRayManager into OpenXrSessionManager / VrPlayerActivity

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt` (or `VrPlayerActivity.kt`)
**Depends on:** Step 2.5, Step 2.4

**Prompt for developer:**

> Locate where `VrHandRayManager` is instantiated and wired to `XrInputCallback.onPointerMove`. Add an analogous wiring for `VrControllerRayManager`:
> - Instantiate `VrControllerRayManager(activity)`.
> - In the `XrInputCallback` anonymous class (or concrete class), override `onControllerPointerMove(hand, ndcX, ndcY)` and forward to `controllerRayManager.onControllerPointerMove(hand, ndcX, ndcY)`.
> - Wire `POINTER_CLICK_DOWN` / `POINTER_CLICK_UP` events with `source == XrInputSource.CONTROLLER` to `controllerRayManager.onControllerClick(hand, down=true/false)`.
> - Call `controllerRayManager.release()` in the XR session teardown path (alongside `handRayManager.release()`).
> - Do not add this wiring inside `VrPlayerActivity` directly if the existing code already delegates to a manager — prefer the same delegation point.

**Verification:**

- `Grep` — `VrControllerRayManager` instantiated (constructor call with `activity`).
- `Grep` — `onControllerPointerMove` forwarded from `XrInputCallback` impl.
- `Grep` — `controllerRayManager.release()` called at teardown.
- `Grep` — `Log\.d(` returns zero hits in touched files.

**Status:** `[ ]` not done

---

### Step 2.7 — Hilt: provide VrControllerRayManager (if DI-managed)

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/di/VrModule.kt`
**Depends on:** Step 2.6

**Prompt for developer:**

> Check `VrModule.kt`. If `VrHandRayManager` is `@Provides`-d there, add an analogous `@Provides` binding for `VrControllerRayManager`. If both managers are constructed inline in `VrPlayerActivity` / `OpenXrSessionManager`, no DI change is needed — skip this step (mark `[x]` with a note).

**Verification:**

- `Grep` — if `VrHandRayManager` is in `VrModule.kt`, then `VrControllerRayManager` must also be there. Otherwise, `VrControllerRayManager` found in whichever file constructs the hand manager.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 2.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] On device (Quest 3): a faint ray line is visible from Touch controller pointing at the UI overlay. (Manual test — document result in Blockers Log if unavailable.)
- [ ] `OpenXrNative.cpp` ≤ 3200 lines.
- [ ] `Grep` for `Log\.d(` in every Kotlin file touched returns zero hits.
- [ ] Dev log entries:

  ```powershell
  .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/cpp/OpenXrNative.cpp" "feature" "Phase 02: add controller aim-ray GL rendering and onControllerPointerMove JNI callback"
  .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/XrInputCallback.kt" "feature" "Phase 02: add onControllerPointerMove default method"
  .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrNative.kt" "feature" "Phase 02: expose nativeSetControllerRayEnabled JNI"
  .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrControllerRayManager.kt" "feature" "Phase 02: new VrControllerRayManager (NDC → MotionEvent bridge for Touch controller)"
  ```

---

## Handoff Notes to Next Phase

- Native now emits controller NDC via `onControllerPointerMove` each frame while controllers are the active source.
- `VrControllerRayManager` bridges controller NDC to the 2D decor view (same as hand ray). Phase 04 will also use the raw NDC for GL panel hit-testing.
- `g_controllerRayEnabled` allows Phase 03 to hide the decor-plane ray when the GL interactive panel takes over (pass `false` when GL panel is shown, `true` otherwise or always depending on UX decision).

---

## Rollback Plan

Revert phase commits. `OpenXrNative.cpp` backup at `temp/` allows restoring to pre-phase state if native changes cause crashes. `VrControllerRayManager` is a new file — delete it. `XrInputCallback` default method is backwards-compatible (no existing implementors break). `OpenXrNative.kt` external function removal requires matching native symbol removal — revert both together.

---

## Revision History

- **2026-04-26** — by `/spec-update` (`claude-sonnet-4-6`, focus: all, --tactical --apply-all)
  - ACCEPT applied: 2 (MD031 blank lines around code fence in Step 2.2 prompt; MD031 blank line before dev-log powershell block)
  - REVIEW applied: 0
  - DISCUSS proposed: 1 — see "Proposed Structural Changes" below.

## Proposed Structural Changes

### Proposal P-1 — Add OpenXrSessionManager.kt backup requirement to Step 2.6  (proposed 2026-04-26 by claude-sonnet-4-6)

**Status:** Proposed

**Summary:** Step 2.6 modifies `OpenXrSessionManager.kt` (512 lines, >500 LOC) without a backup step, violating project rule "file >500 LOC → timestamped backup in `temp/` before edit."
**Affected section:** Phase 02, Step 2.6 Prompt block
**Rationale:** Project backup rule in CLAUDE.md: any file exceeding 500 lines must be backed up before editing. `OpenXrSessionManager.kt` is 512 lines and Step 2.6 modifies it.
**Suggested edit:**
> Add to the start of the Step 2.6 Prompt block:
> "Before wiring: copy `OpenXrSessionManager.kt` to `temp/OpenXrSessionManager_BACKUP_<YYYYMMDD_HHmm>.kt` if no backup from this phase exists yet. Add a Verification predicate: `Glob` — `temp/OpenXrSessionManager_BACKUP_*.kt` exists."
**Next step:** user or another model to decide via `/spec-update --phase 02`.
