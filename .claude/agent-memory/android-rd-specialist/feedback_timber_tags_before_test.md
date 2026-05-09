---
name: Never remove Timber.d("Sxxxx:") before on-device testing
description: Debug tags must stay until user confirms the feature works on device — removing early prevents debugging
type: feedback
---

Do NOT remove `Timber.d("Sxxxx:` tags until the user has manually tested the feature on device and confirmed it works.

**Why:** The tags are the primary tool for verifying code paths in logcat during testing. Removing them before testing leaves the user unable to diagnose problems if the feature misbehaves.

**How to apply:** Phase 05 / cleanup steps that include Timber tag removal must be blocked until the user explicitly confirms on-device test passed. Even if spec phase prerequisites list "manual test completed" — treat it as a hard gate. Set the phase step to `[ ] not done` and add a ⚠️ note. Do not remove tags speculatively based on "I just wrote the code, it must be correct." The build passing is NOT a substitute for device testing.
