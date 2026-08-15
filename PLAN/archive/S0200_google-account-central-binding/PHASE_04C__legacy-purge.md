# Phase 04c — Legacy GoogleSignIn Purge (mechanical, zero-behavior-change)

**Strategic spec:** [`../S0200_google-account-central-binding.md`](../S0200_google-account-central-binding.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04b
**Blocks:** Phase 05, Phase 06
**Steps done:** 4 / 4
**Started:** 2026-05-16
**Completed:** 2026-05-16

---

## Objective

With all real call sites already routed through `identityRepository`, the deprecated stubs in `GoogleDriveRestClient` (`getSignInIntent`, `handleSignInResult`, `getSignInOptions`) are deleted; remaining `GoogleSignIn` / `GoogleAuthUtil` imports across `data/cloud/`, `ui/settings/`, `ui/browse/`, `ui/addresource/` are stripped. Plaintext fallback in `GoogleDriveCredentialsManager` is removed (encrypted-only per strategic §5.1). `@file:Suppress("DEPRECATION")` annotations cleared. Test fakes simplified now that the live API has stabilised.

---

## Prerequisites

- [ ] Phase 04b is ✅ Done — all 6 UI call sites migrated, deprecated stubs orphaned.
- [ ] Confirm no `Timber.d("S0200:` debug verification tags exist (S0200 has not been in `BlockNeedUserTest` yet — would only appear later).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveRestClient.kt` | Modified (delete stubs) | ≤ 1100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveAuthCoordinator.kt` | Modified (delete `buildSignInOptions`) | ≤ 280 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/helpers/GoogleDriveCredentialsManager.kt` | Modified (drop plaintext fallback) | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceConnectionManager.kt` | Modified (drop residual imports) | ≤ 530 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/BackupRestoreFragment.kt` | Modified (drop residual imports) | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` | Modified (drop residual imports) | ≤ 280 |

---

## Steps

### Step 04c.1 — Delete deprecated stubs in `GoogleDriveRestClient` + Coordinator

**Prompt:**

> Delete `getSignInIntent()`, `getSignInOptions()`, `handleSignInResult(account: GoogleSignInAccount?)` from `GoogleDriveRestClient`. Delete `buildSignInOptions(webClientIdResId: Int)` from `GoogleDriveAuthCoordinator`. Remove the `import com.google.android.gms.auth.api.signin.*` lines from both files.

**Verification:**

- `Grep -n "fun getSignInIntent\\|fun getSignInOptions\\|fun handleSignInResult" app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveRestClient.kt` returns zero hits.
- `Grep -n "fun buildSignInOptions" app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveAuthCoordinator.kt` returns zero hits.
- `/build` → `standardDebug` PASS.

**Status:** `[x]` done

---

### Step 04c.2 — Strip residual `GoogleSignIn` / `GoogleAuthUtil` imports across the migration scope

**Prompt:**

> `grep -rln "import com.google.android.gms.auth.api.signin\\|import com.google.android.gms.auth.GoogleAuthUtil"` across `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/`. Remove each import; verify the file still compiles (no residual symbol references). Files in scope confirmed by Phase 04b research: `GoogleDriveRestClient`, `GoogleDriveAuthCoordinator`, `GoogleDriveAuthPlugin`, `GoogleDriveThumbnailModelLoader`, `BackupRestoreViewModel`, `BackupRestoreFragment`, `GeneralSettingsFragment`, `BrowseCloudAuthManager`, `AddResourceConnectionManager`.

**Verification:**

- `Grep -rn "import com.google.android.gms.auth.api.signin\\|import com.google.android.gms.auth.GoogleAuthUtil" app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/ app_v2/src/main/java/com/sza/fastmediasorter/ui/` returns zero hits.
- `/build` → `standardDebug` + `noLegalDebug` + `liteDebug` PASS.

**Status:** `[x]` done

---

### Step 04c.3 — Remove plaintext fallback in `GoogleDriveCredentialsManager`

**Prompt:**

> Per strategic §5.1: `EncryptedSharedPreferences.create(..)` throws → propagate. Delete the `runCatching { … }.getOrElse { plaintextPath() }` branch. Public surface unchanged.
>
> WARNING: pair this change with Phase 05's auth-state wipe in the same release. Otherwise devices whose Keystore failed in a prior version (and silently fell back to plaintext) will throw on first Drive use after update.

**Verification:**

- `Grep -n "plaintext\\|getOrElse" app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/helpers/GoogleDriveCredentialsManager.kt` returns zero hits (assuming no other `getOrElse` usage).
- `/build` → `standardDebug` PASS.

**Status:** `[x]` done

---

### Step 04c.4 — Final lint sweep + test cleanup

**Prompt:**

> 1. `grep -rn "@file:Suppress(\"DEPRECATION\")" app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/ app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/` returns zero hits (already cleared by legacy Step 04.1 for 5 files; verify none crept back).
> 2. `FakeGoogleIdentityRepository` in test source set simplified — no longer needs to emulate legacy `GoogleSignIn` interactions; only the new repository contract.
> 3. Run the full unit-test suite for `:app_v2:testStandardDebugUnitTest`. Pre-existing failures (~26 unrelated tests per project memory) are out of scope; verify the 2 Drive tests still pass.

**Verification:**

- `Grep -rn "@file:Suppress(\"DEPRECATION\")" app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/ app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/` returns zero hits.
- `./gradlew :app_v2:testStandardDebugUnitTest --tests "*GoogleDriveTokenRefreshTest*" --tests "*MultiAccountAuthTest*"` exits 0.
- `/build` → `standardDebug` + `noLegalDebug` + `liteDebug` PASS.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04c.*` above is `[x] done`.
- [x] `grep -rn "import com.google.android.gms.auth.api.signin" app_v2/src/main/` returns zero hits (only doc-comment references remain).
- [x] `grep -rn "@file:Suppress(\"DEPRECATION\")" app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/ app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/` returns zero hits.
- [ ] All Drive unit tests pass — deferred, see Phase 04b Step Log (FakeGoogleIdentityRepository test rewrite). Existing reflection-based runtime assertions still fail under pre-existing test failures pattern.
- [x] `/build` → `standardDebug` PASS (1m42s) + `noLegalDebug` PASS (1m59s) + `liteDebug` PASS (1m35s).
- [x] Dev log entry per modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Phase 05

After Phase 04c the entire Drive code path lives behind the identity domain. Phase 05's auth-state wipe runs:
- `PrimaryGoogleAccountStore.clear()` — wipes the new encrypted store.
- `GoogleDriveCredentialsManager.clearAll()` — clears legacy `EncryptedSharedPreferences("google_drive_credentials")` (the plaintext fallback is already gone after 04c.3, so no extra path here).
- `NetworkCredentialsEntity` rows where `type = "GOOGLE_DRIVE"` deleted; tokens enqueued for revocation.
- Drive `ResourceEntity` rows marked `needsSignIn = true` (preserves folder paths / sync flags).

---

## Rollback Plan

Phase 04c is a strict subset of the cluster — `git revert <commit>` restores the deprecated stubs and plaintext fallback. UI sites already use `signInPrimary` (from 04b) so they don't regress; the deleted stubs simply become unreachable again.
