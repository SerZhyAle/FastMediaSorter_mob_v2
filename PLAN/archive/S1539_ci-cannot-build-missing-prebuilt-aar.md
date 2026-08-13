# S1539 - CI cannot build: the prebuilt FFmpeg DTS AAR never reaches GitHub

**Status:** Archived

## 1. Symptom

`android-ci.yml` has failed **69 runs out of 69** - every run gh returns, back to 2026-04-16. No run
in that history has ever succeeded. The most recent failure is the release push of v2.60.8042.332 on
2026-08-04.

Every failure carries the same root exception:

```
java.io.FileNotFoundException: .../app_v2/libs/fms-ffmpeg-dts.aar (No such file or directory)
```

Nine Gradle tasks fail per run, all of them variations of the same unresolvable classpath.

## 2. Cause

- `app_v2/build.gradle.kts` declares `files("libs/fms-ffmpeg-dts.aar")` as a hard dependency of the
  standard, noLegal, legacy and vr flavors.
- `.gitignore` excludes `libs/`, so the 11.5 MB binary - built locally via WSL2 - is never pushed.
- A CI runner therefore checks out a tree in which a declared dependency does not exist, and dies
  during dependency resolution, before lint, the unit suite or the detector suite can run.

## 3. Why it went unnoticed for four months

- The workflow fires only on a push to `main`, which happens exclusively inside `/skill-release`.
  Day-to-day work on a `DEBUG-v0NN` branch never triggers it.
- Releases are built locally in a worktree where the AAR is present, so a red CI run never blocked a
  shipped release - it only spent Actions minutes and printed a red cross nobody was gated on.
- No documentation claimed CI was green, so nothing contradicted the state.

## 4. Fix

Host the AAR at a permanent address and fetch it before Gradle runs, rather than committing an
11.5 MB binary that would be re-added to history on every rebuild.

- The asset is published to the existing `delivery-so-v1` release - the repository's established home
  for prebuilt binaries that are deliberately not committed.
- `scripts/builders/publish-ffmpeg-dts-aar.ps1` uploads it with `--clobber` after any rebuild.
- `scripts/ci/fetch-prebuilt-libs.sh` downloads it into `app_v2/libs/` and asserts it is non-empty.
  One shared script, so the four build jobs that need it cannot drift apart.
- Wired into every job that invokes Gradle: `verify`, `build-flavors`, `release-check` in
  `android-ci.yml`, and the APK build in `maestro-tests.yml`.
- `scripts/ci/**` added to the workflow's path filters, so a change to the fetch script re-runs CI.

## 5. Deliberate decisions

- **Stable asset name, clobbered on rebuild** - the opposite of the `-v1`/`-v2` revision rule that
  governs the runtime payloads in the same release. No shipped app version fetches this asset, so
  there is no old revision to keep alive, and CI always wants the current binary.
- **No SHA-256 pin in the workflow.** A pin would turn every AAR rebuild into a second mandatory edit
  and a red CI run when it is forgotten - reintroducing the disease this ticket cures. The fetch
  script prints the hash it received instead, and `delivery/INVENTORY.md` records the current one.
- **Not committed to the repository.** 11.5 MB per rebuild, permanently, in a history that would
  never shed it.

## 6. Verification

Fixing the dependency is necessary but not sufficient: lint, the unit suite and the detector suite
have never executed in CI, so their first honest run is unproven ground. The gate is a completed
run, not a green one - a run that reaches the tasks and reports real findings has already proved this
ticket, and any findings it reports are new work.

- Command: `gh workflow run android-ci.yml --ref <branch>` after the change is pushed.
- Expected: the "Fetch prebuilt FFmpeg DTS AAR" step succeeds and Gradle reaches
  `lintStandardDebug` / `testStandardDebugUnitTest` instead of failing during resolution.
- Actual: pending - requires a push, which the owner has not yet authorised.

## 7. Affected files

- `.github/workflows/android-ci.yml`
- `.github/workflows/maestro-tests.yml`
- `scripts/ci/fetch-prebuilt-libs.sh` (new)
- `scripts/builders/publish-ffmpeg-dts-aar.ps1` (new)
- `delivery/INVENTORY.md`
- `docs/DEV_OPS.md`
