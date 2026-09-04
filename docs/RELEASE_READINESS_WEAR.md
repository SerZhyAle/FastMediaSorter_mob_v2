# Wear OS Release Readiness Gate

Canonical readiness contract for a **Wear OS** release of the `wear` module - the signed `:wear:bundleRelease` AAB that goes to the watch form-factor track, and the `:wear:assembleRelease` APK that goes anywhere else. It is an engineer/operator gate, not a marketing surface.

- **Strategic spec:** `PLAN/S1984_wear-release-has-no-prerelease-gate.md`
- **Module scope:** `wear` only. The watch module declares no product flavors, so there is one variant to prove.
- **Phone scope:** none. The phone standard is `docs/RELEASE_READINESS_STANDARD.md` and this document neither extends nor relaxes it.
- **Procedure:** `.claude/commands/spec-prerelease-wear.md`, which is the only supported way to produce this evidence.

Owner policy, inherited from the phone standard and narrowed here:

- The **device tier a verdict rests on is a real watch**. A Wear emulator qualifies for exercising the procedure and is a legitimate way to find a defect, but it is not what a release is signed off on.
- A step a machine cannot decide is **open**, not passed. The run renders `BLOCKED - manual observation open` and the release waits for a human to look.
- The repository provisions **no Wear AVD**: no image, no bring-up script, no flow tree. An emulator that is already running is used; none is created.

---

## What a Wear release must prove

1. **Every device-independent gate passed for the `wear` module.** New strings reach all declared locales, no merged resource outlived its source, the splash brand matches its generator. These run from one place for both modules, so a gate added later covers the watch without anyone remembering to add it.
2. **Both release artifacts were produced by the recorded build path**, in one invocation, not by a hand-typed gradle line.
3. **The verdict names the artifact it judged** - file name, `versionName` and `versionCode` for the APK and the bundle alike. A report that does not name its artifact cannot be attached to a release.
4. **The release build installs and starts on a watch**, by its own launcher component rather than by the phone's.
5. **Every declared screen was reached and recognised.** The screen list is data, walked in a fixed order, each screen reached by label or resource-id and never by a remembered coordinate. Each screen is recognised by a token that belongs to it and not to the screen it was opened from.
6. **The declared list still matches the application.** Every entry names the screen it opens and the string resource it expects; `scripts/quality/assert-wear-walk-contract.ps1` fails when that resource stops resolving, stops containing the expected token, or is referenced by no composable. Nothing else connects the list to the module, and a walk entry that has drifted reports a working screen as broken (S2547).
7. **The walk states its own coverage.** Every screen in the module is either walked or listed with a recorded reason it is not, and the verdict prints both counts. The walk is not a complete tour of the application and never claimed to be; what it must not do is leave the size of the gap unstated, because `clip-check` - which decides WO-V16 - runs only on a screen the walk actually opened.
8. **The process log carries no crash, ANR or app error** for the watch process across the walk, judged from a buffer cleared immediately before launch.
9. **No DECLARED screen is left undecided.** A screen the run could not decide - a dump that failed, or a state-dependent screen absent on a clean install - blocks the pass until a human clears it. A screen excluded under criterion 7 is out of scope by decision and does not block; a screen the walk could not observe because the display was asleep is not a verdict at all, and the walk returns 2 rather than reporting screens it never saw.

## What this gate deliberately does not cover

- **Publication.** Producing an uploadable bundle is proven here; uploading it belongs to `/skill-release-wear`, the watch's own release campaign, which runs this sweep as its gate and then publishes to `wear:production` (S2081). The phone's `/skill-release` never publishes the watch. The run also **distributes nothing** - it builds with `-NoDistribute`, leaving `DOWNLOADS`, the build journal and the Google Drive mirror untouched, because a sweep that judges a build must not simultaneously hand that build to anyone. The artifact it judged stays in `wear/build/outputs`; shipping one is a separate, deliberate call.
- **The release campaign's own build.** This gate judges the pair `scripts/builders/build-wear-release.PS1 -Artifact Both` produced. When a release includes the watch, `scripts/release/build-release-spectrum.ps1` produces its own pair from `:wear:assembleRelease` and `:wear:bundleRelease` in one gradle invocation at the version it stamped into both modules (S2040) - the same shape, a different run. Passing here is not a statement about that artifact, and the release path proves the two watch artifacts match each other by building them together, not by comparing them afterwards.
- **Maestro flows.** The watch has no flow tree; the declared screen list is the scenario.
- **Performance budgets.** The phone sweep measures per-checkpoint timings; the watch run has no equivalent measurement and claims none.
- **The phone companion surface.** Whether the phone can reach the watch is the companion feature's own concern, gated on the phone side.

## Evidence

One directory per run, `temp/scratch/wear-prerelease/`:

- `artifact.json` - what was built and judged.
- `walk.json` - per-screen outcome, plus the log audit's exit code.
- `wear_session.log` - the process log the audit read.
- One screenshot and UI dump per screen visited.

A release keeps whatever of this the operator needs to defend the verdict; nothing here is generated for its own sake.
