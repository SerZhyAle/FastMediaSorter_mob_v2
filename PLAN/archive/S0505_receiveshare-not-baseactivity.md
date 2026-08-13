# S0505 - ReceiveShareActivity bypasses the shared BaseActivity infrastructure

**Ticket:** S0505
**Status:** Archived
**Priority:** 40
**Date:** 2026-06-18
**Tier:** 3 - Moderate (ad-hoc)
**Origin:** auto-captured during S0504 research (out-of-scope finding)

> **Scope:** Re-assessed by `/spec-all` research (2026-06-18). The auto-captured premise (migrate to BaseActivity to inherit the shared input layer) does not hold on inspection - the activity is transparent and chromeless by design. This file now records the analysis and the WontFix recommendation.

---

## 0. Raw capture / Evidence (исходное наблюдение)

Обнаружено при исследовании S0504 (app-wide D-pad focus-container guard).

**Симптом (как сформулировано при захвате):** `ReceiveShareActivity` наследуется напрямую от `AppCompatActivity`, а не от `BaseActivity`, поэтому минует общий слой: TV/D-pad-навигацию, mouse/pointer dispatch, locale, keep-screen-on, GMS-warning.

## Research finding (2026-06-18) - премиса не подтвердилась

Чтение кода (`ReceiveShareActivity.kt`, `BaseActivity.kt`, `AndroidManifest.xml`) показало, что миграция на `BaseActivity` - неверный подход:

- **Activity прозрачная и chromeless по дизайну.** Манифест: `android:theme="@style/Theme.FastMediaSorter.Transparent"` с комментарием «Translucent theme ensures only the Copy-to dialog is visible (no blank window)». В `onCreate` нет `setContentView` / ViewBinding - activity только показывает диалоги (loading -> `FileOperationDestinationDialog`).
- **`BaseActivity<VB : ViewBinding>` требует content-view.** Абстрактные `getViewBinding()`, `setupViews()`, `observeData()`; управляет ViewBinding-lifecycle, откладывает `setupViews()` в `binding.root.post { }`. Прозрачной activity без layout нечего туда подставить, кроме фиктивного.
- **Locale уже применяется.** `ReceiveShareActivity.attachBaseContext` -> `LocaleHelper.applyLocale(newBase)` - то же, что делает `BaseActivity`. Claim «минует LocaleHelper» неверен.
- **Mouse/D-pad-слой работает по content-root, которого нет.** `ActivityMouseDispatchHelper` диспетчит по `_binding?.root`; initial-focus/`TvKeyRouter` работают по собственному дереву view. У прозрачной activity собственного дерева нет - после миграции этим слоям нечего обслуживать.
- **D-pad receiver-строк - concern диалога, не activity.** `RecyclerView` из `sheet_send_to.xml` живёт в `FileOperationDestinationDialog` (Dialog с собственным окном/фокусом), а не в content-view activity. Членство в `BaseActivity` на фокус внутри диалога не влияет.

Те же соображения применимы к siblings с тем же прозрачным паттерном: `ResourceImportActivity`, `StandalonePlayerDispatcherActivity` (оба `Theme.FastMediaSorter.Transparent`).

## Residual concern (отдельный, неподтверждённый)

Единственный потенциально реальный пункт - доходит ли D-pad до receiver-строк в `FileOperationDestinationDialog` на TV. Это:
- НЕ решается миграцией на `BaseActivity` (dialog-level фокус);
- НЕ подтверждён как сломанный (диалоги обычно сами управляют фокусом);
- при подтверждении на устройстве - самостоятельный узкий тикет про focusability строк диалога, не эта миграция.

## Recommendation

- **WontFix миграцию** `ReceiveShareActivity` -> `BaseActivity`. Прозрачные chromeless dialog-host activity намеренно вне `BaseActivity`-контракта.
- Архивировать S0505 (решение владельца - автопилот не архивирует деструктивно).
- Если нужен TV-D-pad в destination-диалоге - открыть отдельный узкий тикет после подтверждения на устройстве.

### Quiz decisions (2026-06-19)

- Archive S0505 as WontFix - BaseActivity migration premise invalidated by research (transparent chromeless dialog-host activity is intentionally outside the BaseActivity content-view contract).
- Residual TV-D-pad concern in `FileOperationDestinationDialog` is unconfirmed - do NOT open a ticket now; open a narrow dialog-D-pad ticket only after on-device confirmation that focus does not reach receiver rows.

## Связь

- S0504 (общий focus-container guard - намеренно не достаёт прозрачные dialog-host activity).
- S0289 (multimodal input parity - оперирует content-view-слоем, которого у этой activity нет).
- S0510, S0512 (ссылались на S0505 как на «почему share/import вне BaseActivity-хука» - этот анализ подтверждает: они вне хука by design).
