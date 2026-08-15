# Стратегическая спецификация: S1624 - Осиротевший строковый ключ network-monitor валит гейт

**Ticket:** S1624
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-13
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - обнаружено при выполнении S1329, фаза 00, 2026-08-13
**Tactical spec:** `PLAN/S1624_orphaned-network-monitor-string-key/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-13

**Захвачено во время:** S1329

**Текст:**

assert-unreferenced-strings goes red on an orphaned network-monitor string key. Observed 2026-08-13 during S1329 phase 00, on a full `scripts/quality/assert-fast-gates.ps1` run: `assert-unreferenced-strings.ps1` FAIL, `declared=2849 unreferenced=8 baseline=7 new=1 slack=0`, the new name being `network_monitor_local_ip_label`. The key is declared in all three maintained locales - `app_v2/src/main/res/values/strings.xml:3119` ("Local IP"), `values-ru/strings.xml:3083`, `values-uk/strings.xml:3068` - and nothing under `app_v2/src` references it. Unrelated to S1329, which touches no strings. Dedup checked: `search.ps1` on "unreferenced", "orphan" and "network_monitor" returns no ticket covering it. S1617 (network-monitor-readability-and-diagnostics, Approved) owns that screen and a Local IP row would sit squarely inside its "diagnostics" half, but its spec does not mention the label, so the key is an orphan rather than a pre-landed asset for it. Needs a decision: wire the row into the network monitor (likely folded into S1617) or remove the key in all three locales via `set-android-string.ps1 -Action remove`. Until then the fast-gates batch cannot return exit 0 for anyone.

---

## 0a. Исход (2026-08-14) - дубликат, архивирован в пользу S1629

Тот же ключ, тот же вывод гейта, та же находка: S1629 (`bugfix-unreferenced-network-monitor-label`) заведён на день позже при прогоне гейтов в S1627 и описывает ровно этот дефект. Работа выполнена там - расследование заполнено, ключ удалён во всех тринадцати локалях.

Почему оба тикета существовали: каждый дедуп-поиск искал по имени ключа с подчёркиваниями (`network_monitor`), а слаги тикетов пишутся через дефисы (`network-monitor`), поэтому ни один не нашёл другого. Ловушка запомнена отдельно.

Этот тикет архивирован как дубликат; ведущий - S1629.

## 1. Проблема

<2–4 предложения. Что сломано или чего не хватает? Эффект на пользователя. Область - модуль/feature-path без имён классов.>

---

## 2. Цели

<Нумерованный список наблюдаемых улучшений. «Что станет возможным / что перестанет происходить».>

**Non-goals:**

- <что явно вне объёма>

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

<Нумерованный список желаемого, но необязательного к первой итерации.>

### 3.2 Жёсткие ограничения

- **Flavor:** <затронутые варианты сборки>
- **API level:** <минимальный уровень Android или «без API-специфики»>
- **Wear OS:** <затрагивается или нет>
- **Производительность:** <бюджет CPU/память/батарея, если критично>
- **Совместимость данных:** <форма миграции без номера версии Room>
- **Локализация:** EN/RU/UK - всегда обязательно, или уточнение.
- **Доступность:** <TalkBack, touch target, не-цветовое отличие - если фича визуальная>

### 3.3 Owner inputs (Approval gate)

<Заполняется при переходе Draft → Approved (через /spec или /spec-update). В скелете оставить пустым, кроме обязательного поля ниже.>

- **Related tickets:** S1617 (владеет экраном network monitor), S1568 (тикет, заведший гейт assert-unreferenced-strings)

---

## 4. Контекст текущей архитектуры

<1–2 абзаца. Какие слои/компоненты отвечают за затронутую область. Почему сейчас нельзя решить проблему из §1. Без перечисления классов.>

---

## 5. Предлагаемый подход

<Архитектурный уровень: какие роли появятся, откуда читают / куда пишут, что меняет ответственность. Имена классов, файлов, методов - запрещены.>

### 5.1 Основные столпы / модули

<Крупные логические блоки. Каждый - подглава с целью и требованиями.>

### 5.2 Потоки данных и событий

<Высокоуровневая схема. «UI → слой применения → кэш → ..». Без имён методов.>

### 5.3 Точки расширяемости

<Что должно остаться открытым к расширению.>

---

## 6. Открытые вопросы / Research items

1. **Куда девать ключ**
   - **Вопрос:** проводить строку в экран сетевого монитора как строку диагностики или удалить её во всех трёх локалях?
   - **Варианты:** свернуть в S1617 как ряд «Local IP»; удалить через `set-android-string.ps1 -Action remove`.
   - **Нужно выяснить:** хотел ли владелец видеть локальный IP на экране монитора.
   - **Статус:** Open

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| <описание> | Низкая / Средняя / Высокая | <что сломается> | <как предотвратить> |

---

## 8. Влияние на пользователя (docs/FEATURES)

<По умолчанию: «Без изменений в docs/FEATURES.» - если только этот спек не вводит способность, которую пользователь воспринял бы как новую фичу.>

---

## 9. Архитектурные решения (ADR)

<Если нет - «ADR нет - решение по устоявшимся паттернам проекта.»>

---

## 10. Связи с другими спеками

Связей нет.

---

## 11. Критерии готовности (strategic-level)

<Нумерованный список. Наблюдаемые результаты, не архитектурные утверждения.>
