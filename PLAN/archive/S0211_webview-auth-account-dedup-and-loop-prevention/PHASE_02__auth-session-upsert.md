# Phase 02 — Auth Session Upsert

**Strategic spec:** [`../S0211_webview-auth-account-dedup-and-loop-prevention.md`](../S0211_webview-auth-account-dedup-and-loop-prevention.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 5 / 5
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Replace insert-only WebView-save with upsert-by-identity. When the harvested cookie set yields a known identity AND an existing account for `(host, identity)` is found, reuse that `accountId`; otherwise generate a new UUID. The decision lives in the data layer; UI just hands cookies to a single new repository method and uses the returned id in its result bundle.

---

## Prerequisites

- [ ] Phase 01 ✅ Done — `AccountIdentityExtractor.extract(...)` available.
- [ ] Working tree clean or on the active DEBUG branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/EncryptedCookieStore.kt` | Modified | ≤ 430 (current 376; backup if projected ≥ 500) |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/AuthSessionRepository.kt` | Modified | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/AuthSessionRepositoryImpl.kt` | Modified | ≤ 310 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthViewModel.kt` | Modified | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthDialogFragment.kt` | Modified | ≤ 340 |

> All five files are under 500 LOC after the planned edits. No backup needed.

---

## Steps

### Step 02.1 — Add identity lookup to `EncryptedCookieStore`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/EncryptedCookieStore.kt`
**Depends on:** —

**Prompt for developer:**

> Add a new public method to `EncryptedCookieStore`:
>
> ```kotlin
> fun findAccountIdByIdentity(host: String, identity: String): String?
> ```
>
> Behaviour: for the given host, iterate over all stored active accounts (use the same prefix scan logic as `listAccounts(host)`), load the cookies for each account via the existing private `loadCookiesInternal` (or expose it via a helper if needed without leaking implementation), call `AccountIdentityExtractor.extract(host, cookies)`, and return the first `accountId` whose extracted identity equals the requested `identity`. Return `null` if no account matches.
>
> Constraints: skip dismissed records (`TYPE_DISMISSED`). Skip records with zero live cookies. Blank `host` or blank `identity` → return `null`. No logging on the hot path. Method is synchronous and IO-cheap (operates on already-decrypted prefs in memory).

**Verification:**

- `Grep -n "fun findAccountIdByIdentity"` against `EncryptedCookieStore.kt` — exactly one match.
- `Grep -n "AccountIdentityExtractor" EncryptedCookieStore.kt` — at least one call-site match (intent: function calls extractor); import + KDoc references expected.
- Compile-check: `app_v2/build.gradle.kts` `assembleStandardDebug` succeeds (deferred to Phase Done Criteria build gate).

**Status:** `[x]` done

**Step Log:**

- 2026-05-15 — Verification 2/2 PASS. Files: EncryptedCookieStore.kt (+27 LOC, import added). Predicate "AccountIdentityExtractor" returned 3 matches (import L7 + KDoc L118 + call L130) — intent (call site present) satisfied. Dev log recorded.

---

### Step 02.2 — Extend `AuthSessionRepository` with `saveSessionFromWebView`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/AuthSessionRepository.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a new method to the `AuthSessionRepository` interface:
>
> ```kotlin
> /**
>  * S0211: dedup-aware save. Computes identity from [cookies] for known platforms;
>  * if an existing account is found for (host, identity) → reuses its accountId.
>  * Otherwise generates a new UUID. Returns the accountId that was used.
>  */
> suspend fun saveSessionFromWebView(
>     host: String,
>     displayName: String,
>     cookies: List<HttpCookie>,
>     userAgent: String? = null,
> ): String?
> ```
>
> Return value is the `accountId` used (new or reused). `null` is reserved for blank-input rejection (host blank, cookies empty).
>
> Do NOT touch the existing `saveSession(host, accountId, displayName, cookies, userAgent)` signature — it is still used elsewhere (settings screen renames, restore flows). The new method is additive.

**Verification:**

- `Grep -n "suspend fun saveSessionFromWebView" AuthSessionRepository.kt` — exactly one match.
- `Grep -n "S0211" AuthSessionRepository.kt` — at least one match (the KDoc tag).

**Status:** `[x]` done

**Step Log:**

- 2026-05-15 — Verification 2/2 PASS. Files: AuthSessionRepository.kt (+14 LOC). Dev log recorded.

---

### Step 02.3 — Implement `saveSessionFromWebView` in `AuthSessionRepositoryImpl`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/AuthSessionRepositoryImpl.kt`
**Depends on:** Steps 02.1, 02.2

**Prompt for developer:**

> Implement the new override in `AuthSessionRepositoryImpl`:
>
> 1. Early return `null` if `host.isBlank() || cookies.isEmpty()` (mirror existing `saveSession` guard; log via Timber.i like the existing one).
> 2. Inside `withContext(Dispatchers.IO)`:
>    - Call `AccountIdentityExtractor.extract(host, cookies)`. Store in `val identity: String?`.
>    - `val reusedId: String? = identity?.let { store.findAccountIdByIdentity(host, it) }`.
>    - `val accountId = reusedId ?: java.util.UUID.randomUUID().toString()`.
>    - Call `store.saveForAccount(host, accountId, displayName, cookies, userAgent)`.
>    - Call `refreshFlows()`.
>    - Log via Timber.i:
>      ```kotlin
>      Timber.i(
>          "S0211: webview save host=%s reused=%s identity=%s accountId=%s",
>          host, reusedId != null, identity ?: "<none>", accountId,
>      )
>      ```
> 3. Return the `accountId`.
>
> Insert the `Timber.d("S0211: AuthSessionRepositoryImpl.saveSessionFromWebView host=<host> identity=<masked>")` debug verification tag at the entry of the function body — per CLAUDE.md "Debug Verification Tags". The tag is owned by the spec status `BlockNeedUserTest` and will be removed by `/spec-check` on Verified flip. Mask `identity` to `head4***` form to keep the log non-sensitive.

**Verification:**

- `Grep -n "override suspend fun saveSessionFromWebView" AuthSessionRepositoryImpl.kt` — exactly one match.
- `Grep -n "findAccountIdByIdentity" AuthSessionRepositoryImpl.kt` — exactly one match.
- `Grep -n "Timber.d\(\"S0211:" AuthSessionRepositoryImpl.kt` — exactly one match (debug tag entry).
- `Grep -n "Timber.i\(\s*\"S0211: webview save" AuthSessionRepositoryImpl.kt` — exactly one match.

**Status:** `[x]` done

**Step Log:**

- 2026-05-15 — Verification 4/4 PASS. Files: AuthSessionRepositoryImpl.kt (+24 LOC, import added). Dev log recorded.

---

### Step 02.4 — Add `saveSessionFromWebView` to `WebViewAuthViewModel`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthViewModel.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add a non-suspending entrypoint to `WebViewAuthViewModel`:
>
> ```kotlin
> /**
>  * S0211: bridges Fragment harvest to the dedup-aware repository call.
>  * Emits the accountId used (new or reused) via [onSaved] on the ViewModel scope.
>  */
> fun saveSessionFromWebView(
>     host: String,
>     displayName: String,
>     cookies: List<HttpCookie>,
>     userAgent: String? = null,
>     onSaved: (String?) -> Unit,
> ) {
>     if (host.isBlank() || cookies.isEmpty()) {
>         onSaved(null)
>         return
>     }
>     viewModelScope.launch {
>         val id = repository.saveSessionFromWebView(host, displayName, cookies, userAgent)
>         onSaved(id)
>     }
> }
> ```
>
> Keep the existing `saveSession(host, accountId, ..)` and the deprecated `saveSession(domain, cookies)` overloads untouched.

**Verification:**

- `Grep -n "fun saveSessionFromWebView" WebViewAuthViewModel.kt` — exactly one match.
- `Grep -n "onSaved" WebViewAuthViewModel.kt` — at least one match.

**Status:** `[x]` done

**Step Log:**

- 2026-05-15 — Verification 2/2 PASS. Files: WebViewAuthViewModel.kt (+19 LOC). Dev log recorded.

---

### Step 02.5 — Wire `WebViewAuthDialogFragment.harvestAndDismiss` to the new ViewModel call

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthDialogFragment.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> Inside `harvestAndDismiss()` (currently around line 171–226), replace the manual `UUID.randomUUID()` generation and the `viewModel.saveSession(targetHost, accountId, displayName, cookies, capturedUa)` call with the new `viewModel.saveSessionFromWebView(...)` call:
>
> 1. Remove the line `val accountId = UUID.randomUUID().toString()`.
> 2. Replace the `viewModel.saveSession(...)` call with:
>    ```kotlin
>    viewModel.saveSessionFromWebView(
>        host = targetHost,
>        displayName = displayName,
>        cookies = cookies,
>        userAgent = capturedUa,
>    ) { savedAccountId ->
>        // savedAccountId may be a freshly minted UUID or a reused existing id.
>        scrubWebViewState()
>        Timber.i(
>            "[S0166] browser login saved: account=%s host=%s",
>            displayName,
>            targetHost,
>        )
>        Timber.d(
>            "S0155: WebView auth saved host=%s accountId=%s ua=%s",
>            targetHost, savedAccountId ?: "(null)", capturedUa?.take(60) ?: "(none)"
>        )
>        emitResultAndDismiss(saved = savedAccountId != null, accountId = savedAccountId)
>    }
>    ```
> 3. Drop the now-unused `import java.util.UUID` if no other reference remains in this file.
>
> The result bundle continues to carry `RESULT_ACCOUNT_ID` so the existing `ReceiveShareActivity` reader path is unaffected. `emitResultAndDismiss(saved=false, accountId=null)` continues to indicate a failed save (blank host or empty cookies).

**Verification:**

- `Grep -n "UUID.randomUUID" WebViewAuthDialogFragment.kt` — zero matches.
- `Grep -n "saveSessionFromWebView" WebViewAuthDialogFragment.kt` — exactly one match.
- `Grep -n "viewModel.saveSession\b" WebViewAuthDialogFragment.kt` — zero matches (no leftover call to the old overload).

**Status:** `[x]` done

**Step Log:**

- 2026-05-15 — Verification 3/3 PASS. Files: WebViewAuthDialogFragment.kt (~+8 LOC net; removed UUID generation & import, replaced with callback-based call). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build` for `standardDebug` (catches Kotlin signature errors across all five files in one shot). Record `expected: BUILD SUCCESSFUL | actual: <result>`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every modified file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 03 uses the same `AccountIdentityExtractor` and `EncryptedCookieStore.loadForAccount` to compute identity per stored account during one-shot cleanup. The new `findAccountIdByIdentity` is also fine for cleanup but expensive (full scan + cookie load per account); cleanup builds its own grouped pass instead.

---

## Rollback Plan

Revert phase commits — no schema change, no migration. UI continues to function with the old `saveSession` overload (it's still there).
