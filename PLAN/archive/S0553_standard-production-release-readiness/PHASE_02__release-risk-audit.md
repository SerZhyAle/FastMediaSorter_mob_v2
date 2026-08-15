# Phase 02 - Release-risk audit

**Strategic spec:** [`../S0553_standard-production-release-readiness.md`](../S0553_standard-production-release-readiness.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01 (gate document skeleton from step 01.3)
**Blocks:** Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-20
**Completed:** 2026-06-20

## Step Log

- 2026-06-20 - 02.1 PASS: `standard-release-smoke.ps1` created; no-device `-Json` run exits 2 (infra abort, no false FAIL); all R8 markers + `search-log.ps1` reuse present.
- 2026-06-20 - 02.2 PASS: `-CheckSeams` exit 0, 4 release-identity seams OK (production dropboxAppKey, no applicationIdSuffix leak, no debug key, MSAL present).
- 2026-06-20 - 02.3 PASS: Release-risk audit section filled (R8 seams, signing/auth, targetSdk-35, mapping.txt+versionCode retention, logging privacy line); placeholder removed.

---

## Objective

Produce an automated `standardRelease` smoke that detects R8/shrink/signing/auth breakage on the real minified artifact, plus the release-risk section of the gate document (diagnostics, mapping retention, logging privacy line).

---

## Prerequisites

- [ ] research/02 (diagnostics/mapping) and research/03 (logging privacy) read.
- [ ] `scripts/devtest/prerelease-verdict.ps1` and `scripts/utils/search-log.ps1` available (reuse, do not re-implement).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/release/standard-release-smoke.ps1` | New | ≤ 260 |
| `docs/RELEASE_READINESS_STANDARD.md` | Modified | ≤ 400 |

> Reuse existing builders/installers/log tooling. The smoke must operate on the minified `standardRelease` artifact - the only build that exposes R8/shrink breakage.

---

## Steps

### Step 02.1 - Author standardRelease smoke (R8/shrink detection)

**Files:** `scripts/release/standard-release-smoke.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/release/standard-release-smoke.ps1`. Accept `-ApkPath <path>` (optional); when absent, build the minified release via `assembleStandardRelease` (or reuse `scripts/builders/build-standard-release.ps1`) and resolve the APK from `app_v2/build/outputs/apk/standard/release/output-metadata.json`. When the release keystore is absent, exit 2 (infrastructure abort) with a clear message - never a false FAIL. Install onto a connected device (reuse `scripts/utils/Install_release_on_adb_connected_device.ps1` and `scripts/devtest/device-ready.ps1` for the device probe), cold-launch `com.sza.fastmediasorter`, capture the launch logcat window, and scan it with `scripts/utils/search-log.ps1` for R8/shrink fatal markers: `ClassNotFoundException`, `NoClassDefFoundError`, `NoSuchMethodError`, `VerifyError`, `Resources$NotFoundException`, plus `FATAL EXCEPTION`. Exit 0 = clean, 1 = breakage found, 2 = infra abort. Support `-Json` emitting a verdict object mirroring the `prerelease-verdict.ps1` shape.

**Verification:**

- `Glob` - `scripts/release/standard-release-smoke.ps1` exists.
- `Grep` - `assembleStandardRelease` and `output-metadata.json` present.
- `Grep` - all R8 markers present: `ClassNotFoundException`, `NoClassDefFoundError`, `NoSuchMethodError`, `VerifyError`, `Resources\$NotFoundException`.
- `Grep` - `search-log.ps1` referenced (reuse, not re-implemented).
- Run with no device: `pwsh -NoProfile -File scripts/release/standard-release-smoke.ps1 -Json` exits 2 with an infra-abort message (no false FAIL).

**Status:** `[x]` done

---

### Step 02.2 - Assert release-only auth / deep-link / signing seams

**Files:** `scripts/release/standard-release-smoke.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a `-CheckSeams` switch that statically asserts the release identity seams without a device: the release `manifestPlaceholders` redirect URIs / app keys (OAuth callback, Dropbox `dropboxAppKey`, MSAL/AppAuth) resolve for the `release` build type, and there is no debug-only `applicationIdSuffix` leaking into release. Print each seam as `OK`/`MISSING`; exit 1 if any required seam is missing, exit 0 when all present. Document (in comments) that release signing fingerprint vs production OAuth registration is a manual operator check (covered by the evidence pack).

**Verification:**

- `Grep` - `CheckSeams` present in the param block.
- `Grep` - `dropboxAppKey` referenced.
- Run: `pwsh -NoProfile -File scripts/release/standard-release-smoke.ps1 -CheckSeams` exits 0 or 1 (never 2 on a normal tree) and prints the seam list.

**Status:** `[x]` done

---

### Step 02.3 - Fill the Release-risk audit section of the gate document

**Files:** `docs/RELEASE_READINESS_STANDARD.md`
**Depends on:** Step 02.1, Step 02.2

**Prompt for developer:**

> Replace the `Release-risk audit` placeholder with: the §5.3 release-only technical regression list; the R8/shrink seam list mirrored from the smoke; targetSdk-35 checks (background services, foreground-service types, photo permissions, battery optimization); the diagnostics + deobfuscation retention policy from research/02 (in-app crash/log export baseline; mandatory `mapping.txt` + native-symbol retention keyed by `versionCode`); and the release-logging privacy line from research/03 (forbidden vs acceptable log categories). Reference `standard-release-smoke.ps1` as the automated check for the R8/auth seams.

**Verification:**

- `Grep` - `## Release-risk audit` section no longer contains a `Filled in Phase` placeholder.
- `Grep` - `mapping.txt` and `versionCode` present (research/02 retention policy).
- `Grep` - `standard-release-smoke.ps1` referenced in the section.
- `Grep` - a logging privacy subsection present (e.g. `privacy` / `forbidden`).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `standard-release-smoke.ps1` runs (exit 2 with no device / no keystore is acceptable) - record exit code.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added (or batched in Phase 05).

---

## Handoff Notes to Next Phase

The release smoke + seam check exist and the release-risk section is written. Phase 03 records their coverage level in the matrix; Phase 04 folds the smoke verdict into the gate aggregator.

---

## Rollback Plan

Revert phase commit(s) - new script + doc section, no runtime change.
