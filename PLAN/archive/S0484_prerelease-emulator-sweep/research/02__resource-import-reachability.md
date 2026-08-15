# Research §6.2 - Resource import path + endpoint reachability

**Strategic item:** §6.2
**Status:** Resolved
**Date:** 2026-06-17

## Question

How to register the predefined resources from `sza_resources.xml` on an emulator, and which endpoints are reachable.

## Findings

- Parser/importer: `SzaResourcesImporter` (`app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/SzaResourcesImporter.kt`). Reads `R.xml.sza_resources`, upserts credentials + `MediaResource` rows.
- **Only trigger is UI:** `GeneralSettingsViewSetupHelper` watches the `etDefaultUser` field; typing the value of `BuildConfig.OWNER_TRIGGER` (sourced from `local.properties` key `sza.owner.trigger`, value `"sza"` on the owner's machine) shows a confirm dialog whose positive button calls `SettingsViewModel.importSzaResources()`. There is **no** adb broadcast / deep-link / debug-activity hook.
- `OWNER_TRIGGER` is empty string when `local.properties` is absent, which disables the dialog entirely.
- No per-resource success log in `importOne` (only skips/errors log). Listing success marker is definitive: `BrowseLoadingManager: COMPLETE - N files loaded and displayed` (`BrowseLoadingManager.kt:271`).
- Reachability from a standard emulator (NAT sandbox):
  - LOCAL `Downloads` (`/storage/emulated/0/Download`) - always present.
  - SMB entries (`192.168.1.x`) - **not reachable** from emulator NAT.
  - SFTP `SFTP` (`sftp://193.178.50.43:22`) and FTP `FTP` (`ftp://193.178.50.43:21`) - public IPs, reachable subject to server uptime.
  - SFTP key-auth `SFTP S0046 key` (`192.168.1.110`) - LAN, not reachable.

## Decision (updated 2026-06-17 - intent-push confirmed)

A second, cleaner import path was confirmed in the manifest and chosen over the OWNER_TRIGGER UI path:

- `ResourceImportActivity` (`ui/resourceimport/ResourceImportActivity.kt`) is `exported=true` with an intent-filter: `ACTION_VIEW`, schemes `content`/`file`, mimeType `application/vnd.fms.resources+xml`. It resolves the URI from `intent.data` (VIEW) or `EXTRA_STREAM` (SEND), previews, shows a confirm dialog, then `importFromUri()`.
- **Import via intent-push (chosen):** `adb push` a resources XML (root `<media-resources>`, e.g. a trimmed `sza_resources.xml` with just the picks) to `/sdcard`, then `adb shell am start -a android.intent.action.VIEW -d file://<path> -t application/vnd.fms.resources+xml -n com.sza.fastmediasorter.debug/.ui.resourceimport.ResourceImportActivity`. The launch is fully scriptable in `prerelease-configure.ps1`; only the single confirm-dialog tap needs the UI (mobile-mcp), and listing verification follows in the scenario.
- **OWNER_TRIGGER Settings-field path = fallback** if intent-push regresses. Keeps ADR-2 either way (no app code).
- **Resource picks (one per class):**
  - LOCAL: `Downloads`.
  - Network (SFTP): `SFTP` (`193.178.50.43`) - the reachable public endpoint stands in for the network class.
  - SMB: register only (row exists), **skip connectivity/listing** - LAN unreachable from emulator. The reachability pre-check (Phase 02) marks it `SKIP` with reason rather than failing the run.
- **Success verification:** per-resource listing confirmed by the `BrowseLoadingManager: COMPLETE` marker via `search-log.ps1`; registration confirmed by opening the resource and getting any listing result (or DB presence for the SKIP'd SMB).

## Impact on plan

- Phase 02 `Resources` config: `Downloads` (LOCAL), `SFTP` (network/SFTP), one SMB name registration-only.
- Phase 02 reachability pre-check distinguishes public (probe+list) from LAN (register-only SKIP).
- Phase 05 scenario imports via mobile-mcp UI, not adb.
- Host-side SMB at `10.0.2.2` is a possible future extension (strategic §5.3) - out of scope for iteration 1.

## Out-of-scope findings (parked)

- Missing per-resource success log in `SzaResourcesImporter.importOne`.
- `OWNER_TRIGGER` silently empty without `local.properties` (no build-time warning).
