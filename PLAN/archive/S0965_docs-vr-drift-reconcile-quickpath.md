# Спецификация: S0965 - выверка VR-доков под реальность + короткий quick-path «включить/настроить/смотреть 3D»

**Ticket:** S0965
**Status:** Archived
**Priority:** 55
**Date:** 2026-07-06
**Tier:** 2 - Easy (docs)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-06

**Текст (владелец):**

я думаю нужна специальная страница документации и сайта по VR версию, где кратко будет описано как включать, настраивать и смотреть 3d контент

**Решения владельца (2026-07-06):** страницы уже есть - **выверить существующие, не плодить дубль**; завести отдельный тикет на дрейф-аудит VR-доков (этот).

---

## 1. Проблема / симптом

VR-доки уже существуют (Jekyll-сайт в `docs/`): `VR_EDITION.md`, `VR_CONTROLS.md`, `VR_SIDELOAD.md` (все ×EN/RU/UK), секция `HOW_TO.md#openxr-vr-immersive-cinema`, запись в `docs/howto/index.md`. Но они **дрейфанули от кода и переобещают**:

- Описывают классы `VrPlayerActivity`, `VrOpenXrRenderManager`, `VrStereoRenderer`, «Cinema mode / огромный виртуальный экран» - в коде их НЕТ (реальный immersive-хост - `DiagnosticXrActivity` диагностического происхождения; `Vr*` встречаются лишь в устаревших комментариях `VideoPlayerManager`/`PlayerMediaLoaderManager`).
- `HOW_TO#openxr` обещает Quest **hand-tracking**, **passthrough** MR-снапшоты, «huge virtual theater screen» - в коде только луч контроллера + HUD-полоска; интерактивная HUD-панель (громкость/глубина/транспорт) отключена как always-on (серый HUD, S0961). Hand-tracking/passthrough/cinema-screen не найдены.
- `VR_EDITION` «Distribution»: `vr` в Meta Horizon Store / `assembleVrRelease` - но по факту `vr`-флейвор VR-плеер не включает (`SUPPORT_VR_PLAYER=false`); VR живёт только в `noLegal` (sideload). Гейт-паритет `vr` - открытый вопрос эпика S0773.

Итог: пользователь, читая доки, ждёт полированный VR-кинотеатр, которого ещё нет (это эпик S0773, почти не построен).

---

## 2. Реальность (два уровня - основа для честной выверки)

1. **Single-eye 3D в обычном плеере** - работает СЕГОДНЯ на всех флейворах: кроп SBS/OU/180/360 на один глаз (`StereoDetector` + `PanelStereoCropApplier`), ручной выбор формата в диалоге плеера (запоминается по файлу), настройки детекции (`stereoTrust*`). Плоский экран, не headset.
2. **Immersive на Quest** - сегодня: диагностический тест из настроек (фикс-плейлист) + запуск на одном файле (бейдж плеера; из Browse - S0962). Полированный VR-кинотеатр = эпик S0773: браузер в очках (S0963), HUD-дорожки (S0964), чинка серого HUD (S0961) - НЕ построены. Only `noLegal` несёт VR-рантайм; нужен OpenXR-девайс.

---

## 3. Исправление (scope)

- **Выверить под реальность** (убрать несуществующие классы/фичи, разметить «сейчас» vs «в эпике S0773»):
  - `docs/VR_EDITION.md` (+_RU/_UK) - «How It Works», «Supported Content», «Distribution», убрать `VrPlayerActivity`/`VrStereoRenderer`/Cinema-mode.
  - `docs/HOW_TO.md#openxr-vr-immersive-cinema` (+_RU/_UK) - убрать hand-tracking/passthrough/theater-screen; переписать под реальный путь (бейдж/пункт меню → immersive на файле).
  - `docs/VR_CONTROLS.md` (+_RU/_UK) - сверить с реальным вводом (луч контроллера, HUD-хиты); поправить расхождения.
  - `docs/howto/index*.md` - выверить формулировку VR-строки.
- **Добавить короткий quick-path** «включить → настроить → смотреть 3D», разведённый по двум уровням §2 (single-eye везде / immersive Quest частично), с честным «что уже работает / что в разработке».
- Устаревшие комментарии `Vr*` в `VideoPlayerManager`/`PlayerMediaLoaderManager` - поправить на действительные имена (`PanelStereoCropApplier` и т.п.) в рамках правки, если задевается.
- Мусор в коде не плодить; строки UI не затрагиваются (только доки).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0773 (VR-кинотеатр эпик - определяет «что coming»), S0962/S0963/S0964 (столпы), S0961 (серый HUD - влияет на честность HUD-описания), S0558 (HOW_TO path-gate - валидатор), S0241 (superseded VR-removal - VR остаётся).
- **Locale:** EN/RU/UK обязательны; только строки `U+2192 (->)` + якорь в HOW_TO гейтятся паритетом (S0558).

---

## 4. Проверка

- Grep: `VrPlayerActivity|VrOpenXrRenderManager|VrStereoRenderer|hand.?tracking|passthrough` в `docs/` -> 0 в user-facing VR-страницах (или только с явной пометкой «planned/S0773»).
- `pwsh scripts/check_strings_localized.ps1` не применим (доки); паритет EN/RU/UK проверить вручную + HOW_TO path-gate (S0558) зелёный.
- Сайт: страницы рендерятся (Jekyll front-matter сохранён), внутренние ссылки не битые.
- Читаемость: quick-path «включить/настроить/смотреть» присутствует и соответствует §2 (single-eye vs immersive).

---

## Last Audit

**2026-07-06 - Verified (docs-only, no build/device).**

Reconciled 12 files (VR_EDITION / VR_CONTROLS / HOW_TO / howto-index × EN/RU/UK). Verification passed:

- **Banned terms:** grep `VrPlayerActivity|VrOpenXrRenderManager|VrStereoRenderer|hand.?tracking|passthrough|theater screen|Cinema mode` across the 12 in-scope files -> 0 hits.
- **Path-gate S0558:** `assert-howto-settings-paths.ps1` OK (49 recipes, EN/RU/UK in parity); immersive path described in plain prose (`Settings > Media`, ASCII) to avoid a non-manifest `->` chain.
- **Accuracy spot-check:** HOW_TO#openxr and VR_EDITION now state the two tiers honestly - single-eye crop everywhere (no headset), full per-eye immersion only on `noLegal` sideload (next/prev-only, no in-headset volume/seek/track), `vr` Store flavor marked "not wired yet (epic S0773)". Detection/force-format toggles correctly gated to `supportsVrMediaControls` (vr/noLegal), only the on/off single-eye crop is universal.
- **Front-matter / anchors / links:** preserved (`#openxr-vr-immersive-cinema` intact).

**Follow-ups parked (out of scope, dedup-checked, no prior tickets):**
- S0966 - `docs/FEATURES*.md` repeats the same overclaim (Rule 11: only /skill-release edits it).
- S0967 - `docs/DEV_OPS.md` ADB section still references fictional `VrPlayerActivity`.
- S0968 - HOW_TO "Watch SBS 3D videos in VR mode" section has backwards flavor scope.
- S0969 - **real code bug:** install-VR CTA in `PlayerPlaybackCallbackImpl.kt` gates on `supportsVrPlayer` (false on `vr` since S0241) instead of `supportsVrMediaControls`, so it fires on the `vr` flavor itself.
