# Phase 01 — Foundation: CollapsibleSectionHeader component

**Strategic spec:** [`../S0256_collapsible-section-header.md`](../S0256_collapsible-section-header.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, 03, 04, 05, 06
**Steps done:** 6 / 6
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Introduce the reusable `CollapsibleSectionHeader` composite view (Kotlin class + layout + styleable attrs) with full programmatic and XML API. No screens are migrated in this phase — only the component, plus a smoke test confirming the canonical indicator behavior, the help-icon TooltipDialog hookup, the virtual-group mode, and the no-sibling event contract.

---

## Prerequisites

- [x] Strategic §6 research items all `Resolved`.
- [x] Working tree is clean or on a feature branch.
- [x] `TooltipDialog` exists at `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/TooltipDialog.kt` (precondition — single dependency of the component).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/attrs.xml` | Modified | ≤ 80 |
| `app_v2/src/main/res/layout/view_collapsible_section_header.xml` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionHeader.kt` | New | ≤ 350 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionHeaderTest.kt` | New | ≤ 180 |
| `dev/CATALOG/app_v2.jsonl` | Modified | n/a (generated) |
| `dev/CATALOG/app_v2.md` | Modified | n/a (generated) |

---

## Steps

### Step 01.1 — Declare `<declare-styleable>` block for the component

**Files:** `app_v2/src/main/res/values/attrs.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Append a `<declare-styleable name="CollapsibleSectionHeader">` block defining the public XML API of the component. Attributes:
>
> - `csh_title` — `format="string|reference"` — group title (overridable at runtime via `setTitle`).
> - `csh_expanded` — `format="boolean"` — initial expanded state, default `false`.
> - `csh_showHelp` — `format="boolean"` — whether to render the help button, default `false`.
> - `csh_helpTitle` — `format="string|reference"` — title for the TooltipDialog opened by the help button.
> - `csh_helpMessage` — `format="string|reference"` — message for the TooltipDialog opened by the help button.
> - `csh_virtual` — `format="boolean"` — virtual-group mode, default `false`. When `true`: no indicator prefix, click-affordance off, transparent background.
>
> Add a top-of-file comment noting that this styleable is consumed by `CollapsibleSectionHeader.kt` and that all attribute names use the `csh_` prefix to avoid collisions with platform attrs.

**Verification:**

- `Grep` — `<declare-styleable name="CollapsibleSectionHeader">` present in `app_v2/src/main/res/values/attrs.xml`.
- `Grep` — exactly six `<attr name="csh_` lines in that file.
- `Grep` — `csh_title`, `csh_expanded`, `csh_showHelp`, `csh_helpTitle`, `csh_helpMessage`, `csh_virtual` each appear once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. Files: `app_v2/src/main/res/values/attrs.xml`. Dev log recorded.

---

### Step 01.2 — Author the merge layout for the component's internal structure

**Files:** `app_v2/src/main/res/layout/view_collapsible_section_header.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create a new layout file containing a single `<merge>` root with one inner `LinearLayout` (`orientation=horizontal`, `gravity=center_vertical`, `padding=@dimen/settings_padding_vertical`, `background=?attr/colorSurfaceVariant`, `clickable=true`, `focusable=true`). Inside the inner `LinearLayout`, in this exact order left-to-right:
>
> 1. `ImageButton android:id="@+id/csh_iconHelp"` — `layout_width=@dimen/settings_help_icon_size`, `layout_height=@dimen/settings_help_icon_size`, `layout_marginEnd=@dimen/settings_padding_vertical`, `background=?attr/selectableItemBackgroundBorderless`, `src=@drawable/ic_help_outline_24`, `tint=@color/text_color_secondary`, `visibility=gone` (toggled by code). `clickable=true`, `focusable=true`, `contentDescription` empty (set in code).
> 2. `TextView android:id="@+id/csh_title"` — `layout_width=0dp`, `layout_height=wrap_content`, `layout_weight=1`, `textSize=@dimen/toggler_title_text_size`, `textStyle=bold`. No `clickable` — click is handled by the outer LinearLayout.
> 3. `FrameLayout android:id="@+id/csh_trailingSlot"` — `layout_width=wrap_content`, `layout_height=wrap_content`, `visibility=gone`, hosts an optional trailing control injected via `setTrailingControl(View?)`.
>
> Use the `xmlns:android` namespace only; no `app:` attrs in this file — `app:`-attrs are consumed by the Kotlin class. Do not set `background` on the help button beyond `selectableItemBackgroundBorderless`.

**Verification:**

- `Glob` — `app_v2/src/main/res/layout/view_collapsible_section_header.xml` exists.
- `Grep` — `<merge ` present in that file.
- `Grep` — `@+id/csh_iconHelp` present.
- `Grep` — `@+id/csh_title` present.
- `Grep` — `@+id/csh_trailingSlot` present.
- `Grep` — `layout_weight="1"` present on the title TextView (anchored by `@+id/csh_title`).
- `Grep` — `?attr/colorSurfaceVariant` present (default header background).

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 7/7 PASS. Files: `app_v2/src/main/res/layout/view_collapsible_section_header.xml`. Dev log recorded.

---

### Step 01.3 — Implement `CollapsibleSectionHeader` Kotlin class

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionHeader.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Create the class. Outline (English KDoc on every public member; Timber for logging; no `Log.d`):
>
> - Package: `com.sza.fastmediasorter.ui.common.widget`.
> - Extends `LinearLayout` (chosen over `FrameLayout` to keep the existing `?attr/colorSurfaceVariant` background and minimum touch-target height).
> - Constructor variants: `(Context)`, `(Context, AttrSet?)`, `(Context, AttrSet?, Int)`. All three delegate to a common `init()`.
> - In `init()`: `LayoutInflater.from(context).inflate(R.layout.view_collapsible_section_header, this, true)` — uses `<merge>`. Read `R.styleable.CollapsibleSectionHeader` from `AttrSet` if non-null; apply title / expanded / showHelp / helpTitle / helpMessage / virtual.
> - Internal state: `private var isExpanded: Boolean`, `private var titleText: CharSequence`, `private var helpTitleRes: Int`, `private var helpMessageRes: Int`, `private var isVirtual: Boolean`, `private var expandedChangeListener: ((Boolean) -> Unit)?`.
> - Public API (every signature stable across PRs):
>   - `fun setTitle(text: CharSequence)` / `fun setTitle(@StringRes resId: Int)`.
>   - `fun setExpanded(expanded: Boolean, notify: Boolean = true)` — updates title prefix, fires listener iff `notify && state actually changed`. In virtual mode, this is a no-op.
>   - `fun isExpanded(): Boolean`.
>   - `fun setOnExpandedChangeListener(listener: ((Boolean) -> Unit)?)`.
>   - `fun setHelp(@StringRes titleRes: Int, @StringRes messageRes: Int)` — sets the help-icon TooltipDialog payload and makes the help icon visible.
>   - `fun setHelpVisible(visible: Boolean)` — toggles the help icon without dropping help payload.
>   - `fun setVirtual(virtual: Boolean)` — virtual-group mode: no indicator prefix, click-affordance off, transparent background, focusable=false.
>   - `fun setTrailingControl(view: View?)` — injects a trailing control or removes it; toggles `csh_trailingSlot.visibility`.
> - Internal behavior:
>   - When `expanded` changes — rewrite `csh_title.text` to `"<prefix> <titleText>"` where prefix is `▼` if expanded else `▶`. In virtual mode no prefix is applied — only `titleText` is rendered.
>   - On root click — flip `isExpanded` and fire the listener. In virtual mode the root is not clickable (consumed in `setVirtual(true)`).
>   - On help-icon click — open `TooltipDialog.show(context, helpTitleRes, helpMessageRes)`. Help-icon click does **not** propagate to root (use `setClickable(true)` on the icon — that is enough since clicks on a clickable child are consumed by the child).
>   - Trailing slot also gets its child's click consumed naturally (no extra wiring).
> - Accessibility: when `csh_iconHelp` becomes visible, set `contentDescription = context.getString(helpTitleRes)` — the title doubles as the icon's label. `csh_title` text already has implicit content. Set `nextFocusForward` between the help icon and the title where applicable; verify focus traversal works under the `app:android:focusableInTouchMode="false"` default.
> - Public attribute access: provide `@get:JvmName("getHelpVisible") val isHelpVisible: Boolean`.
> - Class size budget: ≤ 350 LOC including KDoc.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionHeader.kt` exists.
- `Grep` — `class CollapsibleSectionHeader` matches exactly once (declaration line).
- `Grep` — public functions present: `setTitle`, `setExpanded`, `isExpanded`, `setOnExpandedChangeListener`, `setHelp`, `setHelpVisible`, `setVirtual`, `setTrailingControl`.
- `Grep` — `TooltipDialog.show` referenced exactly once.
- `Grep -n "Log\.d\("` — zero hits in the new file (Timber-only rule).
- `Grep` — `BuildConfig\.(IS|SUPPORT|ENABLE)_` — zero hits in the new file (no flavor gates in main).
- File line count ≤ 350.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 7/7 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionHeader.kt`. Dev log recorded.

---

### Step 01.4 — Unit / smoke test for `CollapsibleSectionHeader`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionHeaderTest.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add a Robolectric-based test class (use existing test conventions; if Robolectric is not yet on the project, fall back to a pure-unit test with a `mock(Context::class.java)` and stub `LayoutInflater`). Cover these cases:
>
> 1. **Title prefix flips on expand** — set title, call `setExpanded(true, notify = false)` → assert title TextView text starts with `▼ `. Call `setExpanded(false, notify = false)` → starts with `▶ `.
> 2. **Listener fires only on actual state change** — install listener, call `setExpanded(true)` twice. Assert listener fired exactly once.
> 3. **Help is hidden by default** — fresh instance: `iconHelp.visibility == GONE`.
> 4. **Help becomes visible after `setHelp(...)`** — `iconHelp.visibility == VISIBLE` and clicking it triggers `TooltipDialog.show` (verify via spy or static mock).
> 5. **Virtual mode** — `setVirtual(true)` → title TextView text equals the raw title (no `▼/▶` prefix), root is not clickable, background is `null`.
> 6. **Trailing slot** — `setTrailingControl(viewMock)` → `csh_trailingSlot.visibility == VISIBLE` and contains the injected view; `setTrailingControl(null)` → `GONE` and slot is empty.
>
> Pass criteria: all six tests green.

**Verification:**

- `Glob` — `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionHeaderTest.kt` exists.
- `Grep` — `class CollapsibleSectionHeaderTest` matches exactly once.
- `Grep` — `@Test` count ≥ 6.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. Files: `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionHeaderTest.kt`. Targeted command `:app_v2:testNoLegalDebugUnitTest --tests "com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeaderTest"` -> PASS. Dev log recorded.

---

### Step 01.5 — Catalog regeneration

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 01.3

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to regenerate the catalog with the new class.
>
> After regeneration: `pwsh -NoProfile -File dev/CATALOG/scripts/set.ps1 -ClassMatches "CollapsibleSectionHeader" -Role "UI/widget" -Status "Active"` to fill the role + status fields for the new entry.

**Verification:**

- `Grep` — `"class":"CollapsibleSectionHeader"` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `CollapsibleSectionHeader` present in `dev/CATALOG/app_v2.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 2/2 PASS. Files: `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`. `catalog_sync.ps1` completed; record updated to `role = UI/widget`, `status = tested`.

---

### Step 01.6 — Dev log entries

**Files:** `dev/CHANGELOG.md`
**Depends on:** Steps 01.1..01.5

**Prompt for developer:**

> One `add_to_dev_log.ps1` call per file touched in this phase:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/attrs.xml" "attrs.xml" "S0256 Phase 01: declare CollapsibleSectionHeader styleable"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout/view_collapsible_section_header.xml" "view_collapsible_section_header.xml" "S0256 Phase 01: layout for unified collapsible header"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionHeader.kt" "CollapsibleSectionHeader" "S0256 Phase 01: introduce unified collapsible header component"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/test/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionHeaderTest.kt" "CollapsibleSectionHeaderTest" "S0256 Phase 01: unit/smoke test for CollapsibleSectionHeader"
> ```

**Verification:**

- `Grep` — `S0256 Phase 01` count ≥ 4 in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 1/1 PASS. Files: `dev/CHANGELOG.md`. Four `S0256 Phase 01` entries appended via `add_to_dev_log.ps1`.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` contains `CollapsibleSectionHeader` with `role = UI/widget`, `status = tested`.
- [x] All six unit tests in `CollapsibleSectionHeaderTest` pass.

## Current Hold

- 2026-05-19 - Phase completion is blocked by the global debug build gate. `scripts/builders/build-debug.PS1 -SkipZip` fails in pre-existing binding/layout code outside `S0256`, including unresolved generated bindings in `ui/settings/IntegrationTestDialog.kt` and `ui/addresource/AddResourceActivity.kt`, plus follow-on `FragmentSettingsGeneralBinding` errors in existing helpers.

---

## Handoff Notes to Next Phase

After this phase the component is fully usable but **no screen consumes it yet**. The screen-migration phases (02–06) are independent of each other — each migrates one screen-cluster to the component and removes its old ad-hoc header code. They may execute in any order; the only constraint is that all of them depend on Phase 01.

Public API contract frozen by Phase 01:

- One XML node `<…CollapsibleSectionHeader .. csh_title=".." csh_showHelp="true" csh_helpTitle=".." csh_helpMessage=".."/>` replaces an entire ad-hoc header row.
- Click handler is attached via `setOnExpandedChangeListener { expanded -> .. }`.
- Help-icon TooltipDialog is wired automatically when `csh_showHelp="true"` and `csh_helpTitle` / `csh_helpMessage` are set.
- Initial expanded state is set via `csh_expanded="true"` in XML or `setExpanded(true, notify = false)` from code (the `notify = false` overload is what migration code uses to seed state from persistence without firing the save listener).
- Virtual mode (`csh_virtual="true"`) is used by Phase 02 for the "About" header.

---

## Rollback Plan

Revert the four files added in this phase plus the regenerated `dev/CATALOG/*` entries. No data migration, no schema change, no user-facing surface — the only consumer of the new class until Phase 02 lands is its unit test.
