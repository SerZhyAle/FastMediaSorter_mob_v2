# Research 01 - MediaProjection screen capture under Google Play policy (2025-2026)

**Spec:** S0671
**Verdict:** allowed_with_conditions
**Method:** Web research against official sources (support.google.com/googleplay/android-developer, developer.android.com), 2024-2026 policy revisions.

## Conclusion

Per-session, system-consent screen capture via MediaProjection with `foregroundServiceType=mediaProjection` + `FOREGROUND_SERVICE_MEDIA_PROJECTION` is supported by Android and permitted on Google Play for a general-purpose media/utility app, provided:
- OS consent dialog is requested before EVERY capture session (no token reuse);
- the foreground-service type is declared AND the Play Console foreground-service declaration is completed (use case + functionality description + demo video);
- captured screen content is treated as personal/sensitive data with prominent in-app disclosure, a privacy policy, and an accurate Data safety entry.

Capturing the current foreground app (even a third-party app) through the system consent flow is the platform's intended behavior and does not by itself violate policy - the user, not the app, authorizes what is shared via the OS dialog. Failure modes are silent/background capture, missing disclosure, or behaving like a monitoring/spyware tool - not the act of capturing on-screen content with consent.

## Key clauses

- Android requires fresh user consent before every capture session; a MediaProjection token is single-use. On Android 14+ reusing the consent Intent or calling `createVirtualDisplay()` twice throws SecurityException. [https://developer.android.com/media/grow/media-projection]
- Apps targeting Android 14+ must declare `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PROJECTION` and a service with `foregroundServiceType="mediaProjection"`; missing type throws MissingForegroundServiceTypeException. [https://developer.android.com/about/versions/14/changes/fgs-types-required]
- The mediaProjection FGS use case is broad ("This content doesn't have to be exclusively media content") - covers general-purpose utility capture. [https://developer.android.com/about/versions/14/changes/fgs-types-required]
- Android 15 QPR1+ adds a prominent status-bar chip; projection auto-stops on screen lock; apps must implement `MediaProjection.Callback.onStop()`. [https://developer.android.com/media/grow/media-projection]
- Play Console requires, per FGS type: use-case selection, functionality description, deferral/interruption behavior, and a demo video of the in-app trigger steps. [https://support.google.com/googleplay/android-developer/answer/13392821]
- Play treats screen recording as personal/sensitive user data; not treating it so is a violation. [https://support.google.com/googleplay/android-developer/answer/11150561]
- Prominent disclosure must be in-app, shown in normal use (not buried in settings), describe data accessed/used/shared, and immediately precede the consent request. A privacy policy + Data safety entry are separately required and are NOT substitutes. [https://support.google.com/googleplay/android-developer/answer/11150561]
- Spyware policy targets covert background monitoring, not consensual, user-initiated, disclosed capture. [https://support.google.com/googleplay/android-developer/answer/14745000]

## Version notes

- API 34 (Android 14): FGS type + permission mandatory; consent Intent and MediaProjection instance single-use (SecurityException on reuse).
- API 35 (Android 15): cannot start mediaProjection FGS from a BOOT_COMPLETED receiver.
- Android 15 QPR1+: prominent status-bar chip, tap-to-stop, auto-stop on lock - must handle `onStop()`.
- Per-session consent is foundational on all versions; hard-enforced via SecurityException from API 34+.

## Implication for S0671

The engine already exists and is labeled "Play-safe confirmable capture path". Work is: flip the ship flag for standard, complete the Play Console FGS declaration + demo video, add in-app prominent disclosure before consent, and ship privacy policy + Data safety. The Android 8-9 path uses the same engine; FGS-type/visible-overlay rules do not apply on API 26-28.
