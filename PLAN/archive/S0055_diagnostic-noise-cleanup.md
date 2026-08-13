# Стратегическая спецификация: S0055 — Очистка диагностического шума и завышенных уровней логирования

**Ticket:** S0055
**Status:** Implemented
**Priority:** 60
**Date:** 2026-05-03
**Tier:** 2 — Easy
**Roadmap entry:** Ad-hoc — анализ полевых логов 2026-05-03 (`logs/fastmediasorter_20260503_03*.log`)
**Tactical plan:** `PLAN/S0055_diagnostic-noise-cleanup/INDEX.md`
**Tactical spec:** `PLAN/S0055_diagnostic-noise-cleanup/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк.

---

## 1. Проблема

В одной полевой сессии Quest 3 (~10 минут активности, версия `2.60.5030.252-VR-DEBUG`) лог-файл наполнен пятью разными типами сообщений, которые либо пишутся на завышенном уровне (W вместо D/I), либо являются debug-кодом, попавшим в production-сборку, либо повторяются без новой информации. Это превращает реальные предупреждения в шум, делает поиск настоящих проблем времязатратным и раздувает размер логов.

### 1.1 Перечень шумов

> Историческая нумерация A/B/C/D/E сохраняется по тексту спеки (где про конкретный пункт проще ссылаться по букве). После выпиливания старого пункта C (camera) текущие пункты — **A, B, C(=ex-D), D(=ex-E)**.

| # | Симптом | Цитата (`logs/fastmediasorter_20260503_031502.log`) | Частота за сессию |
|---|---------|------------------------------------------------------|-------------------|
| A | `NetworkFileDataFetcher.cancel() called for <file>` с полным `Exception("Trace")` стеком ~75 строк | строки 5447, 5523, 5601, 5677.. (30+ раз подряд во время одного скролла) | ~30 + |
| B | `fetchBytesFromSmb TIMEOUT: <file> - StandaloneCoroutine was cancelled` (ярлык `TIMEOUT` для штатной отмены корутины при скролле RecyclerView) | строки 8386, 8388, 8390, 8398 | 4 + за один скролл |
| C | `TEST_CREDS: Test credentials file not found at ..` — печатается на каждом старте, файл штатно отсутствует на устройствах конечных пользователей | каждый старт | 1 / запуск |
| D | `performOperation: Operation cancelled by user` + `kotlinx.coroutines.JobCancellationException: Job was cancelled` со стеком — печатается как W при штатной user-cancel | строки 1514–1516 | 1+ за каждое отменённое копирование |

> **Удалено из этой спеки (2026-05-03):** ранее здесь был пункт про `CameraCapture: no handlers` (`ACTION_IMAGE_CAPTURE`/`VIDEO_CAPTURE` без handler на Quest 3). Это **не шум**, а индикатор отсутствующей фичи: на Quest 3 захват кадра возможен через альтернативные API (Passthrough Camera, MediaProjection). Перенесено в **S0058 vr-passthrough-camera-capture**.

### 1.2 Перенумерация после удаления старого пункта C

| Текущая буква | Старая буква | Симптом |
|---|---|---|
| A | A | NetworkFileDataFetcher.cancel Trace |
| B | B | fetchBytesFromSmb mislabel TIMEOUT |
| C | D | TEST_CREDS not found |
| D | E | performOperation cancelled JCE-стек |

В §5.1 ниже фиксы помечены теми же буквами **A, B, C, D** (перенумерованными).

### 1.3 Подтверждение что A — debug-код

`NetworkFileModelLoader.kt:695-701`:

```kotlin
override fun cancel() {
    val fileName = data.path.substringAfterLast('/')
    // Use Exception to capture stack trace of who called cancel
    Timber.d(Exception("Trace"), "NetworkFileDataFetcher.cancel() called for $fileName")
    isCancelled = true
    loadJob?.cancel()
}
```

Комментарий явно указывает: «capture stack trace of who called cancel» — это диагностическое логирование, оставленное при отладке какой-то прошлой проблемы. Сейчас `cancel()` вызывается каждым `RecyclerView.Recycler.dispatchViewRecycled` при прокрутке списка с миниатюрами (см. стек в логе: `MediaFileAdapter.onViewRecycled → ListViewHolder.clearImage → RequestManager.clear → cancel()`) — то есть при каждой нормальной утилизации view-холдера.

---

## 2. Цели

1. Лог-файл одной типичной сессии (5–10 минут, скролл, навигация, копирование) не должен содержать многократных полностраничных стек-трейсов от штатных операций.
2. Уровень логирования каждого из 5 перечисленных сообщений соответствует фактической природе события: WARNING — только для ситуаций, требующих внимания человека; INFO/DEBUG — для штатных переходов состояния.
3. Сообщения остаются информативными для отладки, но не вводят в заблуждение неточным ярлыком (например, `TIMEOUT` для нормальной cancellation).
4. Изменения не должны скрывать настоящие проблемы — там где сейчас полезный контекст (имя файла, причина), он сохраняется.

**Non-goals:**

- Не менять архитектуру логирования (Timber, формат, дисковые ротации).
- Не вводить новые feature-flags для уровней — фиксы должны быть минимальны и локальны.
- Не трогать `OpenXrNative: setupActionSet failed: -22 (non-fatal if profile unsupported)` — он информативен и уже помечен как non-fatal в самом сообщении.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Удалить debug-код пункта A полностью — он не нужен. Если в будущем понадобится снова — поднять через git history.
2. Пункт C: hide-warning только при первом обнаружении отсутствия camera-handler в сессии (single-shot). Повторные попытки нажатия — D/V уровень либо тихо.
3. Пункт E: при `JobCancellationException` логировать одной строкой `Operation cancelled by user (Copy/Move/Delete: <count> items)` без стека. Стек оставить только для не-cancellation-исключений.
4. Пункт D: отсутствие test_credentials.json — норма у всех кроме разработчика; уровень DEBUG, либо логировать только если переменная окружения / build-флаг указывает на тестовый прогон.

### 3.2 Жёсткие ограничения

- **Flavor:** все (standard, lite, photos, legacy, vr).
- **API level:** без изменений (minSdk 26 / 23).
- **Wear OS:** не затрагивается.
- **Производительность:** изменения снижают объём I/O и нагрузку на disk-ротацию — улучшение, не регрессия.
- **Совместимость данных:** нет схемных изменений.
- **Локализация:** строки лога — английские (см. CLAUDE.md «Communication: English in logs»). Менять не требуется, кроме пункта B где ярлык `TIMEOUT` нужно заменить на корректное `CANCELLED` или `ABORTED`.
- **Доступность:** не затрагивается.

---

## 4. Контекст текущей архитектуры

Все 4 источника шума живут в разных слоях:

- **A, B** — слой Glide DataFetcher для сетевых ресурсов (`data/network/glide/`). `cancel()` инициируется RecyclerView через `MediaFileAdapter.onViewRecycled` при прокрутке. SMB-fetcher одновременно ловит `JobCancellationException` в catch и логирует его как `TIMEOUT`.
- **C** — слой репозитория сетевых credentials, опциональная подгрузка тестовых учёток из `Android/data/.../files/test_media/test_credentials.json`. У end-users файла нет.
- **D** — слой выполнения файловых операций (Copy/Move/Delete), общий обработчик прогресса. Cancellation — штатный путь, но печатается как warning со стеком.

Архитектурно объединять эти 4 фикса в один общий механизм (например, «фильтр уровней по типу события») — преждевременная абстракция (CLAUDE.md: «Don't add features beyond what task requires»). Каждый фикс — точечная правка в своём файле.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

**A. Удалить debug stack trace в `NetworkFileDataFetcher.cancel()`**
Удалить строку с `Timber.d(Exception("Trace"), ..)`. Если хочется сохранить минимум — оставить `Timber.v("cancel() $fileName")` без exception. Исходный поведенческий код (`isCancelled = true; loadJob?.cancel()`) не трогается.

**B. Переименовать misleading-ярлык в SMB-fetcher**
В catch-блоке, где ловится `JobCancellationException` / `CancellationException`, заменить ярлык `TIMEOUT` на `CANCELLED`. Реальные I/O-таймауты (когда корутина не отменена, а сработал watchdog) сохраняют ярлык `TIMEOUT`.

**C. Понизить TEST_CREDS missing-file до DEBUG**
В `NetworkCredentialsRepositoryImpl.loadTestCredentials` при отсутствии файла — `Timber.d` вместо `Timber.w`. Прочие ошибки парсинга/чтения — оставить W.

**D. Чистое логирование штатной отмены операций**
В обработчике прогресса/завершения файловых операций — перед `Timber.w(jobCancellationException, "performOperation: Operation cancelled by user")` проверить тип исключения. Если это `JobCancellationException`/`CancellationException` от пользователя — `Timber.i("performOperation: cancelled by user (operation=$opType, processed=$count)")` без стека. Иные исключения — как сейчас, со стеком.

### 5.2 Точки расширяемости

- Шаблон «cancellation vs real failure» из пункта D применим к любому будущему long-running-обработчику. Если повторится в третьем месте — пора вынести в хелпер.

---

## 6. Открытые вопросы / Research items

1. **Пункт A: нужен ли минимальный лог при cancel?**
   - **Вопрос:** оставить `Timber.v("cancel() $fileName")` или удалить полностью?
   - **Дефолт:** удалить полностью — `loadJob?.cancel()` сам по себе логируется в трассировке корутины при `-Dkotlinx.coroutines.debug=on`.
   - **Статус:** Implemented

2. **Пункт D: какой набор операций считать «cancelled by user» vs «cancelled by system»?**
   - **Вопрос:** при backgrounding Activity Android может cancellить корутину — это тоже user-initiated?
   - **Дефолт:** любой `CancellationException` без вложенного reason — INFO; CancellationException с reason содержащим «timeout» / «error» — WARNING со стеком.
   - **Статус:** Implemented

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Удаление trace из A скроет редкий баг где cancel вызывается из неожиданного места | Низкая | Регрессия в диагностике | Если потребуется — вернуть один Timber.v без exception |
| Замена W→D/I в пункте D скрывает реальные баги отмены, не связанные с user-action | Низкая | Тихое падение операций | Условие на тип исключения; не-CancellationException остаются W |
| Понижение TEST_CREDS до D в debug-сборке усложнит разработчику видеть, что credentials не подгрузились | Низкая | Тестовые подключения не работают «по непонятной причине» | Однострочный D-лог сохраняет факт; в release-сборке вообще не нужно |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений. Все правки — внутренние; пользовательских функций не добавляет и не убирает.

---

## 9. Архитектурные решения (ADR)

**ADR-1: 4 точечных фикса вместо одного централизованного фильтра уровней**

- **Решение:** каждый из 4 шумов исправляется в своём месте без введения общего механизма «log-level policy».
- **Альтернативы:** (a) ввести Timber tree с конфигом «понизить W→D для тегов X, Y, Z»; (b) ввести аннотации/маркеры на класс для подавления.
- **Почему:** объём правки — 4 строки в 4 файлах. Любая централизация — преждевременная абстракция (CLAUDE.md правило «нет фабрик/абстракций сверх задачи»). При появлении 10+ похожих случаев — пересмотреть.

---

## 10. Связи с другими спеками

- **S0051** (bugfix-network-datasource-pause-cancel) — затрагивает тот же сетевой слой, но решает другую проблему (продолжение чтения после паузы плеера). Пункты A/B данной спеки не пересекаются — A/B про скролл RecyclerView и thumbnail cancellation, S0051 про playback pause.
- **S0052** (bugfix-sftp-datasource-log-spam, Verified) — прецедент: отдельный фикс log-spam в смежном слое; та же логика «debug-код в production».
- **S0058** (vr-passthrough-camera-capture, Draft) — старый пункт «C: CameraCapture warning» из этой спеки переехал туда, потому что он про отсутствующую фичу, а не про шум.

---

## 11. Критерии готовности (strategic-level)

1. После сборки с фиксом — типичная 10-минутная сессия (старт + скролл списка SMB-ресурса + копирование файла + отмена копирования) выдаёт лог не более 50 KB warning-строк против ~500 KB сейчас.
2. `Timber.d(Exception("Trace"), ..)` отсутствует в `NetworkFileDataFetcher.cancel()` (проверяемо grep).
3. `fetchBytesFromSmb` — `TIMEOUT` остаётся только при истинном таймауте, при cancellation — `CANCELLED` (проверяемо grep + manual).
4. `TEST_CREDS .. not found` пишется на уровне D (проверяемо grep по `Timber.d`/`Timber.w` в `NetworkCredentialsRepositoryImpl`).
5. `performOperation: Operation cancelled by user` без стека `JobCancellationException` в логе (manual: запустить копирование, отменить — увидеть однострочную I-запись).

---

## 12. Тактическая спецификация

После утверждения — `/spec-tech diagnostic-noise-cleanup` создаст `PLAN/S0055_diagnostic-noise-cleanup/` с фазами по каждому из 4 пунктов A–D.

---

## Last Audit

**Date:** 2026-05-03
**Mode:** field-log
**Evidence:** `logs/fastmediasorter_20260503_180405.log`, `logs/fastmediasorter_20260503_180505.log`
**Outcome:** Implemented — исходные пункты A-D не регрессировали, но вскрыт новый смежный шум ниже по стеку

### Confirmed

- Сообщения из исходного списка §1.1 (`cancel Trace`, ложный `TIMEOUT` при отмене, `TEST_CREDS` на warning-уровне, верхнеуровневый `performOperation .. JobCancellationException`) в последних логах не проявились.
- Пользовательская отмена на уровне диалога уже идёт через info-path и не возвращает старый стек `JobCancellationException` из исходного пункта D.

### New findings outside the original scope

1. При teardown плеера по-прежнему всплывает `PlaybackPosition: Failed to get position .. JobCancellationException`.
2. В тот же момент остаётся error-level `Failed to load destinations` из `DestinationButtonsManager`.
3. В user-cancelled SMB-copy глубже по стеку всё ещё видны `Failed to download file from SMB` и затем `Unexpected error during atomic copy`.
4. Последний пункт привязан уже не только к log-noise, а к отдельному функциональному дефекту temp-файла; он вынесен в `S0069`.
