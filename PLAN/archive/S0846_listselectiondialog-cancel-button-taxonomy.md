# Стратегическая спецификация: S0846 - ListSelectionDialog cancel-кнопка не по dialog taxonomy

**Ticket:** S0846
**Status:** Archived
**Priority:** 40
**Date:** 2026-07-01
**Tier:** 1 - Trivial (ad-hoc)
**Roadmap entry:** Ad-hoc - parked by /spec-all during S0819 (2026-07-01)

> **Scope:** STRATEGIC skeleton (parked finding). Черновик.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-01 (parked during S0819 research)

**Симптом:** Cancel-кнопка в `ListSelectionDialog` использует `Widget.Material3.Button.TextButton` вместо обязательного по политике проекта `Widget.FastMediaSorter.Button.DialogCancel` (CLAUDE.md §11 «Dialog action pair» / `docs/ARCHITECTURE.md` Button Taxonomy).

**Доказательства:**
- `app_v2/src/main/res/layout/dialog_list_selection.xml:59-64` - стиль `Widget.Material3.Button.TextButton`.
- Gate `scripts/quality/assert-dialog-cancel-style.ps1` (в `post-change.ps1`) - проверить, почему не поймал (возможно, layout вне списка сканирования или исключение).

**Связь:** обнаружено при исследовании S0819, но к видимости фокуса отношения не имеет - чисто policy-девиация.

---

## 1. Проблема

Премиса §0 скорректирована при исполнении: `DialogCancel` тут НЕ обязателен - Clear/Cancel в selection picker не является confirm/cancel парой, и layout сознательно в exempt-списке гейта (S0538 Known-exempt + S0567; CLAUDE.md §11 «icon-only/selection/scan dialogs are exempt»). Реальная девиация другая: обе кнопки ссылаются на голый `Widget.Material3.Button.TextButton`, что запрещено Button Taxonomy («do NOT introduce a raw Widget.Material3.* reference»).

## 2. Цели

1. Обе кнопки (`btnClear`, `btnCancel`) используют именованную роль `Widget.FastMediaSorter.Button.Text` (low-emphasis вне dialog action pair).
2. Gate не трогаем - exemption корректен и документирован.

## 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

## Last Audit

**Date:** 2026-07-03
**Verdict:** Verified

- Gate «не поймал» по правильной причине: `dialog_list_selection.xml` в `$exemptFiles` гейта осознанно (не OK/Cancel пара) - вопрос из §0 закрыт, изменение гейта не требуется.
- Fix: оба `style="@style/Widget.Material3.Button.TextButton"` -> `@style/Widget.FastMediaSorter.Button.Text` (существует, themes.xml:306) + WHY-комментарий у actionBar.
- layout-land: counterpart отсутствует по дизайну (self-sizing dialog, задокументировано в шапке layout) - Rule 11 соблюдён.
- Валидация: `.\a.ps1 fr` - expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL (10s).
