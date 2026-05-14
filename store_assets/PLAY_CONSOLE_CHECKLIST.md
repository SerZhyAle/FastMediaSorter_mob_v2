# Play Console Operator Checklist — S0135

Complete this checklist in Play Console → App content / Store listing before publishing to production.
Check each item only after the change is confirmed in the Console UI.

## A. Listing Texts (all three locales: EN / RU / UK)

- [ ] Title updated — source: `store_assets/play_store_description_{locale}.txt` line 2
- [ ] Short description updated — source: same file, line 5
- [ ] Full description updated — source: same file, lines 8+
- [ ] Verify EN title reads "Fast Media Sorter & Organizer" (no `Sorter&` typo)

## B. What's New

- [ ] EN What's New updated — source: `store_assets/whats_new.txt`
- [ ] RU What's New updated — source: `store_assets/whats_new_ru.txt`
- [ ] UK What's New updated — source: `store_assets/whats_new_uk.txt`
- [ ] No placeholder text `fix and improve` remains

## C. Graphics

- [ ] App icon uploaded: `store_assets/icon_512.png` (512 × 512 px)
- [ ] Feature graphic uploaded: `store_assets/feature_graphic_1024x500.png` (1024 × 500 px)
- [ ] 6 screenshots uploaded for EN locale (slots 1–6 per `design_brief.md` sequence)
- [ ] 6 screenshots uploaded for RU locale
- [ ] 6 screenshots uploaded for UK locale
- [ ] Screenshot slot 1 shows sorting-in-action (NOT the Settings screen)

## D. App Category

- [ ] Category reviewed — current: Photography. Decision: _____________
  - Suggested: switch to **Productivity** (broader "file organizer" query coverage, lower competition)
  - Research: check competitor categories for Slidebox, Photo Cleaner, Magic Cleaner before deciding
  - Record final decision in strategic spec §6.2

## E. Developer Account (optional)

- [ ] Developer name decision recorded in strategic spec §6.3 (keep personal / register studio)
- [ ] If changing: confirm Google Play name-change requirements do not reset install/review count

## F. Post-Publish Monitoring (first 14 days)

- [ ] Monitor Play Console Acquisition → Store Listing Conversion Rate
- [ ] Monitor organic installs vs. pre-publish baseline
- [ ] If conversion drops >20% from baseline: prepare rollback copy set

## Checklist completed by: ___________________  Date: ___________
