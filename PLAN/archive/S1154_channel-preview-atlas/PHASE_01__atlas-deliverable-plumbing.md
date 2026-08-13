# Phase 01 - Atlas deliverable plumbing

**Strategic spec:** [`../S1154_channel-preview-atlas.md`](../S1154_channel-preview-atlas.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 04, Phase 06
**Steps done:** 7 / 7
**Started:** 2026-07-23
**Completed:** 2026-07-23

---

## Objective

Add `CHANNEL_PREVIEW_ATLAS` as an on-demand `DeliverableSet` member with a real-progress download + working delete: enum member, exhaustive-`when` coverage, a data-payload descriptor (placeholder pins), an Extensions-Manager row gated to streams flavors, per-flavor descriptor contribution, trilingual strings, and gating unit tests. No UI render, no store/slicer yet.

---

## Prerequisites

- [ ] Strategic §6 research resolved (it is).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/delivery/DeliverableSet.kt` | Modified | ≤ 25 |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/DeliverableDownloadWorker.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliverableDescriptorCatalog.kt` | Modified | ≤ 190 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliverableInventoryImpl.kt` | Modified | ≤ 340 |
| `app_v2/src/standard/java/com/sza/fastmediasorter/di/StandardBundledDeliverableSetsModule.kt` | Modified | ≤ 60 |
| `app_v2/src/legacy/java/com/sza/fastmediasorter/di/LegacyBundledDeliverableSetsModule.kt` | Modified | ≤ 60 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/NoLegalBundledDeliverableSetsModule.kt` | Modified | ≤ 60 |
| `app_v2/src/vrOnly/java/com/sza/fastmediasorter/di/VrBundledDeliverableSetsModule.kt` | Modified | ≤ 60 |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/delivery/DeliverableInventoryFilterTest.kt` | Modified | ≤ 300 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/delivery/RealDeliverableSetDownloaderGateTest.kt` | Modified | ≤ 300 |

> No `res/layout/*.xml` edits in this phase - no landscape-parity obligation.
>
> **Flavor placement.** The descriptor contributors are edited in each streams flavor's own source set (`src/standard`, `src/legacy`, `src/noLegal`, `src/vrOnly`). `lite`/`photos` contribute no atlas descriptor and are NOT edited. No `BuildConfig.*` flavor guard is added to `src/main`.

---

## Steps

### Step 01.1 - Append the enum member

**Files:** `domain/delivery/DeliverableSet.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Append `CHANNEL_PREVIEW_ATLAS` as the LAST entry of `enum class DeliverableSet` (after `FFMPEG_DTS`). Add a one-line KDoc bullet describing it as the on-demand stream channel-preview sprite sheet. Appending keeps existing ordinals stable (marker store + `notificationId = 7300 + ordinal`).

**Verification:**

- `Grep` - `CHANNEL_PREVIEW_ATLAS` matches exactly once in `DeliverableSet.kt`.
- `Grep` - `FFMPEG_DTS,` precedes `CHANNEL_PREVIEW_ATLAS` (member is last).

**Status:** `[x]` done

---

### Step 01.2 - Cover the worker exhaustive `when`

**Files:** `worker/DeliverableDownloadWorker.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `featureNameRes(set)` add the branch `DeliverableSet.CHANNEL_PREVIEW_ATLAS -> R.string.ext_channel_preview_atlas_title`. This is the only exhaustive `when (set)` over `DeliverableSet` that fails to compile after Step 01.1 (risk §7). Do not add a `RealDeliverableSetDownloader.isNativeCodeSet()` branch - the atlas is data, not `.so`, so its default `false` is correct.

**Verification:**

- `Grep` - `CHANNEL_PREVIEW_ATLAS -> R.string.ext_channel_preview_atlas_title` present in `DeliverableDownloadWorker.kt`.
- Build compiles (no non-exhaustive-`when` error) - `/build` fast Kotlin check `.\a.ps1 fk`.

**Status:** `[x]` done

---

### Step 01.3 - Add the data-payload descriptor (placeholder pins)

**Files:** `data/delivery/DeliverableDescriptorCatalog.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `fun channelPreviewAtlas(): DeliverableSourceDescriptor` returning a descriptor for `DeliverableSet.CHANNEL_PREVIEW_ATLAS` with two `resource(..)` `PayloadFile`s: the atlas sheet (`channel-preview-atlas.webp`) and the `url->index` sidecar (`channel-preview-coords.json`), both hosted on the existing `MIRROR` (`delivery-so-v1` tag). Declare the two SHA-256 / minSize pins as named `private const` values with a comment `// FINALIZED in Phase 06 from the published binary` and placeholder zero/empty values. Keep it a pure-data descriptor (mirror `audioVisualizations()`), no ABI logic.

**Verification:**

- `Grep` - `fun channelPreviewAtlas()` present in `DeliverableDescriptorCatalog.kt`.
- `Grep` - `channel-preview-atlas.webp` and `channel-preview-coords.json` both present.
- `Grep` - `FINALIZED in Phase 06` comment present (marks the placeholder pins).

**Status:** `[x]` done

---

### Step 01.4 - Add the Extensions-Manager row

**Files:** `data/delivery/DeliverableInventoryImpl.kt`
**Depends on:** Step 01.1, Step 01.3

**Prompt for developer:**

> In `getExtensions()`, inside the existing `if (capabilityAvailability.isStreamsAvailable())` block (next to the `STREAM_CATALOG` `Catalog` row), add an `ExtensionItem.Module` for `DeliverableSet.CHANNEL_PREVIEW_ATLAS`: `id = moduleKey(...)`, `displayNameRes = R.string.ext_channel_preview_atlas_title`, `descriptionRes = R.string.ext_channel_preview_atlas_desc`, `sizeLabel = moduleSizeLabel(...)`, `section = ExtensionSection.STREAMS`, `statusFlow = moduleStatusFlow(...)`. Add a `FALLBACK_SIZE` map entry for the set (approximate on-disk atlas size, e.g. `30_000_000L`). The existing `Module` branches of `download()` (real WorkManager progress) and `uninstall()` (real `repository.uninstall(set)` delete) already cover it - do not special-case it.

**Verification:**

- `Grep` - `CHANNEL_PREVIEW_ATLAS` appears in `DeliverableInventoryImpl.kt` inside an `ExtensionItem.Module(` construction.
- `Grep` - `ExtensionSection.STREAMS` present on that row.
- `Grep` - `DeliverableSet.CHANNEL_PREVIEW_ATLAS to` present in the `FALLBACK_SIZE` map.

**Status:** `[x]` done

---

### Step 01.5 - Contribute the descriptor per streams flavor

**Files:** `src/standard/.../StandardBundledDeliverableSetsModule.kt`, `src/legacy/.../LegacyBundledDeliverableSetsModule.kt`, `src/noLegal/.../NoLegalBundledDeliverableSetsModule.kt`, `src/vrOnly/.../VrBundledDeliverableSetsModule.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> In each of the four streams-flavor modules, add `DeliverableSet.CHANNEL_PREVIEW_ATLAS to DeliverableDescriptorCatalog.channelPreviewAtlas()` to the map returned by `descriptorContributor()` (the `DeliverableSetContributor.descriptors()` override). Do NOT add it to `bundledSets()` - the atlas is on-demand, not bundled. Leave `lite`/`photos` untouched (no such module, no streams surface).

**Verification:**

- `Grep` - `CHANNEL_PREVIEW_ATLAS to DeliverableDescriptorCatalog.channelPreviewAtlas()` present in all four flavor modules (four hits).
- `Grep` - `CHANNEL_PREVIEW_ATLAS` does NOT appear inside any `bundledSets()` override (zero hits in `bundledSets` context).

**Status:** `[x]` done

---

### Step 01.6 - Add trilingual strings

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - independent

**Prompt for developer:**

> Add keys `ext_channel_preview_atlas_title` and `ext_channel_preview_atlas_desc` across EN/RU/UK in one lockstep call: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key ext_channel_preview_atlas_title -En "..." -Ru "..." -Uk "..."` and the same for `_desc`. Title names the extension ("Channel preview atlas"); description says it shows video-channel previews in grid mode before the first watch. RU/UK prose uses `..` not `...` and plain hyphen `-`; RU uses Ё where grammatical. Strings must pass `docs/COMMUNICATION_POLICY.md` §2 (informative message formula) and §6 (tone checklist).

**Verification:**

- `Grep` - `ext_channel_preview_atlas_title` present in all three `strings.xml` files.
- `Grep` - `ext_channel_preview_atlas_desc` present in all three `strings.xml` files.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "ext_channel_preview_atlas"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 01.7 - Gating unit tests

**Files:** `src/test/.../DeliverableInventoryFilterTest.kt`, `src/test/.../RealDeliverableSetDownloaderGateTest.kt`
**Depends on:** Step 01.4, Step 01.5

**Prompt for developer:**

> In `DeliverableInventoryFilterTest`, assert the atlas `Module` row is present when `isStreamsAvailable()` is true and absent when false (mirror the existing `STREAM_CATALOG` assertions). In `RealDeliverableSetDownloaderGateTest`, assert `CHANNEL_PREVIEW_ATLAS` is NOT treated as a native-code set (it must not be gated behind `isPlayInstall()`), so a Play install still downloads it from the mirror. Do not exercise a real network fetch.

**Verification:**

- `Grep` - `CHANNEL_PREVIEW_ATLAS` present in both test files.
- Run `.\gradlew.bat testStandardDebugUnitTest --tests "*DeliverableInventoryFilterTest" --tests "*RealDeliverableSetDownloaderGateTest"` - both pass.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `.\a.ps1 fkn` (noLegal Kotlin compile) passes - the noLegal descriptor contributor edit is exercised.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (no new class this phase, but rerun `catalog_sync.ps1` once).
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13). Focus: DI-graph change (new descriptor multibinding), exhaustive-`when` coverage.

---

## Handoff Notes to Next Phase

- `DeliverableSet.CHANNEL_PREVIEW_ATLAS` exists; downloaded payload lands at `filesDir/delivery/CHANNEL_PREVIEW_ATLAS/` holding `channel-preview-atlas.webp` + `channel-preview-coords.json`. Phase 02's store reads from exactly that directory.
- Descriptor integrity pins are placeholders until Phase 06 finalizes them; runtime download is intentionally not exercisable yet.

---

## Rollback Plan

Revert the phase commit(s). No data migration, no user-facing surface rendered yet (the row appears but the download would fail on placeholder pins until Phase 06) - safe to revert.
