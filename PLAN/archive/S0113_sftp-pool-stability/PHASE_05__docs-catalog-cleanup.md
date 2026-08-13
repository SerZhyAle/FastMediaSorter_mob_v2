# Phase 05 — Docs and Catalog Cleanup

**Strategic spec:** [`../S0113_sftp-pool-stability.md`](../S0113_sftp-pool-stability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04 (or Phase 04 ⏭️ Skipped)
**Blocks:** —
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Regenerate the class catalog, confirm no FEATURES doc change is needed, run the final dev log entries, and remove all `Timber.d("S0113:` debug tags in preparation for `Verified` status.

---

## Prerequisites

- [ ] Phases 01, 02, 03 are ✅ Done.
- [ ] Phase 04 is ✅ Done or explicitly ⏭️ Skipped (research deferred).
- [ ] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (regenerated) | — |
| `dev/CATALOG/app_v2.md` | Modified (regenerated) | — |
| All `.kt` files touched in Phases 01–04 | Modified (tag removal) | — |

---

## Steps

### Step 05.1 — Remove all S0113 Timber debug tags

**Files:** All `.kt` files touched in Phases 01–04
**Depends on:** — start of phase

**Prompt for developer:**

> Run `Grep -r "S0113:" --include="*.kt"` across the project. For each matching file, remove every line containing `Timber.d("S0113:`. Do not remove any other Timber calls. Commit the removals.

**Verification:**

- `Grep` — `S0113:` in `*.kt` files returns zero hits.

**Status:** `[ ]` not done

---

### Step 05.2 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run:
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
> Then run `set.ps1` for `NetworkDownloadDeduplicator` (new class) to fill `role` and `status`:
> ```powershell
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class NetworkDownloadDeduplicator -Role "utility" -Status "active"
> ```

**Verification:**

- `Grep` — `NetworkDownloadDeduplicator` appears in `dev/CATALOG/app_v2.md`.
- `Glob` — `dev/CATALOG/app_v2.jsonl` modified timestamp is today.

**Status:** `[ ]` not done

---

### Step 05.3 — Final dev log and spec status

**Files:** `dev/CHANGELOG.md` (via script), spec journal
**Depends on:** Step 05.2

**Prompt for developer:**

> Run dev log for each file modified during all phases:
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/.../SftpConnectionPool.kt" "S0113" "Phase 01+04: active stream guard + unified session"
> .\scripts\add_to_dev_log.ps1 "app_v2/.../NetworkDownloadDeduplicator.kt" "S0113" "Phase 02: new download dedup component"
> .\scripts\add_to_dev_log.ps1 "app_v2/.../NetworkFileDownloader.kt" "S0113" "Phase 02: wire deduplicator"
> .\scripts\add_to_dev_log.ps1 "app_v2/.../SftpDataSource.kt" "S0113" "Phase 03: JSchException retry on open()"
> .\scripts\add_to_dev_log.ps1 "app_v2/.../VideoPlayerManager.kt" "S0113" "Phase 03: suppress recoverable SFTP IO toast"
> ```
> Then run `/spec-check S0113` to trigger the audit and advance to `Verified`.

**Verification:**

- `Grep` — `S0113` appears in `dev/CHANGELOG.md`.
- `Grep` — `S0113:` in `*.kt` returns zero hits (verified by Step 05.1 already, confirm again).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `Grep` for `S0113:` in `*.kt` returns zero hits.
- [ ] `dev/CATALOG/app_v2.md` includes `NetworkDownloadDeduplicator`.
- [ ] `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` unchanged (confirmed per strategic §8).
- [ ] `/spec-check S0113` returns `Verified`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

No rollback needed for this phase — only catalog regeneration and tag removal. Revert individual `.kt` files if Step 05.1 accidentally removes non-S0113 lines.
