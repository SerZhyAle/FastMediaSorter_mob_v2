# Стратегическая спецификация: S1773 - Повторный выбор обоев рабочего стола не применяется

**Ticket:** S1773
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-16
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - эпик S1615 (кластер C-37)

<!-- auto-approved by /spec-all - 2026-08-18 -->

---

## 1. Проблема

Однажды выбранное изображение рабочего стола лаунчера остаётся навсегда: при выборе другой картинки первая не меняется. Дефект воспроизводится владельцем стабильно.

### Причина (Root Cause)

`StoreLauncherWallpaperUseCase` записывает файл всегда под одним именем (`wallpaper.<ext>`). При повторном выборе изображения того же формата абсолютный путь не меняется, что вызывает двойной отказ:

1. **StateFlow deduplication:** `LauncherHomeViewModel.wallpaper` использует `.distinctUntilChanged()`, а `LauncherWallpaper.Image(absolutePath)` с неизменившимся путём равен предыдущему — Flow не эмитирует, UI не вызывает `render()`.
2. **Glide cache collision:** даже при принудительном `render()`, Glide кеширует декодированный битмап по ключу пути файла — при неизменившемся пути возвращает старый битмап из кеша.

---

## 2. Цели

1. Повторный выбор изображения рабочего стола заменяет текущие обои.
2. Замена видна сразу, без перезапуска.

**Non-goals:**

- Расширение вариантов обоев (GIF, анимация) - это S1101; здесь только починка повторного выбора.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

- (нет)

### 3.2 Жёсткие ограничения

- **Flavor:** по `docs/FLAVOR_MATRIX.md`.
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Локализация:** новых строк нет.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1615 (родительский эпик, запись L-027); S1101 (варианты обоев, ref).
- **Validation level:** выбор картинки A, затем картинки B -> на столе картинка B.
- **Owner sign-off:** делегировано конвейеру /spec-all эпика S1615 - 2026-08-16.

---

## 4. Реализация (compact inline phases)

### Step 01 - Unique wallpaper filename per save

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/StoreLauncherWallpaperUseCase.kt`

**Prompt for developer:**

> Append `System.currentTimeMillis()` to the output file name in `StoreLauncherWallpaperUseCase.invoke()` so every save produces a distinct absolute path. The existing `deleteRecursively()` still clears the previous copy, so exactly one file remains on disk.

**Why:** the immutable file name makes the absolute path equal across saves, which suppresses DataStore emission (path unchanged) and StateFlow deduplication (data class equality), and lets Glide return a stale bitmap from cache keyed on the old path.

**Verification:**
- `Grep` - `System.currentTimeMillis()` present in `StoreLauncherWallpaperUseCase.kt`.

**Status:** `[x]` done

### Step 02 - Glide signature defense-in-depth

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherWallpaperManager.kt`

**Prompt for developer:**

> Add `.signature(ObjectKey(file.lastModified()))` to the Glide load call in `LauncherWallpaperManager.render()`. Import `com.bumptech.glide.signature.ObjectKey`. This ensures Glide re-decodes the file even if the path were somehow identical (defense-in-depth against future regressions).

**Why:** Glide caches decoded bitmaps by the load model key, which for `File` is the path. A `signature` based on `lastModified` forces Glide to treat each write as a cache miss, so content changes are always visible even without the path-uniqueness fix in step 01.

**Verification:**
- `Grep` - `ObjectKey` import present in `LauncherWallpaperManager.kt`.
- `Grep` - `.signature(ObjectKey(` present in `LauncherWallpaperManager.kt`.

**Status:** `[x]` done

---

## 11. Критерии готовности (strategic-level)

1. Повторно выбранная картинка заменяет обои стола; дефект не воспроизводится.

---

## Приложение. Записи инбокса (дословно)

- **L-027** - «У меня не получается переопределять свое изображение рабочего стола лаунчера. Выбранное однажды остается там навсегда. Выбираю другую картинку, первое не меняется.»
