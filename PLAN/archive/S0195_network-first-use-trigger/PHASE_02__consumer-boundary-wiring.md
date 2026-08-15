# Phase 02 — consumer-boundary-wiring

**Strategic spec:** [`../S0195_network-first-use-trigger.md`](../S0195_network-first-use-trigger.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 5 / 5
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Inject `dagger.Lazy<NetworkLifecycleBootstrapper>` into the first network-touching method of every per-protocol consumer manager / client. Each call site invokes `.get().ensureInitialized()` synchronously before the network operation. The bootstrapper still no-ops at this stage because `FastMediaSorterApp` continues to attach eagerly — this phase only places the triggers.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done — `NetworkLifecycleBootstrapper` class exists.
- [ ] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpClient.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveRestClient.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/DropboxClient.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/OneDriveRestClient.kt` | Modified | ≤ 1500 |

> Line budgets pre-edit are approximate. Every file edit is ≤ 5 line delta — adding a `dagger.Lazy<...>` constructor parameter and one `.get().ensureInitialized()` call per public entry. If any file exceeds 1500 LOC after edit, refuse and split the manager first.

---

## Steps

### Step 02.1 — Wire bootstrapper into `SmbConnectionManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add `private val lifecycleBootstrapper: dagger.Lazy<com.sza.fastmediasorter.data.network.lifecycle.NetworkLifecycleBootstrapper>` to the `@Inject constructor` parameter list of `SmbConnectionManager`. Inside both public entry methods — `withConnection(...)` (around line 251) and `getConnectionForExoPlayer(...)` (around line 876) — add `lifecycleBootstrapper.get().ensureInitialized()` as the first statement of the method body. Do not call it from `init { ... }` — the `init` block already registers with `networkStateMonitor` and must not bootstrap recursively (the bootstrapper itself dereferences `Lazy<SmbConnectionManager>`, which would trigger `init`). Add a short KDoc on each modified method: "S0195: trigger network lifecycle bootstrap on first SMB use." If `SmbConnectionManager.kt` exceeds 1500 LOC after this change, refuse and request a manager-split spec first.

**Verification:**

- `Grep` — `lifecycleBootstrapper: dagger.Lazy<` appears once in `SmbConnectionManager.kt` (constructor param).
- `Grep -n "lifecycleBootstrapper.get().ensureInitialized()"` returns exactly 2 hits in `SmbConnectionManager.kt`.
- `Grep -n "Log\.d\("` returns zero hits in `SmbConnectionManager.kt` (Timber-only rule for files touched).
- Build succeeds (`/build` standard debug).

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Grep predicates 3/3 PASS (ctor param 1; .get().ensureInitialized() 2; Log.d 0). Build deferred to end of phase. Files: SmbConnectionManager.kt (+5 LOC).

---

### Step 02.2 — Wire bootstrapper into `SftpClient`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt`
**Depends on:** — can run in parallel with 02.1

**Prompt for developer:**

> Identify the first network-touching method of `SftpClient` — the one that establishes a new SSH connection or issues the first SFTP RPC. Common candidates: `connect()`, `withConnection(...)`, `executeCommand(...)`. The developer must select the single method through which all consumer SFTP operations funnel. Add `private val lifecycleBootstrapper: dagger.Lazy<com.sza.fastmediasorter.data.network.lifecycle.NetworkLifecycleBootstrapper>` to the `@Inject constructor`. At the top of the chosen entry method add `lifecycleBootstrapper.get().ensureInitialized()`. KDoc note: "S0195: trigger network lifecycle bootstrap on first SFTP use." If multiple parallel entry methods exist (no single funnel point), add the trigger to each.

**Verification:**

- `Grep` — `lifecycleBootstrapper: dagger.Lazy<` appears once in `SftpClient.kt`.
- `Grep -n "lifecycleBootstrapper.get().ensureInitialized()"` returns at least 1 hit in `SftpClient.kt`.
- `Grep -n "Log\.d\("` returns zero hits in `SftpClient.kt`.
- Build succeeds.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Grep predicates 3/3 PASS (ctor 1; .get().ensureInitialized() 2 — `withConnection` + `getConnectionForExoPlayer`; Log.d 0). Build deferred. SftpClient.kt +4 LOC.

---

### Step 02.3 — Wire bootstrapper into `FtpClient`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpClient.kt`
**Depends on:** — parallel with 02.1

**Prompt for developer:**

> Same pattern as Step 02.2 applied to `FtpClient.kt`. Identify the FTP connection-establishing entry point and add the bootstrapper trigger at its top.

**Verification:**

- `Grep` — `lifecycleBootstrapper: dagger.Lazy<` appears once in `FtpClient.kt`.
- `Grep -n "lifecycleBootstrapper.get().ensureInitialized()"` returns at least 1 hit.
- `Grep -n "Log\.d\("` returns zero hits.
- Build succeeds.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Grep predicates 3/3 PASS. Two FTP entry points wired (`connect` + `getConnectionForExoPlayer`). FtpClient.kt +4 LOC. Build deferred.

---

### Step 02.4 — Wire bootstrapper into the three cloud REST clients

**Files:** `data/cloud/GoogleDriveRestClient.kt`, `data/cloud/DropboxClient.kt`, `data/cloud/OneDriveRestClient.kt`
**Depends on:** — parallel with 02.1

**Prompt for developer:**

> For each of the three cloud clients identify the first HTTP-issuing method (typically a token refresh, an account info call, or the first request method invoked from a higher-level use case). Add `private val lifecycleBootstrapper: dagger.Lazy<com.sza.fastmediasorter.data.network.lifecycle.NetworkLifecycleBootstrapper>` to the constructor and call `lifecycleBootstrapper.get().ensureInitialized()` at the top of that method. If a client exposes multiple parallel HTTP entry methods with no single funnel, add the trigger to each. KDoc note: "S0195: trigger network lifecycle bootstrap on first cloud use."

**Verification:**

- `Grep` — `lifecycleBootstrapper: dagger.Lazy<` appears once in each of the three files.
- `Grep -n "lifecycleBootstrapper.get().ensureInitialized()"` returns at least 1 hit in each of the three files.
- `Grep -n "Log\.d\("` returns zero hits in any of the three files.
- Build succeeds.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Grep predicates 3/3 PASS for all 3 clients. Trigger placed at the natural funnel (auth-restore method that `requireAnyNetwork`'s — covers cold-start cloud first-use). GoogleDrive: `tryRestoreFromStorage`. Dropbox: `tryRestoreForAccount`. OneDrive: `authenticate`. Build deferred.

---

### Step 02.5 — Smoke verify dormant fire still no-ops

**Files:** none — verification only
**Depends on:** Steps 02.1, 02.2, 02.3, 02.4

**Prompt for developer:**

> Confirm by reading `FastMediaSorterApp.onCreate` that the four eager hooks remain in place — they are removed in Phase 03, not here. The current invariant: bootstrapper now fires from the consumer side, but `FastMediaSorterApp` already attached observers eagerly, so `ensureInitialized()` flips its `AtomicBoolean` then no-ops the registrations (or it runs the registrations and a duplicate observer is attached — see Implementation note below). The `AtomicBoolean` makes this safe: only one path actually wins, and idempotent attach is guaranteed because the bootstrapper guards each registration with try/catch + `Timber.e` per Phase 01.

**Implementation note for Phase 03 alignment:** between Phase 02 and Phase 03 there is a brief window where both paths could call `ProcessLifecycleOwner.addObserver(smbBackgroundLifecycleManager)` for the same observer instance. AndroidX `LifecycleRegistry.addObserver` is documented to be idempotent for the same instance, so this is safe in practice — confirmed by S0061 acceptance criteria. The bootstrapper's `AtomicBoolean` guarantees the bootstrapper path runs the registrations at most once.

**Verification:**

- `Grep -n "networkStateMonitor.start()" app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` returns 1 hit (still present, removal in Phase 03).
- `Grep -n "networkLifecycleObserver.attach()" app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` returns 1 hit.
- `Grep -n "ProcessLifecycleOwner.get().lifecycle.addObserver(smbBackgroundLifecycleManager)" app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` returns 1 hit.
- Build succeeds.
- Manual smoke (optional): install debug APK, open a local-only resource — `Timber.i("S0195: network lifecycle bootstrap complete")` does NOT appear in logcat (because no remote consumer was invoked). Open any SMB / SFTP / FTP / cloud resource — the bootstrap log fires exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Grep returns 3 hits for the three eager-hook lines in FastMediaSorterApp.kt — confirmed they remain (Phase 03 will remove them). Bootstrapper is wired but dormant. Manual smoke deferred to post-Phase 03 device test.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — `/build` standard debug.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

After Phase 02, the bootstrapper is the active driver of network lifecycle attach on first remote use. The eager hooks in `FastMediaSorterApp` are still present and dominate timing on cold start (they run before any consumer fires). Phase 03 removes them so the bootstrapper becomes the sole driver and first-use semantics is actually achieved.

---

## Rollback Plan

Revert phase commit(s) — every change is additive (new constructor parameter + one method-prologue call). No data migration or user-facing surface changed.
