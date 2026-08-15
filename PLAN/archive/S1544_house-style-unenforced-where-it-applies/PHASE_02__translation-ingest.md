# Phase 02 - Translation ingest

**Strategic spec:** [`../S1544_house-style-unenforced-where-it-applies.md`](../S1544_house-style-unenforced-where-it-applies.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Normalize every returned translation line to the style of its English source before it is written to a resource, on the single door all ten bulk locales pass through.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/utils/locale-bulk-import.ps1` | Modified | ≤ 260 |

---

## Steps

### Step 02.1 - Normalize the value inside the existing per-line loop

**Files:** `scripts/utils/locale-bulk-import.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Dot-source `scripts/quality/lib/house-text-style.ps1` next to the existing `locale-set.ps1` include. In the per-line loop that already rejects an empty line and a placeholder mismatch, call `Convert-HouseStyleText -Area ResourceValue` on the accepted value after the format-signature check passes and before the value is placed in the per-file map, and use the converted text from there on. Apply only the ellipsis and long-dash rules here; exclude the `ё` rule by name, because the returned text is not Russian in any of the ten bulk locales. Record each line whose text changed in a `normalized` list alongside the existing `rejected` list.

**Why:**

Strategic §5.2 puts the rule on the door the debt came through: the English source of every affected key is already house-style clean and the external service rewrote it, so all 440 measured violations entered here.

**Verification:**

- `Grep` - `house-text-style.ps1` dot-sourced exactly once in the file.
- `Grep` - `Convert-HouseStyleText` called inside the per-line loop, after the `Get-FormatSignature` comparison.
- `Grep` - the format-signature check still precedes it and is unchanged.
- Run the script with `-DryRun` against a returned-translation file whose values carry an ellipsis character and an en dash. Expected: a non-zero `normalized` count, `rejected 0`, exit 0, and an unchanged resource mtime. Measured 2026-08-14 over 1882 lines: `accepted 1882 | rejected 0 | normalized 10`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Dry-run over a synthetic re-typographed return: lines 1882, accepted 1882, rejected 0, normalized 10, exit 0, values-de mtime unchanged. Format-signature check at line 165 still precedes the style call at 174.

---

### Step 02.2 - Report every normalized line and keep the exit contract

**Files:** `scripts/utils/locale-bulk-import.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Extend the summary line that already prints `lines / accepted / rejected` with a `normalized` count, and print one `normalized: line <n> <key> (<rule names>)` line per changed value, mirroring the existing `rejected:` loop. Leave the exit codes exactly as documented in the header: normalization is not a rejection, so a run whose only event is normalization still exits 0. Update the `.OUTPUTS` block to say so.

**Why:**

Strategic ADR-2 fixes rather than rejects, since a lost format token crashes the app while a stray dash does not, and §5.2 requires each corrected line to be named so the owner can see what the service rewrote.

**Verification:**

- `Grep` - `normalized` appears in both the summary line and a per-line loop.
- `Grep` - the `.OUTPUTS` block states that normalization alone still exits 0.
- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` - exit 0.
- A `-DryRun` run over a locale known to carry violations prints a non-zero `normalized` count.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Dry-run over a synthetic re-typographed return: lines 1882, accepted 1882, rejected 0, normalized 10, exit 0, values-de mtime unchanged. Format-signature check at line 165 still precedes the style call at 174.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] No build required - this phase touches no compiled source.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

A returned translation can no longer carry a style divergence into a resource file. Any violation found after this phase either predates it or came in through the authored path Phase 03 covers.

---

## Rollback Plan

Revert the phase commit - the import's validation and exit contract are otherwise untouched, and no resource file is written by this phase.
