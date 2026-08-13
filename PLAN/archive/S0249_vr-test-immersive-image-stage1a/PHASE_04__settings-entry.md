# Phase 04 - Settings Entry

**Strategic spec:** [`../S0249_vr-test-immersive-image-stage1a.md`](../S0249_vr-test-immersive-image-stage1a.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Done
**Depends on:** Phase 01, Phase 03
**Blocks:** Phase 05
**Steps done:** 8 / 8
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Rework the S0245 VR Settings block into the final shape required by /ui-clarify 2026-05-19:
- Rename block from `VR` to `Управление 3D-VR` (RU primary, EN/UK localized).
- Move the block into the Settings Media section as a collapsible group.
- Make the block always visible in `vr` / `noLegal` builds, regardless of XR runtime presence.
- Add an advisory text view above the master toggle, visible only when XR runtime is absent (`XrDetectionFacade.state == NONE`).
- Make the master toggle non-interactive (disabled) on non-XR devices.
- Add the `Test Immersive` action row inside the block, visible only when the master toggle is ON.
- Route the action through `XrEntryGateway`.

---

## Prerequisites

- [ ] Phase 01 is Done.
- [ ] Phase 03 is Done.
- [ ] UI restructure decisions from `/ui-clarify` 2026-05-19 reflected in strategic spec §2 + §3.2 + §6 + ADR-6.
- [ ] `docs/COMMUNICATION_POLICY.md` section 6 is checked before adding strings.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_revised_media.xml` | Modified | <= 220 |
| `app_v2/src/main/res/layout-land/fragment_settings_revised_media.xml` | Modified | <= 220 |
| `app_v2/src/main/res/values/strings.xml` | Modified | <= 80 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | <= 80 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | <= 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedMediaSettingsFragment.kt` | Modified | <= 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/di/SettingsTabExtensionModule.kt` | Modified (extension wiring) | <= 60 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/settings/vr/VrSettingsBlockExtension.kt` | New (VR-flavor extension binding to the main settings fragment) | <= 240 |
| `app_v2/src/main/res/layout/include_vr_settings_block.xml` | New (block content layout — advisory + toggle + button) | <= 180 |
| `app_v2/src/main/res/layout-land/include_vr_settings_block.xml` | New (landscape variant of the include) | <= 180 |

> **Target host:** `RevisedMediaSettingsFragment` (the NEW settings family). Old `MediaSettingsFragment` left untouched (deprecated, on path to removal).
> **Landscape coverage:** **both** `layout/fragment_settings_revised_media.xml` AND `layout-land/fragment_settings_revised_media.xml` are edited atomically (CLAUDE.md Rule 12). New `include_vr_settings_block.xml` ships in both layout and layout-land folders.
> **Flavor isolation (Rule 15):** include layouts live in `src/main/res/` because they ship as referenceable resources in all flavors; their content is empty/hidden by default in non-VR flavors. VR-specific code wiring (advisory bind, toggle disable, button click) lives in `src/vr/` via `VrSettingsBlockExtension`. No `BuildConfig.IS_*_FLAVOR` checks in `src/main/`.

---

## Steps

### Step 04.A1 - Add string resources (block title, advisory, button label, toasts)

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** start of phase

**Prompt for developer:**

> Add the following string keys in all three locale files (final wordings locked in strategic spec §6 item 13). All keys go to `src/main/res/values*/strings.xml` (not `src/vr/`) because the layout that consumes them lives in `src/main/res/layout/`:
>
> - `vr_settings_block_title` — EN `3D-VR controls` / RU `Управление 3D-VR` / UK `Керування 3D-VR`
> - `vr_settings_xr_unavailable_advisory` — EN `Available on devices such as Meta Quest 3 and Android XR.` / RU `Доступно на устройствах типа Meta Quest 3, Android XR.` / UK `Доступно на пристроях типу Meta Quest 3, Android XR.`
> - `vr_settings_test_immersive_label` — EN `Test Immersive` / RU `Проверить иммерс` / UK `Перевірити імерс`
> - `vr_settings_test_immersive_content_description` — EN `Open a sample 360° image in immersive mode.` / RU `Открыть пробное 360°-изображение в immerse-режиме.` / UK `Відкрити пробне 360°-зображення в immerse-режимі.`
> - `vr_settings_test_immersive_init_failure_toast` — EN `Cannot start VR. Check that your headset is connected.` / RU `Не удалось запустить VR. Проверь, что гарнитура подключена.` / UK `Не вдалося запустити VR. Перевір, що гарнітура під'єднана.`
> - `vr_settings_test_immersive_runtime_loss_toast` — EN `VR session ended unexpectedly.` / RU `VR-сессия завершилась неожиданно.` / UK `VR-сесія завершилась несподівано.`
>
> Master toggle string (`vr_settings_enable_3d_label` or equivalent from S0245) is reused unchanged.

**Verification:**

- `Grep` - each new key appears in all three `app_v2/src/main/res/values*/strings.xml` files.
- `PowerShell` - `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "vr_settings_"` exits 0.
- `Grep` - no `vr_settings_*` keys appear in `app_v2/src/vr/res/values*/strings.xml` (all migrated to `src/main/`).

**Status:** `[x]` done (2026-05-19)

---

### Step 04.A2 - Add `include_vr_settings_block.xml` (portrait + landscape) and embed into Revised Media

**Files:** `app_v2/src/main/res/layout/include_vr_settings_block.xml` (NEW), `app_v2/src/main/res/layout-land/include_vr_settings_block.xml` (NEW), `app_v2/src/main/res/layout/fragment_settings_revised_media.xml` (MODIFIED), `app_v2/src/main/res/layout-land/fragment_settings_revised_media.xml` (MODIFIED)
**Depends on:** Step 04.A1

**Prompt for developer:**

> Create `include_vr_settings_block.xml` in **both** `layout/` and `layout-land/`. The include defines the collapsible group with header `@string/vr_settings_block_title`, default expanded state, and three child views (advisory `TextView`, master toggle reused from S0245, button row `testImmersiveRow`). Mark all interactive children `focusable="true"`, `clickable="true"`, ≥ 48dp touch target. Apply Material expand/collapse semantics consistent with other Revised settings groups (consult neighbouring sections in `fragment_settings_revised_media.xml` for the project's chosen collapsible widget — DO NOT introduce a new widget if a project pattern already exists). Place the include in the Media fragment immediately after the Video block, in both portrait and landscape layouts. Edit both layout-land and layout in a single change-set per CLAUDE.md Rule 12.

**Verification:**

- `Glob` - `app_v2/src/main/res/layout/include_vr_settings_block.xml` exists.
- `Glob` - `app_v2/src/main/res/layout-land/include_vr_settings_block.xml` exists.
- `Grep` - `<include ... layout="@layout/include_vr_settings_block"` appears in `fragment_settings_revised_media.xml` (portrait).
- `Grep` - `<include ... layout="@layout/include_vr_settings_block"` appears in `layout-land/fragment_settings_revised_media.xml` (landscape).
- `Grep` - the include reuses the same collapsible widget used by adjacent Media sub-groups (verified by widget-name match).
- `Grep` - `android:focusable="true"` and `android:clickable="true"` appear on all interactive rows inside the include.

**Status:** `[x]` done (2026-05-19)

---

### Step 04.A3 - Create `VrSettingsBlockExtension` in `src/vr/` to wire advisory + disabled-toggle + button click

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/settings/vr/VrSettingsBlockExtension.kt` (NEW), `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/di/SettingsTabExtensionModule.kt` (MODIFIED — VR-flavor binding)
**Depends on:** Step 04.A2

**Prompt for developer:**

> Create `VrSettingsBlockExtension` in the `vr` flavor source set. It:
> - Observes `XrDetectionFacade` state (already injected via Hilt per Phase 01).
> - When state == NONE: shows the advisory `TextView` (`View.VISIBLE`), disables the master toggle (`isEnabled = false`), hides the `testImmersiveRow` (`View.GONE`).
> - When state == AVAILABLE_DISABLED_BY_USER: hides advisory (`View.GONE`), enables toggle, hides button.
> - When state == AVAILABLE_ENABLED: hides advisory, enables toggle, shows button.
> - Wires button click → `XrEntryGateway.enterDiagnosticImage()` (Phase 01 contract).
> - Maps `XrEntryResult` to toast strings (`vr_settings_test_immersive_init_failure_toast`, `vr_settings_test_immersive_runtime_loss_toast`).
>
> In `src/main/`, `SettingsTabExtensionModule` defines an extension point that `RevisedMediaSettingsFragment` consults for optional injectable views/bindings. The `vr`-flavor module supplies `VrSettingsBlockExtension` as the active binding. In non-VR flavors, the extension is a no-op (default in `src/main/` or `src/standard/`).

**Verification:**

- `Glob` - `app_v2/src/vr/java/com/sza/fastmediasorter/ui/settings/vr/VrSettingsBlockExtension.kt` exists.
- `Grep` - `XrDetectionFacade` is observed in `VrSettingsBlockExtension.kt`.
- `Grep` - `XrEntryGateway` is called from `VrSettingsBlockExtension.kt` on button click.
- `Grep` - three view-state branches (NONE / AVAILABLE_DISABLED_BY_USER / AVAILABLE_ENABLED) are present in `VrSettingsBlockExtension.kt`.
- `Grep` - no `BuildConfig.IS_*_FLAVOR` or `BuildConfig.SUPPORT_*` check exists in `src/main/java/.../RevisedMediaSettingsFragment.kt` or `SettingsTabExtensionModule.kt` (Rule 15).
- `Grep` - `Log.d(` returns zero hits in any new or modified file (Timber-only).
- `Build` - `assembleNoLegalDebug` and `assembleVrDebug` succeed; `assembleStandardDebug` succeeds with VR-extension defaulting to no-op.

**Status:** `[x]` done (2026-05-19)

---

### Step 04.4 - Wire master-toggle ON state to button visibility + gateway call

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/settings/vr/VrSettingsBlockExtension.kt` (MODIFIED — refinement of state branches authored in Step 04.A3), `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedMediaSettingsFragment.kt` (MODIFIED — extension hookup)
**Depends on:** Step 04.A3

**Prompt for developer:**

> In `RevisedMediaSettingsFragment`, look up the optional `VrSettingsBlockExtension` (resolved via `SettingsTabExtensionModule`). If present (vr/noLegal builds): attach it to the `include_vr_settings_block` views; if absent (standard/lite/photos/legacy): nothing happens — the include exists in layout but stays inert. The extension owns the lifecycle observation; the fragment only forwards onViewCreated / onDestroyView calls. Keep the fragment as orchestration-only — all VR logic lives in the extension.

**Verification:**

- `Grep` - `RevisedMediaSettingsFragment.kt` references an extension lookup (e.g. injected `Optional<SettingsTabExtension>` or `Set<...>`).
- `Grep` - no direct `Xr*` type reference exists in `RevisedMediaSettingsFragment.kt` (all hidden behind the extension abstraction).
- `Grep` - `VrSettingsBlockExtension` overrides onAttach/onDetach (or equivalent lifecycle hooks) consuming the include views.
- `Build` - `assembleNoLegalDebug`, `assembleVrDebug`, `assembleStandardDebug` all succeed.

**Status:** `[x]` done (2026-05-19)

---

### Step 04.5 - Strings audit + remove S0245 leftovers

**Files:** any `app_v2/src/vr/res/values*/strings.xml` carrying old `vr_settings_*` keys; any `app_v2/src/vr/res/layout/fragment_vr_settings*.xml` from S0245.
**Depends on:** Step 04.4

**Prompt for developer:**

> S0245 may have shipped `vr_settings_*` strings inside `src/vr/res/values*/strings.xml` and a `fragment_vr_settings.xml` layout. Since S0249 retargets to `RevisedMediaSettingsFragment` and moves strings to `src/main/res/values*/strings.xml`, these leftovers must be removed (or migrated) — keeping both copies risks divergence and lint warnings. If the S0245 fragment is referenced from somewhere, replace the reference with the new extension hookup before deleting. Run the catalog scan after Kotlin changes.

**Verification:**

- `Grep` - `vr_settings_block_title`, `vr_settings_xr_unavailable_advisory`, `vr_settings_test_immersive_*` keys exist **only** under `app_v2/src/main/res/values*/strings.xml`.
- `Grep` - no `fragment_vr_settings.xml` references remain in any `.kt` file.
- `Glob` - `app_v2/src/vr/res/layout/fragment_vr_settings.xml` is absent (or intentionally kept and documented).
- `PowerShell` - `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "vr_settings_"` exits 0.
- `PowerShell` - `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` followed by `render.ps1` succeed with no errors.

**Status:** `[x]` done (2026-05-19)

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x]` done (incl. Step 04.A1, 04.A2, 04.A3).
- [ ] Block title in `vr` / `noLegal`-сборке = `Управление 3D-VR` (RU), localized in EN/UK.
- [ ] Block is located in the Settings Media section as a collapsible group, always visible.
- [ ] On a phone (no XR runtime) the block shows advisory text + disabled master toggle + no `Test Immersive` button.
- [ ] On Quest 3 / Android XR the block shows enabled master toggle + (when ON) `Test Immersive` button.
- [ ] In `standard` / `lite` / `photos` / `legacy` APK no `vr_settings_*` strings or VR fragment classes are loaded.
- [ ] Project compiles - run `/build` for VR debug (`assembleNoLegalDebug`, `assembleVrDebug`).
- [ ] Strings audit exits 0 for `vr_settings_block_title`, `vr_settings_xr_unavailable_advisory`, `vr_settings_test_immersive_*`.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Catalog scan/render run after Kotlin changes.

---

## Handoff Notes to Next Phase

Settings exposes a VR-only diagnostic entry but does not own OpenXR lifecycle logic. Block structure (rename + collapsible group + advisory + disabled-state-on-non-XR) lands together with the diagnostic button — owner accepted Сценарий B from strategic spec §10 (skip standalone device-test of S0245, one combined device-test covers both).

---

## Rollback Plan

Revert Phase 04 commit(s); no persisted preference key is added.
