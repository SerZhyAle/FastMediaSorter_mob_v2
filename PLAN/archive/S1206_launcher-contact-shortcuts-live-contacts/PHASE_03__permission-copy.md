# Phase 03 - Permission copy

**Strategic spec:** [`../S1206_launcher-contact-shortcuts-live-contacts.md`](../S1206_launcher-contact-shortcuts-live-contacts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of Phase 01 and Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Give the contacts permission the paragraph that explains it at pin time, in all three locales, registered
against the permission rather than the screen that asks.

---

## Prerequisites

- [x] Working tree is clean or on a feature branch.
- [x] `docs/COMMUNICATION_POLICY.md` §2 and §6 read before writing any string.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a - script-driven |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a - script-driven |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a - script-driven |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PermissionEntry.kt` | Modified | ≤ 115 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt` | Modified | ≤ 480 |

> No file in this phase exceeds 500 LOC, so no backup step is required.

---

## Steps

### Step 03.1 - Add the rationale and addendum strings in three locales

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add two keys with one lockstep call each:
>
> ```powershell
> pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key perm_rationale_read_contacts -En "<en>" -Ru "<ru>" -Uk "<uk>"
> pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key perm_addendum_contacts_cell_pinning -En "<en>" -Ru "<ru>" -Uk "<uk>"
> ```
>
> `perm_rationale_read_contacts` is the paragraph shown while asking: it says the app reads a contact's
> current name and photo so a pinned cell keeps up with the address book, and that refusing leaves the
> cell working from what was saved when it was pinned. `perm_addendum_contacts_cell_pinning` is the one
> sentence appended when the request happens during pinning. Check both against
> `docs/COMMUNICATION_POLICY.md` §2 for the message formula and §6 for tone. Do not promise the multiple
> number picker - strategic §3.3 puts it outside this iteration.

**Why:**

Strategic §3 names the remaining cost of this ticket as "формулировка запроса по
`docs/COMMUNICATION_POLICY*.md` - объяснить пользователю, зачем лаунчеру контакты, EN/RU/UK", and the
registry entry for `read_contacts` currently carries only the four-word row label `perm_desc_read_contacts`
("Names for pinned cells"), which is a list label rather than an explanation.

**Verification:**

- `Grep` - `perm_rationale_read_contacts` present in all three `strings.xml` files.
- `Grep` - `perm_addendum_contacts_cell_pinning` present in all three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "perm_"` exits 0.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist.

**Status:** `[x]` done

---

### Step 03.2 - Add the pinning task to `PermissionTask`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PermissionEntry.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add `CONTACT_CELL_PINNING` to the `PermissionTask` enum, keeping the existing one-value-per-line shape.

**Why:**

The enum's own KDoc states that only jobs needing their own sentence appear in it, and strategic §3.3 puts
the request at a specific job - "при закреплении контакта на стол" - whose wording differs from the plain
row description.

**Verification:**

- `Grep` - `CONTACT_CELL_PINNING` matches exactly once in `PermissionEntry.kt`.

**Status:** `[x]` done

---

### Step 03.3 - Wire both strings onto the `read_contacts` entry

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> On the existing `read_contacts` `PermissionEntry`, add `rationaleRes = R.string.perm_rationale_read_contacts`
> and `taskAddenda = mapOf(PermissionTask.CONTACT_CELL_PINNING to R.string.perm_addendum_contacts_cell_pinning)`.
> Leave `id`, `manifestName`, `group`, `optional` and `buildGates` untouched. Mirror the shape of the
> `camera` entry directly below, which already declares a `taskAddenda` map.

**Why:**

`PermissionEntry.taskAddenda` documents that the per-task sentence is declared on the permission rather
than at the call site, because the differing part is still a property of the permission; putting the copy
here is what lets Phase 04 fetch it with the existing `permissionRationale(permission, task)` helper
instead of hand-picking a string resource.

**Verification:**

- `Grep` - `perm_rationale_read_contacts` present in `PermissionRegistryRepositoryImpl.kt`.
- `Grep` - `CONTACT_CELL_PINNING` present in `PermissionRegistryRepositoryImpl.kt`.
- `Grep` - `buildGates = setOf("SUPPORT_LAUNCHER")` still present on the `read_contacts` entry.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0, `Fast check passed` (2026-08-08).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "perm_"` exits 0 - 79 keys, all present in en/ru/uk.
- [x] Dev log entry added - batched for the whole ticket at Phase 05 closure.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. The registry entry keeps its id, gates and optional flag; the two new keys are additive and read by nothing until Phase 04.

---

## Handoff Notes to Next Phase

`activity.permissionRationale(Manifest.permission.READ_CONTACTS, PermissionTask.CONTACT_CELL_PINNING)` now
returns the full paragraph plus the pinning sentence. Phase 04 calls exactly that and adds no string of
its own.

---

## Rollback Plan

Revert phase commit(s) - remove the two keys with
`scripts/utils/set-android-string.ps1 -Action remove` and drop the two entry fields. No user-facing surface
changes, since nothing reads them until Phase 04.
