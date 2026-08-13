# Phase 01 - Shared key+label choice dialog

**Strategic spec:** [`../S0646_settings-simple-list-dialog-unification.md`](../S0646_settings-simple-list-dialog-unification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-23
**Completed:** 2026-06-24

---

## Objective

Add a thin reusable `SimpleValueChoiceDialog` over the existing `ListSelectionDialog<T>` for the common "pick one string-keyed option from a short label list" case. No new component, no DI, no UI surface yet - just the wrapper the string-keyed migrations (D, F, G-K) consume.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `ui/dialog/ListSelectionDialog.kt`, `ListSelectionAdapter.kt` present (verified - S0567).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/SimpleValueChoiceDialog.kt` | New | ≤ 80 |

> No flavor-only code: the class lives in `src/main` and is selected at runtime by callers; the noLegal-gated OCR sites (Phase 03) keep their existing runtime `capabilityAvailability` gate, not a source-set split.

---

## Steps

### Step 01.1 - Create SimpleValueChoiceDialog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/SimpleValueChoiceDialog.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `class SimpleValueChoiceDialog(context, lifecycleOwner, title: CharSequence, options: List<Option>, currentKey: String?, allowClear: Boolean = false, onSelected: (String?) -> Unit) : ListSelectionDialog<SimpleValueChoiceDialog.Option>(...)`.
> Declare nested `data class Option(val key: String, val label: String)`.
> Build the `ListSelectionConfig<Option>` inline (mirror `DestinationPickerDialog`): `loader = { options }`, `formatter` returns `option.label` from `getDisplayName`, `hasSelection = currentKey != null`, `isSelected = { it.key == currentKey }`, `allowClear = allowClear`, and `onSelected = { onSelected(it?.key) }`.
> `emptyMessageRes` / `errorMessageRes` are defensive only (the option list is built in code and never empty). Pass an existing generic message resource - Grep `app_v2/src/main/res/values/strings.xml` and reuse one already present (do NOT add a new string). Confirm the chosen res ids exist before use.
> No runtime colors, no hardcoded hex, no `Log.d` (Timber only).

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/SimpleValueChoiceDialog.kt` exists.
- `Grep` - `class SimpleValueChoiceDialog` matches exactly once (declaration line).
- `Grep` - `data class Option` present.
- `Grep` - `: ListSelectionDialog<` present (extends the canonical dialog, no reinvention).
- `Grep -n "Log\.d\("` - zero hits in the new file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-23 - Verification 5/5 PASS. Files: ui/dialog/SimpleValueChoiceDialog.kt (New, +48 LOC). Extends ListSelectionDialog<Option>; reuses error_unknown for defensive empty/error. Dev log recorded via post-change.

---

### Step 01.2 - Compile foundation

**Files:** (build only)
**Depends on:** Step 01.1

**Prompt for developer:**

> Run the standard debug build to confirm the new wrapper compiles against the existing `ListSelectionConfig`/`ItemFormatter` signatures.

**Verification:**

- `/build` -> `standard debug` (`a.ps1 dq`) exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Validated by the single consolidated clean build (`a.ps1 cd`) covering phases 01-04 + debug tags. BUILD SUCCESSFUL (APK v2.60.6211.547-DEBUG). Note: incremental `dq` threw phantom unresolved-ref cascades confined to edited files; clean build passed.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for the new file via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regen deferred to Phase 05 (single sync per ticket).

---

## Handoff Notes to Next Phase

`SimpleValueChoiceDialog(context, lifecycleOwner, title, options, currentKey, allowClear, onSelected)` is the canonical builder for string-keyed selectors. Phases 02 (D, F), 03 (G-J), 04 (K) call it. Enum-keyed (A) and `MediaResource`-keyed (B, C) and `HomeWidgetEntry` (E) sites use `ListSelectionDialog<T>` directly with a per-site formatter.

---

## Rollback Plan

Revert phase commit - new file only, no caller wired yet, no user-facing surface changed.
