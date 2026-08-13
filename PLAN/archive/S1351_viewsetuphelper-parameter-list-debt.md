# S1351 - GeneralSettingsViewSetupHelper constructor exceeds LongParameterList threshold

**Ticket:** S1351
**Status:** Archived
**Priority:** 35
**Date:** 2026-08-02
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - discovered while closing S1337, parked 2026-08-02

<!-- auto-approved by /spec-all - 2026-08-02 -->

---

## Goal

`GeneralSettingsViewSetupHelper`'s constructor (12 параметров) превышает detekt-порог
`LongParameterList` (10). Три держателя (по прецеденту `LauncherHomeDependencies` из S1314 и
`BrowseViewModel` из S1350) группируют 7 из 12 параметров: общий host-трио (`binding`/`viewModel`/
`fragment`) в `GeneralSettingsHostContext`, пять delegate-хелперов (используемых только внутри
`setupActionButtons()`) в `GeneralSettingsActionHelpers`, пара lambda-геттер/сеттер `isUpdatingSpinner`
схлопывается в один `KMutableProperty0<Boolean>`. Итог: 12 -> 5 параметров, запас 5 до порога (не
ровно на пороге - урок из S1350 §9 ADR-2). Два прямых параметра-листа
(`ensureAllFilesPredefinedResourceUseCase`, `remoteSourceAvailabilityGate`) остаются без изменений -
у каждого нет смыслового соседа. Единственная точка вызова (`GeneralSettingsFragment.kt`'s
`viewSetupHelper by lazy` блок) обновляется вместе с этим. Мёртвая baseline-запись
`LongParameterList` для этого класса удаляется. Расширение на соседние `GeneralSettings*Helper`
(ни один не близок к порогу) явно вне объёма - см. Non-goals.

**Non-goals:**
- Расширение host-трио holder'а на соседние хелперы (`CacheHelper`, `LogHelper` и т.д.) для
  единообразия - ни один не превышает порог, а два из них (`CredentialHelper`, `LogHelper`) берут
  только часть трио, так что единый holder добавил бы неиспользуемое поле. Отдельный тикет при
  запросе владельца.
- Полный Manager-паттерн split класса по 12 `setupXxx`-функциям - непропорционально: класс уже
  делегирует бизнес-логику в выделенные sibling-хелперы, разрыв "на 2 параметра выше порога", а не
  god-class.
- Унификация `isUpdatingSpinner` lambda-пары в остальных 3 местах `GeneralSettingsFragment.kt`
  (`cacheHelper`, `observersHelper`, `colorThemeHelper`) - вне диффа этого тикета, эти конструкторы
  не тронуты.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1337 (обнаружил при удалении мёртвого `permissionsHelper`), S1314
  (источник holder-идиомы, `LauncherHomeDependencies`), S1350 (тот же паттерн, `BrowseViewModel`,
  Verified - прямой прецедент запаса-от-порога).
- **Scope:** `ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` +
  `ui/settings/fragments/GeneralSettingsFragment.kt` (только точка вызова, ~5 строк) +
  `config/detekt/baseline-app_v2.xml` (удаление одной записи).
- **Flavors:** все - оба файла в `src/main`, без BuildConfig-гейта.

---

## Phase 01 - Dependency holder declarations

**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02

### Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsHolders.kt` | New | ≤ 40 |

### Step 01.1 - Create the two holder classes

**Files:** `ui/settings/helpers/GeneralSettingsHolders.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `GeneralSettingsHolders.kt` in `ui/settings/helpers/`, next to `GeneralSettingsViewSetupHelper.kt`.
> Two plain classes (never `data class` - detekt's `LongParameterList.ignoreDataClasses` defaults to
> `true`, same trap `LauncherHomeDependencies.kt` and S1350's `BrowseViewModelDependencies.kt` avoid).
> Neither is Hilt-constructed (this file is hand-built inside `GeneralSettingsFragment`'s `by lazy`
> block, not resolved from the DI graph) - plain public constructors, no `@Inject`, no `@Suppress`.
>
> ```kotlin
> class GeneralSettingsHostContext(
>     val binding: FragmentSettingsGeneralBinding,
>     val viewModel: SettingsViewModel,
>     val fragment: Fragment,
> )
>
> class GeneralSettingsActionHelpers(
>     val cacheHelper: GeneralSettingsCacheHelper,
>     val importExportHelper: GeneralSettingsImportExportHelper,
>     val credentialHelper: GeneralSettingsCredentialHelper,
>     val logHelper: GeneralSettingsLogHelper,
>     val resetHelper: GeneralSettingsResetHelper,
> )
> ```
>
> Source every type's package from `GeneralSettingsViewSetupHelper.kt`'s current imports - do not
> guess a package path.

**Verification:**

- `Glob` - `GeneralSettingsHolders.kt` exists.
- `Grep` - `class GeneralSettingsHostContext` and `class GeneralSettingsActionHelpers` each match
  exactly once, neither preceded by `data `.
- `.\a.ps1 fk` succeeds.

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 3/3 PASS. `GeneralSettingsHolders.kt` created (+21 LOC), two plain
  classes (`GeneralSettingsHostContext`, `GeneralSettingsActionHelpers`), no `data class`. `.\a.ps1
  fk` BUILD SUCCESSFUL in 24s.

---

## Phase 02 - Constructor rewire, call site, baseline prune

**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none - final phase

### Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` | Modified | ≤ 680 (existing file - constructor + `setupXxx` bodies only) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` | Modified | ≤ 10 changed lines |
| `config/detekt/baseline-app_v2.xml` | Modified (delete dead entry) | - |

> `GeneralSettingsViewSetupHelper.kt` is 680 LOC (> 500 - CLAUDE.md Rule 5): explicit backup before
> the first edit.

### Step 02.1 - Backup, rewire constructor and internal usages

**Files:** `ui/settings/helpers/GeneralSettingsViewSetupHelper.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `GeneralSettingsViewSetupHelper.kt` to a timestamped backup under `temp/S1351/` first (Rule 5).
>
> Replace the 12-parameter constructor (currently lines 37-50) with:
>
> ```kotlin
> class GeneralSettingsViewSetupHelper(
>     private val hostContext: GeneralSettingsHostContext,
>     private val isUpdatingSpinner: KMutableProperty0<Boolean>,
>     private val actionHelpers: GeneralSettingsActionHelpers,
>     private val ensureAllFilesPredefinedResourceUseCase: EnsureAllFilesPredefinedResourceUseCase,
>     private val remoteSourceAvailabilityGate: RemoteSourceAvailabilityGate,
> ) {
> ```
>
> Add `import kotlin.reflect.KMutableProperty0`.
>
> Every remaining reference to the old flat names becomes:
> - `binding` -> `hostContext.binding`, `viewModel` -> `hostContext.viewModel`, `fragment` ->
>   `hostContext.fragment` (88/64/54 occurrences respectively across all 12 `setupXxx` functions -
>   mechanical `replace_all`, not per-callsite).
> - `getIsUpdatingSpinner()` -> `isUpdatingSpinner.get()`, `setIsUpdatingSpinner(x)` ->
>   `isUpdatingSpinner.set(x)` (property-reference call convention).
> - `cacheHelper`/`importExportHelper`/`credentialHelper`/`logHelper`/`resetHelper` ->
>   `actionHelpers.cacheHelper` etc. (all confined to `setupActionButtons()`, `cacheHelper` also once
>   in `setupCacheSizeInput()`).
> - `ensureAllFilesPredefinedResourceUseCase` and `remoteSourceAvailabilityGate` stay bare (direct
>   params, unchanged names).
>
> Remove now-unused imports for any type that was only referenced via its old flat constructor
> parameter name (check each before deleting - `Grep` the file for the bare type name after the
> rewire; a type still appearing only in the new holder-field type position is fine to keep imported
> if referenced there, but most of these types will have zero remaining bare references in this file
> since only `GeneralSettingsHolders.kt` needs their imports now).

**Verification:**

- `Grep` - constructor block contains `private val hostContext: GeneralSettingsHostContext`,
  `private val isUpdatingSpinner: KMutableProperty0<Boolean>`, `private val actionHelpers:
  GeneralSettingsActionHelpers`, plus the two unchanged direct params - exactly 5 total.
- `Grep` - none of `private val binding:`, `private val viewModel:`, `private val fragment:`,
  `private val getIsUpdatingSpinner:`, `private val setIsUpdatingSpinner:`, `private val cacheHelper:`
  (old flat declarations) remain.
- `.\a.ps1 fk` succeeds - any missed usage site fails compilation immediately (unresolved reference).

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 3/3 PASS. Constructor: 12 -> 5 params. `binding`/`viewModel`/`fragment`
  bulk `replace_all` each hit one unexpected substring collision the pre-check missed (the
  collision-check's own `grep -v "FragmentSettingsGeneralBinding"` filter accidentally excluded the
  one dangerous line it should have flagged): `import ...databinding.FragmentSettingsGeneralBinding`
  (contains `binding`), `import androidx.fragment.app.Fragment` and
  `ui.settings.fragments.OpenSourceLicensesFragment()` (both contain `fragment`) - all 3 corrupted by
  the substring match, all 3 repaired by hand immediately after each replace_all, before moving on.
  `getIsUpdatingSpinner()`/`setIsUpdatingSpinner(true/false)` and the 5 action-helper names replaced
  clean (no further collisions). First `fk` attempt correctly failed with 13 errors, all in
  `GeneralSettingsFragment.kt` (Step 02.2's file, not yet touched) - zero errors in
  `GeneralSettingsViewSetupHelper.kt` itself, confirming this step's own rewire is complete and
  correct.
- 2026-08-02 - **Correction after `post-change.ps1`:** the body-wide `hostContext.`/`actionHelpers.`
  prefix approach above pushed ~30 already-dense lines over `MaxLineLength` (120) plus several
  `ArgumentListWrapping` findings - this file has far denser per-line usage than `BrowseViewModel.kt`
  (S1350 precedent), where the same prefix approach worked cleanly. Reverted all `hostContext.X`/
  `actionHelpers.X` occurrences back to the bare original names via `replace_all`, and instead added
  8 one-line delegating properties right after the constructor (`private val binding get() =
  hostContext.binding`, same pattern for `viewModel`/`fragment`/`cacheHelper`/`importExportHelper`/
  `credentialHelper`/`logHelper`/`resetHelper`). Every `setupXxx` function body is now byte-identical
  to before this ticket (confirmed via `diff` against the Step 02.1 backup) - only the constructor and
  the 8 new properties changed. This also surfaced 3 genuinely-unused imports (`Fragment`,
  `FragmentSettingsGeneralBinding`, `SettingsViewModel` - no longer spelled out literally anywhere in
  this file once their types are only reached via property-getter inference) - removed. Re-ran
  `post-change.ps1` - PASS, zero detekt findings.

---

### Step 02.2 - Update the call site in GeneralSettingsFragment.kt

**Files:** `ui/settings/fragments/GeneralSettingsFragment.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> The `viewSetupHelper by lazy` block (currently lines 208-215) constructs
> `GeneralSettingsViewSetupHelper(binding, viewModel, this, { isUpdatingSpinner }, {
> isUpdatingSpinner = it }, cacheHelper, importExportHelper, credentialHelper, logHelper,
> resetHelper, ensureAllFilesPredefinedResourceUseCase, remoteSourceAvailabilityGate)`. Change to:
>
> ```kotlin
> private val viewSetupHelper by lazy {
>     GeneralSettingsViewSetupHelper(
>         hostContext = GeneralSettingsHostContext(binding, viewModel, this),
>         isUpdatingSpinner = this::isUpdatingSpinner,
>         actionHelpers = GeneralSettingsActionHelpers(
>             cacheHelper, importExportHelper, credentialHelper, logHelper, resetHelper
>         ),
>         ensureAllFilesPredefinedResourceUseCase = ensureAllFilesPredefinedResourceUseCase,
>         remoteSourceAvailabilityGate = remoteSourceAvailabilityGate,
>     )
> }
> ```
>
> `isUpdatingSpinner` stays a `private var` on the Fragment (line 96, unchanged) - `this::isUpdatingSpinner`
> is a valid `KMutableProperty0<Boolean>` reference from within the same class. Do not touch the other
> three call sites that share the same lambda pair (`cacheHelper` line 183, `observersHelper` lines
> 203-204, `colorThemeHelper` line 219) - out of this ticket's scope (see Non-goals).

**Verification:**

- `Grep` - `GeneralSettingsHostContext(binding, viewModel, this)` present inside the
  `viewSetupHelper` block.
- `Grep` - `isUpdatingSpinner = this::isUpdatingSpinner` present.
- `Grep` - lines 183, 203-204, 219 (the other three lambda-pair call sites) unchanged.
- `.\a.ps1 fk` succeeds.

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 4/4 PASS. Call site updated to construct `GeneralSettingsHostContext`/
  `GeneralSettingsActionHelpers` inline, `isUpdatingSpinner = this::isUpdatingSpinner`. Two imports
  added at correct alphabetical position (`GeneralSettingsActionHelpers`, `GeneralSettingsHostContext`
  - both in `ui.settings.helpers`, a different package than the Fragment's own
  `ui.settings.fragments`, so explicit imports were required unlike inside
  `GeneralSettingsViewSetupHelper.kt` itself). Confirmed the other 3 `isUpdatingSpinner` lambda-pair
  call sites (`cacheHelper`, `observersHelper`, `colorThemeHelper`) remained in their original
  two-lambda form, untouched. `.\a.ps1 fk` BUILD SUCCESSFUL in 1m 19s (all warnings pre-existing,
  unrelated files). `post-change.ps1`'s detekt gate caught one `ArgumentListWrapping` finding on the
  5-argument `GeneralSettingsActionHelpers(cacheHelper, importExportHelper, ...)` call (ktlint wants
  one argument per line once a call already spans multiple lines) - reformatted to one arg per line,
  trailing comma. Re-ran `post-change.ps1` - PASS.

---

### Step 02.3 - Full detekt validation and dead-baseline prune

**Files:** `config/detekt/baseline-app_v2.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/quality/assert-detekt.ps1 -Module app_v2` (full, non-diff-scoped)
> to refresh the report. Confirm no `LongParameterList` finding remains for
> `GeneralSettingsViewSetupHelper` (5 params is under the `constructorThreshold: 10`).
>
> Run `pwsh -NoProfile -File scripts/quality/audit-detekt-baseline-drift.ps1` and check the
> classification for the `LongParameterList:GeneralSettingsViewSetupHelper.kt$..` entry (currently
> `config/detekt/baseline-app_v2.xml:3446`). If the live report is otherwise near-empty (few/no
> project-wide unbaselined findings - a known tool limitation, see S1353), do not trust a blanket
> `DEAD` sweep across unrelated entries; confirm this specific entry independently: the constructor is
> now structurally 5 params (compiler-verified in Step 02.1), so this exact entry cannot match live
> code any more - safe to delete regardless of the tool's broader classification that run.
>
> Delete only this one entry. After editing, run `.\gradlew.bat --stop` (daemon can serve a stale
> in-memory baseline after a hand-edit) before the final gate re-check.

**Verification:**

- `Grep` - `config/detekt/baseline-app_v2.xml` no longer contains
  `LongParameterList:GeneralSettingsViewSetupHelper.kt$GeneralSettingsViewSetupHelper$`.
- `pwsh -NoProfile -File scripts/quality/assert-detekt.ps1 -Module app_v2 -Gate -ChangedFiles
  app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt`
  exits 0.
- `.\a.ps1 d` (standard debug, full build) succeeds.

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 3/3 PASS. Full `assert-detekt.ps1 -Module app_v2` PASS. Deleted the one
  dead `LongParameterList:GeneralSettingsViewSetupHelper.kt$..` baseline entry (independently
  confirmed dead - constructor is compiler-verified at 5 params - rather than trusting the S1353
  tool's broader classification, same caution as S1350). `.\gradlew.bat --stop` run before re-check
  (daemon-staleness precedent). Scoped `assert-detekt.ps1 -Gate -ChangedFiles` PASS. `.\a.ps1 d`
  (standard debug) BUILD SUCCESSFUL in 32s, including `hiltSyncStandardDebug`/
  `hiltJavaCompileStandardDebug`.

---

### Step 02.4 - Catalog regen and journal

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 02.3

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to index the two new holder
> classes. Fill `role`+`status=new` via `dev/CATALOG/scripts/set.ps1 -Module app_v2 -Path
> com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsHolders.kt -Role "<purpose>" -Status new`.
> Journal every touched file via `.\scripts\add_to_dev_log.ps1` (batch as one logical entry). Flip
> ticket to `Implemented`: `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S1351 -Status
> Implemented`.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "GeneralSettingsHostContext"` and
  `-ClassMatches "GeneralSettingsActionHelpers"` each return exactly one record, `status: new`.
- Dev-log sink contains an entry referencing `S1351`.
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S1351 -Format json` reports
  `"status":"Implemented"`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 3/3 PASS. Catalog regenerated as a side effect of Step 02.1's
  `post-change.ps1` run; this step filled `role`+`status=new` for both holder classes via `set.ps1`
  (one call, both records share the same file path) and re-rendered. Dev-log entries present for
  every touched file across all steps. `select.ps1` confirms `status: Implemented`.

---

## Phase Done Criteria (both phases)

- [x] Every step above `[x] done`.
- [x] `.\a.ps1 d` (standard debug) succeeds - BUILD SUCCESSFUL in 32s.
- [x] `assert-detekt.ps1 -Gate -ChangedFiles` clean for the two touched `.kt` files.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated, contains both new holder classes.
- [x] `/spec-check S1351` returns `Verified`.

No device-test gate: pure internal DI/constructor reshape, zero behavior change, no new user-facing
flow - matches this ticket's own Verification bar (§2 of the original capture: compile + detekt gate
only), same as S1334's precedent (Verified without a device pass).

---

## Last Audit

**Date:** 2026-08-02
**Mode:** strategic (Simple path - no tactical folder, phases inline)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 12 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Manual / on-device

None - strategic §2's own Verification bar is compile + detekt gate only (no behavior change, no new
user-facing flow), same as S1334's precedent. FEATURES trilingual check: EXEMPT (internal DI
refactor, no user-visible capability per Non-goals).
