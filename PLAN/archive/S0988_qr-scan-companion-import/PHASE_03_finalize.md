# PHASE 03 - Strings, capability, tags, build, audit

1. Trilingual strings.
   - `scripts/utils/set-android-string.ps1 -Action add` for each key across EN/RU/UK: `companion_qr_scan_button`, `companion_qr_scan_title`, `companion_qr_scan_hint`, `companion_qr_scan_torch`, `companion_qr_camera_denied`, `companion_qr_invalid`.
   - Audit: `scripts/check_strings_localized.ps1 -KeyPrefix "companion_qr"` -> exit 0.

2. Debug verification tags (this ticket ends BlockNeedUserTest).
   - Insert `Timber.d("S0988: <entry>")` at the two changed-flow entries as the final code edits before the last build: (a) scan decode -> payload received; (b) `importFromPayload` entry.
   - One tag per changed flow; removed when leaving BlockNeedUserTest.

3. Build gate.
   - `.\a.ps1 dq` (standard debug) - all new code is `src/main`, so this proves the shared path for every flavor.
   - `.\a.ps1 fkn` (noLegal compile) - confirm no flavor-specific break.
   - No separate photos/legacy assemble (src/main-only change; per no-redundant-flavor-compile rule).

4. Capability inventory (on Implemented).
   - `scripts/all_features/add.ps1` record: companion QR scan import (EN-only), flavors standard/photos/legacy/noLegal.

5. Audit (F5).
   - `/spec-check` layers: camera lifecycle release (Rule 18 / listener symmetry), permission flow, no main-thread work in analyzer, single-owner camera, no hardcoded hex, detekt-clean.
   - Device test: scan a real LITE companion QR (plain + compressed) -> resource created; foreign QR rejected; camera released on back.

## Done predicate

Build PASS + strings parity + tags present -> status `BlockNeedUserTest` with device-test note. Device-test gate auto-runs when a device is online.
