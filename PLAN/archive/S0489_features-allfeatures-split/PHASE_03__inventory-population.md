# Phase 03 - Inventory Population (parallel scan)

**Strategic spec:** [`../S0489_features-allfeatures-split.md`](../S0489_features-allfeatures-split.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02
**Blocks:** Phase 04, 06
**Steps done:** 4 / 4
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Populate `docs/ALL_FEATURES.jsonl` with every implemented user-visible capability by scanning the whole program with parallel agents partitioned by area, deduped against the migrated baseline.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (migrated baseline present).
- [ ] `scripts/all_features/add.ps1` + `validate.ps1` working.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (data) | n/a |
| `docs/ALL_FEATURES_noLegal.jsonl` | Modified (data, gitignored) | n/a |

> No source code changes - this phase reads code and writes records via `add.ps1` only.

---

## Steps

### Step 03.1 - Partition the scan into areas

**Files:** (planning artifact in chat; no file write)
**Depends on:** - start of phase

**Prompt for developer:**

> Build the area partition list driving the parallel scan. Seed it from the existing `docs/FEATURES.md` section headings (Sources, Media Browsing, File Operations, Video Player, Audio Player, OCR/Translation, Network/Cloud, Settings, Widgets, etc.) plus a module sweep (`app_v2` feature packages). Each area = one parallel scan unit. Record the list as the Step 03.2 work-list; cap is the number of areas, not arbitrary.

**Verification:**

- The partition list is enumerated in the phase Blockers Log or chat with ≥ 14 areas (one per current FEATURES section minus pure-marketing ones).

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 1/1 PASS. 18 areas enumerated: Setup & Onboarding, Usage Statistics, Sources & Storage, Network & Cloud, Media Browsing, File Operations, Screen Capture, Destinations, Image & GIF Viewer, Drawing & Annotations, Video Player, VR/OpenXR, Audio Player, Slideshow, Documents, Text Editor, OCR & Translation, Settings/Widgets/Extensions. Grouped into 12 parallel scan clusters.

---

### Step 03.2 - Parallel area scan → records

**Files:** `docs/ALL_FEATURES.jsonl`, `docs/ALL_FEATURES_noLegal.jsonl`
**Depends on:** Step 03.1

**Prompt for developer:**

> Run one scan agent per area concurrently. Each agent reads the area's source (use the class catalog `query.ps1` first, then source files), enumerates every user-visible capability, and emits records strictly via `scripts/all_features/add.ps1` (upsert against the migrated baseline - never duplicate an existing `id`). Set `flavors` from actual source-set / BuildConfig gating, not guesswork. noLegal-only capabilities go to the gitignored file via `-NoLegal`. Agents must NOT run git/build/catalog and must write only through `add.ps1` (serialized writer) to avoid concurrent-file clobber; collect each agent's records and apply centrally if parallel writes risk contention.

**Verification:**

- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0 after population.
- Record count in `docs/ALL_FEATURES.jsonl` substantially exceeds the Phase 02 migrated baseline (documented delta in Blockers Log).
- Spot-check: every `docs/FEATURES.md` section has ≥ 1 corresponding record (`Grep` by `area`).

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS. 12 parallel read-only scan agents returned ~360 records; applied centrally via add.ps1 (no concurrent-file race). Public 326 + noLegal 16 = 342 (> migrated baseline 172). 20 areas covered, each FEATURES section mapped. validate exit 0 both files.

---

### Step 03.3 - Central consistency sweep

**Files:** `docs/ALL_FEATURES.jsonl`, `docs/ALL_FEATURES_noLegal.jsonl`
**Depends on:** Step 03.2

**Prompt for developer:**

> Centrally normalize records emitted by independent agents: unify `area` naming, dedup near-identical capabilities, fix `flavors` for any record whose gating was misread, ensure `id` kebab convention. Re-apply through `add.ps1` (upsert). This counters the known "parallel agents produce heterogeneous records" risk (strategic §7).

**Verification:**

- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.
- `Grep` - no two records share an `id` (validator's uniqueness check passes; re-run prints zero duplicate errors).

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 2/2 PASS. Central ingest normalized heterogeneous agent output: unified area names, deterministic kebab id (areaSlug.nameSlug with collision suffix), flavor routing (noLegal-only -> gitignored file). validate uniqueness check passed, zero duplicate ids.

---

### Step 03.4 - Coverage confirmation vs flavors

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 03.3

**Prompt for developer:**

> Confirm flavor coverage: each of `standard,lite,photos,legacy` appears as availability on the records that source-sets actually gate, and noLegal additions are in the gitignored file only. Produce a short coverage table (areas × flavors) in the Blockers Log. Any area with zero records is either a real gap (scan it) or marketing-only (note it out-of-scope).

**Verification:**

- Coverage table recorded with no unexplained empty area.
- `Grep` - at least one record carries each flavor token (`standard`, `lite`, `photos`, `legacy`).

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 2/2 PASS. Coverage table: standard 299, legacy 286, vr 207, photos 165, lite 147; noLegal-only 16 in gitignored file. Each flavor token present. spec provenance carried on 118 public records. No unexplained empty area.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for the data files via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

ALL_FEATURES is now the full inventory and the source of truth. Phase 04 prunes FEATURES against it; Phase 06 baselines the drift gate against this populated state.

---

## Rollback Plan

Revert the population commit. Schema/tooling (Phase 01) and migrated baseline (Phase 02) remain intact.
