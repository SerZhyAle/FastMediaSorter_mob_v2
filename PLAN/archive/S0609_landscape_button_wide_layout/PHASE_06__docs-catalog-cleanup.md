# Phase 06 - Docs / catalog cleanup

**Strategic spec:** [`../S0609_landscape_button_wide_layout.md`](../S0609_landscape_button_wide_layout.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phases 01-05
**Blocks:** none - final phase
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Close out the spec: dev-log batch, settings-doc-sync gate, and final neuroslop/quality gate over the touched layout files. No FEATURES change (strategic §8 = "Без изменений"). No catalog regen expected (XML-only, no new classes).

---

## Prerequisites

- [ ] Phases 01-05 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CHANGELOG.md` | Modified (via script) | - |
| `docs/settings/settings-manifest.json` | Modified ONLY if gate flags | - |

---

## Steps

### Step 06.1 - Batch dev-log all touched layout files

**Files:** `dev/CHANGELOG.md` (via `add_to_dev_log.ps1` / `close-and-log.ps1 -DevLogs`)
**Depends on:** - start of phase

**Prompt for developer:**

> Record one logical dev-log entry for S0609 covering all landscape layout files touched across phases 01-05 (images, video, audio, general, playback, destinations land + new documents/streams land). Use `close-and-log.ps1 -DevLogs @(...)` (in-process) or one summary entry per CLAUDE.md journaling-granularity rule.

**Verification:**

- `Grep` - `dev/CHANGELOG.md` contains a recent entry mentioning S0609 / settings landscape.

**Status:** `[ ]` not done

---

### Step 06.2 - Settings-doc-sync gate

**Files:** `docs/settings/settings-manifest.json` (only if gate flags)
**Depends on:** Step 06.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1`. The change is geometry-only (no setting added, removed, renamed, or moved in logical order/section), so the gate should pass without regeneration. If it flags (e.g. it treats landscape regrouping as a position change), regenerate the manifest + reference + annotations per CLAUDE.md Rule 22; otherwise no doc change.

**Verification:**

- `Bash` - `assert-settings-doc-sync.ps1` exits 0.

**Status:** `[ ]` not done

---

### Step 06.3 - Final quality gate over touched layouts

**Files:** all S0609 landscape layout files
**Depends on:** Step 06.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/post-change.ps1 -File "app_v2/src/main/res/layout-land/fragment_settings_general.xml" -Target "settings landscape multi-column" -Description "S0609 landscape multi-column settings" -ChangeType Xml -Module app_v2` (or run the neuroslop gate directly across the touched files). Confirm: no hardcoded hex in any touched `layout-land/*` file, landscape parity satisfied, no portrait file modified.

**Verification:**

- `Bash` - `scripts/quality/assert-neuroslop.ps1` exits 0 for the touched files.
- `.\a.ps1 fr` passes (final resource/manifest check).
- `Grep -n "=\"#"` across all touched `layout-land/fragment_settings_*.xml` returns zero.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` is `[x] done`.
- [ ] `dev/CHANGELOG.md` updated.
- [ ] Settings-doc-sync gate green.
- [ ] No `docs/FEATURES*.md` change (§8 = Без изменений).
- [ ] Ready for `/spec-check S0609`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: device verification of each fragment in landscape (narrow phone + wide) and EN/RU/UK long-label pass, then `/spec-check S0609`.

---

## Rollback Plan

Doc/log-only phase - revert the dev-log/manifest commit if needed.
