<sub class="doc-stamp">26.11.12 03:28</sub>

# Pointer - `HARNESS-PROFILE`

| | |
| --- | --- |
| **Id** | `HARNESS-PROFILE` |
| **Version** | 0.16, draft. Owner: the canon (sza-unified-rules) |
| **Home** | `rule-adoption/README.md` section 3 in the shared contracts catalog |
| **Role here** | adopter - configures the shipped harness through `.sza-profile.json` |

## What this repository must do to stay conformant

- Keep `.sza-profile.json` at the root and declare only what differs from the shipped defaults.
- Remember the merge rule: objects merge by key, arrays and scalars replace.
- Every tracked path matches a lock-domain rule; a path no rule matches fails closed.
- `commands.compileCheck` (rule 11) is the one compile command an unattended pass runs; `runner.effortByModel` (rule 12)
  is optional and not declared here, because each runner instance takes its effort from
  `.claude/runner/model-effort.json` through its own `argsTemplate`.

## Where it lives here

- `.sza-profile.json` at the repository root.
- Lock-rule coverage: `scripts/quality/assert-lock-path-coverage.ps1` (release scope).
