# Integration Tests Failure Analysis (2026-03-01)

## Update 2 (повторный прогон после добавления FTP/SFTP, лог 13:08)

- Новый итог: `Passed 118 / Failed 8` (было `115 / 11`).
- Исправлено по факту прогона:
   - `Copy Local → SFTP` больше не падает.
   - `Copy Local → FTP` больше не падает.
   - `Matrix Copy: CLOUD → CLOUD` больше не падает.

Оставшиеся падения (8):
1. `Matrix Move: CLOUD → CLOUD` — `moveCloudToCloud: Failed to get file metadata`.
2-6. `GOOGLE_DRIVE {Upload,Download,Copy,Rename,Delete}` — `google_drive_test` отсутствует в БД (skipped-as-fail).
7. `SMB Speed Test` — в раннере жёстко задан как skipped-as-fail.
8. `FTP Download` — `FTP authentication failed for user: sza` + warning об empty password в credentials.

Дополнительно исправлено в программе после этого прогона (ожидает перепроверки новым запуском):
- `CloudFileOperationHandler`: нормализация `cloud:/` → `cloud://` и корректное определение CLOUD-источника.
- `CloudFileOperationHandler.moveCloudToCloud(...)`: fallback `copy + delete`, если native move не может выполнить операцию по path-based идентификатору.

## Update 3 (свежий прогон после сборки/установки новой версии, лог 13:25)

- Итог: `Total 130 / Passed 123 / Failed 7`.
- Дельта к предыдущему прогону (`126 / 118 / 8`):
   - +4 теста в наборе (добавлены новые интеграционные тесты в раннер).
   - +5 успешных тестов.
   - -1 падение.

### Статус ключевых фиксов

- `Matrix Move: CLOUD → CLOUD` — **исправлено** (в новом списке падений отсутствует).
- `Matrix Copy: CLOUD → CLOUD` — **исправлено** (по-прежнему не падает).

### Что всё ещё падает (7)

1. `GOOGLE_DRIVE Upload` — `google_drive_test` not found in database (skip-as-fail).
2. `GOOGLE_DRIVE Download` — `google_drive_test` not found in database (skip-as-fail).
3. `GOOGLE_DRIVE Copy` — `google_drive_test` not found in database (skip-as-fail).
4. `GOOGLE_DRIVE Rename` — `google_drive_test` not found in database (skip-as-fail).
5. `GOOGLE_DRIVE Delete` — `google_drive_test` not found in database (skip-as-fail).
6. `SMB Speed Test` — intentional skip-as-fail в раннере.
7. `FTP Download` — `Failed to upload to FTP`.

### Комментарий по FTP Download

- На предыдущем прогоне было явно `Empty password stored` + `FTP authentication failed`.
- На текущем прогоне в блоке fail видно устойчивый `Failed to upload to FTP` на этапе подготовительного upload.
- Это выглядит как инфраструктура/учётка/права целевой FTP директории, а не регресс логики copy/move.



Источник данных:
- лог прогона: `temp/current.log`
- логика раннера: `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/IntegrationTestRunner.kt`

## 1) Общая сводка

- Total: 126
- Passed: 115 (91%)
- Failed: 11 (8%)
- Cloud summary:
  - ONEDRIVE: 5/5
  - DROPBOX: 5/5
  - GOOGLE_DRIVE: 0/5

(см. `temp/current.log`, блок Summary на строках ~5326-5334)

---

## 2) Проблемные тесты (полный список)

1. `Copy Local → SFTP`
   - Ошибка: `SFTP resource not found in database`
2. `Copy Local → FTP`
   - Ошибка: `FTP resource not found in database`
3. `Matrix Copy: CLOUD → CLOUD`
   - Ошибка: `Не удалось скопировать ни одного файла: Failed to upload ... to cloud`
4. `Matrix Move: CLOUD → CLOUD`
   - Ошибка: `Не удалось переместить ни одного файла: Failed to upload ... to cloud`
5. `GOOGLE_DRIVE Upload`
   - Ошибка: `GOOGLE_DRIVE test resource not found in database - test skipped`
6. `GOOGLE_DRIVE Download`
   - Ошибка: `GOOGLE_DRIVE test resource not found in database - test skipped`
7. `GOOGLE_DRIVE Copy`
   - Ошибка: `GOOGLE_DRIVE test resource not found in database - test skipped`
8. `GOOGLE_DRIVE Rename`
   - Ошибка: `GOOGLE_DRIVE test resource not found in database - test skipped`
9. `GOOGLE_DRIVE Delete`
   - Ошибка: `GOOGLE_DRIVE test resource not found in database - test skipped`
10. `SMB Speed Test`
    - Ошибка: `Speed test requires database credentials registration - test skipped`
11. `FTP Download`
    - Ошибка: `Upload failed ... Failed to upload to FTP` + `FTP authentication failed for user: sza`

(см. `temp/current.log`, блок Failed Tests на строках ~5337-5345)

---

## 3) Причины по каждому тесту

## A. Нет тестовых ресурсов в БД (конфигурационная причина)

### A1. Copy Local → SFTP
- Причина: раннер ищет ресурс `SFTP` в БД (`findSftpResource()`), не находит и сразу фейлит.
- Доказательство в коде:
  - `findSftpResource()` использует точное имя `"SFTP"`.
  - `testCopyLocalToSftp()` при `null` делает fail с текстом `SFTP resource not found in database`.

### A2. Copy Local → FTP
- Причина: аналогично, ищется ресурс `FTP` в БД (`findFtpResource()`), отсутствует.
- Доказательство в коде:
  - `findFtpResource()` использует точное имя `"FTP"`.
  - `testCopyLocalToFtp()` при `null` делает fail с `FTP resource not found in database`.

### A3-A7. Все GOOGLE_DRIVE тесты (5 шт.)
- Тесты: Upload, Download, Copy, Rename, Delete.
- Причина: `findCloudResource(CloudProvider.GOOGLE_DRIVE)` ищет ресурс по имени `google_drive_test`; в БД его нет.
- Доказательство в коде:
  - `findCloudResource()` маппит Google → `"google_drive_test"`.
  - Каждая cloud-функция (`testCloudProviderUpload/Download/Copy/Rename/Delete`) при `null` сразу пишет fail `... test resource not found in database - test skipped`.

## B. Известно "жёстко зашитое" skipped-as-fail поведение (методологическая причина)

### B1. SMB Speed Test
- Причина: тест намеренно помечается как `fail`/`skipped` в самой реализации.
- Доказательство в коде:
  - `testNetworkSpeedTest(...)` всегда вызывает `recordResult(... success=false, error="Speed test requires database credentials registration - test skipped")`.
- Вывод: это не runtime regression, а текущая политика раннера.

## C. Ошибка реализации cloud→cloud в обработчике операций (кодовая причина)

### C1. Matrix Copy: CLOUD → CLOUD
- Причина: при копировании cloud-файла в cloud destination обработчик трактует cloud source как LOCAL-путь.
- Доказательство в логе:
  - `sourceType=LOCAL` для `sourcePath=cloud:/google_drive/...`
  - далее попытка проверки как локального файла: `Local file does not exist: /cloud:/google_drive/...`
- Итог: upload падает, тест фейлится.

### C2. Matrix Move: CLOUD → CLOUD
- Причина: идентичная C1, но для move.
- Доказательство в логе:
  - `Cloud executeMove ... from LOCAL`
  - затем `Local file does not exist: /cloud:/google_drive/...`

## D. Ошибка аутентификации FTP (инфраструктурная/учётные данные)

### D1. FTP Download
- Причина: этап предварительной загрузки файла на FTP падает из-за auth failure.
- Доказательство в логе:
  - `FTP authentication failed for user: sza`
  - `uploadToFtp: Failed to prepare destination`
  - затем тест фиксирует `Upload failed ... Failed to upload to FTP`.

---

## 4) Сводка причин (группировка)

1. **Конфигурация БД тестовых ресурсов отсутствует** — 7 тестов
   - SFTP/FTP resource missing + 5x GOOGLE_DRIVE missing.
2. **Логическая ошибка cloud handler (cloud source интерпретируется как local)** — 2 теста
   - Matrix Copy/Move CLOUD→CLOUD.
3. **Намеренный skipped-as-fail в тест-раннере** — 1 тест
   - SMB Speed Test.
4. **Проблема FTP auth/учётки** — 1 тест
   - FTP Download.

---

## 5) Приоритетные действия (коротко)

P1 (критично, код):
- Исправить cloud handler для cloud→cloud, чтобы source cloud path не обрабатывался как local file path.

P2 (конфигурация):
- Добавить/проверить в БД ресурсы с точными именами:
  - `google_drive_test`, `onedrive_test`, `dropbox_test`, `SFTP`, `FTP`, `test_media`.

P3 (инфра):
- Проверить валидность FTP credentials (`sza`) и права записи в целевую FTP директорию.

P4 (качество раннера):
- Для `SMB Speed Test` поменять семантику на `SKIP` (не `FAIL`) или включить автоподготовку credentialsId.
