# Strategic Specification: S0235 — Dropbox folder listing fails: `files.metadata.read` scope not granted

**Ticket:** S0235
**Status:** Archived
**Priority:** 80
**Date:** 2026-05-17

<!-- auto-approved by /spec-all — 2026-05-18 -->
<!-- §9: S0232 is Verified as of 2026-05-18 (was Draft when this spec was written) -->
<!-- front A landed in commit ace216c4 on 2026-05-17 05:17; awaiting owner device test -->


---

## 1. Problem

After successful Dropbox OAuth2 PKCE sign-in, the first attempt to list folders inside the new account fails with a `400 Bad Request` from `files/list_folder`. The error message says the access token is missing the `files.metadata.read` scope. Adding a Dropbox resource is therefore blocked: the folder picker cannot show anything to choose from.

Log evidence (`logs/fastmediasorter_20260517_003023.log`, lines 404..520):

- `D/App: DropboxAuthPlugin.handleResume: finishing authentication` at 00:31:16.
- `D/App: Registered Dropbox account in database: serzhyale@gmail.com` at 00:31:17 — account is created and tokens stored.
- `D/App: Dropbox credentials saved (account: serzhyale@gmail.com)` — credentials persisted.
- `I/SLog: Unified interactive auth succeeded {provider=DROPBOX, account=serzhyale@gmail.com}` — auth layer reports success.
- `onCreate: DropboxFolderPickerActivity` at 00:31:17.
- `E/App: Failed to list folders in: ` at 00:31:17.
- `com.dropbox.core.BadRequestException: Error in call to API function "files/list_folder": Your app (ID: 6478499) is not permitted to access this endpoint because it does not have the required scope 'files.metadata.read'. The owner of the app can enable the scope for the app using the Permissions tab on the App Console.`
- `E/App: Failed to load Dropbox folders: Не получилось загрузить папки из облака. Попробуйте ещё раз.` — the user sees a generic "Try again" message that gives no hint that the scope is the cause.
- A second user-triggered retry produces the same exception.

**Owner statement (2026-05-17):** the flow used to work — this is a regression, not a never-shipped path.

Two independent causes are possible and the tactical spec must establish which:

1. **Code regression:** the OAuth2 PKCE call (`Auth.startOAuth2PKCE(activity, appKey, requestConfig)` in `DropboxClient.kt:213-215`) does not explicitly request the `files.metadata.read` scope. If Dropbox previously granted it via app-defaults from the App Console and recently tightened to require explicit `scope` parameters in the PKCE call, our call silently drops to a scope-less token.
2. **App Console drift:** the `files.metadata.read` permission on the Dropbox App (ID 6478499) was disabled at some point, removing it from the app-default scope set. Re-enabling on the App Console restores the previous behaviour without any code change.

The DropboxClient call site is missing both the explicit `scope` and `tokenAccessType` arguments to `startOAuth2PKCE` — the SDK supports both via an overload. The default tokenAccessType is `LEGACY` (non-refreshable long-lived tokens), which Dropbox has been deprecating in favor of `OFFLINE` (refresh tokens). If the App Console moved to `OFFLINE`-only or scope-required mode, the legacy bare call breaks.

---

## 2. Goals

1. Adding a Dropbox account in any cloud-enabled flavor reaches the folder picker AND shows the user's root folders (or a clearly typed "folder is empty" state).
2. The user-visible error when the scope grant is genuinely missing names the cause and points to the resolution — not "Try again".
3. The fix is durable: a future Dropbox-side tightening of OAuth2 defaults does not silently break the path again.
4. Token storage migration: any cached scope-less tokens from the broken sessions are invalidated so the next sign-in goes through the full consent flow with the new scope set.

**Non-goals:**

- Migrating off the Dropbox Java SDK to a different REST client.
- Adding new Dropbox features (sharing, comments, paper docs) — only the existing list/read/write capability set.
- Touching Google Drive / OneDrive auth — separate tickets (S0232, S0233).

---

## 3. Constraints

- **Flavor matrix:** all `SUPPORT_CLOUD = true` flavors. Implementation follows `dev/FLAVOR_DEVELOPMENT_RULES.md`.
- **Dropbox SDK version:** stay on the currently pinned `com.dropbox.core:dropbox-core-sdk` version unless the tactical spec proves an upgrade is required.
- **External dependency:** App Console permission re-enablement (if cause #2 wins) is an owner action outside the codebase. Spec captures it as a binding step but does not perform it.
- **Locale audit:** new user-visible strings (clear scope-error explanation) added to EN/RU/UK in one commit; `check_strings_localized.ps1 -KeyPrefix "s0235_"` must pass.
- **No silent retry:** if the scope is missing on the persisted token, the next list/read attempt MUST trigger a re-auth prompt (not a generic error toast).
- **Communication policy:** all new strings pass `docs/COMMUNICATION_POLICY.md` §6.

---

## 4. Current Architecture Context

`DropboxAuthPlugin.startInteractiveSignIn(activity)` (`app_v2/src/main/java/.../data/cloud/DropboxAuthPlugin.kt:21..32`) calls `DropboxClient.startPkceAuthentication(activity, appKey)`. That method (`DropboxClient.kt:213..215`) invokes `Auth.startOAuth2PKCE(activity, appKey, dbxRequestConfig)` with **no scope parameter and no tokenAccessType**. After the browser callback, `DropboxClient.finishAuthentication()` returns the captured credentials, the account is registered in the DB, and the flow transitions to `DropboxFolderPickerActivity`.

The picker calls `files/list_folder` on the new client; the API rejects it with `BadRequestException` because the token lacks `files.metadata.read`. The current UI catches the exception, logs it, and shows a generic "Не получилось загрузить папки .." string (`Failed to load Dropbox folders`) — no parsing of the Dropbox-specific error code that names the scope.

---

## 5. Proposed Approach

Two parallel fronts:

**A — Explicit scope + offline token in code.**

- Change `startPkceAuthentication` to call the SDK overload `Auth.startOAuth2PKCE(activity, appKey, requestConfig, scopes, includeGrantedScopes, tokenAccessType)` with:
  - `scopes = Arrays.asList("files.metadata.read", "files.content.read", "files.content.write", "account_info.read")`
  - `tokenAccessType = TokenAccessType.OFFLINE` (refresh-capable, Dropbox-preferred default for new apps).
  - `includeGrantedScopes = IncludeGrantedScopes.USER` — if the user has previously granted broader scopes, request grants are merged.
- On `finishAuthentication`, validate that the granted scope set contains `files.metadata.read`. If not — drop the credentials, re-prompt the user with the explicit scope set.

**B — App Console verification + reactivation (owner action).**

- Owner opens Dropbox App Console for App ID 6478499 → Permissions tab → ensure `files.metadata.read`, `files.content.read`, `files.content.write`, `account_info.read` are enabled and saved.
- Owner verifies the OAuth2 redirect URIs match the app's `dropbox_app_key` (already known: `u43ocp...`).

**C — UI: scope-missing diagnostic.**

When `DropboxFolderPickerViewModel` (or equivalent) catches a `BadRequestException` whose `errorMessage` matches `"required scope '([^']+)'"`, render:

- A specific error toast or inline message naming the missing scope.
- A primary CTA: "Sign in again" — triggers `DropboxClient.clearCredentialsFor(account)` + restarts the OAuth flow with the explicit scope set from front A.

Generic Dropbox API errors fall back to the current copy.

**D — Token migration.**

On app first-launch after this spec ships:

- Walk all stored Dropbox credentials.
- For each, decode the token's scope set via `DbxClientV2 → /check/user` (a minimal API call that returns the granted scopes implicitly through its success/failure pattern, or use `oauth2/access_token` introspection if available).
- For any credential lacking `files.metadata.read`, mark the resource as `needs_sign_in = true` and the next list attempt prompts re-auth.

---

## 6. Open Questions

1. Does the Dropbox SDK overload exist on the currently pinned version, or does Phase A require a pin bump? Tactical spec runs `./gradlew :app_v2:dependencies | grep dropbox` and confirms the SDK version supports the `tokenAccessType` parameter.
2. Should `account_info.read` be requested as well? It allows showing the user's display name in the resource list; not strictly required for folder listing.
3. Is there a way to detect (server-side) whether the App Console scope is enabled before the user signs in, so the app can refuse to start the OAuth flow with a clearer message? Unlikely — but worth a brief tactical investigation.
4. Should existing already-working Dropbox accounts (from before the regression) be re-prompted? Front D drops only scope-deficient credentials — if a legacy token already has the scope, it stays. The check itself requires an API call; doing it lazily on first folder-list seems safer than eagerly at app start.

---

## 7. Risks

- **Medium** — changing the OAuth2 scope set forces every new sign-in through the consent screen. Users with cached credentials are unaffected (front D's migration check is conservative).
- **External dependency** — Dropbox App Console state is invisible to the codebase. If front B is skipped, front A alone may still fail because the requested scopes have not been pre-authorised on the app.
- **Token incompatibility** — switching `tokenAccessType` from default (`LEGACY`) to `OFFLINE` produces a different credential shape (long-lived access token vs short + refresh). The downstream `DropboxClient.getClient(accountId)` must handle both. Tactical spec confirms compatibility or adds a credential-type field to the stored record.

---

## 8. User Impact

Dropbox folder picker shows folders again; sign-in produces a usable account on the first attempt.

- **EN:** Dropbox sign-in once again grants the permissions needed to browse your folders.
- **RU:** Вход в Dropbox снова выдаёт права, необходимые для просмотра ваших папок.
- **UK:** Вхід у Dropbox знову надає права, потрібні для перегляду ваших тек.

Bug fix (regression of a previously-working flow). `docs/FEATURES.md` not updated; `dev/FUNCTIONALITY.log` gets a FIX entry naming the scope cause.

---

## 9. Related Specs

- **S0232** `Verified` (2026-05-18) — Unified `applicationId` for cloud-enabled flavors. Removed the per-flavor OAuth-registration barrier; S0235 work is no longer obstructed by mismatched package names.
- **S0233** `Draft` (parallel) — Google Drive Credential Manager fallback. Different provider, different failure mode.
- **S0234** `Draft` (parallel) — Google Account card error UI. Touches the same "no clear error when sign-in fails" gap on the Google side.
- No prior Dropbox-specific spec in catalog (last Dropbox-touching ticket: pre-S0200 silently-merged work in the cloud layer).

---

## 10. Implementation State (2026-05-18 review)

### Front A — Explicit scope + offline token in code: PRE-RESOLVED

`DropboxClient.kt` lines 122-130 now declare:

```kotlin
// S0235: request the folder-browsing scopes explicitly instead of relying on whatever
// the Dropbox App Console happens to grant by default for PKCE tokens.
private const val REQUIRED_METADATA_SCOPE = "files.metadata.read"
private val REQUIRED_PKCE_SCOPES = listOf(
    REQUIRED_METADATA_SCOPE,
    "files.content.read",
    "files.content.write",
    "account_info.read"
)
```

and `DropboxClient.kt` lines 228-237 pass them to the SDK:

```kotlin
fun startPkceAuthentication(activity: android.app.Activity, appKey: String) {
    Auth.startOAuth2PKCE(
        activity,
        appKey,
        dbxRequestConfig,
        DbxHost.DEFAULT,
        REQUIRED_PKCE_SCOPES,
        IncludeGrantedScopes.USER
    )
}
```

The SDK overload exists on the currently pinned `com.dropbox.core:dropbox-core-sdk:5.4.5` (resolves §6 Q1). The Front A code change landed in commit `ace216c4` (2026-05-17 05:17:52 +0200), 3 hours after this spec was originally drafted. The audit at the time of the device test that produced this spec saw the un-patched code; the spec was written from that observation and is now ahead of itself.

### Front B — App Console permission state: OWNER ACTION

Outside the codebase. Owner verifies on Dropbox App Console (App ID 6478499 → Permissions tab) that `files.metadata.read`, `files.content.read`, `files.content.write`, `account_info.read` are enabled and saved. If front A still fails on device after a fresh sign-in, this is the cause.

### Front C — UI scope-missing diagnostic: DEFERRED (post-test)

Not implemented yet. Scope-targeted error message in `DropboxFolderPickerViewModel` (or equivalent) is held back until the device test confirms whether front A alone fixes the user-visible regression. If yes — front C becomes pure polish (still useful, but not a release blocker). If no — front C ships together with front B's resolution.

### Front D — Token migration for cached scope-less tokens: DEFERRED (post-test)

Not implemented yet. Same reasoning as front C — the migration step is only meaningful for users carrying tokens issued **before** front A was in place. After a clean re-install or post-test consent flow, this is moot for the verification round.

### Device-test contract (BlockNeedUserTest exit gate)

Operator runs the standard or noLegal debug build, signs into Dropbox from scratch (clearing prior credentials if needed), and watches for:

1. Folder picker now shows the user's root folders without the `400 Bad Request` toast.
2. Logcat contains `S0235: startPkceAuthentication scopes=[files.metadata.read, files.content.read, files.content.write, account_info.read]` and `S0235: folder list ok account=…`.

Pass → `/spec-check` flips to `Verified`; fronts C+D become a follow-up bullet on this spec or move to a new ticket (operator's choice via `/spec-update`).
Fail with `Required scope 'files.metadata.read'` still in the log → Front B is the cause; owner action on the App Console, then re-test.
Fail with a different error → re-open `Broken` with the new failure mode captured in a fresh audit round.
