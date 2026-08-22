# Стратегическая спецификация: S1855 - Тест атласа логотипов ищет константы в файле, из которого их вынесли

**Ticket:** S1855
**Status:** Archived
**Priority:** 60
**Date:** 2026-08-20
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - находка при работе над S1853, 2026-08-20
**Tactical spec:** нет отдельной папки - фазы записаны в §12 (compact spec, tier 2)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-20

**Текст:**

Найдено при прогоне полного юнит-набора в рамках S1853 (`.\a.ps1 fu`, exit 1, 3904 теста, 8 падений).

`StreamLogoAtlasSlicerTest > app grid constants match the offline packer script` падает с `AssertionError: packer constant LogoTileW not found`.

Тест сверяет константы нарезки атласа с упаковщиком и читает их из `scripts/streams/collect-stream-candidates.ps1`:

```kotlin
val packer = File("../scripts/streams/collect-stream-candidates.ps1")
..
assertEquals(StreamLogoAtlasSlicer.TILE_W, packerValue(text, "LogoTileW"))
```

Константы там больше не объявлены - они лежат в `scripts/streams/modules/StreamPublisher.Artwork.ps1`:

```
803:$script:LogoTileW = 136
804:$script:LogoTileH = 136
805:$script:LogoCols = 59
```

`LogoMaxRows` в модуле не найден вовсе - тест ждёт значение 60.

То есть контракт «две половины одного соглашения» сегодня не проверяется ничем: упаковщик и нарезчик могут разъехаться, а тест продолжит падать по другой причине, и падение уже стало фоновым шумом полного набора.

**Вложения:** нет

---

## 1. Проблема

Юнит-тест, который существует ровно для того, чтобы связать константы сетки логотипов в Kotlin с константами упаковщика на PowerShell, читает файл, из которого эти константы вынесли при разбиении скрипта на модули. Тест падает не потому, что контракт нарушен, а потому что смотрит не туда - и, пока он красный по этой причине, настоящее расхождение он показать не может.

---

## 2. Цели

1. Тест сверяет константы с тем файлом, где они объявлены сейчас.
2. Расхождение значений между Kotlin и упаковщиком снова роняет тест по существу, а не по отсутствию файла.
3. Полный юнит-набор перестаёт нести это падение как фоновый шум.

**Non-goals:**

- Не менять сами значения нарезки и не трогать формат листа логотипов.
- Не переносить константы обратно в старый скрипт ради теста.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

Отдельных пожеланий нет - находка зафиксирована агентом.

### 3.2 Жёсткие ограничения

- **Flavor:** не флейворная работа - тест общий.
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Локализация:** пользовательских строк нет.

### 3.3 Owner inputs (Approval gate)

<!-- auto-approved by /spec-all - 2026-08-20 -->

- **Related tickets:** S1819, S1841, S1843 - тикеты, менявшие лист логотипов и упаковщик. Блокеров нет.
- **UI:** пользовательской поверхности нет - правка только в тестовом исходнике.
- **Flavor:** без флейворного разделения - тест общий для всех сборок.
- **Data:** схема и форматы данных не меняются.
- **API:** публичных сигнатур приложения не касается.

---

## 4. Контекст текущей архитектуры

- Приложение: `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamLogoAtlasSlicer.kt`, companion object - `TILE_W = 136`, `TILE_H = 136`, `COLS = 59`.
- Упаковщик: `scripts/streams/modules/StreamPublisher.Artwork.ps1:803-805` - `$script:LogoTileW/LogoTileH/LogoCols` с теми же значениями.
- Тест: `app_v2/src/test/java/com/sza/fastmediasorter/ui/streams/StreamLogoAtlasSlicerTest.kt` читает `../scripts/streams/collect-stream-candidates.ps1`. Файл существует (это точка входа-оркестратор), поэтому `assertTrue(packer.exists())` проходит, а разбор регулярным выражением не находит ни одной константы - падение приходит на первой же `packerValue`.
- `LogoMaxRows` не переименован, а снят: S1841 убрал потолок в 60 строк, высота листа теперь следует числу тайлов. Реальный потолок объявлен там же как `$script:LogoMaxSheetPx = 16383` - предел размерности VP8 (14 бит), то есть 120 строк при тайле 136 px.
- Ссылка на монолит в тестах ровно одна - других потребителей у неё нет.

---

## 5. Предлагаемый подход

1. Перенаправить парность-тест на `../scripts/streams/modules/StreamPublisher.Artwork.ps1` - файл, где константы объявлены сейчас.
2. Заменить проверку исчезнувшего `LogoMaxRows` на проверку действующего `LogoMaxSheetPx`: это и есть потолок, который сегодня ограничивает лист.
3. Вывести `PACKER_ROW_BUDGET` из проверенного потолка (`LogoMaxSheetPx / TILE_H`) вместо литерала 60, чтобы тест границ `isInBounds` опирался на то же соглашение, которое тест парности только что сверил.
4. Значения нарезки (136/136/59) не трогать - они совпадают по обе стороны.

---

## 6. Открытые вопросы / Research items

1. **Где теперь единственный дом этих констант**
   - **Вопрос:** объявлены ли `LogoMaxRows` и остальные в модуле, или часть значений исчезла при разбиении.
   - **Нужно выяснить:** полный список констант упаковщика и их текущее место.
   - **Статус:** Resolved - `scripts/streams/modules/StreamPublisher.Artwork.ps1` объявляет `LogoTileW = 136`, `LogoTileH = 136`, `LogoCols = 59`, `LogoMaxSheetPx = 16383`, `LogoMinSourcePx = 96`. `LogoMaxRows` не переехал, а снят вместе с потолком в 60 строк (S1841); его роль ограничителя перешла к `LogoMaxSheetPx`.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Тест «чинится» подгонкой ожидаемых значений вместо адреса файла | Средняя | Контракт остаётся непроверенным, расхождение всплывёт у пользователя | Правится путь к файлу; значения меняются только если разошлись по существу |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES.

---

## 9. Архитектурные решения (ADR)

ADR нет - решение по устоявшимся паттернам проекта.

---

## 10. Связи с другими спеками

- S1843, S1841, S1819 - меняли лист логотипов и упаковщик. Не блокеры.

---

## 11. Критерии готовности (strategic-level)

1. `.\a.ps1 fu` не содержит падения `app grid constants match the offline packer script`.
2. Искусственное расхождение значения в упаковщике роняет этот тест.

---

## 12. Фазы реализации (compact, /spec-all Simple path)

### Phase 01 - Re-point the parity test at the packer module

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/streams/StreamLogoAtlasSlicerTest.kt`
**Steps done:** 2 / 2

#### Step 01.1 - Read the grid constants from the module that declares them

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/streams/StreamLogoAtlasSlicerTest.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `app grid constants match the offline packer script`, change the packer path from `../scripts/streams/collect-stream-candidates.ps1` to `../scripts/streams/modules/StreamPublisher.Artwork.ps1` and update the comment above it to name that module. Keep the `TILE_W` / `TILE_H` / `COLS` assertions and the `packerValue` regex unchanged.

**Why:**

The test reads the file the constants were moved out of when the publisher script was split into modules, so it fails on a missing constant rather than on a real drift, and while it is red for that reason it cannot show a genuine divergence between the two halves of the grid contract.

**Verification:**

- `Grep` - `modules/StreamPublisher.Artwork.ps1` matches once in the test file.
- `Grep` - `File("../scripts/streams/collect-stream-candidates.ps1")` returns zero hits in `app_v2/src/test`. The predicate names the code reference, not the string: the comment above the call deliberately keeps the old script's name, because "the constants used to live there" is the whole reason this path is worth explaining.

**Status:** `[x]` done

---

#### Step 01.2 - Replace the retired row cap with the live sheet ceiling

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/streams/StreamLogoAtlasSlicerTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Drop the `LogoMaxRows` assertion and the literal `PACKER_ROW_BUDGET = 60`. Assert `packerValue(text, "LogoMaxSheetPx")` against a new companion constant `PACKER_MAX_SHEET_PX = 16383`, and derive `PACKER_ROW_BUDGET` as `PACKER_MAX_SHEET_PX / StreamLogoAtlasSlicer.TILE_H`. Update the KDoc of both constants and the comment in `isInBounds rejects negative and over-range indices on the packer row budget` to state that the ceiling is the VP8 14-bit dimension limit, not a row cap.

**Why:**

`LogoMaxRows` was not renamed but removed with the 60-row cap in S1841, so an assertion against it can never pass again, and the bounds test's own premise - that the row cap is the stable ceiling - is false while the only ceiling the packer still declares is `LogoMaxSheetPx`.

**Verification:**

- `Grep` - `packerValue(text, "LogoMaxRows")` returns zero hits in `app_v2/src/test`. As above, the predicate names the assertion, not the string: the KDoc keeps the retired constant's name so the next reader learns the cap was removed rather than renamed.
- `Grep` - `PACKER_MAX_SHEET_PX = 16383` matches once.
- `.\a.ps1 fu` filtered to `StreamLogoAtlasSlicerTest` - all 5 tests pass.

**Status:** `[x]` done

---

#### Phase Done Criteria

- [x] Both steps above are `[x]` done.
- [x] `StreamLogoAtlasSlicerTest` passes in full - `check-standard-fast.ps1 -Mode Unit -Tests ..StreamLogoAtlasSlicerTest` PASS, exit 0 (2026-08-20 19:25).
- [x] An artificial change to `$script:LogoTileW` (136 -> 137) fails the parity test on substance - `AssertionError at StreamLogoAtlasSlicerTest.kt:82`, exit 1; reverted byte-exact and re-confirmed PASS, exit 0.
- [x] Dev log entry added via `scripts/post-change.ps1` - `post-change: PASS (Mixed, 127500 ms)`, exit 0.

---

## Last Audit

**Date:** 2026-08-20
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 12 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Manual / on-device

- [ ] Full `.\a.ps1 fu` still carries the other failures found in the same S1853 run - unrelated to this contract and not re-measured here. This ticket verified its own class in isolation: `check-standard-fast.ps1 -Mode Unit -Tests ..StreamLogoAtlasSlicerTest` PASS, exit 0.
