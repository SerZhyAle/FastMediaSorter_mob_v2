# Phase 01 — Retry offset fix

**Strategic spec:** [`../S0092_bugfix-sftp-range-read-retry-overflow.md`](../S0092_bugfix-sftp-range-read-retry-overflow.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** 2026-05-05
**Completed:** 2026-05-05

---

## Objective

Remove `skip(offset)` from the retry branch of shared SFTP range reads and restore direct offset-open semantics.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt` | Modified | ≤ 20 |

---

## Steps

### Step 01.1 — Make retry branch use direct offset-open

**Verification:**

- No `skip(offset)` remains in the retry branch of `readFileBytesRange()`.
- Retry branch uses the same direct offset-open strategy as primary branch.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Retry branch switched from `channel.get(remotePath)` + `skip(offset)` to `channel.get(remotePath, null, offset)`.

### Step 01.2 — Compile touched Kotlin slice

**Verification:**

- Compile command exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — `./gradlew.bat :app_v2:compileStandardDebugKotlin` → `BUILD SUCCESSFUL in 4s`.