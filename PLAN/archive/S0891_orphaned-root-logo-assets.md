# S0891 - Remove orphaned repo-root logo assets

**Ticket:** S0891
**Status:** Archived
**Priority:** 25
**Date:** 2026-07-02
**Tier:** 1 - Trivial (ad-hoc)

<!-- discovered by /spec-all S0815 - 2026-07-02 (docs/site audit spinoff) -->

## 0. Raw capture (inbox)

**Captured:** 2026-07-02, during S0815 docs/site pipeline research (research/02).

`logo-dark.png` and `logo-light.png` at the repo root appear to be referenced nowhere in the tracked repository - a repo-wide search for `logo-dark` / `logo-light` across `.html` / `.css` / `.md` returned zero matches. Candidate dead-weight (CLAUDE.md Rule 20).

## 1. Problem / symptom

Two root PNG assets with no inbound reference from any site page, stylesheet, or doc. Ship weight + repo clutter; no functional impact.

## 2. Before deleting - verify truly unused

Do NOT delete blind. Confirm no reference from:
- `index*.html` / `nolegal*.html`, `styles.css`, all `docs/**`.
- The Jekyll config `_config.yml` and `.github/workflows/jekyll-gh-pages.yml` (e.g. an og:image / social-preview meta).
- Any manifest / external social-card usage that references the raw GitHub URL (grep the raw path).

If a reference exists (e.g. a social-preview meta not caught by the first grep), keep the file and instead document its use.

## 3. Action

- If confirmed orphaned: delete both PNGs; run the site build path check (`docs/**` push still renders); note the removal.

## Related

- S0815 (docs/site icon work - surfaced this during pipeline research).

## Last Audit

**Date:** 2026-07-03
**Verdict:** Verified

- §2 checklist executed: repo-wide grep `logo-dark|logo-light` over the tracked tree (covers `index*.html`, `nolegal*.html`, `styles.css`, `docs/**`, raw-URL references) - expected: 0 refs | actual: 0; `_config.yml` + `.github/workflows/*.yml` grep `logo` - 0 refs (no og:image / social-preview meta).
- Confirmed orphaned -> both PNGs deleted (1251 + 1651 bytes); `git status` shows the two deletions, nothing else touched.
- Site render unaffected: zero inbound references means the Jekyll build never consumed them; local Jekyll run not available - risk residual only if an EXTERNAL service hotlinked the raw GitHub URL (not verifiable from the repo, deemed unlikely for 1-2 KB placeholder logos).
