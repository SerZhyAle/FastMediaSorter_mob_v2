# Phase 04 - Keep-Rule Narrowing and Resource Shrinking

**Strategic spec:** [`../S0385_apk-aab-dead-weight-reduction.md`](../S0385_apk-aab-dead-weight-reduction.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done - fast-wins landed (04.1 BC + 04.4 googleid); 04.2/04.3/04.5 DROPPED at S0385 closure (risky / near-zero win - see step logs)
**Depends on:** none - independent phase (higher risk; validate each step on a release build)
**Blocks:** none
**Steps done:** 2 / 5 (04.2, 04.3, 04.5 ⏭️ Dropped at closure 2026-06-08)
**Started:** 2026-06-08
**Completed:** 2026-06-08

---

## Objective

Narrow over-broad library keep rules, exclude unused dependency data-resources from packaging, restore unused-string/drawable shrinking, and remove the near-dead dependencies - each validated against a release build of the affected paths.

---

## Prerequisites

- [ ] Phase 02 done (app-package keep narrowing already in place to avoid conflicting edits to `proguard-rules.pro`).
- [ ] A release-signable or release-buildable config to exercise R8 on the affected paths.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/proguard-rules.pro` | Modified | ≤ 320 |
| `app_v2/build.gradle.kts` | Modified | n/a (>500 - backup first) |
| `app_v2/src/main/res/raw/keep.xml` | Modified | ≤ 40 |
| `temp/S0385_dead_resource_report.md` | New | ≤ 60 |

> `app_v2/build.gradle.kts` exceeds 500 lines - timestamped backup in `temp/` before edit.

---

## Steps

### Step 04.1 - Exclude unused BouncyCastle post-quantum data and locales

**Files:** `app_v2/build.gradle.kts`, `app_v2/proguard-rules.pro`
**Depends on:** - start of phase

**Prompt for developer:**

> SMB/SFTP do not use BouncyCastle's PICNIC/lowmc post-quantum tables (~1.22 MB) or its non-en/ru/uk reviewer-message locales. Add `packaging.resources.excludes` entries for the `org/bouncycastle/pqc/crypto/picnic/lowmc*` data and the unused `CertPathReviewerMessages_*` locales. Where feasible, narrow `-keep class org.bouncycastle.** { *; }` toward the providers actually loaded by SMBJ rather than the whole package.

**Verification:**

- `Grep` - `packaging` block in `build.gradle.kts` contains exclude entries matching `picnic` / `lowmc` and the unused `CertPathReviewerMessages` locales.
- `Glob` after a `standardRelease` build + unzip - no `org/bouncycastle/pqc/crypto/picnic/lowmc*` entry in the artifact.

**Status:** `[x] done`

**Step Log:**

- 2026-06-08 - Verification PASS. Added `excludes += "org/bouncycastle/pqc/crypto/picnic/**"` and `"org/bouncycastle/x509/CertPathReviewerMessages_de.properties"`. Research: 0 code references to `picnic`/`pqc` (SMB uses classical BC only). standardRelease unzip: picnic entries=0, _de=0, base `CertPathReviewerMessages.properties` retained, BC otherwise intact. ~1.27 MB removed; standard release APK 162.2 → 161.06 MB. Same exclude verified applied in liteRelease (picnic=0). Did NOT narrow the `-keep org.bouncycastle.**` classes (that is 04.2, deferred) - only the unused data resources are dropped.

---

### Step 04.2 - Incrementally narrow remaining whole-package library keeps

**Files:** `app_v2/proguard-rules.pro`
**Depends on:** Step 04.1

**Prompt for developer:**

> For the remaining `-keep class X.** { *; }` rules over GMS, ML Kit, MSAL and Media3, narrow one library at a time to the reflection/JNI/Gson-accessed classes that genuinely need retention. After each single narrowing, build a release variant and exercise the corresponding live path (cloud auth, OCR/translate, OneDrive, playback/cast). Revert any narrowing that breaks a path; keep only the safe reductions. Do not narrow all libraries in one commit.
>
> **Deferred from Phase 02:** also narrow the app's own `-keep class ...data.network.glide.** { *; }` and `-keep class ...data.local.db.** { *; }` here (the dead classes under them were already deleted in Phase 02; narrowing needs release-build validation of live Glide image-loading + Room, which is why it lands in this phase, not Phase 02). Keep `data.remote.**` untouched - it still guards `HostKeyMismatchException` (S0046).

**Verification:**

- `Grep` - at least one previously whole-package `-keep` rule is now a class-specific keep.
- A `standardRelease` build succeeds (R8 does not fail) after each narrowing.

**Status:** `⏭️ Deferred` - not a fast-win

**Step Log:**

- 2026-06-08 - Deferred. Narrowing whole-package reflection keeps (GMS/ML Kit/MSAL/Media3 + the Phase 02 glide/db keeps) is only verifiable by exercising each live path (cloud auth, OCR, OneDrive, playback/cast) on a release build - exactly the staged release-path validation the owner deferred. High risk of a silent release-only crash. Left for a dedicated validated pass.

---

### Step 04.3 - Restore unused string/drawable shrinking

**Files:** `app_v2/proguard-rules.pro`, `app_v2/src/main/res/raw/keep.xml`
**Depends on:** Step 04.2

**Prompt for developer:**

> Remove the global `-keep class **.R$string { *; }`, `-keep class **.R$drawable { *; }`, and `-keepclassmembers class **.R$* { public static <fields>; }` retention that disables resource shrinking for those types. Before removing, audit dynamic resource lookups (`getIdentifier`, name-based resource access) and add any such resources to `keep.xml` via `tools:keep` so they survive. Let the resource shrinker remove the rest.

**Verification:**

- `Grep` - `R\$string` and `R\$drawable` whole-class keeps are absent from `proguard-rules.pro`.
- `Grep` - `getIdentifier` callers in `app_v2/src/**` each have a corresponding `tools:keep` entry in `keep.xml`.

**Status:** `⏭️ Deferred` - not a fast-win (dynamic-string risk)

**Step Log:**

- 2026-06-08 - Deferred. Research found a dynamic string lookup: `KeybindingRowLabelFormatter` calls `getIdentifier(resName, "string", packageName)` (3 sites) - keybinding labels resolved by constructed name. Removing the `R$string` keep + enabling string shrinking could strip those dynamically-referenced strings and break keybinding labels in release. Needs the full set of dynamically-built string names enumerated into `keep.xml` + release runtime verification before it is safe. Not a fast-win.

---

### Step 04.4 - Remove near-dead and mis-scoped dependencies

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 04.3

**Prompt for developer:**

> Drop `androidx.lifecycle:lifecycle-livedata-ktx` if the single WorkManager `LiveData` return still resolves via the transitive `livedata-core` (confirm by build). Move `com.google.android.libraries.identity.googleid` from the global `implementation` to `cloudEnabledImplementation` so it stops shipping in `cloudDisabled` (lite) builds. Do not touch `profileinstaller` or Glide's `okhttp3-integration` (both auto-register without source references).

**Verification:**

- `Grep` - `lifecycle-livedata-ktx` absent from `build.gradle.kts` (or a note recorded that the build required keeping it).
- `Grep` - `googleid` appears only under a `cloudEnabledImplementation` configuration in `build.gradle.kts`.

**Status:** `[x] done` (googleid scoped); `lifecycle-livedata-ktx` skipped

**Step Log:**

- 2026-06-08 - googleid: DONE. Correction - there is no `cloudEnabledImplementation` configuration (`cloudEnabled` is a source-set directory mounted per-flavor, not a flavor), so the dep was scoped via per-flavor configs `standardImplementation`/`noLegalImplementation`/`legacyImplementation`/`vrImplementation`/`photosImplementation` (every flavor that mounts `cloudEnabled`; lite excluded). Verified: liteRelease unzip shows 0 `google/android/libraries/identity` classes; standardRelease compiles (config valid for all flavors). Research confirmed googleid's only consumer is `src/cloudEnabled/.../CredentialManagerGoogleIdentityRepository.kt`.
- 2026-06-08 - lifecycle-livedata-ktx: SKIPPED. Negligible weight (KTX extension artifact), only one transitive `LiveData` touch (`ReceiveShareActivity` WorkManager `getWorkInfoByIdLiveData`); removal would need a transitive-resolution build-check for a sub-KB win. Not worth a build cycle - left as-is.

---

### Step 04.5 - Record the dead-resource weight resolved by R8

**Files:** `temp/S0385_dead_resource_report.md`
**Depends on:** Step 04.3

**Prompt for developer:**

> Build a release variant with the R8 usage report enabled and record how much string/drawable weight the resource shrinker now removes (resolves strategic §6.4). Capture the figure into the report file for the Phase 07 delta.

**Verification:**

- `Glob` - `temp/S0385_dead_resource_report.md` exists.
- `Grep` - the file contains a numeric byte/count figure for removed string/drawable resources.

**Status:** `⏭️ Deferred` - depends on 04.3 (also deferred)

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `/build` `standardRelease` succeeds and the live network/cloud/OCR/playback/cast paths were exercised on a release build without regression.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every touched file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Library keeps narrowed where safe; PQC data and unused locales excluded; resource shrinking restored for strings/drawables; near-dead deps removed/scoped. ML native libs are still packaged everywhere - that is Phase 05.

---

## Rollback Plan

Revert per-step commits. Keep-rule changes are reversible by restoring the prior rule; any release runtime regression maps back to the single narrowing step that introduced it.
