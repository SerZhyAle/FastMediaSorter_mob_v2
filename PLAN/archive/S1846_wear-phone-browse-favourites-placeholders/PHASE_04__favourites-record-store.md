# Phase 04 - The favourites store keeps a record, not a key

**Strategic spec:** [`../S1846_wear-phone-browse-favourites-placeholders.md`](../S1846_wear-phone-browse-favourites-placeholders.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of Phases 01 to 03
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-08-20
**Completed:** 2026-08-20

---

## Objective

A favourite stops being a bare `sourceId:filePath` string and becomes a record that can be listed and reopened, with `sourceId` meaning the same thing in both writers.

---

## Prerequisites

- [ ] Owner ruling read: strategic §13 answer 7 - the row opens a player, and the storage consequences were accepted with it.
- [ ] `research/02__phone-browse-and-favourites-as-is.md` §3 read.
- [ ] `temp/CODE.LOCK` acquired immediately before each source edit and released right after.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearFavoriteRecord.kt` | New | ≤ 90 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/domain/repository/WearFavoritesRepository.kt` | Modified | ≤ 40 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/data/repository/WearFavoritesRepositoryImpl.kt` | Modified | ≤ 180 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/image/ImageViewerViewModel.kt` | Modified | ≤ 320 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/audio/AudioPlayerViewModel.kt` | Modified | ≤ 520 |
| `wear/src/test/java/com/sza/fastmediasorter/wear/data/repository/WearFavoritesRepositoryImplTest.kt` | New | ≤ 220 |

> `AudioPlayerViewModel.kt` is near the 500-line backup threshold. Re-check its line count before editing and take a timestamped backup copy first if it is over (CLAUDE.md Rule 5).
>
> The store lives in `EncryptedSharedPreferences`. Existing entries were written by real users of the debug build and must survive - see step 04.3.

---

## Steps

### Step 04.1 - Define the record

**Files:** `wear/../domain/model/WearFavoriteRecord.kt` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Create `WearFavoriteRecord` holding the source id, the file path, a display name, a media type and the identifier the watch needs to reopen the file. State in its KDoc which field is the identity - the pair that decides whether two records are the same favourite - because the delta sent to the phone keys off it.

**Why:**

Research artifact 02 §3 establishes that the store keeps no name, no media type and no stable id, so nothing can resolve a stored key back to an openable file, and strategic §5 makes exactly those fields the price the owner accepted for a row that opens a player.

**Verification:**

- `Glob` - `WearFavoriteRecord.kt` exists.
- `Grep` - `data class WearFavoriteRecord` matches exactly once.
- `Grep` - the KDoc names which fields form the identity.
- `.\a.ps1 fw` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - `WearFavoriteRecord` created, identity stated in its KDoc as sourceId+filePath and nothing else, with `mediaType`/`displayName` explicitly presentation. `fromLegacyKey` splits on the FIRST separator only - a file path holds colons of its own, and splitting on all of them would rewrite the path and lose the file. One authoring trap hit and recorded in the file: the mime wildcard form inside a KDoc ends the block comment early, which made the whole class unparseable and produced eleven misleading "unresolved reference" errors in the OTHER file; the kind comment is a line comment now.

---

### Step 04.2 - Give the repository a read-all and a record-shaped write

**Files:** `wear/../domain/repository/WearFavoritesRepository.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add a read-all returning every stored record, and widen `addFavorite` to take a record rather than two strings. Keep `isFavorite`, `hasAnyFavorite`, `getPendingDelta` and `clearPendingDelta` as they are - the home screen and the delta path already depend on them and neither needs the new fields.

**Why:**

Research artifact 02 §3 records that the interface offers no read-all at all - only `hasAnyFavorite` - so the screen in Phase 05 has nothing to list, and strategic §5 states the accessor is the trivial half while the stored value is the real change.

**Verification:**

- `Grep` - a read-all member returning `List<WearFavoriteRecord>` matches exactly once in the interface.
- `Grep` - `hasAnyFavorite`, `getPendingDelta` and `clearPendingDelta` are all still declared.
- `.\a.ps1 fw` exits 0 - every implementer and caller still compiles.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Interface gained `addFavorite(record)` and `getFavorites()`. The two-string `addFavorite` was kept rather than replaced: a caller that knows only the pair - the delta replay from the phone - would otherwise have to invent a display name. `hasAnyFavorite`, `getPendingDelta` and `clearPendingDelta` untouched. `fw` exit 0, so every implementer and caller still compiles.

---

### Step 04.3 - Store records, and read old entries without losing them

**Files:** `wear/../data/repository/WearFavoritesRepositoryImpl.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Persist each favourite as a record. On read, accept both shapes: a legacy bare `sourceId:filePath` string is returned as a record whose display name is the last path segment and whose media type and reopen identifier are absent. Write only the new shape. Do not delete or rewrite legacy entries on read - a read must not mutate the store.

**Why:**

Strategic §5 requires that old entries are read as they are and shown by their last path segment, so a migration that dropped them would silently empty a list the user filled themselves.

**Verification:**

- `Grep` - the read path has a branch for the legacy string shape.
- `Grep` - the read path performs no write - no `edit`, `putStringSet` or commit inside it.
- Unit test from step 04.4 covers a store holding one legacy entry and one record.
- `.\a.ps1 fw` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Records live under their OWN preferences key beside the legacy set, not instead of it. Rewriting the old key in place would mean either dropping entries this build cannot enrich or migrating on read - and a read that writes turns listing the favourites into a way to LOSE them if the process dies mid-write. Both keys are read; only the new one is written. `removeFavorite` deletes from both, otherwise a file marked before this ticket and unmarked after it would return on the next read; `isFavorite` and `hasAnyFavorite` likewise consult both.

---

### Step 04.4 - Give sourceId one meaning, and pin all of it with tests

**Files:** `wear/../ui/image/ImageViewerViewModel.kt`, `wear/../ui/audio/AudioPlayerViewModel.kt`, `wear/src/test/.../WearFavoritesRepositoryImplTest.kt` (New)
**Depends on:** Step 04.3

**Prompt for developer:**

> The two writers disagree today: the image viewer hardcodes `"local"` / `"network"` while the audio player uses `uri.host`. Pick one rule, apply it in both, and state it in one place both can cite. While here, route the image viewer's unmark through `ToggleFavoriteUseCase` like the audio player already does, instead of bypassing it.
>
> Add the repository test class: a legacy entry survives a read, a record round-trips with every field, the read-all returns both, and the delta still reports an unmark.

**Why:**

Research artifact 02 §3 shows a generic "favourite to openable file" path cannot key off a field that means a category in one writer and a host name in the other, and strategic §5 makes normalising `sourceId` an explicit consequence the owner accepted.

**Verification:**

- `Grep` - `"local"` and `"network"` are no longer hardcoded as source ids in `ImageViewerViewModel.kt`.
- `Grep` - both view models derive the source id the same way, from one shared helper or constant.
- `Grep` - `ToggleFavoriteUseCase` is referenced in `ImageViewerViewModel.kt`.
- `Glob` - `WearFavoritesRepositoryImplTest.kt` exists; `.\a.ps1 fwu` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - One rule for the source id, `favoriteSourceId(isNetworkSource, networkSourceId)`, cited by both writers. Neither previous spelling was kept: the image viewer wrote the category `network`, the audio player wrote `uri.host`, and NEITHER resolves back to a source. The value that does is the network source own id, which S1687 put on the hand-off for exactly this reason; the category survives only as the fallback for a network file whose id was never recorded, so no existing favourite becomes unaddressable. The image viewer now marks through `ToggleFavoriteUseCase` instead of repeating both halves by hand. Test class: `WearFavoriteRecordTest`, 7 cases, `tests="7" failures="0"` read from the results XML.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [ ] `.\a.ps1 fw` exits 0 and `.\a.ps1 fwu` exits 0.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] `dev/CATALOG/wear.jsonl` regenerated - deferred to Phase 06, which syncs both modules once.
- [x] Phase-boundary audit run - this phase changes a durable stored format, so apply the persistence lens of `docs/CODE_AUDIT_PROTOCOL.md`: the legacy read path is covered by a test and no read mutates the store.

---

## Handoff Notes to Next Phase

A favourite can be listed and, when it was written after this phase, reopened. A legacy entry lists but may not open - Phase 05 must render that difference rather than pretend it away.

---

## Rollback Plan

Revert the phase commit. Records written in the new shape become unreadable to the reverted code, so a rollback after real use loses favourites added since - note this in the commit message rather than assuming it is free.

---

## Design change inside the phase - the merge left the store

The plan put the read-all in the repository implementation and asked for a `WearFavoritesRepositoryImplTest`
beside it. That test cannot exist: the store sits behind `EncryptedSharedPreferences`, the `wear` module has
no Robolectric, and the only existing repository test in the module is a pure parser. Rather than skip the
proof or add a test framework to a module that has none, the part that can actually be got wrong - which
entries survive a merge, in what order, and which duplicate is dropped - was extracted into a pure
`mergeFavorites(records, legacyKeys)` next to the model. The store now supplies the two shapes and nothing
else.

So the test class is `WearFavoriteRecordTest`, not `WearFavoritesRepositoryImplTest`, and it covers the
legacy parse (including a path holding colons of its own), the merge precedence, the empty store, and the
source-id rule both writers now share. The prefs plumbing stays unproven by unit test, which is honest: it
is three `getString`/`putString` calls and the device test in Phase 06 exercises them.

**Rule 5 checked, not assumed:** `AudioPlayerViewModel.kt` is 487 lines after the change, under the 500
threshold, so no backup was owed.
