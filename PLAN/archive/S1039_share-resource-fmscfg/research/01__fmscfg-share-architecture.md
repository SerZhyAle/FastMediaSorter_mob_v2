# Research 01 - .fmscfg share architecture (S1039)

**Дата:** 2026-07-13
**Метод:** android-solution-researcher (read-only) + прямая верификация несущих утверждений.

## Главный вывод

Запрошенная фича на ~85% уже реализована. Половина "отправить файлом" сделана в **S0984**
(`share-sftp-resource-config`, статус `BlockNeedUserTest`): пункт меню ресурса, диалог экспорта,
`ExportCompanionConfigUseCase` -> `.fmscfg`, `ACTION_SEND` через FileProvider (Gmail/Telegram/..).
QR-скан (декод) при импорте сделан в **S0988** (Verified). Реально отсутствует только
**генерация QR** (энкод `.fmscfg` -> отображаемый bitmap).

Итог: S1039 = "добавить генерацию/показ QR поверх существующего share-флоу S0984", scope SFTP-only.

## Ключевые файлы (verified)

- `data/companion/CompanionConfigSerializer.kt` - пишет ТОЛЬКО plain-JSON. KDoc прямо: компактный
  `FMSCFG1:` вариант "import-only, used by the QR path" -> writer нужно добавить.
- `data/companion/CompanionConfigParser.kt` - уже принимает и plain-JSON, и `FMSCFG1:` (gzip+base64);
  `COMPRESSED_PREFIX = "FMSCFG1:"`, `PROTOCOL_SFTP = "sftp"` (protocol заморожен), schemaVersion 2.
  Стр. 86-88: пустой пароль валиден Android-стороной (passwordless share - получатель вводит пароль
  при импорте). Готовое безопасное решение для QR.
- `domain/usecase/companion/ExportCompanionConfigUseCase.kt` - ресурс+credentials -> `CompanionConfigDto`.
  НЕТ проверки на key-only SFTP (в отличие от `ExportResourcesToFileUseCase` .fmsr, который skip'ает) ->
  для key-only даёт молча битый конфиг.
- `domain/usecase/companion/ImportCompanionConfigUseCase.kt` - `.fmscfg`/QR payload -> `MediaResource`.
- `ui/main/helpers/MainSftpShareManager.kt` (42 LOC) - диалог экспорта (чекбокс пароля, LAN-warning).
- `ui/main/helpers/MainEventHandler.kt:173-190` - `shareCompanionConfigFile`: FileProvider
  (`${applicationId}.fileprovider`) + `ACTION_SEND`, MIME `application/vnd.fms.companion-config+json`.
- `ui/main/ResourceAdapter.kt:464-484,831-871` - overflow-меню ресурса; видимость пунктов по типу.
- `res/menu/resource_item_actions.xml` - `action_export_resource` (.fmsr, S0422) + `action_share_sftp_access`
  (.fmscfg, S0984) уже есть.
- `ui/main/MainViewModel.kt:361-372` - `shareSftpResourceConfig(resource, includePassword)`.
- `ui/companionimport/qr/QrCodeAnalyzer.kt` - декод через ZXing `MultiFormatReader` (зеркало для энкодера).

## QR-генерация - зависимость уже есть (verified)

- `app_v2/build.gradle.kts:1331` - `implementation("com.google.zxing:core:3.5.3")`, проектно-широко,
  все флейворы. Содержит `QRCodeWriter`/`MultiFormatWriter`/`BitMatrix` для энкода. **Новых зависимостей нет.**
- QR-показ не требует камеры и рантайм-пермишенов (в отличие от скана S0988) -> доступен и на vr.

## Безопасность (главный риск)

- `.fmscfg` может нести пароль SFTP в открытом виде (`ExportCompanionConfigUseCase`: `password =
  if (includePassword) credentials.password else ""`; `NetworkCredentialsEntity.password` полностью
  дешифрует AES/GCM Keystore-ciphertext перед попаданием в DTO).
- В проекте НЕТ `FLAG_SECURE` нигде -> QR с паролем на экране скриншотится/виден в Recents/пишется
  записью экрана. Файл-вложение такой видимой поверхности не имеет.
- Митигация: для QR по умолчанию пароль ВЫКЛючен (контракт допускает пустой) + экран QR под FLAG_SECURE.

## Флейворы / API

- `SUPPORT_LOCAL_NETWORK=true`: standard/noLegal/photos/legacy/vr; `false` в lite (SFTP-ресурс там не
  создать -> пункт недостижим, явный guard не нужен). Видимость по `resource.type == SFTP` (Rule 14 ok).
- API: без нового минимума. FileProvider/ACTION_SEND/Bitmap/ZXing core работают с API 23 (legacy).

## Разрешённые дизайн-развилки (из контракта/кодовой базы, owner не нужен)

1. Scope = SFTP-only (`.fmscfg` заморожен на protocol=sftp; any-type = .fmsr S0422, у него нет парного сканера).
2. S1039 = QR поверх S0984, не редизайн (сырой запрос совпадает с реализованным флоу S0984).
3. Пароль в QR по умолчанию выключен + FLAG_SECURE.
4. Key-only SFTP блокировать с сообщением (зеркало skip'а в .fmsr-экспорте).
5. Компактный транспорт FMSCFG1: для QR (writer симметричен уже существующему парсеру).
6. QR-опция расширяет существующий диалог "Поделиться доступом" (`MainSftpShareManager`), не новый пункт меню.
7. Доступно на vr (QR-показ без камеры).
8. Один `MediaResource` -> один `.fmscfg` (как S0984; несколько корней уже в контракте).

## /spec-draft кандидаты (для владельца, вне S1039)

- FLAG_SECURE отсутствует во всём приложении - секрет-несущий UI (PIN-диалоги, поля учёток) равно уязвим
  к скриншотам/Recents. Общий аудит - отдельный тикет (QR-инстанс покрыт в S1039).
- Doc-долг по companion/`.fmscfg`: 8 тикетов кода, но `docs/ARCHITECTURE.md` и
  `dev/PROJECT_OPERATIONS_INDEX.md` не упоминают `data/companion`/`ui/companionimport`. Скоуп `/doc-update`.
