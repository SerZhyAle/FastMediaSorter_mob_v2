# Tactical Plan: S0668 - streams-favicon-sprite-map

**Strategic spec:** [`../S0668_streams-favicon-sprite-map.md`](../S0668_streams-favicon-sprite-map.md)
**Research inputs:** [`research/01__pipeline-and-app-integration.md`](research/01__pipeline-and-app-integration.md)
**Feature:** Stream-catalog rows show a per-channel favicon thumbnail sliced from one sprite-atlas PNG shipped inside the existing catalog zip; favicon-less rows show an empty slot.
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Tactical
**Phases:** 6 / 6 done (code); EXTERNAL closure: atlas publish + on-device verification
**Last updated:** 2026-06-25

> **Scope:** tactical, English, developer handoff. Every step has a static verification predicate. Rationale lives in the strategic spec and `research/`.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | csv-coord-contract | - | ✅ Done | 3 | [PHASE_01__csv-coord-contract.md](PHASE_01__csv-coord-contract.md) |
| 02 | offline-favicon-atlas-packer | 01 | ✅ Done | 4 | [PHASE_02__offline-favicon-atlas-packer.md](PHASE_02__offline-favicon-atlas-packer.md) |
| 03 | import-extract-atlas-sidecar | 01 | ✅ Done | 4 | [PHASE_03__import-extract-atlas-sidecar.md](PHASE_03__import-extract-atlas-sidecar.md) |
| 04 | atlas-region-decoder | 01, 03 | ✅ Done | 3 | [PHASE_04__atlas-region-decoder.md](PHASE_04__atlas-region-decoder.md) |
| 05 | adapter-thumbnail-render | 03, 04 | ✅ Done | 4 | [PHASE_05__adapter-thumbnail-render.md](PHASE_05__adapter-thumbnail-render.md) |
| 06 | docs-strings-catalog | 01-05 | ✅ Done | 4 | [PHASE_06__docs-strings-catalog.md](PHASE_06__docs-strings-catalog.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

> **Phase ordering rationale (allowed by /spec-tech):** Phase 01 fixes the shared `favicon_index` column contract AND commits a small deterministic test fixture (mini atlas PNG + CSV snippet). Phases 03-05 (the app side) verify against that fixture, so they do NOT depend on Phase 02 producing a real atlas. Phase 02 (offline PowerShell packer) builds the REAL atlas and can run in parallel with the app phases once the contract from Phase 01 is fixed. This is why Phase 02 and Phase 03 both depend only on Phase 01, not on each other.

---

## Pre-Implementation Blockers

All strategic §6 items needed to START are resolved (see `research/`): source = `homepage` column (#1), build location offline (#2), delivery via existing zip (#4), fallback empty slot (#6), licensing not a blocker (#7), flavor reach = catalog's existing reach (#8). Remaining `-> /spec-tech` items (#2 format, #3 encoding, #5 render) are DECIDED in this plan. Implementation may start at Phase 01.

### Decisions fixed by this plan
- **Coord encoding:** one new CSV column `favicon_index` = zero-based tile ordinal in a fixed grid (empty = no favicon). NOT `x,y,w,h`.
- **Atlas geometry:** PNG grid, tile size 32x32 px, 16 columns. App reconstructs the rect: `col = index % 16`, `row = index / 16`, `rect = (col*32, row*32, 32, 32)`.
- **Persistence:** sidecar files (atlas PNG + `url -> favicon_index` JSON) written at import; NO Room schema change / migration. Keyed by `url` because the entity `id` is randomised per import.
- **Decode:** decode-once + cached crop (escalate to `BitmapRegionDecoder` / Glide loader only on measured jank).

### Backward / forward-compatibility invariant (CRITICAL - must not regress)

Traces to strategic §3.2 "Совместимость данных" and §11 #6. An already-installed OLD app fetching the NEW enriched catalog (a `streams.csv` carrying the extra `favicon_index` column AND an atlas PNG entry inside `stream-catalog.zip`) must NOT crash - it silently ignores the atlas and the extra column. A NEW app fetching an OLD catalog (no column, no atlas) must also work (empty slots). Three properties guarantee this; every phase that touches the parser, importer, or packer MUST preserve them:

1. **Parser ignores unknown columns** - `StreamCatalogCsvParser` resolves cells by header-name lookup (`columnIndex[name]`), never by positional index or column count, and an absent column yields `""` (not an exception). An old app skips `favicon_index`; a new app reading an old catalog gets `faviconIndex == null`.
2. **Importer consumes only `*.csv`, skips the rest** - `ImportStreamCatalogUseCase` iterates every zip entry, returns the `streams.csv` text, and calls `closeEntry()` on every other entry. An old app never reads the atlas PNG. The new PNG-extraction branch (Phase 03) is ADDITIVE - a new branch alongside the CSV path, never replacing the skip-unknown-entry behaviour - and the CSV-return path is unchanged.
3. **Zip always contains a `streams.csv` entry, packed first** - the publish step (Phase 02) keeps an entry whose name ends `streams.csv` and writes it BEFORE the atlas PNG, so both old and new apps reach the CSV without first streaming the whole atlas (`ZipInputStream.closeEntry()` skips by reading bytes). The atlas size stays within the import `callTimeout` budget (30 s, S0583) so the larger zip still downloads on already-shipped apps.

Enforced as a Phase Done Criterion + an explicit `Verification:` Grep in Phase 01 (parser), Phase 02 (packer/publish), Phase 03 (importer).

---

## External / Manual Closure (cannot be auto-completed)

These gate final verification but are owner-run, not code, and MUST NOT block marking the code phases Done:
- [ ] **EXTERNAL:** run `scripts/streams/collect-stream-candidates.ps1` with the new favicon-fetch + atlas-pack path to build a REAL atlas over the live catalog (network fetch of many favicons; partial coverage expected).
- [ ] **EXTERNAL:** publish the new bundle - `Invoke-PublishCatalog` -> `gh release upload delivery-so-v1 stream-catalog.zip --clobber` (owner-run; requires `gh` auth).
- [ ] **EXTERNAL:** on-device verification that real rows render recognisable favicon thumbnails and favicon-less rows show an empty slot.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] **Backward/forward-compat invariant holds** (strategic §3.2 / §11 #6): old app + new catalog ignores the atlas + extra column without crashing; new app + old catalog yields empty slots. Parser stays header-name keyed; importer's PNG extraction is additive (CSV-return path unchanged); zip keeps a `streams.csv` entry packed first.
- [ ] Parser reads `favicon_index`; an older catalog without the column still imports (unit test green).
- [ ] App extracts the atlas PNG from the catalog zip and persists atlas + coords sidecar; favicon-less rows leave an empty slot.
- [ ] Scrolling the stream list does not jank with the favicon thumbnails bound (decode-once verified on device or via profiling).
- [ ] `docs/ALL_FEATURES.jsonl` records the capability; `docs/FEATURES*.md` NOT edited here (showcase routed through `/skill-release`).
- [ ] `dev/CHANGELOG.md` has an entry per logical change.
- [ ] `dev/CATALOG/app_v2.*` regenerated if public API changed.
- [ ] `/spec-check S0668` returns `Verified`; strategic `Status:` advanced by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/6 done`.
2. During a phase: flip a step to `[~]` when started, `[x]` when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a Blockers Log bullet; set the journal status to the matching `Block*` with a `-StatusNote`.
5. All code phases done: leave the EXTERNAL items for the owner, set status to `BlockNeedUserTest` (atlas build + publish + on-device render), then `/spec-check S0668` after device verification.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-25 - Initial tactical plan authored by `/spec-tech`; status Approved -> Tactical.
- 2026-06-25 - Folded the owner's CRITICAL backward/forward-compatibility requirement (old app + new enriched catalog must not crash; new app + old catalog -> empty slots). Added the "Backward / forward-compatibility invariant" subsection + a Completion Gate item (INDEX), and per-phase Phase Done Criteria + explicit `Verification:` Greps: Phase 01 (parser stays header-name keyed, no positional/count assumption, no throw on extra/absent column), Phase 02 (zip always carries `streams.csv` packed FIRST; atlas size capped against the 30 s import `callTimeout` budget, S0583), Phase 03 (PNG extraction is ADDITIVE - CSV-return path + skip-unknown unchanged; atlas-absent path == today, covered by a unit test). No status change (stays Tactical).
