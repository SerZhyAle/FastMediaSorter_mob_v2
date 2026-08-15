# Phase 05 - Docs & catalog cleanup

**Strategic spec:** [`../S0468_screenshot-clipboard.md`](../S0468_screenshot-clipboard.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Document the new capability, record the functionality-lifecycle entry, and regenerate the class catalog.

---

## Prerequisites

- [ ] Phases 01-04 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |

> The gesture-capture feature is documented in `docs/FEATURES.md` (alongside "Edge-gesture screen capture" / "Assignable gesture actions"); extend that existing entry there for consistency, rather than splitting across `FEATURES_noLegal.md`.

---

## Steps

### Step 05.1 - Document the clipboard option in FEATURES (trilingual)

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one sentence near the existing "Assignable gesture actions" entry stating that captured screenshots can additionally be copied to the clipboard, ready to paste into other apps. Mirror the sentence across EN/RU/UK. RU/UK use `ё`/`Ё` where correct. Do not duplicate the existing gesture-capture description.

**Verification:**

- `Grep -i "clipboard"` matches the new sentence in `docs/FEATURES.md`.
- `Grep -i "буфер"` matches the new sentence in `docs/FEATURES_RU.md` and `docs/FEATURES_UK.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification PASS: "Screenshot to clipboard" bullet added to FEATURES.md/_RU/_UK after the gesture-actions entry.

---

### Step 05.2 - Record the functionality-lifecycle entry

**Files:** (writes `dev/FUNCTIONALITY.log` via script)
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/add_to_functionality_log.ps1` with an `ADD` entry describing the user-visible capability: "Gesture screenshots can be copied to the clipboard (opt-in setting)". Run this step standalone/last - the script succeeds but may leave a non-zero `$LASTEXITCODE`; re-verify the journal line was appended.

**Verification:**

- `Grep` - the new clipboard entry appears in `dev/FUNCTIONALITY.log`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - PASS via close-and-log `-FuncOp ADD`: `[2026-06-17 03:05] [S0468] [ADD] Gesture screenshots can be copied to the system clipboard via an opt-in setting`.

---

### Step 05.3 - Dev changelog for every modified file

**Files:** (writes `dev/CHANGELOG.md` via script)
**Depends on:** Step 05.2

**Prompt for developer:**

> Confirm a `dev/CHANGELOG.md` entry exists for each file changed in Phases 01-05 (add any missing via `.\scripts\add_to_dev_log.ps1`). Never hand-edit `dev/CHANGELOG.md`.

**Verification:**

- `Grep` - `ImageClipboardWriter` and `copy_screenshot_to_clipboard` each appear in `dev/CHANGELOG.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - PASS: dev logs recorded for all modified files across phases (post-change per Kotlin file + close-and-log batch of 10 for the remaining source/resource/doc files).

---

### Step 05.4 - Regenerate the class catalog

**Files:** (regenerates `dev/CATALOG/app_v2.jsonl` + `.md`)
**Depends on:** Step 05.3

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to pick up the new `ImageClipboardWriter` class. Then set its role/status via `dev/CATALOG/scripts/set.ps1` if the scan leaves them `unknown`.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "*ImageClipboardWriter*"` returns one record.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - PASS: `catalog_sync` (via close-and-log) scanned + rendered 1834 records; `ImageClipboardWriter` present (core layer); role/status set to `new` via set.ps1.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` carry the new sentence.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: insert a `Timber.d("S0468: ..")` probe at the clipboard entry point and advance to `BlockNeedUserTest` for on-device verification (paste into a third-party app).

---

## Rollback Plan

Revert phase commit(s) - documentation and catalog regeneration only; no runtime impact.
