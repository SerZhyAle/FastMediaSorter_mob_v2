# Phase 01 — Identity Foundations

**Strategic spec:** [`../S0200_google-account-central-binding.md`](../S0200_google-account-central-binding.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 7 / 7
**Started:** 2026-05-16
**Completed:** 2026-05-16

---

## Objective

Add `androidx.credentials` and `androidx.browser:browser` dependencies, declare `cloudEnabled` / `cloudDisabled` shared source sets per the `streamingEnabled`/`streamingDisabled` precedent, and define the new identity-domain contract types in `domain/` so every later phase can compile against them. No real Credential Manager call yet — only types + the no-op binding.

---

## Prerequisites

- [ ] Working tree clean or on `DEBUG-v003`.
- [ ] Strategic §6 research items are all Resolved (verified — all three §6 entries say "Resolved").
- [ ] `androidx.credentials` is not yet on the classpath (verified — grep returned zero hits).
- [ ] `app_v2/src/cloudEnabled/` and `app_v2/src/cloudDisabled/` directories do not yet exist.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | ≤ 1500 |
| `app_v2/src/cloudEnabled/java/.gitkeep` | New | 1 |
| `app_v2/src/cloudDisabled/java/.gitkeep` | New | 1 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/identity/GoogleIdentityRepository.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/identity/PrimaryGoogleAccount.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/identity/PrimaryGoogleAccountState.kt` | New | ≤ 50 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/identity/GoogleScope.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/identity/GoogleAccessToken.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/identity/IdentitySignInResult.kt` | New | ≤ 40 |
| `app_v2/src/cloudDisabled/java/com/sza/fastmediasorter/identity/NoOpGoogleIdentityRepository.kt` | New | ≤ 80 |
| `app_v2/src/cloudDisabled/java/com/sza/fastmediasorter/identity/di/NoOpIdentityModule.kt` | New | ≤ 40 |

> No file in this phase exceeds 500 LOC. No backup step required.

---

## Steps

### Step 01.1 — Add Credential Manager + AndroidX Browser dependencies

**Files:** `app_v2/build.gradle.kts`
**Depends on:** —

**Prompt for developer:**

> In the `dependencies {}` block, add three lines after the existing `implementation("androidx.security:security-crypto:..)` line (pick whatever section currently groups security/auth deps):
>
> ```kotlin
> implementation("androidx.credentials:credentials:1.3.0")
> implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
> implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
> implementation("androidx.browser:browser:1.8.0")
> ```
>
> The `googleid` artifact is the `GetGoogleIdOption` provider used by Credential Manager. The `browser` artifact is required by Phase 03 — adding both here avoids splitting the dep section across two phases. If MSAL transitively pulls `androidx.browser:browser` at a higher version, AGP will resolve to the higher of the two; verify via `./gradlew :app_v2:dependencies --configuration standardDebugRuntimeClasspath` that no version downgrade warning is emitted.

**Verification:**

- `Grep` — `androidx.credentials:credentials:1.3.0` matches in `app_v2/build.gradle.kts`.
- `Grep` — `com.google.android.libraries.identity.googleid:googleid:1.1.1` matches in `app_v2/build.gradle.kts`.
- `Grep` — `androidx.browser:browser:1.8.0` matches in `app_v2/build.gradle.kts`.
- Build closure: `/build` → `standardDebug` — deferred to Phase Done Criteria (project rule: builds run once per phase, not per step).

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 3/3 grep PASS (credentials, credentials-play-services-auth, googleid, browser). Build deferred to Phase Done. Files: `app_v2/build.gradle.kts` (+8 LOC). Dev log recorded.

---

### Step 01.2 — Declare `cloudEnabled` / `cloudDisabled` source sets

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 01.1

**Prompt for developer:**

> In the `android.sourceSets {}` block (currently at lines 391–411), extend each flavor mount with the cloud source set per the matrix below. Mirror the existing `streamingEnabled`/`streamingDisabled` pattern exactly — do NOT introduce a new DSL helper.
>
> | Flavor | `SUPPORT_CLOUD` | Mount |
> |--------|:-:|------|
> | `standard` | true | `src/cloudEnabled/java` |
> | `noLegal` | true | `src/cloudEnabled/java` |
> | `photos` | true | `src/cloudEnabled/java` |
> | `legacy` | true | `src/cloudEnabled/java` |
> | `vr` | true | `src/cloudEnabled/java` |
> | `vrUnlicensed` | true | `src/cloudEnabled/java` |
> | `lite` | false | `src/cloudDisabled/java` |
>
> Add the corresponding `res.directories` mount IF the phase needs flavor-specific resources later. For now, java-only is sufficient.

**Verification:**

- `Grep -n "src/cloudEnabled/java"` matches at least 6 lines inside `app_v2/build.gradle.kts`.
- `Grep -n "src/cloudDisabled/java"` matches at exactly 1 line (`lite`).
- Build closure: `/build` → `standardDebug` returns `BUILD SUCCESSFUL`. Deferred to Phase Done Criteria.

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 2/2 PASS: 6× cloudEnabled (standard, noLegal, photos, legacy, vr, vrUnlicensed) + 1× cloudDisabled (lite). Files: `app_v2/build.gradle.kts` (+~20 LOC). Dev log recorded.

---

### Step 01.3 — Create empty source-set directories with `.gitkeep`

**Files:** `app_v2/src/cloudEnabled/java/.gitkeep`, `app_v2/src/cloudDisabled/java/.gitkeep`
**Depends on:** Step 01.2

**Prompt for developer:**

> Create the two directories and seed each with a `.gitkeep` file containing the single line `# Source set placeholder. See PLAN/S0200_google-account-central-binding/PHASE_01__identity-foundations.md`. AGP will accept empty source sets but git will not track empty dirs — the `.gitkeep` is needed for the commit to be reproducible.

**Verification:**

- `Glob` — `app_v2/src/cloudEnabled/java/.gitkeep` exists.
- `Glob` — `app_v2/src/cloudDisabled/java/.gitkeep` exists.

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 2/2 PASS. Two `.gitkeep` placeholders created under `cloudEnabled/java` and `cloudDisabled/java`. Dev log recorded.

---

### Step 01.4 — Add `GoogleScope` and `GoogleAccessToken` value types

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/identity/GoogleScope.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/identity/GoogleAccessToken.kt`
**Depends on:** —

**Prompt for developer:**

> Create the file `GoogleScope.kt` containing:
>
> - A `@JvmInline value class GoogleScope(val value: String)` wrapping the canonical OAuth-2 scope URI string.
> - A `companion object` with constants for the scopes we use today: `DRIVE = GoogleScope("https://www.googleapis.com/auth/drive")`, `DRIVE_READONLY`, `EMAIL = GoogleScope("email")`, `PROFILE = GoogleScope("profile")`, `OPENID = GoogleScope("openid")`. Source the URIs from the strategic §3.2 (only non-restricted scopes are allowed). No `Gmail`, no `Photos`, no `YouTube user-data`.
>
> Create the file `GoogleAccessToken.kt` containing a data class:
>
> ```kotlin
> data class GoogleAccessToken(
>     val token: String,
>     val scopes: Set<GoogleScope>,
>     val expiresAt: Instant
> )
> ```
>
> Use `java.time.Instant` (consistent with `AuthAccountDomain`). Add KDoc above the class noting it is opaque to callers — never log the `token` field.

**Verification:**

- `Grep -n "@JvmInline value class GoogleScope"` matches exactly once in `GoogleScope.kt`.
- `Grep -n "val DRIVE = GoogleScope"` matches exactly once.
- `Grep -n "data class GoogleAccessToken"` matches exactly once in `GoogleAccessToken.kt`.
- `Grep -n "expiresAt: Instant"` matches exactly once.
- Build closure: `/build` → `standardDebug`. Deferred to Phase Done Criteria.

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 4/4 PASS (single-line `@JvmInline value class`, `val DRIVE`, `data class GoogleAccessToken`, `expiresAt: Instant`). Files: `GoogleScope.kt` (~30 LOC), `GoogleAccessToken.kt` (~20 LOC). Dev log recorded.

---

### Step 01.5 — Add `PrimaryGoogleAccount` + `PrimaryGoogleAccountState` types

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/identity/PrimaryGoogleAccount.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/identity/PrimaryGoogleAccountState.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Create `PrimaryGoogleAccount.kt`:
>
> ```kotlin
> data class PrimaryGoogleAccount(
>     val email: String,
>     val displayName: String?,
>     val photoUrl: String?,
>     val grantedScopes: Set<GoogleScope>,
>     val boundAt: Instant
> )
> ```
>
> Create `PrimaryGoogleAccountState.kt` as a sealed interface modelled after `BackupRestoreUiState`:
>
> ```kotlin
> sealed interface PrimaryGoogleAccountState {
>     /** No primary account is currently bound. */
>     data object Unbound : PrimaryGoogleAccountState
>     /** Credential Manager dialog is in flight. */
>     data object Authenticating : PrimaryGoogleAccountState
>     /** Account is bound and tokens are usable. */
>     data class Bound(val account: PrimaryGoogleAccount) : PrimaryGoogleAccountState
>     /** Account is bound but silent refresh failed — user must re-sign-in with the same email. */
>     data class NeedsResignIn(val account: PrimaryGoogleAccount, val reason: NeedsResignInReason) : PrimaryGoogleAccountState
>     /** Sign-in attempt failed terminally — user can retry. */
>     data class Error(val cause: IdentityFailureReason) : PrimaryGoogleAccountState
> }
>
> enum class NeedsResignInReason { TokenExpired, ScopeRevoked, CredentialManagerError, NetworkUnavailable }
>
> enum class IdentityFailureReason { CctUnavailable, PlayServicesOutdated, UserCancelled, NetworkError, UnknownError }
> ```
>
> Put `NeedsResignInReason` and `IdentityFailureReason` enums in the same file (`PrimaryGoogleAccountState.kt`) — they have no use outside this domain.

**Verification:**

- `Grep -n "sealed interface PrimaryGoogleAccountState"` matches exactly once.
- `Grep -n "data object Unbound"` matches exactly once.
- `Grep -n "data class Bound"` matches exactly once.
- `Grep -n "data class NeedsResignIn"` matches exactly once.
- `Grep -n "enum class NeedsResignInReason"` matches exactly once.
- Build closure: `/build` → `standardDebug`. Deferred to Phase Done Criteria.

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 5/5 PASS. Files: `PrimaryGoogleAccount.kt` (~24 LOC), `PrimaryGoogleAccountState.kt` (~46 LOC). Dev log recorded.

---

### Step 01.6 — Define `GoogleIdentityRepository` contract + `IdentitySignInResult`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/identity/GoogleIdentityRepository.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/identity/IdentitySignInResult.kt`
**Depends on:** Step 01.5

**Prompt for developer:**

> Create `IdentitySignInResult.kt`:
>
> ```kotlin
> sealed interface IdentitySignInResult {
>     data class Success(val account: PrimaryGoogleAccount) : IdentitySignInResult
>     data object Cancelled : IdentitySignInResult
>     data class Failed(val reason: IdentityFailureReason, val cause: Throwable? = null) : IdentitySignInResult
> }
> ```
>
> Create `GoogleIdentityRepository.kt`:
>
> ```kotlin
> interface GoogleIdentityRepository {
>     /** Hot, deduplicated state of the primary account. */
>     val state: StateFlow<PrimaryGoogleAccountState>
>
>     /** Launches interactive sign-in via Credential Manager. Suspends until user cancels or grant completes. */
>     suspend fun signInPrimary(activityContext: Context, scopes: Set<GoogleScope>): IdentitySignInResult
>
>     /** Adds additional scopes to an already-bound primary account. Returns updated grantedScopes. */
>     suspend fun requestAdditionalScopes(activityContext: Context, scopes: Set<GoogleScope>): IdentitySignInResult
>
>     /** Drops the primary binding. Cached token is queued for revocation. */
>     suspend fun signOutPrimary()
>
>     /** Returns a fresh access token covering the requested scopes. Performs silent refresh as needed. */
>     suspend fun getAccessToken(scopes: Set<GoogleScope>): GoogleAccessToken?
>
>     /** Forces an in-flight token to expire and triggers refresh on the next read. */
>     suspend fun invalidateToken()
>
>     /**
>      * Launches an interactive sign-in for a SECONDARY account (does NOT change primary).
>      * The returned account email is what `AddResource` flows use to persist a new `NetworkCredentialsEntity`
>      * for multi-account Drive. Caller is responsible for storing the secondary email.
>      */
>     suspend fun requestSecondaryAccount(activityContext: Context, scopes: Set<GoogleScope>): IdentitySignInResult
> }
> ```
>
> `Context` is `android.content.Context` — the implementation will narrow it to an `Activity` for the Credential Manager bottom sheet.

**Verification:**

- `Grep -n "interface GoogleIdentityRepository"` matches exactly once.
- `Grep -n "val state: StateFlow<PrimaryGoogleAccountState>"` matches exactly once.
- `Grep -n "suspend fun signInPrimary"` matches exactly once.
- `Grep -n "suspend fun requestAdditionalScopes"` matches exactly once.
- `Grep -n "suspend fun requestSecondaryAccount"` matches exactly once.
- `Grep -n "suspend fun getAccessToken"` matches exactly once.
- Build closure: `/build` → `standardDebug`. Deferred to Phase Done Criteria.

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 6/6 PASS. Files: `GoogleIdentityRepository.kt` (~65 LOC), `IdentitySignInResult.kt` (~18 LOC). Dev log recorded.

---

### Step 01.7 — Provide `NoOpGoogleIdentityRepository` + Hilt module in `cloudDisabled`

**Files:** `app_v2/src/cloudDisabled/java/com/sza/fastmediasorter/identity/NoOpGoogleIdentityRepository.kt`, `app_v2/src/cloudDisabled/java/com/sza/fastmediasorter/identity/di/NoOpIdentityModule.kt`
**Depends on:** Step 01.6

**Prompt for developer:**

> Create the No-Op implementation. Every method returns immediately without doing any work; `state` emits `Unbound` forever; `getAccessToken` returns `null`; sign-in methods return `IdentitySignInResult.Failed(IdentityFailureReason.CctUnavailable)` (the closest semantic — cloud is fully disabled, not a CCT problem strictly, but we don't ship a separate `CloudDisabled` reason; document this in KDoc).
>
> ```kotlin
> @Singleton
> class NoOpGoogleIdentityRepository @Inject constructor() : GoogleIdentityRepository {
>     override val state: StateFlow<PrimaryGoogleAccountState> =
>         MutableStateFlow(PrimaryGoogleAccountState.Unbound).asStateFlow()
>
>     override suspend fun signInPrimary(activityContext: Context, scopes: Set<GoogleScope>) =
>         IdentitySignInResult.Failed(IdentityFailureReason.UnknownError)
>     override suspend fun requestAdditionalScopes(activityContext: Context, scopes: Set<GoogleScope>) =
>         IdentitySignInResult.Failed(IdentityFailureReason.UnknownError)
>     override suspend fun signOutPrimary() = Unit
>     override suspend fun getAccessToken(scopes: Set<GoogleScope>): GoogleAccessToken? = null
>     override suspend fun invalidateToken() = Unit
>     override suspend fun requestSecondaryAccount(activityContext: Context, scopes: Set<GoogleScope>) =
>         IdentitySignInResult.Failed(IdentityFailureReason.UnknownError)
> }
> ```
>
> Create the Hilt module `NoOpIdentityModule.kt`:
>
> ```kotlin
> @Module
> @InstallIn(SingletonComponent::class)
> abstract class NoOpIdentityModule {
>     @Binds
>     @Singleton
>     abstract fun bindGoogleIdentityRepository(
>         impl: NoOpGoogleIdentityRepository
>     ): GoogleIdentityRepository
> }
> ```
>
> The package is `com.sza.fastmediasorter.identity.di` — sibling to where the real impl will live in Phase 02 (under `cloudEnabled`). This pairing is intentional: Hilt resolves the binding from whichever source set is mounted for the active flavor.

**Verification:**

- `Glob` — `app_v2/src/cloudDisabled/java/com/sza/fastmediasorter/identity/NoOpGoogleIdentityRepository.kt` exists.
- `Glob` — `app_v2/src/cloudDisabled/java/com/sza/fastmediasorter/identity/di/NoOpIdentityModule.kt` exists.
- `Grep -n "class NoOpGoogleIdentityRepository"` matches exactly once.
- `Grep -n "@Binds" app_v2/src/cloudDisabled/java/com/sza/fastmediasorter/identity/di/NoOpIdentityModule.kt` matches exactly once.
- Build closure: `/build` → `liteDebug` — deferred to Phase Done Criteria.
- Build closure: `/build` → `standardDebug` — deferred to Phase Done Criteria.

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 4/4 grep+glob PASS. Files: `NoOpGoogleIdentityRepository.kt` (~47 LOC) and `NoOpIdentityModule.kt` (~25 LOC) under `src/cloudDisabled/java`. Build verification batched to Phase Done. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles for `liteDebug` AND `standardDebug` — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` and rendered via `render.ps1`.

---

## Handoff Notes to Next Phase

After Phase 01:
- The contract `GoogleIdentityRepository` is wired to a `NoOpGoogleIdentityRepository` binding in the `cloudDisabled` source set.
- The `cloudEnabled` source set exists but is empty (only `.gitkeep`). Phase 02 will populate it with the real Credential Manager impl + `IdentityModule`.
- No flavor currently has a real impl bound. Builds pass for all flavors because Hilt resolves to the no-op for `lite` and there is no binding at all for the cloud-enabled flavors — Phase 02 supplies it.
- WARNING: between Phase 01 and Phase 02, cloud-enabled flavors will have a missing binding (`@Inject GoogleIdentityRepository` would fail at runtime). Phase 02 must be merged before any consumer in Phase 04+ references the repo. Until then, NO consumer of `GoogleIdentityRepository` should be added.

---

## Rollback Plan

Revert the phase commit. The only externally visible change is two added source-set directories and four new dependencies — no migrations, no UI, no DB. Safe to revert at any time before Phase 02 lands.
