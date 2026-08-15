# Phase 04 — Docs, catalog, and ticket finalization

**Strategic spec:** [`../S0228_bugfix-smb-idle-disconnect-timer-race.md`](../S0228_bugfix-smb-idle-disconnect-timer-race.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none — final phase
**Steps done:** 4 / 4
**Started:** 2026-05-16
**Completed:** 2026-05-16

---

## Objective

Run the mandatory post-change rituals after the three implementation phases are done: regenerate the class catalog, confirm dev-log completeness, append the user-visible FIX entry, and advance the ticket to `BlockNeedUserTest` with the required `S0228:` debug probes in code.

---

## Prerequisites

- [ ] Phase 01, Phase 02, and Phase 03 are all ✅ Done.
- [ ] Project compiles — the last `/build` invocation in Phase 03 returned success.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (auto) | n/a |
| `dev/CATALOG/app_v2.md` | Modified (auto) | n/a |
| `dev/FUNCTIONALITY.log` | Modified (append-only) | +1 line |
| `dev/CHANGELOG.md` | Modified (auto, audit only) | n/a |
| `PLAN/S0228_bugfix-smb-idle-disconnect-timer-race.md` | Modified | ≤ +2 lines |
| `PLAN/spec-catalog.jsonl` | Modified via CLI (status transition) | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/IdleDisconnectPolicyImpl.kt` | Modified | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt` | Modified | ≤ 1110 |

---

## Steps

### Step 04.1 — Regenerate the app catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` followed by `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`. `IdleDisconnectPolicyImpl.kt` and `SmbConnectionManager.kt` changed in earlier phases, so the catalog must be refreshed even though no new production classes are expected.

**Verification:**

- `PowerShell` — `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` exit code: expected 0 | actual: <fill>.
- `PowerShell` — `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2` exit code: expected 0 | actual: <fill>.
- `Grep` — in `dev/CATALOG/app_v2.jsonl` for `"path":"com/sza/fastmediasorter/data/network/IdleDisconnectPolicyImpl.kt"`, the `lastTouched` field equals `2026-05-16` | actual: <fill>.
- `Grep` — in `dev/CATALOG/app_v2.jsonl` for `"path":"com/sza/fastmediasorter/data/network/SmbConnectionManager.kt"`, the `lastTouched` field equals `2026-05-16` | actual: <fill>.

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 4/4 PASS. Files: `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md` regenerated. `IdleDisconnectPolicyImpl.kt` and `SmbConnectionManager.kt` both show `lastTouched = 2026-05-16`.

---

### Step 04.2 — Confirm dev-changelog completeness

**Files:** `dev/CHANGELOG.md` (read-only verification)
**Depends on:** Step 04.1

**Prompt for developer:**

> Confirm `dev/CHANGELOG.md` contains at least one entry for each file modified across Phases 01–03: `IdleDisconnectPolicyImpl.kt`, `SmbConnectionManager.kt`, `IdleDisconnectPolicyImplTest.kt`, and `SmbConnectionManagerTest.kt`. If any entry is missing, add it with `./scripts/add_to_dev_log.ps1` — never edit `dev/CHANGELOG.md` by hand.

**Verification:**

- `Grep` — `dev/CHANGELOG.md | pattern: 'IdleDisconnectPolicyImpl\.kt' | -n true | -o true` → expected: ≥ 1 match | actual: <fill>.
- `Grep` — `dev/CHANGELOG.md | pattern: 'SmbConnectionManager\.kt' | -n true | -o true` → expected: ≥ 1 match | actual: <fill>.
- `Grep` — `dev/CHANGELOG.md | pattern: 'IdleDisconnectPolicyImplTest\.kt' | -n true | -o true` → expected: ≥ 1 match | actual: <fill>.
- `Grep` — `dev/CHANGELOG.md | pattern: 'SmbConnectionManagerTest\.kt' | -n true | -o true` → expected: ≥ 1 match | actual: <fill>.

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 4/4 PASS. Files: `dev/CHANGELOG.md` audited only. Entries present for `IdleDisconnectPolicyImpl.kt`, `SmbConnectionManager.kt`, `IdleDisconnectPolicyImplTest.kt`, and `SmbConnectionManagerTest.kt`.

---

### Step 04.3 — Append the FIX entry to functionality log

**Files:** `dev/FUNCTIONALITY.log`
**Depends on:** Step 04.2

**Prompt for developer:**

> Run `./scripts/add_to_functionality_log.ps1 -Id S0228 -Op FIX -Description "SMB idle timeout now has exact-once ownership semantics — stale timer generations are dropped before callback, the SMB cleanup entrypoint is singular, and repeated timeout-fired bursts after one idle window are no longer expected"`. Public FEATURES docs stay unchanged because this is a bugfix, not a new capability.

**Verification:**

- `PowerShell` — `./scripts/add_to_functionality_log.ps1 -Id S0228 -Op FIX ...` exit code: expected 0 | actual: <fill>.
- `Grep` — `dev/FUNCTIONALITY.log | pattern: 'S0228.*FIX' | -n true` → expected: 1 match | actual: <fill>.

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 2/2 PASS. Files: `dev/FUNCTIONALITY.log` (+1 line, S0228 FIX entry appended).

---

### Step 04.4 — Advance the ticket to BlockNeedUserTest

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/IdleDisconnectPolicyImpl.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`, `PLAN/S0228_bugfix-smb-idle-disconnect-timer-race.md`, `PLAN/spec-catalog.jsonl`
**Depends on:** Step 04.3

**Prompt for developer:**

> Before the status flip, insert the two debug probes required by CLAUDE.md for `BlockNeedUserTest`: `Timber.d("S0228: latest idle timeout accepted transport=$transport")` immediately before the real latest-generation callback executes in `IdleDisconnectPolicyImpl.kt`, and `Timber.d("S0228: SMB idle timeout cleanup transport=$transportKey")` immediately before `pool.removeAndCloseAsync(key)` in `SmbConnectionManager.kt`. Then edit `PLAN/S0228_bugfix-smb-idle-disconnect-timer-race.md` so `**Status:** Tactical` becomes `**Status:** BlockNeedUserTest`, and run `pwsh -File scripts/spec_catalog/update.ps1 -Id S0228 -Status BlockNeedUserTest`. The two `S0228:` tags stay in code during device verification and are removed later by `/spec-check` on the `Verified` transition.

**Verification:**

- `Grep` — `PLAN/S0228_bugfix-smb-idle-disconnect-timer-race.md | pattern: '^\*\*Status:\*\* BlockNeedUserTest' | -n true` → expected: 1 match | actual: <fill>.
- `PowerShell` — `pwsh -File scripts/spec_catalog/select.ps1 -Id S0228 -Format json` → expected: `"status":"BlockNeedUserTest"` | actual: <fill>.
- `Grep` — across `app_v2/src/main/java/com/sza/fastmediasorter/data/network/IdleDisconnectPolicyImpl.kt` and `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt` for `pattern: 'Timber\.d\("S0228: ' | -n true | -o true` → expected: 2 matches | actual: <fill>.

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 5/5 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/data/network/IdleDisconnectPolicyImpl.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`, `PLAN/S0228_bugfix-smb-idle-disconnect-timer-race.md`, `PLAN/spec-catalog.jsonl`. Two `S0228:` debug probes inserted, strategic spec moved to `BlockNeedUserTest`, catalog select confirmed the same status, Kotlin file diagnostics clean, final `./build-debug.PS1` PASS, `scripts/spec_catalog/validate.ps1` PASS.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` reflect the 2026-05-16 edit date for both affected production files.
- [x] `dev/FUNCTIONALITY.log` has the FIX line for S0228.
- [x] `PLAN/spec-catalog.jsonl` shows `S0228` with `status: "BlockNeedUserTest"` (via `select.ps1`, never by hand).
- [x] Both `Timber.d("S0228: ...")` tags inserted in Step 04.4 are still present.

---

## Handoff Notes to Next Phase

Final phase — see `INDEX.md` Completion Gate. Next step is live SMB verification: browse or play from the same SMB share, wait at least 30 seconds, access the same share again, and confirm logcat shows no burst of repeated `IdleDisconnect: timeout fired` lines for one transport-key inside one idle window.

---

## Rollback Plan

If device verification fails, revert the Phase 01–03 code commits, restore `SmbConnectionManager.kt` from the backup if needed, and move the ticket out of `BlockNeedUserTest` with `scripts/spec_catalog/update.ps1` before re-planning.