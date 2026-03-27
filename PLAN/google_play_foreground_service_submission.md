# Google Play: публикация релиза с операциями по расписанию

## Контекст

При следующей публикации релиза с включёнными `ENABLE_SCHEDULED_OPERATIONS=true` и
`ENABLE_BACKGROUND_AUDIO=true` Google Play Console покажет форму
**"Foreground service permissions"** с двумя разделами:

- `FOREGROUND_SERVICE_DATA_SYNC`
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK`

По каждому нужно отметить галочки и приложить ссылку на видео-демонстрацию.

---

## Раздел 1 — FOREGROUND_SERVICE_DATA_SYNC

### Что отмечать

**Network processing → Other** ✅

> Это единственный подходящий вариант. Приложение синхронизирует файлы с сетевыми
> папками (SMB/SFTP/FTP) и выполняет операции копирования/перемещения/удаления в
> фоне по расписанию. Ни один из стандартных вариантов ("Backing up, restoring",
> "Media transcoding", "Importing, exporting") не описывает это точно.

**Что НЕ отмечать:** Local processing, Other tasks.

### Сценарий видео для DATA_SYNC

**Продолжительность:** 1–3 минуты.
**Устройство:** реальное Android-устройство с доступом к SMB/SFTP-серверу или
эмулятор + локальные папки.

#### Шаги для записи

1. **Открыть приложение** → перейти в **Настройки → Операции → Операции по расписанию**.

2. **Показать глобальный переключатель** "Использовать операции по расписанию" —
   включить его. Список операций становится виден.

3. **Нажать "Добавить"** — открывается диалог создания операции.

4. В диалоге:
   - Источник: выбрать сетевую папку (SMB/SFTP) или локальную папку.
   - Операция: **Копировать** или **Переместить**.
   - Назначение: выбрать целевую папку (другой ресурс).
   - Расписание: установить интервал, например, каждые 1 час.
   - Нажать **Сохранить**.

5. Созданная операция появляется в списке. Показать что у неё есть:
   - Статус включена/выключена.
   - Время следующего запуска.

6. Нажать **▶ Запустить сейчас** — операция запускается немедленно.

7. **Свернуть приложение** (нажать Home).

8. Подождать 3–5 секунд. В шторке уведомлений появляется уведомление
   *"Scheduled operations"* — это и есть foreground service DATA_SYNC в действии.

9. Вернуться в приложение → **Журнал** → показать лог с записями по каждому файлу
   (`OK: filename.jpg`, `SKIP: filename.jpg` и т.д.).

10. *(Опционально)* Показать что файлы действительно скопировались в папке назначения.

#### Текст пояснения для Play Console (поле "Explanation")

```
FastMediaSorter is a file manager for local and network storages (SMB, SFTP, FTP).
The FOREGROUND_SERVICE_DATA_SYNC permission is used by the Scheduled Operations feature,
which allows users to automate recurring copy, move, or delete tasks between folders
(including remote network shares) on a user-defined schedule (e.g. every 24 hours).
The foreground service ensures the transfer is not killed by the system while
the app is in the background, and shows a persistent notification to the user
during the operation.
```

---

## Раздел 2 — FOREGROUND_SERVICE_MEDIA_PLAYBACK

### Что отмечать

**Media playback** ✅

> Приложение — медиаменеджер с встроенным аудиоплеером. При воспроизведении аудио
> в фоне (экран выключен или другое приложение активно) используется foreground service
> типа mediaPlayback с уведомлением-контроллером (play/pause/next).

**Что НЕ отмечать:** Show picture in picture, Other.

### Сценарий видео для MEDIA_PLAYBACK

**Продолжительность:** 1–2 минуты.

#### Шаги для записи

1. **Открыть приложение** → перейти в любую папку с аудиофайлами (MP3, FLAC и т.д.).

2. Нажать на аудиофайл — открывается аудиоплеер. Музыка начинает играть.

3. Показать что воспроизведение идёт (прогресс-бар движется, название трека видно).

4. **Нажать Home** — приложение уходит в фон.

5. В шторке уведомлений показать уведомление медиаплеера с кнопками
   управления (play/pause). Нажать pause — музыка останавливается. Нажать play —
   возобновляется.

6. *(Опционально)* Включить другое приложение, вернуться — музыка продолжает играть.

#### Текст пояснения для Play Console (поле "Explanation")

```
FastMediaSorter includes a built-in audio player that supports background playback.
The FOREGROUND_SERVICE_MEDIA_PLAYBACK permission is required to keep audio playback
active when the user navigates away from the app or turns off the screen.
A persistent media notification with playback controls (play, pause, skip) is shown
to the user at all times during background playback, in compliance with Android
media playback guidelines.
```

---

## Чеклист перед публикацией релиза с операциями

- [ ] В `build.gradle.kts` изменить в блоке `release`:
  ```kotlin
  buildConfigField("boolean", "ENABLE_SCHEDULED_OPERATIONS", "true")
  buildConfigField("boolean", "ENABLE_BACKGROUND_AUDIO", "true")
  ```
- [ ] Удалить файл `app_v2/src/release/AndroidManifest.xml` или убрать из него
  `tools:node="remove"` для всех разрешений (либо удалить файл целиком — тогда
  все разрешения вернутся из main манифеста автоматически).
- [ ] Собрать AAB: `.\gradlew.bat bundleStandardRelease`
- [ ] Загрузить AAB в Play Console.
- [ ] Заполнить форму "Foreground service permissions" согласно этому документу.
- [ ] Приложить ссылку на видео (YouTube unlisted или Google Drive).

---

## Одно видео или два?

Можно записать **одно видео** (~3 минуты), которое покрывает оба раздела:

1. Сначала показываем фоновое воспроизведение аудио (MEDIA_PLAYBACK).
2. Затем показываем операцию по расписанию с уведомлением (DATA_SYNC).

Один и тот же URL вставляется в оба поля "Video link" в форме Play Console.
