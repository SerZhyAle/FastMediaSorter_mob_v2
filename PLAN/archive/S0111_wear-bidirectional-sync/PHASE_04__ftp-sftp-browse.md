# Phase 04 — FTP/SFTP Browse on Watch

**Strategic spec:** [`../S0111_wear-bidirectional-sync.md`](../S0111_wear-bidirectional-sync.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — independent of Phase 01 (no Data Layer involvement)
**Blocks:** Phase 07
**Steps done:** 5 / 5
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Watch `BrowseScreen` can browse FTP and SFTP directories, not only test their connections. Adds `FtpDataSource` and `SftpDataSource` to the watch data layer; `BrowseViewModel` routes by protocol type.

---

## Prerequisites

- [ ] INDEX.md Blocker 3 (FTP/SFTP library for Wear APK) is checked and a decision is recorded.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `wear/build.gradle.kts` | Modified | — |
| `wear/src/main/java/com/sza/fastmediasorter/wear/data/network/ftp/FtpDataSource.kt` | New | ≤ 300 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/data/network/sftp/SftpDataSource.kt` | New | ≤ 300 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/di/WearAppModule.kt` | Modified | ≤ 200 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/browse/BrowseViewModel.kt` | Modified | ≤ 360 |

---

## Steps

### Step 4.1 — Add protocol library to `wear/build.gradle.kts`

**Files:** `wear/build.gradle.kts`
**Depends on:** — start of phase (requires Blocker 3 decision)

**Prompt for developer:**

> Based on the library decision recorded in Blocker 3 of INDEX.md, add the appropriate dependency to the `dependencies` block of `wear/build.gradle.kts`:
>
> - If Apache Commons Net: `implementation("commons-net:commons-net:3.10.0")` for FTP. For SFTP, add JSch: `implementation("com.github.mwiede:jsch:0.2.17")` (lighter than SSHJ, already compatible with Hilt-driven DI).
> - If socket implementation: skip this step (mark `[x] done` and note "socket impl chosen — no dep added").
>
> Do not add SSHJ — it pulls in Bouncy Castle which conflicts with SMBJ's Bouncy Castle transitive dependency already present in `:wear`.

**Verification:**

- If library added: `Grep` — `commons-net` or `jsch` present in `wear/build.gradle.kts`.
- If socket impl: Step marked done with "socket impl chosen" note.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Library added: commons-net:3.10.0 + jsch:0.2.17 per Blocker 3 decision. Verification PASS. Dev log recorded.

---

### Step 4.2 — Create `FtpDataSource` on watch

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/data/network/ftp/FtpDataSource.kt`
**Depends on:** Step 4.1

**Prompt for developer:**

> Create the listed file. Declare `class FtpDataSource @Inject constructor()`. Implement `suspend fun listDirectory(source: NetworkSource, path: String): List<WearMediaFile>` using `withContext(Dispatchers.IO)`.
>
> If Commons Net: use `FTPClient`, connect to `source.server:source.port`, login with `source.username/password`, set passive mode, call `listFiles(path)`, map each `FTPFile` to `WearMediaFile(id = file.name, name = file.name, path = "$path/${file.name}", size = file.size, mimeType = inferMimeType(file.name), isDirectory = file.isDirectory, source = source)`.
>
> If socket impl: implement a minimal `LIST` command over raw socket, parse UNIX-style listing with a local `parseFtpListLine(line: String): WearMediaFile?` helper.
>
> Add `Timber.d("S0111: FtpDataSource.listDirectory path=$path source=${source.name}")` at method entry. On any exception, throw wrapped with context message.

**Verification:**

- `Glob` — `wear/src/main/java/com/sza/fastmediasorter/wear/data/network/ftp/FtpDataSource.kt` exists.
- `Grep` — `class FtpDataSource` matches.
- `Grep` — `suspend fun listDirectory` present.
- `Grep` — `Timber.d("S0111:` present.
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 5/5 PASS. Files: wear/.../data/network/ftp/FtpDataSource.kt (+56 LOC). Dev log recorded.

---

### Step 4.3 — Create `SftpDataSource` on watch

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/data/network/sftp/SftpDataSource.kt`
**Depends on:** Step 4.1

**Prompt for developer:**

> Create the listed file. Declare `class SftpDataSource @Inject constructor()`. Implement `suspend fun listDirectory(source: NetworkSource, path: String): List<WearMediaFile>` using `withContext(Dispatchers.IO)`.
>
> If JSch: create a `JSch` instance, open a `Session` to `source.server:source.port` with `source.username/password` (or private key if `source.sshPrivateKey != null`), open a `ChannelSftp`, call `ls(path)`, map each `LsEntry` to `WearMediaFile`.
>
> If socket impl: this is not feasible for SFTP (SSH is not implementable over raw sockets in reasonable code); in this case mark the step as `[x] done` with note "SFTP socket impl not feasible — SFTP browse deferred; FTP-only in this phase". Update INDEX.md to reflect SFTP browse as a future sub-task.
>
> Add `Timber.d("S0111: SftpDataSource.listDirectory path=$path source=${source.name}")` at method entry.

**Verification:**

- `Glob` — `wear/src/main/java/com/sza/fastmediasorter/wear/data/network/sftp/SftpDataSource.kt` exists.
- `Grep` — `class SftpDataSource` matches.
- `Grep` — `suspend fun listDirectory` present (or "SFTP deferred" note).
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 4/4 PASS. Files: wear/.../data/network/sftp/SftpDataSource.kt (+67 LOC). JSch implementation chosen (jsch:0.2.17). Dev log recorded.

---

### Step 4.4 — Provide new DataSources in `WearAppModule`

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/di/WearAppModule.kt`
**Depends on:** Steps 4.2, 4.3

**Prompt for developer:**

> In `WearAppModule`, add `@Provides @Singleton` functions `provideFtpDataSource(): FtpDataSource` and `provideSftpDataSource(): SftpDataSource` (both just return new instances with no constructor arguments). Remove the existing `provideFtpConnectionTest()` and `provideSftpConnectionTest()` bindings; replace them with the new DataSource providers.
>
> Note: `FtpConnectionTest` and `SftpConnectionTest` classes may now be deleted — they are stubs replaced by the real DataSources. Delete both files if no other code references them (verify via `Grep`).

**Verification:**

- `Grep` — `provideFtpDataSource` present in `WearAppModule.kt`.
- `Grep` — `provideSftpDataSource` present.
- `Grep` — `FtpConnectionTest` returns zero hits across `wear/src/` (if deleted).

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification PASS (providers added). FtpConnectionTest/SftpConnectionTest retained — still referenced by NetworkSourceRepositoryImpl.testConnection(); deletion would break build. Dev log recorded.

---

### Step 4.5 — Update `BrowseViewModel` to route FTP/SFTP

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/browse/BrowseViewModel.kt`
**Depends on:** Step 4.4

**Prompt for developer:**

> Inject `FtpDataSource` and `SftpDataSource` into `BrowseViewModel`. In the existing `loadNetworkFiles(source: NetworkSource, path: String)` method (or its equivalent), extend the routing block:
> - `NetworkSourceType.SMB` → existing `smbDataSource.listFiles(...)` path (unchanged)
> - `NetworkSourceType.FTP` → `ftpDataSource.listDirectory(source, path)`
> - `NetworkSourceType.SFTP` → `sftpDataSource.listDirectory(source, path)`
> - `NetworkSourceType.GOOGLE_DRIVE` → error("Google Drive not supported on Wear")
>
> Map the resulting `List<WearMediaFile>` to the existing `BrowseUiState.Success` format (no UI change needed — same list model).

**Verification:**

- `Grep` — `ftpDataSource` present in `BrowseViewModel.kt`.
- `Grep` — `sftpDataSource` present.
- `Grep` — `NetworkSourceType.FTP` routed (not falls through to error).
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 4/4 PASS. Files: wear/.../ui/browse/BrowseViewModel.kt (FTP/SFTP routing added). Dev log recorded.

---

## Phase Done Criteria

- [x] Every Step 04.* above is `[x] done`.
- [x] Project compiles — run `/build` for `:wear:assembleDebug`.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/wear.jsonl` regenerated.

---

## Handoff Notes to Next Phase

- FTP browse is fully functional on watch (SFTP may be deferred per Step 4.3 decision).
- `FtpConnectionTest` and `SftpConnectionTest` stubs are removed; real DataSources replace them.
- No phone-side changes in this phase.

---

## Rollback Plan

Revert phase commit(s). Remove library dependencies from `wear/build.gradle.kts` if added. Watch falls back to "FTP connection test not supported" message — acceptable regression.
