---
name: play-edge-to-edge-warnings-status
description: Play Console edge-to-edge warnings - #2 setStatusBarColor/setNavigationBarColor is REAL and present (Play flags the guarded invoke statically; runtime SDK guard is NOT enough); #1 is informational
metadata:
  type: project
---

Two distinct Google Play Console "recommended actions" appear on FastMediaSorter releases targeting SDK 35. Ground truth as of release `2.60.6222.158` (Jun 22, Material 1.14.0, no R8 strip) - owner pasted the live dashboard 2026-06-23.

**WARNING #2 - "uses deprecated APIs/parameters for edge-to-edge": `android.view.Window.setStatusBarColor` + `setNavigationBarColor`. REAL, PRESENT, app must remove the invoke from release bytecode.**
- On `2.60.6222.158` it is flagged with start points `o43.b` and `q43.b` - the R8-INLINED Material `EdgeToEdgeUtils` bottom-sheet helpers (not `bottomsheet.d` this time; obfuscation/inlining moves the attributed site between releases).
- **Play's static scanner does NOT honor Material's runtime `if(SDK_INT<35)` guard for this check.** The deprecated invoke is statically present in the dex (dexdump of 6222.324 found 7 framework invokes: 4 setStatusBarColor + 3 setNavigationBarColor, across `bottomsheet.d`, `o43`, `q43`, `ia6`), and Play flags its presence. invoke-present == flagged.
- **DOUBLE-WRONG history (do not repeat):** (1) I first called #2 a permanent unfixable static false-positive - wrong. (2) I then recorded it as "CLEARED by the Material 1.14.0 bump on release 6200.317" and that "Play honors the guard" - ALSO WRONG: it is present on the very next-week release 6222.158 with the same Material 1.14.0. The "cleared on 6200.317" reading was a transient/misread dashboard state, not a real clearance. Never trust a single-release "it disappeared" observation for this warning.
- **The fix that actually targets it:** remove every `android.view.Window.setStatusBarColor/setNavigationBarColor` invoke from the RELEASE dex. Because R8 INLINES the Material `EdgeToEdgeUtils` wrappers into obfuscated callers (o43/q43/...), an `-assumenosideeffects` keyed on `EdgeToEdgeUtils.*` may miss the already-inlined sites. Prefer the FRAMEWORK-level strip (robust to inlining + also covers androidx.activity.enableEdgeToEdge()'s own pre-29 setters):
  `-assumenosideeffects class android.view.Window { public void setStatusBarColor(int); public void setNavigationBarColor(int); }`
  Needs R8 optimize (proguard-android-optimize.txt + minify) - it IS on for standardRelease. The app itself never calls these setters (grep-verified), so only library calls are removed; bars stay transparent via theme `android:statusBarColor/navigationBarColor=@android:color/transparent` (pre-35 buckets) since the stripped programmatic setters no longer apply them.
- **Trade-off:** stripping the framework setter also drops androidx.activity.enableEdgeToEdge()'s pre-29 (API 23-28) navbar scrim -> 3-button nav over content can lose contrast there. Restore via a translucent theme `android:navigationBarColor` in the pre-29 bucket if it matters (a theme attr, NOT the deprecated method, so it does not re-trigger #2).

**WARNING #1 - "edge-to-edge may not display for all users": app-side COMPLETE, informational, persists.**
- App code is complete: `enableEdgeToEdge()` default-on in BaseActivity + per-screen WindowInsets; no `windowOptOutEdgeToEdgeEnforcement` / `windowTranslucentStatus`.
- Persists even when #2 is addressed (present on 6222.158). Per Google/AndroidX + Flutter engineers (flutter/flutter #169810): informational, does NOT impact users, partly internal-library/heuristic-driven, "disappears when Google/AndroidX update internal library code" - not reliably clearable by an app change.
- Do NOT set `windowOptOutEdgeToEdgeEnforcement` to dodge it: opts OUT of edge-to-edge, deprecated + disabled for API 36.

**Why (lesson):** This warning has burned me 2x with opposite wrong conclusions. The Play scanner's behavior is NOT inferable from bytecode/guard reasoning OR from one release's dashboard. Only a fresh Play upload + dashboard read is authoritative.

**How to apply:**
- #2 is REAL and present (6222.158). To clear it: framework-level R8 strip of `Window.setStatusBarColor/setNavigationBarColor` + transparent bars via theme. Then VERIFY by dexdump (below) AND - finally - by an actual Play upload on an internal track; do not declare it fixed from a local build alone.
- DEX-SCAN METHODOLOGY (verify the strip worked): grep the resolved framework methodref `Landroid/view/Window;.(setStatusBarColor|setNavigationBarColor)`. R8 inlines + renames `EdgeToEdgeUtils`, so grepping the Material symbol / `setStatusBarColor`-as-name returns ~0 regardless - a FALSE-CLEAN. Recipe: `dexdump -d classes*.dex | grep -E 'Landroid/view/Window;\.(setStatusBarColor|setNavigationBarColor)'` -> must be 0 after the strip (was 7 before).
- #1 is informational + app-side-complete; do NOT promise it clears, do NOT add opt-out flags; only an on-device Android 15 visual check rules out a real visual bug behind it.
- Version format `2.60.<M><DD><H>.<mmm>` is date-based (`6200`=Jun 20, `6222`=Jun 22); use `release/v2.60.*` tags + release worktree `P:/ANDROID/FastMediaSorter_release` as source of truth - never infer "before/after a fix" from the version string alone.
- Sources: flutter/flutter #169810 / #183372; developer.android.com edge-to-edge + behavior-changes-15/16; material-components-android #4507.
