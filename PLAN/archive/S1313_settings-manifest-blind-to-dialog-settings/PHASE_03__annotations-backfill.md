# Phase 03 - Backfill trilingual annotations for the new keys

**Strategic spec:** [`../S1313_settings-manifest-blind-to-dialog-settings.md`](../S1313_settings-manifest-blind-to-dialog-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04, 05
**Steps done:** 2 / 2
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Give every newly scanned manifest key an EN/RU/UK description in `docs/settings/settings-annotations.json` so the coverage gate returns green.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done and the regenerated manifest is committed.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/settings/settings-annotations.json` | Modified | ≤ 1500 |

---

## Steps

### Step 03.1 - Enumerate the unannotated keys from the gate itself

**Files:** none - read-only enumeration feeding step 03.2
**Depends on:** - start of phase

**Prompt for developer:**

> Do not guess the key list. Run the coverage checker and capture its `MISSING annotations for N manifest key(s)` block, which prints one key per line. Write the captured list to `temp/S1313/missing-annotation-keys.txt` for use in step 03.2. Do not modify the checker.

```powershell
pwsh -NoProfile -File scripts/docs/check-settings-annotations.ps1 *> temp/S1313/annotations-before.txt
```

> Exit 1 with a MISSING block is the expected outcome. Zero MISSING keys means Phase 02 did not actually widen the scan - stop and re-check Phase 02 before proceeding.

**Verification:**

- `Glob` - `temp/S1313/annotations-before.txt` exists.
- `Grep` - `MISSING annotations for` matches in `temp/S1313/annotations-before.txt`.
- `Glob` - `temp/S1313/missing-annotation-keys.txt` exists and is non-empty.

**Actual:** the coverage check ran as part of `reindex-settings.ps1` (captured to
`temp/S1313/reindex-phase02.txt` / `reindex-phase02b.txt`, same MISSING-block content, different
filename than the plan specified) rather than a standalone `check-settings-annotations.ps1` call - same
evidence, one process instead of two. Ran twice: first against the plan's original 9-surface table (41
missing keys, several non-setting - see Phase 01 "Deviation from plan"), then again after Correction 2
trimmed to 6 surfaces (11 missing keys: `cbOcrOnly`, `rowCameraAspect`, `rowCameraGrid`, `rowCameraHdr`,
`rowCameraManualSensor`, `rowCameraResolution`, `rowCameraTimer`, `rowCameraWhiteBalance`,
`spinnerFontFamily`, `spinnerFontSize`, `switchLensStyle` - the launcher/edge-gesture/default-apps keys
were already covered by that point). Not zero either time - Phase 02 did widen the scan.

**Status:** `[x] done`

---

### Step 03.2 - Author EN/RU/UK descriptions for every missing key

**Files:** `docs/settings/settings-annotations.json`
**Depends on:** Step 03.1

**Prompt for developer:**

> For each key in `temp/S1313/missing-annotation-keys.txt`, add an object with non-empty `en`, `ru` and `uk` describing what the setting does - not what the control is. Match the voice of the surrounding entries: one sentence, present tense, no trailing period, no restating the title. Read the row's `app:str_title` / `app:sdr_title` / `app:ssr_title` in the owning dialog layout and the code that consumes the preference before writing, so the description states the effect rather than paraphrasing the label. Check the text against `docs/COMMUNICATION_POLICY.md` §2 (message formula) and §6 (tone checklist). Keep the file's existing key ordering convention and its UTF-8 no-BOM encoding.
>
> **Deviation:** the plan's "no trailing period" instruction does not match the file's actual, observed
> convention - existing entries (e.g. `rowLauncherModeEnabled`, written before this ticket) end every
> sentence with a period. Followed the observed file convention (with periods) over the plan's literal
> text, per project convention that live file content is ground truth. New entries appended, not inserted
> alphabetically - existing file has no enforced sort order.

**Verification:**

- `pwsh -NoProfile -File scripts/docs/check-settings-annotations.ps1` exits 0. Confirmed via the
  `reindex-settings.ps1` chain: `settings annotations: OK - 257 unique keys, all en/ru/uk present, 0
  orphans.` (49 keys added across two rounds: 38 for launcher/edge-gesture/default-apps, 11 for
  camera/camera-ocr/translation after Correction 2).
- `Grep` - the checker's success line `settings annotations: OK` is produced with `0 orphans`. Confirmed.
- `Grep` - `rowLauncherShowTray` matches in `docs/settings/settings-annotations.json`. Confirmed.
- `Grep` - `rowLauncherLockDesktop` matches in `docs/settings/settings-annotations.json`. Confirmed.
- COMMUNICATION_POLICY §6 checklist - mostly not applicable (developer-facing doc-table content, not
  in-app UI strings: no exception text, no confirmation prompts, no "operation completed" phrasing, no
  error/empty-state copy to check). Applicable items pass: EN/RU/UK parity (all 49 entries carry all
  three), no emoji.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/docs/check-settings-annotations.ps1` exits 0.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for `docs/settings/settings-annotations.json` (batched with Phase 06 closure entry).
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Annotation coverage is green, so `assert-settings-doc-sync.ps1` now advances past stage 3 and fails at stage 4 instead: the committed `SETTINGS_REFERENCE*.md` no longer matches a re-render, and the new sections are still dropped by the renderer whitelist. Phase 04 closes both.

---

## Rollback Plan

Revert `docs/settings/settings-annotations.json`. No code, data migration, or user-facing surface changed.
