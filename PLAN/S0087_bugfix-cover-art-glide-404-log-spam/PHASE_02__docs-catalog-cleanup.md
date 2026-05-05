# Phase 02 — Docs & Catalog Cleanup

**Strategic spec:** [`../S0087_bugfix-cover-art-glide-404-log-spam.md`](../S0087_bugfix-cover-art-glide-404-log-spam.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** —
**Steps done:** 0 / 2
**Started:** —
**Completed:** —

---

## Objective

Regenerate the module catalog and record dev log entries; no FEATURES update needed (internal fix only).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Project compiles cleanly.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (regen) | — |
| `dev/CATALOG/app_v2.md` | Modified (regen) | — |

---

## Steps

### Step 02.1 — Regenerate app_v2 catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` exists and has modification time within this session.
- `Grep` — `AudioCoverArtLoader` returns at least one hit in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[ ]` not done

---

### Step 02.2 — Record dev log entries

**Files:** *(dev log only)*
**Depends on:** Step 02.1

**Prompt for developer:**

> Run:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioCoverArtLoader.kt" "S0087" "Replace Timber.w with 404-aware branch in onLoadFailed"
> ```
>
> *(If this was not already done in Phase 01's Done Criteria — add it now. If already done, skip.)*

**Verification:**

- `Grep` — `S0087` returns at least one hit in `dev/CHANGELOG.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Both steps above are `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] Dev log entry present for `AudioCoverArtLoader.kt`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) — catalog regen only, no functional change.
