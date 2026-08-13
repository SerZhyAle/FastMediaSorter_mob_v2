# Phase 04 - Docs, catalog and capability record

**Strategic spec:** [`../S1587_launcher-default-first-run-polish.md`](../S1587_launcher-default-first-run-polish.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

---

## Objective

Bring the launcher's architecture description in line with the new seeding rule, regenerate the class catalog, and record the delivered capability.

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ARCHITECTURE.md` | Modified | n/a |
| `docs/ALL_FEATURES.jsonl` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |

---

## Steps

### Step 04.1 - State the seeding rule in the architecture doc

**Files:** `docs/ARCHITECTURE.md`
**Depends on:** - start of phase

**Prompt for developer:**

> In the "Launcher Mode" section, add the seeding rule the desktop now follows: the starter set is packed per section, a section header raises the packing floor, and the content section is seeded before the launcher's own actions. Keep it to the level the rest of that section uses - no class internals.

**Why:**

Strategic §5.1.1 makes the packing floor a standing contract rather than a one-off fix, and `docs/ARCHITECTURE.md` is where the launcher subsystem's contracts are recorded (S1461).

**Verification:**

- `Grep` - `docs/ARCHITECTURE.md` contains `packing floor` (or the wording chosen) inside the Launcher Mode section.
- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - ARCHITECTURE.md Launcher Mode section states the per-section packing floor and the content-first order (document_registry validate PASS); catalog_sync app_v2 OK; ALL_FEATURES record launcher.default-desktop-first-run added, validate PASS 694 records.

---

### Step 04.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once for the whole ticket. The catalog files are gitignored local indexes - regenerate, never commit.

**Why:**

CLAUDE.md section 12 requires one catalog sync per ticket after Kotlin edits, and Phase 01 changed a `core/launcher` class the catalog indexes.

**Verification:**

- `catalog_sync.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - ARCHITECTURE.md Launcher Mode section states the per-section packing floor and the content-first order (document_registry validate PASS); catalog_sync app_v2 OK; ALL_FEATURES record launcher.default-desktop-first-run added, validate PASS 694 records.

---

### Step 04.3 - Record the delivered capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add one CHANGE record through `pwsh -NoProfile -File scripts/all_features/add.ps1`, in English, describing the default launcher desktop a phone shows on first run: content section first, cells grouped under their own headers, captions readable over the wallpaper. Flavors are `standard` and `noLegal` - read them off the `SUPPORT_LAUNCHER` row of `docs/FLAVOR_MATRIX.md`, not from memory.

**Why:**

Strategic §8 designates this ticket's user-visible outcome as a CHANGE record in the inventory, and `/spec-check` surfaces a missing record at the `Verified` flip.

**Verification:**

- `Grep` - `S1587` appears in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - ARCHITECTURE.md Launcher Mode section states the per-section packing floor and the content-first order (document_registry validate PASS); catalog_sync app_v2 OK; ALL_FEATURES record launcher.default-desktop-first-run added, validate PASS 694 records.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the phase commit - documentation and inventory only; the catalog is a regenerated local index.
