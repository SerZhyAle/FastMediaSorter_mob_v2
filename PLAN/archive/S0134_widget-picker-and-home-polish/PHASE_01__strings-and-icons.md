# Phase 01 — Strings and Icons

**Strategic spec:** [`../S0134_widget-picker-and-home-polish.md`](../S0134_widget-picker-and-home-polish.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Introduce trilingual label strings for the two widgets that lack them and produce five monochrome 24dp vector chip icons that the widget picker will render next to each provider entry.

---

## Prerequisites

- [ ] Pre-implementation blocker on `widget_resource_launch_label` translation resolved (developer picks variant if owner is silent).
- [ ] Working tree clean or on a feature branch.
- [ ] Existing strings `widget_camera_photos_label`, `widget_continue_reading_label`, `widget_random_music_label` present in EN/RU/UK (audit before edit).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | +4 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +4 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +4 |
| `app_v2/src/main/res/drawable/ic_widget_favorites.xml` | New | ≤ 30 |
| `app_v2/src/main/res/drawable/ic_widget_resource_launch.xml` | New | ≤ 30 |
| `app_v2/src/main/res/drawable/ic_widget_camera_photos.xml` | New | ≤ 30 |
| `app_v2/src/main/res/drawable/ic_widget_random_music.xml` | New | ≤ 30 |
| `app_v2/src/main/res/drawable/ic_widget_continue_reading.xml` | New | ≤ 30 |

---

## Steps

### Step 01.1 — Add `widget_favorites_label` and `widget_resource_launch_label` strings (EN)

**Files:** `app_v2/src/main/res/values/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Add two new string resources next to the existing `widget_*_label` block: `widget_favorites_label="Favorites"` and `widget_resource_launch_label="Resource Launch"`. Insert near line 690 in the same group as `widget_camera_photos_label`. Strings pass `docs/COMMUNICATION_POLICY.md` §2 (label formula: 1..2 nouns, no punctuation) and §6 tone checklist before commit.

**Verification:**

- `Grep` — `<string name="widget_favorites_label">` matches exactly once in `app_v2/src/main/res/values/strings.xml`.
- `Grep` — `<string name="widget_resource_launch_label">` matches exactly once in `app_v2/src/main/res/values/strings.xml`.
- `Grep` — `<string name="widget_camera_photos_label">` still matches exactly once (no accidental duplicate / removal).

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 3/3 PASS. Files: app_v2/src/main/res/values/strings.xml (+2 LOC). Dev log recorded.

---

### Step 01.2 — Mirror new labels into RU and UK locales

**Files:** `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add the same two keys to RU and UK locale files. Suggested values: RU `widget_favorites_label="Избранное"`, `widget_resource_launch_label="Папка"`; UK `widget_favorites_label="Обране"`, `widget_resource_launch_label="Тека"`. Use `ё`/`Ё` if Russian text contains them. Strings pass `docs/COMMUNICATION_POLICY.md` §6 tone checklist before commit.

**Verification:**

- `Grep` — `<string name="widget_favorites_label">` matches exactly once in `app_v2/src/main/res/values-ru/strings.xml`.
- `Grep` — `<string name="widget_resource_launch_label">` matches exactly once in `app_v2/src/main/res/values-ru/strings.xml`.
- `Grep` — `<string name="widget_favorites_label">` matches exactly once in `app_v2/src/main/res/values-uk/strings.xml`.
- `Grep` — `<string name="widget_resource_launch_label">` matches exactly once in `app_v2/src/main/res/values-uk/strings.xml`.
- Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "widget_"` — exit code 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 5/5 PASS (incl. trilingual audit OK for 18 widget_ keys). Files: app_v2/src/main/res/values-ru/strings.xml (+2), app_v2/src/main/res/values-uk/strings.xml (+2). Dev log recorded.

---

### Step 01.3 — Author five monochrome 24dp vector chips for widget picker

**Files:** `app_v2/src/main/res/drawable/ic_widget_favorites.xml`, `app_v2/src/main/res/drawable/ic_widget_resource_launch.xml`, `app_v2/src/main/res/drawable/ic_widget_camera_photos.xml`, `app_v2/src/main/res/drawable/ic_widget_random_music.xml`, `app_v2/src/main/res/drawable/ic_widget_continue_reading.xml`
**Depends on:** — independent of 01.1/01.2

**Prompt for developer:**

> Create five new vector drawables, each `<vector android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24" android:tint="?attr/colorControlNormal">`. Themes per file: favorites — filled star; resource_launch — folder with arrow; camera_photos — stacked photos; random_music — vinyl with shuffle hint; continue_reading — play triangle inside open book. Single `<path>` per icon, monochrome white fill (`#FFFFFFFF`) so system tint applies. No gradients, no nested groups. Reuse path geometry from existing icons in `app_v2/src/main/res/drawable/ic_*.xml` where shapes match.

**Verification:**

- `Glob` — all five files exist under `app_v2/src/main/res/drawable/ic_widget_*.xml`.
- `Grep` — `android:viewportWidth="24"` matches exactly once in each of the five files.
- `Grep` — `android:tint="?attr/colorControlNormal"` matches exactly once in each of the five files.
- `Grep` — `<path` matches at least once in each of the five files.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 4/4 PASS (5 files, viewport=24, tint=?attr/colorControlNormal, path present in each). Files: 5 new ic_widget_*.xml (~12 LOC each). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — `build-debug.PS1` BUILD SUCCESSFUL (1m 13s, standard flavor).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `pwsh -File scripts/add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The two new label strings and five new `ic_widget_*` drawables are now resolvable from XML resource references — Phase 02 can wire them into AndroidManifest receivers and widget metadata files.

---

## Rollback Plan

Revert phase commit(s). No data migration, no behavioural change yet — strings unused, drawables unreferenced. Safe rollback.
