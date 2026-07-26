---
name: onboarding-device-test-gotchas
description: Driving first-run WelcomeActivity onboarding on the AVD - permission-dialog chain after pm clear, coords shift per theme/locale recreate, HOME role dialog absent, input-tap misfire
type: feedback
---

Gotchas when driving a clean-install onboarding walk (WelcomeActivity) on the emulator to verify first-run behaviour (S1107 launcher-role, S1136 recreation storm, any launcher/first-run ticket). Learned 2026-07-24 on S1136, emulator-5554 (Android 15 / API 35).

1. **After `pm clear`, lingering on Welcome page 1 triggers a chain of system permission dialogs** (location -> photos/video -> record audio -> All-files-access Settings screen -> ResolverActivity HOME chooser). A pristine post-install first-run does NOT fire them (they surface when a recreate re-runs the request while you pause to dump/screenshot). They derail the walk into system UIs.
**Why:** WelcomeActivity re-requests permissions on (re)create; each theme/locale recreate re-fires them.
**How to apply:** pre-grant everything via adb BEFORE launching so the walk stays deterministic: `pm grant <pkg> android.permission.{READ_MEDIA_IMAGES,READ_MEDIA_VIDEO,READ_MEDIA_AUDIO,POST_NOTIFICATIONS,RECORD_AUDIO,ACCESS_COARSE_LOCATION,ACCESS_FINE_LOCATION,CAMERA,ACCESS_MEDIA_LOCATION}` + `appops set <pkg> MANAGE_EXTERNAL_STORAGE allow`. Re-grant after every `pm clear` (clear wipes grants).

2. **Every theme/locale selection recreates WelcomeActivity, shifting page-1 layout Y by ~+191px.** Cached tap coords drift into the wrong control (a theme tap lands on the launcher row, etc.).
**How to apply:** re-`uiautomator dump` and read fresh bounds before EVERY tap after a recreate; never reuse coords across a theme/language change. Tap the switch via its `str_switch` bounds centre, not the row title.

3. **The HOME role dialog (RequestRoleActivity) does NOT fire on the AVD.** With launcher opt-in ON + Finish, no `RequestRole`/`requestingPackageName` lines appear at all. So S1107-style launcher-role non-regression (requestingPackageName != null) is unverifiable on emulator - defer that sub-check to a real device (S21+), keep the ticket `BlockNeedUserTest`.

4. **Rapid `adb input tap` during page transitions misfires** - a stray tap once launched a `googleapp://lens` deeplink and hijacked completion. Prefer one mobile-mcp tap per page with a dump-verify between; never fire N blind `input tap` in a loop through the wizard.

5. **Substantive S1136 finding:** the first-run SettingsActivity recreation storm (S1107: 3x onCreate in 1.8s) does NOT reproduce on the current build - Phase A (conditional `recreate()` in `onWelcomeThemeSelected`, only when night-mode bucket is unchanged) already eliminated it. Clean run: first-run Settings settles in a single stable onCreate (~10s displayed). See [[avd-device-sweep-gotchas]] for general AVD infra traps.
