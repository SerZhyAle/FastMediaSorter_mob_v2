# Phase F1 — One-shot startup backfill

**Ticket:** S0139
**Phase:** F1
**Goal:** Fill empty `shareName` for existing SMB credentials by parsing the corresponding `MediaResource.path` once per install/upgrade. Idempotent; gated by SharedPreferences flag.

---

## Steps

- [ ] Add new use-case `BackfillSmbCredentialShareNameUseCase` under `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackfillSmbCredentialShareNameUseCase.kt`.
  - **Class signature:** `class BackfillSmbCredentialShareNameUseCase @Inject constructor(...)`
  - **Constructor injection:** `NetworkCredentialsRepository`, `ResourceRepository`, `@ApplicationContext context: Context`.
  - **Public API:** `suspend operator fun invoke(): BackfillResult` returning a small data class `data class BackfillResult(val scanned: Int, val updated: Int, val skipped: Int)`.
  - **Logic:**
    - SharedPreferences `s0139_backfill` (mode `MODE_PRIVATE`), key `smb_share_name_backfill_v1_done` (Boolean). If true → return `BackfillResult(0, 0, 0)`.
    - Insert one Timber tag at entry: `Timber.d("S0139: backfill scanning SMB credentials for empty shareName")`.
    - Snapshot all credentials via `credentialsRepository.getAllCredentials().first()`.
    - Filter to `type == "SMB" && shareName.isNullOrEmpty()`.
    - If filtered list empty → set flag, return `BackfillResult(scanned=0, updated=0, skipped=0)`.
    - Snapshot all SMB resources via `resourceRepository.getAllResourcesSync()` (or equivalent suspend method) and filter to `type == ResourceType.SMB && credentialsId != null`.
    - Build `Map<credentialId, MediaResource>` taking the first occurrence per id.
    - Iterate filtered credentials. For each:
      - Lookup matching resource by `credential.credentialId`.
      - If absent → `Timber.w("S0139: backfill skipped — no resource for credential ${credential.credentialId}")`, increment `skipped`.
      - Else parse via `SmbPathUtils.parseSmbPath(resource.path)`. If `parsedPath?.connectionInfo?.shareName.isNullOrEmpty()` → `Timber.w("S0139: backfill skipped — path has no shareName: ${resource.path}")`, increment `skipped`.
      - Else call `credentialsRepository.update(credential.copy(shareName = parsedPath.connectionInfo.shareName))`, increment `updated`. Log `Timber.i("S0139: backfilled shareName='${parsedPath.connectionInfo.shareName}' for credential ${credential.credentialId}")`.
    - On normal completion (any `updated`/`skipped` count) set the flag.
    - Catch top-level `Exception`: log `Timber.e(e, "S0139: backfill failed")`. Do NOT set the flag — next launch will retry.
  - **Verification:**
    - Class compiles with single-responsibility logic, no UI dependency.
    - `Timber.d("S0139: ...")` exists at the entry point.
- [ ] Wire the backfill into `FastMediaSorterApp`.
  - **File:** `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt`
  - Add `@Inject lateinit var backfillSmbCredentialShareNameUseCase: BackfillSmbCredentialShareNameUseCase`.
  - In `onCreate()`, alongside the existing `applicationScope.launch(Dispatchers.IO)` blocks (after `tempFileManager.cleanupOldTempFiles(..)`), add:
    ```kotlin
    applicationScope.launch(Dispatchers.IO) {
        runCatching { backfillSmbCredentialShareNameUseCase() }
            .onFailure { Timber.e(it, "S0139: backfill launch failed") }
    }
    ```
  - **Verification:**
    - `git diff` shows the injected field, the launch block, and no other behaviour change.
    - Build passes (`/build standard debug`).
- [ ] Catalogue sync: `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` then `render.ps1`.
  - Set `role` for the new use-case via `set.ps1` (suggested: `role=domain.usecase.maintenance`).

## Out of scope

- No update to `getConnectionInfo` defaulting (`credentials.shareName ?: ""` already handles the read side; self-heal in `ResourceRepositoryImpl.testSmbConnection` is intentional and stays).
- No retry on persistent failure beyond the next process launch — backfill is small and bounded.
- No bulk-DAO query: full credential snapshot is small (typical N ≪ 100). Filtering in Kotlin keeps SQL plain.

## Verification predicates

- New use-case file present and compiles.
- `FastMediaSorterApp.onCreate` references the use-case once.
- After running on a device with a credential whose `shareName` is null/empty, the next session shows `Timber.i("S0139: backfilled ..")` and no further `testSmbConnection: credentials shareName='' differs ..` warnings for that resource.
- SharedPreferences value `s0139_backfill / smb_share_name_backfill_v1_done` is set to `true`.
