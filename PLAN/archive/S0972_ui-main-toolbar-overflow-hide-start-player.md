# Спецификация (compact): S0972 - при переполнении верхней панели скрывать последнюю кнопку «Проигрыватель»

**Ticket:** S0972
**Status:** Archived
**Priority:** 55
**Date:** 2026-07-06
**Tier:** 2 - Easy

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-06

**Текст (владелец):**

на этом устройстве после инсталляции не поместились все кнопки в верхнюю панель. нужно для таких случаев не показывать в верхнем меню последнюю кнопку "проигрыватель". она по идее должна запускать последний проигрыватель, но можно пожертвовать

**Контекст:** устройство POCO/Xiaomi, 720x1600 (360dp ширина), Android 15, standard. Верхняя панель `layoutControlButtons` - горизонтальный LinearLayout без переноса; на узком экране последняя кнопка обрезается.

---

## 1. Проблема / симптом

Верхняя панель главного окна (`layoutControlButtons`) на узких экранах не вмещает все кнопки (Exit, Add, Filter, Refresh, [Menu], Settings, [Toggle], Favorites, **StartPlayer**). Последняя - `btnStartPlayer` (запуск последнего плеера/слайдшоу) - обрезается. Число видимых кнопок варьируется (Menu/Toggle бывают GONE), поэтому статический sw-порог ненадёжен.

---

## 2. Корневая причина

`layoutControlButtons` - horizontal LinearLayout, wrap_content-кнопки, без переноса/overflow-логики. Сумма ширин при большом числе кнопок (или в label-режиме на wide) превышает доступную ширину -> последняя кнопка визуально срезается.

---

## 3. Исправление

В `MainLayoutChromeManager` (уже владеет топ-баром) добавить `applyControlBarOverflow()`:
- измеряет суммарную ширину видимых детей `layoutControlButtons` (`measure(UNSPECIFIED)` + margins) через `doOnLayout`;
- перед замером сбрасывает `btnStartPlayer` в VISIBLE (чтобы решение принималось по полному набору);
- если needed > available (`bar.width - paddings`) -> `btnStartPlayer.visibility = GONE`, иначе VISIBLE;
- вызывает `restitchControlBarFocusChain()` (перестройка D-pad фокуса по видимым).
- Хуки: конец `updateToolbarButtonLabels()` (setup + onConfigurationChanged) и конец `applyCompactToolbar()` (смена compact). MainActivity не трогаем - оба метода уже зовутся оттуда.

Жертвуем только `btnStartPlayer` (прямое пожелание владельца). Клик запуска плеера остаётся доступен из других мест.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none (дедуп по toolbar overflow - пусто).

---

## 4. Проверка

- Компиляция `fc` зелёная; detekt по файлу чист.
- On-device (POCO/узкий 360dp portrait): в топ-панели `btnStartPlayer` скрыт, остальные кнопки видны; на wide/landscape (влезает) - кнопка присутствует. Поворот туда-обратно корректно скрывает/возвращает. D-pad фокус-цепочка непрерывна.

## Last Audit

**Manual (device test):** 2026-07-07 - emulator-5554, standard-debug, Android 17, 1080x2280 @440dpi (~393dp). Verdict: **PASS**.

- Narrow width forced via adb `wm size`; MainActivity relaunched to re-measure; targets resolved from the accessibility tree (label "Start Player"), never coordinates.
- Wide baseline (native ~393dp): expected `btnStartPlayer` present -> actual VISIBLE (last button, x=997), all 9 buttons; no probe. PASS.
- Narrow ~360dp (990x2100, the real POCO target): expected only `btnStartPlayer` hidden, rest reachable -> actual `btnStartPlayer` GONE, all 8 others VISIBLE within bounds (Favorites ends at the 990 edge). Probe fired: `S0972: control bar overflow needed=1040 available=990 - hiding Start Player`. PASS.
- Narrow ~196dp (540x1170): expected overflow hides Start Player -> actual `btnStartPlayer` gone; probe fired `needed=1040 available=540`. (At this extreme width the bar over-overflows, so trailing buttons also clip - probe confirms the GONE branch ran.) PASS.
- Restore (`wm size reset` -> native): expected button returns on re-measure -> actual `btnStartPlayer` VISIBLE again, all 9 buttons; fit path fired 0 probes (measured, stays silent when it fits). PASS.
- Evidence: `temp/S0972/evidence_summary.md`, `temp/S0972/probe_narrow_360dp.log`, `temp/S0972/probe_narrow_196dp.log`.
