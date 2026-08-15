# Research 01 - call-site map for the cloud auth self-heal (S0743)

Source: live code read 2026-06-27 (working tree). Grounds the tactical plan.

## Token issuance

- `GoogleTokenIssuer.issue(email, scopes): GoogleAccessToken?` - `app_v2/src/cloudEnabled/.../identity/GoogleTokenIssuer.kt:48-66`. Swallows every `GoogleAuthUtil.getToken` failure to `null` via `runCatching{}.onFailure{ Timber.w }.getOrNull()`. The ACCOUNT_NOT_PRESENT signal is lost here.
- Single production caller: `CredentialManagerGoogleIdentityRepository.getAccessToken` - `app_v2/src/cloudEnabled/.../identity/CredentialManagerGoogleIdentityRepository.kt:198-205`.
- Test mocks: `app_v2/src/test/.../identity/PrimaryGoogleAccountStateTest.kt:90` (`returns expectedToken`), `:103` (`returns null`), `:78` (`coVerify exactly=0 { issue }`).

## getAccessToken contract + consumers

- Interface `GoogleIdentityRepository.getAccessToken(scopes): GoogleAccessToken?` - `domain/identity/GoogleIdentityRepository.kt:54`. Nullable contract is preserved; self-heal is internal behavior.
- Consumers tolerate null already:
  - `GoogleDriveAuthCoordinator.resolveAccessToken` -> `identityRepository.getAccessToken(driveScopes)?.token` - `data/cloud/GoogleDriveAuthCoordinator.kt:221` (null -> AuthResult.Error upstream).
  - `GoogleDriveThumbnailModelLoader.kt:227`, `CloudThumbnailModelLoader.kt:346` - `runBlocking { ...getAccessToken(..)?.token }` (null -> no thumbnail; safe).

## Re-auth paths

- `requestAdditionalScopes` (`CredentialManagerGoogleIdentityRepository.kt:138`) - the revert-to-original-primary guard at `:143-148`. NO production callers (grep across `app_v2/src/**/*.kt`: only interface decl, impl decl, NoOp decl, KDoc, test). Its revert trap is therefore not exercised in the cloud-add flow -> out of scope, recorded as a §6 follow-up.
- Interactive sign-in actually used: `signInPrimary` (`:71`) via `GoogleDriveInteractiveSignInCoordinator.kt:38`, `GoogleAccountSettingsViewModel.kt:92`, `BackupRestoreViewModel.kt:154`. `signInPrimary` -> `performSignIn` -> `store.save(account)` + state `Bound`. A fresh `signInPrimary` after self-heal to `Unbound` binds whatever present account the user picks - no revert.

## "Connected as" card

- `AddResourceConnectionManager.updateGoogleDriveStatus` - `ui/addresource/AddResourceConnectionManager.kt:122-131`. Reads `(state as? Bound)?.account?.email ?: browserAuthManager.peekStoredAccountEmail()`. `Unbound` -> null -> `R.string.not_connected`. So self-heal to `Unbound` makes the card honest with NO UI change required.

## ACCOUNT_NOT_PRESENT detection

- GMS surfaces it (observed logcat): `W Auth: [GoogleAuthUtil] error status:ACCOUNT_NOT_PRESENT` and the caught throwable is `java.io.IOException: AccountNotPresent`.
- Detection: match the GMS status-name marker `"AccountNotPresent"` against the throwable message (shallow cause walk). Documented stable GMS behavior. Safe-default: anything NOT matched -> `Failed` (binding preserved), so a false negative never clears a valid binding.

## State / store primitives

- `PrimaryGoogleAccountState.Unbound` (`domain/identity/PrimaryGoogleAccountState.kt:12`).
- `PrimaryGoogleAccountStore.clear()` (`cloudEnabled/.../identity/PrimaryGoogleAccountStore.kt:57`).
- No new `NeedsResignInReason` value needed - `NeedsResignIn` semantics ("re-sign-in with the same email") are wrong for a removed account.
