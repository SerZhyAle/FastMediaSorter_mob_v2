# S0628 - Play publisher: attach existing library bundle instead of always re-uploading

**Status:** Archived

> Parked by `/skill-release` follow-up (auto-capture, CLAUDE.md 3.1). Out-of-scope of the v2.60.6221.755 release run.

## 0. Raw capture

Symptom: when the Play commit is rejected by the Foreground-service-permissions gate (HTTP 403,
`You must let us know whether your app uses any Foreground Service permissions`), the edit is
discarded and the AAB stays in the App Bundle Explorer as `Draft / 0 releases`. Re-running
`scripts/release/publish-play-release.ps1` to finish after the owner saves the declaration does NOT
work: `scripts/release/publish-play-release.py` unconditionally `bundles().upload`s the AAB
(lines 103-105) with no "bundle already in library" branch, and Play rejects re-uploading a
versionCode that already exists.

Observed on the 2026-06-22 release of v2.60.6221.755 (versionCode 260622175): GitHub publish + Drive
+ AAB upload all succeeded, only the Play commit hit the FGS 403. The owner then had to finish
manually via Production -> Create new release -> Add from library.

Evidence:
- `publish-play-release.py` step 3 always uploads; no `edits().bundles().list()` pre-check.
- Play Console bundle 260622175 shown as `Draft`, `0 releases` after the 403.

## 2. Goals (rough)

- Before uploading, query `edits().bundles().list()` for the target versionCode; if it already exists
  in the library, skip the upload and attach that versionCode to the track instead.
- Optionally derive the expected versionCode from `build.gradle.kts` so the pre-check works without
  uploading first.
- Keep the first-run (fresh bundle) path unchanged - upload then attach.
- Net effect: a post-FGS re-run of `publish-play-release.ps1` finishes the release instead of failing
  on a duplicate versionCode, so the FGS recovery becomes scriptable rather than a manual Console step.

## 3. Notes

- The FGS declaration itself (App content) is a Play Console UI action and stays owner-manual; this
  ticket only fixes the bundle-attach so the *release commit* can be re-driven by the script.

## 4. Implementation

- `scripts/release/publish-play-release.py`:
  - `get_expected_version_code()` reads `versionCode = <digits>` from `app_v2/build.gradle.kts`
    (the release build stamps the resolved code into `defaultAppVersionCode`), mirroring the
    existing `get_version_name()`. No upload needed to know the target versionCode.
  - `list_existing_bundle_codes()` calls `edits().bundles().list()` inside the open edit and
    returns the set of versionCodes already in the App Bundle Explorer. List failure falls back
    to an empty set (upload path), so a transient API error never blocks a fresh release.
  - Step 3 now branches: if the expected versionCode is already in the library, skip the upload
    and attach that bundle; otherwise upload as before and read the versionCode from the response.
    Track update + commit (steps 4-6) are unchanged and version-source-agnostic.
- `scripts/release/README.md`: Order of Operations + script table updated to the attach-or-upload
  flow; corrected the stale step-7 claim about `changesNotSentForReview` (the code deliberately
  omits it).

## 5. Verification

- Static: `.venv/Scripts/python.exe -m py_compile scripts/release/publish-play-release.py` -> exit 0.
- Live: deferred. Full proof needs the next FGS-rejected release window, where re-running
  `publish-play-release.ps1` after the owner saves the FGS declaration must finish the commit
  by attaching the already-uploaded bundle instead of failing on a duplicate versionCode.

## Last Audit

**Date:** 2026-06-23
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Notes

- `publish-play-release.py`: `get_expected_version_code()` (44) reads `versionCode` from `app_v2/build.gradle.kts`; `list_existing_bundle_codes()` (63) calls `service.edits().bundles().list()` (71) returning the set of existing versionCodes (74), with `except -> return set()` fallback so a list error never blocks a fresh release (75-77).
- Step-3 branch (138-154): when the expected versionCode is already in the library, skip the upload and attach it (`version_code = expected_version_code`, 146-148); otherwise upload as before (else, 149-154) - the fresh-bundle path is unchanged. Track update + commit downstream are version-source-agnostic.
- `scripts/release/README.md`: row 13 updated to "attach .. if its versionCode is already in the library else upload it"; line 107 documents the deliberate `changesNotSentForReview` omission (Play HTTP 400).
- `py_compile scripts/release/publish-play-release.py` re-run: exit 0.
- Debug-tag invariant PASS: Python/Markdown change, no `.kt` touched, zero `Timber.d("S0628:` tags.
- FEATURES trilingual EXEMPT: internal release-publishing tooling, no user-visible showcase change.

### Manual / on-device

- [ ] At the next FGS-rejected (or otherwise pre-uploaded) release window: re-running `publish-play-release.ps1` after the owner saves the declaration finishes the commit by attaching the already-uploaded bundle, with no duplicate-versionCode upload failure.
