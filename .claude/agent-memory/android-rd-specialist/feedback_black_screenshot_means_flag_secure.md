---
name: black-screenshot-means-flag-secure
description: A black or zero-byte screenshot of Settings/AddResource/ResourceEditor is FLAG_SECURE working, not a rendering bug - adb.ps1 shot now says so itself
metadata:
  type: feedback
---

A capture that comes back all-black (or as a zero-byte file) is `FLAG_SECURE`, not a broken screen. Check the window's own flags before spending any time on themes, `windowBackground`, hardware layers or the AVD image.

**Why:** S1284 was filed as "SettingsActivity renders completely black on API 37" after a device test, with careful measurements of black-pixel ratios, night-mode toggles and HWUI frame timings. All of it described `FLAG_SECURE`. `BaseActivity.applySecureFlagIfEnabled` sets the flag whenever `isSensitiveScreen()` is true and the `secureSensitiveScreens` setting is on - and that setting defaults to on (S1045). Settings, Add Resource and Resource Editor all declare themselves sensitive. The screen is perfectly visible to a human; only the capture is blank. It happened **again** on 2026-08-08, twice in one day (S1418 and S1502 device tests), and produced a P90 ticket (S1506) - prose in `docs/TEST_SCENARIOS.md` had documented it since S1284 and still did not stop it.

**How to apply:** `scripts/devtest/adb.ps1 shot` prints an explanation next to the file it wrote when it detects the flag (`-Json` carries `secureWindow`), added by S1506. **That detector is not trustworthy on its own:** on 2026-08-11 it returned `secureWindow:false` twice for a genuinely secure `SettingsActivity` on a Samsung SM-G996U1 running Android 15, while the frame was black - ticketed as S1580. So a `false` is not evidence the screen is unprotected; only a `true` is evidence it is. Fall back to the manual tell below whenever the frame is black. The manual tell is unchanged: a black capture combined with a healthy `uiautomator dump` - correct text and bounds, taps at those coordinates work - plus the status bar and system dialogs appearing normally in the same frame, because those are separate unprotected windows. A healthy tree next to a black frame **confirms** FLAG_SECURE, it does not contradict it.

**Reading the flag by hand - the trap that costs a cycle:** `mCurrentFocus` is printed by `dumpsys window`, NOT by `dumpsys window windows`. On Android 15 the latter has no such line at all, so a probe anchored on it silently finds no focus and reports "not secure". One `dumpsys window` call carries both the `mCurrentFocus=Window{<hash> ..}` line and the per-window `Window #N Window{<hash> ..}:` blocks whose `fl=` line holds `SECURE`.

Different platform versions blank a secure window differently - API 33 yields a zero-byte `screencap` file, API 35/37 an all-black image - so the shape of the failure is not evidence of an API-specific bug. To capture such a screen for a report, turn `secureSensitiveScreens` off first. Recorded in `docs/TEST_SCENARIOS.md` under "Not a Defect: Screen Capture on Secure Screens". Related: [[emulator-acceptance-ceiling]], [[dialogs-invisible-under-wm-override]].
