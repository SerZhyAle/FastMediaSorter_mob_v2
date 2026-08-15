# Стратегическая спецификация: S0137 — Cast network/cloud streaming через локальный proxy

**Ticket:** S0137
**Status:** BlockNeedUserTest
**Priority:** 50
**Date:** 2026-05-10
**Tier:** 4 — Architectural
**Roadmap entry:** Ad-hoc — полевая сессия 2026-05-10, лог `logs/fastmediasorter_20260510_012252.log`
**Tactical plan:** `PLAN/S0137_feature-cast-network-cloud-streaming/INDEX.md`

> **Scope:** STRATEGIC. Реализация недостающего пути «открыть InputStream к удалённому файлу для каста» в `CastMediaManager`. Сейчас этот путь — заглушка с warn'ом, любой каст SMB/SFTP/FTP/Cloud медиа падает в toast «cast_error_file».

---

## 1. Проблема

`CastMediaManager.openRemoteInputStream` ([ui/player/helpers/CastMediaManager.kt:339-347](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt#L339-L347)) — заглушка:

```kotlin
// Full network/cloud streaming requires injecting SmbClient/SftpClient/etc — deferred
// to a follow-on implementation once the basic local+cache path is validated.
Timber.w("CastMediaManager: openRemoteInputStream — network/cloud download not yet wired")
return null
```

Возврат `null` приводит к: `downloadToTemp` → `tempFile` не создан → `localFile == null` → ветка ([CastMediaManager.kt:259-264](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt#L259-L264)) показывает Toast `cast_error_file`.

В логе 2026-05-10 это сработало 3 раза за 30 минут:

- `01:28:46` — каст SMB-mp3 `●Наутилус Помпилиус - Падал тёплый снег.mp3`
- `01:50:27` — каст SMB-mp3 `Юрий_Антонов_-_Под_крышей_дома_твоего.mp3`
- `01:51:29` — каст SMB-jpg `20260203_120944.jpg`

В каждом случае пользователь нажал «cast» и получил «не получилось», без понимания почему.

### 1.1 Что неизвестно

- Поведение proxy-server при больших файлах (видео): уже есть гейт `MAX_VIDEO_CAST_BYTES`, но непонятно, нужен ли аналогичный лимит для аудио и изображений (для них сейчас гейта нет, и большой mp3/raw photo может занять весь cacheDir).
- Готов ли cloud-pipeline отдавать InputStream синхронно (Google Drive / OneDrive / Dropbox SDK обычно тянут файл через async API).
- Как себя ведёт `LocalProxyServer` при разрыве сети между `serveFile` и финальным запросом от Cast receiver.

### 1.2 Влияние на пользователя

- Каст для SMB/SFTP/FTP/Cloud медиа **полностью не работает** — единственный сценарий каста сейчас это локальный файл в `MediaStore` или `Downloads`.
- Это противоречит ожиданию пользователя: SMB-медиа доступно для воспроизведения локально → должно быть доступно и для Cast.
- В UI нет индикатора «cast not supported for this source» — пользователь видит только generic toast после неудачи.

---

## 2. Цели

1. `openRemoteInputStream` возвращает работающий `InputStream` для всех supported network/cloud источников.
2. `downloadToTemp` пишет файл в `cacheDir` без OOM/UI-фриза для типичных размеров (mp3 ≤ 10MB, jpg ≤ 20MB, видео по `MAX_VIDEO_CAST_BYTES`).
3. На время скачивания пользователь видит понятный progress (toast `cast_preparing` сейчас уже есть, но без длительности).
4. При превышении размера каста для аудио/изображений — отдельный toast с явным указанием лимита (по аналогии с `cast_video_too_large`).
5. Каст cloud-источников (Google Drive / OneDrive / Dropbox) — отдельная фаза, может быть отложена в S0137-followup, если SDK не поддерживает синхронный stream.

**Non-goals:**

- Не реализовывать прямой стриминг с SMB/SFTP/FTP в Cast receiver минуя temp-файл — упрощённый proxy через filesystem остаётся.
- Не трогать `panel single-eye crop` (TODO §6 из самого `CastMediaManager`, отдельная спека).
- Не вводить новый формат конфига каста.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. SMB первым приоритетом — это самый частый сетевой источник.
2. Cloud — отдельной фазой; если SDK не позволяет синхронный stream — отложить, не блокировать SMB.
3. Не дублировать логику открытия удалённого InputStream — использовать те же клиенты, что уже инжектируются в Glide-loader'ы (`SmbClient`, `SftpClient`, `FtpClient`).

### 3.2 Жёсткие ограничения

- **Flavor:** только `standard` (где `supportCloud` или активен SMB/SFTP/FTP — фактически везде, кроме `photos`).
- **API level:** без изменений.
- **Производительность:** скачивание видео не должно блокировать UI; временные файлы не должны накапливаться (cleanup в `deleteTempFile` уже есть).
- **Совместимость данных:** нет.
- **Локализация:** EN/RU/UK для новых строк (например, `cast_audio_too_large`, `cast_image_too_large`, `cast_preparing_progress`).

---

## 4. Контекст текущей архитектуры

`CastMediaManager` ([ui/player/helpers/CastMediaManager.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt)) — менеджер каст-сессии. Использует `LocalProxyServer` для отдачи локального файла на receiver через HTTP.

Поток `castFile`:

1. Определить тип источника (LOCAL / NETWORK / CLOUD) и размер.
2. Для LOCAL — взять `File(file.path)` напрямую.
3. Для NETWORK/CLOUD — `downloadToTemp` → `tempFile` в `cacheDir`.
4. `proxyServer.serveFile(localFile)` → `castUrl`.
5. `loadMediaOnReceiver(file, castUrl)` через `RemoteMediaClient`.

`downloadToTemp` ([CastMediaManager.kt:319-333](app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt#L319-L333)) уже умеет копировать поток в файл — нужно только дать ему `InputStream`.

Существующие клиенты:

- `SmbClient` — инжектируется в `NetworkVideoFrameDecoder`, `NetworkPdfThumbnailLoader`, `NetworkEpubCoverLoader` ([di/GlideAppModule.kt](app_v2/src/main/java/com/sza/fastmediasorter/di/GlideAppModule.kt)). Выдаёт `InputStream` для SMB-файла.
- `SftpClient`, `FtpClient` — аналогично.
- Cloud-loader'ы — `CloudThumbnailModelLoader` ([data/cloud/glide/](app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/glide/)) для миниатюр; полное скачивание идёт через provider-specific SDK (Google Drive `Files.get()`, OneDrive `getContent()`, Dropbox `download()`).

`MediaFile` — модель файла; есть `path`, `type`, `size`. Тип источника (SMB / SFTP / FTP / Cloud / LOCAL) определяется по `path` префиксу.

---

## 5. Предлагаемый подход

### 5.1 Этапы работы

**Phase F1 — SMB streaming:**

- Инжектировать `SmbClient` в `CastMediaManager` через Hilt.
- Реализовать ветку `path.startsWith("smb://")` в `openRemoteInputStream` — открыть `InputStream` через `SmbClient`.
- Добавить размерные гейты для аудио/изображений (`cast_audio_too_large`, `cast_image_too_large`) с локализацией.
- Verification: каст SMB-mp3 на Chromecast → файл проигрывается; каст SMB-jpg → отображается на TV.

**Phase F2 — SFTP/FTP streaming:**

- Аналогично F1, добавить ветки `sftp://` и `ftp://`.

**Phase F3 — Cloud streaming (опциональная, follow-up):**

- Изучить, могут ли провайдеры дать синхронный/отложенный InputStream без полного скачивания в RAM.
- Если да — реализовать. Если нет — отложить до S0137-cloud follow-up.

**Phase F4 — Progress feedback:**

- Заменить разовый `cast_preparing` toast на progress-индикатор (либо progress-bar в нотификации, либо обновляемый toast). Решение по UI принимается в /ui-clarify.

### 5.2 Точки расширяемости

- Стандартный паттерн «временный файл в cacheDir + LocalProxyServer» сохраняется; для будущих протоколов (например, WebDAV) добавление сводится к одной ветке в `openRemoteInputStream`.

---

## 6. Открытые вопросы / Research items

1. **Размеры файлов:** какие реальные верхние границы для аудио/изображений (типичные mp3 4-10MB, raw photo 20-50MB)? Поставить лимит, чтобы не забить cacheDir.
   - **Статус:** open (нужен ответ владельца)

2. **Cloud streaming — есть ли API синхронного InputStream?**
   - Google Drive: `Drive.Files.get(fileId).executeMediaAsInputStream()` — да, поддерживает.
   - OneDrive (MSAL+Graph): `client.drive().items(id).content().buildRequest().get()` — возвращает `InputStream`.
   - Dropbox: `client.files().download(path)` — `DbxDownloader.getInputStream()`.
   - **Статус:** все три SDK поддерживают; вопрос только в auth-token freshness.

3. **Прогресс-индикатор UI:** toast / notification / dialog? Решается через /ui-clarify.
   - **Статус:** open

4. **Кэширование скачанного:** при повторном касте того же файла — скачивать заново или переиспользовать `tempFile`?
   - Сейчас `deleteTempFile` чистит при `handleSessionEnd`. Возможно, переиспользовать в рамках одной сессии.
   - **Статус:** open

---

## 7. Риски

- **Большие файлы съедают cacheDir.** Митигация: гейт `MAX_AUDIO_CAST_BYTES` / `MAX_IMAGE_CAST_BYTES`, явный toast.
- **Долгая загрузка блокирует UI.** Митигация: `withContext(Dispatchers.IO)` уже есть; нужен progress.
- **Cloud auth expires во время скачивания.** Митигация: refresh-token до `executeMediaAsInputStream`.
- **Receiver не дожидается окончания scan-времени.** Митигация: показать `cast_preparing` до старта `loadMediaOnReceiver`.

---

## 8. Влияние на пользователя (docs/FEATURES)

- Новая возможность: каст SMB/SFTP/FTP/Cloud медиа на Chromecast.
- Обновление `docs/FEATURES.md` + `_RU` + `_UK`: расширить раздел Chromecast (если он есть; если нет — добавить).
- Новые строки локализации в `strings.xml`.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Скачивание в temp вместо прямого стриминга.**

- **Решение:** не подключать SMB/SFTP/FTP/Cloud напрямую к Cast receiver через свой stream-server.
- **Альтернативы:** локальный HTTP-сервер на телефоне, который проксирует SMB → HTTP в реальном времени.
- **Почему:** Cast receiver требует стандартных HTTP/HTTPS URL'ов; реализация полноценного reverse-proxy с протокольной трансляцией удваивает сложность. Temp-файл проще, надёжнее и работает с любым форматом, который поддерживает receiver.

**ADR-2: Cloud — отдельной фазой.**

- **Решение:** F1 (SMB) и F2 (SFTP/FTP) могут идти первыми; F3 (Cloud) — после успеха F1/F2.
- **Почему:** auth-token lifecycle и синхронный download cloud-провайдеров требуют отдельного research; не блокировать самую частую фичу (SMB) ради не самой частой (Cloud).

---

## 10. Связи с другими спеками

- Нет прямых зависимостей. Соседние:
  - **S0136** (bugfix-glide-disk-cache-not-persisting) — затрагивает тот же `cacheDir`; временные файлы каста должны храниться в **отдельной** поддиректории, чтобы не путаться с Glide.
  - Spec про panel stereo single-eye crop (`PHASE_06__cast-feasibility.md`, упомянутый в TODO кода) — параллельная Cast-задача, не блокирует.

---

## 11. Критерии готовности (strategic-level)

1. Каст SMB-mp3 / SMB-jpg / SMB-mp4 (≤ `MAX_VIDEO_CAST_BYTES`) на Chromecast — отрабатывает без warn'а `not yet wired`.
2. Каст SFTP/FTP — аналогично.
3. Превышение размера для аудио/изображений → toast с указанием лимита (а не generic `cast_error_file`).
4. Лог `CastMediaManager: openRemoteInputStream — network/cloud download not yet wired` исчезает из field-логов.
5. Cloud — либо реализован (F3), либо отложен в follow-up спеку с явным rationale.

---

## 12. Тактическая спецификация

`/spec-tech feature-cast-network-cloud-streaming` → `PLAN/S0137_feature-cast-network-cloud-streaming/` с фазами F1..F4 (F3 опциональная).
