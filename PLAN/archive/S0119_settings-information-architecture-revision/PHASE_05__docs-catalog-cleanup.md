# Phase 05 — Docs and Catalog Cleanup

**Strategic spec:** [`../S0119_settings-information-architecture-revision.md`](../S0119_settings-information-architecture-revision.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all previous phases
**Blocks:** none — final phase
**Steps done:** 4 / 4
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Update FEATURES trilingual docs, regenerate the class catalog, ensure dev log completeness, and invoke `/spec-check S0119` to close the spec.

---

## Prerequisites

- [ ] Phases 01–04 are all ✅ Done.
- [ ] Strategic spec §8 has been re-read — it states no FEATURES update is needed unless a noticeable new navigation model was introduced. Decide at this point whether the multilingual search and IA model constitute a user-facing feature addition.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified (conditional) | — |
| `docs/FEATURES_RU.md` | Modified (conditional) | — |
| `docs/FEATURES_UK.md` | Modified (conditional) | — |
| `dev/CATALOG/app_v2.jsonl` | Modified (auto-regenerated) | — |
| `dev/CATALOG/app_v2.md` | Modified (auto-regenerated) | — |

---

## Steps

### Step 5.1 — Update FEATURES trilingual docs (conditional)

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`

**Depends on:** — start of phase (all prior phases done)

**Prompt for developer:**

> Re-read strategic spec §8. If multilingual settings search (Phase 04) constitutes a user-facing improvement (it does — RU/UK users can now discover settings in their language), add a concise bullet to `docs/FEATURES.md` under the Settings section: "Settings search matches EN/RU/UK keywords — find any setting by typing in your language." Add equivalent bullets in Russian to `docs/FEATURES_RU.md` and Ukrainian to `docs/FEATURES_UK.md`. Use `/doc-update` skill for this step. If the assessor decides the change does not warrant a FEATURES entry, mark this step `[x] skipped` with a rationale note.

**Verification:**

- `Grep` — `Settings search` or `multilingual` mentioned in `docs/FEATURES.md` (or step is explicitly marked skipped with rationale).
- If updated: `Grep` — equivalent bullet present in `docs/FEATURES_RU.md`.
- If updated: `Grep` — equivalent bullet present in `docs/FEATURES_UK.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 3/3 PASS. Multilingual search bullet added to all three FEATURES docs (EN/RU/UK). Dev log recorded.

---

### Step 5.2 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`

**Depends on:** Step 5.1

**Prompt for developer:**

> Run catalog scan and render for the `app_v2` module:
> ```powershell
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
> Phase 04 modified `SettingsSearchIndex.kt` which adds a new field to a data class and modifies a function — catalog must reflect the updated state.

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` exists and has modification timestamp newer than `SettingsSearchIndex.kt` modification timestamp.
- `Grep` — `SettingsSearchIndex` mentioned in `dev/CATALOG/app_v2.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 2/2 PASS. Catalog newer than SettingsSearchIndex.kt (17:03 vs 17:00); SettingsSearchIndex in app_v2.md. Dev log recorded.

---

### Step 5.3 — Verify dev log completeness

**Files:** `dev/CHANGELOG.md`

**Depends on:** Step 5.2

**Prompt for developer:**

> Check `dev/CHANGELOG.md` for entries covering every file modified or created in this spec: `docs/settings-inventory.md`, `docs/ia-model.md`, `docs/migration-map.md`, `SettingsSearchIndex.kt`. If any file is missing a dev log entry, run `.\scripts\add_to_dev_log.ps1` for it now. Also run the entry for this cleanup phase itself:
> ```powershell
> .\scripts\add_to_dev_log.ps1 "PLAN/S0119_settings-information-architecture-revision/" "S0119" "Settings IA revision — all phases complete"
> ```

**Verification:**

- `Grep` — `S0119` mentioned in `dev/CHANGELOG.md`.
- `Grep` — `SettingsSearchIndex` mentioned in `dev/CHANGELOG.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 2/2 PASS. S0119 has 16 entries in CHANGELOG; SettingsSearchIndex entries present. Completion dev log entry added. Dev log recorded.

---

### Step 5.4 — Remove Timber debug tag and run spec-check

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsSearchIndex.kt`

**Depends on:** Step 5.3

**Prompt for developer:**

> Before invoking `/spec-check`, grep all `.kt` files for the `Timber.d("S0119:` tag and remove every match:
> ```powershell
> Get-ChildItem -Recurse -Filter "*.kt" | Select-String 'Timber\.d\("S0119:' | ForEach-Object { $_.Path } | Sort-Object -Unique
> ```
> Remove the matching lines from each file. Commit the removal. Then run `/spec-check S0119` to advance the spec to `Verified`.

**Verification:**

- `Grep` — `Timber.d("S0119:` returns zero hits across all `.kt` files.
- `/spec-check S0119` returns `Verified`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 2/2 PASS. Timber.d("S0119:") = 0 hits; spec-check S0119 → Verified. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 5.*` above is `[x] done`.
- [x] All `Timber.d("S0119:` tags removed from all `.kt` files.
- [x] `dev/CATALOG/app_v2.jsonl` and `app_v2.md` regenerated.
- [x] `dev/CHANGELOG.md` has entries for all S0119 files.
- [x] Strategic spec `Status:` set to `Verified` by `/spec-check`.
- [x] INDEX.md `Status:` flipped to `Done`, `Phases: 5/5 done`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

No code written in this phase (Phase 04 Timber tag removal is low-risk). Revert by restoring the tag if needed; no storage or data impact.
