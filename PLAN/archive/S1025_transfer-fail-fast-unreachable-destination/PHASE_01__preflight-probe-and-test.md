# Phase 01 - preflight-probe-and-test

**Goal:** One destination-reachability probe before dispatch; abort batch on failure. Preserve everything else.

## Steps

- [ ] **1.1** Inject a reachability checker into `domain/usecase/FileOperationUseCase.kt`. Reuse `data/network/SmbErrorClassifier.checkConnectivity(host, port, timeoutMs)` (protocol-agnostic TCP connect). Inject `SmbErrorClassifier` (or extract a tiny `HostReachabilityChecker` wrapping it) via the constructor; update the Hilt provision + `FileOperationUseCaseTest` constructor. Verify: compiles; test constructor updated.
- [ ] **1.2** In `executeInternal`, BEFORE the dispatch `val result = when {...}` (~:260): if the op is `Copy`/`Move` AND `operation.destination.path` is a network scheme in {smb, sftp, ftp} (use `PathUtils`), parse host+port (default 445/22/21) and run `checkConnectivity(host, port, 3000)` ONCE. On failure, return `FileOperationResult.Failure` with the new "destination unreachable" message res - do NOT enter any handler loop. Cloud destination, Delete/Rename, and local: SKIP the probe (unchanged). Do NOT remove or alter the in-loop per-file precheck/retry. Keep the existing `S1028:` tag intact. Verify: probe runs only for network Copy/Move; other ops unaffected; compiles.
- [ ] **1.3** Add string `transfer_destination_unreachable` across EN/RU/UK via `scripts/utils/set-android-string.ps1 -Action add` (EN "Destination server is unreachable - transfer aborted"; RU/UK equivalents, `..` not `...`, ё). Verify: `scripts/check_strings_localized.ps1 -KeyPrefix "transfer_destination_unreachable"` exit 0.
- [ ] **1.4** Unit test in `FileOperationUseCaseTest`: Copy to an unreachable smb destination (mocked checkConnectivity=false) -> `Failure` with the new message AND no handler loop entered; reachable (true) -> proceeds to dispatch; local/cloud destination -> probe not called. Verify: `gradlew testStandardDebugUnitTest --tests "*FileOperationUseCaseTest*"` PASS.

## Done criteria
- Pre-flight probe aborts unreachable network Copy/Move before the loop; everything else unchanged; test green.
