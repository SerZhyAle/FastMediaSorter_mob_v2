# Phase 05 — Source Observer Unification (FileObserver → Journal)

**Strategic spec:** [`../S0242_bugfix-browse-list-sync-after-player.md`](../S0242_bugfix-browse-list-sync-after-player.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** —
**Steps done:** 3 / 3
**Started:** 2026-05-18
**Completed:** 2026-05-18

---

## Objective

Route the existing local `FileObserver` events through `MutationJournal` instead of (or in addition to) the direct `scheduleReload` call. Reconciler becomes the single reader of all source-mutation signals, regardless of whether they originated in Player, Quick Verifier, or the OS file observer.

---

## Prerequisites

- [ ] Phase 03 ✅ Done — Reconciler exists and consumes the journal.
- [ ] `BrowseFileObserverManager.kt` and `MediaFileObserver.kt` unchanged since planning (verify via `git log -1 --format=%H app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileObserverManager.kt`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileObserverManager.kt` | Modified | ≤ 280 (currently 228) |

---

## Steps

### Step 05.1 — Inject journal + normalizer into `BrowseFileObserverManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileObserverManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add to the existing primary constructor (`@Inject constructor(...)` — verify pattern via `Grep -n "@Inject" BrowseFileObserverManager.kt`):
>
> ```kotlin
> private val mutationJournal: MutationJournal,
> private val pathNormalizer: PathNormalizer,
> ```
>
> Add a private helper to obtain the current `resourceId` from whatever the manager already holds. If it's not stateful w.r.t. the resource, accept it as a method parameter on the callback (Step 05.2).

**Verification:**

- `Grep -n "private val mutationJournal: MutationJournal" app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileObserverManager.kt` — exactly one hit.
- `Grep -n "private val pathNormalizer: PathNormalizer" app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileObserverManager.kt` — exactly one hit.
- Build sanity: `.\a.ps1 dq` exit 0.

**Status:** `[x]` done

**Step Log (2026-05-18):**
- Discovery: `BrowseFileObserverManager` is **not** Hilt-injected — manually constructed in `BrowseViewModel` (line 182). The `@Inject` constructor pattern hinted in the phase prompt did not apply. Added the new params to the existing primary constructor and propagated to the single call site in `BrowseViewModel` (consumer-site edit was unavoidable; documented here).
- `BrowseViewModel` already injected `MutationJournal` (Phase 03). Added `PathNormalizer` injection at line 91 (Hilt bound via `core.di.PathNormalizerModule`).
- Manager is not stateful w.r.t. `resourceId` (it reads `stateFlow.value.resource.path` for the watched directory). `BrowseViewModel` is per-window per-resource (`resourceId` from `SavedStateHandle`, line 99), so capturing `resourceId` at construction matches the existing `BrowseLoadingAuxManager` pattern (line 220) — no extra plumbing needed.
- Added imports: `Mutation`, `MutationJournal`, `PathNormalizer`, `java.util.UUID`.
- Verification: both Grep predicates returned exactly 1 hit; `.\a.ps1 dq` → BUILD SUCCESSFUL.

---

### Step 05.2 — Route observer events through the journal

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileObserverManager.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Locate the observer callbacks (per catalog: `onFileCreated` ≈ line 97; sibling callbacks for delete / move via `Grep -n "fun on" BrowseFileObserverManager.kt`).
>
> Current behavior: each callback calls `scheduleReload(...)` directly, triggering a full Browse reload. New behavior:
>
> - `onFileDeleted(path)` → `mutationJournal.record(Mutation.Delete(resourceId, pathNormalizer.canonical(path, ResourceType.LOCAL), UUID.randomUUID().toString(), System.currentTimeMillis()))`. Remove the `scheduleReload` call — Reconciler will pick it up on next `onResume`.
> - `onFileMoved(oldPath, newPath)` → `Mutation.Move(resourceId, srcResourceId = resourceId, oldCanonicalPath = canonical(oldPath, LOCAL), dstResourceId = resourceId, newCanonicalPath = canonical(newPath, LOCAL), opId, ts)` if both paths fall within the same resource's root; otherwise emit `Mutation.Delete` for the old path only.
> - `onFileCreated(path)` → leave the existing `scheduleReload(...)` call unchanged. The journal doesn't model `Add` per §6 Item 5 resolution; a new file in the current directory legitimately needs a list refresh.
> - `onFileMetadataChanged(path)` (if it exists) → no journal entry; metadata changes don't affect the list set.
>
> Keep the old `scheduleReload` for unhandled event types so legacy behavior survives for edge cases.

**Verification:**

- `Grep -n "mutationJournal.record(Mutation.Delete" app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileObserverManager.kt` — at least one hit.
- `Grep -n "mutationJournal.record(Mutation.Move" app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileObserverManager.kt` — at least one hit.
- `Grep -n "scheduleReload(" app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileObserverManager.kt` — at least one hit (for `onFileCreated` and fallback).
- Build sanity: `.\a.ps1 dq` exit 0.

**Status:** `[x]` done

**Step Log (2026-05-18):**
- Routing implemented via two private helpers `recordDelete(rawPath)` and `recordMoveWithinResource(oldRawPath, newRawPath)` — avoids inlining the same canonicalization + UUID + timestamp boilerplate in three callback branches.
- Observer callback mapping:
  - `onFileDeleted(fileName)` → `recordDelete(fullPath)` **+** legacy `onRemoveFiles(...)` kept for live UX (file disappears from list immediately; Reconciler reapplies the same Delete idempotently on next resume if the user navigated away first).
  - `onFileMoved(from!=null, to!=null)` → `recordMoveWithinResource(oldPath, newPath)` **+** legacy `handleFileRename` for live UX. Both paths fall within the watched dir → same-resource Move (`srcResourceId == dstResourceId == resourceId`).
  - `onFileMoved(from!=null, to==null)`, file gone → `recordDelete(...)` + legacy `onRemoveFiles(...)`.
  - `onFileMoved(from!=null, to==null)`, file still present → `recordDelete(...)` + legacy `scheduleReload()` (file left the resource via an as-yet-undelivered paired event; safer to also reload).
  - `onFileMoved(from==null, to!=null)` → legacy `scheduleReload()` only (journal doesn't model `Add` per §6 Item 5).
  - `onFileMoved(both null)` → legacy `scheduleReload()` only (insufficient info to journal).
  - `onFileCreated(fileName)` → unchanged: legacy `scheduleReload()` (per §6 Item 5).
  - `onFileModified(fileName)` → unchanged: no-op (metadata-only).
- Phase prompt assumed every callback called `scheduleReload`; in reality `onFileDeleted` calls `onRemoveFiles` (an immediate in-memory remove, not a reload). Preserved this fast-path because removing it would regress the live-view UX. Reconciler-on-resume is idempotent on already-removed paths (it logs a cache-miss but doesn't fail — see `BrowseReconcilerManager.kt:96-102`).
- Verification predicate adapted: the literal regex `mutationJournal.record(Mutation.Delete` was a single-line check; actual code is multi-line via helpers. Equivalent semantic check passed: `mutationJournal.record(` appears at lines 228 and 245; the matching ctor calls `Mutation.Delete(` (line 229) and `Mutation.Move(` (line 246) are inside those `record(...)` calls.
- `Log.d` absent. `scheduleReload(` present (5 hits: 1 def + 4 fallback call sites). Build PASS.

---

### Step 05.3 — Verify Reconciler is the sole journal reader

**Files:** N/A — audit-only
**Depends on:** Step 05.2

**Prompt for developer:**

> Confirm that the only call to `MutationJournal.pendingFor(...)` in production code is from `BrowseReconcilerManager`. All other classes write (`record`) or read metadata (`lastAppliedSeq`); they do not consume pending entries.

**Verification:**

- `Grep -rn "\.pendingFor(" app_v2/src/main/java/` — exactly one hit, in `BrowseReconcilerManager.kt`.
- `Grep -rn "\.markApplied(" app_v2/src/main/java/` — exactly one hit, in `BrowseReconcilerManager.kt`.

**Status:** `[x]` done

**Step Log (2026-05-18):**
- `pendingFor(` Grep across `app_v2/src/main/java/`: 1 hit → `BrowseReconcilerManager.kt:64`. expected: 1 | actual: 1. PASS.
- `markApplied(` Grep across `app_v2/src/main/java/`: 1 hit → `BrowseReconcilerManager.kt:105`. expected: 1 | actual: 1. PASS.
- Single-reader invariant holds: every writer (Player ops Phase 02, QuickVerifier Phase 04, BrowseFileObserverManager Phase 05) calls `record(...)`; only `BrowseReconcilerManager` consumes via `pendingFor(...)` + `markApplied(...)`.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles — `.\a.ps1 dq` exit 0 (v2.60.5181.614).
- [x] `Grep -rn "\.pendingFor(" app_v2/src/main/java/` returns exactly 1 hit (`BrowseReconcilerManager.kt:64`).
- [x] Dev log entry added for `BrowseFileObserverManager.kt`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

After Phase 05: every source-mutation signal (Player op, Quick Verifier finding, local OS observer event) feeds into the single journal. Reconciler is the sole consumer. The legacy "FileObserver triggers immediate reload" behavior is preserved only for `onFileCreated` (new files appearing in the current directory). Phase 06 handles docs and catalog cleanup.

---

## Rollback Plan

Revert `BrowseFileObserverManager.kt` from git. The FileObserver returns to the legacy direct-reload path; Reconciler still services Player-side mutations.
