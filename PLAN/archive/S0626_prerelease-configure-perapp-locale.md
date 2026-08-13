# S0626 - prerelease-configure locale channel fails on API 33+ (use per-app locale)

**Status:** Archived

> Parked by `/spec-prerelease` (auto-capture, CLAUDE.md 3.1). Out-of-scope of the sweep verdict.

## 0. Raw capture

Symptom: `scripts/devtest/prerelease-configure.ps1` returns exit 10 with stage
`set:Language FAIL - locale not applied (got '')`. The device-wide `cmd locale` adb channel does not
apply the language on modern Android (verified on API 37 emulator).

Workaround that works (applied by hand during the sweep):
`adb -s <id> shell cmd locale set-app-locales com.sza.fastmediasorter.debug --user current --locales ru`
then relaunch -> app UI is RU (`get-app-locales` returns `[ru]`).

Evidence:
- configure JSON: `{"name":"set:Language","status":"FAIL","detail":"locale not applied (got '')"}`, `CONFIGURE_EXIT=10`.
- `getprop persist.sys.locale` / `settings get system system_locales` both empty on the API 37 AVD.

## 2. Goals (rough)

- Switch the Language channel to per-app locale (`cmd locale set-app-locales <pkg> --user current --locales <code>`), which is the supported API 33+ path and avoids touching device-wide locale.
- Stop returning exit 10 for a locale that is in fact applied (per-app), and relaunch the app after setting it.

## 3. Implementation

- `prerelease-configure.ps1` stage 2 already used `cmd locale set-app-locales`, but without `--user current`, so set/get operated on user 0 and read back empty - failing the stage for an applied locale.
- Both the set and the verify now pass `--user current` (USER_CURRENT / user -2), matching the owner's verified workaround and the skill doc.
- After a successful apply the script relaunches the app: `am force-stop` then `am start -n <pkg>/com.sza.fastmediasorter.ui.main.MainActivity` (explicit component dodges the debug build's LeakCanary LAUNCHER trap).
- The API 33+ guard and the existing exit-10-on-genuine-failure behaviour are unchanged; only the misread of an applied locale is fixed.
- Out of scope: configure still relies on the caller passing `-DeviceId` (the skill's step 1.0 clears offline siblings and forwards the resolved id). The multi-device adb-scoping hardening landed for `prerelease-prepare.ps1` under S0625; configure's documented path is always scoped.

## 4. Validation

- `[Parser]::ParseFile` on `prerelease-configure.ps1`: PARSE OK.
- On the live API-37 `emulator-5556`: `set-app-locales --user current --locales ru` then `get-app-locales --user current` returns `[ru]` (verify okLoc=TRUE); the relaunch `am start -n .../MainActivity` exits 0 with no activity-not-found error.

## Last Audit

**Date:** 2026-06-23
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 7 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Notes

- `prerelease-configure.ps1` stage 2: both the set (`cmd locale set-app-locales $DebugPackage --user current --locales $s.Locale`, line 170) and the verify (`get-app-locales $DebugPackage --user current`, line 171) now pass `--user current` (USER_CURRENT / -2) - the fix for the user-0 empty read-back. Comment at 166-169 documents why.
- Relaunch on success: `am force-stop` (177) then `am start -n "$DebugPackage/com.sza.fastmediasorter.ui.main.MainActivity"` (178) - explicit component to dodge the LeakCanary LAUNCHER trap.
- API 33+ guard (141) and exit-10-on-genuine-failure unchanged; only the misread of an applied per-app locale is fixed. `[Parser]::ParseFile` re-run: PARSE OK.
- Debug-tag invariant PASS: script-only change, no `.kt` touched, zero `Timber.d("S0626:` tags.
- FEATURES trilingual EXEMPT: internal pre-release configure tooling, no user-visible showcase change.

### Manual / on-device

- [ ] On a live API>=33 device: configure's Language channel sets the per-app locale (`get-app-locales --user current` returns the chosen code) and the app relaunches in that language; the stage no longer returns exit 10 for an applied locale.
