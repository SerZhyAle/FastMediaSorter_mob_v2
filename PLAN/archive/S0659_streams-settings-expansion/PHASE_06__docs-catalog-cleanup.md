# Phase 06 - Docs + catalog cleanup

**Strategic spec:** [`../S0659_streams-settings-expansion.md`](../S0659_streams-settings-expansion.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01-05 (all)
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** -
**Completed:** -

---

## Objective

Regenerate the settings documentation (Rule 22) for the four new rows, record the delivered capability in `docs/ALL_FEATURES.jsonl`, and regenerate the class catalog. No source/behavior changes.

---

## Prerequisites

- [ ] Phase 01-05 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/settings/settings-manifest.json` | Modified (generated) | n/a |
| `docs/SETTINGS_REFERENCE*.md` | Modified (generated) | n/a |
| `docs/settings/settings-annotations.json` | Modified | n/a |
| `docs/ALL_FEATURES.jsonl` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` + `.md` | Modified (generated, gitignored) | n/a |

---

## Steps

### Step 06.1 - Regenerate settings docs (Rule 22) + annotations

**Files:** `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`, `docs/settings/settings-annotations.json`
**Depends on:** - start of phase

**Prompt for developer:**

> The four new rows changed the settings surface, so regenerate the manifest + reference and add annotations for `streamsDefaultSort`, `streamsDefaultMediaFilter`, `streamsCatalogRefreshPolicy`, and the `clear play statuses` action. Run the settings-doc-sync generator (the same one `scripts/quality/assert-settings-doc-sync.ps1` validates) and add an annotation entry per new setting describing its purpose. The gate runs inside `post-change.ps1`.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` passes (exit 0).
- `Grep` - `streamsDefaultSort` (or its setting id) present in `docs/settings/settings-manifest.json`.

**Status:** `[x]` done

---

### Step 06.2 - Record capability, regenerate catalog, dev logs

**Files:** `docs/ALL_FEATURES.jsonl`, `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 06.1

**Prompt for developer:**

> Record the delivered capability in `docs/ALL_FEATURES.jsonl` via `pwsh -NoProfile -File scripts/all_features/add.ps1` (EN-only, `spec=S0659`): "Streams settings now expose default sort, default media-type filter, a catalog refresh policy, and a clear-play-statuses action; the Streams screen restores the last filter/search on open." (This is normally emitted by `/spec-dev` on `Implemented` via `close-and-log.ps1 -FuncOp ADD` - if that ran, skip this manual add.) Regenerate the class catalog: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Do NOT edit `docs/FEATURES*.md` (release-owned, CLAUDE.md §11 - supersedes strategic §8).

**Verification:**

- `Grep` - `S0659` present in `docs/ALL_FEATURES.jsonl`.
- `Grep` - `ClearStreamPlayOutcomesUseCase` present in `dev/CATALOG/app_v2.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` is `[x] done`.
- [ ] `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` passes.
- [ ] `dev/CHANGELOG.md` has an entry for every modified source file across all phases.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-dev` inserts `Timber.d("S0659: ..")` probes at the changed flow entries (settings rows applied, list seeded from session, catalog suggestion), builds once, and advances to `BlockNeedUserTest` for on-device verification.

---

## Rollback Plan

Revert phase commit(s) - documentation/catalog regeneration only; no runtime impact.
