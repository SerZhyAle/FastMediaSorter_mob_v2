---
name: color-theme-device-switch
description: How to switch the app color theme on a device/emulator for testing - pref-file swap does not stick
type: feedback
---

To reproduce a specific color theme (DARK_GREEN etc., S0569) on a device/emulator, switch it through the UI: Настройки → Общие → Общие настройки интерфейса → Цветовая тема → pick → Перезапустить. Do NOT try to pre-seed `shared_prefs/color_theme_prefs.xml`.

**Why:** `color_theme_prefs.xml` is only a synchronous *mirror* read by `ColorThemePrefs.applySavedMode` at process start. The canonical value lives in DataStore (`files/datastore/settings.preferences_pb`, binary protobuf). On launch the DataStore value re-syncs into the mirror, so a `run-as cp` of the XML is silently overwritten back to the old value (e.g. AUTO) - the theme never applies.

**How to apply:** Any on-device test that needs a non-default theme (contrast/WCAG checks, S0611) - drive the settings spinner + restart prompt via mobile-mcp, don't shortcut via pref files. StreamsActivity is not exported, so `am start` on it fails; reach Streams via the main-window dropdown menu ("Трансляции", gated by SUPPORT_STREAMS). Also: in Git Bash, `adb push /data/...` needs `MSYS_NO_PATHCONV=1` or the POSIX path is mangled to a Windows path.
