---
name: grep-the-symbol-not-the-warning
description: A deprecation-warning inventory undercounts call sites wherever local @Suppress exists - always grep the symbol itself before planning a removal.
metadata:
  type: feedback
---

When planning the removal or migration of a deprecated symbol, never size the caller set from the
compiler-warning list alone - grep the symbol.

**Why:** S1776 (2026-08-21): the S1685 warning inventory showed `EncryptedCookieStore.loadFor` at
one call site (Media3SegmentDownloader). The compiler then failed the deletion with THREE more
live callers (LinkAutoDownloadCoordinator x2, LinkDownloadCookieJar, InvisibleWebViewExtractionStrategy)
- each had a local `@Suppress("DEPRECATION")`, so none produced a warning to count. The research
subagent repeated the undercount because it started from the warning list.

**How to apply:** before writing "N callers" into a spec or tactical plan for any deprecated-API
work, run a plain `grep -rn "\.symbolName("` over the source tree and reconcile it against the
warning list; treat every `@Suppress("DEPRECATION")` hit as a hidden caller. The compiler is the
final arbiter, but finding the callers at planning time instead of at compile time keeps phases
honest.
