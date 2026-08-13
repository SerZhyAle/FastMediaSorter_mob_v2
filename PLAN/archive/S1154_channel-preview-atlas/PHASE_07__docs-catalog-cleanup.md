# Phase 07 - Docs and catalog cleanup

**Strategic spec:** [`../S1154_channel-preview-atlas.md`](../S1154_channel-preview-atlas.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (README + ALL_FEATURES + catalog; Phase 06 asset-name finalization deferred with Phase 06)
**Depends on:** all (Phase 01-06)
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-07-23
**Completed:** 2026-07-23

---

## Objective

Document the atlas format for third-party catalog consumers (Q-B), record the shipped capability in the developer feature inventory, and close out the catalog/dev-log bookkeeping.

---

## Prerequisites

- [ ] Phases 01-06 are ✅ Done (the atlas format + asset name are final).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `delivery/stream-catalog/README.md` | Modified | n/a |
| `docs/ALL_FEATURES.jsonl` | Modified (via tool) | n/a |

> No `res/layout/*.xml` edits - no landscape-parity obligation. No `docs/` page or `DOCUMENT_REGISTRY.jsonl` entry is required (Q-B resolved: README addendum only; the registry has no stream-catalog record today and none is mandated).

---

## Steps

### Step 07.1 - README atlas-format addendum

**Files:** `delivery/stream-catalog/README.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add an "atlas" addendum to the stream-catalog README mirroring the existing CSV-contract section (Q-B): document the channel-preview atlas as a separately published release asset - the sheet dimensions and tile geometry (240x135 tiles, 34 columns, single 8192x8192 sheet, `index = row*34 + col`), and the `url->index` sidecar (`channel-preview-coords.json`) format - so a third-party consumer of our catalog can slice the same sheet. Use `..` not `...` and plain hyphens in prose.

**Verification:**

- `Grep` - `channel-preview-coords.json` documented in `delivery/stream-catalog/README.md`.
- `Grep` - the tile geometry (`240`, `135`, `34`) documented as the slicing contract.

**Status:** `[x]` done

---

### Step 07.2 - Record the capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - independent

**Prompt for developer:**

> Add one `Streams` capability record via `pwsh -NoProfile -File scripts/all_features/add.ps1` (EN-only): video channels show a preview from a downloadable channel-preview atlas in grid mode before the first watch, replaced by the user's own captured frame. Do not edit `docs/FEATURES*.md` (that is `/skill-release`-owned). Validate with `scripts/all_features/validate.ps1`.

**Verification:**

- `Grep` - a record mentioning the channel-preview atlas is present in `docs/ALL_FEATURES.jsonl`.
- Run `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit 0.

**Status:** `[x]` done

---

### Step 07.3 - Catalog + dev-log bookkeeping

**Files:** (regeneration only - no source edit)
**Depends on:** Step 07.1, Step 07.2

**Prompt for developer:**

> Regenerate the class catalog for all classes added across the feature (`pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`) and confirm `role`+`status` are set for `ChannelPreviewAtlasStore`, `ChannelPreviewAtlasSlicer`, `StreamAtlasPromptManager`. Ensure a dev-log entry exists for the README + ALL_FEATURES change.

**Verification:**

- Run `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "*ChannelPreviewAtlas*"` - both classes present with a non-`unknown` role.
- `dev/CHANGELOG.md` has an entry covering this phase.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 07.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-07)` returns zero hits.
- [ ] Dev log entry added for the README + ALL_FEATURES change.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated and all new classes carry `role`+`status`.

---

## Handoff Notes to Next Phase

Final phase - see [`INDEX.md`](INDEX.md) Completion Gate. Run `/spec-check S1154` to advance the strategic spec to `Verified`.

---

## Rollback Plan

Revert the phase commit(s). Docs-only + inventory record - no runtime impact.
