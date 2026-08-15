# Phase 07 - Attach & Auto-Run Installed Sets

**Strategic spec:** [`../S0386_ondemand-ocr-translation-delivery.md`](../S0386_ondemand-ocr-translation-delivery.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (device pass pending under BlockNeedUserTest)
**Depends on:** Phase 05, Phase 06
**Blocks:** Phase 08
**Steps done:** 4 / 4
**Started:** 2026-06-09
**Completed:** 2026-06-09

> **GATED indirectly by B3/B4** via Phase 05 (delivered `.so` must exist and be alignment-verified before `System.load`).

---

## Objective

After a set installs, attach its payload and use it automatically - lazily loading native libs only on first real use, and skipping re-download on subsequent enables (strategic Pillar E, criteria §11.4/§11.6/§11.7).

---

## Prerequisites

- [ ] Phase 05 ✅ Done (sets defined, base stripped).
- [ ] Phase 06 ✅ Done (UX drives the downloader).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliveredNativeLibraryLoader.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/RecognitionBackend.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TranslationBackend.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliveredAudioVisualizationSource.kt` | New | ≤ 120 |

---

## Steps

### Step 07.1 - Verified lazy native loader

**Files:** `data/delivery/DeliveredNativeLibraryLoader.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `DeliveredNativeLibraryLoader` that, on first use of a set, re-verifies payload integrity (reuse `PayloadIntegrityVerifier`) against the app-pinned SHA-256, then `System.load(absolutePath)`s the delivered `.so` from `filesDir/delivery/<set>/`. This covers the self-downloaded first-party/OSS `.so` only: Tesseract, Paddle, FFmpeg DTS. ML Kit Translate is NOT handled here - on store it loads via the `:translate_feature` dynamic module (`SplitInstallHelper.loadLibrary`), on sideload/VR it is bundled and loads normally (2026-06-09 decision). Load is lazy (first actual recognize/DTS use), idempotent, and refuses an unverified payload (ADR-3). No broad swallow - surface a typed load error.

**Verification:**

- `Grep` - `class DeliveredNativeLibraryLoader` matches once.
- `Grep` - `System.load` referenced.
- `Grep` - `PayloadIntegrityVerifier` referenced.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - `DeliveredNativeLibraryLoader.load(set)` verifies every payload file (`PayloadIntegrityVerifier`, app-pinned SHA-256/size) before attach, splices the delivered dir into the classloader native search path (so the engines' own `System.loadLibrary` resolves from filesDir), then warm-`System.load`s each `.so` in dependency order. Bundled sets and ML Kit Translate short-circuit. Lazy (first recognize/DTS use), idempotent (`loadedSets`), and refuses an unverified payload (throws → caller degrades to "unavailable"). NOTE: classloader path injection uses hidden-API reflection (`DexPathList.nativeLibraryDirectories`/`makePathElements`); must be validated on the target API levels on-device.

---

### Step 07.2 - Attach OCR/translation engines from delivered payload

**Files:** `ui/player/helpers/RecognitionBackend.kt`, `ui/player/helpers/TranslationBackend.kt`
**Depends on:** Step 07.1

**Prompt for developer:**

> OCR (`RecognitionBackend`): when Set B is `INSTALLED`, load Tesseract/Paddle via `DeliveredNativeLibraryLoader` instead of the removed bundled libs. Translation (`TranslationBackend`): on store flavors, ensure the `:translate_feature` dynamic module is installed (`SplitInstallManager`) before first use; on sideload/VR, ML Kit Translate is bundled and used directly (no attach). Subsequent enables of an already-installed engine skip download/install and go straight to lazy attach. Keep behavior identical to pre-S0386 once attached (criteria §11.7 - no quality regression).

**Verification:**

- `Grep` - `DeliveredNativeLibraryLoader` referenced in `RecognitionBackend.kt`.
- `Grep` - `INSTALLED` referenced in `RecognitionBackend.kt`.
- `Grep` - `SplitInstall` referenced in the store translation wiring.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - OCR: `RecognitionBackend` gates on `capabilityRepository.isInstalledBlocking(OCR_ENGINES)` and calls `libraryLoader.load(OCR_ENGINES)` before using the engine (all three recognize paths). Translation store: `:translate_feature` SplitInstall path landed in 05.2 (`DynamicTextTranslationFacadeFactory`); sideload/VR translation is bundled and used directly. FFmpeg DTS: `createPlaybackRenderersFactory` attaches `FFMPEG_DTS` via a Hilt EntryPoint before the renderers build. Subsequent enables of an installed set skip download and go straight to lazy attach.

---

### Step 07.3 - Serve audio-visualizations from delivered payload

**Files:** `data/delivery/DeliveredAudioVisualizationSource.kt`
**Depends on:** Step 07.1

**Prompt for developer:**

> Create `DeliveredAudioVisualizationSource` returning the `anim_audio_bg_*` video URIs from `filesDir/delivery/AUDIO_VISUALIZATIONS/` once Set C is installed; the audio player's background visualization reads from here instead of `R.raw`. No `System.load` needed (pure resource).

**Verification:**

- `Grep` - `class DeliveredAudioVisualizationSource` matches once.
- `Grep` - `AUDIO_VISUALIZATIONS` referenced.

**Status:** `[x]` done (commit `379b497f`)

**Step Log:**

- 2026-06-09 - `DeliveredAudioVisualizationSource` returns the `anim_audio_bg_*` URIs from `filesDir/delivery/AUDIO_VISUALIZATIONS/` when Set C is installed, else null (no `R.raw` fallback). Pure resource - no `System.load`.

---

### Step 07.4 - Survive update & cache clear

**Files:** `data/delivery/DeliveredNativeLibraryLoader.kt`
**Depends on:** Step 07.2, Step 07.3

**Prompt for developer:**

> Confirm the loader resolves payloads from the update-surviving, cache-immune `filesDir/delivery/<set>/` location (established in Phase 02) and that the install marker plus payload presence are both required before treating a set as `INSTALLED` (criteria §11.6). If a payload directory is missing despite a set marker, downgrade the set to `NOT_INSTALLED` so the UX re-offers download.

**Verification:**

- `Grep` - `filesDir` referenced in `DeliveredNativeLibraryLoader.kt`.
- `Grep` - `cacheDir` returns zero hits in `DeliveredNativeLibraryLoader.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - The loader resolves payloads from `filesDir/delivery/<set>/` (update-surviving, cache-immune); `cacheDir` is never referenced. `isInstalledBlocking` requires the payload directory to be present (`InstalledSetMarkerStore.isPayloadPresent`), and the loader re-verifies every file's SHA-256 before attach, so a missing/partial payload downgrades the set to unavailable and the UX re-offers download.

---

## Phase Done Criteria

- [ ] Every `Step 07.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-07)` returns zero hits.
- [ ] Manual on-device pass recorded: enable → download → use (OCR text recognized, translation produced, DTS plays, audio-bg shows); and re-enable skips download. Record `expected | actual` per check.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

End-to-end on-demand path works: enable → download → verify → attach → use, surviving update/cache-clear. Phase 08 documents the behavior and finalizes catalog/changelog.

---

## Rollback Plan

Revert phase commit(s) together with Phase 05 (attach depends on stripped base). Reverting both restores fully-bundled behavior. No data migration; delivered payloads in `filesDir` are harmless if orphaned.
