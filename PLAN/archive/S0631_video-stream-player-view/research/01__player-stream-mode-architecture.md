# Research 01 - Stream mode in fullscreen player: new player vs mode-in-existing

**Spec:** S0631
**§6 item:** 1 (новый плеер vs спец-режим)
**Status:** Resolved
**Date:** 2026-06-22

## Question

Что проще/правильнее архитектурно: построить отдельный плеер для трансляций или
добавить специальный режим (профиль) стрима в существующий плеер, который скрывает
неприменимые контролы?

## Verdict

**Режим (профиль) стрима внутри существующего плеера.** Отдельный Activity-плеер не
оправдан: он продублировал бы весь рантайм воспроизведения и хост-инфраструктуру без
выигрыша в переиспользовании.

## Why mode-in-existing wins

1. Точка детекции уже есть и тривиальна: `state.resource?.id == SyntheticResourceIds.STREAM`
   (`domain/model/Models.kt:248-254`, константа `STREAM = -200L`). Стрим в плеер запускается
   именно с этим синтетическим resourceId (подтверждено в логе: `S0591: stream launch resourceId=-200`).
   Альтернатива по пути URL (`isStreamUrl()` в `PlayerPlaybackCallbackImpl.kt:89-92`) менее надёжна.
2. Тяжёлая часть (ExoPlayer setup, буферизация, ICY, ошибки/ретраи) уже сделана в
   `StreamPlaybackHelper.kt`. Новый Activity повторял бы это.
3. Командная панель спроектирована под пер-состояние фильтрацию. Единая точка сборки
   видимых команд для portrait/big-buttons - `CommandPanelLayoutPlanner.buildActiveCommands()`
   (`ui/player/helpers/CommandPanelLayoutPlanner.kt:212-311`). Фильтрация под стрим-набор =
   правка одного метода.
4. Landscape - отдельная ветка `CommandPanelAvailabilityUpdater.applyLandscapeLayout()`
   (`ui/player/CommandPanelAvailabilityUpdater.kt:215-303`), уже ветвится по
   isAudio/isVideo/isPdf/isText/isEpub/isOffice; стрим - ещё одно условие.
5. Отдельный Activity продублировал бы: lifecycle, PiP, rotation, cast-listener, gesture,
   system bars, orientation, инициализацию менеджеров (`PlayerManagerInitializer`). Без выгоды.

## Cleanest detection point

Вычисляемое свойство `isLiveStream` на `PlayerViewModel.PlayerState`
(= `resource?.id == SyntheticResourceIds.STREAM`), проставляется на стрим-fast-path в
`PlayerMediaFilesLoader` (`ui/player/helpers/PlayerMediaFilesLoader.kt:185-296`). Все менеджеры
уже имеют доступ к `state`.

## Owner-approved control set mapped to owners

| Контрол (запрос владельца) | Где живёт | Применимость к live-стриму |
|---|---|---|
| Fullscreen | command panel `FULLSCREEN` | да |
| Snapshot / снятие кадра | `SaveVideoFrameManager` (`SAVE_FRAME`) | да, `TextureView.getBitmap()` берёт живой кадр |
| Rotation / поворот | `ScreenRotationManager` (`ROTATION_TOGGLE`) | да |
| File info | `PlayerDialogHelper.showFileInfo` -> `FileInfoDialog` (`INFO`) | да, но поля null (см. риск) |
| PiP | `PictureInPictureManager`, кнопка в `custom_player_controls*.xml` overlay (не в верхней панели), gated API 31+ | да |
| Video control dialog / управление видео | `PlayerDialogHelper.showPlaybackControlDialog` | да |
| Chrome Cast | `CastMediaManager` (`CAST`) | КНОПКА да, но движок сломан для live URL (см. риск/draft) |
| Send to / отправить ссылку | `PlayerCommandPanelCallbackImpl.buildShareableContent` -> `SendToMenuManager` (`SEND_TO`) | требует ветки share-URL |

Всё прочее (Delete/Rename/Undo/Edit/Copy/Move/Slideshow/Favorite/Random/SleepTimer и пр.) -
скрыть в стрим-профиле.

## Share-link branch point

`PlayerCommandPanelCallbackImpl.buildShareableContent()` (`ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt:72-84`).
Для стрима вернуть `ShareableContent(uris=emptyList(), text=streamUrl, mime="text/plain")`,
чтобы `SystemShareTargetHandler` отправил `ACTION_SEND` с `EXTRA_TEXT=streamUrl` - обычный
шеринг ссылки, который принимает любое приложение. Сейчас путь пытается материализовать
(скачать) локальную копию - для стрима неверно.

## Classes a stream-mode would touch (for /spec-tech)

- `PlayerViewModel.PlayerState` - добавить `isLiveStream`.
- `PlayerMediaFilesLoader` - проставить флаг на стрим-fast-path.
- `CommandPanelLayoutPlanner.buildActiveCommands()` - принять `isLiveStream`, отдать стрим-набор.
- `CommandPanelAvailabilityUpdater` (landscape + portrait/big call-sites) - стрим-гард.
- `PlayerCommandPanelCallbackImpl.buildShareableContent()` - ветка share-URL.

## Risks

- `PlayerActivity.kt` (1201 LOC) и `PlayerMediaLoaderManager.kt` (1132 LOC) близки к лимиту 1500 -
  правки вести в существующих helper'ах, не в Activity.
- Landscape-counterpart `res/layout-land/activity_player_unified.xml` держать в синхроне.
- `CommandPanelAvailabilityUpdater` без юнит-тестов - стрим-фильтр покрыть тестом планнера.
- FileInfo для стрима: `duration/width/height/codec=null`, `size=0` (`PlayerMediaFilesLoader.kt:278-287`) -
  диалог должен корректно показывать пустые/неизвестные поля.

## Out-of-scope findings (parking candidates)

1. **Cast сломан для live-стрима.** `CastMediaManager.resolveAndSend()` классифицирует `http://`-путь
   как локальный файл (`File(path).exists()` = false) -> тихий error-toast. Кнопка Cast для стрима
   показывается, но не кастит. Нужен прямой `MediaItem.fromUri(url)` на ресивер без
   `LocalCastProxyServer`. Префикс к полезности Cast-кнопки в стрим-профиле.
2. **Имя файла снапшота для стрима** выводится из `streamUrl.substringAfterLast('/')` -> уродливое/пустое
   (`SaveVideoFrameManager`, `PlayerMediaFilesLoader.kt:279`). Косметика.
3. **Robustness: BehindLiveWindowException** (отдельный workflow) - live-HLS падает в диалог
   "удалить" вместо восстановления; не относится к UI-профилю S0631.

## Appendix A - flavor / cast gating (cross-check)

| Flag | standard | noLegal | lite | photos | legacy | vr |
|---|---|---|---|---|---|---|
| `SUPPORT_STREAMS` | true | true | false | false | true | true |
| `SUPPORT_CAST` | true | true | true | true | true | false |

- `SUPPORT_STREAMS` гейтит UI-вход в трансляции, не сам pipeline воспроизведения.
- Runtime-флаг `MediaCapabilities.supportsCast` проверяют `CastMediaManager.init()` и
  `buildActiveCommands()` (~line 249). На vr Cast отсутствует - стрим-профиль не должен опираться
  на наличие Cast-кнопки.

## Appendix B - test coverage in the touched area

- Есть тест: `CommandPanelLayoutPlannerTest.kt` (правка `buildActiveCommands()` -> обновить тест),
  `PlayerPlaybackCallbackImplTest.kt`, `PlayerMediaFilesLoaderReconcileTest.kt`.
- Нет тестов: `CommandPanelController`, `CommandPanelAvailabilityUpdater`,
  `buildShareableContent()`, `CastMediaManager`, `SaveVideoFrameManager`, `PictureInPictureManager`,
  `StreamPlaybackHelper`.
