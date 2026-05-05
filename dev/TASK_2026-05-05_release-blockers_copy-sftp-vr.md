# Задача 2026-05-05 — релизные блокеры Standard: copy dialog, SFTP playback, VR rewrite stream

## Контекст

- Источник фактов: `logs/new/fastmediasorter_20260505_031838.log`, `logs/new/fastmediasorter_20260505_033221.log`, Quest-скриншоты из `logs/new/`.
- Подтверждённые пользователем приоритеты:
  1. Основной релизный приоритет — Standard/core functionality.
  2. Copy/move падения — блокер.
  3. SFTP playback/read instability — трактуется как блокер для core network playback.
  4. VR-функциональность системно сломана и должна идти отдельным потоком, не блокируя выпуск Standard.

## Подтверждённые дефекты

### 1. Copy/move progress startup race

- `FileOperationDestinationDialog.performOperation()` начинает получать progress-события до того, как delayed `FileOperationProgressDialog` проходит `super.show()/onCreate`.
- `FileOperationProgressDialog.updateProgress(FileOperationProgress.Starting)` трогает `tvOverallPercent` и `tvEta` до инициализации View.
- В логах это воспроизводится как `UninitializedPropertyAccessException: lateinit property tvOverallPercent has not been initialized`.

### 2. SFTP range-read/playback instability

- Один и тот же сбойный substrate бьёт по Exo playback и по thumbnail extraction.
- В логах фиксируются `SftpDataSource: Error opening SFTP file`, `SFTP range read failed`, `Error reading from network`, playback errors `3003` и `2000`.
- В `SftpClient.readFileBytesRange()` retry-path отличается от primary-path: вместо прямого offset-open используется `skip(offset)`, что совпадает с observed failure profile.

### 3. VR playback authority split

- `VrRouteDecisionHelper` корректно выбирает `IMMERSIVE_VIDEO`, но runtime-state остаётся рассинхронизированным: `stereo coherence MISMATCH coordinator=MONO ...`.
- `VrPlayerActivity` одновременно наследует `PlayerActivity/videoPlayerManager` и инжектит `VrPlaybackEngine`.
- `VrPlayerCommandRouter` делит команды между `videoPlayerManager` и `vrPlaybackEngine`, что создаёт неединственный источник истины по playback state.

## Рабочее решение на этот заход

1. Завести и исполнить `S0091` — bugfix startup race progress dialog.
2. Завести и исполнить `S0092` — bugfix SFTP range-read retry overflow / offset regression.
3. Завести `S0093` как отдельную стратегическую спеку на VR single playback authority и stereo-state unification.

## Что не входит в этот заход

- Полная реализация `S0093`.
- Большой UI/UX redesign copy/move flow.
- Переписывание всех network retry-политик по SMB/FTP.