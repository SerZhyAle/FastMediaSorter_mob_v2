# Phase 01 — Strings

**Strategic spec:** [`../S0255_settings-authorization-group.md`](../S0255_settings-authorization-group.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** none — foundation phase
**Blocks:** Phase 02, 03, 04, 05
**Steps done:** 2 / 2
**Started:** 2026-05-19
**Completed:** -

---

## Objective

Add a single trilingual string key `settings_category_authorization` ("Authorization" / "Авторизация" / "Авторизація") that will label the new collapsible group header introduced in Phase 02.

---

## Prerequisites

- [ ] Strategic spec `Status:` is `Approved` or later.
- [ ] No earlier phases (this is the foundation).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a (resource file) |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |

---

## Steps

### Step 01.1 — Add string key in all three locales

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a new `<string name="settings_category_authorization">…</string>` entry to all three `strings.xml` files. Values: EN = `Authorization`, RU = `Авторизация`, UK = `Авторизація`. Place the entry alphabetically inside the existing settings-category cluster (near `settings_category_app_data`, `settings_category_interface`, `settings_category_system`). Apply `docs/COMMUNICATION_POLICY.md` §2 (label formula — single noun, sentence case) and §6 (tone checklist — no exclamation, no "please", no marketing language).

**Verification:**

- `Grep` — `settings_category_authorization` matches exactly once in `app_v2/src/main/res/values/strings.xml`.
- `Grep` — `settings_category_authorization` matches exactly once in `app_v2/src/main/res/values-ru/strings.xml`.
- `Grep` — `settings_category_authorization` matches exactly once in `app_v2/src/main/res/values-uk/strings.xml`.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 — Verification 4/4 PASS. EN/RU/UK each contain 1 hit for `settings_category_authorization`. Strings: "Authorization" / "Авторизация" / "Авторизація" — single noun, sentence case, comm-policy compliant.

---

### Step 01.2 — Validate trilingual parity

**Files:** none modified
**Depends on:** Step 01.1

**Prompt for developer:**

> Run the locale parity script to confirm no missing/extra key in any of the three locale files for the new prefix.

**Verification:**

- Bash — `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_category_authorization"` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 — Verification PASS. check_strings_localized.ps1 exit 0 (`OK: all 1 key(s) present in EN/RU/UK`).

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`R.string.settings_category_authorization` is available for use in layout XMLs. Phase 02 will consume it as the `android:text` of the new `headerAuthorization` TextView.

---

## Rollback Plan

Revert phase commit — no data migration, no user-visible surface yet (the new key is unused).
