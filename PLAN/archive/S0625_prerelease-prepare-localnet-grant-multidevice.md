# S0625 - prerelease-prepare ACCESS_LOCAL_NETWORK grant skipped on multi-device adb

**Status:** Archived

> Parked by `/spec-prerelease` (auto-capture, CLAUDE.md 3.1). Out-of-scope of the sweep verdict.

## 0. Raw capture

Symptom: on a clean `/spec-prerelease` install every SMB/SFTP/FTP folder scan fails fast with
`LocalNetworkPermissionDeniedException: Local network access permission not granted`, leaving network
resources unusable until the permission is granted by hand.

Root cause: `scripts/devtest/prerelease-prepare.ps1` stage 2.6 (S0614) grants `ACCESS_LOCAL_NETWORK`
only when `ro.build.version.sdk >= 37`. It reads the SDK with `adb shell getprop` scoped to `-s` ONLY
when `-DeviceId` was passed. When the sweep calls prepare without `-DeviceId` and more than one device
is attached (here: phantom offline `emulator-5554` / `emulator-5558` siblings beside the live
`emulator-5556`), `adb shell` is ambiguous, returns empty, `$deviceSdk` stays `0`, and the grant falls
into the SKIP branch - so the permission is never granted on a device that actually needs it.

Evidence:
- prepare JSON stage: `{"name":"local-network-grant","status":"SKIP","detail":"runtime permission needs API 37+ (device API 0); auto-granted below that"}` while the device is real API 37.
- logcat: `E BrowseLoadingManager$loadFilesStandard: com.sza.fastmediasorter.data.network.exceptions.LocalNetworkPermissionDeniedException` at `SftpMediaScanner.kt:51`, scan fails after 18ms.
- Manual `pm grant com.sza.fastmediasorter.debug android.permission.ACCESS_LOCAL_NETWORK` then refresh -> SFTP lists 30 files in 744ms.

Related: S0614 (the grant itself is correct; it is defeated by the device-scoping/multi-device gap).

## 2. Goals (rough)

- Scope every adb call in `prerelease-prepare.ps1` to the device-readiness-resolved id (do not depend on `-DeviceId` being passed).
- Fail loud (not SKIP) when the SDK read returns empty / non-numeric, so a misdetect can never silently skip a required runtime grant.
- Consider a device-singleton pre-flight (kill/clear offline `emulator-55xx` entries) before prepare.

## 3. Implementation

- After stage 1 the script resolves a concrete `$TargetDevice` from `device-ready.ps1`'s `selectedDevice`.
- `device-ready.ps1` only counts ONLINE devices, so it already picks the single live emulator even when offline `emulator-55xx` siblings are attached and `-DeviceId` was omitted.
- Every downstream adb call (uninstall, install, onboarding-bypass, API probe, seed probe, launch, log) is pinned to `$TargetDevice` unconditionally - the old `if ($DeviceId)` gating is gone.
- `$env:ANDROID_SERIAL` is set to `$TargetDevice` unconditionally (was only set when `-DeviceId` was passed).
- Stage 1 still forwards a caller-supplied `-DeviceId` to `device-ready.ps1`, so explicit narrowing among several ONLINE devices keeps working.
- Stage 2.6 API probe is scoped with `-s $TargetDevice`; a non-numeric `getprop` result now fails the stage loud (`Complete-Run 10`) instead of zeroing `$deviceSdk` into the SKIP branch.
- A new `device-resolve` guard fails loud if `selectedDevice` came back empty, so no unscoped adb ever runs.

## 4. Decision on goal 3 (device-singleton pre-flight)

- Not implemented. Scoping every call to the resolved id makes offline `emulator-55xx` siblings irrelevant to prepare, so the ambiguity that caused the skip is removed at the source.
- Killing/clearing emulator entries from a prepare helper is too invasive: it would disrupt other sessions the dev may be running in parallel and is not needed once calls are scoped.

## 5. Validation

- `[Parser]::ParseFile` on `prerelease-prepare.ps1`: PARSE OK.
- On the live API-37 `emulator-5556` (the device class from the bug report): resolved `TargetDevice=emulator-5556`, scoped `getprop ro.build.version.sdk` returned `37`, grant branch evaluates true. Previously, with offline siblings present, the unscoped probe returned empty and dropped into SKIP.

## Last Audit

**Date:** 2026-06-23
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 16 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 2

### Notes

- `prerelease-prepare.ps1`: `$TargetDevice = $script:result.selectedDevice` (108); `device-resolve` guard fails loud on empty (109-111, `Complete-Run 10`); `$env:ANDROID_SERIAL` set unconditionally (117); caller `-DeviceId` still forwarded to `device-ready.ps1` (86).
- Every adb call pinned to `$TargetDevice`: uninstall (120), install (135), onboarding-bypass (151), API probe `-s $TargetDevice` (170-171), seed probe (194), launch (213), log (224). No surviving `if ($DeviceId)` adb-scoping gate.
- Grant branch fixed: non-numeric `getprop` -> `local-network-grant` FAIL + `Complete-Run 10` (172-176) instead of zeroing into SKIP; API>=37 runs `pm grant ACCESS_LOCAL_NETWORK` with fail-loud (179-186); API<37 SKIP (auto-granted).
- Supporting claim verified: `device-ready.ps1` counts only ONLINE devices (`$parts[1] -eq 'device'`, line 136), so offline `emulator-55xx` siblings never enter selection.
- §2 goal 3 (device-singleton pre-flight) EXEMPT by design (§4 rationale - scoping removes the ambiguity at source; killing emulator entries is too invasive).
- FEATURES trilingual EXEMPT: internal prerelease tooling fix, no user-visible showcase capability.
- `[Parser]::ParseFile` re-run: PARSE OK.

### Manual / on-device

- [ ] On a live API-37 device with offline `emulator-55xx` siblings attached and `-DeviceId` omitted: a clean prepare run grants `ACCESS_LOCAL_NETWORK` and SMB/SFTP/FTP folder scans list files (no `LocalNetworkPermissionDeniedException`).
