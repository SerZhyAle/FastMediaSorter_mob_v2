<sub class="doc-stamp">26.11.24 12:37</sub>

# Pointer - `HARNESS-PROFILE`

| | |
| --- | --- |
| **Id** | `HARNESS-PROFILE` |
| **Version** | 0.22, draft. Owner: the canon (sza-unified-rules) |
| **Home** | `rule-adoption/README.md` section 3 in the shared contracts catalog |
| **Role here** | adopter - configures the shipped harness through `.sza-profile.json` |

## What this repository must do to stay conformant

- Keep `.sza-profile.json` at the root and declare only what differs from the shipped defaults.
- Remember the merge rule: objects merge by key, arrays and scalars replace.
- Every tracked path matches a lock-domain rule; a path no rule matches fails closed.
- `commands.compileCheck` (rule 11) is the one compile command an unattended pass runs; `runner.effortByModel` (rule 12)
  is optional and not declared here, because each runner instance takes its effort from
  `.claude/runner/model-effort.json` through its own `argsTemplate`.
- The optional `extraction` block (0.17, rule 13) names a derived product's port ledger, scope file and source
  product; this product is not derived from another, declares none, and no profile has to change.
- 0.18 to 0.21 add optional keys and defaults: `paths.releasesDir` (0.18, rule 14), the eight paths and grammar values
  derived from `paths.specsDir`, `paths.specArchiveDir` and `grammar.ticketIdPattern` (0.19, rule 15), and
  `grammar.legacyTicketIdPatterns` (0.20 and 0.21, rule 16). A key the profile carries wins, and this product declares
  `paths.specsDir` with every derived key; it declares neither `paths.releasesDir` nor a legacy ticket pattern, so no
  profile change follows.
- 0.22 (clarification, wire unchanged) makes the default runner prompt of rule 2 refer to the unattended-pass
  instructions in the lifecycle skill. This profile overrides `runner.promptTemplate` (`/spec-all {Id}`) and appends
  `.claude/runner/silent-mode.md` per instance, so the default does not apply and nothing is owed.

## Where it lives here

- `.sza-profile.json` at the repository root.
- Lock-rule coverage: `scripts/quality/assert-lock-path-coverage.ps1` (release scope).
