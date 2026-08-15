# Phase 03 — Cookie Bridge

**Strategic spec:** [`../S0174_nolegal-ytdlp-universal-extractor.md`](../S0174_nolegal-ytdlp-universal-extractor.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-12
**Completed:** 2026-05-12

---

## Objective

Implement `CookieFileWriter` — a noLegal-specific class that serialises cookies from `EncryptedCookieStore` to a Netscape-format temp file in `context.filesDir`, using eTLD+1 matching, then deletes the file atomically after use. This class is used by `YtDlpExtractionStrategy` in Phase 04.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] `EncryptedCookieStore` API is readable (`loadForAccount`, `listAllAccounts`).
- [ ] `LinkDownloadCookieJar.registrableDomain()` logic is understood (eTLD+1 naive split).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/CookieFileWriter.kt` | New | ≤ 150 |

---

## Steps

### Step 03.1 — Create CookieFileWriter with eTLD+1 matching

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/CookieFileWriter.kt` (New)
**Depends on:** — start of phase

**Prompt for developer:**

> Create `CookieFileWriter` as a `@Singleton` Hilt-injectable class in the `noLegal` sourceSet. Constructor injects `@ApplicationContext context: Context` and `store: EncryptedCookieStore`.
>
> Implement one public method:
>
> ```kotlin
> /**
>  * Writes cookies matching [targetHost] (by eTLD+1) from [EncryptedCookieStore]
>  * to a Netscape-format temp file in [Context.filesDir]. Returns the temp file path,
>  * or null if no cookies are found.
>  *
>  * The caller is responsible for deleting the file via [deleteCookieFile] after use.
>  * Use try/finally to guarantee deletion even on exception.
>  */
> fun writeCookieFile(targetHost: String): File?
> ```
>
> And a companion helper:
>
> ```kotlin
> fun deleteCookieFile(file: File) {
>     runCatching { file.delete() }
>     // deleteOnExit registered at write time — belt-and-suspenders if delete() is not called
> }
> ```
>
> Implementation requirements:
> - Compute `registrableDomain(targetHost)` using the same naive eTLD+1 split as `LinkDownloadCookieJar`: `parts.size >= 2 → "${parts[size-2]}.${parts.last()}"`.
> - Call `store.listAllAccounts()` — returns `List<Pair<String, AccountEntry>>` where first is the stored host.
> - For each pair, if `registrableDomain(storedHost) == registrableDomain(targetHost)` → load cookies via `store.loadForAccount(storedHost, entry.accountId)`.
> - Merge all matching cookies; deduplicate by `cookie.name` (keep first occurrence per name).
> - If merged list is empty → return `null` (no file written).
> - Write to `File(context.filesDir, "ytdlp_cookies_${System.currentTimeMillis()}.txt")`.
> - Call `file.deleteOnExit()` immediately after creating the file object.
> - Netscape format header: `# Netscape HTTP Cookie File\n# https://curl.se/docs/http-cookies.html\n`.
> - Each cookie line: `<domain>\t<flagIncludeSubdomain>\t<path>\t<secure>\t<expiry>\t<name>\t<value>\n`
>   - `<domain>`: `cookie.domain?.trimStart('.') ?: targetHost`
>   - `<flagIncludeSubdomain>`: `TRUE` if domain starts with `.`, else `FALSE`
>   - `<path>`: `cookie.path ?: "/"`
>   - `<secure>`: `TRUE` if `cookie.secure`, else `FALSE`
>   - `<expiry>`: `cookie.maxAge` in seconds since epoch as a long integer; use `0` if `maxAge < 0` (session cookie — yt-dlp accepts 0 as session)
>   - `<name>`: `cookie.name`
>   - `<value>`: `cookie.value ?: ""`
> - Log the write via `Timber.d("CookieFileWriter: wrote %d cookies for host=%s", count, targetHost)`.
> - Do NOT log cookie values.
> - No `Log.d()` calls.

**Verification:**

- `Glob` — `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/CookieFileWriter.kt` exists.
- `Grep` — `class CookieFileWriter` matches exactly once.
- `Grep` — `@Singleton` present.
- `Grep` — `@Inject constructor` present.
- `Grep` — `fun writeCookieFile(targetHost: String): File?` present.
- `Grep` — `fun deleteCookieFile(file: File)` present.
- `Grep` — `deleteOnExit()` called.
- `Grep` — `Log\.d\(` returns zero hits in this file.
- `Grep` — `# Netscape HTTP Cookie File` present as a string literal.

**Status:** `[x]` done

**Step Log:**

- 2026-05-12 — Verification 9/9 PASS. Files: CookieFileWriter.kt (New, ~95 LOC). Dev log recorded.

---

### Step 03.2 — Add @Inject constructor to CookieFileWriter (Hilt wiring)

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/CookieFileWriter.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> `CookieFileWriter` must be injectable via Hilt without an explicit `@Provides` — the `@Singleton` + `@Inject constructor` annotations are sufficient for Hilt to generate the binding in the `SingletonComponent`. Verify that both annotations are present and the constructor takes only Hilt-injectable types: `@ApplicationContext context: Context` and `EncryptedCookieStore`.
>
> Do not add an explicit binding in any `@Module` — the constructor injection is self-contained.

**Verification:**

- `Grep` — `@ApplicationContext` annotation present in the constructor parameter.
- `Grep` — `EncryptedCookieStore` appears as a constructor parameter type.
- No `@Provides fun provideCookieFileWriter` in any `@Module` file (constructor injection only).

**Status:** `[x]` done

**Step Log:**

- 2026-05-12 — Verification 3/3 PASS. @ApplicationContext + EncryptedCookieStore in constructor; no explicit @Provides.

---

### Step 03.3 — Write unit test for CookieFileWriter Netscape serialisation

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/link/nolegal/CookieFileWriterTest.kt` (New)
**Depends on:** Step 03.1

**Prompt for developer:**

> Create a JUnit 4 unit test class `CookieFileWriterTest`. Use `mockk` for mocking (already in the project's test dependencies). Test cases:
>
> 1. `writeCookieFile_noMatchingCookies_returnsNull`: stub `store.listAllAccounts()` to return a host with a different eTLD+1 → assert return is `null`.
> 2. `writeCookieFile_matchingCookies_writesNetscapeFile`: stub one `HttpCookie` with name, value, domain, path, secure=true, maxAge > 0 → call `writeCookieFile("www.instagram.com")` → assert file exists, contains `# Netscape HTTP Cookie File`, contains the cookie name and value on a tab-separated line.
> 3. `deleteCookieFile_deletesFile`: create a real temp file → call `deleteCookieFile(file)` → assert `file.exists() == false`.
>
> Use a real `Context` via `ApplicationProvider.getApplicationContext()` for the `context.filesDir` path (Robolectric or mock context is acceptable if Robolectric is already set up in the module).

**Verification:**

- `Glob` — `app_v2/src/test/java/com/sza/fastmediasorter/data/link/nolegal/CookieFileWriterTest.kt` exists.
- `Grep` — `class CookieFileWriterTest` present.
- `Grep` — `writeCookieFile_noMatchingCookies_returnsNull` test method present.
- `Grep` — `writeCookieFile_matchingCookies_writesNetscapeFile` test method present.
- `Grep` — `deleteCookieFile_deletesFile` test method present.

**Status:** `[x]` done

**Step Log:**

- 2026-05-12 — Verification 5/5 PASS. Files: CookieFileWriterTest.kt (New, ~109 LOC). Dev log recorded.

---

### Step 03.4 — Verify no cookie values appear in logs

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/CookieFileWriter.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Audit `CookieFileWriter.kt` to ensure no `Timber.d` or `Timber.i` call includes `cookie.value`, `cookie.name`, or any interpolated string that might expose credential data. The only log line should record count and host — no cookie content.

**Verification:**

- `Grep` — `cookie.value` does NOT appear in any `Timber.*` call in `CookieFileWriter.kt`.
- `Grep` — `cookie.name` does NOT appear in any `Timber.*` call in `CookieFileWriter.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-12 — Verification 2/2 PASS. No cookie.value / cookie.name in any Timber call.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles (`noLegalDebug` build passes) — BUILD SUCCESSFUL (2026-05-12).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated: `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 03 establishes: `CookieFileWriter` injects `EncryptedCookieStore`, matches cookies by eTLD+1, serialises to Netscape format, manages temp file lifecycle. Phase 04 injects `CookieFileWriter` into `YtDlpExtractionStrategy`.

---

## Rollback Plan

Delete `CookieFileWriter.kt` and `CookieFileWriterTest.kt`. No data migration or user-facing surface changed.
