# Стратегическая спецификация: S0502 - хардкод строки на FAB экрана дубликатов

**Ticket:** S0502
**Status:** Archived
**Priority:** 40
**Date:** 2026-06-18
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - parked during S0500 (unify-buttons) research 2026-06-18

> **Scope:** STRATEGIC. Скелет-захват из /spec-draft.

---

## 0. Захваченный материал (inbox)

> Сырой захват находки на лету. Распределяется по §1/§3.1/§6 при доработке через `/spec` или `/spec-update`.

**Захвачено:** 2026-06-18

**Захвачено во время:** S0500 (research кнопок, android-solution-researcher)

**Текст:**

`app_v2/src/main/res/layout/fragment_duplicates.xml:168` - `ExtendedFloatingActionButton` имеет захардкоженную строку `android:text="Delete Selected"` вместо `@string`-ссылки. Ломает локализацию (EN/RU/UK) - кнопка всегда на английском.

FIX: вынести строку в `strings.xml` через `scripts/utils/set-android-string.ps1 -Action add` (EN/RU/UK в lockstep), заменить инлайн-текст на `@string/<key>`, проверить `scripts/check_strings_localized.ps1`. Проверить, не перекрыт ли `fragment_duplicates.xml` во flavor-source-set.

SCOPE: вне объёма S0500 (унификация кнопок - оформление, не строки). Нетривиально на грани: требует вынос строки в 3 локали + проверку flavor-перекрытий, не однострочник.

**Вложения:**

Вложений нет.

---

## 1. Проблема

Кнопка `fabDeleteSelected` на экране дубликатов (`fragment_duplicates.xml`) несла захардкоженный `android:text="Delete Selected"`. При исследовании выяснилось:

- Статический текст **мёртвый**: FAB стартует `visibility="gone"`, показывается только при выборе и тогда текст всегда задаётся кодом (`getString(R.string.duplicate_fab_delete, count, size)` в `DuplicatesFragment`). Захардкоженная строка не отображается пользователю.
- Реальный user-facing дефект - у видимой строки `duplicate_fab_delete` (форматной, с count+size) **отсутствовал украинский перевод** (EN/RU были, UK - нет). UK-пользователи видели fallback.

## 2. Решение (реализовано 2026-06-18)

- Удалён мёртвый `android:text="Delete Selected"` из `fragment_duplicates.xml` (dead-weight hygiene; текст всегда выставляется кодом). Нарушение Rule 19/локализации устранено в корне, без нового ресурса.
- Добавлен недостающий UK-перевод `duplicate_fab_delete` в `values-uk/strings.xml`: «Видалити вибрані (%1$d елементів, %2$s)» (через `set-android-string.ps1 -Action set -Locale uk -CreateIfMissing`). EN/RU не тронуты.
- Переиспользован существующий ключ - новый ключ не заводился (anti-dup).

## 3. Verification

- `scripts/check_strings_localized.ps1 -KeyPrefix duplicate_fab_delete` → EN/RU/UK OK, exit 0.
- `.\a.ps1 fr` (processStandardDebugResources) → BUILD SUCCESSFUL.
- Grep по `res/layout` - `Delete Selected` больше не встречается.
- Нет flavor-оверрайдов `fragment_duplicates.xml` (только src/main).

## Last Audit

**Date:** 2026-06-18
**Mode:** strategic (compact / Simple)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 4 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Manual / on-device

- [ ] None. Resource build + trilingual parity verified statically. (§FEATURES EXEMPT - localization bugfix, no new capability.)
