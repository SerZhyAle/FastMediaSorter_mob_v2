# Research 01 - Flavor-matrix surface inventory and ground truth

**Ticket:** S1392
**Date:** 2026-08-04
**Method:** read-only sweep of `docs/**`, `dev/**`, root rule files, `scripts/**`, site HTML. Read-only zones (`V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`) excluded. Ground truth read directly from `app_v2/build.gradle.kts` lines 268-547.

---

## 1. Ground truth - `buildConfigField` grid

Six `productFlavors`, all in dimension `version`. `T` = declared `true`, `F` = declared `false`, `-` = not declared for that flavor.

| Flag | standard | noLegal | lite | photos | legacy | vr |
|:-----|:--:|:--:|:--:|:--:|:--:|:--:|
| `SUPPORT_VIDEO` | T | T | T | F | T | T |
| `SUPPORT_AUDIO` | T | T | **T** | F | T | T |
| `SUPPORT_STREAMS` | T | T | **F** | F | T | T |
| `SUPPORT_MIC_RECORDING` | T | T | F | F | T | T |
| `SUPPORT_IMAGES` | T | T | T | T | T | T |
| `SUPPORT_CLOUD` | T | T | F | **T** | **T** | T |
| `SUPPORT_LOCAL_NETWORK` | T | T | **F** | T | T | T |
| `SUPPORT_DOCUMENTS` | T | T | F | F | T | T |
| `ENABLE_ANIMATIONS` | T | T | F | T | T | T |
| `ENABLE_EPUB` | T | T | F | F | T | T |
| `ENABLE_TRANSLATION` | T | T | F | F | T | T |
| `ENABLE_PERSISTENT_AUDIO_PLAYBACK` | T | T | **F** | F | T | T |
| `SUPPORTS_DEFAULT_PLAYER` | T | T | F | T | T | T |
| `SUPPORT_VR_PLAYER` | F | **T** | F | F | F | **F** |
| `VR_UI_COMPOSITION_LAYER_ENABLED` | - | **T** | - | - | - | **F** |
| `SUPPORT_WEAR_COMPANION` | T | T | F | F | T | F |
| `SUPPORT_CAST` | T | T | T | T | T | F |
| `IS_NO_LEGAL_FLAVOR` | - | T | - | - | - | - |
| `SUPPORT_LAUNCHER` | T | T | - | - | - | - |
| `minSdk` | 26 | 26 | 26 | 26 | **23** | 26 |

Notes carried from the gradle comments:

- `SUPPORT_STREAMS = false` in `lite` is attributed to S0575 ("Streams feature UI hidden in lite, streamingDisabled pipeline unchanged").
- `SUPPORT_LOCAL_NETWORK = false` in `lite` is attributed to S0448 (local-files-only).
- `SUPPORT_CAST = false` in `vr` - Horizon OS lacks the Play Services Cast module.
- `SUPPORT_VR_PLAYER` is **true only in `noLegal`**; the `vr` flavor declares it `false`.

## 2. How the app answers the same question

- `core/capability/MediaCapabilities.kt` - typed capability record, bound per flavor by a `MediaCapabilitiesModule` in each flavor source set. Shared code injects it instead of reading `BuildConfig`.
- `core/capability/CapabilityAvailability.kt:47` - `fun isStreamsAvailable(): Boolean = BuildConfig.SUPPORT_STREAMS`. This is the only read site of `SUPPORT_STREAMS` in `app_v2/src/`, so in `lite` the Streams surface has no entry point at all.
- Lyrics are not separately flavor-gated: `LyricsManager` and friends live in `src/main` behind the audio player, so lyrics follow `SUPPORT_AUDIO`.
- `GetMediaFilesUseCase.applyFlavorMediaTypeRestrictions` filters every `MediaType` by capabilities except the four `BINARY_*` types, which are unconditional.

## 3. Confirmed false claims

Each line is a doc claim that contradicts §1.

1. `docs/HOW_TO.md:27` (+ `_RU`, `_UK`) - `Audio playback & lyrics | lite ✗`. Actual: `SUPPORT_AUDIO = true`. Background playback is the part that is off (`ENABLE_PERSISTENT_AUDIO_PLAYBACK = false`), which is a different flag.
2. `docs/HOW_TO.md:28` (+ `_RU`, `_UK`) - `Internet Streams | lite ✓ (progressive only)`. Actual: `SUPPORT_STREAMS = false`, no entry point.
3. `docs/HOW_TO.md:241` - documents readable on "Standard/Lite/Photos/Legacy". Actual: `SUPPORT_DOCUMENTS = false` in `lite` and in `photos`.
4. `docs/HOW_TO.md:263` - "Do not expect cloud reading in Lite, Photos, or Legacy." Actual: `SUPPORT_CLOUD = true` in `photos` and in `legacy`.
5. `docs/HOW_TO.md:344`, `:365` - same cloud error for `legacy` in the notes flow.
6. `docs/howto/scenario-smb-setup.md:8` (+ `-ru`, `-uk`) - "Standard, Lite, Photos, Legacy (all support SMB)". Actual: `SUPPORT_LOCAL_NETWORK = false` in `lite`.
7. `dev/TECH_REQUIREMENTS.md:303-311` - minimum-requirements table groups Lite with Standard/Photos for "Network protocols: SMB 2/3, SFTP, FTP" and for "Google Play Services: Required for Cloud". Both false for `lite`.
8. `docs/DEV_OPS.md:347-348` - `SUPPORT_VR_PLAYER` and `VR_UI_COMPOSITION_LAYER_ENABLED` marked `[+]` for `vr`. Actual: `false` in `vr`, true only in `noLegal`.
9. `docs/DEV_OPS.md:329-336` - core matrix has no `SUPPORT_STREAMS` and no `SUPPORT_LOCAL_NETWORK` row, so `lite`'s two headline restrictions are invisible in the primary developer matrix.
10. `docs/QUICK_START.md:22` (+ `_RU`, `_UK`) - Lite row: "Photos + videos only (no audio, cloud); Streams supports progressive audio streams only". Two errors in one cell.
11. `docs/FAQ.md:278-284` - "The Lite flavor does not include audio features."; `:291` - "Lite supports progressive-audio only."
12. `docs/LIMITATIONS.md:33` - "Lite flavor: Progressive http/https audio streams only."
13. `docs/TROUBLESHOOTING.md:311`, `:315-317` - same progressive-streams claim for `lite`.
14. `docs/MODULE_SELECTION.md:23` - "progressive-audio only .. on `lite`"; `:25` - "Wear OS companion: all flavors except `vr`" (also false for `lite` and `photos`, both `SUPPORT_WEAR_COMPANION = false`).
15. `docs/FEATURES.md:52` and `docs/README.md:52` - `[.. / Lite (progressive-audio only)]`; `:53` - `[.. / Lite]` for inline radio playback. Both promise a surface `lite` does not have.
16. `.claude/agents/android-rd-specialist.md:52` - "`lite`: VIDEO+IMAGES, minSdk 26". Wrong on audio and cast; this is the persona line the inbox text attributed to `CLAUDE.md`.
17. `CLAUDE.md:78` - "**Flavors**: standard, lite, photos, legacy" - omits `vr` and `noLegal`.
18. `docs/COMMUNICATION_POLICY.md:5` - scope line enumerates four flavors, omits `vr`/`noLegal`.
19. `scripts/docs/render-settings-reference.ps1:86` - `$flavorName` display map has no `vr` key, so a `vr`-scoped setting cannot render its flavor name in the generated reference.
20. Root `README.md:110` says "4 different flavors"; `docs/README.md:59` says "5 main app flavors .. plus the XR / noLegal surface". Neither matches the six in `productFlavors`.

## 4. Surfaces that are already correct

Useful as the wording to copy, not to change.

- `docs/ARCHITECTURE.md:247` - "lite/photos - feature absent, no entry point (SUPPORT_STREAMS=false, lite hidden by S0575)". Correct and dated to the right ticket.
- `docs/ARCHITECTURE.md:261` - `SUPPORT_LOCAL_NETWORK` "true in standard/photos/legacy/vr/noLegal, false in `lite`". Correct.
- `docs/DEV_OPS.md:329-336` core matrix rows VIDEO/AUDIO/IMAGES/CLOUD/DOCS/ANIM - all six flavors correct.
- `dev/DEVICE_PROFILE_PRESET_MATRIX.md:103` - "Streams feature UI is hidden in the `lite` and `photos` flavors". Correct.
- `dev/handoff/streams-source-spec/**` - the most granular matrix in the repo, gradle-sourced, correct. Out of scope (strategic Non-goals).

## 5. Existing mechanical coverage - and the gap

- `scripts/check-doc-vs-gradle.ps1` + `scripts/doc-drift/pins.psd1` - **version pins only** (AGP, compileSdk, library versions). No capability flag is in the manifest.
- `scripts/release/standard-surface-snapshot.ps1` - folds `docs/ALL_FEATURES.jsonl` records tagged `standard` against the `standard` BuildConfig flags. Its own header states `docs/FEATURES.md` is deliberately not consulted. `standard` only; no equivalent for the other five flavors.
- `scripts/quality/assert-flavor-flags-not-growing.ps1`, `scripts/guard/flavor-isolation-guard.ps1` - ratchet raw `BuildConfig.SUPPORT_*` reads in `src/main` (Rule 14). They never look at documentation.
- `docs/ALL_FEATURES.jsonl` - 639 records each carrying a `flavors` array (schema enum `standard|lite|photos|legacy|vr|noLegal`). `validate.ps1` checks schema and id uniqueness; nothing cross-checks a record's `flavors` against gradle, and a record does not declare which capability flag it needs.

**Gap:** no artifact is derived from the flavor grid, and no gate compares any markdown table to it. Every claim in §3 could drift silently and did.

## 6. Wiring points for a new gate

- `scripts/quality/assert-fast-gates.ps1` - `$gateArgs` hashtable at lines 55-97 plus the ordered run list; a new `assert-*.ps1` is registered by adding one entry.
- `scripts/post-change.ps1` - each gate is a block calling `scripts/quality/assert-<name>.ps1 -Gate` (lines 390-742). Existing doc-conformance precedents to copy: `assert-howto-settings-paths.ps1`, `assert-settings-doc-sync.ps1`, `assert-script-cheatsheet-sync.ps1`.
- `docs/DOCUMENT_REGISTRY.jsonl` - one JSON object per line; a generated doc carries `"generated": true`. Closure is `validate.ps1` then `generate.ps1` then `generate.ps1 -Check`.
- `scripts/docs/` already hosts renderers with the "GENERATED by .. Do not edit by hand." banner convention (`render-settings-reference.ps1`).

## 7. Open decisions handed to the plan

- `docs/RECEIVING_LINKS_RU.md:228-238` is the only matrix in the repo with no `noLegal` column. Add-as-copy-of-`standard` versus leave-out is a scope call, not a fact (strategic §6.2).
- `docs/settings/` annotations drive the generated settings reference; the missing `vr` key in the renderer map is a code fix, not a doc fix.
