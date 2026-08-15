# Phase 01 — Informative Error for .m2ts Network Playback Failures

**Strategic spec:** [`../S0053_bugfix-m2ts-bdmv-network-playback.md`](../S0053_bugfix-m2ts-bdmv-network-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, 03, 04
**Steps done:** 4 / 4
**Started:** —
**Completed:** 2026-05-02

---

## Objective

Replace the silent ExoPlayer format error on network `.m2ts` / `.m2t` files with an informative error message (ADR-2); no playback change yet — that is Phase 02-03.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] No prior `.m2ts`-specific error handling exists in `VideoPlayerManager.kt`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ 930 |
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |

> `VideoPlayerManager.kt` is currently 907 lines → create a timestamped backup in `temp/` before editing.

---

## Steps

### Step 1.1 — Backup VideoPlayerManager.kt

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Run the following in PowerShell from the project root to create a timestamped backup:
> `Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt" "temp/VideoPlayerManager_$(Get-Date -Format 'yyyyMMdd_HHmmss').kt.backup"`

**Verification:**

- `Glob` — `temp/VideoPlayerManager_*.kt.backup` matches at least one file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 1/1 PASS. Files: temp/VideoPlayerManager_20260502_125739.kt.backup (copy). Dev log: N/A (no source file changed).

---

### Step 1.2 — Add localized strings for BD-TS error

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** — independent, parallel with 1.1

**Prompt for developer:**

> In each strings file, add two new string resources in the playback-error section:
>
> **values/strings.xml** (English):
> ```xml
> <string name="error_bdts_format_title">Format Not Supported</string>
> <string name="error_bdts_format_message">Blu-ray Transport Stream (.m2ts) cannot be played from this network source.\n\nTip: transcode the file to MP4/MKV with a tool such as HandBrake or ffmpeg.</string>
> ```
>
> **values-ru/strings.xml** (Russian):
> ```xml
> <string name="error_bdts_format_title">Формат не поддерживается</string>
> <string name="error_bdts_format_message">Blu-ray Transport Stream (.m2ts) не может воспроизводиться с этого сетевого ресурса.\n\nСовет: перекодируйте файл в MP4/MKV с помощью HandBrake или ffmpeg.</string>
> ```
>
> **values-uk/strings.xml** (Ukrainian):
> ```xml
> <string name="error_bdts_format_title">Формат не підтримується</string>
> <string name="error_bdts_format_message">Blu-ray Transport Stream (.m2ts) не може відтворюватись з цього мережевого ресурсу.\n\nПорада: перекодуйте файл у MP4/MKV за допомогою HandBrake або ffmpeg.</string>
> ```

**Verification:**

- `Grep` — `error_bdts_format_title` present in `app_v2/src/main/res/values/strings.xml`.
- `Grep` — `error_bdts_format_title` present in `app_v2/src/main/res/values-ru/strings.xml`.
- `Grep` — `error_bdts_format_title` present in `app_v2/src/main/res/values-uk/strings.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 3/3 PASS. Files: values/strings.xml, values-ru/strings.xml, values-uk/strings.xml. error_bdts_format_title + error_bdts_format_message added in all 3 locales.

---

### Step 1.3 — Add BD-TS extension detection helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** Step 1.1

**Prompt for developer:**

> In `VideoPlayerManager.kt`, in the `onPlayerError` override (around line 430), locate the block:
> ```kotlin
> if (isFormatError && currentFilePath != null && !isLocalPath) {
>     Timber.w("VideoPlayerManager: ExoPlayer format error on network path — MediaPlayer fallback skipped")
> }
> ```
>
> Replace it with:
> ```kotlin
> if (isFormatError && currentFilePath != null && !isLocalPath) {
>     Timber.w("VideoPlayerManager: ExoPlayer format error on network path — MediaPlayer fallback skipped")
>     val lowerPath = currentFilePath!!.lowercase()
>     if (lowerPath.endsWith(".m2ts") || lowerPath.endsWith(".m2t")) {
>         Timber.i("VideoPlayerManager: BD-TS format error — showing informative dialog")
>         playerCallback.onBdTsFormatError()
>         return
>     }
> }
> ```
>
> The `playerCallback.onBdTsFormatError()` call signals the UI layer; the callback interface extension is added in Step 1.4.

**Verification:**

- `Grep` — `onBdTsFormatError` present in `VideoPlayerManager.kt`.
- `Grep` — `Log\.d(` returns zero hits in `VideoPlayerManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 2/2 PASS. Files: VideoPlayerManager.kt. BD-TS detection block added in onPlayerError. No Log.d calls.

---

### Step 1.4 — Implement onBdTsFormatError in the callback interface and PlayerActivity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` (callback interface, if defined inline), or the interface file found via catalog query `-ClassMatches "*VideoPlayer*Callback*"`.
**Depends on:** Step 1.3

**Prompt for developer:**

> Locate the `VideoPlayerCallback` interface (run `query.ps1 -ClassMatches "*VideoPlayerCallback*"` to get the file path). Add:
> ```kotlin
> fun onBdTsFormatError()
> ```
>
> In `PlayerActivity.kt` (or wherever `VideoPlayerCallback` is implemented), add the implementation:
> ```kotlin
> override fun onBdTsFormatError() {
>     AlertDialog.Builder(this)
>         .setTitle(getString(R.string.error_bdts_format_title))
>         .setMessage(getString(R.string.error_bdts_format_message))
>         .setPositiveButton(android.R.string.ok, null)
>         .show()
> }
> ```
>
> If `VideoPlayerCallback` is an interface already in its own file, add the method there. If it is defined as an inner interface inside `VideoPlayerManager.kt`, add it inside the same file.

**Verification:**

- `Grep` — `onBdTsFormatError` present in the callback interface file.
- `Grep` — `onBdTsFormatError` present in `PlayerActivity.kt` (or the implementing class file).
- `Grep` — `R.string.error_bdts_format_title` present in the implementing class file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 3/3 PASS. Files: VideoPlayerManager.kt (onBdTsFormatError() added to PlayerCallback interface), PlayerPlaybackCallbackImpl.kt (implementation with AlertDialog). R.string.error_bdts_format_title referenced correctly.

---

## Phase Done Criteria

- [x] Every `Step 1.*` above is `[x] done`.
- [x] Project compiles — run `/build` (do not invoke gradle directly). (auto-build — PASS, 2026-05-02)
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 01 establishes:
- `R.string.error_bdts_format_title` / `error_bdts_format_message` in all three locales.
- `VideoPlayerCallback.onBdTsFormatError()` method implemented in `PlayerActivity`.
- `VideoPlayerManager.onPlayerError` detects `.m2ts`/`.m2t` on network paths and routes to the new dialog.

Phase 02 can now implement the actual BD-TS playback fix; if playback succeeds, the `onBdTsFormatError` path becomes a last-resort fallback rather than the primary outcome.

---

## Rollback Plan

Revert phase commit(s) — no data migration or schema change. Removes the new strings and the `onBdTsFormatError` branch.
