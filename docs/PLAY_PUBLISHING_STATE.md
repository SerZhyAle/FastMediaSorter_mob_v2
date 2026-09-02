# Play publishing state

The single record of what Google Play is currently publishing for `com.sza.fastmediasorter`: what the
store serves, what each track holds, and what the Console's `Policy status` page lists. Before this
file existed the answer was derived on demand from ticket status notes and from memory - three times
in one week, and once wrongly (S2272, ADR-2).

**The rule every row obeys.** Each row carries its own measurement date and its own source class. A
row with no date is read as **unmeasured**, never as unchanged. Two source classes exist:

- `measured` - written by a script, from the Play Developer API or from an anonymous page fetch.
- `transcribed` - typed in by the owner from a Console screen no API serves.

The three blocks are deliberately kept apart because they disagree in practice. The console can report
a track `completed` while the store still serves a two-week-old build, and a record holding only one
of those two rows reads as green.

---

## 1. Public serve - `measured`

What an anonymous visitor actually receives. Produced by
`pwsh -NoProfile -File scripts/release/read-play-public-serve.ps1 -Json`.

<!-- s2272:measured:public-serve:begin -->

| Served version | Store `Updated on` | Detected by | Measured (UTC) |
|----------------|--------------------|-------------|----------------|
| `2.60.8151.948` | Aug 15, 2026 | data-callback key 141 | 2026-09-02 |

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
| `production` | `2.60.8241.708` | `260824170` | completed | 2026-09-02 |
| `beta` | - | - | no release | 2026-09-02 |
| `alpha` | - | - | no release | 2026-09-02 |
| `internal` | `2.60.6222.324` | `260622232` | completed | 2026-09-02 |
| `wear:beta` | - | - | no release | 2026-09-02 |
| `wear:internal` | - | - | no release | 2026-09-02 |
| `wear:production` | `2.60.8250.134` | `26082501` | completed | 2026-09-02 |

Reader exit code: 0.

<!-- s2272:measured:tracks:end -->

**Drift since the previous measurement, 2026-08-31** - kept here, outside the marked region, because
the refresher overwrites what is inside it. Two rows moved in one day, which is the whole argument for
recording state instead of deriving it:

- `production` went from `2.60.8250.134` / `260825013` to `2.60.8241.708` / `260824170`. The rejected
  release is no longer on the track and the previous one is back.
- `wear:internal` held a draft on 2026-08-31 and holds no release now.

Both rows still disagree with block 1: the track reports `2.60.8241.708` while the store serves
`2.60.8151.948` from 2026-08-15.

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

Tool: `scripts/release/clear-play-track-release.ps1`, which refuses anything that is not a `draft`
unless told otherwise. **A successful commit does not finish this step.** Measured 2026-09-01: the
`wear:internal` **draft** vanished from `tracks().list()` immediately, while the `completed` record on
`internal` survived the same successful commit - the change went into `Publishing overview` as an
unsent change and takes effect only when the owner sends that batch. So do not re-transcribe
`Policy status` straight after the commit and conclude the row is still there; the step ends at
publication, not at the API call.

### Step 3 - Submit the phone release from current code

**Executor:** owner (Play Console).
**Proof:** block 1 reports a served version above the one recorded there today, checked with
`read-play-public-serve.ps1 -RequireVersionAbove <previous>` rather than by reading the Console.

An ordinary `standard` campaign, with no watch artifact in the batch.

### Step 4 - Return the watch on its own submission

**Executor:** repository first, then owner (Play Console).
**Proof:** all three carriers closed, S1984's pre-release walk run on a real watch, then a watch-only
campaign.

The three Wear claims are carried by **S2273** (watch shapes), **S2274** (missing splash-screen icon)
and **S2275** (functionality not as described - which covers both identical rows, since they are one
subject stated twice). No watch submission is attempted before all three are closed and the
pre-release walk of **S1984** has run on device: a rejection costs a full review cycle.

**Step 3 does not wait on step 4, and never will.** That dependency is the exact failure this record
exists to prevent - one watch defect held a phone release for which there was not a single claim.

---

## How this file is kept current

`scripts/release/refresh-play-publishing-state.ps1` is the **only** writer of blocks 1 and 2. Anything
hand-written between the `s2272:measured:*` marker comments is overwritten on the next refresh - edit
the readers or the refresher instead. Block 3 is the owner's and no script touches it; the refresher
only reports its age.

Refresh at every release and at every Play verdict:

```powershell
pwsh -NoProfile -File scripts/release/refresh-play-publishing-state.ps1
pwsh -NoProfile -File scripts/release/refresh-play-publishing-state.ps1 -Check   # staleness probe, writes nothing
```
