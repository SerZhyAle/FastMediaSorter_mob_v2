# Build vs Release - workflow glossary

Source of truth for two work-process terms used across this project: **build** (RU «сборка») and **release** (RU «релиз»). They are different actions with very different cost. This document defines each, maps it to the existing tooling, and states exactly which GitHub Actions workflows a push starts and what they cost.

Skills `/build`, `/skill-release` and `/skill-release-wear` reference this file for terminology.

---

## The two terms

**Build (сборка)** - local, free, frequent.

- Compile an APK, verify it locally, then commit and push to a `DEBUG-v0NN` branch.
- Artifact is a debug APK in `DOWNLOADS/`. It is never published to any store.
- Bills zero GitHub Actions minutes while the repository is public - but the push does run the full `android-ci.yml` pipeline (see cost map).

**Release (релиз)** - the `main` branch, one-way, rare.

- Update docs and the site, build signed release artifacts, publish to the stores.
- Driven entirely by the `/skill-release` pipeline.
- This is the only flow that publishes anything. It is not the only flow that runs CI - see the cost map.

---

## CI cost map

Three GitHub Actions workflows exist. `android-ci.yml` fires on a push to `main` **and** on a push to a `DEBUG-v0NN` branch; the other two are keyed to `main` (push) or a pull request targeting `main`.

| Workflow | Triggers | Fires on a `DEBUG-v0NN` push? |
|----------|----------|:-----------------------------:|
| `android-ci.yml` (jobs `verify`, `verify-wear`, `static-gates`, `build-flavors`, `release-check`, `notify`; `regenerate-lint-baseline` on manual dispatch only) | push to `main` and to `DEBUG-v*`, PR to `main`, manual dispatch. Both push and PR are path-filtered to `app_v2/**`, `wear/**`, `gradle/**`, `*.kts`, `gradlew*`, `scripts/**`, `dev/**` and the workflow file itself | **Yes** |
| `maestro-tests.yml` (emulator E2E; job `maestro-tests`) | PR to `main`, manual dispatch | No |
| `jekyll-gh-pages.yml` (site deploy; jobs `build`, `deploy`) | push to `main` (path-filtered to the site sources), manual dispatch | No |

That path filter is not much of a narrowing in practice: it carries `dev/**`, and `scripts/add_to_dev_log.ps1` writes `dev/CHANGELOG.md` on every closure, so a DEBUG push carrying a dev-log row fires the whole pipeline even when no Kotlin changed.

### What a DEBUG push actually costs

Nothing in money - because the repository is public, not because CI stays quiet. The distinction is the point of this subsection: the two premises fail differently.

- `gh api repos/SerZhyAle/FastMediaSorter_mob_v2 --jq .visibility` returns `public`, and every job in all three workflows declares `runs-on: ubuntu-latest`. Standard GitHub-hosted runners on a public repository are free and uncapped.
- Measured 2026-09-13 over all six DEBUG-branch runs to date (`gh api repos/SerZhyAle/FastMediaSorter_mob_v2/actions/runs/<id>/timing`): `billable.UBUNTU.total_ms` is `0` for every one, including run `34694019558`, which ran 31 minutes before hitting its own `timeout-minutes`.
- What it does spend is wall clock and signal. Those six runs measured 1.9 to 31.0 minutes; four were cancelled by a newer push inside twenty minutes (`concurrency: cancel-in-progress`); a failing run mails a notification.

Flip the repository to private and the claim inverts: a DEBUG push starts billing ubuntu minutes at roughly the length of its run - about 30 per push at the current pipeline - while `maestro-tests.yml` stays unaffected, since it never fires outside a PR.

Conclusion: **a test build (commit + push to a DEBUG branch) bills 0 minutes because the repository is public.** It does run the full `android-ci.yml` pipeline. No flow in this repository spends paid Actions minutes at present; what the `main` boundary adds is the site deploy and the publication itself, not the bill.

---

## Command / skill mapping

**Build flow** (local, publishes nothing):

- `.\a.ps1 dq` - fast debug build (no zip, quiet).
- `.\a.ps1 fc` - fast local code + resources check; `-Flavor Standard|NoLegal|Lite|Photos|Legacy|Vr|Foss` proves any single flavor locally, for free.
- `.\a.ps1 c "<message>"` - commit and push to the current `DEBUG-v0NN` branch.
- Skill: `/build` - the build checklist (work order) plus the full build/script/versioning reference.

**Release flow** (`main`, publishes):

- Campaign runbook: `/release [<flavor> ..]` - the full work order: assess situation, finish in-flight work + bug-fixes, run `/spec-prerelease`, evaluate, ready the docs (incl. "What's New in vXXX"), run the publish pipeline, distribute everywhere, verify. The "nothing forgotten" checklist lives here.
- Publish pipeline: `/skill-release [<flavor> ..]` - the automated core (merge DEBUG into `main`, tag, generate release notes, build artifacts, publish to Google Play + GitHub Release + Google Drive). It is one step inside `/release`, and the only flow that publishes. Its per-step checklist and channel matrix live in the `/skill-release` skill - this document does not duplicate them.
- Watch publish pipeline: `/skill-release-wear` - the second release entry point, scoped to the `wear` module and the Play `wear:production` track. It runs on the watch's own cadence rather than as a step of `/release` or `/skill-release`, touches no branch and spends no Actions minutes: it stamps its own versionName and versionCode from the run instant (S2788 - the live Play state supplies only the versionCode the release notes are filed under), runs the watch pre-release sweep, builds the watch bundle and publishes it. It does consume a release package number, closing that package's block itself, so the `DEBUG-v0NN` branch name legitimately lags the `current-next-release:` marker afterwards - `/skill-release` reads the marker and re-seats the label at the next plateau release. The phone pipeline neither builds nor publishes the watch (S2081).
- Hotfix on `main` with zero new behavior: `/skill-fix-release` instead of a full release.

---

## Main-push guard

A `pre-push` git hook (`scripts/githooks/pre-push`) blocks an accidental direct push to `main` from the dev worktree, so the site deploy and the `main` pipeline never start outside `/skill-release`.

- One-time activation per clone: `pwsh -NoProfile -File scripts/githooks/activate-hooks.ps1` (sets `core.hooksPath`). The release worktree shares the same `.git`, so it inherits the hook automatically.
- The release worktree (`FastMediaSorter_release`, which `/skill-release` uses for every `main` push) is exempt by directory name - the guard never blocks a legitimate release.
- Escape hatch for an intentional manual `main` push: set `FMS_ALLOW_MAIN_PUSH=1` and push again.

---

## Maintenance

The CI cost map above must be updated whenever a workflow trigger changes (a new workflow, a changed `on:` condition, a new branch trigger, a changed path filter, an added or removed job). Keep this file the single place that states which actions start a workflow and what it costs.

Re-check the **repository visibility** in the same pass, because the zero-bill conclusion rests on it rather than on the triggers. This section carried the opposite trigger fact for months (S3082): `android-ci.yml` had gained its `DEBUG-v*` push trigger and the map still answered `No`, while the conclusion below it stayed accidentally true for a reason it never named. A claim whose premise is unwritten cannot be seen to go stale.
