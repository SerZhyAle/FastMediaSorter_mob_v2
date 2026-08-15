# Research 03 - Website release rendering

**Strategic item:** §6.3
**Status:** Resolved
**Date:** 2026-06-10

## Question

How should the website download buttons obtain the APK URLs and version: via the GitHub Releases API with a static fallback, or via a stable `/releases/latest/download/<name>` redirect?

## Finding

- The stable redirect `https://github.com/<owner>/<repo>/releases/latest/download/<name>` requires a FIXED asset filename across releases. Our asset names embed the version (`FastMediaSorter-<flavor>-<version>.apk`) because IzzyOnDroid (S0215) globs `FastMediaSorter-standard-*.apk` to find the app. A versionless name would break that glob, and maintaining a second versionless asset set doubles uploads.
- The GitHub Releases API `GET https://api.github.com/repos/<owner>/<repo>/releases/latest` returns `tag_name` plus `assets[]` with `.name` and `.browser_download_url`. Unauthenticated rate limit is 60 requests/hour/IP - ample for a download page (one call per visit, cacheable).
- The site already loads remote JS (marked.js) and fetches repo docs (FEATURES.md) at runtime, so an API fetch fits the existing page architecture.

## Decision

Drive the buttons from the Releases API: fetch latest release, map each public flavor to its `assets[]` entry by name pattern, render a button with the live version label linking to `browser_download_url`. On fetch failure (rate limit / offline), degrade to a static link to the repo `/releases/latest` page. Keep versioned asset names (IzzyOnDroid stays happy); no second versionless asset set required.

noLegal's asset is matched and rendered only on `nolegal*.html`; the main `index*.html` mapping excludes it.

## Impact on plan

- Website phase: a small self-contained JS renderer + a button container per page, reusing existing `styles.css` classes (no hardcoded colors), keyboard-focusable links.
- Owner/repo are already referenced on the page (`SerZhyAle/FastMediaSorter_mob_v2`).
