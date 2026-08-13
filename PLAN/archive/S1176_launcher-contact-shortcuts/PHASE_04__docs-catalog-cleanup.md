# Phase 04 - Docs catalog cleanup

**Strategic spec:** [`../S1176_launcher-contact-shortcuts.md`](../S1176_launcher-contact-shortcuts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

---

## Objective

Land the device-verification probes, register the capability, and regenerate the class catalog.

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ExecuteLauncherCommandUseCase.kt` | Modified | ≤ 205 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherContactPickManager.kt` | Modified | ≤ 245 |
| `docs/ALL_FEATURES.jsonl` | Modified | n/a - append via CLI |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a - generated index |

---

## Steps

### Step 04.1 - Add the device-verification probes

**Files:** `ExecuteLauncherCommandUseCase.kt`, `LauncherContactPickManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add exactly two `Timber.d("S1176: ..")` lines, one per changed flow entry: one where a pick becomes a placed cell, one where a contact command is executed. Each on a single line at or under 120 characters - a probe split across two lines does not match `Timber.d("S1176:` and both the gate and the later removal grep would miss it. **Log the action kind and the outcome only.** No display name, no phone number, no lookup key, no package name of the messenger - this is the one flow in the app where a careless probe leaks a user's address book into a shareable log file.

**Verification:**

- `Grep` - `Timber.d("S1176:` matches exactly twice across the repo.
- `Grep` - neither probe interpolates a name, number, lookup key or channel package.
- `Grep` - zero `S1176` hits inside any `Timber.i`/`Timber.w`/`Timber.e` call.

**Status:** `[x]` done - both verified by grep; the probes carry an action name, an outcome class name and a boolean, nothing else.

The pick probe was given a second job. Its outcome kind for `MESSAGE` tests exactly the grant reach that Phase 03's photo question also depends on, so one device round answers both - which is why S1319 needs no probe of its own.

---

### Step 04.2 - Record the capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Append an `ADD` record through `scripts/all_features/add.ps1`: a contact shortcut on the launcher desktop opens the person's profile, the dialler with their number, or their messenger channel. Read the flavor list off the actual gate, not a sibling record - the launcher desktop ships from `src/launcherEnabled`, mounted for `standard` and `noLegal` in `app_v2/build.gradle.kts` with no gradle property of its own. Mention in the description that no contacts permission is requested; that is the user-visible fact that distinguishes this from every other launcher's contact shortcut.

**Verification:**

- `Grep` - `S1176` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.
- The record's `flavors` field matches the source-set mount read from `app_v2/build.gradle.kts`.

**Status:** `[x]` done - `launcher.contact-shortcuts-without-a-contacts-permission`, validate PASS over 616 records.

`flavors` read off the gate itself: `app_v2/build.gradle.kts` mounts `src/launcherEnabled` under `standard` and `noLegal`, and names the same pair in `val launcherFlavors` when injecting that source set's manifest. Not copied from a sibling record.

---

### Step 04.3 - Regenerate the catalog and close out

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 04.2

**Prompt for developer:**

> Run `scripts/catalog_sync.ps1 -Module app_v2` once for the ticket, then set `role` and `status` on the new public types via `dev/CATALOG/scripts/set.ps1`: `LauncherContactTarget`, `ContactSnapshotDataSource`, `PickContactShortcutUseCase`, `LauncherContactPickManager`. The manager lives only in `launcherEnabled`, so declare `-NoFlavors "lite,photos,legacy"` on it. Route the remaining mechanical closure through `scripts/post-change.ps1 -ChangeType Mixed -ScopeToFile`.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "*Contact*"` returns the new types with non-empty `role`.
- `dev/CHANGELOG.md` carries an entry for this ticket.

**Status:** `[x]` done - four types given a role and `status=new`; the manager also carries `noFlavors=[lite,photos,legacy]`, since it exists only in `launcherEnabled`.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (validates code and probes in one pass).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Final check of strategic §11.6: `Grep` for `READ_CONTACTS` and `CALL_PHONE` across every `AndroidManifest.xml` returns zero hits.
- [ ] Status advanced to `BlockNeedUserTest` with a `-StatusNote` carrying strategic §11: pick a contact, place all three actions, confirm each opens the right destination, confirm the avatar/monogram and the spoken description, and confirm a contact with no messenger channel says so.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see [INDEX.md](INDEX.md) Completion Gate. The `S1176:` probes stay until the ticket leaves `BlockNeedUserTest`.

---

## Rollback Plan

Revert the phase commit and remove the `ALL_FEATURES` record with its tooling; the catalog index is gitignored and regenerates.
