# Phase 04 - Privacy policy paragraph

**Strategic spec:** [`../S1335_read-contacts-permission-plumbing.md`](../S1335_read-contacts-permission-plumbing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 05
**Steps done:** 1 / 1
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Disclose the new restricted permission in the privacy policy, all three locales - a prerequisite the
strategic spec names before a release carrying this can ship.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/PRIVACY_POLICY.md` | Modified | +4 lines |
| `docs/PRIVACY_POLICY.ru.md` | Modified | +4 lines |
| `docs/PRIVACY_POLICY.uk.md` | Modified | +4 lines |

---

## Steps

### Step 04.1 - Add the Contacts Permissions subsection, all three locales

**Files:** `docs/PRIVACY_POLICY.md`, `docs/PRIVACY_POLICY.ru.md`, `docs/PRIVACY_POLICY.uk.md`
**Depends on:** - start of phase

**Prompt for developer:**

> In each of the three files' "Permissions Explained" section (`docs/PRIVACY_POLICY.md:124-141` in
> English - same structure in the `.ru.md`/`.uk.md` translations), insert a new `### Contacts
> Permissions` subsection between the existing `### Network Permissions` and `### Other Permissions`
> headers, matching the one-bullet-per-permission style already used there:
> ```markdown
> ### Contacts Permissions
>
> - `READ_CONTACTS`: Optional, used only to show a pinned contact's name and photo on the launcher
>   (Settings > Permissions > Contacts); denying it keeps a plain initial in place of the photo.
> ```
> Translate the bullet into RU/UK for the other two files, matching each file's existing translated
> tone for the neighbouring Storage/Network bullets - do not translate the `### Contacts Permissions`
> header differently from how `### Network Permissions` is already translated in each file (grep it
> first, mirror its exact heading-translation convention).

**Verification:**

- `Grep` - `docs/PRIVACY_POLICY.md` contains `READ_CONTACTS` and a `### Contacts Permissions` header.
- `Grep` - `docs/PRIVACY_POLICY.ru.md` contains `READ_CONTACTS`.
- `Grep` - `docs/PRIVACY_POLICY.uk.md` contains `READ_CONTACTS`.

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 3/3 PASS. Files: `docs/PRIVACY_POLICY.md`, `.ru.md`, `.uk.md` (+4 lines
  each). Document-registry gate flagged `legal-downloads` (published, EN/RU/UK legal doc) - siblings
  (`TERMS_OF_SERVICE.md`, `DOWNLOADS_*.md`, `OPEN_SOURCE.md`, `THIRD_PARTY_LICENSES.md`) checked, none
  concern a runtime-permission disclosure - acknowledged via `-RegistryAck 'legal-downloads'`.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Dev log entry added for all three files via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - doc-only phase, no P0/P1 findings.

---

## Handoff Notes to Next Phase

Privacy policy discloses `READ_CONTACTS` in all three locales. The Play Console restricted-permission
declaration form (release-time, not a repo file) still gates the next release - tracked as a manual
item in `INDEX.md`, not a phase.

---

## Rollback Plan

Remove the three inserted subsections - no data migration, no code dependency on this doc's content.
