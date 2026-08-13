# Стратегическая спецификация: S0723 - Кодификация протокола аудита в правилах проекта

**Ticket:** S0723
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-26
**Tier:** 4 - Strategic (ad-hoc)
**Roadmap entry:** Ad-hoc - дочерний тикет S0714 (принятие Code Audit Protocol)
**Umbrella:** S0714

> **Scope:** STRATEGIC. Цели и объём кодификации правил. Точные формулировки правил - на этапе `/spec-tech`/`/spec-dev`.

---

## 0. Источник

Принуждение протокола `docs/CODE_AUDIT_PROTOCOL.md` на уровне правил разработки, чтобы новый код проходил аудит by construction. Затрагивает `CLAUDE.md`, `AGENTS.md`, `.github/copilot-instructions.md` и dev-доки.

## 1. Проблема

Протокол вводит понятия и правила, которых нет в правилах проекта: таксономия серьёзности P0-P3, симметрия `register/unregister`, запрет main-thread Room, требование проверять reflection/DI/манифесты на минифицированной сборке, дисциплина ExoPlayer/Glide-владения. Пока они только в `docs/CODE_AUDIT_PROTOCOL.md`, агенты и ревью на них не опираются - правила и документ расходятся.

## 2. Цели

1. Внести в `CLAUDE.md` (и синхронно `AGENTS.md` -> `.github/copilot-instructions.md`, по правилу синка строжайший побеждает) ссылку на `docs/CODE_AUDIT_PROTOCOL.md` как обязательную при триггерах аудита.
2. Кодифицировать правила: таксономия серьёзности находок; симметрия `register/unregister`/`addListener/removeListener`; запрет main-thread Room; обязательная проверка reflection/DI/манифестов на release/minified-сборке (увязать с Rule 20); дисциплина владения ExoPlayer/Glide.
3. Прошить триггеры аудита (новый менеджер/воркер/репозиторий, изменения lifecycle/корутин/Room/плеера/старта/DI) в подходящий раздел правил/скиллов.
4. Согласовать с существующими разделами (Strict Rules, Neuroslop Rule 19, Dead-weight Rule 20), не дублируя и не конфликтуя.

**Non-goals:** реализация гейтов/тулинга (S0720-S0722); сам аудит кода (S0715-S0719).

## 3. Объём и ограничения

- Файлы правил: `CLAUDE.md`, `AGENTS.md`, `.github/copilot-instructions.md`; dev-доки по необходимости.
- Синхронность набора правил для не-Claude агентов обязательна (порядок импорта `CLAUDE.md` -> copilot -> prompt).
- Без раздувания: правила - лаконичные, со ссылкой на протокол как на полный текст; одна идея на пункт.
- Без time-estimates в правилах/спеке.

## 4. Критерии приёмки

- [x] `CLAUDE.md` §13 ссылается на `docs/CODE_AUDIT_PROTOCOL.md` и кодирует таксономию P0-P3 + новые правила (listener-симметрия, main-thread Room, R8/reflection-на-minified, ExoPlayer/Glide-владение, concurrency main-safety).
- [x] `AGENTS.md` §8 и `.github/copilot-instructions.md` §7 синхронизированы (сжатая зеркальная форма; строжайший CLAUDE.md канонический).
- [x] Триггеры аудита (новый менеджер/воркер/репозиторий, lifecycle/корутины/Room/плеер/старт/DI/R8) прошиты в раздел во всех трёх файлах.
- [x] Нет конфликта/дублирования с Rule 19/20/18 и Strict Rules - правила ссылаются на протокол ("extends Rule 18/20", "do not duplicate the protocol text - link to it").
- [x] Изменения чисто аддитивные markdown-секции; типографика контролируется (плоский дефис/`->`, без `...`/em-dash); Doc-рунг (grep-подтверждение контента во всех трёх).

## 5. Связанные тикеты

- S0714 (зонтик).
- S0720, S0721, S0722 (тулинг, на который ссылаются кодифицированные правила).
- S0715-S0719 (аудит-проходы, чьи правила кодифицируются).

## Last Audit

**Date:** 2026-06-26
**Mode:** full (strategic, docs)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 5 · WARN 0 · FAIL 0

Протокол `docs/CODE_AUDIT_PROTOCOL.md` кодифицирован в трёх rule-файлах:

- `CLAUDE.md` §13 "Code Audit Protocol" - канонический строгий набор: ссылка на полный протокол, список audit-триггеров, таксономия P0-P3, listener/observer/receiver-симметрия на симметричных lifecycle-краях, Room main-safety (`allowMainThreadQueries` banned, DAO suspend/Flow, `@Transaction`/`withTransaction`), concurrency main-safety, ExoPlayer single-owner + release-контракт, Glide decode-at-size, R8/reflection-на-minified.
- `AGENTS.md` §8 и `.github/copilot-instructions.md` §7 - сжатые зеркала (порядок импорта `CLAUDE.md` -> copilot -> prompt, строжайший побеждает).
- Без дублирования: правила ссылаются на протокол как на полный текст; явные привязки "extends Rule 18/20"; не конфликтуют с Rule 19 (neuroslop) / 20 (dead-weight) / Strict Rules.

Doc-рунг: grep-подтверждение контента во всех трёх файлах. Чисто аддитивные секции, типографика чистая (плоский дефис, без `...`/em-dash).
