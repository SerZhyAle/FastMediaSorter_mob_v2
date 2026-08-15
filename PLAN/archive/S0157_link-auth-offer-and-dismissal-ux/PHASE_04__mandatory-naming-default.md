# Phase 04 — mandatory-naming-default

**Strategic spec:** [`../S0157_link-auth-offer-and-dismissal-ux.md`](../S0157_link-auth-offer-and-dismissal-ux.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** —
**Completed:** 2026-05-11

---

## Objective

Make the account-naming dialog that appears after WebView login always pre-fill with a sensible default (hint from cookies, or the locale-appropriate "Default account" string). Cancel cancels the entire save; the user cannot accidentally get a blank-named account.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done (strings_s0157.xml with `s0157_account_default_name` is present).
- [ ] Working tree is clean or on a feature branch.
- [ ] `WebViewAuthDialogFragment.kt` read before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthDialogFragment.kt` | Modified | ≤ 280 |

> Landscape variant absent — `dialog_webview_auth.xml` has no `layout-land/` counterpart; the dialog uses MATCH_PARENT sizing from `onStart()` and renders correctly in both orientations.

---

## Steps

### Step 04.1 — Pre-fill account name field with default when hint is null

**Files:** `ui/share/auth/WebViewAuthDialogFragment.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `harvestAndDismiss()`, find the `TextInputEditText` initialisation block:
>
> ```kotlin
> val nameInput = TextInputEditText(requireContext()).apply {
>     setText(hint ?: "")
>     setHint(R.string.s0155_name_account_hint)
> }
> ```
>
> Change `hint ?: ""` to `hint ?: getString(R.string.s0157_account_default_name)`. This ensures the field is never blank when the dialog opens — the user can clear or replace it, but must make a conscious choice. Check `docs/COMMUNICATION_POLICY.md` §6: the pre-filled default is a low-friction affordance, not a forced value.

**Verification:**

- `Grep` — `getString(R.string.s0157_account_default_name)` present in `WebViewAuthDialogFragment.kt`.
- `Grep` — `setText(hint ?: "")` does NOT appear (old blank-fallback removed).
- `Grep` — `Log\.d\(` returns zero hits in `WebViewAuthDialogFragment.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 3/3 PASS. `getString(R.string.s0157_account_default_name)` present; blank-fallback removed; `Log.d(` 0 hits. Files: WebViewAuthDialogFragment.kt (+1 line net).

---

### Step 04.2 — Verify Cancel still suppresses cookie save

**Files:** `ui/share/auth/WebViewAuthDialogFragment.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Confirm (read-only verification) that the negative button in the naming dialog still calls `emitResultAndDismiss(saved = false)` — meaning cookies are NOT saved when the user taps Cancel. The S0157 spec requires this: "Нажатие 'Отмена' — куки НЕ сохраняются (вход не зафиксирован)." No code change needed if already correct; add a KDoc comment `// S0157: cancel does NOT save cookies — user may have decided not to commit the login.` above the `.setNegativeButton` block.

**Verification:**

- `Grep` — `// S0157: cancel does NOT save cookies` present in `WebViewAuthDialogFragment.kt`.
- `Grep` — negative button lambda calls `emitResultAndDismiss(saved = false)`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 2/2 PASS. `// S0157: cancel does NOT save cookies` comment present; `emitResultAndDismiss(saved = false)` in negative button lambda. No code change required. Files: WebViewAuthDialogFragment.kt (+1 line net).

---

### Step 04.3 — Dev log entry

**Files:** `WebViewAuthDialogFragment.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Run `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthDialogFragment.kt" "S0157-phase04" "Mandatory account name pre-fill: use default when hint absent"`.

**Verification:**

- `Grep` — `WebViewAuthDialogFragment.kt` appears in `dev/CHANGELOG.md` with a recent timestamp.

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 1/1 PASS. `WebViewAuthDialogFragment.kt` entry at 2026-05-11 14:27:56 in dev/CHANGELOG.md.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added (Step 04.3 covers this).
- [x] Catalog sync: `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `WebViewAuthDialogFragment` now pre-fills the account name with `s0157_account_default_name` if no cookie hint is available.
- Cancel still discards cookies — behaviour unchanged; just explicitly documented.

---

## Rollback Plan

Revert phase commit. Single file change; no data migration.
