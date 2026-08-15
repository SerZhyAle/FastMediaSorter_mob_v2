# Phase 06 - Docs + Catalog Cleanup

**Strategic spec:** [`../S0535_unify-collapsible-groups.md`](../S0535_unify-collapsible-groups.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02, 03, 04, 05
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-20
**Completed:** 2026-06-20

---

## Objective

Document the unified collapsible-group pattern as the recommended approach for new screens, record the delivered capability in the developer inventory, and finalize catalog/dev-log bookkeeping.

---

## Prerequisites

- [ ] Phases 01-05 are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ARCHITECTURE.md` | Modified | +~30 lines |
| `docs/ALL_FEATURES.jsonl` | Modified | +1 record |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

> `docs/ARCHITECTURE.md` is the EN engineering reference (no `_RU`/`_UK` counterpart in `docs/`); single-file edit. `docs/FEATURES*.md` is NOT touched - strategic §8 = "Без изменений".

---

## Steps

### Step 06.1 - Document the recommended collapsible-group pattern

**Files:** `docs/ARCHITECTURE.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a "Collapsible section groups" subsection to `docs/ARCHITECTURE.md` describing the recommended pattern for new screens: one header widget (`CollapsibleSectionHeader`) with the graphical chevron + rotation indicator and optional summary; the bold title typography token (strategic §3.1/§5.1 - title text is bold on every screen); the `CollapsibleSectionsManager.register(header, container, key, defaultExpanded)` orchestrator over `CollapsibleSectionStore`; the `<screen>__<section>` key convention; the default-expansion rule (dense config screens and list groupings collapsed, short dialogs expanded - research 02 D4); and the accessibility contract (state announced, chevron tinted via theme, no hardcoded colors). State plainly that new collapsible UI must use this, not a bespoke mechanism.

**Verification:**

- `Grep` - `Collapsible section groups` (or equivalent heading) present in `docs/ARCHITECTURE.md`.
- `Grep` - `CollapsibleSectionsManager` and `CollapsibleSectionStore` both referenced in `docs/ARCHITECTURE.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-20 - Verification 2/2 PASS. Added "Collapsible Section Groups (MANDATORY)" subsection to `docs/ARCHITECTURE.md` covering header widget, orchestrator, store + migration, key convention, default-expansion rule, accessibility, bold title token, and the list-consumer pattern; `CollapsibleSectionsManager` + `CollapsibleSectionStore` both referenced.

---

### Step 06.2 - Record the delivered capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 06.1

**Prompt for developer:**

> Add one capability record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing the unified collapsible-group system (single header, graphical animated indicator, consolidated state persistence with migration, accessibility state announcement) as a developer inventory entry. EN-only. Do not edit `docs/FEATURES*.md`.

**Verification:**

- `Grep` - a collapsible/unified-section record present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-20 - Verification 2/2 PASS. Added ALL_FEATURES record `settings.collapsible_groups` (Area "Settings & Navigation", flavors standard/lite/photos/legacy/vr, spec S0535). `validate.ps1` PASS (361 records). `docs/FEATURES*` untouched (strategic §8).

---

### Step 06.3 - Regenerate catalog and finalize dev log

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 06.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to regenerate the local catalog after all class additions/deletions across Phases 01-05. Set `role`/`status` for the new public classes (`CollapsibleSectionsManager`, `CollapsibleSectionStore`, `CollapsibleSectionStateMigration`, and any binder) via `set.ps1` if the scan left them blank. Ensure a dev-log entry exists for the docs change.

**Verification:**

- `Grep` - `CollapsibleSectionsManager` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` - `item_stats_section_header` absent from `dev/CATALOG/app_v2.jsonl` (deleted layout not indexed).
- `dev/CHANGELOG.md` has an entry referencing the docs update.

**Status:** `[x] done`

**Step Log:**

- 2026-06-20 - Verification 3/3 PASS. `catalog_sync.ps1 -Module app_v2` regenerated (1904 records); `CollapsibleSectionsManager` present, `item_stats_section_header` absent. Set role + status=tested for the 3 new classes (store .kt = 2 records). Dev-log entry added via post-change (Doc).

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles - no code change in Phase 06 (docs/catalog only); Phase 05 forced build was SUCCESSFUL.
- [x] `dev/CHANGELOG.md` has per-phase entries across the spec.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [x] Ready for `/spec-check S0535`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next action after this phase: device verification of the unified pattern (BlockNeedUserTest), then `/spec-check S0535`.

---

## Rollback Plan

Revert phase commit(s) - documentation and catalog only; no runtime surface affected.
