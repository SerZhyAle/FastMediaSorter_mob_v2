# Phase 05 - Docs and catalog cleanup

**Strategic spec:** [`../S1206_launcher-contact-shortcuts-live-contacts.md`](../S1206_launcher-contact-shortcuts-live-contacts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 5 / 5
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Record the delivered capability, refresh the generated indexes, and close the ticket through the facade.

---

## Prerequisites

- [x] Phases 01-04 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | script-driven |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | script-driven |
| `dev/CHANGELOG.md` | Modified | script-driven |

---

## Steps

### Step 05.1 - Record the capability in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one record through `pwsh -NoProfile -File scripts/all_features/add.ps1`, in English, describing that
> a pinned contact cell shows the person's current name and photo from the address book, asking for the
> contacts permission when the cell is pinned and falling back to what was saved at pin time if the
> permission is refused or the contact is gone. Set `spec` to `S1206`. The capability ships on `standard`
> and `noLegal`, which is what `docs/FLAVOR_MATRIX.md` reports for `SUPPORT_LAUNCHER` - read the matrix,
> do not restate it from memory.

**Why:**

CLAUDE.md section 11 makes `docs/ALL_FEATURES.jsonl` the inventory every shipped capability is recorded in,
and `/skill-release` builds the public showcase from its diff - a capability missing here never reaches the
release notes.

**Verification:**

- `Grep` - `S1206` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0 - `PASS: 675 record(s)`.

**Status:** `[x]` done

---

### Step 05.2 - Register the new classes in the catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`, then set `role` and `status` for the
> two classes Phase 01 added, `LiveContactDataSource` and `LiveContactDetails`, with
> `dev/CATALOG/scripts/set.ps1`. Both live in `src/main` and ship in every flavor that compiles it, so no
> `-NoFlavors` hint applies.

**Why:**

CLAUDE.md section "Catalog & Navigation" requires a new class to carry `role` and `status`, and the catalog
is the first lookup every later ticket performs before grepping.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "LiveContactDataSource"` returns one record with a non-empty `role`.

**Status:** `[x]` done

---

### Step 05.3 - Insert the debug verification tags

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/launcher/LiveContactDataSource.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherContactPickManager.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Add one `Timber.d("S1206: ..")` per changed flow entry, and no more: one in `LiveContactDataSource.read`
> reporting whether a live answer was produced and whether it carried a photo, and one in
> `LauncherContactPickManager.start` reporting the grant state the pin flow saw. Log no name, number,
> lookup key or photo URI - only the outcome kind, matching the discipline `ContactSnapshotDataSource`'s
> KDoc states. These are temporary probes for the device test and are removed when the ticket leaves
> `BlockNeedUserTest`.

**Why:**

CLAUDE.md "Debug Verification Tags" makes a `Timber.d("S1206:` tag exist if and only if the ticket sits in
`BlockNeedUserTest`, and this ticket enters that status at the end of this phase because the two things it
delivers - a live photo and a permission dialog - are only observable on a device with a real address book.

**Verification:**

- `Grep` - `Timber.d("S1206:` matches exactly twice across `app_v2/src`.
- `Grep` - `S1206` returns zero hits in any `Timber.i`, `Timber.w` or `Timber.e` call.

**Status:** `[x]` done

---

### Step 05.4 - Set the block status before the gates run

**Files:** `PLAN/spec-catalog.jsonl` (via CLI - never hand-edited)
**Depends on:** Step 05.3

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S1206 -Status BlockNeedUserTest -StatusNote '<what to test>'`.
> The note describes the device test: pin a contact cell and confirm the explanation appears before the
> system asks for contacts; confirm the cell then shows that person's photo rather than initials; rename
> the contact in the system address book and confirm the cell caption follows without reopening the app;
> refuse the permission on a second cell and confirm it still pins and still works. Use no quote characters
> anywhere inside the note.

**Why:**

`scripts/quality/assert-no-ticket-logs.ps1` runs inside the closure facade and accepts a `Timber.d("S1206:`
probe only while the ticket sits in `BlockNeedUserTest`, so flipping the status after the gate would fail a
close that the same command would pass a minute later.

**Verification:**

- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S1206 -Format json` reports `BlockNeedUserTest`.
- The reported `statusNote` is non-empty and contains no quote characters.

**Status:** `[x]` done

---

### Step 05.5 - Close through the facade

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 05.4

**Prompt for developer:**

> Run `scripts/post-change.ps1` naming the whole changed set with `-Files` and adding `-ScopeToFile`, with
> `-ChangeType Mixed`. Read the verdict: only a bare `post-change: PASS` is clean, and
> `PASS WITH ADVISORIES` names each advisory that has to be read.

**Why:**

CLAUDE.md section 12 routes mechanical closure through the facade so the gates run before the mutating
steps, which is what keeps a failed close from writing a changelog row that a re-run would then duplicate.

**Verification:**

- `scripts/post-change.ps1` exits 0 and prints `post-change: PASS` - bare, no advisories (2026-08-08, 14 files, `-ScopeToFile`).
- `Grep` - `dev/CHANGELOG.md` carries exactly one new entry for this ticket.

**Status:** `[x]` done

**Step Log**

- The first closure run failed the scoped detekt gate on `MatchingDeclarationName`: `PermissionAskability.kt` held both the extension functions and the Hilt entry-point interface. Split into `PermissionAskabilityEntryPoint.kt`, mirroring `PermissionRationaleText` / `PermissionRationaleEntryPoint` beside it. The failed run wrote no changelog row, so the re-run produced exactly one.
- `LauncherContactTarget`'s KDoc was corrected in the same set: it still declared the snapshot to be "the whole design" and pointed at this ticket as the place a live path would belong. Left alone it would have been the most misleading comment in the feature.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 db` BUILD SUCCESSFUL, APK packaged (2026-08-08), plus `.\a.ps1 fk` after the last comment-only edit.
- [x] `dev/CHANGELOG.md` has an entry for this ticket.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - 2655 records.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. The ticket rests in `BlockNeedUserTest` until the device test
confirms a live photo and a live rename on a real address book; `/spec-check` removes the two probes when it
flips the status.

---

## Rollback Plan

Revert phase commit(s) - documentation and catalog only; the generated indexes regenerate from source.
