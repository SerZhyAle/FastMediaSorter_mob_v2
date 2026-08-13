# Спецификация (compact bugfix): S1391 - Пре-релизный вердикт валится на эмуляторном шуме

**Ticket:** S1391
**Status:** Archived
**Priority:** 55
**Date:** 2026-08-04
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-04

**Захвачено во время:** S0484 (`/spec-prerelease` sweep)

**Текст:**

prerelease-verdict.ps1 fails the sweep on emulator-only benign error clusters: this run had exactly 10 app-process error lines in 27 minutes - 8x EGL_emulation eglQueryContext EGL_BAD_ATTRIBUTE, 1x "platform: Failed to open rendernode", 1x cr_AndroidProtocolHandler "Unable to open asset URL: file:///android_asset/d2d_images/cover.jpg" which EpubResourceContentHelper serves successfully 3 ms later - zero toasts, zero crashes, zero ANR, Maestro 17/17 green, all perf checkpoints within limits, yet the verdict returned pass=false actionableErrors=7. Evidence: temp/S0484/run_20260804_160126.log, temp/S0484/log_audit_20260804_160126.json (49 "actionable" clusters, all system/emulator: audio HAL pcmWrite, CellBroadcastUtils, Google keyboard AbstractOpenableExtension, MDD/DownloadManager, WifiStaIfaceAidlImpl, Codec2). Needs a benign allowlist so the gate blocks on real defects only.

---

## 1. Проблема / симптом

Прогон `/spec-prerelease` 2026-08-04, emulator-5554 (API 35), standard-debug `v2.60.8041.533-DEBUG`, окно захвата 27 минут.

Что дал прогон по существу:

- Maestro capability suite: 17 из 17 флоу зелёные.
- Перф-чекпоинты: cold-start 3887/5000, network-listing 917/15000, player-open 3949/4000 - все в пределах; list-scroll 83.65/20 помечен advisory (эмулятор).
- Тостов / снекбаров / `showError` в логе: 0. Крашей: 0. ANR: 0.

Все строки уровня E из процессов приложения за весь прогон (полный список):

```
08-04 16:15:42.753  6872  7137 E platform: Failed to open rendernode: No such file or directory
08-04 16:15:43.865  6872  7117 E cr_AndroidProtocolHandler: Unable to open asset URL: file:///android_asset/d2d_images/cover.jpg
08-04 16:18:26.573  8257  8452 E EGL_emulation: eglQueryContext 32c0  EGL_BAD_ATTRIBUTE
08-04 16:18:26.573  8257  8452 E EGL_emulation: tid 8452: eglQueryContext(2161): error 0x3004 (EGL_BAD_ATTRIBUTE)
08-04 16:19:17.512  8999  9118 E EGL_emulation: eglQueryContext 32c0  EGL_BAD_ATTRIBUTE
08-04 16:19:17.513  8999  9118 E EGL_emulation: tid 9118: eglQueryContext(2161): error 0x3004 (EGL_BAD_ATTRIBUTE)
08-04 16:21:08.564  9741  9888 E EGL_emulation: eglQueryContext 32c0  EGL_BAD_ATTRIBUTE
08-04 16:21:08.564  9741  9888 E EGL_emulation: tid 9888: eglQueryContext(2161): error 0x3004 (EGL_BAD_ATTRIBUTE)
08-04 16:22:20.667 10365 10501 E EGL_emulation: eglQueryContext 32c0  EGL_BAD_ATTRIBUTE
08-04 16:22:20.667 10365 10501 E EGL_emulation: tid 10501: eglQueryContext(2161): error 0x3004 (EGL_BAD_ATTRIBUTE)
```

Строка `cr_AndroidProtocolHandler` доброкачественна по конструкции - через 3 мс тот же ресурс отдаёт перехватчик:

```
08-04 16:15:43.867  6872  7117 D EpubResourceContentHelper: EPUB: Found resource by exact path 'd2d_images/cover.jpg'
08-04 16:15:43.868  6872  7117 D EpubResourceContentHelper: EPUB: Serving intercepted asset 'd2d_images/cover.jpg' from EPUB (117233 bytes, image/jpeg)
```

При этом `prerelease-verdict.ps1` вернул `pass=false`, `actionableErrors=7`, exit 1 - то есть сорвал релизный гейт при нулевом реальном дефекте.

`prerelease-log-audit.ps1` (exit 1) насчитал 49 "actionable"-кластеров, из которых ни один не принадлежит приложению: `android.hardware.audio@7.1-impl.ranchu pcmWrite`, `CellBroadcastUtils`, `AbstractOpenableExtension` (гугловая клавиатура), `MDD`/`DownloadManager`/`DelightKLPDownloader`, `WifiStaIfaceAidlImpl`, `Codec2-*`, `AppOps`/`AppOpService`, `lowmemorykiller`.

Отдельно: системный `PermissionService` пишет E-строку `Permission android.permission.WRITE_EXTERNAL_STORAGE isn't requested by package com.sza.fastmediasorter.debug` на каждый запуск Maestro-флоу (20+ раз за прогон) - это Maestro пытается выдать разрешение, которого приложение не объявляет. Тоже шум.

Эвиденс:

- `temp/S0484/run_20260804_160126.log` (71 МБ, 258308 строк)
- `temp/S0484/log_audit_20260804_160126.json`
- `temp/S0484/metrics_20260804_160126.json`
- `temp/S0484/maestro_suite_20260804_160126.json`

---

## 2. Корневая причина

Установлена. Два независимых списка исключений отстали от того, что реально пишет образ эмулятора; ни один из двух скриптов не сломан по логике.

`prerelease-log-audit.ps1` держит три фильтра: `$foreignTagPatterns` (теги чужих процессов, отбрасываются целиком), `$benignPatterns` (сигнатуры сообщений) и `$benignTagSignaturePairs` (пара тег плюс сообщение для тегов, которые слишком общие для сплошного отбрасывания). Из 49 «actionable»-кластеров прогона ни один не принадлежал процессу приложения, но ни один и не попадал ни в один из трёх фильтров: аудио-HAL `ranchu`, `CellBroadcastUtils`, клавиатурные `AbstractOpenableExtension` и `ExpressiveConceptModelManager`, стек загрузки моделей `MDD`/`DownloadManager`/`DelightKLPDownloader`, `WifiStaIfaceAidlImpl`, семейство `Codec2-*` и `C2Goldfish*`, `lowmemorykiller`, `JavaBinder`, `hwservicemanager`, `BLASTSyncEngine`. Отдельный класс - строка `Not starting debugger since process cannot load the jdwp agent`, которую пишет каждый отлаживаемый процесс образа: она приходит под именем процесса в качестве тега, поэтому списком тегов не ловится в принципе и требует сигнатуры сообщения.

`prerelease-verdict.ps1` считает иначе: берёт `search-log.ps1 -Errors -AppOnly -Unique` и вычитает то, что совпало с `$expectedFallbacks`. `-AppOnly` на этом захвате фильтрует по PID приложения, а не по пакету, поэтому эмуляторные строки, написанные в процессе приложения, остаются: `EGL_emulation` при запросе контекста, `platform: Failed to open rendernode` и `cr_AndroidProtocolHandler: Unable to open asset URL`. Все семь оставшихся строк были из этих трёх источников.

Строка `cr_AndroidProtocolHandler` доброкачественна по построению: WebView сначала пробует открыть asset напрямую, не находит его и пишет E, а через три миллисекунды тот же ресурс отдаёт `EpubResourceContentHelper` из EPUB - это видно в логе прогона.

Существенная деталь для правки: `search-log.ps1 -Pattern` сопоставляется с телом сообщения, а не с тегом, поэтому запись вида `cr_AndroidProtocolHandler: Unable to open asset URL` в `$expectedFallbacks` не срабатывает - префикс тега надо убирать.

---

## 3. Исправление

Оба списка пополнены, логика подсчёта не менялась.

`scripts/devtest/prerelease-log-audit.ps1`:

- В `$foreignTagPatterns` добавлены теги чужих процессов из списка выше, включая имена процессов других приложений, которые приходят как теги.
- В `$benignPatterns` добавлены три сигнатуры сообщений, которые встречаются под разными тегами и списком тегов не ловятся: `Not starting debugger since process cannot load the jdwp agent`, `Failed to open rendernode`, `cr_AndroidProtocolHandler.*Unable to open asset URL`.
- В `$benignTagSignaturePairs` добавлена пара `AppOps` плюс `Trying to set mode for unknown uid` - тег слишком общий, чтобы отбрасывать его целиком, поэтому именно пара.

`scripts/devtest/prerelease-verdict.ps1`:

- В `$expectedFallbacks` добавлены `EGL_emulation`, `eglQueryContext`, `Failed to open rendernode`, `Unable to open asset URL: file:///android_asset`, `Not starting debugger since process cannot load the jdwp agent` и `isn't requested by package`. Записи намеренно без префикса тега - см. §2.

Каждая добавленная запись снабжена комментарием с причиной, как того требует шапка соответствующего списка: любая запись глушит настоящую красную строку.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0484 (`/spec-prerelease`), S1117 (prerelease stream liveness report)

---

## 4. Проверка

Выполнена 2026-08-04 на сохранённом логе прогона, содержимое лога не менялось.

- `prerelease-log-audit.ps1 -LogFile temp/S0484/run_20260804_160126.log` - было 49 actionable-кластеров и exit 1, стало `actionable clusters: 0 | benign: 6 | error toasts: 0`, exit 0.
- `prerelease-verdict.ps1 -LogFile temp/S0484/run_20260804_160126.log -MetricsFile temp/S0484/metrics_20260804_160126.json -MaestroResults temp/S0484/maestro_suite_20260804_160126.json -Json` - было `pass=false actionableErrors=7` и exit 1, стало `{"pass":true,...,"actionableErrors":0}`, exit 0. Perf и Maestro в разборе не изменились: 17/17 зелёных, list-scroll остался advisory.

Промежуточный результат по пути, зафиксирован намеренно: после первой правки аудит дал ровно один оставшийся кластер, `AppOps x44 Trying to set mode for unknown uid 10215`, а вердикт - ровно одну оставшуюся строку, `cr_AndroidProtocolHandler`. Обе доведены до нуля адресно, а не расширением фильтра «на всякий случай».

Тест на неослепление: ни одна добавленная запись не содержит имён классов приложения и не совпадает с текстами его собственных ошибок - фильтры бьют по тегам чужих процессов и по сигнатурам сообщений системного образа.
