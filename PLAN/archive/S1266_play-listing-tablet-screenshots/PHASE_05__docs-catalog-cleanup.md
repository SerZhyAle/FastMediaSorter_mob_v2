# Phase 05 - Validate, publish gate, docs cleanup

**Strategic spec:** [`../S1266_play-listing-tablet-screenshots.md`](../S1266_play-listing-tablet-screenshots.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done - all 3 steps, live publish included
**Depends on:** Phase 04 (24 composed images ready under `tenInchScreenshots`)
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-08-05
**Completed:** 2026-08-05

---

## Objective

Validate the composed set locally, journal the work, and gate the actual Play Console upload behind
an explicit owner-facing step rather than an autonomous live publish - per the canon hard invariant
"never trigger a release - including a store upload - unless the owner asked for that exact release."
The owner's §0 capture proposed doing a tablet set at all; it did not hand this agent standing
authority to push new public listing assets live without a final human look at the actual photos.

---

## Prerequisites

- [x] Phase 04 done - 24/24 composed images present, phone set confirmed untouched.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CHANGELOG.md` (via `add_to_dev_log.ps1`) | Modified (append only) | - |

---

## Steps

### Step 05.1 - Local validate

**Files:** none (validation only)
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/release/publish-play-listing.ps1 -Mode validate`. Confirm the
> report shows a non-zero `tenInchScreenshots` count for all three locales and the existing
> `phoneScreenshots` counts unchanged from before this ticket. A validate-mode failure here is a real
> defect in Phase 04's output - fix and re-run, do not proceed to the manual review step with a
> failing validate.

**Verification:**

- `publish-play-listing.ps1 -Mode validate` exits 0.
- Its report lists `tenInchScreenshots: 8` (or the tool's equivalent count phrasing) for `en-US`,
  `ru-RU`, `uk-UA`.

**Status:** `[x]` done - 2026-08-05

**Evidence:** `publish-play-listing.ps1 -Mode validate` exit 0, edit transaction 01269167714924690562
created and validated, not committed. The tool reports a per-locale total ("images uploaded: 16")
rather than a per-type count, so the predicate's split was confirmed on disk instead:
`play/listing/{en-US,ru-RU,uk-UA}/images/tenInchScreenshots` hold 8 files each and the matching
`phoneScreenshots` directories still hold 8 each - the S1256 baseline, untouched, and 8 + 8 = the 16
the tool counted. No `sevenInchScreenshots` directory exists, per the strategic §3.5 decision.

---

### Step 05.2 - Journal and close the mechanical work

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `.\scripts\add_to_dev_log.ps1` for every tracked-repo file this spec touched:
> `scripts/release/compose-play-screenshots.py` (if not already logged in Phase 01), and one entry
> covering the new `play/listing/*/images/tenInchScreenshots/*.png` assets as a batch (one logical
> entry, not 24 - per CLAUDE.md journaling granularity). Update strategic spec `Status:` to
> `Implemented` via `update.ps1` - not `Verified` yet, since the live publish (Step 05.3) is still
> pending and `/spec-check` should audit the actually-published state, not the locally-composed one.

**Verification:**

- Dev-log sink contains an entry referencing `S1266` for both the tooling change and the new asset
  batch.
- `select.ps1 -Id S1266 -Format json` reports `"status":"Implemented"`.

**Status:** `[x]` done - 2026-08-05

**Evidence:** the tooling entry the prompt allows to skip was already present from Phase 01
(`dev/CHANGELOG.md`, 2026-08-02 14:26, `compose-play-screenshots.py` tablet mode), so this step added
only the asset batch as one logical entry. `select.ps1 -Id S1266 -Format json` returns
`"status":"Implemented"`; the spec header synced to `Implemented` in the same call.

---

### Step 05.3 - Manual gate: live Play Console publish

**Files:** none (external Play Console action, not a repo file)
**Depends on:** Step 05.2

**Prompt for developer:**

> This step is intentionally NOT auto-executed by `/spec-dev`/`/spec-all`. Present the 24 composed
> images (or a representative sample - the 8 en-US frames at minimum) to the owner for a final visual
> look, then run `pwsh -NoProfile -File scripts/release/publish-play-listing.ps1 -Mode publish` only
> after that confirmation. Post-publish, re-run `-Mode validate` (or the tool's live-listing read
> path) to confirm the tablet set is actually live and the phone set is still exactly what S1256
> published - strategic §11 criterion 3.

**Verification:**

- MANUAL - owner confirmation recorded (chat or ticket note) before the publish command runs.
- Post-publish validate/read confirms `tenInchScreenshots` live for all three locales and
  `phoneScreenshots` unchanged from the S1256 baseline.

**Status:** `[x]` done - 2026-08-05

**Owner confirmation:** given in chat at 02:38 on 2026-08-05, verbatim "публкуй в pla console!", in
direct reply to the deferral above. The agent stated before running that the owner had not been shown
the frames and named their on-disk location; the owner's instruction stood.

**Command correction:** this step's prompt named `-Mode publish`, which the script rejects - its
`ValidateSet` accepts only `validate` and `commit`. Published with
`publish-play-listing.ps1 -Mode commit`, exit 0, edit transaction 16337531778952905198 committed
("Play may route via review").

**Post-publish evidence:** `-Mode validate` re-run would only re-check the local tree, and the
uploader has no read path, so the live listing was read directly - open edit, `edits().images().list`
per locale and type, delete the edit uncommitted (`temp/S1266/read_live_listing_images.py`, exit 0,
read-only edit 11236871415379663719). Live counts:

- `tenInchScreenshots` - 8 for `en-US`, 8 for `ru-RU`, 8 for `uk`. The tablet set is live in all three.
- `phoneScreenshots` - 8 for each of the three, unchanged from the S1256 baseline.
- `sevenInchScreenshots` - 6 for `en-US`, 0 for `ru-RU` and `uk`. Untouched by this commit, which
  confirms §3.5's prediction empirically: the uploader skips an image type it has no local folder for
  rather than wiping it. The `ru-RU`/`uk` zeros are pre-existing, not a regression from this ticket;
  §3.5 makes a 7" refresh an owner-requested follow-up, not an automatic one.

---

## Phase Done Criteria

- [x] Steps 05.1 and 05.2 `[x] done` (mechanically closeable by this pipeline).
- [x] Step 05.3 recorded as `[manual - deferred to human]` if the pipeline reaches this phase without
      a live owner present - per CLAUDE.md canon, do not simulate or skip the confirmation.
- [x] Dev log entries present.
- [x] `/spec-check S1266` run after Step 05.3 actually completes (by the owner or a follow-up
      session) - until then the ticket stays `Implemented`, not `Verified`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. `Verified` is reachable only after the live publish
(Step 05.3) is confirmed, not at local-validate time.

---

## Rollback Plan

Steps 05.1-05.2: no live state changed, nothing to roll back. Step 05.3 (once executed): Play Console
retains prior listing versions - revert via the console's own listing history if the live set needs
to be pulled back.
