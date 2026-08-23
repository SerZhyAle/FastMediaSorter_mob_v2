# /spec-prerelease - Reference

On-demand detail for the driver at `.claude/commands/spec-prerelease.md`. The driver runs the sweep on its own; read a section here only when the driver points at it.

## Overview

Automates `dev/PRE_RELEASE_MANUAL_TESTS.md` as one gated sweep on emulator: prepare clean standard-debug install with seeded media → configure resources + settings → run deterministic Maestro capability suite, use mobile-mcp only for uncovered exploratory paths → measure perf → aggregate machine PASS/FAIL verdict. PASS proposes `/skill-release`; FAIL parks deduped `/spec-draft` tickets and routes pending-test tickets through `/spec-check`. Steps 0 / 0.5 first refresh the mutable external content a release carries - downloadable stream-catalog delivery asset, then externally-rotting dependency pins (`yt-dlp`) - both content-only, no device, non-gating. Step 0.7 then reindexes the settings search + navigation mirror (regenerate-then-verify) - content-only, no device, but **gating**: the build must always ship a current settings index.

## Tool inventory

Composes existing tools - `scripts/devtest/prerelease-prepare.ps1`, `scripts/devtest/prerelease-configure.ps1`, `scripts/devtest/prerelease-measure.ps1`, `scripts/devtest/prerelease-verdict.ps1`, `scripts/devtest/prerelease-log-audit.ps1`, `scripts/utils/search-log.ps1`, `maestro/run-tests.ps1`, `scripts/streams/collect-stream-candidates.ps1`, `scripts/quality/reindex-settings.ps1`, mobile-mcp, `/skill-release`, `/spec-draft`, `/spec-check` - adds **no** app runtime code (S0484 ADR-2).

## 0 - Stream-catalog asset

Curated stream catalog `delivery/stream-catalog/streams.csv` ships as **mutable** GitHub Release asset, independent of app binary (S0588; `delivery/stream-catalog/README.md`). Refresh here so release carries fresh, live streams.

The append run keeps only `alive` rows and writes timestamped `temp/` backup before touching CSV.

The deep-signal probe pulls real media bytes, not just a playlist `200`, so "declared but not playing" streams are caught. Prints the `alive / dead / geo / unknown` breakdown and `Would prune N row(s)`, deletes nothing. Long run (~2000 rows) - launch in background, read the log tail.

`geo` = region-locked (HTTP 403/451 from the build machine) - kept, not counted as prunable.

### Pruning (human-gated, outside this sweep)

The deep-signal `-PruneDead` run drops `dead` + non-geo `unknown` (timeout / SSL / `401` / `5xx`) and **keeps** region-locked `geo` rows, tagging them `access=geo`. Review `temp/stream-catalog-liveness.csv`; only after review (ideally a second-network re-probe for the `unknown` rows) run `scripts/streams/collect-stream-candidates.ps1 -CatalogOnly -DeepSignal -PruneDead -Publish` manually, outside this sweep.

### Why the guarded packer is the only publish path

If `streams.csv` changed (append, or later manual prune), re-package and re-upload asset or change never reaches users - app fetches release asset, not repo file.

This bundles `streams.csv` **and** `favicon-atlas.png` and enforces the S0925 guard. A raw `Compress-Archive -Path .\streams.csv` ships a CSV-only zip with no atlas - the app then gets `atlasPng=null`, `FaviconAtlasStore.write(null, coords)` deletes the atlas and writes empty coords, and **every** channel loses its favicon app-wide (recurred 2026-07-12). `-SkipLiveness` skips the ~2489-URL probe and does not mutate the CSV, so the published pair stays consistent.

(Hosting / release tag in `delivery/stream-catalog/README.md`.)

## 0.5 - Dependency pins

Same slot rationale as step 0: refresh mutable external content before the release carries it.

### Why yt-dlp is the only inline bump (Tier A)

**Tier A - check and bump inline: `yt-dlp` only.** Its rot is server-side: extractors break because YouTube/Instagram change, not because our code changed, so a stale pin ships a broken link-download that no amount of our own testing would have caught. Pure-Python drop-in, so a bump cannot break Kotlin compile.

### Bump procedure

1. Read the current pin from the noLegal `pip { install("yt-dlp @ ...") }` block in `app_v2/build.gradle.kts` - never assume, the dated comment history above it records why the channel was chosen.
2. Stay on nightly only while a needed extractor fix is nightly-only - the pin comment names the fix, so it is checkable, not a vibe.
3. On bump: edit the pin, append one dated comment line stating the channel and the reason (matches existing S0190/S0950 comment style), and sync the doc pin in `docs/TECH_STACK.md` ("Sideload / XR-only surface").
4. A bump is `BlockNeedUserTest`-shaped: pip resolving proves nothing about extraction. Report it as needing a real link-download on device; do not claim it verified.

### Tier B check-only policy

**Tier B - check only, never bump: everything else** (Media3, Room, Glide, AGP, Kotlin, AndroidX, cloud SDKs, and `NewPipeExtractor` despite it rotting the same way as yt-dlp - it is a Java dep whose API surface can break compile).

- There is no version catalog and no `dependencyUpdates` task here - pins are hand-written in `app_v2/build.gradle.kts`. Do **not** hand-sweep every pin each release; that is unbounded work with no gate behind it.
- Check a Tier B pin only when this sweep's own evidence points at it (audit cluster, crash, perf record naming the library). Then park `/spec-draft` with that evidence - do not edit the pin.
- `scripts/check-doc-vs-gradle.ps1` is an internal docs-vs-Gradle consistency check, not an upstream freshness check. Non-zero exit here means our own docs drifted; fix the doc line, not the pin.

## 0.7 - Why the settings reindex is unconditional

The in-app settings search index is rebuilt at runtime by scanning `SettingsSearchLayoutCatalog` and routing hits through `SettingsSearchTabMapping`; nothing is serialized into the APK. So what the build "ships" for search - and, above all, for the *navigation to a setting* - is only as current as the layout catalog, the tab mapping, and the doc mirror (`settings-manifest.json` + `SETTINGS_REFERENCE*.md` + annotations + HOW_TO paths). A stale mirror or an unindexed screen makes the shipped search silently miss settings or fail to navigate to them.

## 1 - Pre-flight detail

### 1.0 - What phantom devices break

Phantom offline `emulator-55xx` siblings (left by earlier AVD sessions) silently wreck sweep two ways: make `adb shell getprop` ambiguous, so `prerelease-prepare.ps1` reads API `0` and SKIPs `ACCESS_LOCAL_NETWORK` grant (S0614 defeated, every network scan then dies with `LocalNetworkPermissionDeniedException`); and make Maestro fail every flow with "device not connected" (exit 4).

### 1.1 - What prepare does

Clean uninstall → install standard-debug → seed media → launch verify.

### 1.2 - Why both grants are hard-granted

On API 33+/37 emulators both are runtime-gated and onboarding bypass skips in-app prompts, so network scan or local browse otherwise fails on clean install.

`MANAGE_EXTERNAL_STORAGE` pre-granted means "Требуются разрешения" dialog never blocks scenario; `ACCESS_LOCAL_NETWORK` mandatory even for public-internet SFTP (app gates all network listing behind it).

### 1.3 - Why the explicit component name

Debug build ships second LAUNCHER activity (`leakcanary.internal.activity.LeakLauncherActivity`), so `monkey -c LAUNCHER` / `resolve-activity` lands on LeakCanary or ResolverActivity.

## 2 - Configure detail

Why the OWNER_TRIGGER path is the entry requirement: S0492 - no adb-scriptable import; former `file://` intent-push always failed on minSdk 26.

Run adb-scriptable configuration (reachability pre-check honouring per-resource SKIP; resource import delegated to UI scenario below).

Its `set:Language` stage now applies language itself via supported **per-app** locale path (`cmd locale set-app-locales <pkg> --user current --locales ru`), verifies against current user, relaunches app - no manual locale step needed (S0626).

Run config is `scripts/devtest/prerelease.config.psd1` (resource picks + reachability class + setting channels).

### UI checklist

Then drive UI via mobile-mcp for parts adb cannot do (resolve every target from `mobile_list_elements_on_screen`, never hard-coded coordinates):

- **Import resources:** Settings → expand "Авторизация и аккаунты" → type `sza.owner.trigger` value into "Default User" field (`id/etDefaultUser`) and submit; confirm "Импорт ресурсов / Добавить ресурсы SZA?" dialog (`id/button1` = Да). Reads APK-bundled `res/xml/sza_resources.xml` and registers SMB/SFTP/FTP rows (S0492 - only working import path; no adb intent-push).
- **DataStore settings:** apply each `Channel='ui'` setting (theme DARK, sort DATE_DESC, grid on, trash on / confirm off, accept-shared on) through Settings; relaunch after theme change.
- **Listing check:** open each `probe-and-list` resource and confirm its file list loaded via `BrowseLoadingManager: COMPLETE - N files loaded and displayed` log marker (`register-only` SMB resource verified as registered only, not listed).

## 3 - Scenario detail

### Why the capture must be `threadtime`

`search-log.ps1` (and verdict's error count built on it) only parse `threadtime` line shape (pid+tid+package columns); a `-v time` capture parses to zero rows, so verdict silently reads `actionableErrors=0` and a screen full of red error toasts passes as clean log.

### Maestro suite handling

Run revived Maestro capability suite as deterministic regression layer. Suite output off-context except compact JSON verdict.

**Exception:** exit `4` with every flow logging `Device <id> was requested, but it is not connected` is infrastructure failure (phantom offline siblings - see 1.0), not app defects; clear devices, re-run suite, do not park `/spec-draft` for it.

A quick `-Suite smoke` first confirms Maestro can drive device before full run. Use mobile-mcp only for new or exploratory paths without a Maestro flow; resolve every target from `mobile_list_elements_on_screen` immediately before acting.

### Perf checkpoints

1. **Cold start** - measured by pre-flight launch:
   `pwsh -NoProfile -File scripts/devtest/prerelease-measure.ps1 -Checkpoint cold-start -Json`.
2. **List scroll** - `dumpsys gfxinfo <pkg> reset`, scroll a populated list, then
   `prerelease-measure.ps1 -Checkpoint list-scroll -Json`. On emulator this record emitted `advisory`, does not release-gate.
3. **Player open** - pass measured open time from covered player flow or exploratory standalone-player roundtrip to
   `prerelease-measure.ps1 -Checkpoint player-open -ElapsedMs <n> -Json`.
4. **Network listing** - when reachable network resource is part of run, pass its measured listing open time to
   `prerelease-measure.ps1 -Checkpoint network-listing -ElapsedMs <n> -Json`.

## 4 - Why the verdict alone is not proof

Verdict is coarse gate: produces one error count, hard-stops only on crashes/ANR. Does **not** enumerate which app errors fired, so a green verdict alone never proves run was clean.

## 4.1 - What the log audit does

Verdict's log signal is a single number; red toasts and handled-but-loud failures hide behind it.

Parses both logcat formats, keeps app-process lines, folds stack traces into throwing cluster, then splits clusters into **benign** (known emulator/capability fallbacks - Cast/Dynamite absent, `WifiRequiredException`, emulator GPU noise) and **actionable**, separately flags user-facing error surfaces (toast / snackbar / `showError`).

"Keeps app-process lines" means the pid column, matched against the process ids the capture itself announces in `Start proc <pid>:com.sza.fastmediasorter` - the same recovery `search-log.ps1 -AppOnly` performs, sharing `Get-AppPidsFromLog` (S1332). A line from another pid is not classified at all, whatever it says: attribution is a different question from the benign allowlists below, which silence a cluster by identity. The tag denylists still run after the pid filter, so a cluster suppressed by tag stays suppressed.

The audit reports which rule decided, as `attribution` in the JSON and as a header line in text mode. `pid` is the normal case. `heuristic` means the capture carries no app `Start proc` announcement - it began after the app was already running - so lines were kept by tag alone, and a tag denylist is enumerative: an unlisted tag reaches the actionable list even from another process. Read an `exit 1` under `heuristic` as "these clusters may not be ours" and re-capture with the app restart inside the window before triaging (S1859: two `E/A` clusters from Google's tiktok tracing framework failed the mandatory step on a run where the app logged nothing wrong).

A working stream that still throws a red toast (e.g. FTP active-mode fallback NPE during otherwise-fine audio playback) is a real defect.

An emulator-only benign cluster recurring every sweep is a candidate for audit's benign allowlist, not a ticket.

A framework error the app already handled is suppressed **conditionally**, never by tag alone (S1700): the thumbnail chain `FrameDecoder err -1004` / `StagefrightMetadataRetriever` / `MetadataRetrieverClient` / `MediaMetadataRetrieverJNI` counts as benign in both the verdict and the audit only while the same capture carries the app's own `NetworkVideoFrameDecoder` timeout marker. The JNI shim runs inside the app process, so `-AppOnly` attributes it to us; without the paired marker the same chain means local decoding broke and still fails the gate.

The same shape covers the emulator's graphics stack (S1969): `E/FrameEvents: addRelease: Did not find frame` is benign in both the verdict and the audit only while the same capture carries `EGL_emulation`. `libgui` emits it from inside the app process during a window transition, so pid attribution keeps it, and one such line ended the v033 sweep at `pass=false` with 22/22 Maestro, no toast, no crash and no ANR. The marker decides because only the emulator's GLES translator writes it - on a physical device it appears in no line at all, and there a missed frame release stays a real defect. Both directions are pinned by fixtures in `scripts/devtest/prerelease-log-audit.tests/`.

## Final report segments

Appended to the one-line verdict, verbatim shapes:

Append `stream-catalog: +N appended, alive/dead/geo/unknown A/D/G/U, M would-prune, re-upload <done|n.a.>` segment (or `stream-catalog: skipped (--dry-run)`). Append `deps: yt-dlp <pinned> → <latest|current>, bump <done|n.a.|reverted>, noLegal build <PASS|FAIL|n.a.>` segment (or `deps: skipped (--dry-run)`).
