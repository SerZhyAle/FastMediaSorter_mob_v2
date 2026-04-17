# Specification: Enhanced SMB Host Discovery In Add Resource Flow

**Status**: Draft
**Version**: 1.1
**Author**: FastMediaSorter Team
**Date**: April 2026
**Module**: `ui/addresource/`, `domain/usecase/`, `data/network/`

---

## 1. Overview

This specification defines an enhancement of the existing SMB discovery flow used when adding a network resource. The current product already contains:

- a local network host scan dialog
- real-time host list updates during scanning
- SMB share scan for a selected server

The problem is that the current flow still misses many valid SMB targets and many valid shares because host probing is limited to the detected subnet and share enumeration is constrained by SMBJ library limitations.

This document refines the scope so it matches the current architecture and clearly separates:

- host discovery on the local network
- share discovery on a selected SMB host
- optional future handling for administrative shares

---

## 2. Problem Statement

- **Current State**: local network scan can find hosts, but coverage is incomplete and results are limited to the detected subnet.
- **Current State**: SMB share scan uses a common-name trial approach and may miss custom-named shares.
- **Technical Constraint**: SMBJ `0.12.1` does not provide reliable full share enumeration through a public API.
- **User Impact**: users may fail to find a valid SMB server, or may find the server but still not see the expected share.

---

## 3. Functional Requirements

### 3.1 Enhanced Discovery Mechanism

| Req ID | Description | Priority |
|--------|-------------|----------|
| F-01 | Scan the currently detected IPv4 subnet first as the default host-discovery path | HIGH |
| F-02 | Optionally scan fallback private subnets `192.168.0.*` and `192.168.1.*` when current-subnet discovery is empty or clearly incomplete | HIGH |
| F-03 | Probe SMB port `445` first; probe `139` only as an SMB fallback path, not as a separate protocol result | HIGH |
| F-04 | Stream discovered hosts to the UI in real time while scanning is in progress | HIGH |
| F-05 | Provide an explicit stop/cancel action for an active host scan | HIGH |
| F-06 | Reuse the selected discovered host to prefill the SMB server field and continue with share scan | HIGH |
| F-07 | Enumerate accessible regular shares for the selected host through authenticated share scan | HIGH |
| F-08 | Keep manual share entry as a first-class fallback when automatic share discovery is incomplete | HIGH |
| F-09 | Treat administrative shares (`C$`, `D$`, `ADMIN$`, `IPC$`) as opt-in advanced behavior only | MEDIUM |
| F-10 | Persist recently discovered hosts locally for faster re-selection in a future iteration | LOW |

### 3.2 User Interface

The enhanced flow must extend the existing SMB add-resource experience rather than introduce a separate screen.

- **Entry point**: existing Add Resource SMB flow
- **Discovery surface**: existing network discovery dialog
- **Scan button**: starts host discovery
- **Results list**: shows discovered hosts in real time
- **Stop button**: cancels the active host scan
- **Host selection**: returns the chosen host to the SMB form
- **Scan Shares button**: continues with authenticated share discovery for the selected host
- **Advanced toggle**: optional future control for showing administrative shares

### 3.3 Administrative Share Support

Administrative shares must not be treated as a guaranteed baseline feature.

- They usually require elevated rights.
- They are filtered out by the current `SmbClient.listShares()` implementation.
- SMBJ does not expose a robust full share-enumeration API for this use case.

If implemented later, administrative shares must be:

- disabled by default
- shown only after explicit user opt-in
- clearly labeled as advanced or admin-only
- allowed to fail silently without breaking normal share discovery

---

## 4. Non-Functional Requirements

| Req ID | Description | Target |
|--------|-------------|--------|
| NF-01 | Host TCP probe timeout per port | 200-500 ms |
| NF-02 | Authenticated SMB share-scan timeout per selected host | 2-3 seconds |
| NF-03 | Maximum concurrent host probes | 10-20 |
| NF-04 | UI update latency for discovered hosts | <500 ms |
| NF-05 | Cancellation response time | <1 second |
| NF-06 | Memory footprint for discovered-host cache | <10 MB |

---

## 5. Flavor Scope

Flavor support must reflect current build flags and current UI behavior.

| Flavor | Support | Notes |
|--------|---------|-------|
| `standard` | FULL | SMB add-resource flow is available |
| `lite` | BLOCKED | Network resource entry is hidden together with cloud/network capability gates |
| `photos` | PARTIAL | SMB flow remains available at build/UI level, but media-type options are limited by flavor flags |
| `legacy` | FULL | Same SMB flow, with lower API baseline |

Notes:

- The repository does **not** currently show a dedicated SMB-only flavor gate.
- In current UI logic, network resource visibility is tied to broader network/cloud capability handling.
- `photos` must not be marked blocked unless product logic is changed to explicitly disable SMB there.

---

## 6. API Level Analysis

| API Level | Requirement | Constraint |
|-----------|-------------|-----------|
| minSdk 26 | Standard/Lite/Photos host discovery and SMB access | Uses normal TCP probing and SMB client access |
| minSdk 23 | Legacy flavor support | Legacy remains in scope for SMB flow |
| All supported API levels | `INTERNET` permission | Required for TCP probing and SMB communication |
| Optional future path | `ACCESS_NETWORK_STATE` / connectivity APIs | Only needed if subnet detection moves to connectivity-specific APIs |

Notes:

- This feature is based on unicast probing, not broadcast discovery.
- mDNS/Bonjour must not be treated as a baseline dependency for this scope.

---

## 7. Architecture & Design

### 7.1 Component Interaction

The enhancement must follow the existing architecture and reuse existing flow boundaries:

```text
AddResourceActivity / SMB form
    -> NetworkDiscoveryDialog
    -> AddResourceViewModel
        -> DiscoverNetworkResourcesUseCase        (host discovery)
        -> SmbOperationsUseCase.listShares()      (share discovery)
            -> SmbClient.listShares()
                -> SmbConnectionManager / SMBJ
```

For resource creation after discovery:

```text
Selected host/share
    -> AddResourceViewModel
        -> SmbOperationsUseCase.saveCredentials()
        -> MediaResource persistence
```

### 7.2 Scanning Strategy

1. Detect the active local IPv4 subnet.
2. Probe that subnet first for TCP reachability on SMB-oriented ports.
3. Emit discovered hosts immediately to the dialog list.
4. Allow the user to stop scanning at any time.
5. After host selection, perform authenticated SMB share discovery against that host.
6. If automatic share discovery is incomplete, preserve manual share entry as fallback.
7. Only if explicitly enabled later, attempt advanced administrative-share probing.

### 7.3 Key Classes

- **`AddResourceViewModel`**: owns scan state and discovered host list
- **`NetworkDiscoveryDialog`**: displays real-time host results and selection UI
- **`DiscoverNetworkResourcesUseCase`**: performs host discovery over the local network
- **`SmbOperationsUseCase`**: orchestrates SMB test and share-scan operations
- **`SmbClient`**: performs SMBJ-backed connection and trial share enumeration
- **`SmbConnectionManager`**: manages SMB client and pooled connection reuse
- **`NetworkHost`**: host-discovery model returned to the UI

The following class names must not be presented as if they already exist, because they are not current repository source-of-truth objects for this feature:

- `DiscoverSMBHostsUseCase`
- `SMBDiscoveryRepository`
- `SMBNetworkDataSource`
- `NetworkScanViewModel`
- `AdminShareFilter`
- `SMBHost`

---

## 8. Implementation Notes

### 8.1 Existing Baseline

The current repository already contains:

- `DiscoverNetworkResourcesUseCase` for local host scan
- `NetworkDiscoveryDialog` for real-time host display
- `SmbOperationsUseCase.listShares()` for share scan on a selected server
- `SmbClient.listShares()` using SMBJ-backed trial connections against common share names

### 8.2 SMBJ Limitation

The specification must explicitly preserve this constraint:

- SMBJ does not provide reliable full share enumeration for arbitrary SMB servers in this flow.
- Current share discovery is a best-effort common-name probe.
- Custom share names can still require manual entry.

Therefore this spec must not promise:

- guaranteed full share enumeration
- guaranteed discovery of hidden shares
- guaranteed discovery of administrative shares

### 8.3 Concurrency And Cancellation

- Use Kotlin coroutines on `Dispatchers.IO` for host probing.
- Keep probe concurrency bounded.
- Cancellation must propagate through the active scan job, not only hide the dialog.
- Real-time results should continue using streaming semantics rather than waiting for the full subnet to finish.

### 8.4 Permissions

Baseline permission:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

Do not mark `CHANGE_NETWORK_STATE` as required for this feature unless implementation scope changes to actively modify network state.

---

## 9. Testing Plan

### 9.1 Unit Tests

- Validate IPv4 subnet extraction and host-range generation.
- Validate port-probe logic for `445` and optional fallback `139`.
- Validate cancellation during active host discovery.
- Validate share-scan behavior when SMBJ returns partial or empty results.
- Validate that manual share entry remains available after failed discovery.

### 9.2 Integration Tests

- Scan against a real Windows SMB host on the current subnet.
- Verify host discovery against fallback `192.168.0.*` and `192.168.1.*` only when fallback logic is enabled.
- Verify selected host is transferred correctly into the SMB add-resource form.
- Verify share scan returns accessible regular shares for known test credentials.
- Verify custom share names still work through manual entry when auto-scan misses them.

### 9.3 E2E (Maestro Smoke)

- Navigate to Add Resource -> SMB.
- Tap Scan Network and verify hosts appear in real time.
- Tap Stop and verify scan halts quickly.
- Select a discovered host and verify the server field is populated.
- Tap Scan Shares and verify accessible shares appear.
- Add a resource manually when share auto-discovery does not return the expected share.

Remove from baseline smoke scope unless separately implemented:

- admin share toggle
- long-press host details

---

## 10. Accessibility Considerations

- Announce scan start, host discovery, and scan completion or cancellation for screen readers.
- Keep the stop action visible and reachable while scanning.
- Maintain minimum 48dp touch targets.
- Ensure the live-updating host list remains readable at larger font scales.

---

## 11. Architecture Decision Records (ADRs)

### ADR-1: Parallel Probing vs Sequential Scan

**Decision**: Use bounded parallel probing.
**Rationale**: Sequential `/24` probing is too slow; bounded concurrency preserves responsiveness without flooding the device or network.

### ADR-2: Detected Subnet First vs Always Scan Multiple Hardcoded Ranges

**Decision**: Scan the detected subnet first, then optionally probe fallback private ranges.
**Rationale**: This matches the existing implementation model, reduces noisy scanning, and keeps runtime bounded on normal home networks.

### ADR-3: Best-Effort Share Discovery vs Guaranteed Share Enumeration

**Decision**: Keep share discovery best-effort and preserve manual entry.
**Rationale**: SMBJ limitations make guaranteed enumeration unrealistic without a different protocol strategy or library.

### ADR-4: Administrative Shares As Advanced Scope

**Decision**: Keep admin-share visibility out of baseline scope.
**Rationale**: These shares require elevated rights, are commonly hidden, and are explicitly filtered by the current implementation.

---

## 12. Success Criteria

- [ ] Host discovery finds the majority of active SMB-capable hosts on the active subnet within a bounded scan time.
- [ ] Scan can be stopped within 1 second of user action.
- [ ] Selected host is transferred cleanly into the SMB add-resource form.
- [ ] Accessible regular shares are listed for known-good test credentials.
- [ ] Manual share entry still succeeds when automatic share discovery is incomplete.
- [ ] No crashes or ANRs occur during repeated scans.
- [ ] Maestro smoke path passes for discover -> stop -> select host -> scan shares.

Optional future success criteria, not baseline:

- [ ] Admin shares can be shown through an explicit advanced toggle when permissions allow it.
- [ ] Recently discovered hosts are cached for quick re-entry.

---

## 13. Dependencies & Constraints

| Dependency | Version | Notes |
|------------|---------|-------|
| SMBJ | `0.12.1` | Current SMB client library; share enumeration is limited |
| Kotlin Coroutines | Project-aligned | Required for concurrent probing and streaming results |
| Timber | Current | Required logging approach |

Key constraint:

- Full SMB share enumeration is constrained by SMBJ library capabilities in the current stack.

---

## 14. Open Questions / Future Work

1. Should fallback private-range scanning be automatic, or user-triggered after an empty first pass?
2. Should recently discovered hosts be cached in Room for quick reuse?
3. Should admin-share probing be exposed only in debug or advanced settings?
4. Is a different library or protocol path required if full share enumeration becomes a hard requirement?
5. Should IPv6 discovery remain out of scope until a separate network-spec revision?

---

## 15. References

- [SMBJ Documentation](https://github.com/hierynomus/smbj)
- `dev/NETWORK_SPECS.md` - network protocol notes
- `docs/TECH_STACK.md` - dependency overview
- `docs/ARCHITECTURE.md` - layered architecture rules
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DiscoverNetworkResourcesUseCase.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SmbOperationsUseCase.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbClient.kt`
