# Phase 03 — Docs / Catalog Cleanup

**Strategic spec:** [`../S0036_vr-android-xr-sdk-compat.md`](../S0036_vr-android-xr-sdk-compat.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02
**Blocks:** none — final phase
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Record the diagnostic decisions captured during S0036 research (rendernode artefact, EmbeddingMixedHandler residual noise, Q3 won't-fix), confirm `docs/FEATURES.md` requires no edit, and finalize the dev changelog and catalog state. No source code or manifest changes.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Phase 02 ✅ Done.
- [ ] `vrDebug` build green.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CHANGELOG.md` | Modified (append-only via script) | n/a |
| `docs/FEATURES.md` | Not modified — confirm only |
| `docs/FEATURES_RU.md` | Not modified — confirm only |
| `docs/FEATURES_UK.md` | Not modified — confirm only |
| `dev/CATALOG/app_v2.jsonl` | Not modified — no `.kt` changed |
| `dev/CATALOG/app_v2.md` | Not modified — no `.kt` changed |

> All edits to `dev/CHANGELOG.md` go through `pwsh -File scripts/add_to_dev_log.ps1`. Direct edits are forbidden.

---

## Steps

### Step 03.1 — Document XR SDK known-noise artefacts

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** — start of phase

**Prompt for developer:**

> Add a single dev-log entry that captures the known-noise artefacts identified during S0036 research, so future engineers triaging Android XR SDK logs do not re-investigate. Run:
>
> ```powershell
> pwsh -File scripts/add_to_dev_log.ps1 `
>     "PLAN/S0036_vr-android-xr-sdk-compat.md" `
>     "spec-doc" `
>     "S0036 known XR SDK system noise: 'Failed to open rendernode' (x86_64 emulator hwui artefact, won't-fix without real device, see S0036 §6 Q3); 'EmbeddingMixedHandler: No WindowHierarchyInfo found' is XR Shell internal — declaring PROPERTY_XR_ACTIVITY_START_MODE=HOME_SPACE is the recommended action; residual noise is acceptable and not blocking UX (see S0036 §6 Q2)."
> ```

**Verification:**

- `Grep` — `dev/CHANGELOG.md` contains `Failed to open rendernode` at least once.
- `Grep` — `dev/CHANGELOG.md` contains `EmbeddingMixedHandler` at least once.
- `Grep` — `dev/CHANGELOG.md` contains `S0036` on the same line as `known XR SDK system noise` or within 2 lines.

**Status:** `[ ]` not done

---

### Step 03.2 — Confirm `docs/FEATURES.md` trilingual: no change

**Files:** none modified — confirmation step only
**Depends on:** Step 03.1

**Prompt for developer:**

> Strategic §8 mandates **no** edit to `docs/FEATURES.md` / `_RU` / `_UK`: the fix is infrastructure-only (developer-side compatibility with a test platform) and invisible to end users. Confirm this by inspecting the three files and verifying they contain no S0036-related entry. If the user later decides Android XR SDK becomes a target end-user platform, a follow-up spec adds the entry.

**Verification:**

- `Grep` — `S0036` is **absent** from `docs/FEATURES.md`.
- `Grep` — `S0036` is **absent** from `docs/FEATURES_RU.md`.
- `Grep` — `S0036` is **absent** from `docs/FEATURES_UK.md`.
- `Grep` — `Android XR SDK` is **absent** (or remains at its prior count) in all three files.

**Status:** `[ ]` not done

---

### Step 03.3 — Final dev log entries and catalog non-regen confirmation

**Files:** `dev/CHANGELOG.md` (append via script)
**Depends on:** Step 03.2

**Prompt for developer:**

> Add dev-log entries for both phase deliverables (one entry each, if not already present from per-phase commits):
>
> ```powershell
> pwsh -File scripts/add_to_dev_log.ps1 `
>     "app_v2/src/vr/AndroidManifest.xml" `
>     "spec-dev" `
>     "S0036 Android XR SDK compat: declare uses-feature spatial + PROPERTY_XR_ACTIVITY_START_MODE=HOME_SPACE; overlay configChanges (smallestScreenSize|screenLayout|density|navigation|uiMode|fontScale) for Settings/Welcome/Main via tools:replace"
> ```
>
> Then confirm catalog regen is **not** required: no `.kt` files were modified during S0036, so `dev/CATALOG/app_v2.jsonl` and `app_v2.md` stay untouched. Per `CLAUDE.md` Post-Change Steps §3, catalog sync runs only after `.kt` changes.

**Verification:**

- `Grep` — `dev/CHANGELOG.md` last 100 lines contain `app_v2/src/vr/AndroidManifest.xml` and `S0036 Android XR SDK compat` together.
- Manual: `git diff -- dev/CATALOG/app_v2.jsonl dev/CATALOG/app_v2.md` produces zero output (catalog files unchanged).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Strategic spec marked `Implemented` via `pwsh -File scripts/spec_catalog/update.ps1 -Id S0036 -Status Implemented` (the implementer is expected to run this; `/spec-check` then advances to `Verified`).
- [ ] `vrDebug` and `vrUnlicensedDebug` builds green.

---

## Handoff Notes to Next Phase

Final phase — see [INDEX.md Completion Gate](INDEX.md#completion-gate). After this phase the spec is ready for `/spec-check S0036` to verify and advance status to `Verified`.

---

## Rollback Plan

Append a corrective note to `dev/CHANGELOG.md` via `add_to_dev_log.ps1` referencing the rollback. Do not delete prior log entries — the changelog is append-only.
