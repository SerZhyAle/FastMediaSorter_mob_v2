# Draft: S0495 - починить компиляцию CameraCaptureSaverTest

**Status:** Archived
**Priority:** 60
**Date:** 2026-06-18

## 0. Raw capture (symptom / evidence)

`:app_v2:compileStandardDebugUnitTestKotlin` падает на:

```
app_v2/src/test/java/com/sza/fastmediasorter/data/capture/CameraCaptureSaverTest.kt:42:103
  No value passed for parameter 'settingsRepository'.
  No value passed for parameter 'imageClipboardWriter'.
```

Production-класс `CameraCaptureSaver` получил конструкторные параметры `settingsRepository` и `imageClipboardWriter`, но тест на строке 42 конструирует его без них.

## 1. Проблема

Сломана компиляция всего unit-test source set `standardDebugUnitTest` из-за устаревшего конструктора в `CameraCaptureSaverTest`. Любой запуск `testStandardDebugUnitTest` (и компиляция новых тестов) блокируется этой одной ошибкой. Обнаружено при добавлении unit-тестов в S0493 - мои тесты скомпилировались чисто, но suite не собрался из-за этого файла.

## 2. Что нужно

- Обновить конструкцию `CameraCaptureSaver` в `CameraCaptureSaverTest.kt:42`: передать моки `settingsRepository` и `imageClipboardWriter`.
- Прогнать класс: `--tests "*CameraCaptureSaverTest"`, убедиться, что компилируется и проходит (или зафиксировать как pre-existing runtime-fail отдельно).

## 3. Заметка

Не входит в объём S0493 (камера ≠ send-to). Запаркован при ревью S0493, чтобы разблокировать unit-test суиту.

## 4. Решение

- `CameraCaptureSaverTest.kt:42` теперь конструирует `CameraCaptureSaver` с двумя добавленными S0469-параметрами: `FakeSettingsRepository()` (готовый фейк из `testing/fakes/`) и реальный `ImageClipboardWriter(context)`.
- Фейк отдаёт дефолтный `AppSettings` (`cameraCaptureCopyToClipboard = false`), поэтому ветка клипборда не выполняется и тесты остаются сфокусированы на трёх маршрутах сохранения - новые тесты не добавлялись (вне объёма).
- Попутно убран устаревший probe `Timber.d("S0494: ..")` в `MaterializeShareContentUseCase.kt:94` (S0494 уже не `BlockNeedUserTest`), который вешал ticket-log-гейт.

## 5. Проверка

- `:app_v2:compileStandardDebugUnitTestKotlin` - компилируется.
- `--tests "*CameraCaptureSaverTest"` - 6 тестов, 0 падений, 0 ошибок.
