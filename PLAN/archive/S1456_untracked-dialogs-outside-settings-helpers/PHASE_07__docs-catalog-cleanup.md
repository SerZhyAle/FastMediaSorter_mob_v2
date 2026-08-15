# Phase 07 - Docs and catalog cleanup

**Strategic spec:** [`../S1456_untracked-dialogs-outside-settings-helpers.md`](../S1456_untracked-dialogs-outside-settings-helpers.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03, Phase 04, Phase 05, Phase 06
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Close the ratchet at zero, record the capability, refresh the class catalog and leave the debug probes the device test needs.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] `assert-untracked-dialogs.ps1 -List` prints nothing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/untracked-dialog-baseline.txt` | Modified | ≤ 1 |
| `docs/ALL_FEATURES.jsonl` | Modified (append) | n/a |
| `dev/CATALOG/app_v2.jsonl` | Modified (generated) | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/util/*.kt` or the swept sites | Modified | ≤ 1500 each |

---

## Steps

### Step 07.1 - Close the ratchet at zero

**Files:** `scripts/quality/untracked-dialog-baseline.txt`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/quality/assert-untracked-dialogs.ps1 -UpdateBaseline` and confirm the file now reads `0`. If it does not, the sweep is incomplete: return to the phase that owns the printed path instead of lowering the number by hand.

**Why:**

Strategic §9 records that a baseline left above zero would legalise the existing leak, which is the outcome this ticket exists to prevent.

**Verification:**

- `scripts/quality/untracked-dialog-baseline.txt` contains exactly `0`.
- `pwsh -NoProfile -File scripts/quality/assert-untracked-dialogs.ps1 -Gate` exits 0.

**Status:** `[x]` done

---

### Step 07.2 - Insert the device-test probes

**Files:** the swept sites carrying the highest-traffic dialogs
**Depends on:** Step 07.1

**Prompt for developer:**

> Add one `Timber.d("S1456: <flow entry>")` per changed flow entry - one per swept family, not per file - at these five sites: `ui/browse/managers/BrowseDeleteDialogManager` delete confirmation, `ui/player/helpers/PlayerSettingsManager` playback-speed dialog, `ui/addresource/AddResourceConnectionManager` connection dialog, `ui/streams/StreamRemoveConfirmation` removal confirmation, and `ui/launcher/helpers/LauncherResourceActionManager` resource-action dialog.
> Do not touch the `S1447:` probes inside `util/LifecycleDialogExt.kt`.

**Why:**

Strategic §10 records that S1447 is still in `BlockNeedUserTest` and owns those probes, while this ticket needs its own entry probes to be verifiable on device.

**Verification:**

- `Grep` - `Timber.d("S1456:` matches at least five times across `app_v2/src`.
- `Grep` - `Timber.d("S1447:` still matches exactly twice in `util/LifecycleDialogExt.kt`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 07.3 - Record the capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 07.2

**Prompt for developer:**

> Append one record with `scripts/all_features/add.ps1` describing the fix in English: dialogs raised outside settings now close themselves when their host is destroyed, and a ratchet gate keeps new ones bound.

**Why:**

Strategic §8 asks for an `ALL_FEATURES` record of type FIX instead of a `FEATURES` showcase entry, because nothing user-facing gains a capability.

**Verification:**

- `Grep` - `S1456` matches in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

---

### Step 07.4 - Regenerate the class catalog and close the change

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 07.3

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`, then close the whole change through `scripts/post-change.ps1` naming the full changed set with `-Files` and `-ScopeToFile`.

**Why:**

Strategic §11 requires the catalog to reflect any changed constructor signature, and the closure facade is the only place the gate set runs together.

**Verification:**

- `pwsh -NoProfile -File scripts/post-change.ps1 -Files "<changed set>" -ScopeToFile -Target "S1456" -Description "Bind dialogs outside settings helpers to their host lifecycle" -ChangeType Mixed` exits 0 and prints `post-change: PASS`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 07.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-07)` returns zero hits.
- [ ] `dev/CHANGELOG.md` carries an entry for the ticket.
- [ ] Status advanced to `BlockNeedUserTest` with a `-StatusNote` naming what the device must verify.

## Step Log

- 2026-08-09 - 07.1 PASS. `scripts/quality/untracked-dialog-baseline.txt` reads `0`; `assert-untracked-dialogs.ps1 -Gate` exits 0 with an empty list.
- 2026-08-09 - 07.2 PASS. Six `Timber.d("S1456: ..")` probes at five flow entries (Browse delete has two distinct entries - the local and the network confirmation). Both `S1447:` probes in `LifecycleDialogExt.kt` untouched. `.\a.ps1 fk` BUILD SUCCESSFUL.
- 2026-08-09 - 07.3 and 07.4 PASS through `close-and-log.ps1`: ALL_FEATURES FIX record (area General, all six flavors - the binding lives in `src/main` and is gated by nothing), six dev-log rows, catalog scan and render, status -> `BlockNeedUserTest` with the device-test note.
- 2026-08-09 - Strategic §1 corrected from 146/90 to 144/88 after the predicate fix, and a `REPRO` block added: 144 unbound sites before, gate empty and baseline 0 after, with the runtime half named as the device test's job.

---
## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. The `S1456:` probes stay in the tree until `/spec-check` flips the ticket to `Verified`.

---

## Rollback Plan

Revert the phase commits - no data migration and no user-facing surface changed; the baseline file returns to the previous number with the same command.
