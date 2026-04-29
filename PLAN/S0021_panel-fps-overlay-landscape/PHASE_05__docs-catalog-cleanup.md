# Phase 05 — Docs / Catalog Cleanup

**Strategic spec:** [`../S0021_panel-fps-overlay-landscape.md`](../S0021_panel-fps-overlay-landscape.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all previous phases
**Blocks:** none — final phase
**Steps done:** 3 / 3
**Started:** —
**Completed:** —

---

## Objective

Document the new "Show FPS over player" feature in `docs/FEATURES*.md` (3 locales) and refresh `dev/CATALOG/app_v2.jsonl`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` | Modified by scan.ps1 | n/a |
| `dev/CATALOG/app_v2.md` | Modified by render.ps1 | n/a |

---

## Steps

### Step 05.1 — Add user-facing bullet to `docs/FEATURES*.md`

**Files:** three `FEATURES*.md` files
**Depends on:** — start of phase

**Prompt for developer:**

> In the existing "Video" section of each FEATURES file, add a bullet next to / under the existing VR-HUD-FPS mention:
>
> - EN: "Diagnostic FPS counter overlay over the flat 2D player — separate setting, available on all flavors with a player."
> - RU: "Диагностический счётчик FPS поверх плоского 2D-плеера — отдельная настройка, доступна на всех флейворах с плеером."
> - UK: "Діагностичний лічильник FPS над плоским 2D-плеєром — окреме налаштування, доступне на всіх флейворах з плеєром."

**Verification:**

- `Grep` — `Diagnostic FPS counter overlay` matches exactly once in `docs/FEATURES.md`.
- `Grep` — `Диагностический счётчик FPS поверх плоского` matches exactly once in `docs/FEATURES_RU.md`.
- `Grep` — `Діагностичний лічильник FPS над плоским` matches exactly once in `docs/FEATURES_UK.md`.

**Status:** `[x]` done

---

### Step 05.2 — Refresh catalog scan + render

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` followed by `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`. Confirm the new file `PlayerFpsMeter.kt` appears as a row in the jsonl.

**Verification:**

- `Grep` — `PlayerFpsMeter` matches at least once in `dev/CATALOG/app_v2.jsonl`.
- `PowerShell` — both scan and render commands print success summaries.

**Status:** `[x]` done

---

### Step 05.3 — Final dev log sweep

**Files:** `dev/CHANGELOG.md` (via add_to_dev_log.ps1)
**Depends on:** Step 05.2

**Prompt for developer:**

> Confirm dev log entries exist for every file modified across Phases 01–04 + 05.1. Files: `AppSettings.kt`, `SettingsRepositoryImpl.kt`, `BackupData.kt`, `BackupMapper.kt`, three `strings.xml`, `fragment_settings_video.xml`, `VideoSettingsFragment.kt`, `PlayerFpsMeter.kt`, `activity_player.xml` (or relevant layout), `bg_fps_overlay.xml`, `PlayerActivity.kt`, `VrPlayerActivity.kt`, three `FEATURES*.md`. Add missing entries via `.\scripts\add_to_dev_log.ps1`.

**Verification:**

- `Grep` — in `dev/CHANGELOG.md`, `PlayerFpsMeter` matches at least once and `playerShowFps` matches at least once on a 2026-04-28 line.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.

---

## Rollback Plan

Documentation is append-only — no rollback needed.
