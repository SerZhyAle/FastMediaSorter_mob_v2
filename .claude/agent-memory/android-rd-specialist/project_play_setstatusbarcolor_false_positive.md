---
name: play-setstatusbarcolor-false-positive
description: Play Console "deprecated setStatusBarColor / edge-to-edge" warning from Material BottomSheet is an unfixable static-analysis false-positive
metadata:
  type: project
---

Google Play Console's release warning "Your app uses deprecated APIs for edge-to-edge .. android.view.Window.setStatusBarColor .. com.google.android.material.bottomsheet.d.onCreate" is a permanent, non-fixable static-analysis false-positive for this app. Do NOT spend effort trying to clear it.

**Why:**
- Play Console's deprecated-API report is STATIC bytecode analysis of the uploaded AAB, not a runtime crawler. It flags the literal `invokevirtual Window.setStatusBarColor` instruction without evaluating the surrounding `if (Build.VERSION.SDK_INT < 35)` guard.
- Material `1.14.0` (pinned at app_v2/build.gradle.kts:1078) only GUARDS the call inside `EdgeToEdgeUtils.setStatusBarColor(Window,int)` - the invoke physically remains in the library `.aar` bytecode, so the scanner keeps reporting it. Verified by local javap disassembly of the cached 1.14.0 classes.jar.
- `1.14.0-alpha01` once removed the call (commit 603f1ef) but it was REVERTED (aaa40dbc) for breaking pre-API-35 bar coloring. `1.14.0` (2026-05-13) is the FINAL stable Material *Views* release; the repo is in maintenance mode (Compose-first). So upstream will never remove it for the Views stack.
- App's own code never calls raw `setStatusBarColor` (uses `WindowCompat.setDecorFitsSystemWindows`); the warning is 100% Material library bytecode. Theme.Material3 is the mandatory base theme, so the dependency cannot be dropped, and Play scans the whole shipped AAB regardless of reachability (dotnet/android #10304 confirms it is flagged even when fully absent from app source).
- Candidate fixes that all FAIL: newer Material (none), R8 dead-branch strip (minSdk 26 straddles 35, R8 keeps the branch), migrating bottom sheets to Compose ModalBottomSheet (Material .aar bytecode still ships), ProGuard keep/theme attr tricks. None make Play respect the runtime guard.
- Runtime impact on API 35+: zero - the guarded call never executes. Confirmed harmless (analogous Flutter warning called "a red herring .. will not impact users" by Android engineers, flutter/flutter #169810).

**Recurrence (RECORDED INCIDENT):** Both warnings keep coming back release after release and the owner has been told before that they would clear. They were flagged on release `2.60.6180.134` (= 2026-06-18, the LATEST prod release, NOT an old build), which ALREADY contains every edge-to-edge fix: spec S0221 (May 16-21, "play-console-deprecated-window-color-apis", Verified+Archived), Material upgrade 1.12->1.13 on 2026-04-13 done explicitly "to fix deprecated setStatusBarColor", the X.15 edge-to-edge migration (March), and per-screen WindowInsets across ~15 activities. So #2 has been "fixed" at least twice and STILL appears - this is the proof that it is a non-fixable static false-positive. Do NOT promise either warning will clear "next release"; that prediction already failed.

**How to apply:**
- When the owner pastes either warning again, lead with: these are NOT new, the latest release already has all the fixes, and they recur because of Play's static heuristics - not a regression. Do not re-research from scratch, do not open a spec ticket, do not promise a fix.
- WARNING #2 (Material BottomSheet setStatusBarColor): library false-positive, runtime no-op, not clearable. Material 1.14.0 (bumped 2026-06-19) does NOT remove it either (disassembled - guarded invoke still in bytecode; Material Views is in maintenance mode). Safe to ignore / acknowledge in console.
- WARNING #1 ("edge-to-edge may not display for all users"): app-side code is COMPLETE (audit of all BaseActivity subclasses = 0 screens at risk; BaseActivity.enableEdgeToEdge default-on + per-screen insets). It STILL appeared on the fully-fixed 2.60.6180.134, so it is largely a Play heuristic / informational signal (partly about Android < 15 backward-compat, per flutter/flutter #169810 - Android engineers call the analogue "a red herring .. will not impact users"). Treat as likely-permanent + non-blocking; do NOT predict it will clear. If the owner wants certainty there is no real bug, the right move is a visual on-device check on a real Android 15 device, not another release.
- First version format clarification: `2.60.<M><DD><H>.<mmm>` is date-based (e.g. `6180` = Jun 18); release tags `release/v2.60.*` are the source of truth for "is this build before/after a fix" - never guess from the version string alone.
- Sources: material-components-android issues #4507/#4626/#4732, commits c2051db/aaa40dbc/603f1ef; dotnet/android #10304; flutter/flutter #169810/#183372.
