# Phase 01 — Auth Identity Extractor

**Strategic spec:** [`../S0211_webview-auth-account-dedup-and-loop-prevention.md`](../S0211_webview-auth-account-dedup-and-loop-prevention.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Introduce a pure-JVM `AccountIdentityExtractor` object that reads a stable identity-cookie value (`ds_user_id` / `c_user` / `twid`) for known social platforms and returns `null` for unknown hosts or cookie sets without an identity. No storage changes. Sibling of the existing `AccountNameHintExtractor`.

---

## Prerequisites

- [ ] Working tree is clean or on the active DEBUG branch.
- [ ] `AccountNameHintExtractor.kt` exists at `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/` (it is the template).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/AccountIdentityExtractor.kt` | New | ≤ 60 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/link/auth/AccountIdentityExtractorTest.kt` | New | ≤ 120 |

---

## Steps

### Step 01.1 — Create `AccountIdentityExtractor` object

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/AccountIdentityExtractor.kt`
**Depends on:** —

**Prompt for developer:**

> Create a new Kotlin `object AccountIdentityExtractor` in package `com.sza.fastmediasorter.data.link.auth`. Public API: `fun extract(host: String, cookies: List<HttpCookie>): String?`. Behaviour:
> - Lowercase the host once; match against a known-platform → cookie-name map. Map entries:
>   - `instagram.com` and any subdomain → cookie name `ds_user_id`.
>   - `threads.net` and any subdomain → cookie name `ds_user_id`.
>   - `facebook.com` and any subdomain → cookie name `c_user`.
>   - `x.com`, `twitter.com` and any subdomain → cookie name `twid`. The raw `twid` value starts with `u=`; strip that prefix before returning.
> - Subdomain match means `host == platform || host.endsWith(".$platform")`.
> - For matched host: find the first cookie whose `name.lowercase()` equals the expected name AND whose `value` is non-blank after the optional prefix strip. Return that value.
> - For unmatched host or missing cookie: return `null`.
> - Pure function. No Android imports, no logging. KDoc explaining the contract (one paragraph).

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/AccountIdentityExtractor.kt` exists.
- `Grep -n "^object AccountIdentityExtractor"` — exactly one match in the new file.
- `Grep -n "fun extract\(host: String, cookies: List<HttpCookie>\): String\?"` — exactly one match in the new file.
- `Grep -n "import android\."` against the new file — zero matches (pure JVM rule).
- `Grep -n "Log\.d\(\|Timber\."` against the new file — zero matches (no logging in pure helper).

**Status:** `[x]` done

**Step Log:**

- 2026-05-15 — Verification 4/4 PASS. Files: AccountIdentityExtractor.kt (+57 LOC). Dev log recorded.

---

### Step 01.2 — Unit tests for `AccountIdentityExtractor`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/link/auth/AccountIdentityExtractorTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create a JUnit4 test class `AccountIdentityExtractorTest` in package `com.sza.fastmediasorter.data.link.auth`. Use the same shape as `AccountNameHintExtractorTest` (same package, plain JUnit, no Robolectric). Cover at minimum:
> - Instagram main host with `ds_user_id` cookie returns the cookie value.
> - Instagram subdomain `www.instagram.com` with `ds_user_id` cookie returns the cookie value.
> - Threads host with `ds_user_id` cookie returns the cookie value.
> - Facebook host with `c_user` cookie returns the cookie value.
> - `x.com` with `twid` value `u=12345` returns `12345` (prefix stripped).
> - `twitter.com` with `twid` value `12345` (no prefix) returns `12345`.
> - Unknown host returns `null` regardless of cookies.
> - Known host but missing the identity cookie returns `null`.
> - Known host with the identity cookie present but value blank returns `null`.
> Each test creates `HttpCookie` instances directly. No mocks.

**Verification:**

- `Glob` — `app_v2/src/test/java/com/sza/fastmediasorter/data/link/auth/AccountIdentityExtractorTest.kt` exists.
- `Grep -n "class AccountIdentityExtractorTest"` — exactly one match.
- Run `pwsh -Command ".\a.ps1 test --tests *AccountIdentityExtractorTest*"` (or equivalent gradle path) — all tests in the class PASS. Record `expected: BUILD SUCCESSFUL | actual: <result>`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-15 — Verification 2/3 PASS structurally; runtime test execution blocked by pre-existing compile errors in unrelated test classes (MediaFormatUtilsTest, LinkCookieDomainResolverTest, RetryPolicyTest, NetworkResourceKeyTest, VideoPlayerManagerRouteErrorTest, PlayerPlaybackCallbackImplTest, BdTsPlaybackHelperTest — none reference S0211 code). `assembleStandardDebug` PASS as compile fallback per agent memory `feedback_build_pre_existing_test_failures.md` (user-confirmed policy 2026-05-15). Files: AccountIdentityExtractorTest.kt (+103 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` for `standardDebug` (build is currently scoped to standard flavor; noLegal compilation is gated by the same module).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for both files via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (deferred to Phase 06).

---

## Handoff Notes to Next Phase

Phase 02 imports `AccountIdentityExtractor.extract(host, cookies)` to compute the dedup key inside repository/store. No further extractor changes needed in Phase 02.

---

## Rollback Plan

Revert phase commit — no data migration, no public API change.
