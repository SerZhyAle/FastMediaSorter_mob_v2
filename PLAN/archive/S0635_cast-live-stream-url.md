# Стратегическая спецификация: S0635 - Chrome Cast для live-стримов (прямой URL)

**Ticket:** S0635
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-22
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-22
**Tactical spec:** `PLAN/S0635_cast-live-stream-url/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Draft-инбокс. Доработать через `/spec` / `/spec-update`.

> **Архивирована как дубликат S0632** (`cast-live-stream-direct-url`, 2026-06-23). S0632 уже реализует ровно
> этот фикс: `CastMediaManager.resolveAndSend()` классифицирует путь через `castStreamResolver.resolve()`,
> и для `CastStreamDecision.Direct` (live HLS/DASH/HTTP) вызывает `loadStreamOnReceiver()` - прямой URL на
> ресивер (`STREAM_TYPE_LIVE`, без `LocalCastProxyServer` и без скачивания); `UnsupportedProtocol` (rtsp)
> даёт сообщение "can't be cast". §6 research-вопрос (тянет ли ресивер прямой HLS-URL) покрыт device-тестом
> S0632. На момент архивации S0632 в `BlockNeedUserTest`. Дальнейшая работа ведётся в S0632.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-22 (находка при research S0631)

**Симптом:** кнопка Chrome Cast показывается для live-видео-трансляции, но не кастит - тихий error-toast.

**Доказательство:** `CastMediaManager.resolveAndSend()` (`ui/player/helpers/CastMediaManager.kt:235-261`)
классифицирует `http://`/`https://`-путь как локальный файл (`isLocalFile = true`), затем
`File(path).exists()` = `false` для URL -> отдаёт ошибку. Проброс через `LocalCastProxyServer` - не тот
путь для прямого live-HTTP-URL.

**Направление фикса:** в `resolveAndSend` детектировать `http://`/`https://` (live-стрим) и передавать
URL напрямую на Cast-ресивер как `MediaItem.fromUri(uri)` без прокси-сервера.

**Связь:** предпосылка к полезности Cast-кнопки в стрим-профиле S0631 (там кнопка остаётся по запросу
владельца, но без этого фикса не кастит).

**Вложения:** нет.

---

## 1. Проблема

Для live-видео-трансляции кнопка Chrome Cast присутствует, но трансляция на ресивер не уходит:
обработчик принимает HTTP(S)-URL потока за локальный файл и падает в тихую ошибку.

---

## 2. Цели

1. Нажатие Cast на live-видео-трансляции отправляет поток на Chromecast-ресивер по прямому URL.

**Non-goals:**

- UI-профиль контролов трансляции (S0631).
- Устойчивость воспроизведения потока (S0634).

---

## 3. Пожелания и ограничения

### 3.2 Жёсткие ограничения

- **Flavor:** где `SUPPORT_CAST` (везде кроме vr) и `SUPPORT_STREAMS`.
- **API level:** без новой API-специфики.
- **Локализация:** EN/RU/UK для любых новых строк ошибок.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0631 (UI-профиль, использует Cast-кнопку), S0634 (robustness).

---

## 6. Открытые вопросы / Research items

1. Поддерживает ли используемый Cast-ресивер прямой HLS-URL без локального прокси (CORS, формат) -
   проверить на реальном Chromecast.
   - **Статус:** Open

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Ресивер не тянет прямой HLS-URL | Средняя | Cast по-прежнему не работает для части потоков | Проверить на устройстве; fallback-сообщение |

---

## 10. Связи с другими спеками

S0631 (UI-профиль трансляций), S0634 (robustness). От них не блокируется.
