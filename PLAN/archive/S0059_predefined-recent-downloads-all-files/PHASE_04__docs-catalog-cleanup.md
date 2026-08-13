# Phase 04 — Docs / Catalog Cleanup

**Strategic spec:** [`../S0059_predefined-recent-downloads-all-files.md`](../S0059_predefined-recent-downloads-all-files.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02, Phase 03
**Blocks:** —
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Close the feature: trilingual `docs/FEATURES*.md` update per strategic §8, regenerate the source catalog so the new utility / use-case classes are indexed, and append the final dev-log entries.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] Phase 03 ✅ Done.
- [ ] Working tree compiles cleanly with both phases merged.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a (one bullet) |
| `docs/FEATURES_RU.md` | Modified | n/a (one bullet) |
| `docs/FEATURES_UK.md` | Modified | n/a (one bullet) |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `dev/CATALOG/app_v2.md` | Regenerated | n/a |

---

## Steps

### Step 04.1 — Trilingual FEATURES update

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Phases 02 and 03

**Prompt for developer:**

> Locate the section about predefined / virtual catalogs in each of the three FEATURES files (search for `Recent`, `Недавние`, `Нещодавні`). Append a single bullet under the appropriate sub-heading in each file:
> - `docs/FEATURES.md`: `- "Recent" and "Downloads" predefined catalogs show all files (not just media) by default; the "All files" toggle remains user-overridable per resource.`
> - `docs/FEATURES_RU.md`: `- Каталоги «Recent» и «Downloads» по умолчанию показывают все файлы, а не только медиа; переключатель «Все файлы» остаётся доступен в редакторе ресурса.`
> - `docs/FEATURES_UK.md`: `- Каталоги «Recent» і «Downloads» за замовчуванням показують усі файли, а не лише медіа; перемикач «Усі файли» залишається доступним у редакторі ресурсу.`
> Use `..` (two dots) and the letter `ё`/`ё` where grammatically correct in Russian.

**Verification:**

- `Grep` — `Recent.*Downloads.*all files` in `docs/FEATURES.md` matches at least once.
- `Grep` — `Recent.*Downloads.*все файлы` in `docs/FEATURES_RU.md` matches at least once.
- `Grep` — `Recent.*Downloads.*усі файли` in `docs/FEATURES_UK.md` matches at least once.
- `Grep` — `\.\.\.` (three dots) does not appear on any of the three new bullets (`..` only).

**Status:** `[ ]` not done

---

### Step 04.2 — Catalog regeneration

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run the catalog scan and render scripts in order:
> ```powershell
> & "/c/Program Files/PowerShell/7/pwsh.exe" -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> & "/c/Program Files/PowerShell/7/pwsh.exe" -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
> If any new class lacks a `role` / `status` value, fill it via `dev/CATALOG/scripts/set.ps1` according to `dev/CATALOG/README.md`. For this feature: `PredefinedResourceClassifier` → role `util`; `MigrateRecentDownloadsAllFilesUseCase` → role `usecase`.

**Verification:**

- `Grep` — `PredefinedResourceClassifier` appears in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `MigrateRecentDownloadsAllFilesUseCase` appears in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `PredefinedResourceClassifier` appears in `dev/CATALOG/app_v2.md`.
- `Grep` — `MigrateRecentDownloadsAllFilesUseCase` appears in `dev/CATALOG/app_v2.md`.

**Status:** `[ ]` not done

---

### Step 04.3 — Final dev-log entries

**Files:** all five files modified in this phase
**Depends on:** Steps 04.1, 04.2

**Prompt for developer:**

> Add one dev-log entry per modified file via `.\scripts\add_to_dev_log.ps1`:
> - `docs/FEATURES.md` / `_RU.md` / `_UK.md` → target `docs`, description `S0059 phase 04: trilingual FEATURES bullet`.
> - `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` → target `catalog`, description `S0059 phase 04: regenerate catalog`.

**Verification:**

- `Grep` for `S0059 phase 04` in `dev/CHANGELOG.md` matches at least five times.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] All FEATURES locales contain the new bullet.
- [ ] Catalog files contain both new classes.
- [ ] Dev-log has at least five `S0059 phase 04` entries.

---

## Handoff Notes to Next Phase

Final phase — see [INDEX.md](INDEX.md) Completion Gate. Next action is `/spec-check S0059`.

---

## Rollback Plan

Revert the docs commits and re-run `scan.ps1` + `render.ps1` to restore the catalog to the post-Phase-03 state. This phase introduces no behavioural change.
