# Phase 06 - Docs and catalog cleanup

**Strategic spec:** [`../S1642_launcher-section-header-grid-span.md`](../S1642_launcher-section-header-grid-span.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04, Phase 05
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-08-15
**Completed:** 2026-08-15

---

## Objective

Close the ticket's bookkeeping: regenerate the class catalog, record the capability, and journal every
file the five implementation phases touched.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `docs/ALL_FEATURES.jsonl` | Modified | n/a |
| `dev/CHANGELOG.md` | Appended via script | n/a |

---

## Steps

### Step 06.1 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once for the whole ticket. No new class was introduced, so no `set.ps1` role or status entry is needed; the sync exists to pick up the changed signatures on `LauncherSectionMembership`, `LauncherGridGeometry` and `LauncherDesktopRepository`.

**Why:**

not stated in strategic spec

**Verification:**

- Command exits 0.
- `Grep` - `normalizeSectionSpans` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - catalog_sync ran with each phase closure - normalizeSectionSpans present in dev/CATALOG/app_v2.jsonl (2 hits). ALL_FEATURES record launcher.section-header-compact added for standard,noLegal - flavors read from docs/FLAVOR_MATRIX.md SUPPORT_LAUNCHER row, not from memory; validate.ps1 PASS 715 records; zero S1642 hits in docs/FEATURES*.md per strategic section 8. post-change closed every phase and the probe set: all PASS, final run PASS WITH ADVISORIES (1) - document-registry, unattributable, and document_registry validate.ps1 plus generate.ps1 -Check both clean.

---

### Step 06.2 - Record the capability change

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 06.1

**Prompt for developer:**

> Add one `CHANGE` record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing the launcher desktop section header as a compact two-cell header whose row also holds shortcuts, scoped to the flavors the launcher ships in. Write no entry in `docs/FEATURES*.md`.

**Why:**

Strategic §8 rules that the existing customisable-desktop description is refined rather than joined by a
new showcase entry, and that no new entry is created until separate user value is proven.

**Verification:**

- Command exits 0.
- `Grep` - `S1642` present in `docs/ALL_FEATURES.jsonl`.
- `Grep` - `S1642` returns zero hits in `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - catalog_sync ran with each phase closure - normalizeSectionSpans present in dev/CATALOG/app_v2.jsonl (2 hits). ALL_FEATURES record launcher.section-header-compact added for standard,noLegal - flavors read from docs/FLAVOR_MATRIX.md SUPPORT_LAUNCHER row, not from memory; validate.ps1 PASS 715 records; zero S1642 hits in docs/FEATURES*.md per strategic section 8. post-change closed every phase and the probe set: all PASS, final run PASS WITH ADVISORIES (1) - document-registry, unattributable, and document_registry validate.ps1 plus generate.ps1 -Check both clean.

---

### Step 06.3 - Journal the ticket

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 06.2

**Prompt for developer:**

> Close through the facade: `pwsh -NoProfile -File scripts/post-change.ps1 -Files "<every file the five phases touched>" -ScopeToFile -Target "S1642" -Description "Launcher: section header spans 2x1 and shares its row with shortcuts" -ChangeType Mixed -Module app_v2`. Name the whole changed set in `-Files`, not one representative file.

**Why:**

not stated in strategic spec

**Verification:**

- `post-change: PASS` printed, or `PASS WITH ADVISORIES` with each advisory read and resolved.
- Command exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - catalog_sync ran with each phase closure - normalizeSectionSpans present in dev/CATALOG/app_v2.jsonl (2 hits). ALL_FEATURES record launcher.section-header-compact added for standard,noLegal - flavors read from docs/FLAVOR_MATRIX.md SUPPORT_LAUNCHER row, not from memory; validate.ps1 PASS 715 records; zero S1642 hits in docs/FEATURES*.md per strategic section 8. post-change closed every phase and the probe set: all PASS, final run PASS WITH ADVISORIES (1) - document-registry, unattributable, and document_registry validate.ps1 plus generate.ps1 -Check both clean.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - documentation and catalog only, no runtime surface changed.
