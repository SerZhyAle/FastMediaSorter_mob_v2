# Phase 04 - SAF directory operations

**Strategic spec:** [`../S1378_sd-card-external-storage-support.md`](../S1378_sd-card-external-storage-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 7 / 7
**Started:** 2026-08-05
**Completed:** 2026-08-05

---

## Objective

Implement recursive whole-folder copy, move, delete and rename over a document tree with progress and cancellation, retire the pre-flight refusal that blocks such operations today, and make a removable volume a valid endpoint of the existing cross-protocol tree transfer.

---

## Prerequisites

- [x] Phase 03 is ✅ Done.
- [x] Working tree is clean or on a feature branch.
- [x] `temp/CODE.LOCK` acquired before the first source edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SafOperationStrategy.kt` | Modified | ≤ 900 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SafDirectoryWalker.kt` | New | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandler.kt` | Modified | ≤ 655 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/TransferProtocolKeys.kt` | New | ≤ 25 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/DirectoryTreeTransferManager.kt` | Modified | ≤ 210 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandlerDirectoryGuardTest.kt` | Modified | ≤ 140 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/DirectoryTreeTransferManagerSafTest.kt` | New | ≤ 120 |

> Budget for the handler raised 640 -> 655, and four files added to the list. The handler ended at 651: this phase removed 17 lines from it, but the plan's 640 was written against a 653-LOC file that other tickets had already grown to 668 by the time the phase ran. The four extra files are the protocol-key extraction forced by Step 04.7 and the two tests that lock it, plus the guard test whose assertion this phase inverts - none of which the plan anticipated.
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/helpers/DirectoryRefusalMessages.kt` | Modified | ≤ 60 |
| `app_v2/src/main/res/values/strings.xml` | Modified | key removal |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/strategy/SafDirectoryWalkerTest.kt` | New | ≤ 240 |

> `SafOperationStrategy.kt` crosses 500 LOC once this phase lands - back it up before editing and keep it under the 1500-LOC ceiling by holding the traversal in the separate walker file.

---

## Steps

### Step 04.1 - Extract the recursive walker

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SafDirectoryWalker.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Back up `SafOperationStrategy.kt` into `temp/S1378/` first. Then add a walker that enumerates a document tree depth-first, yielding each entry with its relative path and directory flag, counting entries and total bytes in a first pass so a caller can report progress against a known total. Keep it free of copy and delete logic - traversal only. Make every suspend member honour coroutine cancellation by checking `ensureActive` between entries.

**Why:**

Strategic §3.2 fixes a performance budget of a progress update at least once per second and cancellation within the current file, which requires a known total and a cancellation-aware traversal rather than a blind recursive copy.

**Verification:**

- `Glob` - the file exists and `temp/S1378/SafOperationStrategy*.kt` exists.
- `Grep` - `class SafDirectoryWalker` matches exactly once.
- `Grep` - `ensureActive` present.

**Status:** `[x]` done - `SafDirectoryWalker.kt` created; `temp/S1378/SafOperationStrategy.kt.20260805-105000.bak` (16747 B) expected: exists | actual: exists; `class SafDirectoryWalker` expected: 1 | actual: 1; `ensureActive` expected: present | actual: present (before every yielded entry and every level).

> Two walk orders, not one: `walk` hands a directory over **before** its children (a copy needs the destination subtree created top-down) and `walkDepthFirstLeavesFirst` after them (a provider refuses to delete a non-empty document). A single order would have forced one of the two operations to buffer the tree.

---

### Step 04.2 - Implement listing, type and info members

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SafOperationStrategy.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Override `listEntries`, `isDirectory` and `getDirectoryInfo` in `SafOperationStrategy` using the walker and a single `ContentResolver` query per directory level, so a listing costs one query rather than one query per child.

**Why:**

The interface default builds `listEntries` from `listFiles` plus one `isDirectory` call per child, which on a document tree means an extra resolver round trip per file and directly threatens the progress budget in strategic §3.2.

**Verification:**

- `Grep` - `override suspend fun listEntries`, `override suspend fun isDirectory` and `override suspend fun getDirectoryInfo` each match exactly once in the file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done - each override present exactly once (`isDirectory` was already added in Phase 03 and is not duplicated); compile expected: 0 | actual: 0.

---

### Step 04.3 - Implement recursive copy with progress

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SafOperationStrategy.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Override `copyDirectory`: create the destination subtree level by level, copy each file through the resolver, and report `(copied, total, currentName)` through the progress callback at least once per second. On a name conflict inside the destination, keep the existing file and count the entry as skipped rather than overwriting it. Return the copied count; on a failure part-way through, return `Result.failure` carrying how many entries were copied so the caller can state a partial result.

**Why:**

Strategic §6 item 3 resolves that an interrupted transfer stops on the current entry and reports a partial result instead of rolling back, which is only expressible if the failure carries the completed count.

**Verification:**

- `Grep` - `override suspend fun copyDirectory` matches exactly once in the file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done - one override; compile expected: 0 | actual: 0. A name already taken at the destination keeps the existing document and counts as skipped; the partial count survives a failure via `PartialDirectoryTransferException`.

---

### Step 04.4 - Implement recursive move, delete and rename

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SafOperationStrategy.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Override `moveDirectory` as copy followed by deletion of the source, deleting a source entry only after its write to the destination is confirmed. Override `deleteDirectory` depth-first with the same progress shape, and `renameDirectory` through the document rename call, refusing a rename that would leave the current parent.

**Why:**

Strategic §7 mitigates the mid-operation ejection risk by requiring the source to be deleted only after a confirmed write, which makes the ordering inside move a correctness requirement rather than an implementation preference.

**Verification:**

- `Grep` - `override suspend fun moveDirectory`, `override suspend fun deleteDirectory` and `override suspend fun renameDirectory` each match exactly once in the file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done - each override present exactly once; compile expected: 0 | actual: 0. The delete-after-confirmed-write ordering strategic §7 requires is per entry, not per tree; the P0 in the audit below is what that ordering nearly cost.

---

### Step 04.5 - Retire the document-tree refusal

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandler.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/helpers/DirectoryRefusalMessages.kt`, `app_v2/src/main/res/values/strings.xml`
**Depends on:** Step 04.4

**Prompt for developer:**

> Remove the `isDocumentTreeDestination` branch from `refuseUnsafeDirectoryOperation`, the private helper itself, and the now-unreachable `DESTINATION_NOT_SUPPORTED` reason together with its message branch and its string key in all three locales. Keep the destination-inside-source and same-location refusals untouched. Remove the string keys with `scripts/utils/set-android-string.ps1 -Action remove`, not by hand.

**Why:**

Strategic ADR-3 requires the refusal to be lifted in the same change that provides the recursive implementation and not one phase earlier, because a relaxed guard without an implementation converts an explicit refusal into a silent failure on user data.

**Verification:**

- `Grep` - `isDocumentTreeDestination` returns zero hits across `app_v2/src`.
- `Grep` - `DESTINATION_NOT_SUPPORTED` returns zero hits across `app_v2/src`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1` exits 0.

**Status:** `[x]` done - `isDocumentTreeDestination` expected: 0 | actual: 0; `DESTINATION_NOT_SUPPORTED` expected: 0 | actual: 0; `check_strings_localized.ps1 -KeyPrefix error_folder` expected: 0 | actual: 0 (9 keys, all present in en/ru/uk).

> Two corrections to this step. The key lived in `strings_file_operations.xml`, not `strings.xml` - `set-android-string.ps1 -Action remove` without `-Locale` took all three locales in one call and confirmed no code reference survived. And the step's file list omitted `UnifiedFileOperationHandlerDirectoryGuardTest`, which asserted the very refusal being retired; its test is now inverted, asserting that a document-tree destination is let through and routed to the cross-protocol transfer rather than to the local strategy.

---

### Step 04.6 - Cover the traversal and the move ordering with tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/strategy/SafDirectoryWalkerTest.kt`
**Depends on:** Step 04.5

**Prompt for developer:**

> Add unit tests over a mocked document tree asserting: a nested tree is enumerated depth-first with correct relative paths, the pre-count matches the number of entries yielded, cancelling the scope stops the walk, and move deletes a source entry only after the destination write for that entry has been reported.

**Why:**

Strategic §11 criterion 3 makes a nested-folder transfer with working cancellation an acceptance condition, and the delete-after-write ordering is the invariant standing between an interrupted move and data loss.

**Verification:**

- `.\a.ps1 fu` - `SafDirectoryWalkerTest` passes; record `expected: PASS | actual: <result>`.

**Status:** `[x]` done - `SafDirectoryWalkerTest` expected: PASS | actual: PASS (exit 0), five tests over a fake children-cursor tree.

> One honest limit: the cancellation test asserts that a cancellation raised mid-walk propagates and stops the walk where it happened. It does not drive an externally cancelled scope - that would assert the coroutines runtime rather than this walker, and the `ensureActive()` calls that serve it are covered by Step 04.1's grep predicate. The delete-after-write ordering the step also names is asserted where it lives, in `moveDirectory`, not in the walker, which by design knows nothing about writing.

---

### Step 04.7 - Cover a removable volume as a cross-protocol transfer endpoint

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/DirectoryTreeTransferManagerSafTest.kt`
**Depends on:** Step 04.6

**Prompt for developer:**

> `DirectoryTreeTransferManager` already carries a whole tree between two different protocols by listing entries through the source strategy and writing them through the destination strategy, resolved from the same protocol map. Confirm and lock that behaviour for the new endpoint: add tests asserting that a network source with a `content://` destination resolves `SafOperationStrategy` as the destination strategy, that the destination subtree is created level by level before its files are written, and that a `content://` source with a network destination resolves it as the source strategy. Change the manager only if a test proves it mishandles the new key - it is generic over the protocol map and should need no edit.

**Why:**

Strategic §2 goal 3a makes a removable volume a full participant in folder transfer between resources of different types - moving a film or music directory from a network resource or the cloud straight onto the card - and that path runs through the cross-protocol manager rather than through the strategy's own `copyDirectory`, so it needs its own coverage.

**Verification:**

- `.\a.ps1 fu` - `DirectoryTreeTransferManagerSafTest` passes; record `expected: PASS | actual: <result>`.
- `Grep` - `DirectoryTreeTransferManager.kt` is unchanged unless the Step Log records which test forced an edit.

**Status:** `[x]` done - `DirectoryTreeTransferManagerSafTest` expected: PASS | actual: PASS (exit 0), three tests. **`DirectoryTreeTransferManager.kt` WAS changed**, and the reason is recorded here as the step requires.

> The step's premise was wrong: the manager is *not* generic over the protocol map. It carried its own private `protocolKeyOf`, a second copy of the same `when` that `UnifiedFileOperationHandler.getProtocolKey` holds - and Phase 03 taught only the first copy about `content:`. So inside the cross-protocol transfer a `content://` destination still resolved to the **local** strategy, the very strategy that declares it cannot address `content:`. A network-to-card folder move would have failed at the destination while every same-protocol path looked healthy.
>
> Fixed at the source rather than by patching the second copy: both call sites now delegate to one `transferProtocolKeyFor` in `TransferProtocolKeys.kt`. A third protocol cannot half-land the same way. The first test in `DirectoryTreeTransferManagerSafTest` is the one that fails without this change.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 db` expected: 0 | actual: 0, re-run after the `ThrowsCount` restructuring so the verdict covers the final state.
- [x] `Grep` for `TODO(phase-04)` returns zero hits - expected: 0 | actual: 0.
- [x] `SafOperationStrategy.kt` is under 1500 LOC - expected: < 1500 | actual: 612 (also inside this phase's own 900 budget).
- [x] Dev log entry added for the phase - written by `post-change.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - 2431 records (was 2428; `SafDirectoryWalker`, its entry types and `TransferProtocolKeys` are the new entries).
- [x] Phase-boundary audit run - one P0, one P1 and one P2 found and fixed inside the phase, none unresolved.

Closure: `post-change: PASS WITH ADVISORIES (1)`, exit 0, `assert-detekt: PASS [scoped]`, whole `data.transfer` test package green (`PKG_EXIT=0`). Closed as `Mixed` because a string key was removed, which is also what surfaced the P2 doc-pin drift.

Two closure attempts were rejected before that, both usefully:

- `doc-pin-drift` FAIL on the stale Room version - the P2 above.
- `ThrowsCount` on `copyFileInto` (3 throws, limit 2). Resolved by extracting `openDocumentInput` / `openDocumentOutput`, which also removed the duplicate stream-opening logic `openSource` was carrying.
- The `document-registry` gate then held the close until `dev/TECH_REQUIREMENTS.md`'s siblings were read. None of the eight carries the Room schema version - the only near-match, `docs/ARCHITECTURE.md`, is about the companion-config transport marker and says in the same line that it is not a schema version - so no sibling edit was needed and the close was acknowledged with `-RegistryAck 'architecture'`.

---

## Phase-boundary audit (2026-08-05)

Triggers fired: new long-lived helper (Layer 1), recursive suspend traversal and cancellation (Layer 2), stream ownership on a data-moving path (Layer 3), a guard removed from a path that writes user data (Layer 1).

**P0 - the move cleanup would have deleted a file it never copied. Found and fixed while writing.**

`moveDirectory` copies each entry and then removes the emptied source directories leaves-first. The first draft deleted **every** leftover the second walk found, not only directories. A file skipped because its name was already taken at the destination is exactly such a leftover - it was deliberately not copied, and the draft would have deleted the only copy of it. The cleanup pass now removes directories only, the per-entry deletion fires only on a confirmed write (`copyEntryWritten` returning true), and the final `sourceRoot.delete()` is allowed to fail, which is the correct outcome when a skipped file is still inside.

**P1 - a second, private copy of the protocol map.** Recorded in full under Step 04.7. In short: `DirectoryTreeTransferManager` had its own `protocolKeyOf`, Phase 03 taught only `UnifiedFileOperationHandler` about `content:`, and a network-to-card folder transfer therefore still resolved the local strategy for the destination. Fixed at the source - one `transferProtocolKeyFor` used by both.

**P2 - Phase 02's schema bump never reached the docs. Found by this phase's closure, fixed here.**

`dev/TECH_REQUIREMENTS.md` still pinned the Room DB version at 44 after Phase 02 took it to 45. The reason it survived a green Phase 02 closure is mechanical and worth remembering: `post-change -ChangeType Kotlin` **skips** the `doc-pin-drift` gate entirely, and Phase 02 touched only Kotlin. This phase also removed a string key, so it closed as `Mixed`, which runs the doc gates - and the drift surfaced immediately. Any future phase that bumps a pinned value in Kotlin alone will hide the same way; close it as `Mixed` or run `scripts/check-doc-vs-gradle.ps1` by hand.

Accepted with eyes open, no action this phase:

- **Traversal cost.** A tree operation walks the tree more than once: `measure` for the progress denominator, then the operation's own walk, and `moveDirectory` adds a third pass for the directory cleanup. That is the price of the known total strategic §3.2 asks for, and each pass is one resolver query per directory level rather than per file. Worth revisiting only if Phase 05's space pre-flight adds a fourth.
- **Recursion depth.** `descend` recurses per directory level, so depth is bounded by the folder nesting of the tree, not by its file count. A user folder hierarchy does not reach a depth where the suspend call stack matters; a synthetic one could.

Cleared:

- **Cancellation (Layer 2).** `ensureActive()` before every level and every yielded entry, so a walk stops inside the tree. Every broad catch in the strategy opens with `rethrowIfCancellation()`, so an abandoned operation is not converted into a `Result.failure` the UI would render as an error.
- **Guard removal (Layer 1).** ADR-3 requires the refusal to be lifted in the same change that supplies the implementation, which is what this phase does; the destination-inside-source and same-location refusals are untouched, and their tests still pass.

---

## Handoff Notes to Next Phase

Whole-folder operations against a document tree are functional and no longer refused. Nothing yet checks whether the destination has room - Phase 05 adds that pre-flight in front of these operations.

Two things Phase 05 should know before adding that pre-flight:

- A tree operation already walks the tree at least twice. If the space check needs a byte total, take it from `SafDirectoryWalker.measure`, which already returns one, rather than adding a walk of its own.
- `transferProtocolKeyFor` is now the single path->strategy-key mapping. Anything that needs to know which strategy a path belongs to calls it; do not re-derive the `when`.

---

## Rollback Plan

Revert the phase commit; the removed refusal, its reason and its string keys come back with it. The pre-edit copy of the strategy is in `temp/S1378/`.
