# Phase 04 - MainThreadIoDetector

**Strategic spec:** [`../S1195_lint-strict-but-never-run-72-unbaselined-errors.md`](../S1195_lint-strict-but-never-run-72-unbaselined-errors.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 07
**Steps done:** 4 / 4

---

## Objective

Two findings, one of each kind. Both were verified by reading the source, not the report:

- **`PhotoVideoStandaloneActivity.kt:400` is a false positive.** The flagged `it.delete()` sits in `stageBitmapForPrint`, which is invoked at line 374 as `val staged = withContext(Dispatchers.IO) { stageBitmapForPrint(bitmap) }`. The call is already confined; the detector cannot see it because `visitMethodCall` walks UAST parents from the call node and stops at the enclosing function body. Analysis is intra-procedural.
- **`PrintDispatchActivity.kt:141` is a true positive.** `dispatchText()` is invoked from `onPostResume()` at line 71 with no coroutine anywhere in the chain, so `file.readText()` runs on the main thread. P1 by the CLAUDE.md §13 taxonomy: main-thread disk I/O.

The rule is narrow and its shape is right. It recognises only a literal enclosing `withContext(..IO..)` (`MainThreadIoDetector.kt:41-47`), so three further confinement forms would also misfire: `launch(Dispatchers.IO)`, a `suspend` function whose caller confines it, and a `@WorkerThread`-annotated function.

---

## Prerequisites

- [x] Phase 01 done.
- [x] `temp/CODE.LOCK` acquired.
- [x] Read `MainThreadIoDetector.kt` in full (72 LOC).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `lint-rules/src/main/java/com/sza/fastmediasorter/lint/MainThreadIoDetector.kt` | Modified | ≤ 200 |
| `lint-rules/src/test/java/com/sza/fastmediasorter/lint/CustomLintRulesTest.kt` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/print/PrintDispatchActivity.kt` | Modified | ≤ 400 |

---

## Steps

### Step 04.1 - Recognise the confinement forms the rule misses

**Files:** `lint-rules/src/main/java/com/sza/fastmediasorter/lint/MainThreadIoDetector.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Extend the confinement walk at lines 38-49 beyond a literal `withContext`. Treat the call as confined when any enclosing element is one of:
> - `withContext(..)` whose first argument resolves to `Dispatchers.IO` or `Dispatchers.Default` - keep the existing case, but resolve the argument rather than string-matching `asRenderString().contains("IO")`, so a `Dispatchers.Main` argument containing the letters "IO" in a variable name cannot pass;
> - a coroutine builder - `launch(..)`, `async(..)` - with an IO or Default dispatcher argument;
> - a function annotated `@WorkerThread`;
> - a `suspend` function - a suspend function cannot be assumed main-thread, and reporting inside one is the detector claiming knowledge it does not have.
>
> Also add the containing-function escape that fixes the `PhotoVideoStandaloneActivity` case: when the enclosing method is `private` and **every** call site within the same file is itself confined, do not report. Keep this deliberately narrow - same file, private function - so the rule stays cheap and predictable rather than attempting a call graph.
>
> If the single-file call-site check turns out to be more than a contained change, prefer the simpler correct behaviour: report at the *call site* rather than inside the callee. Record which option was taken and why.

**Verification:**

- `.\a.ps1 flr` green.
- `Grep` - `WorkerThread` present in the detector.
- `Grep` - `asRenderString` no longer used for dispatcher matching.

**Status:** `[x]` done

---

### Step 04.2 - Test both directions

**Files:** `lint-rules/src/test/java/com/sza/fastmediasorter/lint/CustomLintRulesTest.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Extend `testMainThreadIoDetector`. False positives that must be clean, one per case: a private helper doing `File.delete()` called only from `withContext(Dispatchers.IO) { .. }` in the same class (models `PhotoVideoStandaloneActivity`); a `readText()` inside `lifecycleScope.launch(Dispatchers.IO) { .. }`; a `readText()` inside a `suspend fun`; a `readText()` in a `@WorkerThread` function.
>
> True positives that must still fire: the existing `MyViewModel.loadData` case; and a `readText()` called from an Activity's `onPostResume()` with no coroutine (models `PrintDispatchActivity`), asserting exactly one `MainThreadIo` error.

**Verification:**

- `.\a.ps1 flr` green with 4 new `expectClean()` cases and 1 new positive case.

**Status:** `[x]` done

---

### Step 04.3 - Fix the one true positive

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/print/PrintDispatchActivity.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Move the blocking read in `dispatchText()` off the main thread. The whole function runs from `onPostResume()`, so the read, the HTML escaping that follows it, and the failure path all need restructuring rather than a `withContext` around one line: read and escape on `Dispatchers.IO`, resume on the main thread only to hand the result to the WebView. Use `lifecycleScope.launch` so the work is cancelled with the Activity, and keep the existing `fallbackToShareOrFail` behaviour on a null result exactly as it is - the guard-clause structure and its `@Suppress("ReturnCount", "TooGenericExceptionCaught")` are deliberate and documented.
>
> Check `dispatchPdf()` and `dispatchImage()` in the same file for the same shape while you are there; if either does blocking I/O from the same `onPostResume` path, fix it in this step and say so - it is the same defect, and the detector did not report it only because those paths do not use the nine method names it watches.

**Verification:**

- `.\a.ps1 fk` passes.
- `Grep` - no `file.readText()` reachable from `onPostResume` without a dispatcher hop.
- Targeted lint or `.\a.ps1 flr` shows `PrintDispatchActivity` clean of `MainThreadIo`.

**Status:** `[x]` done

---

### Step 04.4 - Re-measure

**Files:** none - measurement only
**Depends on:** Step 04.3

**Prompt for developer:**

> Full `:app_v2:lintStandardDebug` under `temp/BUILD.LOCK`, output to `temp/S1195/phase04-lint.log`. Expected: `MainThreadIo` 0 live findings - one false positive removed by the detector fix, one true positive removed by the code fix. Record the count and any survivor.

**Verification:**

- `expected: 0 live MainThreadIo | actual: <N>` recorded here with the log path.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `.\a.ps1 flr` green and `.\a.ps1 fk` green, both cited.
- [x] The `PrintDispatchActivity` main-thread read is gone, proven by a lint run and not by inspection.
- [x] `pwsh -NoProfile -File scripts/post-change.ps1 -ChangeType Kotlin -ScopeToFile ..` closure run.
- [x] Dev log entry added.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Suite green at `expected: 25/25 | actual: 25/25` when this phase closed (`temp/S1195/phase04-run.log`), and 27/27 after Phase 02's two follow-up cases landed (`temp/S1195/phase02-refine.log`). `.\a.ps1 fk` `BUILD SUCCESSFUL in 37s`, no new warnings (`temp/S1195/phase04-fk.log`).

**Step 04.1 took the precise option, not the fallback.** `isConfinedByEveryCaller` resolves every call site of the enclosing function within the same `UFile` and requires all of them to be confined, gated on the function being `private`. Reporting at the call site instead was not needed. Confinement is now recognised in four forms: `withContext` / `launch` / `async` with a resolved `Dispatchers.IO` or `Dispatchers.Default`, a `@WorkerThread` function, and any `suspend` function.

Dispatcher matching resolves the argument to its declaring class rather than rendering it to text, so `Dispatchers.Main` can no longer be mistaken for IO. Covered by `testMainThreadIoDetectorRejectsMainDispatcher`.

A note for whoever writes the next confinement test: **a `suspend` enclosing function is itself a confinement**, so it masks any negative case placed inside one. The Main-dispatcher rejection case had to be rewritten to use `launch(Dispatchers.Main)` from a non-suspend function to test what it claims to test.

**`dispatchImage()` needed the same fix and got it.** `BitmapFactory.decodeFile` reads and decodes the whole file, and ran on the main thread straight out of `onPostResume()` - the identical defect to the text path, invisible to the rule only because `decodeFile` is not one of the nine method names it watches. Both paths now read on `Dispatchers.IO` inside `lifecycleScope.launch` and resume on the main thread only to touch the WebView / PrintHelper. `dispatchPdf()` was checked and needs nothing: it only calls `file.exists()`, an O(1) stat, and hands the file to `PdfPrintDocumentAdapter`, which already copies on its own `adapterScope` (`Dispatchers.IO + SupervisorJob`).

The restructuring split two functions into helpers, so the `@Suppress` sets moved with the code they justify: `dispatchText` / `dispatchImage` keep only `ReturnCount` for the missing-file guard clause, `renderTextForPrint` carries `ReturnCount, TooGenericExceptionCaught` for the WebView construction, and `decodeBitmapForPrint` carries `TooGenericExceptionCaught` for the decode.

### Step 04.4 measurement

`expected: 0 live MainThreadIo | actual: 7`, and the gap is fully explained. Log `temp/S1195/phase04-lint-final.log`.

**Both findings this phase targeted are gone.** `PhotoVideoStandaloneActivity:400` no longer fires - the caller-confinement escape works on real code. `PrintDispatchActivity:141` no longer fires - the code fix works.

The 7 live findings are a **different set of files**, and they are baseline staleness rather than new defects: `IntegrationTestViewModel.kt` (1) and `ReceiveShareActivity.kt` (6). Those are exactly two of the four files already carrying `MainThreadIo` baseline entries. Rewriting the detector changed its message text, and a lint baseline entry matches on message, so previously-filtered findings stopped matching and resurfaced.

Two of the four baselined files - `CameraCaptureActivity.kt` and `DebugActivity.kt` - did **not** resurface, which is a useful signal on its own: the new confinement forms correctly recognise those call sites as off the main thread.

Phase 07 must re-triage the 7 on their merits. They are P1-category (main-thread disk I/O) per §6.2, so they should not simply be re-baselined.

---

## Rollback Plan

Detector and tests revert together. The `PrintDispatchActivity` change is user-visible under failure conditions only (a slow read no longer blocks the frame), so revert it separately if the print flow regresses.
