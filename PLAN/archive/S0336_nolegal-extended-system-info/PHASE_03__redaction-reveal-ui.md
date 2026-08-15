# Phase 03 - Redaction reveal UI

**Strategic spec:** [`../S0336_nolegal-extended-system-info.md`](../S0336_nolegal-extended-system-info.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-03
**Completed:** 2026-06-03

**Step Log:**

- 2026-06-03 - Steps 03.1-03.3 Verification PASS (reveal strings parity EN/RU/UK OK; Grep confirms data-driven `hasSensitive` branch, S0337 probe untouched, no BuildConfig/Log.d; `assembleStandardDebug` + `assembleNoLegalDebug` both compile).

---

## Objective

Surface the masked report by default in the existing System info dialog and offer a confirmed "Copy full report" action only when the report carries sensitive content (`hasSensitive`). Purely data-driven - no `BuildConfig` gate - so the action never appears on flavors whose contributor set is empty.

---

## Prerequisites

- [ ] Phase 01 + Phase 02 are ✅ Done (`SystemInfoReport` with `hasSensitive`/`fullText`, real noLegal contributor producing sensitive fields).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsLogHelper.kt` | Modified | ≤ 250 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |

> The reveal strings are a generic UI affordance ("Copy full report" + confirmation), not noLegal diagnostic content, and are referenced unconditionally by `src/main` code at compile time - so they belong in `src/main/res`. The noLegal-specific section/field labels stay in `src/noLegal/res` (Phase 02). `DialogUtils.showScrollableDialog` already exposes `negativeButtonText`/`onNegative` and copy/share - no change to `DialogUtils` is required.

---

## Steps

### Step 03.1 - Add reveal-action strings (trilingual)

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add three keys to all three `src/main` `strings.xml` files in lockstep via `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add` (one call per key, parity-enforced `-En -Ru -Uk`):
> - `system_info_copy_full_report` - the action label (e.g. EN "Copy full report").
> - `system_info_reveal_confirm_title` - confirmation dialog title.
> - `system_info_reveal_confirm_message` - confirmation body warning that the copy will include sensitive values (signature hash, local addresses, mount paths).
> Apply `docs/COMMUNICATION_POLICY.md` §2 (confirmation message formula) and §6 (tone checklist). Use `..` not `...`, correct `ё` in Russian.

**Verification:**

- `Grep` - all three keys present in each of the three `src/main` `strings.xml` files (`expected: 3 | actual: <n>` per key).
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "system_info_reveal"` exits 0; same for `system_info_copy_full_report`.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 03.2 - Wire the masked report and the data-driven reveal action

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsLogHelper.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Update `showSystemInfoDialog()`: the use case now returns `SystemInfoReport`. Show `report.maskedText` in `DialogUtils.showScrollableDialog`. When `report.hasSensitive` is `true`, pass `negativeButtonText = R.string.system_info_copy_full_report` and an `onNegative` that calls a new private `confirmAndCopyFullReport(report.fullText)`. When `hasSensitive` is `false`, open the dialog exactly as today (no extra button) - this is the path every non-noLegal flavor takes (empty contributor set). Add `confirmAndCopyFullReport(text)`: a `MaterialAlertDialogBuilder` confirmation (`system_info_reveal_confirm_title` / `_message`) whose positive action copies `text` to the clipboard via `ClipboardManager` and toasts `R.string.copied_to_clipboard`; negative cancels.
>
> Do NOT modify or remove the existing `Timber.d("S0337: system info dialog opened")` line - it is S0337's `BlockNeedUserTest` probe and is owned by that ticket. Introduce no `BuildConfig.IS_*` / `SUPPORT_*` guard - the branch is driven solely by `report.hasSensitive`.

**Verification:**

- `Grep` - `report.maskedText` and `report.hasSensitive` and `report.fullText` all present.
- `Grep` - `confirmAndCopyFullReport` declared and called.
- `Grep` - `R.string.system_info_copy_full_report` referenced.
- `Grep` - `"S0337: system info dialog opened"` still matches exactly once (probe untouched).
- `Grep` - no `BuildConfig.IS_` / `BuildConfig.SUPPORT_` added to this file.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

---

### Step 03.3 - Confirm both-flavor build and isolation

**Files:** (build only)
**Depends on:** Step 03.2

**Prompt for developer:**

> Build both ends of the matrix: `standardDebug` (reveal action must be unreachable - empty set ⇒ `hasSensitive == false`) and `noLegalDebug` (reveal action reachable). No code change in this step.

**Verification:**

- Build invariant: `/build` `assembleStandardDebug` passes.
- Build invariant: `/build` `assembleNoLegalDebug` passes.
- `expected: standard has no R.string.nolegal_diag_* in its merged resources | actual: <confirm>` - the noLegal section/field strings are absent from the standard variant (they live only in `src/noLegal/res`).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `/build` `standardDebug` and `noLegalDebug` both compile.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] The reveal action is reachable by keyboard / D-pad / mouse - it is a standard `MaterialAlertDialogBuilder` button, inheriting the dialog framework's focus traversal and `DialogAccessibilityHelper` initial focus (Strict Rule 17 satisfied without custom focus wiring).
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

- Feature is functionally complete after this phase. Phase 04 is documentation, functionality log, catalog metadata, and the `/spec-dev` → `BlockNeedUserTest` transition with one `Timber.d("S0336: ...")` probe per changed flow entry (added by `/spec-dev`, removed on leaving `BlockNeedUserTest`).

---

## Rollback Plan

Revert the phase commit(s). The dialog returns to showing the (still correctly aggregated) masked text with no reveal action; no data surface changes.
