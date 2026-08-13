# Phase 06 - Docs and catalog cleanup

**Strategic spec:** [`../S1201_radio-logo-atlas.md`](../S1201_radio-logo-atlas.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Steps done:** 3 / 3
**Started:** -
**Completed:** 2026-07-26

---

## Objective

Record the delivered capability, regenerate the class catalog, and document the atlas in the delivery README so the next person can rebuild it without reading the packer.

---

## Prerequisites

- [ ] Phases 01-05 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (via `add.ps1`) | - |
| `delivery/stream-catalog/README.md` | Modified | - |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

---

## Steps

### Step 06.1 - Record the capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one record via `scripts/all_features/add.ps1` describing the shipped capability in user terms (stream channels without a capturable frame show the station's own logo in the grid). Read `-Flavors` off the actual gate - the four flavor DI modules that contribute the descriptor - not off a sibling record. EN only.

**Verification:**

- `Grep` - `S1201` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.
- The record's `flavors` field equals the set of flavor modules touched in Phase 03 Step 03.3.

**Status:** `[x]` done

---

### Step 06.2 - Document the rebuild procedure

**Files:** `delivery/stream-catalog/README.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> Add a section for the logo atlas beside the existing channel-preview section: which packer switch builds it, where the source artwork cache lives and what a `.miss` marker means, the tile geometry and its app-side mirror class, and the rule that a rebuilt sheet needs a new element revision plus fresh pins rather than a re-upload under the same name.

**Verification:**

- `Grep` - `stream-logo-atlas` and `-WithStreamLogos` both present in the README.
- `Grep` - the README names `StreamLogoAtlasSlicer` as the app-side geometry mirror.

**Status:** `[x]` done

---

### Step 06.3 - Regenerate the catalog and close the ticket

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 06.2

**Prompt for developer:**

> Run `scripts/catalog_sync.ps1 -Module app_v2` once, then set `role` and `status` for the two new classes via `dev/CATALOG/scripts/set.ps1`. Close the ticket through `close-and-log.ps1` with the dev-log batch for every file this plan touched.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "*StreamLogoAtlas*"` returns two records, each with a non-empty `role`.
- `dev/CHANGELOG.md` carries an entry for every file listed across Phases 01-05.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] `/spec-check S1201` returns `Verified`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Documentation and index only - revert the commit; no runtime effect.
