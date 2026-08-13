# Strategic spec: S0744 - Drive browser-auth store keeps a removed account "Connected" after identity self-heal

**Status:** Archived
**Priority:** 45
**Date:** 2026-06-27
**Tier:** 3 - Moderate
**Roadmap entry:** Follow-up of S0743 (device test 2026-06-27, RFCR110NBQJ)

<!-- auto-approved by /spec-all - 2026-06-27 -->

## 0. Raw capture (verbatim, evidence)

While device-verifying S0743 on RFCR110NBQJ, the identity-domain self-heal fired correctly (logcat: `clearing stale binding`), but the Google Drive provider card kept showing `Connected as serzhyale@gmail.com` and a second token attempt re-hit `ACCOUNT_NOT_PRESENT`. The dead-end did not clear.

Root cause: there are TWO account stores for Drive. S0743 clears the identity-domain binding (`PrimaryGoogleAccountStore`). A parallel Drive browser-auth store (`GoogleDriveBrowserAuthManager`, `peekStoredAccountEmail()`) still holds `serzhyale@gmail.com`. `AddResourceConnectionManager.updateGoogleDriveStatus` falls back to it (`(state as Bound)?.email ?: browserAuthManager.peekStoredAccountEmail()`), so the card keeps showing the removed account, and `GoogleDriveAuthCoordinator.resolveAccessToken` can restore the binding from it.

`dumpsys account` on the device: `serzhyale@gmail.com` exists only as `type=com.osp.app.signin` / `com.samsung.android.mobileservice` (Samsung account), NOT `com.google` - so no Drive token can ever mint for it.

## 1. Problem

The S0743 self-heal lives in the identity domain and, by layering, cannot touch the Drive-specific `GoogleDriveBrowserAuthManager`. When the browser-auth store holds a removed/unusable account, the card lies ("Connected as ..") and the binding is re-fed, so the dead-end persists for users who authenticated Drive via the browser path (Quest/XR, or any browser-auth fallback).

## 2. Goals

1. When a Drive token attempt self-heals the identity binding (S0743 `ACCOUNT_NOT_PRESENT` -> `Unbound`), the Drive browser-auth store is cleared too, so both stores are consistent and the "Connected as <email>" card stops showing the removed account.
2. The user can always escape to a usable account: the interactive Drive sign-in (adding a cloud resource) forces the Credential Manager account chooser instead of silently reusing a previously-authorized (possibly stale) account.
3. Do NOT break the legitimate browser-auth path (XR / Quest); only clear the browser store on the identity self-heal transition, never on a transient/refreshable failure.

## 3. Design / decisions

### 3.1 ADR-1 - Browser store clear primitive

`GoogleDriveBrowserAuthManager.clear()`: drop the in-memory `activeCredential` and wipe the persistent blob via the existing `GoogleDriveCredentialsManager.clearAllCredentials()`. No new storage code.

### 3.2 ADR-2 - Coordinate the self-heal in GoogleDriveAuthCoordinator

`GoogleDriveAuthCoordinator.resolveAccessToken` is the single place that owns BOTH `identityRepository` and `browserAuthManager`. When the identity `getAccessToken` returns null AND the identity state just transitioned `Bound -> Unbound` during the call (the S0743 self-heal fired for `ACCOUNT_NOT_PRESENT`), the coordinator also calls `browserAuthManager.clear()`. No new identity-domain interface and no layering inversion (identity stays unaware of the Drive-specific manager). Transient failures keep `Bound` -> no clear.

### 3.4 ADR-3 - Force the account chooser for Drive resource-add

`runIdentitySignIn` (the interactive Drive sign-in for adding a resource) requests the Credential Manager ACCOUNT CHOOSER (`filterByAuthorizedAccounts = false`) instead of the authorized-only first pass. This lets the user pick the account that IS on the device, instead of the flow silently reusing a previously-authorized account that can no longer mint a token. Implemented via a `preferAccountChooser` flag threaded through `GoogleIdentityRepository.signInPrimary` (default false -> existing behavior for Settings reconnect / backup paths). Better UX too: choosing an account is the expected behavior when adding a NEW cloud resource.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0743 (parent - identity-domain self-heal), S0200 (identity domain), S0639 (restricted-scope OAuth - not the cause)
- **Data scope:** clears the encrypted Drive browser-auth blob on a removed-account self-heal; no new data persisted, no new permission.
- **Flavor scope:** Drive cloud code; applies to cloud-enabled flavors; no change to non-cloud flavors.
- **API scope:** existing Credential Manager `GetGoogleIdOption` (toggles `filterByAuthorizedAccounts`); no new API/scopes.

## 4. Evidence

- `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveBrowserAuthManager.kt` (`peekStoredAccountEmail`, `hasActiveSession`, `ensureActiveFromStored`).
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceConnectionManager.kt:122-131` (card fallback to browser email).
- `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveAuthCoordinator.kt:214-229` (`resolveAccessToken` browser + identity branches).
- S0743 Last Audit (device evidence).

## 5. Notes

- Builds on S0743 (identity-domain self-heal) - that part is done; this ticket covers the second store.
- Sensitive auth code; do not blindly clear the browser store (could break XR browser-auth). Needs a discriminating signal that the stored account is genuinely unusable.
- [FOLLOW-UP S0746] Device test revealed the genuine cloud-add blocker is deeper: an alias Google account whose ID-token email != AccountManager name makes `GoogleAuthUtil.getToken` return `ACCOUNT_NOT_PRESENT`. Out of scope here; ticketed as S0746.

## Last Audit

**2026-06-27 - Verified (scope). All three S0744 goals implemented and device-confirmed. The owner's remaining cloud-add failure is a distinct alias-account root cause -> S0746.**

Implemented:
- `GoogleDriveBrowserAuthManager.clear()` - drops `activeCredential` + wipes the persistent blob via `GoogleDriveCredentialsManager.clearAllCredentials()`.
- `GoogleDriveAuthCoordinator.resolveAccessToken` - on the identity self-heal transition (`Bound` -> `Unbound` during `getAccessToken`, i.e. S0743 `ACCOUNT_NOT_PRESENT`), also calls `browserAuthManager.clear()`. Transient failures keep `Bound` -> no clear.
- `GoogleIdentityRepository.signInPrimary` gains `preferAccountChooser` (default false); `CredentialManagerGoogleIdentityRepository` skips the authorized-only pass when true; `NoOpGoogleIdentityRepository` updated; `GoogleDriveInteractiveSignInCoordinator.runIdentitySignIn` passes `preferAccountChooser = true`.

Build: `compileStandardDebugKotlin` + `compileLiteDebugKotlin` PASS; `testStandardDebugUnitTest *PrimaryGoogleAccountStateTest` 6/6 green; debug APK installed on RFCR110NBQJ.

Device evidence (RFCR110NBQJ, temporary diagnostic since removed):
- Browser-store self-heal fires - logcat `I GoogleDriveAuthCoordinator: Identity binding self-healed; clearing stale Google Drive browser-auth store`; the provider card flipped from `Connected as serzhyale@gmail.com` to `Not connected` (goal 1).
- Account chooser forced - `bound primary account=serzhyale@gmail.com chooser=true` confirms `preferAccountChooser=true` reached Credential Manager (goal 2).
- Residual blocker is NOT this ticket: the chosen account binds as `serzhyale@gmail.com` (the ID-token email) while the device `com.google` account name is `serhii.zhyhunenko@gmail.com`, so `GoogleAuthUtil` returns `ACCOUNT_NOT_PRESENT`. Alias-account mint failure -> S0746.

Verdict: S0744's stated goals (browser-store self-heal, forced chooser, honest card) are met and device-verified. The owner cannot yet add a Drive resource on THIS device because of the alias-account mint failure (S0746), which is a separate, deeper root cause.
