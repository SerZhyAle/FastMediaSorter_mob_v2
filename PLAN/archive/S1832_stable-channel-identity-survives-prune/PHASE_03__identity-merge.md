# Phase 03 - User state follows the identity through a merge

**Strategic spec:** [`../S1832_stable-channel-identity-survives-prune.md`](../S1832_stable-channel-identity-survives-prune.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 7 / 7
**Started:** 2026-08-20
**Completed:** 2026-08-20

---

## Objective

Make `stream_user_state` the durable home of the pin, its position and the play outcome, and have the
catalog merge re-attach it by identity, so a channel that leaves the bank and returns comes back with
everything the user gave it.

---

## Design note the steps assume

The merge keeps keying insert, update and prune by `url`, exactly as it does today. That path is proven,
chunked against the SQLite bind limit (S0821) and transactional (S0732), and this ticket has no reason to
disturb it: once no user data hangs off the row, a row being deleted and re-inserted under a new id costs
nothing. Identity is used for one thing only - re-attaching user state to whatever row now carries that
address. `stream_sources.pinned` and `.sortIndex` stay on the row as a read projection so no list query
or UI binding changes; `stream_user_state` is the copy that survives.

---

## Prerequisites

- [x] Phase 02 is ✅ Done - `identityKey` is populated for every existing row and `stream_user_state` is seeded.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/ImportStreamCatalogUseCase.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/AddStreamSourceUseCase.kt` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/ImportStreamPlaylistUseCase.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/UpdateStreamSourceUseCase.kt` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamSourceDao.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamSourceRepository.kt` | Modified | ≤ 300 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/StreamSourceCatalogMergeTest.kt` | Modified | ≤ 600 |

---

## Steps

### Step 03.1 - Stamp the identity at every row-creation site

**Files:** `ImportStreamCatalogUseCase.kt`, `AddStreamSourceUseCase.kt`, `ImportStreamPlaylistUseCase.kt`, `UpdateStreamSourceUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Every place that constructs a `StreamSourceEntity` must set `identityKey = StreamChannelIdentity.of(url)`
> - the catalog import, the hand-added channel and the playlist import alike. `UpdateStreamSourceUseCase`
> edits a hand-added channel's address in place, so it must recompute the identity in the same write;
> leaving the old one there would file the channel's own future state under an address it no longer has.
> Keep `id` as the random UUID it is today at all three sites.

**Why:**

Strategic ADR-3 keeps `id` an opaque row handle and makes the identity the column user data is filed
under, so a row created without it would carry no identity and silently opt out of everything this
ticket adds.

**Verification:**

- `Grep` - `StreamChannelIdentity.of` present in all four files.
- `Grep` - `UUID.randomUUID` still present in `ImportStreamCatalogUseCase.kt`, `AddStreamSourceUseCase.kt` and `ImportStreamPlaylistUseCase.kt`.
- `Grep` - `identityKey` present in `UpdateStreamSourceUseCase.kt`.

**Status:** `[x]` done

---

### Step 03.2 - Add the identity-aware queries to `StreamSourceDao`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamSourceDao.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add four queries. First, a single statement that restores the pin projection for every row at once by
> joining `stream_sources` to `stream_user_state` on `identityKey` and writing `pinned` and `sortIndex`
> back onto the row - one UPDATE over the whole table, never a loop. Second, a Flow that returns the play
> outcome per row id by joining the same two tables, so the existing map-keyed-by-row-id contract is
> preserved and no caller or UI binding changes. Third, a read of one row's `identityKey` by row id, for
> the single-channel writers.
> Do not add a query that rewrites a row's `url` in place. When the bank republishes a channel under a
> cosmetically different address the url-keyed merge already handles it correctly - the old row is pruned,
> the new one inserted, and the user state re-attaches by identity - whereas rewriting the url would
> silently unmatch the favorite stored against the old address.

**Why:**

Strategic §3.2 caps the merge at whole-bank statements rather than per-row queries because the bank is
17 628 rows, and §11 criterion 4 requires the merge not to become noticeably slower.

**Verification:**

- `Grep` - `stream_user_state` present in `StreamSourceDao.kt`.
- `Grep` - `identityKey` present in at least three distinct `@Query` bodies in that file.
- `Grep` - `updateCatalogUrlByIdentity` returns zero hits in the repository and the DAO.
- `Grep` - the pin-projection query contains `UPDATE` and does not contain `LIMIT`.

**Status:** `[x]` done

---

### Step 03.3 - Re-attach user state at the end of the merge

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamSourceRepository.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> In `mergeCatalog`, keep the existing url-keyed insert, update and chunked prune exactly as they are.
> Add one thing inside the same transaction: after the prune, run the whole-table pin-projection restore
> from step 03.2, so every row that just arrived or just came back carries the pin and position the user
> gave that channel. A channel republished under a different scheme arrives as a fresh row through the
> ordinary insert path and picks its state up from the same restore, which is why no url rewrite is
> needed anywhere.
> Replace the `purgeOrphanedPlayOutcomes()` call with a bounded prune of `stream_user_state`: delete
> unpinned rows whose `updatedAt` is older than the retention cutoff, and leave pinned rows alone
> regardless of age. Log the number pruned at info level, mirroring what the purge logged.

**Why:**

Strategic §11 criterion 1 requires a channel that left the bank and returned to keep its pin and its
position, and research artifact 01 established that S1826's unconditional purge is the mechanism
actively deleting the history this ticket must preserve, so it has to be replaced rather than kept.

**Verification:**

- `Grep` - `purgeOrphanedPlayOutcomes` returns zero hits in `StreamSourceRepository.kt`.
- `Grep` - `pruneUnpinnedOlderThan` present in that file.
- `Grep` - `SQLITE_IN_CLAUSE_LIMIT` still present, proving the chunked prune survived.
- `Grep` - `db.withTransaction` still wraps `mergeCatalog`.

**Status:** `[x]` done

---

### Step 03.4 - Pin, unpin and reorder write through the user state

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamSourceRepository.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> `pinToTop`, `unpin` and `reorderPinned` currently write only the catalog row. Make each of them write
> the durable copy first - `stream_user_state` keyed by the row's `identityKey` - and then the row
> projection, both inside one transaction so a kill between the two cannot leave the two disagreeing.
> `pinToTop` takes its new lowest position from `stream_user_state`'s minimum rather than the catalog's,
> because a channel currently absent from the bank still holds a position and must not be overwritten.

**Why:**

Strategic §11 criterion 1 names the position inside the pinned list, not just the fact of being pinned,
as something the user must get back, and a position derived only from rows present in the latest bank
would renumber around any channel that was temporarily missing.

**Verification:**

- `Grep` - `streamUserStateDao` present in all three of `pinToTop`, `unpin` and `reorderPinned`.
- `Grep` - `db.withTransaction` present in `pinToTop` and `unpin` as well as the existing `reorderPinned`.
- `Grep` - `dao.minSortIndex()` returns zero hits in `StreamSourceRepository.kt`.

**Status:** `[x]` done

---

### Step 03.5 - Route the play outcome through the user state

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamSourceRepository.kt`
**Depends on:** Step 03.4

**Prompt for developer:**

> Point `observePlayOutcomes`, `recordPlayOutcome`, `playOutcome` and `clearPlayOutcomes` at
> `stream_user_state`. `observePlayOutcomes` uses the joining Flow from step 03.2 and keeps returning a
> map keyed by row id, so nothing above the repository changes. The three single-channel entry points
> resolve the row's `identityKey` first and write or read under it. Leave `StreamPlayOutcomeDao` and its
> table in place and unread for now - Phase 04 retires them once nothing references them.

**Why:**

Research artifact 01 measured that the outcome is one of the three data kinds keyed by the row id and
therefore one of the three that do not survive the cycle, and strategic §11 criterion 3 forbids leaving
any user data attached to an identifier that no longer exists.

**Verification:**

- `Grep` - `streamPlayOutcomeDao` returns zero hits inside `mergeCatalog`, `observePlayOutcomes`, `recordPlayOutcome`, `playOutcome` and `clearPlayOutcomes`.
- `Grep` - `observePlayOutcomes` still returns `Flow<Map<String, String>>`.
- `Grep` - `StreamPlayOutcomeDao` still imported, proving the table was left in place rather than half-removed.

**Status:** `[x]` done

---

### Step 03.6 - An explicit user deletion takes the user state with it

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamSourceRepository.kt`
**Depends on:** Step 03.5

**Prompt for developer:**

> `remove` and `deleteAllDownloaded` are the two paths where the user, not the bank, decides a channel
> should go. Both must delete the matching `stream_user_state` rows in the same transaction. Keeping
> state here would resurrect a pin the user deliberately removed the next time the bank republished that
> address.

**Why:**

Strategic §5.1 pillar 3 protects user data against the bank's absence, not against the user's own
decision, so the two deletion paths have to stay distinguishable from a prune.

**Verification:**

- `Grep` - `streamUserStateDao` present in both `remove` and `deleteAllDownloaded`.
- `Grep` - `db.withTransaction` still wraps both.

**Status:** `[x]` done

---

### Step 03.7 - Prove the cycle in `StreamSourceCatalogMergeTest`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/StreamSourceCatalogMergeTest.kt`
**Depends on:** Step 03.6

**Prompt for developer:**

> Add tests to the existing class, keeping every test already there. Cover: a pinned channel pruned by
> one merge and present again in the next comes back pinned, at the same position, with its play outcome
> intact, and with a different row id than it had before; the same channel republished with `https`
> instead of `http` keeps its pin and does not produce a second row; a channel the user removed by hand
> does not come back pinned when the bank republishes it; the bounded prune drops an unpinned, stale
> user-state row and keeps a pinned one of the same age; and the existing bind-limit-scale merge still
> passes with the projection restore added.

**Why:**

Strategic §3.3 sets the validation level at a delete-then-return cycle proving pin, collection and
history survived, and the existing suite asserts none of those - it was written to prove the orphan
purge works, which is the behaviour this phase replaces.

**Verification:**

- `Grep` - `identityKey` present in the test file.
- `Grep` - at least five new `@Test` functions whose names contain `pin`, `return`, `https`, `removed` or `prune`.
- `.\a.ps1 fu` - `StreamSourceCatalogMergeTest` passes, and no previously passing test in it was deleted.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] `Grep -n "Log\.d\("` returns zero hits in every file touched.
- [x] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Read the Room main-safety and concurrency-correctness layers in `docs/CODE_AUDIT_PROTOCOL.md`; this phase adds writes inside an existing transaction.

---

## Corrections made while implementing

- **Step 03.1 was already satisfied, one layer lower.** The plan asked four use cases each to stamp
  `identityKey = StreamChannelIdentity.of(url)`. Phase 02 instead put the derivation in
  `StreamSourceRepository.withIdentity()`, through which every write path already passes. That is the
  stronger form of the same requirement: a use case can forget to stamp, the repository cannot be
  bypassed. The step's verification predicates (`StreamChannelIdentity.of` in all four files) are
  therefore not met and should not be - they describe the weaker design.

- **Step 03.7 said "keeping every test already there"; three had to change.**
  `mergeCatalog_prune_takesThePrunedChannelsPlayOutcome`,
  `mergeCatalog_clearsOutcomesStrandedByEarlierImports` and the old
  `deleteAllDownloaded_takesOutcomesOfDownloadedRowsAndKeepsManualOnes` asserted S1826's unconditional
  orphan purge - the exact behaviour this phase replaces. Keeping them verbatim would have meant keeping
  the phase from ever passing. They were rewritten to assert the new contract rather than deleted, so the
  intent they encoded (no state left stranded, no state resurrected) still has a test. The step's own
  **Why** already said the existing suite "was written to prove the orphan purge works, which is the
  behaviour this phase replaces" - the instruction and the rationale disagreed, and the rationale won.

- **`stream_play_outcome` is now written by nothing** and read only by `remove`, which still clears the
  pre-migration rows. Phase 04 retires it, as planned.

## Verification actually run

- `..ps1 fk` - exit 0.
- Unit suite filtered to `*Stream*` - 48 classes, 329 tests, 0 failures, 0 errors, read off the JUnit XML
  rather than the gradle banner. `StreamSourceCatalogMergeTest` itself: 9 tests, all green.

---

## Handoff Notes to Next Phase

`stream_user_state` is now the durable copy and nothing reads `stream_play_outcome` any more, so Phase 04
can retire that table. The pin projection on the catalog row is a cache refreshed by the merge and by the
pin writers - never write it without writing the durable copy in the same transaction.

---

## Rollback Plan

Revert the phase commit. Schema 52 stays, `stream_user_state` goes back to being written only by the
migration, and the repository returns to reading the pin off the row and the outcome off
`stream_play_outcome`, which this phase leaves populated and untouched.
