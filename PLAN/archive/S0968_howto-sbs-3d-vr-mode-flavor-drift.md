# Стратегическая спецификация: S0968 - HOW_TO «Watch SBS 3D videos in VR mode» - неверный флейвор-скоуп и, возможно, неверные шаги

**Ticket:** S0968
**Status:** Archived
**Priority:** 40
**Date:** 2026-07-06
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-06 (найдено при выполнении S0965)
**Tactical spec:** `PLAN/S0968_howto-sbs-3d-vr-mode-flavor-drift/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-06

**Текст:**

Found while executing S0965 (VR docs reconciliation), directly adjacent to the section S0965 was scoped to fix (docs/HOW_TO.md `## OpenXR VR Immersive Cinema`), but itself out of S0965's named scope (S0965 touched only VR_EDITION.md/HOW_TO.md's OpenXR section/VR_CONTROLS.md/howto/index.md).

The section right above it, `## Watch SBS 3D videos in VR mode` (docs/HOW_TO.md line 151, + HOW_TO_RU.md/HOW_TO_UK.md mirrors), says `**Available in:** Standard, Legacy` and describes these steps: open an SBS 3D video, enter fullscreen, open **Playback Settings**, switch **3D Video** to **Auto-detect** or **Side-by-Side (SBS)**.

Verified during S0965 research: the strings used for this exact flow (`playback_settings_3d_auto` = "Auto-detect", `playback_settings_3d_sbs` = "Side-by-Side (SBS)") are referenced ONLY in `PlaybackControlDialogFragment.kt`, inside the "3D" tab (`ControlSection.STEREO`) of the player's Control dialog. That tab is gated by `MediaCapabilities.supportsVrMediaControls`, which is `false` by default and only `true` on the `vr`/`noLegal` flavors (confirmed in `app_v2/src/main/java/com/sza/fastmediasorter/core/capability/MediaCapabilities.kt` default + `app_v2/src/vr/java/.../di/MediaCapabilitiesModule.kt` override). So this manual-format-picker flow is NOT available on Standard/Legacy at all - the section's flavor list looks backwards relative to what it claims, or is describing UI that no longer exists in that form on Standard/Legacy.

What IS universal (all flavors, confirmed via `AppSettings.kt` comment "Detection-source flags below are flavor-independent (flat stereo exists on every flavor)" and `PlaybackSettingsFragment.kt`'s single `rowPanelStereoSingleEye` toggle, "Show 3D content from one eye") is automatic SBS/OU/180/360 detection + single-eye crop, with one on/off master toggle in Playback settings - not a per-format Auto-detect/SBS dropdown.

This needs its own verification pass: confirm the exact current UI flow on a Standard-flavor build (does the described dropdown exist anywhere for Standard, e.g. renamed/relocated, or was it fully replaced by the single toggle + vr/noLegal-only manual override), then rewrite the section's "Available in" line and steps to match, keeping EN/RU/UK in parity and respecting the HOW_TO settings-path gate (S0558) for any "Settings -> .." arrow chains.

Why out of scope for S0965: different named section (not the OpenXR one S0965 was scoped to touch), and the fix requires confirming the real current UI flow first - not a one-line find/replace.

Please scaffold a Draft spec capturing this verbatim so it can be picked up later.

---

## 1. Проблема

Секции HOW_TO.md «Watch SBS 3D videos in VR mode» и «How to Watch 3D Videos (VR)» (+ _RU/_UK) утверждали «Available in / Flavor: Standard, Legacy», но описываемый ими контрол (вкладка 3D в диалоге Control плеера с выбором Auto-detect/SBS/OU/Mono) активен только на `vr`/noLegal (гейт `supportsVrMediaControls`). Универсальный для всех флейворов контрол - единственный тумблер «Show 3D content from one eye» в Playback settings (авто-детект + обрезка до одного глаза), а не per-format дропдаун. Скоуп был перевёрнут; нужно было выверить реальный UI-флоу и переписать обе секции.

---

## 2. Цели

1. Обе 3D-секции описывают реальный двухуровневый флоу: авто одноглазая обрезка (управляется тумблером «Show 3D content from one eye») - на всех видео-флейворах (Standard/Lite/Legacy + vr/noLegal); ручной per-format выбор (вкладка 3D в Control dialog) - только на `vr`/XR-noLegal.
2. Правильный «Available in / Flavor» без обратного скоупа; без противоречия соседней секции «OpenXR VR Immersive Cinema» (S0965).
3. EN/RU/UK в паритете; гейт HOW_TO settings-path (S0558) зелёный.

**Non-goals:**

- Изменение самого UI/поведения - только выверка документации под текущий код.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

<Нумерованный список желаемого, но необязательного к первой итерации.>

### 3.2 Жёсткие ограничения

- **Flavor:** Standard/Lite/Legacy (универсальный тумблер) vs vr/noLegal (вкладка 3D в Control dialog)
- **API level:** без API-специфики
- **Wear OS:** не затрагивается
- **Производительность:** н/д (доки)
- **Совместимость данных:** н/д
- **Локализация:** EN/RU/UK - всегда обязательно (HOW_TO.md/_RU/_UK)
- **Доступность:** н/д
- **HOW_TO path-gate (S0558):** любые новые «Settings -> ..» цепочки со стрелкой U+2192 должны резолвиться в `docs/settings/settings-manifest.json` / `docs/settings/howto-path-vocab.json`, иначе гейт `scripts/quality/assert-howto-settings-paths.ps1` упадёт.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0965 (docs-vr-drift-reconcile-quickpath - источник находки, уже поправил соседнюю секцию OpenXR VR Immersive Cinema под ту же реальность)

---

## 4. Контекст текущей архитектуры

<Заполнить при разработке: PlaybackControlDialogFragment.kt строит вкладку STEREO только при supportsVrMediaControls; PlaybackSettingsFragment.kt содержит универсальный тумблер panelStereoSingleEye. Нужно на реальном Standard-билде подтвердить, что дропдауна Auto-detect/SBS там действительно нет.>

---

## 5. Предлагаемый подход

<Архитектурный уровень - заполняется позже.>

### 5.1 Основные столпы / модули

<TBD>

### 5.2 Потоки данных и событий

<TBD>

### 5.3 Точки расширяемости

<TBD>

---

## 6. Открытые вопросы / Research items

1. На реальном Standard-флейворе - что именно видит пользователь в Playback Settings по поводу 3D (только тумблер, или тоже что-то похожее на Auto-detect/SBS)?
2. Нужно ли секцию объединить с «OpenXR VR Immersive Cinema» (которую S0965 уже переписал как single-eye-3D-везде + immersive-только-noLegal), чтобы не дублировать одну и ту же тему в двух местах HOW_TO.md?

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Пользователь Standard-флейвора ищет несуществующий у него контрол (Auto-detect/SBS dropdown) | Средняя | Путаница, тикеты в поддержку | Выверить и переписать секцию |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES - предмет тикета лежит в HOW_TO.md.

---

## 9. Архитектурные решения (ADR)

ADR нет - решение по устоявшимся паттернам проекта.

---

## 10. Связи с другими спеками

- S0965 (docs-vr-drift-reconcile-quickpath) - источник находки, уже выверил соседнюю секцию под ту же двухуровневую реальность (single-eye everywhere / immersive noLegal-only).

---

## 11. Критерии готовности (strategic-level)

1. Секция «Watch SBS 3D videos in VR mode» (+ _RU/_UK) описывает реальный UI-флоу для реального круга флейворов, без противоречия соседней секции OpenXR VR Immersive Cinema.

---

## Last Audit

**Date:** 2026-07-07
**Mode:** strategic (docs-only)
**Outcome:** Verified
**Counts:** PASS 3 · WARN 1 · FAIL 0

### Checks

- PASS - обе секции («Watch SBS 3D videos in VR mode» строка 151, «How to Watch 3D Videos (VR)» строка 700) в EN/RU/UK несут двухуровневый скоуп: авто одноглазая обрезка на Standard/Lite/Legacy + `vr`/XR-noLegal; ручной per-format пикер только на `vr`/XR-noLegal. Обратного «Standard, Legacy» больше нет.
- PASS - гейт `scripts/quality/assert-howto-settings-paths.ps1` = OK (49 рецептов, все пути резолвятся, локали в паритете), exit 0.
- PASS - нет противоречия с «OpenXR VR Immersive Cinema» (S0965): вторая секция кросс-ссылается на неё для полноэкранного просмотра в шлеме.
- WARN (research §6.2) - три секции HOW_TO.md пересекаются по теме 3D (151 «Watch SBS 3D», 177 «OpenXR VR Immersive Cinema», 700 «How to Watch 3D Videos»). Скоуп выправлен и непротиворечив, но консолидация трёх секций в одну - отдельная doc-структурная работа (кандидат в follow-up `/spec-draft`), вне критерия §11.

### Manual / on-device

- [ ] Ни один - предмет тикета в HOW_TO.md, проверяется статически.
