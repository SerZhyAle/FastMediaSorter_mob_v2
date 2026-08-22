# Стратегическая спецификация: S1804 - Целевая позиция аннотаций в Kotlin 2.2

**Ticket:** S1804
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-18
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - находка при работе над S1781, 2026-08-18

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-18

**Текст:**

Kotlin 2.2 warns on every @ApplicationContext / annotated constructor parameter: "This annotation is currently applied to the value parameter only, but in the future it will also be applied to field" (KT-73255). Surfaced while compiling the wear module for S1781 (NetworkSourcesViewModel.kt), but it is repo-wide - 139 @ApplicationContext occurrences across app_v2 and wear, plus every other annotation with an ambiguous default target. Needs a decision between adding -Xannotation-default-target=param-property to the compiler args once, or annotating each site with @param:. Out of scope for S1781; not covered by S1776 (that one is Media3/auth API deprecations).

**Захвачено во время:** S1781

---

## Problem

Захваченный текст ставит вопрос выбора подхода, но выбор сделан четыре месяца назад: `-Xannotation-default-target=param-property` добавлен в аргументы компилятора `app_v2` 2026-04-11, и запись в журнале разработки прямо называет причину - «Fixed KT-73255 compiler warnings regarding annotation targets». Модуль часов этот флаг не получил: его блок настроек компилятора задаёт только целевую версию JVM. Отсюда и предупреждение, всплывшее при сборке часов для S1781, - оно осталось ровно там, куда решение 2026-04-11 не дотянулось.

Замер рабочего дерева 2026-08-18: аннотация `@ApplicationContext` встречается 173 раза, из них в 12 файлах модуля часов; `freeCompilerArgs` объявлен в одном месте репозитория - `app_v2/build.gradle.kts:1450`.

## Approach

- `wear/build.gradle.kts` - добавить `freeCompilerArgs.add("-Xannotation-default-target=param-property")` в существующий блок настроек компилятора, рядом с целевой версией JVM, повторив решение `app_v2` дословно.

Почему флагом, а не разметкой каждой площадки через `@param:`: подход уже выбран и прожил в репозитории четыре месяца на большем из двух модулей, а 173 площадки правились бы вручную ради того же результата. Разойтись между модулями в способе решения одной задачи дороже, чем повторить строку.

## Done criteria

1. `wear/build.gradle.kts` содержит `-Xannotation-default-target=param-property` внутри блока `kotlin { compilerOptions { .. } }`.
2. Полная пересборка Kotlin модуля часов не печатает ни одного предупреждения «applied to the value parameter only» - изменение аргументов компилятора само инвалидирует задачу, поэтому проверка идёт по полной, а не инкрементальной сборке.
3. Сборка модуля часов проходит успешно.

---

## Отладочные теги

Не вставляются. Правило 2 связывает `Timber.d("Sxxxx: ..")` со статусом `BlockNeedUserTest`, а этот тикет туда не идёт: он меняет аргумент компилятора и не меняет ни одного потока выполнения, поэтому точки входа, которую можно было бы пометить, не существует, а проверка на устройстве ничего бы не показала. Терминал тикета - `Implemented`, и проверяется он механически: наличием строки и отсутствием предупреждений в полной сборке.

---

## Связи с другими спеками

- S1781 - в его сборке предупреждение всплыло; на его работу тикет не влияет.
- S1776 `deprecated-media3-and-auth-api-migrations` - другая тема (устаревшие API Media3 и авторизации), пересечения нет.
