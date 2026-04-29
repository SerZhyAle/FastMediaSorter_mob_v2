# Phase 05 — Docs / Catalog Cleanup

**Strategic spec:** [`../S0006_vr-fps-counter.md`](../S0006_vr-fps-counter.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 04
**Blocks:** —
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Add the user-facing documentation entry per strategic §8 to all three locale FEATURES files; regenerate the module catalog so the modified VR HUD / settings classes have fresh signatures.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done.
- [ ] All previous phases compiled successfully under `vr debug`.
- [ ] Working tree clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | unchanged (one bullet) |
| `docs/FEATURES_RU.md` | Modified | unchanged (one bullet) |
| `docs/FEATURES_UK.md` | Modified | unchanged (one bullet) |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `dev/CATALOG/app_v2.md` | Regenerated | n/a |

---

## Steps

### Step 05.1 — Add VR FPS HUD bullet to all three FEATURES files

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Locate the existing diagnostic "Diagnostic FPS counter overlay" bullet (added by S0021) in each of the three files. Immediately above it, insert a new bullet describing the VR-immersive HUD FPS counter:
>
> - EN (`docs/FEATURES.md`): `- **VR HUD FPS counter**: A separate setting "Show VR FPS" in Video settings (only effective when VR is globally enabled) draws the current frame rate in the top-right corner of the immersive HUD overlay. Off by default; the toggle change applies the next time you enter immersive mode. The value is averaged over a 500 ms window and freezes at the last valid reading during render-cycle stalls.`
> - RU (`docs/FEATURES_RU.md`): `- **Счётчик FPS в VR HUD**: Отдельная настройка «Показывать FPS в VR» в настройках видео (активна только при включённом VR глобально) выводит текущую частоту кадров в правом верхнем углу иммерсивного HUD-оверлея. По умолчанию выключено; смена флага применяется при следующем входе в иммерсив. Значение усредняется по окну 500 мс и «застывает» на последнем валидном при просадках рендер-цикла.`
> - UK (`docs/FEATURES_UK.md`): `- **Лічильник FPS у VR HUD**: Окреме налаштування «Показувати FPS у VR» у налаштуваннях відео (активне лише за глобально увімкненого VR) виводить поточну частоту кадрів у правому верхньому куті імерсивного HUD-оверлея. Типово вимкнено; зміна перемикача застосовується під час наступного входу в імерсив. Значення усереднюється у вікні 500 мс і «застигає» на останньому валідному при просіданнях рендер-циклу.`

**Verification:**

- `Grep` — `VR HUD FPS counter` matches at least once in `docs/FEATURES.md`.
- `Grep` — `Счётчик FPS в VR HUD` matches at least once in `docs/FEATURES_RU.md` (must contain `ё`).
- `Grep` — `Лічильник FPS у VR HUD` matches at least once in `docs/FEATURES_UK.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-04-28 — Verification 3/3 PASS. EN/RU/UK FEATURES bullets inserted above the existing S0021 diagnostic-overlay bullet.

---

### Step 05.2 — Regenerate the `app_v2` catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`, then `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`. Stage both regenerated artefacts together with the code from previous phases.

**Verification:**

- `Grep` — `VideoSettingsFragment` appears in `dev/CATALOG/app_v2.jsonl` (catalog scanner covers `src/main` only — VR-flavor classes intentionally excluded).
- `Glob` — `dev/CATALOG/app_v2.md` exists and was modified within the current phase window.

**Status:** `[x] done`

**Step Log:**

- 2026-04-28 — Verification 2/2 PASS. Catalog regen: 804 records.

---

### Step 05.3 — Dev log entries for FEATURES + catalog

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 05.2

**Prompt for developer:**

> Run `.\scripts\add_to_dev_log.ps1` for every file modified in this phase plus the regenerated catalog artefacts. Use one invocation per file, target string `spec-dev`, description briefly identifying S0006 phase 05.

**Verification:**

- `Grep` — `S0006` matches in `dev/CHANGELOG.md` (case-sensitive).
- `Grep` — `dev/CATALOG/app_v2` matches in `dev/CHANGELOG.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-04-28 — Verification 2/2 PASS. Five dev log entries written.

---

## Phase Done Criteria

- [x] Every `Step 05.*` is `[x] done`.
- [x] Project compiles — final `/build` `vr debug` PASS (auto-build — PASS).
- [x] No `TODO(phase-05)` hits.
- [x] All three FEATURES files contain the new bullet.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. Run `/spec-check S0006` next.

---

## Rollback Plan

Revert phase commit; FEATURES bullets and catalog regen are isolated from runtime behaviour.
