# Phase 03 - Import: extract atlas PNG + persist coords sidecar

**Strategic spec:** [`../S0668_streams-favicon-sprite-map.md`](../S0668_streams-favicon-sprite-map.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01 (parser exposes `faviconIndex`)
**Blocks:** Phase 04, Phase 05
**Steps done:** 0 / 4

---

## Objective

Extend the catalog import to also extract the atlas PNG entry from the downloaded zip, persist it to app-private files storage, and persist a `url -> favicon_index` coords sidecar - WITHOUT any Room schema change. Both sidecar files are rewritten wholesale on every import (same fresh-fetch lifecycle as the CSV).

---

## Prerequisites

- [ ] Phase 01 merged: `ParsedCatalogEntry.faviconIndex` available.
- [ ] Decision read: sidecar (no migration), keyed by `url` because the entity `id` is randomised per import.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/.../domain/usecase/streams/ImportStreamCatalogUseCase.kt` | Modified | ≤ 220 |
| `app_v2/.../data/repository/streams/FaviconAtlasStore.kt` (new) | New | ≤ 160 |
| `app_v2/.../di/...Module.kt` (existing streams DI module) | Modified | ≤ 30 |
| `app_v2/src/test/java/.../FaviconAtlasStoreTest.kt` | New | ≤ 120 |

> Confirm the exact DI module that provides streams use cases via `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Injected "ImportStreamCatalogUseCase"` before editing; bind the new store there.

---

## Steps

### Step 03.1 - Sidecar store for atlas bytes + coords map

**Files:** `app_v2/.../data/repository/streams/FaviconAtlasStore.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `FaviconAtlasStore` (`@Inject constructor(@ApplicationContext context)`). It owns two app-private files under `context.filesDir` (e.g. `streams/favicon-atlas.png` and `streams/favicon-coords.json`). API:
> - `suspend fun write(atlasBytes: ByteArray?, coords: Map<String, Int>)` - writes both files atomically (write to a temp file then rename); a null/empty `atlasBytes` clears both (deletes atlas, writes empty coords) so a catalog with no atlas leaves a clean empty state.
> - `fun atlasFile(): File?` - the atlas file or null if absent.
> - `suspend fun coords(): Map<String, Int>` - parse the JSON map (empty map if absent/corrupt - swallow with a single `Timber.i`).
> Coords JSON is a flat object `{ "<url>": <index> }`. No Room, no DataStore. Do log levels per CLAUDE.md: a missing/empty sidecar is `Timber.i`, not `Timber.e`.

**Verification:**

- `Grep` - `class FaviconAtlasStore` exists with `write(`, `atlasFile(`, `coords(` members.
- `Grep` - the store uses `context.filesDir` and writes under a `streams/` subdir.
- `.\a.ps1 fk` - Kotlin compiles (exit 0).

**Status:** `[ ]`

---

### Step 03.2 - Extract the atlas PNG entry during the zip walk

**Files:** `app_v2/.../domain/usecase/streams/ImportStreamCatalogUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Generalise `downloadCsv()` so the single `ZipInputStream` pass captures BOTH the CSV text AND the atlas PNG bytes in one walk (the zip is consumed once - do not re-download). Rename to a private `downloadCatalog(): CatalogPayload?` returning `data class CatalogPayload(val csv: String, val atlasPng: ByteArray?)`.
>
> **This change is ADDITIVE - it must NOT alter the existing CSV-return behaviour (compat invariant):**
> - The atlas PNG is handled by a NEW branch alongside the existing entry handling; the skip-unknown-entry behaviour (`closeEntry()` on every non-matched entry) stays. An entry that is neither CSV nor the atlas is still skipped exactly as today.
> - Capture into locals while walking: a name ending `streams.csv` -> CSV text (capped via `readCappedUtf8`, as today); a name ending `favicon-atlas.png` -> bytes (cap with a separate `MAX_ATLAS_BYTES`, e.g. 4 MB; over-cap -> drop the atlas, KEEP the CSV). Because Phase 02 packs the CSV FIRST, do NOT early-`return` on the CSV match (an early return would skip the later PNG entry); instead keep both captured locals and finish the walk, then build the payload. Returning the CSV the moment it is found is acceptable ONLY if the atlas is never needed - but here it is, so finish the walk.
> - If NO atlas entry is present (an OLD catalog), `atlasPng` is null and the result carries the CSV exactly as today -> the rest of the import behaves identically (CSV only, no atlas, empty slots).
> - Preserve the existing CSV-only fallback (first `*.csv` when no `streams.csv`), the `callTimeout` client derivation, and the error handling.

**Verification:**

- `Grep` - `favicon-atlas.png` is matched in the zip walk inside `ImportStreamCatalogUseCase.kt`.
- `Grep` - a `MAX_ATLAS_BYTES` constant exists in the companion.
- `Grep` - the zip body is read in a SINGLE `ZipInputStream(stream).use { .. }` block (no second `execute()` for the atlas).
- **Compat invariant (additive, CSV path unchanged):** `Grep` - the entry walk still calls `closeEntry()` on non-matched entries (skip-unknown preserved) and the CSV is still read via `readCappedUtf8`; the atlas is a separate branch, not a replacement of the CSV-return path. The `data class CatalogPayload` carries `atlasPng: ByteArray?` so an atlas-absent zip yields a null atlas with the CSV intact.
- `.\a.ps1 fk` - compiles (exit 0).

**Status:** `[ ]`

---

### Step 03.3 - Persist atlas + coords after a successful parse

**Files:** `app_v2/.../domain/usecase/streams/ImportStreamCatalogUseCase.kt`
**Depends on:** Step 03.1, Step 03.2

**Prompt for developer:**

> Inject `FaviconAtlasStore`. After the parse succeeds and BEFORE/AROUND the `repository.mergeCatalog(..)` call, build `coords = entries.filter { it.faviconIndex != null }.associate { it.url to it.faviconIndex!! }` and call `faviconAtlasStore.write(payload.atlasPng, coords)`. The store keys by `url` (stable, the entity's unique index) NOT by the per-import random `id`. A failed sidecar write must NOT fail the whole import - wrap it and log `Timber.w` (the catalog rows still merge; favicons just stay absent until the next import). Do not add a favicon field to `StreamSourceEntity`.

**Verification:**

- `Grep` - `faviconAtlasStore.write(` is called in `ImportStreamCatalogUseCase.kt` with a `url ->` keyed coords map.
- `Grep` - NO new favicon/coord property was added to `StreamSourceEntity` (the entity file is unchanged by this phase).
- `.\a.ps1 fc` - code + resources compile (exit 0).

**Status:** `[ ]`

---

### Step 03.4 - Sidecar round-trip unit test

**Files:** `app_v2/src/test/java/.../FaviconAtlasStoreTest.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Robolectric (or a temp-dir-backed context) test for `FaviconAtlasStore`: (a) `write(bytes, mapOf("u1" to 0, "u2" to 17))` then `coords()` returns the same map and `atlasFile()` is non-null with the same bytes; (b) `write(null, emptyMap())` clears - `atlasFile()` null/absent and `coords()` empty; (c) a corrupt coords file -> `coords()` returns empty map without throwing. Assert the url is the key (not an id).
>
> **Compat coverage (atlas-absent path):** also add a test over the zip-walk extraction (`downloadCatalog`-level, or a small extracted helper that takes a `ByteArray` zip so no network is needed): (d) a zip containing ONLY `streams.csv` (an OLD catalog) yields a payload with the CSV text and `atlasPng == null`, and the downstream `write(null, ..)` leaves no atlas (empty slots) - proving the new branch did not change the CSV-only behaviour; (e) a zip with `streams.csv` first THEN `favicon-atlas.png` yields both the CSV and non-null atlas bytes (CSV not truncated by the later entry). Build the in-memory zips with `ZipOutputStream` in the test.

**Verification:**

- `Glob` - `FaviconAtlasStoreTest.kt` exists under `app_v2/src/test`.
- **Compat invariant:** a test asserts the atlas-absent zip (CSV only) yields `atlasPng == null` with the CSV intact (old-catalog path == today), and the CSV-first-then-PNG zip yields both - read the per-class XML to confirm these cases pass.
- `.\gradlew.bat :app_v2:testStandardDebugUnitTest --tests "*FaviconAtlasStoreTest*"` - the class passes (read per-class XML).

**Status:** `[ ]`

---

## Phase Done Criteria

- [ ] Every `Step 03.*` is `[x] done`.
- [ ] The import extracts the atlas PNG in the SAME zip pass as the CSV and persists atlas + `url->index` sidecar via `FaviconAtlasStore`.
- [ ] `StreamSourceEntity` and the Room schema are UNCHANGED (no migration).
- [ ] A failed sidecar write does not fail the catalog import.
- [ ] **Compat invariant (strategic §3.2 / §11 #6):** the PNG-extraction branch is ADDITIVE - the skip-unknown-entry (`closeEntry()`) behaviour and the CSV-return path are unchanged; an OLD catalog zip (no atlas entry) yields `atlasPng == null` and the import behaves exactly as today (CSV only, empty slots) - covered by the atlas-absent unit test.
- [ ] `.\a.ps1 fc` passes; the sidecar round-trip test passes.
- [ ] Dev log entry added; `catalog_sync.ps1 -Module app_v2` run once for the new class.

---

## Handoff Notes to Next Phase

`FaviconAtlasStore.atlasFile()` + `coords()[url]` are the inputs Phase 04 decodes and Phase 05 binds. The atlas is one local PNG file; the coords map is `url -> favicon_index`. Phase 04 turns `(atlasFile, index)` into a 32 px tile bitmap.

---

## Rollback Plan

Revert the phase commit: drop `FaviconAtlasStore` + its DI binding, restore `downloadCsv()` to CSV-only, delete the test. Phase 01 column/parser stay (unread again). No Room change to roll back.
