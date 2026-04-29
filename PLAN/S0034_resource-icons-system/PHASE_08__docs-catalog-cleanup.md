# Phase 08 — Docs & Catalog Cleanup

**Strategic spec:** [`../S0034_resource-icons-system.md`](../S0034_resource-icons-system.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04, Phase 05, Phase 06, Phase 07
**Blocks:** —
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Final-phase cleanup: update user-facing feature documentation in three languages, regenerate the code catalog, ensure dev changelog completeness, and flip the spec status so `/spec-check S0034` can run.

---

## Prerequisites

- [ ] Phases 01–07 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | — |
| `docs/FEATURES_RU.md` | Modified | — |
| `docs/FEATURES_UK.md` | Modified | — |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | — |
| `dev/CATALOG/app_v2.md` | Regenerated | — |

---

## Steps

### Step 08.1 — Update `docs/FEATURES.md`

**Files:** `docs/FEATURES.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Add one bullet under the most appropriate existing feature area (likely "Resource list" / "Main screen"). Bullet must read (in English):
>
> > Themed resource icons — every resource is displayed with a themed icon (music note / film reel / etc.) automatically chosen by type, with a connection-source badge in the corner; users can pick a specific icon per resource via the toolbar selector when creating or editing a resource.
>
> Do not duplicate existing bullets; do not re-summarise the connection-source badges (that's pre-existing behaviour).

**Verification:**

- `Grep` — `Themed resource icons` matches once in `docs/FEATURES.md`.

**Status:** `[ ]` not done

---

### Step 08.2 — Update Russian and Ukrainian mirrors

**Files:** `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 08.1

**Prompt for developer:**

> Translate the Step 08.1 bullet into Russian and Ukrainian, placing it in the same feature-area section. Russian phrasing must use `..` not `...` and `ё`/`Ё` where required (e.g. «нота / киноплёнка»). Ukrainian uses native characters (`і`, `ї`, `є`).

**Verification:**

- `Grep` — `Тематические иконки ресурсов` (or equivalent natural Russian phrasing) matches once in `docs/FEATURES_RU.md`.
- `Grep` — `Тематичні іконки ресурсів` (or equivalent) matches once in `docs/FEATURES_UK.md`.

**Status:** `[ ]` not done

---

### Step 08.3 — Regenerate code catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Phases 02–07

**Prompt for developer:**

> Run:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> The new public types (`ResourceIconSet`, `ResourceIconRegistry`, `ResourceIconDefaults`, `ResourceIconComposer`, `ConnectionBadgeMapper`, `ResolveResourceIconUseCase`, `IconPickerBottomSheet`, `IconPickerAdapter`) auto-populate. For each, set `role` and `status` via `set.ps1` per `dev/CATALOG/README.md` conventions.

**Verification:**

- `Grep` — `ResourceIconRegistry` matches in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `IconPickerBottomSheet` matches in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `ResolveResourceIconUseCase` matches in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[ ]` not done

---

### Step 08.4 — Verify dev changelog completeness

**Files:** `dev/CHANGELOG.md`
**Depends on:** Phases 02–07

**Prompt for developer:**

> Scan `dev/CHANGELOG.md` (most-recent block) and confirm an entry exists for every file listed in any phase's "Files Touched" section. If any are missing, run `.\scripts\add_to_dev_log.ps1 "<path>" "<target>" "<description>"` retroactively. Do not edit `dev/CHANGELOG.md` by hand.

**Verification:**

- `Grep` — `ResourceIconRegistry\.kt` matches at least once in `dev/CHANGELOG.md`.
- `Grep` — `IconPickerBottomSheet\.kt` matches at least once in `dev/CHANGELOG.md`.
- `Grep` — `MIGRATION_25_26` or `AppDatabase\.kt` matches at least once in `dev/CHANGELOG.md`.

**Status:** `[ ]` not done

---

### Step 08.5 — Flip spec status to `Implemented`

**Files:** `PLAN/spec-catalog.jsonl` (via CLI only — never edit directly)
**Depends on:** Steps 08.1..08.4

**Prompt for developer:**

> Run:
>
> ```powershell
> pwsh -File scripts/spec_catalog/update.ps1 -Id S0034 -Status Implemented
> ```
>
> Then trigger `/spec-check S0034` to perform the audit. The check writes its summary into `PLAN/S0034_resource-icons-system.md` `## Last Audit` and flips the journal status to `Verified` / `Partial` / `Broken` based on the result.

**Verification:**

- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0034 -Format json` shows `"status":"Implemented"` after this step (before `/spec-check`) or `"Verified"` / `"Partial"` / `"Broken"` after `/spec-check` runs.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 08.*` above is `[x] done`.
- [ ] No build required — documentation-only phase except for catalog regen which has no compile impact.
- [ ] `Grep` for `TODO(phase-08)` returns zero hits.
- [ ] Dev log entries present for `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`, `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`.
- [ ] `/spec-check S0034` has been run.

---

## Handoff Notes to Next Phase

Final phase — see [INDEX.md](INDEX.md) Completion Gate. Once `/spec-check` returns `Verified`, the spec is closed.

---

## Rollback Plan

Documentation and catalog regen are reversible by reverting the markdown / JSONL files to their previous versions. The journal status flip can be reverted with `pwsh -File scripts/spec_catalog/update.ps1 -Id S0034 -Status In Progress`.
