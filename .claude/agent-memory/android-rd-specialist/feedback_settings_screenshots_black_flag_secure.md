---
name: settings-screenshots-black-flag-secure
description: Settings screenshots come back solid black on any device because the screen is FLAG_SECURE - the fix is an in-app setting, so verify colour/layout work on a throwaway emulator, not the owner's phone
metadata:
  type: feedback
---

A screenshot of the Settings screen returns a **solid black image**, not an error, whenever the in-app setting "Защищать секретные экраны" (`secureSensitiveScreens`, `AppSettings.kt`, default **ON**) is enabled. `BaseActivity` applies `FLAG_SECURE`, the capture succeeds, and the black frame arrives with an explanatory note from `adb.ps1 shot` - easy to misread as a rendering bug in whatever you just changed.

**Why:** learned 2026-08-15 while proving a Compose colour fix on the Wear-companion sheet, which opens over Settings. A device-operator run burned its whole turn budget returning only black frames. The flag cannot be cleared from the shell - it is a DataStore preference (`KEY_SECURE_SENSITIVE_SCREENS` in `SettingsRepositoryImpl`), not SharedPreferences, so `adb.ps1 prefs` neither shows nor sets it. The only switch is the row inside the app.

**How to apply:**

- Any visual verification whose route passes through Settings - a settings row, a dialog or bottom sheet raised from Settings, a theme or colour change - needs that setting OFF first.
- **Just turn it off on the S21 (RFCR110NBQJ).** It is the dedicated test device and carries blanket authorization to change any setting - see [[test-device-galaxy-s21]]. On 2026-08-15 I failed to apply that standing permission, detoured to an AVD and put the choice to the owner as a question; he answered "этот телефон для тестирования, ты можешь там менять любые настройки". The detour cost a full emulator boot, install and drive for a screenshot the phone would have given directly. An AVD is the fallback for when no phone is attached, not the default.
- While the flag is still on, navigate by the layout tree (`uiautomator dump`), which works normally - only the pixels are suppressed. That is how you reach the setting in order to turn it off.
- Brief a delegated device operator on this up front, including explicit permission to change the setting. Without it the agent stops and asks, having spent its budget discovering the trap.

See also [[avd-evidence-traps-width-and-logs]] and [[emulator-acceptance-ceiling]].
