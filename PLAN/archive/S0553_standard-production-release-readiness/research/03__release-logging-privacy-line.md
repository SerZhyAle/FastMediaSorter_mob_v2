# Research 03 - Release logging privacy line (S0553 §9.6)

**Вопрос:** какие production logs приемлемы, а какие уже считаются утечкой device/settings surface для market build?

## Наблюдения

- В release уже приглушены отдельные каналы: `LOG_SMB_IO=false`, `LOG_NETWORK_THUMBNAILS=false`, `LOG_LINK_DOWNLOAD=false` (debug: часть из них true).
- Логирование только через Timber (CLAUDE.md). Persistent `Timber.i/w/e` не должны нести `Sxxxx` (зарезервировано под BlockNeedUserTest probes).
- §5.3 спеки требует GDPR-compliant диагностику с opt-in.

## Решение - privacy line для standard production логов

Запрещено в release-логах (= утечка, блокер по §5.5/§6.5):

- Device identifiers: serial, IMEI, MAC, Android ID, hardware fingerprint.
- Account/credential surface: email, токены, пароли, OAuth/refresh tokens, API keys.
- Имена пользовательского контента: полные пути файлов, отображающие имена медиа.
- Полные сетевые URI: host+share+path для SMB/SFTP/FTP/cloud (хост и путь раскрывают приватную инфраструктуру).
- Гео/локация.

Приемлемо в release-логах:

- Тип операции, класс/код ошибки, счётчики, длительности.
- Capability-fallback notices на корректном уровне (`Timber.i`, не `Timber.e` для ожидаемых фолбэков).
- Анонимизированные/усечённые идентификаторы (схема URI без host/path; расширение файла без имени).

Применение: gate включает release-logging audit на запрещённые категории по списку выше; нарушение = operational loss до waiver.

**Статус:** Resolved
