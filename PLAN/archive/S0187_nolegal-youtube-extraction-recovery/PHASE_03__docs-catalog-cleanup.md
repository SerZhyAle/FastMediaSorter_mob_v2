# Phase 03 — Docs and Catalog Cleanup

**Strategic spec:** [`../S0187_nolegal-youtube-extraction-recovery.md`](../S0187_nolegal-youtube-extraction-recovery.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** —
**Steps done:** 2 / 2
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Update `docs/FEATURES_noLegal*.md` with the YouTube recovery capability (as mandated by strategic §8), regenerate the class catalog, and record dev log entries for all modified files.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Phase 02 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES_noLegal.md` | Modified | existing |
| `docs/FEATURES_noLegal_RU.md` | Modified | existing |
| `docs/FEATURES_noLegal_UK.md` | Modified | existing |

---

## Steps

### Step 03.1 — Update FEATURES_noLegal trilingual docs

**Files:** `docs/FEATURES_noLegal.md`, `docs/FEATURES_noLegal_RU.md`, `docs/FEATURES_noLegal_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In each of the three FEATURES_noLegal files, append a bullet to the existing features list describing the
> YouTube recovery capability. Use the exact wording from strategic spec §8 for each language:
>
> **EN** (`docs/FEATURES_noLegal.md`):
> `- noLegal: reliable YouTube and YouTube Music share downloads — NewPipe and yt-dlp work together, format mismatches no longer break the share.`
>
> **RU** (`docs/FEATURES_noLegal_RU.md`):
> `- noLegal: надёжная загрузка YouTube и YouTube Music через share — NewPipe и yt-dlp работают вместе, несовпадение форматов больше не ломает обмен.`
>
> **UK** (`docs/FEATURES_noLegal_UK.md`):
> `- noLegal: надійне завантаження YouTube і YouTube Music через share — NewPipe і yt-dlp працюють разом, невідповідність форматів більше не ламає обмін.`
>
> Verify the COMMUNICATION_POLICY.md §6 tone checklist: the strings are factual feature descriptions,
> not UI messages — exempted from formula requirements. No user-action strings changed.

**Verification:**

- `Grep` — `NewPipe and yt-dlp work together` matches exactly once in `docs/FEATURES_noLegal.md`.
- `Grep` — `NewPipe и yt-dlp работают вместе` matches exactly once in `docs/FEATURES_noLegal_RU.md`.
- `Grep` — `NewPipe і yt-dlp працюють разом` matches exactly once in `docs/FEATURES_noLegal_UK.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS. Files: FEATURES_noLegal*.md. Dev log recorded.

---

### Step 03.2 — Catalog regen and dev log for all modified files

**Files:** _(catalog artifacts and dev log — no source change)_
**Depends on:** Step 03.1

**Prompt for developer:**

> Run catalog scan and render for the `app_v2` module:
> ```powershell
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> Then add dev log entries for every file modified across all three phases:
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/YtDlpExtractionStrategy.kt" "S0187" "Return NotFound for yt-dlp format-unavailable errors so cascade reaches NewPipe"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/KnownAuthResources.kt" "S0187" "Add youtube.com to known auth resources"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/test/java/com/sza/fastmediasorter/data/link/auth/KnownAuthResourcesTest.kt" "S0187" "Add YouTube host matching tests"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_noLegal.md" "S0187" "Add YouTube recovery feature bullet"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_noLegal_RU.md" "S0187" "Add YouTube recovery feature bullet (RU)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_noLegal_UK.md" "S0187" "Add YouTube recovery feature bullet (UK)"
> ```

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` exists and has modification time ≥ start of this session.
- `Glob` — `dev/CATALOG/app_v2.md` exists and has modification time ≥ start of this session.

**Status:** `[x]` done

**Step Log:**

- 2026-05-14 — Verification 2/2 PASS. Catalog: 1042 records scanned+rendered. All 6 dev log entries recorded.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] `dev/CHANGELOG.md` has entries for all six files listed in Step 03.2.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert bullet additions to FEATURES_noLegal files. No code change in this phase.
