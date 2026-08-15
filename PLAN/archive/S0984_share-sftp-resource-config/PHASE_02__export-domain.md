# Phase 02 - Export domain (use case + reachability classifier)

**Strategic spec:** [`../S0984_share-sftp-resource-config.md`](../S0984_share-sftp-resource-config.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-07-11
**Completed:** 2026-07-11

---

## Objective

Add `ExportCompanionConfigUseCase` (builds and writes a `.fmscfg` from a live SFTP `MediaResource`) and a host-reachability classifier that flags private/CGNAT hosts. No UI.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`CompanionConfigSerializer` exists; relaxation landed).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/companion/ExportCompanionConfigUseCase.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/utils/SftpHostReachabilityClassifier.kt` | New | ≤ 90 |
| `app_v2/src/test/java/com/sza/fastmediasorter/utils/SftpHostReachabilityClassifierTest.kt` | New | ≤ 100 |

---

## Steps

### Step 02.1 - Add `SftpHostReachabilityClassifier`

**Files:** `utils/SftpHostReachabilityClassifier.kt` (New), `utils/SftpHostReachabilityClassifierTest.kt` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Create `object SftpHostReachabilityClassifier` in `com.sza.fastmediasorter.utils` with `enum class Reachability { PRIVATE_LAN, PUBLIC_ROUTABLE, HOSTNAME }` and `fun classify(host: String): Reachability`. Classify as `PRIVATE_LAN` when the host is a literal IPv4 in RFC1918 (`10/8`, `172.16/12`, `192.168/16`), CGNAT `100.64/10`, loopback `127/8`, link-local `169.254/16`, or a `.local` mDNS name; `PUBLIC_ROUTABLE` for any other literal IPv4/IPv6; `HOSTNAME` for a non-`.local` DNS name (routability unknown). Pure function, no network calls (strategic §5: no I/O at export). No `Timber` needed. Add a focused unit test covering one address per class plus a public IP and a plain hostname.

**Verification:**

- `Glob` - `utils/SftpHostReachabilityClassifier.kt` exists.
- `Grep` - `fun classify(host: String)` and `PRIVATE_LAN` present.
- `.\a.ps1 fu` (or `--tests "*SftpHostReachabilityClassifier*"`) - test passes.

**Status:** `[x] done`

---

### Step 02.2 - Add `ExportCompanionConfigUseCase`

**Files:** `domain/usecase/companion/ExportCompanionConfigUseCase.kt` (New)
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `class ExportCompanionConfigUseCase @Inject constructor(@ApplicationContext context, CompanionConfigSerializer serializer, SmbOperationsUseCase smbOperationsUseCase, @IoDispatcher ioDispatcher)`. Signature: `suspend operator fun invoke(resource: MediaResource, includePassword: Boolean): Result<File>` on `withContext(ioDispatcher)`. Steps: (1) `SftpPathUtils.parseSftpPath(resource.path)` -> host/port/remotePath, fail `Result.failure` if null; (2) load credentials via `smbOperationsUseCase.getSftpCredentials(resource.credentialsId)` (tolerate a resource with no stored password - key-auth-only degrades to passwordless: use `""`); (3) build `CompanionConfigDto(schemaVersion = CompanionConfigParser.SUPPORTED_SCHEMA_VERSION, resourceName = resource.name, protocol = CompanionConfigParser.PROTOCOL_SFTP, accessPaths = listOf(CompanionAccessPathDto(kind = CompanionAccessPathDto.KIND_LAN, host = host, port = port)), username = creds.username, password = if (includePassword) creds.password else "", hostKeyFingerprintSha256 = resource.hostKeyFingerprint.orEmpty(), roots = listOf(CompanionRootDto(virtualPath = remotePath, label = resource.name)), createdAt = <ISO-8601 now>)`; (4) `serializer.serialize(dto)`; (5) write UTF-8 to `File(context.cacheDir, "share_temp").apply { mkdirs() }` named `<sanitized resource.name>.fmscfg` (sanitize with `replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "resource" }`, mirroring `MainViewModel.exportResourceForShare`); return `Result.success(file)`. Wrap in try/catch -> `Result.failure` with a `Timber.e`. Reuse the `CompanionAccessPathDto.KIND_LAN` / parser companion constants - no new string literals for protocol/kind.

**Verification:**

- `Glob` - `domain/usecase/companion/ExportCompanionConfigUseCase.kt` exists.
- `Grep` - `suspend operator fun invoke(resource: MediaResource, includePassword: Boolean): Result<File>` present.
- `Grep` - `serializer.serialize(` and `getSftpCredentials(` both referenced.
- Build predicate covered by Phase Done Criteria.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (standard debug).
- [ ] `*SftpHostReachabilityClassifier*` unit test passes.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (2 new classes).

---

## Handoff Notes to Next Phase

Phase 03 calls `ExportCompanionConfigUseCase(resource, includePassword)` from `MainViewModel` and uses `SftpHostReachabilityClassifier.classify(host)` to decide whether the export dialog shows the "works only on your network" warning. `includePassword` is driven by the dialog checkbox (inverted: checkbox "do not include password" -> `includePassword = false`).

---

## Rollback Plan

Revert the phase commit(s). Two new leaf classes, no callers yet until Phase 03 - safe to drop.
