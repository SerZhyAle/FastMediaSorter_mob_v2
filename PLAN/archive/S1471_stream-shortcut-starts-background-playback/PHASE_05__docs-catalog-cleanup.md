# Phase 05 - Docs and catalog cleanup

**Strategic spec:** [`../S1471_stream-shortcut-starts-background-playback.md`](../S1471_stream-shortcut-starts-background-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Record the delivered capability, regenerate the class catalog for the two new classes, and close the ticket through the mechanical facade.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | +1 record |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `dev/CHANGELOG.md` | Appended via script | n/a |

---

## Steps

### Step 05.1 - Record the capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing, in English, that tapping a pinned radio-stream shortcut or a launcher stream tile starts background playback without opening the Streams screen, and falls back to that screen when background audio is off, the stream is video, or there is no network. Set `spec` to `S1471`. Take the flavor list from the gate rather than from memory - `SUPPORT_STREAMS` and `ENABLE_PERSISTENT_AUDIO_PLAYBACK` bound it to standard, noLegal, legacy and vr.

**Why:**

Strategic §3 delivers a user-visible behaviour change, and CLAUDE.md section 11 makes `docs/ALL_FEATURES.jsonl` the inventory every shipped capability is recorded in.

**Verification:**

- `Grep` - `S1471` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

**Result (2026-08-09):** record `internet-streams.shortcut-starts-background-playback` added, flavors `standard,noLegal,legacy,vr` read off the `SUPPORT_STREAMS` (line 29) and `ENABLE_PERSISTENT_AUDIO_PLAYBACK` (line 38) rows of `docs/FLAVOR_MATRIX.md`, not from memory. `validate.ps1` exit 0, 679 records. S0637's own record stays as it is - it describes pinning the shortcut, which this ticket does not change.

---

### Step 05.2 - Set catalog roles for the new classes

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`, then fill `role` and `status` for `StreamHeadlessPlayManager` and `StreamPlayLaunchActivity` with `pwsh -NoProfile -File dev/CATALOG/scripts/set.ps1`. Both live in `src/main` and ship in every flavor, so pass no `-NoFlavors`.

**Why:**

CLAUDE.md "Catalog & Navigation" requires new classes to carry `role` and `status`, and both classes are the entry points a future reader will search for when asking how a shortcut plays a stream.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "StreamHeadlessPlayManager"` returns one record with a non-empty `role`.
- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "StreamPlayLaunchActivity"` returns one record with a non-empty `role`.

**Status:** `[x]` done

**Result (2026-08-09):** `catalog_sync.ps1 -Module app_v2` exit 0 (2681 records). Roles set for **four** classes, not the two the prompt named: the plan counted only the two it introduced by name, but Phase 02 also added `StreamShortcutRouteManager` and Phase 04 added `MigrateStreamShortcutsUseCase`, and a new class without a role is exactly what CLAUDE.md "Catalog & Navigation" forbids. All four carry `status=new`.

---

### Step 05.3 - Close through the facade

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/post-change.ps1` naming the whole changed set with `-Files` and adding `-ScopeToFile`, `-ChangeType Mixed`, `-Module app_v2`. Read the printed verdict: only a bare `post-change: PASS` is clean, and `PASS WITH ADVISORIES` names each advisory to read before closing.

**Why:**

CLAUDE.md section 12 routes mechanical closure through this facade, and naming only part of the changed set would certify only that part.

**Verification:**

- `post-change.ps1` exits 0 and prints `post-change: PASS` or `PASS WITH ADVISORIES`.
- `Grep` - `S1471` present in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Result (2026-08-09):** `post-change: PASS (Mixed, 60526 ms)`, exit 0 - the bare verdict, no advisories. Seven Kotlin files named via `-Files` with `-ScopeToFile`; every scoped gate judged that whole set. `assert-detekt: PASS [scoped]` - three files carry new findings project-wide, none of them ours. `assert-no-ticket-logs` counted 145 allowed `BlockNeedUserTest` probes, which includes this ticket's three.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - documentation and generated indexes only; regenerate the catalog after any revert.
