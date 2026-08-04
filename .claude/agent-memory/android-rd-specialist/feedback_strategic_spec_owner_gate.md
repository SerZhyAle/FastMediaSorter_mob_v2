---
name: strategic-spec-owner-gate
description: Draft strategic spec cannot transition to Approved without a relevance-driven §3.3 "Owner inputs (Approval gate)" subsection; the gate validates whatever /spec emitted into §3.3 plus the universally-required `Related tickets` bullet.
metadata:
  type: feedback
---

**Rule.** Every strategic specification authored as a Draft (via `/spec`, manually, or as a sub-agent batch) must contain a §3.3 subsection titled **"Owner inputs (Approval gate)"** inside the existing §3 "Пожелания и ограничения". The *set* of bullets inside §3.3 is **not fixed** - it is decided by `/spec` Process step 5.1 based on the spec's detected character. The transition Draft → Approved is gated by `scripts/spec_catalog/check-owner-inputs.ps1` returning exit 0; the gate validates only the bullets actually emitted, and additionally requires `Related tickets` to be present.

**Why.** The original 12-field §3.3 (introduced 2026-05-20 morning) traded false-precision for friction: on infrastructure / tooling specs the author was forced to write `n/a - non-user-facing` five times in a row just to pass the gate. The user explicitly called this out as bureaucracy. The relevance-driven design retains the three loss patterns the gate was meant to close, but applies each control only where it can fire:

1. **UI placement thrash** (S0125 ran 10 `CHANGE → FIX` cycles in 36 hours) - closed by emitting `UI placement contract` + `Accessibility` *only when `ui-facing` tag matches*.
2. **Flavor scope guessing** - closed by emitting `Flavor scope` *only when `flavor-aware` tag matches* (slug or text mentions `vr` / `wear` / `noLegal` / `lite` / `photos` / `legacy` / "вариант сборки").
3. **Audit / followup loops** - closed by emitting `Validation level` + `Owner sign-off` *only when any executable scope tag matched*, and by always emitting `Related tickets` so `/spec-next` and `bulk-update.ps1` can read dependency chains.

For pure documentation / refactor / rename specs that match no scope tag, §3.3 contains a single line: `- **Related tickets:** none`. That counts as a complete §3.3.

**How to apply.**

- **On creation.** During Process step 5 of `/spec`, scan `shortName` slug + §1 body + §3.2 bullets case-insensitively against the scope-tag table (kept in `.claude/commands/spec.md` step 5.1). Emit only the bullets that the matched tags request, plus the universal `Related tickets`. Fill each emitted bullet with a concrete value drawn from research already in §1/§3.2/§4/§10/§11 - do not leave bracketed placeholders.

- **§3.3 emission table (mirrored from `spec.md` step 5.1):**

  | Tag | Bullets emitted |
  | --- | --- |
  | `flavor-aware` | Flavor scope |
  | `api-bound` | API level constraints |
  | `wear-os` | Wear OS |
  | `perf-critical` | Performance budget |
  | `data-surface` | Data compatibility |
  | `localization-touched` | Localization |
  | `ui-facing` | UI placement contract, Accessibility |
  | `comm-policy-applies` | Communication policy |
  | *(any tag matched)* | Validation level, Owner sign-off |
  | *(always)* | Related tickets |

- **On promotion to Approved.** Never call `scripts/spec_catalog/update.ps1 -Id Sxxxx -Status Approved` without trusting the embedded gate to fire `check-owner-inputs.ps1` automatically. If the gate fails, the agent reports which bullets are missing or carry placeholders. The agent must not silently fill placeholders to bypass the gate, and must not emit extra `n/a` bullets to satisfy phantom requirements - the gate does not require fields that were not emitted.

- **On transition out of Approved.** Once the spec leaves Draft → Approved, §3.3 is frozen unless `/spec-update` is run. Later edits that re-open the spec to Draft must re-evaluate scope tags - if the spec's character changed (e.g. doc-only refactor that now grew UI scope), re-emit §3.3 with the new bullet set.

- **Backward compatibility.** Specs S0268..S0277 (created with the old 12-field §3.3 before this rule was relaxed) remain valid - the data-driven gate accepts whatever bullets are present, so the existing 12-bullet form still passes. Do not retroactively shrink those specs.

**Edge cases.**

- A spec that genuinely spans multiple scopes (e.g. UI + data + flavor) emits the union of bullets - that is the correct outcome, not bureaucracy.
- A bullet whose value is `n/a - <reason>` counts as filled. Use it sparingly - if `n/a` appears, the bullet was probably not relevant in the first place and should not have been emitted.
- Manual `update.ps1 -Status Approved` calls outside `/spec` still hit the gate; emit §3.3 manually using the same emission table if the spec was authored without going through `/spec`.

**Related memories:** [[no-scaffolding-as-done]] - same family of failures motivated the original gate.
