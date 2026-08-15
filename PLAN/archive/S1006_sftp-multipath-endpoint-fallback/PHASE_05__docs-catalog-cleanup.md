# Phase 05 - Docs & catalog cleanup

**Strategic spec:** [`../S1006_sftp-multipath-endpoint-fallback.md`](../S1006_sftp-multipath-endpoint-fallback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phases 01-04
**Blocks:** none
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Regenerate the class catalog, record dev-log entries, and move the ticket to `BlockNeedUserTest` for on-device verification.

---

## Prerequisites

- [ ] Phases 01-04 ✅ Done.
- [ ] Project builds green.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` + `.md` | Regenerated | - |
| `dev/CHANGELOG.md` (via script) | Appended | - |

> No `docs/FEATURES*` edit - strategic §8 states this improves an already-shipped capability; the showcase is `/skill-release`-owned.

---

## Steps

### Step 05.1 - Set role/status on the new class and regenerate the catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`. Set role + status for the new `SftpEndpointResolver` via `dev/CATALOG/scripts/set.ps1` (data-layer, active). Confirm the new `Migration38To39` is indexed.

**Verification:**

- `Grep` - `SftpEndpointResolver` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` - `Migration38To39` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[ ]` not done

---

### Step 05.2 - Dev-log entry for the ticket

**Files:** `dev/CHANGELOG.md` (via `add_to_dev_log.ps1`)
**Depends on:** Step 05.1

**Prompt for developer:**

> One logical dev-log entry (batch the changed files via `close-and-log.ps1 -DevLogs`) describing S1006: companion SFTP resources resolve the reachable endpoint (LAN at home, WAN in transit) from one import. Record the capability in `docs/ALL_FEATURES.jsonl` via `scripts/all_features/add.ps1` (EN-only) as an enhancement of companion import.

**Verification:**

- `Grep` - an S1006 entry exists in `dev/CHANGELOG.md`.
- `Grep` - a companion multi-path record exists in `docs/ALL_FEATURES.jsonl`.

**Status:** `[ ]` not done

---

### Step 05.3 - Flip the ticket to BlockNeedUserTest

**Files:** (catalog journal only - status transition)
**Depends on:** Steps 05.1, 05.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S1006 -Status BlockNeedUserTest -StatusNote 'On a real phone with an imported companion resource: browse/play it on home Wi-Fi (LAN), then switch to cellular and browse/play the SAME resource without re-importing - it must connect via the port-forwarded address; connection test must pass when either address is reachable. Requires a companion build that exports both lan+portforward paths (see temp/S1006/companion_handoff_prompt.md).'`. Confirm the single `S1006:` debug probe from Phase 04.6 is present in code (invariant for this status).

**Verification:**

- `select.ps1 -Id S1006 -Format json` shows `BlockNeedUserTest`.
- `Grep` - exactly one `Timber.d("S1006:` in `app_v2/src/main`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated with the new class + migration.
- [ ] Ticket status is `BlockNeedUserTest` with the device-test note.
- [ ] Exactly one `S1006:` debug probe in `app_v2/src/main`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After the owner's device test passes, `/spec-check S1006` promotes to `Verified` and removes the debug probe.

---

## Rollback Plan

Catalog/dev-log are regenerated artifacts - re-run `scan.ps1` to restore. No source rollback in this phase.
