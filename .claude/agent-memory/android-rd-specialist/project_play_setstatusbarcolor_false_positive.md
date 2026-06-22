---
name: play-edge-to-edge-warnings-status
description: Play Console edge-to-edge warnings - #2 setStatusBarColor was FIXED by Material 1.14.0 (cleared on release 6200.317); #1 may-not-display is informational + app-side-complete
metadata:
  type: project
---

Two distinct Google Play Console "recommended actions" appear on FastMediaSorter releases targeting SDK 35. Status as of release `2.60.6200.317` (2026-06-20, Material 1.14.0):

**WARNING #2 - deprecated `Window.setStatusBarColor` from `com.google.android.material.bottomsheet.d.onCreate`: CLEARED / WAS FIXABLE.**
- It DISAPPEARED on release `2.60.6200.317` once Material was bumped to 1.14.0 (commit 2606192155, Jun 19). On the prior release `2.60.6180.134` (Material 1.13.0) it was still present.
- Material 1.14.0 wraps the call in `if (SDK_INT < 35)` (EdgeToEdgeUtils), and Play's scanner DOES honor that guard for this deprecation check - which is why Material guarded it instead of deleting it.
- **CORRECTION of an earlier WRONG conclusion:** I previously concluded (from GitHub issues #4507 etc. + bytecode still containing the invoke) that #2 was a permanent unfixable static false-positive and that 1.14.0 would NOT help. That was WRONG - empirically the 1.14.0 guard cleared it. The "bytecode still contains the invokevirtual, so Play flags it statically" argument did not hold.

**WARNING #1 - "edge-to-edge may not display for all users": app-side COMPLETE, but warning is informational and persists.**
- App code is complete: audit found 0 BaseActivity subclasses at risk; `enableEdgeToEdge()` default-on in BaseActivity + per-screen WindowInsets; themes have no `statusBarColor` / `windowTranslucentStatus` / `windowOptOutEdgeToEdgeEnforcement` (only `enforceStatusBarContrast=true` in values-night-v29, which is the safe scrim, not an opt-out).
- It STILL appeared on the fully-fixed `2.60.6200.317`. Per Google/AndroidX + Flutter team (flutter/flutter #169810, answered by Android engineers): this one is informational, does NOT impact users, is partly internal-library/heuristic-driven, and "disappears naturally when Google/AndroidX update internal library code" - not reliably clearable by a specific app change.
- Do NOT set `windowOptOutEdgeToEdgeEnforcement` to dodge it: that opts OUT of edge-to-edge and is deprecated + disabled for API 36.

**Why (lesson):** I declared #2 "unfixable" with high confidence and the very next release disproved it. Do not pronounce a Play warning permanently unfixable from GitHub-issue reasoning + bytecode inspection alone - confirm empirically across an actual release first.

**How to apply:**
- When the owner pastes these: #2 is FIXED (gone since Material 1.14.0 / release 6200.317) - if it reappears, first check the Material version didn't regress below 1.14.0. #1 is informational + app-side-complete; do NOT promise it clears next release, do NOT add opt-out flags; the only way to rule out a real visual bug behind #1 is an on-device Android 15 visual check.
- Version format `2.60.<M><DD><H>.<mmm>` is date-based (`6180`=Jun 18, `6200`=Jun 20); use `release/v2.60.*` tags + the release worktree `P:/ANDROID/FastMediaSorter_release` as source of truth for what actually shipped - never infer "before/after a fix" from the version string alone.
- Sources: flutter/flutter #169810 / #183372; developer.android.com edge-to-edge + behavior-changes-15/16; material-components-android #4507.
