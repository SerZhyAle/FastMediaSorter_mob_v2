# Стратегическая спецификация: S0537 - Унификация чекбоксов выбора (Pattern B)

**Ticket:** S0537
**Status:** Archived
**Priority:** 40
**Date:** 2026-06-19
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - выделено из S0536 (quiz 2026-06-19): владелец отделил чекбоксы выбора от on/off-тогглеров.

---

## 0. Захват

Выделено из S0536 «Унификация UI-тогглеров». Владелец на квизе уточнил объём S0536: тот тикет покрывает только on/off-тогглеры (переключатели настроек и enable/disable). Чекбоксы для выбора элементов в списках / множественного выбора (Pattern B) - отдельная задача и в S0536 не входят.

Дословно (владелец): «речь идёт только о тогглерах в системе для настроек и включить-выключить. Галочки которые у нас для выбора в списках тут ни при чём, их можно унифицировать отдельным тикетом».

---

## 1. Проблема

Чекбоксы выбора (Pattern B - `MaterialCheckBox`) в приложении не проверены на единообразие. Нужно выяснить, унифицированы ли они по виду, размещению и поведению (выбор элементов списка, множественный выбор, согласие в формах add-resource/cloud), есть ли рекомендованная форма и где есть расхождения.

---

## 2. Цели

1. Установить фактическое состояние чекбоксов выбора по приложению.
2. Закрепить единую рекомендованную форму чекбокс-строки (Pattern B) для новых случаев выбора.
3. Устранить найденные расхождения, не затрагивая on/off-тогглеры (их покрывает S0536).

**Non-goals:**

- On/off-переключатели и enable/disable-тумблеры - покрыты S0536.

---

## 3. Рекомендованная форма (Pattern B)

- Каждый чекбокс выбора/опции/согласия использует `com.google.android.material.checkbox.MaterialCheckBox`.
- Граница с S0536 чистая: switches/тогглеры включения - это `MaterialSwitch`/`SwitchMaterial` (S0536); любая «галочка» - это `MaterialCheckBox` (S0537).
- Исключение: оверлей выбора элемента поверх превью (`cbSelect` в `item_media_file*`) остаётся `AppCompatCheckBox` с кастомным `selector_checkbox_select` и `buttonTint=@null` - намеренный визуал поверх изображения; `MaterialCheckBox` форсит material-tint и сломал бы оверлей.

---

## 4. Реализация

Конвертированы все оставшиеся plain `<CheckBox>` (не-Material) в `MaterialCheckBox`:

- `res/layout/item_resource_to_add.xml` - 5 (cbAdd, cbDestination, cbReadOnly, cbScanSubdirectories, cbAllFiles).
- `res/layout/dialog_scheduled_operation.xml` + `res/layout-land/` - 5+5 (multi-select маски типов файлов).
- `res/layout/dialog_network_delete_confirmation.xml` + `res/layout-land/` - 1+1 (cbDontShowAgain).

Kotlin не затронут: `MaterialCheckBox` наследует `android.widget.CheckBox`, поэтому поля ViewBinding только расширяют тип, а `findViewById<android.widget.CheckBox>` в `BrowseDialogHelper` остаётся валидным.

---

## 6. Открытые вопросы / Research items

1. **Инвентаризация чекбоксов выбора**
   - **Результат:** оверлей выбора (`cbSelect`) уже единообразен (main+noLegal). Основная масса форм/диалогов (add-resource, resource_editor, cloud folder pickers, dialog_filter, item_duplicate_file, dialogs настроек) уже на `MaterialCheckBox`. Расхождением были только plain `<CheckBox>` в 5 файлах.
   - **Переиспользуемая строка-чекбокс:** отдельного общего компонента нет; единообразие держится на едином виджете `MaterialCheckBox` + material-теме. Введение общего layout-include не оправдано (контексты различны).
   - **Статус:** Closed

---

## Last Audit

- 2026-06-19: конвертированы 17 plain `<CheckBox>` -> `MaterialCheckBox` в 5 файлах. Сборка `.\a.ps1 fc` (compileStandardDebugKotlin + processStandardDebugResources) - PASS. Kotlin без изменений.

---

## 10. Связи с другими спеками

- S0536 - унификация on/off-тогглеров; настоящий тикет покрывает комплементарный объём (чекбоксы выбора), границы не пересекаются.

---

## 12. Дальнейшие шаги

Требует полноценного `/spec` (research → approval) перед `/spec-tech`.
