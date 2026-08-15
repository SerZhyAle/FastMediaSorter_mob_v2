# Phase 07 — Docs, Catalog, Cleanup

**Strategic spec:** [`../S0035_android17-local-network-permission.md`](../S0035_android17-local-network-permission.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all prior phases
**Blocks:** —
**Steps done:** 5 / 5
**Started:** 2026-05-04
**Completed:** 2026-05-04

---

## Objective

Update user-facing feature docs, regenerate catalog, sync spec statuses, and close the ticket through `/spec-check`.

---

## Prerequisites

- [ ] Phases 01-06 are ✅ Done.
- [ ] All compile / build gates from earlier phases passed.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `dev/CATALOG/app_v2.md` | Regenerated | n/a |
| `PLAN/spec-catalog.jsonl` | Modified via script | n/a |
| `PLAN/S0035_android17-local-network-permission.md` | Modified | n/a |
| `PLAN/S0035_android17-local-network-permission/INDEX.md` | Modified | n/a |
| `dev/CHANGELOG.md` | Modified via script | n/a |

---

## Steps

### Step 07.1 — Update `docs/FEATURES*`

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Add the strategic §8 feature note to all three feature inventory files. Mention that Android 17+ requests local-network permission before the first SMB / FTP / SFTP connection and that denial routes the user to an explanation / settings path. Mention Cast only if the final behaviour is user-visible.

**Verification:**

- `Grep` — `local network permission|ACCESS_LOCAL_NETWORK|Android 17` appears in `docs/FEATURES.md`.
- `Grep` — the corresponding RU text appears in `docs/FEATURES_RU.md`.
- `Grep` — the corresponding UK text appears in `docs/FEATURES_UK.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS (EN/RU/UK bullets added). Dev log recorded.

---

### Step 07.2 — Regenerate `dev/CATALOG`

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 07.1

**Prompt for developer:**

> Run:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> If any new helper types were introduced while implementing S0035, fill their `role` and `status` metadata with `set.ps1` before continuing.

**Verification:**

- `Grep` — `PermissionHelper` appears in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `CastMediaManager` appears in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `PermissionHelper` appears in `dev/CATALOG/app_v2.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS (PermissionHelper in jsonl, CastMediaManager in jsonl, PermissionHelper in md). scan.ps1 + render.ps1 ran successfully.

---

### Step 07.3 — Add final dev-log entries

**Files:** `dev/CHANGELOG.md` via helper
**Depends on:** Step 07.2

**Prompt for developer:**

> Run `./scripts/add_to_dev_log.ps1` for every modified implementation file and for these tactical/meta files:
>
> - `PLAN/S0035_android17-local-network-permission.md`
> - `PLAN/S0035_android17-local-network-permission/INDEX.md`
> - `dev/CATALOG/app_v2.jsonl`
> - `dev/CATALOG/app_v2.md`
>
> Do not edit `dev/CHANGELOG.md` directly.

**Verification:**

- `Grep` — `S0035` returns new entries in `dev/CHANGELOG.md` for the touched implementation and tactical files.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification PASS. S0035 appears 49 times in dev/CHANGELOG.md covering all touched files.

---

### Step 07.4 — Sync strategic / tactical statuses

**Files:** `PLAN/spec-catalog.jsonl`, `PLAN/S0035_android17-local-network-permission.md`, `PLAN/S0035_android17-local-network-permission/INDEX.md`
**Depends on:** Step 07.3

**Prompt for developer:**

> After code is complete, run:
>
> ```powershell
> pwsh -File scripts/spec_catalog/update.ps1 -Id S0035 -Status Implemented
> ```
>
> Then update:
>
> - strategic spec `Status:` to `Implemented`;
> - tactical `INDEX.md` `Status:` to `Done` and `Phases:` to `7 / 7 done`.
>
> Do not mark `Verified` here.

**Verification:**

- `Command` — `pwsh -File scripts/spec_catalog/select.ps1 -Id S0035 -Format json` returns `"status":"Implemented"`.
- `Grep` — `**Status:** Implemented` matches in `PLAN/S0035_android17-local-network-permission.md`.
- `Grep` — `**Status:** Done|**Phases:** 7 / 7 done` returns hits in `PLAN/S0035_android17-local-network-permission/INDEX.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS (catalog status Implemented, strategic spec Implemented, INDEX Done/7/7).

---

### Step 07.5 — Run `/spec-check`

**Files:** none modified — verification only
**Depends on:** Step 07.4

**Prompt for developer:**

> Run `/spec-check S0035`. Expected outcome: `Verified`. If the audit returns `Partial` or `Broken`, enter the fix loop (`/spec-fix S0035` → repeat) until the remaining issues are empty or a real blocker remains.

**Verification:**

- `Command` — `pwsh -File scripts/spec_catalog/select.ps1 -Id S0035 -Format json` returns `"status":"Verified"` after `/spec-check`.
- `Grep` — `**Status:** Verified` matches in `PLAN/S0035_android17-local-network-permission.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS (catalog=Verified, strategic Status=Verified). /spec-check outcome: Verified, PASS 26, WARN 0, FAIL 0, MANUAL 8.

---

## Phase Done Criteria

- [x] Every Step 07.* above is `[x] done`.
- [x] Feature docs updated in EN / RU / UK.
- [x] Catalog regenerated and committed in sync.
- [x] Strategic status reaches `Verified` only through `/spec-check`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX completion gate.

---

## Rollback Plan

Revert docs and tactical metadata together, then re-run the catalog render if the implementation is backed out before release.