# Phase 02 - Collapsible groups in the dialog

**Strategic spec:** [`../S1422_launcher-settings-collapsible-groups.md`](../S1422_launcher-settings-collapsible-groups.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Wrap the fourteen existing rows of the launcher settings dialog into the four collapsible groups defined in
`INDEX.md`, in both orientations, and register them with the shared `CollapsibleSectionsManager`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - the four group titles resolve.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/dialog_launcher_settings.xml` | Modified | ≤ 60 added |
| `app_v2/src/main/res/layout-land/dialog_launcher_settings.xml` | Modified | ≤ 60 added |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/LauncherSettingsDialogFragment.kt` | Modified | ≤ 30 added |

Both orientations of `dialog_launcher_settings.xml` exist and are edited together (CLAUDE.md Rule 11); they
share one ViewBinding, so every new id must appear in both files with the same name.

---

## Steps

### Step 02.1 - Group the portrait layout

**Files:** `app_v2/src/main/res/layout/dialog_launcher_settings.xml`

**Depends on:** - start of phase

**Prompt for developer:**

> Insert four `com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader` / `LinearLayout` pairs into
> the root vertical `LinearLayout`, after the title-and-close header row, and move each existing row inside its
> group's container without changing the order of the rows themselves. Use the ids, store keys, titles and
> icons from `INDEX.md`'s group table, in the order given there: Taskbar, Top bar, Desktop, System.
>
> Header shape follows the settings screens - `android:layout_width="match_parent"`,
> `android:layout_height="wrap_content"`, `app:csh_title="@string/launcher_settings_group_<slug>"` and
> `app:csh_icon="@drawable/<icon>"`. Container shape follows them too - vertical `LinearLayout`,
> `match_parent` × `wrap_content`, `android:paddingStart`/`paddingEnd`/`paddingBottom="@dimen/margin_small"`.
>
> Do not add, remove, rename or reorder any row id, and do not touch the reset or close buttons - they stay
> outside every group. Update the two stale comments the grouping invalidates: the `S1415` comment that hands
> the grouping of the tray run to S1410, and the `S1087` comment that explains the status-area row's placement
> by its neighbour rather than by its group.

**Why:**

Strategic §1 states the flat list stopped being readable at fourteen rows and §3.4 fixes which rows belong to
which group, while §5 requires the rows keep their existing order so the change is grouping only.

**Verification:**

- `Grep` - `headerLauncherTaskbar`, `headerLauncherTopBar`, `headerLauncherDesktop`, `headerLauncherSystem` each match exactly once in the file.
- `Grep` - `containerLauncherTaskbar`, `containerLauncherTopBar`, `containerLauncherDesktop`, `containerLauncherSystem` each match exactly once in the file.
- `Grep` - `CollapsibleSectionHeader` matches exactly four times.
- `Grep -o "android:id=\"@\+id/row[A-Za-z0-9]*\""` returns the fourteen existing row ids in unchanged relative order.
- `Grep` - `Grouping this run of rows belongs to S1410` returns zero hits.
- `.\a.ps1 fr` exits 0.

**Status:** `[x]` done

---

### Step 02.2 - Group the landscape layout identically

**Files:** `app_v2/src/main/res/layout-land/dialog_launcher_settings.xml`

**Depends on:** Step 02.1

**Prompt for developer:**

> Apply the same four header/container pairs, the same ids, the same order and the same icons to the landscape
> file. The landscape variant is a single scrolling column identical to portrait, as its own file header
> comment states, so the two files stay structurally identical.

**Why:**

The two orientations share one ViewBinding, so a header or container id present in only one file fails to
generate a binding field and the registration in Step 02.3 cannot compile.

**Verification:**

- `Grep` - the four `headerLauncher*` and four `containerLauncher*` ids each match exactly once in the landscape file.
- `Grep` - `CollapsibleSectionHeader` matches exactly four times in the landscape file.
- Both files declare the same fourteen row ids in the same order.
- `.\a.ps1 fr` exits 0.

**Status:** `[x]` done

---

### Step 02.3 - Register the four sections

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/LauncherSettingsDialogFragment.kt`

**Depends on:** Step 02.2

**Prompt for developer:**

> Add `private val sectionsManager by lazy { CollapsibleSectionsManager(requireContext()) }` and a
> `setupCollapsibleSections()` private function called from `onViewCreated` before `setupRows()`. Register the
> four sections with the store keys from `INDEX.md`: `launcher__taskbar`, `launcher__top_bar`,
> `launcher__desktop`, `launcher__system`. Pass `defaultExpanded = true` for `launcher__top_bar` only; the
> other three take the parameter's default.
>
> Nothing else changes - the manager restores saved state without animation, animates user toggles and persists
> every change on its own.

**Why:**

Strategic §2 fixes the initial state as "only Top bar expanded" and §3.1 records that persistence, restore and
animation already come from `CollapsibleSectionsManager`, so this ticket registers sections instead of writing
new state handling.

**Verification:**

- `Grep` - `CollapsibleSectionsManager(requireContext())` matches exactly once in the file.
- `Grep` - `sectionsManager.register(` matches exactly four times.
- `Grep` - `"launcher__top_bar"` appears on a call that also carries `defaultExpanded = true`.
- `Grep` - `setupCollapsibleSections()` is called from `onViewCreated`.
- `Grep -n "Log\.d\("` returns zero hits in the file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

## Step Log

- 2026-08-07 - Step 02.1 done. Portrait layout rewritten with four header/container pairs. Verification PASS: `CollapsibleSectionHeader` matches 4, each of the four `headerLauncher*` and `containerLauncher*` ids matches once, the fourteen `row*` ids come out in unchanged order, the stale `Grouping this run of rows belongs to S1410` sentence is gone, `.\a.ps1 fr` exit 0 (`BUILD SUCCESSFUL in 8s`). The `S1087` comment was rewritten to stop explaining the row by its former neighbour.
- 2026-08-07 - Step 02.2 done. Landscape file rewritten to match. Verification PASS: 4 headers, each of the eight new ids once, `diff` of the full id list between the two files is empty (IDENTICAL), `.\a.ps1 fr` exit 0 (`BUILD SUCCESSFUL in 7s`).
- 2026-08-07 - Step 02.3 done. Verification PASS: `CollapsibleSectionsManager(requireContext())` once, `sectionsManager.register(` four times, `setupCollapsibleSections()` present as declaration plus the call in `onViewCreated`, zero `Log.d(`, no line over 120 chars, `.\a.ps1 fk` exit 0 (`BUILD SUCCESSFUL in 50s`). `defaultExpanded = true` sits on its own line inside the wrapped `launcher__top_bar` call (line 106) because the single-line form is 140 chars.
- 2026-08-07 - Closure note: the `post-change.ps1` facade runs once for all three files in Step 03.3 instead of once per step. Running it per step would fire the settings-doc-sync gate against a manifest that Phase 03 has not regenerated yet, i.e. a known-stale FAIL three times over. Per-step dev-log rows were written by hand.
- 2026-08-07 - Screenshot deferred (no device). `device-ready.ps1` reported `state: no-device` at session start, so the S1338 UI-phase screenshot cannot be taken this phase. The placement decision it guards is recorded - strategic §2 quotes the owner's ruling and §3.4 fixes the group composition - and this phase's own Done Criteria do not demand the shot, so the deferral is recorded and the phase continues. The visual check moves to the device-test gate.
- 2026-08-07 - Phase-boundary audit (CLAUDE.md §13). Layer 1: the fragment gained one lazy field and one four-line function that only declares which rows group together; no business logic entered the UI layer, the file is 296 LOC, and the two rewritten comments state why rather than what. Layer 2: `by lazy { CollapsibleSectionsManager(requireContext()) }` is the same shape eight other fragments and three dialogs already use; the lazy is first touched in `onViewCreated`, never after detach. Layer 3: the manager holds only the SharedPreferences-backed store - the per-section listener lives on the header view and dies with the binding, so re-entering the dialog re-registers against fresh views without leaking the old ones. Layer 4 not applicable - no Room surface. No P0/P1 findings.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` exit 0, `BUILD SUCCESSFUL in 24s`, APK `FastMediaSorter_standard_debug_v2.60.8071.632-DEBUG.apk`.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1` - three rows.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (see Step Log).

---

## Handoff Notes to Next Phase

The dialog now hosts four persisted collapsible groups; the settings manifest and reference still describe the
pre-grouping layout and are regenerated in Phase 03.

---

## Rollback Plan

Revert the phase commit - markup and registration only, no persisted user data is written beyond the four
collapsed-state keys, which fall back to their defaults when absent.
