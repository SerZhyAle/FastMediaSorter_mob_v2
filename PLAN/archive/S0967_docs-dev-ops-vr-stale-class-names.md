# Стратегическая спецификация: S0967 - Устаревшие имена классов и сценарий в VR-разделе DEV_OPS.md

**Ticket:** S0967
**Status:** Archived
**Priority:** 35
**Date:** 2026-07-06
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-06 (найдено при выполнении S0965)
**Tactical spec:** `PLAN/S0967_docs-dev-ops-vr-stale-class-names/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-06

**Текст:**

Docs drift found while executing S0965 (VR docs reconciliation), out of that ticket's scope (S0965 only touches VR_EDITION.md/HOW_TO.md/VR_CONTROLS.md/howto/index.md).

Problem: docs/DEV_OPS.md (developer/maintainer doc, ADB debugging section around lines 330-425) repeatedly names a class `VrPlayerActivity` as the real immersive host Activity, with concrete debugging instructions built around it (task-affinity behavior, adb logcat filter tags including `VrPlayerActivity`, expected healthy-state description). Verified during S0965 that `VrPlayerActivity` does NOT exist anywhere in app_v2/src (grepped the whole main source tree) - the real immersive host class is `DiagnosticXrActivity` (in app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/). The DEV_OPS.md section also references other unverified logcat tags (VrRuntimeClient, OpenXrSessionManager, VrTaskTransition) that may or may not match real class/tag names in the current code - would need the same verification pass as S0965 did for the user-facing docs.

Why this is out of scope for S0965: S0965 was scoped to exactly 4 user-facing doc files; DEV_OPS.md is a separate maintainer/debugging reference requiring its own verification pass against the real ADB/task-affinity behavior of DiagnosticXrActivity (different content shape - debugging runbook, not feature description - so it needs its own read-the-code-and-rewrite pass, not a quick find-replace of the class name, since the whole task-affinity narrative may itself be stale).

Please scaffold a Draft spec capturing this verbatim so it can be picked up later.

---

## 1. Проблема

docs/DEV_OPS.md, раздел ADB-отладки VR (строки ~330-425), называл несуществующий класс `VrPlayerActivity` реальным immersive-хостом и строил вокруг него конкретные инструкции (task affinity, logcat-теги, ожидаемое здоровое состояние). Реальный immersive-хост - `DiagnosticXrActivity` (`app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/`). Требовалась отдельная верификация всего сценария task-affinity/logcat-тегов под текущий код, а не только замена имени класса.

---

## 2. Цели

1. DEV_OPS.md VR-раздел описывает реальный immersive-хост `DiagnosticXrActivity` (launchMode `singleTask`, category `com.oculus.intent.category.VR`, без `taskAffinity`-override) и объясняет отсутствие task-affinity-сплита исторической справкой (helper `VrTaskTransition` удалён в S0251, старый хост заменён на standalone в S0282).
2. Приведённые logcat-теги реально существуют в коде: `S0249.XrSession` / `S0249.JniBridge` - наши native-теги; `OpenXR_SessionImpl` / `VrRuntimeClient` - от рантайма Meta/HorizonOS; `DiagnosticXrActivity` / `DiagnosticXrRenderThread` - реальные классы `src/vr`.

**Non-goals:**

- Изменение реального поведения task-affinity/immersive-хоста - только выверка документации под текущий код.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

<Нумерованный список желаемого, но необязательного к первой итерации.>

### 3.2 Жёсткие ограничения

- **Flavor:** vr / noLegal
- **API level:** без API-специфики
- **Wear OS:** не затрагивается
- **Производительность:** н/д (доки)
- **Совместимость данных:** н/д
- **Локализация:** DEV_OPS.md - только EN (внутренний maintainer-документ, без _RU/_UK зеркал - проверить)
- **Доступность:** н/д

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0965 (docs-vr-drift-reconcile-quickpath - источник находки), S0773 (vr-cinema-program-separate-player)

---

## 4. Контекст текущей архитектуры

<Заполнить при разработке: реальный ADB debugging flow для DiagnosticXrActivity - task affinity, logcat-теги, здоровое/нездоровое состояние - нужно снять с реального устройства/кода, а не унаследовать из старого текста про VrPlayerActivity.>

---

## 5. Предлагаемый подход

<Архитектурный уровень - заполняется позже.>

### 5.1 Основные столпы / модули

<TBD>

### 5.2 Потоки данных и событий

<TBD>

### 5.3 Точки расширяемости

<TBD>

---

## 6. Открытые вопросы / Research items

1. Какие logcat-теги реально существуют в коде для DiagnosticXrActivity/её зависимостей (замена VrRuntimeClient/OpenXrSessionManager/VrTaskTransition, если они устарели)?
2. Актуален ли сценарий task-affinity (`android:taskAffinity="${applicationId}.vr"`) в текущем манифесте `src/vr/AndroidManifest.xml`, или он тоже унаследован от старой архитектуры?

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Разработчик следует устаревшей инструкции DEV_OPS.md, ищет несуществующий класс/тег при реальной отладке VR | Средняя | Потеря времени на отладку, ложные выводы | Выверить раздел под текущий код |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES - это внутренний maintainer-документ.

---

## 9. Архитектурные решения (ADR)

ADR нет - решение по устоявшимся паттернам проекта.

---

## 10. Связи с другими спеками

- S0965 (docs-vr-drift-reconcile-quickpath) - источник находки.
- S0773 (vr-cinema-program-separate-player) - целевая архитектура VR-кинотеатра.

---

## 11. Критерии готовности (strategic-level)

1. docs/DEV_OPS.md больше не ссылается на несуществующий `VrPlayerActivity`; описывает реальный immersive-хост (`DiagnosticXrActivity`) и проверенные logcat-теги/task-affinity поведение.

---

## Last Audit

**Date:** 2026-07-07
**Mode:** strategic (docs-only)
**Outcome:** Verified
**Counts:** PASS 4 · WARN 0 · FAIL 0

### Checks

- PASS - `grep VrPlayerActivity|VrOpenXrRenderManager|VrStereoRenderer docs/DEV_OPS.md` = 0 hits.
- PASS - раздел «The real immersive host: `DiagnosticXrActivity`» описывает launchMode `singleTask` + `com.oculus.intent.category.VR`, явно «there is no `android:taskAffinity` override» (research §6.2 закрыт).
- PASS - logcat-теги строки 386 реальны: `grep` в `app_v2/src/vr` нашёл `DiagnosticXrActivity`, `DiagnosticXrRenderThread`, `S0249.XrSession`, `S0249.JniBridge`, `OpenXR_SessionImpl`, `VrRuntimeClient` (research §6.1 закрыт).
- PASS - историческая справка объясняет удаление `VrTaskTransition` (S0251) и замену хоста (S0282) - причина, по которой старый текст описывал несуществующий affinity-сплит.

### Manual / on-device

- [ ] Ни один - внутренний maintainer-документ, проверяется статически.
