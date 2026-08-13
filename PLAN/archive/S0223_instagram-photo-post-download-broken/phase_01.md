# Phase 01 — ytdlp_utils.py: URL-pattern exclusion for IG `/p/`

**File:** `app_v2/src/noLegal/python/ytdlp_utils.py`

## Goal

Prevent yt-dlp from probing Instagram `/p/` URLs. Currently `InstagramIE.suitable()` returns `True`
for these URLs, causing a ~10 s probe + open cycle that always fails with
`"There is no video in this post"`. Reels (`/reel/`) must remain unaffected.

## Steps

- [x] 01.1 — Extract host-exclusion logic from `probe_url` into a new `_is_probe_excluded(url)` helper.
  - Preserve existing `_PROBE_EXCLUDED_HOSTS` set (`threads.com`, `www.threads.com`).
  - Add path-pattern branch: if `host` is `instagram.com` or `www.instagram.com` AND `path` starts with `/p/`, return `True`.
  - Return `False` otherwise.

- [x] 01.2 — Replace the inline host check in `probe_url` with `if _is_probe_excluded(url): return None`.

- [x] 01.3 — Verify: `_is_probe_excluded("https://www.instagram.com/p/ABC/")` → `True`; `_is_probe_excluded("https://www.instagram.com/reel/ABC/")` → `False`; `_is_probe_excluded("https://www.threads.com/post/1")` → `True`.

## Verification

- Read `ytdlp_utils.py`: `_is_probe_excluded` function present.
- `_PROBE_EXCLUDED_HOSTS` still used inside `_is_probe_excluded`.
- `probe_url` contains `if _is_probe_excluded(url): return None` before yt-dlp import.
