---
name: black-screenshot-means-flag-secure
description: A black or zero-byte screenshot of Settings/AddResource/ResourceEditor is FLAG_SECURE working, not a rendering bug - check window flags before diagnosing
metadata:
  type: feedback
---

A capture that comes back all-black (or as a zero-byte file) is `FLAG_SECURE`, not a broken screen. Check the window's own flags before spending any time on themes, `windowBackground`, hardware layers or the AVD image.

**Why:** S1284 was filed as "SettingsActivity renders completely black on API 37" after a device test, with careful measurements of black-pixel ratios, night-mode toggles and HWUI frame timings. All of it described `FLAG_SECURE`. `BaseActivity.applySecureFlagIfEnabled` sets the flag whenever `isSensitiveScreen()` is true and the `secureSensitiveScreens` setting is on - and that setting defaults to on (S1045). Settings, Add Resource and Resource Editor all declare themselves sensitive. The screen is perfectly visible to a human; only the capture is blank.

**How to apply:** the tell is a black capture combined with a healthy `uiautomator dump` - correct text and bounds, taps at those coordinates work - plus system dialogs and the status bar appearing normally in the same frame, because those are separate unprotected windows. Confirm with `adb -s <device> shell "dumpsys window windows | grep -A 6 '<ActivityName>'"` and look for `SECURE` in the `fl=` line; that settles it in one command. Different platform versions blank a secure window differently - API 33 yields a zero-byte `screencap` file, API 37 yields an all-black image - so the shape of the failure is not evidence of an API-specific bug. To capture such a screen for a report, turn `secureSensitiveScreens` off first. Recorded in `docs/TEST_SCENARIOS.md` under "Not a Defect: Screen Capture on Secure Screens". Related: [[emulator-acceptance-ceiling]], [[dialogs-invisible-under-wm-override]].
