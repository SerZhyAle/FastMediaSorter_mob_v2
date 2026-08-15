# S0171 — Link Download: CDN Request Context Replay + TikTok Desktop-UA Extraction

**Status**: In Progress  
**Priority**: 80  
**Created**: 2026-05-12  
**Updated**: 2026-05-12  
**Parent**: S0170 (deferred items: BUG-2 fixes 2–3, BUG-3, BUG-4)

<!-- implemented by /spec-all — 2026-05-12; STANDARD flavor, awaiting on-device verification -->
<!-- yt-dlp engine remains a separate noLegal-flavor effort (Google Play won't ship it) — see §9 -->

---

## 1. Motivation

S0170 delivered honest failure for Instagram (BUG-2 fix 1: sniff → reject corrupt download) and stopped the Facebook auth loop (BUG-1). Verified in on-device logs `fastmediasorter_20260512_025229.log`:

```
S0170: rejected corrupted link download — kind=too-small bytes=200 mime=video/mp4
S0170: link share result — host=www.instagram.com result=DownloadCorrupted
```

User currently has **0% success on Instagram** — the file is intercepted, download is attempted, CDN returns a 200-byte garbage stub, sniff rejects. The rejection is correct; the underlying cause is the bare re-fetch missing the `Referer` header. Same log proves Threads CDN works without `Referer` (different bucket policy).

This spec fixes the root cause so the intercepted CDN URLs are actually downloaded, and adds TikTok desktop-UA so the WebView sees the actual video page instead of the "open in app" interstitial.

---

## 2. Research Findings (verified against yt-dlp / instaloader / gallery-dl source + CDN behaviour from test logs)

### A. Instagram CDN — `Referer` (and a real `User-Agent`) header is the missing piece

> **Correction (research, 2026-05-12):** two small refinements to this subsection. (1) The ~200-byte error response is **not** an HTML stub — the log chain `dynamic outcome=stream` → `LinkDownloadWriter: rejected corrupted` proves `DirectFileExtractionStrategy.open()` did **not** block on the mime check (that would have produced `OpenResult.Blocked(MimeNotAllowed)` → `Result.Failed.MimeBlocked`, not `DownloadCorrupted`), so the body is served with an allowed media-ish `Content-Type` (`video/mp4` or octet-stream) — IG's edge lies about the type. It's also not HTML/JSON at byte 0 (the S0170 sniffer reported `kind=too-small`, not `kind=html`/`kind=json`). (2) `Referer` is not literally the *only* missing piece: `LinkDownloadModule.provideLinkDownloadClient` sets no `User-Agent`, so the CDN GET currently goes out as `User-Agent: okhttp/4.x` — a UA IG (and many CDNs) reject outright. The fix must set **both** `Referer` and a real browser `User-Agent` on the re-fetch (Step 5's code already does this — just keep this subsection's prose in sync).

Instagram CDN bucket `t50.2886-16` on `scontent.cdninstagram.com` validates `Referer` at the edge, independently of the `oh=`/`oe=` token in the URL. Without it the CDN returns `200 OK` with a ~200-byte stub (confirmed: `kind=too-small bytes=200`).

Required headers for a successful CDN re-fetch:
```http
Referer: https://www.instagram.com/
User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6422.165 Safari/537.36
Accept: video/mp4,video/*;q=0.9,*/*;q=0.8
```

**Key facts:**
- `Cookie` is NOT required — the `oh=` token in the URL is already the auth credential.
- `Origin` is NOT required, only `Referer`.
- The CDN URL is **not UA-bound** — any desktop Chrome UA works regardless of which UA loaded the page.
- Token `oe=` is a hex-encoded Unix timestamp. Lifetime is typically 24 h from generation. The intercepted URL is used immediately after WebView renders → no expiry concern in practice.
- Threads CDN bucket `t15.5256-10` does NOT enforce `Referer` (embed-friendly policy) → bare GET works → explains why Threads already works while Instagram doesn't.

**Sources:** yt-dlp `InstagramIE._HEADERS`, instaloader `session.headers`, gallery-dl Instagram extractor.

### B. TikTok — desktop User-Agent eliminates the "open in app" interstitial

Test logs `fastmediasorter_20260512_025229.log` lines 516–518:
```
dynamic-extractor start url=https://vm.tiktok.com/ZNRs3o6FG timeoutMs=22000
dynamic-strategy social-preview-only
```
The WebView sends Android UA → TikTok mobile web fires `snssdk1233://feed` / `snssdk1340://feed` deep link redirects (intercepted, blocked) → serves "open in app" stub → no video CDN URLs → `social-preview-only`.

Switching WebView `userAgentString` to a desktop Chrome string makes TikTok serve the full SSR page.

**UA that works:**
```
Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36
```

On the full SSR page, TikTok embeds video data in:
```html
<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">{ ... }</script>
```

JSON path to video URL:
```
.__DEFAULT_SCOPE__["webapp.video-detail"].itemInfo.itemStruct.video.playAddr       ← main stream URL
.__DEFAULT_SCOPE__["webapp.video-detail"].itemInfo.itemStruct.video.downloadAddr   ← watermark-free (if present)
.__DEFAULT_SCOPE__["webapp.video-detail"].itemInfo.itemStruct.video.bitrateInfo[0].PlayAddr.UrlList[0]  ← high-quality fallback
```

The `playAddr` / `downloadAddr` URLs are signed CDN URLs. They are not Referer-protected (unlike Instagram) but do require:
```http
Referer: https://www.tiktok.com/
User-Agent: <same desktop UA used to load the page>
```

### C. TikTok `vm.tiktok.com` short URL resolution

`vm.tiktok.com/XYZ` is a 301 redirect to `www.tiktok.com/@user/video/<ID>`. With Android UA, the redirect target still serves the interstitial. With desktop UA, the redirect target serves the full page. Resolution is therefore implicit once the desktop UA is set — no separate redirect-follow step needed.

**BUG-3 (cookie host mismatch) interaction:** Second TikTok attempt in logs (line 557):
`sessionApplied=false` because `accountId=null` (retry lambda looked up `vm.tiktok.com`, found nothing). BUG-3 canonical-host fix is still needed (tracked in §7) but is lower priority than the UA fix — without the UA fix the session has no effect anyway (TikTok serves interstitial regardless of cookies).

---

## 3. Affected Files

| File | Change |
|------|--------|
| `data/link/HtmlMediaCandidate.kt` | Add `pageOrigin: String?` field |
| `data/link/InvisibleWebViewExtractionStrategy.kt` | (1) Track `currentPageUrl` in WebViewClient; (2) pass `pageOrigin` on candidate creation; (3) add desktop-UA override for TikTok hosts; (4) add `__UNIVERSAL_DATA_FOR_REHYDRATION__` block to `DOM_DISCOVERY_SCRIPT`; (5) pass `pageOrigin` headers to `direct.open()` |
| `data/link/DirectFileExtractionStrategy.kt` | Accept optional `extraHeaders: Map<String,String>` in `open()`; add them to the OkHttp GET request |
| `domain/usecase/link/OpenResult.kt` | (if needed) no change if `direct.open()` overload is added without touching the sealed class |

---

## 4. Implementation Plan

### Step 1 — `HtmlMediaCandidate`: add `pageOrigin`

```kotlin
data class HtmlMediaCandidate(
    val url: String,
    val source: Source,
    val tentativeMime: String?,
    val tentativeSizeBytes: Long?,
    val manifest: StreamingManifest? = null,
    val pageOrigin: String? = null,   // ← NEW: origin of the page that produced this candidate
)
```

`pageOrigin` = scheme + host of the page loaded in WebView (e.g. `https://www.instagram.com`). Used by `DirectFileExtractionStrategy` to build `Referer`.

### Step 2 — `InvisibleWebViewExtractionStrategy`: track page URL + TikTok UA override

In `renderCandidates()`, introduce `var currentPageUrl: String = url` updated in `onPageStarted` / `onPageFinished`:

```kotlin
override fun onPageStarted(view: WebView?, loadedUrl: String?, favicon: Bitmap?) {
    super.onPageStarted(view, loadedUrl, favicon)
    if (loadedUrl != null) currentPageUrl = loadedUrl
}
override fun onPageFinished(view: WebView?, loadedUrl: String?) {
    super.onPageFinished(view, loadedUrl)
    if (loadedUrl != null) currentPageUrl = loadedUrl
    // ... existing DOM_SETTLE logic
}
```

`pageOrigin` for each candidate = `currentPageUrl.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" }`.

Update `rememberCandidate` to accept and store the current `pageOrigin`:
```kotlin
fun rememberCandidate(rawUrl: String?, source: HtmlMediaCandidate.Source) {
    // ...existing bounds checks...
    if (!observedRequests.containsKey(normalized)) {
        observedRequests[normalized] = Pair(manifestAwareSource(normalized, source), currentPageUrl)
    }
}
```
(Change `observedRequests` type from `LinkedHashMap<String, Source>` to `LinkedHashMap<String, Pair<Source, String?>>`)

In `finish()`, construct candidates with `pageOrigin`:
```kotlin
val intercepted = synchronized(observedRequests) {
    observedRequests.mapNotNull { (candidateUrl, pair) ->
        val origin = pair.second?.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" }
        candidateFor(candidateUrl, pair.first, pageOrigin = origin)
    }
}
```

**TikTok UA override** in `configureWebView()`:
```kotlin
val host = url.toHttpUrlOrNull()?.host?.lowercase().orEmpty()
if (TIKTOK_HOSTS.any { host == it || host.endsWith(".$it") }) {
    webView.settings.userAgentString = DESKTOP_CHROME_UA
}
```

```kotlin
// in companion object:
val TIKTOK_HOSTS = setOf("tiktok.com", "vm.tiktok.com", "vt.tiktok.com")
const val DESKTOP_CHROME_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
```

### Step 3 — `DOM_DISCOVERY_SCRIPT`: add TikTok JSON block

Append to the existing script (after OG-meta block, before `return out`):

```javascript
// TikTok: __UNIVERSAL_DATA_FOR_REHYDRATION__ SSR JSON
(function() {
  try {
    var el = document.getElementById('__UNIVERSAL_DATA_FOR_REHYDRATION__');
    if (!el) return;
    var data = JSON.parse(el.textContent);
    var scope = data && data.__DEFAULT_SCOPE__;
    var detail = scope && scope['webapp.video-detail'];
    var item = detail && detail.itemInfo && detail.itemInfo.itemStruct;
    var video = item && item.video;
    if (!video) return;
    if (video.playAddr) push(video.playAddr, 'VIDEO_TAG');
    if (video.downloadAddr && video.downloadAddr !== video.playAddr) push(video.downloadAddr, 'VIDEO_TAG');
    var bitrateInfo = video.bitrateInfo;
    if (Array.isArray(bitrateInfo) && bitrateInfo.length > 0) {
      var urlList = bitrateInfo[0].PlayAddr && bitrateInfo[0].PlayAddr.UrlList;
      if (Array.isArray(urlList) && urlList.length > 0) push(urlList[0], 'VIDEO_TAG');
    }
  } catch(e) {}
})();
```

### Step 4 — `DirectFileExtractionStrategy`: accept extra headers

Add overload (keeps `UrlExtractionStrategy` interface intact):

```kotlin
suspend fun open(
    url: String,
    onProgress: (bytesRead: Long, total: Long?) -> Unit,
    extraHeaders: Map<String, String> = emptyMap(),
): OpenResult = withContext(Dispatchers.IO) {
    val httpUrl = url.toHttpUrlOrNull()
        ?: return@withContext OpenResult.Blocked(BlockedReason.NonHttpScheme)
    try {
        val requestBuilder = Request.Builder().url(httpUrl).get()
        extraHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }
        val response: Response = httpClient.newCall(requestBuilder.build()).execute()
        // ... rest unchanged
    }
}
```

The `UrlExtractionStrategy.open(url, onProgress)` interface method delegates to this overload with `extraHeaders = emptyMap()`.

### Step 5 — `InvisibleWebViewExtractionStrategy.open()`: pass headers to direct

```kotlin
val chosen = CandidateSelectionPolicy.choose(preferred)
    ?: return OpenResult.NotFound("dynamic_no_media")
val manifest = chosen.manifest
if (manifest != null) {
    return OpenResult.Streaming(
        manifest = manifest,
        tentativeFileName = deriveStreamingFileName(chosen.url),
    )
}

// S0171: pass page-origin headers for CDN re-fetch (Referer required by Instagram CDN)
val origin = chosen.pageOrigin
val extraHeaders = if (origin != null) {
    buildMap {
        put("Referer", "$origin/")
        put("User-Agent", webViewUaFor(chosen.url))
        put("Accept", "video/mp4,video/*;q=0.9,*/*;q=0.8")
    }
} else emptyMap()
return direct.open(chosen.url, onProgress, extraHeaders)
```

```kotlin
private fun webViewUaFor(url: String): String {
    val host = url.toHttpUrlOrNull()?.host?.lowercase().orEmpty()
    return if (TIKTOK_HOSTS.any { host == it || host.endsWith(".$it") }) {
        DESKTOP_CHROME_UA
    } else {
        // Use a generic desktop UA for all CDN re-fetches — avoids UA mismatch with page UA.
        DESKTOP_CHROME_UA
    }
}
```

### Step 6 — Debug enrichment: candidate URL + HTTP status + page HTML dump

Before the next test round all three failure paths must produce self-evident logcat. Three sub-steps:

**6a. CDN re-fetch tag** — extend the `S0171: CDN re-fetch` `Timber.d` line in `InvisibleWebViewExtractionStrategy` to include the first 120 chars of `chosen.url`:
```kotlin
Timber.d("S0171: CDN re-fetch — host=%s source=%s referer=%s range=%s url=%.120s",
    httpUrl.host, chosen.source, origin ?: "none",
    extraHeaders.containsKey("Range"), chosen.url)
```

**6b. HTTP response logging** — in `DirectFileExtractionStrategy.open()`, immediately after `httpClient.newCall(...).execute()`, log before the response body is consumed:
```kotlin
Timber.d("S0171: direct-open response — url=%.120s status=%d contentType=%s length=%s",
    response.request.url.toString(), response.code,
    response.header("Content-Type", "null"),
    response.header("Content-Length", "?"))
```
Also log each redirect hop if `response.priorResponse != null` (chain walk) — add a helper that logs every `priorResponse.request.url` down the chain.

**6c. Page HTML dump on dynamic-failure** — in `InvisibleWebViewExtractionStrategy.renderCandidates()`, after DOM settle when no stream candidates are found, evaluate a JS snippet that returns `document.documentElement.outerHTML` and write the result to:
```
getExternalFilesDir(null)/link_debug_<host>_<yyyyMMdd_HHmmss>.html
```
Use `WebView.evaluateJavascript()` on the main thread inside the existing timeout/settle coroutine. Cap the dump at 512 KB. Only write when `BuildConfig.DEBUG` is true. Log the output path with `Timber.d("S0171: page-dump — %s")`. 

### Step 7 — TikTok: prioritise `downloadAddr` / `bitrateInfo` URL over `playAddr`

`playAddr` on `www.tiktok.com/…` (confirmed in build `333`) is a redirect-based API endpoint that returns `text/html` or `application/json` after following redirects → `MimeBlocked`. `downloadAddr` and `bitrateInfo[0].PlayAddr.UrlList[0]` carry direct `v16-webapp*.tiktok.com` CDN URLs.

In `DOM_DISCOVERY_SCRIPT`, reorder the push sequence inside the TikTok block: push `downloadAddr` first, then `bitrateInfo[0].PlayAddr.UrlList[0]`, push `playAddr` last (fallback):
```javascript
// Prioritise direct CDN URLs — downloadAddr and bitrateInfo carry v16-webapp* hosts;
// playAddr sometimes resolves to a www.tiktok.com API redirect (MimeBlocked).
if (video.downloadAddr && video.downloadAddr !== video.playAddr) push(video.downloadAddr, 'VIDEO_TAG');
var bitrateInfo = video.bitrateInfo;
if (Array.isArray(bitrateInfo) && bitrateInfo.length > 0) {
  var urlList = bitrateInfo[0].PlayAddr && bitrateInfo[0].PlayAddr.UrlList;
  if (Array.isArray(urlList) && urlList.length > 0) push(urlList[0], 'VIDEO_TAG');
}
if (video.playAddr) push(video.playAddr, 'VIDEO_TAG');
```

`CandidateSelectionPolicy.choose()` picks the first accepted candidate — after this reorder it will prefer `downloadAddr` / `bitrateInfo` CDN URLs over `playAddr`.

### Step 8 — `LinkDownloadCookieJar`: eTLD+1 wildcard for TikTok CDN subdomains

Confirmed in build `333`: `v16-webapp-prime.tiktok.com` returns `403` because `LinkDownloadCookieJar.loadForRequest()` does exact-host match — cookies stored under `www.tiktok.com` are not forwarded to CDN subdomains. Browsers honour `Domain=.tiktok.com` on `odin_tt`/`ttwid`/`s_v_web_id` and send them to all subdomains.

In `LinkDownloadCookieJar.loadForRequest(url)`, change host matching from strict equality to eTLD+1 wildcard for the session-context host:
```kotlin
private fun hostsMatch(storedHost: String, requestHost: String): Boolean {
    if (storedHost == requestHost) return true
    // e.g. stored=www.tiktok.com, request=v16-webapp-prime.tiktok.com
    // both share eTLD+1 = tiktok.com
    val storedEtld1 = storedHost.substringAfterLast('.', "")
        .let { tld -> storedHost.substringBeforeLast('.', storedHost).substringAfterLast('.') + '.' + tld }
    val requestEtld1 = requestHost.substringAfterLast('.', "")
        .let { tld -> requestHost.substringBeforeLast('.', requestHost).substringAfterLast('.') + '.' + tld }
    return storedEtld1 == requestEtld1 && storedEtld1.isNotBlank()
}
```
Or, simpler: use `okhttp3.internal.publicsuffix.PublicSuffixDatabase` (already on classpath via OkHttp) to extract registrable domain, then compare:
```kotlin
val db = PublicSuffixDatabase.get()
fun registrable(host: String) = db.getEffectiveTldPlusOne(host)
if (registrable(storedHost) != null && registrable(storedHost) == registrable(requestHost)) return true
```

Scope: only apply wildcard for the `sessionContext` host comparison inside `loadForRequest`. The `saveFromResponse` path is unchanged (still saves under the response host key).

---

## 5. Expected Test Results After Implementation

| Platform | Before S0171 | After S0171 |
|----------|-------------|-------------|
| Instagram reel | `DownloadCorrupted` toast (200 bytes) | File downloads, sniff passes, player opens |
| Instagram post `/p/` | `social-preview-only` toast | Unchanged — DOM doesn't fire video URL (BUG-5 deferred) |
| TikTok `vm.tiktok.com` | `social-preview-only` (interstitial page) | WebView renders full page, `__UNIVERSAL_DATA__` parsed, `playAddr` intercepted as candidate |
| Threads | Already works | Unchanged |
| Facebook | `NoMediaFound` toast (no loop) | Unchanged |

**TikTok success path in logcat to verify:**
```
dynamic-extractor start url=https://vm.tiktok.com/…
[should NOT see] webview-auth blocked-redirect scheme=snssdk1233
S0151-diag: host=vm.tiktok.com strategy=dynamic sessionApplied=true outcome=stream
```

**Instagram success path in logcat to verify:**
```
S0151-diag: host=www.instagram.com strategy=dynamic outcome=stream
[should NOT see] rejected corrupted link download — kind=too-small
[S0166] real media saved ...
```

---

## 6. Risk & Edge Cases

| Risk | Mitigation |
|------|-----------|
| TikTok `playAddr` URL requires same desktop UA on re-fetch | `webViewUaFor()` returns `DESKTOP_CHROME_UA` for TikTok CDN hosts |
| TikTok `playAddr` URL may be Referer-validated (`https://www.tiktok.com/`) | `pageOrigin` from WebView will be `https://www.tiktok.com` after redirect — Referer set correctly |
| Instagram `oe=` token expires between intercept and download | Window is < 2 s in practice; 24 h lifetime makes this a non-issue |
| Desktop UA breaks non-TikTok WebView rendering | UA is only overridden for `TIKTOK_HOSTS`; all other sites get default system WebView UA |
| `currentPageUrl` read from non-main thread | Guard with `synchronized` or `@MainThread` annotation; `observedRequests` is already `synchronized` |
| TikTok `__UNIVERSAL_DATA__` JSON schema changes | `try/catch` in JS block → fails silently → falls back to intercepted request candidates |
| `probeCandidates()` HEAD request on CDN URL without `Referer` → gets the bogus `200`/`video/mp4`/tiny `Content-Length`, feeding garbage sizes to `shouldReturnBatch` / `isAcceptedCandidate` | Add the same `pageOrigin` Referer (+ desktop UA) to the HEAD request in `probeCandidates()`, or skip the HEAD entirely for intercepted candidates — promote this from a risk note into Step 5 of the plan |
| TikTok `playAddr` re-fetch sometimes returns `403` without a `Range` header (yt-dlp always sends `Range: bytes=0-` for TikTok) | Add `Range: bytes=0-` to the TikTok branch of `extraHeaders` in `webViewUaFor`/the header-builder |
| Expecting cookies to reach the CDN GET | IG CDN: fine — `oh=` URL token is the credential. **TikTok CDN (`v16-webapp-prime.tiktok.com`): confirmed `status=403` in build `333` logs** — `LinkDownloadCookieJar` must implement eTLD+1 wildcard matching (`*.tiktok.com`) to forward `www.tiktok.com` session cookies to CDN subdomains |
| TikTok `playAddr` resolves to API endpoint (`www.tiktok.com`), not a direct CDN URL | **Confirmed in build `333`** — `host=www.tiktok.com source=VIDEO_TAG` → `MimeBlocked`. Prioritise `downloadAddr` / `bitrateInfo[0].PlayAddr.UrlList[0]` which carry direct `v16-webapp*.tiktok.com` CDN URLs |
| IG CDN double-fetch — WebView GETs the signed URL first; OkHttp re-fetch arrives second | `shouldInterceptRequest` fires after WebView's own request — signed token may be single-use on some IG CDN buckets. Hypothesis B for the persistent 248-byte stub. Requires candidate URL logging to confirm |
| Byte-range query params on intercepted IG URLs | Non-issue — IG progressive `.mp4` URLs (`video_versions[].url`, `<video>.src`) carry no `bytestart`/`byteend`; IG's byte-range chunking lives in the `Range` HTTP header during in-player playback, never in the URL. `shouldInterceptRequest` sees the clean URL; `direct.open` re-fetches with no `Range` → full file. S0170's "BUG-2 fix 3 (strip range params)" is a no-op for these cases — out of scope |

---

## 7. Deferred (not in this spec)

- **BUG-3** — Canonical host rule for cookie storage + `vm.tiktok.com` account lookup. Currently `sessionApplied=false` on second TikTok attempt because retry lambda does `listAccountsForHost("vm.tiktok.com")` → empty. Fix: normalize host to canonical (`www.tiktok.com`) before account lookup. Separate ticket.
- **BUG-5** — Instagram `/p/` post extraction (carousel DOM doesn't fire video URL in 22 s). Needs diagnostics enrichment first. Separate ticket.
- **S0170 §8.G items** — foreground blocking during download, auto-open-player UX, destination selection — need `/ui-clarify`.

---

## 8. Research Addendum (Claude, 2026-05-12)

### A. S0170 fixes — verified in `logs/fastmediasorter_20260512_025229.log` (build `2.60.5120.227-DEBUG`)

- **BUG-1 (Facebook auth loop) — fixed.** Three separate FB shares of `facebook.com/share/p/…` (lines 601 / 638 / 675): each runs `[S0166] unknown host, standard pipeline` → `[S0166] applying stored session: host=www.facebook.com accountId=64e68132…` (`enqueueLinkDownloadSilent` now passes the stored account) → `[S0166] no real media found after analysis` → `S0170: link share result — host=www.facebook.com result=NoMediaFound accountId=64e68132… authOfferShown=false`. No `escalating to auth offer`, no repeated `auth dialog shown`. The escalation guard's `accountId == null` clause suppresses the offer because a session is already present.
- **BUG-2 fix 1 (corrupt-download rejection) — working.** Instagram reel `/reel/DYNxdMqjHoX` (line 281): `S0151-diag … strategy=dynamic … outcome=stream` → line 282 `LinkDownloadWriter: rejected corrupted download — kind=too-small bytes=200 name=AQNJdvWK…mp4` → `S0170: rejected corrupted link download — kind=too-small bytes=200 mime=video/mp4` → `[S0166] download rejected as corrupted` → `result=DownloadCorrupted`. The player no longer opens on the 200-byte stub; the user gets the honest toast. `DownloadCorrupted` is not `NoMediaFound` so it does not re-trigger the unknown-host escalation.
- **BUG-6 (cookie dedup) — working.** Line 501 `S0170: webview auth cookies collected (deduped) — host=www.tiktok.com count=10`; line 502 `encrypted-cookie-store save … count=10`; line 507 names = 10 distinct (`tt_csrf_token, tiktok_webapp_theme_source, tiktok_webapp_theme, passport-sotl-auth-token-nonce_…, s_v_web_id, cookie-consent, tt_chain_token, ttwid, msToken, perf_feed_cache`) — no duplicates.
- **Threads still content-dependent.** `@tiarasetter/post` → `social-preview-only`; `@l.miroslava2710/post` (line 425) → `outcome=stream` → line 426 `LinkDownloadWriter: saved 'AQMWX3G6…mp4'` → `FellBackToDownloads`. So the Threads CDN re-fetch succeeds with the bare `okhttp/*` UA and no `Referer` — confirms the §2.A premise that the Threads bucket doesn't enforce `Referer`.
- **Instagram = 0 % confirmed.** `/p/DYNAM-nFdCm`, `/stories/dellerstacey` → `social-preview-only`; `/reel/…` → stream-but-corrupted (200 bytes). Nothing downloads playable.
- **TikTok still interstitial.** `vm.tiktok.com/ZNRs3o6FG` → html `outcome=not-found`, dynamic `outcome=social-preview-only` (lines 513, 517–518). Second attempt (line 557) `sessionApplied=false` — BUG-3: the account is stored under `acct:www.tiktok.com:…` but the lookup is on `vm.tiktok.com`.

### B. Plan sanity-check against the current checkout — all green

- `data/link/HtmlMediaCandidate.kt` — `data class` with 5 fields + nested `Source` enum, no `pageOrigin` today. Adding `val pageOrigin: String? = null` is clean: `probeCandidates()` rebuilds via `candidate.copy(...)` so the field survives, and `.distinctBy { it.url }` doesn't touch it. Make sure DOM-script candidates (the TikTok `playAddr`) also receive a `pageOrigin` — the `currentPageUrl` at DOM-eval time, which after the `vm.tiktok.com` → `www.tiktok.com` 301 is `https://www.tiktok.com`.
- `domain/usecase/link/UrlExtractionStrategy.kt` declares `suspend fun open(url: String, onProgress: …): OpenResult` — only two params. `DirectFileExtractionStrategy` currently `override`s it. Step 4's overload must therefore be a **new non-`override`** `suspend fun open(url, onProgress, extraHeaders)`, with the `override fun open(url, onProgress)` left in place delegating to it (`= open(url, onProgress, emptyMap())`). An `override` cannot grow a third parameter even with a default — the Step 4 code snippet should show the delegating override explicitly so this isn't mis-implemented.
- `di/LinkDownloadModule.kt` — `DirectFileExtractionStrategy` is bound as a `@Binds @IntoSet UrlExtractionStrategy` (the registry path uses the 2-arg `open`) **and** is a direct constructor dependency of `InvisibleWebViewExtractionStrategy` (which can call the 3-arg overload). No DI change is needed. Also note: the `@Named("linkDownload")` client has no default `User-Agent` and `followRedirects(true)` — relevant to §2.A item C and to the `vm.tiktok.com` 301 following.
- `OpenResult.Streaming` vs `direct.open`: TikTok `playAddr` is a progressive URL (not `.m3u8`/`.mpd`) → routes through `direct.open` → needs the page-origin headers (covered by Step 5). If a TikTok `.mpd` ever surfaces it routes to `runStreaming`, which uses `StreamingPipeline` — a different download path the headers wouldn't touch. Out of scope for S0171; note it.

### C. Scope notes

- **`html` strategy + desktop-UA for TikTok (consider, not required).** `HtmlPageExtractionStrategy` runs before the WebView strategy and currently returns `not-found` for `vm.tiktok.com` (log line 513). Giving it a desktop `User-Agent` + a regex parse of `__UNIVERSAL_DATA_FOR_REHYDRATION__` / `SIGI_STATE` for TikTok hosts would be faster (no WebView, no 22 s timeout) and would also work when the WebView path is flaky. Keep the DOM-script branch as the fallback. Optional.
- **Promote the `probeCandidates()` HEAD-headers item into Step 5** — it currently only lives in the §6 risk table, but it's a real correctness item: without the `Referer`+UA on the HEAD, the size heuristics see the bogus tiny `Content-Length`.
- **Byte-range stripping is out of scope** — S0170's "BUG-2 fix 3" is a no-op for the cases in these logs (see the §6 risk row). Don't add it to S0171.

### D. Success precondition to verify after implementation

After the `Referer`+UA fix, the S0170 sniffer (`LinkDownloadWriter.sniffMedia`) must **not** false-reject the now-real reel: a real reel is MB-sized so the `length < 1024` "too-small" branch won't fire, and the `ftyp`-at-offset-4 check passes. The logcat success path is `[S0166] real media saved …` with **no** `rejected corrupted link download` line.

### E. Second on-device pass — `fastmediasorter_20260512_034113.log` (build `2.60.5120.333-DEBUG`, 2026-05-12)

**S0171 probes confirmed.** `TikTok desktop-UA override applied` fired (lines 647, 706). `CDN re-fetch` fired (lines 286, 465, 657, 708, 752, 824). DOM-script found `VIDEO_TAG` candidates for TikTok (source=VIDEO_TAG, lines 657, 708). Both implementation paths are reached — failures are network/policy level, not code path.

**Threads** — `@hedgehogphilosopher/post/DYMrp4wEYTz` → CDN re-fetch to `instagram.fmla1-2.fna.fbcdn.net` with `referer=https://www.threads.com/` → MP4 saved. Two other Threads posts = `social-preview-only` (no video, expected). ✅

**Facebook CDN** (unexpected) — `scontent.fmla1-1.fna.fbcdn.net` / `static.xx.fbcdn.net` accept re-fetch with `referer=https://www.facebook.com/`; images (webp, jpg) saved. Not in §5 expected results but works. ✅

**Instagram `/reel/DYHYxFSIibl`** — CDN re-fetch to `instagram.fmla1-1.fna.fbcdn.net` with correct `referer` and desktop UA fires, server returns 248-byte stub (`kind=too-small`). Same CDN domain pool that serves Threads successfully — issue is IG-specific. Three hypotheses:
1. `INLINE_LINK` source on `fmla1-*` is a story/preview asset, not `video_versions[].url` — Referer fix applied to the wrong URL.
2. Double-fetch invalidation — WebView's own GET burns the signed URL; OkHttp re-fetch arrives second and gets the stub.
3. IG enforces additional policy headers (`Sec-Fetch-Site`, `Sec-Fetch-Mode`, `Origin`) that the Threads bucket doesn't require. ❌

**TikTok `vm.tiktok.com/ZNRsTRFXg/`** — `playAddr` resolved to `host=www.tiktok.com` (API/redirect endpoint, not CDN). `direct.open()` follows the redirect; final MIME = non-media → `MimeBlocked`. Compounding factor: WiFi→LTE network switch at `03:44:37` during re-fetch (lines 649-656). `downloadAddr` or `bitrateInfo[0].PlayAddr.UrlList[0]` should be prioritised — they typically carry direct `v16-webapp*.tiktok.com` CDN URLs. ❌

**TikTok `vm.tiktok.com/ZNRsTysAK/`** — `playAddr` CDN host = `v16-webapp-prime.tiktok.com` (correct), but `status=403`. Two root causes: (1) BUG-3 — `accountId=null` because `vm.tiktok.com` lookup finds 0 accounts; (2) cookie wildcard gap — `LinkDownloadCookieJar` stores cookies under `www.tiktok.com`, does not forward them to `v16-webapp-prime.tiktok.com` (exact-host match). Browser sends `odin_tt`/`ttwid`/`s_v_web_id` via `Domain=.tiktok.com`; the OkHttp jar must do the same. ❌

**Next-pass debug enrichment required** — add the following before the next on-device test:
- Log full `candidate.url` (first 120 chars) in the `S0171: CDN re-fetch` tag — needed to distinguish `video_versions` vs preview asset for Instagram.
- Log OkHttp response code + `Content-Type` header from `DirectFileExtractionStrategy.open()` before accept/reject decision.
- Log redirect chain intermediate URLs in `direct.open()` — needed to confirm where `playAddr` actually lands.
- After DOM-settle, when `dynamic` extraction fails (`outcome != stream`), dump full page HTML to `temp/link_debug_<host>_<timestamp>.html` — needed to verify what IG and TikTok serve to the WebView under desktop UA.

---

## 9. Implementation Status

### Done (2026-05-12, `/spec-all`) — STANDARD flavor, needs on-device verification

Flavor-agnostic — no `BuildConfig` gate (these are extraction fixes, not yt-dlp; the yt-dlp engine remains a separate `noLegal`-flavor effort).

- **Step 1** — `HtmlMediaCandidate` gained `val pageOrigin: String? = null` (scheme+host of the page that produced the candidate).
- **Step 2** — `InvisibleWebViewExtractionStrategy`: `WebViewClient.onPageStarted`/`onPageFinished` track the current page URL in an `AtomicReference` (follows the `vm.tiktok.com` → `www.tiktok.com` 301); `observedRequests` now stores `Pair<Source, pageUrl>`; intercepted and DOM candidates are built with their `pageOrigin`. For TikTok hosts (`tiktok.com`, `vm.tiktok.com`, `vt.tiktok.com`) the WebView `userAgentString` is set to a desktop Chrome string (`DESKTOP_CHROME_UA`) so TikTok serves the full SSR page instead of the "open in app" stub.
- **Step 3** — `DOM_DISCOVERY_SCRIPT` gained a TikTok block: parses `#__UNIVERSAL_DATA_FOR_REHYDRATION__` → `__DEFAULT_SCOPE__["webapp.video-detail"].itemInfo.itemStruct.video` → pushes `playAddr`, `downloadAddr`, and `bitrateInfo[0].PlayAddr.UrlList[0]` as `VIDEO_TAG` candidates (wrapped in `try/catch` against schema drift).
- **Step 4** — `DirectFileExtractionStrategy` gained `open(url, onProgress, extraHeaders)` (the `UrlExtractionStrategy.open(url, onProgress)` override delegates to it with `emptyMap()`); `extraHeaders` are applied to the GET.
- **Step 5** — `InvisibleWebViewExtractionStrategy.open()` and `probeCandidates()` build per-candidate replay headers via `cdnReplayHeaders(candidate)`: always a browser `User-Agent`; `Referer: <pageOrigin>/` when known (required by the Instagram CDN); `Accept: video/*`; `Range: bytes=0-` for TikTok CDN hosts. `isAcceptedCandidate` now accepts `VIDEO_TAG`/`SOURCE_TAG`/`AUDIO_TAG`/`OG_VIDEO`/`JSON_LD`/`TWITTER_PLAYER_STREAM`/manifest sources without a MIME probe (TikTok `playAddr` URLs are extension-less signed paths).
- **Bonus** — `LinkDownloadModule.provideLinkDownloadClient` gained a `DefaultUserAgentInterceptor`: every link-download OkHttp request gets a desktop browser `User-Agent` unless the caller set one — fixes the `okhttp/4.x` UA that CDNs reject (helps the `html` strategy and the standalone direct-file path too).
- Debug probe tags `Timber.d("S0171: …")` at two changed flow entries (TikTok UA override in `configureWebView`, CDN re-fetch in `open()`) — bound to status `BlockNeedUserTest`.
- `standard debug` build green.

### Not done in pass 1 — in progress (Steps 6-8)

- **Step 6 — Debug enrichment** (candidate URL, HTTP status/Content-Type, redirect chain, page HTML dump to `temp/`) — NOT implemented yet. Required before next on-device test.
- **Step 7 — TikTok `downloadAddr` priority** in `DOM_DISCOVERY_SCRIPT` — NOT implemented yet. Fixes `MimeBlocked` for `playAddr=www.tiktok.com` case.
- **Step 8 — `LinkDownloadCookieJar` eTLD+1 wildcard** — NOT implemented yet. Fixes `status=403` on `v16-webapp-prime.tiktok.com`.

### Deferred beyond this spec

- **BUG-3** — canonical-host rule for cookie storage / `vm.tiktok.com` account lookup (own ticket).
- **`html`-strategy desktop-UA + `__UNIVERSAL_DATA__` regex parse for TikTok** — §8.C optional item.
- **Facebook `share/p/…` video** — still `NoMediaFound`; FB video extraction is a large separate effort.
- **Instagram `/p/` posts** — `social-preview-only` (BUG-5, deferred).

### On-device test checklist

- [x] **FAIL** (build `333`) Instagram `/reel/DYHYxFSIibl` — CDN re-fetch fires with correct `Referer`+UA to `instagram.fmla1-1.fna.fbcdn.net`; server returns 248-byte stub. Root cause unknown (§8.E, three hypotheses). **Requires debug enrichment before next pass** (candidate URL, HTTP status/Content-Type, page HTML dump to `temp/`).
- [ ] **RETEST after debug fix** Instagram `/reel/…` → `[S0166] real media saved`, no `rejected corrupted link download — kind=too-small`. Logcat must show `S0171: CDN re-fetch — host=… candidate=…` + response `status=200 contentType=video/*`.
- [x] **FAIL** (build `333`) TikTok `vm.tiktok.com/ZNRsTRFXg/` — `playAddr` host=`www.tiktok.com` (API endpoint) → `MimeBlocked`. TikTok `vm.tiktok.com/ZNRsTysAK/` — `v16-webapp-prime.tiktok.com` returns 403 (cookie eTLD+1 gap + BUG-3). **Requires**: `downloadAddr`/`bitrateInfo` URL priority fix + `LinkDownloadCookieJar` wildcard + BUG-3.
- [ ] **RETEST after fix** TikTok `vm.tiktok.com/…` → `S0171: TikTok desktop-UA override applied`, `outcome=stream`, CDN re-fetch `host=v16-webapp*.tiktok.com`, file saved. No `snssdk…` redirect, no `MimeBlocked`, no `AuthRequired`.
- [x] **PASS** (build `333`) Threads post with video → MP4 saved (`AQMy8jZEG0oNwEYn80TetQqVAkB2H0052AGVZG3BCEn7BC4…mp4`). Regression OK.
- [x] **PASS** (build `333`) Facebook `share/p/…` → image saved (webp/jpg), no auth loop. S0170 regression OK. (Bonus: FB CDN now returns media via CDN re-fetch with `Referer`.)

---

## Revision History

- **2026-05-12** — by `/spec-update` (`claude-opus-4-7`, focus: completeness, consistency)
  - Applied: added §8 "Research Addendum (Claude)" (S0170 fixes verified from `fastmediasorter_20260512_025229.log`; plan sanity-checked against the current checkout; scope notes; success precondition), two correction notes in §2.A (the IG CDN error stub is `video/mp4`+tiny-body, not an HTML page; the bare `okhttp/*` UA is also part of the problem — set both `Referer` and `User-Agent`), and four §6 risk-table rows (probe HEAD headers, TikTok `Range: bytes=0-`, cookieless CDN GET, byte-range non-issue). Proposed (DISCUSS): 0.
- **2026-05-12** — resumed by Android R&D Specialist after archive of S0161/S0166/S0170
  - Status `BlockNeedUserTest` → `In Progress`. Added Steps 6-8 to §4: debug enrichment (candidate URL + HTTP status/Content-Type logging + page HTML dump to `temp/`), TikTok `downloadAddr`/`bitrateInfo` priority reordering in `DOM_DISCOVERY_SCRIPT`, `LinkDownloadCookieJar` eTLD+1 wildcard for `*.tiktok.com` CDN subdomains. Updated §9 to split "not done" into "in progress" vs "deferred beyond spec".
- **2026-05-12** — by Android Solution Researcher (log analysis, `fastmediasorter_20260512_034113.log`, build `2.60.5120.333-DEBUG`)
  - Added §8.E: second on-device pass. S0171 probes confirmed (UA override + CDN re-fetch fire, DOM `VIDEO_TAG` extracted). Threads ✅ MP4 saved. Facebook ✅ images via CDN re-fetch (unexpected bonus). Instagram ❌ CDN still returns 248-byte stub — three hypotheses documented (wrong candidate URL / double-fetch invalidation / missing policy headers). TikTok ❌ two separate failures: (1) `playAddr=www.tiktok.com` API endpoint → `MimeBlocked`; (2) `v16-webapp-prime.tiktok.com` → 403 (cookie eTLD+1 gap + BUG-3). Updated on-device checklist with FAIL/PASS/RETEST status. Updated §6 cookie row (TikTok CDN 403 confirmed not-fine); added two new risk rows (playAddr=API-endpoint, IG double-fetch). Documented debug enrichment for next pass: candidate URL, HTTP status/Content-Type, redirect chain, page HTML dump to `temp/link_debug_<host>_<ts>.html`.
