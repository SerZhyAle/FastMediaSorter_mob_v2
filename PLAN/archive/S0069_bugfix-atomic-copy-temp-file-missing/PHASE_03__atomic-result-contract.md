# Phase 03 — Atomic Result Contract

**Strategic spec:** [`../S0069_bugfix-atomic-copy-temp-file-missing.md`](../S0069_bugfix-atomic-copy-temp-file-missing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04, Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-03
**Completed:** 2026-05-03

---

## Objective

Make `AtomicFileOperationStrategy` branch explicitly on `success`, `cancelled`, and `failed`, and keep temp-path decisions inside the atomic layer only.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] SMB cancellation now reaches the atomic layer as `CancellationException`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt` | Modified | ≤ 380 |

---

## Steps

### Step 03.1 — Introduce explicit atomic outcome model

**Files:** `AtomicFileOperationStrategy.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a private sealed outcome model inside `AtomicFileOperationStrategy.kt` for the post-delegate stage. Use three explicit branches only: `Success`, `Cancelled`, and `Failed`. The model may be a `private sealed interface` / `sealed class` plus small data carriers. Do not infer cancellation from generic `Exception.message`.

**Verification:**

- `Grep -n "sealed interface .*Outcome|sealed class .*Outcome" "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt"` matches once.
- `Grep -n "Cancelled|Failed|Success" "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt"` returns the three outcome names in the new model.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 - Verification 2/2 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt (+6 LOC). Explicit `AtomicCopyOutcome` model added with `Success`, `Cancelled`, and `Failed` branches.

---

### Step 03.2 — Add explicit cancellation branch in `copyFile`

**Files:** `AtomicFileOperationStrategy.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Refactor `copyFile` so `CancellationException` is handled in an explicit branch before the generic exception path. Cancellation must never fall through to `Unexpected error during atomic copy`. The cancellation branch may log at `Timber.i` / `Timber.d`, but not as `Timber.e`.

**Verification:**

- `Grep -n "catch \(e: CancellationException\)" "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt"` matches at least once.
- `Grep -n "Unexpected error during atomic copy" "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt"` still matches once in the generic failure branch only.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 - Verification 2/2 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt (+4 LOC). `copyFile` now handles `CancellationException` explicitly before the generic unexpected-error branch.

---

### Step 03.3 — Extract per-outcome handlers inside atomic layer

**Files:** `AtomicFileOperationStrategy.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Extract the post-copy branches into explicit helper methods inside `AtomicFileOperationStrategy.kt`. At minimum, separate:
>
> - success finalisation (`rename` path only),
> - cancelled handling,
> - failed handling.
>
> Keep `tempDestination` ownership inside `AtomicFileOperationStrategy`; do not let SMB delegate code decide cleanup or rename.

**Verification:**

- `Grep -n "finali[sz]e|handleCancelled|handleFailed" "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt"` returns helper method hits for the three branches.
- `Grep -n "TempFileNamingStrategy.getTempPath" "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt"` still matches once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 - Verification 2/2 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt (+47 LOC). Success, cancelled, and failed branches are now routed through explicit helper methods inside the atomic layer.

---

### Step 03.4 — Compile gate

**Files:** none
**Depends on:** Step 03.3

**Prompt for developer:**

> Run the narrow compile gate again:
>
> ```powershell
> ./gradlew.bat :app_v2:compileStandardDebugKotlin
> ```
>
> Do not continue until the orchestrator compiles after the explicit outcome split.

**Verification:**

- `./gradlew.bat :app_v2:compileStandardDebugKotlin` exits with code 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 - Verification 1/1 PASS. Compile gate satisfied by user-confirmed successful build after Step 03.3.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `AtomicFileOperationStrategy.copyFile` has explicit `success / cancelled / failed` branches.
- [x] Cancellation no longer falls through to the generic `Unexpected error` path.
- [x] `compileStandardDebugKotlin` passes.

---

## Handoff Notes to Next Phase

After this phase, the atomic orchestrator owns the decision tree. Phase 04 can tighten the post-condition and single cleanup contour without mixing it with SMB-specific cancellation propagation.

---

## Rollback Plan

Revert `AtomicFileOperationStrategy.kt` to the pre-phase version and re-run the compile gate.
