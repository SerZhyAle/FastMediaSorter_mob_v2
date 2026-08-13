# Phase 05 — Docs / Catalog / Cleanup

**Strategic spec:** [`../S0134_widget-picker-and-home-polish.md`](../S0134_widget-picker-and-home-polish.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03, Phase 04
**Blocks:** —
**Steps done:** 4 / 4
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Remove orphaned `widget_preview_*.xml` layouts left dead by Phase 02, update trilingual `docs/FEATURES*` with the widget polish bullet, regenerate the catalog for `app_v2`, and run the strings localisation audit.

---

## Prerequisites

- [ ] Phase 02 ✅ Done (previewLayout removed from metadata).
- [ ] Phase 03 ✅ Done (background ready).
- [ ] Phase 04 ✅ Done (empty state ready).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/widget_preview_favorites.xml` | Deleted | — |
| `app_v2/src/main/res/layout/widget_preview_resource_launch.xml` | Deleted | — |
| `app_v2/src/main/res/layout/widget_preview_camera_photos.xml` | Deleted | — |
| `app_v2/src/main/res/layout/widget_preview_random_music.xml` | Deleted | — |
| `app_v2/src/main/res/layout/widget_preview_continue_reading.xml` | Deleted | — |
| `docs/FEATURES.md` | Modified | +2 |
| `docs/FEATURES_RU.md` | Modified | +2 |
| `docs/FEATURES_UK.md` | Modified | +2 |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | — |
| `dev/CATALOG/app_v2.md` | Regenerated | — |

---

## Steps

### Step 05.1 — Delete orphaned widget preview layouts

**Files:** `app_v2/src/main/res/layout/widget_preview_favorites.xml`, `app_v2/src/main/res/layout/widget_preview_resource_launch.xml`, `app_v2/src/main/res/layout/widget_preview_camera_photos.xml`, `app_v2/src/main/res/layout/widget_preview_random_music.xml`, `app_v2/src/main/res/layout/widget_preview_continue_reading.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Delete all five `widget_preview_*.xml` layout files. They are unreferenced after Phase 02 removed `android:previewLayout` from the metadata. Confirm via `Grep` that no Kotlin source, no XML, and no AndroidManifest references `widget_preview_*` layouts before deletion. The static `widget_preview_*.png` drawables remain — they are referenced by `android:previewImage`.

**Verification:**

- `Glob` — none of the five `widget_preview_*.xml` files exist under `app_v2/src/main/res/layout/`.
- `Grep` — `widget_preview_favorites` returns hits only inside `app_v2/src/main/res/drawable/` (the PNG) and `app_v2/src/main/res/xml/` (previewImage reference); zero hits inside `app_v2/src/main/res/layout/` and zero hits inside `app_v2/src/main/java/`.
- Same shape verification for the other four `widget_preview_*` keys.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS (zero hits in java/, layout/widget_preview_*.xml all removed; zero references in src/). Files: 5 widget_preview_*.xml deleted. Dev log recorded.

---

### Step 05.2 — Add user-facing bullet to `docs/FEATURES.md` + RU + UK mirrors

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — independent of 05.1

**Prompt for developer:**

> Locate the existing widgets section in each of the three FEATURES files. Append one bullet:
> - EN: "Widgets adapt to launcher theme on Android 12+ (Material You) and show distinct names and chip icons in the system picker; Favorites widget displays a hint when no favorites are saved yet."
> - RU: "Виджеты подстраиваются под тему лаунчера на Android 12+ (Material You), имеют индивидуальные имена и иконки в системном picker; виджет «Избранное» показывает подсказку, когда список пуст."
> - UK: "Віджети адаптуються до теми лаунчера на Android 12+ (Material You), мають індивідуальні назви та піктограми в системному picker; віджет «Обране» показує підказку, коли список пустий."
>
> Strings pass `docs/COMMUNICATION_POLICY.md` §6 tone checklist.

**Verification:**

- `Grep` — `Material You` matches at least once in `docs/FEATURES.md`.
- `Grep` — `Material You` matches at least once in `docs/FEATURES_RU.md`.
- `Grep` — `Material You` matches at least once in `docs/FEATURES_UK.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 3/3 PASS (Material You bullet present in EN/RU/UK §18). Files: docs/FEATURES.md (+1), docs/FEATURES_RU.md (+1), docs/FEATURES_UK.md (+1). Dev log recorded.

---

### Step 05.3 — Regenerate `app_v2` catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 05.1, Step 05.2

**Prompt for developer:**

> Run from project root: `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` then `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`. The scan picks up the modified `FavoritesWidgetProvider`, `FavoritesWidgetService`, `MainActivity`. Manual `role` and `status` fields on existing entries are preserved.

**Verification:**

- `Grep` — `FavoritesWidgetProvider` matches at least once in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `FavoritesWidgetService` matches at least once in `dev/CATALOG/app_v2.jsonl`.
- The `dev/CATALOG/app_v2.md` modification timestamp is newer than the start of the phase.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 3/3 PASS (994 records scanned and rendered; FavoritesWidgetProvider + FavoritesWidgetService present; .md timestamp 2026-05-10 00:54). Files: dev/CATALOG/app_v2.jsonl + .md regenerated. Dev log recorded.

---

### Step 05.4 — Run strings audit and add dev-log entries

**Files:** —
**Depends on:** Step 05.2

**Prompt for developer:**

> Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "widget_"`. Expect exit code 0 (all `widget_*` keys mirrored across EN/RU/UK). For every file modified in Phases 01..05 that has not yet been logged, add an entry via `pwsh -File scripts/add_to_dev_log.ps1 "<path>" "<target>" "<short description>"`. Cross-reference the union of `Files Touched` tables from PHASE_01..PHASE_05 — every entry must appear exactly once in `dev/CHANGELOG.md` for this spec.

**Verification:**

- `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "widget_"` returns exit code 0.
- `Grep` — `S0134` matches at least once in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 2/2 PASS (strings audit exit 0 — 18 widget_ keys present in EN/RU/UK; CHANGELOG mentions S0134 46 times across all phase commits). No additional dev log entries needed — every "Files Touched" file already logged per-step.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles — final build below.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" of every phase via `pwsh -File scripts/add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. After this phase advances to ✅ Done, run `/spec-check S0134` to set spec status to `Verified` and trigger Timber-tag removal sweep across all `.kt` files modified in this spec.

---

## Rollback Plan

Revert phase commit(s). Documentation and catalog rollback is harmless; orphaned preview layouts can be restored from git if needed.
