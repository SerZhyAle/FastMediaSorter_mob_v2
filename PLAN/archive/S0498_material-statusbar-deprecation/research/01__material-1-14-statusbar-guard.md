# Research 01 - Material 1.14.0 guards setStatusBarColor on API 35

**Spec:** S0498
**§6 item:** 1
**Status:** Resolved
**Date:** 2026-06-18

## Question

Does Material Components 1.14.0 stop calling the deprecated `android.view.Window.setStatusBarColor`
from `BottomSheetDialog` on Android 15 (API 35), so the Play Console deprecated-edge-to-edge
warning clears after bumping from 1.13.0?

## Method

Cross-read the Material Components Android sources by git tag (1.13.0 vs 1.14.0) plus the upstream
tracking issue. Confirmed the project's pin and the Play Console report against the actual bytecode-level
call site.

## Findings

### Project state (before change)

- Dependency pinned at `com.google.android.material:material:1.13.0` (app_v2 build script). No version
  catalog; no documented reason for the pin. 1.13.0 released 2024-09-03, so ~13 months stale at 2026-06.
- App code never calls `setStatusBarColor` itself (grep over `.kt` is empty). The flagged call is
  library-internal: Play Console points at `com.google.android.material.bottomsheet.d.onCreate`
  (obfuscated `d` = `BottomSheetDialog`).
- Prior mitigation (theme override `enableEdgeToEdge=true` on `bottomSheetDialogTheme` in the
  `values-v35` / `values-night-v35` themes) is based on a FALSE premise. Its comment claims
  "Material 1.13.0+ rewrote BottomSheetDialog to use WindowInsetsControllerCompat instead of
  setStatusBarColor" - untrue. Proof: release 2.60.6180.134 (built 2026-06-18) already ships this
  override, yet Play Console still reports the warning.

### Material 1.13.0 (tag)

`BottomSheetDialog.onCreate` calls `window.setStatusBarColor(0)` UNCONDITIONALLY - no `SDK_INT` guard.
The `enableEdgeToEdge` theme attribute (`edgeToEdgeEnabled` field) only gates `drawEdgeToEdge`
(combined with a transparent navigation bar) and `setDecorFitsSystemWindows`; it does NOT prevent the
`setStatusBarColor` call. So the theme override cannot remove the flagged call.

### Material 1.14.0 (tag, stable 2025-05-13) and master

- `BottomSheetDialog.onCreate` now calls `EdgeToEdgeUtils.setStatusBarColor(window, 0)` instead of
  `window.setStatusBarColor(0)`.
- `EdgeToEdgeUtils.setStatusBarColor`:

  ```java
  public static void setStatusBarColor(@NonNull Window window, @ColorInt int color) {
    if (VERSION.SDK_INT < VERSION_CODES.VANILLA_ICE_CREAM) {
      window.setStatusBarColor(color);
    }
  }
  ```

  i.e. on API 35+ the deprecated setter is not invoked → the call disappears from the executed path,
  which is exactly the migration pattern Play Console's deprecated-edge-to-edge detector recognizes.
- 1.14.0 changelog: "Deprecated enableEdgeToEdge for bottom sheet dialogs on API 35+" - confirms the
  attribute the project relied on is itself deprecated; the library now handles API 35 edge-to-edge
  natively, so the theme override is obsolete.

### Caveat (not the reported warning)

`onAttachedToWindow` still calls `WindowCompat.setDecorFitsSystemWindows(window, !drawEdgeToEdge)`
without an explicit `SDK_INT` guard in 1.14.0. The Play Console report for release 2.60.6180.134 lists
ONLY `android.view.Window.setStatusBarColor`, not `setDecorFitsSystemWindows`, so this does not affect
the targeted warning. Watch for it on the next pre-launch report after the bump.

## Conclusion

Bumping Material Components 1.13.0 → 1.14.0 removes the deprecated `setStatusBarColor` call on API 35
and makes the `values-v35` theme override redundant. This drives §5 (bump + theme cleanup) and §9 ADR-1.

## Sources

- material-components-android issue #4732 - BottomSheetDialog uses deprecated setStatusBarColor() and
  setDecorFitsSystemWindows() on Android 15.
- BottomSheetDialog.java at tags 1.13.0 and 1.14.0 (and master) - onCreate call site.
- internal/EdgeToEdgeUtils.java at tag 1.14.0 - SDK_INT < VANILLA_ICE_CREAM guard.
- material-components-android releases page - 1.14.0 stable (2025-05-13) and changelog line.
