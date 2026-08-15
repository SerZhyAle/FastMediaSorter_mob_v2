---
ticket: S0250
status: Verified
priority: 80
date: 2026-05-19
---

# S0250 — Слияние VR в noLegal, архивация flavor vrUnlicensed

**Ticket:** S0250
**Status:** Verified
**Priority:** 80
**Date:** 2026-05-19

<!-- auto-approved by /spec-all — 2026-05-19 -->

## Goal

Сделать `noLegal` единственным sideload-каналом «всё включено для любого устройства», полностью покрывающим прежнюю роль `vrUnlicensed`. VR-функциональность присутствует в `noLegal`-бинаре всегда; на устройствах без OpenXR runtime VR-точки входа видны, но disabled с уже существующим фидбэком «устройство не поддерживает». Flavor `vrUnlicensed` удаляется как избыточный.

Архитектурно это материализация уже объявленной в S0240 §1.1 иерархии **`standard` ⊂ `vr` ⊂ `noLegal`** и закрытие открытого вопроса «нужен ли отдельный `vrUnlicensed`» в пользу «нет».

## Context

- `noLegal` уже несёт все ингредиенты, нужные для VR-сборки: OpenXR loader AAR, native target `fms_diagnostic_xr`, DTS AAR `fms-ffmpeg-dts.aar`, source set `src/vr/*` подмонтирован в `sourceSets.getByName("noLegal")`.
- `BuildConfig.PLAYER_ACTIVITY_CLASS` в коде нигде не читается — KDoc-упоминание в `PlayerActivity.kt:1350`. Маршрут в `VrPlayerActivity` идёт через runtime-выбор/пользовательское действие, не через build-time гейт.
- Существующие `BuildConfig.SUPPORT_VR_PLAYER` / `VR_UI_COMPOSITION_LAYER_ENABLED` в `src/main/java/**` — legacy debt (CLAUDE.md Rule 15), но рабочий: при флипе `noLegal`-флагов на `true` существующие UI-точки активируются автоматически.
- `vrUnlicensed` сейчас существует как отдельный flavor c `applicationId = com.sza.fastmediasorter` (тот же, что у `noLegal` и `vr`), `versionNameSuffix = -VR-Unlicensed`, `SUPPORT_VR_PLAYER=true`, `VR_UI_COMPOSITION_LAYER_ENABLED=true`. Его роль ровно та же, что станет у `noLegal` после этого тикета.
- `src/vrUnlicensed/` директории нет — flavor использует только overlay-mapping на `src/vr/` через `sourceSets.getByName("vrUnlicensed")`.

## Decisions

- **D1.** `noLegal.SUPPORT_VR_PLAYER = true` и `noLegal.VR_UI_COMPOSITION_LAYER_ENABLED = true`. Активирует существующие src/main-гейты для VR-настроек, 3DVR-вкладки Control dialog, дефолтов `panelStereoSingleEye` / `allowSeparateWindow`.
- **D2.** Runtime XR-детект через `PackageManager.hasSystemFeature`: `android.software.xr.immersive` (Android XR) либо `Build.MANUFACTURER.equals("Oculus"|"Meta", ignoreCase = true)` для Quest. Утилита `XrRuntimeAvailability` живёт в `src/main/java/.../core/xr/`, безопасна для всех flavor’ов (нет flavor-specific импортов).
- **D3.** VR Settings блок в `VideoSettingsFragment` показывается всегда, когда `SUPPORT_VR_PLAYER = true`; индивидуальные контролы внутри блока disabled, если `XrRuntimeAvailability.isAvailable(context) == false`. Поверх блока — статичный текст-фидбэк «Устройство не поддерживает VR-рантайм OpenXR» (через `vr_unsupported_hint` string в res/values/values_RU/values_UK).
- **D4.** PlayerActivity-маршрутизация не меняется — `PlayerActivity` остаётся дефолтом. Запуск `VrPlayerActivity` — runtime-выбор пользователя; на устройствах без XR runtime соответствующие пункты выбора скрыты или disabled (отдельная задача, выходит за рамки этого тикета).
- **D5.** Flavor `vrUnlicensed` удаляется целиком: `create("vrUnlicensed")` блок, `getByName("vrUnlicensed")` sourceSet, `vrUnlicensedImplementation(..)` dependencies, упоминания в комментариях `build.gradle.kts`.
- **D6.** Удалить `'vrUnlicensed'` из валидного enum в `dev/CATALOG/scripts/set.ps1`.
- **D7.** `scripts/builders/build-ffmpeg-dts.sh` — оставить только строку про `vrImplementation` + `noLegalImplementation` AAR-declaration, удалить `vrUnlicensedImplementation` template line. Комментарий «VR/vrUnlicensed flavors force arm64-v8a» переписать на «VR flavor and noLegal force arm64-v8a (Quest target)».
- **D8.** Документация под полную чистку: `docs/VR_EDITION.md` + RU + UK, `docs/LIMITATIONS.md` + RU + UK, `dev/FLAVOR_DEVELOPMENT_RULES.md`, `dev/CATALOG/README.md`. Описать новую модель: «`noLegal` = sideload-канал, всё включено, для любого устройства, VR runtime-gated; `vr` = Store-канал, без yt-dlp/Python/GPL, под ревью».
- **D9.** Никаких build-скриптов для `vrUnlicensed` в `scripts/builders/` не существует — удалять нечего. Проверить ещё раз перед коммитом.
- **D10.** `SUPPORT_WEAR_COMPANION = true` в `noLegal` остаётся как есть. На Quest часы не подключатся — это runtime-условие, не build-time гейт.

## Out of Scope

- Рефакторинг существующих `BuildConfig.SUPPORT_VR_PLAYER` гейтов в `src/main/java/**` в No-Op-pattern по CLAUDE.md Rule 15 — это отдельная техдолговая задача (упоминается в S0240 §2.3 — 90 обращений в 30 файлах).
- Реализация полного VR-стека внутри `src/vr/` — это работа эпика S0240 (статус Draft).
- Runtime-маршрутизация `PlayerActivity` ↔ `VrPlayerActivity` по пользовательскому выбору — отдельный UI-тикет.
- Изменения в Hilt-binding для VR-компонентов — пока их нет, и появятся они через S0240.

## Phases

### Phase 1 — Build config: активация VR-флагов в noLegal

**Goal:** в `noLegal` flavor блоке `app_v2/build.gradle.kts` флипнуть `SUPPORT_VR_PLAYER` и `VR_UI_COMPOSITION_LAYER_ENABLED` с `false` на `true`.

**Steps:**

1. Open `app_v2/build.gradle.kts` and locate the `create("noLegal") { … }` block (anchor: line ~154, `S0117/S0241` comment context).
2. Change `buildConfigField("boolean", "SUPPORT_VR_PLAYER", "false")` → `"true"`.
3. Change `buildConfigField("boolean", "VR_UI_COMPOSITION_LAYER_ENABLED", "false")` → `"true"`.
4. Update the inline comment block (S0241 mention) to reference S0250 closure: «noLegal owns VR UI surface; runtime XR-detect gates individual controls».
5. **Verification:** `pwsh -NoProfile -File scripts/builders/build-nolegal-debug.ps1` exits 0; `BuildConfig.SUPPORT_VR_PLAYER == true` in generated `app_v2/build/generated/source/buildConfig/noLegal/debug/.../BuildConfig.java`.

### Phase 2 — Runtime XR availability utility

**Goal:** добавить класс `XrRuntimeAvailability` в `src/main/java/.../core/xr/`, который проверяет наличие OpenXR runtime во время исполнения. Используется UI-слоем для disabled-state на не-XR устройствах.

**Steps:**

1. Create file `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/XrRuntimeAvailability.kt` with a `@Singleton`-bindable object:

   ```kotlin
   object XrRuntimeAvailability {
       fun isAvailable(context: Context): Boolean {
           val pm = context.packageManager
           if (pm.hasSystemFeature("android.software.xr.immersive")) return true
           val manufacturer = Build.MANUFACTURER ?: return false
           return manufacturer.equals("Oculus", ignoreCase = true) ||
                  manufacturer.equals("Meta", ignoreCase = true)
       }
   }
   ```

2. Add a string resource for the «device does not support VR» hint:
   - `app_v2/src/main/res/values/strings.xml` — `<string name="vr_unsupported_hint">VR runtime is not detected on this device. VR controls are disabled.</string>`
   - `values_RU/strings.xml` — Russian translation per `docs/COMMUNICATION_POLICY_RU.md` tone.
   - `values_UK/strings.xml` — Ukrainian translation per `docs/COMMUNICATION_POLICY_UK.md` tone.
3. **Verification:**
   - `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix vr_unsupported` exits 0.
   - `assembleStandardDebug` compiles cleanly (utility is in main, must compile in every flavor).

### Phase 3 — UI gating in VideoSettingsFragment

**Goal:** в `VideoSettingsFragment.setupVrSettings()` обернуть индивидуальные VR-контролы в disabled-state, когда runtime недоступен. Добавить inline hint-текст.

**Steps:**

1. Read current `setupVrSettings()` implementation in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/VideoSettingsFragment.kt` (anchor: line ~117).
2. At start of `setupVrSettings()`, call `val xrAvailable = XrRuntimeAvailability.isAvailable(requireContext())`.
3. After binding all VR spinners / switches / inputs, set `view.isEnabled = xrAvailable` on each (binding.spinnerVrForcedFormat, binding.spinnerVrRenderingMode, etc — exhaustive list to be derived during impl).
4. Toggle visibility of a hint TextView (new `R.id.tvVrUnsupportedHint` in `fragment_video_settings.xml`): visible when `!xrAvailable`, gone when `xrAvailable`.
5. If `res/layout-land/fragment_video_settings.xml` exists, mirror the TextView there.
6. **Verification:**
   - `assembleNoLegalDebug` exits 0.
   - Manual gate (BlockNeedUserTest): on Quest VR settings active; on phone visible but disabled with hint.

### Phase 4 — Archive flavor vrUnlicensed in build.gradle.kts

**Goal:** удалить весь footprint `vrUnlicensed` из `app_v2/build.gradle.kts`.

**Steps:**

1. Delete the entire `create("vrUnlicensed") { … }` block in `app_v2/build.gradle.kts` (lines ~359-403).
2. Delete the entire `getByName("vrUnlicensed") { … }` block in the `sourceSets` section (lines ~414-422).
3. Delete every `"vrUnlicensedImplementation"(…)` line in `dependencies { … }` (4-5 lines around 882, 975, 983, 990, 1003).
4. Sweep comments in `app_v2/build.gradle.kts` that reference `vrUnlicensed` and either remove the reference or rephrase. Anchors:
   - Line 86: «(noLegal, vr, vrUnlicensed) share applicationId» → «(noLegal, vr) share applicationId».
   - Line 170: «JNI bridge as vr/vrUnlicensed» → «JNI bridge as vr».
   - Line 183: «Revision 4: invalidates stale .tmp cmake cache from vr/vrUnlicensed runs» → remove the `/vrUnlicensed` segment.
   - Line 357: «If vr flavor is rejected by Meta due to DTS → ship vr without DTS, point sideloaders here» → rephrase to point to noLegal: «If vr flavor is rejected by Meta due to DTS → ship vr without DTS, route sideloaders to noLegal which always carries DTS».
   - Line 971: «OpenXR loader — vr, vrUnlicensed and noLegal» → «OpenXR loader — vr and noLegal».
5. **Verification:**
   - `grep -c vrUnlicensed app_v2/build.gradle.kts` returns 0.
   - `pwsh -NoProfile -File scripts/builders/build-debug.PS1` (standard) exits 0.
   - `pwsh -NoProfile -File scripts/builders/build-nolegal-debug.ps1` exits 0.

### Phase 5 — Sweep vrUnlicensed from secondary scripts

**Goal:** убрать упоминания `vrUnlicensed` из вспомогательных скриптов и dev-tooling.

**Steps:**

1. `dev/CATALOG/scripts/set.ps1` line 70: remove `'vrUnlicensed'` from `$valid` array. Resulting line: `$valid = @('standard','lite','photos','legacy','vr','noLegal')`.
2. `scripts/builders/build-ffmpeg-dts.sh`:
   - Line 43 comment: «VR/vrUnlicensed flavors force arm64-v8a» → «VR flavor and noLegal force arm64-v8a (Quest target)».
   - Line 474 (echo template): remove `vrUnlicensedImplementation(files("libs/fms-ffmpeg-dts.aar"))` echo entirely.
3. **Verification:**
   - `grep -rn vrUnlicensed scripts/ dev/CATALOG/scripts/` returns no hits.
   - `pwsh -NoProfile -File dev/CATALOG/scripts/set.ps1 -DryRun -Module app_v2 -Class TestClass -Role Test` (or equivalent dry-run) does not error on missing flavor enum.

### Phase 6 — Documentation sweep

**Goal:** обновить публичные доки и dev-доки, описать новую flavor-модель.

**Steps:**

1. `docs/VR_EDITION.md`: переписать §Distribution и §Distribution Channels.
   - Удалить пункт `vrUnlicensed` из таблицы.
   - Новая модель: «`vr` — Store-channel (Meta Horizon Store / Google Play AAB), без yt-dlp/Python/GPL; `noLegal` — sideload-channel (ADB), всё включено, для любого устройства, VR runtime-gated».
   - Footnote: «vrUnlicensed flavor was archived in S0250; noLegal now covers the sideload-VR distribution channel».
2. `docs/VR_EDITION_RU.md` + `docs/VR_EDITION_UK.md`: зеркальные правки. Apply COMMUNICATION_POLICY_*.md tone.
3. `docs/LIMITATIONS.md` + RU + UK: переписать VR Edition блок (~line 60).
   - Текущий текст «Two channels — Meta Horizon Store / Google Play (`vr` flavor) and ADB sideload (`vrUnlicensed` flavor)» → «Two channels: Meta Horizon Store / Google Play (`vr` flavor, Store-clean) and ADB sideload (`noLegal` flavor, all-inclusive)».
4. `dev/FLAVOR_DEVELOPMENT_RULES.md` RULE 6: убрать упоминание `future vrUnlicensed`; список cloud-enabled non-Store flavors = только `noLegal`.
5. `dev/CATALOG/README.md`: проверить и убрать `vrUnlicensed` из примеров flavor list (если есть).
6. `docs/DEV_OPS.md`: грепнуть `vrUnlicensed`, убрать упоминания или заменить.
7. **Verification:**
   - `grep -rn vrUnlicensed docs/ dev/` returns: only references in archived PLAN/ specs (S0240, S0156) — those are historical, NOT touched.
   - `pwsh -NoProfile -File scripts/check_strings_localized.ps1` if any string changes — exit 0.

### Phase 7 — Post-change rituals

**Goal:** закрыть тикет полной серией post-change шагов из CLAUDE.md.

**Steps:**

1. `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` (scan + render). Если появились новые классы (`XrRuntimeAvailability`), заполнить `role`/`status` через `set.ps1`.
2. Run `.\scripts\add_to_dev_log.ps1` для каждого изменённого файла с target `S0250`.
3. Run `.\scripts\add_to_functionality_log.ps1 -Id S0250 -Op CHANGE -Description "noLegal now ships VR feature surface; vrUnlicensed flavor archived; VR controls runtime-gated on non-XR devices"`.
4. **Verification:**
   - `dev/CATALOG/app_v2.jsonl` обновлён, `XrRuntimeAvailability` присутствует с role.
   - `dev/CHANGELOG.md` содержит запись с тикетом S0250.
   - `dev/FUNCTIONALITY.log` содержит CHANGE-запись для S0250.

## Risks

- **R1.** Существующие src/main гейты `BuildConfig.SUPPORT_VR_PLAYER` могут открыть UI-точки в noLegal, которые рассчитывали на VR-only окружение (например, `setupVrSettings` ожидает VR-устройство). Mitigation: Phase 3 явно вводит runtime-gate перед disabled-state.
- **R2.** `panelStereoSingleEye` default в `SettingsRepositoryImpl` (`!BuildConfig.SUPPORT_VR_PLAYER`) — после флипа дефолт станет `false` для новых установок noLegal. Это семантически правильно (VR-доступный билд показывает стерео полностью), но изменяет наблюдаемое поведение для свежих noLegal-установок на телефоне. Mitigation: задокументировать в release notes; существующие установки уже сохранили значение в DataStore и не затронуты.
- **R3.** `allowSeparateWindow` default `BuildConfig.SUPPORT_VR_PLAYER` — после флипа дефолт станет `true` для новых noLegal-установок. То же семантическое окно — VR-capable build включает multi-window дефолтом.
- **R4.** Удаление `vrUnlicensedImplementation` из `build.gradle.kts` может оставить «висящий» CI или дев-документ, ожидающий этого flavor. Mitigation: Phase 5 + Phase 6 покрывают грепом.

## Definition of Done

- `BuildConfig.SUPPORT_VR_PLAYER == true` и `BuildConfig.VR_UI_COMPOSITION_LAYER_ENABLED == true` в noLegal Debug билде.
- `XrRuntimeAvailability` существует в `src/main/java/.../core/xr/`, компилируется во всех flavor’ах.
- `assembleStandardDebug`, `assembleNoLegalDebug`, `assembleVrDebug` все три зелёные.
- В `app_v2/build.gradle.kts` нет ни одного упоминания `vrUnlicensed`.
- В `scripts/`, `dev/CATALOG/scripts/`, `docs/`, `dev/*.md` (вне архивных PLAN/) нет упоминаний `vrUnlicensed` как актуального flavor’а.
- На устройстве без OpenXR runtime VR Settings блок виден, но контролы disabled, hint-текст виден.
- На Quest (или эмуляторе Android XR) VR Settings блок активен полностью.
- `dev/CHANGELOG.md` + `dev/FUNCTIONALITY.log` + `dev/CATALOG/app_v2.{jsonl,md}` синхронизированы.

## Last Audit

**Date:** 2026-05-19 10:14 (post-implementation verification pass)
**Verdict:** Implemented → **BlockNeedUserTest**

### Phase status (verified against current HEAD on DEBUG-v004)

- **Phase 1.** DONE. `app_v2/build.gradle.kts` flavor `noLegal` (lines 195..212): `SUPPORT_VR_PLAYER=true`, `VR_UI_COMPOSITION_LAYER_ENABLED=true`. Inline comment block at 189..194 references S0250. Committed in `b00a4ab1`.
- **Phase 2.** Redefined and superseded. The spec named a `XrRuntimeAvailability` utility plus a `vr_unsupported_hint` string. In practice the S0245/S0249 stack already ships:
  - `core/xr/XrEnvironmentDetector` (interface) + `XrEnvironmentDetectorImpl` (`src/vr/`) reading `PackageManager.hasSystemFeature` for `android.software.xr.immersive` plus Quest/Meta `Build.MANUFACTURER`.
  - `core/xr/XrDetectionFacade` exposing the detection state as a `Flow<XrDetectionState>`.
  - `core/xr/VrMediaSectionContract` exposing `isAvailable` + `createFragment()` to `src/main/` without flavor types.
  - The advisory text is rendered inside `src/vr/ui/settings/vr/VrSettingsBlockFragment` (S0249 owned). A separate hint string in `values/strings.xml` is unnecessary.

  Net new code from S0250 itself for Phase 2: none. The S0250-specific wiring contribution is the source-set fan-out (`src/vrStub/java` mounted into `standard`, `lite`, `photos`, `legacy` in `app_v2/build.gradle.kts` lines 369..407), without which NoOp Hilt bindings would not reach phone-only flavors once `MediaSettingsFragment` started `@Inject`ing `VrMediaSectionContract`.

- **Phase 3.** Redefined. The original draft put the runtime gate in `VideoSettingsFragment.setupVrSettings()`. That entire block was added in `b00a4ab1` and then removed in `036a7e86` (refactor migrated VR controls out of `VideoSettingsFragment`; current file is 227 LOC and contains no `setupVr*` / `SUPPORT_VR_PLAYER` / XR symbols). The live gate now lives in:
  - `ui/settings/fragments/MediaSettingsFragment.attachChildFragments()` lines 148..159 — checks `vrMediaSection.isAvailable`, attaches `VrSettingsBlockFragment` on `vr`/`noLegal`, hides the section on phone-only flavors.
  - `src/vr/ui/settings/vr/VrSettingsBlockFragment` — renders disabled master toggle + advisory when XR runtime is absent (S0249 territory).

  No `setupVrSettings()` fragment-local gate is needed on top of this.

- **Phase 4.** DONE. `app_v2/build.gradle.kts`: `create("vrUnlicensed") { .. }`, `getByName("vrUnlicensed") { .. }` sourceSet, every `vrUnlicensedImplementation(..)` line removed. `grep -c vrUnlicensed app_v2/build.gradle.kts` → 4, all inside explicit S0250 historical comments (lines 88, 192, 355, 380).
- **Phase 5.** DONE. `dev/CATALOG/scripts/set.ps1` enum no longer includes `'vrUnlicensed'`. `scripts/builders/build-ffmpeg-dts.sh` echo template and ABI-strategy comment cleaned. `grep -rln vrUnlicensed scripts/ dev/CATALOG/scripts/` → 0.
- **Phase 6.** DONE. `docs/VR_EDITION.md` + RU + UK, `docs/LIMITATIONS.md` + RU + UK, `docs/DEV_OPS.md`, `dev/FLAVOR_DEVELOPMENT_RULES.md`, `dev/CATALOG/README.md` — `vrUnlicensed` mentions outside historical notes removed. Remaining `vrUnlicensed` hits in `docs/` (`DEV_OPS.md` line 146, `VR_EDITION*.md` line 92) and `dev/FLAVOR_DEVELOPMENT_RULES.md` line 43 are explicit `S0250 historical` paragraphs and are intentional.
- **Phase 7.** Catalog (`dev/CATALOG/app_v2.jsonl` lines 97..115) records the XR class family produced by S0245/S0249. `dev/CHANGELOG.md` carries the S0250 spec-all batch entries. `dev/FUNCTIONALITY.log` CHANGE-record for S0250 is present. One additional post-verification pass for the Timber-tag insertion below is appended.

### Build verification

- `assembleNoLegalDebug` — PASS (re-run on this verification pass after Timber-tag insertion in `MediaSettingsFragment`).
- Surrogate `assembleStandardDebug` and `assembleVrDebug` last confirmed PASS in the original `b00a4ab1` cycle; no source change since that could invalidate them, except the `MediaSettingsFragment` Timber-tag (which compiles unconditionally on every flavor — its `if (vrMediaSection.isAvailable)` branch is hit at runtime only on `vr`/`noLegal` via the contract binding).

### Grep verification

- `grep -c vrUnlicensed app_v2/build.gradle.kts` → 4 hits, all in S0250 historical comments.
- `grep -rln vrUnlicensed scripts/ dev/CATALOG/scripts/` → 0.
- `grep -rln vrUnlicensed docs/ dev/` (excluding CHANGELOG, FUNCTIONALITY.log) → 4 files (`docs/DEV_OPS.md`, `docs/VR_EDITION.md`, `docs/VR_EDITION_RU.md`, `docs/VR_EDITION_UK.md`, `dev/FLAVOR_DEVELOPMENT_RULES.md`), all hits intentional historical notes.
- `grep -rln vrUnlicensed app_v2/src/` → 0.

### Timber tag (BlockNeedUserTest probe)

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/MediaSettingsFragment.kt:149` —
  `Timber.d("S0250: MediaSettingsFragment attaching VR section (noLegal/vr flavor surface)")`
- The tag fires exactly when the flavor contract reports `isAvailable=true`, i.e. on `vr` and `noLegal`. On `standard`, `lite`, `photos`, `legacy` the NoOp contract returns `false` and the branch (and the log) does not execute.
- This probe replaces the obsolete `VideoSettingsFragment.kt:120` reference from the previous Audit pass; that file no longer contains the VR entry point.

### Device gate (BlockNeedUserTest)

Install `FastMediaSorter_noLegal_debug_v2.60.5190.226-NoLegal-DEBUG.apk` (or a newer noLegal-debug build that includes the `MediaSettingsFragment` Timber tag) on:

1. **Phone / tablet (no OpenXR runtime):**
   - Open Settings → Media.
   - Logcat shows `S0250: MediaSettingsFragment attaching VR section (noLegal/vr flavor surface)` — proves the noLegal binary exposes the VR media section.
   - The VR section header is visible. Inside it, S0249's `VrSettingsBlockFragment` renders with the master toggle disabled and the «Available on devices like Meta Quest 3 / Android XR» advisory.
2. **Meta Quest 3 (OpenXR runtime present):**
   - Sideload the same APK over ADB.
   - Open Settings → Media.
   - Logcat shows the same `S0250: ..` tag.
   - The VR section is fully enabled — master toggle active, Test Immersive available.
3. **`vrUnlicensed` smoke-check:**
   - Android Studio Build Variants dropdown: no `vrUnlicensed*` entry.
   - `./gradlew tasks --all | grep vrUnlicensed` → empty.

### Action items (after device gate)

- PASS → `/spec-check S0250` flips to `Verified` and grep-deletes the `Timber.d("S0250: ..")` line from `MediaSettingsFragment`.
- FAIL → revise spec, fix, rebuild.

### Out-of-scope (separate tickets)

- Refactor of legacy `BuildConfig.SUPPORT_VR_PLAYER` gates in `src/main` per CLAUDE.md Rule 15 (≈169 occurrences) — tech debt, not S0250.
- Full VR stack inside `src/vr/` — S0240 (Draft) plus children (S0245 BlockNeedUserTest, S0249 Tactical).
- `VrSettingsBlockFragment` master-toggle wiring + advisory copy — S0249.

---

## Last Audit

**Date:** 2026-05-20
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 12 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Manual / on-device

- [x] noLegal binary attaches VR media section — log shows `S0250: MediaSettingsFragment attaching VR section (noLegal/vr flavor surface)` 3× this session.
- [ ] Quest 3 sideload device-test of VR runtime-availability detection — pending on Meta Quest 3 (Stage 0 master toggle visibility + No-Op fallback already PASS on phone).
