# Play Console Operator Checklist - S0135

> Operator slice of the full standard production gate: `docs/RELEASE_READINESS_STANDARD.md` (verdict via `scripts/release/standard-release-gate.ps1`).

Complete this checklist in Play Console → App content / Store listing before publishing to production.
Check each item only after the change is confirmed in the Console UI.

## A. Listing Texts (every locale the app declares)

Source of truth is `play/listing/<locale>/`, one folder per language declared in
`app_v2/src/main/res/xml/locales_config.xml` - thirteen of them since S2340, not the three this
checklist used to name. Publish with `scripts/release/publish-play-listing.ps1 -Mode commit`; never
retype the texts into the Console.

The boxes below are per locale and the publisher pushes all of them in one edit, so tick each line
once the Console shows the set complete. Ten of the thirteen carry text only and inherit their
graphics from the default language, which is why section C still lists screenshots for three locales
and not for thirteen.

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
- [ ] Phone artifact and watch artifact are **not** in the same review batch. Managed publishing groups
      everything unsent into one batch, and a rejection of either takes the whole batch with it. On
      2026-08-25 the two travelled together; the watch verdict of 2026-08-31 held the phone release
      that had been live since 2026-08-15, with not one claim against the phone (S2272, ADR-1).
      Nothing enforces this mechanically - the Developer API exposes no review state at all - so this
      line is the enforcement. Current publication state and the ordered recovery plan:
      `docs/PLAY_PUBLISHING_STATE.md`
- [ ] `Publishing overview -> unsent changes` read immediately before sending, and it contains nothing
      from the watch. Measured 2026-09-01: while a rejection stands, `edits().commit()` refuses
      automatic review (HTTP 400) and only `changesNotSentForReview=True` succeeds, so **every** API
      edit queues into that list and rides out in the batch you send by hand. Building the two
      artifacts separately does not keep them apart - this list does

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

## C4. Data safety - location (S2083)

Source of truth: `store_assets/PLAY_DATA_SAFETY_LOCATION.md`. Like C3 this form has no Play Developer
API endpoint, so it is answered by hand - decide in the file, never in the console. The form is filled
**per form factor**: the Wear answer is not inherited from the phone.

The answer is settled for both form factors as of 2026-09-01: **location is not collected and not
shared anywhere.** Every row below is unchecked, and the file above holds the evidence for each one.

- [ ] Wear OS form factor: `Approximate location`, `Precise location` and background all **unchecked**
      - the watch declares no location permission at all, so this one is a statement of fact
- [ ] Phone form factor: `Approximate location`, `Precise location` and background all **unchecked**
      - nothing retains a fix off the device. The two network paths that touch a coordinate are both
        quantized to a map tile and retain nothing, which is the ephemeral-processing exemption
        Google states on its own Data safety page
- [ ] Background location unchecked on the phone too - `ACCESS_BACKGROUND_LOCATION` is declared in
      neither module
- [ ] Before answering the phone rows, confirm `GoogleMapsLiveFrameView.kt` still denies the page's
      geolocation and still coarsens the coordinate (S2292). Until that fix landed, the Google Maps
      Live Frame gadget (S2241) auto-granted an embedded Google page its own geolocation and passed a
      full-precision coordinate, which would have forced a `Sharing` declaration instead
- [ ] Store description names why location is asked for (section A) - the form and the description are
      reviewed as a pair, exactly as in C3
- [ ] If a location row was previously checked, changing it changes the published label - expect
      review, and send the held edit from `Publishing overview -> Send changes for review`

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

## G. Technical quality thresholds - February 2027 (S2100)

Play enforces memory, bitmap and DEX thresholds from February 2027; exceeding one costs Play
visibility and restricts publishing. All four surfaces below read **only after a bundle upload** -
no local check substitutes for them, which is why they are an operator step and not a build gate.

- [ ] Android vitals → **Dynamic memory metrics** - record the percentiles per RAM bucket
- [ ] Crashes and ANRs → **out-of-memory filter** - record OS kills for low memory
- [ ] Bundle upload → **DEX code optimization insights** - record the achieved optimization %
- [ ] **Proactive alerts** - note which fired, if any: unoptimized bitmaps, weak DEX optimization,
      limited split-bundle usage
- [ ] Copy every figure above into `dev/PLAY_QUALITY_THRESHOLDS_2027.md` section 3, with the date
      and the release it came from. Numbers left in the console are numbers the next ticket
      re-gathers from zero - and S1157 is blocked waiting on the DEX percentage specifically.

Two things not to go looking for (S2449 - both settled before the upload, neither needs re-deriving):

- **There is no Wear breakdown under the memory surfaces, and its absence is correct.** Google scopes
  both memory metrics to mobile and tablet form factors, so the watch is not judged by these
  thresholds at all - and `:wear` ships its own bundle to `wear:production` rather than riding inside
  the phone bundle. Quotes and read date: `dev/PLAY_QUALITY_THRESHOLDS_2027.md` section 1.4.
- **A fired "limited split-bundle usage" alert is recorded, not acted on.** This project declares no
  feature module and its configuration splits are already on by AGP default, so no delivery change
  exists that would satisfy such an alert. Note whether it fired and move on: that observation is the
  whole of S2449's residual. Detail: `dev/PLAY_QUALITY_THRESHOLDS_2027.md` section 3.1.

## Checklist completed by: ___________________  Date: ___________
