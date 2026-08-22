---
description: "Use to run the end-to-end pre-release emulator sweep that gates /skill-release - clean install, resources, settings, scenario, perf, verdict. Triggers: 'spec-prerelease', 'pre-release sweep', 'is the build release-ready'."
---

# /spec-prerelease - End-to-End Pre-Release Emulator Sweep

> **GLOBAL DIRECTIVES (anti-bureaucracy):**
> 1. Dry prose, no filler.
> 2. Autonomy over bureaucracy: don't block on minor issues; surface only critical findings.
> 3. Terse report: one line - verdict + report path.
> 4. Never auto-run release: PASS proposes `/skill-release`, owner confirms (ADR-1, S0484).

Automates `dev/PRE_RELEASE_MANUAL_TESTS.md` as one gated sweep on emulator: refresh the mutable content a release carries (0 / 0.5 non-gating, 0.6 / 0.7 / 0.8 **gating**) → prepare clean standard-debug install → configure resources + settings → Maestro capability suite → perf → machine PASS/FAIL verdict. PASS proposes `/skill-release`; FAIL parks deduped `/spec-draft` tickets and routes pending-test tickets through `/spec-check`.

Composes existing scripts only and adds **no** app runtime code (S0484 ADR-2); long-form overview and the full tool inventory in `.claude/reference/spec-prerelease.md` - read it when you need which script owns which stage.

## Usage

```text
/spec-prerelease                      # use the single online emulator
/spec-prerelease --device <id>        # pin a specific adb id
/spec-prerelease --dry-run            # plan only - no build/install/UI/verdict
```

Hard requirement: **mobile-mcp** server reachable (same gate as `/spec-test-device`). Standard-debug build must be built where `local.properties` sets `sza.owner.trigger`: resource import is performed only through OWNER_TRIGGER Settings-field path (S0492).

## Process

### 0 - Refresh stream-catalog delivery asset (content, no device, non-gating)

Needs no device, does not gate emulator verdict; its own non-zero exit is a finding on stream-catalog report line - never a sweep abort. On `--dry-run`, list as planned and run nothing (no network, no writes).

1. Append newly-discovered alive streams:

   ```powershell
   pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -PerQuery 30
   ```

2. Probe whole catalog as **non-destructive deep-signal** health report (S1117), in background:

   ```powershell
   pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -CatalogOnly -DeepSignal -Throttle 64
   ```

   Surface the breakdown on the report line so ballast can't accumulate unseen release-over-release.

**Never auto-prune in this sweep.** Pruning is human-gated opt-in.

If `streams.csv` changed, **always publish through the guarded packer** below - never `Compress-Archive` the CSV and `gh release upload` it by hand:

```powershell
pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -CatalogOnly -SkipLiveness -Publish
```

Read `.claude/reference/spec-prerelease.md` §"0 - Stream-catalog asset" before pruning, before publishing by any other path, or to read the `alive / dead / geo / unknown` breakdown.

### 0.4 - Judge the release-scope gates over the whole tree (content, no device, GATING)

Needs no device and no gradle. These are the gates whose subject is the state of the whole repository or a shipped artifact, so they say nothing useful about one changed file and everything useful about the scope about to ship (CLAUDE.md Rule 33).

```powershell
pwsh -NoProfile -File scripts/quality/assert-release-scope-gates.ps1
```

**Why it exists (S1939).** Applied per ticket, a repository-wide gate cannot attribute its finding to the change in front of it: it either fails on another session's work in flight or is demoted to advisory and stops meaning anything. Measured 2026-08-22 - `assert-unreferenced-strings` and `assert-splash-brand-sync` produced 50 of the 191 red lines that `fg` emitted over 53 runs, none of them about the operator's own work, and `assert-device-profile-matrix` spent 33 minutes of closure time over a month to report a single finding. The relocation is a script with an exit code rather than a list of calls in this file on purpose: gated rules hold at ~99%, rules stated as prose at 1-8%, so moving a gate into prose would change its force instead of its stage.

Branch on the exit code:

- **0** - the release scope is clean. Continue.
- **1** - at least one gate found a defect. **Release blocker**, and the fix is a repository-wide one: regenerate the artifact the gate names, or delete the dead weight it found. Re-run until 0. The failure is expected to be a batch of small items that accumulated over the cycle - that is the design, not a surprise.
- **2** - a gate script is absent. Treat as sweep abort (exit 2 in step 4), never as a pass.

Carry the outcome into the step 4 verdict the way 0.7's is carried: exit 1 blocks a clean PASS, exit 2 aborts the sweep.

### 0.5 - Refresh externally-rotting dependency pins (content, no device, non-gating)

Needs no device, does not gate the emulator verdict; its own failure is a finding on the deps report line, never a sweep abort. On `--dry-run`, list planned checks and run nothing (no network, no writes). Runs **before** step 1 prepare - both build, and `temp/BUILD.LOCK` (Rule 23) admits one gradle invocation at a time.

Two tiers, never mixed.

**Tier A - check and bump inline: `yt-dlp` only.** Read the current pin from the noLegal `pip { install("yt-dlp @ ...") }` block in `app_v2/build.gradle.kts`, then check both channels:

```powershell
(Invoke-RestMethod https://pypi.org/pypi/yt-dlp/json).info.version                                          # stable
(Invoke-RestMethod https://api.github.com/repos/yt-dlp/yt-dlp-nightly-builds/releases/latest).tag_name       # nightly
```

Prefer stable once it supersedes the pinned nightly date.

Verify. The standard-debug sweep never loads Chaquopy, so nothing downstream proves this pin - it needs its own build:

```powershell
.\a.ps1 nd
pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1
```

Build red on the new pin (pip resolve / Chaquopy sdist fetch) → revert to the previous pin, park one `/spec-draft`, continue the sweep. Never hand step 1 a broken noLegal tree.

**Tier B - check only, never bump: everything else.** A runtime-lib bump invalidates the sweep that follows it, so it belongs to its own ticket with its own regression pass, never to this window.

Read `.claude/reference/spec-prerelease.md` §"0.5 - Dependency pins" before editing any pin - Tier A bump procedure and the Tier B check-only policy live there.

### 0.6 - Confirm the previous release's deobfuscation artifacts are retained (content, no device, GATING)

**Mandatory, unconditional - not "if the last release looked fine".** `docs/RELEASE_READINESS_STANDARD.md` declares retention REQUIRED for every production release, and for a long time nothing enforced it: the miss surfaced only when S1156 sat in `BlockExternal` for three weeks because three obfuscated symbols from a shipped release could not be resolved. This step is where that becomes visible one release later instead of three weeks later. Needs no device, no gradle. On `--dry-run`, list the plan and run nothing.

```powershell
pwsh -NoProfile -File scripts/quality/assert-deobfuscation-retained.ps1
```

The check reads the stored mapping back through the archive and recomputes its SHA-256; presence alone is deliberately not accepted, because a cloud folder mid-sync presents a correctly sized placeholder. Branch on the exit code:

- **0** - the previous release is retained and verified, or it predates the retention baseline and is out of scope. Continue.
- **1** - the previous release is **not** retained, or a stored payload failed verification. **Hard release blocker.** The gate prints the exact `retain-deobfuscation.ps1` command per failed variant; run it against that release's shipped artifact, then re-run this step. Shipping now would leave the previous release permanently undecodable, which is the whole failure this scheme exists to end.
- **2** - the archive root is unreachable, or no release tag could be resolved. **Also blocking.** An archive that cannot be read is not evidence that anything was retained - "cannot verify" must never pass as "verified", since the failure mode being guarded is retention succeeding into the void.

Carry the outcome into the step 4 verdict on its own report line, naming the failed variants.

### 0.7 - Reindex settings search + navigation (content, no device, GATING)

**Mandatory, unconditional - not "if a setting changed".** Regenerate-then-verify here so the release always carries a fresh index. Needs no device; runs before step 1 prepare (both build, `temp/BUILD.LOCK` admits one gradle invocation at a time). On `--dry-run`, list the plan and run nothing.

```powershell
pwsh -NoProfile -File scripts/quality/reindex-settings.ps1
```

Unlike steps 0 / 0.5, this step **gates** the sweep - branch on its exit code:

- **0** - already fresh, verify gate green. Continue.
- **2** - drift was regenerated: `settings-manifest.json` / `SETTINGS_REFERENCE*.md` were stale and are now refreshed. Commit the updated files (`.\a.ps1 c "..."`) before the release proceeds, then continue. This is a finding on the reindex report line; the sweep may run, but a PASS must not propose `/skill-release` until the fresh mirror is committed.
- **3** - verify gate failed on something regeneration cannot fix: a settings layout missing from `SettingsSearchLayoutCatalog`, an unannotated manifest key, or a drifted HOW_TO navigation path. **Hard release blocker** - fix at source (append the layout, annotate the key, sync the HOW_TO path), re-run this step. Do not proceed to step 1 on exit 3.
- **1** - infrastructure failure (gradle/render). Treat as sweep abort (exit 2 in step 4), same as any build-side failure.

Carry the reindex outcome into the step 4 verdict: exit 2 (uncommitted fresh mirror) or exit 3 (unfixed inconsistency) blocks a clean PASS just as a red log audit does.

On exit 3, read `.claude/reference/spec-prerelease.md` §"0.7" - it names the artefact the shipped index is actually built from.

### 0.8 - Produce and clear the new-lexeme list (content, no device, GATING)

**Mandatory, unconditional - not "if strings changed".** The app declares thirteen interface locales; three are authored, ten are machine-translated in bulk. This is the step where the ten catch up, so the release ships no English caption to a user who chose another language. Needs no device, no gradle. On `--dry-run`, list the plan and run nothing.

```powershell
pwsh -NoProfile -File scripts/quality/assert-new-lexemes-translated.ps1
pwsh -NoProfile -File scripts/quality/assert-new-lexemes-translated.ps1 -Module wear
```

**Both modules, every time (S1628).** The gate has always accepted `-Module`, and this step has always called it without one - so it judged `app_v2` and the watch was never judged at all. The watch is interface too: its 102 keys sat in three locales while the rule claimed thirteen, and nothing said so, because the only thing that would have said so was never invoked. Run both lines; either one at exit 1 blocks the release.

Branch on each exit code:

- **0** - every string reaches all thirteen locales, or its gap predates the rule. Continue.
- **1** - new untranslated strings exist. **Hard release blocker**, and the fix is a task of this stage, not of the ticket that added the strings: hand `temp/S1627/new_lexemes_en.txt` (written by the gate's own run) to the external translation service, import each returned file with `scripts/utils/locale-bulk-import.ps1 -TextPath <returned file>`, then re-run this step until it is 0. The wear run writes its own list; import it with `-Module wear` so the returned strings land in the watch's resources rather than the phone's. When the values are already known, `set-android-string.ps1 -Action set -Key <key> -Locale <tag>` fills them directly instead. Do not proceed to step 1 on exit 1.
- **2** - the gate cannot verify: its producer or `scripts/quality/locale-untranslated-baseline.txt` is missing. Treat as sweep abort (exit 2 in step 4), same as any infrastructure failure - never as a pass.

### 0.9 - Refuse a tree that still packages a deleted resource (content, no device, GATING)

Needs no device and no gradle, and it runs before the clean install because the install in step 1 is built from exactly the tree this judges.

```powershell
pwsh -NoProfile -File scripts/quality/assert-no-orphan-merged-resources.ps1
pwsh -NoProfile -File scripts/quality/assert-no-orphan-merged-resources.ps1 -Module wear
```

**Why it exists (S1825).** Deleting a file under `src/*/res` does not remove its compiled artifact from `build/intermediates/merged_res`; the artifact keeps shipping and, on a device whose configuration matches its qualifier, wins over the file that is actually in the tree. On 2026-08-20 every variant - `standardRelease` included - packaged a `layout-w600dp/activity_streams.xml` deleted nine days earlier. It stayed invisible while the stale copy was merely old, and turned into a crash on every wide-screen open the moment the live layout gained a view the old one lacks.

Branch on each exit code:

- **0** - every merged artifact still has a source, or nothing is built yet. Continue.
- **1** - an artifact outlived its source. **Hard release blocker.** Re-run with `-Fix` (it drops the artifacts and the incremental merge state together), then rebuild so the variant re-merges from source, then re-run this step until it is 0. Do not proceed to step 1 on exit 1: the APK the sweep would install is the one carrying the stale resource.
- **2** - the module directory is absent. Treat as sweep abort (exit 2 in step 4), never as a pass.

Carry the outcome into the step 4 verdict the way 0.7's is carried: exit 1 blocks a clean PASS, exit 2 aborts the sweep.

### 0.9 - Report capabilities no user guide mentions (content, no device, ADVISORY)

Needs no device, no gradle. Sits here rather than in `post-change.ps1` because a capability record is written when the code lands, while its guide text is written at release time - failing the authoring call would block a feature ticket on documentation that is not yet due. On `--dry-run`, list the plan and run nothing.

```powershell
pwsh -NoProfile -File scripts/quality/assert-guide-coverage.ps1 -Gate
```

Branch on its exit code:

- **0** - every capability outside the baseline is mentioned in at least one English guide. Continue.
- **1** - a capability shipped that no guide mentions. **Advisory, not a release blocker**: name each id in the step 4 verdict and hand them to the guide-writing step of the release campaign. Silencing one by adding it to `scripts/quality/guide-coverage-baseline.txt` is allowed only with a stated reason - the baseline is the accumulated debt this gate exists to shrink (S1395 let it grow across several releases unseen).
- **2** - the gate cannot verify: the inventory, the document registry, or the guide set is missing. Treat as sweep abort, same as any infrastructure failure - never as a pass.

### 1 - Pre-flight: single device, prepare, hard-grant permissions

**1.0 - Assert exactly one online device first.** Clear every phantom offline `emulator-55xx` sibling, resolve one id, pass that id to **every** helper below via `-DeviceId` - never rely on single-device auto-detect:

```powershell
. "scripts/devtest/lib/find-adb.ps1"            # S1341: shared auto-discovery, no hardcoded path
$adb = Find-Adb
& $adb devices                                  # if >1 line, kill/clear each 'offline' sibling:
& $adb -s emulator-5554 emu kill; & $adb disconnect emulator-5554
# repeat per offline id; assert exactly one 'device' (not 'offline') remains, capture it as $dev
```

**1.1 - Prepare emulator:**

```powershell
pwsh -NoProfile -File scripts/devtest/prerelease-prepare.ps1 -DeviceId $dev -Json
```

Exit codes = abort signals: `1` adb missing, `2` no online device, `3` multiple devices, `10` a prepare stage failed. On any non-zero, abort with failing stage from JSON `stages` array. On `--dry-run`, print planned stages and stop here.

**1.2 - Hard-grant runtime permissions via adb** - do not trust prepare's API-gated grant (S0625):

```powershell
& $adb -s $dev shell pm grant com.sza.fastmediasorter.debug android.permission.ACCESS_LOCAL_NETWORK
& $adb -s $dev shell appops set --uid com.sza.fastmediasorter.debug MANAGE_EXTERNAL_STORAGE allow
```

**1.3 - Launch real activity, not LeakCanary.** Launch explicitly:
`adb -s $dev shell am start -n com.sza.fastmediasorter.debug/com.sza.fastmediasorter.ui.main.MainActivity`.

Read `.claude/reference/spec-prerelease.md` §"1 - Pre-flight detail" when a scan dies on permissions, the wrong activity opens, or Maestro reports a disconnected device.

### 2 - Configure resources + settings

```powershell
pwsh -NoProfile -File scripts/devtest/prerelease-configure.ps1 -DeviceId $dev -Json
```

Language is applied by the script's own `set:Language` stage - no manual locale step. On API < 33 stage SKIPs (per-app locale unavailable); genuine apply failure still exits 10.

Then drive UI via mobile-mcp for the parts adb cannot do. Read `.claude/reference/spec-prerelease.md` §"2 - Configure detail" before driving it - the run config and the three UI-driven parts (resource import, `Channel='ui'` settings, listing check) are itemised there and nowhere else.

### 3 - Drive core scenario

Start background logcat capture into `temp/S0484/run_<TS>.log` first (clear, then `adb logcat -v threadtime *:V`). **Use `threadtime`, not `time`** - a `-v time` capture makes the verdict silently report zero errors (reference §"3 - Scenario detail"). Then run the Maestro capability suite:

```powershell
$ts = Get-Date -Format yyyyMMdd_HHmmss
$maestroResults = "temp/S0484/maestro_suite_$ts.json"
pwsh -NoProfile -File maestro/run-tests.ps1 -Suite all -DeviceId $dev -Json > $maestroResults
```

Treat non-zero runner exit as FAIL finding and keep `$maestroResults` as evidence for step 4 - but check `.claude/reference/spec-prerelease.md` §"3 - Scenario detail" first: one exit shape is infrastructure, not a defect, and must not be parked as a ticket. That section also carries the smoke-suite pre-check, the mobile-mcp rule for uncovered paths, and the per-checkpoint perf invocations below. Capture screenshots only on FAIL to `temp/S0484/screens/` as evidence; screenshots no longer a pass gate.

Perf checkpoints still run where suite or exploratory path exercises surface - cold-start, list-scroll, player-open, network-listing, each recorded through `scripts/devtest/prerelease-measure.ps1 -Checkpoint <name> [-ElapsedMs <n>] -Json`.

Stop background logcat capture at end. Write each measure record to `temp/S0484/metrics_<TS>.json` (array consumed by verdict aggregator).

### 4 - Aggregate verdict and branch on PASS

Run verdict aggregator over run window:

```powershell
pwsh -NoProfile -File scripts/devtest/prerelease-verdict.ps1 `
    -LogFile temp/S0484/run_<TS>.log `
    -MetricsFile temp/S0484/metrics_<TS>.json `
    -MaestroResults temp/S0484/maestro_suite_<TS>.json `
    -ScreensDir temp/S0484/screens `
    -Json
```

Exit `0` = PASS, `1` = content FAIL, `2` = infrastructure abort. Write timestamped report to `temp/S0484/prerelease_<TS>.md` (device profile, per-stage results, verdict breakdown, evidence paths). Aggregate verdict is `reindex AND log AND perf AND maestro`; screenshots evidence-only. A step-0.7 exit 2 (fresh settings mirror not yet committed) or exit 3 (unfixed catalog/annotation/HOW_TO inconsistency) forces a non-PASS just as a red log audit does - record it on the reindex report line. A step-0.8 exit 1 (new strings still untranslated) forces a non-PASS the same way; record it on its own report line, naming the key count. A step-0.9 exit 1 (a merged artifact outlived its source) forces a non-PASS as well, and its report line names the orphaned resource and the variants that carry it - the sweep's own install is built from that tree, so a pass there would certify a package nobody verified.

Always run detailed log audit below before trusting a PASS - the verdict is a coarse gate; why it never proves a clean run is in `.claude/reference/spec-prerelease.md` §"4".

### 4.1 - Detailed log audit (mandatory, every run)

Run deep audit over same log:

```powershell
pwsh -NoProfile -File scripts/devtest/prerelease-log-audit.ps1 -LogFile temp/S0484/run_<TS>.log -Json
```

Exit `0` = clean, `1` = actionable clusters and/or error toasts present (triage), `2` = log unreadable.

Read the `attribution` field before the clusters: `pid` means each cluster was matched to the app's own process, `heuristic` means the capture carried no app `Start proc` line and the clusters may belong to other processes (S1859).

Treat every **actionable cluster** and every **error toast** as a finding even when machine verdict is PASS. Fold audit's actionable clusters into report's verdict breakdown and into FAIL-branch `/spec-draft` triage below. Include audit JSON path in evidence pack. On exit `1`, read `.claude/reference/spec-prerelease.md` §"4.1 - What the log audit does" to tell a benign cluster from an actionable one before triaging.

**On PASS:** only after audit is clean (or findings triaged/parked) print report path and propose `/skill-release` as next step. **Do not auto-run it** - release starts only on explicit owner confirmation (ADR-1). State this in final line.

### 5 - Branch on FAIL

For each distinct defect verdict **or step-4.1 audit** surfaced (research/06):

- **Dedup first** by symptom via `scripts/spec_catalog/search.ps1` (error code / class / subsystem keyword + same-day created). If open ticket already covers it, reference that id; do not draft duplicate.
- Otherwise park one `/spec-draft` per distinct defect, capturing symptom + evidence (log lines, screenshot path under `temp/`) into §0.

For pending-test tickets (`BlockNeedUserTest`) whose flow this sweep exercised, route evidence through `/spec-check <Sxxxx>` - it owns status flip (`Verified` / `Partial` / `Broken`) and, on any transition leaving `BlockNeedUserTest`, the `Timber.d("Sxxxx:` tag removal. Sweep never flips status by guess and never deletes a tag for a ticket that stays `BlockNeedUserTest`. Tickets sweep did not exercise stay untouched.

### Final report

One line: `spec-prerelease: device <id>, verdict PASS/FAIL, report temp/S0484/prerelease_<TS>.md`
- on PASS append `/skill-release` proposal; on FAIL append parked ids + tickets routed to `/spec-check`. Then append the stream-catalog segment and the deps segment - copy their literal formats from `.claude/reference/spec-prerelease.md` §"Final report segments" when composing the line.
