# S1025 research 01 - pre-flight probe insertion map

**Date:** 2026-07-14
**Owner decision:** single pre-flight reachability probe before the per-file loop; abort whole batch on one failure. Keep in-loop retry.

## The single insertion point

`domain/usecase/FileOperationUseCase.kt` `executeInternal` (:212), BEFORE the dispatch `val result = when {...}` (:260). This is the ONLY site that sees every operation once before any loop - there are 6 downstream per-file loops (base Copy/Move + SMB/SFTP/FTP `executeMove` overrides + Cloud `executeCopy`/`executeMove`), each with its own `forEachIndexed`, so a probe at a handler level would miss the others. Covers BOTH the foreground dialog path and the background `BrowseFileTransferWorker` (both funnel through `executeInternal`).

## Reusable primitive

`data/network/SmbErrorClassifier.kt` `checkConnectivity(host: String, port: Int, timeoutMs: Int): Boolean` (:131-143) - protocol-agnostic `Socket().connect(InetSocketAddress(host, port), timeoutMs)`; only the log text is SMB-flavored. Already used per-fresh-connection by `SmbConnectionManager` (:828). Reuse for the batch pre-flight. Timeout: `CONNECTIVITY_CHECK_TIMEOUT_MS = 3000` (SmbConnectionManager:98).
Precedent for the exact single-probe idea (narrower): `ui/player/FileOperationsHandler.checkSmbDestinationReachability` (:302-327).

## Host/port derivation

Read `operation.destination.path` DIRECTLY (not the conflated `hasSmbPath` flags which OR source+destination). Parse scheme + host:port from the destination URI:
- `smb://host[:445]/..`, `sftp://host[:22]/..`, `ftp://host[:21]/..`. Default ports 445/22/21.
- Delete/Rename: no separate destination -> SKIP (out of scope, operate in-place).
- Cloud destination: SKIP the TCP probe - Cloud keeps its existing `checkAuthenticationRequired` gate (`CloudFileOperationHandler.kt:115-119/205-209`).

## Terminal result + UI

Return `FileOperationResult.Failure` with a dedicated "destination unreachable" message res. Existing Failure handling already surfaces it with ZERO plumbing changes: foreground `FileOperationDestinationDialog.handleOperationResult` (:495-530) + background `BrowseFileTransferWorker.buildTerminalEvent`/`postResultNotification` (:197-202/:383-386) + `BrowseFileOperationsManager.handleTerminalEvent`. Do NOT add a new sealed variant (would need a new `when` branch in every consumer). `FileOperationProgressDialog` itself just dismisses on Completed - messaging flows through the caller, which the Failure path already does.

## Keep in-loop retry (separation)

The new pre-flight probe is additive and separate from `SmbConnectionManager.withConnection`'s per-file precheck (:325-342) - the in-loop retry for transient errors stays. Minor: on the first file the same host may be connect-tested twice (probe + first-file precheck) - acceptable latency, owner accepted the tradeoff.

## Injection

`FileOperationUseCase` must reach `checkConnectivity`. Inject `SmbErrorClassifier` (or extract a tiny `HostReachabilityChecker`) into FileOperationUseCase's constructor - update the Hilt provision + any test constructor (FileOperationUseCaseTest exists).

## Test

`FileOperationUseCaseTest` (exists) - add: Copy to an unreachable smb destination -> Failure(destination-unreachable) with NO per-file loop entered; local/cloud destination -> probe skipped; reachable destination -> proceeds. Mock the reachability check.

## Minimal change set
1. Pre-flight probe block in `executeInternal` before dispatch (smb/sftp/ftp Copy/Move only).
2. Inject the reachability checker.
3. New string `transfer_destination_unreachable` EN/RU/UK.
4. Unit test.
