# Стратегическая спецификация: S0462 - TesseractManager: нет таймаута при скачивании .traineddata

**Ticket:** S0462
**Status:** Archived
**Priority:** 40
**Date:** 2026-06-16
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-16
**Tactical spec:** `PLAN/S0462_bugfix-tesseractmanager-no-network-timeout/`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Захваченный материал (inbox)

> Материал перенесён в §1–§6; секция сохранена для истории.

**Захвачено:** 2026-06-16

**Текст:**

TesseractManager downloads .traineddata via URL.openStream() with no connect/read timeout — coroutine hangs indefinitely on slow network (TesseractManager.kt:125). Unlike the delivery downloader which uses OkHttp with 15s timeouts (RealDeliverableSetDownloader.kt:46-49), this path has no cancellation support.

Обнаружено во время исследования S0461 (OCR PaddlePaddle payload missing).

**Захвачено во время:** S0461

---

## 1. Проблема

`checkAndDownloadData()` в TesseractManager скачивает `.traineddata` (fast-модели Tesseract) через `URL.openStream()` без таймаута подключения и чтения. На медленной сети или при зависшем сервере корутина блокируется на IO-диспетчере неограниченно долго, что замораживает инициализацию OCR. `TesseractModelManager` (загрузчик best-моделей) уже использует `HttpURLConnection` с 15 с таймаутами — fast-загрузчик отстал при том же риске зависания.

---

## 2. Цели

1. Добавить таймаут подключения и чтения (15 с каждый) к загрузке `.traineddata`.
2. Привести паттерн fast-загрузчика в соответствие с `TesseractModelManager`.
3. Гарантировать `connection.disconnect()` в `finally` для освобождения ресурсов.

**Non-goals:**

- Прогресс-репортинг для fast-загрузки (fast-модели < 5 MB — прогресс нецелесообразен).
- Retry-логика (ответственность вызывающего кода или UX-уровня).
- Изменение значения таймаута относительно 15 с.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Использовать тот же таймаут 15 с, что в `TesseractModelManager` (паритет).
2. Явный `connection.disconnect()` в `finally`-блоке (паттерн проекта).

### 3.2 Жёсткие ограничения

- **Flavor:** ocrEnabled source set (standard, legacy, noLegal)
- **API level:** без API-специфики
- **Wear OS:** не затрагивается
- **Производительность:** вызов уже на `Dispatchers.IO` — таймаут не блокирует main thread
- **Совместимость данных:** нет
- **Локализация:** строки не затрагиваются
- **Доступность:** не применимо

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0461 (OCR payload missing — обнаружено в том же исследовании)

---

## 4. Контекст текущей архитектуры

`TesseractManager` (ocrEnabled source set) отвечает за fast-модели (tessdata_fast, < 5 MB). Скачивает через `URL.openStream()` без таймаута. `TesseractModelManager` (src/main) отвечает за best-модели (tessdata_best, 10–15 MB) и уже использует `HttpURLConnection` с `connectTimeout = 15000` / `readTimeout = 15000`. Оба класса в одном пакете `helpers`, что делает паритет паттерна очевидным требованием.

---

## 5. Предлагаемый подход

Заменить `URL(..).openStream()` в `checkAndDownloadData()` на `HttpURLConnection` с `connectTimeout = 15_000`, `readTimeout = 15_000`, `instanceFollowRedirects = true` и `connection.disconnect()` в `finally`. Остальная логика метода (включая очистку частичного файла при ошибке) остаётся неизменной.

### 5.1 Основные столпы / модули

- `TesseractManager.checkAndDownloadData()` — единственная точка изменения.

### 5.2 Потоки данных и событий

- Вызов остаётся на `Dispatchers.IO` — блокирующий `HttpURLConnection` безопасен.
- `HttpURLConnection.inputStream` читается в `FileOutputStream` — та же логика копирования.
- При `SocketTimeoutException` / любом `IOException` — очистка частичного файла и `false`.

### 5.3 Точки расширяемости

- Таймаут вынести в константу `companion object` по паттерну `TesseractModelManager`.

---

## 6. Открытые вопросы / Research items

Все вопросы закрыты:

- Таймаут: 15 с (паритет с `TesseractModelManager`, строка 135–136).
- Отмена корутины: обеспечивается `Dispatchers.IO` + `SocketTimeoutException`; `withTimeout` не нужен.

---

## 7. Риски

- **Агрессивный таймаут на медленной сети:** Средняя вероятность. Fast-модели < 5 MB — 15 с достаточно даже на медленном 3G для connect + первый read. Митигация: тот же параметр используется в `TesseractModelManager` без нареканий.

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES.

---

## 9. Архитектурные решения (ADR)

ADR нет — изменение по устоявшемуся паттерну проекта.

---

## 10. Связи с другими спеками

- S0461 — обнаружено в процессе исследования

---

## 11. Критерии готовности (strategic-level)

1. `checkAndDownloadData()` использует `HttpURLConnection` с `connectTimeout` и `readTimeout` = 15 с.
2. `connection.disconnect()` вызывается в `finally`.
3. Частичный файл удаляется при любом исключении (включая `SocketTimeoutException`).
4. Проект компилируется без ошибок.

---

## 12. Ссылка на тактическую спецификацию

`PLAN/S0462_bugfix-tesseractmanager-no-network-timeout/`

---

## Last Audit

**Date:** 2026-06-16
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 10 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Manual / on-device

- No on-device verification required (internal network timeout; no visible UX change).
