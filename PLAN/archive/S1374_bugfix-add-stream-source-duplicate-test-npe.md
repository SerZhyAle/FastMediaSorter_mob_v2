# Спецификация (compact bugfix): S1374 - NPE в duplicate-тесте Add Stream Source

**Ticket:** S1374
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-03
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-03

**Текст:**

Во время обязательной проверки S1338 команда `pwsh -NoProfile -File .\a.ps1 fu` завершилась с exit 1. Один из 3098 тестов упал:

`AddStreamSourceUseCaseTest > duplicateUrl_isRejected_withoutInsertOrStat FAILED`

`java.lang.NullPointerException at Lazy.kt:100`

Тест находится в `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/streams/AddStreamSourceUseCaseTest.kt`; сам тест и `AddStreamSourceUseCase.kt` не изменены в текущем dirty tree. Поиск каталога по симптомам не нашел открытого тикета. Это блокирует доказательство промежуточной миграции Hilt/Room с kapt на KSP в S1338.

**Захвачено во время:** S1338

---

## 1. Проблема / симптом

<Что наблюдается, где (flavor/устройство/экран), эвиденс - лог-строки, stack trace, repro. Без имён классов на этапе захвата.>

---

## 2. Корневая причина

<Заполняется при расследовании. В скелете - «<расследовать>».>

---

## 3. Исправление

<Минимальный фикс - заполняется при реализации. В скелете - «<реализовать>».>

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1338

---

## 4. Проверка

<Как доказать фикс: unit-тест / команда / on-device сценарий. В скелете - «<определить>».>
