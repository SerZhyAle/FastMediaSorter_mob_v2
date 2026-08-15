# Phase F2 — Write-side guard

**Ticket:** S0139
**Phase:** F2
**Goal:** Detect future regressions: any code path that persists an SMB credential with empty `shareName` raises an immediate visible warning. Non-throwing — defense-in-depth, not a hard gate (some legacy import flows legitimately receive credentials without share metadata).

---

## Steps

- [ ] Add a private helper `warnIfEmptyShareName` in `NetworkCredentialsRepositoryImpl`.
  - **File:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/NetworkCredentialsRepositoryImpl.kt`
  - **Signature:** `private fun warnIfEmptyShareName(entity: NetworkCredentialsEntity, op: String)`
  - **Body:**
    ```kotlin
    if (entity.type.equals("SMB", ignoreCase = true) && entity.shareName.isNullOrEmpty()) {
        Timber.w(
            "S0139: SMB credential persisted with empty shareName " +
                "(op=$op, server='${entity.server}', credentialId='${entity.credentialId}')"
        )
    }
    ```
  - **Why a warning, not throw:**
    - Strategic spec §2 non-goals state self-heal stays as defense-in-depth — shipping a throw could break legitimate import flows that never carried `shareName` in their source artefact.
    - Field session evidence shows the bug is rare and silently masked today; a warning is enough to attribute future occurrences.
- [ ] Call the helper from both write paths in the same file.
  - In `override suspend fun insert(credentials: NetworkCredentialsEntity): Long` — first line: `warnIfEmptyShareName(credentials, op = "insert")`.
  - In `override suspend fun update(credentials: NetworkCredentialsEntity)` — first line: `warnIfEmptyShareName(credentials, op = "update")`.
  - **Verification:** Both functions log the warning when a SMB credential with empty/null `shareName` is passed; no behavioural change otherwise.
- [ ] Catalogue sync: `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` then `render.ps1`.

## Out of scope

- No similar guard for `domain` or `port` — current ticket is specifically about `shareName`.
- No throw / `require` — non-blocking by design.
- No instrumentation in DAO directly — all writes funnel through the repository.

## Verification predicates

- Grepping for `S0139:` in changed files produces exactly two occurrences: one in `BackfillSmbCredentialShareNameUseCase` (entry tag — phase F1), one in `warnIfEmptyShareName`.
- Inserting a SMB credential with empty `shareName` (debug seed path) emits a single `W/Timber S0139: SMB credential persisted ..` line in logcat.
- `git diff` for `NetworkCredentialsRepositoryImpl.kt` shows: helper definition + two call-site additions; no other changes.
