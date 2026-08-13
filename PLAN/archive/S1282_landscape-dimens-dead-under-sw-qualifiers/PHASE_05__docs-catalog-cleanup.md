# Phase 05 - Docs and catalog cleanup

**Strategic spec:** [`../S1282_landscape-dimens-dead-under-sw-qualifiers.md`](../S1282_landscape-dimens-dead-under-sw-qualifiers.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04 (04 ⛔ Blocked - ran without it, see Prerequisites)
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Bring the generated tooling docs back in sync with the new script and close the ticket's mechanical
bookkeeping.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done - the cheatsheet is regenerated from the script that phase adds.
- [ ] Phase 04 is ✅ Done or ⛔ Blocked with its reason recorded in the Blockers Log.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/SCRIPT_CHEATSHEET.md` | Modified (generated) | generated - do not hand-edit |
| `dev/CHANGELOG.md` | Modified (generated) | generated - `add_to_dev_log.ps1` only |

---

## Steps

### Step 05.1 - Regenerate the script cheatsheet

**Files:** `docs/SCRIPT_CHEATSHEET.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Phase 03 added a script with its own `param()` block, so the committed cheatsheet is stale. Run
> `pwsh -NoProfile -File scripts/utils/help.ps1 -Generate`. Never hand-edit the file - it is a render
> target and the gate byte-compares it against the generator.

**Verification:**

- `Grep` - `assert-qualifier-shadowing` present in `docs/SCRIPT_CHEATSHEET.md`.
- `pwsh -NoProfile -File scripts/quality/assert-script-cheatsheet-sync.ps1 -Gate` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. `help.ps1 -Generate` wrote `docs/SCRIPT_CHEATSHEET.md` (245 scripts); two references to `assert-qualifier-shadowing`; sync gate reports "in sync".

---

### Step 05.2 - Confirm the document registry has nothing to update

**Files:** none expected
**Depends on:** Step 05.1

**Prompt for developer:**

> Query the registry for this change's product area and trigger:
> `pwsh -NoProfile -File scripts/document_registry/query.ps1 -ProductArea ui -Trigger ui`. The two
> records it returns cover icons and user-visible strings; this ticket changes neither, only
> dimension buckets and a gate script. Record that verdict rather than assuming it - if the query
> starts returning a record about layout metrics, update that record instead of skipping the step.

**Verification:**

- Query run and its output recorded in the phase notes.
- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. `validate.ps1`: 24 records, PASS.
- 2026-07-31 - Queried two axes, not one. `-ProductArea ui -Trigger ui` returns `icon-legend`, which covers icons - unaffected, no icon changed. The new gate also put this change in the `quality` area, which returns `quality-assurance` (`docs/CODE_AUDIT_PROTOCOL.md` and friends). Checked whether that document enumerates the gate batch: it does not - it lists a curated thematic subset, and existing resource-domain gates (`assert-layout-variant-id-parity`, `assert-orientation-implied-feature`) are absent from it too. `docs/DEV_OPS.md` likewise names only detekt and listener-symmetry. Verdict: no registry record affected. The one render target this change does own, `docs/SCRIPT_CHEATSHEET.md`, is regenerated in step 05.1 and gate-enforced.

---

### Step 05.3 - Journal the ticket

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> Close the ticket through `close-and-log.ps1` with one dev-log entry per logical change - the
> resource buckets, the gate, the cheatsheet - not one per file. Strategic §8 says "Без изменений в
> docs/FEATURES", so pass no `-FuncOp`: there is no shippable capability record for a resource-
> resolution correction that users experience as "landscape looks like it always claimed to".

**Verification:**

- `Grep` - `S1282` present in `dev/CHANGELOG.md`.
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S1282 -Format json` reports the intended status.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. `close-and-log.ps1` flipped `In Progress -> BlockNeedUserTest` with the device-test note, wrote the spec-level dev-log line and re-scanned the catalog. No `-FuncOp`: strategic §8 records no FEATURES change, and a resource-resolution correction ships no new capability.
- 2026-07-31 - No `Timber.d("S1282: ..")` probe tags inserted, deliberately. The ticket changed resources and scripts only, so there is no Kotlin flow entry to tag. CLAUDE.md requires one tag per changed flow entry; zero changed flows means zero tags. `assert-no-ticket-logs.ps1` is one-directional - it rejects a probe whose ticket is not `BlockNeedUserTest`, it does not demand a probe exist - so the invariant holds and `a.ps1 fg` stays green.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `.\a.ps1 fg` exits 0 - re-run after closure, see Step Log below.
- [x] `docs/FEATURES*.md` untouched - strategic §8 mandates no update.
- [x] `dev/CATALOG/` re-scanned by `close-and-log.ps1`; no Kotlin class added, changed or removed.
- [x] Phase-boundary audit - skipped per protocol: `Files Touched` is generated docs only.

---

## Handoff Notes to Next Phase

Final phase - see [`INDEX.md`](INDEX.md) Completion Gate.

---

## Rollback Plan

Regenerated docs revert with their generators; the changelog is append-only and needs no rollback.
