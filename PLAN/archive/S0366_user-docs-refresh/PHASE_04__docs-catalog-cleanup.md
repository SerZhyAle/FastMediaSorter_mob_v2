# Phase 04 - Docs catalog cleanup

**Ticket:** S0366
**Status:** ✅ Done

**Completed:** 2026-06-06

## Steps

1. Update the documentation map and any affected index pages so the final docs set reflects the refreshed public user path.
   - Verification: `rg -n "README|QUICK_START|HOW_TO|FAQ|TROUBLESHOOTING|LIMITATIONS|MODULE_SELECTION|FEATURES" docs/DOCS_MAP.md` shows the final, current documentation index. expected: current index entries | actual: record.

2. Run the standard post-change documentation log step for every touched doc file and capture the changed file list.
   - Verification: `git diff --name-only -- docs/ README.md QUICK_START.md HOW_TO.md FAQ.md TROUBLESHOOTING.md LIMITATIONS.md MODULE_SELECTION.md FEATURES.md index.html index-ru.html index-uk.html nolegal.html nolegal-ru.html nolegal-uk.html` returns the final document set. expected: final doc list | actual: record.

3. Confirm there are no stale references left in the public documentation set.
   - Verification: `rg -n "TODO|TBD|legacy|deprecated|coming soon|preview" docs/ README.md QUICK_START.md HOW_TO.md FAQ.md TROUBLESHOOTING.md LIMITATIONS.md MODULE_SELECTION.md FEATURES.md index.html index-ru.html index-uk.html nolegal.html nolegal-ru.html nolegal-uk.html` returns no unresolved stale markers. expected: no matches | actual: record.

## Done criteria

- The public documentation set is internally consistent and aligned with the current feature inventory.
- The final docs set is ready for a review pass and implementation follow-up.

## Step Log

- 2026-06-06 - Updated `docs/DOCS_MAP.md`, recorded the post-change dev log for all touched public docs, and finished with `STALE_TERMS_CHECK=PASS`.
