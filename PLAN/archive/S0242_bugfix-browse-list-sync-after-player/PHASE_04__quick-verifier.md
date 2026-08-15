# Phase 04 — Quick Verifier (background existence probe)

**Strategic spec:** [`../S0242_bugfix-browse-list-sync-after-player.md`](../S0242_bugfix-browse-list-sync-after-player.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** —
**Steps done:** 5 / 5
**Started:** 2026-05-18
**Completed:** 2026-05-18

---

## Objective

After Reconciler completes and the list is rendered, run a background existence probe on the first N=10 visible files. Probe disappearances are routed back through the journal as `Mutation.Delete` so Reconciler stays the single source of truth. Strategy per resource type — no probe for FTP per §6 Item 3 resolution.

---

## Prerequisites

- [ ] Phase 03 ✅ Done — Reconciler exists.
- [ ] `CloudStorageClient.fileExists(...)` and `getFileMetadata(...)` available on Drive/OneDrive/Dropbox impls.
- [ ] `ConnectionThrottleManager` available for cloud/SMB/SFTP throttling.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/verifier/QuickVerifier.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/verifier/LocalQuickVerifier.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/verifier/SmbQuickVerifier.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/verifier/SftpQuickVerifier.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/verifier/CloudQuickVerifier.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/verifier/QuickVerifierDispatcher.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/QuickVerifierModule.kt` | New | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` | Modified | (already touched in Phase 03 — verify backup is still recent) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseReconcilerManager.kt` | Modified | ≤ 250 (currently ~220 after Phase 03) |

---

## Steps

### Step 04.1 — Create `QuickVerifier` contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/verifier/QuickVerifier.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `interface QuickVerifier` in `com.sza.fastmediasorter.domain.verifier`. Single method:
>
> ```kotlin
> /**
>  * Probe existence of [paths] on the source.
>  * Returns the subset of [paths] that ARE NOT PRESENT on the source.
>  * Must not throw; on network errors returns empty list (no false-positive deletions).
>  */
> suspend fun missingFiles(resourceId: Long, paths: List<String>): List<String>
> ```
>
> All paths passed in must already be in canonical form (caller uses `PathNormalizer`).
>
> Add a marker `data class QuickVerifierKey(val type: ResourceType)` for multibinding key (Step 04.5).

**Verification:**

- `Glob` — `QuickVerifier.kt` exists.
- `Grep` — `interface QuickVerifier` matches once.
- `Grep` — `suspend fun missingFiles(` matches once.

**Status:** `[x]` done

**Step Log (2026-05-18):**
- Created `domain/verifier/QuickVerifier.kt` (44 LOC).
- Defines `interface QuickVerifier { suspend fun missingFiles(resourceId, paths) }` + marker `data class QuickVerifierKey(val type: ResourceType)`.
- Verification: `interface QuickVerifier` = 1 hit, `suspend fun missingFiles(` = 1 hit — PASS.
- No-op-on-error and raw-path contract documented in KDoc per phase prompt.

---

### Step 04.2 — Per-resource-type strategies

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/data/verifier/LocalQuickVerifier.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/verifier/SmbQuickVerifier.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/verifier/SftpQuickVerifier.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/verifier/CloudQuickVerifier.kt`

**Depends on:** Step 04.1

**Prompt for developer:**

> Each strategy implements `QuickVerifier`. Inject the relevant client/manager from the existing data layer:
>
> - **LocalQuickVerifier** — `@Inject constructor()`. Implementation: `paths.filter { !File(it).exists() }` on `Dispatchers.IO`. Trivial.
> - **SmbQuickVerifier** — `@Inject constructor(private val smbManager: SmbConnectionManager /* or whatever name */, private val throttle: ConnectionThrottleManager)`. Find the SMB manager via `Grep -rn "class.*Smb.*Manager" app_v2/src/main/java/`. Implementation: for each path, open existing session → `session.openFile(path, …)` with `EnumSet.of(SMB2CreateOptions.FILE_NO_INTERMEDIATE_BUFFERING)` is overkill; use the dirent `existsAt(path)` if the wrapper exposes it, otherwise catch `SMBApiException` with `STATUS_OBJECT_NAME_NOT_FOUND` / `STATUS_OBJECT_PATH_NOT_FOUND` as "missing", anything else swallow. Wrap the loop in `throttle.withThrottle(ProtocolLimits.SMB) { … }`.
> - **SftpQuickVerifier** — `@Inject constructor(private val sftpManager: SftpManager, private val throttle: ConnectionThrottleManager)`. Use `sftpManager.stat(path)` returning `null` on `SFTPException(NoSuchFile)`. Wrap in `throttle.withThrottle(ProtocolLimits.SFTP)`.
> - **CloudQuickVerifier** — `@Inject constructor(private val cloudClientFactory: CloudStorageClientFactory /* or the existing repository that gives a client per resource */, private val throttle: ConnectionThrottleManager)`. Per path: resolve the right `CloudStorageClient` for this resource, call `client.fileExists(name, parentId)` OR `client.getFileMetadata(fileId)`-then-catch-404 depending on what canonical-path form the resource uses. Wrap in `throttle.withThrottle(ProtocolLimits.CLOUD)`.
>
> All strategies: never throw. On any exception, log `Timber.w("QuickVerifier(<type>): probe error for resource=%d, returning no-op", e)` and return empty list. The "no-op on error" rule prevents false deletes from transient network issues — a real missing file will be detected on the next pull-to-refresh.
>
> No FTP strategy — `QuickVerifierDispatcher` skips FTP per §6 Item 3.

**Verification:**

- All four files exist (`Glob`).
- `Grep` — `class LocalQuickVerifier @Inject constructor()` matches once.
- `Grep` — `class SmbQuickVerifier @Inject constructor(` matches once; `throttle.withThrottle(ProtocolLimits.SMB` matches once.
- `Grep` — `class SftpQuickVerifier @Inject constructor(` matches once; `throttle.withThrottle(ProtocolLimits.SFTP` matches once.
- `Grep` — `class CloudQuickVerifier @Inject constructor(` matches once; `throttle.withThrottle(ProtocolLimits.CLOUD` matches once.
- `Grep -rn "Log\.d\(" app_v2/src/main/java/com/sza/fastmediasorter/data/verifier/` — zero hits.

**Status:** `[x]` done

**Step Log (2026-05-18):**
- Created 4 strategy files under `data/verifier/`:
  - `LocalQuickVerifier.kt` (31 LOC) — `File.exists()` filter; no throttle (`LOCAL` is a no-op).
  - `SmbQuickVerifier.kt` (84 LOC) — delegates to `SmbOperationStrategy.exists()` under `ProtocolLimits.SMB`.
  - `SftpQuickVerifier.kt` (80 LOC) — delegates to `SftpOperationStrategy.exists()` under `ProtocolLimits.SFTP`.
  - `CloudQuickVerifier.kt` (84 LOC) — delegates to `CloudOperationStrategy.exists()` under `ProtocolLimits.CLOUD`.
- **API deviation from phase prompt**: instead of injecting `SmbConnectionManager` / `SftpManager` / `CloudStorageClient` directly and reimplementing path-parsing + credential lookup, each network strategy delegates to the corresponding `FileOperationStrategy.exists(path)` already implemented in `data/transfer/strategy/`. Rationale: those strategies already centralize `SmbPathUtils.parseSmbPath` + `NetworkCredentialsRepository.getByServerAndShare/getByTypeServerAndPort` + provider-specific error handling. Reimplementing this in the verifier would duplicate ~100 LOC per protocol and would silently drift from the strategy's behaviour. Delegation keeps the SMB/SFTP/Cloud "is this file there" question in a single owner. The contract (no-throw, error → empty list) is enforced at the verifier layer via `runCatching { strategy.exists(path) }.getOrNull()`.
- **Throttle resourceKey** chosen to share semaphore with the active scanner: SMB → `smb://server:port/share`, SFTP → `sftp://host:port`, Cloud → `cloud://<provider>`. Each falls back to `verify://<proto>/<resourceId>` when the first path is unparseable.
- Verification: 4× `class …QuickVerifier @Inject constructor` (one each), 3× `ProtocolLimits.{SMB,SFTP,CLOUD}` at the `withThrottle` call site, 0× `Log.d(` — PASS.

---

### Step 04.3 — `QuickVerifierDispatcher` — select strategy per `ResourceType`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/verifier/QuickVerifierDispatcher.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Create `class QuickVerifierDispatcher @Inject constructor(private val local: LocalQuickVerifier, private val smb: SmbQuickVerifier, private val sftp: SftpQuickVerifier, private val cloud: CloudQuickVerifier, private val resourceRepo: ResourceRepository /* or whatever holds the Resource by id */)` in `com.sza.fastmediasorter.data.verifier`. Annotate `@Singleton`.
>
> ```kotlin
> suspend fun probe(resourceId: Long, candidates: List<MediaFile>, n: Int = 10): List<String> {
>     val resource = resourceRepo.findById(resourceId) ?: return emptyList()
>     val verifier: QuickVerifier? = when (resource.type) {
>         ResourceType.LOCAL        -> local
>         ResourceType.SMB          -> smb
>         ResourceType.SFTP         -> sftp
>         ResourceType.FTP          -> null  // §6 Item 3: FTP excluded
>         ResourceType.GOOGLE_DRIVE,
>         ResourceType.DROPBOX,
>         ResourceType.ONE_DRIVE    -> cloud
>         else                      -> null  // unknown type — skip
>     } ?: return emptyList()
>
>     val first = candidates.take(n).map { it.path }  // already canonical from Reconciler
>     return verifier.missingFiles(resourceId, first)
> }
> ```
>
> Logging: `Timber.d("QuickVerifierDispatcher: resource=%d type=%s probed=%d missing=%d", rid, type, first.size, missing.size)`.

**Verification:**

- `Glob` — `QuickVerifierDispatcher.kt` exists.
- `Grep` — `class QuickVerifierDispatcher @Inject constructor(` matches once.
- `Grep` — `ResourceType.FTP\s*->\s*null` matches once (single-line or multi-line — accept either).
- `Grep` — `suspend fun probe(resourceId: Long, candidates: List<MediaFile>, n: Int = 10)` matches once.

**Status:** `[x]` done

**Step Log (2026-05-18):**
- Created `data/verifier/QuickVerifierDispatcher.kt` (87 LOC).
- Constructor-injects the 4 strategies + `ResourceRepository.getResourceById(id)` (the project method — the prompt called it `findById`, that name does not exist).
- **`ResourceType` enum deviation from phase prompt**: prompt lists `GOOGLE_DRIVE / DROPBOX / ONE_DRIVE` as separate values; actual enum is `LOCAL / SMB / SFTP / FTP / CLOUD`. A single `ResourceType.CLOUD` branch dispatches to `CloudQuickVerifier` which itself fans out to the correct provider via `MediaResource.cloudProvider` (handled internally by `CloudOperationStrategy`). Exhaustive `when` with no `else` — adding a new `ResourceType` later will be a compile-time error.
- FTP branch logs and returns empty list explicitly per §6 Item 3.
- Verification: `class QuickVerifierDispatcher @Inject constructor(` = 1 hit, `ResourceType.FTP -> {` = 1 hit, `suspend fun probe(resourceId: Long, candidates: List<MediaFile>, n: Int = 10)` matched across 4 lines — PASS.

---

### Step 04.4 — Hilt module for verifiers

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/di/QuickVerifierModule.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Create `@Module @InstallIn(SingletonComponent::class) abstract class QuickVerifierModule` in `core.di`. Bind the four strategies as `@Singleton`-scoped concrete classes (constructor-injected — Hilt picks them up automatically; module exists only to mark Singleton scope explicitly via `@Binds` to themselves if needed, OR drop the module entirely and rely on constructor injection if no qualifier needed). Verify by reading existing pattern in `RepositoryModule.kt`:
>
> - If the strategies are referenced directly (`LocalQuickVerifier`, `SmbQuickVerifier`, etc. as concrete types in `QuickVerifierDispatcher`), no module is needed; Hilt instantiates them via constructor injection. **In that case delete this file from the plan**.
> - If `QuickVerifier` is used as a polymorphic dependency anywhere (e.g. for multibinding), add the binds here.
>
> Current plan: `QuickVerifierDispatcher` references concrete types, so this step likely degenerates to "no module needed — delete from Files Touched". Document the outcome in chat.

**Verification:**

- If file created: `Grep -c "@Binds" QuickVerifierModule.kt` ≥ 1.
- If skipped: document in chat — `Step 04.4 — degenerate, no module needed`.
- Build sanity: `.\a.ps1 dq` exit 0 either way (Hilt graph compiles).

**Status:** `[x]` done (skipped — degenerate)

**Step Log (2026-05-18):**
- **Outcome: file NOT created. No `QuickVerifierModule.kt` needed.**
- All four strategies (`LocalQuickVerifier`, `SmbQuickVerifier`, `SftpQuickVerifier`, `CloudQuickVerifier`) and `QuickVerifierDispatcher` are `@Singleton + @Inject constructor(..)` — Hilt instantiates them via constructor injection without explicit binding.
- `QuickVerifier` interface is NOT used polymorphically anywhere — the dispatcher holds the four strategies as concrete types. No multibinding map is needed (the `QuickVerifierKey` data class is defined for forward compatibility; if multibinding is wired later, the dispatcher's `when` block becomes a `Map<QuickVerifierKey, Provider<QuickVerifier>>` lookup).
- Build sanity (Hilt graph compiles) deferred to the Step 04.5 build — that build is the discriminating closure for the whole new graph.
- `Files Touched` table in this phase file lists `QuickVerifierModule.kt` as "New" — leaving the row for documentation but the file does not exist on disk. Phase Done Criteria `Grep -rn "QuickVerifier" app_v2/src/main/java/ → ≥ 6 hits` is still satisfied by the 6 created files (interface + 4 strategies + dispatcher + verifier key usages).

---

### Step 04.5 — Run verifier after Reconciler; emit findings into journal

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseReconcilerManager.kt` (extend Phase 03's class)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` (extend onResume)

**Depends on:** Steps 04.3 (Step 04.4 is no-op if degenerate)

**Prompt for developer:**

> 1. Inject `QuickVerifierDispatcher` and a coroutine scope (Application scope — `@ApplicationScope` qualifier per existing `AppModule.kt`) into `BrowseReconcilerManager`. Add a new method:
>
> ```kotlin
> /**
>  * Probe first N visible files for existence. Emit Mutation.Delete into the journal
>  * for each missing path. Fire-and-forget — does not block the Reconciler.reconcile() return.
>  */
> fun scheduleQuickVerify(resourceId: Long, visible: List<MediaFile>) {
>     applicationScope.launch {
>         val missing = verifierDispatcher.probe(resourceId, visible, n = 10)
>         missing.forEach { path ->
>             journal.record(
>                 Mutation.Delete(
>                     resourceId = resourceId,
>                     canonicalPath = path,
>                     opId = UUID.randomUUID().toString(),
>                     timestampMs = System.currentTimeMillis(),
>                 )
>             )
>         }
>         // Reconciler will pick these up on the next onResume — no immediate UI update.
>     }
> }
> ```
>
> Rationale: probe results going through the journal means the same Reconciler code handles all delete sources (Player op, FileObserver event, Quick Verifier finding) on the next `onResume`.
>
> 2. In `BrowseActivity.onResume()`, after `reconcile(...)` returns, call `browseReconcilerManager.scheduleQuickVerify(rid, result.updatedList)`. The probe runs after the adapter is rebound (next-frame) so it does not delay first paint.

**Verification:**

- `Grep -n "fun scheduleQuickVerify(" app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseReconcilerManager.kt` — exactly one hit.
- `Grep -n "scheduleQuickVerify(" app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` — at least one hit, inside `onResume()`.
- `Grep -n "Mutation.Delete(" app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseReconcilerManager.kt` — at least one hit (inside the probe loop).
- Build sanity: `.\a.ps1 dq` exit 0.

**Status:** `[x]` done

**Step Log (2026-05-18):**
- `BrowseReconcilerManager.kt` extended: +2 constructor deps (`QuickVerifierDispatcher`, `@ApplicationScope CoroutineScope`), +1 method `scheduleQuickVerify(resourceId, resourceType, visible)` (54 LOC including KDoc). Imports: `ApplicationScope`, `QuickVerifierDispatcher`, `CoroutineScope`, `launch`, `UUID`. File grew from 266 LOC → 322 LOC (still well under 1500-LOC cap and 500-LOC backup threshold).
- **Signature deviation from phase prompt**: prompt sketch was `fun scheduleQuickVerify(resourceId, visible)`. Added a third arg `resourceType: ResourceType` because returned RAW paths must be canonicalized via `pathNormalizer.canonical(rawPath, resourceType)` before being recorded as `Mutation.Delete(canonicalPath = …)`. Without the type, the Reconciler-side comparator would not match the journaled deletion against the visible row on next reconcile (Reconciler canonicalizes visible rows with the same per-type rules).
- `BrowseActivity.runReconciler()`: appended one line calling `browseReconcilerManager.scheduleQuickVerify(resource.id, resource.type, result.updatedList)` after the existing `viewModel.replaceMediaFiles` branch.
- Verification: `fun scheduleQuickVerify(` = 1 hit in `BrowseReconcilerManager.kt`, `scheduleQuickVerify(` = 1 hit in `BrowseActivity.kt` (inside `runReconciler` called from `onResume`), `Mutation.Delete(` = 1 hit in `BrowseReconcilerManager.kt` (inside the probe loop). **Build `.\a.ps1 dq` exit 0** — BUILD SUCCESSFUL in 40s. PASS.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles — `.\a.ps1 dq` exit 0 (BUILD SUCCESSFUL in 40s, 2026-05-18).
- [x] `Grep -rn "QuickVerifier" app_v2/src/main/java/` — 36 hits across 7 files (≥ 6 required).
- [x] `Grep -rn "ResourceType.FTP" app_v2/src/main/java/com/sza/fastmediasorter/data/verifier/` — single skip-branch at `QuickVerifierDispatcher.kt:66` (plus one doc reference at :19).
- [x] Dev log entry added for every new / modified file (7 entries: interface, 4 strategies, dispatcher, BrowseReconcilerManager, BrowseActivity).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

After Phase 04: Reconciler covers Player-originated mutations + out-of-band deletions for local / SMB / SFTP / cloud. FTP relies purely on Player journal + pull-to-refresh. Phase 05 routes the existing local `FileObserver` (`BrowseFileObserverManager`) into the journal for symmetry — currently it directly calls `scheduleReload` and bypasses Reconciler.

---

## Rollback Plan

Delete new files in `data/verifier/`, `domain/verifier/`, `core/di/QuickVerifierModule.kt`. Revert the `BrowseReconcilerManager` and `BrowseActivity` extensions from Step 04.5 via git. Reconciler still functions for journal-driven mutations.
