---
name: camera-session-manager-function-ceiling
description: CameraCaptureSessionManager sits exactly on detekt's 40-function TooManyFunctions ceiling - any new private helper fails the gate until the class is decomposed
metadata:
  type: project
---

`ui/cameracapture/helpers/CameraCaptureSessionManager.kt` has **exactly 40 functions**, and detekt's `TooManyFunctions` threshold for classes is also 40 (it fails at 40, not above it). Adding even one small private helper trips the gate.

**Why:** the class accumulated the whole CameraX session surface - bind, lens switch, mode switch, night, HDR, macro, zoom (optical + digital), torch, focus, capture, JPEG crop, EXIF restore, recording start/pause/resume/stop. S1071 and S1185 already shaved pieces off it (`restoreExif`, `handleFinalizeError`, `VideoDigitalZoomProcessor`) by moving logic to **private top-level functions in the same file** or to new classes - top-level functions do not count toward a class's function total.

**How to apply:**

- Before adding a helper method here, expect the gate to reject it. Either fold the logic into an existing function, or make it a private **top-level** function in the file (only works when it reads no session state - the existing top-level ones are pure).
- State-reading helpers have no cheap escape: on S1189 phase 03 a `bindFallbackLens` helper had to be inlined into the bind failure branch, and on phase 04 a `rebindForMacro` helper had to be collapsed into a single shared rebind at the end of `applyMacro`. Both cost a full compile + detekt cycle to discover.
- The real fix is decomposition (extract a lens/binding collaborator). Treat that as its own ticket, not as a side effect of a feature change.
- The six `current*()` accessors (`currentExposureCompensationIndex`, `currentWhiteBalanceMode`, `currentManualIso`, `currentManualShutterNs`, `currentAspectRatio`, `currentResolution`) are the obvious candidates to convert to `val` properties, which would drop the count by six - but that changes every call site, so it belongs in a decomposition ticket rather than a feature branch.

Related: [[detekt-scoped-gate-surfaces-untouched-debt]], [[detekt-baseline-signature-resurface]].
