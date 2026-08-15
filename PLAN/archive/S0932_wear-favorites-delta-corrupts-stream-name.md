# S0932 - Wear favorites delta corrupts stream-favorite rows

**Ticket:** S0932
**Status:** Archived
**Priority:** 40
**Date:** 2026-07-04
**Tier:** 2 - Easy (ad-hoc)

> Bugfix. Out-of-scope finding from S0783, root cause confirmed in code (2026-07-05).

## 0. Raw capture (verbatim evidence)

Found while fixing S0783 (stream favorites showing `master.m3u8` instead of the channel name).

- `ApplyWatchFavoritesDeltaUseCase` applies watch->phone favorite deltas by writing a `FavoritesEntity` with `displayName = item.filePath.substringAfterLast('/')`, `resourceId = 0L`, `mediaType = 0` (IMAGE), and no `kind`/`streamMediaKind`.
- For a live-channel favorite (S0783 STREAM row) whose uri is an HLS URL like `https://.../master.m3u8`, this write produces exactly the reported `master.m3u8` display name, downgrades the row to a generic FILE/IMAGE favorite, and drops the STREAM discriminator - so the row loses its stream identity and open-in-stream-player routing.
- This is the most plausible root of the stale/broken `favorites.displayName` snapshot S0783 masks at display time (S0783 re-resolves the name from the live catalog, but the stored DB row stays wrong; open-routing depends on `resourceId == SyntheticResourceIds.STREAM`, which this write clobbers).

Evidence: `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ApplyWatchFavoritesDeltaUseCase.kt` (delta-apply mapping).

## 1. Problem

The watch->phone favorites delta path was written for file favorites only and unconditionally rebuilds each incoming item as a plain file favorite: display name from the path tail, `resourceId = 0`, `mediaType = IMAGE`, `kind` omitted. The favorites table is shared with live channels (S0783). When a delta item's uri matches an existing STREAM favorite, `addFavorite` upserts on the unique `uri` index and REPLACES the good stream row with a mis-titled, mis-typed generic file favorite that no longer opens in the stream player.

## 2. Decisions (resolved from codebase, 2026-07-05)

- **Preserve, do not rebuild (was: exclude vs preserve).** The `FavoritesEntity` S0783 contract states file-only consumers (widget, export/backup, **Wear**) read the `kind = 'FILE'` slice - Wear does not own STREAM rows. The watch delta carries only `filePath`/`isFavorite`/`changedAt` (`WearFavoriteDeltaItem`), with no `kind`/`resourceId`/`streamMediaKind`, so it cannot faithfully reconstruct a STREAM row. Therefore the apply must never overwrite a favorite the phone already holds; it only materializes genuinely new file favorites the watch introduced.
- **Corruption is on the apply side.** No producer in `app_v2` builds a phone->watch favorites delta; the confirmed clobber is entirely in `ApplyWatchFavoritesDeltaUseCase`. A producer-side exclusion of STREAM rows (Wear module) would be defensive-in-depth but is out of scope here and unnecessary to fix the confirmed corruption - the apply guard is sufficient.
- **Verification.** The mapping fix is proven by unit test at the apply level (a seeded STREAM favorite survives a matching delta; a new file favorite is still added). End-to-end confirmation (favorite a channel, round-trip through a watch, observe the row) needs a Wear device and is a later on-device check, not a precondition of the logic fix.

## 3. Approach

- `ApplyWatchFavoritesDeltaUseCase` (`domain/usecase`): for an `isFavorite = true` item, skip the rebuild-and-add when `favoritesRepository.isFavoriteSync(item.filePath)` is already true; only construct + add a `FavoritesEntity` for genuinely new uris. `isFavorite = false` (remove) is unchanged.
- `ApplyWatchFavoritesDeltaUseCaseTest` (`test/.../domain/usecase`): add a case seeding an existing STREAM favorite and asserting a matching delta item does not clobber it (name, `resourceId = SyntheticResourceIds.STREAM`, `kind = STREAM`, `streamMediaKind` preserved; `addFavorite` not called for it) while a new file favorite in the same batch is still added.

## 4. Done criteria

1. Applying a watch delta whose item uri equals an existing STREAM favorite leaves that row's `displayName`, `resourceId`, `kind` and `streamMediaKind` unchanged (no downgrade to a `master.m3u8` file favorite).
2. Applying a watch delta for a brand-new file uri still adds a file favorite (no regression).
3. Unfavorite (remove) behavior unchanged.
4. `ApplyWatchFavoritesDeltaUseCaseTest` passes, including the new STREAM-preservation case.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0783 (streams-favorites-channel-entry - shared favorites table / STREAM discriminator; masked this at display time), S0552 (resume-wear-development - Wear program umbrella).

## Last Audit

**Date:** 2026-07-05
**Mode:** compact (bugfix)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 4 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 0

### Manual / on-device

- [x] Fix present exactly as section 3: `ApplyWatchFavoritesDeltaUseCase` guards the FILE-entity rebuild with `if (favoritesRepository.isFavoriteSync(item.filePath)) continue`; the `isFavorite = false` remove path is unchanged.
- [x] Test present + green: `ApplyWatchFavoritesDeltaUseCaseTest` "delta preserves an existing STREAM favorite and still adds a new file favorite" seeds a STREAM row (`resourceId = SyntheticResourceIds.STREAM`, `kind = KIND_STREAM`, `streamMediaKind = "VIDEO"`), asserts it survives a matching delta and a new file favorite is still added; criteria 2/3 covered by the add/remove/mixed/empty tests. Targeted run `:app_v2:testStandardDebugUnitTest --tests *ApplyWatchFavoritesDeltaUseCaseTest` -> exit 0.
- [x] Done criteria 1-4 satisfied statically + by the passing unit test.
- [ ] End-to-end Wear round-trip (favorite a channel, sync through a watch, observe the stored row) - needs a Wear device; per section 2 a later on-device check, NOT a precondition of the logic fix.
