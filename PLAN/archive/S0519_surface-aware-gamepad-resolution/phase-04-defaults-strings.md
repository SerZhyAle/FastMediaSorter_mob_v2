# Phase 04 - Defaults + trilingual strings

Goal: supply the default browser gamepad bindings the resolver reads, and the user-facing labels for the new commands and group.

## Default bindings

- [ ] In `app_v2/src/main/assets/input/default_bindings.json`, append 7 browser entries (group `BROWSER_ACTIONS`, gamepad keycodes mirroring the deleted literal tree):
  - `browser.select` -> `gamepad_btn:96` (BUTTON_A)
  - `browser.back` -> `gamepad_btn:97` (BUTTON_B)
  - `browser.multi_select` -> `gamepad_btn:99` (BUTTON_X)
  - `browser.context_menu` -> `gamepad_btn:100` (BUTTON_Y)
  - `browser.search` -> `gamepad_btn:108` (BUTTON_START)
  - `browser.tab_prev` -> `gamepad_btn:102` (BUTTON_L1)
  - `browser.tab_next` -> `gamepad_btn:103` (BUTTON_R1)
  - Each entry: `label_key` = `keybinding_label_<id>`, `flavor_gate: null`, empty keyboard/mouse/vr arrays.
  - Verification: JSON parses; 7 new entries with `"group": "BROWSER_ACTIONS"`.

## Strings (EN / RU / UK lockstep)

- [ ] In `res/values/strings_input.xml`, `res/values-ru/strings_input.xml`, `res/values-uk/strings_input.xml`, add command labels:
  - `keybinding_label_browser_select`, `_back`, `_multi_select`, `_context_menu`, `_search`, `_tab_next`, `_tab_prev`.
  - And group header `keybinding_group_browser_actions`.
  - Verification: `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "keybinding_label_browser_"` exits 0; same for `keybinding_group_browser_actions`.

## Suggested copy

- EN: Select / Back / Multi-select / Context menu / Search / Next tab / Previous tab; group "Browser actions".
- RU: Выбрать / Назад / Выделение / Контекстное меню / Поиск / Следующая вкладка / Предыдущая вкладка; group "Действия браузера".
- UK: Вибрати / Назад / Виділення / Контекстне меню / Пошук / Наступна вкладка / Попередня вкладка; group "Дії браузера".

## Notes

- Keycode constants: BUTTON_A=96, B=97, X=99, Y=100, L1=102, R1=103, START=108.
- `resolveCommandLabel` builds `keybinding_label_<commandId with . -> _>`; the JSON `label_key` field is informational and must match the same convention.
