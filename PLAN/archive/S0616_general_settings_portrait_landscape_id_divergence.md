# Стратегическая спецификация: S0616 - Расхождение view-id между portrait и landscape в фрагменте общих настроек

**Ticket:** S0616
**Status:** Archived
**Priority:** 70
**Date:** 2026-06-22
**Tier:** 2 - Easy
**Roadmap entry:** Ad-hoc - захвачено при research S0609 (2026-06-22)
**Tactical spec:** будет создан через `/spec-tech`

> **Scope:** STRATEGIC. Draft-инбокс.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-22
**Захвачено во время:** research S0609 (инвентаризация раскладок настроек)

**Симптом:** в фрагменте общих настроек набор view-id различается между portrait и landscape. При ViewBinding это даёт nullable-поля биндинга, и при доступе к ним в «не той» ориентации возможен NPE либо настройка молча становится недоступной.

**Доказательства:**

- `actvSyncInterval` / `tilSyncInterval` присутствуют только в `app_v2/src/main/res/layout-land/fragment_settings_general.xml` (2 совпадения), в `app_v2/src/main/res/layout/fragment_settings_general.xml` отсутствуют (0).
- `rowCompactElements` присутствует только в portrait `app_v2/src/main/res/layout/fragment_settings_general.xml` (1 совпадение), в landscape отсутствует (0).

**Что выяснить при доработке:**

- Действительно ли `GeneralSettingsViewSetupHelper` обращается к этим id без null-проверки (реальный краш) или поля просто nullable и безопасно игнорируются.
- Какой настройке соответствует каждый id и в какой ориентации она должна быть доступна.
- Привести наборы id к паритету между portrait и landscape (либо явная null-безопасность по дизайну).

**Связь:** обнаружено в ходе S0609 (многоколоночная раскладка настроек), но это самостоятельный дефект, вне объёма S0609.

**Вложения:** нет.

---

## Корректировка диагноза (2026-06-22, F1-research)

Исходная гипотеза «NPE-риск» **опровергнута**. Все обращения к расходящимся id - null-safe:

- `GeneralSettingsObserversHelper`: `binding.rowCompactElements?.isChecked`, `binding.actvSyncInterval?.let { .. }`.
- `GeneralSettingsViewSetupHelper`: `binding.rowCompactElements?.let { .. }`, `binding.actvSyncInterval?.let { .. }`.

Краша нет: в ориентации, где view отсутствует, код безопасно no-op'ит.

Реальная (минорная) проблема - **orientation-паритет настроек**:

- `rowCompactElements` (тумблер «компактные элементы») присутствует только в portrait → в ландшафте настройка недоступна.
- `actvSyncInterval`/`tilSyncInterval` (интервал фоновой синхронизации) присутствуют только в landscape → в портрете настройка недоступна.

Пользователь не может задать часть настроек в одной из ориентаций.

**Дизайн-неоднозначность (требует решения владельца):** не ясно, является ли orientation-locking намеренным (например, sync-interval добавлен только в плотную ландшафтную раскладку сознательно) или это случайная недоделка. Правка в сторону паритета затрагивает portrait-UX (нужно перенести containerSync с его show/hide-логикой и label-TextView в портрет), что не тривиально и может противоречить замыслу.

**Вопрос владельцу:** сделать обе настройки доступными в обеих ориентациях (паритет), или orientation-locking оставить как есть? Если паритет - подтвердить, что sync-interval уместен в портретной System-секции.

---

## Решение владельца (quiz, 2026-06-24)

Выбран **полный паритет**: обе настройки должны быть доступны в обеих ориентациях.

- `rowCompactElements` (тумблер «компактные элементы») добавить в landscape-раскладку.
- Dropdown интервала синхронизации (`tilSyncInterval`/`actvSyncInterval`) добавить в portrait-раскладку, в `containerSync` (та же System-секция, рядом с enable-background-sync / sync-now). Sync-interval в портретной System-секции подтверждён как уместный.
- После правки наборы view-id в `layout/` и `layout-land/fragment_settings_general.xml` должны совпадать; nullable-обращения в `GeneralSettingsObserversHelper` / `GeneralSettingsViewSetupHelper` могут остаться (безвредны), но обе ветки теперь всегда исполняются.

### Quiz decisions (2026-06-24)
- Привести расхождение orientation-наборов к паритету или оставить orientation-locking? → Полный паритет (обе настройки нужны в обеих ориентациях; orientation-locking признан случайной layout-недоделкой, а не дизайном).

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0609 (захват), S0618 (landscape-плотность Display-секции)
- **UI placement:** rowCompactElements в landscape - отдельной строкой сразу после rowDeviceProfile (зеркалит portrait); sync-interval dropdown в portrait - внутри containerSync/layoutSyncControls перед btnSyncNow (зеркалит landscape)
- **UI visibility/fallback:** обе настройки видны в обеих ориентациях; биндинг-поля становятся non-null, существующие `?.let`/`?.` ветви в хелперах исполняются без изменений кода

---

## Plan (Simple)

Чистая XML-правка паритета. Ни Kotlin, ни schema, ни DI. Хелперы (`GeneralSettingsViewSetupHelper`, `GeneralSettingsObserversHelper`) уже null-safe и заработают в обеих ориентациях автоматически.

### Phase 01 - Layout parity

- Add `rowCompactElements` (`SettingsToggleRow`, same attrs as portrait line 64-73) to `app_v2/src/main/res/layout-land/fragment_settings_general.xml`, as a standalone `match_parent` row immediately after `rowDeviceProfile`, before the three-toggle landscape row.
  - Verification: `grep -c rowCompactElements layout-land/fragment_settings_general.xml` -> `1`.
- Add the sync-interval dropdown (`tilSyncInterval` `TextInputLayout` + `actvSyncInterval` `AutoCompleteTextView`, mirroring landscape line 573-577) into `app_v2/src/main/res/layout/fragment_settings_general.xml` inside `layoutSyncControls`, before `btnSyncNow`.
  - Verification: `grep -c actvSyncInterval layout/fragment_settings_general.xml` -> `1`.
- Verification (parity): id-set of `tilSyncInterval`/`actvSyncInterval`/`rowCompactElements` present in BOTH `layout/` and `layout-land/`.

### Phase 02 - Build gate

- `.\a.ps1 dq` (standard debug) -> PASS.

---

## Last Audit

**Date:** 2026-06-24 (via /spec-all)
**Verdict:** Verified

**Implemented (full parity):**

- `app_v2/src/main/res/layout-land/fragment_settings_general.xml`: added `rowCompactElements` (`SettingsToggleRow`) as a standalone row after `rowDeviceProfile`, mirroring portrait.
- `app_v2/src/main/res/layout/fragment_settings_general.xml`: added the sync-interval dropdown (`tilSyncInterval` + `actvSyncInterval`) into `layoutSyncControls` before `btnSyncNow`, mirroring landscape.
- No Kotlin logic change: `GeneralSettingsViewSetupHelper` / `GeneralSettingsObserversHelper` already access both ids null-safely; binding fields are now non-null in both orientations and the existing branches run.
- Settings docs (Rule 22): `actvSyncInterval` surfaced as a new manifest key - regenerated `docs/settings/settings-manifest.json`, added its `docs/settings/settings-annotations.json` entry (EN/RU/UK), re-rendered `docs/SETTINGS_REFERENCE*.md`. `assert-settings-doc-sync.ps1` PASS.

**Parity check:** `rowCompactElements`, `tilSyncInterval`, `actvSyncInterval` each present 1x in BOTH `layout/` and `layout-land/`.

**Device evidence (emulator-5554, portrait):**

- General tab: Compact-elements toggle visible; sync-interval dropdown (180) visible beside Sync Now in one horizontal row - no overflow.
- Both wiring flows fired (binding fields non-null in portrait), confirming the previously-absent sync-interval path now executes.

**Residual (manual, low risk):** landscape rendering not exercised - this xlrg photo-frame emulator profile keeps the Settings activity portrait-locked, so `layout-land` was not inflated. The landscape addition is a single full-width toggle via the identical working pattern; build + ViewBinding generation confirm soundness. Confirm visually on a phone/profile that rotates Settings if desired.
