# Phase 02 - Repository self-heal to Unbound + unit tests

**Files:**
- `app_v2/src/cloudEnabled/java/com/sza/fastmediasorter/identity/CredentialManagerGoogleIdentityRepository.kt`
- `app_v2/src/test/java/com/sza/fastmediasorter/identity/PrimaryGoogleAccountStateTest.kt`

## Steps

1. Rewrite `getAccessToken` (`:198-205`) to consume `TokenIssueResult`:
   ```
   override suspend fun getAccessToken(scopes: Set<GoogleScope>): GoogleAccessToken? {
       val bound = (_state.value as? PrimaryGoogleAccountState.Bound)?.account ?: return null
       return when (val result = tokenIssuer.issue(bound.email, scopes)) {
           is TokenIssueResult.Success -> result.token
           TokenIssueResult.AccountAbsent -> {
               // Bound account is no longer on the device (GMS ACCOUNT_NOT_PRESENT). Drop the stale
               // binding so the next sign-in re-binds to a present account instead of dead-ending
               // on the absent one. NeedsResignIn is wrong here (its contract is "same email"). S0743.
               Timber.i("Primary Google account no longer present on device; clearing stale binding")
               store.clear()
               _state.value = PrimaryGoogleAccountState.Unbound
               null
           }
           TokenIssueResult.Failed -> {
               _state.value = PrimaryGoogleAccountState.NeedsResignIn(bound, NeedsResignInReason.TokenExpired)
               null
           }
       }
   }
   ```
   Verification: `when` is exhaustive over the three `TokenIssueResult` arms; interface return type stays `GoogleAccessToken?`.

2. Confirm no other code in this file references `issue`'s old nullable shape (it does not - single call site). `signOutPrimary` already does `store.clear()` + `Unbound`; the self-heal path mirrors that minus remote revocation (the absent account cannot be revoked).

3. Update `PrimaryGoogleAccountStateTest`:
   - `:90` `coEvery { issuer.issue(sampleAccount.email, scopes) } returns expectedToken` -> `returns TokenIssueResult.Success(expectedToken)`.
   - `:103` `coEvery { issuer.issue(sampleAccount.email, scopes) } returns null` -> `returns TokenIssueResult.Failed` (this is the existing "flips to NeedsResignIn(TokenExpired)" test - assertion unchanged).
   - Add a new test `getAccessToken on AccountAbsent clears binding and emits Unbound`:
     ```
     coEvery { store.load() } returns sampleAccount
     coEvery { issuer.issue(sampleAccount.email, scopes) } returns TokenIssueResult.AccountAbsent
     repo = buildRepo()
     val token = repo.getAccessToken(scopes)
     assertNull(token)
     coVerify(exactly = 1) { store.clear() }
     assertTrue(repo.state.value is PrimaryGoogleAccountState.Unbound)
     ```
   Verification: three identity tests compile; `issuer` mock no longer returns a bare `GoogleAccessToken?`.

## Build gate

- `pwsh -NoProfile -File a.ps1 dq` -> `assembleStandardDebug` PASS.
- `./gradlew.bat testStandardDebugUnitTest --tests *PrimaryGoogleAccountStateTest` -> PASS (per-class XML green).

## Acceptance

- AccountAbsent -> `store.clear()` + `Unbound`; Failed -> `NeedsResignIn(TokenExpired)`; Success -> token.
- Compile + identity unit test green.
- Device gate per INDEX (owner device already in the dead-end state).
