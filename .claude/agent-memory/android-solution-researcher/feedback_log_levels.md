---
name: feedback-log-levels
description: When reading existing Timber call sites, Timber.e is a strong claim "dev must act"; flag any Timber.e on an expected device-capability path as a tone bug in the research report
metadata:
  type: feedback
---

When reading existing `Timber` call sites during research, treat the log level as a deliberate signal: `Timber.e` (ERROR-level red log) is reserved for situations where the program produces an error the developer must investigate and fix. `Timber.e` is NOT for "device does not support feature" / "expected fallback" / "known runtime constraint".

**Why:** ERROR-level lines should be a signal, not noise. When non-error situations (e.g. native library absent on non-arm64 device, optional capability missing, graceful degradation paths) emit ERROR logs, the signal is diluted and a real bug becomes harder to spot. A research report that treats `Timber.e` count as "number of crashes" would over-count noise and miss the real signal.

**How to apply:**
- When grepping for `Timber.e` during research, categorise each hit:
  - Genuine misconfiguration the dev should react to (e.g. `SecurityException` blocking a load that should have worked, schema migration crash, unexpected `IllegalStateException` in DI) → cite as a real error site.
  - Expected device-capability mismatch (e.g. `UnsatisfiedLinkError` for a deliberately-omitted ABI slice, missing optional Vulkan extension, headset features on phone) emitted at `Timber.e` → flag in the research report under "Risks Identified" as a **log-level tone bug** (Low severity, tech debt). These should have been `Timber.i` or `Timber.d`.
  - Cleanly-handled exception in a normal-flow `runCatching` (file not found, network unreachable, user cancelled, peer disconnected) emitted at `Timber.e` → also flag as a tone bug; appropriate level is `Timber.w` or `Timber.d`.
- Catch-blocks for `external fun` native calls AFTER a successful `System.loadLibrary` *can* stay at `Timber.e` - at that point an `UnsatisfiedLinkError` IS abnormal. Cite the `nativeAvailable` short-circuit pattern (if present) as the correct guard.
- Do NOT propose log-level changes in the research report body (that is a writer-agent task) - cite the finding under Risks and let the spec author decide whether to schedule a fix.
