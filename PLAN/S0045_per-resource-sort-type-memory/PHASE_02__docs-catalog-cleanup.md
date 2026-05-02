# Phase 02 — Docs, Catalog, Cleanup

**Strategic spec:** [`../S0045_per-resource-sort-type-memory.md`](../S0045_per-resource-sort-type-memory.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** — (final phase)
**Steps done:** 3 / 3
**Started:** 2026-05-02
**Completed:** 2026-05-02

---

## Objective

Update trilingual feature docs with the sort-memory bullet, regenerate the module catalog, and record dev log entries for all modified files.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | existing |
| `docs/FEATURES_RU.md` | Modified | existing |
| `docs/FEATURES_UK.md` | Modified | existing |

---

## Steps

### Step 02.1 — Update `docs/FEATURES.md` (EN)

**Files:** `docs/FEATURES.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In `docs/FEATURES.md`, in the section covering sort modes or per-resource settings (look for the existing bullets about "Extensive sort modes" and "Per-resource settings"), add a concise bullet: `- **Sort mode memory**: The sort mode chosen in Browse or Slideshow is automatically saved per resource and restored on the next visit — no manual reconfiguration needed.`

**Verification:**

- `Grep` pattern `Sort mode memory` in `docs/FEATURES.md` — must match exactly once.

**Status:** `[x]` done — bullet added at `docs/FEATURES.md:62`.

---

### Step 02.2 — Update `docs/FEATURES_RU.md` and `docs/FEATURES_UK.md`

**Files:** `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Mirror the EN bullet into both localised files at the matching section position.
> - **RU:** `- **Память режима сортировки**: Выбранный в Browse или Слайдшоу режим сортировки автоматически сохраняется для каждого ресурса и восстанавливается при следующем посещении — без ручной настройки.`
> - **UK:** `- **Пам'ять режиму сортування**: Обраний у Browse або Слайдшоу режим сортування автоматично зберігається для кожного ресурсу та відновлюється при наступному відвідуванні — без ручного налаштування.`

**Verification:**

- `Grep` pattern `Память режима сортировки` in `docs/FEATURES_RU.md` — must match exactly once.
- `Grep` pattern `Пам'ять режиму сортування` in `docs/FEATURES_UK.md` — must match exactly once.

**Status:** `[x]` done — RU bullet at `docs/FEATURES_RU.md:60`, UK bullet at `docs/FEATURES_UK.md:60`.

---

### Step 02.3 — Regenerate catalog and add dev log entries

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Steps 02.1, 02.2

**Prompt for developer:**

> 1. Run catalog scan + render for the `app_v2` module:
>    ```powershell
>    pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
>    pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
>    ```
> 2. Add dev log entries for all files changed in Phase 01 and Phase 02:
>    ```powershell
>    .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "S0045" "Add sort-memory bullet (EN)"
>    .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "S0045" "Add sort-memory bullet (RU)"
>    .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "S0045" "Add sort-memory bullet (UK)"
>    .\scripts\add_to_dev_log.ps1 "dev/CATALOG/app_v2.jsonl" "S0045" "Catalog regen after Phase 01 changes"
>    ```

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.md` exists and its modification time is newer than `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt`.
- `Grep` pattern `S0045` in `dev/CHANGELOG.md` — must return at least 3 hits (one per docs file + one from Phase 01).

**Status:** `[x]` done — catalog regenerated; dev log entries recorded (see `dev/CHANGELOG.md` 2026-05-02 00:16:22 block).

---

## Phase Done Criteria

- [ ] Every step above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] `/spec-check S0045` run and result recorded in strategic spec `## Last Audit` block.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert docs edits and catalog regen. No code changes in this phase.
