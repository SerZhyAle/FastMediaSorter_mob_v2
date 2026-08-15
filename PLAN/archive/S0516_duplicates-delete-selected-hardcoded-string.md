# Стратегическая спецификация: S0516 - захардкоженная строка "Delete Selected" на FAB в fragment_duplicates

**Ticket:** S0516
**Status:** Archived
**Priority:** 45
**Date:** 2026-06-18

> **Scope:** Compact spec (Simple path). Localization fix for the duplicates screen.

---

## Goal

Устранить разрыв локализации на экране дубликатов. Исходный симптом (захардкоженный литерал на FAB «Delete Selected») к моменту проработки уже устранён - текст FAB задаётся из `duplicate_fab_delete`. Но проверка разметки и парности строк вскрыла два оставшихся проявления той же проблемы: захардкоженный подзаголовок и недостающие UK-переводы.

## Acceptance criteria

1. В `fragment_duplicates.xml` нет захардкоженных user-facing литералов - подзаголовок вынесен в `@string/duplicate_select_resources_subtitle`.
2. Новый ключ присутствует в EN/RU/UK (lockstep).
3. Все `duplicate_` ключи присутствуют в EN/RU/UK (закрыт UK-пробел: `duplicate_delete_title`, `duplicate_delete_message`, `duplicate_groups_summary`).
4. `processStandardDebugResources` проходит; `check_strings_localized.ps1 -KeyPrefix duplicate_` exit 0.

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0500 (button unification - finding origin).

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-18 (research-кандидат S0500, /spec-draft candidate #1)

**Симптом:**

`ExtendedFloatingActionButton` в `fragment_duplicates.xml` содержит захардкоженный английский литерал `android:text="Delete Selected"` вместо `@string/<key>` - ломается локализация на RU/UK (кнопка остаётся английской).

**Доказательство:**

- `app_v2/src/main/res/layout/fragment_duplicates.xml` (около строки 168) - `android:text="Delete Selected"`.
- Зафиксировано в research-артефакте S0500: `PLAN/S0500_unify-buttons/research/01__button-inventory.md` (раздел "/spec-draft candidates", п.1).

**Что сделать при проработке:**

- Вынести строку в `values/strings.xml` + `values-ru/` + `values-uk/` (lockstep, `set-android-string.ps1 -Action add`).
- Проверить landscape-двойник `layout-land/fragment_duplicates.xml` на тот же литерал.
- Проверить, нет ли других захардкоженных строк в этой разметке.

---

## 10. Связи с другими спеками

- Обнаружено при S0500 (унификация кнопок), research-артефакт §"/spec-draft candidates".

---

## Phase 01 - Localize duplicates screen

**Files:**
- `app_v2/src/main/res/layout/fragment_duplicates.xml`
- `app_v2/src/main/res/values/strings.xml` + `values-ru/` + `values-uk/`

**Done:**

- `tvSelectResourcesSubtitle` literal -> `@string/duplicate_select_resources_subtitle` (new key, EN/RU/UK lockstep via `set-android-string.ps1 -Action add`).
- UK gap filled: `duplicate_delete_title`, `duplicate_delete_message`, `duplicate_groups_summary` (present in EN+RU, were missing in UK; all referenced in code).
- No landscape counterpart for `fragment_duplicates.xml`; no other hardcoded user-facing literals in the layout.

**Status:** `[x]` done

---

## Last Audit

**Date:** 2026-06-18
**Mode:** strategic (Simple)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 4 - WARN 0 - FAIL 0 - MANUAL 0 - EXEMPT 0

- [PASS §1] `grep` of `fragment_duplicates.xml` shows zero hardcoded `android:text="<literal>"`; subtitle now `@string/duplicate_select_resources_subtitle`.
- [PASS §2/§3] `check_strings_localized.ps1 -KeyPrefix duplicate_`: all 15 keys OK in EN/RU/UK (exit 0).
- [PASS §4] `.\a.ps1 fr` (`processStandardDebugResources`) BUILD SUCCESSFUL.
- Internal localization fix - no ALL_FEATURES capability change. Zero `Timber.d("S0516:` tags. No landscape variant to mirror.
