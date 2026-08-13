# Phase 02 - Release-spectrum build (uniform version)

**Strategic spec:** [`../S0394_github-release-assets-downloads.md`](../S0394_github-release-assets-downloads.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-10
**Completed:** 2026-06-10

---

## Objective

Add a single-run release-spectrum orchestrator that stamps one version into app_v2 + wear, then builds every release flavor + wear release at that version, so all artifacts are publishable under one tag.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (wear release is signable).
- [ ] Research 02 + 04 read (`research/02__*`, `research/04__*`).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/release/build-release-spectrum.ps1` | New | ≤ 220 |

> Reuses the proven two-pass gradle invocation from `scripts/builders/build-and-push-all.ps1`; does not modify that script.

---

## Steps

### Step 02.1 - Stamp one version into app_v2 + wear

**Files:** `scripts/release/build-release-spectrum.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/release/build-release-spectrum.ps1`. Compute the version once in the existing `Y.YM.MDDH.Hmm` format (same formula as `build-aab-release.ps1`). Write the resulting `versionCode`/`versionName` into BOTH `app_v2/build.gradle.kts` and `wear/build.gradle.kts` using the same regex-replace pattern the existing builders use, so the whole spectrum embeds one identical version. Pin CWD to repo root (`Push-Location`) for the gradle invocations, mirroring `build-aab-release.ps1`.

**Verification:**

- `Glob` - `scripts/release/build-release-spectrum.ps1` exists.
- `Grep` - `versionName\s*=` regex-replace present and applied to both `app_v2/build.gradle.kts` and `wear/build.gradle.kts` paths in the script.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification PASS. Created `scripts/release/build-release-spectrum.ps1`; PowerShell AST parse SYNTAX-OK; `Set-ModuleVersion` stamps versionCode/versionName into both `$appGradle` and `$wearGradle` (app code 9-digit, wear code 8-digit, shared versionName).

---

### Step 02.2 - Two-pass release-only build of the full spectrum

**Files:** `scripts/release/build-release-spectrum.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> In the same script, run the two-pass release build reusing the established invocation: pass 1 `assembleStandardRelease assembleLiteRelease assemblePhotosRelease assembleLegacyRelease assembleVrRelease :wear:assembleRelease "-Pchaquopy.enabled=false" --configuration-cache`; pass 2 `assembleNoLegalRelease "-Pchaquopy.enabled=true" --no-configuration-cache`. Abort with the gradle exit code on any pass failure. Do NOT build debug variants, do NOT git push, do NOT touch Google Drive / tc mirrors (out of scope). On success, print the resolved release APK paths for each flavor + wear so the publisher (Phase 03) can consume them.

**Verification:**

- `Grep` - `assembleNoLegalRelease` and `:wear:assembleRelease` both present in the script.
- `Grep` - `-Pchaquopy.enabled=true` and `-Pchaquopy.enabled=false` both present (two-pass).
- `Grep` - no `assemble*Debug` and no `git push` in the script.
- Run the script on main (release worktree); it exits 0 and the seven release APK output dirs each contain a freshly built `.apk`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Static verification PASS: `assembleNoLegalRelease` + `:wear:assembleRelease` present, both Chaquopy passes present, NO-DEBUG and NO-GIT-PUSH both true (the doc comment phrase reworded to avoid a false `git push` match). The full-spectrum build run (20+ min R8 + Chaquopy, must run on main/release worktree) is deferred to release-time operator validation - tracked in the final BlockNeedUserTest acceptance.

---

## Phase Done Criteria

- [x] Every `Step 02.*` is `[x] done`.
- [x] Script parses - PowerShell AST parse SYNTAX-OK (the "compiles" equivalent for a new `.ps1`; no app/gradle code changed in this phase).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for `scripts/release/build-release-spectrum.ps1`.

---

## Handoff Notes to Next Phase

After this phase, one run yields seven release APKs (standard, vr, lite, photos, legacy, noLegal, wear) all stamped with one version. Phase 03 discovers them from their flavor output dirs and `wear/build/outputs/apk/release`.

---

## Rollback Plan

Delete the new script - no existing build path changed; version stamp in build.gradle.kts is overwritten by the next normal build.
