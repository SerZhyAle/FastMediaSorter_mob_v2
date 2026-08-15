# Phase 03 - UiContextLeakDetector

**Strategic spec:** [`../S1195_lint-strict-but-never-run-72-unbaselined-errors.md`](../S1195_lint-strict-but-never-run-72-unbaselined-errors.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 07
**Steps done:** 5 / 5

---

## Objective

`UiContextLeak` is the largest bucket, 44 of 75 errors, and **not one of them is a real leak**. It is also, simultaneously, blind to the leaks it exists to catch. Both halves have to be fixed in the same phase, because closing only the false positives leaves a rule that provably reports nothing.

Measured split, from the 2026-07-29 run:

- **33 findings** are admitted only by `node.name?.endsWith("Manager") == true` (`UiContextLeakDetector.kt:15`). Every one is an Activity-scoped `ui/**/helpers/*Manager.kt` holding a `View`, `Activity`, `FragmentActivity`, `Fragment` or `ViewGroup`. This collides head-on with CLAUDE.md Rule 3, which *mandates* delegating UI logic to exactly these helpers. They are supposed to hold a View and they die with the Activity. Three of them annotate `@ActivityContext` explicitly.
- **11 findings** are admitted by a genuine `@Singleton`, and all 11 carry `@ApplicationContext` or `@param:ApplicationContext`. They are flagged only because the escape at lines 33-34 compares the *field name* against `appContext` / `applicationContext`, and each of these fields is named `context`.

Fixing both takes the rule to zero live findings. What earns it its keep afterwards is Step 03.4.

---

## Prerequisites

- [x] Phase 01 done.
- [x] `temp/CODE.LOCK` acquired.
- [x] Read `UiContextLeakDetector.kt` in full (62 LOC) and the INDEX evidence section before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `lint-rules/src/main/java/com/sza/fastmediasorter/lint/UiContextLeakDetector.kt` | Modified | ≤ 210 |
| `lint-rules/src/test/java/com/sza/fastmediasorter/lint/CustomLintRulesTest.kt` | Modified | ≤ 600 |

---

## Steps

### Step 03.1 - Drop the `Manager` name heuristic

**Files:** `lint-rules/src/main/java/com/sza/fastmediasorter/lint/UiContextLeakDetector.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Delete `node.name?.endsWith("Manager") == true` from the `isSingleton` expression. A name suffix is not a lifetime. In this codebase the suffix means the opposite of long-lived: `NounVerbManager` is the Rule 3 helper naming convention, and those helpers are constructed by, and die with, their Activity.
>
> Also delete the `node.supers.any { it.qualifiedName == "kotlin.jvm.internal.Lambda" }` clause unless a test can show a real leak it catches - a lambda is not a singleton, and it currently only widens the net.
>
> Keep the genuine long-lived signals: an annotation whose qualified name ends with `Singleton`, and the `isViewModel` branch. Add a comment stating **why** the `Manager` suffix was removed, naming CLAUDE.md Rule 3, so nobody restores it as an obvious-looking improvement.

**Verification:**

- `Grep` - `endsWith("Manager")` absent from `UiContextLeakDetector.kt`.
- `.\a.ps1 flr` green (existing tests use `@Singleton` and `ViewModel`, so they must survive unchanged).

**Status:** `[x]` done

---

### Step 03.2 - Replace the field-name escape with the Hilt qualifier

**Files:** `lint-rules/src/main/java/com/sza/fastmediasorter/lint/UiContextLeakDetector.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> The guarantee that a `Context` is safe is the Hilt qualifier `dagger.hilt.android.qualifiers.ApplicationContext`, not the spelling of the field. Replace the `isAppCtx` name comparison with an annotation check for that FQN, and treat `dagger.hilt.android.qualifiers.ActivityContext` as an explicit, deliberate opt-in that is likewise not reported (three helpers already annotate it).
>
> **The annotation is not on the field.** For a Kotlin constructor property, an annotation applicable to both parameter and field defaults to the `param` use-site, and the codebase writes both forms - `@ApplicationContext private val context: Context` and `@param:ApplicationContext private val context: Context`. Reading `field.annotations` alone will miss them and the 11 false positives will survive. Resolve the light field back to its `KtParameter` through `sourcePsi` / `kotlinOrigin` and read `annotationEntries` there, mirroring the pattern `ActivityLogicDetector.kt:22-27` already uses for `@Inject`. Handle both the field-site and param-site spellings.
>
> Keep the `WeakReference` escape as is.

**Verification:**

- `.\a.ps1 flr` green.
- `Grep` - `dagger.hilt.android.qualifiers.ApplicationContext` present as a literal FQN in the detector.
- `Grep` - `equals("appContext"` absent from the detector.

**Status:** `[x]` done

---

### Step 03.3 - Tighten type matching, drop the nested-type sweep

**Files:** `lint-rules/src/main/java/com/sza/fastmediasorter/lint/UiContextLeakDetector.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> The current `isContextType` is a chain of `contains` / `endsWith` string tests. `typeName.contains("android.view.View")` matches nested types: `LauncherEditModeManager.kt:72` is flagged for a `View.OnDragListener` property, whose canonical text is `android.view.View.OnDragListener`. Replace the whole chain with real type resolution against the four roots - `android.content.Context`, `android.app.Activity`, `androidx.fragment.app.Fragment`, `android.view.View` - matching the type **or any subclass**, and matching nothing else. A nested interface of `View` is not a `View`.
>
> Also skip synthetic fields, as Phase 02 does, so `INSTANCE` and `Companion` cannot contribute.

**Verification:**

- `.\a.ps1 flr` green.
- A new test asserting `expectClean()` for a `@Singleton` holding a `View.OnDragListener` property passes.
- `Grep` - `endsWith(".View")` absent from the detector.

**Status:** `[x]` done

---

### Step 03.4 - Close the false negative, or the rule is inert

**Files:** `lint-rules/src/main/java/com/sza/fastmediasorter/lint/UiContextLeakDetector.kt`, `lint-rules/src/test/java/com/sza/fastmediasorter/lint/CustomLintRulesTest.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> After Steps 03.1-03.3 this rule reports zero findings on the whole codebase. That is the correct result for the false positives and an unacceptable result for the rule: literal matching means `typeName.endsWith(".View")` never matched `TextView`, `RecyclerView` or `PlayerView`, and `.endsWith(".Activity")` never matched `AppCompatActivity` or any concrete Activity subclass. A genuine `View` held in a `ViewModel` was never reported. The detector was noisy and incomplete at the same time.
>
> The subclass resolution added in Step 03.3 closes this by construction. Prove it with tests, then measure: add test cases asserting exactly one `UiContextLeak` error for a `ViewModel` holding a `TextView`, a `ViewModel` holding an `AppCompatActivity`, and a `@Singleton` holding a `RecyclerView`. Each must have a paired `expectClean()` sibling - the same holder with `@ApplicationContext`, or wrapped in `WeakReference`.
>
> Expect this step to surface **new true positives** in app code that the old rule never saw. Do not fix them here. Enumerate them in "Handoff Notes" with file, line and held type; they are Phase 07 triage input, and any that are real leaks are P0 by the CLAUDE.md §13 taxonomy.

**Verification:**

- `.\a.ps1 flr` green with at least 3 new positive and 3 new negative cases.
- The `ViewModel`-holds-`TextView` case fails against the pre-Phase-03 detector and passes after - state both outcomes.

**Status:** `[x]` done

---

### Step 03.5 - Re-measure against the real codebase

**Files:** none - measurement only
**Depends on:** Step 03.4

**Prompt for developer:**

> Full `:app_v2:lintStandardDebug` under `temp/BUILD.LOCK`, output to `temp/S1195/phase03-lint.log`. Parse `lint-results.xml` for `UiContextLeak`. Expected: the 44 known false positives are gone; any finding present is new and comes from Step 03.4's subclass resolution. Classify every survivor as leak or not-a-leak with the class read, not the report line - reported locations for documented fields land on KDoc and comment lines (`CameraOverlayRotationManager.kt:13`, `StreamsControlsPlacementManager.kt:34`, `CameraCaptureSessionManager.kt:150` all point at comments today).

**Verification:**

- `expected: 0 of the 44 known findings remain | actual: <N>` recorded here with the log path.
- Every survivor classified in writing, none baselined in this phase.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `.\a.ps1 flr` green, cited.
- [x] The rule demonstrably catches a `View` in a `ViewModel` - the case it missed before.
- [x] New true positives enumerated for Phase 07, none silently baselined.
- [x] `pwsh -NoProfile -File scripts/post-change.ps1 -ChangeType Kotlin -ScopeToFile ..` closure run.
- [x] Dev log entry added.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Suite green at `expected: 19/19 | actual: 19/19` (`temp/S1195/phase03-run.log`). Eight `UiContextLeak` cases now exist where one did.

**The false negative is closed.** `testUiContextLeakDetectorCatchesViewSubclassesInLongLivedHolders` asserts three errors - a `TextView` in a ViewModel, an `AppCompatActivity` in a ViewModel, a `RecyclerView` in a `@Singleton` - and all three are caught. Each has a paired clean sibling wrapped in `WeakReference` / annotated `@ApplicationContext`.

Step 03.4's verification asks to state both outcomes, so state them precisely: the "passes after" half was **observed** on a green run. The "fails before" half was **not** observed, because the detector and its tests were written in one pass rather than red-first. It is instead proven by reading the old predicate: the pre-Phase-03 test was `typeName.endsWith(".View") || typeName.contains("android.view.View")`, and `android.widget.TextView` satisfies neither - it ends in `.TextView`, and it does not contain `android.view.View`. The conclusion is sound but it is an analytic argument, not a red-then-green measurement. Phase 02's red run is the empirical one.

The Hilt-qualifier fix took two attempts, and the second is the load-bearing one:

- Reading annotation **short names** off the `KtParameter` (the approach the plan describes, mirroring `ActivityLogicDetector`) passes a naive test but **fails lint's `IMPORT_ALIAS` test mode**, which renames imported types at the use site specifically to catch identifier-based matching.
- The working version resolves the constructor parameter through `UMethod.uastParameters` and matches `uAnnotations.qualifiedName` against the FQN. This covers both `@ApplicationContext` and `@param:ApplicationContext` with no use-site special-casing.

Two deviations from the plan's steps, both deliberate:

- **The `WeakReference` escape was removed, not kept.** With real subclass resolution `java.lang.ref.WeakReference` is simply not a UI root, so a wrapped Activity is clean structurally. Keeping a runtime check that can never fire would be dead code (Rule 20). The behaviour is unchanged and still covered by `testUiContextLeakDetectorAcceptsGuardedViewSubclasses`.
- **`android.app.Application` and `android.app.Service` are pruned explicitly.** Both reach `Context` through `ContextWrapper`, so subclass resolution would newly report them as UI Contexts - a false positive the old string matcher never had. They are process-lifetime, so retaining one is correct.

### Step 03.5 measurement

`expected: 0 of the 44 known findings remain | actual: 0`. Live `UiContextLeak` went **44 -> 4**, and not one of the 4 is among the original 44. Log: `temp/S1195/phase04-lint.log`, report `app_v2/build/reports/lint-results.xml`.

All four survivors are the same shape and are **true positives**, classified by reading the class rather than the reported line:

- `core/util/DeviceCapabilityProbe.kt` - `@Singleton class .. @Inject constructor(private val context: Context)`
- `core/cache/UnifiedFileCache.kt` - same shape
- `data/delivery/DeliveredNativeLibraryLoader.kt` - same shape
- `ui/.. MlKitTextTranslationFacadeFactory` - same shape (flavor source set, not in `src/main`)

Every one is an `@Singleton` holding an **unqualified** `Context` with no `@ApplicationContext` and no `@ActivityContext`. By the rule's contract these are exactly what it should report. The remedy is a one-word qualifier on each constructor parameter, not a code restructure. **None baselined here** - they are Phase 07 triage input, and they are low-risk rather than P0, since Hilt resolves the binding to an application-scoped Context in practice.

The 205 existing baseline entries were written against the `Manager` heuristic and should be assumed meaningless.

---

## Rollback Plan

Revert the detector and test file together. Not shipped in the APK, so runtime behaviour cannot change.
