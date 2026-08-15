# Phase 03 - SAF operation strategy

**Strategic spec:** [`../S1378_sd-card-external-storage-support.md`](../S1378_sd-card-external-storage-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04, Phase 05
**Steps done:** 5 / 5
**Started:** 2026-08-05
**Completed:** 2026-08-05

---

## Objective

Give document-tree addresses their own `FileOperationStrategy` implementation covering single-file operations, and route them to it - replacing today's situation where a `content://` path is dispatched to the local strategy that declares it does not support it.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] Working tree is clean or on a feature branch.
- [x] `temp/CODE.LOCK` acquired before the first source edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SafOperationStrategy.kt` | New | ≤ 450 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/DirectoryStrategyModule.kt` | Modified | ≤ 40 added |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandler.kt` | Modified | ≤ 675 |
| `app_v2/src/main/java/com/sza/fastmediasorter/utils/SafHelper.kt` | Modified | ≤ 340 |

> Only one helper was missing: `guessMimeType` existed but was `private`, and `DocumentFile.createFile` needs a MIME type up front. Made public rather than re-derived per call site - a wrong guess is sticky, the provider stores it on the document.
| `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/strategy/SafOperationStrategyTest.kt` | New | ≤ 220 |

> `UnifiedFileOperationHandler.kt` (653 LOC) crosses the 500-LOC line - Step 03.1 backs it up before any edit.

> Budget raised 660 -> 675 during execution. The file measured 666 LOC **before** this phase touched it - it grew past the plan's number under other tickets between the plan being written (653) and being executed. This phase adds two lines, taking it to 668. Well inside Rule 2's 1500-LOC ceiling; the phase budget was simply stale.

---

## Steps

### Step 03.1 - Back up the handler

**Files:** `temp/S1378/`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `UnifiedFileOperationHandler.kt` into `temp/S1378/` with a timestamped name before editing it.

**Why:**

not stated in strategic spec - CLAUDE.md Rule 5 requires a timestamped backup before editing any file above 500 LOC.

**Verification:**

- `Glob` - `temp/S1378/UnifiedFileOperationHandler*.kt` exists.

**Status:** `[x]` done - `temp/S1378/UnifiedFileOperationHandler.kt.20260805-103400.bak` (26221 B); expected: exists | actual: exists.

---

### Step 03.2 - Implement `SafOperationStrategy` for single-file operations

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SafOperationStrategy.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Implement `FileOperationStrategy` for `content://` document-tree addresses over `DocumentFile` and `ContentResolver`: `copyFile`, `moveFile`, `deleteFile`, `exists`, `createDirectory`, `createTextFile`, `writeFile`, `readFile`, `listFiles`. `supportsProtocol` answers true only for a path starting with `content:`, `getProtocolName` returns `saf`. Reuse the existing helpers in `SafHelper` for tree-root resolution, child creation, delete and rename rather than duplicating that logic; extend `SafHelper` where a helper is missing. Report byte progress through the existing `ByteProgressCallback` on copy. Run I/O on `Dispatchers.IO`. Every failure returns `Result.failure` with a specific exception - no empty catch, no silent success.

**Why:**

Strategic ADR-2 makes the document-tree route the primary path that must cover every operation, and the research artifact records that `LocalOperationStrategy.supportsProtocol` explicitly excludes `content:/` while the dispatcher still routes such paths to it - so today the local strategy is asked to do what it declares it cannot.

**Verification:**

- `Grep` - `class SafOperationStrategy` matches exactly once.
- `Grep` - `getProtocolName` returns `"saf"` in that file.
- `Grep` - `Log\.d\(` returns zero hits in the file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done - `class SafOperationStrategy` expected: 1 | actual: 1; `getProtocolName(): String = "saf"` expected: present | actual: present; `Log.d(` expected: 0 | actual: 0; compile expected: 0 | actual: 0.

> Ten operations implemented, not the nine listed: `isDirectory` is included because the interface's default `listEntries` calls it per child. Rationale in the handoff notes.

---

### Step 03.3 - Register the strategy in Hilt

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/di/DirectoryStrategyModule.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add an `@Provides @IntoMap @StringKey("saf")` entry supplying `SafOperationStrategy`, matching the shape of the existing `local` and `smb` entries in the same module.

**Why:**

not stated in strategic spec - the handler resolves strategies from an injected map keyed by protocol, so an unregistered strategy is unreachable.

**Verification:**

- `Grep` - `@StringKey("saf")` matches exactly once in `DirectoryStrategyModule.kt`.
- `.\a.ps1 d` succeeds - a Kotlin compile check does not validate the Hilt graph, and a missing binding only fails at kapt.

**Status:** `[x]` done - `@StringKey("saf")` expected: 1 | actual: 1; full debug build expected: 0 | actual: 0.

> Pre-existing `ImportOrdering` in this module was fixed in the same edit (`data.local.staging.*` sat after `data.transfer.strategy.*`); without it the scoped detekt gate refuses the close as soon as the file enters the changed set.

---

### Step 03.4 - Route document-tree paths to the new key

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandler.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> In `getProtocolKey`, map a path starting with `content:` to `saf` before the `local` fallback. Leave the other protocol prefixes and the fallback itself unchanged.

**Why:**

Strategic §5.1 pillar 3 requires the access route to be chosen once at dispatch instead of re-tested inside each executor, which is what this single mapping establishes for every operation that follows.

**Verification:**

- `Grep` - `"saf"` appears inside `getProtocolKey`.
- `Grep` - `startsWith("content:")` appears in `getProtocolKey`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done - `UnifiedFileOperationHandler.kt:641` reads `path.startsWith("content:") -> "saf"`, placed above the `local` fallback; compile expected: 0 | actual: 0.

---

### Step 03.5 - Cover routing and single-file operations with tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/transfer/strategy/SafOperationStrategyTest.kt`
**Depends on:** Step 03.4

**Prompt for developer:**

> Add unit tests asserting: `supportsProtocol` accepts a `content://` path and rejects a filesystem path, an `smb://` path and a `cloud://` path; `getProtocolName` returns `saf`; a copy of a missing source returns `Result.failure` rather than a silent success. Use mockk for `ContentResolver` as the existing repository tests do.

**Why:**

Strategic §2 goal 7 requires the whole operation set to work on a device without all-files access, so the routing that selects this strategy is load-bearing and must fail loudly in tests rather than at a user's first move.

**Verification:**

- `.\a.ps1 fu` - `SafOperationStrategyTest` passes; record `expected: PASS | actual: <result>`.

**Status:** `[x]` done - `SafOperationStrategyTest` expected: PASS | actual: PASS (exit 0), six tests. Run scoped with `check-standard-fast.ps1 -Mode Unit -Tests ..` rather than the whole `fu` suite, which S1244 records as prone to OOM-truncating mid-run.

> Robolectric, not plain JVM: `SafHelper` parses every address through `android.net.Uri`. The `ContentResolver` stays a mockk, so no real provider is touched. Two tests beyond the step's list cover the refusals the audit introduced (`deleteFile` and `exists` on a non-`content:` path).

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 db` expected: 0 | actual: 0, re-run after the detekt restructuring so the verdict covers the final state.
- [x] `Grep` for `TODO(phase-03)` returns zero hits - expected: 0 | actual: 0.
- [x] Dev log entry added for the phase - written by `post-change.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - 2428 records (was 2427; `SafOperationStrategy` is the new class).
- [x] Phase-boundary audit run - one P1 and two P2 found and fixed inside the phase, none unresolved.

Closure: `post-change: PASS WITH ADVISORIES (1)`, exit 0, `assert-detekt: PASS [scoped]`. The advisory is `detekt-preflight` on `UnifiedFileOperationHandler.kt:525,563` - two pre-existing over-length lines that this phase did not touch, surfaced by the two-line shift. Same artefact as Phase 02.

Line budgets: `SafOperationStrategy` 347/450, its test 95/220, `SafHelper` 320/340, `DirectoryStrategyModule` +11 lines of the 40 allowed.

---

## Phase-boundary audit (2026-08-05)

Triggers fired: new long-lived helper class (Layer 1), new DI entry (Layer 1), stream ownership on a file-operation path (Layer 3), suspend I/O (Layer 2).

**P1 - destination stream leaked on an unreadable source. Found and fixed inside this phase.**

`copyFile` originally opened the destination first and the source second. The single most common failure of this strategy - a revoked grant or a vanished document making the source unopenable - therefore threw with the destination stream already open and never closed, and on the SAF path that stream is a provider-held file descriptor. Reordered so the source opens first and the destination is opened inside its `use` block; a source failure now closes nothing because nothing was opened.

**P2 - the two halves of `moveFile` disagreed about what a path may be. Fixed.**

`copyFile` deliberately accepts a local path on either end (that is how `BaseFileOperationHandler` performs a cross-protocol transfer: download to a temp file, upload from it). `deleteFile` deliberately refuses anything that is not a `content:` address. `moveFile` called both, so a move with a local source would have copied successfully and then reported "copied but failed to delete source" - a half-success, the worst failure shape available. Not reachable today (`moveToTrash` picks the strategy from the source path, so the source always matches), which is exactly why it would have survived review until a future caller hit it. Source removal now goes through a private `removeTransferred` that handles both address kinds; the public `deleteFile` keeps its strict contract.

**P2 - `exists` answered `false` for a local path. Fixed.**

A confident "no" about a path that does in fact exist is how a dispatcher misroute stays invisible - the caller concludes the file is gone and acts on it. `exists` now refuses a non-`content:` path the way `deleteFile` does. The asymmetry with `copyFile` is deliberate and documented: a transfer has two ends, a query has one.

Cleared, no action needed:

- **Threading (Layer 2/5).** Every operation body is inside `withContext(Dispatchers.IO)`; `ByteProgressCallback.onProgress` is itself `suspend`, which the first build caught and which is why the copy loop is a `suspend` function.
- **Streams elsewhere (Layer 3).** `createTextFile`, `writeFile` and `readFile` all use `?.use {}`. The one remaining sharp edge is that `openDestination` returns an already-open stream inside a `Result`, which the type system cannot force a caller to close - it has exactly one caller, `copyFile`, which always wraps it. Phase 04 must keep that invariant if it reuses the helper.
- **DI (Layer 1).** `@IntoMap @StringKey("saf")` in the existing module, no new scope or qualifier. The full `standard debug` build is what proves the map entry resolves.

The first closure attempt failed the scoped detekt gate on `ReturnCount` in four of the new private helpers (limit 2, they had 3-5). That is a structural note for Phase 04 rather than a formatting nit: a `Result`-returning helper written in the natural guard style - one early return per failure mode - crosses the limit almost immediately, and this file has one failure mode per SAF call that can answer null. The shape that satisfies the gate without hiding a branch is a single `?: return` for the one unavoidable precondition plus a terminal `when`/`?:` expression; `openDestination` was additionally split so the local-destination branch became its own function. Phase 04's directory operations have strictly more failure modes and will hit this on the first draft.

---

## Handoff Notes to Next Phase

Any `content://` address now reaches `SafOperationStrategy`.

What Phase 04 inherits:

- Implemented here beyond the phase's original list: `isDirectory`. The interface's default `listEntries` calls it per child, so leaving it out would have made a working `listFiles` produce a failing `listEntries` - a worse trap than a missing operation. The four *mutating* directory members (`deleteDirectory`, `renameDirectory`, `copyDirectory`, `getDirectoryInfo`) still fall through to the defaults and are Phase 04's work.
- The path contract is written at the top of the class and is the thing to read first: a `content:` document URI, or such a URI with `/<displayName>` appended for a target that does not exist yet. SAF cannot write at an arbitrary URI - a document is always created inside its parent - so any new operation resolves through `resolveExisting` or `resolveSlot`, never by string-building a URI.
- `createTextFile` creates the document immediately, unlike `LocalOperationStrategy`, which defers creation to the editor's first Save (S0189) so a cancelled note leaves nothing behind. SAF has no stable "path that will exist later" to hand back, because the provider assigns the document id. A cancelled new note on a removable volume therefore leaves an empty file. Worth deciding deliberately in Phase 06 rather than discovering on a device.

---

## Rollback Plan

Revert the phase commit - no schema and no user-facing surface changed. The pre-edit copy of the handler is in `temp/S1378/`.
