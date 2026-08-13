# Phase 07 — Docs + Catalog Cleanup

**Strategic spec:** [`../S0067_enh-network-stale-connection-invalidation-multi-protocol.md`](../S0067_enh-network-stale-connection-invalidation-multi-protocol.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 06
**Blocks:** —
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Update trilingual `FEATURES`, regenerate catalog, finalise dev log entries, run `/spec-check` to flip status to `Verified`.

---

## Prerequisites

- [ ] Phase 06 ✅ Done.
- [ ] All previous phase Done Criteria checked.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | (n/a) |
| `docs/FEATURES_RU.md` | Modified | (n/a) |
| `docs/FEATURES_UK.md` | Modified | (n/a) |
| `dev/CATALOG/app_v2.jsonl` | Modified (regenerated) | (n/a) |
| `dev/CATALOG/app_v2.md` | Modified (regenerated) | (n/a) |

---

## Steps

### Step 07.1 — Update trilingual `FEATURES`

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a single bullet to the "Networking & cloud" / equivalent section of all three files:
>
> - EN: `- Auto-recovery for FTP / SFTP / Cloud connections after idle (token-refresh for cloud, dead-socket replacement for FTP/SFTP) — operations resume without app restart.`
> - RU: `- Авто-восстановление FTP / SFTP / облачных подключений после простоя (refresh-токена для облака, замена мёртвого сокета для FTP/SFTP) — операции продолжаются без перезапуска приложения.`
> - UK: `- Автовідновлення FTP / SFTP / хмарних з'єднань після простою (refresh-токен для хмари, заміна мертвого сокета для FTP/SFTP) — операції продовжуються без перезапуску застосунку.`

**Verification:**

- `Grep -n "Auto-recovery for FTP / SFTP / Cloud" "docs/FEATURES.md"` matches once.
- `Grep -n "Авто-восстановление FTP / SFTP" "docs/FEATURES_RU.md"` matches once.
- `Grep -n "Автовідновлення FTP / SFTP" "docs/FEATURES_UK.md"` matches once.

**Status:** `[ ]` not done

---

### Step 07.2 — Regenerate `dev/CATALOG`

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 07.1

**Prompt for developer:**

> 1. `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`
> 2. `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`
> 3. For each new class added in Phases 01–06 (`NetworkConnectionGate`, `ConnectionGateRegistry`, `ConnectionDiagnostics`, `SmbConnectionGate`, `SftpConnectionGate`, `FtpConnectionGate`, `CloudConnectionGate`, `NetworkLifecycleObserver`, `CloudTokenHandle`), set `role` and `status` via `dev/CATALOG/scripts/set.ps1` (see `dev/CATALOG/README.md`).

**Verification:**

- `Grep -n "NetworkConnectionGate" "dev/CATALOG/app_v2.jsonl"` matches at least once.
- `Grep -n "SmbConnectionGate" "dev/CATALOG/app_v2.jsonl"` matches once.
- `Grep -n "ConnectionGateRegistry" "dev/CATALOG/app_v2.jsonl"` matches once.

**Status:** `[ ]` not done

---

### Step 07.3 — Finalise dev log

**Files:** `dev/CHANGELOG.md` (via helper)
**Depends on:** Step 07.2

**Prompt for developer:**

> Run `.\scripts\add_to_dev_log.ps1` once per file modified across Phases 01–06 that does not yet have an entry. The helper appends; do not edit `dev/CHANGELOG.md` directly.
>
> Also add a summary entry: `target=PLAN/S0067_..., agent=spec-all, msg=S0067 implementation complete (gates for SMB/SFTP/FTP/Cloud + lifecycle observer + diagnostics)`.

**Verification:**

- `Grep -n "S0067 implementation complete" "dev/CHANGELOG.md"` matches once.
- `Grep -n "NetworkConnectionGate.kt" "dev/CHANGELOG.md"` matches at least once.

**Status:** `[ ]` not done

---

### Step 07.4 — Run `/spec-check`

**Files:** none modified — verification only
**Depends on:** Step 07.3

**Prompt for developer:**

> Run `/spec-check S0067`. Expected outcome: `Verified` (all 6 strategic-level criteria from §11 closed by code audit + build PASS).
>
> If `/spec-check` returns `Partial` or `Broken`: enter the audit loop (`/spec-fix S0067` → repeat — max 5 iterations).
>
> If `Verified` → strategic spec status auto-flips to `Verified`; tactical INDEX `Status:` flips to `Done`.

**Verification:**

- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0067 -Format json` returns `"status":"Verified"`.
- INDEX.md `Status:` reads `Done`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 07.*` is `[x] done`.
- [ ] All trilingual `Grep` predicates pass.
- [ ] `dev/CATALOG/app_v2.jsonl` includes all new gate classes.
- [ ] `/spec-check S0067` returns `Verified`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

This phase is documentation + catalog only. To roll back, revert `docs/FEATURES*` and re-run catalog scan/render — no code change to undo.
