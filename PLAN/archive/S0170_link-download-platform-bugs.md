# S0170 — Link Download: Platform-Specific Bug Cluster (Facebook / Instagram / TikTok / Threads)

**Status**: BlockNeedUserTest  
**Priority**: 85  
**Created**: 2026-05-12  
**Updated**: 2026-05-12  

<!-- auto-approved by /spec-all — 2026-05-12 -->

> **Implementation note (2026-05-12, /spec-all):** this pass implements the highest-value low-risk subset — **BUG-1** (Facebook auth loop), **BUG-2 fix 1** (post-download content validation → honest failure instead of opening the player on a broken file), and **BUG-6** (cookie dedup). Deferred to a follow-up: **BUG-2 fixes 2–3** (Referer/UA replay, strip byte-range params), **BUG-3** (canonical-host rule + migration + redirect-shortener resolution), **BUG-4** (TikTok desktop-UA + JSON parse), **BUG-5** (diagnostics enrichment), and all §8.G cross-cutting items (need `/ui-clarify` + a scoping decision). See §8.H for the order.

---

## 1. Scope

Post-S0166 on-device testing (2026-05-12, logs `fastmediasorter_20260512_014007.log` and `fastmediasorter_20260512_014152.log`) exposed five distinct bugs across four platforms. None of them are session-storage or threading problems (those were fixed in S0166/previous session). All are logic bugs or extraction gaps in the coordinator / presenter / extraction strategies.

Test device: Samsung SM-S731B, Android 16/API 36. App version `2.60.5120.138-DEBUG`.

---

## 2. Observed Test Results

| Platform | URL type | Outcome | Root cause tag |
|----------|----------|---------|----------------|
| Instagram | Post `/p/…` | Both html+dynamic: `social-preview-only` → toast | BUG-5 |
| Instagram | Reel `/reel/…` | dynamic: `outcome=stream` → file saved → **ExoPlayer crash on playback** | BUG-2 |
| Threads | Post (taisiya) | dynamic: `outcome=stream` → **saved OK, played** | ✅ WORKS |
| Threads | Post (zlata) | dynamic: `social-preview-only` → toast | BUG-5 (intermittent) |
| TikTok | Short URL `vm.tiktok.com/…` | Cookies saved for `www.tiktok.com`; applied with `sessionApplied=false` → social-preview-only | BUG-3 + BUG-4 |
| Facebook | Post `share/p/…` | NoMediaFound → auth loop (shows auth dialog repeatedly after login) | **BUG-1** |

---

## 3. Bug Detail

### BUG-1 — Facebook: infinite auth-offer loop (Critical)

**Logs**: lines 681–838 in `fastmediasorter_20260512_014152.log`.

**Symptom**: User sees the auth dialog → logs in → dialog appears again → dismisses → dialog again → dismisses → dialog again. User cannot escape except by pressing Back to home.

**Exact trace**:
1. `[S0166] unknown host, standard pipeline: host=www.facebook.com` — Facebook is not in `KnownAuthResources`, so it goes through standard (no-session) pipeline.
2. `html` + `dynamic` both return `NoMediaFound` (Facebook HTML extraction finds nothing).
3. Escalation fires: `NoMediaFound && !isAuthRetry && KnownAuthResources.matchHost(host)==null && !isDismissed` → `offerAuthThenDownload()`.
4. User opens WebView, logs into Facebook, presses Save → 11 cookies saved for `www.facebook.com`.
5. `offerAuthThenDownload` positive-button callback calls **`processLinkAutoDownload(url, accountId=savedAccountId, isAuthRetry=false)`** — `isAuthRetry` is hardcoded to default `false` here, bypassing the guard.
6. With cookies → coordinator still returns `NoMediaFound` (Facebook HTML/dynamic extractors don't find media even authenticated).
7. Escalation fires again: `!isAuthRetry` is still `true`, `isDismissedForHost` is still `false` → **dialog shown again**.
8. User presses "Пропустить" (neutral) → `processLinkAutoDownload(url, null, isAuthRetry=false)` is called → same flow → dialog again.
9. User dismisses → `processLinkAutoDownload(url, null, isAuthRetry=false)` → dialog again.
10. Infinite loop until user presses phone Back.

**Two root causes**:
- A) `offerAuthThenDownload` callback always calls `processLinkAutoDownload(..., isAuthRetry=false)` — it should use `isAuthRetry=true` after user completes auth.
- B) The escalation guard `!isAuthRetry` is the only guard; there is no check for "do we already have a session for this host?" — if a session exists and still fails, the escalation should be suppressed regardless of `isAuthRetry`.

**File**: `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`  
Lines: `offerAuthThenDownload()` positive-button fragment-result callback (~line 261), neutral-button handler (~line 268).

---

### BUG-2 — Instagram/Threads: downloaded file is unplayable (High)

> **Correction (research, 2026-05-12)**: the "DASH segment" hypothesis below is wrong. The two logged ExoPlayer errors are `UnrecognizedInputFormatException {contentIsMalformed=false}` (log1 line 359 — bytes are not any recognised container) and `ParserException: "Loading finished before preparation is complete" {contentIsMalformed=true}` (log2 line 272 — truncated download); a real `.m4s`/fMP4 segment would be parsed by `FragmentedMp4Extractor`, and `SEGMENT_EXTENSIONS` already filters those out. The real root cause is the bare re-fetch losing the WebView request context (no `Referer`/`User-Agent`/`Origin`) plus byte-range query params on intercepted Instagram CDN URLs plus zero post-download content validation — see §8 item B.

**Logs**: lines 319–359 in `fastmediasorter_20260512_014007.log`, lines 232–264 in `fastmediasorter_20260512_014152.log`.

**Symptom**: File saved successfully (reported as saved, progress dialog closes, player opens), but player immediately shows error "Неверный или повреждённый медиафайл" or "Воспроизведение не удалось".

**ExoPlayer error** (both logs):
```
androidx.media3.exoplayer.ExoPlaybackException: Source error
Caused by: UnrecognizedInputFormatException: None of the available extractors
(FlvExtractor, FlacExtractor, WavExtractor, FragmentedMp4Extractor, Mp4Extractor, ...)
could read the stream. {contentIsMalformed=false, dataType=1}
```

**Key evidence**:
- Saved filename: `AQMa5TjMAJcjYiUzGOS0SsZBtq7DajRcc-jc9-ZisL5_qbv470XmnCgyAnLYZTZG4LvGA-OdqNIlGe7HxY6CbHD6LsWQT3CS8QhefLc.mp4`
- This is the last path segment of an Instagram CDN URL (`scontent.cdninstagram.com/…`).
- The URL ends in `.mp4` but the content served is not a parseable MP4/AVI/MKV file.

**Root cause — `InvisibleWebViewExtractionStrategy.candidateFor()`**:
The method accepts any URL whose path ends in `.mp4` as a direct download candidate (creates `HtmlMediaCandidate` with `manifest=null`). For Instagram, CDN `.mp4` URLs are token-protected DASH segments or initialization segments — they are NOT standalone playable files. `probeCandidates()` sends HEAD requests to get real `Content-Type` but that info isn't used to reject DASH segments.

**Why the Threads "dress video" worked**:  
Some Threads CDN URLs happen to point to complete short-video files. Instagram CDN URLs for reels use token-authenticated CMAF/fMP4 segments that require the full DASH manifest context. Without DASH assembly, the downloaded `.mp4` segment is a partial/fragmented file ExoPlayer can't play.

**Contrast with failing cases**: `S0151-diag outcome=stream` fires for both (good and bad), meaning the code can't distinguish a complete video file from a DASH segment based on URL alone.

**File**: `app_v2/src/main/java/com/sza/fastmediasorter/data/link/InvisibleWebViewExtractionStrategy.kt`  
Method `candidateFor()` (~line 401), `probeCandidates()` (~line 124), `isAcceptedCandidate()` (~line 149).

---

### BUG-3 — TikTok: cookie host mismatch `vm.tiktok.com` vs `www.tiktok.com` (High)

**Logs**: lines 601–655 in `fastmediasorter_20260512_014152.log`.

**Symptom**: User authenticates TikTok in WebView. Cookies saved as `www.tiktok.com`. URL shared is `https://vm.tiktok.com/ZNRs3Kkty/`. Retry call uses `accountId=null` (because account lookup runs against `vm.tiktok.com` and finds nothing). Coordinator logs `sessionApplied=false`.

**Exact trace**:
```
01:48:18  browser login saved: account=Аккаунт по умолчанию host=www.tiktok.com
01:51:26  ReceiveShareActivity: ... url=https://vm.tiktok.com/ZNRs3Kkty/ accountId=null retry=true
01:51:29  S0151-diag: host=vm.tiktok.com strategy=html sessionApplied=false outcome=not-found
01:51:36  S0151-diag: host=vm.tiktok.com strategy=dynamic sessionApplied=false outcome=social-preview-only
```

**Root cause**: In `ReceiveShareActivity.processLinkAutoDownload` retry lambda:
```kotlin
val host = Uri.parse(retryUrl).host.orEmpty()  // = "vm.tiktok.com"
val newAccountId = authSessionRepository.listAccountsForHost(host)  // finds nothing
    .filter { !it.isDismissed }.maxByOrNull { it.savedAt }?.accountId  // = null
processLinkAutoDownload(retryUrl, newAccountId, isAuthRetry = true)  // accountId=null
```
`listAccountsForHost("vm.tiktok.com")` returns empty because keys are stored under `www.tiktok.com`.
`applySessionContext("vm.tiktok.com", null)` → `cookieStore.loadFor("vm.tiktok.com")` → `pickBestAccount("vm.tiktok.com")` → empty → `sessionApplied=false`.

`vm.tiktok.com` is a short URL redirector. The canonical host for TikTok cookies is always `www.tiktok.com`.

**File**: `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt` (retry lambda ~line 391), `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/EncryptedCookieStore.kt` (no host normalization).

---

### BUG-4 — TikTok: dynamic strategy never finds video (Medium)

**Logs**: all TikTok attempts return `social-preview-only` for dynamic strategy.

**Symptom**: Even when session is properly applied (first attempt: `sessionApplied=true`, 36 cookies including `sessionid`), `InvisibleWebViewExtractionStrategy` returns `social-preview-only`.

**Evidence**:
```
01:44:47  webview-auth blocked-redirect scheme=snssdk1233 host=feed
01:44:47  webview-auth blocked-redirect scheme=snssdk1340 host=feed
01:44:48  webview-auth blocked-redirect scheme=snssdk1233 host=feed
01:44:49  webview-auth blocked-redirect scheme=market host=details
01:47:48  webview-auth blocked-redirect scheme=bytedance host=dispatch_message
```
TikTok's mobile web page immediately tries to redirect to the native TikTok app (`snssdk1233://`, `snssdk1340://`, `bytedance://`) or Play Store (`market://`). The WebView blocks these but TikTok serves a minimal "open in app" page — no video CDN URLs are loaded.

**Additionally**: `html` strategy returns `not-found` for `vm.tiktok.com` (short URL returns 302 redirect to `www.tiktok.com/…` — html extractor may not follow the redirect or doesn't find OG video tags on the redirect target).

**Fix complexity**: HIGH. TikTok requires either: (a) using the TikTok API (requires API key / app registration), or (b) forcing the WebView `User-Agent` to a non-mobile string so TikTok renders full web page, or (c) following the `vm.tiktok.com` redirect and extracting from the actual content URL on `www.tiktok.com`.

**File**: `app_v2/src/main/java/com/sza/fastmediasorter/data/link/InvisibleWebViewExtractionStrategy.kt` (WebView User-Agent ~line 290+).

---

### BUG-5 — Instagram posts: both strategies consistently fail (Medium)

**Logs**: Instagram post `/p/DYNEzGqj4Ll/` (log1) and `/p/DYMdTiBFU8l/` (log2) → both html+dynamic `social-preview-only` with session applied.

**Symptom**: Instagram reel URLs sometimes work (dynamic intercepts CDN URL), but post URLs never work.

**Evidence**: 
- Post `/p/DYNEzGqj4Ll/`: html=social-preview-only, dynamic=social-preview-only (session=9 cookies)
- Post `/p/DYMdTiBFU8l/`: html=social-preview-only, dynamic=social-preview-only (session=14 cookies)
- Reel `/reel/DYN0OH7gVhr/`: html=social-preview-only, dynamic=**stream** → file saved (but BUG-2 means it's unplayable)

**Hypothesis**: Instagram carousel posts (`/p/`) use a different CDN structure where videos are loaded lazily via API (GraphQL), not in initial WebView render. The 22s WebView timeout is enough for Reels (JS hydration + video autoplay starts), but carousel posts never autoplay the video without user scroll interaction.

**This bug intersects with BUG-2**: Even when dynamic "works" for reels, the saved file is unplayable. So the user has zero success rate with Instagram currently.

**File**: `app_v2/src/main/java/com/sza/fastmediasorter/data/link/InvisibleWebViewExtractionStrategy.kt` (DOM_DISCOVERY_SCRIPT ~line 440, WebView setup ~line 290).

---

### BUG-6 — Duplicate cookies in saved session (Low)

**Evidence**: `names=[csrftoken,datr,ig_did,mid,wd,ds_user_id,sessionid,dpr,rur,dpr,csrftoken,ds_user_id,wd,rur]` — 14 cookies, 5 are duplicates.

**Root cause**: `WebViewAuthDialogFragment` collects cookies from `CookieManager.getInstance().getCookie(url)` which may include duplicates if the WebView accumulates cookies from multiple domains/paths. No deduplication before save.

**Impact**: Minor. Doesn't cause auth failures (the first instance of each cookie name is used). But wastes encrypted storage and makes logs harder to read.

**File**: `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthDialogFragment.kt` (cookie collection ~line unknown) or `EncryptedCookieStore.saveForAccount()`.

---

## 4. Priority Order for Implementation

| # | Bug | Severity | Effort | Impact |
|---|-----|----------|--------|--------|
| 1 | BUG-1 Facebook auth loop | Critical | Low | Stops FB download completely |
| 2 | BUG-2 Downloaded file unplayable | High | Medium | Instagram reel download useless |
| 3 | BUG-3 TikTok cookie host mismatch | High | Low | Blocks TikTok retry path |
| 4 | BUG-5 Instagram posts never work | Medium | Medium | Posts = 0% success rate |
| 5 | BUG-4 TikTok dynamic never finds video | Medium | High | TikTok needs separate investigation |
| 6 | BUG-6 Duplicate cookies | Low | Low | Cosmetic |

---

## 5. Fix Approach (Research Findings — NOT implementation)

### BUG-1 Fix

In `ReceiveShareActivity.offerAuthThenDownload()`:
- Positive-button (auth complete) callback: call `processLinkAutoDownload(url, savedAccountId, isAuthRetry=true)` — not `false`.
- Neutral-button (skip): call `cleanupAndFinish()` + toast "Недоступно" — NOT another download attempt.
- Negative-button (dismiss always): `markDismissed(host)` then `cleanupAndFinish()`.

In `processLinkAutoDownload()` escalation block (line ~359–368):  
Add guard: if `authSessionRepository.listAccountsForHost(hostForEscalation).isNotEmpty()` → skip escalation, toast instead. This covers the case where session exists but extraction still fails.

### BUG-2 Fix

In `InvisibleWebViewExtractionStrategy.probeCandidates()` / `isAcceptedCandidate()`:
- After HEAD request, check: if `Content-Type` contains `video/mp4` or `video/…` → accept.
- If HEAD returns `application/octet-stream` or `application/x-…` → flag for further inspection.
- Key addition: check if URL path segment matches `SEGMENT_EXTENSIONS` (`m4s`, `cmf`, `cmfv`, etc.) — these are DASH segments. `SEGMENT_EXTENSIONS` already exists in the companion object but is only used in `isLikelySegment()`.
- Consider: if the CDN URL path doesn't have any recognizable segment extension AND Content-Type HEAD returns video MIME → it's a complete file.
- Alternative: after download, before calling player, do a quick ExoPlayer probe (0.5s timeout) to validate the file. If probe fails, delete file and report failure instead of opening player on a broken file.

### BUG-3 Fix

In `ReceiveShareActivity.processLinkAutoDownload()` retry lambda:
```kotlin
val canonicalHost = KnownAuthResources.matchHost(host)
    ?.let { resource -> 
        // For redirect-shorteners (vm.tiktok.com), use the canonical host from KnownAuthResources
        "www.${resource.host}"
    } ?: host
val newAccountId = authSessionRepository.listAccountsForHost(canonicalHost)...
processLinkAutoDownload(retryUrl, newAccountId, isAuthRetry = true)
```
Better approach: store and lookup cookies by canonical host, not by URL host. Normalize `vm.tiktok.com` → `www.tiktok.com` during session lookup.

### BUG-4 Notes

TikTok investigation needed (separate research):
- Test forcing WebView `User-Agent` to desktop Chrome string to prevent "open in app" page.
- Or: follow `vm.tiktok.com` redirect via HEAD to get the canonical URL, then open that URL in WebView instead.
- The 22s timeout may be sufficient if TikTok actually loads video content.

### BUG-5 Notes

Instagram posts investigation needed:
- Test if increasing DOM_SETTLE_MS beyond 4000 helps (post carousels need user scroll to lazy-load video).
- Test programmatic scroll in DOM_DISCOVERY_SCRIPT to trigger video element creation.
- Consider: Instagram API (`/api/graphql/`) returns video URLs in JSON. The WebView intercepts all sub-requests — the JSON response URL might be capturable and parseable.

### BUG-6 Fix

In `EncryptedCookieStore.saveForAccount()` or at point of collection: deduplicate by cookie name, keeping last-seen value for each name.

---

## 6. Key File Map

| File | Relevant area |
|------|--------------|
| `ui/share/ReceiveShareActivity.kt` | `offerAuthThenDownload()`, `processLinkAutoDownload()` escalation block, retry lambda |
| `ui/share/LinkAutoDownloadResultPresenter.kt` | `presentSocialPreviewOnly()` |
| `data/link/InvisibleWebViewExtractionStrategy.kt` | `candidateFor()`, `isAcceptedCandidate()`, `probeCandidates()`, WebView setup |
| `data/link/auth/KnownAuthResources.kt` | Platform list and host matching |
| `data/link/cookie/EncryptedCookieStore.kt` | `saveForAccount()`, `loadFor()`, host key format |
| `domain/usecase/link/LinkAutoDownloadCoordinator.kt` | `applySessionContext()`, `handle()` |

---

## 7. Out of Scope

- No UI changes in this spec.
- No new platforms added (Facebook may need a dedicated dynamic strategy — tracked separately).
- Facebook HTML extraction failure is a separate deep-dive; this spec only fixes the loop.

---

## 8. Research Addendum (Claude, 2026-05-12)

Code-verified against: `ui/share/ReceiveShareActivity.kt`, `domain/usecase/link/LinkAutoDownloadCoordinator.kt`, `data/link/InvisibleWebViewExtractionStrategy.kt`, `data/link/DirectFileExtractionStrategy.kt`, `data/link/LinkDownloadWriter.kt`, `data/link/cookie/{EncryptedCookieStore,LinkDownloadCookieJar,LinkDownloadSessionContext}.kt`, `data/link/auth/KnownAuthResources.kt`, `ui/share/LinkAutoDownloadResultPresenter.kt`, `ui/share/auth/WebViewAuthDialogFragment.kt`. Build under test (`2.60.5120.138-DEBUG`) may carry uncommitted changes — reconcile the working tree before implementing (see item C).

### A. BUG-1 — confirmed in code; refined root cause

The escalation block in `ReceiveShareActivity.processLinkAutoDownload()` (~lines 354–368) re-shows `offerAuthThenDownload()` whenever `result is NoMediaFound && !isAuthRetry`, host is unknown, and host is not dismissed. Five contributing facts:

- `offerAuthThenDownload()` positive-button callback (line 262) calls `processLinkAutoDownload(url, accountId = savedAccountId)` — `isAuthRetry` defaults to `false`. After the user logs in, the re-attempt is not flagged as a retry → `NoMediaFound` again → escalation fires again → dialog reappears.
- The neutral `[Skip]` and negative `[Don't ask]` buttons also call `processLinkAutoDownload(url, accountId = null)` with `isAuthRetry = false` (lines 270, 275).
- `markDismissed(host)` on the negative button (line 274) runs in a fire-and-forget `lifecycleScope.launch` — races the escalation's `isDismissedForHost()` read, so even `[Don't ask]` can loop once before the record commits.
- The unknown-host escalation has no "session already exists" guard, unlike the known-host path (`LinkAutoDownloadResultPresenter.presentSocialPreviewOnly`, line 122: `[S0166] existing session, extraction still failed — toast only`).
- If the user opens the WebView and closes it without pressing Save, `WebViewAuthDialogFragment` still emits `saved=false / accountId=null` → `processLinkAutoDownload(url, null)` → coordinator's `accountId=auto` path re-applies the OLD stored FB session → `NoMediaFound` → escalate again.

Log evidence: log2 lines 731–838 — `escalating to auth offer` → `auth dialog shown` → `auth dismissed (no record created)` → `blocking link download … accountId=null` → `applying stored session: host=www.facebook.com accountId=auto cookies=11` → repeats ~5×; the user escaped only via the phone Back button.

Recommended fix:

- Make the auth offer one-shot per `ReceiveShareActivity` instance: `private var authOfferShown = false`; set it in `offerAuthThenDownload()`; in the escalation block require `!authOfferShown` in addition to the existing guards. A subsequent `NoMediaFound` after the auth round goes to an honest-failure toast (S0166 §5 «Контент по этой ссылке недоступен»), not another dialog.
- Pass `isAuthRetry = true` from all three `offerAuthThenDownload()` button callbacks — it also correctly gates the presenter's re-auth dialog for the `SocialPreviewOnly` (known-host) path.
- `await` `markDismissed(host)` before calling `processLinkAutoDownload` on the negative button.
- Additionally add a "session already exists" guard to the escalation block (skip if `authSessionRepository.listAccountsForHost(host).any { !it.isDismissed }`) for the case where the user already had a FB session from a previous share.

### B. BUG-2 — corrected root cause; the saved file is broken because the re-fetch is wrong

The dynamic extractor intercepts a CDN media URL (via `WebView.shouldInterceptRequest`, or a DOM `<video>.src`) and hands it to `DirectFileExtractionStrategy.open()`, which issues a bare OkHttp GET on the `@Named("linkDownload")` client with no `Referer`, no matching `User-Agent`, no `Origin`. Instagram/Facebook CDN URLs are signed (`oh=`/`oe=`/`_nc_…`) and gated on `Referer: https://www.instagram.com/`; without it the CDN returns a 403-with-HTML-body, or a redirect to a login page, or — when the intercepted URL carries byte-range params (Instagram uses `bytestart=`/`byteend=`) — only a partial chunk. `DirectFileExtractionStrategy` only treats a clean `401`/`403` as `AuthRequired` (line 89); a `200`-with-garbage or a `200` with `video/mp4` Content-Type but a truncated body passes straight through. `LinkDownloadWriter.writeFromStream` then saves whatever bytes arrive with zero content validation, and `LinkAutoDownloadResultPresenter` immediately launches `StandalonePlayerActivity` on the broken file when `settings.linkAutoDownloadOpenInPlayer` is on (default) → player error toast. The Threads "dress" video worked by luck — that particular Threads CDN URL was unsigned and complete (720×1280, 7.1 s — log2 line 441).

Recommended fix, in priority order:

- (must) Validate the downloaded bytes before declaring success: after `writeFromStream`, sniff the first ~12 bytes (`ftyp`, `RIFF`, EBML `1A 45 DF A3`, JPEG `FF D8`, PNG, GIF, …) and/or inspect the GET response `Content-Type`; if it is not a real media container, or it is `text/html`, or the body is suspiciously small, or `Content-Length` did not match the bytes read → discard the file, return a new `Result.Failed.DownloadCorrupted` (or reuse `NoMediaFound`) → honest toast, do not open the player. This alone converts the worst symptom into S0166 §5 behaviour.
- (should) Replay the WebView request context: capture the WebView `User-Agent` and the page URL; on the re-fetch set `Referer: <pageUrl>` and `Origin: <pageOrigin>`. Add an optional headers parameter to `DirectFileExtractionStrategy.open()`; `InvisibleWebViewExtractionStrategy` passes page-origin headers for intercepted candidates.
- (should) Strip byte-range / segment query params (`bytestart`, `byteend`, `range`, `rn`, `rl`) from intercepted candidate URLs before re-fetching, or send `Range: bytes=0-` and follow through to the full body.
- (nice) If a `.m3u8`/`.mpd` was also intercepted, prefer it over a raw `.mp4` chunk — the streaming pipeline assembles the full stream. Not seen for IG reels in these logs but cheap to add.

### C. BUG-3 — confirmed; broaden to a single canonical-host rule

`EncryptedCookieStore` keys records as `acct:<rawHost>:<accountId>` (`keyForAccount`, line 228). `WebViewAuthDialogFragment` saves under `targetHost` = host of the login URL, which for known resources is `KnownAuthResource.loginUrl` → `www.tiktok.com`, `www.instagram.com`, `www.threads.com`, `www.facebook.com` (lines 51–52, 200–209). But the shared-URL host is `vm.tiktok.com` (short-URL redirector), or `instagram.com` vs `www.instagram.com`, etc. The account lookups in `ReceiveShareActivity` (`AccountSelectionManager`, `enqueueLinkDownloadSilent` line 290, the retry lambda line 390) and `LinkAutoDownloadCoordinator.applySessionContext` (line 38: `cookieStore.loadForAccount(url.host, accountId)`) all key off the raw URL host → miss. S0166 §3 says the storage host is «домен без пути» and `KnownAuthResources.matchHost` already normalises `www.`, but the storage layer ignores all of that. Log evidence: log2 line 643 `blocking link download url=https://vm.tiktok.com/ZNRs3Kkty/ accountId=null retry=true` (retry lambda did `listAccountsForHost("vm.tiktok.com")` → empty → null) → line 650 `S0151-diag: host=vm.tiktok.com strategy=html sessionApplied=false`.

Recommended fix:

- Introduce a single `canonicalHost(host)` helper = `KnownAuthResources.matchHost(host)?.host ?: host.lowercase().removePrefix("www.")`. Use it for the storage key in `EncryptedCookieStore` (save, load, and dismissed records), in `WebViewAuthDialogFragment` (`targetHost`), in `AccountSelectionManager`, in `enqueueLinkDownloadSilent`, in the retry lambda, and in `applySessionContext`.
- Add a one-time migration re-keying existing `acct:www.X:…` / `acct:vm.tiktok.com:…` records to `acct:X:…` where `X` is a known resource host.
- Resolve redirect-shorteners (`vm.tiktok.com`, `vt.tiktok.com`, IG `instagr.am`, FB `fb.watch`, and the `share/p/…` / `/share/…` indirection) to their final URL via a HEAD/GET-with-follow before host detection, session lookup, and extraction — the html strategy on `vm.tiktok.com` returns `not-found` because it does not follow the 302 (log2 lines 612, 650).
- Reconcile the working tree first: log2 line 606 (`encrypted-cookie-store load key=acct:www.tiktok.com:5dfea51f…`) loaded with the `www.tiktok.com` key while the checked-out `applySessionContext` would pass `vm.tiktok.com` — the build under test is likely ahead of `main`.

### D. BUG-4 — confirmed; concrete path worth trying

Logs show TikTok mobile web immediately attempting `snssdk1233://feed`, `snssdk1340://feed`, `bytedance://dispatch_message`, `market://details` (log2 lines 534–537, 600) — the "open in app" interstitial; the WebView blocks the schemes and TikTok serves a minimal stub with no video CDN URLs. Concrete attempt: (a) resolve the `vm.tiktok.com` redirect first (item C), then (b) load the canonical `https://www.tiktok.com/@user/video/<id>` URL in the WebView with a desktop Chrome `User-Agent` (optionally `?lang=en`) — desktop TikTok serves the full SSR page including the `<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__">` (or legacy `SIGI_STATE`) JSON, which contains the playable video URL (`playAddr` / `downloadAddr`). Add a DOM-script (or html-strategy) branch that parses that JSON; the video URL is signed but normally playable with `Referer: https://www.tiktok.com/` (ties into item B fix 2). High effort — document as a separate sub-task, do not block the other fixes on it.

### E. BUG-5 — refine; the actual deliverable is diagnostics

`/p/` posts return `social-preview-only` on both strategies every time (log1 lines 261/268 post `DYNEzGqj4Ll`; log2 lines 337/346 post `DYMdTiBFU8l`); `/reel/` returns `dynamic=stream` (log1 line 319) but that file is unplayable (BUG-2) — so the user currently has a 0 % success rate on Instagram. Two sub-cases the current logging cannot distinguish: (i) the post genuinely has no video (image carousel / text) — then `social-preview-only` → toast is correct and the only fault is the generic toast text; (ii) the post has a video that IG loads lazily via a GraphQL XHR after first paint — the 22 s `HARD_TIMEOUT_MS` + 4 s `DOM_SETTLE_MS` catch a Reel's autoplay but not a feed-post video that needs a scroll/visibility trigger.

Recommended deliverable: enrich the diagnostics on `social-preview-only` / `not-found` (S0166 §5 already calls for «максимум диагностической информации даже в релизной сборке», but it is not implemented) — log `<video>` element count, `og:video` presence, presence of `window.__additionalDataLoaded` / `__APOLLO_STATE__`, page title, whether a login-wall heuristic fired, and the intercepted-request hostnames. A follow-up then decides between bumping `DOM_SETTLE_MS` for IG, a programmatic `window.scrollBy` + click in `DOM_DISCOVERY_SCRIPT`, or intercepting and JSON-parsing the `/graphql/query` / `/api/v1/media/…/info/` XHR response. Do not guess the fix now — get the diagnostics first.

### F. BUG-6 — confirmed; exact location

`WebViewAuthDialogFragment.parseCookieHeader` (line 261) splits the `CookieManager.getInstance().getCookie("https://$host")` string on `;` with no dedup; the browser lists a cookie name once per `(domain, path)` scope (e.g. `csrftoken` at `.instagram.com` and at `www.instagram.com`), producing `names=[csrftoken,datr,ig_did,mid,wd,ds_user_id,sessionid,dpr,rur,dpr,csrftoken,ds_user_id,wd,rur]` — 9 unique, 14 stored (log2 line 215). Harmless (first instance wins) but wasteful and noisy. Fix: in `parseCookieHeader`, `associateBy { it.name }.values` (keep last-seen), or dedup in `EncryptedCookieStore.saveForAccount`.

### G. Cross-cutting S0166 verification gaps surfaced by these logs

Each item below needs a scoping decision — fold into S0170 or open a separate ticket. UI-touching items must go through `/ui-clarify` before implementation.

- Link extraction blocks `ReceiveShareActivity` visibly in the foreground for ~10–30 s per share (html ~3–5 s + dynamic ~6–8 s, up to the 22 s timeout); the activity is foreground for the whole run (see the `App moved to FOREGROUND` / `BACKGROUND` bracketing in both logs). S0166 §2/§3 require «Пользователь этого не видит — WebView работает фоново» and «Приложение немедленно отдаёт фокус обратно вызвавшему приложению». Not implemented — the user stares at a progress dialog. Biggest UX issue after BUG-1.
- Link downloads always land in MediaStore `Downloads` with `reason=NoResourceConfigured` (log1 line 321, log2 lines 234/410) — there is no destination-resource selection in the share-link flow, contrary to S0166 §3 («в выбранный ресурс хранилища»). `settings.linkAutoDownloadResourceId` is apparently unset. If this is an accepted simplification, state it; otherwise it is a gap.
- After a successful share download the app auto-launches `StandalonePlayerActivity` (gated by `linkAutoDownloadOpenInPlayer`). S0166 §3 says «По завершении скачивания — тост с результатом» and «немедленно отдаёт фокус обратно» — it does not say open the player. Auto-launching the player is what makes BUG-2 so visible. UI ambiguity → `/ui-clarify`: is the desired behaviour "save + toast with an Open action" or "save + auto-open player"? Even if auto-open stays, it must be gated behind the BUG-2 validation (never auto-open a file that failed validation).
- Threads is content-dependent: taisiya's post → `stream` / saved / played (log2 lines 408–446), zlata's post → `social-preview-only` / toast (log2 lines 498–501). If zlata's post is image/text-only, the toast is correct and the fix is a clearer message («В этом посте нет видео или галереи для скачивания»), not more extraction work; per S0166 §4 `[Отмена]` semantics, optionally offer «Скачать превью?». Copy/UX point.

### H. Suggested implementation order (augments §4)

1. BUG-1 — one-shot escalation + `isAuthRetry` passing + `await markDismissed` (Critical, Low). Stops the FB loop, the single worst bug.
2. BUG-2 fix 1 — download validation + honest failure (High, Low–Med). Stops "saved a broken file + player error" on every IG reel; biggest correctness win.
3. BUG-2 fixes 2–3 — `Referer`/`User-Agent` replay + strip byte-range params (High, Med). Actually makes IG reels download a playable file.
4. BUG-3 — `canonicalHost` + migration + redirect-shortener resolution (High, Med). Unblocks the TikTok retry path; unifies `www`/bare/short hosts.
5. BUG-5 — diagnostics enrichment (Med, Low). Cheap; unblocks the IG `/p/` investigation; satisfies an existing S0166 §5 requirement.
6. BUG-6 — cookie dedup (Low, Low). Trivial; do it alongside BUG-3.
7. BUG-4 — TikTok desktop-UA + `__UNIVERSAL_DATA__` JSON parse (Med–High). Separate sub-task; document but do not block.

The §G items (foreground blocking, destination selection, auto-open player) need a `/ui-clarify` pass and a scoping decision before entering S0170 or a new ticket.

---

## 9. Implementation Status

### Done (2026-05-12, `/spec-all`) — needs on-device verification

- **BUG-1** — `ReceiveShareActivity`: added `authOfferShown` (one-shot escalation per Activity instance); the unknown-host escalation now also requires `!isAuthRetry && accountId == null && no active session`; all three `offerAuthThenDownload()` button callbacks pass `isAuthRetry = true`; `markDismissed(host)` is awaited before re-running the pipeline on the negative button.
- **BUG-2 fix 1** — `LinkDownloadWriter.writeFromStream()`: after the copy loop the temp file is sniffed (magic bytes for the common containers; `<` → HTML, `{`/`[` → JSON, `< 1024 B` → too-small); on rejection it returns the new `WriteResult.Corrupted`. New `LinkAutoDownloadCoordinator.Result.Failed.DownloadCorrupted`; `LinkAutoDownloadResultPresenter` toasts the new string `link_autodownload_error_corrupted` (EN/RU/UK). `Result.Failed.DownloadCorrupted` is not `NoMediaFound`, so it does not re-trigger the unknown-host escalation, and the player is not opened.
- **BUG-6** — `WebViewAuthDialogFragment.parseCookieHeader()` dedups by cookie name (last-seen wins) before the session is saved.
- Debug probe tags `Timber.d("S0170: …")` at three changed flow entries (result-handling in `ReceiveShareActivity`, corrupted-download rejection in `LinkDownloadWriter`, cookie collection in `WebViewAuthDialogFragment`) — bound to status `BlockNeedUserTest`.
- `standard debug` build green.

### Deferred (follow-up)

- **BUG-2 fixes 2–3** — replay WebView request context (`Referer`/`User-Agent`/`Origin`) on the CDN re-fetch; strip byte-range query params from intercepted candidate URLs. (Makes IG reels actually download a *playable* file — fix 1 only makes the failure honest.)
- **BUG-3** — `canonicalHost()` rule everywhere + storage-key migration + redirect-shortener resolution.
- **BUG-4** — TikTok desktop-UA + `__UNIVERSAL_DATA_FOR_REHYDRATION__` JSON parse.
- **BUG-5** — diagnostics enrichment on `social-preview-only` / `not-found`.
- **§8.G** — foreground blocking / destination selection / auto-open-player — need `/ui-clarify` + a scoping decision.

### On-device test checklist

- [x] **PASS** (build `2.60.5120.227-DEBUG`, `fastmediasorter_20260512_025229.log` §8.A of S0171) Facebook `share/p/…` → auth dialog once, then `NoMediaFound` toast, no repeat. Logcat: `S0170: link share result — host=www.facebook.com result=NoMediaFound … authOfferShown=false` (session present → no new offer). BUG-1 ✅.
- [x] **PASS** (same log) Instagram `/reel/…` → `rejected corrupted download — kind=too-small bytes=200`, toast shown. BUG-2 fix 1 ✅.
- [x] **PASS** (same log) Cookie dedup — TikTok `count=10` distinct names after login. BUG-6 ✅.

---

## Revision History

- **2026-05-12** — by `/spec-update` (`claude-opus-4-7`, focus: completeness, consistency)
  - Applied: added §8 "Research Addendum (Claude)" (items A–H — code-verified root causes for BUG-1…BUG-6, cross-cutting S0166 gaps, suggested implementation order) and a Correction note under BUG-2 (the "DASH segment" hypothesis is wrong; real cause is the bare CDN re-fetch + byte-range params + missing post-download validation). Proposed (DISCUSS): 0.
