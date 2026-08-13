# Phase 03 - Docs and catalog cleanup

**Strategic spec:** [`../S1332_bugfix-adb-log-hides-app-timber-lines.md`](../S1332_bugfix-adb-log-hides-app-timber-lines.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Bring the registered documents that describe the `log` verb back in line with what it now does, regenerate the gated cheatsheet, and journal the change.

---

## Prerequisites

- [x] Phase 01 and Phase 02 are ✅ Done.
- [x] Document registry queried for product areas `workflow`, `quality`, `testing` and trigger `workflow`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/DEV_OPS.md` | Modified | <= 4 changed lines |
| `docs/SCRIPT_CHEATSHEET.md` | Regenerated | n/a - generated |
| `dev/CHANGELOG.md` | Appended via script | n/a |

---

## Steps

### Step 03.1 - Correct the DEVICE OPS description

**Files:** `docs/DEV_OPS.md`
**Depends on:** - start of phase

**Prompt for developer:**

> The DEVICE OPS block describes the verb as `# logcat -d app tail, filtered; full dump -> temp/`, which is exactly the claim that was untrue. Replace the trailing comment on the `adb log` example so it states the selection rule: the app's own process lines plus lines naming the package. Add one sentence below the code block noting that a `WARN` verdict means the filter suppressed lines the capture file still contains, and that the capture under `temp/scratch/` is the fallback.
>
> Do not change the example's invocation form. `docs/DEV_OPS.md` belongs to the registered `developer-operations` record, so this edit is a registry-relevant change.

**Verification:**

- `Grep` - `logcat -d app tail, filtered` returns zero hits in `docs/DEV_OPS.md`.
- `Grep` - `WARN` matches at least once in the DEVICE OPS section of `docs/DEV_OPS.md`.
- `Grep` - `.\a.ps1 adb log -Tail 400 -Grep` still matches (the invocation form is unchanged).

**Status:** `[x]` done

---

### Step 03.2 - Regenerate the script cheatsheet

**Files:** `docs/SCRIPT_CHEATSHEET.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> `docs/SCRIPT_CHEATSHEET.md` is generated from every repository `param()` block and its drift is gated inside `scripts/post-change.ps1` by `scripts/quality/assert-script-cheatsheet-sync.ps1`. The generator indexes `lib/` and `*.tests/` scripts too - `scripts/quality/lib/changed-files.ps1` and `scripts/guard.tests/Run-Tests.ps1` both have entries - so both new files from Phases 01 and 02 make the file stale and the gate will refuse the change. Regenerate with `pwsh -NoProfile -File scripts/utils/help.ps1 -Generate`. Never hand-edit the file.

**Verification:**

- Value equality - `pwsh -NoProfile -File scripts/quality/assert-script-cheatsheet-sync.ps1 -Gate -Quiet` exits 0.
- `Grep` - `scripts/devtest/adb-log-filter.tests/Run-Tests.ps1` matches in `docs/SCRIPT_CHEATSHEET.md`.
- `Grep` - `scripts/devtest/lib/adb-log-filter.ps1` matches in `docs/SCRIPT_CHEATSHEET.md`.
- `Grep` - the `Exit:` line under the `scripts/devtest/adb.ps1` entry is byte-identical to its pre-change text, ending `7 - the underlying adb command returned non-zero`.

**Status:** `[x]` done

---

### Step 03.3 - Validate the document registry

**Files:** none - validation only against `docs/DOCUMENT_REGISTRY.jsonl`
**Depends on:** Step 03.2

**Prompt for developer:**

> Two registered documents changed: `developer-operations` (`docs/DEV_OPS.md`) and `script-cheatsheet` (`docs/SCRIPT_CHEATSHEET.md`, `generated: true`). Run the registry closure trio. No new record is needed - both paths are already registered, and `scripts/devtest/**` is not a registered documentation path.

**Verification:**

- Value equality - `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0.
- Value equality - `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` exits 0.

**Status:** `[x]` done

---

### Step 03.4 - Journal the change

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 03.3

**Prompt for developer:**

> Route closure through the facade with `-ScopeToFile`, since the working tree carries other tickets' work in progress: `pwsh -NoProfile -File scripts/post-change.ps1 -File "scripts/devtest/adb.ps1" -Target "S1332" -Description "adb.ps1 log selects app lines by pid, warns when the filter suppresses matches" -ChangeType Script -ScopeToFile`. Batch the remaining files into one logical dev-log entry rather than one per file - the lib, the suite, the fixture and the two docs are a single change. Do not run `catalog_sync.ps1`: no `.kt` was touched, so the class catalog cannot have drifted. Do not add a `docs/ALL_FEATURES.jsonl` record: a developer-machine script is not a shipped app capability.
>
> Release the code lock if `post-change.ps1` did not: `pwsh -NoProfile -File scripts/utils/exit-code-lock.ps1`.

**Verification:**

- `Grep` - `dev/CHANGELOG.md` contains an `S1332` entry naming `scripts/devtest/adb.ps1`.
- `Grep` - `dev/CHANGELOG.md` contains entries for `scripts/devtest/lib/adb-log-filter.ps1` and `scripts/devtest/adb-log-filter.tests/Run-Tests.ps1`.
- Value equality - `pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Code` reports no holder.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/devtest/adb.ps1 help` exits 0.
- [x] `pwsh -NoProfile -File scripts/devtest/adb-log-filter.tests/Run-Tests.ps1` exits 0.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Device confirmation still outstanding - the tactical plan cannot prove the on-device behaviour. Hand back to the orchestrator with the check named in the strategic spec section 4: launch the app, then confirm `adb.ps1 log -Grep "<a class tag seen in the raw capture>"` returns that line, and that a nonsense pattern still returns 0 with an `OK` verdict and no `WARN`.

---

## Handoff Notes to Next Phase

Final phase - see [INDEX.md](INDEX.md) Completion Gate.

---

## Rollback Plan

Revert `docs/DEV_OPS.md`, re-run `scripts/utils/help.ps1 -Generate`. No data migration, no user-facing surface.
