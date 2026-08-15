# Phase 01 - Inventory and baseline audit

**Ticket:** S0366
**Status:** ✅ Done

**Completed:** 2026-06-06

## Steps

1. Build the candidate document list from `docs/DOCS_MAP.md`, `docs/FEATURES.md`, and the public landing pages in the repo root.
   - Verification: `rg -n "README|QUICK_START|HOW_TO|FAQ|TROUBLESHOOTING|LIMITATIONS|MODULE_SELECTION|FEATURES|index.html|nolegal.html" docs/DOCS_MAP.md docs/FEATURES.md index.html index-ru.html index-uk.html nolegal.html nolegal-ru.html nolegal-uk.html` returns the expected public doc set. expected: list of candidate files | actual: record.

2. Compare every candidate page against the current feature inventory in `docs/FEATURES.md` and flag stale wording, old scenarios, and outdated flavor claims.
   - Verification: `rg -n "legacy|old|planned|preview|deprecated|soon|coming soon" docs/README.md docs/QUICK_START.md docs/HOW_TO.md docs/FAQ.md docs/TROUBLESHOOTING.md docs/LIMITATIONS.md docs/MODULE_SELECTION.md index.html index-ru.html index-uk.html` shows the stale claims to fix. expected: candidate lines found | actual: record.

3. Record the audit results in `temp/S0366_doc_audit.md` with file path, issue summary, and proposed fix direction.
   - Verification: `Test-Path temp/S0366_doc_audit.md` returns True. expected: True | actual: record.

## Done criteria

- Candidate documents and stale claims are listed in the audit log.
- Each candidate page has a clear target update path before rewriting starts.

## Step Log

- 2026-06-06 - Candidate user docs and landing pages were enumerated from the docs map, feature inventory, and root landing pages. Audit captured in `temp/S0366_doc_audit.md`.
