# Strategic Specification: S0223 — Instagram Photo Post Download Failure

**Ticket:** S0223
**Status:** Verified
**Priority:** 75
**Date:** 2026-05-16
**Implemented date:** 2026-05-16
**Verified date:** 2026-05-18
<!-- auto-approved by /spec-all — 2026-05-16 -->
<!-- Single-video /p/ outcome relabelled to SingleVideoSaved on 2026-05-18 per audit Option A; verdict flipped Partial -> Verified -->

---

## 1. Problem

Instagram photo posts and carousels (`/p/XXXXX/`, `/p/XXXXX/?img_index=N`) return `outcome=SocialPreviewOnly` even when the user is signed in with a valid session (9 cookies including `sessionid`, `ds_user_id`, `csrftoken`).

Evidence from `logs/fastmediasorter_20260516_045552.log`:

- `/reel/` URLs → `outcome=stream` via ytdlp, file saved — works correctly.
- `/p/` URLs → all three strategies fail:
  - ytdlp: `outcome=not-found` (yt-dlp does not support Instagram image posts)
  - html: `sniffEmbeddedJson unique=0` (Instagram serves zero embedded JSON for `/p/` pages in this scraping context, even with valid session cookies)
  - dynamic: `social-preview-only` (dynamic extractor renders the page but image assets are not harvested)
- Result: `LinkDownloadWorker done result=SocialPreviewOnly` → user sees no images downloaded despite being signed in.

Three photo download attempts in the session: `DYWvX_3jMsh` (carousel, `img_index=4`), `DYTpIrSjHF1` — both failed identically.

---

## 2. Goals

1. Instagram `/p/` post single-image URLs download the full-size image to the configured destination.
2. Instagram `/p/` carousel URLs (with or without `img_index`) download the full carousel OR the specific image at `img_index` — to be resolved in ADR-1.
3. A valid Instagram session (containing `sessionid`, `ds_user_id`, `csrftoken`) is sufficient for extraction without requiring additional auth prompts.
4. Video reels (`/reel/`) continue to work as before — no regression.

**Non-goals:**

- Downloading stories or highlights (different URL schema).
- Downloading content from private accounts (not in the user's followers list).
- Handling login-gated posts for expired sessions — that falls under re-auth flow (separate scope).

---

## 3. Constraints

- Flavor: `noLegal` (primary). Standard if the extraction stack is shared.
- The extraction pipeline for `link-dl` uses `YtDlpExtractionStrategy`, `HtmlExtractionStrategy`, `DynamicExtractionStrategy` — the fix fits into one or more of these, or adds a new Instagram-specific strategy.
- No new BuildConfig gates in `src/main/java/`.
- Session cookies must be passed through the same encrypted cookie store already used for Instagram reels.
- Related verified spec: **S0181** (Threads image carousel scraper — `dynamic-strategy embedded-json-image-present` path). The Threads solution may be directly adaptable since Threads and Instagram share the same cookie infrastructure.
- Related active spec: **S0197** (`threads-ig-data-sjs-extractor`, `BlockNeedUserTest`) — the `sniffEmbeddedJson` path. If S0197 lands a JS-based extractor for IG, it may subsume this spec partially; coordinate.
- Active: **S0190** (`nolegal-youtube-shorts-ytmusic-extraction`) — extraction coordinator; any new IG strategy plugs into the same coordinator chain.

---

## 4. Current Architecture Context

The download pipeline for a shared URL:

1. `ReceiveShareActivity` enqueues `LinkDownloadWorker` with the URL + accountId.
2. `LinkAutoDownloadCoordinator` dispatches through strategies in order: ytdlp → html → dynamic.
3. `YtDlpExtractionStrategy`: calls yt-dlp with session cookies; works for video URLs, returns `not-found` for image posts.
4. `HtmlExtractionStrategy`: fetches the page HTML with session cookies, runs `sniffEmbeddedJson` — returns `unique=0` for IG `/p/` pages (Instagram has removed embedded `__d` JSON from the page source for scraping contexts).
5. `DynamicExtractionStrategy`: renders the page in an embedded view, runs `sniffEmbeddedJson` again after JS execution — returns `social-preview-only` because image CDN URLs in the rendered DOM are not captured by the current harvester.

The Threads path works because Threads renders `data-media-url` attributes into the DOM that the sniffer catches. Instagram's rendered DOM uses `src` attributes on `<img>` tags with CDN URLs, but the sniffer may be filtering them as thumbnails rather than full-size assets.

---

## 5. Proposed Approach

### Option A — Extend DynamicExtractionStrategy for Instagram `/p/` images

- Detect Instagram `/p/` URL pattern in the dynamic extractor.
- After page render, harvest `<img>` tags with CDN URLs (`cdninstagram.com` or `fbcdn.net`) that match the full-size heuristic (width > 600 px from `srcset`, or `_n.jpg` suffix pattern).
- Return as `embedded-json-image-present` result (same code path used by Threads, line 5110 of the log: `bypass-preview-only`).

### Option B — Instagram Graph API via session cookies

- Use the Instagram Graph API endpoint (`/api/v1/media/{shortcode}/info/`) with session cookies injected.
- This is the same endpoint used by most 3rd-party Instagram scrapers with session auth.
- Advantage: structured response with full carousel image URLs at original resolution.
- Risk: Instagram has rate-limiting and may require `x-ig-app-id` header; session cookies alone may not suffice without the CSRF token dance.

### Option C — yt-dlp with `--cookies` for image posts

- Newer versions of yt-dlp (≥ 2024.x) added support for Instagram image posts via the `instagram` extractor when valid session cookies are provided.
- Check if the bundled yt-dlp version supports this; if yes, the `YtDlpExtractionStrategy` may already handle this with a minor configuration change (e.g., disable `--skip-download` flag or pass `--extract-flat false`).

**Chosen approach: Option B** — Instagram private API (`/api/v1/media/{media_id}/info/`) called from `HtmlPageExtractionStrategy` when `sniffEmbeddedJson` yields zero results for an `instagram.com/p/` URL. JSON response parsed by existing `StructuredMediaSniffer.collectThreadPost` (identical `image_versions2` + `carousel_media` schema). Produces `EMBEDDED_JSON` candidates → bypasses `SocialPreviewOnly` guard. Option A ruled out (IMG_TAG source does not bypass the guard); Option C ruled out (yt-dlp 2026.3.17 does not support IG image posts).

---

## 6. Open Questions

1. **Single-image vs. full carousel** — when `img_index` is present in the URL, should the app download only that image or the entire carousel?
   - **Resolution:** Download the entire carousel regardless of `img_index`. Consistent with Threads carousel behavior (S0181). ✅ Owner confirmed 2026-05-16.

2. **yt-dlp version capability for IG image posts** — does the bundled yt-dlp support `/p/` extraction with cookies?
   - **Resolution:** yt-dlp 2026.3.17 `InstagramIE` raises `"There is no video in this post"` for `/p/` URLs — image posts not supported. Option C ruled out. ✅ Resolved via research spike.

3. **Full-size vs. thumbnail disambiguation** in DOM harvesting — how to distinguish CDN full-size URLs from thumbnails?
   - **Resolution:** Moot — chosen approach (Option B: Instagram private API) returns `image_versions2.candidates[]` sorted by resolution; first candidate is always full-size. No DOM parsing required. ✅ Resolved.

---

## 7. Risks

- Instagram changes DOM structure frequently; any DOM-based harvester degrades silently over time. Mitigate: log a warning when zero full-size assets are found from the rendered page even when session is valid.
- Rate limiting from Instagram API (Option B): mitigate with exponential backoff already present in the download stack.
- Carousel download may produce partial results if some images are CDN-expired by the time the download starts; handle via partial-batch completion (same as Threads path).

---

## 8. User Impact

No change to `docs/FEATURES.md` section wording needed — the feature (Instagram download) is already listed. This spec fixes a regression/breakage in the existing feature.

---

## 9. Related Specs

- **S0181** `Verified` — Threads image carousel scraper (solution reusable)
- **S0197** `BlockNeedUserTest` — `sniffEmbeddedJson` extractor for Threads/IG
- **S0190** `BlockNeedUserTest` — YouTube/YtMusic extraction coordinator (shared pipeline)
- **S0182** `BlockNeedUserTest` — sticky UA across download stacks
- **S0151** `Archived` — original IG/Threads extraction and auth

---

## Revision History

- **2026-05-16** — Updated strategic status to `BlockNeedUserTest` to match the catalog after implementation landed and moved to device-verification state.

---

## Last Audit

**Date:** 2026-05-18
**Mode:** strategic
**Flags:** —
**Outcome:** Verified
**Counts:** PASS 6 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Resolution of "Partial" follow-up (2026-05-17 → 2026-05-18)

Owner UX decision (2026-05-18): **Option A** — single-video `/p/` posts saving one file through the legacy fallback writer are functionally complete; the only observable issue was the misleading `FellBackToDownloads` outcome label in trace logs.

Applied this round:

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenter.kt` now relabels the outcome from `FellBackToDownloads` to `SingleVideoSaved` when the saved file's extension matches a video container (`.mp4`/`.webm`/`.mkv`/`.mov`/`.m4v`). No behaviour change — same path, same notification, same destination. The relabel is observable only in `LinkDownloadTrace.tag(...)` output for analytics and future device runs.
- Non-video fallbacks remain labelled `FellBackToDownloads` so genuine "fallback" cases still surface explicitly in trace logs (e.g. ResourceUnavailable or ResourceWriteFailed for non-media artifacts).

### Manual / on-device

- [ ] Optional re-test on a `/p/` single-video post (`DXqwTP1jP0M` from the 2026-05-17 log, or any IG single-video post) to confirm the trace shows `outcome=SingleVideoSaved`. Not a regression risk — the relabel is a pure log-string change.

### Earlier audit summary (kept for context)

**Run:** Device test on Samsung SM-S731B / Android 16 / noLegal-DEBUG `2.60.5162.358`, 2026-05-17 00:31..00:32 (log `logs/fastmediasorter_20260517_003023.log`).
**Verdict:** Partial — IG photo posts now extract via the `ig-api` path (Option B from §5). One of two carousels in the session saved all images; the second fell back to a single-file save.

### Probes confirmed firing

Two IG photo-post share events in this session:

1. `https://www.instagram.com/p/DYaeJFvG8xw/?igsh=...` (lines 559..588):
   - `S0223: ig-api entry shortcode=DYaeJFvG8xw mediaId=3898560993472269424` (line 572).
   - `S0223: ig-api sniff items=1 unique=4` (line 574) — 4 distinct assets discovered.
   - `S0224: LinkDownloadNotification set total=4 success=4` (line 586).
   - `S0202: MainActivity received share result url=... outcome=BatchCompleted notification=true` (line 588).
   - **Outcome: BatchCompleted, 4 files saved.** ✅
2. `https://www.instagram.com/p/DXqwTP1jP0M/?igsh=...` (lines 601..622):
   - `S0223: ig-api entry shortcode=DXqwTP1jP0M mediaId=3885130057467624716` (line 613).
   - `S0223: ig-api sniff items=1 unique=2` (line 615) — 2 distinct assets discovered.
   - `[S0166] real media saved via fallback: file=…mp4 reason=NoResourceConfigured` (line 620) — single video file saved through the generic fallback writer rather than the multi-asset batch path.
   - `S0202: MainActivity received share result url=... outcome=FellBackToDownloads notification=true` (line 622).
   - **Outcome: FellBackToDownloads, 1 file saved.** ⚠

### Analysis

Path #1 confirms the §5 strategic goal: ig-api harvests `image_versions2.candidates[]`, the batch path saves all of them to Downloads, the notification reflects `4/4`. The path works as designed.

Path #2 reveals an unhandled sub-case: `items=1 unique=2` produced one `.mp4` saved through the generic fallback writer, not through the batch path. This URL is a **single-video reel** posted under `/p/` route (not the carousel pattern §1 describes). The ig-api sniff returned 2 candidates: one full-size video, one thumbnail/cover image. The downstream selector picked the .mp4 only and used the legacy fallback path instead of the batch writer.

Two possible interpretations:

- **A** — Acceptable. Single-asset video posts under `/p/` are functionally complete with one file saved. The "FellBackToDownloads" outcome label is misleading but the user gets the right file. Suggested follow-up: rename the outcome to `SingleVideoSaved` for this branch.
- **B** — Bug. The ig-api sniffer reports `unique=2` (video + thumbnail) but the downstream should have saved both as a 2-item batch; the fallback path is wrong for posts that already produced api-level metadata.

Owner UX decision blocks declaring this Verified — leaving status `Partial`.

### Open follow-up

- Disambiguate `/p/` single-video posts from carousels in the ig-api result path. Audit `LinkDownloadCoordinator` (or equivalent) where the decision between `BatchCompleted` and `FellBackToDownloads` is made after a successful ig-api sniff. If the audit shows the legacy fallback consuming the api result without using the batch list, this is a behavioural bug to fix in a follow-up tactical phase.
- Consider removing thumbnails from the ig-api sniff output when the post type is video-only — then `unique=1`, the batch writer takes the single asset, and the outcome correctly becomes `BatchCompleted` (single-item batch).

### Debug verification tags

Tags removed when transitioning out of `BlockNeedUserTest`:

- `Timber.d("S0223: ig-api entry shortcode=… mediaId=… url=…")` — owning file TBD.
- `Timber.d("S0223: ig-api sniff items=… unique=… baseUri=…")` — owning file TBD.

Action: grep `Timber.d("S0223:` across `app_v2/` `.kt` sources and delete; commit with this status change.
