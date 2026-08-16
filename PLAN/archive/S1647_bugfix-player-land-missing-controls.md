# Спецификация (compact bugfix): S1647 - Альбомная разметка плеера не объявляет два контрола

**Ticket:** S1647
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-14
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-14

**Захвачено во время:** S1549 (измерение дельт разметок, Stage F2)

**Текст:**

Landscape player layout is missing two controls that portrait declares. app_v2/src/main/res/layout-land/activity_player_unified.xml does not declare btnTouchZonesHelp or tvVideoGestureIndicator, both of which app_v2/src/main/res/layout/activity_player_unified.xml does declare. ViewBinding therefore generates both fields as nullable, and every consumer already guards with a safe call: PlayerControlsSetupManager.kt:513, PlayerUiStateCoordinatorCallbackImpl.kt:149, PlayerVrLaunchManager.kt:342 and :368, VideoTouchDelegate.kt:40 and :223-224. Nothing crashes, which is why this survived. The user-visible effect is that the touch-zone help button and the video gesture indicator are absent whenever the landscape variant is inflated. Found 2026-08-14 while measuring layout deltas for S1549. Evidence that this is an oversight rather than a decision: no comment anywhere in ui/player/** states that landscape drops these two controls, while this codebase does comment deliberate cross-orientation differences inline (S0940, S0374, S1068 each carry a justification). The landscape file was rewritten as a ConstraintLayout with a redesigned command bar and the two views appear to have been lost in that rewrite. Deciding where the two controls belong in the landscape command bar is a UI placement decision and needs the owner. Out of scope for S1549 by that ticket's own non-goals, which exclude revising the content of landscape layouts - S1549 only makes the existing landscape layout apply on rotation. Note for whoever picks this up: the same landscape file also carries a dead always-GONE controlsOverlay twin whose buttons reference apparently stale string resources (player_volume_down_symbol, player_play_symbol) that differ from the portrait copy's keys - worth resolving in the same pass since both sit in the same file.

---

## 1. Проблема / симптом

Кнопка подсказки зон касания и индикатор жеста видео отсутствуют всегда, когда инфлейтится альбомный вариант разметки плеера. Падения нет: ViewBinding делает поля nullable, а все потребители уже пишут безопасный вызов - именно поэтому дефект дожил до сих пор незамеченным.

Сегодня альбомный вариант применяется только при холодном старте уже в альбомной ориентации, так что дефект виден редко. После S1549 альбомная разметка начнёт применяться при каждом повороте, и отсутствие двух контролов станет заметным постоянно.

---

## 2. Корневая причина

Измерено 2026-08-14.

- Оба контрола объявлены ровно один раз в книжном файле и ноль раз в альбомном.
- Вариантов у этой разметки всего два - `res/layout/` и `res/layout-land/`; ни `sw`-, ни `w600dp`-копии нет, поэтому правка затрагивает один файл, а не семейство.
- В альбомном файле нет ни одного комментария, объясняющего отсутствие этих двух контролов, тогда как осознанные различия между ориентациями в этом коде комментируются на месте. Гипотеза захвата подтверждается: это потеря при переписывании, а не решение.

Отдельно проверена вторая половина заметки из захвата, и она оказалась неточной. Мёртвый близнец `controlsOverlay` в альбомном файле действительно есть - строка 263, `visibility="gone"`, - но его строковые ключи `player_volume_down_symbol` и `player_play_symbol` не устаревшие: оба объявлены в `values/strings_video_player.xml` и присутствуют в трёх локалях. Отличие от книжной копии - это другой набор ключей, а не ссылки в пустоту. Сам близнец «мёртвым» тоже не оказался - решение по нему принято 2026-08-15 и записано в §3: блок остаётся.

---

## 3. Исправление

**Решение владельца о размещении (2026-08-15, `/spec-quiz`): как в портрете.** Оба контрола объявляются в альбомном файле оверлеями поверх медиа, а не элементами командной панели, ровно как в книжном варианте: кнопка подсказки зон касания прижата к верхнему правому углу, индикатор жеста центрирован. Командная панель альбомной разметки не меняется.

Вопрос о размещении в командной панели снят - в портрете эти контролы никогда в ней и не были. Формулировка захвата про «место в переработанной командной панели» опиралась на неверную предпосылку.

**Второе решение владельца (2026-08-15) в первой формулировке звучало «мёртвый близнец `controlsOverlay` удаляется в этом же проходе». Его предпосылка не подтвердилась измерением, и владелец пересмотрел решение (`/spec-quiz`, 2026-08-15): блок остаётся, тема закрыта.** Отдельного тикета под него не заводится, пункт 3 состава правки снимается совсем.

Измерено (агент, 2026-08-15):

- Блок объявляет девять идентификаторов: `controlsOverlay`, `toolbar`, `playbackButtonRow`, `btnVolumeDown`, `btnPrevious`, `btnPlayPause`, `btnNext`, `btnVolumeUp`, `btnSlideShow`, `btnDelete`.
- Книжный файл объявляет каждый из них ровно один раз, то есть это настоящие представления, а не заглушки.
- Kotlin разыменовывает их **без безопасного вызова**: `controlsOverlay` в 6 местах, `btnSlideShow` в 7, `btnDelete` в 5, `btnPlayPause` в 2.
- Блок не «всегда gone»: `PlayerUiStateCoordinator.kt:298` включает его по условию `!showCommandPanel && showControls && !useTouchZones`, без учёта ориентации, а в альбомном `ConstraintLayout` он объявлен `match_parent` вообще без констрейнтов, то есть при показе разворачивается на весь экран. В альбоме это работающий полноэкранный оверлей, а не заглушка биндинга.
- Собственный комментарий файла говорит только про вторую половину причины: строка 268 «MISSING VIEWS REQUIRED FOR BINDING COMPATIBILITY», строка 272 «hidden but required for binding types».

Следствие: удаление блока делает эти поля обнуляемыми в общем биндинге и ломает каждое перечисленное место, а заодно убирает работающий UI - это не уборка мёртвого веса. Тот же приём и та же причина уже задокументированы в этом файле для `miniNowPlayingBar`.

Расхождение альбомной копии с книжной (свои строковые ключи `player_play_symbol` и соседи, захардкоженные `24sp`/`32sp`/`16dp`, два цвета `#80000000`) остаётся как есть - владелец выбрал не расширять правку.

Состав правки:

1. Добавить в альбомный файл кнопку подсказки зон касания с тем же идентификатором, что в книжном, и той же привязкой к верхнему правому краю.
2. Добавить туда же центрированный индикатор жеста видео с тем же идентификатором.
3. ~~Удалить блок `controlsOverlay` и всё его содержимое из альбомного файла.~~ Снято решением владельца от 2026-08-15 - см. измерение выше.
4. ~~Проверить, что строковые ключи, которые после удаления перестают использоваться, либо остаются нужны книжному варианту, либо удаляются вместе с блоком по правилу 20.~~ Не применимо: блок остаётся.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1549 (при измерении его дельт дефект и найден; S1549 делает альбомную разметку применяемой и тем самым делает этот дефект постоянно видимым), S0393 (`StandalonePlayerActivity` помечен устаревшим, но делит тот же файл разметки)
- **Размещение:** решение владельца записано выше, в §3.

### Решения по опросу владельца (2026-08-15)

- Размещение двух контролов → как в портрете: оверлеи поверх медиа, командная панель не трогается.
- ~~Мёртвый `controlsOverlay` → удалить в этом же проходе.~~ Пересмотрено тем же владельцем ниже, после измерения.

### Quiz decisions (2026-08-15)

- Блок `controlsOverlay` в альбомной разметке → оставить как есть, тему закрыть (блок живой: показывается в полноэкранном режиме и в альбоме, а удаление ломает 20 мест разыменования без безопасного вызова; отдельный тикет на перевод потребителей не заводится).

---

## 4. Проверка

1. Компиляция стандартного флейвора проходит, и ViewBinding перестаёт объявлять оба поля обнуляемыми для альбомного варианта.
2. На устройстве: открыть видео в плеере, повернуть в альбом. Индикатор жеста появляется по центру при жесте яркости, громкости и перемотки.
3. Там же: в полноэкранном режиме зон касания на изображении или GIF кнопка подсказки видна в верхнем правом углу и открывает подсказку.
4. Книжная ориентация не изменилась: оба контрола на прежних местах.
5. ~~Удаление мёртвого блока не оставило ссылок в никуда.~~ Не применимо: блок `controlsOverlay` остаётся по решению владельца от 2026-08-15.

---

## Last Audit

**2026-08-15, агент (`/spec-do`).** Тикет достался в статусе `In Progress` с правкой, уже лежащей в дереве и не отражённой ни в одной записи дневника изменений. Аудит устанавливает, что именно сделано.

Состав правки выполнен полностью: пункты 1 и 2 в дереве, пункты 3 и 4 сняты решением владельца.

- `res/layout-land/activity_player_unified.xml:222` объявляет `tvVideoGestureIndicator` с `layout_gravity="center"` внутри `FrameLayout` области медиа - как в книжном варианте.
- Там же строка 231 объявляет `btnTouchZonesHelp` с `layout_gravity="top|end"`, последним по порядку ради z-order - как в книжном варианте.
- Оба объявления несут комментарий с идентификатором тикета, объясняющий, что контрол был потерян при переписывании файла в `ConstraintLayout`.
- Блок `controlsOverlay` на месте, строка 263 - согласно решению владельца.

Пункт 1 раздела «Проверка» пройден, измерено в этом аудите:

- `.\a.ps1 fc` (`:app_v2:compileStandardDebugKotlin` + `:app_v2:processStandardDebugResources`) - `BUILD SUCCESSFUL`.
- Сгенерированный `ActivityPlayerUnifiedBinding.java` объявляет `btnTouchZonesHelp` (строка 196) и `tvVideoGestureIndicator` (строка 295) как `@NonNull`. Обнуляемость, которая и была механизмом дефекта, ушла.

Осознанно не сделано: потребители обоих полей продолжают писать безопасный вызов - `PlayerUiStateCoordinatorCallbackImpl.kt:149`, `PlayerControlsSetupManager.kt:513`, `PlayerVrLaunchManager.kt:342` и `:368`, `VideoTouchDelegate.kt:40`, `:223`, `:224` (все семь ссылок перепроверены и совпадают с захватом). После правки эти `?.` избыточны, но поведения не меняют, а владелец в §3 выбрал не расширять правку. Отдельного тикета не завожу: правка механическая и привязана к этому же месту.

Остаток: пункты 2-4 раздела «Проверка» проверяются только на устройстве.
