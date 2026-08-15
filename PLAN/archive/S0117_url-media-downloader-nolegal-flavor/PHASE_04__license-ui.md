# Phase 04 - License UI

**Strategic spec:** [../S0117_url-media-downloader-nolegal-flavor.md](../S0117_url-media-downloader-nolegal-flavor.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-05-09
**Completed:** 2026-05-09

---

## Objective

Expose the GPL notice and upstream source links for the embedded NewPipe dependency on the existing Open Source Licenses surface, gated to `noLegal` only.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Trilingual strings for batch UX are already in place.
- [x] Existing Open Source Licenses screen has been re-read before edits.
- [x] Portrait and landscape settings entry points are updated together if touched.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OpenSourceLicensesFragment.kt` | Modified | <= 250 |
| `app_v2/src/main/res/layout/fragment_open_source_licenses.xml` | Modified | <= 300 |
| `app_v2/src/main/res/values/strings.xml` | Modified | <= 200 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | <= 200 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | <= 200 |

---

## Steps

### Step 04.1 - Add the noLegal GPL card to the existing licenses screen

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OpenSourceLicensesFragment.kt`, `app_v2/src/main/res/layout/fragment_open_source_licenses.xml`
**Depends on:** Step 03.3

**Prompt for developer:**

> Add a dedicated NewPipe GPL card to the existing Open Source Licenses screen and gate it with `BuildConfig.IS_NO_LEGAL_FLAVOR` so market variants never show the section. Reuse the current screen structure instead of creating a new first-run dialog.

**Verification:**

- `Grep` - `IS_NO_LEGAL_FLAVOR` referenced in `OpenSourceLicensesFragment.kt`.
- `Grep` - `btn_newpipe_source` present in `fragment_open_source_licenses.xml`.
- `Grep` - `btn_newpipe_license` present in `fragment_open_source_licenses.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 - Verification PASS. Added hidden NewPipe GPL card and gated it with `BuildConfig.IS_NO_LEGAL_FLAVOR`.

---

### Step 04.2 - Add EN/RU/UK strings for the noLegal license section

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add the trilingual strings for the NewPipe GPL notice and upstream source links using the `s0117_` prefix. Keep public wording platform-neutral and focused on license compliance only.

**Verification:**

- `Grep` - `s0117_` keys present in all three `strings.xml` files.
- `Command` - `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix s0117_` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 - Verification PASS. Added trilingual `s0117_` GPL notice strings and re-ran localization parity.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles for the touched slice via `:app_v2:compileNoLegalDebugKotlin`.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] Portrait/landscape settings entry points remain in sync.

---

## Handoff Notes to Next Phase

Final phase should focus on catalog sync, string audit, spec status, and final validation only.