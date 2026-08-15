# Phase 01 - Edit Data Path

**Strategic spec:** [`../S0660_stream-card-overflow-actions-menu.md`](../S0660_stream-card-overflow-actions-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Introduce an in-place update path for a stored stream source - DAO update, repository method, and `UpdateStreamSourceUseCase` - that re-classifies the media kind from the new URL while preserving the row identity, pin state, sort order, origin and play-outcome. No UI in this phase.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `StreamSourceRepository`, `StreamSourceDao`, `StreamMediaKindClassifier`, `StreamSourceEntity` exist (verified in `/spec-tech` step 2).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamSourceDao.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamSourceRepository.kt` | Modified | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/UpdateStreamSourceUseCase.kt` | New | ≤ 80 |

---

## Steps

### Step 01.1 - Add an in-place DAO update for url/title/mediaKind

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamSourceDao.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a suspend `@Query` `updateUserFields(id: String, url: String, title: String, mediaKind: String)` that sets only `url`, `title` and `mediaKind` `WHERE id = :id AND sourceOrigin = 'MANUAL'`. Restricting the SQL to `MANUAL` rows enforces the manual-only edit scope (strategic §6.4) at the data layer, so a mis-routed call can never mutate a CATALOG/IMPORTED row. Do not touch `sortIndex`, `pinned`, `addedAt`, `lastPlayOutcome` or the category/topic/language columns.

**Verification:**

- `Grep` - `fun updateUserFields(` matches exactly once in `StreamSourceDao.kt`.
- `Grep` - `sourceOrigin = 'MANUAL'` present in the new query string.
- `Grep -n "Log\.d\("` on `StreamSourceDao.kt` returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-24 - Verification 3/3 PASS. Files: StreamSourceDao.kt (+12 LOC). Dev log recorded.

---

### Step 01.2 - Expose `updateUserFields` on the repository

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamSourceRepository.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `suspend fun updateUserFields(id: String, url: String, title: String, mediaKind: String) = dao.updateUserFields(id, url, title, mediaKind)`. Keep it a thin pass-through, matching the existing `pinToTop`/`remove` style.

**Verification:**

- `Grep` - `fun updateUserFields(` matches exactly once in `StreamSourceRepository.kt`.
- `Grep` - `dao.updateUserFields(` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-24 - Verification 2/2 PASS. Files: StreamSourceRepository.kt (+4 LOC). Dev log recorded.

---

### Step 01.3 - Add `UpdateStreamSourceUseCase`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/UpdateStreamSourceUseCase.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Create `UpdateStreamSourceUseCase` (constructor-injected `StreamSourceRepository` + `StreamMediaKindClassifier`), mirroring `AddStreamSourceUseCase`. `suspend operator fun invoke(source: StreamSourceEntity, url: String, title: String?): UpdateResult`: trim the url; return `UpdateResult.InvalidUrl` if `classifier.isSupportedScheme` is false; resolve the title from the trimmed input or fall back to the existing `source.title`; re-classify the media kind from the new url; guard with `if (source.sourceOrigin != "MANUAL") return UpdateResult.NotEditable`; call `repository.updateUserFields(source.id, trimmedUrl, resolvedTitle, mediaKind)`; return `UpdateResult.Success`. Declare `sealed interface UpdateResult { Success; InvalidUrl; NotEditable }`. No `Timber` calls.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/UpdateStreamSourceUseCase.kt` exists.
- `Grep` - `class UpdateStreamSourceUseCase` matches exactly once.
- `Grep` - `sourceOrigin != "MANUAL"` present (manual-only guard).
- `Grep` - `repository.updateUserFields(` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-24 - Verification 4/4 PASS. Files: UpdateStreamSourceUseCase.kt (New, 41 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new use-case class) - deferred to Phase 04 cleanup.

---

## Handoff Notes to Next Phase

`UpdateStreamSourceUseCase` enforces manual-only edit at both the use-case guard and the DAO `WHERE` clause. Phase 03 consumes it via `StreamsViewModel.onEdit`; the UI must additionally hide the Edit menu item for non-`MANUAL` rows so the user never reaches a no-op path.

---

## Rollback Plan

Revert the phase commit(s) - no Room schema version change (only a `@Query` `UPDATE`, no column added), no migration, no user-facing surface yet.
