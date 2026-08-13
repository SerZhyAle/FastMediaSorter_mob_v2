# S0544 - Error/detail dialog action row clipped in landscape

**Status:** Archived
**Priority:** 45
**Date:** 2026-06-19
**Tier:** TBD
**Origin:** parked during S0483 device-test sweep (2026-06-19) - out-of-scope finding (CLAUDE.md §3.1)

> **Scope:** Implemented (landscape layout fix). Раздел §0 - исходный захват; решение в разделе «Решение».

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-19 (во время device-теста S0483 на emulator-5556, standard debug v2.60.6191.257).

**Симптом:**

- В альбомной ориентации у диалога ошибок/деталей (`dialog_error_detail.xml`, строится `ScrollableTextDialog`) нижний ряд действий (`layoutDialogActions` - Share/Save/Report/Copy/Close) уезжает за нижнюю кромку экрана при высоте окна ~1080px и становится недоступен. Затронуты ВСЕ кнопки ряда, не только новая Report-кнопка S0483.

**Доказательство/первопричина (предварительно):**

- Корень `layout-land/dialog_error_detail.xml` - вертикальный `LinearLayout` (`wrap_content`), НЕ обёрнут в единый внешний `ScrollView`.
- Дети: message-`ScrollView` (`android:layout_height="@dimen/dialog_landscape_list_max_height"`) + опциональный details-`ScrollView` (та же фикс-высота, по умолчанию gone) + `layoutDialogActions` (`wrap_content`) внизу.
- Сумма (заголовок диалога + message-scroll фикс-высоты + paddings + ряд действий) может превышать доступную высоту диалога в альбоме -> ряд действий выталкивается за фолд; диалог не скроллится целиком.
- Комментарий S0378 в файле показывает прежнюю попытку «уместить в высоту экрана» через `dialog_landscape_list_max_height` - но клиппинг ряда действий остаётся.

**Объём (затронуто):**

- `app_v2/src/main/res/layout-land/dialog_error_detail.xml` (портретный counterpart `layout/dialog_error_detail.xml` проверить тоже).
- Любой вызов `ScrollableTextDialog` (ошибки Browse, лог-вьювер и т.п.) в альбоме на коротких по высоте экранах.

**Нужно подтвердить на реальном устройстве:** клиппинг воспроизводится не только на эмуляторе (высота landscape реального 1080p-телефона тоже ~1080px, так что вероятно да).

**Возможное направление (для research, не решение):** обернуть весь контент диалога в единый внешний скролл с закреплённым нижним рядом действий, либо отдать вид материальному билдеру/`MaterialAlertDialog` с pinned-buttons, либо ограничить суммарную высоту скролл-секций так, чтобы ряд действий всегда помещался.

## Решение

**Первопричина (подтверждена):**

- Ряд действий (`layoutDialogActions`) лежит внутри `customPanel` `AlertDialog` - это НЕ скроллируемая зона.
- В альбоме сумма (заголовок диалога + message-`ScrollView` фикс-высоты 240dp + paddings + ряд действий) превышает высоту короткого landscape-окна, поэтому весь ряд выталкивается за нижнюю кромку и недоступен.

**Выбранный подход (зеркалит `dialog_resource_picker`):**

- В `layout-land/dialog_error_detail.xml` ряд действий закреплён ПЕРВЫМ ребёнком (наверху) - всегда виден сразу под заголовком, не зависит от высоты контента.
- Скроллы контента (message + опциональные details) остаются ниже с прежней фикс-высотой `dialog_landscape_list_max_height`.
- При переполнении окна теперь клипается только хвост скроллируемого контента (его можно доскроллить), а кнопки никогда.
- Это та же конвенция, что уже принята в проекте: `dialog_resource_picker` (land) с комментарием «buttons moved to TOP so always visible in landscape».

**Объём правки:**

- Только `app_v2/src/main/res/layout-land/dialog_error_detail.xml` (перенос ряда наверх, `layout_marginTop` -> `layout_marginBottom`).
- Портретный `layout/dialog_error_detail.xml` НЕ тронут: на высоком экране клиппинга нет, и портретный counterpart `dialog_resource_picker` так же держит кнопки внизу - расхождение ориентаций намеренное.
- Kotlin (`ScrollableTextDialog`) без изменений логики: id и типы вью прежние; добавлен один debug-tag на время device-теста.

**Валидация:** `.\a.ps1 fc` (compileStandardDebugKotlin + processStandardDebugResources) - BUILD SUCCESSFUL.

**Нужно проверить на устройстве:** открыть диалог ошибок/деталей в альбомной ориентации (например, лог-вьювер или ошибка Browse) и убедиться, что весь ряд действий (Share/Save/Report/Copy/Close) виден и тапается; контент скроллится.

## Связь

- S0483 (crash-report email button - добавил `btnReport` в этот ряд; клиппинг к нему не относится, предсуществующий).
- S0378 (landscape dialog sizing - прежняя правка высоты скроллов).
- S0538 (унификация диалоговых кнопок - возможный общий шов для нижнего ряда).
