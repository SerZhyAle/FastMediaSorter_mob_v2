# Strategic Specification: S0233 — Google Drive sign-in: fallback when CredentialManager fails

**Ticket:** S0233
**Status:** Archived
**Priority:** 80
**Date:** 2026-05-17

<!-- auto-approved by /spec-all — 2026-05-18 -->
<!-- Scope narrowed 2026-05-18: this round implements Front A (Play Services auto-repair) only; Fronts B (legacy GoogleSignIn fallback) and C (UI diagnostic) are spawned out — see §10. -->


---

## 1. Problem

On Samsung SM-S731B / Android 16 / SDK 36 (a current production device class) the Google Drive sign-in flow fails immediately with `PlayServicesOutdated` and the user is left without any usable path to add a Drive resource. The CredentialManager call returns `NoCredentialException: No credentials available`, the identity domain emits `IdentitySignInResult.Failed(PlayServicesOutdated)`, the card transitions to `PrimaryGoogleAccountState.Error`, and no further action is possible.

Log evidence (`logs/fastmediasorter_20260517_003023.log`, lines 225..240):

- `D/UserAction: CLICK: GoogleDriveCard (AddResource)` at 00:30:29.
- `D/SLog: Starting unified interactive signIn {provider=GOOGLE_DRIVE}` at 00:30:30.
- `D/SLog: Interactive signIn launched {provider=GOOGLE_DRIVE}` at 00:30:30.
- `E/App: GoogleDriveAuthPlugin: signInPrimary failed: PlayServicesOutdated` at 00:30:31.
- `androidx.credentials.exceptions.NoCredentialException: No credentials available` at `CredentialProviderFrameworkImpl.kt:172`.

The device has Google Play Services installed (this is a production Samsung phone with Android 16) but the framework's credential provider lookup returns nothing for `GetGoogleIdOption`. The S0200 sign-in path assumes Credential Manager is the single supported entry point — there is no second attempt, no Play-Services-update prompt, no legacy `GoogleSignIn` Activity fallback.

---

## 2. Goals

1. When `signInPrimary` fails with `PlayServicesOutdated` (or any `NoCredentialException` derivative), the user has a concrete next step that can succeed without a fresh OS install.
2. Where Google Play Services itself is fixable (out-of-date but updatable), the app triggers the standard Play-Services update flow (`GoogleApiAvailability.makeGooglePlayServicesAvailable`) instead of failing silently.
3. Where Play Services is missing/broken and unfixable, the app surfaces a clear user-visible message naming the cause and the resolution (see S0234 for the UI layer).
4. The fallback path does not introduce a second long-term auth backend — Credential Manager remains the primary; the legacy path (if used) is a recovery-only escape valve.

**Non-goals:**

- Replacing Credential Manager with legacy `GoogleSignIn` on devices where Credential Manager works.
- Supporting devices without any Google Play Services (e.g., Huawei, F-Droid users) — outside the cloud flavor matrix.
- Reworking the `GoogleIdentityRepository` contract surface (`signInPrimary(activity, scopes)` stays).
- Touching OneDrive / Dropbox auth flows (separate tickets S0232, S0235).

---

## 3. Constraints

- **Flavor matrix:** all `SUPPORT_CLOUD = true` flavors (`standard`, `noLegal`, `photos`, `legacy`, `vr`, `vrUnlicensed`). Implementation must respect `dev/FLAVOR_DEVELOPMENT_RULES.md` — the fallback impl lives in `src/cloudEnabled/java/` (shared cloud-enabled source set) alongside the Credential Manager impl.
- **API level:** `minSdk 26`. Legacy `GoogleSignIn` API is deprecated by Google but still functional on API 26+; not all original Drive scopes work via legacy on every device version — the tactical spec must verify scope coverage.
- **No new permissions:** Play-Services-update flow uses `GoogleApiAvailability` which requires no extra permissions.
- **Existing domain types:** reuse `IdentityFailureReason.PlayServicesOutdated` (already defined). Add new reasons only if Play Services flow distinguishes "missing" vs "outdated" vs "update-in-progress".
- **No bypass:** the fallback must still produce a valid OAuth access token usable by Drive REST calls. If legacy `GoogleSignIn` cannot grant the same scope set, document it and route the user back to the "update Play Services" path.

---

## 4. Current Architecture Context

S0200 introduced `GoogleIdentityRepository.signInPrimary(activity, scopes)` as the single auth entrypoint. The Credential Manager implementation lives in `src/cloudEnabled/java/` and is bound via Hilt. `GoogleDriveAuthPlugin` (`src/main/java/.../data/cloud/GoogleDriveAuthPlugin.kt`) calls it from the `AddResourceActivity → GoogleDriveCard` click path, and `GoogleAccountSettingsViewModel` calls it from the Settings card click path. The repository emits `IdentitySignInResult.Failed(PlayServicesOutdated)` on `NoCredentialException`, which propagates to the state Flow and to `consumeImmediateResult()`.

There is no recovery path. The plugin and ViewModel both surface the failure as a state transition; no Play-Services-update intent is launched, no legacy `GoogleSignIn` Activity is started, no user-actionable diagnostic is shown.

---

## 5. Proposed Approach

Three layered fallbacks, ordered by user friction (lowest first):

**A — Play Services update prompt.**

Before declaring `PlayServicesOutdated`, the identity domain checks `GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)`. If the status is `SERVICE_VERSION_UPDATE_REQUIRED` / `SERVICE_DISABLED` / `SERVICE_MISSING`, launch `GoogleApiAvailability.makeGooglePlayServicesAvailable(activity)` — this opens the standard Play Services repair flow. On success → retry Credential Manager `signInPrimary` automatically. On user-cancel → emit `Failed(PlayServicesOutdated)` with an updated `cause.message` that explains the user dismissed the update.

**B — Legacy GoogleSignIn fallback.**

If Play Services is healthy but Credential Manager still returns `NoCredentialException` (the device is in a known-broken state — e.g., a downgraded Play Services with broken credential providers), fall back to `com.google.android.gms.auth.api.signin.GoogleSignIn` Activity-result flow with the same scope set. The legacy API requests `requestServerAuthCode(WEB_CLIENT_ID)` + `requestScopes(*scopes)` and uses the existing `startActivityForResult` channel. The result feeds into `GoogleIdentityRepository.attachExternalCredential(...)` (a new identity-domain entry point — tactical spec defines the contract).

**C — Manual diagnostic + user instruction.**

If both A and B fail, the identity domain emits a richer `IdentityFailureReason.PlayServicesUnavailable(diagnostic: String)` (replaces / extends the current `PlayServicesOutdated` enum entry). The string carries the precise Play Services status code returned by `isGooglePlayServicesAvailable` — useful for support. The UI (S0234) renders a user-friendly message with the diagnostic + a button "Открыть Google Play Услуги" that links to `market://details?id=com.google.android.gms`.

The fallback chain stops at the first success. Logging records which path produced the credential (`Timber.d("GoogleIdentitySignIn: succeeded via path=<credentialManager|playServicesRepair|legacyGoogleSignIn>")`).

---

## 6. Open Questions

1. Does the legacy `GoogleSignIn` API on Android 16 (API 36) still grant the `https://www.googleapis.com/auth/drive` scope without Google forcing a redirect to Credential Manager? Google has been progressively narrowing legacy scope grants — tactical spec must verify on the target device.
2. Should the Play-Services-update flow (path A) auto-retry `signInPrimary` after the update succeeds, or require an explicit user re-tap on the card? Owner decision; auto-retry is more friction-free but masks the cause from analytics.
3. The `WEB_CLIENT_ID` for legacy `GoogleSignIn` — does the existing Drive REST client carry one, or is a new OAuth2 client (Web type) required in Google Cloud Console? Tactical spec resolves with a Google Cloud Console audit.
4. Status code mapping: `GoogleApiAvailability.SERVICE_INVALID` and `SERVICE_UPDATING` — distinct user messages, or collapse into one? Owner UX call.

---

## 7. Risks

- **Medium** — adding a legacy `GoogleSignIn` path reintroduces a deprecated dependency surface. Risk that Google removes it entirely in a future Play Services update.
- **Low** — Play Services repair flow is a Google-supplied UI; minimal regression surface.
- **Compatibility** — legacy `GoogleSignIn` and Credential Manager use different account-selection UIs. Two consecutive prompts on a single click could confuse users; suppress with a brief progress indicator + single visible UI per attempt.

---

## 8. User Impact

Drive sign-in succeeds on devices where it currently silently fails.

- **EN:** Google Drive sign-in now recovers automatically when Google Play Services needs an update, and falls back to a guided repair flow if it doesn't.
- **RU:** Вход в Google Drive теперь автоматически восстанавливается, когда нужно обновить Google Play Услуги, и предлагает пошаговую помощь, если автоматический путь не сработал.
- **UK:** Вхід у Google Drive тепер автоматично відновлюється, коли потрібно оновити Google Play Послуги, та пропонує покрокову допомогу, якщо автоматичний шлях не спрацював.

Bug fix; `docs/FEATURES.md` not updated. `dev/FUNCTIONALITY.log` gets a FIX entry.

---

## 9. Related Specs

- **S0200** `Implemented` — Central Google account binding. Introduced Credential Manager as the primary path; this spec adds the recovery chain S0200 lacks.
- **S0239** `BlockNeedUserTest` — Credential Manager GMS version guard. Already implemented the pre-check side (`gmsGuard()` in `CredentialManagerGoogleIdentityRepository`) — detects `SERVICE_VERSION_UPDATE_REQUIRED` before invoking Credential Manager and reports it as `PlayServicesOutdated` instead of leaking it as `UnknownError`. S0233 Front A builds on top: when S0239's guard fires, S0233 actually offers the user a remediation path instead of just emitting an error.
- **S0234** `Draft` (parallel) — Google Account card error UI. Pairs with the spawned Front C work (see §10).
- **S0232** `Verified` (2026-05-18) — Unified `applicationId` for cloud-enabled flavors. Removed the cross-flavor OAuth-registration barrier; S0233 work is no longer obstructed by mismatched package names.
- **S0235** `BlockNeedUserTest` (2026-05-18) — Dropbox `files.metadata.read` scope regression. Different provider, different failure mode; both specs now sit in `BlockNeedUserTest` awaiting the same device-test pass.
- **S0075** `Partial` — device-reach-google-play. Likely overlap on Play Services availability detection; tactical spec checks for shared helper.

---

## 10. Scope decision (2026-05-18 review)

This round implements **Front A only** (Play Services auto-repair). Fronts B and C are deferred:

- **Front A — Play Services repair flow (this round):** when `gmsGuard()` returns `PlayServicesOutdated` and the calling context is an `Activity`, launch `GoogleApiAvailability.makeGooglePlayServicesAvailable(activity).await()`. On success → re-run `gmsGuard()`; if it now returns `null`, continue with Credential Manager `signInPrimary` as if the user had tapped sign-in afresh. On failure (user cancelled the Play Services dialog or the device cannot satisfy the version) → fall through to the existing `Failed(PlayServicesOutdated)` path. Self-contained backend change; no UI strings, no new identity-domain methods, no flavor-isolation work.
- **Front B — Legacy GoogleSignIn fallback (deferred to follow-up):** still requires §6 Q1 device test (does legacy `GoogleSignIn` still grant `https://www.googleapis.com/auth/drive` on Android 16?) before tactical can be written safely. Will be spawned as a separate ticket if Front A's device test reveals devices that need it.
- **Front C — UI scope-missing diagnostic (deferred to S0234):** absorbed by S0234's broader UI work on the Google Account card.

The narrower scope brings the change footprint to ~30..60 LOC inside `app_v2/src/cloudEnabled/java/com/sza/fastmediasorter/identity/CredentialManagerGoogleIdentityRepository.kt` plus debug probes; no other source set is touched in this round.

### Device-test contract (BlockNeedUserTest exit gate)

Operator runs the standard or noLegal debug build on the failing device (Samsung SM-S731B / Android 16 / SDK 36 from §1, or an emulator with deliberately downgraded GMS):

1. Tap GoogleDriveCard (AddResource) — observe whether the Play Services repair dialog now appears, or whether the existing `PlayServicesOutdated` error still surfaces.
2. Logcat must contain `S0233: Front A repair attempt start gmsStatus=<UPDATE_REQUIRED|UNAVAILABLE>`. On repair success: `S0233: Front A repair succeeded; re-running gmsGuard`. On failure: `S0233: Front A repair declined or failed cause=<message>`.
3. If the dialog appears AND the user completes the update AND sign-in succeeds → green path; flip to `Verified` via `/spec-check`.
4. If the dialog appears but the user cancels → orange path; behaviour should be identical to today's `Failed(PlayServicesOutdated)` (no regression). Still `Verified` as far as S0233 is concerned; Front B becomes the next escalation.
5. If the dialog never appears OR `gmsGuard()` keeps returning `UNAVAILABLE` (device cannot host any compatible GMS) → spawn Front B work as a fresh ticket.
