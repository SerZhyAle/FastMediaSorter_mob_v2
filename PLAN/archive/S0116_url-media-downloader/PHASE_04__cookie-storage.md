# Phase 04 — Cookie Storage and Injection (Pillar K)

**Strategic spec:** [`../S0116_url-media-downloader.md`](../S0116_url-media-downloader.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05, 06, 07
**Steps done:** 6 / 6
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Persist domain cookies in `EncryptedSharedPreferences`. Inject them into the OkHttp client used by `DirectFileExtractionStrategy` / `HtmlPageExtractionStrategy` and into the Media3 `DefaultHttpDataSource.Factory` used by `Media3SegmentDownloader`. Provide repository surface for listing and deleting saved sessions.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`LinkDownloadTrace` available; sealed types in place).
- [ ] `androidx.security:security-crypto:1.1.0-alpha06` already on classpath (verified).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/EncryptedCookieStore.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/LinkDownloadCookieJar.kt` | New | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/AuthSessionRepository.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/AuthSessionRepositoryImpl.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/LinkDownloadModule.kt` | Modified | ≤ 250 |
| `app_v2/src/streamingEnabled/java/com/sza/fastmediasorter/data/link/streaming/Media3SegmentDownloader.kt` | Modified | ≤ 320 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/link/cookie/EncryptedCookieStoreTest.kt` | New | ≤ 180 |

---

## Steps

### Step 04.1 — Implement `EncryptedCookieStore`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/EncryptedCookieStore.kt` (New)
**Depends on:** — start of phase

**Prompt for developer:**

> `@Singleton class EncryptedCookieStore @Inject constructor(@ApplicationContext private val context: Context)`. Use the project's existing `EncryptedSharedPreferences` pattern (see `data/cloud/DropboxClient.kt:81-87` and `data/cloud/helpers/GoogleDriveCredentialsManager.kt:33-39` for reference):
>
> ```kotlin
> private val prefs by lazy {
>     val masterKey = MasterKey.Builder(context)
>         .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
>         .build()
>     EncryptedSharedPreferences.create(
>         context,
>         "link_download_cookies",
>         masterKey,
>         EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
>         EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
>     )
> }
> ```
>
> Storage filename `link_download_cookies` (distinct from existing app prefs). API: `fun loadFor(domain: String): List<HttpCookie>`, `fun saveFor(domain: String, cookies: List<HttpCookie>)`, `fun listDomains(): List<String>`, `fun deleteFor(domain: String)`, `fun savedAt(domain: String): java.time.Instant?`.
>
> Serialize each domain as a **single JSON object string**, not `HttpCookie.toString()`. Suggested payload shape:
>
> ```json
> {
>   "savedAtEpochMillis": 1715155151000,
>   "cookies": [
>     {
>       "name": "session",
>       "value": "abc",
>       "domain": "example.com",
>       "path": "/",
>       "expiresAtEpochMillis": 1715255151000,
>       "secure": true,
>       "httpOnly": true
>     }
>   ]
> }
> ```
>
> Preserve `domain`, `path`, `expiresAtEpochMillis`, `secure`, and `httpOnly` across round-trip. Session cookies may store `expiresAtEpochMillis = null`. On load, drop cookies whose expiry is already in the past. Log via `LinkDownloadTrace.verbose` — domain + cookie count + names only, never `value`.

**Verification:**

- `Glob` — `EncryptedCookieStore.kt` exists.
- `Grep` — `MasterKey\.Builder\(context\)` matches once.
- `Grep` — `MasterKey\.KeyScheme\.AES256_GCM` matches once.
- `Grep` — `EncryptedSharedPreferences\.create\(` matches once.
- `Grep` — `link_download_cookies` matches once.
- `Grep` — `fun listDomains\(\): List<String>` matches once.
- `Grep` — `fun deleteFor\(domain: String\)` matches once.
- `Grep` — `fun savedAt\(domain: String\): Instant\?` matches once (`java.time.Instant` imported, idiomatic Kotlin shape — FQN not required at call site).
- `Grep` — `expiresAtEpochMillis` matches at least once in `EncryptedCookieStore.kt`.
- `Grep` — `httpOnly` matches at least once in `EncryptedCookieStore.kt`.
- `Grep` — `HttpCookie\.toString\(` returns 0 hits in `EncryptedCookieStore.kt`.
- `Grep` — `Log\.d\(` returns 0 hits.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 12/12 PASS. Files: EncryptedCookieStore.kt (NEW 132 LOC) — JSON-payload domain entries with savedAtEpochMillis + cookies array; expired cookies dropped on load; values never logged. Predicate corrected during execution: `Instant?` is the idiomatic Kotlin shape with the import (FQN was a spec slip). Dev log recorded.

---

### Step 04.2 — Implement `LinkDownloadCookieJar`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/LinkDownloadCookieJar.kt` (New)
**Depends on:** Step 04.1

**Prompt for developer:**

> `@Singleton class LinkDownloadCookieJar @Inject constructor(private val store: EncryptedCookieStore) : okhttp3.CookieJar`. `loadForRequest(url: HttpUrl): List<okhttp3.Cookie>` reads from store using `url.host` and converts `HttpCookie` → `okhttp3.Cookie.Builder` (preserve domain, path, expiry, secure, httpOnly). `saveFromResponse(url: HttpUrl, cookies: List<okhttp3.Cookie>)` is a no-op — cookies are persisted only via WebView extraction in Phase 05, never absorbed from arbitrary HTTP responses.

**Verification:**

- `Glob` — `LinkDownloadCookieJar.kt` exists.
- `Grep` — `: CookieJar` matches once (`okhttp3.CookieJar` imported).
- `Grep` — `override fun loadForRequest` matches once.
- `Grep` — `override fun saveFromResponse` matches once (must be no-op body).

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 4/4 PASS. Files: LinkDownloadCookieJar.kt (NEW 53 LOC) — translates `HttpCookie` → `okhttp3.Cookie`; `saveFromResponse` is no-op per Phase 05 single-source-of-cookies rule. Predicate corrected (FQN → import-shorthand). Dev log recorded.

---

### Step 04.3 — Define and implement `AuthSessionRepository`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/AuthSessionRepository.kt` (New), `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/AuthSessionRepositoryImpl.kt` (New)
**Depends on:** Step 04.2

**Prompt for developer:**

> Domain interface: `interface AuthSessionRepository { fun observeDomains(): Flow<List<AuthSessionDomain>>; suspend fun saveSession(domain: String, cookies: List<HttpCookie>); suspend fun deleteSession(domain: String); suspend fun hasSession(domain: String): Boolean }` plus `data class AuthSessionDomain(val host: String, val cookieCount: Int, val savedAt: Instant)`. Impl wraps `EncryptedCookieStore` and emits via a `MutableStateFlow<List<AuthSessionDomain>>` updated on `saveSession`/`deleteSession`. Initial value computed from `store.listDomains()`.

**Verification:**

- `Glob` — both files exist.
- `Grep` — `interface AuthSessionRepository` matches once.
- `Grep` — `class AuthSessionRepositoryImpl` matches once.
- `Grep` — `data class AuthSessionDomain` matches once.
- `Grep` — `MutableStateFlow<List<AuthSessionDomain>>` matches once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 5/5 PASS. Files: AuthSessionRepository.kt (NEW 28 LOC, interface + AuthSessionDomain), AuthSessionRepositoryImpl.kt (NEW 56 LOC, MutableStateFlow snapshot pattern). Dev log recorded.

---

### Step 04.4 — Wire `LinkDownloadCookieJar` into `@Named("linkDownload")` OkHttpClient

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/di/LinkDownloadModule.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Find the existing `@Provides @Named("linkDownload") fun provideLinkDownloadClient(...)`. Inject `LinkDownloadCookieJar` and call `.cookieJar(jar)` on the `OkHttpClient.Builder`. Also add `@Provides fun bindAuthSessionRepository(impl: AuthSessionRepositoryImpl): AuthSessionRepository = impl`. Confirm no existing test depends on the cookieJar being null (search test sources).

**Verification:**

- `Grep` — `\.cookieJar\(` matches once in `LinkDownloadModule.kt`.
- `Grep` — `LinkDownloadCookieJar` matches at least 2 times in `LinkDownloadModule.kt`.
- `Grep` — `bindAuthSessionRepository` matches once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 3/3 PASS. Files: LinkDownloadModule.kt (+8 LOC: cookieJar wired into provideOkHttpClient + AuthSessionRepository @Binds in LinkDownloadStrategiesModule). Dev log recorded.

---

### Step 04.5 — Inject cookies into Media3 `DefaultHttpDataSource.Factory`

**Files:** `app_v2/src/streamingEnabled/java/com/sza/fastmediasorter/data/link/streaming/Media3SegmentDownloader.kt`
**Depends on:** Step 04.4

**Prompt for developer:**

> Inject `EncryptedCookieStore` into `Media3SegmentDownloader`. Before constructing `HlsDownloader` / `DashDownloader`, build a cookie header: `val host = Uri.parse(manifestUrl).host ?: ""; val cookieHeader = store.loadFor(host).joinToString("; ") { "${it.name}=${it.value}" }`. If `cookieHeader.isNotEmpty()`, call `factory.setDefaultRequestProperties(mapOf("Cookie" to cookieHeader))`. Log `S0116: cookie-jar inject domain=$host, cookies=${count} for streaming` (no values).

**Verification:**

- `Grep` — `setDefaultRequestProperties` matches at least once in `Media3SegmentDownloader.kt`.
- `Grep` — `cookie-jar inject domain=` matches at least once (the `S0116:` prefix is added at runtime by `LinkDownloadTrace.tag`).
- `Grep` — `EncryptedCookieStore` matches in constructor parameter list of this file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 3/3 PASS. Files: Media3SegmentDownloader.kt (+13 LOC: cookie injection into DefaultHttpDataSource via setDefaultRequestProperties + S0116 cookie-jar inject debug tag). Dev log recorded.

---

### Step 04.6 — Add `EncryptedCookieStoreTest`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/link/cookie/EncryptedCookieStoreTest.kt` (New)
**Depends on:** Step 04.5

**Prompt for developer:**

> Robolectric (uses `Context` + EncryptedSharedPreferences). Cases: (1) round-trip preserves `name`, `value`, `domain`, `path`, future expiry, `secure`, and `httpOnly`; (2) save expired cookie (`expiresAtEpochMillis` in the past) → load returns empty; (3) `listDomains()` returns saved domains in deterministic order; (4) `deleteFor` removes only the specified domain. No assertion on encrypted-at-rest (covered by AndroidX library); just contract behaviour.

**Verification:**

- `Glob` — `EncryptedCookieStoreTest.kt` exists.
- `Grep` — `@RunWith\(RobolectricTestRunner::class\)` matches once.
- `Grep` — `@Test` matches at least 4 times.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 3/3 PASS. Files: EncryptedCookieStoreTest.kt (NEW 96 LOC, 4 @Test cases). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] All flavors compile (`standard`, `lite`, `photos`, `legacy`, `vr`, `vrUnlicensed` debug variants).
- [ ] `EncryptedCookieStoreTest` passes.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

- `AuthSessionRepository` is the single domain entry point for Phase 05 (settings list UI, delete buttons) and Phase 05 WebView flow (calls `saveSession` on successful login).
- Cookie injection is live for OkHttp and Media3. Phase 05 only needs to harvest cookies from WebView and call `repository.saveSession(...)`.
- HTTP requests now carry cookies if present; this is observed in `LOG_LINK_DOWNLOAD` verbose traces. No tests for "request includes cookie" yet — covered indirectly when Phase 05 instrumentation runs end-to-end.

---

## Rollback Plan

Revert phase commit. Cookie storage becomes empty / unused; OkHttpClient cookieJar reverts to default no-op. Existing S0003 flows continue to work without cookies.

## Revision History

- **2026-05-08** - by `/spec-update` (`GPT-5.4`, focus: consistency, completeness, verifiability)
	- Applied: replaced lossy cookie serialization with attribute-preserving storage contract and stronger verification. Proposed (DISCUSS): 0.
