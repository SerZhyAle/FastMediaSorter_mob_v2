# Phase 02 - Contact pick flow

**Strategic spec:** [`../S1176_launcher-contact-shortcuts.md`](../S1176_launcher-contact-shortcuts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

---

## Objective

Let the user pick a person and an action from the launcher desktop and land the resulting cell, without the app ever holding a contacts permission.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - the codec exists.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/launcher/ContactSnapshotDataSource.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/PickContactShortcutUseCase.kt` | New | ≤ 140 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherContactPickManager.kt` | New | ≤ 240 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/res/values{,-ru,-uk}/strings.xml` | Modified | n/a |

> `ContactSnapshotDataSource` is the only place that touches `ContactsContract`. Everything above it works on the snapshot model from Phase 01.

---

## Steps

### Step 02.1 - Read the picked contact without a permission

**Files:** `ContactSnapshotDataSource.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Wrap the system contact picker's result: given the content URI the picker returns, read the display name, the lookup key, the preferred phone number and the messenger data rows registered against that contact. The picker grants a one-time read on the picked record only, which is exactly why no `READ_CONTACTS` is needed - state that in KDoc so a later reader does not "fix" it by adding the permission. All queries run off the main thread at this boundary, not in the caller. A contact with no number yields a snapshot where `DIAL` is unavailable rather than a failure. For messenger rows, keep the package that registered each row and drop any whose package is not installed.

**Verification:**

- `Grep` - `ContactsContract` appears only in this file across the whole change.
- `Grep` - `withContext(Dispatchers.IO)` or an equivalent boundary present.
- `Grep` - `READ_CONTACTS` returns zero hits.

**Status:** `[x]` done

### Correction found while implementing (2026-07-30)

**The action decides which system picker opens, so it is asked before the contact.** The step assumed one picker and a later action choice, but `DIAL` has a strictly better door: `ACTION_PICK` on `Phone.CONTENT_URI` returns the exact phone row the user chose, readable under the grant with no deeper query. Asking the action afterwards would force the app to guess which of a contact's numbers to dial.

That also splits the permission risk cleanly. `PROFILE` and `DIAL` read the picked URI itself - covered by the picker's own grant, no assumption involved. Only `MESSAGE` reads deeper (the contact's `Entity` directory, to find the rows messengers registered), and whether the one-time grant reaches that sub-directory is the one thing here that cannot be settled from the source. It is written to degrade - a refused read yields "no channels", never a crash - and it is the device-test item for this ticket.

---

### Step 02.2 - Turn a pick into a placed cell

**Files:** `PickContactShortcutUseCase.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add the use case that takes a picked contact URI plus the chosen action and produces the `LauncherCell` to place: encode the Phase 01 target, take the label from the display name, and place it through the launcher desktop repository's free-slot placement, the same rule S1170 settled. Choosing a messenger channel belongs here too: with one channel, use it; with several, return the choice to the caller rather than guessing; with none, report that `MESSAGE` is unavailable for this contact.

**Verification:**

- `Grep` - `class PickContactShortcutUseCase` present.
- `Grep` - the Phase 01 codec is used to build the target; no manual `"contact:" +` concatenation.
- ~~`Grep` - the free-slot placement function is the only placement call.~~ **Predicate is wrong - see below.**

**Status:** `[ ]` not done

### Correction found before implementing (2026-07-30)

**Free-slot placement is the wrong rule for this entry point.** The desktop add-flow starts from a tap on an EMPTY CELL: `LauncherCellContentPickerDialogFragment` carries `row`/`col`, `LauncherHomeActivity` stores them as `pendingRow`/`pendingCol`, and all seven existing categories land through `addShortcut(command)` at exactly that cell. A contact placed by free-slot search would ignore the cell the user just tapped - a behaviour regression against every other category, and visible as "I tapped there, it appeared somewhere else".

`addCellInFirstFreeSlot` exists for the S1170 Settings entry point, which has no grid on screen and therefore nothing to point at. Same repository, two entry points, two correct rules.

**So the contact flow follows the established shape:** category -> picker -> `addShortcut(LauncherCellCommand.Contact(target))`. The use case's job shrinks to producing the target (snapshot + action + channel resolution); placement stays where it already is.

**Status:** `[x]` done

---

### Step 02.3 - Wire the desktop entry point

**Files:** `LauncherContactPickManager.kt`, `LauncherHomeActivity.kt`, `strings.xml` x3
**Depends on:** Step 02.2

**Prompt for developer:**

> Add "Contact" to the same add-to-desktop path that already offers app shortcuts and gadgets on an empty cell, and put the flow in a manager under `ui/launcher/helpers/` - the Activity delegates, it does not hold the logic (CLAUDE.md Rule 3, and `LauncherHomeActivity` is already near its ceiling). The manager launches the system picker through an activity-result contract, asks for the action, resolves the channel when needed, and calls the Phase 02.2 use case. Every new string across EN/RU/UK in one `set-android-string.ps1 -Action add` call, checked against `docs/COMMUNICATION_POLICY.md` §2 and §6. The action chooser is a selection dialog: reuse the project's canonical list-selection dialog rather than a one-off, and its cancel button must use the named dialog-cancel style (CLAUDE.md §11).

**Verification:**

- `Glob` - `LauncherContactPickManager.kt` exists under `src/launcherEnabled/`.
- `Grep` - `LauncherHomeActivity` gained no business logic - it only constructs and delegates to the manager.
- `Grep` - the canonical list-selection dialog type is used; no bespoke `AlertDialog.Builder` list.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "<prefix>"` exits 0.
- `pwsh -NoProfile -File scripts/quality/assert-dialog-cancel-style.ps1` passes.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `standard` via `a.ps1 fk` and then `a.ps1 db`, `noLegal` via `a.ps1 fkn`; all BUILD SUCCESSFUL with no warnings. The full debug build is not redundant here: `fk` stops before `hiltJavaCompile`, so it is the only run that actually validates the new bindings in the graph.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] `LauncherHomeActivity` stays under the 1500-line ceiling - 635 lines.
- [x] No manifest gained `READ_CONTACTS` or `CALL_PHONE` - grep over every `AndroidManifest.xml` returns nothing.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Check the activity-result contract is registered before `onStart` and that the manager is torn down with the Activity.

## Phase-boundary audit (2026-07-30)

No P0/P1. Checked: the contract is registered in an Activity field initialiser, which is the only point before `STARTED` (`BaseActivity` posts `setupViews()`); the manager is a plain field of the Activity, so it dies with it and holds nothing static; every cursor is inside `use`; both provider reads sit behind `withContext(Dispatchers.IO)`; the `PackageManager` lookup that resolves a channel's owner runs inside that same IO context; no listener is registered, so there is no symmetry edge to match; and nothing about the person reaches Timber - only the action name and the exception class.

**P2, fixed in place:** a result arriving after the process was killed behind the system picker used to do nothing at all. The result registry restores the result; neither the in-flight action nor the host's pending square survives, so the pick is genuinely unrecoverable - but it now says so instead of failing silently.

**P3, accepted:** `SearchableOptionPickerDialog` holds its callback in a transient field, so a rotation with the action dialog open leaves that dialog inert. Pre-existing behaviour of the shared component, identical for every other picker in this add-flow; changing it belongs to that component, not to this phase.

---

## Handoff Notes to Next Phase

Cells now carry real `contact:` targets. Phase 03 renders them; until it lands they draw with whatever the generic shortcut binder shows.

Two things Phase 03 should not have to rediscover:

- `LauncherContactChannel` was added to `LauncherContactTarget.kt` rather than its own file - it is the picking-time pair of a target and the label its own messenger wrote, and it is not stored.
- `ic_contact.xml` is new, and it is the category row's icon only. It is deliberately NOT the cell's icon: Phase 03 draws the person's photo or a monogram, and a shared glyph would make every contact cell identical.

The launcher's add-cell picker is not one of the registries `IconInventoryExportTest` scans - none of its category icons (`ic_apps`, `ic_folder`, ..) appear in `docs/icons/icon-inventory.json` - so `ic_contact` needs no inventory record and adds no icon-doc debt.

---

## Rollback Plan

Revert the phase commit. Cells already pinned keep a valid `contact:` target that Phase 01's executor still runs, so nothing is orphaned.
