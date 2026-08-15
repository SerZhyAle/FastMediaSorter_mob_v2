# Стратегическая спецификация: S0175 — noLegal: апгрейд NewPipeExtractor

**Ticket:** S0175
**Status:** Verified
**Priority:** 50
**Date:** 2026-05-12
**Tier:** 1 — Quick Win
**Roadmap entry:** Ad-hoc — ресёрч S0156, итерация 2026-05-12
**Epic:** S0156 — noLegal Capability Surface Audit
**Tactical spec:** `PLAN/S0175_nolegal-newpipe-version-bump/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

noLegal flavor использует NewPipeExtractor `v0.24.0` (2023 год). С тех пор YouTube Innertube API менялся несколько раз, SoundCloud ротирует client_id, Bandcamp обновил API. Версия `v0.24.0` может давать сбои на реальных URL без очевидной причины. Актуальные релизы NewPipe содержат исправления этих breakage. Кроме того, Odysee (LBRY) поддерживается в NewPipeExtractor, но не включён в allowlist сервисов.

---

## 2. Цели

1. Апгрейд NewPipeExtractor до актуальной стабильной версии (`v0.26.1`).
2. Исправления YouTube/SoundCloud/Bandcamp из новых версий применяются автоматически.

**Non-goals:**

- Добавление Odysee/LBRY: upstream `ServiceList` не содержит Odysee ни в одной версии до `v0.26.1` включительно — недостижимо в рамках этого тикета.
- Добавление Instagram, TikTok, Facebook — покрывает S0174.
- Изменение архитектуры extraction pipeline.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Проверить release notes последних версий на наличие breaking changes в public API.

### 3.2 Жёсткие ограничения

- **Flavor:** `noLegal` только.
- **API level:** minSdk 26.
- **Wear OS:** не затрагивается.
- **APK size:** без ограничений.
- **Лицензии:** GPL-3.0 — уже применяется.
- **Совместимость данных:** без изменений Room.
- **Локализация:** нет новых строк.

---

## 4. Контекст текущей архитектуры

NewPipeExtractor используется как зависимость `noLegalImplementation` через JitPack. Существующий wrapper-компонент вызывает `StreamExtractor` для получения video URL и метаданных; fallback на CDN-download компонент для финальной загрузки. Смена версии — изменение одной строки в gradle с последующей проверкой совместимости API.

---

## 5. Предлагаемый подход

Обновить версию зависимости. Проверить API-совместимость: изменились ли сигнатуры методов `StreamExtractor`, `PlaylistExtractor`, типы результатов. Добавить `Odysee.serviceId` в allowlist. Запустить существующие unit-тесты.

### 5.1 Основные столпы

**A — Version bump**
Одна строка в gradle. JitPack разрешит новый тег автоматически.

**B — API compatibility check**
Сравнить public API тех методов, которые использует wrapper. Если breaking — минимальный адаптер.

**C — Odysee allowlist**
Добавить `ServiceList.Odysee.serviceId` в allowlist констант. Проверить, что `probe(odysee_url)` → `Applicable`.

### 5.2 Потоки данных и событий

Без изменений — тот же путь `probe() → resolveService() → StreamExtractor → CDN download`.

### 5.3 Точки расширяемости

Нет изменений в точках расширяемости.

---

## 6. Открытые вопросы / Research items

1. **Breaking changes в NewPipeExtractor после v0.24.0**
   - **Вопрос:** Есть ли breaking changes в public API, которые затрагивают текущий wrapper?
   - **Ответ (resolved):** v0.25.0 удалил `DateWrapper(Calendar)` конструкторы (мы не используем); v0.26.0 изменил тип возврата `getMediaCapabilities()` (мы не вызываем). Wrapper не затронут — адаптация не требуется.
   - **Статус:** Closed.

2. **Odysee в ServiceList**
   - **Вопрос:** Поддерживается ли Odysee в NewPipeExtractor?
   - **Ответ (resolved):** Upstream `ServiceList` v0.26.1 содержит только YouTube, SoundCloud, MediaCCC, PeerTube, Bandcamp. Odysee отсутствует.
   - **Статус:** Closed — цель §2 удалена как недостижимая.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Breaking API change в новой версии | Низкая | Compile error | Адаптировать wrapper; обычно минимальные изменения |
| JitPack build недоступен для нового тега | Низкая | Gradle sync fail | Использовать commit hash вместо тега |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES — улучшение надёжности существующей функции, не новая функция.

---

## 9. Архитектурные решения (ADR)

ADR нет — решение по устоявшимся паттернам проекта.

---

## 10. Связи с другими спеками

- **S0156** — родительский epic.
- **S0174** — независим; можно делать параллельно. S0174 покроет платформы, которые NewPipe не поддерживает.

---

## 11. Критерии готовности (strategic-level)

1. `assembleNoLegalDebug` выходит с кодом 0 после bump до `v0.26.1`.
2. Нет unresolved symbol ошибок из `org.schabi.newpipe.extractor.*`.
3. Compile warnings, связанные с NewPipeExtractor API, устранены (если появились).

---

## 12. Тактическая спецификация

`PLAN/S0175_nolegal-newpipe-version-bump/INDEX.md`

---

## Last Audit

**Date:** 2026-05-12
**Mode:** full
**Flags:** —
**Outcome:** Verified
**Counts:** PASS 13 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Manual / on-device

- [ ] Smoke: open a YouTube URL in noLegal build → stream resolves (regression test for bump).
- [ ] Smoke: open a SoundCloud URL → stream resolves.
