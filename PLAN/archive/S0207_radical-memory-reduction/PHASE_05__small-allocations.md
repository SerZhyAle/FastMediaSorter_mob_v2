# Phase 05 — Small Allocations (Bitmap Dedup + Audio Buffer Profile)

**Strategic spec:** [`../S0207_radical-memory-reduction.md`](../S0207_radical-memory-reduction.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** none — independent
**Blocks:** —
**Steps done:** 4 / 5
**Started:** 2026-05-15
**Completed:** —

---

## Objective

Three independent low-risk optimisations:
1. Extension placeholders in the file list preserve the current extension-text badge UX (`mp3`, `flac`, `wav`, ..) while deduplicating the generated bitmap work across rows.
2. ExoPlayer audio playback uses a smaller buffer profile (`MAX_BUFFER_MS = 12_000` for local audio, 20_000 for network audio) — separate from the existing video profile.
3. **Upstream icon-generator LRUs** (`ExtensionThumbnailGenerator`, `BinaryFileThumbnailGenerator`) shrunk and switched to `RGB_565`. Today these cache up to 120 + 50 ARGB_8888 200×200 bitmaps — worst-case ~19 + 8 MB of native bitmap memory (API 28+ stores pixel data natively). Per-row dedup (item 1) alone is insufficient: the upstream cache is the larger consumer.
4. Phase calibration explicitly isolates browse-side audio metadata extraction and process-scope media-list caching before the ticket can claim that icon/buffer work solved the canonical MP3 memory spike.

---

## Prerequisites

- [x] Working tree clean or on feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PrefetchLoadControlFactory.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSetupHelper.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/LocalPlaybackHelper.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CloudPlaybackHelper.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/util/ExtensionThumbnailGenerator.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/util/BinaryFileThumbnailGenerator.kt` | Modified | ≤ 200 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/PrefetchLoadControlFactoryTest.kt` | New | ≤ 200 |
| `app_v2/src/test/java/com/sza/fastmediasorter/util/ExtensionThumbnailGeneratorTest.kt` | New | ≤ 200 |

---

## Steps

### Step 05.1 — Preserve extension-text placeholder UX while deduplicating allocations

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Locate the path that produces the current extension placeholder bitmap (for example the badge that visually says `mp3`, `flac`, `wav`, .. per file type). Refactor it so identical placeholders are reused across rows instead of rasterised per `ViewHolder`.
>
> Guardrails:
> - Keep the current user-visible badge semantics. Do **not** replace extension text with a generic shared audio icon.
> - If the adapter already delegates to `ExtensionThumbnailGenerator`, reuse that cache keyed by a normalized extension string / size bucket instead of creating new placeholder bitmaps in the adapter.
> - If the adapter still creates the bitmap itself, introduce a small shared cache keyed by the rendered extension token. Same text + same size should reuse the same bitmap.
>
> Backup the file (>500 lines? — if yes, timestamped copy in `temp/`).

**Verification:**

- `Glob` — `temp/AdapterThumbnailLoader.*.kt.bak` exists (if file >500 lines).
- `Grep` — no new generic audio-icon resource is introduced into the extension-placeholder path.
- `Grep` — the extension-text rendering path remains present after the refactor (for example the helper that derives or draws the extension token still exists).

**Status:** `[x]` done — `AdapterThumbnailLoader` still uses extension-text badges and now reuses the shared generator default-size bitmap path.

---

### Step 05.2 — Add audio buffer constants in `VideoPlayerManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** — independent of Step 05.1

**Prompt for developer:**

> In the constants companion of `VideoPlayerManager`, add a new audio-specific block immediately after the existing `CLOUD_*` buffer constants:
> ```kotlin
> // Audio playback uses a smaller buffer than video — codec/decoder allocations are
> // ~5× lower, and high buffer adds memory cost without audible benefit.
> internal const val AUDIO_MIN_BUFFER_MS = 5_000
> internal const val AUDIO_MAX_BUFFER_MS = 12_000
> internal const val AUDIO_BUFFER_FOR_PLAYBACK_MS = 2_000
> internal const val AUDIO_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 4_000
>
> // Network audio (SFTP / SMB / FTP / cloud) needs a larger buffer than local.
> internal const val AUDIO_NETWORK_MIN_BUFFER_MS = 10_000
> internal const val AUDIO_NETWORK_MAX_BUFFER_MS = 20_000
> internal const val AUDIO_NETWORK_BUFFER_FOR_PLAYBACK_MS = 4_000
> internal const val AUDIO_NETWORK_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 6_000
> ```
> Do not yet change existing buffer selection logic — that is Step 05.3.
**Verification:**

- `Grep` — `AUDIO_MAX_BUFFER_MS = 12_000` exactly once.
- `Grep` — `AUDIO_NETWORK_MAX_BUFFER_MS = 20_000` exactly once.
- `Grep` — all eight new constants present.

**Status:** `[x]` done — all eight local/network audio buffer constants were added to `VideoPlayerManager`.

---

### Step 05.3 — Wire audio buffer constants into LoadControl selection

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PrefetchLoadControlFactory.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSetupHelper.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/LocalPlaybackHelper.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CloudPlaybackHelper.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Find the `DefaultLoadControl.Builder()` (or equivalent) initialisation in `VideoPlayerManager`. Currently the selection branches on `isCloud` vs local. Extend the branch:
> - `isAudio && isLocal` → use `AUDIO_*` constants.
> - `isAudio && !isLocal` (any network protocol — SFTP, SMB, FTP, cloud) → use `AUDIO_NETWORK_*` constants.
> - `!isAudio && isCloud` → existing `CLOUD_*` constants (unchanged).
> - `!isAudio && !isCloud` → existing local video constants (unchanged).
>
> `isAudio` is the same boolean derived in Phase 01 Step 01.6. If it is not yet accessible at the LoadControl build site, propagate it explicitly — the LoadControl is built once per `playVideo` call.

**Verification:**

- `Grep` — `AUDIO_MAX_BUFFER_MS` referenced in `VideoPlayerManager.kt` (not just the constant declaration — at least one use in code).
- `Grep` — `AUDIO_NETWORK_MAX_BUFFER_MS` referenced (at least one use).
- `Grep` — `setBufferDurationsMs(` count unchanged in number of branches × 2 — the LoadControl builder calls should appear in the same shape, just with new arguments.

**Status:** `[x]` done — implemented in `PrefetchLoadControlFactory` plus helper call sites, which is the actual `LoadControl` owner in the current code.

---

### Step 05.4 — Shrink + RGB_565 upstream icon-generator LRUs

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/util/ExtensionThumbnailGenerator.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/util/BinaryFileThumbnailGenerator.kt`

**Depends on:** — independent of 05.1..05.3

**Prompt for developer:**

> In `ExtensionThumbnailGenerator.kt`:
> - Reduce `LruCache<String, Bitmap>(120)` to `LruCache<String, Bitmap>(30)`.
> - Replace `Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)` with `Bitmap.createBitmap(96, 96, Bitmap.Config.RGB_565)`. The icon use-case never needs alpha at 200 px on a list row; a 96-px icon at RGB_565 is `96*96*2 = 18 KB` vs the previous `200*200*4 = 160 KB` — a 9× reduction per slot.
> - Worst-case after change: 30 × 18 KB ≈ 540 KB (was 19 MB).
>
> In `BinaryFileThumbnailGenerator.kt`:
> - Reduce `LruCache<String, Bitmap>(50)` to `LruCache<String, Bitmap>(20)`.
> - Switch the bitmap config from `ARGB_8888` to `RGB_565`. If the generator renders actual file previews (not flat icons) and 96-px is too small for preview legibility, keep the existing dimensions but only flip the config to `RGB_565` — measure the legibility regression before reducing dimensions.
>
> Preserve all other behaviour. Backup files >500 lines.

**Verification:**

- `Grep` — `LruCache<String, Bitmap>(30)` in `ExtensionThumbnailGenerator.kt`, exactly once.
- `Grep` — `LruCache<String, Bitmap>(20)` in `BinaryFileThumbnailGenerator.kt`, exactly once.
- `Grep` — `Bitmap.Config.ARGB_8888` count in both files is 0 (removed).
- `Grep` — `Bitmap.Config.RGB_565` present in both files.

**Status:** `[x]` done — upstream extension/binary generator caches were shrunk and switched to `RGB_565`; extension placeholders now default to 96 px.

---

### Step 05.5 — Calibration measurement (audio)

**Files:** —
**Depends on:** Steps 05.1, 05.2, 05.3, 05.4 + project compiles

**Prompt for developer:**

> Play one local MP3 and one SFTP MP3. For each:
> - Confirm playback starts within previous baseline time (no regression).
> - Inspect `logs/current.log` for the `MEM_PROBE | checkpoint=AFTER_STATE_READY` line — native heap reserved should be lower than the Phase 04 measurement by a measurable delta (target: ≥ 5 MB lower; report actual).
>
> Also capture a browse-side measurement before Play and record whether the remaining spike is dominated by browse audio metadata extraction and/or process-scope media-list caching. If those two paths still dominate the canonical scenario, record that explicitly in `INDEX.md` and do **not** treat Phase 05 as sufficient evidence that S0207 is ready for `/spec-check`.

**Verification:**

- Two `MEM_PROBE | checkpoint=AFTER_STATE_READY | scenario=audio` lines present in `logs/current.log`.
- Delta vs Phase 04 baseline recorded in Blockers Log of INDEX.md.
- Blockers Log of `INDEX.md` explicitly states whether browse audio metadata extraction / media-list cache still dominate the pre-play memory spike.

**Status:** `[manual — deferred to human]` — requires device/emulator run; deferred to BlockNeedUserTest operator test.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles — `./gradlew.bat :app_v2:compileStandardDebugKotlin :app_v2:testStandardDebugUnitTest --tests "com.sza.fastmediasorter.ui.player.helpers.PrefetchLoadControlFactoryTest" --tests "com.sza.fastmediasorter.util.ExtensionThumbnailGeneratorTest"` PASS.
- [ ] Audio playback works for local and SFTP MP3 with no audible glitches.
- [ ] Placeholder UX is unchanged: extension-text badges still render as before; no generic icon regression was introduced.
- [x] Narrow coverage exists for the touched slice: audio buffer selection, placeholder reuse in `AdapterThumbnailLoader`, and cache/generator changes.
- [x] Dev log entry added for the touched files.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Phase 06 (startup workers defer) is independent. Phase 07 (idle disconnect) will piggy-back on the audio-network buffer behaviour — slower buffer means the connection is held longer; idle-disconnect ensures it is still released after the playback completes and the user moves elsewhere.

If Phase 05 calibration shows that browse-side audio metadata extraction or process-scope media-list caching still dominate the canonical scenario, do not let S0207 proceed to `/spec-check` as if the ticket were solved. Either extend the tactical plan or file a follow-up mitigation spec before closure.

---

## Rollback Plan

Revert phase commits — buffer constants gone, adapter falls back to per-row bitmap. No data migration, no schema change.

---

## Revision History

- **2026-05-15** — manual implementation sync after Phase 05 code landed
  - Applied: marked Steps 05.1..05.4 complete; expanded Files Touched to the helper/test files used by the landed implementation; recorded focused compile/test validation and left Step 05.5 open pending local+SFTP MP3 calibration.
- **2026-05-15** — by `/spec-update` (Claude Opus 4.7, focus: completeness)
  - Applied: Objective expanded with third optimisation (upstream icon-generator LRU shrink); "Files Touched" extended with `ExtensionThumbnailGenerator.kt` + `BinaryFileThumbnailGenerator.kt`; new Step 05.4 (shrink LRU to 30/20, switch to RGB_565, reduce dimensions where safe); calibration renumbered 05.4 → 05.5; phase counter 4 → 5. Reason: strategic §5.8 originally addressed only per-row adapter dedup; upstream LRUs hold up to 19 + 8 MB native bitmap memory (API 28+). Proposed (DISCUSS): 0.
  - Evidence: `temp/S0207_research/07_blind_spots.md` items B2, B3 + `00_SUMMARY.md` F5.
- **2026-05-15** — by `/spec-update` (GPT-5.4, focus: consistency, completeness)
  - Applied: rewrote Step 05.1 so allocation dedup preserves the existing extension-text placeholder UX instead of replacing it with a generic icon; added an explicit calibration gate for browse audio metadata extraction / process-scope media-list caching; added targeted test expectations for the previously untested placeholder and buffer-selection slices. Proposed (DISCUSS): 0.
