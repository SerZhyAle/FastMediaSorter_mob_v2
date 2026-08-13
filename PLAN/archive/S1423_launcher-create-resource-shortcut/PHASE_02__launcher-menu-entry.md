# Phase 02 - Launcher menu entry point

**Strategic spec:** [`../S1423_launcher-create-resource-shortcut.md`](../S1423_launcher-create-resource-shortcut.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Add the single shared "start resource creation from the home screen" call, and the launcher-menu row that is its first caller.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved - both are.
- [ ] Working tree is clean or on a feature branch.
- [ ] `AddResourceActivity.createIntent` accepts `pinShortcutOnCreate` (Phase 01).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 2 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 2 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 2 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherResourceCreateManager.kt` | New | ≤ 60 |
| `app_v2/src/launcherEnabled/res/layout/fragment_launcher_start_menu.xml` | Modified | ≤ 12 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherStartMenuFragment.kt` | Modified | ≤ 10 |

> **Landscape parity (Rule 11).** `fragment_launcher_start_menu.xml` exists only under `app_v2/src/launcherEnabled/res/layout/`. The sibling `res/layout-land/` directory holds `activity_launcher_home.xml` alone. Landscape variant absent - not needed; the sheet uses the one layout in both orientations.
>
> **Flavor placement.** Every new and modified non-string file in this phase is under `app_v2/src/launcherEnabled/`, which `app_v2/build.gradle.kts` mounts for standard and noLegal only - the same set that carries the rest of the home screen. No `BuildConfig.SUPPORT_LAUNCHER` guard is written into `src/main`.

---

## Steps

### Step 02.1 - Add the launcher menu row string in EN/RU/UK

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add key `launcher_create_resource_menu_row` across all three locales in one lockstep call:
>
> ```powershell
> pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key launcher_create_resource_menu_row -En "Create resource" -Ru "Создать ресурс" -Uk "Створити ресурс"
> ```
>
> Do not hand-edit the three files. Check the wording against `docs/COMMUNICATION_POLICY.md` §2 for the message type and §6 for the tone checklist before running the command. Place it with the other `launcher_menu_*` keys.

**Why:**

Strategic §3.2 makes EN/RU/UK mandatory and names the entry-point label as the one string the feature is certain to add, so an English-only key would ship the launcher menu half-translated for the two locales the app supports.

**Verification:**

- `Grep` - `launcher_create_resource_menu_row` matches exactly once in each of the three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_create_resource"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

---

### Step 02.2 - Add `LauncherResourceCreateManager`, the one shared call

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherResourceCreateManager.kt` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Create `LauncherResourceCreateManager` with an `@Inject constructor()` and one function that takes a `Context` and starts `AddResourceActivity.createIntent(context, pinShortcutOnCreate = true)`. Nothing else belongs in it: it holds no state, knows nothing about how a resource is created, and never inspects the outcome - Phase 01 already put the pin at the point where success is known.
>
> Place it beside the other launcher helpers in `ui/launcher/helpers/`. Add a KDoc line recording that this is the single launch path shared by every home-screen entry point, so S1466 adds a caller here rather than a third handler.

**Why:**

Strategic §5.1.1 requires the launch to be one shared call rather than one handler per entry point, stating that three copies diverge at the first edit; §2 goal 2 and ADR-2 additionally require the existing creation screen to be invoked as it is, so this class must launch it and do nothing more.

**Verification:**

- `Glob` - `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherResourceCreateManager.kt` exists.
- `Grep` - `class LauncherResourceCreateManager` matches exactly once in that file.
- `Grep` - `pinShortcutOnCreate = true` matches in that file.
- `Grep` - `AddResourceActivity.createIntent` matches exactly once across `app_v2/src/launcherEnabled` (there is one launch path, not several).
- `Grep` - `Log\.d\(` returns zero hits in that file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

---

### Step 02.3 - Add the row to the start-menu layout

**Files:** `app_v2/src/launcherEnabled/res/layout/fragment_launcher_start_menu.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a `MaterialButton` with id `rowCreateResource` directly after `rowResources`, copying that row's attributes exactly: `style="@style/Widget.FastMediaSorter.Button.Text"`, `layout_width="match_parent"`, `layout_height="wrap_content"`, `android:focusable="true"`, `android:foreground="@drawable/focus_button_background"`, `android:gravity="start|center_vertical"`, `app:iconGravity="start"`. Set `android:text="@string/launcher_create_resource_menu_row"` and `app:icon="@drawable/ic_add"` (`app_v2/src/main/res/drawable/ic_add.xml`). Use no literal hex colour; the shared style carries the colours.

**Why:**

Strategic §3.3 records the owner's ruling of 2026-08-07 that one of the entry points is an item in the launcher menu, and §3.2 requires it to be focusable and usable from a keyboard and D-pad on equal terms with the other desktop elements - which the copied `focusable` and focus-foreground attributes are what deliver.

**Verification:**

- `Grep` - `rowCreateResource` matches exactly once in that layout.
- `Grep` - `@string/launcher_create_resource_menu_row` matches in that layout.
- `Grep` - `android:focusable="true"` appears in the new row's block.
- `Grep` - `="#` returns zero hits in that layout (Rule 19, no hardcoded colours).
- `Glob` - `app_v2/src/launcherEnabled/res/layout-land/fragment_launcher_start_menu.xml` does not exist, confirming the Rule 11 note above still holds.
- `.\a.ps1 fr` exits 0.

**Status:** `[x] done`

---

### Step 02.4 - Bind the row to the shared call

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherStartMenuFragment.kt`
**Depends on:** Step 02.2, Step 02.3

**Prompt for developer:**

> Inject `LauncherResourceCreateManager` with `@Inject lateinit var` beside the existing `roleManager`. In `onViewCreated`, set a click listener on `binding.rowCreateResource` that calls the manager with `requireContext()` and then `dismiss()`, matching how `rowOpenApp` and `rowEditDesktop` behave. Add no result listener and no outcome handling here - this row starts the flow and nothing more.

**Why:**

Strategic §5.1.1 states that an entry point knows nothing about creation and only launches the existing screen, and ADR-1 places the pin in the finalizing step precisely so a calling screen never catches the result itself.

**Verification:**

- `Grep` - `rowCreateResource` matches exactly once in `LauncherStartMenuFragment.kt`.
- `Grep` - `LauncherResourceCreateManager` matches in that file.
- `Grep` - `ResourceShortcutPinManager` returns zero hits in that file (the entry point does not pin).
- `Grep` - `Log\.d\(` returns zero hits in that file.
- `.\a.ps1 fk` exits 0 and `.\a.ps1 fkn` exits 0 - the file compiles in both flavors that mount `launcherEnabled`.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly). **UNPROVEN** - no gradle was run in this session (run-level deviation). Not a failure; not looked at.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`. **DEFERRED** - single batched closure is the owner's.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` - this phase adds a public class. **DEFERRED** - part of the same batched closure.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md`). Layer trigger that fires here: a new long-lived helper with a DI binding.

---

## Handoff Notes to Next Phase

`LauncherResourceCreateManager` is the only place that launches creation from the home screen. Phase 03 injects the same class into `LauncherHomeActivity` rather than repeating the intent, and S1466 will do the same for the long-press menu.

---

## Rollback Plan

Revert phase commit(s) - no data migration. The added string keys can stay; an orphaned key is caught separately by the dead-weight sweep (Rule 20).

---

## Step Log

- 2026-08-07 - Step 02.1 - Verification 3\3 PASS (key present exactly once in each of `values/`, `values-ru/`, `values-uk/`; `check_strings_localized.ps1 -KeyPrefix launcher_create_resource` exit 0; COMMUNICATION_POLICY §6 checklist clean - a two-word action label, no error/empty-state/confirmation clauses apply). Added via `set-android-string.ps1 -Action add`. Note against the prompt: the script appends to the end of `strings.xml` and has no placement control, so the key does not sit beside the other `launcher_menu_*` keys; hand-moving it would have meant hand-editing the three files, which the same prompt forbids. Ten best-effort locales report the key untranslated - not fatal, EN/RU/UK is the strict set. Files: `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`. Dev log NOT recorded - batched closure is the owner's.
- 2026-08-07 - Step 02.2 - Verification 4\5 PASS (file exists; `class LauncherResourceCreateManager` once; `pinShortcutOnCreate = true` present; `AddResourceActivity.createIntent` exactly once across `src/launcherEnabled`; `Log.d(` zero hits). `.\a.ps1 fk` NOT RUN (run-level deviation). Left unscoped rather than `@Singleton` - the prompt names only `@Inject constructor()`, and the class is stateless. Files: `src/launcherEnabled/.../ui/launcher/helpers/LauncherResourceCreateManager.kt` (new, 22 LOC). Dev log NOT recorded - batched closure is the owner's.
- 2026-08-07 - Step 02.3 - Verification 5\6 PASS (`rowCreateResource` once; `@string/launcher_create_resource_menu_row` present; `android:focusable="true"` inside the new block; `="#` zero hits; `res/layout-land/fragment_launcher_start_menu.xml` absent, so the Rule 11 note holds). `.\a.ps1 fr` NOT RUN (run-level deviation). Row is a `com.google.android.material.button.MaterialButton` written with the same fully-qualified tag and the same seven attributes as `rowResources`, so the generated binding field is `MaterialButton` like its siblings. Files: `src/launcherEnabled/res/layout/fragment_launcher_start_menu.xml` (+12 LOC). Dev log NOT recorded - batched closure is the owner's.
- 2026-08-07 - Step 02.4 - Verification 4\5 PASS (`rowCreateResource` once; `LauncherResourceCreateManager` present; `ResourceShortcutPinManager` zero hits; `Log.d(` zero hits). `.\a.ps1 fk` / `fkn` NOT RUN (run-level deviation). Files: `src/launcherEnabled/.../ui/launcher/menu/LauncherStartMenuFragment.kt` (+7 LOC). Dev log NOT recorded - batched closure is the owner's.
- 2026-08-07 - Phase 02 boundary audit (CLAUDE.md §13). Layer 1: the row starts the flow and stops there - no result listener, no outcome handling, matching `rowOpenApp`/`rowEditDesktop`. Layer 3 (the trigger - new helper with a DI binding): `LauncherResourceCreateManager` is unscoped and stateless and takes the `Context` per call, so it retains no Activity; the click listener is set in `onViewCreated` on a binding the fragment nulls in `onDestroyView`, so it dies with the view like its siblings. Flavor isolation: both code files sit under `src/launcherEnabled/`, no `BuildConfig` guard anywhere; the three string keys sit in `src/main/res/values*/strings.xml` alongside the existing `launcher_menu_*` keys, which is where the launcher's other labels already live. **No P0/P1 findings.**
- 2026-08-07 - S1338 UI-phase gate: placement decision IS on record - strategic §3.3, owner's ruling of 2026-08-07 naming the launcher menu as entry point 2. Screenshot deferred: no build was permitted this run, so no APK exists to photograph. Phase left `🚧 In Progress` for that plus the compile / dev-log / catalog criteria.

---

## Step Log - phase closure

- 2026-08-07 - Compile criterion PROVEN, by the owner session rather than the implementation run: `.\a.ps1 fk` (standard) exit 0, `check-standard-fast.ps1 -Mode Code -Flavor Lite` exit 0 - which is the one that matters here, because `ResourcePickerDialogFragment` lives in `src/main` and compiles into the four flavors that mount `src/launcherDisabled` - and `.\a.ps1 dq` exit 0, a full debug build whose kapt pass is what actually validates the Hilt graph the new `@Singleton CreatedResourcePinManager` joins (a Kotlin compile alone would not have).
- 2026-08-07 - Two detekt findings introduced by this phase were fixed before closure: `SpacingBetweenDeclarationsWithAnnotations` on the new `@Inject` field and `SpacingBetweenDeclarationsWithComments` on the new event declaration. Scoped gate afterwards: `assert-detekt -Gate -ChangedFiles <all seven Kotlin files>` -> `PASS [scoped]`.
- 2026-08-07 - Dev log, catalog regen and the `ALL_FEATURES` record ran as one batched closure in Phase 04: `post-change: PASS WITH ADVISORIES (1)`, the single advisory being detekt-preflight attributing findings to files outside this change (verified by the scoped run above).
- 2026-08-07 - **UI-gate screenshot: DEFERRED, and here is exactly why.** An emulator run was attempted against the fresh APK. It never reached the launcher desktop: the walk toggled "Use as home screen" and worked through the resulting cascade of system permission dialogs, but the system "Home app" chooser still offered only Pixel Launcher - our app was absent, meaning `LauncherHomeActivity` was still disabled and the in-app path that enables the component had not actually run. The emulator then dropped offline mid-walk and did not return. The placement decision itself IS on record (strategic 3.3, owner ruling of 2026-08-07), so the first half of the S1338 gate holds; the visual half moves to the device test this ticket is now parked for, and its status note carries the setup sequence.