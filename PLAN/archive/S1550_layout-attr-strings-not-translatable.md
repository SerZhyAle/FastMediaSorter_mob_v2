# Стратегическая спецификация: S1550 - Layout attribute literals in strings.xml carry no translatable="false"

**Ticket:** S1550
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-09
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - находка при исполнении S1420, 2026-08-09
**Tactical spec:** `PLAN/S1550_layout-attr-strings-not-translatable/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-09

**Текст:**

81 keys in app_v2/src/main/res/values/strings.xml (80) and strings_setup.xml (1) hold Android layout attribute literals rather than user-facing text, but carry no translatable="false". Examples measured 2026-08-09 with the seeder's own regex: dialog_file_info_tv*_lineSpacingMultiplier = "1.2" (44 keys), dialog_*_boxBackgroundMode = "outline", dialog_access_password_tilPassword_endIconMode = "password_toggle", dialog_filter_resource_chipGroupMediaType_endIconMode = "clear_text", dialog_network_discovery_rvHosts_layoutManager = "androidx.recyclerview.widget.LinearLayoutManager". Symptom: every bulk-translation pass over strings.xml offers these to the translator as if they were prose. A translated "outline" or a localised class name silently breaks the widget that reads the literal - it survives the build and fails at inflate time. They also inflate the untranslated-key counter that scripts/check_strings_localized.ps1 reports, so coverage never reaches zero honestly. Evidence: S1420 phase 03 step 03.2 - three independent translation agents each had to recognise and skip them by hand, which is exactly the judgement that will not survive the next pass. Not a mechanical sweep: the neighbouring _text keys in the same prefix ARE user-visible (dialog_color_picker_tvColorName_text = "Green", dialog_integration_test_chipAudio_text = "Audio"), so each of the 81 needs a per-key ruling. Open research: what marking translatable="false" does to the existing complete values-ru / values-uk entries for those keys and to the parity gate, and whether Android Lint's Untranslatable check then fires. Found while running S1420 phase 03; out of scope there because S1420 must not edit the strict values/ source and because changing eligibility mid-plan would move every remaining step's expected count.

**Захвачено во время:** S1420

---

## Last Audit

**2026-08-16 - предмет тикета отсутствует в дереве. Тикет закрывается без изменений кода.**

Постановка описывала 81 ключ в `values/strings.xml` и `strings_setup.xml`, чьи значения - литералы атрибутов Android. Прямое измерение сегодня:

- `grep -c 'name="[^"]*lineSpacingMultiplier"'` по `values/strings.xml` -> **0**. То же для `boxBackgroundMode`, `endIconMode`, `layoutManager` -> **0** каждый.
- Ключей с префиксом `dialog_file_info` (в постановке их 44) в файле нет вовсе -> **0**.
- Поиск по значениям, а не по именам, по **всем двадцати** файлам `values/strings*.xml`: строк со значением `1.2`, `outline`, `password_toggle`, `clear_text`, `filled` или `none` -> **0**.
- Сами литералы никуда не делись, но лежат там, где им место: `endIconMode`, `boxBackgroundMode` и прочие встречаются только как атрибуты в `res/layout/*.xml`.

То есть псевдо-строки удалены, а layout читает литералы напрямую. Симптом - «переводчику предлагают перевести `outline`» - воспроизвести не на чем, и счётчик непереведённых ключей ими больше не завышается.

Открытый вопрос §6 (что `translatable="false"` сделает с существующими `values-ru` / `values-uk` и с гейтом паритета) снимается вместе с предметом: помечать нечего.

**Вывод:** тикет не имеет предмета. Кода не менялось, гейтов не запускалось - менять нечего. Статус -> `Archived`.

---

## 1. Проблема

Постановка §0 описывала 81 строковый ключ, хранивший литералы атрибутов Android вместо пользовательского текста. На 2026-08-16 таких ключей в дереве нет (см. Last Audit), поэтому раздел сохранён как история находки, а не как действующая задача.

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

- **Related tickets:** S1420, S1190, S1195

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

1. **Что делает `translatable="false"` с уже полными `values-ru` / `values-uk`**
   - **Вопрос:** ломает ли пометка гейт паритета, если ключ уже переведён в строгих локалях
   - **Нужно выяснить:** поведение `scripts/check_strings_localized.ps1` и Android Lint `Untranslatable`
   - **Статус:** Open
2. **Какие из 81 ключа действительно непереводимы**
   - **Вопрос:** соседние `_text` ключи того же префикса - пользовательский текст, поэтому суффикс имени не является достаточным признаком
   - **Нужно выяснить:** пер-ключевое решение по всем 81
   - **Статус:** Open

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| <описание> | Низкая / Средняя / Высокая | <что сломается> | <как предотвратить> |

---

## 8. Влияние на пользователя (docs/FEATURES)

<По умолчанию: «Без изменений в docs/FEATURES.»>

---

## 9. Архитектурные решения (ADR)

<Если нет - «ADR нет - решение по устоявшимся паттернам проекта.»>

---

## 10. Связи с другими спеками

- **S1420** - обнаружил находку; продолжает переводить эти ключи как обычный текст, пока тикет не закрыт.
- **S1190** - завёл оснастку перевода и ADR-6.
- **S1195** - Android Lint: `MissingTranslation` выключен; `Untranslatable` затрагивается этим тикетом.

---

## 11. Критерии готовности (strategic-level)

<Нумерованный список. Наблюдаемые результаты, не архитектурные утверждения.>
