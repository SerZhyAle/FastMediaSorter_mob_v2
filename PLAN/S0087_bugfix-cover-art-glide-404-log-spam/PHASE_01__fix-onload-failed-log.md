# Phase 01 — Fix onLoadFailed Log

**Strategic spec:** [`../S0087_bugfix-cover-art-glide-404-log-spam.md`](../S0087_bugfix-cover-art-glide-404-log-spam.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 0 / 1
**Started:** —
**Completed:** —

---

## Objective

Replace the single `Timber.w(e, ...)` call in `onLoadFailed` with a branch that logs HTTP 404 at DEBUG (no exception chain) and all other failures at WARNING.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioCoverArtLoader.kt` | Modified | ≤ 450 |

> File is currently 407 lines — no backup required.

---

## Steps

### Step 01.1 — Replace Timber.w in onLoadFailed with 404-aware branch

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioCoverArtLoader.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `AudioCoverArtLoader.kt`, locate the `RequestListener<Drawable>` anonymous object inside `searchOnlineAndDisplayCover` (around line 338). Replace the body of `onLoadFailed` as follows:
>
> ```kotlin
> override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
>     val is404 = e?.rootCauses?.any { it is com.bumptech.glide.load.HttpException && it.statusCode == 404 } == true
>     if (is404) {
>         val domain = runCatching { Uri.parse(model.toString()).host ?: "unknown" }.getOrDefault("unknown")
>         Timber.d("searchOnlineAndDisplayCover[$callId]: cover art not found (404) — $domain")
>     } else {
>         Timber.w("searchOnlineAndDisplayCover[$callId]: Glide load FAILED — ${e?.message}")
>     }
>     audioEmptyStateController?.show(mode) ?: binding.audioCoverArtView.setImageResource(R.drawable.ic_music_note)
>     return true
> }
> ```
>
> `android.net.Uri` is already imported. `com.bumptech.glide.load.HttpException` is referenced with its full qualifier — do not add a top-level import to avoid collision. No other changes outside this method body.

**Verification:**

- `Grep` — `Timber.w(e,` returns **zero** hits in `AudioCoverArtLoader.kt`.
- `Grep` — `is404` returns exactly **one** hit in `AudioCoverArtLoader.kt`.
- `Grep` — `cover art not found (404)` returns exactly **one** hit in `AudioCoverArtLoader.kt`.
- `Grep` — `Log\.d\(` returns **zero** hits in `AudioCoverArtLoader.kt`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Step 01.1 is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioCoverArtLoader.kt" "S0087" "Replace Timber.w with 404-aware branch in onLoadFailed"`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `AudioCoverArtLoader.onLoadFailed` no longer emits a WARNING with full exception chain on HTTP 404.
- Non-404 failures still log at WARNING with a brief message.
- Phase 02 (docs-catalog-cleanup) may start.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
