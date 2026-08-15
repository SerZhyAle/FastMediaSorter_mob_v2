# Phase 02 - Listing uploader

**Strategic spec:** [`../S0497_play-store-listing-aso-rewrite.md`](../S0497_play-store-listing-aso-rewrite.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-18
**Completed:** 2026-06-19

---

## Objective

Add a Play Developer API uploader that pushes per-locale listing texts and images from `play/listing/`,
reusing the existing service-account key, with a non-committing `validate` mode and an explicit `commit` mode.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`play/listing/<locale>/*.txt` exist).
- [ ] `.secrets/play-console-key.json` present (same key as `publish-play-release.py`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/release/publish-play-listing.py` | New | ≤ 220 |
| `scripts/release/publish-play-listing.ps1` | New | ≤ 70 |

---

## Steps

### Step 02.1 - Implement the Python listing uploader

**Files:** `scripts/release/publish-play-listing.py`
**Depends on:** - start of phase

**Prompt for developer:**

> Mirror auth/key resolution from `scripts/release/publish-play-release.py` (service_account
> credentials, scope `androidpublisher`, `build('androidpublisher','v3')`, key search incl.
> `.secrets/play-console-key.json`). Create an edit via `edits().insert()`. For each locale dir under
> `play/listing/` map folder->language (`en-US`,`ru-RU`,`uk-UA`) and call `edits().listings().update()`
> with `title`, `shortDescription`, `fullDescription` read from the `.txt` files (UTF-8). If
> `images/phoneScreenshots/` exists for a locale, call `edits().images().deleteall()` then
> `edits().images().upload()` per file for `phoneScreenshots` (and `featureGraphic` if present).
> Validate char limits before upload (skip + warn on over-limit, non-zero exit). Accept argv `mode`
> (`validate` default | `commit`): `validate` calls `edits().validate()` and never commits; `commit`
> calls `edits().commit()`. Print a per-locale summary and the edit id. Timber-style is N/A (Python) -
> use `print`. No bare `except:` - catch specific API errors and exit non-zero with the message.

**Verification:**

- `Glob` - `scripts/release/publish-play-listing.py` exists.
- `Grep` - `edits().listings().update` present.
- `Grep` - `edits().images()` present.
- `Grep` - both `edits().validate(` and `edits().commit(` present.
- `Grep` - `play-console-key.json` present (key reuse).

**Status:** `[x]` done

---

### Step 02.2 - Implement the PowerShell wrapper

**Files:** `scripts/release/publish-play-listing.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Mirror `scripts/release/publish-play-release.ps1`: resolve `.venv\Scripts\python.exe`, invoke
> `publish-play-listing.py`, throw on non-zero exit. Add `-Mode` param (`validate` default | `commit`),
> pass it through to the Python script. Default run must be `validate` (no live commit).

**Verification:**

- `Glob` - `scripts/release/publish-play-listing.ps1` exists.
- `Grep` - `publish-play-listing.py` referenced.
- `Grep` - `validate` is the default for `-Mode` (param default literal).

**Status:** `[x]` done

---

### Step 02.3 - Run validate-mode dry pass

**Files:** `scripts/release/publish-play-listing.py`, `scripts/release/publish-play-listing.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/release/publish-play-listing.ps1 -Mode validate` and confirm it
> authenticates, builds the edit, validates the listing payload, and exits 0 without committing. If the
> service account lacks "Edit store listing" rights the API error surfaces here - record it as a blocker.
> Record the command run and its exit code per CLAUDE.md §15.

**Verification:**

- Command exit code 0 recorded (or blocker logged if rights missing).
- `Grep` - no `edits().commit(` was reached in validate mode (mode branch verified by reading output).

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification FAIL (exit 1), blocker logged. Ran `pwsh -NoProfile -File scripts/release/publish-play-listing.ps1 -Mode validate` (owner-authorized). Auth OK; edit `14312239236686287521` created; `en-US` + `ru-RU` listings updated in-edit (confirms service account HOLDS "Edit store listing" rights); `commit()` not reached (validate mode). FAIL cause: `uk-UA` rejected with HTTP 400 "The requested language is not currently supported: uk-UA" - Ukrainian is not enabled as a store-listing language in the Play Console. External blocker → strategic status `BlockExternal`. Resolution: owner adds Ukrainian under Play Console → Store presence → Main store listing → Manage translations, then re-run validate.
- 2026-06-19 - Root cause was NOT external: Play's Ukrainian language code is `uk`, not `uk-UA` (owner-provided Console screenshot). Fixed `publish-play-listing.py` to map folder->Play-language (`uk-UA`->`uk`, en-US/ru-RU unchanged); reverted status BlockExternal->In Progress. Re-ran validate (owner-authorized): exit 0, edit `00449176981496323933`, all three locales updated (`en-US`, `ru-RU`, `uk-UA -> uk`), `edits().validate()` succeeded, `commit()` not reached. Verification 2/2 PASS.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `publish-play-listing.ps1 -Mode validate` exits 0 (or blocker logged).
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Uploader can push texts now; image push activates once Phase 03 produces
`play/listing/<locale>/images/phoneScreenshots/`. Live `commit` stays owner-gated (INDEX operational note).

---

## Rollback Plan

Delete both `publish-play-listing.*` scripts - no other code imports them; existing AAB uploader untouched.
