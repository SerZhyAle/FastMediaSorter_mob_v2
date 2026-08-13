# Стратегическая спецификация: S0416 - Краш foreground-сервиса из-за `?attr`-tint иконки уведомления

**Ticket:** S0416
**Status:** Archived
**Priority:** 70
**Date:** 2026-06-14
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - анализ on-device лога (2026-06-14)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк.

---

## 1. Проблема

`CannotPostForegroundServiceNotificationException: Bad notification for startForeground` на Android 16: foreground-сервис скачивания (link/deliverable download) падает, потому что small-icon его уведомления - vector с `android:tint="?attr/..."`. Тема-атрибут не резолвится, когда систему инфлейтит иконку статус-бара вне темы приложения; Android 16 строго валидирует small-icon при `startForeground` и отвергает уведомление.

Тот же класс дефекта, что в S0405 (там была `ic_widget_camera_quick_capture`). Это уже **второй** случай - значит проблема системная: любая `?attr`-tinted иконка, отданная в `setSmallIcon` foreground-уведомления, ведёт к крашу на Android 16.

Свидетельство (причина 1, иконка): лог сессии 2026-06-14 09:22:46 (Samsung SM-S731B, Android 16) - перед крашем серия `notify(7100, channel=link_download_channel, ONGOING_EVENT)`, затем `E/AndroidRuntime` и `CrashActivity`. Иконка обоих воркеров скачивания - общий `ic_cloud_download` с `android:tint="?attr/colorOnSurface"`.

Причина 2 (отсутствующий канал, только link-download): после фикса иконки краш сохранился на сборке `.241`. Лог `fastmediasorter_20260614_124526` показал: `LinkDownloadWorker: start url=instagram...` → `S0416: link-download foreground notification built` (тег сработал РОВНО один раз) → краш. Тег стоит в `buildForegroundInfo`, который зовётся из `getForegroundInfo` (там есть `ensureChannel`) и из `doWork` (там НЕТ). Один вызов = `getForegroundInfo` не вызывался = `ensureChannel` не выполнялся = канал `link_download_channel` не создан. Android 16 отвергает foreground-нотификацию на несуществующем канале. `DeliverableDownloadWorker` этим не страдает - он зовёт `ensureNotificationChannel()` перед `setForeground`.

---

## 2. Цели

1. Foreground-уведомления воркеров скачивания (link + deliverable) используют notification-safe small-icon (белая заливка, без `?attr`-tint) - старта FGS на Android 16 без краша.
2. Общий `ic_cloud_download` не трогается: он остаётся тематически тонированным для своих UI-кнопок (настройки бэкапа/импорта).
3. (Профилактика) Механический гейт, запрещающий `?attr`-tinted drawable в `setSmallIcon` любого foreground-уведомления, чтобы дефект не воспроизвёлся третий раз.

**Non-goals:**

- Переписывание самих воркеров/механизма скачивания.
- Изменение прочих (не-foreground) уведомлений.

---

## 3. Пожелания и ограничения

- **Flavor:** воркеры в `src/main`, дефект во всех сборках с этими фичами; без `BuildConfig`-гейтов (Rule 15).
- **API level:** проявляется на Android 16 / API 36; иконка должна быть валидной small-icon на всех API.
- **Иконка статус-бара:** white-on-transparent, без ссылок на тему; система сама тонирует.

---

## 4. Контекст текущей архитектуры

`setSmallIcon` обоих воркеров скачивания ссылается на общий `ic_cloud_download` (он же используется как `app:icon` кнопок в настройках, где `?attr`-tint желателен). FGS-уведомление строится в точках входа `buildForegroundInfo` (link) и `createForegroundInfo` (deliverable). `setForeground` обёрнут в try/catch «non-fatal», но `CannotPostForegroundServiceNotificationException` доставляется системой асинхронно на main-thread и потому не перехватывается - краш фатальный.

---

## 5. Предлагаемый подход

1. Отдельная notification-иконка `ic_notification_cloud_download` (копия пути `ic_cloud_download`, белая заливка, без `?attr`-tint).
2. Оба воркера скачивания переведены на неё во всех `setSmallIcon` (прогресс, успех, отказ, foreground).
3. `LinkDownloadWorker.buildForegroundInfo` гарантирует канал (`ensureChannel`) перед построением foreground-нотификации - покрывает путь `doWork`, где `getForegroundInfo` не вызывается. Идемпотентно.
4. Профилактический гейт: (а) ни один drawable в `setSmallIcon` foreground-уведомления не `?attr`-tinted; (б) любой `setForeground`/`ForegroundInfo`-путь гарантированно создаёт свой канал перед стартом.

---

## 6. Открытые вопросы

1. **Полнота аудита.** Проверены все FGS-small-icon'ы: только `ic_cloud_download` (link + deliverable) был `?attr`-tinted; остальные (`ic_notification_audio`, `ic_widget_quick_audio_recorder_idle`, `ic_notification_screen_capture`) чисты. Подтвердить, что новых FGS-иконок не добавлено.
   - **Статус:** Resolved (на момент 2026-06-14)

2. **Точное подтверждение краша 09:22:46.** Полный стектрейс не приложен (в логе только строка `Process: PID`). Воспроизведение link-download на Android 16 на старой сборке должно показать `CannotPostForegroundServiceNotificationException`; на пофикшенной - старт FGS без краша.
   - **Статус:** Open (нужен device-репро / полный стектрейс)

---

## 7. Критерии готовности

1. Запуск link-download и deliverable-download на Android 16 поднимает foreground-уведомление без краша.
2. Общий `ic_cloud_download` в UI-кнопках выглядит как прежде (тематический tint сохранён).
3. Гейт ловит повторное появление `?attr`-tinted FGS-иконки.

---

## 8. Связи с другими спеками

- S0405 - первый случай того же класса (иконка оверлей-скриншота); фикс той же семьи (`ic_notification_screen_capture`).
