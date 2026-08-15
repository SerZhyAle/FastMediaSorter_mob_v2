# Стратегическая спецификация: S1065 - Maestro-флоу add_resource_forms и slideshow_basic хрупки к реальному состоянию устройства

**Ticket:** S1065
**Status:** Archived
**Priority:** 35
**Date:** 2026-07-15
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - обнаружено в /spec-prerelease 2026-07-15
**Tactical spec:** `PLAN/S1065_maestro-flows-flaky-leftover-state/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

> Сырой захват находки в ходе /spec-prerelease. Не дефект приложения - хрупкость тестовой инфраструктуры (сиблинг S1043).

## 0. Approval Gate (owner input)

- **Requested mode:** Delegated by user - `/spec-all S1065` requests the complete automated implementation and verification pipeline.
- **Goal / expected outcome:** Delegated by user - make the two identified Maestro flows deterministic without changing runtime app behavior.
- **Local anchor:** Provided by user - S1065; affected flows are `add_resource_forms.yaml` and `slideshow_basic.yaml`.
- **Scope boundaries / forbidden areas:** Delegated by user - Maestro YAML and test infrastructure only; no Android runtime code, product behavior, or S1043 settings-flow scope.
- **Done / success signal:** Delegated by user - both affected flows and the `-Suite all` capability suite pass on the reference emulator without manual state reset.
- **Autonomy rule:** Delegated by user - agent may decide with explicit assumptions within the stated test-only scope.
- **UI decisions / delegation:** N/A - the task changes test automation only and does not alter user-visible UI.

`Approved` is permitted: every required decision is either supplied by the request or explicitly delegated by `/spec-all`.

---

**Захвачено:** 2026-07-15

**Контекст:** прогон `/spec-prerelease` на emulator-5554 (standard-debug, prerelease build). Полный Maestro-набор `-Suite all`: 14/17 PASS, 3 FAIL - `settings_toggle_sweep.yaml` (уже покрыт S1043), `add_resource_forms.yaml`, `slideshow_basic.yaml`.

**Доказательство, что приложение чистое (не регрессия):**

- Verdict log-dimension PASS: `actionableErrors=0`, `crashBlocks=false`, никаких crash/ANR.
- Детальный log-audit: `toastCount=0`; все 9 "actionable"-кластеров - системный/эмуляторный шум (DateSmartspaceView, chromium/WebView, lowmemorykiller, googlequicksearchbox AppOps, JavaBinder и т.п.), ни один не из `com.sza.fastmediasorter`.
- Ручная репродукция `add_resource_forms`: после тапа `btnAddResource` экран AddResourceActivity открывается и `layoutResourceTypes` (GridLayout с карточками Локальная/Сетевая/SFTP/Облако) ПРИСУТСТВУЕТ. Пикер работает - падение теста ложное.

**Симптом (ложные FAIL):**

- `add_resource_forms.yaml`: `Assert layoutResourceTypes is visible` падает после тапа `btnAddResource`. ПРОШЁЛ в smoke-наборе на свежеподготовленном приложении (первый флоу), ПАДАЕТ после импорта SZA-ресурсов и в изоляции при накопленном состоянии. Во время падений устройство было в ландшафте (content 2280x1080), оставленном предыдущими player-флоу - `_shared/go_home.yaml` не сбрасывает ориентацию.
- `slideshow_basic.yaml`: `Assert btnSlideshowCmd is visible` падает после тапа по `photoView`. `player_image.yaml` (тот же фото-вьюер) в этом же прогоне ПРОШЁЛ - фото-вьюер исправен; под подозрением селектор `btnSlideshowCmd` / тайминг toggle контролов / та же оставленная ориентация. Требует подтверждения при триаже.

**Эвиденс-логи:**

- `temp/add_resource_forms_maestro_20260715_184229.log`, `temp/add_resource_forms_maestro_20260715_185638.log`, `temp/add_resource_forms_maestro_20260715_190206.log`
- `temp/slideshow_basic_maestro_20260715_183952.log`, `temp/slideshow_basic_maestro_20260715_185411.log`, `temp/slideshow_basic_maestro_20260715_190514.log`
- `temp/S0484/maestro_suite_20260715_182304.json` (полный набор), `temp/S0484/run_20260715_182304.log` (logcat)

**Гипотеза корневой причины:** shared-фрагменты (`_shared/go_home.yaml` и др.) не приводят устройство к детерминированному состоянию перед флоу - оставленная ориентация (ландшафт после player-флоу) и накопленное состояние ресурсов ломают допущения о чистом портретном экране. Тот же класс, что и S1043 (persisted settings tab), но на не-settings экранах.

**Предлагаемое направление (на research):**

- Добавить в `_shared/go_home.yaml` (или отдельный `_shared/reset_orientation.yaml`) явный сброс ориентации в портрет перед флоу, зависящими от портретной раскладки.
- Проверить `slideshow_basic` вручную: присутствует ли `btnSlideshowCmd` после тапа по `photoView` на чистом портрете; если селектор/ид переименован - обновить флоу.
- Рассмотреть общий `_shared/`-механизм детерминированного сброса состояния (ориентация + активная вкладка + положение списка), переиспользуемый набором - объединить с планом S1043.

---

## 1. Проблема

Maestro-флоу `add_resource_forms.yaml` и `slideshow_basic.yaml` дают ложные FAIL в полном наборе `-Suite all` и в изоляции при накопленном/оставленном состоянии устройства (ориентация, импортированные ресурсы), хотя соответствующий UI приложения работает корректно (подтверждено ручной репродукцией и чистым logcat). Это подрывает надёжность `/spec-prerelease` как релиз-гейта - тот же класс проблемы, что зафиксирован в S1043 для `settings_toggle_sweep.yaml`, но на других экранах.

## 2. Цели

1. `add_resource_forms.yaml` проходит независимо от накопленного состояния (импортированные ресурсы, ориентация), оставленного предыдущими флоу.
2. `slideshow_basic.yaml` проходит детерминированно; при необходимости обновить селектор `btnSlideshowCmd` под фактический UI.
3. Полный набор `-Suite all` проходит без ручного вмешательства в состояние устройства между флоу.

**Non-goals:**

- Изменение поведения приложения (ориентация, персист состояния - осознанный UX, не баг).
- Дублирование S1043 - настроечная вкладка остаётся в S1043; здесь - остальные флоу.

## 3. Пожелания и ограничения

### 3.2 Жёсткие ограничения

- **Flavor:** не относится - тестовая инфраструктура.
- **API level / Wear OS / Производительность / Данные / Локализация / Доступность:** не относится (YAML-флоу).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1043 (`maestro-settings-tab-flaky`) - тот же класс хрупкости тестового набора к состоянию; рассмотреть общий shared-механизм сброса.

## 10. Связи с другими спеками

- S1043 - сиблинг: persisted settings tab ломает `settings_toggle_sweep.yaml`. S1065 покрывает остальные флоу (`add_resource_forms`, `slideshow_basic`), ломающиеся тем же классом причин (оставленное состояние/ориентация).

## 11. Критерии готовности (strategic-level)

1. `add_resource_forms.yaml` и `slideshow_basic.yaml` проходят после того, как предыдущие флоу оставили устройство в ландшафте и/или с импортированными ресурсами.
2. Полный `-Suite all` проходит 17/17 без ручного вмешательства между флоу.
3. Не введено изменений в рантайм-код приложения (только Maestro `_shared/`-фрагменты и/или селекторы флоу).

## 12. Реализация и последняя проверка

- `add_resource_forms.yaml` уже содержит ожидание готовности формы вместо мгновенной проверки после запуска отдельного Activity. Повторный прогон на `emulator-5554` 2026-07-28: PASS (1/1).
- `slideshow_basic.yaml` уже повторяет тап по изображению, когда панель команд скрыта. В этой итерации добавлен `_shared/open_all_images.yaml`: он открывает виртуальный ресурс по точной сохранённой метке `All Images` или `Все изображения`, без regex, и используется во всех затронутых flow.
- Статическая проверка ссылок на shared flow и обеих точных меток: PASS. `scripts/post-change.ps1` для нового flow: PASS.
- Проверка `slideshow_basic.yaml` на `emulator-5554` остановлена на предусловии тестовых данных: `virtual://all_images` показал `0 files`; `photo_001.jpg` не появился после точечной загрузки и MediaStore scan. До полного suite требуется стандартная подготовка медиа через `scripts/utils/setup_test_media.ps1` либо эквивалентная подготовка только согласованного reference-устройства.

**Last audit:** Partial - нет evidence для `slideshow_basic.yaml` после готового MediaStore fixture и нет запуска `-Suite all`; runtime-код приложения не изменялся.
