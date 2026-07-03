---
name: play-capture-family-status
description: Capture family SHIPPED to Play in 2.60.6270.802 (2026-06-27); specialUse + mediaProjection FGS both ACCEPTED by Play review - precedent for future FGS declarations
metadata:
  type: project
---

Capture-family migration noLegal -> Play standard is COMPLETE. Release `2.60.6270.802` (versionCode `260627080`) passed Play review and went live 2026-06-27 (177 countries / 20,458 devices). S0672/S0724 closed and Archived 2026-06-27; their debug tags are removed.

Durable precedent facts for future Play submissions:
- `FOREGROUND_SERVICE_SPECIAL_USE` was ACCEPTED by Play review - despite being the most-rejected FGS type - with a VISIBLE grey strip (ADR-3) + user-initiated/user-perceptible `specialUse` justification wording. No demo video was required.
- `FOREGROUND_SERVICE_MEDIA_PROJECTION` had already passed earlier (release `2.60.6251.711`, verified 2026-06-26).
- The Play Console App-content "Foreground service permissions" declaration was filled once by the owner; subsequent API commits pass with no HTTP 403.
- QS-tile fallback (`ScreenshotGestureTileService`, `src/standardEdgeTile/`, gated `fms.edgeGestureTile=off`) was built as the rejection contingency and never needed - it remains available for future FGS rejections.
- Shipped flag state: `fms.screenCapture=on` + `fms.edgeGestureOverlay=on` in `gradle.properties` - see [[screencapture-split-standard-vs-nolegal]].

**Why:** what Play review actually accepts for risky FGS types is not derivable from code and saves re-research on the next FGS-affecting submission.

**How to apply:** when adding or altering an FGS type / Play-sensitive permission, reuse the visible-affordance + user-initiated-wording playbook above; check rollout state read-only via [[reference_play_console_api_access]].
