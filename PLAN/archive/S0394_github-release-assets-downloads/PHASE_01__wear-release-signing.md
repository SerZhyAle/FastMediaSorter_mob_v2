# Phase 01 - Wear OS release signing

**Strategic spec:** [`../S0394_github-release-assets-downloads.md`](../S0394_github-release-assets-downloads.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 1 / 1
**Started:** 2026-06-10
**Completed:** 2026-06-10

---

## Objective

Give the wear module a release signing config that reuses the shared release keystore, so `:wear:assembleRelease` produces a signed (sideload-installable) APK covered by the existing pinned fingerprint.

---

## Prerequisites

- [ ] `keystore.properties` exists at repo root (same source app_v2 release signing reads).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `wear/build.gradle.kts` | Modified | ≤ 190 |

> No `res/layout` involved - landscape parity N/A. No Kotlin/flavor source set - flavor discipline N/A.

---

## Steps

### Step 01.1 - Add release signing to the wear module

**Files:** `wear/build.gradle.kts`
**Depends on:** - start of phase

**Prompt for developer:**

> In `wear/build.gradle.kts`, add a `signingConfigs { create("release") { .. } }` block inside the `android { }` block that loads `keyAlias` / `keyPassword` / `storeFile` / `storePassword` from the repo-root `keystore.properties` (mirror how `app_v2/build.gradle.kts` `signingConfigs.create("release")` resolves them). Bind it on the existing `release` buildType via `signingConfig = signingConfigs.getByName("release")`. Guard the config so a missing `keystore.properties` fails with a clear message rather than silently producing an unsigned APK. Do NOT modify the `compileSdk` / `minSdk` / `targetSdk` / Java-version lines (each carries a "CRITICAL: Do not change" note).

**Verification:**

- `Grep` - `signingConfigs` matches in `wear/build.gradle.kts`.
- `Grep` - `signingConfig = signingConfigs.getByName("release")` present inside the `release` buildType.
- Build `:wear:assembleRelease` via `/build`; the produced APK under `wear/build/outputs/apk/release/` is NOT named `*-release-unsigned.apk`.
- `Grep` - the `compileSdk = 35` / `minSdk = 28` / `targetSdk = 35` lines are unchanged.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification 4/4 PASS. Mirrored app_v2 release signing into `wear/build.gradle.kts` (imports + `hasReleaseKeystore`/`requiresReleaseSigning` guards + `signingConfigs.create("release")` from `keystore.properties` + `release` buildType binding). `:wear:assembleRelease` BUILD SUCCESSFUL, output `wear/build/outputs/apk/release/wear-release.apk` (signed, not `-unsigned`). CRITICAL sdk lines unchanged.

---

## Phase Done Criteria

- [x] Step 01.1 is `[x] done`.
- [x] Project compiles - `:wear:assembleRelease` BUILD SUCCESSFUL in 1m53s (validates the changed module + whole-build configuration).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for `wear/build.gradle.kts` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The wear release APK is now signed with the shared release key - the publisher's single pinned fingerprint (`scripts/release/expected-signing-fingerprint.txt`) covers it. Phase 02 can include the wear release output in the uniform-version spectrum build.

---

## Rollback Plan

Revert the phase commit - no data migration or user-facing surface changed; only the wear release signing config is removed (back to unsigned release).
