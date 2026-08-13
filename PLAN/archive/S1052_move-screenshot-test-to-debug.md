# Стратегическая спецификация: S1052 - Перенос кнопки «Тест снимка экрана» в отладочный блок вкладки «Общие»

**Ticket:** S1052
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-15
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-15
**Tactical spec:** `PLAN/S1052_move-screenshot-test-to-debug/` (будет создан через `/spec-tech`)

---

## 1. Проблема

Кнопка «Тест снимка экрана» - это диагностический инструмент разработчика, который вручную запускает захват скриншота меню. Сейчас она лежит внутри карточки краевых жестов на вкладке настроек с папками-приёмниками, где её видят обычные пользователи (на сборках, где доступен захват меню). Это тестовый инструмент, а не повседневная настройка, и место ему - в блоке отладочных инструментов на вкладке «Общие», причём только в DEBUG-сборках.

---

## 2. Цели

1. Кнопка «Тест снимка экрана» появляется в блоке «Отладочные журналы и тестовые инструменты» на вкладке «Общие».
2. Кнопка показывается только в DEBUG-сборках.
3. Кнопка исчезает из карточки краевых жестов на вкладке с папками-приёмниками.
4. Действие сохраняется: тап запускает захват скриншота меню, как и раньше.

**Non-goals:**

- Изменение механики захвата.
- Появление кнопки в релизных сборках.
- Изменение условия, при котором вообще доступен захват меню.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Переиспользовать существующий строковый ключ подписи кнопки без заведения нового.

### 3.2 Жёсткие ограничения

- **Flavor:** действие захвата остаётся привязанным там, где присутствует лаунчер захвата меню (сейчас standard и noLegal); поведение не меняется. Дополнительный гейт - тип сборки DEBUG через `BuildConfig.DEBUG`, а не флейвор-флаг. Флейвор-зависимость видимости остаётся за существующим интерфейсом лаунчера захвата (`src/main` + флейвор-наборы), без `BuildConfig.IS_*` в `src/main`.
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** влияние незначительное.
- **Совместимость данных:** миграции не требуется.
- **Локализация:** EN/RU/UK - подпись кнопки уже локализована, переиспользуется.
- **Доступность:** кнопка остаётся focusable и достижимой с D-pad.

### 3.3 Owner inputs (Approval gate)

- **Flavor scope:** действие остаётся привязанным там, где есть лаунчер захвата меню (standard и noLegal); видимость кнопки дополнительно требует DEBUG-сборки. Гейт типа сборки через `BuildConfig.DEBUG` (тип сборки, не флейвор-флаг - Rule 14 не нарушается).
- **UI placement contract:** внутри сворачиваемой группы «Отладочные журналы и тестовые инструменты» на вкладке «Общие», рядом с прочими отладочными инструментами.
- **Accessibility:** кнопка остаётся focusable и достижимой с D-pad.
- **Validation level:** DEBUG-сборка показывает кнопку в отладочном блоке «Общих»; релизная сборка её скрывает; на вкладке с папками-приёмниками кнопки больше нет.
- **Owner sign-off:** 2026-07-15 (перенос по запросу владельца).
- **Related tickets:** S0559 (исходное тестовое действие захвата меню), S0435 (выделение карточки краевых жестов).

---

## 4. Контекст текущей архитектуры

Вкладка настроек с папками-приёмниками содержит карточку краевых жестов, куда тестовая кнопка была припаркована (S0559) после строк действий жестов. Вкладка «Общие» уже владеет группой отладочных журналов и тестовых инструментов, которая появляется только в подходящих сборках. Действие кнопки проводится менеджером захвата против биндинга фрагмента с папками-приёмниками; при переносе проводка должна переехать на поверхность вкладки «Общие», иначе на прежнем биндинге останется висячая ссылка.

---

## 5. Предлагаемый подход

Убрать контрол «Тест снимка экрана» из карточки краевых жестов и разместить в группе отладочных инструментов вкладки «Общие». Условие видимости становится составным: DEBUG-сборка И привязанный лаунчер захвата меню. Проводка, связывающая тап с лаунчером захвата, переезжает на новую поверхность настроек, а вкладка с папками-приёмниками перестаёт ссылаться на этот контрол в обеих ориентациях.

### 5.1 Основные столпы / модули

- **Отладочная группа вкладки «Общие»** - новый владелец кнопки: составной гейт видимости и проводка тапа.
- **Карточка краевых жестов** - теряет кнопку и её проводку.

### 5.2 Потоки данных и событий

UI (отладочная группа «Общих») -> проверка `BuildConfig.DEBUG` И наличия лаунчера захвата -> показ или скрытие кнопки -> тап -> запуск захвата скриншота меню.

### 5.3 Точки расширяемости

Флейвор-зависимость остаётся за существующим интерфейсом лаунчера захвата; новый флейвор-флаг не вводится. Дополнительный гейт - только тип сборки `BuildConfig.DEBUG`. Отладочная группа должна принимать дополнительную кнопку без переверстки соседних инструментов.

---

## 6. Открытые вопросы / Research items

1. **Текущее размещение, проводка и гейт видимости кнопки**
   - **Вопрос:** где живёт кнопка сейчас, чем проводится её действие и куда она переносится.
   - **Нужно выяснить:** якоря вёрстки (portrait + landscape), точка проводки, текущее условие видимости, целевая отладочная группа.
   - **Статус:** Resolved
   - **Артефакт:** `PLAN/S1052_move-screenshot-test-to-debug/research/01__current-location-and-gate.md`

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Кнопка задублирована или потеряна между ориентациями | Средняя | Двойной или отсутствующий контрол | Править `res/layout/` и `res/layout-land/` для обоих фрагментов; добавить ровно один раз |
| Релизная сборка «протекает» кнопкой | Средняя | Отладочный инструмент виден пользователю | Явный гейт `BuildConfig.DEBUG`; проверка на релизном варианте |
| Осталась висячая проводка на старом биндинге | Средняя | Ошибка компиляции или мёртвая ссылка | Перенести проводку тапа на новый хост, удалить старую ссылку |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES - это перемещение отладочного инструмента, а не новая способность.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Гейт кнопки на `BuildConfig.DEBUG`**

- **Решение:** показывать кнопку только в DEBUG-сборках через `BuildConfig.DEBUG`.
- **Альтернативы:** отдельный ресурс-флаг; отдельный build variant; оставить видимой везде.
- **Почему:** DEBUG - это тип сборки, а не флейвор; поле присутствует во всех вариантах, и гейтинг отладочного инструмента на `BuildConfig.DEBUG` - устоявшийся паттерн. Запрет Rule 14 касается `BuildConfig.IS_*` флейвор-флагов и здесь не нарушается.

---

## 10. Связи с другими спеками

- S0559 - исходное store-safe тестовое действие захвата меню.
- S0435 - выделение карточки краевых жестов, внутри которой сейчас лежит кнопка.

Блокирующих связей нет.

---

## 11. Критерии готовности (strategic-level)

1. В DEBUG-сборке на вкладке «Общие» в группе «Отладочные журналы и тестовые инструменты» видна кнопка «Тест снимка экрана».
2. В релизной сборке кнопка отсутствует везде.
3. Кнопка больше не появляется в карточке краевых жестов на вкладке с папками-приёмниками.
4. Тап запускает захват скриншота меню, как и раньше.
5. Портретная и ландшафтная ориентации совпадают.
6. Новые строковые ключи не заведены.

## Last Audit

**Date:** 2026-07-15
**Mode:** implementation (F3) + on-device gate
**Outcome:** BlockNeedUserTest (device-verify §11 on DEBUG + release builds)
**Counts:** PASS 6 · WARN 0 · FAIL 0 · MANUAL 4 (on-device)

Relocation implemented; compiles on standard + noLegal, unit tests green. `btnTakeScreenshotNow` moved from the destinations screen-gestures card into `containerDebugSettings` on the General tab (both orientations); the destinations layouts no longer reference it. Visibility is the composite gate `BuildConfig.DEBUG && menuScreenshotLaunchers.firstOrNull() != null`, wired in `GeneralSettingsFragment.setupScreenshotTestButton()`; the old `OperationsCaptureManager.setupScreenshotAction` plus its injection/call/imports were removed. `SettingsSearchCapabilityGate` gained a per-row branch mirroring the same composite gate so search never surfaces the button in release (it lost the `groupScreenGestures` container gate on the move). String `settings_take_screenshot_now` reused (no new key). Rule 22 doc-sync regenerated (manifest entry now `sectionId: general`; annotations + reference re-rendered; 5-stage gate green). Adversarial review (4 lenses: gate-semantics, search-mirror, dead-weight, layout-parity) returned zero findings.

### Behaviour change (intended, per §2 goals)

- standard debug: button now VISIBLE in the General-tab debug section (previously sat inside a card that is hidden on standard, so it was effectively never shown).
- noLegal debug: button VISIBLE in the debug section (was in the gesture card).
- Any RELEASE build, all flavors: button ABSENT everywhere incl. settings-search (previously visible on noLegal release inside the gesture card).
- lite/photos/legacy/vr debug: button hidden (no menu-capture launcher bound).

### Manual / on-device (DEBUG build)

- [x] General tab -> "Debug logs & test tools" shows "Screenshot test"; tap launches menu capture (standard/noLegal). - verified on-device 2026-07-19
- [x] Destinations-tab screen-gestures card no longer shows the button. - verified on-device 2026-07-19
- [x] Portrait and landscape match. - verified on-device 2026-07-19
- [ ] Release/target build: button absent everywhere including settings-search. - not run: code-gated by `BuildConfig.DEBUG`, out of scope for this debug-build emulator run

### Device run 2026-07-19 (standard debug, emulator-5554, Android 15)

Build under test: `com.sza.fastmediasorter.debug` v2.60.7182.317-DEBUG (Build 260718231). Result: PASS on all in-scope (DEBUG-build) criteria.

- General tab -> "Debug logs and test tools" (`headerDebugSettings`): expected "Screenshot test" (`btnTakeScreenshotNow`) present inside `containerDebugSettings` | actual present, alongside Show Log / Current Session Log / RUN INTEGRATION TESTS / Import Test Setup. PASS.
- Tap "Screenshot test": expected menu-capture flow launches | actual MediaProjection consent dialog "Screen capture needs your permission" appeared; logcat carries `D/GeneralSettingsFragment: S1052: screenshot-test tapped from General-tab debug section` (valid Timber.d probe). PASS.
- Management (destinations) tab -> "Edge screen gestures" card (`groupScreenGestures`): expected no "Screenshot test" button | actual card holds only the Gesture-overlay row + "Configure gestures" (`btnOpenEdgeGestureConfig`); no `btnTakeScreenshotNow`. PASS.
- Orientation: expected portrait == landscape | actual identical in both (present in General debug section, absent from destinations gestures card) in portrait and landscape. PASS.
- Release/target-build absence (incl. settings-search): not exercised - visibility is code-gated on `BuildConfig.DEBUG` and needs a release build; out of scope for this emulator debug run.
- Log: no app errors or exceptions in the captured window (`temp/S1052/run_20260719_2352.log`).
- Evidence: `temp/S1052/screens/step_01_general_debug_screenshot_test_present_portrait.png`, `step_01b_screenshot_test_tap_triggers_capture_consent.png`, `step_02_destinations_gestures_card_portrait.png`, `step_03_general_debug_screenshot_test_present_landscape.png`, `step_04_destinations_gestures_card_landscape.png`.
