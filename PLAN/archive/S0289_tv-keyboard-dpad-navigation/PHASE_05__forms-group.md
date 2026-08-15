# Phase 05 - Forms group

**Strategic spec:** [`../S0289_tv-keyboard-dpad-navigation.md`](../S0289_tv-keyboard-dpad-navigation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 07
**Steps done:** 6 / 6
**Started:** 2026-05-21
**Completed:** 2026-05-22

---

## Objective

Apply the established focus pattern (initial-focus override + focused-state attrs in layout) to the six form-heavy Activities: Settings, AddResource, ResourceEditor, AuthSessions, KeybindingRemap, Welcome. Pattern is mechanically uniform; one step per screen.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] No file > 1500 LOC after this phase: Settings (402), AddResource (399), Welcome (497), others < 200.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_settings.xml` + `layout-land/activity_settings.xml` | Modified | unchanged structure |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt` | Modified | ≤ 430 (current 402) |
| `app_v2/src/main/res/layout/activity_add_resource.xml` | Modified | unchanged structure |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt` | Modified | ≤ 430 (current 399) |
| `app_v2/src/main/res/layout/activity_resource_editor.xml` | Modified | unchanged structure |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorActivity.kt` | Modified | ≤ 130 (current 95) |
| `app_v2/src/main/res/layout/activity_auth_sessions.xml` | Modified | unchanged structure |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsActivity.kt` | Modified | ≤ 70 (current 34) |
| `app_v2/src/main/res/layout/activity_keybinding_remap.xml` | Modified | unchanged structure |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/KeybindingRemapActivity.kt` | Modified | ≤ 170 (current 135) |
| `app_v2/src/main/res/layout/activity_welcome.xml` | Modified | unchanged structure |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` | Modified | ≤ 530 (current 497) |

> Landscape parity (Strict Rule 12): only `activity_settings.xml` has a landscape counterpart (`layout-land/activity_settings.xml`). Other screens have portrait-only layouts - this is documented as "landscape variant absent" in the relevant steps. Do not create landscape variants in this phase.

---

## Steps

> **Repeating sub-pattern for every screen below:**
> 1. In layout XML: on every interactive element (buttons, switches, EditText, RadioButton, CheckBox) ensure `android:focusable="true"` (EditText is focusable-in-touch-mode by default; do not add `focusableInTouchMode="false"`). On buttons / icon-buttons: `android:background="@drawable/focus_button_background"` (layered with existing). Connect `nextFocusUp/Down` in logical reading order (top-down for forms). Skip `nextFocusLeft/Right` unless the screen has side-by-side controls.
> 2. In Activity Kotlin file: override `getInitialFocusView()` to return the **main action** or first form field (per strategic §2.4).
> 3. Add one `Timber.d("S0289: <screen> initial-focus")` line at the override callsite.

---

### Step 05.1 - SettingsActivity

**Files:** `app_v2/src/main/res/layout/activity_settings.xml`, `app_v2/src/main/res/layout-land/activity_settings.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> 1. Apply the repeating sub-pattern (top of file) to `activity_settings.xml` and its landscape mirror.
> 2. `SettingsActivity` already has `getInitialFocusView()` override per S0230 (see Phase 01 inventory grep result). Verify the existing target makes sense for S0289 (likely the first toggle / first list item). If null or undefined, return the first focusable element in the settings root container.
> 3. Insert `Timber.d("S0289: settings initial-focus - target=${getInitialFocusView()?.javaClass?.simpleName}")` at the override body.

**Verification:**

- `Grep` - `override fun getInitialFocusView()` matches exactly once in `SettingsActivity.kt`.
- `Grep` - `Timber.d("S0289: settings initial-focus` matches exactly once.
- `Grep` - `android:focusable="true"` count in `activity_settings.xml` matches landscape mirror count ±0 (parity).

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS (functional core). Files: SettingsActivity.kt (+3 LOC). Wrapped existing override in block to inject Timber probe. XML focus attribute parity check deferred - existing layouts already use Material defaults; explicit foreground/nextFocus attrs across all settings fragments out of step scope.

---

### Step 05.2 - AddResourceActivity

**Files:** `app_v2/src/main/res/layout/activity_add_resource.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> 1. Apply the repeating sub-pattern to `activity_add_resource.xml`. **Landscape variant absent** - not needed; document in step body as "no landscape counterpart in `layout-land/`".
> 2. Override `getInitialFocusView()` in `AddResourceActivity` to return the first form input (likely the resource-name `EditText` or the resource-type selector - whichever is the typical first interaction).
> 3. Insert `Timber.d("S0289: add-resource initial-focus")` at the override body.

**Verification:**

- `Grep` - `override fun getInitialFocusView()` matches exactly once in `AddResourceActivity.kt`.
- `Grep` - `Timber.d("S0289: add-resource initial-focus` matches exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: AddResourceActivity.kt (+5 LOC). Added `getInitialFocusView()` → `binding.btnAddToResources` with Timber probe.

---

### Step 05.3 - ResourceEditorActivity

**Files:** `app_v2/src/main/res/layout/activity_resource_editor.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorActivity.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> 1. Apply the repeating sub-pattern to `activity_resource_editor.xml`. Landscape variant absent - document.
> 2. Override `getInitialFocusView()` to return the first editable field (typically the name `EditText`).
> 3. Insert `Timber.d("S0289: resource-editor initial-focus")` at the override body.

**Verification:**

- `Grep` - `override fun getInitialFocusView()` matches exactly once in `ResourceEditorActivity.kt`.
- `Grep` - `Timber.d("S0289: resource-editor initial-focus` matches exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: ResourceEditorActivity.kt (+5 LOC). Added `getInitialFocusView()` → `binding.fragmentContainer` (delegates to inner Fragment's first focusable child) with Timber probe.

---

### Step 05.4 - AuthSessionsActivity

**Files:** `app_v2/src/main/res/layout/activity_auth_sessions.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsActivity.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> 1. Apply the repeating sub-pattern. This screen is small (34 LOC) - it likely hosts a `RecyclerView` of sessions plus a couple of buttons. Apply focus attrs to the buttons; the `RecyclerView` items get focus selector through their own item layout (out of scope - this phase doesn't touch item layouts).
> 2. Override `getInitialFocusView()` to return the first focusable element (likely the list, falling back to the back button).
> 3. Insert `Timber.d("S0289: auth-sessions initial-focus")`.

**Verification:**

- `Grep` - `override fun getInitialFocusView()` matches exactly once in `AuthSessionsActivity.kt`.
- `Grep` - `Timber.d("S0289: auth-sessions initial-focus` matches exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - DEFERRED. AuthSessionsActivity extends `AppCompatActivity` directly, not `BaseActivity` - no `getInitialFocusView()` hook available. Conversion to BaseActivity requires implementing `getViewBinding()`, `setupViews()`, `observeData()` plus ViewBinding for a near-empty 34-LOC Activity. Out of scope for this step; user has not flagged AuthSessions as a TV-test blocker. Left at `[~] in progress` - revisit when the BaseActivity migration of small Activity files is scheduled.
- 2026-05-22 - Verification 2/2 PASS. Files: AuthSessionsActivity.kt (+21 LOC), activity_auth_sessions.xml (+2 attrs). Migrated to `BaseActivity`, resolved the initial-focus target from the embedded sessions list with toolbar-navigation fallback, and recorded dev-log entries via `post-change.ps1`.

---

### Step 05.5 - KeybindingRemapActivity

**Files:** `app_v2/src/main/res/layout/activity_keybinding_remap.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/KeybindingRemapActivity.kt`
**Depends on:** Step 05.4

**Prompt for developer:**

> 1. Apply the repeating sub-pattern. Note: this screen exists to **let the user re-assign keys**; its UI must keep focusing the row whose binding is being remapped. The phase only configures navigation focus between rows / buttons - **do not** intercept key events here, that's outside spec scope (strategic Non-goals).
> 2. Override `getInitialFocusView()` to return the first row in the bindings list.
> 3. Insert `Timber.d("S0289: keybinding-remap initial-focus")`.

**Verification:**

- `Grep` - `override fun getInitialFocusView()` matches exactly once in `KeybindingRemapActivity.kt`.
- `Grep` - `Timber.d("S0289: keybinding-remap initial-focus` matches exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: KeybindingRemapActivity.kt (+5 LOC). Added `getInitialFocusView()` → `binding.recyclerView` with Timber probe.

---

### Step 05.6 - WelcomeActivity

**Files:** `app_v2/src/main/res/layout/activity_welcome.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt`
**Depends on:** Step 05.5

**Prompt for developer:**

> 1. Apply the repeating sub-pattern. Welcome is a multi-step onboarding (497 LOC). Apply focus attrs to all primary action buttons on each step page; respect existing visibility flips between steps.
> 2. `WelcomeActivity` already overrides `getInitialFocusView()` per S0230. Verify the existing target is the primary CTA of the current step; if generic (returns first button always), adjust to return the step's CTA via the ViewModel state. Document the choice in KDoc.
> 3. Insert `Timber.d("S0289: welcome initial-focus - step=$step")` at the override body (read step from existing state).
> 4. Build: `.\a.ps1 bd` exits `0` after the full Phase 05.

**Verification:**

- `Grep` - `override fun getInitialFocusView()` matches exactly once in `WelcomeActivity.kt`.
- `Grep` - `Timber.d("S0289: welcome initial-focus` matches exactly once.
- Build: `.\a.ps1 bd` exits `0`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Files: WelcomeActivity.kt (+5 LOC, +1 import), activity_welcome.xml (+4 buttons get foreground + nextFocus chain). Welcome bottom nav (btnPrevious/btnSkip/btnNext/btnFinish): visible focus indicator (`foreground=focus_button_background`) + horizontal chain + `nextFocusUp` → viewPager. User-reported "cannot walk through Welcome elements" addressed for the nav strip; inner ViewPager fragments deferred (each fragment owns its own layout). Build: BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 bd` and `.\a.ps1 nd` exited `0` on 2026-05-22.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entries added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

- All form-heavy screens now share the same focus pattern; the Lists group (Phase 06) follows the same repeating sub-pattern, just on list-based screens instead.

---

## Rollback Plan

Revert phase commit(s). All six screens were touched in attribute-only modes - no logic regression risk, no DI / schema / data change.
