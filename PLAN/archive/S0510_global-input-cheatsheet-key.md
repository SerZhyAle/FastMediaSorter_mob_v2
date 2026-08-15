# S0510 - Global input cheat-sheet key (F1) + first-run hint

**Status:** Archived
**Priority:** 40
**Date:** 2026-06-18
**Tier:** 2 - Easy (ad-hoc)
**Origin:** owner request 2026-06-18 (multi-device input improvements)

> **Scope:** Compact spec (Simple path). One predictable global key (F1) opens the per-surface input-help dialog on any BaseActivity screen; close the showHelp() coverage gap on the keyboard-driven gap screens; one-shot first-run hint on non-touch devices.

---

## Goal (RU)

`InputHelpDialogFragment` уже даёт per-surface справку, и большинство экранов открывают её по F1 через свой `KeyboardShortcutHandler`. Но экраны без собственного key-обработчика (Statistics, KebindingRemap, AuthSessions, ..) глотают F1 - единой предсказуемой клавиши «показать подсказки» нет, и нет первичной подсказки для non-touch пользователей. Цель - перехватить F1 в `BaseActivity.onKeyDown` как фолбэк (экран объявляет свою поверхность через `getInputHelpSurface()`), закрыть пробел на ключевых клавиатурных экранах и показать однократную first-run-подсказку на non-touch устройстве.

## Проблема

`BaseActivity` не перехватывает F1 централизованно. Экраны без `KeyboardShortcutHandler` (Statistics/KeybindingRemap/AuthSessions) не открывают справку ни по какой клавише. Discoverability на клавиатуре/TV низкая.

## Acceptance criteria

1. `BaseActivity` несёт `protected open fun getInputHelpSurface(): InputSurface? = null` и перехватывает `KEYCODE_F1` в `onKeyDown`: если поверхность не null - открывает `InputHelpDialogFragment` и возвращает true; иначе делегирует `super`.
2. Фолбэк не конфликтует с экранами, уже обрабатывающими F1 в своём `onKeyDown` (их обработчик срабатывает раньше `super.onKeyDown`, поэтому они оставляют `getInputHelpSurface()` = null).
3. Пробел закрыт на клавиатурных экранах без своего F1-обработчика: `StatisticsActivity` (MAIN), `KeybindingRemapActivity` (SETTINGS), `AuthSessionsActivity` (SETTINGS).
4. Однократная first-run-подсказка на non-touch устройстве (TV/hw-клавиатура/без тачскрина) через переиспользуемый SharedPreferences-флаг; чистая `shouldShow()` покрыта JVM-тестом; показывается на `MainActivity`.
5. Подсказка локализована EN/RU/UK. Сборка проходит.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0289 (multimodal parity), S0505 (ReceiveShareActivity off BaseActivity - why share/import are out of this hook).

## Scope decisions (research-driven, no owner prompt)

- **F1 only.** Spec allowed «F1 и/или Shift+/»; Shift+`/` = `?` collides with text entry on text-capable surfaces, so the secondary shortcut is dropped. F1 is unambiguous and conflict-free.
- **Gap screens covered:** Statistics/KeybindingRemap/AuthSessions. Welcome/Calculator/Game/Camera/widget-config return null (onboarding / own key handlers / non-keyboard UI).
- **Out of scope:** `ReceiveShareActivity` + `ResourceImportActivity` extend `AppCompatActivity`, not `BaseActivity`, so they cannot receive this hook - tracked under S0505.

---

## Phase 01 - Global F1 + coverage + first-run hint

**Files:**
- `core/ui/BaseActivity.kt` - `getInputHelpSurface()` + `onKeyDown` F1 fallback
- `ui/statistics/StatisticsActivity.kt`, `ui/keybinding/KeybindingRemapActivity.kt`, `ui/settings/auth/AuthSessionsActivity.kt` - override `getInputHelpSurface()`
- `ui/common/input/InputHelpFirstRunHint.kt` (new) - one-shot non-touch hint (pure `shouldShow` + `showIfNeeded`)
- `ui/main/MainActivity.kt` - call `InputHelpFirstRunHint.showIfNeeded(this)`
- `res/values/strings_input.xml` + `values-ru/` + `values-uk/` - `keybinding_f1_hint`
- `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/input/InputHelpFirstRunHintTest.kt` (new)

**Done:**

- BaseActivity F1 fallback + overridable surface; three gap screens declare their surface.
- `InputHelpFirstRunHint` shows once per install on non-touch devices; `shouldShow` unit-tested.
- `keybinding_f1_hint` added EN/RU/UK.

**Status:** `[x]` done

---

## Верификация

- `.\a.ps1 fc` passes; `--tests *InputHelpFirstRunHintTest` green; existing `KeyboardShortcutHandlerTest` (F1->ShowHelp) still green.
- Localization parity for `keybinding_f1_hint`.
- Device: optional (per origin) - F1 opens help on a gap screen; hint appears once on a non-touch device.

## Связь

- S0289 (multimodal parity), S0505 (share/import off BaseActivity).

---

## Implementation State

**Date:** 2026-06-19
**Status:** Implemented (unit-test gate deferred - see note).

- BaseActivity F1 fallback (`getInputHelpSurface()` + `onKeyDown`), 3 gap-screen overrides, `InputHelpFirstRunHint` (+ test), MainActivity hint call, `keybinding_f1_hint` EN/RU/UK - all written.
- `.\a.ps1 fc` (compileStandardDebugKotlin + resources) BUILD SUCCESSFUL on these changes; localization parity OK for `keybinding_f1_hint`.
- `InputHelpFirstRunHintTest` could NOT be executed: a concurrent in-progress edit elsewhere (`ui/settings/fragments/OperationsSettingsFragment.kt:75` references `OperationsCaptureManager` without resolving it - unrelated to S0510) currently breaks `compileStandardDebugKotlin`, so the test task cannot compile the module. Re-run `gradlew :app_v2:testStandardDebugUnitTest --tests *InputHelpFirstRunHintTest` once the tree is green; the helper is a 3-case pure function.
- Device verification optional per origin. No debug tags (device not part of acceptance).

## Verification (2026-06-19)

- `assembleStandardDebug` (`.\a.ps1 d`) BUILD SUCCESSFUL - the unrelated `OperationsCaptureManager` break that previously blocked the test compile is resolved (class now exists in `ui/settings/helpers/`).
- `testStandardDebugUnitTest --tests *InputHelpFirstRunHintTest` BUILD SUCCESSFUL (green); `KeyboardShortcutHandlerTest` (F1->ShowHelp) green in the same run.
- No `Timber.d("S0510:` tags in `.kt` (status not BlockNeedUserTest - invariant holds).
- Device verification optional per origin; build + unit gate satisfied -> Verified.
