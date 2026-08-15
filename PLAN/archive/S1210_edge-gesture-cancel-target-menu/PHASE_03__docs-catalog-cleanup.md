# Phase 03 - Docs and catalog cleanup

**Strategic spec:** [`../S1210_edge-gesture-cancel-target-menu.md`](../S1210_edge-gesture-cancel-target-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Record the delivered capability, refresh the class catalog, and close the change through the mechanical facade.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | ≤ 3 |
| `dev/CATALOG/app_v2.jsonl` | Modified | regenerated |

---

## Steps

### Step 03.1 - Record the capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add the shipped capability through `scripts/all_features/add.ps1` (EN only): an edge gesture can be called off by returning the finger to the cancel target at the edge, and the chosen action runs on lift. Flavors come from the actual gate - `noLegal` always, `standard` under the edge-overlay build flag - read the written record back to confirm.

**Verification:**

- `Grep` - the new record matches once in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x] done`

---

### Step 03.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 03.1

**Prompt for developer:**

> Run `scripts/catalog_sync.ps1 -Module app_v2` once for the ticket so the changed public surface of the two touched classes is indexed.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "ScreenGestureHintView"` returns the class.

**Status:** `[x] done`

---

### Step 03.3 - Close the change

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 03.2

**Prompt for developer:**

> Close through `scripts/post-change.ps1` with `-ChangeType Kotlin -ScopeToFile`, one dev-log entry for the ticket rather than one per file.

**Verification:**

- `post-change.ps1` reports PASS and exits 0.
- `Grep` - `S1210` matches once in `dev/CHANGELOG.md`.

**Status:** `[x] done`

---

## Step Log

- 2026-07-27 - All three steps executed in one `close-and-log.ps1` pass: status -> BlockNeedUserTest with the device-test note, three dev-log entries, feature record, catalog scan + render. The capability is `noLegal`-only, so the record went to the gitignored `docs/ALL_FEATURES_noLegal.jsonl` via `-FeatNoLegal` - `standard` ships the edge overlay only under `-Pfms.edgeGestureOverlay=on`, which is off by default.
- 2026-07-27 - `assert-no-ticket-logs.ps1` re-run after the status flip: expected 0 | actual 0. The S1210 probe is legal now that the ticket is BlockNeedUserTest.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `docs/FEATURES*.md` untouched - the showcase is release-owned.
- [ ] Ticket advanced to `BlockNeedUserTest` with a status note describing the on-device check.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Documentation-only phase - revert the commit; no runtime surface is affected.
