# Стратегическая спецификация: S0202 — Link share download must survive backgrounding

<!-- auto-approved by /spec-all — 2026-05-14 -->

**Ticket:** S0202
**Status:** BlockNeedUserTest
**Priority:** 80
**Date:** 2026-05-14
**Implemented date:** 2026-05-14
**Tier:** TBD (likely 2 — significant)
**Roadmap entry:** Ad-hoc — device test 2026-05-14
**Epic:** S0156 — noLegal Capability Surface Audit
**Tactical plan:** `PLAN/S0202_link-share-background-survival/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

Сценарий по жалобе пользователя 2026-05-14:

1. В Instagram нажимается «Поделиться → FastMediaSorter» на видео/фото-посте.
2. Открывается transparent share-Activity, появляется блокирующий progress-диалог «Анализ страницы..».
3. Пользователь сворачивает приложение пальцем — возвращается в Instagram.
4. Скачивание молча обрывается. Файла нет, ошибки нет, уведомления нет, тоста нет — состояние «как будто share вообще не нажимали».

Подтверждение из лога `logs/fastmediasorter_20260514_200132.log`:

```
[219] 20:01:35  [link-dl] dynamic-extractor start url=https://www.threads.com/@babochka_vld/post timeoutMs=22000
[220] 20:01:36  App moved to BACKGROUND - optimizing resources
       ↓ полное молчание
[225] 20:03:47  ReceiveShareActivity.attachBaseContext  ← новый share через 2 минуты, никакой развязки предыдущего
```

Это критический UX-дефект: пользователь полагается на share-механизм Android, который сам по себе должен подразумевать «нажал — забыл, оно докачается». Текущая реализация требует от пользователя стоять перед экраном до 22 секунд (бюджет динамического экстрактора), что несовместимо с реальным сценарием «свайпнул контент → нажал поделиться → скроллю дальше».

---

## 2. Цели

1. После нажатия share любая ссылка обрабатывается до конца независимо от того, остаётся ли пользователь в нашем приложении.
2. Backgrounding share-Activity не прерывает ни сетевой запрос, ни WebView-рендеринг, ни запись на диск.
3. Пользователь получает итог (success или error) через системное уведомление, даже если сам процесс share-Activity больше не существует.
4. Cancel-кнопка в прогресс-диалоге остаётся работающей: пользователь может явно отменить тяжёлый share, пока он на экране.
5. Лимит «не более одного активного share за раз для одного URL» сохраняется — дублирующий share того же URL не запускает второй worker.

**Non-goals:**

- Полная очередь множества параллельных скачиваний (это территория S0161 + S0186, уже реализовано для batch-варианта).
- Глубокая интеграция с системным менеджером download'ов Android.
- Поведение share для НЕ-ссылочного контента (файлов через ACTION_SEND_MULTIPLE) — там нет долгого extraction-этапа.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Перед глазами пользователя сохраняется блокирующий прогресс-диалог — он информативен и удобен на быстрых скачиваниях (yt-dlp за 2-5 секунд).
2. Когда пользователь сворачивается — диалог исчезает без шума, но работа продолжается «в фоне».
3. По завершении — системное уведомление или фон-toast при возврате в наше приложение. Минимум: уведомление с именем файла и кнопкой «Open».

### 3.2 Жёсткие ограничения

- **Flavor:** main sourceSet — затрагивает все flavors, где включена фича share-receive (`standard`, `noLegal`, `vr`, `legacy`).
- **API level:** minSdk 26 — foreground-service notification обязательна для долгих фоновых процессов на API 26+.
- **Wear OS:** не затрагивается.
- **Производительность:** общий time-to-success не должен ухудшиться. Перенос в Worker может добавить ≤200ms на оверхед регистрации — допустимо.
- **Совместимость данных:** изменений схемы хранения нет.
- **Локализация:** EN/RU/UK — потребуются новые строки для notification-канала и его сообщений (имя канала, заголовок, текст).
- **Communication policy:** обязательно — все новые user-visible строки (notification title/text, toast по возврату) проходят `docs/COMMUNICATION_POLICY.md` §6.
- **Permission:** API 33+ требует `POST_NOTIFICATIONS` — это разрешение у noLegal/standard уже запрошено для других случаев (LinkDownloadWorker, NetworkSync). Уточнить в tactical, что для share-receive оно тоже работает по факту первого notification.

---

## 4. Контекст текущей архитектуры

`ReceiveShareActivity` — transparent activity, перехватывает `ACTION_SEND`. На single-URL share вызывает `processLinkAutoDownload(url, accountId)`. Тот запускает `lifecycleScope.launch { coordinator.handle(url, ...) }`. `coordinator.handle` обходит цепочку extraction-стратегий, и при успехе вызывает `LinkDownloadWriter`. Финальный результат показывается через `LinkAutoDownloadResultPresenter` — это либо toast, либо вложенный auth-диалог.

Параллельно существует `LinkDownloadWorker` (S0161) — он используется ТОЛЬКО для batch-вариантов (множественный URL) и для re-auth-сценария уведомления.

Лимит attention: `lifecycleScope` биндится к Activity. Когда Activity уходит в фон → `onStop`, при определённых системных условиях `onDestroy`, и в любом случае при `finishAffinity` / system-kill — scope отменяется. WebView в `InvisibleWebViewExtractionStrategy` дополнительно тормозится Android'ом при потере видимости (background-throttling), JavaScript-таймеры замедляются → 22-секундный бюджет не успевает.

S0161 (Archived, Implemented) явно зафиксировал решение «single-URL через lifecycleScope, batch через WorkManager» — основания: yt-dlp возвращался за секунды, прогресс-диалог давал нужный UX, перенос в Worker казался overkill. Сейчас, когда noLegal-flavor использует тяжёлый dynamic-extractor (S0197), эти основания утратили силу.

---

## 5. Предлагаемый подход

Единая стратегия — share single-URL всегда выполняется в `LinkDownloadWorker`, а UI прогресс-диалога становится лёгким «зеркалом» состояния worker'а через WorkManager LiveData.

### 5.1 Основные столпы

**Pillar A — Worker as the single execution surface.**
`ReceiveShareActivity.processLinkAutoDownload` больше не запускает корутину в своём scope. Вместо этого она enqueue'ит `LinkDownloadWorker` (single-URL вариант) и подписывается на его прогресс через `WorkManager.getWorkInfoByIdLiveData`. Worker сам поднимает foreground-service при старте (через `setExpedited`), что защищает его от Android background-kill'а.

**Pillar B — Progress dialog as a lifecycle-bound observer.**
`LinkAutoDownloadProgressDialog` отображает прогресс из `WorkInfo.progress` через flow/LiveData. При backgrounding Activity диалог естественно исчезает. Worker продолжает работу. Прогресс остаётся видим через notification (см. Pillar C).

**Pillar C — Foreground notification как fallback-UX.**
Worker публикует foreground notification с текстом «Анализ страницы..» → «Скачивание 23%..» → итоговое sticky-уведомление с именем файла и кнопкой «Открыть». На быстрых yt-dlp (3-5 сек) пользователь это уведомление почти не видит. На медленном dynamic-extractor (22 сек) — это его единственный канал обратной связи в фоне.

**Pillar D — Cancel routing.**
Cancel-кнопка в диалоге → `WorkManager.cancelWorkById`. Если активити уже не существует, cancel-action работает через notification action button («Отмена»).

**Pillar E — Result presentation на возврате в приложение.**
Если share завершился в фоне с успехом и пользователь вернулся в наше приложение — toast в любом активном экране (через `WorkInfoObserver`-singleton). На случай auth-required (`SocialPreviewOnly`) — поведение существующего S0161 EXTRA_REAUTH_URL сохраняется: notification action «Sign in» открывает `ReceiveShareActivity` с reauth-флагом.

### 5.2 Потоки данных и событий

```
share intent → ReceiveShareActivity.onCreate
    ↓
enqueueLinkDownloadWorker(url, accountId)  (новая ветка)
    ├── enqueue OneTimeWorkRequest c expedited=true
    ├── show LinkAutoDownloadProgressDialog в LiveData-режиме
    └── activity finish()-итcя сразу после первого progress-update,
          либо остаётся до user-cancel — TBD §6.1
    ↓
LinkDownloadWorker.doWork (новый single-mode)
    ├── setForeground(notification "Анализ страницы..")
    ├── coordinator.handle(url, accountId)
    ├── на каждый progress → setProgress + updateNotification
    ├── финал → updateNotification (sticky с filename)
    └── для SocialPreviewOnly → post-notification c action «Sign in»
    ↓
Пользователь в любой точке возвращается в приложение
    ├── если share уже завершён → showResultToast в текущей активити
    ├── если share ещё идёт → notification видна, опционально открыть progress
    └── cancel: либо в диалоге, либо в notification
```

### 5.3 Точки расширяемости

- Та же архитектура подойдёт для будущих long-running одиночных операций (например, fetch metadata перед сохранением).
- Notification channel — переиспользует существующий канал worker'а; не создаём новый.
- Cancel-flow — единый для UI и notification, гарантия одной точки отмены.

---

## 6. Открытые вопросы / Research items

1. **Когда закрывать transparent share-Activity?**
   - Вариант A: сразу после enqueue (мгновенно). Прогресс виден только через notification, диалог не появляется. Проще, но обед быстрых yt-dlp share становится менее уютным.
   - Вариант B: показать диалог, держать активити до first-success или first-failure, finish'ить через коллбэк. Сохраняет привычный UX для быстрых сценариев.
   - Вариант C: держать активити максимум N секунд (например, 4), затем finish если worker ещё не закончил. Компромисс.
   - **Рекомендация на старте:** Вариант B + watchdog в 4 секунды → если worker не успел, finish + foreground notification. Уточнить в tactical.

2. **Что показывать в progress-диалоге для WebView-стратегии?**
   - Сейчас этап «Анализ страницы..» индикативен на 22 секунды. Можно ли поделить на под-этапы (cookies → page load → JS eval → CDN download)? Это даст более осмысленную progress bar.
   - Open — отложить до tactical.

3. **Cancel atomicity.**
   - WorkManager.cancelWorkById устанавливает флаг отмены, но реально отмена видна только когда worker сам проверит `isStopped`. У dynamic-extractor 22-сек таймаут — может потребоваться вставить периодические `coroutineContext.ensureActive()` в горячих местах extraction-цепочки.
   - Open — проверить в tactical, сколько `yield()`-точек уже есть в координаторе.

4. **Параллельные share одного URL.**
   - Если пользователь нажмёт share дважды быстро на тот же URL — должны ли мы дедуплицировать? WorkManager API позволяет `enqueueUniqueWork` с политикой `KEEP` — да, дедуп через unique work name по URL-хешу.
   - Принято: использовать `enqueueUniqueWork` с `KEEP`, нормализуя URL по хосту+пути (отбрасывая утм-параметры).

5. **noLegal-only или main?**
   - Проблема воспроизводится во всех flavor'ах с share-receiver. Standard также может попасть в долгий extraction (TikTok тоже использует WebView fallback). Решение: main sourceSet, не flavor-gated.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Foreground service quota исчерпан (API 31+) | Низкая | Worker не получает foreground status, Android прибивает | `setExpedited` + RUN_AS_NON_EXPEDITED_WORK_REQUEST fallback (как у batch-варианта) |
| Notification permission не выдан (API 33+) | Средняя | Пользователь не видит ни диалог (если уже свернулся), ни уведомление | На share-flow гарантировать первый запрос permission'а в onboarding'е; если permission denied — оставить toast-результат в активити при возврате (S0156 §6.9) |
| Cancel race с финальным write | Средняя | Файл частично записан, MediaStore сохраняет «битый» | Worker делает атомарный write через temp + rename; cancel до rename = удалить temp |
| Прогресс-диалог не успевает подписаться на LiveData до первого emit'а | Низкая | Первая стадия не видна в UI, видна только в notification | Worker эмитит initial state до начала тяжёлой работы; диалог получает его при первой подписке |
| Регрессия для быстрых yt-dlp share (UX становится сложнее) | Средняя | Пользователь видит лишнее уведомление вместо привычного «share прошёл за 3 сек, toast, finish» | Watchdog (вариант 6.1B) и подавление notification на success ≤ 2 сек после старта (in-progress notification cancel + toast при возврате) |
| `enqueueUniqueWork(KEEP)` маскирует реальный retry | Низкая | Юзер думает «не сработало, нажму ещё раз», а второй share игнорируется | Уникальное имя — короткий TTL (например, 60 сек после завершения), либо REPLACE-политика на failed worker'е |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без новой пользовательской фичи как таковой — это исправление существующего failure-сценария. Запись пойдёт в:
- `dev/CHANGELOG.md` — implementation entry.
- `dev/FUNCTIONALITY.log` — `FIX` запись «share download survives backgrounding».
- `docs/FEATURES.md` не обновляется (см. CLAUDE.md правило «feature docs only для new user-visible capability»).

Если в ходе работы появится **notification как новая user-visible capability** (а не просто способ доставки результата) — потребуется обновление `docs/FEATURES.md` + `_RU` + `_UK` (capability «прогресс скачивания в уведомлениях»). Решение откладывается до tactical-фазы.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Worker как единственный execution surface для single-URL share.**

- **Решение:** перенести single-URL extraction-цепочку в `LinkDownloadWorker` (расширить существующий batch-вариант новым режимом single).
- **Альтернативы:** (а) держать `lifecycleScope` + использовать `Service.startForegroundService` поверх; (б) переключить activity на `FLAG_ACTIVITY_NO_HISTORY` + долгий keepalive через partial-wakelock.
- **Почему:** WorkManager — каноничный механизм long-running work в Android, уже используется для batch. Дублировать аналогичную инфраструктуру через Service — больше кода, выше риск конфликта с Android Doze.

**ADR-2: Активити остаётся transparent + блокирующий диалог на быстрых сценариях.**

- **Решение:** не убирать activity полностью, а сделать её лёгким UI-наблюдателем за worker'ом.
- **Альтернативы:** finish сразу после enqueue, всю UX отдать notification'у.
- **Почему:** регрессия UX на быстрых yt-dlp (3-5 сек) недопустима — пользователь привык видеть прогресс и кнопку отмены. Variant B (с watchdog) даёт «лучшее обоих миров».

**ADR-3: Notification — foreground-service, не background.**

- **Решение:** worker поднимает foreground-service-notification через `setExpedited` + `setForeground` API.
- **Альтернативы:** обычная (background) notification без service.
- **Почему:** на API 26+ Android прибивает worker'ы без foreground-status за десятки секунд. Текущий 22-сек бюджет dynamic-extractor'а слишком близко к этому порогу.

---

## 10. Связи с другими спеками

- **S0161 (Archived, Implemented)** — установил исходный lifecycleScope-подход; S0202 — его эволюция. После S0202 раздел «Architecture» в S0161 устаревает.
- **S0186 (BlockNeedUserTest)** — cascade-resilience; пересекается слабо: catch-all в координаторе остаётся в силе и в worker-варианте.
- **S0197 (Tactical)** — данный дефект backgrounding'а особенно болезнен для dynamic-extractor'а Threads/IG; обе фичи будут проверены на одном device-тесте.
- **S0156** — umbrella для noLegal flavor; share-receive — её часть.
- **S0174 (Broken)** — yt-dlp; быстрая ветка extraction'а, выгодополучатель быстрого UX, который worker не должен испортить.

---

## 11. Критерии готовности (strategic-level)

1. На устройстве: share Threads carousel → свернуть приложение через 1 сек → дождаться 30 сек → notification показывает «Сохранено N файлов» с кнопкой «Открыть».
2. На устройстве: share YouTube short → закрыть share-диалог свайпом → дождаться → notification с success или с понятной ошибкой.
3. На устройстве: share Instagram photo → быстро (≤ 3 сек завершения) → notification успевает скрыться, в активити показан toast.
4. На устройстве: share с активным cancel → нажать cancel в notification → worker корректно отменяется, в Downloads ничего недописано.
5. На устройстве: share того же URL дважды подряд → второй share игнорируется (deduplication через unique work name).
6. Регрессия: yt-dlp share, 3-сек завершение, активити остаётся видимой → пользователь видит привычный toast + finish без notification noise.
7. Регрессия: SocialPreviewOnly → notification «Sign in» action открывает `ReceiveShareActivity` с EXTRA_REAUTH_URL (поведение S0161 сохраняется).

---

## 12. Следующий шаг

`/spec-tech S0202` — создаст `PLAN/S0202_link-share-background-survival/INDEX.md` с фазами.
