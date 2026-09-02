# Build vs Release - workflow glossary

Source of truth for two work-process terms used across this project: **build** (RU «сборка») and **release** (RU «релиз»). They are different actions with very different cost. This document defines each, maps it to the existing tooling, and states exactly when paid GitHub Actions minutes are spent.

Skills `/build`, `/skill-release` and `/skill-release-wear` reference this file for terminology.

---

## The two terms

**Build (сборка)** - local, free, frequent.

- Compile an APK, verify it locally, then commit and push to a `DEBUG-v0NN` branch.
- Artifact is a debug APK in `DOWNLOADS/`. It is never published to any store.
- Costs zero GitHub Actions minutes (see cost map).

**Release (релиз)** - the `main` branch, paid CI, rare.

- Update docs and the site, build signed release artifacts, publish to the stores.
- Driven entirely by the `/skill-release` pipeline.
- This is the only flow that spends GitHub Actions minutes.

---

## CI cost map

Three GitHub Actions workflows exist. None of them triggers on a push to a `DEBUG-v0NN` branch - every trigger is keyed to `main` (push) or a pull request targeting `main`.

| Workflow | Triggers | Fires on a `DEBUG-v0NN` push? |
|----------|----------|:-----------------------------:|
| `android-ci.yml` (verify: lint + unit tests + standard build; extra-flavor builds; R8 release check) | push to `main`, PR to `main`, manual dispatch | No |
| `maestro-tests.yml` (emulator E2E) | PR to `main`, manual dispatch | No |
| `jekyll-gh-pages.yml` (site deploy) | push to `main`, manual dispatch | No |

Conclusion: **a test build (commit + push to a DEBUG branch) costs 0 minutes.** Paid CI fires only at the `main` boundary - i.e. inside `/skill-release` when a DEBUG branch is merged into `main`, and on any PR opened against `main`.

---

## Command / skill mapping

**Build flow** (local, free):

- `.\a.ps1 dq` - fast debug build (no zip, quiet).
- `.\a.ps1 fc` - fast local code + resources check; `-Flavor Standard|NoLegal|Lite|Photos|Legacy|Vr` proves any single flavor locally, for free.
- `.\a.ps1 c "<message>"` - commit and push to the current `DEBUG-v0NN` branch.
- Skill: `/build` - the build checklist (work order) plus the full build/script/versioning reference.

**Release flow** (`main`, paid CI):

- Campaign runbook: `/release [<flavor> ..]` - the full work order: assess situation, finish in-flight work + bug-fixes, run `/spec-prerelease`, evaluate, ready the docs (incl. "What's New in vXXX"), run the publish pipeline, distribute everywhere, verify. The "nothing forgotten" checklist lives here.
- Publish pipeline: `/skill-release [<flavor> ..]` - the automated core (merge DEBUG into `main`, tag, generate release notes, build artifacts, publish to Google Play + GitHub Release + Google Drive). It is one step inside `/release`, and the only flow that spends Actions minutes. Its per-step checklist and channel matrix live in the `/skill-release` skill - this document does not duplicate them.
- Watch publish pipeline: `/skill-release-wear` - the second release entry point, scoped to the `wear` module and the Play `wear:production` track. It runs on the watch's own cadence rather than as a step of `/release` or `/skill-release`, touches no branch and spends no Actions minutes: it resolves a version from the live Play state, runs the watch pre-release sweep, builds the watch bundle and publishes it. The phone pipeline neither builds nor publishes the watch (S2081).
- Hotfix on `main` with zero new behavior: `/skill-fix-release` instead of a full release.

---

## Main-push guard

A `pre-push` git hook (`scripts/githooks/pre-push`) blocks an accidental direct push to `main` from the dev worktree, so paid CI never starts outside `/skill-release`.

- One-time activation per clone: `pwsh -NoProfile -File scripts/githooks/activate-hooks.ps1` (sets `core.hooksPath`). The release worktree shares the same `.git`, so it inherits the hook automatically.
- The release worktree (`FastMediaSorter_release`, which `/skill-release` uses for every `main` push) is exempt by directory name - the guard never blocks a legitimate release.
- Escape hatch for an intentional manual `main` push: set `FMS_ALLOW_MAIN_PUSH=1` and push again.

---

## Maintenance

The CI cost map above must be updated whenever a workflow trigger changes (a new workflow, a changed `on:` condition, a new branch trigger). Keep this file the single place that states which actions spend Actions minutes.
