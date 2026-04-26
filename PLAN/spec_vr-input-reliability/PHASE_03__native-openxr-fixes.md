# Phase 03 — Native OpenXR Fixes (Haptic + Interaction Profile)

**Strategic spec:** [../spec_vr-input-reliability.md](../spec_vr-input-reliability.md)
**Status:** Implemented
**Pillars:** В (Haptic path), Г (INTERACTION_PROFILE_CHANGED), Е (Touch Pro warning)

---

## Goal

Fix three native-layer issues in `OpenXrNative.cpp`:

1. Haptic error -16: Quest 3 uses `meta/touch_plus_controller` interaction profile, which is missing from the ActionSet bindings.
2. Unhandled XR event type 52: `INTERACTION_PROFILE_CHANGED` — add a handler that logs the new profile.
3. Touch Pro LOGW noise: downgrade the failure-to-register log from W to D.

**File:** `app_v2/src/vr/cpp/OpenXrNative.cpp`

---

## Steps

### Step 03.1 — Add `meta/touch_plus_controller` to `setupActionSet()` haptic bindings

In `setupActionSet()` the suggested interaction profiles array currently contains:
- `/interaction_profiles/oculus/touch_controller`
- `/interaction_profiles/oculus/touch_pro_controller`

Add a third entry:
- `/interaction_profiles/meta/touch_plus_controller`

Add the haptic output bindings for this profile exactly the same way they are added for `oculus/touch_controller` — using `/user/hand/left/output/haptic` and `/user/hand/right/output/haptic` paths. Follow the existing pattern: `xrStringToPath` + `XrActionSuggestedBinding` + `xrSuggestInteractionProfileBindings`.

**Verification:**

```text
Grep -pattern "meta/touch_plus_controller" -path "app_v2/src/vr/cpp/OpenXrNative.cpp" → ≥1 match
Grep -pattern "touch_plus_controller" -path "app_v2/src/vr/cpp" → ≥1 match
```

---

### Step 03.2 — Downgrade Touch Pro registration failure log W → D

In `setupActionSet()`, locate the log statement(s) emitted when `xrSuggestInteractionProfileBindings` fails for `oculus/touch_pro_controller`. Change the log level from `LOGW` to `LOGD`. Do NOT suppress or skip the registration attempt itself — only the log level changes.

**Verification:**

```text
Grep -pattern "touch_pro_controller" -path "app_v2/src/vr/cpp/OpenXrNative.cpp" → ≥1 match
Grep -pattern "LOGW.*touch_pro" -path "app_v2/src/vr/cpp/OpenXrNative.cpp" → 0 matches (no W-level logs for touch_pro)
```

---

### Step 03.3 — Handle `XR_TYPE_EVENT_DATA_INTERACTION_PROFILE_CHANGED` in `pollEvents()`

In `pollEvents()`, the `switch` (or if-chain) over `evt.type` currently falls to `default` for type 52. Add a case:

```cpp
case XR_TYPE_EVENT_DATA_INTERACTION_PROFILE_CHANGED: {
    LOGD("pollEvents: interaction profile changed — querying active profile");
    // Query the current active profile for each top-level path of interest.
    // Supported top-level paths: /user/hand/left, /user/hand/right
    const char* topLevelPaths[] = {"/user/hand/left", "/user/hand/right"};
    for (const auto* path : topLevelPaths) {
        XrPath topLevel = XR_NULL_PATH;
        xrStringToPath(instance_, path, &topLevel);
        XrInteractionProfileState state{XR_TYPE_INTERACTION_PROFILE_STATE};
        XrResult res = xrGetCurrentInteractionProfile(session_, topLevel, &state);
        if (XR_SUCCEEDED(res) && state.interactionProfile != XR_NULL_PATH) {
            uint32_t len = 0;
            char profileName[256] = {};
            xrPathToString(instance_, state.interactionProfile, sizeof(profileName), &len, profileName);
            LOGD("pollEvents: active profile for %s = %s", path, profileName);
        }
    }
    break;
}
```

Place this case before the `default` case. Use `instance_` and `session_` member names that match the existing code — check the actual names used in the file.

**Verification:**

```text
Grep -pattern "XR_TYPE_EVENT_DATA_INTERACTION_PROFILE_CHANGED" -path "app_v2/src/vr/cpp/OpenXrNative.cpp" → ≥1 match
Grep -pattern "interaction profile changed" -path "app_v2/src/vr/cpp/OpenXrNative.cpp" → ≥1 match
Grep -pattern "unhandled event type=52" -path "app_v2/src/vr/cpp" → 0 matches (type 52 now handled)
```

---

## Phase Done Criteria

- [ ] `meta/touch_plus_controller` appears in `setupActionSet()` haptic binding suggestions.
- [ ] No `LOGW` referencing `touch_pro` in `OpenXrNative.cpp`.
- [ ] `XR_TYPE_EVENT_DATA_INTERACTION_PROFILE_CHANGED` has an explicit case in `pollEvents()`.
- [ ] The `default` branch no longer logs type 52 as "unhandled".
