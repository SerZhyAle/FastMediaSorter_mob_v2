---
name: probe-tag-multiline-grep
description: Timber.d("Sxxxx:" probes may be line-wrapped for detekt 120-char rule - single-line grep misses them
metadata:
  type: feedback
---

When checking or removing Sxxxx debug probes, the strict single-line pattern `Timber.d("Sxxxx:` misses probes wrapped for the detekt 120-char line limit: `Timber.d(` on one line, `"Sxxxx: .."` on the next. Confirmed real cases: S0765 (CameraCaptureSessionManager.kt:462) and S0771 (DiagnosticXrActivity.kt:529).

**Why:** detekt-clean-first (S0826) forces wrapping long probe lines, so the canonical grep from CLAUDE.md §2 is no longer sufficient; a tag-removal pass that trusts it leaves orphaned probes behind after leaving BlockNeedUserTest.
**How to apply:** on any probe audit/removal, grep for `"Sxxxx:` (quote + id, without the Timber.d prefix) or use a multiline-aware search; verify zero hits for both patterns before committing the status transition.
