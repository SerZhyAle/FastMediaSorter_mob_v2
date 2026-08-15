# Research 04 - Spectrum build orchestrator

**Strategic item:** §6.4
**Status:** Resolved
**Date:** 2026-06-10

## Question

Which orchestrator builds the whole release set in one run, in what order, so the publisher gets a complete spectrum?

## Finding

`scripts/builders/build-and-push-all.ps1` (aliased `a.ps1 b` / `bp`) already builds the full set:
- Two-pass Gradle: pass 1 non-noLegal flavors (standard/lite/photos/legacy/vr) debug+release + `:wear:assembleDebug`/`:wear:assembleRelease` with Chaquopy disabled; pass 2 noLegal debug+release with Chaquopy enabled.
- Copies every produced APK to `DOWNLOADS/` (gitignored), mirrors to Google Drive (raw + password ZIP) and the tc folder, then `git add . && git commit && git push`.

Gaps for this feature:
- It builds debug AND release; the publisher only needs release artifacts.
- It does not stamp a uniform version (see research 02) - relies on whatever is in build.gradle.kts.
- It does not publish to GitHub Releases.
- `a.ps1` only routes `r` / `nl` / `vr` through the release worktree (main branch); `b`/`bp` runs in the current dir. The publisher enforces branch == main, so the release-spectrum flow must run on main (release worktree).

## Decision

Reuse the existing two-pass build logic for the release spectrum. The release-spectrum flow = stamp one version (app_v2 + wear) -> build all release flavors + wear release in the two passes -> hand the produced release APKs to the extended publisher. Run it on main (release worktree) so the publisher's branch guard passes. Debug artifacts and the Google Drive / tc mirrors stay as-is (out of scope).

## Impact on plan

- The orchestration phase can extend/parameterize the existing all-builder (release-only + version stamp) rather than invent a new build path.
- The publisher discovers release APKs from each flavor's `app_v2/build/outputs/apk/<flavor>/release` dir and `wear/build/outputs/apk/release`, matching the existing S0214 discovery pattern.
