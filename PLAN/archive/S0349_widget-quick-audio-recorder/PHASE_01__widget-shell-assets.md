# Phase 01 - Widget shell & assets

**Strategic spec:** [`../S0349_widget-quick-audio-recorder.md`](../S0349_widget-quick-audio-recorder.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Pending
**Depends on:** - start
**Blocks:** Phase 02, Phase 04

---

## Objective

Create the icon-only `1x1` widget surface and its provider, wired to launch the trampoline activity (added in Phase 03). Mirror the established `RandomMusic` widget assets and the S0348 icon-style.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `widget/QuickAudioRecorderWidgetProvider.kt` | New | ≤ 90 |
| `res/layout/widget_quick_audio_recorder.xml` | New | ≤ 25 |
| `res/xml/widget_quick_audio_recorder_info.xml` | New | ≤ 20 |
| `res/drawable/ic_widget_quick_audio_recorder.xml` | New | ≤ 15 |
| `res/drawable/ic_widget_quick_audio_recorder_recording.xml` | New | ≤ 15 |
| `res/values/strings.xml` (+ ru/uk) | Modified | ≤ 6 |
| `src/main/AndroidManifest.xml` | Modified | ≤ 8 |

---

## Steps

### Step 01.1 - Strings (label + description)

- Add `widget_quick_audio_recorder_label` and `widget_quick_audio_recorder_description` across EN/RU/UK.
- Use `scripts/utils/set-android-string.ps1 -Action add` for lockstep parity.
- EN label: "Quick Recorder"; EN description: "Tap to start a voice recording; tap again to stop and save".

**Verification:**
- `check_strings_localized.ps1 -KeyPrefix "widget_quick_audio_recorder"` exit 0.

### Step 01.2 - Icon drawables

- `ic_widget_quick_audio_recorder.xml`: 24dp mic vector (idle), tint `?attr/colorControlNormal`.
- `ic_widget_quick_audio_recorder_recording.xml`: 24dp mic/stop vector with red fill for the recording state.

**Verification:**
- Both files parse (build); `Glob` confirms existence.

### Step 01.3 - Layout + widget-info

- `widget_quick_audio_recorder.xml`: copy `widget_random_music.xml` structure - `LinearLayout` id `widget_quick_audio_recorder_container`, `widget_background`, centered `ImageView` id `widget_quick_audio_recorder_icon` at `widget_icon_size_large`, white tint, `contentDescription = @string/widget_quick_audio_recorder_label`, src = idle icon.
- `widget_quick_audio_recorder_info.xml`: copy `widget_random_music_info.xml` - `minWidth/minHeight 48dp`, `targetCellWidth/Height 1`, `updatePeriodMillis 0`, `resizeMode none`, `widgetCategory home_screen`, theme `Widget.FastMediaSorter`, `initialLayout` + `previewLayout` = the new layout, `previewImage = @drawable/ic_widget_quick_audio_recorder`, `description = @string/widget_quick_audio_recorder_description`.

**Verification:**
- `Grep targetCellWidth="1"` and `updatePeriodMillis="0"` present in info xml.

### Step 01.4 - Provider

- `QuickAudioRecorderWidgetProvider : AppWidgetProvider`.
- `onUpdate` loops ids → `updateAppWidget(context, mgr, id, isRecording = QuickAudioRecorderService.isRecording)`.
- `companion object`:
  - `fun updateAppWidget(context, mgr, appWidgetId, isRecording)`: build `RemoteViews(widget_quick_audio_recorder)`, set icon (`setImageViewResource` idle vs recording drawable), set click `PendingIntent.getActivity` → `QuickAudioRecorderActivity` (action `ACTION_TOGGLE`, `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT`), `mgr.updateAppWidget(id, views)`.
  - `fun updateAllWidgets(context, isRecording)`: resolve own ids via `AppWidgetManager.getAppWidgetIds(ComponentName(context, QuickAudioRecorderWidgetProvider::class.java))` and refresh each. Called by the service on state change.
- No `BuildConfig` reads. Timber only. Reference `QuickAudioRecorderActivity`/`QuickAudioRecorderService` by class - they are added in later phases (compilation deferred to Phase 03 build gate; this phase's build runs after 03 if needed, otherwise stub the references behind the actual classes created in 02/03).

> Sequencing note: to keep each phase independently compilable, create the provider with references to the activity/service that 02/03 will add. Run the first full compile at the Phase 03 gate. Phase 01/02 structural verification is grep/Glob.

**Verification:**
- `Grep "class QuickAudioRecorderWidgetProvider"` once.
- `Grep "BuildConfig"` zero hits; `Grep "Log\.d\("` zero hits.

### Step 01.5 - Main manifest receiver

- Declare `<receiver android:name=".widget.QuickAudioRecorderWidgetProvider" android:exported="true" android:label="@string/widget_quick_audio_recorder_label" android:icon="@drawable/ic_widget_quick_audio_recorder">` with `APPWIDGET_UPDATE` intent-filter + provider meta-data `@xml/widget_quick_audio_recorder_info`.
- Mirror placement next to the other widget receivers.

**Verification:**
- `Grep "QuickAudioRecorderWidgetProvider"` in `src/main/AndroidManifest.xml` once.

---

## Phase Done Criteria

- All Step 01.* structural greps pass.
- Strings localized EN/RU/UK (`check_strings_localized.ps1` exit 0).
- Dev log entry per touched file (batched at Phase 05).
