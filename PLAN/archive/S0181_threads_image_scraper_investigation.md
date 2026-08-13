# S0181 — Threads.com / Instagram image and carousel scraper

**Status:** Verified (research-only — findings consumed by S0197 on 2026-05-14).
**Follow-up:** S0197 implements the Tier 2 data-sjs extractor wiring; awaiting device test.

## Problem

Three confirmed failure modes (the underlying mechanism is shared, fix is one
ticket):

1. `threads.com` single image posts download the channel's OG preview image
   (or another unrelated large asset) instead of the actual post image.
2. `threads.com` carousel posts download only the first slide whose `<img>`
   actually mounted at DOM-scan time; remaining slides are never seen.
3. `instagram.com` photo posts (single image **and** carousel) — `result=SocialPreviewOnly`,
   nothing real is saved.

Original observation — log `fastmediasorter_20260513_185117.log` (lines 522..794),
single Threads image:

- yt-dlp `ThreadsIE` extractor recognises only `threads.net`; `threads.com`
  yields `Unsupported URL`. Probe excludes `threads.com` hosts via
  `_PROBE_EXCLUDED_HOSTS` in `ytdlp_utils.py` so the chain falls through.
- `html` strategy: outcome `not-found` — no JSON-LD media payload.
- `dynamic` strategy ([InvisibleWebViewExtractionStrategy.kt](app_v2/src/main/java/com/sza/fastmediasorter/data/link/InvisibleWebViewExtractionStrategy.kt)):
  outcome `FellBackToDownloads`, but the saved file is the channel header / OG
  image, not the post image.

Threads video posts work through the same dynamic strategy. Threads image posts
do not.

Confirmed by device test 2026-05-14 (logs
`fastmediasorter_20260514_004634.log`, `fastmediasorter_20260514_005112.log`):

- Threads carousel `…/post/DYSIu04jaI4` — `dynamic-extractor start` →
  `FellBackToDownloads` with exactly ONE `.webp` saved. Carousel iteration not
  attempted.
- Threads video `…/post/DYStz5hErdB` — single `.mp4` saved (control: video path
  works, single asset is sufficient).
- Instagram photo post `https://www.instagram.com/p/DYSU9o1Mwfk/?…` — yt-dlp
  probe → `DownloadError: [Instagram] DYSU9o1Mwfk: There is no video in this
  post` (correct for a photo) → html strategy `outcome=not-found` → dynamic
  strategy `social-preview-only` → `result=SocialPreviewOnly`. User-visible
  failure: "не удалось получить контент по этой ссылке".

Answer to one of the §Sharpened research questions ("does the current path
actually succeed end-to-end on IG photo posts"): **no, it does not**. IG photo
post hits the same OG-preview-only failure as Threads single image. The fix
must therefore cover IG hosts (`instagram.com`, `www.instagram.com`,
`m.instagram.com`), not just Threads.

## Out of scope for this ticket

No code changes. Research only. Concrete edits go into a follow-up `Sxxxx`.

## Working hypothesis

The post image is already loaded into the WebView as a regular `<img>` element
(this is what makes the in-page "Save image" UI work — it just hands the
already-loaded asset to the browser's save handler). The `dynamic` strategy
already harvests it via `IMG_TAG` / `IMG_SRCSET` in
[DOM_DISCOVERY_SCRIPT](app_v2/src/main/java/com/sza/fastmediasorter/data/link/InvisibleWebViewExtractionStrategy.kt#L581-L642).
The bug is not in extraction — it is in **selection**:
[CandidateSelectionPolicy.choose](app_v2/src/main/java/com/sza/fastmediasorter/data/link/CandidateSelectionPolicy.kt#L11)
returns the first candidate whose probed `Content-Length` is ≥ 1 MiB. On a
Threads post page that bucket contains both the real post image and unrelated
assets (channel header, user avatar at large size, OG preview). Whichever the
HEAD probe sizes first wins.

Carousel posts are a separate issue: Threads lazy-loads the non-first carousel
slides only after the user swipes, so the DOM `<img>` sweep sees only slide 1.
`shouldReturnBatch` requires ≥ 2 candidates each ≥ 1 MiB, which never triggers
for a one-image-in-DOM carousel — the strategy falls through to single-image
selection and saves only the first slide (when selection is right at all).

## Findings from external research (2026)

The canonical, login-free way to get Threads media URLs is to read the
server-side rendered JSON embedded in the page:

- Threads pages contain one or more `<script type="application/json" data-sjs>`
  tags. Together they carry the full hydration state, including post payloads.
- For a post, the relevant subtree contains a `thread_items[].post` object with:
  - `post.image_versions2.candidates[].url` — single image; the `candidates`
    array is sorted from largest to smallest; `[0]` is full-res, `[1]` is the
    common "feed size" fallback used by Scrapfly.
  - `post.carousel_media[]` — array, one entry per slide. Each entry has its own
    `image_versions2.candidates[].url` (and optionally `video_versions[].url`).
  - `post.carousel_media_count` — slide count, even when the DOM only mounted
    the first slide.
  - `post.video_versions[].url` — for video posts (already handled by the
    existing dynamic path via `<video>` and intercepted requests).
- URL hosts are on Meta's image CDN — `*.fbcdn.net` family
  (`instagram.fmla*.fna.fbcdn.net`, `scontent.cdninstagram.com`, etc.) — the
  same CDN as Instagram, which the app already handles in S0171 with the
  desktop Chrome UA + page-origin `Referer` replay (see
  [InvisibleWebViewExtractionStrategy.cdnReplayHeaders](app_v2/src/main/java/com/sza/fastmediasorter/data/link/InvisibleWebViewExtractionStrategy.kt#L145)).
- Public posts on both `threads.net` and `threads.com` serve the `data-sjs`
  payload without authentication.
- There is no public Meta Graph API for reading arbitrary posts — only
  publishing — so the `data-sjs` route is the only stable option.

References:

- Scrapfly, "How to scrape Threads by Meta using Python (2026 Update)" —
  documents the `data-sjs` script tag selector and the JMESPath
  `post.carousel_media[].image_versions2.candidates[1].url`.
- Zeeshanahmad4/Threads-Scraper and Apify Threads scrapers — same approach.

## Sharpened research questions

Single image post:

- For a known-failing `threads.com` image post, what does the `dynamic`
  strategy's candidate list look like just before
  `CandidateSelectionPolicy.choose`? Specifically: how many `IMG_TAG` /
  `IMG_SRCSET` / `OG_IMAGE` candidates, what are their hosts and probed sizes?
- Is the real post image always among them, or is it ever missed (lazy `<img>`
  loaded only on viewport scroll)?
- Which URL pattern reliably identifies a post image on the Threads CDN
  (`/v/t51.*` path prefix? specific subdomain shape?) — is it sharp enough to
  rerank deterministically without parsing the JSON?
- Does the OG image URL share host with the post image (both `fbcdn.net`) so
  host-only filtering cannot distinguish them?

Carousel:

- Capture a carousel post HTML snapshot via the existing DEBUG HTML dump
  (`dumpPageHtml`, `link_debug_<host>_<ts>.html` in external app storage) and
  confirm the `data-sjs` script contains `carousel_media` with all slides.
- Are carousel slide URLs reachable directly with the same `Referer` /
  desktop-UA combination already used in `cdnReplayHeaders`, or do they need a
  separate cookie / session?
- For the batch path (`OpenResult.Batch`) — what is the user-visible flow when
  it triggers today (single download vs gallery import vs batch picker)? The
  proper deliverable for a carousel is "all N slides", not "best slide".

Shared (Threads + IG):

- Instagram photo posts: does the current path actually succeed end-to-end on
  IG photo posts, or only on IG video posts? If it succeeds on IG photos, what
  picks the right image there — is it the same `IMG_TAG` rank-by-size, or is
  some IG-specific path already in place?
- Is the existing
  [StructuredMediaSniffer](app_v2/src/main/java/com/sza/fastmediasorter/data/link/StructuredMediaSniffer.kt)
  the right place to add a `data-sjs` parser, or does it belong as a new
  Threads/IG-specific sniffer plugged into the `dynamic` path?

## Investigation steps

Diagnostic (no behaviour change):

- In `InvisibleWebViewExtractionStrategy.open` — log the full candidate list
  just before
  [CandidateSelectionPolicy.choose](app_v2/src/main/java/com/sza/fastmediasorter/data/link/InvisibleWebViewExtractionStrategy.kt#L122)
  (URL, source, probed size, probed MIME). Verbose-only, host-gated to
  `threads.*` and `fbcdn.net` so it doesn't drown other sites.
- Reuse the existing DEBUG HTML dump path
  ([dumpPageHtml](app_v2/src/main/java/com/sza/fastmediasorter/data/link/InvisibleWebViewExtractionStrategy.kt#L535))
  — force a dump for Threads hosts even when DOM candidates are non-empty so
  the `data-sjs` payload is captured for offline analysis.
- Run a manual share of:
  - one known-failing `threads.com` single-image post,
  - one known-failing `threads.com` carousel post,
  - one working `threads.com` video post (control).
  Collect logcat + the file the app saved + the dumped HTML.

Offline analysis (against the dumped HTML):

- Confirm `<script type="application/json" data-sjs>` exists and that
  `post.image_versions2.candidates[].url` resolves the expected post image.
- Confirm `post.carousel_media[]` resolves the full carousel for a multi-image
  post.
- Decide whether the right `candidates[]` index is `[0]` (largest) or `[1]`
  (Scrapfly choice — usually 1080-wide and avoids the "original" entry that
  occasionally 404s on cold edge nodes).

Codebase touch-points the eventual fix will hit:

- `app_v2/.../data/link/InvisibleWebViewExtractionStrategy.kt` — DOM
  discovery, post-discovery selection, batch decision.
- `app_v2/.../data/link/CandidateSelectionPolicy.kt` — current size-first
  ranking; needs a site-aware override or a source-priority bump for
  Threads/IG `IMG_TAG` matched against the `data-sjs` URL set.
- `app_v2/.../data/link/StructuredMediaSniffer.kt` — natural home for a
  `data-sjs` parser if we go the JSON-extraction route.
- `app_v2/.../data/link/HtmlPageExtractionStrategy.kt` — the cheaper `html`
  path could short-circuit Threads if the JSON is present without needing the
  full WebView render.

## Proposed remediation tiers

Two follow-up tickets are likely, chosen after the dumped HTML is examined:

Tier 1 — selection-only fix (small, low risk):

- Rerank `IMG_TAG` / `IMG_SRCSET` candidates whose host matches the Threads /
  IG CDN family above non-CDN images. Effectively: when multiple `IMG_TAG`
  candidates exist and at least one is on `*.fbcdn.net` / `cdninstagram.com`,
  prefer it over `OG_IMAGE` and over images on other hosts.
- Strictly worse on weird edge cases than parsing the JSON, but fixes the
  common single-image case without a parser. Useful as a "stop the bleeding"
  patch.

Tier 2 — `data-sjs` JSON extraction (correct fix, also unlocks carousel):

- Parse the embedded JSON, walk to `post.image_versions2.candidates[*].url`
  and `post.carousel_media[*].image_versions2.candidates[*].url`, emit one
  candidate per slide with `source = JSON_LD` (or a new `EMBEDDED_JSON`
  source) so the selection policy treats them as trusted and the batch path
  triggers automatically for ≥ 2 slides.
- Lives in `StructuredMediaSniffer` or a dedicated `ThreadsJsonSniffer`,
  plugged into both `html` and `dynamic` paths so the `html` strategy can
  succeed without the WebView render when the JSON is in the initial HTML
  response.

## Related

- S0156 — `noLegal` flavor link-download feature umbrella.
- S0174 — yt-dlp integration; explains why `threads.com` is in
  `_PROBE_EXCLUDED_HOSTS`.
- S0171 — Instagram / Threads CDN replay headers (desktop Chrome UA,
  `Referer`); the fix here piggy-backs on that mechanism.
- S0156 §6.9 — `noLegal`-only docs live in `docs/FEATURES_noLegal*.md`.

---

## Last Audit

**Run:** /spec-all → F5 audit, 2026-05-14 18:33.
**Verdict:** Verified — research-only deliverable produced and consumed downstream.

### Acceptance basis

S0181 is a research-investigation ticket (§Out of scope: "No code changes. Research only. Concrete edits go into a follow-up Sxxxx."). The deliverable is the research artifact itself: failure-mode catalogue, working hypothesis, codebase touch-points, proposed remediation tiers.

### Evidence of consumption

- S0197 (`PLAN/S0197_threads-ig-data-sjs-extractor.md` §10) explicitly cites S0181 as source: *"research-ticket, источник findings и подтверждённый failure-репорт. После реализации S0197 переходит в Implemented (его исследовательские выводы консумированы)."*
- S0197 implements the Tier 2 `data-sjs` JSON extraction path described in §"Proposed remediation tiers" — that is the direct downstream of this research.
- S0197 currently `BlockNeedUserTest`; its eventual verification is orthogonal to S0181's deliverable status — S0181's job (producing the findings) is independent of whether those findings later prove correct in production.

### Debug verification tags

- Grep `Timber.d("S0181:` across `app_v2/**/*.kt` → no matches. Consistent with this spec never having entered `BlockNeedUserTest` (research-only, no on-device probe required).
