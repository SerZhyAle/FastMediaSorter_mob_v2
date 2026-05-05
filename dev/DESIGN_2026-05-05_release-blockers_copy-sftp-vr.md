# Дизайн 2026-05-05 — Standard release blockers: copy, SFTP, VR split

## 1. Целевой порядок исполнения

1. `S0091` — точечный bugfix, минимальный риск, прямой release-blocker.
2. `S0092` — точечный bugfix shared SFTP read substrate, влияет сразу на playback и thumbnails.
3. `S0093` — отдельный архитектурный поток, не должен тормозить выпуск Standard.

## 2. Решение для S0091

### Проблема

`FileOperationProgressDialog` показывается с задержкой (`SHOW_DELAY_MS = 2000ms`), но получает `Starting` progress раньше и пишет в `lateinit`-View до `onCreate()`.

### Решение

- Защитить доступ к summary-View (`tvOverallPercent`, `tvEta`) до фактической инициализации layout.
- Не менять `FileOperationDestinationDialog`, если локальный bugfix в диалоге полностью устраняет крэш.
- Использовать минимальное изменение с узкой валидацией через компиляцию touched slice.

### Риск

- Низкий. Изменение локализовано в одном диалоге без изменения transfer/business logic.

## 3. Решение для S0092

### Проблема

`SftpClient.readFileBytesRange()` в retry-path отходит от safe primary-path: вместо `channel.get(remotePath, null, offset)` выполняет `channel.get(remotePath)` + `skip(offset)`. Логи показывают ошибки именно в ветке retry/skip.

### Решение

- Сохранить одинаковую offset-semantics на primary и retry path.
- Убрать `skip(offset)` из retry branch и перейти на прямой `channel.get(remotePath, null, offset)`.
- Не расширять объём до полной переработки пула/thumbnail scheduling, если локальный fix закрывает observed failure.

### Риск

- Низкий/средний. Изменение локальное, но затрагивает shared SFTP read path и потребует compile validation.

## 4. Решение для S0093

### Проблема

VR runtime одновременно использует inherited playback stack и отдельный `VrPlaybackEngine`, а команда `SeekTo` уже уходит в один backend, тогда как speed/audio идут в другой.

### Решение

- Зафиксировать архитектурную цель: один playback authority для VR.
- Отдельно описать migration path: route/state/render chain, command router, track selection, stereo-state source of truth.
- Не реализовывать в этом заходе, пока не закрыты core blockers `S0091/S0092`.

## 5. Валидация

- `S0091`: compile touched Kotlin slice, затем проверить отсутствие новых диагностик в диалоге.
- `S0092`: compile touched Kotlin slice, затем проверить отсутствие новых диагностик в SFTP-коде.
- После каждого `.kt`-изменения: `dev/CHANGELOG.md` через скрипт, затем `dev/CATALOG/scripts/scan.ps1 -Module app_v2` и `render.ps1`.