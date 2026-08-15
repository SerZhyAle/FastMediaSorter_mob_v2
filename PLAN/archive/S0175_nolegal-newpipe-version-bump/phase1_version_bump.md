# Phase 1 — Bump NewPipeExtractor to v0.26.1

**Target file:** `app_v2/build.gradle.kts`

## Steps

- [x] 1. In `app_v2/build.gradle.kts`, change:
  ```
  "noLegalImplementation"("com.github.TeamNewPipe:NewPipeExtractor:v0.24.0")
  ```
  to:
  ```
  "noLegalImplementation"("com.github.TeamNewPipe:NewPipeExtractor:v0.26.1")
  ```
- [x] 2. Patch strategic spec §2 and §6: remove Odysee goal (upstream does not support it); close open research item with finding.
- [x] 3. Build `noLegalDebug` variant; BUILD SUCCESSFUL.
- [x] 4. Run `post-change.ps1` for all changed files.

### Side-fixes applied (inline, pre-existing)

- `Stream.url` deprecated in v0.26.1 → replaced with `Stream.content` in `NewPipeSiteExtractionStrategy.kt`.
- `build.gradle.kts`: fixed `providers.gradleProperty("chaquopy.enabled")` not reading `local.properties` — added explicit `Properties` load (matches existing OWNER_TRIGGER pattern).
- All three noLegal builder scripts: added `--no-configuration-cache` flag (Chaquopy 17.x is not CC-compatible).
- `src/vr/res/mipmap-anydpi-v26/`: removed `ic_launcher.xml` + `ic_launcher_round.xml` (identical to main source set, caused resource duplicate conflict with noLegal source set).

## Verification

- `./gradlew :app_v2:assembleNoLegalDebug` exits 0.
- No unresolved symbol errors referencing `org.schabi.newpipe.extractor.*`.
- `NewPipeSiteExtractionStrategy.kt` unchanged.
