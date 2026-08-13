# Phase 04 - Network Access (LAN + Internet Reachability)

**Strategic spec:** [`../S0421_windows-sftp-folder-share-companion.md`](../S0421_windows-sftp-folder-share-companion.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 6 / 6
**Started:** 2026-07-10
**Completed:** 2026-07-10

---

## Objective

Make the SFTP port reachable and discoverable: announce the service on the LAN via mDNS, attempt automatic port-forwarding (UPnP-IGD / NAT-PMP / PCP), determine the external address, and produce a clear manual-forwarding fallback when automation fails. Level A only - no P2P.

---

## Prerequisites

- [ ] Phase 02 ✅ Done (server reports a listen port).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `desktop/windows-companion/internal/netaccess/mdns.go` | New | ≤ 150 |
| `desktop/windows-companion/internal/netaccess/portmap.go` | New | ≤ 300 |
| `desktop/windows-companion/internal/netaccess/externaladdr.go` | New | ≤ 150 |
| `desktop/windows-companion/internal/netaccess/reachability.go` | New | ≤ 200 |
| `desktop/windows-companion/internal/netaccess/portmap_test.go` | New | ≤ 150 |

---

## Steps

### Step 04.1 - mDNS announce

**Files:** `internal/netaccess/mdns.go`
**Depends on:** - start of phase

**Prompt for developer:**

> Announce the SFTP service on the local network with `grandcat/zeroconf` under a service type such as `_sftp-fms._tcp` including the listen port and an instance name derived from the machine name. Provide `StartAnnounce(port int, instance string) (stop func())`. The Android side may discover this later for zero-config LAN connection.

**Verification:**

- `Grep` - `zeroconf.Register` present in `mdns.go`.
- `Grep` - `func StartAnnounce(` present.

**Status:** `[x]` done

---

### Step 04.2 - Automatic port mapping

**Files:** `internal/netaccess/portmap.go`
**Depends on:** - start of phase

**Prompt for developer:**

> Attempt to open the internet path automatically: UPnP-IGD via `huin/goupnp` first, then NAT-PMP/PCP as fallbacks. Request a mapping from an external port to the local listen port with a lease and periodic renewal. Expose `TryMap(localPort int) (externalPort int, method string, err error)` and a `Release()`. Never fail the whole app if mapping fails - return the error for the reachability report.

**Verification:**

- `Grep` - `goupnp` used in `portmap.go`.
- `Grep` - `func TryMap(` present.
- `go build ./...` exits 0.

**Status:** `[x]` done

---

### Step 04.3 - External address detection

**Files:** `internal/netaccess/externaladdr.go`
**Depends on:** Step 04.2

**Prompt for developer:**

> Determine the external-facing address for the exported config: prefer the IGD's reported external IP (from 04.2) when a mapping succeeded; otherwise query a couple of well-known IP-echo endpoints (with timeout, no third-party account) as a best-effort. Detect the CGNAT case (external IP is RFC6598 100.64/10 or differs from the IGD WAN IP) and flag it so the UX can warn that manual forwarding will not help.

**Verification:**

- `Grep` - `func ExternalAddress(` present.
- `Grep` - CGNAT / `100.64` detection token present.

**Status:** `[x]` done

---

### Step 04.4 - Reachability report + fallback guidance

**Files:** `internal/netaccess/reachability.go`
**Depends on:** Step 04.1, 04.2, 04.3

**Prompt for developer:**

> Aggregate LAN + internet reachability into a `Reachability` struct: `{ lanAddress, lanMdnsActive, portMapMethod, externalHost, externalPort, isCgnat, manualForwardHint }`. When auto-mapping fails or CGNAT is detected, populate `manualForwardHint` with the concrete router-forwarding instruction (which internal IP:port to forward) and a DDNS suggestion. This struct feeds Phase 05 export and Phase 06 onboarding.

**Verification:**

- `Grep` - `type Reachability struct` present.
- `Grep` - `manualForwardHint` field present.

**Status:** `[x]` done

---

### Step 04.5 - Wire reachability into the worker + IPC status

**Files:** `internal/netaccess/reachability.go` (exported API consumed by service)
**Depends on:** Step 04.4, Phase 03

**Prompt for developer:**

> Have the worker call reachability on server start and on demand, and extend the IPC `Status` (Phase 03) with the reachability fields so the tray UI can show LAN vs internet state. Do not create an import cycle: `netaccess` must not import `service`; the worker imports `netaccess`.

**Verification:**

- `Grep` - `netaccess` imported by `internal/service/worker.go`.
- `Grep` - no `internal/service` import inside `internal/netaccess/` package.

**Status:** `[x]` done

---

### Step 04.6 - Port-map unit test (mocked IGD)

**Files:** `internal/netaccess/portmap_test.go`
**Depends on:** Step 04.2

**Prompt for developer:**

> Unit-test the mapping decision logic with a mocked IGD client: success path returns external port + method; failure path returns an error without panicking and leaves the app runnable. Test the CGNAT flag derivation from 04.3 with synthetic addresses.

**Verification:**

- `go test ./internal/netaccess/ -run TestPortMap` passes.
- `go test ./internal/netaccess/ -run TestCgnatFlag` passes.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` is `[x] done`.
- [x] `go build ./...` and `go test ./internal/netaccess/...` pass (2026-07-10: 5/5 PASS - PortMapSuccessViaUpnp, PortMapFailureLeavesAppRunnable, PortMapNatPmpFallback, CgnatFlag, CgnatDetectionFromSyntheticAddresses).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry: batched at ticket closure via `close-and-log.ps1` (out-of-repo subproject).

**Execution notes (2026-07-10, /spec-all):**

- NAT-PMP fallback needed two extra tiny deps beyond the 01.3 pinned set: `jackpal/go-nat-pmp` + `jackpal/gateway` (goupnp is UPnP-IGD only). PCP not separately implemented - NAT-PMP is its subset protocol and PCP routers answer NAT-PMP; revisit only if field data shows PCP-only routers.
- UPnP: IGDv2 `WANIPConnection2` preferred, IGDv1 fallback; both behind an `igdClient` adapter interface that the unit tests mock (`discoverIgd`/`mapNatPmp`/`echoQuery` injection vars).
- Mapping holds a 1h lease renewed every 25min in a goroutine; `Release()` idempotent.
- CGNAT detection: RFC 6598 `100.64/10` OR private "WAN" IP (double NAT) OR echo-IP != IGD-IP; hint text honestly says forwarding cannot help under CGNAT.
- `mDNS` service type `_sftp-fms._tcp`, instance `FastMediaSorter Companion on <hostname>`.
- Worker runs reachability async after server start (UPnP/echo probing takes seconds; control channel never blocks); stale results discarded if the server stopped/moved port meanwhile; mDNS + mapping torn down in `stopServer`.
- IPC `Status.Reachability` populated (field was pre-declared in Phase 03 protocol).

---

## Handoff Notes to Next Phase

Reachability (LAN mDNS + best-effort internet mapping + external address + CGNAT flag + manual hint) is computable and surfaced in IPC status. Phase 05 packages the reachable address + host-key fingerprint + credential into the exportable resource config.

---

## Rollback Plan

Revert phase commit(s). Release any active port mapping (`Release()`) before revert. No Android or data changes.
