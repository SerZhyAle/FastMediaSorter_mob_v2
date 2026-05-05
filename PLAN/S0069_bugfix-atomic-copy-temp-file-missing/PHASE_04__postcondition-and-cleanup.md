# Phase 04 — Postcondition and Cleanup

**Strategic spec:** [`../S0069_bugfix-atomic-copy-temp-file-missing.md`](../S0069_bugfix-atomic-copy-temp-file-missing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-03
**Completed:** 2026-05-03

---

## Objective

Implement the cheap temp-file post-condition and collapse cleanup into a single non-racing contour inside the atomic orchestrator.

---

## Prerequisites

- [ ] Phase 03 ✅ Done.
- [ ] `AtomicFileOperationStrategy` already has explicit outcome branches.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt` | Modified | ≤ 420 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/TempFileNamingStrategy.kt` | Read-only audit | 120 current LOC |

---

## Steps

### Step 04.1 — Add a cheap post-condition helper

**Files:** `AtomicFileOperationStrategy.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a private helper such as `verifyTempPostCondition(...)` in `AtomicFileOperationStrategy.kt`. For local temp paths, check `exists + length`; for any reopened non-local scope, keep the check cheap (`exists` only). Do not read the full file back.

**Verification:**

- `Grep -n "verifyTempPostCondition" "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt"` matches once.
- `Grep -n "length\(|exists\(" "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt"` returns hits inside the new helper region.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 - Verification 3/3 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt (+25 LOC). Cheap `verifyTempPostCondition` helper added with local `exists + length` and non-local `exists` checks.

---

### Step 04.2 — Emit structured invariant diagnostics

**Files:** `AtomicFileOperationStrategy.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Replace the bare `Temp file doesn't exist after copy!` branch with a structured invariant marker. Include `source`, `temp`, and `destination` in the log line. Use a stable marker string such as `temp-missing-invariant` so the reproducer is grep-friendly.

**Verification:**

- `Grep -n "temp-missing-invariant" "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt"` matches once.
- `Grep -n "source=.*temp=.*destination=" "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt"` returns at least one hit in the invariant log line.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 - Verification 3/3 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt (+6 LOC). Invariant path now emits `temp-missing-invariant` with `source`, `temp`, and `destination` fields.

---

### Step 04.3 — Collapse cleanup to one decision point

**Files:** `AtomicFileOperationStrategy.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Refactor `copyFile` so temp cleanup is decided in one place after the outcome is known. Keep the actual delete in the existing `NonCancellable` helper. Remove duplicated inline cleanup call sites from success/failure/cancel branches. Final rename must happen only after the post-condition passes.

**Verification:**

- `Grep -n "cleanupTempFile\(" "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt"` returns exactly two hits (one invocation + the helper definition).
- `Grep -n "renamePath\(" "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt"` still matches once in the success-finalisation path.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 - Verification 4/4 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt (+19 LOC / -17 LOC). Temp cleanup is now decided once in `completeCopyOutcome`, and the only `renamePath(...)` call-site remains inside success finalisation.

---

### Step 04.4 — Compile gate

**Files:** none
**Depends on:** Step 04.3

**Prompt for developer:**

> Run:
>
> ```powershell
> ./gradlew.bat :app_v2:compileStandardDebugKotlin
> ```
>
> The atomic wrapper must compile after the post-condition helper and single cleanup contour are in place.

**Verification:**

- `./gradlew.bat :app_v2:compileStandardDebugKotlin` exits with code 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 - Verification 1/1 PASS. Compile gate satisfied by user-confirmed successful build after Step 04.3.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Temp verification is explicit and cheap.
- [x] `temp-missing-invariant` marker exists.
- [x] Cleanup is triggered from one decision point only.
- [x] `compileStandardDebugKotlin` passes.

---

## Handoff Notes to Next Phase

Phase 05 adds automated coverage and audits the two UI entrypoints that issue `User cancelled network share copy`.

---

## Rollback Plan

Revert `AtomicFileOperationStrategy.kt` to the pre-phase commit and re-run the compile gate.
