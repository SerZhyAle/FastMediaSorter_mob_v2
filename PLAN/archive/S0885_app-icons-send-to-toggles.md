# S0885 - Recognizable app icons on "Send file to.." toggles (already delivered by S0838)

**Status:** Archived
**Priority:** 50
**Date:** 2026-07-02
**Tier:** 3 - Moderate (ad-hoc)

<!-- resolved-against-code by /spec-all - 2026-07-04: capability already shipped via S0838 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

> Настройки - Плеер - в группе "Команды отправить файл в.." .У тогглеров программ Телеграмм, Вотсапп, Инстаграмм, Kepp Notes символ тоглера одинаковый. надо сделать узнаваемые символы приложений. копирайт игнорируем

---

## Goal (RU)

В разделе настроек Playback, в группе "Команды отправить файл в..", у тогглеров приложений (Telegram, WhatsApp, Instagram, Keep, ..) должен быть узнаваемый значок каждого приложения, а не один общий символ на всех.

---

## Итог: уже реализовано (S0838)

Запрошенная возможность уже присутствует в дереве - её доставил **S0838** ("send-to-icons-unify-settings-and-menus", Archived, closed 2026-07-01), который унифицировал разрешение иконок между настройками и рантайм-меню «Отправить в..».

Каждый тогглер строится динамически на один зарегистрированный `ShareTarget` (S0452), и его иконка уже разрешается по-приёмнику - см. `PlaybackSettingsFragment.setupSendCommandsGroup()`:

- Сразу показывается собственный глиф приёмника (`target.iconRes`), а не общий на всех значок (S0838, строка ~305).
- Затем вне главного потока разрешается и подставляется значок пусковой иконки установленного приложения через `ShareTargetIconResolver.resolveIcon(t)` (S0474 + S0838, строки ~339-352) - самый узнаваемый символ.

Подход через рантайм-разрешение пусковой иконки установленного приложения снимает и вопрос копирайта (никаких зашитых брендовых литералов - ADR-5, "avoids hardcoded brand literals"), то есть решает задачу чище, чем предполагала исходная заметка ("копирайт игнорируем").

Fallback: для логического или не установленного приёмника остаётся его собственный объявленный глиф (`target.iconRes`) - тоже различимый per-receiver, не общий.

---

## Last Audit

**Date:** 2026-07-04 - Verified (already delivered by S0838; no new code).

Evidence (all in current tree):
- `PlaybackSettingsFragment.setupSendCommandsGroup()` builds one `SettingsToggleRow` per registered `ShareTarget` (S0452) and calls `target.iconRes?.let { setIcon(it) }` (per-receiver glyph, not a shared icon).
- Same method resolves the installed-app launcher icon off the main thread via `shareTargetIconResolver.resolveIcon(t)` and upgrades each row in place (`row.setIcon(icon)`), matching the runtime «Send to..» menus (`SendToMenuManager`, S0459/S0478).
- `ShareTargetIconResolver` is the single resolver shared by settings and menus (injected in both `PlaybackSettingsFragment` and `SendToMenuManager`).
- Predecessor ticket: `S0838` (Archived) - slug "send-to-icons-unify-settings-and-menus".

Verdict: duplicate of shipped S0838. No implementation, no strings, no drawables required. Closed Verified against code.
