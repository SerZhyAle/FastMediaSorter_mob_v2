# Research 01 - Deterministic pinned-order index strategy

**Strategic §6 item:** 1 (Стратегия детерминизма индексов)
**Status:** Resolved

## Question

How to make move-up / move-down / move-to-top deterministic given the existing local order column (`StreamSourceEntity.sortIndex`) may contain gaps and duplicates (pin-to-top assigns `min-1`; the `addedAt` DESC tiebreak resolves equal indices).

## Findings

- `sortIndex` is an `Int` on `stream_sources`. Consumers order by `sortIndex ASC` within the pinned set:
  - `StreamSourceDao.observePinned()` - main-window panel (`ORDER BY sortIndex ASC, addedAt DESC`).
  - `StreamSourceDao.observeAll()` - player prev/next channel navigation (`ORDER BY pinned DESC, sortIndex ASC, addedAt DESC`).
- The only current mutation is `pinToTop`: `sortIndex = min(sortIndex) - 1`. It never renumbers, so repeated pins leave sparse/negative indices - fine for ordering, not for relative moves.
- Swapping only the adjacent neighbour's index is fragile when two pinned rows share a `sortIndex` (equal values swap to no-op).

## Decision

Renumber the **whole pinned set contiguously** on every reorder:

1. Read the pinned rows in current display order (`sortIndex ASC, addedAt DESC`).
2. Apply the move to that in-memory list (remove at `from`, insert at `to`; `to` clamped to `[0, lastIndex]`).
3. Write `sortIndex = list position` for the reordered ids in one Room transaction.

Rationale: pinned count is small, the operation is O(N) with zero edge cases, duplicates/gaps self-heal into `0..N-1`, and it leaves unpinned rows and the existing `pinToTop` path untouched (a freshly pinned `min-1` row still lands at the top, then the next reorder normalises it).

## Impact on plan

- Repository exposes a `pinnedSnapshot()` read and a `reorderPinned(orderedIds)` transactional write; the move math lives in the use case (domain), so the data layer never imports a domain enum.
- No Room schema change - `sortIndex` already exists; `@Database` version is not bumped.
