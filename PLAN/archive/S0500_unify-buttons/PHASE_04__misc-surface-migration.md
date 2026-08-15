# Phase 04 - Misc surface button migration

**Strategic spec:** [`../S0500_unify-buttons.md`](../S0500_unify-buttons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-06-18
**Completed:** 2026-06-18

**Step Log:**

- 2026-06-18 - 04.1 plain `<Button>` -> MaterialButton + role family across welcome, permissions_management (+land), open_source_licenses, item_extension, item_paging_load_state, activity_main (+land), activity_browse (+land); bare primaries (btn_grant_all/btn_continue_to_app/btnGrantAll) -> Button.Filled; retry buttons (raw M3 TextButton) -> Button.Text. widget_scheduled_tasks left untouched (RemoteViews). 04.2 item_destination_button @color/white -> ?attr/colorOnPrimary; also welcome btnNext/btnFinish @color/white -> ?attr/colorOnPrimary (x3 width variants). 04.3 no literal button strings on misc surfaces. 04.4 build PASS. neuroslop-gate exit 0 (deltas 0).

---

## Objective

Migrate the remaining plain `<Button>` widgets on non-dialog surfaces (welcome, permissions fragment, list items, activity bars, licenses) to `MaterialButton` + the `Button.*` family, and remove the inline hex on `item_destination_button.xml` (strategic §6 fork 7). The RemoteViews app-widget layout is explicitly excluded.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/page_welcome_permissions.xml` + `layout-land/` | Modified | ≤ 300 |
| `app_v2/src/main/res/layout/fragment_permissions_management.xml` + `layout-land/` | Modified | ≤ 400 |
| `app_v2/src/main/res/layout/fragment_open_source_licenses.xml` | Modified | ≤ 300 |
| `app_v2/src/main/res/layout/item_extension.xml` | Modified | ≤ 200 |
| `app_v2/src/main/res/layout/item_paging_load_state.xml` | Modified | ≤ 200 |
| `app_v2/src/main/res/layout/activity_main.xml` + `layout-land/` | Modified | ≤ 400 |
| `app_v2/src/main/res/layout/activity_browse.xml` + `layout-land/` | Modified | ≤ 600 |
| `app_v2/src/main/res/layout/item_destination_button.xml` | Modified | ≤ 60 |
| `app_v2/src/main/res/values/strings.xml` + `values-ru/` + `values-uk/` | Modified | n/a |

> **Landscape parity (Rule 11):** `page_welcome_permissions`, `fragment_permissions_management`, `activity_main`, `activity_browse` have `layout-land/` twins - edit both. `fragment_open_source_licenses`, `item_extension`, `item_paging_load_state`, `item_destination_button` have no `layout-land/` twin (single-orientation / list-item) - correct.
> **Out-of-scope reminder:** do NOT touch `widget_scheduled_tasks.xml` - RemoteViews app-widget, `MaterialButton` unsupported (see INDEX Out-of-scope).

---

## Steps

### Step 04.1 - Migrate plain `<Button>` by role on misc surfaces

**Files:** all layouts in "Files Touched" except `item_destination_button.xml` (portrait + land)
**Depends on:** - start of phase

**Prompt for developer:**

> Replace each plain `<Button>` with `com.google.android.material.button.MaterialButton` and assign a `Button.*` family style by existing emphasis (Filled = primary/confirm, Tonal = secondary emphasis, Outlined = secondary outline, Text = low-emphasis/cancel) - same rule as Phase 03. Preserve id, text, focus attributes (`focusable`, `nextFocus*`), and constraints. In `activity_browse.xml` / `activity_main.xml` control bars, keep any existing `nextFocus*` / focus drawable so D-pad/TV navigation is unchanged (Rule 16).

**Verification:**

- `Grep` (`-oE "<Button\b"`) - zero plain `<Button>` elements remain in the listed misc layouts (portrait + land).
- `Grep` - migrated buttons reference a `@style/Widget.FastMediaSorter.Button.*` style.
- `Grep` - `widget_scheduled_tasks.xml` still contains its two plain `<Button>` (untouched).

**Status:** `[x]` done

---

### Step 04.2 - Remove inline hex on item_destination_button (fork 7)

**Files:** `app_v2/src/main/res/layout/item_destination_button.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace `android:textColor="@color/white"` (line ~10) with `android:textColor="?attr/colorOnPrimary"` so the label colour is theme-driven and safe on light theme.

**Verification:**

- `Grep` - zero `@color/white` and zero `="#` hex on any colour attribute in `item_destination_button.xml`.
- `Grep` - `?attr/colorOnPrimary` present on the text colour attribute.

**Status:** `[x]` done

---

### Step 04.3 - Extract hardcoded button strings to EN/RU/UK

**Files:** misc layouts above, `values/strings.xml` (+ ru/uk)
**Depends on:** Step 04.1

**Prompt for developer:**

> Grep the touched layouts for literal `android:text="..."` on buttons (not `@string/`). For each, add a key across all three locales in one `scripts/utils/set-android-string.ps1 -Action add -Key <key> -En -Ru -Uk` call and repoint `android:text` to `@string/<key>`. Strings must pass `docs/COMMUNICATION_POLICY.md` §2 and §6.

**Verification:**

- `Grep` - zero literal `android:text="[A-Za-z]` on buttons in the touched layouts.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "<key_prefix>"` exits 0 (skip if no new keys were needed).
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 04.4 - Compile gate

**Files:** (none - build only)
**Depends on:** Steps 04.1-04.3

**Prompt for developer:**

> Build standard debug.

**Verification:**

- `/build` -> `standard debug` PASS.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] No plain `<Button>` remains on any in-scope surface (repo-wide grep returns only `widget_scheduled_tasks.xml`).
- [ ] No inline hex / `@color/white` on `item_destination_button.xml`.
- [ ] Dev log entry added for the touched file batch (may defer to Phase 05 batch).

---

## Handoff Notes to Next Phase

- All in-scope plain `<Button>` are now `MaterialButton` + family. The only remaining plain `<Button>` repo-wide is `widget_scheduled_tasks.xml` (RemoteViews, intentional). Phase 05 finalises catalog/dev-log and runs the audit.

---

## Rollback Plan

Revert the phase commit(s). Layout + string-resource change only; no behaviour change.
