# Phase 01 — Exception Policy + Handler Fix

**Strategic spec:** [`../S0229_bugfix-smb-audio-metadata-browse-instability.md`](../S0229_bugfix-smb-audio-metadata-browse-instability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** —
**Blocks:** Phase 02
**Steps done:** 5 / 5
**Started:** —
**Completed:** —

---

## Objective

Apply three targeted fixes to `AudioMetadataLoader.kt`:

1. Wrap `trackGroupsFuture.get()` with `runInterruptible { }` so coroutine cancellation properly interrupts the blocking future, eliminating the `Handler on a dead thread` race.
2. Extend `shouldLogMetadataRetrieverFailureAsDebug()` to classify `EOFException` and `IOException` as expected partial-read misses, downgrading them from warning to debug.
3. Reduce semaphore from `Semaphore(3)` to `Semaphore(2)` to cut simultaneous fetch pressure during scroll-idle burst.

No parser change, no new public API, no DB migration, no UI change.

---

## Prerequisites

- `app_v2/src/main/java/com/sza/fastmediasorter/core/util/AudioMetadataLoader.kt` readable and 773 LOC (confirmed).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/util/AudioMetadataLoader.kt` | Modified | ≤ 780 LOC |

---

## Steps

### Step 01.1 — Backup AudioMetadataLoader.kt

**File:** `temp/`

**Action:**

> File is 773 LOC (> 500 LOC threshold). Create a timestamped backup before editing:
>
> ```powershell
> $ts = Get-Date -Format "yyyyMMdd_HHmmss"
> Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/core/util/AudioMetadataLoader.kt" `
>     "temp/AudioMetadataLoader_backup_$ts.kt"
> ```

**Verification:**

- `Test-Path "temp/AudioMetadataLoader_backup_*.kt"` returns `True`.
- expected: file exists | actual: verify.

**Status:** `[x]` done — 2026-05-16. Backup created at `temp/AudioMetadataLoader_backup_20260516_162321.kt`.

---

### Step 01.2 — Reduce semaphore to 2

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/AudioMetadataLoader.kt`

**Change:** Line 82.

Replace:
```kotlin
private val semaphore = Semaphore(3)
```
With:
```kotlin
// S0229: reduced from 3 → 2 to limit simultaneous SMB partial-read fetches during scroll-idle burst.
private val semaphore = Semaphore(2)
```

**Verification:**

- `Grep -Pattern "Semaphore\(2\)" AudioMetadataLoader.kt` → found.
- `Grep -Pattern "Semaphore\(3\)" AudioMetadataLoader.kt` → not found.
- expected: Semaphore(2) present, Semaphore(3) absent | actual: Semaphore(2) at line 84, Semaphore(3) absent. PASS.

**Status:** `[x]` done — 2026-05-16.

---

### Step 01.3 — Extend EOFException / IOException downgrade

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/AudioMetadataLoader.kt`

**Change:** `shouldLogMetadataRetrieverFailureAsDebug()` (~line 287).

Replace:
```kotlin
    private fun shouldLogMetadataRetrieverFailureAsDebug(filePath: String, throwable: Throwable): Boolean {
        if (!isPartialNetworkMetadataPath(filePath)) return false
        return throwableCauseChain(throwable).any {
            it.javaClass.simpleName == "UnrecognizedInputFormatException"
        }
    }
```
With:
```kotlin
    private fun shouldLogMetadataRetrieverFailureAsDebug(filePath: String, throwable: Throwable): Boolean {
        if (!isPartialNetworkMetadataPath(filePath)) return false
        // S0229: EOFException and IOException are expected outcomes when parsing a 64 KB partial
        // header — the truncated buffer makes a complete parse impossible by design. Treat them
        // as debug-level noise alongside UnrecognizedInputFormatException.
        return throwableCauseChain(throwable).any {
            it.javaClass.simpleName == "UnrecognizedInputFormatException" ||
                it is java.io.EOFException ||
                it is java.io.IOException
        }
    }
```

**Verification:**

- `Grep -Pattern "it is java.io.EOFException"` in file → found.
- `Grep -Pattern "it is java.io.IOException"` in file → found.
- expected: both patterns present | actual: `it is java.io.EOFException` at line 296, `it is java.io.IOException` at line 297. PASS.

**Status:** `[x]` done — 2026-05-16.

---

### Step 01.4 — Wrap trackGroupsFuture.get() with runInterruptible

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/AudioMetadataLoader.kt`

**Change:** `extractMetadataFromBytes()` (~line 429). Two edits needed:

**A — Add import** (after existing `kotlinx.coroutines.withContext` import):
```kotlin
import kotlinx.coroutines.runInterruptible
```

**B — Wrap the blocking future call** (~line 435):

Replace:
```kotlin
            val trackGroups = trackGroupsFuture.get(5, TimeUnit.SECONDS)
```
With:
```kotlin
            // S0229: runInterruptible ensures coroutine cancellation interrupts the blocking
            // future.get() call. Without this, a cancelled scope leaves MetadataRetriever's
            // internal handler running on a dead thread, producing "Handler on a dead thread".
            val trackGroups = runInterruptible { trackGroupsFuture.get(5, TimeUnit.SECONDS) }
```

**Verification:**

- `Grep -Pattern "runInterruptible"` in file → found (at least 2 occurrences: import + call site).
- `Grep -Pattern "trackGroupsFuture.get\(5, TimeUnit.SECONDS\)"` → not found as a bare call (must be inside runInterruptible).
- expected: runInterruptible present | actual: import at line 29, call site at line 445. PASS.

**Status:** `[x]` done — 2026-05-16.

---

### Step 01.5 — Build verification

**Action:**

> Run standard debug build:
> ```powershell
> .\build-debug.PS1
> ```

**Verification:**

- Build output contains `BUILD SUCCESSFUL`.
- No new compilation errors in `AudioMetadataLoader.kt`.
- expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL in 30s. PASS.

**Status:** `[x]` done — 2026-05-16.

---

## Phase Done Criteria

- [x] All 5 steps `[x] done`.
- [x] Build passes for `standardDebug`.
- [x] `Semaphore(2)` in place, `Semaphore(3)` absent.
- [x] `EOFException` + `IOException` in `shouldLogMetadataRetrieverFailureAsDebug()`.
- [x] `runInterruptible { }` wraps `trackGroupsFuture.get()`.

---

## Rollback Plan

Restore from `temp/AudioMetadataLoader_backup_*.kt` if needed.
