# Pointer - `HARNESS-PROFILE`

| | |
| --- | --- |
| **Id** | `HARNESS-PROFILE` |
| **Version** | 0.9, draft. Owner: the canon (sza-unified-rules) |
| **Home** | `rule-adoption/README.md` section 3 in the shared contracts catalog |
| **Role here** | adopter - configures the shipped harness through `.sza-profile.json` |

## What this repository must do to stay conformant

- Keep `.sza-profile.json` at the root and declare only what differs from the shipped defaults.
- Remember the merge rule: objects merge by key, arrays and scalars replace.
- Every tracked path matches a lock-domain rule; a path no rule matches fails closed.

## Where it lives here

- `.sza-profile.json` at the repository root.
- Lock-rule coverage: `scripts/quality/assert-lock-path-coverage.ps1` (release scope).
