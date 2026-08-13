# Phase 06 — Docs, Catalog, Cleanup

**Strategic spec:** [`../S0061_bugfix-smb-stale-connection-invalidation.md`](../S0061_bugfix-smb-stale-connection-invalidation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** all prior phases
**Blocks:** —
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Update user-facing feature docs (trilingual), regenerate the `dev/CATALOG/app_v2` files for the modified network area, and run final dev-log entries. No source-code changes here.

---

## Prerequisites

- [ ] Phases 01..05 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `dev/CATALOG/app_v2.md` | Regenerated | n/a |

---

## Steps

### Step 06.1 — Update `docs/FEATURES.md` + RU + UK

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a single bullet to the SMB / Network reliability section of each feature doc. Use the trilingual style of the file:
>
> - EN: `SMB connection auto-recovers after server-side idle disconnect — file copy and playback continue without app restart.`
> - RU: `SMB-соединение автоматически восстанавливается после простоя — копирование и воспроизведение продолжают работать без перезапуска приложения.`
> - UK: `SMB-з’єднання автоматично відновлюється після простою — копіювання та відтворення продовжують працювати без перезапуску застосунку.`
>
> Use `..` (two dots), not `...`. Use `ё`/`Ё` where appropriate in RU.

**Verification:**

- `Grep` — `SMB connection auto-recovers` matches exactly once in `docs/FEATURES.md`.
- `Grep` — `SMB-соединение автоматически восстанавливается` matches exactly once in `docs/FEATURES_RU.md`.
- `Grep` — `SMB-з’єднання автоматично відновлюється` matches exactly once in `docs/FEATURES_UK.md`.

**Status:** `[ ]` not done

---

### Step 06.2 — Regenerate catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run from project root:
>
> ```powershell
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> The scan refreshes auto-fields (paths, signatures, line counts) without overwriting manually-curated `role`/`status`/`description`. The render regenerates the human-readable `.md`. For new classes added in Phases 01–04 (`SmbConnectionPool`, `SmbConnectionHealthProbe`, `SmbBackgroundLifecycleManager`), use `dev/CATALOG/scripts/set.ps1` to fill `role` and `status` fields.

**Verification:**

- `Grep` — `SmbConnectionPool` matches in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `SmbConnectionHealthProbe` matches in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `SmbBackgroundLifecycleManager` matches in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `SmbConnectionPool` matches in `dev/CATALOG/app_v2.md` (rendered).

**Status:** `[ ]` not done

---

### Step 06.3 — Final dev-log entries

**Files:** `dev/CHANGELOG.md` (via script — never edit directly)
**Depends on:** Step 06.1, Step 06.2

**Prompt for developer:**

> Run a dev-log entry for each high-level deliverable:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "feature-doc" "S0061: SMB auto-recovery after idle"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "feature-doc" "S0061: SMB auto-recovery after idle (RU)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "feature-doc" "S0061: SMB auto-recovery after idle (UK)"
> .\scripts\add_to_dev_log.ps1 "dev/CATALOG/app_v2.jsonl" "catalog" "S0061: regenerate after SMB pool extraction"
> .\scripts\add_to_dev_log.ps1 "dev/CATALOG/app_v2.md" "catalog" "S0061: regenerate after SMB pool extraction"
> ```
>
> Phase-by-phase code-change entries should already exist (each phase wrote one per file). This step adds only the final-tier deliverable entries.

**Verification:**

- `Grep` — `S0061: SMB auto-recovery after idle` matches in `dev/CHANGELOG.md` at least three times (EN/RU/UK).
- `Grep` — `S0061: regenerate after SMB pool extraction` matches in `dev/CHANGELOG.md` at least twice.

**Status:** `[ ]` not done

---

### Step 06.4 — Final build gate (full sweep)

**Files:** none
**Depends on:** Step 06.3

**Prompt for developer:**

> Run `/build` → standard debug as a final sanity check. The cleanup phase should not have changed any compilable artifact, so this is a no-op build, but it confirms nothing else regressed.

**Verification:**

- `/build` standard debug returns PASS.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `/build` standard debug PASS.
- [ ] All trilingual feature docs reflect the new auto-recovery behavior.
- [ ] Catalog regenerated and includes the three new classes.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. Next: `/spec-check S0061`.

---

## Rollback Plan

Docs/catalog are reversible by `git checkout`. No code changes here.
