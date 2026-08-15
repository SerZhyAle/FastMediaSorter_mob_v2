# Спецификация (compact bugfix): S1611 - Встроенные изображения EPUB не загружаются в просмотрщике документов

**Ticket:** S1611
**Status:** Archived
**Priority:** 40
**Date:** 2026-08-12
**Tier:** bugfix

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-12

**Текст:**

Обнаружено автоматически в ходе `/spec-prerelease` (S0484), при разборе лога прогона Maestro-набора на эмуляторе Pixel_9 (API 35, standard-debug).

Лог-строка (процесс приложения, pid 15951, `temp/S0484/run_app_only_20260812_170036.log`):

```text
08-12 17:07:16.344 15951 16234 E AndroidProtocolHandler: Unable to open asset URL: file:///android_asset/d2d_images/cover.jpg
```

Строка возникает во время сессии `PlayerActivity`, при показе документа; через ~2.4 с активность уничтожается штатно.

**Вложения:**
- Полный лог прогона - `temp/S0484/run_20260812_170036.log`
- Лог, отфильтрованный по процессам приложения - `temp/S0484/run_app_only_20260812_170036.log`

---

## 1. Проблема / симптом

**Не подтверждено. Дефекта нет.** Посылка захвата - "изображения EPUB не загружаются" - опровергнута тем же логом, из которого была взята.

Захват прочитал одну строку уровня `E` и достроил по ней следствие, которого в логе нет. Соседние строки того же потока (`tid 16234`) показывают обратное.

---

## 2. Корневая причина

Строка `AndroidProtocolHandler: Unable to open asset URL` - штатный побочный эффект схемы доставки ресурсов книги, а не сбой.

Механика, по коду `EpubViewerManager` -> `EpubWebViewLifecycle` -> `EpubResourceContentHelper`:

- HTML главы грузится через `loadDataWithBaseURL` с базовым URL `file:///android_asset/` ([EpubViewerManager.kt:481-482](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/EpubViewerManager.kt#L481-L482)).
- Относительная ссылка на картинку резолвится WebView против этой базы, поэтому WebView сперва пробует настоящий каталог ассетов приложения, не находит там файла книги и печатает `E AndroidProtocolHandler`.
- Сразу за этим срабатывает `shouldInterceptRequest`, снимает префикс `file:///android_asset/` и отдаёт байты из распакованного EPUB ([EpubWebViewLifecycle.kt:74-87](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/EpubWebViewLifecycle.kt#L74-L87), [EpubResourceContentHelper.kt:48-66](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/EpubResourceContentHelper.kt#L48-L66)).

Последовательность в захваченном логе, один поток `tid 16234`, интервал 27 мс:

```text
17:07:16.344 E AndroidProtocolHandler: Unable to open asset URL: file:///android_asset/d2d_images/cover.jpg
17:07:16.346 D EpubResourceContentHelper: EPUB: Found resource by exact path 'd2d_images/cover.jpg'
17:07:16.347 D EpubResourceContentHelper: EPUB: Serving intercepted asset 'd2d_images/cover.jpg' from EPUB (117233 bytes, image/jpeg)
17:07:16.371 D EpubViewerManager: EpubViewerManager: firstChapterRendered chapter=0 chapterCount=10
```

Обложка доставлена в WebView целиком: 117233 байта, MIME `image/jpeg`, после чего глава отрисована. Пустого места вместо картинки не было.

Побочное наблюдение, тоже не дефект: `EPUB: Found 0 <img> tags in chapter` - обложка в этой книге (генератор Draft2Digital) свёрстана не тегом `<img>`, поэтому предварительная инлайн-подстановка `data:`-URI её не покрыла и картинка пошла через перехват. Это второй, штатный путь той же подсистемы; результат идентичен.

---

## 3. Исправление

**Кода не менять.** Путь рендеринга изображений EPUB работает, доказано логом выше.

Отвергнутый вариант - сменить базовый URL на не-`file:` схему, чтобы WebView не зондировал настоящие ассеты и не печатал `E`. Отвергнут: выигрыш чисто косметический, одна строка лога, а смена базового URL меняет origin документа и задевает и `shouldInterceptRequest`, и JS-мост выделения текста в работающей отгруженной фиче. Цена риска выше цены строки.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Проверка

Проверка выполнена по захваченному логу, повторный прогон не нужен: `temp/S0484/run_app_only_20260812_170036.log`, строки 5731-5732 (ресурс найден и отдан) и `firstChapterRendered` следом.

Что запомнить на будущее, чтобы не заводить этот тикет второй раз: `E AndroidProtocolHandler: Unable to open asset URL` в сессии просмотрщика документов - ожидаемый шум. Признак настоящего сбоя картинки - соседняя строка `EPUB: Asset '<path>' not found in EPUB resources` уровня `W` со списком доступных изображений. Её в прогоне нет.
