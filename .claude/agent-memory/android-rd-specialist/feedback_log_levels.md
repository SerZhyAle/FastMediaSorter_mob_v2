---
name: feedback-log-levels
description: Reserve Timber.e for genuine programmatic errors that the developer must react to; never for expected device-capability fallbacks
metadata:
  type: feedback
---

`Timber.e` (ERROR-level red log) is reserved for situations where the program itself produces an error that the developer must investigate and fix. It is not a level for "device does not support feature" / "expected fallback" / "known runtime constraint".

**Why:** ERROR-level lines should be a signal, not noise. When non-error situations (e.g. native library absent on non-arm64 device, optional capability missing, graceful degradation paths) emit ERROR logs, the signal is diluted and a real bug becomes harder to spot. The user (developer) wants to scan logs and see only things he needs to act on.

**How to apply:**
- Expected device-capability mismatch (e.g. `UnsatisfiedLinkError` for a deliberately-omitted ABI slice, missing optional Vulkan extension, headset features on phone, etc.) → `Timber.i` or `Timber.d`. One line is enough; no stack trace.
- Genuine misconfiguration we *should* react to (e.g. `SecurityException` blocking a load that should have worked, schema migration crash, unexpected `IllegalStateException` in DI) → `Timber.e` with stack trace.
- Cleanly-handled exception in a normal-flow `runCatching` (file not found, network unreachable, user cancelled, peer disconnected) → `Timber.w` if worth noting, `Timber.d` if routine, never `Timber.e`.
- Catch-blocks for `external fun` native calls AFTER a successful `System.loadLibrary` *can* stay at `Timber.e` - at that point an `UnsatisfiedLinkError` IS abnormal. But add a short-circuit `nativeAvailable` flag so they do not fire when the library deliberately did not load.

When auditing existing `Timber.e` calls during any work on a touched file: if the catch covers a documented graceful-fallback path, downgrade it.
