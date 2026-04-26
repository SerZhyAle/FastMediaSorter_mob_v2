# Phase 04 — HUD Lifecycle Guard

**Strategic spec:** [../spec_vr-input-reliability.md](../spec_vr-input-reliability.md)
**Status:** Implemented
**Pillar:** Д — Lifecycle guard for VrHudRenderer (ADR-2)

---

## Goal

Prevent `setHudLayerVisible()` from calling into the native XR layer after the XR session has been released. The session manager already maintains an `AtomicBoolean running` that is set to `false` during `release()`. Guard the call with a check on this flag.

---

## Steps

### Step 04.1 — Guard `setHudLayerVisible()` with `running.get()` in `OpenXrSessionManager`

**File:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt`

Current implementation (approximately lines 337–339):

```kotlin
fun setHudLayerVisible(visible: Boolean) {
    OpenXrNative.nativeSetHudLayerVisible(visible)
}
```

Replace with:

```kotlin
fun setHudLayerVisible(visible: Boolean) {
    if (!running.get()) {
        Timber.d("OpenXrSessionManager: setHudLayerVisible(%s) ignored — session not running", visible)
        return
    }
    OpenXrNative.nativeSetHudLayerVisible(visible)
}
```

Use the same `running` `AtomicBoolean` already present in the class (also used in `createHudSwapchain()` and `release()`). Do not introduce a new field.

**Verification:**

```text
Grep -pattern "setHudLayerVisible" -path "app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt" → ≥1 match
Grep -pattern "running.get\(\)" -path "app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt" → ≥2 matches (createHudSwapchain + setHudLayerVisible)
```

---

## Phase Done Criteria

- [ ] `setHudLayerVisible()` contains a `running.get()` guard that returns early when the session is not running.
- [ ] The guard uses the existing `running` `AtomicBoolean` — no new field introduced.
- [ ] A `Timber.d` log is emitted when the call is suppressed, to confirm the guard fires during shutdown.
