# Wear OS Release Readiness Gate

Canonical readiness contract for a **Wear OS** release of the `wear` module - the signed `:wear:bundleStandardRelease` AAB that goes to the watch form-factor track, and the `:wear:assembleStandardRelease` APK that goes anywhere else. It is an engineer/operator gate, not a marketing surface.

- **Strategic spec:** `PLAN/S1984_wear-release-has-no-prerelease-gate.md`
- **Module scope:** `wear` only (`standard` flavor for store release, `noLegal` for sideload; S2090).
- **Phone scope:** none. The phone standard is `docs/RELEASE_READINESS_STANDARD.md` and this document neither extends nor relaxes it.
- **Procedure:** `.claude/commands/spec-prerelease-wear.md`, which is the only supported way to produce this evidence.

Owner policy, inherited from the phone standard and narrowed here:

- The **device tier a verdict rests on is a real watch**. A Wear emulator qualifies for exercising the procedure and is a legitimate way to find a defect, but it is not what a release is signed off on.
- A step a machine cannot decide is **open**, not passed. The run renders `BLOCKED - manual observation open` and the release waits for a human to look.
- The repository **does** provision a Wear AVD: `scripts/devtest/wear-shape-bench.ps1 -Profile <id> -Ensure` creates the AVD for a declared profile from `scripts/devtest/wear-shape-profiles.json` when it is missing, and leaves an existing one untouched. This line used to say the opposite while that script was already in the tree (corrected by S2548). An emulator that is already running is still used as-is.

---

## What a Wear release must prove

1. **Every device-independent gate passed for the `wear` module.** New strings reach all declared locales, no merged resource outlived its source, the splash brand matches its generator. These run from one place for both modules, so a gate added later covers the watch without anyone remembering to add it.
2. **Both release artifacts were produced by the recorded build path**, in one invocation, not by a hand-typed gradle line.
3. **The verdict names the artifact it judged** - file name, `versionName` and `versionCode` for the APK and the bundle alike. A report that does not name its artifact cannot be attached to a release.
4. **The release build installs and starts on a watch**, by its own launcher component rather than by the phone's.
5. **Every declared screen the judged flavor carries was reached and recognised.** The screen list is data, walked in a fixed order, each screen reached by label or resource-id and never by a remembered coordinate. Each screen is recognised by a token that belongs to it and not to the screen it was opened from. An entry declaring a `flavors` scope that does not name the installed build is recorded `outOfFlavor` and not walked: the Home section or Apps program it reaches for is not drawn in that artifact, so there is no screen to judge (S3358).
6. **The declared list still matches the application.** Every entry names the screen it opens and the string resource it expects; `scripts/quality/assert-wear-walk-contract.ps1` fails when that resource stops resolving, stops containing the expected token, or is referenced by no composable. Nothing else connects the list to the module, and a walk entry that has drifted reports a working screen as broken (S2547).
   - **And its flavor scope still matches the catalogs.** Every entry also names the `HomeSectionId` or `WearAppId` row it depends on, and `StoreBoundaryTest` / `NoLegalBoundaryTest` run `HomeSectionCatalog` and `WearAppCatalog` with their own flavor's capabilities to assert that the row is drawn there exactly when the entry claims to be walkable there. Without it the list described the sideload Home while the sweep judged the store build, and the 2026-09-20 run on `emulator-5556` returned eighteen `unreachable` verdicts about screens that were never in the artifact (S3358).
7. **The walk states its own coverage.** Every screen in the module is either walked or listed with a recorded reason it is not, and the verdict prints both counts, plus the number of declared entries the installed flavor withholds. The walk is not a complete tour of the application and never claimed to be; what it must not do is leave the size of the gap unstated, because `clip-check` - which decides WO-V16 - runs only on a screen the walk actually opened.
8. **The process log carries no crash, ANR or app error** for the watch process across the walk, judged from a buffer cleared immediately before launch.
9. **No DECLARED screen is left undecided.** A screen the run could not decide - a dump that failed, or a state-dependent screen absent on a clean install - blocks the pass until a human clears it. A screen excluded under criterion 7 is out of scope by decision and does not block; a screen the walk could not observe because the display was asleep is not a verdict at all, and the walk returns 2 rather than reporting screens it never saw.

## Deobfuscation retention for the watch (S2722)

The watch's R8 `mapping.txt` is archived by the same scheme as the phone's, described in `docs/RELEASE_READINESS_STANDARD.md`: `scripts/release/build-release-spectrum.ps1` calls `scripts/release/retain-deobfuscation.ps1 -Variant wear` after a spectrum build that includes the watch, and the payload lands under the `Deobfuscation` sink at `<sink>\<wear versionCode>\wear-deobfuscation.zip`.

- **The directory is keyed by the WATCH's versionCode**, not the phone's. Since S2721 the two codes are unrelated (`yyMMddHH * 10 + 6 + floor(minute / 15)` for the watch), and a watch-only release published through `/skill-release-wear` has no phone code to file under at all - which is why the archive is not re-keyed to one code per release.
- **Both modules stamp the same `versionName` in a joint release**, so that is what ties the two directories together. `scripts/quality/assert-deobfuscation-retained.ps1` resolves a release by name and judges every payload found under it, the watch's included. A judged release with no wear payload is reported as such in the verdict rather than passed over in silence.
- **A watch-only release carries its own name** (S2788): `/skill-release-wear` stamps the versionName from its own run instant instead of copying the phone's published one, so such a release occupies a directory no phone release shares. That is the name its crash reports carry, so `fetch-deobfuscation.ps1 -VersionName <version> -Variant wear` still resolves it - what changed is the address, not the reachability.
- **Recovery from a watch crash report:** `pwsh -NoProfile -File scripts/release/fetch-deobfuscation.ps1 -VersionName <version> -Variant wear`. The version string off the crash report is enough; the variant is what selects the watch's directory when the phone shipped the same version.
- **`/skill-release-wear` creates no `release/v*` tag**, so the retention gate's tag-driven path never judges a watch-only release on its own. Ask about one explicitly with `-VersionName <version>`.

## What this gate deliberately does not cover

- **Publication.** Producing an uploadable bundle is proven here; uploading it belongs to `/skill-release-wear`, the watch's own release campaign, which runs this sweep as its gate and then publishes to `wear:production` (S2081). The phone's `/skill-release` never publishes the watch. The run also **distributes nothing** - it builds with `-NoDistribute`, leaving `DOWNLOADS`, the build journal and the Google Drive mirror untouched, because a sweep that judges a build must not simultaneously hand that build to anyone. The artifact it judged stays in `wear/build/outputs`; shipping one is a separate, deliberate call.
- **The release campaign's own build.** This gate judges the pair `scripts/builders/build-wear-release.PS1 -Artifact Both` produced. When a release includes the watch, `scripts/release/build-release-spectrum.ps1` produces its own pair from `:wear:assembleStandardRelease` and `:wear:bundleStandardRelease` in one gradle invocation at the version it stamped into both modules (S2040) - the same shape, a different run. Passing here is not a statement about that artifact, and the release path proves the two watch artifacts match each other by building them together, not by comparing them afterwards.
- **Maestro flows.** The watch has a flow tree at `maestro/wear/`, run by `maestro/run-tests.ps1 -Suite wear -DeviceId <watch serial>` and invisible to the phone's `-Suite all` (S2548). It covers user PATHS - a home hop and back, a setting that survives leaving and re-entering its screen, an off-screen row reached by scrolling, playback where the stand carries media. **The declared screen list remains the pre-release verdict**: it answers "does the screen open", the flows answer "does the path complete", and the flows do not become a gate until three consecutive runs on one build produce the same outcomes. Addressing is by `WearTestTags` id, never by a translated caption; bezel rotation is `scripts/devtest/adb.ps1 rotary`, outside the flow language. See `maestro/wear/README.md`.
- **Performance budgets.** The phone sweep measures per-checkpoint timings; the watch run has no equivalent measurement and claims none.
- **The phone companion surface.** Whether the phone can reach the watch is the companion feature's own concern, gated on the phone side.

## Evidence

One directory per run, `temp/scratch/wear-prerelease/`:

- `artifact.json` - what was built and judged.
- `walk.json` - per-screen outcome, plus the log audit's exit code.
- `wear_session.log` - the process log the audit read.
- One screenshot and UI dump per screen visited.

A release keeps whatever of this the operator needs to defend the verdict; nothing here is generated for its own sake.
