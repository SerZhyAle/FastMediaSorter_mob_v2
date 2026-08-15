# S0203 — VrPermissionBridgeFragment must be public

## Summary

Fix `IllegalStateException` crash on VR passthrough camera capture when requesting the
`horizonos.permission.HEADSET_CAMERA` permission. The bridge fragment was declared
`private class` (Kotlin top-level), which compiles to package-private in JVM bytecode.
`FragmentTransaction.doAddOp` rejects non-public fragments because the system cannot
re-instantiate them from saved instance state across config change / process death.

## Affected Files

- `app_v2/src/vr/java/com/sza/fastmediasorter/vr/capture/VrBrowsePassthroughCaptureManager.kt`

## Goals

- Remove the `private` modifier from `VrPermissionBridgeFragment` so the class becomes
  top-level public, satisfying `FragmentTransaction.doAddOp` visibility check.
- Keep the behaviour and lifetime of the fragment unchanged.

## Non-Goals

- No new functionality.
- No change to the camera capture pipeline.
- No fix for the latent issue where `onGranted` / `onDenied` lambdas are lost after
  process death (separate ticket if/when needed).

## Risk

- Trivial visibility change; no API surface impact (the class is consumed inside the
  same file via direct construction).
- Build verified on `noLegalDebug` (BUILD SUCCESSFUL).

## Origin

Production crash report `2026-05-14 21:15:13.212` on Samsung SM-S731B (Android 16, SDK 36),
flavor `noLegal`, version `2.60.5141.916-NoLegal-DEBUG`. Stack trace points to
`VrBrowsePassthroughCaptureManager.launch:108` → `FragmentTransaction.add` →
`doAddOp` → `IllegalStateException`.

## Status History

- 2026-05-14 — Implemented (one-line modifier change + WHY comment).
- 2026-05-15 — Verified (audit PASS 6/0/0).

## Last Audit

**Date:** 2026-05-15
**Mode:** strategic
**Flags:** —
**Outcome:** Verified
**Counts:** PASS 6 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

### Manual / on-device

- [ ] Reproduce original crash scenario on Samsung SM-S731B (Android 16): trigger VR passthrough camera capture, request HEADSET_CAMERA permission, confirm no `IllegalStateException` from `FragmentTransaction.doAddOp`.

