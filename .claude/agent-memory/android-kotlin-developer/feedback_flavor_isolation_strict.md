---
name: feedback_flavor_isolation_strict
description: VR/noLegal/lite/photos/legacy code lives in src/<flavor>/java/, never in src/main/java/ - BuildConfig flavor guards in main are forbidden
metadata:
  type: feedback
---

For any work targeting a non-`standard` flavor (`vr`, `vrUnlicensed`, `noLegal`, `lite`, `photos`, `legacy`), put new code in `src/<flavor>/java/` (or `res/`, `AndroidManifest.xml`). Define the contract interface and No-Op fallback in `src/main/java/`, real impl in flavor source set, bind via flavor-specific Hilt `@Module`. Never write `if (BuildConfig.SUPPORT_VR_PLAYER) {..}` / `if (BuildConfig.IS_NO_LEGAL_FLAVOR) {..}` / etc. in `src/main/java/`.

**Why:** Audit on 2026-05-14 found 169 `BuildConfig.SUPPORT_*` flavor-guard occurrences across 45 files in `src/main/` (24 are VR/noLegal-sensitive: `SUPPORT_VR_PLAYER`, `IS_NO_LEGAL_FLAVOR`, `VR_UI_COMPOSITION_LAYER_ENABLED`). Compile-time leaks were absent - DI contracts and source-set placement are correct on disk. But the BuildConfig-gate antipattern made flavor leakage the path of least resistance, so every new agent inherited it. Owner stated the position directly: "code in its own files/folders; VR modules should not leak into STANDARD; noLegal parts should not leak into VR or STANDARD." Result: codified into CLAUDE.md Rule 15 + reinforced in `/spec`, `/spec-tech`, `/spec-dev`, `/quick`, `/build`, `/catalog` skills (2026-05-14).

**How to apply:**
1. Before writing any class that touches `BuildConfig.SUPPORT_*` / `BuildConfig.IS_NO_LEGAL_FLAVOR` / `BuildConfig.VR_UI_COMPOSITION_LAYER_ENABLED`, stop and read `dev/FLAVOR_DEVELOPMENT_RULES.md`. The new code does not belong in `src/main/java/`.
2. Place the interface in `src/main/java/`; the No-Op default in `src/main/` (or `src/standard/`); the real impl in `src/<flavor>/java/`; bind in a flavor-local Hilt `@Module` under `src/<flavor>/java/.../di/`.
3. Layout/string overrides → `src/<flavor>/res/`; manifest additions → `src/<flavor>/AndroidManifest.xml`.
4. After a new flavor-only class compiles, run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`, then call `set.ps1 -NoFlavors "<comma-list of the other flavors>"` - valid set is `standard,lite,photos,legacy,vr,vrUnlicensed,noLegal` (expanded from 4 to 7 on 2026-05-14).
5. Existing 169 BuildConfig gates are tech debt - never add new ones; refactor incrementally when touching surrounding code. Pseudo-`vr/render` package in `src/main` tracked as spec [[s0199-vr-render-cleanup]] (Draft, Tier 3, Priority 30).

Canonical examples already on disk:
- `src/vr/java/.../vr/di/VrModule.kt` - binds `FullscreenCommandOverride` / `BrowsePassthroughCaptureProvider` / `VrLayerFactory`.
- `src/noLegal/java/.../di/NoLegalLinkDownloadModule.kt` - multibinding `@IntoSet` for link extraction strategies (NewPipe / yt-dlp / site-specific).
- `src/main/java/.../ui/player/entry/VrTaskTransition.kt` - reference no-op pattern: early `return false` when `SUPPORT_VR_PLAYER=false`, the object becomes inert. Acceptable only as a transition shim while interface-extraction is pending.

Forbidden anti-patterns:
- `if (BuildConfig.SUPPORT_VR_PLAYER) { startVr() }` inside `src/main/java/`.
- New `.kt` file under `src/main/java/com/sza/fastmediasorter/vr/**` or `noLegal/**`.
- Importing `com.sza.fastmediasorter.vr.<Impl>` from `src/main/java/` directly.
- Adding flavor-specific drawable/layout to `src/main/res/` (e.g. `ic_vr_*.xml` - should be in `src/vr/res/`).
