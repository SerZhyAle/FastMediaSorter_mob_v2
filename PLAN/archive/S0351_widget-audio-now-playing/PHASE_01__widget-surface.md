# Phase 01 - Widget Surface

**Strategic spec:** [`../S0351_widget-audio-now-playing.md`](../S0351_widget-audio-now-playing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Done
**Depends on:** none
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-06-04
**Completed:** 2026-06-04

---

## Objective

Create the RemoteViews surface, provider info, and trilingual strings for the Audio Now Playing widget.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/widget_audio_now_playing.xml` | New | <= 140 |
| `app_v2/src/main/res/xml/widget_audio_now_playing_info.xml` | New | <= 40 |
| `app_v2/src/main/res/values/strings_widget.xml` | Modified | <= 80 |
| `app_v2/src/main/res/values-ru/strings_widget.xml` | Modified | <= 80 |
| `app_v2/src/main/res/values-uk/strings_widget.xml` | Modified | <= 80 |

Landscape variant absent: widget RemoteViews use appwidget-provider sizing, not an Activity layout.

---

## Steps

### Step 01.1 - Add widget strings

**Files:** `app_v2/src/main/res/values/strings_widget.xml`, `app_v2/src/main/res/values-ru/strings_widget.xml`, `app_v2/src/main/res/values-uk/strings_widget.xml`

**Prompt for developer:**

> Add label, description, inactive title, unknown artist, and action accessibility strings for the Audio Now Playing widget in EN/RU/UK. Check wording against `docs/COMMUNICATION_POLICY.md` §6.

**Verification:**

- `Grep` - `widget_audio_now_playing_label` exists in all three files.
- `Grep` - `widget_audio_now_playing_action_play_pause` exists in all three files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix widget_audio_now_playing` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS. EN/RU/UK strings present; `check_strings_localized.ps1 -KeyPrefix widget_audio_now_playing` exit 0.

### Step 01.2 - Add widget layout

**Files:** `app_v2/src/main/res/layout/widget_audio_now_playing.xml`

**Prompt for developer:**

> Create a horizontal RemoteViews layout with artwork, title/artist text, and four stable action ids: previous, play/pause, next, favorite. Use existing audio/play/pause/skip/star drawables and avoid nested cards.

**Verification:**

- `Glob` - `app_v2/src/main/res/layout/widget_audio_now_playing.xml` exists.
- `Grep` - ids `widget_audio_now_playing_previous`, `widget_audio_now_playing_play_pause`, `widget_audio_now_playing_next`, `widget_audio_now_playing_favorite` exist.
- `Grep` - no `TextView` uses a hard-coded visible string.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS. Layout exists with previous/play-pause/next/favorite action ids and no hard-coded visible text.

### Step 01.3 - Add provider info

**Files:** `app_v2/src/main/res/xml/widget_audio_now_playing_info.xml`

**Prompt for developer:**

> Create a resizable appwidget-provider targeting `2x1`, horizontally resizable to `4x1`, with `updatePeriodMillis="0"` and the new layout as initial/preview layout.

**Verification:**

- `Glob` - `app_v2/src/main/res/xml/widget_audio_now_playing_info.xml` exists.
- `Grep` - `targetCellWidth="2"` exists.
- `Grep` - `updatePeriodMillis="0"` exists.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS. Provider info exists with `targetCellWidth="2"`, `resizeMode="horizontal"`, and `updatePeriodMillis="0"`.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] String locale audit passes for key prefix `widget_audio_now_playing`.

---

## Rollback Plan

Remove the new layout, provider info, and string keys.
