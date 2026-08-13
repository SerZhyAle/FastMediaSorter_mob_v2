# S0955 - Browse hot-path perf: PagingMediaFileAdapter reloads thumbnails on every selection tick

**Status:** Archived
**Priority:** 50
**Date:** 2026-07-05
**Tier:** 3 - Moderate (ad-hoc)

<!-- parked by S0905 audit sweep (Layer 6) - 2026-07-05 -->

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-05, из S0905 Layer 6 static perf sweep.

Symptom: on the paging browse adapter, a selection toggle forces a full rebind that re-issues a Glide thumbnail request for every affected row, even when the file/thumbnail is unchanged - noticeable on 1000+-file folders during multi-select drag.

Evidence:
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/PagingMediaFileAdapter.kt:86-95` - `setSelectedPaths()` calls `notifyItemChanged(index)` with no payload; adapter has no `onBindViewHolder(holder, position, payloads)` override, so every tick runs full `bind()`.
- `PagingMediaFileAdapter.kt:162-244` (esp. 209, 226-232) - `ListViewHolder.bind()` unconditionally calls `loadThumbnail(file)` with no `lastLoadedKey` identity guard, unlike sibling `MediaFileAdapter.ListViewHolder`.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt:119-153` - AUDIO/TEXT/OFFICE_DOCUMENT/generic-binary branches skip the `lastLoadedKey` short-circuit the image/video/pdf/epub path uses (line 153), so the cache-hit path re-does full cold-path work (extension parse + cache-key rebuild + LruCache lookup + setImageBitmap).

Severity (as captured): P2 (hot-path allocation/redundant Glide work).

Scope note: mirror the sibling adapter's `lastLoadedKey` guard + add a payload-aware `onBindViewHolder` that skips thumbnail reload on selection-only changes; extend the short-circuit to all `AdapterThumbnailLoader` branches.

## 1. Investigation (2026-07-05)

**Latent, and blocked on S0954.** `PagingMediaFileAdapter` (and `PagingLoadStateAdapter`) are **never instantiated** - they are the RecyclerView half of the same dead Paging3 browse subsystem S0954 documents (no `submitData` anywhere; `pagingDataFlow` never collected; `loadMediaFilesWithPagination` has zero callers). The live browse list uses `MediaFileAdapter` via `BrowseRecyclerViewManager`, which already has the `lastLoadedKey` guard. So this P2 rebind cost cannot occur today.

Consequence: the fix here is decided by S0954's fork -
- S0954 Option A (remove dead Paging3) -> `PagingMediaFileAdapter`/`PagingLoadStateAdapter` are deleted with the subsystem; this ticket is subsumed (archive alongside).
- S0954 Option B (revive + fix) -> this adapter comes back into use and the `lastLoadedKey` guard + payload-aware `onBindViewHolder` in the scope note become the right fix.

The `AdapterThumbnailLoader` short-circuit gap (AUDIO/TEXT/OFFICE/binary branches skipping the `lastLoadedKey` check, `AdapterThumbnailLoader.kt:119-153`) is the ONLY part touching a shared, live loader - but the live `MediaFileAdapter` path also uses it, so if worth doing it is a tiny independent P3 (can be split out regardless of the S0954 fork).

## Related

- S0954 - the parent dead-Paging3 finding this depends on (remove vs revive fork).
- S0905 (audit-tail sweep, source); docs/CODE_AUDIT_PROTOCOL.md Layer 6.

## Resolution (2026-07-15)

Archived as **subsumed**. S0954's remove-vs-revive fork resolved as **remove** (S0954 -> Archived): `PagingMediaFileAdapter` and `PagingLoadStateAdapter` no longer exist in `app_v2/src/` (Glob `Paging*Adapter.kt` -> 0 files). This ticket's headline scope (payload-aware `onBindViewHolder` + `lastLoadedKey` guard on `PagingMediaFileAdapter`) is therefore moot - all evidence lines point to deleted files. Blocker is Archived, not Verified, so the block would never clear.

The one separable, still-live residual - the `AdapterThumbnailLoader` short-circuit gap on the live `MediaFileAdapter` path (AUDIO/TEXT/OFFICE/binary branches return before the `lastLoadedKey` guard) - is split off to **S1053** (Draft, prio 35) with the verified evidence carried over.
