# Phase 03 - Send-to button

**Strategic spec:** [`../S0754_camera-orientation-send-settings-dialog.md`](../S0754_camera-orientation-send-settings-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Research input:** [`research/02__send-to-mechanism.md`](research/02__send-to-mechanism.md)
**Status:** ⬜ Not started
**Depends on:** none
**Blocks:** Phase 06
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Add a smaller "Send to.." button by the shutter that opens the existing recipients flow for the last captured file, reusing `SendToMenuManager` (no new mechanism).

---

## Prerequisites

- [ ] `research/02__send-to-mechanism.md` read.
- [ ] `CameraCaptureActivity.kt` > 500 LOC - back up to `temp/` before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/drawable/ic_camera_send_to.xml` | New | ≤ 20 |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | - |
| `app_v2/src/main/res/layout/activity_camera_capture.xml` | Modified | ≤ 440 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 740 |

> Camera is portrait-locked (Phase 01) - `res/layout-land/activity_camera_capture.xml` is dead (retired in Phase 06); do not edit the landscape variant. Landscape-parity exemption documented in Phase 06.

---

## Steps

### Step 03.1 - Send-to icon and label string

**Files:** `ic_camera_send_to.xml` (New), strings (EN/RU/UK)
**Depends on:** - start of phase

**Prompt for developer:**

> Create a 24dp "send/share" vector `ic_camera_send_to`. Add `camera_control_send_to` ("Send to" / "Отправить в" / "Надіслати в") via `set-android-string.ps1 -Action add`. Tone per `docs/COMMUNICATION_POLICY.md` §6.

**Verification:**

- `Glob` - `ic_camera_send_to.xml` exists.
- `Grep` - `camera_control_send_to` in all three `values*/strings.xml`.
- `check_strings_localized.ps1 -KeyPrefix "camera_control_send_to"` exits 0.

**Status:** `[ ]` not done

---

### Step 03.2 - Add the send-to button by the shutter

**Files:** `app_v2/src/main/res/layout/activity_camera_capture.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> In the action bar add a `MaterialButton` `@+id/btnCameraSendTo` (smaller than the shutter, e.g. 48-52dp) beside `btnCapturePhoto`, using the overlay button style, icon `ic_camera_send_to`, `android:visibility="gone"` (shown when a capture exists), focusable, contentDescription/tooltip `@string/camera_control_send_to`. No inline `#hex`.

**Verification:**

- `Grep` - `@+id/btnCameraSendTo` matches once in the layout.
- `.\a.ps1 fr` passes (exit 0).

**Status:** `[ ]` not done

---

### Step 03.3 - Wire send-to to the existing recipients flow

**Files:** `CameraCaptureActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Back up `CameraCaptureActivity.kt` (Rule 5). `@Inject` `SendToMenuManager` and the settings source (the existing settings repository / `AppSettings` provider used elsewhere). On `btnCameraSendTo` click: build `ShareableContent` from a `FileProvider` URI of `lastSavedPath` (mediaType = image/video per mode) and call `menuManager.show(this, content, settings)`. Show the button only after a capture is saved (`lastSavedPath != null`); register it for icon rotation (Phase 01). Reuse the existing flow - do not duplicate recipient logic.

**Verification:**

- `Grep` - `SendToMenuManager` injected and `.show(` called in `CameraCaptureActivity.kt`; `ShareableContent` built from `lastSavedPath`.
- `Grep` - `btnCameraSendTo.visibility` gated on `lastSavedPath`.
- `Glob` - fresh `temp/CameraCaptureActivity.kt.*.bak`.
- `.\a.ps1 fc` passes (exit 0).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entries (batched in Phase 06).

---

## Handoff Notes to Next Phase

Send-to reuses `SendToMenuManager`; the button shows after a capture. Phase 04 adds the camera capability probe for the pro-settings dialog.

---

## Rollback Plan

Revert the phase commit; restore `CameraCaptureActivity.kt` from `temp/`. No migration.
