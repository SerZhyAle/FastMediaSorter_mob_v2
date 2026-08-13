# Phase 07 — Docs, Catalog, Changelog

**Strategic spec:** [`../S0154_player-file-operation-queue.md`](../S0154_player-file-operation-queue.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** all prior phases
**Blocks:** —
**Steps done:** 3 / 3
**Started:** 2026-05-11
**Completed:** —

---

## Objective

Land the user-facing documentation, regenerate the class catalog, and ensure the dev changelog covers every modified file. No production code changes.

---

## Prerequisites

- [ ] Phases 01–06 ✅ Done (06.4 may be `⏭️ Skipped`).
- [ ] Working tree builds — `/build` green.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | — |
| `docs/FEATURES_RU.md` | Modified | — |
| `docs/FEATURES_UK.md` | Modified | — |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | — |
| `dev/CATALOG/app_v2.md` | Regenerated | — |
| `dev/CHANGELOG.md` | Appended (via script) | — |

---

## Steps

### Step 07.1 — Update FEATURES (EN/RU/UK) via `/doc-update`

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Use `/doc-update`. Add one concise bullet to the player section of all three FEATURES mirrors per strategic §8: move / delete / rename of the current file no longer makes the user wait — the action is accepted immediately, the player advances to the next file (or stays put for rename), and operations run sequentially in the background; the move / delete / rename buttons work back-to-back without sticking. Mention the optional "N in queue" indicator only if Phase 06 Step 06.4 was implemented. Apply Author Style (`..`, `ё`/`Ё`).

**Verification:**

- `Grep` — a new bullet referencing the background operation queue / "не нужно ждать" exists in `docs/FEATURES_RU.md` and `docs/FEATURES_UK.md`, and the EN equivalent in `docs/FEATURES.md`.

**Status:** `[x]` done

---

### Step 07.2 — Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 07.1

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` then `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`. Confirm the new `ui/player/fileops/` classes (`PlayerFileOperation`, `PlayerFileOperationQueue`, `PlayerFileOperationEvent`, and `NetworkAwareFiles` if extracted) appear with a meaningful `role` and `status` — set via `pwsh -File dev/CATALOG/scripts/set.ps1 ...` if `scan` left them blank.

**Verification:**

- `Grep` — `PlayerFileOperationQueue` appears in `dev/CATALOG/app_v2.jsonl` with a non-empty `role`.
- `Grep` — `PlayerFileOperation` appears in `dev/CATALOG/app_v2.md`.

**Status:** `[x]` done

---

### Step 07.3 — Dev changelog sweep

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 07.2

**Prompt for developer:**

> For every file modified across Phases 01–06 that does not yet have a `dev/CHANGELOG.md` entry, add one via `.\scripts\add_to_dev_log.ps1 "<path>" "spec-dev" "<S0154 description>"`. Do not edit `dev/CHANGELOG.md` by hand. Then run `pwsh -File scripts/spec_catalog/update.ps1 -Id S0154 -Status BlockNeedUserTest` (the implementation chain owns this transition; `/spec-dev` / `/spec-all` normally do it — only run it manually if no skill is in flight) and ensure the Debug Verification Tags for S0154 are present in the changed flow entry points per CLAUDE.md.

**Verification:**

- `Grep` — `S0154` appears in `dev/CHANGELOG.md` for the new `fileops` files and for `FileOperationsHandler.kt` / `PlayerManagerInitializer.kt`.
- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0154 -Format json` reports `status` `BlockNeedUserTest` (after the implementation chain runs).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 07.*` above is `[x] done`.
- [x] `docs/FEATURES.md` + `_RU` + `_UK` updated.
- [x] `dev/CATALOG/app_v2.jsonl` + `.md` regenerated and committed alongside the code.
- [x] `dev/CHANGELOG.md` covers every modified file.
- [ ] INDEX.md Completion Gate checklist all ticked except `/spec-check` (run after this phase).

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. Next action is `/spec-check S0154` after on-device verification clears `BlockNeedUserTest`.

---

## Rollback Plan

Docs/catalog/changelog only — revert the doc commit; no functional impact.
