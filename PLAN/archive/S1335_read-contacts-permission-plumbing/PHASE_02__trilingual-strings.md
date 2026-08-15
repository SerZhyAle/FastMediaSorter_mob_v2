# Phase 02 - Trilingual strings

**Strategic spec:** [`../S1335_read-contacts-permission-plumbing.md`](../S1335_read-contacts-permission-plumbing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 1 / 1
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Add the three string keys (`perm_group_contacts`, `perm_title_read_contacts`,
`perm_desc_read_contacts`) in EN/RU/UK, matching the existing `perm_group_*`/`perm_title_*`/
`perm_desc_*` naming pattern - Phase 03's registry entry references these `R.string.*` ids at compile
time, so this phase must land no later than Phase 03.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | +3 lines |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +3 lines |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +3 lines |

---

## Steps

### Step 02.1 - Add the three permission strings, all three locales

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Use `scripts/utils/set-android-string.ps1 -Action add -Key <key> -En "<en>" -Ru "<ru>" -Uk "<uk>"`
> once per key (parity-enforced, byte-preserving) - do not hand-edit the XML. Add:
> - `perm_group_contacts` - group header, matching the terse noun style of `perm_group_network`
>   ("Network") / `perm_group_camera`. EN: "Contacts".
> - `perm_title_read_contacts` - matching `perm_title_access_local_network` ("Local network access")
>   style: short capability name. EN: "Contacts access".
> - `perm_desc_read_contacts` - matching `perm_desc_access_local_network`'s one-sentence,
>   plain-language, no-jargon style (this app's non-technical-audience convention): explain what it
>   is for (pinning a contact to the launcher, showing their photo/name) without naming internal
>   classes or tickets. EN: "Show a contact's name and photo when you pin them to your launcher, and
>   let you reach them directly from the pinned cell."
>
> RU/UK translations: plain, non-technical register (per `docs/COMMUNICATION_POLICY.md` house style -
> this repo's audience is explicitly non-technical), matching the tone of the neighbouring
> `perm_desc_access_local_network` translations already in each locale file.

**Verification:**

- `Grep` - `name="perm_group_contacts"` present in all three `strings.xml` files (`values`,
  `values-ru`, `values-uk`).
- `Grep` - `name="perm_title_read_contacts"` present in all three.
- `Grep` - `name="perm_desc_read_contacts"` present in all three.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "perm_"` exits 0 (parity
  audit after any `strings.xml` touch, per CLAUDE.md Post-Change §3).

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 4/4 PASS. Files: `values/strings.xml`, `values-ru/strings.xml`,
  `values-uk/strings.xml` (+3 keys each). `check_strings_localized.ps1 -KeyPrefix "perm_"` - 53/53
  keys present in en/ru/uk, no gaps.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "perm_"` exits 0.
- [x] Dev log entry added for the three `strings.xml` files via `.\scripts\add_to_dev_log.ps1` (via `post-change.ps1`).
- [x] Phase-boundary audit run - doc/resource-only phase, Layer 1 only: naming matches existing
      `perm_*` convention, no P0/P1 findings.

---

## Handoff Notes to Next Phase

Three string resources exist. Phase 03's `PermissionEntry` and `getGroups()` arm can now reference
`R.string.perm_group_contacts` / `R.string.perm_title_read_contacts` /
`R.string.perm_desc_read_contacts` without a missing-resource compile error.

---

## Rollback Plan

`scripts/utils/set-android-string.ps1 -Action remove -Key <key>` for each of the three keys, all three
locales - no data migration, no other surface depends on these strings yet.
