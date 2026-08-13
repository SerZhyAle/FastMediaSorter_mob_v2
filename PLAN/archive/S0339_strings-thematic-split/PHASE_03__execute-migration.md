# Phase 03 - Execute Migration

**Strategic spec:** [`../S0339_strings-thematic-split.md`](../S0339_strings-thematic-split.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Move every key owned by the taxonomy out of residual `strings.xml` into its thematic file, in all three locales in lockstep, using the Phase 01 engine.

---

## Prerequisites

- [ ] Phase 02 ✅ Done (taxonomy + baselines present).
- [ ] Working tree committed at end of Phase 02 (clean rollback point).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings_*.xml` (×16 themes) | New / Modified | - |
| `app_v2/src/main/res/values-ru/strings_*.xml` | New / Modified | - |
| `app_v2/src/main/res/values-uk/strings_*.xml` | New / Modified | - |
| `app_v2/src/main/res/values{,-ru,-uk}/strings.xml` | Modified (shrinks) | - |

---

## Steps

### Step 03.1 - Dry-run the full migration

**Files:** (no writes)
**Depends on:** - start of phase

**Prompt for developer:**

> Iterate the taxonomy and invoke the `move -Prefix .. -File .. -DryRun` engine for every entry. Sum the planned move counts; confirm no per-key locale-mismatch errors surface. Capture the dry-run report to `temp/s0339_migration_dryrun.txt`.

**Verification:**

- Manual: dry-run completes exit 0 with zero locale-mismatch errors.
- Record total planned moves `expected (~1300) | actual`.

**Status:** `[ ]` not done

---

### Step 03.2 - Execute the migration

**Files:** all `strings*.xml` across the three locales
**Depends on:** Step 03.1

**Prompt for developer:**

> Re-run the same taxonomy iteration without `-DryRun`. The engine creates each thematic file on first write and moves keys in EN/RU/UK lockstep. After completion, run `-Action list` to confirm residual `strings.xml` shrank and thematic files are populated identically across locales.

**Verification:**

- `... -Action list` shows each thematic file with a non-zero, locale-consistent count.
- Residual `strings.xml` EN count dropped by the moved total - record `expected | actual`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Both `Step 03.*` are `[x] done`.
- [ ] Every thematic file has identical key count across EN/RU/UK (parity).
- [ ] Dev log entry added for the resource directories.

---

## Handoff Notes to Next Phase

Migration is mechanically complete; Phase 04 proves the union invariant and that the app still builds.

---

## Rollback Plan

`git checkout -- app_v2/src/main/res/values*/strings*.xml` restores the pre-migration state - no build config, code, or DB touched.
