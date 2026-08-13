# Phase 03 - Test Fixture Debug Isolation

**Strategic spec:** [`../S0385_apk-aab-dead-weight-reduction.md`](../S0385_apk-aab-dead-weight-reduction.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent phase
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-08
**Completed:** 2026-06-08

---

## Objective

Stop shipping the test-credentials JSON asset and force-kept debug-only credential model into release builds, where both are unreachable.

> **Corrected approach (vs the original draft) - evidence-based.** Investigation during execution found two distinct, BOTH debug-only test-credential systems, so the original "move the model to src/debug" step would have broken release compilation. Findings:
> - **System #1** - `data.repository.TestCredentialsConfig` / `TestCredential` (`TestCredentialModels.kt`): consumed only by `NetworkCredentialsRepositoryImpl.loadTestCredentials()`, which runs only inside `init { if (BuildConfig.DEBUG) { .. } }` and reads **external storage**, never the bundled asset. Force-kept by `-keep ...data.repository.TestCredential** { *; }`.
> - **System #2** - `domain.usecase.TestCredentialsLoader` (nested `TestCredential`): reads `assets/test_credentials.json` as a fallback, but is invoked only from the "Import Test Setup" settings button, which is gated `if (BuildConfig.DEBUG && IntegrationTestDialog.isAvailable())` and is `View.GONE` in release (`GeneralSettingsViewSetupHelper.kt:505-518`). So in release the asset is never read.
> - Therefore: the asset is dead weight in release (only debug-gated readers), and System #1's models are R8-strippable in release (dead `BuildConfig.DEBUG` branch) once the force-keep is removed.
> - **Do NOT move the model classes** - keep them in `src/main` so `NetworkCredentialsRepositoryImpl` and `TestCredentialsLoader` compile in all variants; let R8 strip the unused ones in release. Only the asset moves, and only the force-keep is dropped.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/assets/test_credentials.json` | Moved → `app_v2/src/debug/assets/test_credentials.json` | - |
| `app_v2/proguard-rules.pro` | Modified | ≤ 320 |

> No `.kt` files change: both consumers are already runtime-gated by `BuildConfig.DEBUG`. The model classes stay in `src/main` (R8 strips them in release).

---

## Steps

### Step 03.1 - Relocate the test fixture asset to the debug source set

**Files:** `src/main/assets/test_credentials.json` → `src/debug/assets/test_credentials.json`
**Depends on:** - start of phase

**Prompt for developer:**

> Move `test_credentials.json` from `src/main/assets/` to `src/debug/assets/`. The only readers are debug-gated (System #1 reads external storage under `BuildConfig.DEBUG`; System #2's `assets.open` runs only from the release-`GONE` "Import Test Setup" button), so release never reads it. Debug builds still merge `src/debug/assets/` and keep the fixture.

**Verification:**

- `Glob` - `app_v2/src/main/assets/test_credentials.json` no longer exists.
- `Glob` - `app_v2/src/debug/assets/test_credentials.json` exists.

**Status:** `[x] done`

**Step Log:**

- 2026-06-08 - Verification 2/2 PASS. Moved test_credentials.json: main absent, src/debug/assets present. | expected: main=absent debug=present | actual: main=absent debug=present.

---

### Step 03.2 - Confirm consumers stay debug-safe (no code change)

**Files:** none (verification only)
**Depends on:** Step 03.1

**Prompt for developer:**

> No edit required. Confirm that every reader of the moved asset and of the System #1 models is reachable only under `BuildConfig.DEBUG`: `NetworkCredentialsRepositoryImpl` init guard, and the "Import Test Setup" button (`GeneralSettingsViewSetupHelper` debug gate). Release compilation is unaffected because the model classes remain in `src/main`.

**Verification:**

- `Grep` - the only `TestCredentialsConfig` / `data.repository.TestCredential` usages in `src/main/**` sit inside a `BuildConfig.DEBUG` guard or a release-`GONE` UI path.
- `Grep -n "Log\.d\("` returns zero hits in any file touched (none touched).

**Status:** `[x] done`

**Step Log:**

- 2026-06-08 - Verification PASS (no code change). `data.repository.TestCredentialsConfig`/`TestCredential` are referenced in src/main ONLY at `NetworkCredentialsRepositoryImpl.loadTestCredentials()` (called from `init { if (BuildConfig.DEBUG) }`) + their own declaration. "Import Test Setup" button is release-`GONE` (`GeneralSettingsViewSetupHelper.kt:505-518`). No non-debug reader of the moved asset. No .kt edited.

---

### Step 03.3 - Drop the test-credentials force-keep

**Files:** `app_v2/proguard-rules.pro`
**Depends on:** Step 03.2

**Prompt for developer:**

> Remove the `-keep class ...data.repository.TestCredential** { *; }` rule. With the only consumer dead under `BuildConfig.DEBUG`, R8 then strips `TestCredentialsConfig` / `TestCredential` from release. This rule matches only the `data.repository` models - `TestCredentialsLoader.TestCredential` (System #2, `domain.usecase`) is a different class and is unaffected.

**Verification:**

- `Grep` - `TestCredential` returns zero hits in `app_v2/proguard-rules.pro`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-08 - Verification PASS (intent). Removed `-keep class ...data.repository.TestCredential** { *; }`. The only residual `TestCredential` token in proguard-rules.pro is the explanatory removal comment (line 213), not a rule. `TestCredentialsLoader.TestCredential` (System #2, domain.usecase) is a different class, never matched by this rule, unaffected.

---

## Phase Done Criteria

- [x] All 3 steps `[x] done`.
- [x] `standardDebug` BUILD SUCCESSFUL (37s) - debug fixture path intact, models present.
- [x] `standardRelease` BUILD SUCCESSFUL (3m24s) - models stay in main, R8 strips the dead ones, no compile break.
- [x] Artifact check: `assets/test_credentials.json` | expected: debug=present release=absent | actual: debug=present (191.4MB APK) release=absent (162.2MB APK). R8 `usage.txt` additionally confirms `TestCredential` + `TestCredentialsConfig` STRIPPED from release.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entries added (post-change Config for proguard + asset-move line).

---

## Handoff Notes to Next Phase

The test-credentials JSON is debug-only; release artifacts no longer carry it, and the debug-only credential models are R8-stripped in release. No runtime path changed (both readers were already `BuildConfig.DEBUG`-gated).

---

## Rollback Plan

Revert the phase commit: restore the asset to `src/main/assets` and the keep rule. No data migration or user-facing surface changed.
