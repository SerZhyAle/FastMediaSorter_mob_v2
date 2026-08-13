# Phase 02 — Permission Contract

**Strategic spec:** [`../S0035_android17-local-network-permission.md`](../S0035_android17-local-network-permission.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Completed:** 2026-05-04
**Depends on:** Phase 01 continue-path
**Blocks:** Phase 03, Phase 04, Phase 05, Phase 06
**Steps done:** 4 / 4
**Started:** —
**Completed:** —

---

## Objective

Introduce a typed `local-network permission denied` contract that survives classifier/wrapper layers and can be routed separately from generic timeout, auth, or host-unreachable failures.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done and the continue-path was selected.
- [ ] The compile gate from Phase 01 passed.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkExceptions.kt` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkErrorClassifier.kt` | Modified | 160 current LOC |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SmbOperationsUseCase.kt` | Modified | n/a |

---

## Steps

### Step 02.1 — Add a typed permission exception

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkExceptions.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add `LocalNetworkPermissionDeniedException` as a dedicated `NetworkException` subtype. Keep it distinct from `NetworkAccessDeniedException`; this ticket needs a separate UI path and must not piggyback on auth/ACL failures.

**Verification:**

- `Grep` — `class LocalNetworkPermissionDeniedException` matches exactly once in `app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkExceptions.kt`.
- `Grep` — `: NetworkException` matches on the same class declaration line.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkExceptions.kt (+8 LOC). Dev log recorded.

---

### Step 02.2 — Teach the classifier to preserve and create the new type

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkErrorClassifier.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Update `NetworkErrorClassifier.classify()` so it:
>
> - returns `LocalNetworkPermissionDeniedException` unchanged when it is already present;
> - maps `SecurityException`, `ACCESS_LOCAL_NETWORK`, `local network permission`, or equivalent OS-level denial messages to `LocalNetworkPermissionDeniedException` before the generic `NetworkAccessDeniedException` branches run.

**Verification:**

- `Grep` — `LocalNetworkPermissionDeniedException` appears in `app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkErrorClassifier.kt`.
- `Grep` — `SecurityException` appears in the classifier.
- `Grep` — `ACCESS_LOCAL_NETWORK|local network permission` appears in the classifier heuristics.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkErrorClassifier.kt (+18 LOC). Dev log recorded.

---

### Step 02.3 — Preserve the typed exception through use-case wrappers

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SmbOperationsUseCase.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Audit every `Result.failure(...)` wrapper in `SmbOperationsUseCase` that handles SMB / SFTP / FTP operations. When the underlying cause is `LocalNetworkPermissionDeniedException`, preserve that exact type instead of wrapping it in a new generic `Exception(result.message, result.exception)`. Apply this to at least `listShares`, `scanMediaFiles`, and any connection test path that still crosses the same wrapper surface.

**Verification:**

- `Grep` — `LocalNetworkPermissionDeniedException` appears in `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SmbOperationsUseCase.kt`.
- `Grep` — `Result.failure` still appears in that file.
- `Grep` — `Exception(result.message, result.exception)` count is reduced or guarded so the new permission type is not swallowed.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS. Files: SmbOperationsUseCase.kt (+4 import LOC, 3 guards). Dev log recorded.

---

### Step 02.4 — Re-run the narrow compile gate

**Files:** none modified — verification only
**Depends on:** Step 02.3

**Prompt for developer:**

> Run:
>
> ```powershell
> ./gradlew.bat :app_v2:compileStandardDebugKotlin
> ```
>
> Do not start any UI wiring until the new exception type and classifier compile cleanly.

**Verification:**

- `Command` — `./gradlew.bat :app_v2:compileStandardDebugKotlin` exits with code `0`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — BUILD SUCCESSFUL. Also fixed NetworkErrorMessageMapper exhaustive when. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every Step 02.* above is `[x] done`.
- [ ] `LocalNetworkPermissionDeniedException` exists as a first-class type.
- [ ] The classifier can create and preserve the new type.
- [ ] `SmbOperationsUseCase` no longer swallows the new type behind generic `Exception(...)` wrappers.

---

## Handoff Notes to Next Phase

Phase 03 uses the typed contract to present user-facing copy in Settings and shared rationale flows. Do not add strings or dialogs before the classifier contract exists.

---

## Rollback Plan

Revert the new exception subtype and classifier/use-case updates together. Partial rollback is not allowed because it would leave dead routing branches.