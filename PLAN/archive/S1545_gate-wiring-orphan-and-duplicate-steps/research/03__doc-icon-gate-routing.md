# S1545 research 03: document-icon gate routing

## Method

Search all script, hook and command callers for the icon consistency gate. Run the gate in blocking mode against the current tree. Read its declared input and output surfaces.

## Findings

- No automatic caller invokes the gate. The only operational mention is a printed next-step instruction in an icon-cleanup helper.
- The blocking invocation passes on the current tree and reports 25 mapped drawables.
- The gate validates one map, generated icon assets, three landing pages, three how-to pages, the document map and three settings-reference pages.
- Its scope is finite and explicit, so a path predicate can cover each input class without running for unrelated documentation edits.

## Decision

Add a conditional closure step for changes to the icon map, icon assets or generators, any checked landing page, and any checked markdown surface. Add regression coverage for every included and excluded path class. Do not make all documentation changes pay for this check.

## Evidence

- `scripts/quality/assert-doc-icons-sync.ps1` declared checks and inputs.
- Repository-wide caller search.
- `pwsh -NoProfile -File scripts/quality/assert-doc-icons-sync.ps1 -Gate` returned `PASS - map/assets/landing/markdown in sync (25 drawables)`.
