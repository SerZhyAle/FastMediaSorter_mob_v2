---
name: bnut-sweep-plan
description: 2026-07-02 triage of all 65 BlockNeedUserTest tickets - batch plan at temp/spec_sweep_batch_plan.md, zero stale-close candidates
metadata:
  type: project
---

2026-07-02 workflow triage analyzed all 65 BlockNeedUserTest tickets (one agent per ticket, spec + code + probe check). Results:

- Batch plan: `temp/spec_sweep_batch_plan.md` - 24 batches: emulator 44 tickets (E1-E15), emulator-noLegal 8 (EN1-EN2), real-device 7 (R1-R4), real-device-noLegal 3 (RN1-RN2), VR Quest 3 (V1). Batch E1 (main-window panels) alone covers 14 tickets in one session.
- Stale audit verdict: ZERO tickets closable without a device test - every status note has genuinely untested behavior. Do not re-litigate; run the sweep instead.
- Env corrections vs status notes: S0715 says "connected phone" but emulator suffices (LeakCanary dumpHeap defaults FALSE - enable first, app_v2/build.gradle.kts:766 / DebugFeatureFlags.kt).
- Analyst agents wrongly claimed edge-gesture tickets need noLegal / `-P fms.edgeGestureOverlay=on`: the build.gradle.kts DEFAULT is off, but committed `gradle.properties` (lines 17-18) overrides BOTH capture flags to on - plain standard-debug ships edge gestures. Batch EN1 runs on a plain standard emulator.
- Probe-tag exceptions: S0764 (XML-only change, no code flow to probe) and S0715 (audit ticket, verdict = LeakCanary reports) have NO Timber.d probes while in BlockNeedUserTest - known, accepted.
- S0710 remaining defect-B path (permissionStop halt + advisory notification) has no S0710 probe - verify via the notification + worker logs.
- S0771/S0772/S0763 share one Quest 3 session and the same 7K asset; test S0772 (OOM) first - a crash blocks S0771 stereo judgment.

2026-07-03 update: owner ran the simple-visual blocks on his own S21+ and closed 23 tickets archive-as-verified in one session (Block A 16 + S0776/S0835 + Block D minus S0820). S0820 was held back (not archived) - migrated toggle value not applied after upgrade until manual re-toggle; parked as S0921. Owner closes in runbook-block batches (temp/DEVICE_SWEEP_S21_SIMPLE.md), on his own device, trusting his own pass - not waiting for my device sweep.

**Why:** 65 tickets = half the catalog stuck; the plan is the executable path out.
**How to apply:** when running /spec-sweep or /spec-test-device, start from the plan file, not from the raw catalog; delete this memory when the backlog is drained. When owner says "archive block X as verified", the closing pipeline is: remove that block's debug probe tags + orphaned Timber imports, compile, then Block->Implemented->Verified->Archived per ticket.
