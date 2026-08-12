# Standard Production Release Readiness Gate

Canonical readiness contract for the **standard** Google Play production build (signed `standardRelease` AAB). This document is the single source of truth for what must be proven before publishing to Production. It is an engineer/operator gate, not a marketing surface.

- **Strategic spec:** `PLAN/S0553_standard-production-release-readiness.md`
- **Flavor scope:** `standard` only (not vr / noLegal / lite / photos / legacy / wear-only).
- **Distribution target:** Google Play production.
- **Platform baseline:** minSdk 26, targetSdk 35.
- **Mechanical gate:** `scripts/release/standard-release-gate.ps1` folds the checks below into one PASS/FAIL/WAIVED verdict.

Owner policy (S0553 §3.3, 2026-06-20):

- Blockers are **tiered** - flavor-surface regressions, release-only technical regressions, and operational/policy failures hard-stop; coverage losses and temporary subsystem limits are waiver-eligible with a recorded note.
- Minimum device matrix is **one recent phone**; the API/OEM-diversity gap is a recorded coverage gap, not a silent PASS.
- Cast and Wear are **best-effort waiver** - physically verified only when a change touches those subsystems.
- Play Console gate: **clean Pre-launch report**; known non-user-facing warnings only with a recorded waiver.
- Waivers live in `store_assets/release_waivers/<versionName>.md`.

---

## Target surface

The standard production build must preserve the full market-compatible user surface declared for standard. The machine-readable baseline is derived from two sources only (research/01); the curated `docs/FEATURES.md` showcase is **not** consulted for the gate.

- **Source of truth:** `docs/ALL_FEATURES.jsonl` records with `flavors` containing `"standard"` and `status` = `"active"`, cross-checked against the `standard` flavor BuildConfig matrix in `app_v2/build.gradle.kts`.
- **Snapshot tool:** `scripts/release/standard-surface-snapshot.ps1` emits the machine-readable snapshot (`temp/standard-surface-snapshot.json`) and, with `-CheckRegressions`, flags any standard-supported capability whose gating flag is `false`/absent (candidate flavor-surface regression - see Release-risk audit).

Standard capability baseline (gated by the BuildConfig flags shown):

- Local + SMB + FTP + SFTP resources (`SUPPORT_LOCAL_NETWORK`) and cloud resources + auth (`SUPPORT_CLOUD`).
- Browse / filter / sort / copy / move / rename / delete / undo flows.
- Video (`SUPPORT_VIDEO`), audio + persistent playback (`SUPPORT_AUDIO`, `ENABLE_PERSISTENT_AUDIO_PLAYBACK`), images (`SUPPORT_IMAGES`), PDF / EPUB / text (`SUPPORT_DOCUMENTS`, `ENABLE_EPUB`).
- OCR and translation (`ENABLE_TRANSLATION`) and downloadable extensions allowed on the market path.
- Chromecast (`SUPPORT_CAST`).
- Notification + default-player integration (`SUPPORTS_DEFAULT_PLAYER`) and quick widgets.
- Usage statistics, settings search, backup/restore, send-to surface.
- Wear companion (`SUPPORT_WEAR_COMPANION`).
- Animations (`ENABLE_ANIMATIONS`), mic recording (`SUPPORT_MIC_RECORDING`).
- DTS decode - no BuildConfig flag gates it (S1057). A build ships DTS when `libs/fms-ffmpeg-dts.aar` is on its dependency list, and decodes at runtime once the FFmpeg payload is present - bundled, or installed on demand and attached via `DeliverableSet.FFMPEG_DTS`.

A capability present in the snapshot but missing/non-functional in the built `standardRelease` is a candidate regression, classified under Release-risk audit.

---

## Intentional exclusions

These surfaces are absent from standard **by design** (S0553 §5.1). Their absence is documented and is never a regression - the gate must not flag them, and the team must not "fix" them:

- `noLegal`-only capabilities excluded from market builds by policy / licensing / heavy-runtime reasons (`IS_NO_LEGAL_FLAVOR = false` in standard).
- VR immersive player surface (`SUPPORT_VR_PLAYER = false` in standard).
- noLegal screen-gesture screenshot overlay and adjacent sideload-only workflows.
- APK install from browse, heavy diagnostics/fingerprinting surface, and other store-incompatible seams.
- Owner-only debug tooling, test-import helpers, integration-test UI, debug package identity (`.debug` applicationId suffix), and debug-only credentials.

---

## Release-risk audit

Losses that appear only on `standardRelease` versus `standardDebug` (S0553 §5.3). The release build is R8-minified + resource-shrunk + release-signed (`isMinifyEnabled=true`, `isShrinkResources=true`); the debug build is not - so these breakages are invisible to debug smoke and must be proven on the minified artifact. Automated check: `scripts/release/standard-release-smoke.ps1`.

### R8 / resource-shrink seams

`standard-release-smoke.ps1` cold-launches the minified APK and scans the launch logcat for these fatal markers (any hit = hard-stop regression):

- `ClassNotFoundException`, `NoClassDefFoundError` - reflection / serialization / DI class stripped or renamed.
- `NoSuchMethodError`, `VerifyError` - method inlined/removed or signature mangled.
- `Resources$NotFoundException` - resource shrunk away (menu / icon / string / layout reachability).
- `FATAL EXCEPTION` - any launch crash.

Manual spot-checks on the release artifact for seams the launch smoke does not reach: OAuth callback round-trip, deep links, JS bridge, optional/downloadable modules.

### Release signing / auth / manifest seams

`standard-release-smoke.ps1 -CheckSeams` statically asserts (no device): production Dropbox app key resolves for release, no debug `applicationIdSuffix` leaks into release (package stays `com.sza.fastmediasorter` so it matches production OAuth registrations), the debug Dropbox key never appears in release, and the MSAL (OneDrive) dependency is present. Manual operator check: release signing fingerprint matches the production OAuth / MSAL / Dropbox / AppAuth registrations and the Play upload key.

### targetSdk 35 behavior checks

- Background work: WorkManager / foreground-service starts comply with Android 14/15 restrictions; declared foreground-service types are correct.
- Permissions: photo/media access uses the selected-photos / granular media model; no legacy broad storage assumptions.
- Permission parity (mechanical, blocks the release on failure). The manual permission audit this list used to carry is replaced by `PermissionRegistryManifestParityTest`, which compares the merged manifest against the permission registry in both directions and names the offending permission when they disagree. Run it on the four variants where the permission composition actually differs - the build-type axis, the flavor axis and the install-from-file axis:

  ```powershell
  .\gradlew.bat :app_v2:testStandardReleaseUnitTest --tests "*PermissionRegistryManifestParityTest"
  .\gradlew.bat :app_v2:testLiteDebugUnitTest      --tests "*PermissionRegistryManifestParityTest"
  .\gradlew.bat :app_v2:testPhotosDebugUnitTest    --tests "*PermissionRegistryManifestParityTest"
  .\gradlew.bat :app_v2:testNoLegalDebugUnitTest   --tests "*PermissionRegistryManifestParityTest"
  ```

  `photos` was missing from this list until S1454/S1460, and that omission is precisely why its half of the divergence went unseen: its composition is the narrowest of all - no launcher, no video, no documents, no microphone - so it is the variant most likely to declare a permission nothing behind it can use.

  A failure is a release blocker, not a note: either the build declares a permission no screen can show the user, or a screen offers to grant one the build does not hold. Fix the registry row or the manifest; an entry in `PermissionManifestExemptions` is the third option and needs a written reason.
- Battery optimization: persistent audio + scheduled operations survive Doze / app-standby as designed.
- Network security: production cleartext / trust-anchor policy does not change runtime behavior versus debug.

### Diagnostics and deobfuscation retention (research/02)

- Baseline diagnostics = the existing in-app crash/log export path (`core/logging/LogExportHelper.kt`, `CrashReportPromptManager`, `SupportIntentFactory`). An external crash sink is out of scope for this gate.
- The release build emits the deobfuscation artifacts automatically: R8 `mapping.txt` (from `isMinifyEnabled`) and native symbols (`ndk.debugSymbolLevel = "FULL"`).
- **Required:** `mapping.txt` and the native symbols are retained and keyed by `versionCode` for every production release (uploaded to Play Console / archived). Missing retention = operational loss (post-release triage capability).

### Release logging privacy line (research/03)

Forbidden in release logs (= leak, blocker): device identifiers (serial / IMEI / MAC / Android ID), credentials / tokens / emails, user content file names and full paths, full SMB/SFTP/FTP/cloud URIs (host+share+path), location. Acceptable: operation type, error class/code, counts, durations, capability-fallback notices at the correct level. Release already mutes `LOG_SMB_IO` / `LOG_NETWORK_THUMBNAILS` / `LOG_LINK_DOWNLOAD`.

---

## Coverage matrix

Per capability group: coverage status and the evidence level that backs it. Machine-readable source: `docs/release/standard-coverage-matrix.json` (this table renders it). Coverage that is `not`/`partial` is an explicit, recorded state - never silently promoted to PASS (ADR-2).

| Capability group | Coverage | Evidence level | Note |
|---|---|---|---|
| local + SMB + SFTP + FTP | covered | emulator-spine | SMB register-only behind emulator NAT - verify on real LAN |
| cloud + auth (OAuth/MSAL/Dropbox) | partial | manual-device | release-identity OAuth callbacks proven on release signing |
| video | covered | emulator-spine | |
| audio + persistent playback | partial | manual-device | persistent notification + Doze survival is manual |
| image | covered | emulator-spine | |
| PDF / EPUB / text | covered | emulator-spine | |
| OCR | partial | manual-device | engine-install dependent; expected fallback otherwise |
| translation | partial | manual-device | ML Kit downloadable model path |
| Cast | partial | manual-device | best-effort waiver (§3.3) |
| Wear companion | partial | manual-device | best-effort waiver (§3.3) |
| widgets + default-player | partial | manual-device | |
| statistics + backup + settings search | covered | emulator-spine | |
| R8 / shrink integrity | covered | release-build | `standard-release-smoke.ps1` |
| surface vs baseline | covered | static | `standard-surface-snapshot.ps1 -CheckRegressions` |
| VR immersive player | intentionally-excluded | static | `SUPPORT_VR_PLAYER=false` |
| noLegal-only capabilities | intentionally-excluded | static | `IS_NO_LEGAL_FLAVOR=false` |

**Recorded coverage gap (S0553 §3.3):** the minimum device matrix is a **single recent phone**. API-level diversity (26-30 vs 33-35), OEM behavior, D-pad/TV and multi-form-factor are **unproven by design**. This is an accepted, recorded gap, not a silent PASS (ADR-2).

### Evidence ladder

What each level proves (weakest to strongest scope):

- **static** - source/config truth without building (surface snapshot, seam parse, manifest checks).
- **fast-build** - the project compiles (`a.ps1 fk`/`fc`); symbols resolve. No runtime proof.
- **release-build** - the minified, signed `standardRelease` builds and cold-launches without R8/shrink breakage (`standard-release-smoke.ps1`).
- **emulator-spine** - the `/spec-prerelease` scriptable spine: clean install, resource reachability, core playback, cold-start perf, log verdict on an emulator (standard debug).
- **manual-device** - a human exercises the flow on a real phone (auth round-trips, notifications, Cast/Wear, widgets, PiP).
- **play-console** - Internal Testing + Pre-launch report + Data Safety + store listing reviewed in Play Console.

Risk bucket -> required minimum evidence (strategic §6):

- §6.1 flavor boundary drift -> static (`-CheckRegressions`) + the surface baseline section.
- §6.2 release-vs-debug drift -> release-build (`standard-release-smoke.ps1`) + manual-device auth spot check.
- §6.3 Play-policy drift -> play-console (operator checklist, Data Safety, Pre-launch) + the mechanical permission parity check below, which replaced the manual permission audit.
- §6.4 coverage illusion -> the explicit matrix above; gaps stay visible, never auto-PASS.
- §6.5 production diagnostics gap -> static (mapping/symbol retention policy) + the evidence pack.

---

## Operator evidence pack

Artifacts that must exist after release preparation (S0553 §8.4). "Build succeeded" is not evidence; these are.

Required artifacts and where they live:

- Release smoke verdict - `scripts/release/standard-release-smoke.ps1 -Json` output, archived under `temp/`.
- Surface snapshot - `temp/standard-surface-snapshot.json` (`standard-surface-snapshot.ps1`).
- Coverage manifest - `docs/release/standard-coverage-matrix.json` (versioned).
- Screenshots of key screens - under `temp/` for the run.
- Play Console Pre-launch report link.
- Data Safety review note (reviewed for this release).
- Signing fingerprint confirmation - release upload key matches the production registration.
- Deobfuscation: `mapping.txt` + native symbols, keyed by `versionCode`, uploaded to Play Console / archived (research/02).
- Waiver file - `store_assets/release_waivers/<versionName>.md` for any waiver-eligible gap.

Operator checklist: `store_assets/PLAY_CONSOLE_CHECKLIST.md` is the Play-side operator slice of this gate (listing texts, graphics, category, monitoring). Waivers live in `store_assets/release_waivers/` (see its `README.md`).

**Play gate strictness (§3.3):** a clean Pre-launch report is required. Known non-user-facing warnings (e.g. emulator-only crashes, ad-SDK noise) are allowed only with a recorded waiver entry.

---

## Verdict contract

One verdict for a standard production release, produced by `scripts/release/standard-release-gate.ps1` (the mechanical gate). It folds the surface check, release smoke, coverage manifest and the per-release waiver file into a single result:

- **PASS** (exit 0) - no hard-stop signal and no outstanding waiver-eligible gap. Safe to publish.
- **WAIVED** (exit 3) - no hard-stop signal; waiver-eligible gaps exist and a per-release waiver file records owner acceptance. Publishable with the waiver on record.
- **FAIL** (exit 1) - a hard-stop signal is present, OR waiver-eligible gaps exist with no waiver file (gaps must not be silently passed - ADR-2).
- **infra abort** (exit 2) - the gate's own inputs are unreadable; not a product verdict.

Tiered blocker policy (S0553 §3.3):

- **Hard-stop (never waiver-eligible):** flavor-surface regressions (§5.2), release-only technical regressions (§5.3, incl. R8/shrink breakage), operational/policy failures (§5.5, §6.3). Any of these -> FAIL.
- **Waiver-eligible (recorded note required):** coverage losses (§5.4) and temporary subsystem limits (e.g. Cast/Wear best-effort, single-device matrix). These -> WAIVED only with a waiver entry, else FAIL.
- **Intentional exclusions (§5.1):** not losses; never affect the verdict.

Waiver authority: only the **owner** may approve a waiver. A sufficient waiver record = loss-class reference + author + date, stored in `store_assets/release_waivers/<versionName>.md` (see its `README.md`). The gate reads that file to convert a waiver-eligible FAIL into WAIVED.
