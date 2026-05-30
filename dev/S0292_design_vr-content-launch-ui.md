# S0292 Design Note - VR content launch UI

## Цель

Сделать VR дочерним режимом текущего `PlayerActivity`, а не отдельным пользовательским плеером. Пользователь должен запускать VR из текущего player-контекста и возвращаться обратно в тот же файл с восстановлением базового flat-player состояния.

## Принятые допущения

- Стратегическая спека S0292 содержит достаточно конкретные owner-approved UI decisions, поэтому реализация не блокируется на дополнительные вопросы в чат.
- Device-test не входит в этот раунд; deliverable этого прохода - код, ресурсы, tactical closure, build validation.
- Реальная иммерсивная playback-поддержка в этом раунде ограничена изображениями. `VIDEO` и `GIF` получают тот же UI-entry, но на запуске возвращают typed `Unavailable(NotYetSupported)` без ухода в broken VR flow.

## Архитектурное решение

1. Единый transport-layer остаётся в `core/xr/`.
2. Единый стартовый orchestration-layer оформляется как `StartVrPlaybackUseCase`:
   - re-check capability,
   - normalise launch request,
   - short-circuit unsupported media,
   - build immersive intent through `XrEntryGateway`,
   - dispatch XR host.
3. XR host (`DiagnosticXrActivity`) перестаёт быть только settings-diagnostic экраном:
   - принимает `VrLaunchInput`,
   - умеет открыть либо diagnostic playlist, либо один локально-резолвленный image file,
   - возвращает typed `VrLaunchResult` в panel world.
4. Player-specific orchestration выносится из `PlayerActivity` в helper-manager:
   - render floating badge / inline prompt,
   - prepare local file for immersive launch when current file is network/content-backed,
   - build return payload,
   - consume return result and restore minimal panel state.

## UI решение

- Primary entry: floating badge в `mediaContentArea`, top-end overlay cluster.
- Fallback entry: overflow item `Open in VR`.
- Silent absence: badge и overflow item существуют только для `IMAGE|VIDEO|GIF` при XR-enabled state.
- Discoverability when XR-capable but toggle-off: one-time inline prompt with actions `Open settings` and `Dismiss`.
- Pure-media mode: badge/prompt скрываются вместе с overlay-chrome.

## Return model

- На launch формируется `PlayerStateSnapshot` + player return payload.
- После immersive exit XR host поднимает panel return intent через тот же Home+PendingIntent handoff pattern, который уже используется для Settings round-trip на Quest.
- `PlayerActivity` при повторном открытии:
  - переоткрывает тот же файл,
  - восстанавливает command-panel/fullscreen preference,
  - показывает snackbar только для `Crashed` / `Unavailable`.

## Validation target for this round

- `assembleStandardDebug`
- `assembleNoLegalDebug`
- catalog sync for `app_v2`
- targeted strings/localisation check for new player VR keys

