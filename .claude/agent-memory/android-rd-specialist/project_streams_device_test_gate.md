---
name: streams-device-test-gate
description: To device-test streams the runtime enableStreams toggle must be on; emulator network capture quirks for buffering/reconnecting labels
metadata:
  type: project
---

To device-test the Streams feature, the runtime `enableStreams` toggle must be ON. The main-menu "Трансляции" entry is gated on `BuildConfig.SUPPORT_STREAMS && isStreamsEnabled` ([MainActivity.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt) ~L671, S0575). `enableStreams` defaults **false** (DataStore `KEY_ENABLE_STREAMS`), so on a fresh install the entry is absent even in `standard`.

**Why:** the compile-time capability (`SUPPORT_STREAMS`) is necessary but not sufficient - there is a second runtime master toggle the user must flip. Easy to mistake "no Трансляции menu item" for a broken build.

**How to apply:**
- Enable via UI: Settings (gear) -> Медиа tab -> "Трансляции" section -> "Включить трансляции" switch. DataStore prefs don't stick via file-swap (re-sync) - use the UI.
- Then main menu "Ещё" -> "Трансляции" -> StreamsActivity. Empty by default; "Добавить трансляцию" takes a URL. A reliable multi-variant HLS test URL: `https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8`.

**Emulator network for buffering/reconnecting verification (S0685 stream wait-phase labels):**
- Kill net with `adb -s <dev> shell svc wifi disable; svc data disable` (cellular data carries NO traffic on the AVD - wifi-only; `adb emu network speed` throttling is useless here).
- Mid-play kill -> buffer drains (~20-30s) -> classified-retry -> the "Восстановление соединения.." label shows for ~10-15s before the 4-retry budget (backoff 2/4/8/16s) exhausts and the S0581 "stream unavailable" dialog surfaces.
- The plain "Буферизация.." window is sub-frame on a healthy fast emulator (shorter than the screenshot pipeline) - hard to capture; verify by the shared render path + log (`StreamPlaybackHelperKt` Timber lines) instead.
