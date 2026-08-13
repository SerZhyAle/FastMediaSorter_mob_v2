# Phase 01 - Unified component

**Strategic spec:** [`../S0384_unify-scrollable-text-dialogs.md`](../S0384_unify-scrollable-text-dialogs.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03, 04
**Steps done:** 0 / 3

---

## Objective

Introduce `ScrollableTextDialog` (object) with a reusable bind helper as the single rendering layer for all scrollable-text dialogs, built on `dialog_error_detail` and `MaterialAlertDialogBuilder`. Add an optional extra-action icon slot to the layout for reuse cases (e.g. "clear").

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ScrollableTextDialog.kt` | New | ≤ 320 |
| `app_v2/src/main/res/layout/dialog_error_detail.xml` | Modified | ≤ 145 |
| `app_v2/src/main/res/layout-land/dialog_error_detail.xml` | Modified | ≤ 145 |

> Landscape counterpart exists - both edited in this phase.

---

## Steps

### Step 01.1 - Add extra-action slot to the layout (both orientations)

**Files:** `res/layout/dialog_error_detail.xml`, `res/layout-land/dialog_error_detail.xml`

**Prompt for developer:**

> In the `layoutDialogActions` row of BOTH layouts, insert one more `MaterialButton` `btnExtra` (style `Widget.Material3.Button.IconButton`, no icon in XML, `android:visibility="gone"`) positioned just before `btnCopy`. It hosts an optional caller-supplied action (e.g. "clear log"). Also add `android:fontFamily` is NOT set in XML - monospace is applied in code.

**Verification:**

- `Grep` - `@+id/btnExtra` present in both files.
- `Grep` - `btnPrimaryCta`, `btnPrimary`, `btnInlineAction`, `btnExtra`, `btnCopy`, `btnClose` all present in both files.

**Status:** `[ ]` not done

---

### Step 01.2 - Create `ScrollableTextDialog` with bind helper + public show

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ScrollableTextDialog.kt`

**Prompt for developer:**

> Create `object ScrollableTextDialog`. Port the proven logic from `ErrorDialog` (S0378): inflate `dialog_error_detail`, bind message + collapsible details, the icon action row (primary/CTA, inline save-or-copy-full, copy, close) with `contentDescription` + `TooltipCompat`, plus the private `copyToClipboard` and `saveErrorToFile`. Add:
> - A `data class ExtraAction(@DrawableRes icon: Int, contentDescription: String, dismissOnClick: Boolean, onClick: () -> Unit)`.
> - An internal `bind(view, dialogProvider, config)` that does all view wiring and returns nothing; the dismiss callback is supplied by the host so both the object dialog and `ScheduledLogDialog` can reuse it. Close and primary CTA dismiss; copy/share/save/extra do not (§6.1).
> - Public `show(context, title, message, details=null, monospace=false, actionButtonText=null, onActionClick=null, inlineActionButtonText=null, onInlineActionClick=null, showShare=true, showCopy=true, showSave=true, extraAction=null, cancelable=true): AlertDialog?` using `MaterialAlertDialogBuilder`; resize to ~90% width and call `DialogAccessibilityHelper.applyInitialFocus` (parity with the old DialogUtils path).
> - `monospace=true` sets the message `TextView` typeface to monospace.
> - Keep the `Throwable` convenience overload.
> - `Log.d(` must not appear; use Timber only.

**Verification:**

- `Glob` - `ScrollableTextDialog.kt` exists.
- `Grep` - `object ScrollableTextDialog`, `fun show(`, `data class ExtraAction`, `MaterialAlertDialogBuilder` present.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[ ]` not done

---

### Step 01.3 - Build

**Prompt for developer:** Build `standard debug`. The new component is not yet referenced by callers; this validates layout + component compile.

**Verification:**

- `.\a.ps1 dq` - BUILD SUCCESSFUL.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] All steps `[x]`.
- [ ] `standard debug` build passes.

---

## Handoff Notes to Next Phase

`ScrollableTextDialog.show(..)` is available with a param surface that is a superset of both `ErrorDialog.show` and `DialogUtils.showScrollableDialog`. The layout exposes `btnExtra` for the scheduled-log "clear" action.
