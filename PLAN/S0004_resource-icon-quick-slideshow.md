# Spec: resource-icon-quick-slideshow

**Tier:** UX enhancement
**Roadmap-id:** ad-hoc
**Status:** Implemented
**Module:** app_v2
**Last updated:** 2026-04-27

<!-- auto-approved by /spec-all — 2026-04-27 -->

---

## Goal

В списке ресурсов на главном экране иконка типа ресурса (нота / видео / изображение) у виртуальных
агрегатов («Вся музыка», «Все видео», «Все изображения») и у локальных «библиотек»
(аудиотека, видеотека, фототека) должна стать кнопкой быстрого запуска. По нажатию: ресурс
открывается, плеер стартует со слайдшоу с первого файла, порядок берётся из последнего
сохранённого `sortMode` ресурса. Нажатие по остальной площади карточки сохраняет текущее поведение
(переход в Browse).

Eligibility: `resource.profile in {AUDIO_LIBRARY, VIDEO_LIBRARY, PHOTO_STORAGE}` — этот предикат
покрывает оба класса (виртуальные агрегаты `ScanLocalFoldersUseCase` уже выставляет нужный профиль;
локальные «библиотеки» приобретают профиль через редактор ресурса).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt` | Modified | ≤ 800 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ 1000 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainViewModel.kt` | Modified | ≤ 700 |
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |

No layout XML changes — ripple/clickable state is applied programmatically per-bind so it can be
toggled by eligibility without diverging the two layout files (`item_resource.xml`,
`item_resource_grid.xml`).

---

## Phase 01 — Icon quick-slideshow click

**Status:** ⬜ Not started
**Depends on:** none

### Step 1.1 — Extend ResourceAdapter constructor with `onIconClick` callback

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt`

**Prompt for developer:**

> Add a new constructor parameter `onIconClick: (MediaResource) -> Unit` to `ResourceAdapter`,
> placed right after `onItemClick`. Add a private companion helper:
>
> ```kotlin
> private fun isQuickSlideshowEligible(resource: MediaResource): Boolean =
>     resource.profile == ResourceProfile.AUDIO_LIBRARY ||
>     resource.profile == ResourceProfile.VIDEO_LIBRARY ||
>     resource.profile == ResourceProfile.PHOTO_STORAGE
> ```
>
> Import `com.sza.fastmediasorter.domain.model.ResourceProfile`.

**Verification:**

- `Grep` — `onIconClick: \(MediaResource\) -> Unit` matches once in `ResourceAdapter.kt`.
- `Grep` — `fun isQuickSlideshowEligible` matches once in `ResourceAdapter.kt`.
- `Grep` — `import com.sza.fastmediasorter.domain.model.ResourceProfile` matches once.

**Status:** `[x] done`

---

### Step 1.2 — Wire icon click in `ResourceViewHolder.bind`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt`

**Prompt for developer:**

> In `ResourceViewHolder.bind`, after `ivResourceTypeIcon.setImageResource(iconRes)`, append:
>
> ```kotlin
> val quickEligible = isQuickSlideshowEligible(resource)
> if (quickEligible) {
>     ivResourceTypeIcon.isClickable = true
>     ivResourceTypeIcon.isFocusable = true
>     ivResourceTypeIcon.foreground = ContextCompat.getDrawable(
>         root.context,
>         R.drawable.ripple_icon_quick_slideshow
>     )
>     ivResourceTypeIcon.contentDescription =
>         root.context.getString(R.string.cd_resource_icon_quick_slideshow)
>     ivResourceTypeIcon.setOnClickListenerDebounced { onIconClick(resource) }
> } else {
>     ivResourceTypeIcon.isClickable = false
>     ivResourceTypeIcon.foreground = null
>     ivResourceTypeIcon.setOnClickListener(null)
>     ivResourceTypeIcon.contentDescription =
>         root.context.getString(R.string.resource_type_icon)
> }
> ```
>
> Use the `selectableItemBackgroundBorderless` system attribute via a small drawable resource
> `app_v2/src/main/res/drawable/ripple_icon_quick_slideshow.xml` (see Step 1.5) so that the icon
> shows a circular ripple. View recycling: the `else` branch is required to clear state on rebind.

**Verification:**

- `Grep` — `onIconClick(resource)` matches at least once in `ResourceAdapter.kt`.
- `Grep` — `R.string.cd_resource_icon_quick_slideshow` matches at least twice in `ResourceAdapter.kt` (grid + list bind).
- `Grep` — `isQuickSlideshowEligible(resource)` matches twice in `ResourceAdapter.kt`.

**Status:** `[x] done`

---

### Step 1.3 — Wire icon click in `GridViewHolder.bind`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/ResourceAdapter.kt`

**Prompt for developer:**

> Replicate the `Step 1.2` block immediately after `ivResourceTypeIcon.setImageResource(iconRes)`
> in `GridViewHolder.bind`. Same logic, same string keys, same drawable.

**Verification:**

- The two `Grep` checks from Step 1.2 are now satisfied (grid + list).
- `Grep -n "ivResourceTypeIcon.setOnClickListenerDebounced"` returns exactly two hits in `ResourceAdapter.kt`.

**Status:** `[x] done`

---

### Step 1.4 — Add `MainViewModel.startSlideshowFor`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainViewModel.kt`

**Prompt for developer:**

> Add a new public method on `MainViewModel`, placed directly after `startPlayer()`:
>
> ```kotlin
> fun startSlideshowFor(resource: MediaResource) {
>     if (state.value.isNavigating) {
>         Timber.d("Navigation already in progress, ignoring icon click")
>         return
>     }
>     viewModelScope.launch(ioDispatcher) {
>         try {
>             updateState {
>                 it.copy(
>                     isNavigating = true,
>                     navigationMessage = context.getString(
>                         com.sza.fastmediasorter.R.string.starting_slideshow_for,
>                         resource.name
>                     )
>                 )
>             }
>             selectResource(resource)
>             saveLastUsedResourceId(resource.id)
>             validateAndOpenResource(resource, slideshowMode = true)
>         } finally {
>             updateState { it.copy(isNavigating = false, navigationMessage = null) }
>         }
>     }
> }
> ```
>
> No new imports beyond what `startPlayer` already uses.

**Verification:**

- `Grep` — `fun startSlideshowFor\(resource: MediaResource\)` matches once in `MainViewModel.kt`.
- `Grep` — `validateAndOpenResource(resource, slideshowMode = true)` matches at least once in `MainViewModel.kt`.

**Status:** `[x] done`

---

### Step 1.5 — Add ripple drawable

**Files:** `app_v2/src/main/res/drawable/ripple_icon_quick_slideshow.xml` (new)

**Prompt for developer:**

> Create a borderless circular ripple drawable that defers to the platform attribute:
>
> ```xml
> <?xml version="1.0" encoding="utf-8"?>
> <ripple xmlns:android="http://schemas.android.com/apk/res/android"
>     android:color="?attr/colorControlHighlight">
>     <item android:id="@android:id/mask">
>         <shape android:shape="oval">
>             <solid android:color="#FFFFFFFF" />
>         </shape>
>     </item>
> </ripple>
> ```

**Verification:**

- `Glob` — `app_v2/src/main/res/drawable/ripple_icon_quick_slideshow.xml` exists.
- `Grep` — `colorControlHighlight` matches in that file.

**Status:** `[x] done`

---

### Step 1.6 — Add trilingual content description

**Files:**
`app_v2/src/main/res/values/strings.xml`,
`app_v2/src/main/res/values-ru/strings.xml`,
`app_v2/src/main/res/values-uk/strings.xml`

**Prompt for developer:**

> Add the same key `cd_resource_icon_quick_slideshow` to all three locale files:
>
> - `values/strings.xml` → `"Start slideshow"`
> - `values-ru/strings.xml` → `"Запустить слайдшоу"`
> - `values-uk/strings.xml` → `"Запустити слайдшоу"`
>
> Place near other content-description strings (search for `cd_` or `contentDescription` siblings).

**Verification:**

- `Grep` — `cd_resource_icon_quick_slideshow` matches once in each of the three `strings.xml` files (3 hits total).

**Status:** `[x] done`

---

### Step 1.7 — Wire `onIconClick` in `MainActivity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`

**Prompt for developer:**

> In `MainActivity.setupViews()`, when constructing `ResourceAdapter`, add the new `onIconClick`
> callback right after the `onItemClick` lambda:
>
> ```kotlin
> onIconClick = { resource ->
>     viewModel.startSlideshowFor(resource)
> },
> ```

**Verification:**

- `Grep` — `onIconClick = \{ resource ->` matches once in `MainActivity.kt`.
- `Grep` — `viewModel.startSlideshowFor\(resource\)` matches once in `MainActivity.kt`.

**Status:** `[x] done`

---

### Phase Done Criteria

- [x] All Step 1.* are `[x] done`.
- [x] `/build` → `standard debug` PASS (2026-04-29).
- [ ] Manual: tap the music-note icon on «Вся музыка» — plays slideshow without going through Browse.
- [ ] Manual: tap the video icon on «Все видео» — plays slideshow.
- [ ] Manual: tap the image icon on «Все изображения» — plays slideshow.
- [ ] Manual: tap the icon on a local AUDIO_LIBRARY/VIDEO_LIBRARY/PHOTO_STORAGE folder — plays slideshow.
- [ ] Manual: tap the icon on an FTP / SMB / non-library local folder — no quick-launch reaction; tapping the row still opens Browse.
- [x] Dev log entry added for every modified file via `.\scripts\add_to_dev_log.ps1`.

---

## Out of Scope

- Changing which icons are displayed (`ic_virtual_music`, `ic_virtual_video`, etc.).
- Touching `ic_resource_local` fallback for `VIRTUAL_PATH_ALL_IMAGES`.
- Wear OS — module owns its own UI.
- Widget launch flow.
- Camera Photos virtual aggregate (`VIRTUAL_PATH_CAMERA_PHOTOS` has profile `PHOTO_STORAGE`, so it
  *will* gain the quick-slideshow icon — this is intentional and consistent).
