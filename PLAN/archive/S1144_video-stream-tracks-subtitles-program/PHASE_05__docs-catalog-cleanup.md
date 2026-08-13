# Phase 05 - Docs, settings-sync, catalog, cleanup

**Strategic spec:** [`../S1144_video-stream-tracks-subtitles-program.md`](../S1144_video-stream-tracks-subtitles-program.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

---

## Objective

Regenerate settings docs (Rule 22, a setting was added in Phase 04), regenerate the class catalog for new classes, and record the delivered capability in the feature inventory. No FEATURES showcase edits (strategic §8).

---

## Prerequisites

- [ ] Phase 01-04 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/settings/settings-manifest.json` + `docs/SETTINGS_REFERENCE*.md` + `docs/settings/settings-annotations.json` | Regenerated | - |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `docs/ALL_FEATURES.jsonl` | Appended (via tool) | - |

---

## Steps

### Step 05.1 - Regenerate settings docs (Rule 22)

**Files:** `docs/settings/*`, `docs/SETTINGS_REFERENCE*.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Phase 04 added a streams track-language default setting - regenerate the settings manifest + reference + annotations per Rule 22 (the `assert-settings-doc-sync` gate in `post-change.ps1` enforces this). Fill the new setting's annotation entry.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` passes.

**Status:** `[x] done`

---

### Step 05.2 - Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` so `StreamTrackPreferenceUseCase` (and any new class) is indexed; set role/status via `set.ps1` if left `unknown`.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "*StreamTrackPreferenceUseCase*"` returns the class.

**Status:** `[x] done`

---

### Step 05.3 - Record capability in feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 05.2

**Prompt for developer:**

> Record via `pwsh -NoProfile -File scripts/all_features/add.ps1` in area **Streams**: video streams remember the chosen audio/subtitle track per channel and show the live program name. Flavors from `SUPPORT_STREAMS`: `standard,legacy,noLegal,vr`. Normally emitted by `/spec-dev` on the `Implemented` flip via `close-and-log.ps1 -FuncOp ADD`; if already recorded, skip.

**Verification:**

- `Grep` - `S1144` present in `docs/ALL_FEATURES.jsonl`.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Settings docs regenerated (Rule 22 gate passes).
- [ ] Catalog regenerated; `StreamTrackPreferenceUseCase` indexed.
- [ ] Feature inventory has an `S1144` record.
- [ ] Dev log entry for the ticket.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Headline (track recall across restart + program overlay) is device-verified via `/spec-test-device`; ticket lands `BlockNeedUserTest` until then.

---

## Rollback Plan

Documentation/catalog only - regenerate or revert the inventory append.
