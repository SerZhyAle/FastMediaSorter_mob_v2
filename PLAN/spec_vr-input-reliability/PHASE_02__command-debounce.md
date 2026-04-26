# Phase 02 — VR Command Debounce

**Strategic spec:** [../spec_vr-input-reliability.md](../spec_vr-input-reliability.md)
**Status:** Implemented
**Pillar:** Б — VR Command Debounce (ADR-3)

---

## Goal

A single physical button press must result in exactly one command dispatch, regardless of how long the user holds the button. Implement a centralized debouncer for toggle-class VR commands with configurable per-type suppression windows.

Volume (150 ms) and zoom (120 ms) are already rate-limited in `VrControllerInputManager` and are excluded from the new debouncer.

---

## Steps

### Step 02.1 — Create `VrCommandDebouncer`

**File (new):** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrCommandDebouncer.kt`

```kotlin
package com.sza.fastmediasorter.vr.helpers

import com.sza.fastmediasorter.vr.VrCommand
import javax.inject.Inject

internal class VrCommandDebouncer @Inject constructor() {

    private val lastTimestamps = mutableMapOf<VrCommand, Long>()

    fun shouldDispatch(command: VrCommand, source: VrCommandSource): Boolean {
        if (source != VrCommandSource.CONTROLLER) return true
        val windowMs = windowFor(command)
        if (windowMs <= 0L) return true
        val now = System.currentTimeMillis()
        val last = lastTimestamps[command] ?: 0L
        if (now - last < windowMs) return false
        lastTimestamps[command] = now
        return true
    }

    private fun windowFor(command: VrCommand): Long = when (command) {
        VrCommand.PLAY_PAUSE,
        VrCommand.OPEN_FILE,
        VrCommand.TOGGLE_HUD,
        VrCommand.NEXT_FILE,
        VrCommand.PREV_FILE -> 500L
        else -> 0L
    }
}
```

Adjust the `VrCommand` constants in `windowFor()` to match the actual enum values in the codebase. If `VrCommandSource` does not exist as a type, use whatever type the `source` parameter has in `VrControllerInputManager.dispatchCommand()`.

**Verification:**

```text
Glob app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrCommandDebouncer.kt → ≥1 result
Grep -pattern "shouldDispatch" -path "app_v2/src/vr" → ≥1 match
```

---

### Step 02.2 — Inject `VrCommandDebouncer` into `VrControllerInputManager`

**File:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrControllerInputManager.kt`

Add `VrCommandDebouncer` to the constructor. `VrControllerInputManager` must already use `@Inject constructor` — add the debouncer as a new parameter. Hilt will wire it automatically.

```kotlin
internal class VrControllerInputManager @Inject constructor(
    // ... existing parameters ...
    private val commandDebouncer: VrCommandDebouncer,
) { ... }
```

**Verification:**

```text
Grep -pattern "VrCommandDebouncer" -path "app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrControllerInputManager.kt" → ≥1 match
```

---

### Step 02.3 — Apply debounce gate in `dispatchCommand()`

**File:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrControllerInputManager.kt`

In `dispatchCommand(command, source)`, add the debounce check immediately before the `onCommand(command, source)` call (after the existing volume and zoom rate-limit blocks):

```kotlin
if (!commandDebouncer.shouldDispatch(command, source)) {
    Timber.d("VrControllerInputManager: debounced %s", command)
    return
}
onCommand(command, source)
```

Do NOT remove or modify the existing volume/zoom rate-limit blocks — they remain as-is.

**Verification:**

```text
Grep -pattern "commandDebouncer.shouldDispatch" -path "app_v2/src/vr" → ≥1 match
Grep -pattern "debounced" -path "app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrControllerInputManager.kt" → ≥1 match
```

---

## Phase Done Criteria

- [ ] `VrCommandDebouncer.kt` exists in `app_v2/src/vr/.../vr/helpers/`.
- [ ] `VrCommandDebouncer.shouldDispatch()` returns `false` for a second identical toggle command within 500 ms.
- [ ] `VrControllerInputManager` constructor includes `VrCommandDebouncer`.
- [ ] `dispatchCommand()` calls `commandDebouncer.shouldDispatch()` before `onCommand()`.
- [ ] Volume and zoom rate-limit blocks are untouched.
