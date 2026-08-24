# Play Console Operator Checklist - S0135

> Operator slice of the full standard production gate: `docs/RELEASE_READINESS_STANDARD.md` (verdict via `scripts/release/standard-release-gate.ps1`).

Complete this checklist in Play Console → App content / Store listing before publishing to production.
Check each item only after the change is confirmed in the Console UI.

## A. Listing Texts (all three locales: EN / RU / UK)

Source of truth is `play/listing/<locale>/` - `en-US`, `ru-RU`, `uk-UA`. Publish with
`scripts/release/publish-play-listing.ps1 -Mode commit`; never retype the texts into the Console.

- [ ] Title updated - source: `play/listing/<locale>/title.txt`
- [ ] Short description updated - source: `play/listing/<locale>/short_description.txt`
- [ ] Full description updated - source: `play/listing/<locale>/full_description.txt`
- [ ] Verify EN title reads "Fast Media Sorter & Organizer" (no `Sorter&` typo)
- [ ] Full description still opens with file organizing, before any viewer or player - the
      All files access declaration is reviewed against it (S1989)

## B. What's New

- [ ] EN What's New updated - source: `store_assets/whats_new.txt`
- [ ] RU What's New updated - source: `store_assets/whats_new_ru.txt`
- [ ] UK What's New updated - source: `store_assets/whats_new_uk.txt`
- [ ] No placeholder text `fix and improve` remains

## C. Graphics

- [ ] App icon uploaded: `store_assets/icon_512.png` (512 × 512 px)
- [ ] Feature graphic uploaded: `store_assets/feature_graphic_1024x500.png` (1024 × 500 px)
- [ ] 6 screenshots uploaded for EN locale (slots 1-6 per `design_brief.md` sequence)
- [ ] 6 screenshots uploaded for RU locale
- [ ] 6 screenshots uploaded for UK locale
- [ ] Screenshot slot 1 shows sorting-in-action (NOT the Settings screen)

## C2. Wear OS form factor (S1707)

Everything below the first line is scripted and re-runnable; only the first line needs the web console,
because the Developer API cannot create a form-factor track - an attempt returns
`404 Track not found: wear:production`.

- [ ] Form factor opted in: Test and release -> Advanced settings -> Form factors -> Add form factor -> Wear OS
- [ ] Watch bundle uploaded to the Wear track (versionCode is the phone's minus its last digit, by design):
      `publish-play-release.ps1 -Track 'wear:production' -Aab DOWNLOADS/FastMediaSorter_wear_release.aab -VersionCode <wear code> -NotesVersionCode <phone code>`
- [ ] Wear screenshots live: `play/listing/en-US/images/wearScreenshots/` then `publish-play-listing.ps1 -Mode commit`
- [ ] Watch artifact is on the Wear track, not a mobile one - a wear release left on a mobile track keeps
      serving users but can never be updated

## C3. All files access declaration (S1989)

Source of truth: `store_assets/PLAY_PERMISSIONS_DECLARATION.md`. The Play Developer API exposes no
endpoint for this form, so both fields and the video link are pasted by hand - edit the file first,
never compose the text in the Console. An update was rejected on 2026-08-24 with
`All Files Access Permission policy: Not a core feature` because the submitted text made two claims a
reviewer refutes in a minute.

- [ ] `Usage` = `Core functionality` only; the other five options unchecked
- [ ] Field 1 and field 2 pasted verbatim from `PLAY_PERMISSIONS_DECLARATION.md`, both under 500 chars
- [ ] Evidence video shows a non-media file moved between two user-chosen folders, and nothing else
      - recorded and uploaded, do not re-shoot: **https://youtube.com/shorts/FstAoyHtCXI**
        (1:10, shot on the release build carrying the S1992 fix; source file
        `store_assets/allfiles_evidence.mp4`). What is in it, and the one caveat about the scheduled
        run, are in `PLAY_PERMISSIONS_DECLARATION.md`, section `The take of 2026-08-24`
      - two superseded links must never be pasted again:
        `https://youtube.com/shorts/hxMUsW3354g` went with the **rejected** submission, and
        `https://youtube.com/shorts/vgFAqm0ggGQ` is the first cut of the current footage - 89% of it
        is a still frame, with 29 motionless seconds right after the grant that read as a freeze
- [ ] Store listing already reads as a file organizer (section A) - the two are reviewed as a pair
- [ ] Under a policy enforcement, Play refuses automatic review: send the held edit from
      `Publishing overview -> Send changes for review` together with the declaration

## D. App Category

- [ ] Category reviewed - current: Photography. Decision: _____________
  - Suggested: switch to **Productivity** (broader "file organizer" query coverage, lower competition)
  - `Photography` disagrees with the All files access declaration, whose core functionality is file
    organizing. Not a rejection reason on its own, but it argues against the declaration a reviewer
    reads beside it (S1989)
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
