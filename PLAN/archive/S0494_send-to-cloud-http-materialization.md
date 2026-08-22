# Draft: S0494 - «Отправить в..» для cloud:// и http(s) источников

**Status:** Archived
**Priority:** 55
**Date:** 2026-06-18
**Parent:** S0493 (send-to для удалённых файлов - покрывает smb/sftp/ftp)

## 0. Raw capture (symptom / evidence)

S0493 ввёл материализацию удалённого файла в локальную копию для меню «Отправить в..», но покрывает только smb/sftp/ftp (через `DownloadNetworkFileUseCase`). `MaterializeShareContentUseCase.isDownloadableScheme` пропускает только эти схемы; для `cloud://` и `http(s)://` возвращается `Result.failure` → пользователь видит тост «не удалось подготовить файл».

Из исследования (карта интеграции S0493):
- cloud:// : примитив есть, но публичный путь без прогресса - `CloudFileOperationHandler.downloadFromCloudToPublic(cloudPath, destPath, fileName)` (`data/cloud/CloudFileOperationHandler.kt:445`) вызывает приватный `downloadFromCloudTo(..)` с `progressCallback = null`. Файл уже 1031 LOC (выше soft-cap 1000).
- http(s):// : как `MediaFile.path` в проекте не встречается; есть `DirectFileExtractionStrategy.open(..)` (`data/link/DirectFileExtractionStrategy.kt:86`), но он завязан на link-sharing pipeline и отдаёт `InputStream`, не локальный `File`.

## 0.1 Уточнение состояния кода (2026-08-15)

Часть §0 устарела: `cloud://` уже подключён к `MaterializeShareContentUseCase` (ветка `isCloudScheme` -> `downloadFromCloudToPublic`). Проверено чтением кода, не историей git. Остаток объёма:

- `http(s)://` не поддержан: `isDownloadableScheme` его отвергает, ветки загрузки нет.
- Прогресс для cloud отсутствует: `downloadFromCloudToPublic` жёстко передаёт `progressCallback = null`, хотя приватный путь и клиентский слой прогресс уже умеют.
- `CloudFileOperationHandler` - 1080 LOC, выше собственного soft-cap 1000 (не выше жёсткого лимита 1500 из Rule 2), и работа по прогрессу его ещё увеличит.

## 1. Проблема

Для файлов с облачных источников (`cloud://`) меню «Отправить в..» появляется, но отправка падает с тостом, потому что нет интеграции облачной загрузки в материализацию шаринга. http(s) - потенциальный будущий источник.

## 2. Что нужно

- Подключить облачную загрузку в `MaterializeShareContentUseCase` (расширить `isDownloadableScheme` + ветку загрузки) с прогрессом.
- Решить, нужен ли публичный cloud-download-с-прогрессом use case (вынести из 1031-LOC `CloudFileOperationHandler`).
- Оценить, нужен ли http(s) как `MediaFile.path`-источник.

## 3. Открытые вопросы

- Resolved: извлекать ли публичный `CloudDownloadUseCase` из `CloudFileOperationHandler` (over-cap файл)? Да - следует из Rule 2 (лимит размера файла) и разделения слоёв, отдельного решения владельца не требует.
- Resolved: прогресс для cloud - пробросить `progressCallback` через приватный путь, механическая часть той же работы.
- Resolved: http(s) входит в объём этого тикета, отдельной итерации не будет (решение владельца 2026-08-15).

### 3.3 Owner inputs (Approval gate)

- **Scope http(s):** включено в этот тикет, не выносится в отдельную итерацию (владелец, 2026-08-15).
- **Verification account:** Google Drive `serzhyale@gmail.com`, ресурс «H» на Galaxy S21 (RFCR110NBQJ) - 100 файлов, доступ подтверждён 2026-08-15.
- **Related tickets:** S0493 (родитель, покрывает smb/sftp/ftp)
