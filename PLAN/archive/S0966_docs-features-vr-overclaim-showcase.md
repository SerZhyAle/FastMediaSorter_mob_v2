# Стратегическая спецификация: S0966 - Ложные VR-заявления в публичной витрине FEATURES.md

**Ticket:** S0966
**Status:** Archived
**Priority:** 40
**Date:** 2026-07-06
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-06 (найдено при выполнении S0965)
**Tactical spec:** `PLAN/S0966_docs-features-vr-overclaim-showcase/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-06

**Текст:**

Docs drift found while executing S0965 (VR docs reconciliation), out of that ticket's scope (S0965 only touches VR_EDITION.md/HOW_TO.md/VR_CONTROLS.md/howto/index.md).

Problem: docs/FEATURES.md (+ FEATURES_RU.md + FEATURES_UK.md) line 76 - the public feature showcase, published to the site - makes the same false VR claims S0965 just removed elsewhere: "virtual cinema screen for flat files", "head tracking HUD", and "passthrough snapshot capture on Quest 3", all tagged `[VR Only]`. None of these exist in code (verified during S0965: no hand-tracking-gesture control, no passthrough/mixed-reality snapshot feature, no "cinema mode" virtual screen - only a controller aiming ray + trigger for next/previous navigation in the noLegal-only immersive session, and the `vr` Store flavor doesn't even have working immersive playback yet, SUPPORT_VR_PLAYER=false, epic S0773 pending).

Why this needs its own ticket instead of a quick fix: per CLAUDE.md Rule 11, docs/FEATURES*.md is populated ONLY by /skill-release from the docs/ALL_FEATURES.jsonl diff since the previous release - it must never be edited per-spec directly. So the real fix is either (a) find and correct the source ALL_FEATURES.jsonl record(s) that seeded this showcase entry, so the next /skill-release pass regenerates it honestly, or (b) if FEATURES.md is simply stale relative to ALL_FEATURES and needs an out-of-band correction, that decision needs an owner call given the Rule-11 governance constraint. Either way it's release-process work, not a docs-prose edit, so it doesn't belong in S0965.

Please scaffold a Draft spec capturing this verbatim so it can be picked up later.

---

## 1. Проблема

docs/FEATURES.md + _RU + _UK строка 76 (`[VR Only]`) обещает «virtual cinema screen for flat files», «head tracking HUD» и «passthrough snapshot capture on Quest 3». Публичная витрина, опубликованная на сайт, вводит в заблуждение относительно того, что реально работает в `vr` Store-сборке (там `SUPPORT_VR_PLAYER=false`, иммерсивный плеер ещё не готов - epic S0773).

Расследование 2026-07-10 (см. §4.1) уточнило исходную формулировку S0965: заявления не «висят в воздухе» - большинство порождено *активными* записями `docs/ALL_FEATURES.jsonl` (VR & OpenXR area, 25 записей, все `status:active`). Значит проблема не в тексте FEATURES.md, а глубже: расходятся три источника истины - код (иммерсив едва работает), инвентарь ALL_FEATURES (25 активных VR-возможностей) и публичная витрина FEATURES.md. Честный фикс - это аудит правдивости VR-витрины + governance-решение по Rule 11, а не правка одной строки прозы.

---

## 2. Цели

<Нумерованный список наблюдаемых улучшений.>

**Non-goals:**

- Реализация hand-tracking/passthrough/cinema-mode - вне объёма (это была бы фича, а не докс-фикс).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

<Нумерованный список желаемого, но необязательного к первой итерации.>

### 3.2 Жёсткие ограничения

- **Flavor:** vr / noLegal (VR feature surface)
- **API level:** без API-специфики
- **Wear OS:** не затрагивается
- **Производительность:** н/д (доки)
- **Совместимость данных:** н/д
- **Локализация:** EN/RU/UK - всегда обязательно (FEATURES.md/_RU/_UK)
- **Доступность:** н/д
- **Governance (CLAUDE.md Rule 11):** docs/FEATURES*.md редактируется ТОЛЬКО через `/skill-release` из диффа `docs/ALL_FEATURES.jsonl` с прошлого релиза - никогда напрямую по тикету. Решение о механизме фикса (правка ALL_FEATURES.jsonl исходной записи vs внеплановая правка) требует owner-инпута.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0965 (docs-vr-drift-reconcile-quickpath - источник находки), S0773 (vr-cinema-program-separate-player - определяет что реально «coming»)
- **VR-audit scope (2026-07-11):** аудит 25 активных VR-записей ALL_FEATURES провести из кода в рамках S0966; отдельный device-gated тикет не заводить.
- **Fix mechanism (2026-07-11):** задокументированное исключение из Rule 11 - прямая правка `docs/FEATURES*.md` с owner sign-off (исключение зафиксировано в §6).
- **Claims disposition (2026-07-11):** все три заявления остаются как готовые - код будет доведён к публикации; урезания витрины нет.

---

## 4. Контекст текущей архитектуры

Пайплайн: `docs/ALL_FEATURES.jsonl` (developer-инвентарь, one JSONL per capability) -> `/skill-release` берёт дифф с прошлого релиза -> курирует прозу в `docs/FEATURES*.md` (публичная витрина). Строка витрины агрегирует несколько записей ALL_FEATURES в одно предложение - связь не 1:1, поэтому правка ALL_FEATURES не перегенерит существующий текст FEATURES.md автоматически; нужен проход `/skill-release`.

### 4.1 Findings (research 2026-07-10)

Источники строки 76 в `docs/ALL_FEATURES.jsonl` (все `status:active`):

- «virtual cinema screen for flat files» <- L181 `vr-openxr.virtual-cinema-screen-for-flat-files` (spec S0326).
- «head tracking HUD» <- L186 `head-locked-hud-overlay` (S0290) + L187 `interactive-hud-control-panel` (S0283) + L200 `fps-overlay-in-immersive-hud` (S0290).
- hand-tracking (упомянут в исходном тексте S0965) <- L190 `hand-tracking-input-support` (S0249).
- «passthrough snapshot capture on Quest 3» <- **источника в ALL_FEATURES нет**; это чистый дрейф FEATURES.md (ручная правка или запись, ретайрнутая позже). `flat-player-state-snapshot-and-restore` (L196, S0292) - про снапшот состояния flat-плеера, не про passthrough-камеру.

Всего VR & OpenXR area: 25 активных записей (L178-L200, L349-L350), specs S0249/S0283/S0290/S0292/S0296/S0326.

Противоречие: инвентарь утверждает 25 рабочих VR-возможностей, тогда как `vr` Store-flavor имеет `SUPPORT_VR_PLAYER=false` и рабочего иммерсивного плеера ещё нет (epic S0773 pending). Либо часть из 25 записей - оверклейм, либо возможности есть в `noLegal` sideload-VR, но не в Store `vr`. Разведение этого - предмет owner-решения (§6), часть проверок device-gated (нужен Quest 3).

---

## 5. Аудит правдивости 25 VR-записей (from-code, 2026-07-11)

Аудит 25 активных записей area «VR & OpenXR» из `docs/ALL_FEATURES.jsonl` проведён из кода (без Quest, decision #1). Иммерсивный код живёт в flavor-source-set `app_v2/src/vr/` (нативный OpenXR-рантайм `fms_diagnostic_xr` + Kotlin `ui/xr`, `core/xr`), примонтированном в flavor `noLegal` (`SUPPORT_VR_PLAYER=true`, `build.gradle.kts:607`). Store-flavor `vr` монтирует только `app_v2/src/vrOnly/` и имеет `SUPPORT_VR_PLAYER=false` (`build.gradle.kts:543,649`) - рабочий иммерсив собирается в noLegal sideload-VR, а не в Store `vr`. Рантайм `diagnostic_xr` - настоящий GLES/OpenXR-движок (шейдеры, swapchain, action set, haptics), не заглушка.

Вердикт: BACKED (код реализует заявление), PARTIAL (код есть, заявление шире реального поведения), ABSENT (кодового пути нет). Диспозиция: `backed-now` (рабочий код в noLegal), `owner-flag` (материальный зазор запись<->код, требует решения владельца «доводить код vs переформулировать запись»). `phantom` (нет кода И не входит в owner-committed набор) в наборе не встретился.

### 5.1 Таблица аудита

| # | id (vr-openxr.*) | Spec | Вердикт | Диспозиция | Код / зазор |
|---|------------------|------|:-------:|------------|-------------|
| 1 | openxr-immersive-session-engine | S0249 | BACKED | backed-now | `DiagnosticXrActivity` + `DiagnosticXrRenderThread` (native `runFrameLoop`), arm64-only `.so`. |
| 2 | 360-degree-equirectangular-image-viewer | S0249 | BACKED | backed-now | Сфера-меш в `XR_TYPE_COMPOSITION_LAYER_PROJECTION` (`xr_session.cpp`); mono/SBS/OU из имени. Мелко: не extension `equirect2`, а меш в projection-слое. |
| 3 | 180-degree-hemisphere-image-viewer | S0249 | PARTIAL | owner-flag | Режим детектится, рендерится обычным hemisphere-мешем; кода fisheye-dewarp нет. |
| 4 | virtual-cinema-screen-for-flat-files | S0326 | PARTIAL | owner-flag (headline, keep) | Плоский quad-экран реален; селектор `vrRenderingMode` (CINEMA/FULL_SBS/FULL_OU) - мёртвая настройка, рендерер её не читает. |
| 5 | stereoscopic-sbs-ou-rendering | S0326 | BACKED | backed-now | `u_stereoLayout` half-crop в шейдере + `StereoDetector`. |
| 6 | cylinder-projected-panorama-viewer | S0249 | PARTIAL | owner-flag | Detection-only; `CYLINDER_180 -> HEMISPHERE_180`; cylinder composition-layer нет. |
| 7 | immersive-video-playback | S0296 | BACKED | backed-now | `GL_TEXTURE_EXTERNAL_OES` video-surface -> ExoPlayer, `seekTo` из snapshot. |
| 8 | dynamic-media-playlist-in-immersive-mode | S0290 | BACKED | backed-now | `navigateToNext/PrevMedia`, HUD-кнопки + нативный thumbstick. |
| 9 | head-locked-hud-overlay | S0290 | BACKED | backed-now (headline) | `generateFilenameHudBytes` + red error variant; `xr_hud_update` привязка к позе головы. |
| 10 | interactive-hud-control-panel | S0283 | PARTIAL | owner-flag (headline, keep) | Play/Pause/Prev/Next + Volume/Depth + ray hover/click реальны; но «world-locked» неверно - панель head-locked, как баннер. |
| 11 | adjustable-stereo-parallax-depth | S0283 | BACKED | backed-now | HUD-слайдер -> `setParallaxShift` -> `u_parallaxShift` uniform. |
| 12 | controller-haptic-feedback | S0283 | BACKED | backed-now | `xr_input_apply_haptic` -> реальный `xrApplyHapticFeedback`. |
| 13 | hand-tracking-input-support | S0249 | PARTIAL | owner-flag | Манифест + нативные hand-trackers + pinch->nav реальны; заявленный exit по руке не привязан (exit только на контроллере). |
| 14 | multi-source-input-exit-handler | S0249 | PARTIAL | owner-flag | Унифицированы 2 из 3 источников (Key+Motion); нативный controller-exit минует handler; `onNativeAction` мёртв. |
| 15 | automatic-stereo-format-detection | S0326 | BACKED | backed-now | `StereoDetector` (mp4-боксы/имя/GPano/Matroska/AR) + `StereoDetectionConfig`. |
| 16 | per-file-stereo-format-override | S0326 | PARTIAL | owner-flag | Реально для 2D-плеера (`StereoFormatOverrideDao`); в VR-иммерсив не пробрасывается. |
| 17 | auto-immersive-entry-for-stereo-content | S0326 | ABSENT | owner-flag | Флаг `vrAutoImmersive` + UI есть, но чтения флага/авто-запуска нет; `launchFromBadge` только по клику. |
| 18 | player-vr-launch-badge-and-overflow-entry | S0292 | PARTIAL | owner-flag (minor) | Badge/overflow + capture реальны; показ для всех video/image/gif, не только stereo. |
| 19 | flat-player-state-snapshot-and-restore | S0292 | BACKED | backed-now | `PlayerStateSnapshot` (11 полей) capture -> XR -> restore. |
| 20 | xr-runtime-detection | S0249 | BACKED | backed-now | `XrEnvironmentDetectorImpl` `hasSystemFeature` (Quest / Android XR / none). |
| 21 | 3d-vr-master-toggle-with-profile-sync | S0249 | BACKED | backed-now | `MasterTogglePreferences` + `VrProfileSettingsSyncImpl.align` (VR_HEADSET -> ON). |
| 22 | vr-settings-block-in-media-settings | S0249 | BACKED | backed-now | `VrSettingsBlockFragment` все ряды (два из них dead downstream: см. #4/#17). |
| 23 | fps-overlay-in-immersive-hud | S0290 | BACKED | backed-now (headline) | EMA-FPS из `predictedDisplayTime` -> `xr_session_get_fps` -> HUD. |
| 24 | loading-overlay-before-first-immersive-frame | - | BACKED | backed-now | `showInitialLoadingOverlay` -> dismiss в `onRenderThreadSessionReady`. |
| 25 | bundled-360-fallback-asset | - | BACKED | backed-now | CC0 8192x4096 equirect JPEG `vr_diagnostic_360_mono.jpg` + `DiagnosticXrAssetProvider`. |

### 5.2 Сводка

- BACKED (backed-now): 16.
- PARTIAL: 8; ABSENT: 1 - все 9 в диспозиции `owner-flag` (зазор запись<->код, но каждая запись - часть owner-committed иммерсивной VR-поверхности).
- phantom (ретайр): 0.
- Мутаций `docs/ALL_FEATURES.jsonl`: 0.

### 5.3 Passthrough (чистый дрейф FEATURES.md)

«passthrough snapshot capture on Quest 3» (FEATURES.md стр.76) - записи в ALL_FEATURES нет (подтверждено `grep`), кода passthrough-камеры нет. Per decision #3 строка витрины остаётся (владелец обязуется реализовать к публикации). JSONL-действия нет; в ALL_FEATURES нечего трогать.

### 5.4 Почему 0 мутаций (conservative gate)

Decision #3 (keep-all: все три заявления реализуются, витрина не урезается, честность закрывается доведением кода) + правило задачи: ретайрить/понижать запись через `scripts/all_features` только если это однозначный phantom-оверклейм, который владелец явно не выпускает. Все 25 записей - часть единой owner-committed иммерсивной VR-поверхности (владелец: код будет доведён к публикации). Однозначных phantom нет -> 0 ретайров и 0 downgrade. Материальные зазоры зафиксированы как `owner-flag` (см. §5.5) - решение «доводить код vs переформулировать запись» принимает владелец; это не ship/retire-вопрос (ship уже решён decision #3), а implementation-follow-up (кандидаты в scope epic S0773).

### 5.5 Owner-flags (материальные зазоры запись<->код)

Каждый - «доводить код до заявления vs переформулировать запись». Не блокеры S0966, не ретайры:

1. `virtual-cinema-screen-for-flat-files` (#4, headline) - селектор `vrRenderingMode` мёртв (рендерер не читает CINEMA/FULL_SBS/FULL_OU).
2. `auto-immersive-entry-for-stereo-content` (#17) - `vrAutoImmersive` никем не читается; авто-запуска иммерсива нет.
3. `hand-tracking-input-support` (#13) - hand-tracking управляет навигацией, а не exit-сессией, как гласит запись.
4. `multi-source-input-exit-handler` (#14) - объединены 2 из 3 источников; нативный controller-exit минует grace-handler; `onNativeAction` - мёртвый код.
5. `per-file-stereo-format-override` (#16) - ручной per-file 3D-override не пробрасывается из 2D-плеера в VR-иммерсив.
6. `cylinder-projected-panorama-viewer` (#6) - detection-only; cylinder composition-layer отсутствует, рендер как у 180 hemisphere.
7. `interactive-hud-control-panel` (#10, headline) - «world-locked» неверно: панель head-locked.
8. `180-degree-hemisphere-image-viewer` (#3) - «fisheye per-eye UV crop» без кода dewarp; рендер обычным hemisphere.
9. `360-degree-equirectangular-image-viewer` (#2, minor/wording) - «composition layer» = сфера-меш в projection-слое, не `equirect2` extension.

---

## 6. Открытые вопросы / Owner decisions required (BlockQuestions)

Q1 первоначального драфта разрешён исследованием (§4.1): источники есть для всего, кроме passthrough-снимка. Остаются три решения, которые нельзя вывести из кода - они определяют объём тикета:

1. **Правдивость 25 активных VR-записей ALL_FEATURES.** Store `vr` имеет `SUPPORT_VR_PLAYER=false`, рабочего иммерсива нет (S0773 pending) - но те же возможности могут реально работать в `noLegal` sideload-VR. Нужен owner-вердикт: какие из L178-L200/L349-L350 - честный `active`, какие оверклейм под ретайр/downgrade. Часть проверок требует Quest 3 (device-gated).
2. **Governance Rule 11: механизм правки FEATURES.md.** Rule 11 запрещает править `docs/FEATURES*.md` по тикету - только `/skill-release` из диффа ALL_FEATURES. Строка 76 - курированная агрегация, правка ALL_FEATURES её не перегенерит сама. Как легитимно убрать ложные заявления (особенно passthrough-снимок, у которого нет ALL_FEATURES-источника вовсе): внеплановый `/skill-release`-проход, задокументированное исключение с owner sign-off, или иное?
3. **Согласование с S0773.** Что из перечисленного (cinema screen, HUD, passthrough) реально «coming» и должно остаться как «планируется», а что удалить полностью - определяется статусом epic S0773.

Рекомендация (pending owner): (1) провести отдельный VR-honesty аудит 25 записей на device-attached (Quest) сессии; (2) до тех пор из FEATURES.md строка 76 как минимум убрать passthrough-снимок (нет источника, не существует) через легитимный `/skill-release`-канал. Оба шага - owner sign-off gate, не автономная правка.

**Разрешено (2026-07-11, /spec-quiz):**

1. **RESOLVED** - аудит 25 VR-записей провести из кода сейчас (без Quest), оверклеймы понизить/ретайрнуть в рамках S0966. Отдельный device-тикет не заводить.
2. **RESOLVED** - механизм правки: задокументированное исключение из Rule 11 - прямая правка `docs/FEATURES*.md` с owner sign-off, исключение зафиксировать в спеке. Не ждать штатного `/skill-release`.
3. **RESOLVED / переопределяет рекомендацию** - ни одно из трёх заявлений (cinema screen, head-tracking HUD, passthrough snapshot) НЕ удалять. Владелец: все три будут реализованы, к моменту публикации код будет готов; доки показывают их как готовые. Честность закрывается доведением кода/ALL_FEATURES до заявлений, а не урезанием витрины.

### Quiz decisions (2026-07-11)

- VR-audit: правдивость 25 записей -> Аудит из кода сейчас (owner: проверить по коду в рамках S0966, без отдельного Quest-тикета).
- Rule 11 fix: механизм правки строки 76 -> Задокументированное исключение (owner: прямая правка FEATURES*.md с sign-off, зафиксировать исключение).
- S0773 wording: cinema/HUD/passthrough -> Всё оставить как готовое (owner: всё будет реализовано, код поспеет к публикации; удалять нечего).

### Rule 11 documented exception (authorized 2026-07-11)

- **Exception:** прямая правка `docs/FEATURES.md` + `_RU` + `_UK` в рамках S0966 разрешена в обход штатного `/skill-release`-канала (CLAUDE.md Rule 11), с owner sign-off (decision #2).
- **Scope:** синхронно по EN/RU/UK; только строка 76 (VR-заявления).
- **Применено:** нет. Per decision #3 витрина не урезается, все три заявления остаются как committed-to-publication, поэтому фактических правок `FEATURES*.md` в S0966 не сделано. Исключение зафиксировано как авторизованное, но неиспользованное - активируется, если владелец позже решит переформулировать заявления вместо доведения кода.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Публичный сайт продолжает обещать hand-tracking/passthrough/cinema-mode, которых нет | Средняя | Разочарование пользователей, установивших VR-сборку ради этих фич | Скорее исправить эту запись через легитимный канал |

---

## 8. Влияние на пользователя (docs/FEATURES)

Прямое - это и есть предмет тикета: docs/FEATURES.md + _RU + _UK строка ~76 должна перестать обещать не существующие VR-возможности.

---

## 9. Архитектурные решения (ADR)

ADR нет - решение по устоявшимся паттернам проекта (Rule 11 governance).

---

## 10. Связи с другими спеками

- S0965 (docs-vr-drift-reconcile-quickpath) - источник находки, уже поправил VR_EDITION/HOW_TO/VR_CONTROLS/howto-index под ту же реальность.
- S0773 (vr-cinema-program-separate-player) - определяет, что из перечисленного реально планируется, а что нет.

---

## 11. Критерии готовности (strategic-level)

1. Каждое из трёх заявлений строки 76 (cinema screen, head-tracking HUD, passthrough snapshot) подтверждено рабочим кодом ИЛИ его запись ALL_FEATURES приведена в соответствие реальности к моменту публикации (owner: все три реализуются, витрина не урезается).
   - **Статус (2026-07-11): SATISFIED (по owner-policy).** cinema screen - BACKED core (плоский quad-экран, #4), head-tracking HUD - BACKED (#9/#10/#22/#23), passthrough - кода/записи нет, но per decision #3 остаётся как committed-to-publication. Витрина не урезана (0 правок FEATURES.md). Честность закрывается доведением кода (owner-flags §5.5 / scope S0773), не урезанием.
2. Аудит 25 активных VR-записей ALL_FEATURES из кода выполнен; расхождения записи<->код зафиксированы, оверклеймы понижены/ретайрнуты.
   - **Статус (2026-07-11): SATISFIED.** Аудит 25/25 выполнен (§5.1): 16 BACKED, 8 PARTIAL, 1 ABSENT. Однозначных phantom нет -> 0 ретайров/downgrade (conservative gate §5.4, decision #3); 9 материальных зазоров задокументированы как owner-flag (§5.5) - follow-up, не блокеры.
3. Механизм правки строки 76 - задокументированное исключение из Rule 11 с owner sign-off (зафиксировано в §6), правка FEATURES*.md синхронна по EN/RU/UK.
   - **Статус (2026-07-11): SATISFIED (exception recorded, не применён).** Исключение авторизовано и зафиксировано (§6 «Rule 11 documented exception»). Правок FEATURES*.md не делалось - per decision #3 урезания нет, поэтому синхронная EN/RU/UK-правка не потребовалась.

---

## Last Audit

**Date:** 2026-07-11
**Method:** from-code (без Quest, decision #1); flavor-source-set `app_v2/src/vr` (noLegal-mounted, `SUPPORT_VR_PLAYER=true`) + shared `app_v2/src/main` stereo/detection logic. No build, no device.
**Status set:** Implemented.

**Result:** 25/25 активных записей area «VR & OpenXR» проаудированы (полная таблица §5.1).

- BACKED (backed-now): 16.
- PARTIAL: 8; ABSENT: 1 - все 9 в диспозиции owner-flag (§5.5).
- phantom (ретайр): 0. Мутаций `docs/ALL_FEATURES.jsonl`: 0.

**Rationale for 0 mutations:** decision #3 (keep-all, доведение кода вместо урезания) + conservative gate - ретайр только однозначного phantom, который владелец явно не выпускает; таких нет (вся VR-поверхность owner-committed). Зазоры запись<->код зафиксированы как owner-flag / follow-up (кандидаты в scope epic S0773), не ретайры и не блокеры S0966.

**Passthrough:** «passthrough snapshot capture on Quest 3» (FEATURES.md стр.76) - ни записи ALL_FEATURES, ни кода. Per decision #3 строка витрины остаётся; JSONL-действия нет.

**Rule 11 exception:** авторизовано (decision #2), зафиксировано (§6), не применено (правок FEATURES*.md нет).

**Follow-up для владельца (owner-flags, §5.5):** решение «доводить код до заявления vs переформулировать запись» по 9 записям (материальные - #4 dead `vrRenderingMode`, #17 dead `vrAutoImmersive`, #13 hand-exit, #14 controller-exit bypass + dead `onNativeAction`, #16 override не пробрасывается в VR). ship/retire не требуется (ship решён decision #3).

**Next verify:** отдельный `/spec-check`-проход (позже) сверит таблицу §5.1 с кодом и при желании владельца заведёт code-completion тикеты по owner-flags.
