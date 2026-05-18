# S0125 - Перезапуск постановки задачи по пересмотру окна настроек

**Дата:** 2026-05-19
**Ветка:** DEBUG-v004
**Связанный тикет:** S0125

## Формулировка задачи

Текущая задача больше не трактуется как «временно показать рядом второе окно настроек». После пользовательского аудита это признано ложным направлением. Реальная задача S0125 теперь звучит так: сохранить текущее стабильное окно настроек без потери функций и параллельно спроектировать, а затем собрать новое современное окно настроек с другой информационной архитектурой, более сильной визуальной иерархией, предсказуемой адаптацией под разные размеры экрана и понятным контрактом роста для будущих настроек.

Цель новой волны не в копировании существующих строк или в переносе старых fragment/layout деревьев в новый host. Цель в том, чтобы сделать settings-поверхность, в которой пользователю легче понять, где находится нужная настройка, почему она находится именно там, и куда попадут новые настройки в будущем.

## UI Clarification Status

Status: READY

### Confirmed

- Legacy `SettingsActivity` остаётся текущим стабильным окном и не переписывается в этой corrective-волне.
- Любые user-facing entry points в revised host должны быть убраны, пока revised content всё ещё основан на legacy fragments или legacy XML includes.
- Новая settings-система не должна терять ни одну существующую настройку, её описание, helper-слой, dependent inline control, embedded action, dialog flow или management-entry route.
- Новая settings-система обязана сохранять parity для touch, mouse, keyboard shortcuts и D-pad / TV remote.
- Search остаётся обязательной частью навигации, но больше не считается компенсацией плохой структуры.
- Число top-level страниц настроек не растёт. Допускается переименование разделов и переразложение их содержимого.
- Portrait и landscape обязаны сохранять одинаковую logical composition; различаться может только внутренняя укладка контента.

### Approved decisions

- До готовности первого реально native revised slice legacy path остаётся единственным публичным settings path.
- Финальная revised surface сохраняет 4 top-level pages: General, Media, Playback, Operations.
- Внутри каждой top-level page вводится современная card/section-based композиция с более сильными headers и явным разделением типов сущностей: preference, action, route, danger, info.
- На узких экранах content строится как вертикальный стек section cards.
- На широких экранах активная page переходит на двухколоночную композицию: слева section navigator / overview, справа detail body текущей секции.
- Search overlay переходит в роль command-palette поверх revised host: результат всегда ведёт в каноническое место сущности.

### Delegated assumptions

- Визуальный язык redesign выбирает агент в рамках Material3 + project theme tokens, без попытки копировать текущее legacy visual grouping.
- Внутри page допускается замена слабых accordion-headers на более сильные section cards, summary blocks и compact action clusters, если preserved behavior сохраняется.
- Debug-only и deferred legacy-only zones не обязаны входить в первую public волну revised host; они могут оставаться на legacy path до отдельной follow-up работы.

## Явно запрещённые решения

- Нельзя снова публиковать revised host как `New settings`, если он всё ещё собран из legacy fragments или legacy includes.
- Нельзя считать задачу выполненной только потому, что новый Activity или новый tab host компилируется.
- Нельзя считать search достаточной заменой понятной IA.
- Нельзя добавлять второй storage layer, второй набор settings keys или revised-only persistence semantics.

## Ближайший ожидаемый результат

1. Убрать user-facing exposure ложного revised окна.
2. Зафиксировать дизайн-контракт redesign reboot.
3. Перейти к native redesign General как первой публично отличимой slice.