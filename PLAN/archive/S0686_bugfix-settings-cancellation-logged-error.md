# Стратегическая спецификация: S0686 - Settings VM logs coroutine cancellation as ERROR

**Ticket:** S0686
**Status:** Archived
**Priority:** 90
**Date:** 2026-06-25
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - parked during log analysis 2026-06-25
**Tactical spec:** `PLAN/S0686_bugfix-settings-cancellation-logged-error/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Захваченный материал (inbox)

> Сырой захват идеи на лету. Вербатим-текст и evidence. Распределяется по §1/§3.1/§6 при доработке через `/spec` или `/spec-update`.

**Захвачено:** 2026-06-25 (во время анализа лога первой инсталляции)

**Текст:**

SettingsViewModel logs coroutine cancellation as ERROR on screen teardown. First-install logcat (Pixel-10-Pro-Fold-Android-17_2026-06-25_120933.logcat) shows 2x `E SettingsViewModel: Error getting last sync timestamp` with `kotlinx.coroutines.JobCancellationException: Job was cancelled` at 12:09:16.367 and 12:09:16.404, fired during the rapid Welcome->Settings activity recreation. Root cause: SettingsViewModel.kt:545-548 `catch (e: Exception) { Timber.e(e, "Error getting last sync timestamp"); null }` swallows CancellationException and logs it at ERROR level. The file has 17 broad `catch (e: Exception)` blocks - several likely share the same defect (treating normal coroutine cancellation as a real error, polluting logs with false ERROR lines on every settings teardown). Scope: sweep SettingsViewModel (and audit sibling ViewModels) to rethrow CancellationException (or catch it before generic Exception) so benign cancellations are not logged at Timber.e. Violates project rule "Reserve Timber.e for real errors only" + neuroslop broad-catch guidance (CLAUDE.md Rule 19).

**Evidence (лог-строки):**

```
[12:09:16.367] E SettingsViewModel: Error getting last sync timestamp
               kotlinx.coroutines.JobCancellationException: Job was cancelled; job=SupervisorJobImpl{Cancelling}@e222c2a
[12:09:16.404] E SettingsViewModel: Error getting last sync timestamp
               kotlinx.coroutines.JobCancellationException: Job was cancelled; job=SupervisorJobImpl{Cancelling}@e222c2a
```

**Источник:** `logs/Pixel-10-Pro-Fold-Android-17_2026-06-25_120933.logcat` (первый запуск, Pixel 10 Pro Fold, Android 17 / API 37).
**Код:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt:545-548` (плюс ещё 16 broad-catch блоков в том же файле).

**Вложения:**

Вложений нет (evidence захвачен текстом выше; лог-файл лежит в `logs/`).

---

## 1. Проблема

Широкие `catch (Exception)` внутри корутин экрана Настроек трактуют штатную отмену корутины (`CancellationException`) как реальную ошибку. При пересоздании экрана (поворот, складывание fold-устройства, применение пресета профиля на первом запуске) выполняющаяся операция отменяется - и пользователь видит ложный error-тост («Не удалось очистить кэш» и подобные), а в логах появляется ложная строка уровня ERROR. Это вводит пользователя в заблуждение и зашумляет диагностику.

---

## 2. Цели

1. Отмена корутины не приводит к показу error-тоста.
2. Отмена корутины не пишется в лог уровнем ERROR/WARN.
3. Реальные ошибки операций по-прежнему сообщаются пользователю и логируются.
4. Обработка отмены переиспользуема, чтобы паттерн не повторялся вручную на каждом сайте.

**Non-goals:**

- Сплошной sweep всех ~180 широких `catch` по приложению - в этой итерации только путь первого запуска и экран Настроек.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

<Нумерованный список желаемого, но необязательного к первой итерации.>

### 3.2 Жёсткие ограничения

- **Flavor:** все - правка в общем коде src/main.
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** нейтрально.
- **Совместимость данных:** миграции нет.
- **Локализация:** новых пользовательских строк не добавляет.
- **Доступность:** не применимо - визуальных изменений нет.

### 3.3 Owner inputs (Approval gate)

<Заполняется при переходе Draft → Approved (через /spec или /spec-update). В скелете оставить пустым, кроме обязательного поля ниже.>

- **Related tickets:** none

---

## 4. Контекст текущей архитектуры

Корутины экранного слоя (ViewModel и UI-хелперы Настроек) запускаются в scope, привязанном к жизненному циклу; при пересоздании экрана scope отменяется. Существующие широкие catch не отличают штатную отмену от реальной ошибки, поэтому без явной обработки `CancellationException` проблему из §1 решить нельзя.

---

## 5. Предлагаемый подход

Единый переиспользуемый примитив-расширение в слое утилит: первым делом в любом широком catch внутри корутины пробрасывает отмену дальше, иначе передаёт управление штатной обработке ошибки. Применяется как одна строка в начале существующих catch - и в слое ViewModel (лог-онли catch), и в UI-хелперах Настроек (catch с тостом). Эталон уже существовал в коде: ручной сетевой синк ловит отмену отдельной веткой до общего `Exception`.

### 5.1 Основные столпы / модули

- Утилита отмены (один примитив, общий для всех слоёв).
- Слой ViewModel Настроек: лог-онли catch.
- UI-хелперы Настроек: catch, показывающие тост.

### 5.2 Потоки данных и событий

Корутина экрана отменяется при тейрдауне -> широкий catch ловит отмену -> примитив пробрасывает её как кооперативную -> ветка ошибки (лог + тост) не выполняется.

### 5.3 Точки расширяемости

Примитив применим в любом широком catch внутри корутины по всему приложению - оставшиеся ~180 сайтов можно покрыть отдельной итерацией без новой инфраструктуры.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет.

---

## 7. Риски

- Пропустить сайт с тем же дефектом. Вероятность средняя. Последствие - остаточные ложные тосты. Митигация - device-тест по перечню операций из status note, отдельная итерация для остального приложения.
- Проглотить реальную ошибку, спутав её с отменой. Вероятность низкая. Последствие - тихий сбой. Митигация - примитив пробрасывает только `CancellationException`, остальное идёт в штатную ветку.

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES - внутреннее исправление, новой возможности нет.

---

## 9. Архитектурные решения (ADR)

ADR нет - решение по устоявшемуся паттерну проекта (проброс отмены до общего catch).

---

## 10. Связи с другими спеками

Связей нет.

---

## 11. Критерии готовности (strategic-level)

1. Поворот или складывание устройства во время операции в Настройках не показывает ложный error-тост.
2. В логах нет ложной строки ERROR/WARN об отменённой операции при тейрдауне экрана.
3. Настоящая ошибка операции по-прежнему даёт и тост, и запись в лог.
4. Сборка standardDebug проходит.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0686` - создаст `PLAN/S0686_bugfix-settings-cancellation-logged-error/` с фазами.

---

## Last Audit

**Date:** 2026-06-26
**Mode:** full (device-test via spec-sweep)
**Outcome:** Verified
**Counts:** PASS 4 · WARN 0 · FAIL 0 · MANUAL 0 · INCONCLUSIVE 1

Fix proven on-device: rotation caught 58 ms into a 47 MB clearCache (activity recreated mid-op) produced no false error toast and no false `E`/`JobCancellationException` line across 12 Settings recreations. Genuine-failure branch INCONCLUSIVE (emulator clearCache always succeeds) - confirmed by code-read. Debug tag removed on Verified flip.

### Manual / on-device

- [x] Rotation during Clear cache shows NO false error toast - verified on-device 2026-06-26
- [x] No false ERROR/WARN line for the cancelled op in logcat on Settings teardown - verified on-device 2026-06-26
- [x] `S0686:` debug tag fired (clear-cache flow exercised, 3 runs) - verified on-device 2026-06-26
- [x] Activity recreated mid-flight (47 MB cache, rotation 58 ms into op, deleteRecursive still running) still produced no false toast / E line - verified on-device 2026-06-26
- [ ] Genuine operation failure still toasts + logs normally - INCONCLUSIVE (not exercised; emulator cache clear always succeeds, needs contrived no-permission fixture; success/failure branches verified by code-read only)

Evidence: temp/S0686_mobile_test_scenario_20260626_1433.md · log temp/S0686_run_20260626_1436.log · screens temp/S0686_screens/

Original spec evidence (`E SettingsViewModel: Error getting last sync timestamp` + `JobCancellationException` on rapid Settings recreation) did NOT reproduce across 12 orientation recreations in this session.

## Revision History

- **2026-06-26** - by `/spec-test-device` (`claude-opus-4-8[1m]`, device: emulator-5554, Android 17 / SDK 37)
  - Scenario: temp/S0686_mobile_test_scenario_20260626_1433.md · PASS/FAIL/INCONCLUSIVE 4/0/1 · Errors in log: 0 (app-side)
