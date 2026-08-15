# Phase 05 — Cloud Gate (Token-Refresh-Only)

**Strategic spec:** [`../S0067_enh-network-stale-connection-invalidation-multi-protocol.md`](../S0067_enh-network-stale-connection-invalidation-multi-protocol.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Add `CloudConnectionGate` covering Google Drive, OneDrive, Dropbox. Per ADR-2: gate manages **token expiry** only — does not close OkHttp pool. Provider-level resourceKey (`cloud://google_drive` / `cloud://onedrive` / `cloud://dropbox`).

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] `GoogleDriveRestClient.kt`, `OneDriveRestClient.kt`, `DropboxClient.kt` exist (verified).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/lifecycle/CloudConnectionGate.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/lifecycle/CloudTokenHandle.kt` | New | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/UnifiedCloudAuthManager.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/NetworkLifecycleModule.kt` | Modified | ≤ 350 |

---

## Steps

### Step 05.1 — Define `CloudTokenHandle`

**Files:** `data/network/lifecycle/CloudTokenHandle.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Define `data class CloudTokenHandle(val provider: String, val resourceKey: String, val accessToken: String, val expiresAtMs: Long)`. Add `val isExpiringSoon: Boolean get() = System.currentTimeMillis() >= expiresAtMs - 60_000L` (60-second margin per strategic §5.1).

**Verification:**

- `Glob` — `CloudTokenHandle.kt` exists.
- `Grep -n "data class CloudTokenHandle"` matches once.
- `Grep -n "isExpiringSoon"` matches once.

**Status:** `[ ]` not done

---

### Step 05.2 — Expose `tokenFor(provider)` + `forceRefresh(provider)` on `UnifiedCloudAuthManager`

**Files:** `data/cloud/UnifiedCloudAuthManager.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add (or expose if existing internally):
>
> - `suspend fun tokenFor(provider: String): CloudTokenHandle?` — returns current handle if available; pulls expiry from existing per-plugin storage.
> - `suspend fun forceRefresh(provider: String): CloudTokenHandle?` — invokes the provider's refresh path (`GoogleDriveAuthPlugin.refresh()` / `OneDriveAuthPlugin.refresh()` / `DropboxAuthPlugin.refresh()`); returns updated handle or `null` on failure.
>
> Both methods are protocol-neutral: provider id `"google_drive" | "onedrive" | "dropbox"`. Internally route to the matching plugin.

**Verification:**

- `Grep -n "suspend fun tokenFor" "UnifiedCloudAuthManager.kt"` matches once.
- `Grep -n "suspend fun forceRefresh" "UnifiedCloudAuthManager.kt"` matches once.
- `/build` `standardDebug` passes.

**Status:** `[ ]` not done

---

### Step 05.3 — Implement `CloudConnectionGate`

**Files:** `data/network/lifecycle/CloudConnectionGate.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Define `@Singleton class CloudConnectionGate @Inject constructor(private val auth: UnifiedCloudAuthManager, private val diagnostics: ConnectionDiagnostics) : NetworkConnectionGate<CloudTokenHandle>`.
>
> - `override val protocol = NetworkProtocol.CLOUD`
> - `acquire(consumer, resourceKey)`:
>   1. Parse provider id from `resourceKey` (e.g. `cloud://google_drive` → `google_drive`).
>   2. `val handle = auth.tokenFor(provider) ?: throw IllegalStateException("Cloud not authenticated: $provider")`.
>   3. If `handle.isExpiringSoon` → `auth.forceRefresh(provider)` and use returned handle; on null throw same `IllegalStateException`.
>   4. Track `lastRecreateMs` if a refresh fired.
> - `release(connection, success)` — on success: update `lastSuccessMs`. On failure: classify via `TransientFailure.classify(t)`. If `TOKEN_EXPIRED` → `auth.forceRefresh(provider)` (the next `withRetry` attempt picks up new token automatically); other reasons recorded in diagnostics, no socket close.
> - `withRetry` — default interface implementation; the second `acquire` benefits from the post-failure `forceRefresh`.
> - `closeFor(consumer)` — **no-op for sockets** (ADR-2). Optionally clears in-memory `lastSuccessMs` for UI consumers so the next op re-evaluates expiry.
> - `lastRecreateMs(resourceKey)` — return tracked timestamp.

**Verification:**

- `Glob` — `CloudConnectionGate.kt` exists.
- `Grep -n "class CloudConnectionGate"` matches once.
- `Grep -n "override val protocol = NetworkProtocol.CLOUD"` matches once.
- `Grep -n "no-op for sockets"` matches once (ADR-2 marker).
- `Grep -n "auth.forceRefresh"` matches at least once.

**Status:** `[ ]` not done

---

### Step 05.4 — Wire into DI registry

**Files:** `core/di/NetworkLifecycleModule.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> Update `provideRegistry` to take `cloud: CloudConnectionGate` parameter and call `register(cloud)`.

**Verification:**

- `Grep -n "register(cloud)" "NetworkLifecycleModule.kt"` matches once.
- `Grep -n "cloud: CloudConnectionGate"` matches once.
- `/build` `standardDebug` passes.

**Status:** `[ ]` not done

---

### Step 05.5 — Provider availability gate (flavor `photos` excluded)

**Files:** `core/di/NetworkLifecycleModule.kt`
**Depends on:** Step 05.4

**Prompt for developer:**

> Wrap `register(cloud)` in a `if (BuildConfig.HAS_CLOUD)` guard if such flag exists; otherwise check the `BuildConfig` field used elsewhere for cloud gating (`HAS_CLOUD` or analogous). If the flavor lacks cloud, skip registration silently — no error.

**Verification:**

- `Grep -n "BuildConfig\\.HAS_CLOUD" "NetworkLifecycleModule.kt"` matches once.
- `/build` `liteDebug` passes (flavor without cloud — gate is omitted).
- `/build` `standardDebug` passes (flavor with cloud — gate is registered).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` is `[x] done`.
- [ ] `/build` `standardDebug` and `liteDebug` PASS.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry per "Files Touched".

---

## Handoff Notes to Next Phase

Registry now holds `{SMB, SFTP, FTP, CLOUD}` (CLOUD only on cloud-enabled flavors). Phase 06 wires `ProcessLifecycleOwner` observer + diagnostics flow → snackbar.

---

## Rollback Plan

Revert phase commit — additive only. Existing per-plugin refresh paths remain in place.
