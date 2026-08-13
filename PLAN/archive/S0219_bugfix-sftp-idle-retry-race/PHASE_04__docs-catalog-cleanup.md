# Phase 04 — Docs, catalog, and ticket finalization

**Strategic spec:** [`../S0219_bugfix-sftp-idle-retry-race.md`](../S0219_bugfix-sftp-idle-retry-race.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none — final phase
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Run the mandatory post-change rituals once all three implementation phases are done: regenerate the class catalog for `app_v2`, append a FIX entry to the functionality log, confirm dev-changelog completeness, and advance the spec catalog to `BlockNeedUserTest`. No FEATURES update is performed — strategic §8 declares "Без изменений", and S0219 is a bugfix not a new capability.

---

## Prerequisites

- [ ] Phase 01, Phase 02, Phase 03 are all ✅ Done.
- [ ] Project compiles — last `/build` invocation in Phase 03 returned success.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (auto) | n/a |
| `dev/CATALOG/app_v2.md` | Modified (auto) | n/a |
| `dev/FUNCTIONALITY.log` | Modified (append-only) | +1 line |
| `dev/CHANGELOG.md` | Modified (auto, audit only) | n/a |
| `PLAN/spec-catalog.jsonl` | Modified via CLI (status transition) | n/a |

---

## Steps

### Step 04.1 — Regenerate app_v2 catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` followed by `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`. Both files (`SftpClient.kt`, `SftpConnectionPool.kt`) were modified in Phases 01–03; the catalog's `loc` and `last` fields will be refreshed automatically. No new classes were introduced, so `role`/`status` fields do not need manual updates via `set.ps1`.

**Verification:**

- `Bash` — `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` exit code: expected 0 | actual: <fill>.
- `Bash` — `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2` exit code: expected 0 | actual: <fill>.
- `Grep` — in `dev/CATALOG/app_v2.jsonl` for `"path":"com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt"`, the `last` field equals `2026-05-16` | actual: <fill>.
- `Grep` — in `dev/CATALOG/app_v2.jsonl` for `"path":"com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt"`, the `last` field equals `2026-05-16` | actual: <fill>.

**Status:** `[ ]` not done

---

### Step 04.2 — Confirm dev-changelog completeness

**Files:** `dev/CHANGELOG.md` (read-only verification)
**Depends on:** Step 04.1

**Prompt for developer:**

> Confirm `dev/CHANGELOG.md` carries one entry per modified file across Phases 01–03: at least one entry mentioning `SftpClient.kt`, at least one mentioning `SftpConnectionPool.kt`. If any are missing, append them with `.\scripts\add_to_dev_log.ps1 "<path>" "<target>" "<description>"` — never edit `dev/CHANGELOG.md` by hand. Each phase should already have added its own entry; this step is a defensive audit, not the primary record.

**Verification:**

- `Grep` — `dev/CHANGELOG.md | pattern: 'SftpClient\.kt' | -n true | -o true` → expected: ≥ 2 matches (Phase 01 + Phase 03 each recorded a touch) | actual: <fill>.
- `Grep` — `dev/CHANGELOG.md | pattern: 'SftpConnectionPool\.kt' | -n true | -o true` → expected: ≥ 1 match (Phase 02) | actual: <fill>.

**Status:** `[ ]` not done

---

### Step 04.3 — Append FIX entry to functionality log

**Files:** `dev/FUNCTIONALITY.log`
**Depends on:** Step 04.2

**Prompt for developer:**

> Run `.\scripts\add_to_functionality_log.ps1 -Id S0219 -Op FIX -Description "SFTP browse no longer fails with 'inputstream is closed' after the idle timeout window — pool retry path now reachable from every wrapper, idle-disconnect callback never tears down a session mid-borrow, idle timer rearms on every non-cancellation completion"`. This is the user-visible behaviour-change record per CLAUDE.md §3 of Post-Change Steps; the public FEATURES catalogue stays untouched because no new capability is introduced.

**Verification:**

- `Bash` — `.\scripts\add_to_functionality_log.ps1 -Id S0219 -Op FIX ...` exit code: expected 0 | actual: <fill>.
- `Grep` — `dev/FUNCTIONALITY.log | pattern: 'S0219.*FIX' | -n true` → expected: 1 match | actual: <fill>.

**Status:** `[ ]` not done

---

### Step 04.4 — Advance spec status to BlockNeedUserTest

**Files:** `PLAN/spec-catalog.jsonl` (via CLI), `PLAN/S0219_bugfix-sftp-idle-retry-race.md` (status line edit)
**Depends on:** Step 04.3

**Prompt for developer:**

> Update the spec status in two places:
>
> 1. Edit `PLAN/S0219_bugfix-sftp-idle-retry-race.md`: change `**Status:** Tactical` (set by `/spec-tech` at end of run) to `**Status:** BlockNeedUserTest`.
> 2. Run `pwsh -File scripts/spec_catalog/update.ps1 -Id S0219 -Status BlockNeedUserTest`.
>
> The `Timber.d("S0219: ...")` tags inserted in Phases 01–03 stay in code while the ticket is in `BlockNeedUserTest` — they are the operator's logcat probes for the round of device testing. They will be removed by `/spec-check` on transition to `Verified` (or by `/spec-update` on re-open).
>
> Final chat output for this phase: `S0219 complete — pending device test. Status: BlockNeedUserTest. Debug tags: <count>.`

**Verification:**

- `Grep` — `PLAN/S0219_bugfix-sftp-idle-retry-race.md | pattern: '^\*\*Status:\*\* BlockNeedUserTest' | -n true` → expected: 1 match | actual: <fill>.
- `Bash` — `pwsh -File scripts/spec_catalog/select.ps1 -Id S0219 -Format json` → expected: `"status":"BlockNeedUserTest"` | actual: <fill>.
- `Grep` — across `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/` for `pattern: 'Timber\.d\("S0219: ' | -n true | -o true` → expected: ≥ 9 matches (one per wrapper unwrapped in Phase 01 + Phase 02 borrow/invalidate tags + Phase 03 enter tags) | actual: <fill>.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` reflect the May 2026 edit timestamps for both affected files.
- [ ] `dev/FUNCTIONALITY.log` has the FIX line for S0219.
- [ ] `PLAN/spec-catalog.jsonl` shows `S0219` with `status: "BlockNeedUserTest"` (via `select.ps1`, never by hand).
- [ ] Every `Timber.d("S0219: ...")` tag inserted in Phases 01–03 is still present (their removal is owned by `/spec-check`, not this phase).

---

## Handoff Notes to Next Phase

Final phase — see [`INDEX.md`](INDEX.md) Completion Gate. Next step is hands-on device verification by the operator: open an SFTP folder, leave the app idle for ≥ 30 seconds, open the same or a neighbouring folder, and confirm the user-visible toast «Не получилось открыть папку» does not appear; logcat shows `S0219:` probes firing on the expected paths but no `inputstream is closed` from `_stat`.

---

## Rollback Plan

If verification fails on device:

- Roll back code via the per-phase snapshots in `temp/` (Phase 03 backup is the latest of `SftpClient.kt`; Phase 02 backup is the latest of `SftpConnectionPool.kt`).
- Run `pwsh -File scripts/spec_catalog/update.ps1 -Id S0219 -Status Broken` and let `/spec-fix` (or a follow-up `/spec-update`) re-plan.
- The dev log and functionality log entries can stay — they accurately record what was attempted; the FIX entry will be balanced by a follow-up CHANGE entry on the next pass.
