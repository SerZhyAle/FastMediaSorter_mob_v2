# Phase 02 — Docs & Catalog Cleanup

**Strategic spec:** [`../S0084_bugfix-cache-subfolder-mismatch-restore.md`](../S0084_bugfix-cache-subfolder-mismatch-restore.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** —
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Update trilingual feature docs, regenerate the module catalog, and add dev-log entries for all files touched in this spec.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ 2000 |
| `docs/FEATURES_RU.md` | Modified | ≤ 2000 |
| `docs/FEATURES_UK.md` | Modified | ≤ 2000 |
| `dev/CATALOG/app_v2.jsonl` | Modified (regen) | — |
| `dev/CATALOG/app_v2.md` | Modified (regen) | — |

---

## Steps

### Step 02.1 — Update FEATURES.md (EN)

**Files:** `docs/FEATURES.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In `docs/FEATURES.md` §1 "Resource / Source Management", find the bullet starting with **"Last browse position save & restore"**. Append the following clause to the existing sentence (after the period, before any further text on the same bullet):
>
> `When the last-played file was inside a subfolder, the app opens directly in that subfolder on resume, without reloading the parent directory first.`

**Verification:**

- `Grep` — `without reloading the parent directory first` returns exactly **one** hit in `docs/FEATURES.md`.

**Status:** `[ ]` not done

---

### Step 02.2 — Update FEATURES_RU.md

**Files:** `docs/FEATURES_RU.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `docs/FEATURES_RU.md` §1, find the bullet corresponding to "Last browse position save & restore". Append a corresponding Russian clause to that bullet:
>
> `Если последний воспроизведённый файл находился в подпапке, приложение открывается сразу в этой подпапке при восстановлении, не перезагружая родительскую директорию.`

**Verification:**

- `Grep` — `не перезагружая родительскую директорию` returns exactly **one** hit in `docs/FEATURES_RU.md`.

**Status:** `[ ]` not done

---

### Step 02.3 — Update FEATURES_UK.md

**Files:** `docs/FEATURES_UK.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `docs/FEATURES_UK.md` §1, find the bullet corresponding to "Last browse position save & restore". Append a corresponding Ukrainian clause to that bullet:
>
> `Якщо останній відтворений файл знаходився у підпапці, застосунок відкривається безпосередньо в ній під час відновлення, без перезавантаження батьківської директорії.`

**Verification:**

- `Grep` — `без перезавантаження батьківської директорії` returns exactly **one** hit in `docs/FEATURES_UK.md`.

**Status:** `[ ]` not done

---

### Step 02.4 — Regenerate catalog and record dev log

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Run the following commands in sequence:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt" "S0084" "Fix cold-start cache miss log (subfolder mismatch false positive)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "S0084" "Update last-position-restore bullet — subfolder open behaviour"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "S0084" "Update last-position-restore bullet — subfolder open behaviour (RU)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "S0084" "Update last-position-restore bullet — subfolder open behaviour (UK)"
> ```

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` exists and has modification time ≥ start of this phase.
- `Grep` — `PlayerMediaFilesLoader` returns at least one hit in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] All three FEATURES files contain the new clause (EN/RU/UK verified above).
- [ ] `dev/CATALOG/app_v2.jsonl` and `app_v2.md` regenerated.
- [ ] Dev log entries recorded for all modified files.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
