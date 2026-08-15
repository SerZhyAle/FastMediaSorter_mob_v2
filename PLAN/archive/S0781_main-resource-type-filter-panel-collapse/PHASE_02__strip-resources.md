# Phase 02 - Strip Resources

**Strategic spec:** [`../S0781_main-resource-type-filter-panel-collapse.md`](../S0781_main-resource-type-filter-panel-collapse.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase (resources)
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-07-01
**Completed:** 2026-07-01

---

## Objective

Add the dedicated strip color, the trilingual strip label, and the collapsed-strip view (a narrow, focusable, colored row, initially `GONE`) as a sibling of `tabResourceTypes` in all three `activity_main.xml` variants. No Kotlin yet.

---

## Prerequisites

- [ ] None beyond a clean tree.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/colors.xml` | Modified | n/a |
| `app_v2/src/main/res/values-night/colors.xml` | Modified (if present) | n/a |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/layout/activity_main.xml` | Modified | n/a |
| `app_v2/src/main/res/layout-land/activity_main.xml` | Modified | n/a |
| `app_v2/src/main/res/layout-w600dp/activity_main.xml` | Modified | n/a |

> **Landscape parity:** all three layout variants are edited in this phase (steps 02.3 portrait, 02.4 land, 02.5 w600dp - wait, see steps below; the strip is added to every variant that hosts `tabResourceTypes`).

---

## Steps

### Step 02.1 - Add the dedicated strip colors

**Files:** `res/values/colors.xml` (+ `res/values-night/colors.xml` if it exists)
**Depends on:** - start of phase

**Prompt for developer:**

> Add a dedicated, theme-appropriate colored background for the collapsed filter strip plus a contrasting foreground:
> - `<color name="main_resource_filter_strip_background">..</color>` - a clearly "colored" tone (the owner asked for a distinct hue, like the player's green/blue copy/move strips), not `colorSurface`.
> - `<color name="main_resource_filter_strip_foreground">..</color>` - label/chevron color with adequate contrast.
> If `res/values-night/colors.xml` exists, add night variants of BOTH so the strip stays legible on dark theme. Do NOT inline hex into any layout (Rule 19) - the hex lives only here in `colors.xml`.

**Verification:**

- `Grep` - `main_resource_filter_strip_background` matches in `res/values/colors.xml`.
- `Grep` - `main_resource_filter_strip_foreground` matches in `res/values/colors.xml`.

**Status:** `[x]` done

---

### Step 02.2 - Add the trilingual strip label

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one string key in lockstep across EN/RU/UK via the byte-preserving tool (single call, parity-enforced):
> `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key main_resource_type_filter_strip -En "Resource type filter" -Ru "Фильтр типов ресурсов" -Uk "Фільтр типів ресурсів"`
> The label is the strip's only text; keep it short (it sits on one narrow line). Tone: it is a static label, not a message - verify against `docs/COMMUNICATION_POLICY.md` §6 (concise, no trailing period, sentence case per locale).

**Verification:**

- `Grep` - `main_resource_type_filter_strip` matches once in each of `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "main_resource_type_filter_strip"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 02.3 - Add the collapsed strip view to the portrait layout

**Files:** `res/layout/activity_main.xml`
**Depends on:** Step 02.1, Step 02.2

**Prompt for developer:**

> Add the collapsed strip as a sibling immediately AFTER `tabResourceTypes` inside the same `AppBarLayout` (so it occupies the same row position when shown). Use a horizontal `LinearLayout`:
> - `android:id="@+id/resourceTabsCollapsedStrip"`, `layout_width="match_parent"`, `layout_height="wrap_content"`, narrow vertical padding (a thin strip), `android:gravity="center"`.
> - `android:background="@color/main_resource_filter_strip_background"`, `android:visibility="gone"`.
> - `android:focusable="true"`, `android:clickable="true"`, `android:foreground="@drawable/focus_button_background"`.
> - D-pad parity with the TabLayout it replaces: `android:nextFocusUp="@id/btnStartPlayer"`, `android:nextFocusDown="@id/rvResources"`.
> - Children: a `TextView` `@+id/resourceTabsCollapsedStripLabel` (`text="@string/main_resource_type_filter_strip"`, `textColor="@color/main_resource_filter_strip_foreground"`) and an expand-affordance `ImageView` (`@drawable/ic_expand_more` or equivalent, `tint="@color/main_resource_filter_strip_foreground"`, `contentDescription` = the same label) so users see it is tappable.
> No inline hex; colors come from step 02.1, text from step 02.2.

**Verification:**

- `Grep` - `@+id/resourceTabsCollapsedStrip` matches once in `res/layout/activity_main.xml`.
- `Grep` - `main_resource_filter_strip_background` matches once in `res/layout/activity_main.xml` (no `#` hex literal).

**Status:** `[x]` done

---

### Step 02.4 - Mirror the strip in the landscape and w600dp layouts

**Files:** `res/layout-land/activity_main.xml`, `res/layout-w600dp/activity_main.xml`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add the identical `resourceTabsCollapsedStrip` block (same ids, attributes, children) immediately after `tabResourceTypes` in BOTH `res/layout-land/activity_main.xml` and `res/layout-w600dp/activity_main.xml`. Match each variant's existing `nextFocusUp`/`nextFocusDown` neighbors of `tabResourceTypes` in that file (read the variant's TabLayout block first - the neighbor ids can differ per variant). Keep all three variants structurally in sync.

**Verification:**

- `Grep` - `@+id/resourceTabsCollapsedStrip` matches once in `res/layout-land/activity_main.xml` AND once in `res/layout-w600dp/activity_main.xml`.
- `Grep` - no `#` hex color literal added near the new block in either file.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fr` for resource/manifest packaging is sufficient).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] `scripts/check_strings_localized.ps1 -KeyPrefix "main_resource_type_filter_strip"` exits 0.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Phase 03 binds these ids: `resourceTabsCollapsedStrip` (the tap-to-expand target, focus stop), `resourceTabsCollapsedStripLabel`. The strip starts `GONE`; the manager makes it `VISIBLE` only when collapsed AND the panel is available (vanish rule).

---

## Rollback Plan

Revert phase commit(s) - additive resources only; unused color/string/view ids have no runtime effect until Phase 03 wires them.
