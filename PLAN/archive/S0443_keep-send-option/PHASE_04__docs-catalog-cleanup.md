# Phase 04 - Docs, catalog, device verification

**Strategic spec:** [`../S0443_keep-send-option.md`](../S0443_keep-send-option.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** -
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Record the new user-visible toggle in feature docs (EN/RU/UK), regenerate the class catalog for touched/new classes, and stage on-device verification of the four Keep surfaces. This is the closing phase: code is complete after Phase 03; here the spec becomes externally consistent and testable.

---

## Prerequisites

- [ ] Phases 01-03 ✅ Done; project builds green.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | - |
| `docs/FEATURES_RU.md` | Modified | - |
| `docs/FEATURES_UK.md` | Modified | - |
| `dev/CATALOG/app_v2.jsonl` (+ `.md`) | Regenerated | - |

> `dev/CHANGELOG.md` is updated per-file across all phases via `scripts/add_to_dev_log.ps1` (not a Files-Touched row here).

---

## Steps

### Step 04.1 - Document the feature + regenerate catalog

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`, catalog

**Depends on:** Phases 01-03

**Prompt for developer:**

> Add a feature entry describing the "Allow send to Google Keep" toggle: it lives in the "Send file to.." group on the Player settings page, defaults ON when a Google account is present, is disabled (with a "Not installed" subtitle) when Keep is absent, and controls visibility of the "Send to Keep" command everywhere it appears. Mirror the wording in all three locales (`docs/FEATURES.md` + `_RU.md` + `_UK.md`), matching the existing tone. Then regenerate the catalog for the affected module: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Set `role` + `status` for the new class (`KeepShareTargetModule`) via the catalog `set.ps1` if `catalog_sync` leaves it `unknown`.

**Verification:**

- `Grep` - the Keep-toggle feature line is present in `docs/FEATURES.md`, `_RU.md`, and `_UK.md` (all three).
- `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0.
- `Grep` - `KeepShareTargetModule` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[ ]` not done

---

### Step 04.2 - Stage on-device verification (BlockNeedUserTest)

**Files:** the gated surfaces from Phases 02-03 (one debug tag per changed flow entry)

**Depends on:** Step 04.1

**Prompt for developer:**

> This step transitions the ticket to `BlockNeedUserTest`. Per the project tag invariant, insert exactly one `Timber.d("S0443: <path description>")` at the entry point of each distinct changed flow - i.e. where each of the four Keep gates computes its combined `keepEnabled && keepInstalled` value (player command panel, text editor action panel, draw overlay, standalone overflow if it is a distinct path). Do not insert tags in intermediate helpers and do not embed `S0443` in any permanent `Timber.i/w/e`. Then build an installable APK (`.\a.ps1 dav`) and prepare the device-test script below. The parent / `/spec-test-device` runs the walk; tags are removed on exit from `BlockNeedUserTest` (to `Verified`), committed with the status change.

**On-device test checklist (for `/spec-test-device`):**

1. Keep installed, account present (fresh install): the "Keep" toggle is present in Player settings "Send file to.." group and ON by default.
2. Toggle ON + Keep installed: "Send to Keep" appears in - (a) player command-panel overflow on a text file, (b) text editor action-panel overflow, (c) draw-overlay editor overflow on a static image, (d) standalone text host overflow.
3. Toggle OFF: the command disappears from all four surfaces; toggle row stays enabled.
4. Keep uninstalled: the toggle is disabled with the "Not installed" subtitle and the command is absent everywhere.
5. Account absent (no Google): the toggle defaults OFF; turning it ON + Keep installed still surfaces the command.

**Verification:**

- `Grep -n 'Timber\.d("S0443:'` - one tag per changed Keep-gate flow entry; zero in permanent `Timber.i/w/e`.
- `.\a.ps1 dav` produces an installable APK (record the build log path).
- Device walk recorded by `/spec-test-device` (deferred to the device run).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` carry the Keep-toggle entry.
- [ ] `pwsh -NoProfile -File scripts/check_strings_localized.ps1` is not required (no new `strings.xml` keys - the Keep label/icon already exist); if any string was added, run it and fix exit 1.
- [ ] Catalog regenerated; new class has `role` + `status`.
- [ ] `Timber.d("S0443:` tags present iff status is `BlockNeedUserTest`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.

---

## Handoff Notes to Next Phase

- After device confirmation: remove every `Timber.d("S0443:` tag, commit with the `BlockNeedUserTest -> Verified` transition, then `/spec-check S0443`.

---

## Rollback Plan

Docs/catalog are additive and safe to revert. Debug tags are removed on leaving `BlockNeedUserTest` regardless.
