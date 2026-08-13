# Tactical plan: S0349 - Quick Audio Recorder widget

**Strategic spec:** [`../S0349_widget-quick-audio-recorder.md`](../S0349_widget-quick-audio-recorder.md)
**Status:** Implemented - BlockNeedUserTest (all 5 phases coded; standard/lite/photos builds pass; on-device verification pending)
**Parent foundation:** S0348 (icon-style language, `HomeWidgetCatalog`, manifest flavor-gating)

---

## Goal

Ship a `1x1` icon-only "Quick Audio Recorder" home-screen widget. One tap starts background recording via a microphone foreground service; the next tap stops and saves an `.m4a` to the app's external `Music/` directory. Available only where `SUPPORT_MIC_RECORDING == true` (gated by manifest, not `BuildConfig`).

---

## Phases

- **Phase 01** - Widget shell & assets: provider, `1x1` icon layout, widget-info xml, icon drawables, label/description strings, main-manifest receiver.
- **Phase 02** - Recording foreground service: microphone FGS, notification channel + Stop action, `MediaRecorder`, save to default dir, event-driven widget icon state, `FOREGROUND_SERVICE_MICROPHONE` permission.
- **Phase 03** - Trampoline & permission flow: transparent activity + launch manager, `RECORD_AUDIO` request, settings fallback, start/stop routing, main-manifest activity.
- **Phase 04** - Flavor gating & registry: `lite`/`photos` manifest removals, `HomeWidgetCatalog` entry, availability matrix verification.
- **Phase 05** - Docs, build gate, catalog & logs.

---

## Dependency order

1. Phase 01 (assets first - layouts/strings needed by everything else).
2. Phase 02 (service depends on widget provider from 01).
3. Phase 03 (trampoline depends on service from 02).
4. Phase 04 (gating/registry depend on provider/activity/service names from 01-03).
5. Phase 05 (docs + build gate last).

---

## Files (new)

- `app_v2/src/main/java/com/sza/fastmediasorter/widget/QuickAudioRecorderWidgetProvider.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/widget/QuickAudioRecorderService.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/widget/QuickAudioRecorderActivity.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/widget/QuickAudioRecorderLaunchManager.kt`
- `app_v2/src/main/res/layout/widget_quick_audio_recorder.xml`
- `app_v2/src/main/res/xml/widget_quick_audio_recorder_info.xml`
- `app_v2/src/main/res/drawable/ic_widget_quick_audio_recorder.xml`
- `app_v2/src/main/res/drawable/ic_widget_quick_audio_recorder_recording.xml`

## Files (modified)

- `app_v2/src/main/AndroidManifest.xml` (receiver + activity + service + `FOREGROUND_SERVICE_MICROPHONE`)
- `app_v2/src/lite/AndroidManifest.xml` (3 removals)
- `app_v2/src/photos/AndroidManifest.xml` (3 removals)
- `app_v2/src/main/java/com/sza/fastmediasorter/widget/registry/HomeWidgetCatalog.kt` (catalog entry)
- `app_v2/src/main/res/values/strings.xml` + `values-ru` + `values-uk`
- `docs/FEATURES.md` + `_RU` + `_UK`

---

## Phase Done global criteria

- `standardDebug` compiles; `liteDebug` + `photosDebug` merge manifests (widget absent).
- No `BuildConfig.SUPPORT_*` read in any new `src/main` file (Rule 15).
- No `Log.d(` in new files (Timber only).
- EN/RU/UK parity for all new strings.
