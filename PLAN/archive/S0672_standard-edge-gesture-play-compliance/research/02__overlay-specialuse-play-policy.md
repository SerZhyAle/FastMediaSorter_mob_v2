# Research 02 - SYSTEM_ALERT_WINDOW overlay + FOREGROUND_SERVICE_SPECIAL_USE under Play + Android 15 (2025-2026)

**Spec:** S0672
**Verdict:** restricted_review (high risk; the weakest part of the design)
**Method:** Web research against official Android + Play policy sources. Project targets API 35 (Android 15), so the visible-overlay rule applies directly.

## Conclusion

An always-on INVISIBLE edge overlay strip kept alive by a `specialUse` foreground service fails on two independent axes:

1. **Android 15 runtime (target API 35).** Starting an FGS from the background under the `SYSTEM_ALERT_WINDOW` exemption now requires a currently VISIBLE `TYPE_APPLICATION_OVERLAY` window. A deliberately invisible strip cannot satisfy this and throws `ForegroundServiceStartNotAllowedException` - a runtime crash, not just a review nit.
2. **Play review.** `specialUse` is the hardest FGS type to approve. The FGS eligibility gate requires the use to be user-beneficial, core, user-initiated OR user-perceptible, and not safely deferrable/interruptible. "Persistent background service to keep an invisible edge strip alive to trigger a screenshot gesture" is a weak, likely-rejected justification (idle, invisible, deferrable).

An overlay quick-launch panel that launches other apps is permitted in principle (launcher/sidebar apps ship this) but only if genuinely user-invoked and visible, not deceptive, not interfering with launched apps (Device and Network Abuse), and not relying on an invisible always-on strip.

## Key clauses

- `specialUse` is the catch-all FGS type; needs `foregroundServiceType=specialUse` + `FOREGROUND_SERVICE_SPECIAL_USE` + `startForeground(... FOREGROUND_SERVICE_TYPE_SPECIAL_USE)`. [https://developer.android.com/develop/background-work/services/fgs/service-types]
- `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` mandatory; free-form, reviewed in Play Console - "provide enough information to let the reviewer see why you need specialUse". [https://developer.android.com/develop/background-work/services/fgs/service-types]
- Play Console declaration required for every FGS type on Android 14+: functionality description, user impact if deferred/interrupted, use case, demo video. "All foreground service types are subject to review." [https://support.google.com/googleplay/android-developer/answer/13392821]
- FGS eligibility gate: beneficial + core + user-initiated/user-perceptible + non-deferrable; weak justification is the typical rejection cause. [https://support.google.com/googleplay/android-developer/answer/13392821]
- Android 15 narrows the SYSTEM_ALERT_WINDOW background-FGS-start exemption: app needs SYSTEM_ALERT_WINDOW AND a visible `TYPE_APPLICATION_OVERLAY` window before starting the FGS, else `ForegroundServiceStartNotAllowedException`. [https://developer.android.com/about/versions/15/behavior-changes-15]
- Same exemption confirmed in FGS docs. [https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start]
- Overlays are a security risk: Android 12 made `SYSTEM_ALERT_WINDOW` harder to grant + added `HIDE_OVERLAY_WINDOWS`; secure/sensitive activities may suppress overlays. [https://developer.android.com/security/fraud-prevention/activities]
- Device and Network Abuse: no unauthorized interference with other apps. [https://support.google.com/googleplay/android-developer/answer/16559646]

## Version notes

- API 23-30: overlay via `ACTION_MANAGE_OVERLAY_PERMISSION`; overlay can start FGS from background.
- API 31 (Android 12): permission harder; `HIDE_OVERLAY_WINDOWS`; FGS-from-bg tightened.
- API 34 (Android 14): FGS type mandatory; `FOREGROUND_SERVICE_SPECIAL_USE` + subtype property + Play declaration.
- API 35 (Android 15): visible-overlay requirement - invisible strip cannot launch FGS from background.

## Conditions for S0672

- Do NOT rely on an always-on invisible overlay. Make the overlay a VISIBLE `TYPE_APPLICATION_OVERLAY` window before `startForegroundService`.
- If keeping `specialUse`: write a precise subtype + matching Play description framing it as a user-initiated, user-perceptible active session; provide a demo video.
- Prefer avoiding `specialUse` entirely: trigger from a visible affordance (QuickSettings tile, notification action, in-app control, MediaProjection session) so no persistent background FGS is needed.
- Quick-launch panel: genuinely user-invoked + visible, no interference, respect `HIDE_OVERLAY_WINDOWS` and secure windows, request `SYSTEM_ALERT_WINDOW` via standard grant with in-app rationale.
- Expect manual review and possible rejection on the `specialUse` subtype; budget for back-and-forth and a fallback without a persistent FGS.

## Cross-check vs current code (to verify during /spec-tech)

Research flagged a likely Android-15 FGS-start ordering bug: `OverlayHostService.onStartCommand` may call `startForegroundCompat` AFTER `overlayManager.show()`. On API 35 the FGS must not start while no visible overlay exists. Audit the auto-restore path (`ScreenGestureOverlayStartupCoordinator`) too. UNVERIFIED here - confirm against the live file before relying on it.
