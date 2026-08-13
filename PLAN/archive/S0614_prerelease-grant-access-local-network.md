# S0614 - prerelease sweep should grant ACCESS_LOCAL_NETWORK on API >= 37

**Status:** Archived

## 0. Raw capture

Parked from a `/spec-prerelease` sweep on emulator-5554 (Pixel Tablet, Android 17 / API 37), build 2.60.6211.547-DEBUG.

Symptom: on the freshly-installed standard-debug app, opening any network resource (SMB / SFTP / FTP) returns an empty listing ("0 files") and logs an app-side error. First SFTP open of `sftp://193.178.50.43:22/data` failed instantly:

```
BrowseLoadingManager: START loading - resource='SFTP' (id=18), type=SFTP
BrowseLoadingManager$loadFilesStandard: ERROR in flow - Exception (after 11ms)
com.sza.fastmediasorter.data.network.exceptions.LocalNetworkPermissionDeniedException: Local network access permission not granted
BrowseLoadingManager: Flow COMPLETE after 13ms - final batch: 0 files
```

Note: the TCP reachability probe and the in-app connection test both PASSED ("Connection test OK: SFTP connection successful to 193.178.50.43:22 using password") - only the scan path threw, because the scan is gated.

Root cause (verified in code): every network scanner + network Glide loader gates on `PermissionHelper.hasLocalNetworkPermission(context)`, which checks the runtime permission `android.permission.ACCESS_LOCAL_NETWORK`. The manifest declares it with `android:minSdkVersion="37"`, so on API >= 37 it is a runtime-granted permission. Throw sites: `SftpDataSource`, `SmbDataSource`, `FtpDataSource`, `SmbMediaScanner`, `FtpMediaScanner`, `NetworkEpubCoverLoader`, `NetworkPdfThumbnailLoader`, `NetworkVideoFrameDecoder`.

Why the sweep hit it: the app requests this permission during onboarding (`PermissionHelper.requestLocalNetworkPermission`, REQUEST_CODE_LOCAL_NETWORK=105), but `prerelease-prepare.ps1` sets `welcome_completed=true` to bypass onboarding, and grants only `MANAGE_EXTERNAL_STORAGE` - never `ACCESS_LOCAL_NETWORK`. So on any API >= 37 target, the sweep's network-listing step (step 6) fails spuriously on a clean install.

Workaround used mid-sweep: `adb shell pm grant com.sza.fastmediasorter.debug android.permission.ACCESS_LOCAL_NETWORK`. After granting, the same SFTP resource listed 30 files in 726 ms and network audio streamed/played fine - confirming the network path itself is healthy and the app behaviour is correct (it correctly gates network access behind the permission).

Evidence:
- Log: `temp/s0484_run_20260622_090821.log` (search `LocalNetworkPermissionDeniedException`).
- This was the only genuine app-process actionable cluster in the run; everything else in the audit was emulator/system noise.

## 1. Problem

The pre-release sweep harness grants `MANAGE_EXTERNAL_STORAGE` but not `ACCESS_LOCAL_NETWORK`. Combined with the onboarding bypass, this makes the network-listing scenario step (and any SMB/SFTP/FTP coverage) fail on a clean install on API >= 37, even though the app is behaving correctly. The failure is masked from the coarse verdict (it counted 0 app-actionable errors) and only surfaced via the detailed log audit + manual root-cause.

## 2. Proposed direction (Draft - not approved)

- Add a guarded grant stage to `prerelease-prepare.ps1` (mirroring the storage grant): when target API >= 37, `pm grant <pkg> android.permission.ACCESS_LOCAL_NETWORK`, and report it in the prepare `stages` JSON.
- Consider whether `prerelease-configure.ps1` reachability pre-check should also assert the permission is granted before delegating the listing step, so a missing grant is reported as a setup failure rather than a content failure.
- Out of scope: no app code change - the app gating is correct.

## 3. Notes

- Related: S0046 (sftp-key-auth-hardening), S0529 (network-audio-always-continue) - both touch network resource access but neither covers the sweep permission grant.
- API-version-sensitive: only API >= 37 targets are affected; lower-API emulators return `true` from `hasLocalNetworkPermission` unconditionally.

## 4. Implementation

- `prerelease-prepare.ps1`: new `local-network-grant` stage (2.6), runs after install + onboarding bypass.
- Resolves adb via a local `Get-Adb` helper (mirrors `prerelease-configure.ps1` / `device-ready.ps1`).
- Reads device API from `getprop ro.build.version.sdk`; on API >= 37 runs `pm grant <pkg> android.permission.ACCESS_LOCAL_NETWORK`, FAIL + exit 10 on a non-zero grant, OK otherwise; below API 37 the stage is `SKIP` (permission auto-granted, declared minSdk 37).
- Stage outcome is reported in the prepare `stages` JSON like every other stage.
- No app code change - the in-app permission gating is correct.
- `prerelease-configure.ps1` reachability assertion (proposal bullet 2) intentionally not added: the prepare grant now makes the permission deterministically present, so a redundant configure-side check would only duplicate it.
- Verification: requires a clean-install `/spec-prerelease` sweep against an API >= 37 target; the grant stage must report OK and the network-listing step must list files instead of throwing `LocalNetworkPermissionDeniedException`.

## Last Audit

**Date:** 2026-06-23
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Notes

- `prerelease-prepare.ps1`: `local-network-grant` stage 2.6 present (header line 9), runs after install + onboarding-bypass; adb resolved via `$adb`/`$adbTarget`; reads `getprop ro.build.version.sdk`; API>=37 -> `pm grant ACCESS_LOCAL_NETWORK` with FAIL + `Complete-Run 10` on non-zero grant (180-184); below 37 -> `SKIP` (188); every branch reported through `Add-Stage 'local-network-grant'` into the stages JSON.
- No app code change (the in-app `PermissionHelper.hasLocalNetworkPermission` gating is correct by design); the only edits are in the sweep harness script.
- The same stage was later hardened by S0625 (device scoping + non-numeric getprop fail-loud at 174); both are reflected in the live code consistently.
- Debug-tag invariant PASS: zero `Timber.d("S0614:` tags (status Implemented).
- FEATURES trilingual EXEMPT: internal pre-release sweep tooling, no user-visible showcase change.

### Manual / on-device

- [ ] Clean-install `/spec-prerelease` sweep on an API>=37 target: `local-network-grant` reports OK and the network-listing step lists SMB/SFTP/FTP files instead of throwing `LocalNetworkPermissionDeniedException`.
