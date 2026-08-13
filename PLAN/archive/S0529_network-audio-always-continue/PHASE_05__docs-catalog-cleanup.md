# Phase 05 - Docs and catalog cleanup

**Strategic spec:** [`../S0529_network-audio-always-continue.md`](../S0529_network-audio-always-continue.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** -
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Regenerate the class catalog for the new factory, record the delivered capability, and finalize dev-log bookkeeping.

---

## Prerequisites

- [ ] Phases 01-04 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `docs/ALL_FEATURES.jsonl` | Modified | ≤ +1 record |

---

## Steps

### Step 05.1 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` so the new `NetworkAwareMediaSourceFactory` is indexed; set its `role`/`status` via `set.ps1` if missing.

**Verification:**

- `Grep` - `NetworkAwareMediaSourceFactory` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[ ]` not done

---

### Step 05.2 - Record the delivered capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> This fixes a documented setting that silently failed for network/cloud audio - it restores promised behaviour rather than adding a brand-new capability. Record one inventory record only if the streaming-continue behaviour counts as a newly reliable capability, via `scripts/all_features/add.ps1` (EN-only). If it is purely a fix, skip and note "fix - no ALL_FEATURES record" in the dev log.

**Verification:**

- `Grep` - either a new `S0529` record exists in `docs/ALL_FEATURES.jsonl`, or the dev-log entry states the fix-only rationale.

**Status:** `[ ]` not done

---

### Step 05.3 - Finalize dev log

**Files:** (bookkeeping)
**Depends on:** Step 05.1, Step 05.2

**Prompt for developer:**

> Ensure one dev-log entry per logical change across the phases via `.\scripts\add_to_dev_log.ps1` (batch acceptable). No `docs/FEATURES*.md` edits (strategic §8 = «Без изменений»).

**Verification:**

- `Grep` - `S0529` referenced in `dev/CHANGELOG.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] Dev log complete for all modified files.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Device verification on a real slow SFTP resource (the original log scenario) is the remaining gate via `/spec-test-device` + `/spec-check`.

---

## Rollback Plan

Bookkeeping only - nothing to roll back beyond regenerating the catalog.
