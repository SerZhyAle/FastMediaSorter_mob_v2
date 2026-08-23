# Стратегическая спецификация: S1792 - Синхронизация зеркал настроек внутри записи, а не в местах вызова

**Ticket:** S1792
**Status:** Archived
**Priority:** 70
**Date:** 2026-08-17
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - решение владельца по S1692 (квиз 2026-08-17): живые дефекты чинятся отдельным малым тикетом, не дожидаясь стратегической консолидации
**Tactical spec:** `PLAN/S1792_settings-mirror-sync-inside-the-write/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-17

**Текст:** решение владельца, записанное в S1692 §3.3: «fix the theme and compact-player-elements mirror synchronization in a separate small ticket before the strategic consolidation» - и в блоке Quiz decisions: «да, отдельный тикет (исправление не ждёт стратегической консолидации)».

**Захвачено во время:** прохода по очереди, когда S1692 оказался следующим и выяснилось, что постановленного тикета никто не завёл.

---

## 1. Проблема

Настройка, изменённая не с экрана настроек, а применением профиля устройства, доходит до основного хранилища, но не доходит до своего синхронного зеркала. Экран настроек показывает новое значение, приложение продолжает работать по старому, и никакого указания, что нужен перезапуск, нет.

Путь пользователя: Настройки -> Общие -> профиль устройства -> «автомобильная магнитола», «видеоплеер» или «читалка». Профиль прописывает тему оформления и компактность элементов, приложение остаётся с прежними. Помогает принудительное закрытие приложения.

---

## 2. Цели

1. Настройка, изменённая любым путём, доходит до своего зеркала в тот же момент - независимо от того, кто вызвал запись.
2. Пользователь не остаётся в состоянии «экран показывает одно, приложение делает другое».

**Non-goals:**

- Стратегическая консолидация трёх механизмов хранения - это S1692; здесь чинится только проводка двух зеркал.
- Язык интерфейса: он намеренно не синхронизируется в записи настроек, и причина записана в коде рядом - синхронизация затёрла бы системный откат языка значением по умолчанию. Трогать это без отдельного решения нельзя.

---

## 3. Пожелания и ограничения

### 3.2 Жёсткие ограничения

- **Совместимость данных:** файлы-зеркала переименовывать нельзя, у установленных пользователей в них лежат значения.
- **Существующие комментарии:** причина, по которой язык исключён из синхронизации, - требование, а не заметка.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1692 (стратегическая консолидация, откуда пришло решение); S1148 (образец правильной проводки - синхронизация буферизации потоков внутри записи); S0328 (сверка зеркала темы на старте).

---

## 4. Контекст текущей архитектуры

Часть настроек продублирована в синхронные зеркала: значения нужны до того, как асинхронное хранилище доступно - тема применяется при создании экрана, компактность нужна плееру при первой вёрстке.

Запись настроек уже умеет обслуживать зеркало правильно: для буферизации потоков синхронизация лежит **внутри** самой записи и потому срабатывает у любого вызывающего. Для темы и компактности - нет: их зеркала пишутся в местах вызова из интерфейса, то есть на экране настроек и в экране приветствия. Применение профиля идёт мимо интерфейса и потому мимо синхронизации.

Сверка зеркал на старте приложения существует и работает - она и объясняет, почему принудительное закрытие помогает: следующий запуск приводит зеркала в порядок. Внутри текущего процесса до неё дело не доходит.

---

## 5. Предлагаемый подход

Синхронизация обоих зеркал переносится туда, где уже лежит синхронизация буферизации потоков, - внутрь записи настроек, под ту же проверку «значение изменилось». После этого любой путь, каким бы кодом он ни был вызван, обновляет зеркало вместе с основным значением, и места вызова в интерфейсе перестают нести эту обязанность.

Сверка на старте остаётся: она страхует обновление приложения и импорт настроек, где зеркало могло устареть до того, как этот код появился.

---

## 6. Открытые вопросы / Research items

1. **Нужно ли применять тему немедленно, а не только записать зеркало**
   - **Вопрос:** запись зеркала делает следующий экран правильным, но текущий остаётся прежним. Достаточно ли этого, или профиль обязан перекрасить приложение на месте?
   - **Статус:** Resolved
   - **Измерено 2026-08-17:** это две разные операции, и их надо разделить. Запись зеркала - работа слоя данных, применение темы - работа интерфейса: экран настроек сегодня вызывает обе подряд, а старт приложения делает применение отдельным переходом на главный поток именно потому, что из фонового его делать нельзя. Значит запись зеркала переезжает внутрь записи настроек (это чинит расхождение), а применение остаётся у вызывающего из интерфейса. Профиль применяется ровно из двух мест интерфейса - экран профиля в настройках и экран приветствия, - и оба уже живут в модели представления, то есть у обоих есть куда положить применение.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Перенос затронет язык заодно | Средняя | Системный откат языка затрётся значением по умолчанию - дефект хуже исходного | Язык явно вне объёма, его комментарий-требование цитируется в шаге |
| Двойная запись зеркала (и в записи настроек, и в месте вызова) | Низкая | Лишняя запись, но не расхождение | Места вызова в интерфейсе разгружаются в том же изменении |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES - это исправление дефекта.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Зеркало обслуживается записью, а не вызывающим**

- **Решение:** синхронизация зеркала живёт внутри записи настроек.
- **Альтернативы:** добавить вызов синхронизации в применение профиля.
- **Почему:** второй вариант чинит один известный путь и оставляет дефект открытым для следующего, кто запишет настройки не через интерфейс; первый делает свойство невозможным нарушить, и ровно в этой форме оно уже работает для буферизации потоков.

---

## 10. Связи с другими спеками

Блокеров нет. S1692 породил этот тикет и ждёт его завершения как предварительного шага, но сам не блокирован: его объём - другие вопросы.

---

## 11. Критерии готовности (strategic-level)

1. Смена профиля устройства меняет тему и компактность без перезапуска приложения или с явным указанием, что перезапуск нужен.
2. Синхронизация обоих зеркал вызывается из одного места - записи настроек.
3. Язык остаётся исключённым, и причина этого по-прежнему записана рядом.

---

## Тактический план (compact - Simple path, /spec-all)

> Scope: tactical, English, developer handoff. Every step carries a verification predicate. Rationale lives in §1-§5 above.

# Phase 01 - Mirror writes move into the settings write

**Status:** ✅ Complete
**Depends on:** none - foundation phase
**Steps done:** 3 / 3

## Objective

Write both synchronous mirrors - colour theme and compact player elements - from inside the settings write, beside the streams-buffering mirror that already lives there, so a caller that never touches the UI still leaves the mirrors correct. Applying the theme to the running process stays a UI concern and does not move.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | no growth beyond the added block |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsColorThemeHelper.kt` | Modified | mirror write removed, apply kept |
| `app_v2/src/test/java/.../SettingsRepositoryMirrorTest.kt` | New | <= 120 |

## Steps

### Step 01.1 - Write both mirrors inside the settings write

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Beside the existing S1148 streams-buffering mirror block, add the colour-theme mirror write and the compact-player-elements mirror write, each under the same "value actually changed" guard the buffering one uses. Write only - do not apply the theme here: applying touches the UI and this is the data layer. Leave the language block exactly as it is, and keep its comment: it explains that syncing language here would overwrite the system-locale fallback with the DataStore default, which is a requirement, not a note.

**Why:**

> §1: a profile applied from outside the settings screen reaches the store but not the mirrors, so the app keeps running on the old values with no hint that a restart is needed; §5 puts the sync where no caller can miss it.

**Verification:**

- `Grep` - the colour-theme mirror setter and the compact-elements mirror setter both appear in this file.
- `Grep` - the language comment about the system-locale fallback is still present, unchanged.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

### Step 01.2 - Drop the now-duplicated mirror write from the settings screen

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsColorThemeHelper.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Remove the mirror write from the colour-theme helper and keep the apply call: the write now happens inside the settings write that this helper already triggers. Do not touch the welcome screen in this step - it writes the theme before any settings write exists on a fresh install, so its mirror write is load-bearing.

**Why:**

> §5: once the write owns the mirror, a second writer at the call site is a place for the two to disagree again, which is the defect this ticket removes.

**Verification:**

- `Grep` - the helper no longer calls the mirror setter and still calls the apply function.

**Status:** `[x]` done

### Step 01.3 - Test that a non-UI write updates the mirrors

**Files:** `app_v2/src/test/java/.../SettingsRepositoryMirrorTest.kt`
**Depends on:** Steps 01.1, 01.2

**Prompt for developer:**

> Add a unit test that saves settings with a changed colour theme and a changed compact-elements flag through the repository, without going through any UI class, and asserts both mirrors hold the new values afterwards. Assert as well that the language mirror is untouched by the same call.

**Why:**

> §11.2 requires the sync to live in one place; a test that writes through the repository is what keeps a later edit from moving it back to a call site unnoticed.

**Verification:**

- The new test passes.
- `Grep` - the test does not import any `ui.` class.

**Status:** `[x]` done

## Phase Done Criteria

- [x] All three steps `[x]` done.
- [x] `.\a.ps1 fk` exits 0 and the new test passes.
- [x] Dev log entry added for the changed-file set.

## Last Audit

**Date:** 2026-08-18
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 7 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 0

### Manual / on-device

- [ ] Verify profile preset application in settings updates color theme and compact elements synchronously.

