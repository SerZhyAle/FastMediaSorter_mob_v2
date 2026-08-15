# Strategic spec: S0743 - Google Drive cloud picker dead-end when bound account was removed from the device

**Status:** Archived
**Priority:** 55
**Date:** 2026-06-27
**Tier:** 3 - Moderate
**Roadmap entry:** ad-hoc (parked finding - device log analysis 2026-06-27, owner could not add a cloud resource)

<!-- auto-approved by /spec-all - 2026-06-27 -->

## Goal

Когда привязанный primary Google-аккаунт удалён из системных аккаунтов устройства, облачный picker уходит в необратимый тупик: токен чеканится для отсутствующего аккаунта, GMS отдаёт `ACCOUNT_NOT_PRESENT`, а повторная авторизация откатывается к тому же мёртвому аккаунту. Цель - распознать это состояние как отдельное, само-залечить устаревшую привязку и дать пользователю войти заново под аккаунтом, который реально есть на устройстве. Happy-path (аккаунт на месте) и кейс «случайно выбрал не тот аккаунт» не должны регрессировать.

---

## 0. Raw capture (verbatim, evidence)

Reproduced on the real Galaxy S21+ (RFCR110NBQJ, Android 15), debug build, 2026-06-27:

1. Main screen lists FTP + 3 SFTP resources, NO cloud resource (confirms the cloud add failed).
2. Add -> Cloud Storage -> "Select Cloud Provider". Google Drive card shows `Connected as serzhyale@gmail.com`.
3. Tapping Google Drive raises a Credential Manager dialog that offers a DIFFERENT account - `serhii.zhyhunenko@gmail.com` (the only Google account currently on the device). User taps "Sign in".
4. `GoogleDriveFolderPickerActivity` opens on "My Drive" -> "No folders found" + red snackbar:
   `ERROR: Authentication failed: Re-authentication required. Please re-add this Google Drive resource.`
5. Back on the provider screen the card STILL shows `Connected as serzhyale@gmail.com` - the binding did NOT switch.

Logcat root cause (01:30:05):
```
W Auth    : [GoogleAuthUtil] error status:ACCOUNT_NOT_PRESENT with method:getTokenWithDetails
W GoogleTokenIssuer$issue: Token issuance failed for scopes=[.../auth/drive, .../auth/drive.readonly]
W GoogleTokenIssuer$issue: java.io.IOException: AccountNotPresent
    at com.google.android.gms.auth.GoogleAuthUtil.getToken(...)
    at com.sza.fastmediasorter.identity.GoogleTokenIssuer$issue$2$1.invokeSuspend(GoogleTokenIssuer.kt:56)
E GoogleDriveFolderPickerViewModel$loadFolders: Authentication failed: Re-authentication required. Please re-add this Google Drive resource.
```

## 1. Problem

The persisted primary Google account (`serzhyale@gmail.com`) is no longer present in the device's system accounts; the device now only has `serhii.zhyhunenko@gmail.com`. Two collaborating defects turn this into a permanent dead-end:

- **Account-removed is misclassified, never reconciled.** `CredentialManagerGoogleIdentityRepository.getAccessToken` maps EVERY null token to `NeedsResignIn(reason = TokenExpired)` and leaves the persisted binding intact. `ACCOUNT_NOT_PRESENT` (the bound account was removed from the device) is a distinct, terminal-for-this-account condition with a different recovery, not a refreshable token. Because the binding is never cleared, the "Connected as serzhyale@" card keeps presenting a connected state for an account the device no longer has.
- **Re-auth reverts to the unusable primary.** `requestAdditionalScopes` signs in for the union scope set, then reverts to the original primary whenever the chosen account differs (`result.account.email != current.email`). When the bound account is gone, every account the user CAN pick is "different", so the guard reverts to the dead account on every attempt - the user can never re-bind to a present account through this flow.

## 2. Goals

1. Token issuance distinguishes "bound account removed from the device" (`ACCOUNT_NOT_PRESENT`) from a refreshable / transient failure.
2. On that signal the identity domain self-heals: clears the stale binding and moves to a state from which the next interactive sign-in re-binds to a present account, instead of perpetually reverting to the absent one.
3. The "Connected as <email>" card and the picker error stop presenting a connected/"re-add the resource" state for a removed account; they route the user to a fresh sign-in.
4. No regression: bound account present -> token mints -> folders list; and the legitimate "user picked the wrong account, keep my primary" case still keeps the primary when the primary is itself usable.

## 3. Design / decisions

### 3.1 ADR-1 - Detect ACCOUNT_NOT_PRESENT at the token issuer

`GoogleTokenIssuer.issue` currently collapses every `GoogleAuthUtil.getToken` failure to `null` via `runCatching`. It will instead distinguish the GMS `ACCOUNT_NOT_PRESENT` outcome (surfaced as `java.io.IOException` whose message is the GMS status name `AccountNotPresent`) and report it to the single caller (`getAccessToken`) as a typed outcome rather than a bare null.

- Rationale: deterministic signal straight from GMS; avoids enumerating device accounts via `AccountManager.getAccountsByType("com.google")`, which on API 26+ does not return Google accounts the app does not own without `GET_ACCOUNTS` - a permission this app must not add.
- The message-string check is the documented GMS behavior for this status; it is isolated to the issuer with an explanatory comment and a single constant.

### 3.2 ADR-2 - Self-heal the binding to Unbound

When `getAccessToken` receives the ACCOUNT_NOT_PRESENT outcome for the bound account, the identity domain drops the binding entirely: `store.clear()` + state `Unbound` (logged at INFO). A removed account is not a "re-sign-in with the same email" situation - `NeedsResignIn` is the wrong state (its contract is "same email"), and the correct recovery is a fresh sign-in with whatever account is present. Going straight to `Unbound` is therefore both semantically correct and the smallest change:

- The existing "Connected as <email>" card (`AddResourceConnectionManager.updateGoogleDriveStatus`) already renders `Unbound` as "Not connected" - no UI change, and the card stops lying about a removed account.
- The next provider tap is a fresh interactive sign-in that binds the present account; the dead-end is gone (recovers on the next attempt after the first self-healing failure).

No new `NeedsResignInReason` value is introduced. Transient / refreshable failures keep the existing `NeedsResignIn(TokenExpired)` behavior and do NOT clear the binding.

The issuer reports the distinction to its single caller via a small sealed result rather than a bare null:

```
sealed interface TokenIssueResult {
    data class Success(val token: GoogleAccessToken) : TokenIssueResult
    data object AccountAbsent : TokenIssueResult  // GMS ACCOUNT_NOT_PRESENT - bound account removed from device
    data object Failed : TokenIssueResult           // transient / refreshable (network, revoked, ..)
}
```

Safe-default: any failure that is not positively identified as ACCOUNT_NOT_PRESENT maps to `Failed` (keep the binding), so a misclassification can never wrongly clear a valid binding.

> `requestAdditionalScopes`' "revert to original primary" guard is NOT touched - it has no production callers today (interface + impl + test only), so its theoretical revert trap is never exercised in this flow. Recorded as a §6 follow-up should it ever be wired up.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0200 (identity domain / Credential Manager), S0639 (restricted-scope OAuth verification - not the cause here), S0742 (sibling cloud/log-hygiene finding from the same device session)
- **Data scope:** touches the encrypted primary-account binding store (clear on stale binding) - no new data persisted, no new permission; tokens remain in-memory only.
- **Flavor scope:** identity code lives in the `cloudEnabled` source set - applies to every cloud-enabled flavor (standard, legacy, photos); no change to non-cloud flavors.
- **API scope:** consumes existing Google OAuth (Credential Manager + `GoogleAuthUtil`); no new scopes, no new API surface.

## 4. Scope / non-goals

- In scope: Google Drive identity binding self-heal + honest card/picker messaging.
- Non-goal (this ticket): proactive reconciliation of the bound account against device accounts at app start; OneDrive / Dropbox analogous stale-binding audit. Both recorded as follow-ups in §6.

## 5. Risks

- Misdetecting a transient failure as ACCOUNT_NOT_PRESENT would wrongly clear a valid binding. Mitigation: detection is gated strictly on the GMS status-name marker; anything not positively identified maps to `Failed` (binding preserved).
- Self-heal to `Unbound` drops the "Connected as" memory for a removed account. This is intended - the account is gone, so claiming it is connected is the actual bug being fixed.

## 6. Open items

- [FOLLOW-UP] Proactive start-up reconciliation of the bound account vs current device accounts (so a stale "Connected as" never renders even before a token mint is attempted). Needs a permission-free device-account probe; deferred.
- [FOLLOW-UP] Audit OneDrive / Dropbox for an analogous stale-binding dead-end.
- [FOLLOW-UP] `requestAdditionalScopes` has no production callers today; if it is ever wired up, apply the same "do not revert to an unusable primary" guard.
- [FOLLOW-UP S0744] Extend self-heal to the Drive browser-auth store (`GoogleDriveBrowserAuthManager`) - device test showed a SECOND stale store keeps the card "Connected" and re-feeds the binding even after the identity self-heal. Owned by `GoogleDriveAuthCoordinator` (holds both identity + browser); identity domain must not depend on the Drive-specific manager.

## 7. Notes

- Owner-environment trigger: the device's bound account (`serzhyale@gmail.com`) is not on this device; the device has `serhii.zhyhunenko@gmail.com`. A fresh user signing in with a present account is unaffected, so this is NOT a hard release blocker for the cloud feature in general - but it IS a real permanent dead-end once a bound account is removed, with no in-flow recovery.
- Immediate manual workaround (owner): disconnect the Google account in-app (`GoogleAccountSettingsViewModel.signOutPrimary`) then add the cloud resource signing in fresh; or re-add `serzhyale@gmail.com` to the device.
- NOT related to S0639: the failure is `ACCOUNT_NOT_PRESENT`, not a scope-verification/consent rejection.

## Last Audit

**2026-06-27 - Partial. Code implemented + unit-tested + device-log-confirmed; full E2E recovery on the test device blocked by a second stale store (follow-up S0744).**

Implemented (`cloudEnabled` source set):
- `GoogleTokenIssuer.issue` now returns sealed `TokenIssueResult { Success | AccountAbsent | Failed }`, classifying the GMS `ACCOUNT_NOT_PRESENT` failure (IOException message marker, bounded cause walk, safe-default to `Failed`).
- `CredentialManagerGoogleIdentityRepository.getAccessToken` self-heals on `AccountAbsent`: `store.clear()` + state `Unbound` + INFO log; `Failed` keeps `NeedsResignIn(TokenExpired)`; `Success` returns the token.
- `PrimaryGoogleAccountStateTest`: mocks updated to the sealed type; new `AccountAbsent` test. `testStandardDebugUnitTest --tests *PrimaryGoogleAccountStateTest` = 6/6 green.

Build: `assembleStandardDebug` PASS; debug APK installed on RFCR110NBQJ.

Device evidence (RFCR110NBQJ, data preserved in the dead-end state):
- Self-heal fires - logcat: `W GoogleTokenIssuer: java.io.IOException: AccountNotPresent` -> `I CredentialManagerGoogleIdentityRepository: Primary Google account no longer present on device; clearing stale binding`.
- Decisive root cause from `dumpsys account`: `serhii.zhyhunenko@gmail.com` is `type=com.google` (real system Google account); `serzhyale@gmail.com` exists ONLY as `type=com.osp.app.signin` / `com.samsung.android.mobileservice` (Samsung account), NOT `com.google`. `GoogleAuthUtil.getToken(serzhyale@, drive)` therefore can never succeed -> ACCOUNT_NOT_PRESENT is correct and permanent for that binding.
- E2E dead-end NOT broken on this device: after the identity self-heal, the "Connected as serzhyale@" card persists and a second token attempt re-hits ACCOUNT_NOT_PRESENT. Cause: a parallel Drive browser-auth store (`GoogleDriveBrowserAuthManager.peekStoredAccountEmail() == serzhyale@`) the identity self-heal does not (and by layering must not) clear; the card falls back to it and the binding is restored from it. Tracked as S0744.

Verdict: the implemented identity-domain self-heal is correct and resolves the GENERAL dead-end (single-store Credential-Manager users). This device additionally needs the Drive browser-auth store to self-heal (S0744) before its specific dead-end clears. No regression to the happy path or the transient-failure path.
