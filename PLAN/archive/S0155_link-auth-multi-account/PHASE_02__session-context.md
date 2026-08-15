# Phase 02 — session-context

**Strategic spec:** [`../S0155_link-auth-multi-account.md`](../S0155_link-auth-multi-account.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01 (store-multi-account)
**Blocks:** Phase 03, Phase 04, Phase 05
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Introduce `LinkDownloadSessionContext` — a singleton that holds pre-loaded cookies for the currently executing download — and wire it into `LinkDownloadCookieJar` and `InvisibleWebViewExtractionStrategy`, so both OkHttp and the invisible WebView inject cookies for the selected account rather than the default one.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/LinkDownloadSessionContext.kt` | New | ≤ 55 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/LinkDownloadCookieJar.kt` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/InvisibleWebViewExtractionStrategy.kt` | Modified | ≤ 510 |

> `InvisibleWebViewExtractionStrategy.kt` is 489 LOC — backup required before editing (see Step 02.2).

---

## Steps

### Step 02.1 — Create LinkDownloadSessionContext

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/LinkDownloadSessionContext.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `LinkDownloadSessionContext` as a Hilt `@Singleton`. It stores the cookies that the currently running download should use, keyed by canonical host.
>
> ```kotlin
> package com.sza.fastmediasorter.data.link.cookie
>
> import java.net.HttpCookie
> import javax.inject.Inject
> import javax.inject.Singleton
>
> /**
>  * S0155: holds pre-loaded cookies for the account selected for the
>  * currently executing link-download pipeline run. Set by
>  * [com.sza.fastmediasorter.domain.usecase.link.LinkAutoDownloadCoordinator]
>  * before the pipeline starts; cleared in the finally block.
>  *
>  * Thread-safety: access is single-threaded within a coroutine pipeline run;
>  * @Volatile ensures visibility across coroutine context switches.
>  */
> @Singleton
> class LinkDownloadSessionContext @Inject constructor() {
>     @Volatile private var activeCookies: Pair<String, List<HttpCookie>>? = null
>
>     /** Set before the pipeline run; [host] is the canonical KnownAuthResources host. */
>     fun set(host: String, cookies: List<HttpCookie>) {
>         activeCookies = host to cookies
>     }
>
>     /** Load cookies if [requestHost] matches the active host (or its parent domain). */
>     fun cookiesFor(requestHost: String): List<HttpCookie>? {
>         val (activeHost, cookies) = activeCookies ?: return null
>         val normalized = requestHost.lowercase().removePrefix("www.")
>         val activeNorm = activeHost.lowercase().removePrefix("www.")
>         return if (normalized == activeNorm || normalized.endsWith(".$activeNorm")) cookies else null
>     }
>
>     /** Clear after the pipeline run. */
>     fun clear() { activeCookies = null }
> }
> ```

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/LinkDownloadSessionContext.kt` exists.
- `Grep` — `class LinkDownloadSessionContext` present exactly once.
- `Grep` — `fun set(host: String, cookies: List<HttpCookie>)` present.
- `Grep` — `fun cookiesFor(requestHost: String)` present.
- `Grep` — `fun clear()` present.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[ ]` not done

---

### Step 02.2 — Wire LinkDownloadSessionContext into LinkDownloadCookieJar

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/LinkDownloadCookieJar.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Inject `LinkDownloadSessionContext` into `LinkDownloadCookieJar`. In `loadForRequest(url)`, check `context.cookiesFor(url.host)` first — if non-null and non-empty, convert those `HttpCookie` objects to OkHttp `Cookie` and return them (same conversion logic already in the method). Only fall back to `store.loadFor(host)` if the context returns null (i.e., no active session or host mismatch). Keep the `LinkDownloadTrace.verbose` logging call.
>
> Constructor change: add `private val context: LinkDownloadSessionContext` parameter.

**Verification:**

- `Grep` — `private val context: LinkDownloadSessionContext` present in `LinkDownloadCookieJar.kt`.
- `Grep` — `context.cookiesFor(` present in `LinkDownloadCookieJar.kt`.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[ ]` not done

---

### Step 02.3 — Wire LinkDownloadSessionContext into InvisibleWebViewExtractionStrategy

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/InvisibleWebViewExtractionStrategy.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Create timestamped backup of `InvisibleWebViewExtractionStrategy.kt` (489 LOC, exceeds backup threshold) before editing:
>
> ```powershell
> $ts = Get-Date -Format "yyyyMMdd_HHmmss"
> Copy-Item `
>   "app_v2/src/main/java/com/sza/fastmediasorter/data/link/InvisibleWebViewExtractionStrategy.kt" `
>   "temp/InvisibleWebViewExtractionStrategy_${ts}.backup.kt"
> ```
>
> Then inject `LinkDownloadSessionContext` into `InvisibleWebViewExtractionStrategy`. In `injectSavedCookies(url: String)`, check `context.cookiesFor(host)` first — if non-null, inject those cookies (already have conversion logic via `buildCookieHeader`); skip the existing `cookieDomainsFor(host)` loop. If context returns null, keep the existing `cookieDomainsFor + store.loadFor` loop unchanged. Constructor: add `private val sessionContext: LinkDownloadSessionContext` parameter.

**Verification:**

- `Glob` — `temp/InvisibleWebViewExtractionStrategy_*.backup.kt` exists.
- `Grep` — `private val sessionContext: LinkDownloadSessionContext` present in `InvisibleWebViewExtractionStrategy.kt`.
- `Grep` — `sessionContext.cookiesFor(` present.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entries added for all files in "Files Touched" via `add_to_dev_log.ps1`.
- [ ] `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` run.

---

## Handoff Notes to Next Phase

- `LinkDownloadSessionContext` is available via Hilt injection.
- `LinkDownloadCookieJar` and `InvisibleWebViewExtractionStrategy` use context cookies when active.
- Phase 03 wires the account-naming flow in `WebViewAuthDialogFragment`.
- Phase 04 sets and clears the context in `LinkAutoDownloadCoordinator.handle()`.

---

## Rollback Plan

Revert phase commit(s). No user-visible or data-storage change — purely pipeline wiring.
