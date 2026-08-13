# Phase 04 - docs-catalog-cleanup

**Strategic spec:** [`../S0336_nolegal-extended-system-info.md`](../S0336_nolegal-extended-system-info.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all prior phases
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-06-03
**Completed:** 2026-06-03

**Step Log:**

- 2026-06-03 - Steps 04.1-04.4 Verification PASS (FEATURES_noLegal §7 EN/RU/UK added; public FEATURES untouched per `git status`; FUNCTIONALITY.log S0336 ADD via close-and-log; catalog regenerated with `noFlavors=[standard,lite,photos,legacy,vr]` on the three noLegal classes; 19 dev-log entries batched).

---

## Objective

Record the feature in the noLegal-only documentation, append the functionality-log entry, regenerate the class catalog with flavor-isolation metadata, and confirm the dev changelog is complete. No public `docs/FEATURES*.md` change.

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done; `noLegalDebug` and `standardDebug` both build.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES_noLegal.md` | Modified | - |
| `docs/FEATURES_noLegal_RU.md` | Modified | - |
| `docs/FEATURES_noLegal_UK.md` | Modified | - |
| `dev/CATALOG/app_v2.jsonl` + `.md` | Regenerated | - |

> `docs/FEATURES_noLegal*.md` are gitignored, local-only (S0156 §6.9). Public `docs/FEATURES.md` / `_RU` / `_UK` MUST NOT be touched - this is a noLegal-only capability.

---

## Steps

### Step 04.1 - Document the capability in noLegal feature docs (trilingual)

**Files:** `docs/FEATURES_noLegal.md`, `docs/FEATURES_noLegal_RU.md`, `docs/FEATURES_noLegal_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one concise entry to all three noLegal feature docs (EN/RU/UK mirrors): the noLegal System info dialog now shows an extended diagnostics section (security/root, permissions audit, installer + signature, bundled runtimes incl. Python/yt-dlp/PaddleOCR/Tesseract/OpenXR-VR, mounts, network, process resources), with sensitive values masked by default and a confirmed "Copy full report" action. Prefer `/doc-update`. Do NOT edit public `docs/FEATURES*.md`. Use `..` not `...`, correct `ё` in Russian.

**Verification:**

- `Grep` - the new entry present in each of the three `docs/FEATURES_noLegal*.md` files.
- `Grep` - `git status --porcelain docs/FEATURES.md docs/FEATURES_RU.md docs/FEATURES_UK.md` shows no modification (`expected: empty | actual: <confirm>`).

**Status:** `[x]` done

---

### Step 04.2 - Append the functionality-log entry

**Files:** `dev/FUNCTIONALITY.log` (via script)
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `\.\scripts\add_to_functionality_log.ps1 -Id S0336 -Op ADD -Description "noLegal System info: extended diagnostics section with default-masked sensitive values and confirmed full-report copy"`. Run it standalone / last (the script leaves a non-zero `$LASTEXITCODE` even on success - re-verify the journal line landed).

**Verification:**

- `Grep` - a line containing `S0336` and `ADD` present in `dev/FUNCTIONALITY.log`.

**Status:** `[x]` done

---

### Step 04.3 - Regenerate catalog with flavor-isolation metadata

**Files:** `dev/CATALOG/app_v2.jsonl` + `.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Then mark the three noLegal-only classes with `set.ps1 -NoFlavors "standard,lite,photos,legacy,vr"` and a `role` + `status`: `NoLegalExtendedDiagnosticsContributor`, `NoLegalDiagnosticsCollectors`, `NoLegalExtendedDiagnosticsModule`. Wrap each `set.ps1` call in try/catch when batching (it aborts the batch on a missing path).

**Verification:**

- `Grep` - `NoLegalExtendedDiagnosticsContributor` present in `dev/CATALOG/app_v2.jsonl` with `noFlavors` listing the five public flavors.
- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "*ExtendedDiagnostics*"` lists the new contract + noLegal classes.

**Status:** `[x]` done

---

### Step 04.4 - Confirm dev changelog completeness

**Files:** `dev/CHANGELOG.md` (via script, indirect)
**Depends on:** Step 04.3

**Prompt for developer:**

> Confirm every file modified across Phases 01-03 has a `dev/CHANGELOG.md` entry (added through `add_to_dev_log.ps1` during each phase). Add any missing entry via `\.\scripts\add_to_dev_log.ps1`. Never hand-edit `dev/CHANGELOG.md`.

**Verification:**

- `Grep` - changelog mentions `GatherSystemInfoUseCase`, `ExtendedDiagnostics`, `NoLegalExtendedDiagnosticsContributor`, and `GeneralSettingsLogHelper` edits.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `docs/FEATURES_noLegal*.md` updated; public `docs/FEATURES*.md` untouched.
- [ ] `dev/FUNCTIONALITY.log` carries the S0336 ADD line.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated; noLegal-only classes carry `-NoFlavors`.
- [ ] `dev/CHANGELOG.md` complete for all touched files.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After this, `/spec-dev` flips S0336 to `BlockNeedUserTest` (one `Timber.d("S0336: ...")` probe per changed flow entry) for on-device verification, then `/spec-check` → `Verified` removes the probes.

---

## Rollback Plan

Documentation and catalog only - revert the doc edits; the catalog regenerates from source on the next `catalog_sync.ps1` run.
