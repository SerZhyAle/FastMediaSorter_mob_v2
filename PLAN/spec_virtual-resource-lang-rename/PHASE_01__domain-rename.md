# Phase 01 — domain-rename

**Strategic spec:** [`../spec_virtual-resource-lang-rename.md`](../spec_virtual-resource-lang-rename.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — this is the foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 0 / 2
**Started:** —
**Completed:** —

---

## Objective

Create the hardcoded default-names table and the `RenameVirtualResourcesUseCase` that compares virtual resource names/comments against the table and updates mismatches to the current language.

---

## Prerequisites

- [ ] All phases in "Depends on" are `✅ Done`. *(N/A — foundation phase)*
- [ ] Strategic spec §6 research items are all Resolved. *(No open items)*
- [ ] Working tree is clean or changes are on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
| --- | --- | --- |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/VirtualResourceDefaultNames.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/RenameVirtualResourcesUseCase.kt` | New | ≤ 80 |

---

## Steps

### Step 1.1 — Create VirtualResourceDefaultNames

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/VirtualResourceDefaultNames.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create object `VirtualResourceDefaultNames` in package `com.sza.fastmediasorter.domain.usecase`. Define a nested `data class Entry(val name: String, val comment: String)`. Expose a top-level constant `TABLE: Map<String, Map<String, Entry>>` — a two-level map of `virtualPath → langCode → Entry`. Populate it with the 6 × 3 = 18 entries below. Import path constants from `LocalMediaScanner.Companion`. Do NOT use `context.getString()` — all strings are hardcoded literals.
>
> | Path constant | "en" name | "en" comment |
> | --- | --- | --- |
> | `VIRTUAL_PATH_RECENT` | `"Recent Media"` | `"Recently accessed local media files"` |
> | `VIRTUAL_PATH_ALL_AUDIO` | `"All Music"` | `"Local audio files (mp3, flac, ogg, aac…)"` |
> | `VIRTUAL_PATH_ALL_VIDEO` | `"All Videos"` | `"Local video files (mp4, mkv, mov, wmv…)"` |
> | `VIRTUAL_PATH_ALL_IMAGES` | `"All Images"` | `"Local image files (jpg, png, gif, webp…)"` |
> | `VIRTUAL_PATH_ALL_DOCS` | `"All Documents"` | `"Local documents (pdf, epub, txt…)"` |
> | `VIRTUAL_PATH_CAMERA_PHOTOS` | `"Camera Photos"` | `"Photos and videos from the device camera"` |
>
> | Path constant | "ru" name | "ru" comment |
> | --- | --- | --- |
> | `VIRTUAL_PATH_RECENT` | `"Недавние медиа"` | `"Недавно открытые локальные медиафайлы"` |
> | `VIRTUAL_PATH_ALL_AUDIO` | `"Вся музыка"` | `"Локальные аудиофайлы (mp3, flac, ogg, aac…)"` |
> | `VIRTUAL_PATH_ALL_VIDEO` | `"Все видео"` | `"Локальные видеофайлы (mp4, mkv, mov, wmv…)"` |
> | `VIRTUAL_PATH_ALL_IMAGES` | `"Все изображения"` | `"Локальные изображения (jpg, png, gif, webp…)"` |
> | `VIRTUAL_PATH_ALL_DOCS` | `"Все документы"` | `"Локальные документы (pdf, epub, txt…)"` |
> | `VIRTUAL_PATH_CAMERA_PHOTOS` | `"Фото с камеры"` | `"Фото и видео с камеры устройства"` |
>
> | Path constant | "uk" name | "uk" comment |
> | --- | --- | --- |
> | `VIRTUAL_PATH_RECENT` | `"Нещодавні медіа"` | `"Нещодавно відкриті локальні медіафайли"` |
> | `VIRTUAL_PATH_ALL_AUDIO` | `"Вся музика"` | `"Локальні аудіофайли (mp3, flac, ogg, aac…)"` |
> | `VIRTUAL_PATH_ALL_VIDEO` | `"Усі відео"` | `"Локальні відеофайли (mp4, mkv, mov, wmv…)"` |
> | `VIRTUAL_PATH_ALL_IMAGES` | `"Усі зображення"` | `"Локальні зображення (jpg, png, gif, webp…)"` |
> | `VIRTUAL_PATH_ALL_DOCS` | `"Усі документи"` | `"Локальні документи (pdf, epub, txt…)"` |
> | `VIRTUAL_PATH_CAMERA_PHOTOS` | `"Фото з камери"` | `"Фото та відео з камери пристрою"` |

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/VirtualResourceDefaultNames.kt` exists.
- `Grep` — `object VirtualResourceDefaultNames` matches exactly once in that file.
- `Grep` — `data class Entry` matches in that file.
- `Grep` — pattern `virtual://recent` returns zero hits in that file (paths imported via constants, not raw strings).
- `Grep` — `VIRTUAL_PATH_RECENT` appears in that file (confirms import of path constants).
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

---

### Step 1.2 — Create RenameVirtualResourcesUseCase

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/RenameVirtualResourcesUseCase.kt`
**Depends on:** Step 1.1

**Prompt for developer:**

> Create `class RenameVirtualResourcesUseCase @Inject constructor(@param:ApplicationContext private val context: Context, private val resourceRepository: ResourceRepository)` in package `com.sza.fastmediasorter.domain.usecase`.
>
> Implement `suspend operator fun invoke()`:
>
> 1. Call `LocaleHelper.getLanguage(context)` to get `currentLang: String`.
> 2. Call `resourceRepository.getAllResourcesSync()` and filter to entries where `VirtualPathUtils.isVirtualPath(it.path)` is true.
> 3. For each virtual resource:
>    a. Look up `VirtualResourceDefaultNames.TABLE[resource.path]` — skip if null (unknown virtual path).
>    b. Look up `defaults[currentLang]` — skip if null (unsupported lang, no action needed).
>    c. Check if `resource.name` equals any `entry.name` in `defaults` for a lang != `currentLang`. If yes, `nameNeedsUpdate = true`.
>    d. Check if `resource.comment` equals any `entry.comment` in `defaults` for a lang != `currentLang`. If yes, `commentNeedsUpdate = true`.
>    e. If either flag is true: call `resourceRepository.updateResource(resource.copy(name = if (nameNeedsUpdate) currentEntry.name else resource.name, comment = if (commentNeedsUpdate) currentEntry.comment else resource.comment))` and increment a counter.
> 4. Log: if counter > 0 → `Timber.i("RenameVirtualResources: renamed %d resource(s) to lang='%s'", count, currentLang)`. Otherwise → `Timber.d("RenameVirtualResources: nothing to rename for lang='%s'", currentLang)`.
>
> Wrap the entire body in `try/catch(e: Exception) { Timber.e(e, "RenameVirtualResources: failed") }` so a DB error never blocks app startup.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/RenameVirtualResourcesUseCase.kt` exists.
- `Grep` — `class RenameVirtualResourcesUseCase` matches exactly once.
- `Grep` — `@Inject constructor` appears in that file.
- `Grep` — `suspend operator fun invoke()` appears in that file.
- `Grep` — `try` appears in that file (error guard present).
- `Grep` — `Timber\.e\(` appears in that file (error logging present).
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 1.*` above is `[x] done`.
- [ ] Project compiles — run the `/build` skill (do not invoke gradle directly).
- [ ] `Grep` for `Log\.d\(` in both new files returns zero hits.
- [ ] Dev log entries added for both new files via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (two new public classes added).

---

## Handoff Notes to Next Phase

Phase 02 can inject `RenameVirtualResourcesUseCase` directly via `@Inject` — no new Hilt module needed. `AppStartupInitializer` is constructed manually in `FastMediaSorterApp`, so the new use-case must be added as a constructor parameter there and passed through.

---

## Rollback Plan

Revert the two new files — no data migration, no DB change, no user-visible surface.

---

## Revision History

- **2026-04-26** — by `/spec-update` (`claude-sonnet-4-6`, focus: all six)
  - ACCEPT applied: 1 (negative-grep predicate clarified in Step 1.1 Verification)
  - REVIEW applied: 0
  - DISCUSS proposed: 0 items — phase clean after lint and predicate fixes
