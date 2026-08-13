# Phase 03 - Provider Manifest

**Strategic spec:** [`../S0351_widget-audio-now-playing.md`](../S0351_widget-audio-now-playing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-04
**Completed:** 2026-06-04

---

## Objective

Add the AppWidgetProvider, register it, and isolate unavailable flavors.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/AudioNowPlayingWidgetProvider.kt` | New | <= 220 |
| `app_v2/src/main/AndroidManifest.xml` | Modified | <= 600 |
| `app_v2/src/lite/AndroidManifest.xml` | Modified | <= 120 |
| `app_v2/src/photos/AndroidManifest.xml` | Modified | <= 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/registry/HomeWidgetCatalog.kt` | Modified | <= 130 |

---

## Steps

### Step 03.1 - Add provider class

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/AudioNowPlayingWidgetProvider.kt`

**Prompt for developer:**

> Create `AudioNowPlayingWidgetProvider : AppWidgetProvider`. Render the snapshot into RemoteViews and bind action PendingIntents to `AudioPlaybackService.ACTION_WIDGET_COMMAND`.

**Verification:**

- `Glob` - provider file exists.
- `Grep` - `class AudioNowPlayingWidgetProvider` matches once.
- `Grep` - provider binds all four action ids.
- `Grep` - no `BuildConfig` appears in the provider.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS. Provider exists, binds all action ids, and has no `BuildConfig` reference.

### Step 03.2 - Register and flavor-gate provider

**Files:** `app_v2/src/main/AndroidManifest.xml`, `app_v2/src/lite/AndroidManifest.xml`, `app_v2/src/photos/AndroidManifest.xml`

**Prompt for developer:**

> Add the widget receiver in the main manifest and remove it from lite/photos overlays because `ENABLE_PERSISTENT_AUDIO_PLAYBACK=false` there.

**Verification:**

- `Grep` - `AudioNowPlayingWidgetProvider` exists in main manifest.
- `Grep` - `widget_audio_now_playing_info` exists in main manifest.
- `Grep` - `AudioNowPlayingWidgetProvider` exists with `tools:node="remove"` in lite and photos manifests.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS. Main manifest registers provider; lite/photos overlays remove it.

### Step 03.3 - Add picker registry entry

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/registry/HomeWidgetCatalog.kt`

**Prompt for developer:**

> Add Audio Now Playing to the in-app widget picker registry if the registry exists. Rely on manifest presence as the flavor gate; do not read `BuildConfig` in the registry.

**Verification:**

- `Grep` - `AudioNowPlayingWidgetProvider` appears in `HomeWidgetCatalog.kt`.
- `Grep` - no new `BuildConfig` reference appears in `HomeWidgetCatalog.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS. Registry includes `AudioNowPlayingWidgetProvider` without adding `BuildConfig`.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Standard/lite/photos merged manifests have expected provider presence/absence after build.

---

## Rollback Plan

Remove provider class, manifest entries, overlay removals, and registry entry.
