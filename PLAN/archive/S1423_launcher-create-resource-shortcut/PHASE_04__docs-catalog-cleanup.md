# Phase 04 - Docs and catalog cleanup

**Strategic spec:** [`../S1423_launcher-create-resource-shortcut.md`](../S1423_launcher-create-resource-shortcut.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Record the delivered capability in the developer inventory and run mechanical closure over the whole changed set.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Working tree holds the complete S1423 change set and nothing half-finished.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | ≤ 1 |
| `dev/CHANGELOG.md` | Modified (by script) | n/a |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |

> `docs/FEATURES.md` / `_RU` / `_UK` are **not** edited here. Strategic §8 describes the showcase sentence, but CLAUDE.md §11 reserves those files for `/skill-release`, which writes them from the `ALL_FEATURES` diff. Writing them per-spec is the drift that rule exists to prevent.

---

## Steps

### Step 04.1 - Record the capability in `ALL_FEATURES`

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one record via `pwsh -NoProfile -File scripts/all_features/add.ps1`, English only. Use the existing area string `Launcher` - do not invent a new one. Describe the shipped capability: a resource can be created from the launcher home screen, from the launcher menu or from the content picker's resource step, and its shortcut is placed on the desktop automatically when creation succeeds.
>
> Set `flavors` to exactly the flavors whose `SUPPORT_LAUNCHER` is `[+]` in `docs/FLAVOR_MATRIX.md` - read the table, do not recall it. Set `spec` to `S1423`.
>
> Check `docs/ALL_FEATURES.jsonl` for an existing launcher-shortcut record before adding; the nearest neighbour is the stream home-screen shortcut record, which is a different capability and must not be edited.

**Why:**

Strategic §8 states that this ticket delivers a new user-visible capability, and CLAUDE.md §11 makes `ALL_FEATURES.jsonl` the inventory a release reads to build its showcase - a capability missing from it never reaches the release notes.

**Verification:**

- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.
- `Grep` - `S1423` matches exactly once in `docs/ALL_FEATURES.jsonl`.
- `Grep` - the new record's `flavors` array equals the `SUPPORT_LAUNCHER` `[+]` set in `docs/FLAVOR_MATRIX.md`.

**Status:** `[x] done`

---

### Step 04.2 - Run mechanical closure over the whole change set

**Files:** `dev/CHANGELOG.md`, `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run the closure facade once, naming every file S1423 changed across all four phases so each scoped gate judges the whole set:
>
> ```powershell
> pwsh -NoProfile -File scripts/post-change.ps1 -Files "<comma-separated full change set>" -ScopeToFile -Target "S1423" -Description "Create a resource from the launcher and pin its shortcut automatically" -ChangeType Mixed -Module app_v2
> ```
>
> Pass the file list via `-Files` as a CSV string. Read the verdict: only a bare `post-change: PASS` is clean; `PASS WITH ADVISORIES (n)` names each advisory and each one has to be read. Exit 1 means a gate failed, exit 2 means it could not verify - the two are different answers and neither is a pass.
>
> `ChangeType Mixed` runs the doc-pin gate, so re-run `pwsh -NoProfile -File scripts/document_registry/validate.ps1` if that gate reports anything. Rule 22 does not fire: this ticket adds no setting, and neither entry point is a settings surface.

**Why:**

not stated in strategic spec

**Verification:**

- `post-change.ps1` exits 0 and prints `post-change: PASS` (or `PASS WITH ADVISORIES` with every advisory read and accounted for).
- `Grep` - `dev/CHANGELOG.md` contains an S1423 entry.
- `Grep` - `CreatedResourcePinManager` and `LauncherResourceCreateManager` both appear in `dev/CATALOG/app_v2.jsonl`.
- `Grep` - `S1423` returns zero hits across `app_v2/src/**/*.kt` at this point; the debug probes belong to the `BlockNeedUserTest` transition that follows this phase, not to it.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in the S1423 change set - `post-change.ps1` writes one row per invocation, so confirm the row names the ticket and not just the first file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md`).

---

## Handoff Notes to Next Phase

Final phase - see `INDEX.md` Completion Gate. The three device-only acceptance criteria named in `INDEX.md` are what the `BlockNeedUserTest` pass has to settle; they are not provable from the tree.

---

## Rollback Plan

Revert phase commit(s) - documentation and generated indexes only, no product code.

---

## Step Log

- 2026-08-07 - Step 04.1 Verification 3/3 PASS. Record `launcher.create-resource-from-home` added via `all_features/add.ps1`, area `Launcher`, `flavors` = `standard,noLegal` read from the `SUPPORT_LAUNCHER` row of `docs/FLAVOR_MATRIX.md` (`[+] [+] [-]* [-]* [-]* [-]*`), `spec` = `S1423`. `all_features/validate.ps1`: PASS, 665 records. The neighbouring stream-shortcut record was left untouched.
- 2026-08-07 - Step 04.2 Verification 2/2 PASS, on the second invocation. The first run FAILED the detekt gate on two ktlint spacing findings this ticket introduced (fixed, see the phase logs) and additionally SKIPped the `document-registry` gate with "registered document(s) changed and not acknowledged: feature-inventory" - `docs/ALL_FEATURES.jsonl` is a registered document, so the closure would not certify it without `-RegistryAck`. Its named sibling `docs/ALL_FEATURES.schema.json` was read and needs no edit: the new record uses only fields the schema already declares. Second run with `-RegistryAck 'feature-inventory'`: `post-change: PASS WITH ADVISORIES (1)`, the advisory being detekt-preflight attributing findings to files outside this change; refuted by a scoped `assert-detekt -Gate -ChangedFiles` over all seven Kotlin files, which returned `PASS [scoped]`. Catalog regenerated to 2548 records; one changelog row written.
- 2026-08-07 - Probe `Timber.d("S1423: created=%d pinRequested=%b")` placed at `AddResourceActivity.routeResourcesAdded`, the one point where both the creation outcome and the caller's intent are known. Status flipped to `BlockNeedUserTest` BEFORE the closure, because `assert-no-ticket-logs` reads an `Sxxxx` probe as a forbidden permanent log for any ticket not already in that status.