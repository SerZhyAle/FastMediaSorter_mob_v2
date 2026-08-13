# Спецификация: S1317 - Миниатюра анимированного WebP не строится и кэшируется как отказ

**Ticket:** S1317
**Status:** Archived
**Tactical plan:** `PLAN/S1317_animated-webp-thumbnail-cannot-be-bitmap/INDEX.md`
**Priority:** 45
**Date:** 2026-07-30
**Tier:** 2 - Small

<!-- parked from remote log intake (/newlog), 2026-07-30 -->

---

## 0. Захваченный материал (inbox)

**Источник:** удалённый лог-пакет `Отладочные логи FastMediaSorter`, забран 2026-07-30 18:05, сессия `fastmediasorter_20260729_162305.log`.

**Сырые строки (одна из четырёх одинаковых групп):**

```
17:32:59.888 V/App: NetworkFileDataFetcher: Fetch complete for 075238f46c53279b.webp, read 1698KB
17:32:59.907 W/App: Network image load failed: 075238f46c53279b.webp, Failed to load resource
There was 1 root cause:
java.lang.IllegalArgumentException(Unable to convert android.graphics.drawable.AnimatedImageDrawable@2d5c71b to a Bitmap)
17:32:59.908 D/App: Added to failed thumbnail cache (1/5000): 075238f46c53279b.webp
```

Повторилось 4 раза в этой сессии, каждый раз на своём файле.

---

## 1. Почему отдельный тикет, а не часть S1026

Заметка статуса S1026 (`animated-webp-support`, `BlockNeedUserTest`) уже описывает это дословно: «SEPARATE UNFIXED LEG: thumbnails still threw Unable to convert AnimatedImageDrawable to a Bitmap 5x - root cause not established». То есть отказ известен, причина не установлена, и он **не входит** в то, что S1026 починил.

S1026 чинил другое - `AnimatedImageDrawableNoOpEncoder.getEncodeStrategy` возвращал `EncodeStrategy.NONE`, из-за чего полноэкранное декодирование падало с `Unknown strategy: NONE`. В новых логах этой ошибки нет ни разу.

Если не выделить эту ногу в свой тикет, она уедет вместе с закрытием S1026 и потеряется.

---

## 2. Что известно по логу

- Файл **скачался успешно** (1698 KB прочитано) - сеть и загрузка ни при чём, падает именно декодирование в `Bitmap`.
- Значит запрос идёт по пути, требующему `Bitmap` (`asBitmap()` или трансформация, которой нужен bitmap), а декодер отдаёт `AnimatedImageDrawable`.
- **Отказ кэшируется**: `Added to failed thumbnail cache (1/5000)`. То есть миниатюра не просто не построилась один раз - она больше не будет пытаться строиться, пока кэш не сбросят. Пользователь видит постоянно битую миниатюру.
- Полноэкранный путь при этом, судя по отсутствию ошибок, работает - то есть один и тот же файл открывается, но в списке выглядит сломанным.

---

## 3. Что проверить при разработке

1. Какой именно Glide-запрос строит миниатюру сетевого изображения и просит ли он `Bitmap` явно.
   - **Ответ:** `AdapterThumbnailLoader.kt:516-532`, и `Bitmap` он просит **не** через `asBitmap()`,
     а через обязательную трансформацию `.centerCrop()`. Пути с `asBitmap()` от дефекта иммунны.
2. Можно ли для анимированных форматов брать первый кадр вместо конверсии всего drawable.
   - **Ответ:** да, и это единственная надёжная точка контроля - проектный декодер стоит в бакете
     `PREPEND_ALL`, то есть раньше встроенного глайдовского, поэтому обязан сам отдавать первый кадр,
     а не уступать очередь. Побочная выгода: результат отчитывается как `BitmapDrawable` и впервые
     начинает кэшироваться на диск штатным энкодером.
3. Отдельно: не должен ли отказ такого рода **не** попадать в кэш неудачных миниатюр - кэш рассчитан на битые файлы, а тут файл целый.
   - **Ответ:** отказ не только кэшируется, но и переживает перезапуск - `markThumbnailAsFailed`
     персистит его с TTL 7 суток. Разовая чистка по версии схемы, чтобы уже пострадавшие увидели
     результат сразу.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1026 `animated-webp-support` (`BlockNeedUserTest`) - соседняя нога, чей страж
  `isAnimationDisabled` и есть механизм этого отказа; S1156 (`BlockExternal`) - миниатюры, но про
  память, не про формат; S1116 (`BlockNeedUserTest`) - иммунна, использует `asBitmap()`.
- **Flavors:** все шесть, на API 28+. Гейт `AnimatedImageSupportUtils.isAnimatedImageDecodeSupported()`
  проверяет уровень API, а не флейвор, и оба файла лежат в `src/main`. Устройства API 23-27
  (актуально для `legacy`) не затронуты. Поле `ENABLE_ANIMATIONS` к этому отношения не имеет - у него
  нет ни одного потребителя в коде.
- **Видимое пользователю изменение:** анимированный WebP в списке перестаёт показывать плашку
  расширения вместо кадра. Новых строк, настроек и элементов разметки нет.
- **Приёмка:** на устройстве - анимированный `.webp` по SMB или FTP даёт настоящую миниатюру,
  полноэкранный просмотр по-прежнему анимируется, в логе нет `Unable to convert`.

---

## 4. Связи

- S1026 `animated-webp-support` (`BlockNeedUserTest`) - соседняя, уже исправленная нога той же области. Дедуп выполнен: `search.ps1` по `AnimatedImageDrawable`, `animated webp`, `webp`, `thumbnail`.
- S1156 `Cloud thumbnail bitmap downsampling / memory` (`BlockExternal`) - тоже про построение миниатюр, но про память, не про формат.
