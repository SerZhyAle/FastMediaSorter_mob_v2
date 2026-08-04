---
name: camera-session-manager-function-ceiling
description: CameraCaptureSessionManager lives against detekt's 40-function TooManyFunctions ceiling - S1262 bought 6 slots back by turning the current* getters into val properties
metadata:
  type: project
---

**Current state (2026-07-31, after S1262 phase 03): 36 functions of 40.** There is room again, but the class is 1000+ LOC and will re-approach the ceiling with the next feature.

`ui/cameracapture/helpers/CameraCaptureSessionManager.kt` used to sit at **exactly 39-40 functions**, and detekt's `TooManyFunctions` threshold for classes is 40 (it fails **at** 40, not above it). Adding even one small private helper tripped the gate.

**The cheap escape, now proven:** the six trivial `current*()` getters (`currentExposureCompensationIndex`, `currentWhiteBalanceMode`, `currentManualIso`, `currentManualShutterNs`, `currentAspectRatio`, `currentResolution`) are now `val` properties with `get() =` bodies. Property accessors do not count toward `TooManyFunctions`, so that freed six slots. The scare about "changes every call site" was wrong: there were **10 call sites in 3 files** (`CameraCaptureActivity`, `CameraSettingsCallbackHandler`), a five-minute mechanical edit. `currentZoomRatio()` and `currentLinearZoom()` are still functions and are the next two slots if ever needed.

**Why:** the class accumulated the whole CameraX session surface - bind, lens switch, mode switch, night, HDR, macro, zoom (optical + digital), torch, focus, capture, JPEG crop, EXIF restore, recording start/pause/resume/stop. S1071 and S1185 already shaved pieces off it (`restoreExif`, `handleFinalizeError`, `VideoDigitalZoomProcessor`) by moving logic to **private top-level functions in the same file** or to new classes - top-level functions do not count toward a class's function total.

**How to apply:**

- Before adding a helper method here, expect the gate to reject it. Either fold the logic into an existing function, or make it a private **top-level** function in the file (only works when it reads no session state - the existing top-level ones are pure).
- State-reading helpers have no cheap escape: on S1189 phase 03 a `bindFallbackLens` helper had to be inlined into the bind failure branch, and on phase 04 a `rebindForMacro` helper had to be collapsed into a single shared rebind at the end of `applyMacro`. Both cost a full compile + detekt cycle to discover.
- The real fix is decomposition (extract a lens/binding collaborator). Treat that as its own ticket, not as a side effect of a feature change.
- A `ReturnCount` finding is the other thing this file punishes: three `return`s in one function fails the scoped gate. Fold the guards into one branch instead of adding an early exit.

Related: [[detekt-scoped-gate-surfaces-untouched-debt]], [[detekt-baseline-signature-resurface]].
