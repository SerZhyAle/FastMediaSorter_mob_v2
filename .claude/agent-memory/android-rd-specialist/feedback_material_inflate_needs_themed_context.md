---
name: material-inflate-needs-themed-context
description: MaterialButton/Material views inflated from an Application or Service context crash via ThemeEnforcement - overlay-window features must use a Material-themed ContextThemeWrapper
metadata:
  type: feedback
---

Inflating a layout that contains `com.google.android.material.*` views (e.g. `MaterialButton`) with `LayoutInflater.from(applicationContext)` or a bare `Service` context throws `IllegalArgumentException: The style on this component requires your app theme to be Theme.MaterialComponents (or a descendant)` from `ThemeEnforcement.checkTheme`, surfacing as an `InflateException` at the first Material view's XML line.

**Why:** the Application/Service context's theme is the base app theme, which does not descend from `Theme.MaterialComponents`. Material views hard-require a Material theme on the inflating context. Activities are fine (their theme is Material); Application/Service contexts are not. This bit S0930: `QuickRecorderIndicatorControllerImpl.show()` inflated the shared `view_recording_indicator` (S0774) pill from `appContext` inside `QuickAudioRecorderService`, crashing on-device the moment the draw-over-apps overlay path was taken - and because `show()` sat inside the recorder's `try`, the caught exception ran `failAndStop()` and discarded the recording.

**How to apply:**
- For any overlay window / notification custom RemoteViews-adjacent / Service-hosted inflate of a Material layout, wrap the context: `LayoutInflater.from(ContextThemeWrapper(appContext, R.style.Theme_FastMediaSorter))` (or a Material overlay theme).
- In code review, flag `LayoutInflater.from(appContext / service)` on any layout containing Material views. Debug builds may not catch it until the exact runtime path runs (permission-gated), so it needs on-device verification, not just compile.
- Defence in depth: keep UI-layer `show()` calls OUT of a recorder/capture `try` block (or guard them) so a view-inflation failure can never discard already-captured data.
- Related: [[feedback_check_generated_binding_types]] (binding downcast Button vs MaterialButton), [[project_focus_frame_infra]].
