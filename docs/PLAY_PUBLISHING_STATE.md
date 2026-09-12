# Play publishing state

The single record of what Google Play is currently publishing for `com.sza.fastmediasorter`: what the
store serves, what each track holds, what the Console's `Policy status` page lists, and how the
published builds behave on users' devices (Android vitals, block 4, S2917). Before this
file existed the answer was derived on demand from ticket status notes and from memory - three times
in one week, and once wrongly (S2272, ADR-2).

**The rule every row obeys.** Each row carries its own measurement date and its own source class. A
row with no date is read as **unmeasured**, never as unchanged. Two source classes exist:

- `measured` - written by a script, from the Play Developer API or from an anonymous page fetch.
- `transcribed` - typed in by the owner from a Console screen no API serves.

The four blocks are deliberately kept apart because they disagree in practice. The console can report
a track `completed` while the store still serves a two-week-old build, and a record holding only one
of those two rows reads as green.

---

## 1. Public serve - `measured`

What an anonymous visitor actually receives. Produced by
`pwsh -NoProfile -File scripts/release/read-play-public-serve.ps1 -Json`.

<!-- s2272:measured:public-serve:begin -->

| Served version | Store `Updated on` | Detected by | Measured (UTC) |
|----------------|--------------------|-------------|----------------|
| `2.60.9021.951` | Sep 2, 2026 | data-callback key 141 | 2026-09-12 |

Reader exit code: 0.

<!-- s2272:measured:public-serve:end -->

---

## 2. Track state - `measured`

What each track holds. Produced by
`pwsh -NoProfile -File scripts/release/read-play-tracks.ps1 -Json`.

A track's `completed` status says only that a rollout was configured. It is **not** evidence that the
release is publicly served, and it is **not** evidence that it passed review - a rejected release
keeps reporting `completed`, so review state is invisible from here and from the API entirely.

<!-- s2272:measured:tracks:begin -->

| Track | versionName | versionCode | Status | Measured (UTC) |
|-------|-------------|-------------|--------|----------------|
| `production` | `2.60.9121.346` | `260912134` | completed | 2026-09-12 |
| `beta` | - | - | no release | 2026-09-12 |
| `alpha` | - | - | no release | 2026-09-12 |
| `internal` | `2.60.6222.324` | `260622232` | completed | 2026-09-12 |
| `wear:beta` | - | - | no release | 2026-09-12 |
| `wear:internal` | - | - | no release | 2026-09-12 |
| `wear:production` | `2.60.9021.951` | `26090503` | completed | 2026-09-12 |

Reader exit code: 0.

<!-- s2272:measured:tracks:end -->

**Drift 2026-09-11 -> 2026-09-12: the phone release v2.60.9121.346 went up.** `production` moved from
`260902195` to `260912134` / `2.60.9121.346`, uploaded and committed by
`scripts/release/publish-play-release.ps1` on 2026-09-12 and **held**: Play refuses automatic review
for this app, so the uploader committed with changes held and the owner sent them for review from the
Console the same day. Block 1 still serves `2.60.9021.951` - that is the expected shape while a
release is in review, not a disagreement of the kind recorded through 2026-08-31. `wear:production`
and `internal` did not move.

**Drift 2026-09-09 -> 2026-09-10** - kept here, outside the marked region, because
the refresher overwrites what is inside it. Measured 2026-09-10 (S2294): **no row moved.** Every
versionCode, versionName and status is identical to the 2026-09-09 measurement; only the measurement
dates advanced. Two consequences worth stating rather than re-deriving:

- Blocks 1 and 2 now **agree** for the first time in this record: `production` holds `260902195` /
  `2.60.9021.951` and the store serves that same `2.60.9021.951`. The two-week disagreement recorded
  through 2026-08-31 (track ahead of store) is closed.
- `internal` still holds the stale `2.60.6222.324` / `260622232` from 2026-06-22, `completed`. Step 2
  of the recovery plan has not been executed.

**Drift 2026-08-31 -> 2026-09-09**, kept for the chain: `production` went from `2.60.8250.134` /
`260825013` to `2.60.8241.708` / `260824170` (the rejected release off the track, the previous one
back), then to today's `260902195`; `wear:internal` held a draft on 2026-08-31 and has held nothing
since.

---

## 3. Policy status - `transcribed`

<!-- s2272:transcribed:policy-status -->

**No script writes this block.** The Play Developer API exposes tracks, edits and listings; it exposes
no policy or rejection surface at all. This block is transcribed by the owner from
`Policy status` in the Console, so it can only be aged, never refreshed. `refresh-play-publishing-state.ps1`
reports how old it is and changes nothing in it.

**Transcribed:** 2026-08-31, from the owner's screenshot.

`Update rejected` - four rows, all dated 2026-08-31, all Wear:

| Row | Claim | Carrier |
|-----|-------|---------|
| 1 | `Wear App Quality Guidelines: Wear app functionality not working as described` | S2275 |
| 2 | `Wear App Quality Guidelines: Wear app functionality not working as described` (duplicate line) | S2275 |
| 3 | `Wear App Quality Guidelines: Missing app icon in splash screen` | S2274 |
| 4 | `Wear App Quality Guidelines: Watch shapes` | S2273 |

**Transcribed:** 2026-09-09, from the owner's Play verdict letter of 2026-09-08 (quoted verbatim in
S2756 section 0), Routing ID `ZLFS`, versionCode `26090503`.

`Update rejected` - one row, dated 2026-09-08, Wear:

| Row | Claim | Carrier |
|-----|-------|---------|
| 1 | `Issue found: Wear app functionality not working as described` / `The functionality of your app doesn't work as described.` | S2755, S2756 |

This is a **separate verdict on a separate artifact**, not a restatement of the rows above: the
2026-08-31 batch carried versionCode `26082501` (block 2, `wear:production`), this letter carries
`26090503` and its own Routing ID. Both entries therefore stand, each under its own date, exactly as
the rule at the top of this file requires.

**This entry is the letter, not the page.** `Policy status` in the Console has not been re-read
since 2026-08-31, so nothing here says whether the four August rows are still listed there. A letter
reports one submission's verdict; the page reports the app's standing claims. The next screenshot
transcription is what reconciles them - until then, treat the August table as unmeasured rather than
as current.

The claim is carried from both sides, per the owner's decision of 2026-09-08 recorded in S2756: the
watch behaviour by **S2755** (text cut off at large font), the listing text by **S2756** (the store
description now matches what the watch build actually implements).

`App updates with these issues will be rejected`:

- `App must target Android 16 (API level 36) or higher` - `Enforced Aug 31, 2026`, warning sent
  2026-07-21. This has stopped being a warning: it is now a state in which updates are rejected.
  `app_v2/build.gradle.kts` already sets `targetSdk = 36`, so the requirement is met by the code and
  broken by what is still sitting on a track.

**Absent as of this transcription: `All Files Access`.** The `Not a core feature` row behind the
rejections of 2026-08-23 and 2026-08-24 is gone. The build that removed `MANAGE_EXTERNAL_STORAGE`
from the store flavors and `ACCESS_FINE_LOCATION` from the watch (S2012, S2013), plus the accepted
appeal, closed it. Recorded because an absence measured once is worth more than the same question
researched again.

---

## 4. Android vitals - `measured`

How the published builds behave on users' devices, read from the Play Developer Reporting API.
Produced by `pwsh -NoProfile -File scripts/release/watch-play-vitals.ps1` (`.\a.ps1 pv`), which is
the only writer of this block and the only script that reads that API.

- The window is the last 28 days the API has data for, in `America/Los_Angeles` dates - the only
  time zone its daily figures exist in - and the rates are the user-perceived 28-day values Google
  judges bad behaviour by.
- The bands are Google's published thresholds, kept in the `PlayVitals` block of
  `scripts/devtest/prerelease.config.psd1` with the date they were read.
- `insufficient data` is a result, not a failure: too few users for a rate to mean anything.
- A red band files a Draft ticket; the ticket's section 0 carries the evidence.
- A run whose read failed writes nothing, so a date in this block is always a date of real data.

<!-- s2272:measured:vitals:begin -->

No measurement yet - the Reporting API was not enabled on the service account's Cloud project when
this block was created (2026-09-11). The reader names the activation URL.

<!-- s2272:measured:vitals:end -->

---

## Recovery plan

Ordered. Every step names who can execute it, because most of this cannot be done from the
repository: splitting a review batch, removing a track release and submitting for review have no
scripted path, and nothing here can cancel a submission or dismiss a policy row.

### Step 1 - Split the phone and the watch into separate review batches

**Executor:** owner (Play Console).
**Proof:** the phone artifact is sent for review with no watch artifact in the same batch.

Managed publishing groups everything unsent into one batch. On 2026-08-25 the phone and the watch
travelled together, and the watch verdict of 2026-08-31 took the phone release with it. This is first
because every later step depends on it.

**Where the batch is actually formed, measured 2026-09-01.** While a rejection stands on the app,
`edits().commit()` returns HTTP 400 `Changes cannot be sent for review automatically` and only
`changesNotSentForReview=True` succeeds. Every API edit therefore lands in `Publishing overview` as an
unsent change and rides out in whatever batch the owner sends by hand. ADR-1 is not satisfied by
building the two artifacts separately - it is satisfied by there being nothing from the watch among
the unsent changes at the moment the batch is sent. Read that list before sending, every time.

**Cancelling a release does not empty a track; it rolls it back.** After the cancellation of
2026-09-01, `production` did not go empty - it returned to the 2026-08-24 build, which was itself
rejected. "Release cancelled" and "track clean" are different states, so criterion 2 cannot be checked
by the fact that a cancellation happened.

**Executed - the outcome is measured, the sequence is not (2026-09-10, S2294).** The phone artifact
`260902195` reached `production` and passed review on 2026-09-02; the watch artifact `260909148`
reached `wear:production` on 2026-09-09 as its own submission seven days later. Two artifacts on two
dates with two verdicts is what a split batch looks like from the outside, and it is the whole purpose
of this step, so the step is done. What is **not** recorded is the sequence in `Publishing overview`
that produced the split - that is S2294's open question 1, and only an owner transcription answers it.
Absent that, a future split is repeated from the outcome, not from a written procedure.

### Step 2 - Clear `App must target Android 16`

**Executor:** owner (Play Console).
**Proof:** the row is absent from `Policy status` on the next transcription into block 3.

Candidate: the stale `internal` release at versionCode `260622232` (`2.60.6222.324`) from 2026-06-22,
still `completed` as of the measurement in block 2. Confirm what the Console attributes the row to
before removing anything, then re-transcribe and check the row is gone. S1991's decision to leave
tracks alone until a verdict arrived is spent - the verdict came, and there is no active review left
to disturb.

**That candidate is now the only one there can be, measured 2026-09-01 (S2294).** `targetSdk = 36` is
set in every module of the tree - `app_v2`, `wear`, `watchface` - and no flavor overrides it; `legacy`
moves `minSdk` only. The 35 -> 36 bump landed with S1149, closed 2026-07-22. The versionCode scheme is
`YYMMDDHHm` (`app_v2/build.gradle.kts:288`), which dates every live track release: `internal`
`260622232` is 2026-06-22, a month **before** the bump, while `production` `260824170` (2026-08-24)
and `wear:production` `26082501` (2026-08-25) both postdate it. `beta`, `alpha`, `wear:beta` and
`wear:internal` hold nothing. So the stale `internal` release is the only artifact on any track built
before `targetSdk` reached 36. This is an exhaustive elimination, not a Console attribution - it says
what the row *can* be raised by, and the step still ends at a re-transcription of block 3.

The owner's working invocation, confirmed by a dry run on 2026-09-01 that wrote nothing: the script
read the track, reported the release as `completed` and refused with exit 1, naming the missing flag.
Clearing it therefore needs `-Track internal -AllowNonDraft -Confirm`.

**Not executed as of 2026-09-10 (S2294).** `internal` still holds `2.60.6222.324` / `260622232`,
`completed` (block 2, measured 2026-09-10). Nothing about the row has changed since 2026-09-01, and
`Policy status` has not been re-read since 2026-08-31, so whether the Android 16 row is still listed
is unmeasured in both directions.

Tool: `scripts/release/clear-play-track-release.ps1`, which refuses anything that is not a `draft`
unless told otherwise. **A successful commit does not finish this step.** Measured 2026-09-01: the
`wear:internal` **draft** vanished from `tracks().list()` immediately, while the `completed` record on
`internal` survived the same successful commit - the change went into `Publishing overview` as an
unsent change and takes effect only when the owner sends that batch. So do not re-transcribe
`Policy status` straight after the commit and conclude the row is still there; the step ends at
publication, not at the API call.

**Attempted and it did NOT take, 2026-09-12 (S2994).** `clear-play-track-release.ps1 -Track
internal -AllowNonDraft -Confirm` returned exit 0 and reported the one `completed` record removed,
but Play refused the automatic review and the script fell back to `changesNotSentForReview`. **A
held commit only queues a change; it does not alter the state later edits are built from, and here
it did not even produce a row.** The owner's `Publishing overview` of 2026-09-12 carries one group,
`Store listings`, and no `Internal testing` group at all, while `tracks().list()` still reports
`260622232`. So this step is not merely unfinished - the API cannot finish it while a rejection
stands. The June artifact is the last one below `targetSdk 36` and it remains on `internal`.

Route left: remove the release from the `internal` track in the Console by hand, or accept that the
carrier of `App must target Android 16` is an internal-testing artifact rather than a served one.
The same held-commit behaviour was measured on `wear:production` the same day (step 2a).

### Step 2a - Take the Wear artifact out of the pending batch

**Executor:** owner (Play Console) plus one API call, done.
**Proof:** `Publishing overview` lists no `Production (Wear OS)` row before the batch is submitted.

Release 37 ships without the watch (owner ruling 2026-09-12, carrier S2996). Two things had to go.

The owner discarded the rejected wear release `260909148` in the Console on 2026-09-12. That did
**not** empty the track: `wear:production` rolled back to `26090503` - the artifact of the
2026-09-08 rejection - and `Publishing overview` carried it as a pending `Start full rollout`. This
is the rollback behaviour the plan assumed and is now measured: discarding a release restores the
previous record rather than clearing the track.

`clear-play-track-release.ps1 -Track wear:production -AllowNonDraft -Confirm` reported exit 0 and
"1 release record(s) removed", again committed with `changesNotSentForReview` - **and it did not
take.** A dry run hours later still printed `would clear 1 release record(s)` with the full
`26090503` record, notes included, and the Console still showed the row. Each run added a change to
the batch instead of removing one; the batch grew 21 -> 25.

**What actually removed it: `Save for later` on the group header, in the Console** (owner,
2026-09-12). That control holds a whole pending group out of the submission, it is the only thing
that removes one, and the batch then read `24 changes` with `1 change saved for later`. Every
earlier note in this repository calling it a column label is wrong - it is a live button, and it is
the answer whenever a pending group must not reach review.

### Step 2b - Take the watch out of the store listing

**Executor:** one API call for the text, done; the images need an owner decision first.
**Proof:** the live listing carries no Wear OS bullet, and `wearScreenshots` are gone from it.

Carrier S2996. The listing is the contract a reviewer reads - two of the three rejections quote it
verbatim ("functionality not working **as described**") - so a release that ships no watch must not
promise one.

**Text, done 2026-09-12.** The `Wear OS` bullet was removed from `full_description.txt` in all
thirteen locales (one line each, the file's only occurrence of the string; sources backed up under
`temp/S2996/`). `assert-play-listing-locales.ps1` passes with 0 violations over 13 declared locales,
the longest description landing at 3721 of the 4000 characters Play allows.
`publish-play-listing.ps1 -Mode validate` cannot run at all under enforcement - Play refuses the
automatic review and `validate()` has no way to hold changes, which the script reports and which is
not a finding about the listing. `-Mode commit` then returned `SUCCESS: edit committed, but HELD -
not sent for review`, so the new text waits in the same unsent batch as the two track clears.

**Images stay, and that is the right answer, not a defeat.**
`clear-play-listing-images.ps1 -ImageType wearScreenshots -Confirm` is refused at commit time:

```
HTTP 403 ... "Cannot opt-in to Android Wear without screenshots."
```

While the app is opted in to the Wear OS form factor, the listing must carry at least one watch
screenshot, so the images cannot be deleted without first opting out under `Advanced settings ->
Form factors -> Wear OS -> Manage`. Play ties the form factor to the SCREENSHOTS, not to the
artifact: clearing `wear:production` was accepted on the same day, deleting four images was not.

**The opt-out is not required for release 37, and the precedent is our own.** The phone-only
submission of 2026-09-02 passed review and is what `production` serves today, and it went out with
the Wear OS form factor opted in and all four wear screenshots on the listing - they were staged
2026-08-24 (S1707) and refreshed 2026-09-05 by the wear release flow, so they were live throughout.
What has been rejected three times is the WATCH submission, never the phone one. Removing the
screenshots was therefore always the optional half of this step; the text was the half that
carries the "as described" argument, and the text is done. Owner ruling 2026-09-12: leave the form
factor alone. The opt-out question returns with the watch in release 38 (S2994 section 5.4), where
it belongs.

What does matter for the batch is the Wear PRODUCTION ROW, not the pictures - an artifact in the
batch is what dragged the phone release down in August (block 4, step 1).

Two operational facts from the same run, both worth keeping. **`publish-play-listing.ps1` re-uploads
every non-empty image folder**, `wearScreenshots` included - the en-US pass uploaded 22 images - so
a clear run before a publish is undone by that publish; the clear must always come after. And a
`503` mid-run leaves nothing behind: the edit is never committed, so the retry is safe.

### Step 3 - Submit the phone release from current code

**Executor:** owner (Play Console).
**Proof:** block 1 reports a served version above the one recorded there today, checked with
`read-play-public-serve.ps1 -RequireVersionAbove <previous>` rather than by reading the Console.

An ordinary `standard` campaign, with no watch artifact in the batch.

**Executed and proven, 2026-09-10 (S2294).**
`read-play-public-serve.ps1 -RequireVersionAbove 2.60.8151.948` exits 0: the store serves
`2.60.9021.951`, updated Sep 2, 2026, above the `2.60.8151.948` this record carried on 2026-09-01. The
proof is the reader's exit code, not a Console reading, exactly as the step demands.

### Step 4 - Return the watch on its own submission

**Executor:** repository first, then owner (Play Console).
**Proof:** all three carriers closed, S1984's pre-release walk run on a real watch, then a watch-only
campaign.

Every Wear claim recorded in block 3 with its carrier and that carrier's status, checked in the
catalog on 2026-09-09:

- `Watch shapes` (2026-08-31) - **S2273**, `Broken`. Open.
- `Missing app icon in splash screen` (2026-08-31) - **S2274**, `Archived`. Closed.
- `Functionality not working as described` (2026-08-31, both identical rows - one subject stated
  twice) - **S2275**, `Archived`. Closed.
- `Wear app functionality not working as described` (2026-09-08, versionCode `26090503`) -
  **S2755**, `Verified`, and **S2756**, `Verified`. Closed.

No watch submission is attempted before every open carrier above is closed and the pre-release walk
of **S1984** has run on device: a rejection costs a full review cycle. Open as of this check: none.

**Second submission sent, 2026-09-09 - `260909148` / `2.60.9021.951`.** All three 2026-09-08 carriers
were closed first, and release package 36 was closed on the artifact that reached the track (owner
instruction, `PLAN/RELEASE_QUEUE_DONE.md`). The review verdict is not in yet, so this submission is
recorded here and nowhere else - a rejection of it lands as fresh tickets in package 38, never as a
third reopening of 36.

**Third submission rejected, 2026-09-11 - `260909148` / `2.60.9021.951` (S2994).**
Email received 2026-09-11 12:17 -0700, Routing ID `ZLFS`, App Status: Rejected.
Specific issues found:
1. "Your app does not provide text without cut off in default font size as shown/described on the store listing."
2. "texts are cut off when a large font size is selected in your app."

**Wear Quality Guidelines mapping & review fleet findings (S2994):**
- **WO-V1 (User configured font size):** App must conform to font size set by user in system settings without text/control clipping or overlapping.
- **WO-V16 (Watch shapes):** Content must fit physical display area without text/control clipping.
- **WO-V14 (Font size):** Min font size 12sp (essential) / 10sp (non-essential).
- **WO-G2 (Play listing description):** List main features; mention tile/complication if surface included.
- **WO-G5 (Play listing screenshots):** Accurate Wear OS screenshots, 1:1 aspect ratio, no transparent mask/device frame.
- **WO-P5 (Companion app):** Non-standalone app must connect smoothly with companion.

**Precedents & Geometry Rules (S2994):**
- **Geometry over text metrics (spearo-go precedent):** Page scrolling (`ScalingLazyColumn`) does not substitute item layout geometry. Fix padding via viewport-relative insets (e.g. top 10%, bottom 12%, side 8%) rather than fixed dp padding to avoid pushing top/bottom lines under watch edges or pager dots.
- **Non-defects confirmed:** Ellipsis (`TextOverflow.Ellipsis`) is Google-recommended overflow behaviour; typography already uses `MaterialTheme.typography` tokens; no `fontScale` clamp exists.
- **Testing requirements:** Verification on real Galaxy Watch 7 (`SM-L310`) and shape profiles `small-round` (192dp) / `large-round` (227dp) using `wear-ink-clip.ps1` on every frame under all font sizes (1.0..1.24 & adb scale 1.5).

**Strategy for Release 36 / 37 / 38 (Owner ruling 2026-09-12):**
1. Release 36 Wear artifact failed (`260909148`). Phone artifact `2.60.9021.951` succeeded and is live.
2. Release 37 proceeds as **phone-only** (no Wear OS artifact submitted).
3. Release 38 will deliver the full Wear OS fix package after physical Galaxy Watch 7 glass testing (S2757), store listing bullet adjustment (S2996), `BLOOD_PRESSURE` removal from `standard` (S2995), and re-shooting `wearScreenshots`.

**What was traded to send it, named rather than buried.** The precondition "no `BlockNeedUserTest`
left in the package" was waived by the owner on 2026-09-09; 25 of the 46 rows the package shipped went
out unmeasured on glass, S2757 (the full watch retest) is still In Progress and moved to package 38,
and S2770 - content outside the round glass, the same class as the `Watch shapes` claim - was closed
by code audit with no device measurement. That is the configuration that produced the 2026-09-08
rejection. If this submission is rejected on a shape or functionality claim, read this paragraph
before re-deriving why.

**Step 3 does not wait on step 4, and never will.** That dependency is the exact failure this record
exists to prevent - one watch defect held a phone release for which there was not a single claim.

---

## How this file is kept current

`scripts/release/refresh-play-publishing-state.ps1` is the **only** writer of blocks 1 and 2, and
`scripts/release/watch-play-vitals.ps1` the only writer of block 4. Anything hand-written between the
`s2272:measured:*` marker comments is overwritten on the next run - edit the readers or the writers
instead. Block 3 is the owner's and no script touches it; the refresher only reports its age.

Refresh at every release and at every Play verdict:

```powershell
pwsh -NoProfile -File scripts/release/refresh-play-publishing-state.ps1
pwsh -NoProfile -File scripts/release/refresh-play-publishing-state.ps1 -Check   # staleness probe, writes nothing
pwsh -NoProfile -File scripts/release/watch-play-vitals.ps1                      # block 4; files a Draft on a red band
pwsh -NoProfile -File scripts/release/watch-play-vitals.ps1 -Check               # block 4 staleness probe, writes nothing
```
