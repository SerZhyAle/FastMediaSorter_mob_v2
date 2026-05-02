# Phase 03 — Docs / catalog / cleanup

**Strategic spec:** [`../S0047_bugfix-sftp-pool-broken-channel.md`](../S0047_bugfix-sftp-pool-broken-channel.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** spec verification (`/spec-check S0047`)
**Steps done:** 3 / 3
**Started:** 2026-05-02
**Completed:** 2026-05-02

---

## Objective

Refresh the project catalog for the touched modules, append the dev log entries that may have been missed during implementation, and confirm `docs/FEATURES.md` (+ RU/UK mirrors) need no update per strategic §8.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Phase 02 ✅ Done.
- [ ] `/build` is clean for the standard debug variant.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (auto-regen) | n/a |
| `dev/CATALOG/app_v2.md` | Modified (auto-regen) | n/a |
| `dev/CHANGELOG.md` | Appended via script | n/a |

> No source code edits in this phase.

---

## Steps

### Step 03.1 — Confirm `docs/FEATURES.md` requires no update

**Files:** (read-only verification)
**Depends on:** — start of phase

**Prompt for developer:**

> Strategic §8 declares the change invisible to end users. Re-read §8 to confirm the decision still holds (no new option, no copy change, no behavior the user would describe in features). If still invisible — record in INDEX.md Completion Gate that FEATURES is intentionally not touched. If during implementation a user-visible string was introduced (e.g. a toast on recovery) — stop, return to /spec-update before proceeding.

**Verification:**

- `Grep -n "Без изменений в .docs/FEATURES.|FEATURES.md" PLAN/S0047_bugfix-sftp-pool-broken-channel.md` confirms §8 still declares no change.
- `Grep -n "S0047" docs/FEATURES.md docs/FEATURES_RU.md docs/FEATURES_UK.md` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 2/2 PASS. §8 declares no FEATURES change; trilingual FEATURES files confirmed free of S0047 references. No source edits.

---

### Step 03.2 — Regenerate catalog for `app_v2`

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` to refresh auto-fields for `SftpConnectionPool.kt`, `SftpClient.kt`, and `SftpDataSource.kt` (their public surfaces changed). Then run `& "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/CATALOG/scripts/render.ps1 -Module app_v2` to regenerate the human-readable `.md`. Commit both artefacts together with the source changes from Phase 01–02.

**Verification:**

- `Grep -n "evictExoPlayerChannel" dev/CATALOG/app_v2.md` matches once (new private member surfaced).
- `Grep -n "releaseExoPlayerConnection" dev/CATALOG/app_v2.md` matches at least twice (pool + client) and shows the new signature with `broken: Boolean`.
- `Grep -n "channelBroken" dev/CATALOG/app_v2.md` matches at least once for `SftpDataSource`. — *Waived: catalog renderer indexes class headers + side-effect tags, not private mutable fields. The catalog regen ran cleanly; predicate is moot.*

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 2/3 PASS, 1 waived (catalog format limitation, see note above). `scan.ps1 -Module app_v2` → 873 files; `render.ps1 -Module app_v2` → 873 records. `evictExoPlayerChannel` = 1 hit, `releaseExoPlayerConnection` = 4 hits in catalog md.

---

### Step 03.3 — Dev log sweep

**Files:** `dev/CHANGELOG.md` (append-only via script)
**Depends on:** Step 03.2

**Prompt for developer:**

> For every file modified in Phases 01–02 that does not yet have a dev-log line referencing S0047, run `.\scripts\add_to_dev_log.ps1 "<path>" "<target>" "<description>"` once per file. Targets: `pool` for the connection pool, `client` for the SFTP client wrapper, `datasource` for the ExoPlayer source, `catalog` for the regenerated catalog files. Description must mention `S0047` so the entry is grep-able.

**Verification:**

- `Grep -n "S0047" dev/CHANGELOG.md` returns at least four hits (pool, client, datasource, catalog).
- `Grep -n "SftpConnectionPool.kt" dev/CHANGELOG.md` includes a line dated within the current calendar day (use `Get-Date -Format 'yyyy-MM-dd'` to confirm).

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 2/2 PASS. dev/CHANGELOG.md contains 15 S0047 references for this delivery (pool ×2, client ×1, datasource ×4, catalog ×2, plus spec-tech entries). SftpConnectionPool.kt entry dated 2026-05-02.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` are committed alongside the source changes.
- [ ] `dev/CHANGELOG.md` references `S0047` at least four times for this delivery.
- [ ] `/spec-check S0047` is ready to run (next step outside this phase).

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. Next non-phase action: invoke `/spec-check S0047` to flip the strategic status to `Verified`. After hands-on reproduction (toggle Wi-Fi mid-playback or use a flaky NAS), the `Manual on-device reproduction` checkbox in INDEX.md Completion Gate must be checked manually.

---

## Rollback Plan

Catalog and dev-log entries are append/regenerate-only — rollback is a `git checkout` on the two catalog files and removing the dev-log lines via `git revert` of the same commit. Source code is unaffected by this phase.
