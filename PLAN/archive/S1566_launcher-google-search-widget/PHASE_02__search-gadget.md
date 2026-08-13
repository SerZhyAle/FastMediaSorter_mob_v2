# Phase 02 - Search gadget on the desktop

**Strategic spec:** [`../S1566_launcher-google-search-widget.md`](../S1566_launcher-google-search-widget.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-08-11
**Completed:** 2026-08-11

---

## Objective

Ship the placeable gadget: strings, layout with an editable search field, the gadget class, and its
registration, so a user can add the cell from the picker and search from it.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `WebSearchLaunchManager.launch` exists and returns `Boolean`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a - four keys added |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a - four keys added |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a - four keys added |
| `app_v2/src/main/res/drawable/bg_launcher_search_field.xml` | New | ≤ 15 |
| `app_v2/src/launcherEnabled/res/layout/gadget_launcher_search.xml` | New | ≤ 70 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/SearchGadget.kt` | New | ≤ 130 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadgetRegistry.kt` | Modified | ≤ 130 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
>
> **Landscape parity.** `res/layout-land/gadget_launcher_search.xml` is deliberately absent: no gadget layout
> has a landscape variant, because the desktop grid measures the cell and the layout is orientation-neutral.
>
> **Flavor placement.** The gadget class and its layout live in `launcherEnabled`. Strings stay centralised in
> `src/main/res/values*`, which is where every other gadget's strings live even though the class does not.

---

## Steps

### Step 02.1 - Add the four user-visible strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add four keys in one lockstep call each, using
> `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En .. -Ru .. -Uk ..`:
> `launcher_gadget_search` - the picker label, one noun phrase such as Search;
> `launcher_gadget_search_hint` - the hint inside the field, such as Search the web;
> `launcher_gadget_search_description` - the TalkBack description of the field;
> `launcher_gadget_search_no_browser` - the message shown in the cell when the search page cannot be opened.
> Check each string against `docs/COMMUNICATION_POLICY.md` §2 for the message formula of its type and §6 for
> the tone checklist. The failure message states what happened and what the user can do, and never blames the
> device. Then run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_gadget_search"`.

**Why:**

Strategic §3.2 makes EN, RU and UK mandatory for every new string, and §11.3 requires the cell to say
something on a device with no browser rather than stay silent, which is the only reason the fourth key exists.

**Verification:**

- `Grep` - each of the four keys matches exactly once in each of the three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_gadget_search"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-08-11 - Verification 3\3 PASS. Files: app_v2/src/main/res/values/strings.xml, values-ru, values-uk (4 keys each, added through set-android-string.ps1 -Action add). check_strings_localized -KeyPrefix "launcher_gadget_search" exit 0, all 4 present in en/ru/uk. The ten best-effort locales report the keys untranslated, which is expected and is S1420's tranche work, not a gap here. Tone: the failure line states what happened and the one action that fixes it, carries no raw exception text and does not blame the device. Dev log recorded.

---

### Step 02.2 - Add the gadget layout

**Files:** `app_v2/src/launcherEnabled/res/layout/gadget_launcher_search.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `gadget_launcher_search.xml` with a `<merge xmlns:tools=".." tools:parentTag="android.widget.FrameLayout">`
> root, the shape every gadget layout uses because the view inflates the layout into itself. Inside it, a
> vertical container shaped like a search field: a horizontal row holding
> `@drawable/ic_search` and an `EditText` with id `gadgetSearchInput`, plus a `TextView` with id
> `gadgetSearchMessage` that is `gone` by default and carries the no-browser message. The field sets
> `android:hint="@string/launcher_gadget_search_hint"`,
> `android:contentDescription="@string/launcher_gadget_search_description"`,
> `android:inputType="text"`, `android:imeOptions="actionSearch"`, `android:maxLines="1"` and
> `android:focusable="true"`. Take every colour from `?attr/` or `@color/` - no hardcoded hex. The layout must
> stay legible at the two-column minimum width, so no fixed widths and no side-by-side text beyond the icon
> and the field.
>
> Give the row the pill shape the owner asked for through a new themed drawable
> `app_v2/src/main/res/drawable/bg_launcher_search_field.xml`: a rectangle with a large corner radius, a
> `?attr/colorSurfaceVariant` fill and a `?attr/colorOutline` stroke.

**Background constraint (verified 2026-08-11, do not reuse `bg_rounded_input`):** the existing rounded input
drawable hardcodes `#30FFFFFF` and `#40FFFFFF`, which suits the dark overlay of the text viewer that owns it
and would be unreadable on a light-theme desktop cell. The gadget therefore gets its own theme-attribute
drawable rather than borrowing that one.

**Why:**

Strategic §3.1 asks the cell to read as a search box rather than another icon, §6.3 sets the resize floor
below the seed size so the layout must survive two columns, and §3.2 requires the difference to be carried by
text and shape rather than colour alone.

**Verification:**

- `Glob` - `app_v2/src/launcherEnabled/res/layout/gadget_launcher_search.xml` exists.
- `Grep` - `tools:parentTag="android.widget.FrameLayout"` present.
- `Grep` - `android:id="@+id/gadgetSearchInput"` and `android:id="@+id/gadgetSearchMessage"` each match once.
- `Grep` - `android:imeOptions="actionSearch|flagNoExtractUi"` present. The second flag was added over the plan's original `actionSearch`: it matches the all-apps search box and keeps the IME out of fullscreen extract mode, which in landscape would cover the desktop with a full-screen text editor.
- `Grep` - `="#` returns zero hits in that file.
- `Glob` - `app_v2/src/launcherEnabled/res/layout-land/gadget_launcher_search.xml` does not exist, matching every other gadget layout.

**Status:** `[x]` done

**Step Log:**

- 2026-08-11 - Verification 6\6 PASS. Files: app_v2/src/main/res/drawable/bg_launcher_search_field.xml (new, 12 LOC), app_v2/src/launcherEnabled/res/layout/gadget_launcher_search.xml (new, 70 LOC). Icon tint uses `app:tint` rather than `android:tint` plus a lint suppression, so no warning is silenced. Field carries `importantForAutofill="no"`, which is the honest answer for a web-search box and removes the Autofill lint without a `tools:ignore`. Dev log recorded.

---

### Step 02.3 - Add `SearchGadget` and its view

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/SearchGadget.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Create `SearchGadget` as an `@Inject constructor` class implementing `LauncherGadget`, taking
> `dagger.Lazy<WebSearchLaunchManager>`. Set `key = LauncherGadgetRegistry.KEY_SEARCH`, `defaultSpanW = 2`,
> `defaultSpanH = 1`, `minSpanW = 1`, `minSpanH = 1`, `labelRes = R.string.launcher_gadget_search`,
> `iconRes = R.drawable.ic_search`, `requiresResourceParam = false`. Return a private
> `SearchGadgetView : LauncherGadgetView` from `createView`, inflating `GadgetLauncherSearchBinding`. In the
> view, set an `OnEditorActionListener` for `IME_ACTION_SEARCH` that reads the field, calls
> `launch(context, query)`, and on a `true` result clears the field and hides the keyboard, on a `false`
> result shows `gadgetSearchMessage` with `launcher_gadget_search_no_browser`. Do not override `onActive` -
> the gadget observes nothing. Do not persist the query, do not write it into the cell `param`, and do not
> keep any history.

**Why:**

Strategic ADR-2 forbids storing the query because a search string left on a home screen is personal data
visible to anyone holding the device, and §3.2 forbids background subscriptions for this gadget since it has
nothing to observe until the user types.

**Verification:**

- `Glob` - `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/SearchGadget.kt` exists.
- `Grep` - `class SearchGadget @Inject constructor` matches exactly once.
- `Grep` - `override val defaultSpanW: Int = 2` and `override val minSpanW: Int = 1` present.
- `Grep` - `IME_ACTION_SEARCH` present.
- `Grep` - `onActive` returns zero hits in that file.
- `Grep` - `param` is not written to persistence anywhere in that file.
- `Grep` - `Log\.d\(` returns zero hits in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-11 - Verification 7\7 PASS. Files: app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/SearchGadget.kt (new, 78 LOC). Closed together with step 02.4 in one post-change: neither compiles alone, since the gadget names `KEY_SEARCH` and the registry names `SearchGadget`, so a step boundary between them would leave the tree not building. Added over the prompt: a blank field returns early instead of reporting the no-browser message, because "nothing typed" and "nothing can open it" are different states and only the second deserves the message. Dev log recorded.

---

### Step 02.4 - Register the gadget

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadgetRegistry.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add `const val KEY_SEARCH = "search"` to the registry companion, next to the other keys and under the same
> never-renamed contract. Add `search: SearchGadget` as a constructor parameter and include it in the
> `gadgets` list. Record in a comment that this takes the constructor to nine parameters against detekt's
> threshold of ten, so the next gadget joins a qualified list module instead of the constructor.

**Why:**

Strategic §3.2 makes the key a stored value inside a cell's `target` column that is never renamed after
release, and the registry is the single place the desktop learns a gadget exists, so the cell cannot be
offered or restored without this entry.

**Verification:**

- `Grep` - `const val KEY_SEARCH = "search"` matches exactly once.
- `Grep` - `search: SearchGadget` present in the constructor.
- `Grep` - `listOf(clock, weather, playlist, streams, folderPreview, search)` or an equivalent list including `search` present.
- `.\a.ps1 fk` exits 0.
- `.\a.ps1 fkn` exits 0 - `noLegal` compiles the same source set.

**Status:** `[x]` done

**Step Log:**

- 2026-08-11 - Verification 5\5 PASS. Files: app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadgetRegistry.kt (modified, +6 LOC). `a.ps1 fk` BUILD SUCCESSFUL in 1m2s, `a.ps1 fkn` BUILD SUCCESSFUL in 1m8s; the only warnings are two pre-existing deprecations in files this ticket does not touch. Neither target validates the Hilt graph, so the phase build is what proves the new constructor parameter resolves.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` BUILD SUCCESSFUL in 1m, APK v2.60.8082.309-DEBUG. This run executed
      `hiltJavaCompileStandardDebug`, which is what proves the new `search: SearchGadget` constructor
      parameter resolves in the graph; `fk` alone would not have caught a missing binding.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - 2773 records, up from 2771, the two new classes.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Layer 1: the gadget holds no business logic,
      URL assembly stays in the manager, the class is 78 LOC. Layer 2: no coroutine and no `onActive`
      override, so the phase adds nothing that needs cancelling. Layer 3: the editor-action listener is set
      on this view's own child and dies with the view on the next desktop rebind - the listener-symmetry gate
      reports imbalance 0. Layer 4: no Room surface.
- [x] UI placement decision recorded - owner quiz of 2026-08-11, strategic §6.1 and §6.3, quoted in §3.3.
- [ ] **Screenshot deferred - device present but wrong device.** `RFCR110NBQJ` is the owner's working phone.
      Reaching the launcher desktop requires enabling launcher mode in-app and then accepting the system
      Home-app chooser, and a system role must never be granted on the owner's phone. The shot belongs on an
      emulator and is folded into this ticket's device test, which the ticket is parked on anyway.

---

## Handoff Notes to Next Phase

`KEY_SEARCH` is `"search"` and the gadget seeds at 2x1. Phase 03 duplicates that literal into
`LauncherStarterSets` on purpose, and the parity test is what keeps the two copies equal.

---

## Rollback Plan

Revert phase commit(s). The four string keys and the registry entry go with them; no cell can exist yet with
`target = "search"` because seeding arrives only in Phase 03, so no stored data is orphaned.
