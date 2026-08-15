---
name: registry-ack-up-front
description: Pass -RegistryAck on the FIRST post-change call whenever the changed set touches a registered document, or the closure ends in an advisory and the re-run writes a duplicate dev-log row.
metadata:
  type: feedback
---

Whenever a closure's changed set includes a file matched by a `docs/DOCUMENT_REGISTRY.jsonl` record's
`paths` (privacy policy, settings reference + its manifest/annotations, ALL_FEATURES, README mirrors),
read those records first and pass `-RegistryAck "<id>[,<id>]"` on the **first** `post-change.ps1` call.

**The two that bite tooling tickets, measured 2026-08-14:**

- `repository-rules` covers far more than `CLAUDE.md` - its `paths` include `.claude/commands/*.md`,
  `.claude/reference/*.md`, `.claude/templates/*.md`, `.claude/agents/*.md`, `.claude/skills/*/SKILL.md`,
  `AGENTS.md`, `GEMINI.md`, `.github/copilot-instructions.md`, `docs/AGENT_HOOKS.md`. So **editing any
  command or skill file** trips the advisory. Discharging it means checking whether the sibling rule
  sets restate the rule you changed - grep the distinctive phrase across `CLAUDE/AGENTS/GEMINI.md` and
  `.claude/**`, and record the result.
- `script-cheatsheet` covers `docs/SCRIPT_CHEATSHEET.md`, a generated single-path record with no
  siblings - acknowledging it is a formality, but it still blocks a bare `PASS`.

**Related, same "before the first call" family:** `docs/SCRIPT_CHEATSHEET.md` goes stale whenever a
`.ps1` is **added** or its `param()` block or header `Exit codes:` section changes, and
`script-cheatsheet-sync-gate` then fires as an advisory. Run
`pwsh -NoProfile -File scripts/utils/help.ps1 -Generate` and put the regenerated file in the changed
set **before** the first closure call. Hit three times in one session on 2026-08-14.

**Why:** the `document-registry` step withholds a bare `PASS` until the touched records are
acknowledged - it exits 1 as an advisory, so the run still ends `PASS WITH ADVISORIES` and exit 0. The
only way to a clean verdict is to re-run, and `post-change` writes its dev-log row on every run, so the
second one lands a near-duplicate changelog entry for the same logical change. Hit twice in one session
(2026-08-10): `settings-reference` + `feature-inventory` on S1036, `legal-downloads` on S1546.

**How to apply:** before the closure, `Select-String -Path docs/DOCUMENT_REGISTRY.jsonl` for the file
names in the set, or just grep the registry for the doc's directory. Read the matched records (the loop
requires it anyway), check the sibling `paths` the change did NOT touch for the same edit, then close
once with the ack. Acknowledging is a claim you read them - never pass `all` to silence the step.

**If you already missed it (2026-08-12, S1595): do NOT re-run to clear the advisory.** The dev-log row
is already written and `dev/CHANGELOG.md` may not be hand-edited, so the re-run buys a cosmetic PASS at
the price of a permanent duplicate entry - strictly worse. Discharge the real obligation instead: read
the named records, check every untouched sibling for the same edit, and write what you found and why no
sibling needed changing into the phase file or the audit block. `PASS WITH ADVISORIES` with the
reasoning recorded beats a bare `PASS` bought with a corrupted changelog.

**The S1622 guard does NOT cover a re-run whose description changed (measured 2026-08-13, S1589).** Its
dedup deliberately treats a materially different description as a different change - goal 2 of S1622
requires exactly that, so a description that is a prefix of a longer one writes its own row. The
`[set of N: ..]` suffix is the only part it ignores. So if the re-run adds a file AND you rewrite the
description to name it, you get two rows and the script is behaving correctly. Keep the description
**byte-identical** across a re-run to let the guard collapse it; if you must change it, expect the
second row and remove the stale one deliberately.
