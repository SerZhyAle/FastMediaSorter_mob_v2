# Спецификация (compact bugfix): S1018 - AndroidProtocolHandler не может открыть asset d2d_images/cover.jpg

**Ticket:** S1018
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-12
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-12

**Текст:**

bugfix: AndroidProtocolHandler failed to load asset URL during /spec-prerelease sweep (2026-07-12): "Unable to open asset URL: file:///android_asset/d2d_images/cover.jpg". Confirmed from the app's own process (PID 30481, one of the app's "Start proc" PIDs during this session, cross-checked against the full run log) - not foreign/system noise. Occurred once at 20:39:54.354, ~489ms after an "ActivityThread: ConnectivityService is null in handleBindApplication!" line at 20:39:53.865 on a different PID (30728) - that second line is a well-known benign Android framework race during early process attach and is likely unrelated noise from the same launch window, not the cause. The referenced asset path "d2d_images/cover.jpg" suggests this ties to the app's dynamicBackgroundExtension setting/feature (seen enabled in the startup settings dump of this build) - a WebView-based dynamic background image failed to resolve from android_asset. No crash, no user-facing toast (log-audit toastCount=0 for the whole session), so likely silent - user would just not see a background image where one was expected. Needs investigation: does android_asset/d2d_images/cover.jpg actually exist in the APK; is the asset path/name mismatched; under what flow does the app attempt to load it (reproduce and find the trigger). Evidence: full run log P:\ANDROID\FastMediaSorter_mob_v2\temp\S0484\run_20260712_193755.log, line 184418; log-audit output temp/S0484/log_audit_20260712_193755.json (actionable cluster, tag=AndroidProtocolHandler, count=1).

---

## 1. Проблема / симптом

`AndroidProtocolHandler` (WebView asset resolver) не может открыть `file:///android_asset/d2d_images/cover.jpg` - зафиксировано ровно один раз в процессе приложения (PID 30481) во время `/spec-prerelease` сьюпа. Без краша, без видимого тоста - вероятно, тихий сбой: пользователь просто не увидит ожидаемое динамическое фоновое изображение.

---

## 2. Корневая причина

**Не дефект. Benign self-healing в EPUB-вьюере.** Расследование по самому run-логу (temp/S0484/run_20260712_193755.log, строки 184415-184419):

```
20:39:54.354 E AndroidProtocolHandler: Unable to open asset URL: file:///android_asset/d2d_images/cover.jpg
20:39:54.355 D EpubResourceContentHelper: EPUB: Found resource by exact path 'd2d_images/cover.jpg'
20:39:54.357 D EpubResourceContentHelper: EPUB: Serving intercepted asset 'd2d_images/cover.jpg' from EPUB (117233 bytes, image/jpeg)
20:39:54.405 D EpubViewerManager: firstChapterRendered chapter=0 chapterCount=10
```

- `d2d_images/cover.jpg` - внутренний ресурс (обложка, 117КБ JPEG) **открытой EPUB-книги**, а не asset нашего APK. Литерал `d2d_images` / `cover.jpg` не встречается ни в исходниках, ни в `assets/` app_v2.
- Догадка §0-evidence про `dynamicBackgroundExtension` неверна: `DynamicBackgroundProcessor` - это ImageView edge-pixel extension, не WebView, к загрузке asset'ов отношения не имеет.
- Механизм: `EpubWebViewLifecycle` грузит главы с base URL `file:///android_asset/` (`EpubViewerManager` стр.481). Относительный `d2d_images/cover.jpg` резолвится в `file:///android_asset/d2d_images/cover.jpg`. Встроенный в WebView `AndroidProtocolHandler` пытается открыть его как реальный android_asset, не находит и логирует E-строку. Параллельно наш `shouldInterceptRequest` -> `EpubResourceContentHelper.assetResponse` находит ресурс с первого прохода (`book.resources.getByHref('d2d_images/cover.jpg')`, exact match) и отдаёт содержимое. Обложка отображается корректно.
- Без краша, без тоста (toastCount=0), картинка рендерится. E-строку эмитит внутренний класс WebView (`AndroidProtocolHandler`), а не наш код - подавить его Logcat-вывод из приложения нельзя.

---

## 3. Исправление

**Продуктовый код не меняется - чинить нечего.** EPUB-ресурс отдаётся корректно через перехват; E-строка `AndroidProtocolHandler` - это шум внутреннего резолвера WebView, идущий рядом с успешным перехватом.

Единственный способ убрать E-строку - увести base URL с `file:///android_asset/` на схему без встроенного WebView-обработчика (custom-scheme / `WebViewAssetLoader`). Это рефактор рабочей EPUB-фичи с реальным риском регрессии в резолве всех относительных путей книги, ради подавления одной benign E-строки - не оправдано, тем более перед релизом. Не выделяется в отдельный тикет (косметика).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Проверка

Доказательство - сам run-лог (§2): за E-строкой немедленно следует успешная отдача ресурса из EPUB (117233 байт, image/jpeg) и `firstChapterRendered`. Обложка отрисована, пользовательского влияния нет. Дополнительной проверки на устройстве не требуется.
