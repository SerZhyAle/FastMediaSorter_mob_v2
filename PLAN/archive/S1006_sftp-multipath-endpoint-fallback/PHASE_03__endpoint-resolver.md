# Phase 03 - SFTP endpoint resolver (happy-eyeballs)

**Strategic spec:** [`../S1006_sftp-multipath-endpoint-fallback.md`](../S1006_sftp-multipath-endpoint-fallback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Introduce `SftpEndpointResolver`: given a requested host:port it returns the reachable candidate for that resource (LAN-first happy-eyeballs TCP race), cached per network and invalidated on network change. No consumer wiring yet.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`HostPort`, `altAccessPaths` exist).
- [ ] Research 02 (probe tuning) and Research 03 (network signal) read.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpEndpointResolver.kt` | New | ≤ 220 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/remote/sftp/SftpEndpointResolverTest.kt` | New | ≤ 200 |

> Resolver depends on `ResourceDao` (candidate lookup) and `NetworkStateMonitor` (invalidation) - both already `@Singleton`/injectable; no new Hilt `@Module`, use `@Singleton class .. @Inject constructor(..)`.

---

## Steps

### Step 03.1 - Candidate lookup: map a requested host:port to its resource's full endpoint group

**Files:** `data/remote/sftp/SftpEndpointResolver.kt` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Create `@Singleton class SftpEndpointResolver @Inject constructor(private val resourceDao: ResourceDao, private val networkStateMonitor: NetworkStateMonitor)`. Add a private helper that, given a requested `(host, port)`, finds the SFTP resource whose primary `path` host:port OR any `altAccessPaths` entry equals it, and returns the ordered candidate list `[primary] + altAccessPaths` (primary first = LAN-first per contract). If no resource matches (manual resource, unknown host), return a single-element list `[requested]` so behaviour is unchanged. Cache the host->group map in memory; it is cheap to rebuild and only needs the resource rows. Add a `ResourceDao` query for SFTP resources if none returns path+alt (verify existing DAO first; reuse `getAllResources`-style query rather than adding one if present).

**Verification:**

- `Glob` - `SftpEndpointResolver.kt` exists.
- `Grep` - `class SftpEndpointResolver` matches once (declaration).
- `Grep` - `NetworkStateMonitor` referenced.

**Status:** `[ ]` not done

---

### Step 03.2 - Happy-eyeballs probe + resolve API

**Files:** `data/remote/sftp/SftpEndpointResolver.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add `suspend fun resolve(host: String, port: Int): HostPort`. Build the candidate list (Step 03.1). Return the cached winner for the current network epoch if present. Otherwise race candidates concurrently on `Dispatchers.IO`: each probe opens a raw `java.net.Socket().connect(InetSocketAddress(host, port), PROBE_TIMEOUT_MS)` then closes it; a successful probe marks that candidate reachable. Prefer the LAN (first) candidate within a short grace window; otherwise take the first candidate to connect. `PROBE_TIMEOUT_MS` companion const ~2500. If none connect, return the primary (first) candidate unchanged so the normal 10 s SFTP connect surfaces the real error. Cache the winner keyed by the resource group for the current network epoch. Keep every probe/log line <= 120 chars (detekt-clean-first, Rule 19).

**Verification:**

- `Grep` - `fun resolve(` matches once.
- `Grep` - `Socket()` or `InetSocketAddress` referenced (raw TCP probe, not JSch).
- `Grep` - `PROBE_TIMEOUT_MS` companion const defined (no bare numeric literal in the call).

**Status:** `[ ]` not done

---

### Step 03.3 - Invalidate the cache on network change

**Files:** `data/remote/sftp/SftpEndpointResolver.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Implement `NetworkStateMonitor.NetworkChangeCallback` on the resolver and `registerCallback(this)` in an `init` block (app-scoped singleton, never unregistered). On `onNetworkChanged()` and `onNetworkLost()` clear the endpoint-selection cache so the next cold connection re-probes. Do not clear the host->group map (resources did not change) - only the reachability winners.

**Verification:**

- `Grep` - `NetworkChangeCallback` implemented.
- `Grep` - `registerCallback` called.
- `Grep` - `onNetworkChanged` overridden with a cache-clear.

**Status:** `[ ]` not done

---

### Step 03.4 - Unit-test the resolver

**Files:** `data/remote/sftp/SftpEndpointResolverTest.kt` (New)
**Depends on:** Steps 03.1-03.3

**Prompt for developer:**

> With a fake `ResourceDao` (a resource whose primary is an unreachable host and whose alternate is a reachable loopback port bound by the test) and a fake `NetworkStateMonitor`: assert `resolve()` returns the reachable alternate; assert a second call is served from cache (no second probe); assert `onNetworkChanged()` clears the cache so the next `resolve()` re-probes; assert an unknown host resolves to itself unchanged. Use a real `ServerSocket` on `127.0.0.1` for the reachable candidate.

**Verification:**

- `Glob` - `SftpEndpointResolverTest.kt` exists.
- `Grep` - `onNetworkChanged` exercised in the test.
- Run `.\a.ps1 fu` (or the module test task via `/build`) - resolver tests pass.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for the new files.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new `SftpEndpointResolver` class) - may defer to Phase 05.

---

## Handoff Notes to Next Phase

`SftpEndpointResolver.resolve(host, port)` returns the reachable endpoint for a resource group and self-invalidates on network change. Phase 04 injects it into every connection-establishment point.

---

## Rollback Plan

Revert the phase commit(s). The resolver is not yet referenced by any consumer, so removing it is inert.
