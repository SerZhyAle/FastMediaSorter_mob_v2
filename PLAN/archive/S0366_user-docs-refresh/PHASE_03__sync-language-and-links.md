# Phase 03 - Synchronize EN/RU/UK mirrors and links

**Ticket:** S0366
**Status:** ✅ Done

**Completed:** 2026-06-06

## Steps

1. Keep EN/RU/UK doc mirrors aligned in structure, section names, and link labels so every language points to the same user path.
   - Verification: `rg -n "^#|^## |^### " docs/README.md docs/README_RU.md docs/README_UK.md docs/QUICK_START.md docs/QUICK_START_RU.md docs/QUICK_START_UK.md docs/HOW_TO.md docs/HOW_TO_RU.md docs/HOW_TO_UK.md` shows the same top-level headings across mirrors. expected: matching section structure | actual: record.

2. Fix broken or stale internal links and update any landing-page links that still point to retired documentation pages.
   - Verification: `rg -n '\]\((?!https?://|mailto:|#)' docs/*.md index*.html nolegal*.html` lists local doc links to verify manually. expected: only valid local links remain | actual: record.

3. Confirm the updated docs still match the documentation map and the current feature inventory.
   - Verification: `rg -n "README|QUICK_START|HOW_TO|FAQ|TROUBLESHOOTING|LIMITATIONS|MODULE_SELECTION|FEATURES" docs/DOCS_MAP.md docs/FEATURES.md` returns the current expected index entries. expected: current links present | actual: record.

## Done criteria

- EN/RU/UK mirrors match the same structure and user path.
- Public links resolve to the current documentation set and do not point to stale pages.

## Step Log

- 2026-06-06 - Synced the EN/RU/UK mirrors for the refreshed flavor matrix, restored missing Quick Start sections, and localized mirror-to-mirror help links.
