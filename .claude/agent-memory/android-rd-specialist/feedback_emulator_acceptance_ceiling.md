---
name: emulator-acceptance-ceiling
description: Ticket-acceptance classes an emulator can never prove (GLES video effects, multi-window, non-writable folders, FLAG_SECURE, disabled launcher host, LOW-RAM gating) - scope sweeps around them
type: feedback
---

Before queueing a `BlockNeedUserTest` ticket into a `/spec-sweep`, check its acceptance against this
list. These are **structural ceilings of the emulator**, not infra flakiness - rebooting, retrying, or
a better script will not get past them. Measured across 43 classified tickets on 2026-07-30: only
about **19 of 67** open `BlockNeedUserTest` tickets were automatable at all.

Distinct from [[avd-device-sweep-gotchas]], which lists infra traps that *do* have workarounds.

1. **Media3 `setVideoEffects` is a no-op on emulator GLES.** Any acceptance phrased as "the video
   visibly rotates / is visibly transformed" cannot render (S0995). Needs real GPU hardware.
2. **`LAUNCH_ADJACENT` collapses into a single instance.** Two simultaneous player windows cannot
   exist, so multi-window resource ref-counting is unprovable (S0896). Needs a real split-screen or
   freeform device.
3. **`chmod 555` does not stick on FUSE storage.** A genuinely non-writable destination folder cannot
   be manufactured on-device, so "reject a non-writable target" criteria have no fixture (S1009).
4. **`FLAG_SECURE` screens cannot be screenshotted.** The companion QR share screen is one, and its
   acceptance also wants the code scanned by a second real camera (S1039).
5. **`LauncherHomeActivity` ships `android:enabled="false"` and `pm enable` is refused to the shell.**
   Every launcher-hosted picker is therefore unreachable unless the app is given the HOME role - which
   you must not do on a sweep-shared emulator without saying so (hit on S1286).
6. **`MemoryTier.detect` reports LOW RAM at 2.42 GB on the project AVDs.** Features gated behind a
   memory tier silently take the degraded path, so their animation/quality criteria never execute
   (S1026). This one is fixable - build an AVD with RAM >= 3 GB.
7. **Live-stream acceptance needs a live stream.** ICY/HLS metadata, Icecast status endpoints and real
   AAC hardware decode paths have no honest local fixture; a static fixture passes degenerately and
   proves nothing (S1137, S1142, S1146, S1158).
8. **Multi-lens optics, AOD and Chromecast are hardware.** Zoom-pill rounding, sub-1x lens
   enumeration, always-on-display strips and cast receivers all need the real thing (S1189, S1260,
   S1261, S1167, S1155).

**Why:** a sweep that queues these tickets burns a full build-install-drive-harvest cycle per ticket
and still ends INCONCLUSIVE, which is worse than not running it - it also tempts a premature
`/spec-check` that would strip the debug probes.

**How to apply:** classify before running. Route ceiling-bound tickets to the owner's manual test list
with the physical reason stated, and spend sweep cycles only on the direct-emulator and
local-service classes. When a ticket is close to the line, say which single constraint decided it.
