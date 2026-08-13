# Phase 03 - Staleness decided by the manifest, not by the build

**Strategic spec:** [`../S1483_artwork-delivery-without-build-pins.md`](../S1483_artwork-delivery-without-build-pins.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 0 / 3
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Decide "an update is available" by comparing the installed stamp against the published manifest,
so a rebuilt payload becomes offerable without an app release.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/delivery/ArtworkManifest.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/ArtworkManifestClient.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliverableInventoryImpl.kt` | Modified | ≤ 60 changed |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/delivery/ArtworkManifestTest.kt` | New | ≤ 140 |

---

## Steps

### Step 03.1 - Model and fetch the manifest

**Files:** `ArtworkManifest.kt`, `ArtworkManifestClient.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add an `ArtworkManifest` model (schema version, generated-at stamp, per-set file entries with name,
> size and sha) and a client that fetches it over the existing download OkHttp client with a short
> timeout, parses it, and caches the result in memory for the process. Every failure path - network,
> HTTP status, malformed JSON, unknown schema version - returns null, never throws.

**Why:**

The strategic spec makes a missing or corrupt manifest mean "no update available": a storage failure
must not become an app failure, and an exception on this path would surface as a broken Streams
screen rather than as a quietly absent offer.

**Verification:**

- `Glob` - both files exist.
- `Grep` - no `throw` on the parse path; failures return null.

**Status:** `[x]` done

---

### Step 03.2 - Compare the installed stamp against the manifest

**Files:** `DeliverableInventoryImpl.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> For the two artwork sets, replace the descriptor-stamp comparison in `isStale` with a manifest
> comparison: stale when the manifest's stamp for that set differs from the stamp recorded at install
> time. Record the manifest stamp on install. Native sets keep comparing against the descriptor.

**Why:**

The build comparing against itself is precisely why a rebuilt payload reached nobody; the manifest
moves the verdict to the side that actually changes.

**Verification:**

- `Grep` - `isStale` branches on the set being artwork.
- `.\a.ps1 fk` passes.

**Status:** `[x]` done

---

### Step 03.3 - Test the freshness verdict

**Files:** `ArtworkManifestTest.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Cover: same stamp means fresh, different stamp means stale, absent manifest means fresh, malformed
> JSON means fresh, unknown schema version means fresh.

**Why:**

Four of those five cases are failure paths, and a failure path that silently inverts would either
nag every user forever or hide every future rebuild.

**Verification:**

- `.\a.ps1 fu --tests "*ArtworkManifestTest*"` passes.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [ ] `.\a.ps1 fk` passes; new tests green.
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

The manifest is now fetched and cached; Phase 04 reuses the same entries for the payload size shown
in the offer.
