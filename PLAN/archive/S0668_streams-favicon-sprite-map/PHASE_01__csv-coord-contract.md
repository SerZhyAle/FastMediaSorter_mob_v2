# Phase 01 - CSV coord contract + parser field + fixture

**Strategic spec:** [`../S0668_streams-favicon-sprite-map.md`](../S0668_streams-favicon-sprite-map.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05, Phase 06
**Steps done:** 0 / 3

---

## Objective

Fix the shared favicon-coord contract: add the `favicon_index` column to the offline CSV schema and to the app's CSV parser, and commit a deterministic test fixture (mini atlas PNG + CSV snippet) so the app-side phases can verify the index->rect math and parse tolerance without a real atlas or network.

---

## Prerequisites

- [ ] Strategic §6 #1/#2/#3 reviewed via `research/01__pipeline-and-app-integration.md`.
- [ ] Coord encoding decision (`favicon_index`, fixed 32 px / 16-col grid) read from INDEX "Decisions fixed by this plan".

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/streams/collect-stream-candidates.ps1` | Modified | ≤ 5 (schema array only) |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamCatalogCsvParser.kt` | Modified | ≤ 160 |
| `app_v2/src/test/resources/streams/favicon-fixture.csv` | New | ≤ 10 |
| `app_v2/src/test/resources/streams/favicon-atlas.png` | New (binary) | n/a |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/StreamCatalogCsvParserFaviconTest.kt` | New | ≤ 120 |

---

## Steps

### Step 01.1 - Add `favicon_index` to the offline CSV schema

**Files:** `scripts/streams/collect-stream-candidates.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Append `'favicon_index'` as the last element of the `$Schema` array (currently lines 105-109, 17 columns). It is the zero-based tile ordinal in the fixed grid; empty string = no favicon. Do NOT reorder existing columns (the app parser keys by header name, but consumers diff by position). No packing logic here - that is Phase 02; this step only reserves the column so every CSV write emits the header.

**Verification:**

- `Grep` - `'favicon_index'` matches once in `scripts/streams/collect-stream-candidates.ps1` inside the `$Schema` definition.
- `Grep` - the schema still contains `'homepage'` and `'confidence'` (no existing column dropped).

**Status:** `[ ]`

---

### Step 01.2 - Read `favicon_index` in the CSV parser

**Files:** `app_v2/.../data/repository/StreamCatalogCsvParser.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `val faviconIndex: Int?` to `ParsedCatalogEntry` (place it after `confidence`). In `parse()`, read it via the existing `cell(fields, "favicon_index")` helper and parse to a nullable Int: blank or non-numeric -> `null`, otherwise `toIntOrNull()`. A negative value -> `null` (treat as no favicon). Do NOT add a Room field and do NOT touch `ImportStreamCatalogUseCase` here. Preserve the class KDoc note that unknown/extra columns are tolerated (this column is additive and an older catalog without it decodes to `null`).

**Verification:**

- `Grep` - `faviconIndex` appears in both `data class ParsedCatalogEntry` and the `ParsedCatalogEntry(` constructor call in `parse()`.
- `Grep` - `cell(fields, "favicon_index")` matches once in `StreamCatalogCsvParser.kt`.
- **Compat invariant (must not regress):** `Grep` - cell resolution stays header-name keyed: `columnIndex[name]` (in `cell()`) still present and the new column is read via `cell(fields, "favicon_index")`, NOT by a positional index or a column-count assumption. `Grep` - no `require(`/`check(`/`throw` was added in `parse()`/`cell()` keyed on the field count or on a missing column (an extra/absent column must never throw). Old app safety: an old build's parser ignores the extra column for free; this step must not introduce any positional dependency that would break that.
- `.\a.ps1 fk` - Kotlin compiles (exit 0).

**Status:** `[ ]`

---

### Step 01.3 - Commit a deterministic fixture + parser unit test

**Files:** `app_v2/src/test/resources/streams/favicon-fixture.csv`, `app_v2/src/test/resources/streams/favicon-atlas.png`, `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/StreamCatalogCsvParserFaviconTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Commit a tiny fixture: `favicon-fixture.csv` with a header row containing `favicon_index` plus a few data rows - at least one with a numeric index (e.g. `0`, `17`), one with a blank index, and one row missing the column entirely is covered by a SECOND inline CSV string in the test (older-catalog case). Commit `favicon-atlas.png` as a small grid PNG (e.g. 64x64 px = a 2x2 grid of 32 px tiles, distinct solid colours per tile) for Phase 04 to decode. Write `StreamCatalogCsvParserFaviconTest` asserting: (a) a numeric `favicon_index` parses to that Int; (b) a blank cell -> `null`; (c) a CSV string with NO `favicon_index` column still parses every row with `faviconIndex == null` and no exception; (d) a non-numeric/negative index -> `null`. Pure JVM test, no Android, no Robolectric.

**Verification:**

- `Glob` - `app_v2/src/test/resources/streams/favicon-fixture.csv` and `favicon-atlas.png` both exist.
- File size - `favicon-atlas.png` is non-empty (> 0 bytes) and < 5 KB.
- `.\gradlew.bat :app_v2:testStandardDebugUnitTest --tests "*StreamCatalogCsvParserFaviconTest*"` - the new test class passes (read the per-class XML under `build/test-results`).

**Status:** `[ ]`

---

## Phase Done Criteria

- [ ] Every `Step 01.*` is `[x] done`.
- [ ] `favicon_index` is the last column of the offline `$Schema` and is read by the parser as a nullable Int.
- [ ] An older catalog CSV without the column parses with `faviconIndex == null` and no exception (unit test green).
- [ ] **Compat invariant (strategic §3.2 / §11 #6):** the parser still resolves cells by header-name lookup (`columnIndex[name]`), tolerates the extra `favicon_index` column and any absent column without throwing - test case (c) (CSV with no `favicon_index` column) proves the new-app-reads-old-catalog path; the old-app-reads-new-catalog path is preserved because no positional/column-count assumption was introduced.
- [ ] The committed fixture (CSV + atlas PNG) exists and the new parser test passes.
- [ ] Dev log entry added for the schema + parser + fixture change.

---

## Handoff Notes to Next Phase

The column name `favicon_index`, tile size 32 px and 16-column grid are now the FIXED contract. Phase 02 (packer) must emit indices consistent with a 16-wide grid; Phase 04 (decoder) reconstructs the rect with the same constants. Centralise the two constants (TILE=32, COLS=16) when the app first needs them (Phase 04) so the packer and app cannot drift - reference them in code comments back to this phase.

---

## Rollback Plan

Revert the phase commit: drop `favicon_index` from `$Schema`, drop `faviconIndex` from `ParsedCatalogEntry`, delete the fixture + test. No shipped data or user surface changed (the column is additive and unread downstream until Phase 03).
