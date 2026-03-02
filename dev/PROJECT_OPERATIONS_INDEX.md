# FastMediaSorter v2 — Project Operations Index

Last Updated: 2026-02-28
Purpose: single entrypoint for fast research and navigation.

## 1) Workspace Topology
- Main app: `app_v2/`
- Wear companion: `wear/`
- Engineering process/docs/scripts: `dev/`
- Product/docs: `docs/`
- Automation scripts: `scripts/`
- Temporary artifacts only: `temp/`
- Read-only zones: `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`

## 2) Source Layout (Main App)
Root package: `app_v2/src/main/java/com/sza/fastmediasorter/`

- `ui/` — screens/fragments/compose + ViewModels (no business logic)
- `domain/` — use cases and domain abstractions/interfaces
- `data/` — repositories/data sources/network/db adapters
- `di/` — Hilt DI modules
- `core/`, `util/`, `utils/`, `worker/`, `widget/` — shared infra/background/widget
- `FastMediaSorterApp.kt` — application entrypoint

Data flow rule: `UI -> ViewModel -> UseCase -> Repository -> DataSource`

## 3) Source Layout (Wear)
Root package: `wear/src/main/java/com/sza/fastmediasorter/wear/`

- `ui/`, `domain/`, `data/`, `di/` — same layering principles
- `MainActivity.kt` — thin host activity
- `FastMediaSorterWearApp.kt` — wear application entrypoint

## 4) Build & Variants (Source of Truth)
- Modules included: `settings.gradle.kts` -> `:app_v2`, `:wear`
- Main build config: `app_v2/build.gradle.kts`
- Wear build config: `wear/build.gradle.kts`
- SDK / Java baseline: SDK 35, Java 17
- Flavors (main app): `standard`, `lite`, `photos`, `legacy`

Dependency version policy:
- First check `gradle/libs.versions.toml`
- If absent, treat module Gradle files as authoritative

## 5) Fast Commands
- Primary debug build: `./dev/build-with-version.ps1`
- Fast debug build: `./build-debug.PS1`
- Flavor debug build: `./gradlew.bat assembleStandardDebug`
- Unit tests: `./gradlew.bat testStandardDebugUnitTest`
- Lint: `./gradlew.bat lintStandardDebug`
- Wear debug build: `./gradlew.bat :wear:assembleDebug`

## 6) Mandatory Constraints
- Never write generated files/logs/backups to project root; use `temp/`
- Keep activity logic minimal; move complex logic to manager/helper classes
- Use `Timber`; avoid `Log.d()`
- If modifying a file >500 lines, create timestamped backup in `temp/`

## 7) Research Routing (What to Open First)
- Architecture/data flow: `docs/ARCHITECTURE.md`
- Build/scripts/flavors/flags: `docs/DEV_OPS.md` + module `build.gradle.kts`
- Libraries/protocol specifics: `docs/TECH_STACK.md`
- Full tech stack, dependencies, constraints, min/recommended requirements: `dev/TECH_REQUIREMENTS.md`
- Process and phase gating: `dev/AGENT_WORKFLOW.md`
- Documentation map: `docs/DOCS_MAP.md`

## 8) Quick Start Research Checklist
1. Confirm target module (`app_v2` or `wear`) and flavor impact.
2. Locate feature area in `ui/domain/data` path.
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

- Cloud providers/auth/integration:
	- `data/cloud/`
	- `data/cloud/datasource/`, `data/cloud/glide/`

- Network protocols (SMB/FTP/SFTP):
	- `data/network/`
	- `data/network/datasource/`, `data/network/exceptions/`, `data/network/pool/`
	- Connectivity monitor: `core/network/`

- File transfer and strategy layer:
	- `data/transfer/`
	- `data/transfer/strategy/`, `data/transfer/strategies/`, `data/transfer/access/`

- DI wiring:
	- `di/` (Hilt modules and bindings)

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
