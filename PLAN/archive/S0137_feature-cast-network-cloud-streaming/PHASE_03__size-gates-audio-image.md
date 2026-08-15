# Phase 03 — Size gates for audio and image

**Strategic spec:** [`../S0137_feature-cast-network-cloud-streaming.md`](../S0137_feature-cast-network-cloud-streaming.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⛔ Blocked (research §6.1 — owner must confirm size limits)
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Add explicit size gates `MAX_AUDIO_CAST_BYTES` and `MAX_IMAGE_CAST_BYTES` so that oversized audio / image network downloads are refused with a localized toast that names the limit, rather than silently consuming the cache and then loading slowly on the receiver.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Strategic §6.1 is Resolved — owner has confirmed the two byte limits. Defaults proposed (pending confirmation): `MAX_AUDIO_CAST_BYTES = 50 MB`, `MAX_IMAGE_CAST_BYTES = 30 MB`.
- [ ] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt` | Modified | ≤ 360 |
| `app_v2/src/main/res/values/strings.xml` | Modified | append |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | append |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | append |

---

## Steps

### Step 03.1 — Add the two byte-limit constants and the AUDIO/IMAGE branches in `resolveAndSend`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In the companion object of `CastMediaManager`, add (next to `MAX_VIDEO_CAST_BYTES`):
>
> ```kotlin
> private const val MAX_AUDIO_CAST_BYTES = 50L * 1024 * 1024   // 50 MB
> private const val MAX_IMAGE_CAST_BYTES = 30L * 1024 * 1024   // 30 MB
> ```
>
> Adjust both values if owner answers strategic §6.1 with different numbers.
>
> In `resolveAndSend`, extend the `when` block that currently only covers `MediaType.VIDEO + MAX_VIDEO_CAST_BYTES` to also handle `MediaType.AUDIO` and `MediaType.IMAGE / MediaType.GIF`. Each oversize branch shows a localized toast and returns:
>
> ```kotlin
> file.type == MediaType.AUDIO && file.size > MAX_AUDIO_CAST_BYTES -> {
>     Timber.d("S0137: CastMediaManager — audio too large (${file.size}) for cast")
>     withContext(Dispatchers.Main) {
>         Toast.makeText(context, R.string.cast_audio_too_large, Toast.LENGTH_LONG).show()
>     }
>     return
> }
> (file.type == MediaType.IMAGE || file.type == MediaType.GIF) && file.size > MAX_IMAGE_CAST_BYTES -> {
>     Timber.d("S0137: CastMediaManager — image too large (${file.size}) for cast")
>     withContext(Dispatchers.Main) {
>         Toast.makeText(context, R.string.cast_image_too_large, Toast.LENGTH_LONG).show()
>     }
>     return
> }
> ```

**Verification:**

- `Grep` — `MAX_AUDIO_CAST_BYTES` matches in `CastMediaManager.kt`.
- `Grep` — `MAX_IMAGE_CAST_BYTES` matches in `CastMediaManager.kt`.
- `Grep` — `R.string.cast_audio_too_large` matches exactly once in `CastMediaManager.kt`.
- `Grep` — `R.string.cast_image_too_large` matches exactly once in `CastMediaManager.kt`.
- `Grep` — `S0137: CastMediaManager — audio too large` matches in `CastMediaManager.kt`.
- `Grep` — `S0137: CastMediaManager — image too large` matches in `CastMediaManager.kt`.

**Status:** `[ ]` not done

---

### Step 03.2 — Add `cast_audio_too_large` and `cast_image_too_large` to all three locales

**Files:**
`app_v2/src/main/res/values/strings.xml`,
`app_v2/src/main/res/values-ru/strings.xml`,
`app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add immediately after the existing `cast_video_too_large` entry, in each locale file:
>
> EN (`values/strings.xml`):
>
> ```xml
> <string name="cast_audio_too_large">Audio file too large to cast (&gt; 50 MB)</string>
> <string name="cast_image_too_large">Image too large to cast (&gt; 30 MB)</string>
> ```
>
> RU (`values-ru/strings.xml`):
>
> ```xml
> <string name="cast_audio_too_large">Аудиофайл слишком большой для каста (&gt; 50 МБ)</string>
> <string name="cast_image_too_large">Изображение слишком большое для каста (&gt; 30 МБ)</string>
> ```
>
> UK (`values-uk/strings.xml`):
>
> ```xml
> <string name="cast_audio_too_large">Аудіофайл завеликий для каста (&gt; 50 МБ)</string>
> <string name="cast_image_too_large">Зображення завелике для каста (&gt; 30 МБ)</string>
> ```
>
> If owner confirms different byte limits in step 03.1, update the literal numbers in all three locales accordingly. Strings must pass `docs/COMMUNICATION_POLICY.md` §6 tone checklist (clear, no jargon, names the limit so the user knows the rule).

**Verification:**

- `Grep` — `name="cast_audio_too_large"` appears exactly once in each of the three `strings.xml` files.
- `Grep` — `name="cast_image_too_large"` appears exactly once in each of the three `strings.xml` files.
- `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "cast_"` — exit code 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[ ]` not done

---

### Step 03.3 — Confirm size literals in EN/RU/UK strings match the constants in code

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`, `CastMediaManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Re-read the three locale files and `CastMediaManager.kt` together. The "50 MB" / "30 MB" literals in the toast strings must equal the byte values of `MAX_AUDIO_CAST_BYTES` / `MAX_IMAGE_CAST_BYTES`. If owner picked non-round numbers in 03.1, write a follow-up step to introduce a parametrised string (`%1$d MB`) and refactor; otherwise no action.

**Verification:**

- `Grep` — `50 MB` in `values/strings.xml` matches the value of `MAX_AUDIO_CAST_BYTES` (visual check; or write a small helper if numeric).
- `Grep` — `30 MB` in `values/strings.xml` matches `MAX_IMAGE_CAST_BYTES`.
- Manual: literals consistent across all three locales.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every modified file via `.\scripts\add_to_dev_log.ps1`.
- [ ] String locale audit: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "cast_"` returns exit 0.

---

## Handoff Notes to Next Phase

Generic-toast fallback (`cast_error_file`) is now reserved for true failures; size violations have explicit feedback. Phase 04 (progress feedback) is independent of Phase 03 and remains blocked on `/ui-clarify`.

---

## Rollback Plan

Revert the phase commit. Constants and string keys disappear together with their callers; no schema, no data migration.
