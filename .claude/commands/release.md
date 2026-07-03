# /release - Release Campaign Runbook

> **GLOBAL DIRECTIVES:**
> 1. Dry technical prose. RU in chat, EN in commits/docs.
> 2. This is a **work-order checklist**, not a script. It orders the whole release campaign and delegates execution to existing skills. `/skill-release` is ONE step (Step 5), not the whole thing.
> 3. Terminology: "build" vs "release" and CI cost are defined in `docs/BUILD_VS_RELEASE.md`. A release is the only flow that spends paid GitHub Actions minutes.
> 4. Gate discipline: do not advance to publication while in-flight work is unfinished or the pre-release sweep is not PASS. Stop and report at every gate that fails.
> 5. Never auto-skip a channel. The point of this runbook is that nothing is forgotten.

Coordinates a full release campaign end to end: assess situation -> finish in-flight work + bug-fixes -> pre-release sweep -> evaluate -> ready the docs (incl. "What's New in vXXX") -> run the publish pipeline -> distribute everywhere -> verify. Each heavy step is an existing skill; this file is the order of work and the "nothing forgotten" checklist.

## Usage

```text
/release                       # standard only (Google Play AAB + canonical GitHub asset)
/release <flavor> [<flavor>..] # widen GitHub spectrum (vr, lite, photos, legacy, noLegal, wear)
/release all                   # full spectrum
/release --assess              # run Step 0 only (situation report), then stop
```

Run from the development directory (`FastMediaSorter_mob_v2`) on a `DEBUG-v0NN` branch. The flavor argument is passed through verbatim to `/skill-release` at Step 5.

---

## Campaign Steps

### Step 0 - Assess the situation

Build the release picture before touching anything. Do not change code in this step.

1. Confirm branch + tree:
   - `git branch --show-current` - must be `DEBUG-v0NN` (not `main`).
   - `git status --porcelain` - note uncommitted WIP (it will be committed in Step 1 or by `/skill-release` Step 1b, not lost).
2. Inventory outstanding spec work:
   - `.\a.ps1 ss` - unresolved specs.
   - List specs in `In Progress`, `Partial`, `Broken`, `Block*` via `scripts/spec_catalog/select.ps1` - these are candidates to finish or consciously defer.
   - List `BlockNeedUserTest` tickets - they need on-device confirmation before they can be called done.
3. Decide release scope: which pending tickets ship this release, which are deferred to the next DEBUG cycle. Record the decision.

`--assess` stops here with a one-paragraph situation report.

### Step 1 - Finish in-flight work and bug-fixes

Bring the chosen scope to a closed state. Pick the right tool per ticket - do not hand-roll:

- `In Progress` tactical spec -> `/spec-dev <Sxxxx>` to finish its phases.
- Small bug / UI fix without a spec -> `/skill-fix` (local validation only).
- `Partial` / `Broken` spec -> `/spec-fix <Sxxxx>` then `/spec-check <Sxxxx>`.
- `BlockNeedUserTest` tickets in scope -> `/spec-sweep` (batch) or `/spec-test-device <Sxxxx>`, then `/spec-check` flips status and removes the `Timber.d("Sxxxx:` probes.

Gate: every in-scope ticket is `Verified` / `Implemented` (or consciously deferred). Working tree builds. Commit + push WIP to the DEBUG branch (`.\a.ps1 c "<msg>"`). Deferred tickets are noted, not silently dropped.

### Step 2 - Pre-release sweep

Run the end-to-end emulator sweep - the single gate that proves the build is shippable:

```text
/spec-prerelease [--device <id>]
```

It does a clean standard-debug install with seeded media, configures resources + settings, runs the Maestro capability suite, measures perf, and produces a machine PASS/FAIL verdict plus a detailed log audit. Requires `mobile-mcp` and an online emulator.

Gate: verdict must be **PASS** with a clean (or triaged) log audit. On FAIL, `/spec-prerelease` parks deduped `/spec-draft` tickets - return to Step 1, fix the blocking ones, re-run. Do not proceed to publication on a red sweep.

### Step 3 - Evaluate the result

- Read `temp/s0484_prerelease_<TS>.md` (verdict breakdown, perf, evidence).
- Confirm every actionable log-audit cluster and every error toast is either fixed or a known benign emulator fallback.
- Confirm no release-coverage regression (countries / age / device reach: minSdk, ABI, uses-feature, flavor set) versus the previous release.
- Decision: GO or back to Step 1. A GO here is the entry condition for publication.

### Step 4 - Ready the documentation (incl. "What's New")

The publish pipeline (Step 5) generates the user-facing release notes from curated inputs - so the headline "What's New in vXXX" is only as good as what is ready now. Make the inputs current before Step 5:

- **Feature inventory** `docs/ALL_FEATURES.jsonl` - every shipped capability this release must have its EN record (via `scripts/all_features/add.ps1`). `/skill-release` Step 3 diffs this since the last release to write "What's New" / "What's Fixed"; a missing record means a missing release-note line. noLegal-only records go to `docs/ALL_FEATURES_noLegal.jsonl`.
- **Settings docs** - if any setting changed, the settings manifest + reference are regenerated (CLAUDE.md Rule 22 gate).
- **HOW_TO / navigation paths** - if a Settings path changed, the HOW_TO path gate is green.

Note what `/skill-release` will produce automatically in Step 5, so you can verify it afterward rather than hand-editing:

- `docs/WHATS_NEW.md` + `_RU` + `_UK` - "Current release: vXXX" block prepended (feeds fastlane changelogs).
- `README.md` (+ RU/UK) - "What's New in vXXX" heading.
- `docs/FEATURES*` showcase - promoted standout items from the inventory diff.

Do NOT hand-edit `WHATS_NEW.md` / `FEATURES*` here - they are owned by `/skill-release`. This step only guarantees its inputs are complete.

### Step 5 - Run the publish pipeline

```text
/skill-release [<flavor> ..]   # pass through the flavor scope from Usage
```

This is the automated core: commit pending WIP, compute the self-consistent version + versionCode, write `WHATS_NEW.md` / `README` / fastlane changelogs, merge `DEBUG-v0NN` -> `main`, tag `release/vXXX`, open the next DEBUG branch, build the standard AAB (+ requested flavor APKs), publish the standard AAB to Google Play and the requested APKs to GitHub Releases, mirror the Google Drive ZIP, and update the FEATURES showcase.

This is the step that spends paid GitHub Actions minutes (push to `main` -> android-ci + pages). Read its final report - it lists every automated outcome and the manual follow-ups for Step 6.

### Step 6 - Distribute everywhere

Complete the channels `/skill-release` cannot fully automate - work its final report's "Manual follow-ups" list, do not skip any:

- **Google Play FGS gate** - if a new `FOREGROUND_SERVICE_*` type shipped, declare it in Play Console -> App content (demo video for mic/cam/loc/media-projection), then finish the release from library.
- **4pda forum** (RU) - manual post, cumulative since the last 4pda post (not last release). Three spoilers (Что нового / Что исправлено / noLegal). Attach standard release APK + noLegal debug APK (`.\a.ps1 nd` for a fresh asset). Author style (`..`, `ё`); noLegal items from gitignored docs only.
- **IzzyOnDroid** - first time only: submit the RFP. After acceptance it auto-pulls the standard APK from each GitHub release.
- **GitHub Store** - owner check that search/install works after indexing.

### Step 7 - Verify and final report

- Confirm the tag, the Play track state, and the GitHub release assets exist at the new version.
- Confirm `WHATS_NEW.md` "Current release" header matches the tag and the built artifact (no version skew).
- Output one structured summary: version, tag, next DEBUG branch, flavors published, channels done, and any deferred tickets or pending manual follow-ups.

---

## Checklist - nothing forgotten

- [ ] On a `DEBUG-v0NN` branch, situation assessed, release scope decided (Step 0).
- [ ] All in-scope tickets `Verified` / `Implemented`; deferred ones recorded; tree builds + pushed (Step 1).
- [ ] `/spec-prerelease` verdict PASS, log audit clean or triaged (Step 2-3).
- [ ] No release-coverage regression vs previous release (Step 3).
- [ ] `docs/ALL_FEATURES.jsonl` has a record for every shipped capability (Step 4).
- [ ] Settings manifest + HOW_TO path gates green if settings/paths changed (Step 4).
- [ ] `/skill-release` ran; `WHATS_NEW.md` "What's New in vXXX" generated and version-consistent (Step 5).
- [ ] Google Play published / FGS declared if needed (Step 5-6).
- [ ] GitHub release assets published for the requested flavors (Step 5).
- [ ] Google Drive ZIP synced (Step 5).
- [ ] 4pda post composed (Step 6).
- [ ] IzzyOnDroid RFP submitted if first time (Step 6).
- [ ] Tag + artifacts verified, no version skew, final report emitted (Step 7).

---

## Relationship to other skills

- `/build` - the local, free, per-change flow (Step 1 uses it indirectly via the dev cycle). Not part of publication.
- `/spec-prerelease` - the shippability gate (Step 2).
- `/skill-release` - the automated publish pipeline (Step 5). `/release` wraps it with the surrounding campaign; it does not replace or duplicate it.
- `/skill-fix-release` - fix-release flow for a regression on `main` with zero new behavior; use that instead of a full `/release` when only shipping a hotfix.
