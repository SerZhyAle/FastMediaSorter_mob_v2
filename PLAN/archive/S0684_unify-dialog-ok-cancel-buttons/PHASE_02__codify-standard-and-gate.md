# Phase 02 - Codify Standard and Gate

**Strategic spec:** [`../S0684_unify-dialog-ok-cancel-buttons.md`](../S0684_unify-dialog-ok-cancel-buttons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 5 / 5
**Started:** 2026-06-25
**Completed:** 2026-06-25

---

## Objective

Fix the new cancel standard in the developer rules (ARCHITECTURE Button Taxonomy, CLAUDE.md, AGENTS.md) and add a mechanical gate that fails any future dialog introducing a one-off cancel button, so new dialogs inherit the standard without manual styling.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (the `DialogCancel` style + `cancel_button_*` colours exist - the doc and gate reference them).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ARCHITECTURE.md` | Modified | ~ 3 lines changed |
| `scripts/quality/assert-dialog-cancel-style.ps1` | New | ≤ 120 |
| `scripts/quality/dialog-cancel-style-baseline.txt` | New | 1 line (`0`) |
| `scripts/post-change.ps1` | Modified | ~ +12 lines |
| `CLAUDE.md` | Modified | ~ +1 line |
| `AGENTS.md` | Modified | ~ +1 line |

---

## Steps

### Step 02.1 - Update the Button Taxonomy doc

**Files:** `docs/ARCHITECTURE.md`
**Depends on:** - start of phase

**Prompt for developer:**

> In the "Button Taxonomy (MANDATORY)" section:
> - Replace the S0538 Cancel slot row (line ~176, currently "Neutral outlined") with the soft-pink tonal wording from research 06: Cancel = soft-pink tonal fill (`cancel_button_bg`/`cancel_button_on`), deliberately smaller than the green confirm (shorter height `dialog_cancel_button_min_height`, narrower than the wide confirm); saturated red reserved for `DialogDestructive` only.
> - Amend the intro paragraph (line ~171) so it states the new asymmetry (confirm/destructive ~56dp and wide; cancel intentionally shorter and narrower) and the colour key (green = confirm, soft-pink tonal = cancel, saturated red = destructive only).
> - Amend the general "Low-emphasis / cancel" row (line ~168) "When to use" cell so it scopes `Button.Text` to link-like/inline dismiss OUTSIDE a dialog pair, and points dialog/bottom-sheet pairs at the `DialogCancel` slot.
> Keep the doc's existing table style and tone.

**Verification:**

- `Grep` - `ARCHITECTURE.md` Cancel slot row references soft-pink / tonal and no longer says "Neutral outlined".
- `Grep` - intro paragraph mentions cancel being shorter/narrower than confirm.

**Status:** `[x] done`

**Step Log:**

- 2026-06-25 - Verification 2/2 PASS. Cancel slot row -> soft-pink tonal (no "Neutral outlined" left); intro paragraph states the confirm-wide/cancel-shorter asymmetry + colour key; general low-emphasis row scoped to non-dialog dismiss.

---

### Step 02.2 - Author the mechanical gate script

**Files:** `scripts/quality/assert-dialog-cancel-style.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `assert-dialog-cancel-style.ps1` following the Pattern A ratchet template `scripts/quality/assert-deprecated-pm-flags.ps1`: `#requires -Version 7.0`, synopsis block, `[CmdletBinding(DefaultParameterSetName='Report')]` with `-Gate` / `-UpdateBaseline` / `-List`, `Set-StrictMode -Version Latest`, `$ErrorActionPreference='Stop'`, repo-root resolution, a `dialog-cancel-style-baseline.txt` single-int baseline under `$PSScriptRoot`.
> Scan roots `app_v2/src/main/res/layout` + `app_v2/src/main/res/layout-land`, filename filter `^(dialog_|bottom_sheet_).*\.xml$`. Read each file `-Raw`; for every `com.google.android.material.button.MaterialButton` element (attribute order varies - slice the whole element to its tag close), count it as a violation when ALL hold: (a) cancel signal - `android:id` matches `(?i)cancel|_skip$|btnskip|btndismiss|btnno\b|btnnotnow` OR `android:text` references `@android:string/cancel` or a cancel/skip/dismiss string; (b) NOT a confirm/destructive - id does not match `(?i)(ok|apply|confirm|save|grant|yes|continue|done|delete|remove|clear)` and style is not `DialogConfirm`/`DialogDestructive`; (c) `style` is absent or not `@style/Widget.FastMediaSorter.Button.DialogCancel`. Compute the element start line for `-List`. Gate/exit semantics identical to the template (`-Gate` exits 1 when `current > baseline`; `-UpdateBaseline` ratchets down only). FAIL message points at `docs/ARCHITECTURE.md` "Button Taxonomy". Timber/Log not applicable (PowerShell).

**Verification:**

- `Glob` - `scripts/quality/assert-dialog-cancel-style.ps1` exists.
- Run `pwsh -NoProfile -File scripts/quality/assert-dialog-cancel-style.ps1 -List` - exits 0, prints zero violations (all current dialogs comply).

**Status:** `[x] done`

**Step Log:**

- 2026-06-25 - Verification 2/2 PASS. Authored `assert-dialog-cancel-style.ps1` (Pattern A ratchet). Initial `-List` surfaced 9 hits - all S0538/S0567 documented-exempt non-pairs (icon-only `dialog_filter`/`dialog_filter_resource`, selection picker `dialog_list_selection`, scan control `dialog_network_discovery`) plus a false positive on `dialog_image_edit btnNegative` (colour-negative image filter, not a cancel). Refined the gate (Rule 13): dropped `negative` from the cancel signal and added an `$exemptFiles` allowlist for the 4 documented-exempt surfaces. Re-run `-List` = 0 violations.

---

### Step 02.3 - Seed the baseline at 0

**Files:** `scripts/quality/dialog-cancel-style-baseline.txt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/quality/assert-dialog-cancel-style.ps1 -UpdateBaseline`. Confirm it writes `0` (current dialogs are all compliant). Then `pwsh -NoProfile -File scripts/quality/assert-dialog-cancel-style.ps1 -Gate` must exit 0.

**Verification:**

- `Read` - `scripts/quality/dialog-cancel-style-baseline.txt` contains `0`.
- `-Gate` run exits 0 (record `expected: 0 | actual: <n>`).

**Status:** `[x] done`

**Step Log:**

- 2026-06-25 - Verification 2/2 PASS. `-UpdateBaseline` seeded `dialog-cancel-style-baseline.txt` = 0; `-Gate` exits 0 (expected: 0 | actual: 0).

---

### Step 02.4 - Wire the gate into post-change.ps1

**Files:** `scripts/post-change.ps1`
**Depends on:** Step 02.2, Step 02.3

**Prompt for developer:**

> Mirror the focus-highlight gate wiring (research 06): add a flag near line ~141 `$runsDialogCancelGate = $resolvedChangeType -in @('Xml','Mixed')`; add an `if ($runsDialogCancelGate) { Invoke-Step "dialog-cancel-style-gate" { & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-dialog-cancel-style.ps1") -Gate } } else { Skip-Step "dialog-cancel-style-gate" "not applicable for ChangeType $resolvedChangeType" }` block next to the other layout gates (~line 262). Match the surrounding `Invoke-Step`/`Skip-Step` style exactly.

**Verification:**

- `Grep` - `post-change.ps1` references `assert-dialog-cancel-style.ps1` inside an `Invoke-Step "dialog-cancel-style-gate"`, and `$runsDialogCancelGate` is defined.
- `pwsh -NoProfile -Command "& { . ./scripts/post-change.ps1 -WhatIf ... }"` is NOT required - avoid a full `post-change.ps1` run here (it dev-logs + syncs as a side effect). The standalone gate already proved green in Step 02.3; this step only proves the wiring is present and syntactically valid via `pwsh -NoProfile -Command "[scriptblock]::Create((Get-Content -Raw scripts/post-change.ps1)) | Out-Null"` (parses without error).

**Status:** `[x] done`

**Step Log:**

- 2026-06-25 - Verification 2/2 PASS. Added `$runsDialogCancelGate` (narrow trigger: dialog/bottom-sheet layout edits) + an `Invoke-Step "dialog-cancel-style-gate"` block beside focus-highlight. `post-change.ps1` parses OK.

---

### Step 02.5 - Add the developer rule to CLAUDE.md + AGENTS.md

**Files:** `CLAUDE.md`, `AGENTS.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add one rule (same wording in both files, EN) under the UI/feature-policy area: "Dialog action pair: any confirm/cancel pair in a dialog, bottom sheet, or custom layout uses the S0538/S0684 styles - confirm = `DialogConfirm` (green, wide), cancel = `DialogCancel` (soft-pink tonal, shorter/narrower), destructive confirm = `DialogDestructive` (red). Never a one-off cancel button. Gate: `scripts/quality/assert-dialog-cancel-style.ps1` (in `post-change.ps1`)." Keep `CLAUDE.md` and `AGENTS.md` in sync (AGENTS.md mirrors shared rules).

**Verification:**

- `Grep` - both `CLAUDE.md` and `AGENTS.md` contain `assert-dialog-cancel-style.ps1` and reference the DialogCancel pink standard.

**Status:** `[x] done`

**Step Log:**

- 2026-06-25 - Verification 1/1 PASS. Added the dialog-action-pair rule to `CLAUDE.md` §11 and `AGENTS.md` §3, both citing the gate and the pink DialogCancel standard.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `assert-dialog-cancel-style.ps1 -Gate` exits 0; baseline file holds `0`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for the doc + gate + rule batch.

---

## Handoff Notes to Next Phase

The standard is now enforced three ways: documented (ARCHITECTURE Button Taxonomy + themes.xml comment from Phase 01), ruled (CLAUDE.md/AGENTS.md), and gated (`assert-dialog-cancel-style.ps1` in `post-change.ps1`, baseline 0). Phase 03 finalizes the dev log and confirms no catalog/FEATURES change is owed.

---

## Rollback Plan

Revert the phase commit(s) - the gate script, baseline, and wiring drop out; the doc/rule revert to the outlined-cancel wording. No runtime impact (the gate is build-time tooling only).
