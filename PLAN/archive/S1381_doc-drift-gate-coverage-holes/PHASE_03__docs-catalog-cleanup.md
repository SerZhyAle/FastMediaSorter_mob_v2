# Phase 03 - Docs, catalog and cleanup

**Strategic spec:** [`../S1381_doc-drift-gate-coverage-holes.md`](../S1381_doc-drift-gate-coverage-holes.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none
**Steps done:** 2 / 2

## Objective

Close documentation and registry obligations for the expanded drift contract.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/DEV_OPS.md` | Modified if operator contract changes | ≤ 500 |
| `docs/DOCS_MAP.md` | Generated if registry output changes | n/a |

## Steps

### Step 03.1 - Document any operator-facing contract change

**Files:** `docs/DEV_OPS.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Update developer operations only if the expanded checker changes invocation, output, or failure-resolution contract. Never hand-edit generated documents.

**Why:**

Technical documentation must remain reliable, and registered maintained documents must be reviewed.

**Verification:**

- `pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1` exits 0.
- `rg -n "check-doc-vs-gradle" docs/DEV_OPS.md` confirms the operator path when changed.

**Status:** `[x]` done - no operator contract change required.

### Step 03.2 - Validate document registry and final gate

**Files:** `docs/DOCUMENT_REGISTRY.jsonl`, `docs/DOCS_MAP.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Validate the registry, generate derived views when required, and run the drift gate plus its regression runner. Record unchanged registered records if registry content stays unchanged.

**Why:**

The completion criteria require the technical documents and registry representation to remain valid after the guard expands.

**Verification:**

- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0.
- `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` exits 0.
- `pwsh -NoProfile -File scripts/doc-drift/tests/Run-Tests.ps1` exits 0.

**Status:** `[x]` done - registry validation and generated-view check passed.

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `docs/FEATURES*.md` unchanged - no user-facing feature.
- [ ] `/spec-check S1381` returns `Verified`.
