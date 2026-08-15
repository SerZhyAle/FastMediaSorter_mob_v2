# Phase 03 - Widget gadget bridge

**Strategic spec:** [`../S1170_launcher-desktop-app-widgets.md`](../S1170_launcher-desktop-app-widgets.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04, Phase 05, Phase 06
**Steps done:** 4 / 4
**Started:** 2026-07-30
**Completed:** 2026-07-30

---

## Objective

Give every catalog widget a stable launcher-gadget key and ship the nine mechanical ones as a single parameterised gadget, registered without growing the registry constructor.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done - the mechanical gadgets resolve through its route keys.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/registry/HomeWidgetEntry.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/registry/HomeWidgetCatalog.kt` | Modified | ≤ 200 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/HomeWidgetGadget.kt` | New | ≤ 160 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/di/HomeWidgetGadgetModule.kt` | New | ≤ 140 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadgetRegistry.kt` | Modified | ≤ 120 |
| `app_v2/src/launcherEnabled/res/layout/gadget_home_widget.xml` | New | ≤ 60 |

> No `layout-land` counterpart: the gadget renders inside a desktop cell whose span already carries the orientation, and the launcher stores spans per orientation. There is no landscape variant of any existing gadget layout in this source set.

---

## Steps

### Step 03.1 - Give every catalog entry an explicit gadget key

**Files:** `app_v2/.../widget/registry/HomeWidgetEntry.kt`, `HomeWidgetCatalog.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `val gadgetKey: String` to `HomeWidgetEntry` and set it on all fourteen catalog entries with short stable snake_case ids (`calculator`, `camera_ocr_translate`, `capture_ocr_panel`, `camera_launch`, `camera_photos`, `camera_quick_capture`, `continue_reading`, `game_launch`, `random_photo_frame`, `random_music`, `audio_now_playing`, `quick_audio_recorder`, `favorites`, `scheduled_tasks`). KDoc must record why the key is stored rather than derived from `providerClass.simpleName`: the key is persisted inside a desktop cell's `target` column, so a class rename or an R8 pass would otherwise orphan every placed cell. These ids are a persistence format from the moment Phase 06 writes one - do not renumber or rename them later.

**Verification:**

- `Grep` - `val gadgetKey: String` present in `HomeWidgetEntry`.
- `Grep` - `gadgetKey =` matches exactly 14 times in `HomeWidgetCatalog.kt`.

**Status:** `[x]` done - `expected: 14 | actual: 14`.

---

### Step 03.2 - Add the parameterised mechanical gadget

**Files:** `app_v2/src/launcherEnabled/.../gadget/HomeWidgetGadget.kt`, `res/layout/gadget_home_widget.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Write one `HomeWidgetGadget` implementing `LauncherGadget`, constructed with its `key`, `labelRes`, `iconRes`, spans, and the `LauncherCellCommand` its tap runs. `createView` inflates `gadget_home_widget.xml` (icon over label, matching the existing gadget layouts' padding and text appearance) and sets a click listener that calls `host.run(command)` - never an intent of its own. `requiresResourceParam` is false and `param` is ignored. The layout must use `?attr/` or `@color/` for every colour, never a hardcoded `="#hex"`. This one class covers all nine mechanical widgets; do not write nine classes.

**Verification:**

- `Grep` - `class HomeWidgetGadget` matches exactly once and implements `LauncherGadget`.
- `Grep` - `host.run(` present in the file; `startActivity` absent.
- `Grep` - `="#` returns zero hits in `gadget_home_widget.xml`.

**Status:** `[x]` done

**Authoring trap worth recording.** The first version of the sibling module did not compile because a KDoc line named the size source as `res/xml/*_info.xml`. Kotlin block comments **nest**, so the `/*` in that glob opened a second comment level and swallowed the rest of the file; the compiler reported `Missing '}'` at the object header and `Unclosed comment` at the last line - neither pointing at the actual line. Never write a `/*` sequence inside a Kotlin comment.

---

### Step 03.3 - Provide the mechanical gadget set

**Files:** `app_v2/src/launcherEnabled/.../gadget/di/HomeWidgetGadgetModule.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add a Hilt `@Module` in the launcherEnabled source set providing the nine mechanical `HomeWidgetGadget` instances as one collection: `calculator`, `camera_ocr_translate`, `capture_ocr_panel`, `game_launch` and `quick_audio_recorder` resolve to routes that already existed (`fn:calculator`, `fn:ocr`, `fn:ocr`, `fn:game`, `fn:quick_voice`), and `camera_photos`, `camera_launch`, `continue_reading`, `random_music` to Phase 02's new keys. The OCR panel widget has two tap targets on Android home; on the desktop it becomes one cell running the OCR route, matching the label the user picked. Each gadget's default span comes from the corresponding widget's declared `res/xml/widget_*_info.xml` size so the cell matches the Android home screen (strategic §3); read the value, do not guess it.
>
> Four catalog keys are deliberately NOT here: `favorites` and `scheduled_tasks` are lists (Phase 04), `random_photo_frame` and `camera_quick_capture` carry per-instance state, and `audio_now_playing` drives a service (Phase 05). That is 9 + 2 + 3 = 14.

**Verification:**

- `Glob` - the module file exists under `src/launcherEnabled/`.
- `Grep` - `HomeWidgetGadget(` matches exactly 9 times in the module.
- `Grep` - `BuildConfig.` returns zero hits in the module.
- ~~`Grep` - none of the five excluded keys appears in this module.~~ **Predicate corrected:** none of them is *registered* - no `KEY_` constant and no `HomeWidgetGadget(` call for any of the five. They do appear in the provider's KDoc, which is where the 9 + 2 + 3 = 14 split is explained; a bare textual absence check would have forced deleting that explanation to satisfy the letter of a rule whose point is "do not register them here".

**Status:** `[x]` done - `expected: HomeWidgetGadget( x9, BuildConfig x0, zero excluded registrations | actual: 9, 0, none` - PASS.

---

### Step 03.4 - Register the set without growing the constructor

**Files:** `app_v2/src/launcherEnabled/.../gadget/LauncherGadgetRegistry.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> `LauncherGadgetRegistry` takes five gadgets by constructor today. Inject Phase 03.3's collection as a single additional parameter and concatenate it into `all()`; `byKey` keeps working unchanged because it looks up the merged list. Do not add one constructor parameter per widget - detekt's `constructorThreshold` is 10 and nineteen parameters would fail the gate. Keep the existing `KEY_*` constants; the home-widget keys live on the catalog entries, not here.

**Verification:**

- `Grep` - the registry constructor has at most 6 parameters.
- `Grep` - `fun byKey(key: String): LauncherGadget?` unchanged in signature.
- `.\a.ps1 fk` and `.\a.ps1 fkn` both pass - the source set ships in `standard` and `noLegal`.

**Status:** `[x]` done - constructor at 6 parameters, `byKey` untouched.

**Binding shape.** The collection is `@HomeWidgetGadgets List<@JvmSuppressWildcards LauncherGadget>`, qualified rather than bare. `List<LauncherGadget>` is exactly the type the registry itself deals in, so an unqualified binding added later would silently satisfy this injection point. It is `LauncherGadget` rather than `HomeWidgetGadget` so Phases 04 and 05 can add their five own-class gadgets to the same binding without changing the registry again.

**This step's own verification predicate is insufficient, and that is worth fixing in future plans.** `.\a.ps1 fk` and `fkn` both reported BUILD SUCCESSFUL (1m18s / 54s) on a version of this code whose Dagger graph did not build: those targets run `compileStandardDebugKotlin` and stop, while Dagger validates in `hiltJavaCompile`. The next fuller run failed with `@HomeWidgetGadgets List<? extends LauncherGadget> cannot be provided` - Kotlin compiles a `List<Foo>` parameter to Java `List<? extends Foo>`, which Dagger keys differently from the module's `List<Foo>`. Fixed with `@JvmSuppressWildcards`, the same shape `ResolvePanelRouteAvailabilityUseCase` already uses for its injected `Set`. **Any phase that adds a binding must verify with a target that runs kapt+hilt to completion, not with `fk`.**

`expected: DI graph builds and launcher tests pass | actual: see Phase Done Criteria below`.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles AND the Dagger graph builds - the proof is the run that reached `hiltJavaCompileStandardDebug` and `testStandardDebugUnitTest`, BUILD SUCCESSFUL in 1m45s, not the earlier Kotlin-only `fk`/`fkn` passes. Launcher suites: `expected: all green | actual: LauncherDesktopRepositoryImplTest 21, LauncherCellDaoTest 12, LauncherGridGeometryTest 11, LauncherStarterSetsTest 10, LauncherStarterSetsParityTest 1, GoogleDomainBrowserLauncherTest 3 - 58 tests, 0 failures, 0 errors` - PASS.
- [x] detekt scoped gate PASS over all twelve touched files - five findings fixed, one caused by this phase (`CyclomaticComplexMethod` 25/20 on the availability chain, split into a helper that keeps the single terminal default) and four pre-existing non-baselined findings that only surfaced because the files entered the changed set.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] No `BuildConfig.IS_*` / `SUPPORT_*` / `ENABLE_*` guard added anywhere under `src/main` - the only `src/main` change is the flavor-neutral `gadgetKey` field.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. The gadget view holds no Flow, receiver or listener, so there is nothing to unregister; the tap goes through `host.run` and therefore through the one shared launch guard rather than a fifth failure path.

---

## Handoff Notes to Next Phase

`gadgetKey` is now a persistence format. Phases 04 and 05 add their four gadgets under the same keys and must reuse `HomeWidgetGadget`'s layout and tap contract wherever their behaviour does not genuinely differ.

---

## Rollback Plan

Revert the phase commit. No cell has been written with a home-widget key yet - Phase 06 is what first persists one - so no stored desktop is orphaned.
