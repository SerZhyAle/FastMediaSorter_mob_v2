---
name: play-capture-family-status
description: noLegal->Play capture migration - screenshots DONE/Verified (2.60.6251.711); edge-strip + QS-tile code-complete (S0672 BlockNeedUserTest) + visible-grey strip (S0724); device-test + visible-strip specialUse video/submission next
metadata:
  type: project
---

State of the "bring the screen-capture family from noLegal to the Google Play standard build" initiative (owner's goal). Two halves:

**1. Screenshots / MEDIA_PROJECTION - DONE & live.** Release `2.60.6251.711` (versionCode `260625171`) - first to declare `FOREGROUND_SERVICE_MEDIA_PROJECTION` on a reviewed track - passed Play review, 100% production rollout (verified 2026-06-26 via `temp/play_status.py`). **S0629 Verified.** The §5.1 demo-video gap was moot (review passed without it).

**2. Edge gesture panel / SPECIAL_USE - CODE-COMPLETE, not yet on Play.** Implemented via `/spec-dev S0672` on 2026-06-26, status **BlockNeedUserTest** (all 3 phases Done):
- Phase 01 - `OverlayHostService` FGS-start guarded against `ForegroundServiceStartNotAllowedException` (Android-15 backstop); the `specialUse` subtype reworded to a user-initiated/user-perceptible justification.
- Phase 02 - QS-tile fallback `ScreenshotGestureTileService` behind an independent `fms.edgeGestureTile=off` gate (new `src/standardEdgeTile/` source set), launches the capture consent path with no specialUse/overlay - the contingency if Play rejects the strip.
- Phase 03 - ALL_FEATURES record, catalog regen, noLegal silent-path confirmed untouched.
- Both triggers gated OFF by default (`fms.edgeGestureOverlay=off`, `fms.edgeGestureTile=off`) - opt-in per build pending Play submission.

**Visible strip (S0724) - owner-implemented 2026-06-26, BlockNeedUserTest.** Option "Show the left-edge gesture strip" (Settings -> Operations -> Screen gestures), default OFF; when ON the strip becomes opaque grey RGB(128,128,128) instead of transparent. Owner reworked `OverlayHostService.start(context, stripVisible)` + `EXTRA_STRIP_VISIBLE` + `overlayManager.setStripVisible` for live recolour (coexists with the S0672 guard/tag).

**KEY DECISION - ADR-3 (owner, 2026-06-26): submit a VISIBLE grey strip to Play, NOT the invisible one** (revises the original ADR-1 "submit invisible as-is"). A visible overlay is exactly what `research/02` prescribed: it (a) defends the `specialUse` declaration (reviewer sees a user-perceptible strip, not an idle invisible service) and (b) satisfies the Android-15 visible-overlay FGS-start exemption. So the visible strip removes BOTH §1 stop-factors, not just softens them. The Play demo video + submission must show the visible grey strip - i.e. depend on S0724 being enabled in the submission build.

**Why:** owner lost track across many Block* tickets; consolidation (option A) keeps S0672 as the single umbrella. Folded/Archived: S0671 (engine, into S0672), S0629 Verified, S0630/S0621/S0418. `S0680` (gesture crop+share) and `S0713` (existing QS tiles missing the `QS_TILE` bind action - parked then owner-implemented) stay separate.

**How to apply (next steps when owner returns from the audit):**
- Project is in AUDIT mode as of 2026-06-26: owner parking many small-fix tickets, fixing them all, THEN releasing. Do not start a release mid-audit.
- The remaining S0672 work is on-device: build with `-Pfms.edgeGestureOverlay=on` (+ S0724 visible-strip option ON, + optionally `-Pfms.edgeGestureTile=on`) on a real Android-15 device; verify the strip path (logcat `S0672: edge-gesture overlay strip started`) and tile path (`S0672: QS tile clicked`), then record the §6.2 demo video showing the VISIBLE grey strip -> swipe -> consent -> capture.
- Agent CAN drive + record the video on a real device (build/install/consent/screenrecord); the one unknown is whether `adb input swipe` reaches the `TYPE_APPLICATION_OVERLAY` strip on real hardware (emulator can't) - test with one dry swipe first; if it fails, owner does just the physical swipe while the agent records.
- SPECIAL_USE is still the most-rejected FGS type (S0672 §7); fallback if rejected = the QS-tile build (`-Pfms.edgeGestureTile=on`, no specialUse), strip back to noLegal.
- Read-only Play access: [[reference_play_console_api_access]]. Build gotchas hit this session (chaquopy flag breaks noLegal compile; manifest `-P` injection needs `--rerun-tasks`): [[project_build_gotchas]]. Capture-suite flavor nuance: [[project_screencapture_nolegal_only]].
- Decays fast: re-verify spec statuses + production state before acting.
