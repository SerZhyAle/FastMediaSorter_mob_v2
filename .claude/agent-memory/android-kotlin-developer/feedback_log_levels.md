---
name: feedback_log_levels
description: Reserve Timber.e for genuine programmatic errors that the developer must react to; never for expected device-capability fallbacks
metadata:
  type: feedback
---

`Timber.e` (ERROR-level red log) is reserved for situations where the program itself produces an error that the developer must investigate and fix. It is not a level for "device does not support feature" / "expected fallback" / "known runtime constraint".

**Why:** ERROR-level lines should be a signal, not noise. When non-error situations (e.g. native library absent on non-arm64 device, optional capability missing, graceful degradation paths) emit ERROR logs, the signal is diluted and a real bug becomes harder to spot. The user (developer) wants to scan logs and see only things he needs to act on.

**How to apply:**
- When writing a catch-block for a documented graceful-fallback path (`UnsatisfiedLinkError` for a deliberately-omitted ABI slice, optional Vulkan extension absent, XR features on a phone, a network provider that the current flavor does not ship), log at `Timber.i` or `Timber.d`. One line is enough; no stack trace.
- Use `Timber.e` (with stack trace) only for genuine misconfiguration the developer must act on: unexpected `SecurityException`, schema migration crash, `IllegalStateException` in DI graph, native call failing AFTER the library successfully loaded.
- Cleanly-handled exceptions in normal-flow `runCatching` blocks (file not found, network unreachable, user cancelled, peer disconnected) → `Timber.w` if worth noting, `Timber.d` if routine, never `Timber.e`.
- When auditing existing `Timber.e` calls in a file I'm touching for another reason: if the catch covers a documented graceful-fallback path, downgrade the level in the same commit. The fix is mechanical and the surrounding context is already in my head.
