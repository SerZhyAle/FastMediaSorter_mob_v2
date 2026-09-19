# FastMediaSorter v2 - Project Operations Index

Last Updated: 2026-09-11
Purpose: single entrypoint for fast research and navigation.

## 1) Workspace Topology
- Main app: `app_v2/`
- Wear companion: `wear/`
- Engineering process/rules/contracts: `dev/`
- Product/docs: `docs/`
- Specs, roadmaps, proposals, feature plans: `PLAN/`
- Automation scripts: `scripts/`
- Temporary artifacts only: `temp/`
- Read-only zones: `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`
- Branch model: `main` = release-stable only; development in `DEBUG-v0NN` branches. What a push to one does and does not trigger: `docs/BUILD_VS_RELEASE.md`; how a release moves to the next branch: `docs/HOW_TO_DEVELOP_AND_RELEASE-ru.md`.

## 2) Source Layout (Main App)
Root package: `app_v2/src/main/java/com/sza/fastmediasorter/`

- `ui/` - screens/fragments/compose + ViewModels (no business logic)
- `domain/` - use cases and domain abstractions/interfaces
- `data/` - repositories/data sources/network/db adapters
- `di/` - Hilt DI modules
- `core/`, `util/`, `utils/`, `worker/`, `widget/` - shared infra/background/widget
- `FastMediaSorterApp.kt` - application entrypoint

Data flow rule: `UI -> ViewModel -> UseCase -> Repository -> DataSource`

## 3) Source Layout (Wear)
Root package: `wear/src/main/java/com/sza/fastmediasorter/wear/`

- `ui/`, `domain/`, `data/`, `di/` - same layering principles
- `MainActivity.kt` - thin host activity
- `FastMediaSorterWearApp.kt` - wear application entrypoint

## 4) Build & Variants (Source of Truth)
- Modules included: `settings.gradle.kts` -> `:app_v2`, `:wear`
- Main build config: `app_v2/build.gradle.kts`
- Wear build config: `wear/build.gradle.kts`
- SDK / Java baseline: compileSdk 37, default minSdk 26 (Android 8+), Java 17. Several flavors override `minSdk` - read the `minSdk` row of `docs/FLAVOR_MATRIX.md`, never a list written here
- Flavors (main app): `standard`, `noLegal`, `lite`, `photos`, `legacy`, `vr`, `foss`. Which capability each one carries: `docs/FLAVOR_MATRIX.md`, generated from the `productFlavors` block - never answer a flavor question from memory (S1392)
- Watch flavors: `standard` (what Play accepts) and `noLegal` (sideload), declared in `wear/build.gradle.kts` since S2090, so every watch gradle task and output path carries a flavor segment

Dependency version policy:
- First check `gradle/libs.versions.toml`
- If absent, treat module Gradle files as authoritative

## 5) Fast Commands
Every gradle-backed command goes through `a.ps1`, never `gradlew.bat` directly: the launcher takes the `Build.Phone` / `Build.Wear` lock (CLAUDE.md Rule 23), and a bare gradle call races a sibling session's build. From the Bash tool: `pwsh -NoProfile -File ./a.ps1 <target>`. Full target list: CLAUDE.md section 9; which one fits which change and what each costs: `docs/BUILD_TEST_FAST_PATH.md`.

- Phone debug build: `./a.ps1 d` (`dq` quiet, `dav` timestamped version, `cd` clean)
- Fast Kotlin compile: `./a.ps1 fk` (`fkn` for noLegal)
- Fast resources/manifest check: `./a.ps1 fr`; code + resources: `./a.ps1 fc`
- Unit tests for your own change: `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "<pattern>"`; the full suite `./a.ps1 fu` holds `Build.Phone` for minutes
- Fast static gates batch: `./a.ps1 fg`
- Lint + typos: `./a.ps1 ch`
- Watch debug build: `./a.ps1 wd` (builds `:wear:assembleStandardDebug`; `iw` builds noLegal and installs it)
- Watch fast checks: `./a.ps1 fw` (Kotlin), `fwr` (resources/manifest), `fwu` (unit suite) - the phone targets exit 0 without compiling a single watch file
- Show current branch: `git branch --show-current`
- Branch creation and the merge to `main` belong to the release flow (`/skill-release`, which builds in a dedicated worktree), never to a hand-typed merge

## 6) Mandatory Constraints
- Never write generated files/logs/backups to project root; use `temp/`
- Keep activity logic minimal; move complex logic to manager/helper classes
- Use `Timber`; avoid `Log.d()`
- If modifying a file >500 lines, create timestamped backup in `temp/Sxxxx/` (or `temp/scratch/` with no ticket)

## 7) Research Routing (What to Open First)
- Architecture/data flow: `docs/ARCHITECTURE.md`
- Build/scripts/flavors/flags: `docs/DEV_OPS.md` + module `build.gradle.kts`
- Libraries/protocol specifics: `docs/TECH_STACK.md`
- Full tech stack, dependencies, constraints, min/recommended requirements: `dev/TECH_REQUIREMENTS.md`
- Process and phase gating: `dev/AGENT_WORKFLOW.md`
- Agent-session cost discipline (spawn policy, context hygiene, skill tiers, MCP usage, measurement loop): `docs/AGENT_COST_PLAYBOOK.md` (S0816)
- Which physical device / emulator / workstation may be acted on, and how far: `docs/DEVICE_FLEET.md` (CLAUDE.md Rule 35) - read it before the first `adb` call of a task; authorization is per device, and it is written nowhere else
- Device profile presets / first-run onboarding: `dev/DEVICE_PROFILE_PRESET_MATRIX.md` (matrix data: `app_v2/src/main/assets/device_profile_presets.csv`; consistency guard: `scripts/check_device_profile_presets.ps1`)
- Usability review of accumulated test screenshots (secondary UI findings unrelated to what each frame was shot for): `.claude/skills/screenshot-usability-audit/SKILL.md` is the entry point; discovery and non-signal pre-filter via `scripts/devtest/find-recent-screenshots.ps1`; each run writes its own dated `dev/SCREENSHOT_USABILITY_AUDIT_<YYYY-MM-DD>.md` (S2108)
- Feature specs, roadmaps, proposals: `PLAN/` folder
- Feature inventory (source of truth, every shipped capability, EN-only): `docs/ALL_FEATURES.jsonl` - write via `scripts/all_features/add.ps1`, validate via `scripts/all_features/validate.ps1` (S0489). Replaced the retired `dev/FUNCTIONALITY.log`; chronology lives in git history + release diffs (`scripts/all_features/diff.ps1`). `docs/FEATURES*` is the curated public showcase, populated only by `/skill-release`.
- Standard production release readiness gate: `docs/RELEASE_READINESS_STANDARD.md` (single verdict via `scripts/release/standard-release-gate.ps1`; operator slice `store_assets/PLAY_CONSOLE_CHECKLIST.md`; waivers `store_assets/release_waivers/`).
- Documentation map: `docs/DOCS_MAP.md`
- Documentation registry: `docs/DOCUMENT_REGISTRY.jsonl` is the source of truth for maintained documents, site pages, owners, update triggers, and publication state. Query it with `scripts/document_registry/query.ps1`; validate with `scripts/document_registry/validate.ps1`; regenerate/check derived views with `scripts/document_registry/generate.ps1 [-Check]`.
- **Activity entry points** (navigation anchors, intents, deeplinks): `dev/ACTIVITY_CATALOG/` - query via `pwsh -NoProfile -File dev/ACTIVITY_CATALOG/scripts/query.ps1 -Module app_v2 -Search "<keyword>"` or browse `app_v2.md` / `wear.md`.
- **Kotlin classes by named product sector** (S1344): `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Sectors` lists them, `-Sector <name>` returns the whole sector across `ui`, `domain` and `data` without guessing a search word. A sector composes with every other filter (`-Sector player -Layer ui`). Definitions are hand-authored in `dev/CATALOG/sectors.json` and stamped onto records by `catalog_sync.ps1`; `dev/CATALOG/<module>.jsonl` stays a gitignored generated index.
- **The catalog-before-grep gate is mechanical, not advisory** (S1344): a `PreToolUse` hook refuses an *unnarrowed* Kotlin search - a `Grep`/`Glob` targeting `.kt` with no `path` and no directory-naming `glob` - until `query.ps1` has run once in the session. A search that already names a subtree is never blocked, and nothing outside `.kt` is in scope. The refusal prints the exact `query.ps1` command to run instead. Hook: `.claude/hooks/guard-catalog-before-kt-search.ps1`, armed each session by `.claude/hooks/reset-catalog-touch-marker.ps1`, proved by `.claude/hooks/tests/run-guard-catalog-cases.ps1`.
- **An empty search result is not proof of absence** (S1599): a `PostToolUse` hook watches `Grep` calls that returned nothing *and* carried a `path`. It re-runs the same pattern once at the repository root - keeping `glob` and `type`, dropping only `path` - and attaches the count and top files as additional context. It is deliberately asymmetric: **if the widened re-run also finds nothing it says nothing**, so it can only ever speak when the original "not found" would have been wrong, and a false positive is impossible by construction. Absence checks where zero is the correct answer (probe-tag sweeps, `TODO(phase-NN)`, Rule 19 banned-API sweeps) are suppressed by a data-driven list at the top of the hook. It fails silent on any error, because a hook that changes what the agent *reads* corrupts reasoning rather than merely gating a call. Measured basis: 651 of 4,297 `Grep` calls in the week of 2026-08-05 returned nothing, 93.9% of them path-scoped, and an unscoped `Grep` missed zero times (`PLAN/S1599_grep-search-series-and-misses/research/01__zero-hit-anatomy.md`). Hook: `.claude/hooks/observe-empty-grep.ps1`, registered behind a stdin pre-filter in `.claude/settings.json`, proved by `.claude/hooks/tests/Run-ObserveEmptyGrep-Tests.ps1` - which tests **reachability of that pre-filter separately from correctness of the hook**, because an unreachable hook is indistinguishable from one that allows everything.

## 8) Quick Start Research Checklist
1. Confirm target module (`app_v2` or `wear`) and flavor impact.
2. **Which Activity handles X?** → query `dev/ACTIVITY_CATALOG/` first (`query.ps1 -Search "<keyword>"`), then locate the feature area in `ui/domain/data` path.
2a. **Which classes make up feature X?** → `dev/CATALOG/scripts/query.ps1 -Sector <name>` (see section 7). A repo-wide `.kt` search before this is refused by the gate, not merely discouraged.
3. Validate constraints from this file + `.github/copilot-instructions.md`.
4. Open the one domain-specific doc from section 7.
5. Only then inspect implementation files.

## 9) Feature-to-Path Map (Fast Jump)

Main app (`app_v2/src/main/java/com/sza/fastmediasorter/`):

- App entry/bootstrap:
	- `ui/main/` (entry flow, resources list, routing)
	- `ui/main/MainActivity.kt`, `ui/main/MainViewModel.kt`

- Browse/media list operations:
	- `ui/browse/`
	- `ui/browse/managers/`, `ui/browse/filelist/`, `ui/browse/loading/`, `ui/browse/selection/`, `ui/browse/undo/`

- Player/playback/doc viewing:
	- `ui/player/`
	- `ui/player/helpers/` (delegated heavy logic)
	- `ui/player/render/`, `ui/player/views/`, `ui/player/callbacks/`
	- Primary host: `ui/player/PlayerActivity.kt`

- Settings/preferences:
	- `ui/settings/`
	- `ui/settings/fragments/`
	- Primary files: `ui/settings/SettingsActivity.kt`, `ui/settings/SettingsViewModel.kt`

- Lights and screen-as-a-surface programs (front flashlight, water flashlight, physical flash, black screen):
	- `ui/flashlight/` (`FrontFlashlightActivity.kt` the screen lamp, `WaterFlashlightActivity.kt` the touch-locked one, `FlashlightToggleActivity.kt` the screenless torch trampoline)
	- `ui/blackscreen/`
	- Torch hardware: `core/screencapture/gesture/DeviceActionHandler.kt` (`setTorch` / `toggleFlashlight`)
	- Watch half: `wear/.../ui/apps/waterflashlight/`
	- Which surfaces each one appears on: `core/panel/SubProgramCatalog.kt`

- Sub-programs (the calculator, stopwatch, mini-game, network monitor, recorder, lights, camera routes, watch companion) and the surfaces that offer them:
	- `core/panel/` - `SubProgramCatalog.kt` (membership, order, surface eligibility, widget pairing, off-switch), `SubProgramEntry.kt`, `SubProgramSurface.kt`, `InternalRouteCatalog.kt` (label, icon, intent per route key)
	- `ui/main/helpers/` - `MainProgramsMenuCoordinator.kt` (the dropdown menu and the programs panel), `MainProgramsMenuOrder.kt`
	- `widget/registry/` - `HomeWidgetCatalog.kt`, the widget each entry pairs with
	- Availability: `domain/usecase/panel/ResolvePanelRouteAvailabilityUseCase.kt`. Overview: `docs/ARCHITECTURE.md` "Sub-Program Registry".

- Desktop companion config (`.fmscfg` SFTP-share import/export) - NOT the Wear companion:
	- `data/companion/` (`CompanionConfigParser.kt` read side, `CompanionConfigSerializer.kt` write side, `CompanionConfigDto.kt` contract mirror, `CompanionResourceTokens.kt`)
	- `domain/usecase/companion/` (`ImportCompanionConfigUseCase.kt`, `ExportCompanionConfigUseCase.kt`)
	- `ui/companionimport/` (+ `ui/companionimport/qr/` for the QR share path)
	- Contract is cross-repo frozen: authoritative text is the companion repo's `docs/CONFIG_FORMAT.md`; this repo owns the consumer half only. Overview: `docs/ARCHITECTURE.md` "Desktop Companion Config (`.fmscfg`) Subsystem".

- Cloud providers/auth/integration:
	- `data/cloud/`
	- `data/cloud/datasource/`, `data/cloud/glide/`

- Network protocols (SMB/FTP/SFTP):
	- `data/network/`
	- `data/network/datasource/`, `data/network/exceptions/`, `data/network/pool/`
	- Connectivity monitor: `core/network/`

- Chromecast / Cast output:
	- `core/cast/` (`CastOptionsProvider`, `LocalCastProxyServer`)
	- `ui/player/helpers/CastMediaManager.kt` (session, proxy, download)

- File transfer and strategy layer:
	- `data/transfer/`
	- `data/transfer/strategy/`, `data/transfer/strategies/`, `data/transfer/access/`

- DI wiring:
	- `di/` (Hilt modules and bindings)

- Immersive VR / OpenXR (flavor source set `app_v2/src/vr/`, shipped in `vr` + `noLegal` only):
	- `core/xr/` - detection, gating, entry (`XrEnvironmentDetectorImpl`, `VrMediaSectionContractImpl`, `XrEntryGatewayImpl`, `StartVrPlaybackUseCaseImpl`)
	- `core/xr/runtime/` - native OpenXR bridge (`NativeDiagnosticXrRuntime` -> `libfms_diagnostic_xr.so`, built by `app_v2/src/vr/cpp/CMakeLists.txt`)
	- `ui/xr/` - immersive Activities (`DiagnosticXrActivity`, `ImmersiveBrowseActivity`) + `DiagnosticXrRenderThread` (owns the OpenXR frame loop)
	- `ui/xr/helpers/` - HUD panel (`HudCanvasRenderer`, `SubtitleCueRenderer`) painted to the HUD quad on state change
	- Overview: `docs/ARCHITECTURE.md` "Immersive VR / OpenXR Subsystem". VR classes are catalogued - `dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "*Xr*"`.

Wear app (`wear/src/main/java/com/sza/fastmediasorter/wear/`):

- Entry points:
	- `MainActivity.kt`
	- `FastMediaSorterWearApp.kt`

- Wear feature areas:
	- `ui/` (screens and controllers)
	- `data/repository/`, `data/preferences/`, `data/network/`
	- `domain/repository/`, `domain/model/`
	- `di/`

Research hygiene:
- Exclude `*.backup` files from implementation analysis unless explicitly asked for history/comparison.
