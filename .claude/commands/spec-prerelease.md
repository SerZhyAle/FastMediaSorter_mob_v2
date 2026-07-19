# /spec-prerelease - End-to-End Pre-Release Emulator Sweep

> **GLOBAL DIRECTIVES (anti-bureaucracy):**
> 1. Dry prose, no filler.
> 2. Autonomy over bureaucracy: don't block on minor issues; surface only critical findings.
> 3. Terse report: one line - verdict + report path.
> 4. Never auto-run release: PASS proposes `/skill-release`, owner confirms (ADR-1, S0484).

Automates `dev/PRE_RELEASE_MANUAL_TESTS.md` as one gated sweep on emulator: prepare clean standard-debug install with seeded media → configure resources + settings → run deterministic Maestro capability suite, use mobile-mcp only for uncovered exploratory paths → measure perf → aggregate machine PASS/FAIL verdict. PASS proposes `/skill-release`; FAIL parks deduped `/spec-draft` tickets and routes pending-test tickets through `/spec-check`. Steps 0 / 0.5 first refresh the mutable external content a release carries - downloadable stream-catalog delivery asset, then externally-rotting dependency pins (`yt-dlp`) - both content-only, no device, non-gating. Step 0.7 then reindexes the settings search + navigation mirror (regenerate-then-verify) - content-only, no device, but **gating**: the build must always ship a current settings index.

Composes existing tools - `scripts/devtest/prerelease-prepare.ps1`, `scripts/devtest/prerelease-configure.ps1`, `scripts/devtest/prerelease-measure.ps1`, `scripts/devtest/prerelease-verdict.ps1`, `scripts/devtest/prerelease-log-audit.ps1`, `scripts/utils/search-log.ps1`, `maestro/run-tests.ps1`, `scripts/streams/collect-stream-candidates.ps1`, `scripts/quality/reindex-settings.ps1`, mobile-mcp, `/skill-release`, `/spec-draft`, `/spec-check` - adds **no** app runtime code (S0484 ADR-2).

## Usage

```text
/spec-prerelease                      # use the single online emulator
/spec-prerelease --device <id>        # pin a specific adb id
/spec-prerelease --dry-run            # plan only - no build/install/UI/verdict
```

Hard requirement: **mobile-mcp** server reachable (same gate as `/spec-test-device`). Standard-debug build must be built where `local.properties` sets `sza.owner.trigger`: resource import is performed only through OWNER_TRIGGER Settings-field path (S0492 - no adb-scriptable import; former `file://` intent-push always failed on minSdk 26).

## Process

### 0 - Refresh stream-catalog delivery asset (content, no device, non-gating)

Curated stream catalog `delivery/stream-catalog/streams.csv` ships as **mutable** GitHub Release asset, independent of app binary (S0588; `delivery/stream-catalog/README.md`). Refresh here so release carries fresh, live streams. Needs no device, does not gate emulator verdict; its own non-zero exit is a finding on stream-catalog report line - never a sweep abort. On `--dry-run`, list as planned and run nothing (no network, no writes).

1. Append newly-discovered alive streams. Run keeps only `alive` rows and writes timestamped `temp/` backup before touching CSV:

   ```powershell
   pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -PerQuery 30
   ```

2. Probe whole catalog as **non-destructive** health report - prints `Would prune N row(s)` and deletes nothing:

   ```powershell
   pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -CatalogOnly
   ```

**Never auto-prune in this sweep.** Pruning is human-gated opt-in: a geo-restricted stream reads `dead`/404 from build machine yet plays on user's device. Review `temp/stream-catalog-liveness.csv`; only if a row is genuinely dead after review (ideally a second-network re-probe) run `scripts/streams/collect-stream-candidates.ps1 -CatalogOnly -PruneDead` manually, outside this sweep.

If `streams.csv` changed (append, or later manual prune), re-package and re-upload asset or change never reaches users - app fetches release asset, not repo file. **Always publish through the guarded packer** below - never `Compress-Archive` the CSV and `gh release upload` it by hand:

```powershell
pwsh -NoProfile -File scripts/streams/collect-stream-candidates.ps1 -CatalogOnly -SkipLiveness -Publish
```

This bundles `streams.csv` **and** `favicon-atlas.png` and enforces the S0925 guard. A raw `Compress-Archive -Path .\streams.csv` ships a CSV-only zip with no atlas - the app then gets `atlasPng=null`, `FaviconAtlasStore.write(null, coords)` deletes the atlas and writes empty coords, and **every** channel loses its favicon app-wide (recurred 2026-07-12). `-SkipLiveness` skips the ~2489-URL probe and does not mutate the CSV, so the published pair stays consistent.

(Hosting / release tag in `delivery/stream-catalog/README.md`.)

### 0.5 - Refresh externally-rotting dependency pins (content, no device, non-gating)

Same slot rationale as step 0: refresh mutable external content before the release carries it. Needs no device, does not gate the emulator verdict; its own failure is a finding on the deps report line, never a sweep abort. On `--dry-run`, list planned checks and run nothing (no network, no writes). Runs **before** step 1 prepare - both build, and `temp/BUILD.LOCK` (Rule 23) admits one gradle invocation at a time.

Two tiers, never mixed.

**Tier A - check and bump inline: `yt-dlp` only.** Its rot is server-side: extractors break because YouTube/Instagram change, not because our code changed, so a stale pin ships a broken link-download that no amount of our own testing would have caught. Pure-Python drop-in, so a bump cannot break Kotlin compile.

1. Read the current pin from the noLegal `pip { install("yt-dlp @ ...") }` block in `app_v2/build.gradle.kts` - never assume, the dated comment history above it records why the channel was chosen.
2. Check both channels:

   ```powershell
   (Invoke-RestMethod https://pypi.org/pypi/yt-dlp/json).info.version                                          # stable
   (Invoke-RestMethod https://api.github.com/repos/yt-dlp/yt-dlp-nightly-builds/releases/latest).tag_name       # nightly
   ```

3. Prefer stable once it supersedes the pinned nightly date. Stay on nightly only while a needed extractor fix is nightly-only - the pin comment names the fix, so it is checkable, not a vibe.
4. On bump: edit the pin, append one dated comment line stating the channel and the reason (matches existing S0190/S0950 comment style), and sync the doc pin in `docs/TECH_STACK.md` ("Sideload / XR-only surface").
5. Verify. The standard-debug sweep never loads Chaquopy, so nothing downstream proves this pin - it needs its own build:

   ```powershell
   .\a.ps1 nd
   pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1
   ```

6. Build red on the new pin (pip resolve / Chaquopy sdist fetch) → revert to the previous pin, park one `/spec-draft`, continue the sweep. Never hand step 1 a broken noLegal tree.
7. A bump is `BlockNeedUserTest`-shaped: pip resolving proves nothing about extraction. Report it as needing a real link-download on device; do not claim it verified.

**Tier B - check only, never bump: everything else** (Media3, Room, Glide, AGP, Kotlin, AndroidX, cloud SDKs, and `NewPipeExtractor` despite it rotting the same way as yt-dlp - it is a Java dep whose API surface can break compile). A runtime-lib bump invalidates the sweep that follows it, so it belongs to its own ticket with its own regression pass, never to this window.

- There is no version catalog and no `dependencyUpdates` task here - pins are hand-written in `app_v2/build.gradle.kts`. Do **not** hand-sweep every pin each release; that is unbounded work with no gate behind it.
- Check a Tier B pin only when this sweep's own evidence points at it (audit cluster, crash, perf record naming the library). Then park `/spec-draft` with that evidence - do not edit the pin.
- `scripts/check-doc-vs-gradle.ps1` is an internal docs-vs-Gradle consistency check, not an upstream freshness check. Non-zero exit here means our own docs drifted; fix the doc line, not the pin.

### 0.7 - Reindex settings search + navigation (content, no device, GATING)

**Mandatory, unconditional - not "if a setting changed".** The in-app settings search index is rebuilt at runtime by scanning `SettingsSearchLayoutCatalog` and routing hits through `SettingsSearchTabMapping`; nothing is serialized into the APK. So what the build "ships" for search - and, above all, for the *navigation to a setting* - is only as current as the layout catalog, the tab mapping, and the doc mirror (`settings-manifest.json` + `SETTINGS_REFERENCE*.md` + annotations + HOW_TO paths). A stale mirror or an unindexed screen makes the shipped search silently miss settings or fail to navigate to them. Regenerate-then-verify here so the release always carries a fresh index. Needs no device; runs before step 1 prepare (both build, `temp/BUILD.LOCK` admits one gradle invocation at a time). On `--dry-run`, list the plan and run nothing.

```powershell
pwsh -NoProfile -File scripts/quality/reindex-settings.ps1
```

Unlike steps 0 / 0.5, this step **gates** the sweep - branch on its exit code:

- **0** - already fresh, verify gate green. Continue.
- **2** - drift was regenerated: `settings-manifest.json` / `SETTINGS_REFERENCE*.md` were stale and are now refreshed. Commit the updated files (`.\a.ps1 c "..."`) before the release proceeds, then continue. This is a finding on the reindex report line; the sweep may run, but a PASS must not propose `/skill-release` until the fresh mirror is committed.
- **3** - verify gate failed on something regeneration cannot fix: a settings layout missing from `SettingsSearchLayoutCatalog`, an unannotated manifest key, or a drifted HOW_TO navigation path. **Hard release blocker** - fix at source (append the layout, annotate the key, sync the HOW_TO path), re-run this step. Do not proceed to step 1 on exit 3.
- **1** - infrastructure failure (gradle/render). Treat as sweep abort (exit 2 in step 4), same as any build-side failure.

Carry the reindex outcome into the step 4 verdict: exit 2 (uncommitted fresh mirror) or exit 3 (unfixed inconsistency) blocks a clean PASS just as a red log audit does.

### 1 - Pre-flight: single device, prepare, hard-grant permissions

**1.0 - Assert exactly one online device first.** Phantom offline `emulator-55xx` siblings (left by earlier AVD sessions) silently wreck sweep two ways: make `adb shell getprop` ambiguous, so `prerelease-prepare.ps1` reads API `0` and SKIPs `ACCESS_LOCAL_NETWORK` grant (S0614 defeated, every network scan then dies with `LocalNetworkPermissionDeniedException`); and make Maestro fail every flow with "device not connected" (exit 4). Clear them, resolve one id, pass that id to **every** helper below via `-DeviceId` - never rely on single-device auto-detect:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices                                  # if >1 line, kill/clear each 'offline' sibling:
& $adb -s emulator-5554 emu kill; & $adb disconnect emulator-5554
# repeat per offline id; assert exactly one 'device' (not 'offline') remains, capture it as $dev
```

**1.1 - Prepare emulator** (clean uninstall → install standard-debug → seed media → launch verify):

```powershell
pwsh -NoProfile -File scripts/devtest/prerelease-prepare.ps1 -DeviceId $dev -Json
```

Exit codes = abort signals: `1` adb missing, `2` no online device, `3` multiple devices, `10` a prepare stage failed. On any non-zero, abort with failing stage from JSON `stages` array. On `--dry-run`, print planned stages and stop here.

**1.2 - Hard-grant runtime permissions via adb** - do not trust prepare's API-gated grant (S0625). On API 33+/37 emulators both are runtime-gated and onboarding bypass skips in-app prompts, so network scan or local browse otherwise fails on clean install:

```powershell
& $adb -s $dev shell pm grant com.sza.fastmediasorter.debug android.permission.ACCESS_LOCAL_NETWORK
& $adb -s $dev shell appops set --uid com.sza.fastmediasorter.debug MANAGE_EXTERNAL_STORAGE allow
```

`MANAGE_EXTERNAL_STORAGE` pre-granted means "Требуются разрешения" dialog never blocks scenario; `ACCESS_LOCAL_NETWORK` mandatory even for public-internet SFTP (app gates all network listing behind it).

**1.3 - Launch real activity, not LeakCanary.** Debug build ships second LAUNCHER activity (`leakcanary.internal.activity.LeakLauncherActivity`), so `monkey -c LAUNCHER` / `resolve-activity` lands on LeakCanary or ResolverActivity. Launch explicitly:
`adb -s $dev shell am start -n com.sza.fastmediasorter.debug/com.sza.fastmediasorter.ui.main.MainActivity`.

### 2 - Configure resources + settings

Run adb-scriptable configuration (reachability pre-check honouring per-resource SKIP; resource import delegated to UI scenario below):

```powershell
pwsh -NoProfile -File scripts/devtest/prerelease-configure.ps1 -DeviceId $dev -Json
```

Its `set:Language` stage now applies language itself via supported **per-app** locale path (`cmd locale set-app-locales <pkg> --user current --locales ru`), verifies against current user, relaunches app - no manual locale step needed (S0626). On API < 33 stage SKIPs (per-app locale unavailable); genuine apply failure still exits 10.

Run config is `scripts/devtest/prerelease.config.psd1` (resource picks + reachability class + setting channels). Then drive UI via mobile-mcp for parts adb cannot do (resolve every target from `mobile_list_elements_on_screen`, never hard-coded coordinates):

- **Import resources:** Settings → expand "Авторизация и аккаунты" → type `sza.owner.trigger` value into "Default User" field (`id/etDefaultUser`) and submit; confirm "Импорт ресурсов / Добавить ресурсы SZA?" dialog (`id/button1` = Да). Reads APK-bundled `res/xml/sza_resources.xml` and registers SMB/SFTP/FTP rows (S0492 - only working import path; no adb intent-push).
- **DataStore settings:** apply each `Channel='ui'` setting (theme DARK, sort DATE_DESC, grid on, trash on / confirm off, accept-shared on) through Settings; relaunch after theme change.
- **Listing check:** open each `probe-and-list` resource and confirm its file list loaded via `BrowseLoadingManager: COMPLETE - N files loaded and displayed` log marker (`register-only` SMB resource verified as registered only, not listed).

### 3 - Drive core scenario

Start background logcat capture into `temp/S0484/run_<TS>.log` first (clear, then `adb logcat -v threadtime *:V`). **Use `threadtime`, not `time`** - `search-log.ps1` (and verdict's error count built on it) only parse `threadtime` line shape (pid+tid+package columns); a `-v time` capture parses to zero rows, so verdict silently reads `actionableErrors=0` and a screen full of red error toasts passes as clean log. Run revived Maestro capability suite as deterministic regression layer:

```powershell
$ts = Get-Date -Format yyyyMMdd_HHmmss
$maestroResults = "temp/S0484/maestro_suite_$ts.json"
pwsh -NoProfile -File maestro/run-tests.ps1 -Suite all -DeviceId $dev -Json > $maestroResults
```

Suite output off-context except compact JSON verdict. Treat non-zero runner exit as FAIL finding and keep `$maestroResults` as evidence for step 4. **Exception:** exit `4` with every flow logging `Device <id> was requested, but it is not connected` is infrastructure failure (phantom offline siblings - see 1.0), not app defects; clear devices, re-run suite, do not park `/spec-draft` for it. A quick `-Suite smoke` first confirms Maestro can drive device before full run. Use mobile-mcp only for new or exploratory paths without a Maestro flow; resolve every target from `mobile_list_elements_on_screen` immediately before acting. Capture screenshots only on FAIL to `temp/S0484/screens/` as evidence; screenshots no longer a pass gate.

Perf checkpoints still run where suite or exploratory path exercises surface:

1. **Cold start** - measured by pre-flight launch:
   `pwsh -NoProfile -File scripts/devtest/prerelease-measure.ps1 -Checkpoint cold-start -Json`.
2. **List scroll** - `dumpsys gfxinfo <pkg> reset`, scroll a populated list, then
   `prerelease-measure.ps1 -Checkpoint list-scroll -Json`. On emulator this record emitted `advisory`, does not release-gate.
3. **Player open** - pass measured open time from covered player flow or exploratory standalone-player roundtrip to
   `prerelease-measure.ps1 -Checkpoint player-open -ElapsedMs <n> -Json`.
4. **Network listing** - when reachable network resource is part of run, pass its measured listing open time to
   `prerelease-measure.ps1 -Checkpoint network-listing -ElapsedMs <n> -Json`.

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

Exit `0` = PASS, `1` = content FAIL, `2` = infrastructure abort. Write timestamped report to `temp/S0484/prerelease_<TS>.md` (device profile, per-stage results, verdict breakdown, evidence paths). Aggregate verdict is `reindex AND log AND perf AND maestro`; screenshots evidence-only. A step-0.7 exit 2 (fresh settings mirror not yet committed) or exit 3 (unfixed catalog/annotation/HOW_TO inconsistency) forces a non-PASS just as a red log audit does - record it on the reindex report line.

Verdict is coarse gate: produces one error count, hard-stops only on crashes/ANR. Does **not** enumerate which app errors fired, so a green verdict alone never proves run was clean. Always run detailed log audit below before trusting a PASS.

### 4.1 - Detailed log audit (mandatory, every run)

Verdict's log signal is a single number; red toasts and handled-but-loud failures hide behind it. Run deep audit over same log:

```powershell
pwsh -NoProfile -File scripts/devtest/prerelease-log-audit.ps1 -LogFile temp/S0484/run_<TS>.log -Json
```

Parses both logcat formats, keeps app-process lines, folds stack traces into throwing cluster, then splits clusters into **benign** (known emulator/capability fallbacks - Cast/Dynamite absent, `WifiRequiredException`, emulator GPU noise) and **actionable**, separately flags user-facing error surfaces (toast / snackbar / `showError`). Exit `0` = clean, `1` = actionable clusters and/or error toasts present (triage), `2` = log unreadable.

Treat every **actionable cluster** and every **error toast** as a finding even when machine verdict is PASS - a working stream that still throws a red toast (e.g. FTP active-mode fallback NPE during otherwise-fine audio playback) is a real defect. Fold audit's actionable clusters into report's verdict breakdown and into FAIL-branch `/spec-draft` triage below; an emulator-only benign cluster recurring every sweep is a candidate for audit's benign allowlist, not a ticket. Include audit JSON path in evidence pack.

**On PASS:** only after audit is clean (or findings triaged/parked) print report path and propose `/skill-release` as next step. **Do not auto-run it** - release starts only on explicit owner confirmation (ADR-1). State this in final line.

### 5 - Branch on FAIL

For each distinct defect verdict **or step-4.1 audit** surfaced (research/06):

- **Dedup first** by symptom via `scripts/spec_catalog/search.ps1` (error code / class / subsystem keyword + same-day created). If open ticket already covers it, reference that id; do not draft duplicate.
- Otherwise park one `/spec-draft` per distinct defect, capturing symptom + evidence (log lines, screenshot path under `temp/`) into §0.

For pending-test tickets (`BlockNeedUserTest`) whose flow this sweep exercised, route evidence through `/spec-check <Sxxxx>` - it owns status flip (`Verified` / `Partial` / `Broken`) and, on any transition leaving `BlockNeedUserTest`, the `Timber.d("Sxxxx:` tag removal. Sweep never flips status by guess and never deletes a tag for a ticket that stays `BlockNeedUserTest`. Tickets sweep did not exercise stay untouched.

### Final report

One line: `spec-prerelease: device <id>, verdict PASS/FAIL, report temp/S0484/prerelease_<TS>.md`
- on PASS append `/skill-release` proposal; on FAIL append parked ids + tickets routed to `/spec-check`. Append `stream-catalog: +N appended, M would-prune, re-upload <done|n.a.>` segment (or `stream-catalog: skipped (--dry-run)`). Append `deps: yt-dlp <pinned> → <latest|current>, bump <done|n.a.|reverted>, noLegal build <PASS|FAIL|n.a.>` segment (or `deps: skipped (--dry-run)`).
