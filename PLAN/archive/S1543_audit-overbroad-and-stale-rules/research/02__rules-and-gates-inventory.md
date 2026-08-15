# S1543 research 02 - inventory of the numbered rules and their mechanical gates

Date: 2026-08-09. Sources: live working tree - `CLAUDE.md` §10, `scripts/quality/assert-*.ps1` (51 files via Glob), `scripts/quality/lib/source-matchers.ps1`, `scripts/post-change.ps1`, `scripts/quality/assert-fast-gates.ps1`, `a.ps1`, `.claude/hooks/`, `~/.claude/hooks/`, `.github/workflows/`; ticket states via `scripts/spec_catalog/select.ps1`.

Answers §6 item 2. This is a point-in-time audit record, deliberately **not** a new permanent document - a standing rules inventory would itself become the stale document this ticket complains about (§9 ADR-3).

Verdict vocabulary is research 03 §2: **live-enforced**, **live-ungated**, **over-broad**, **stale**, **unprovable**.

## 1. Headline counts

- Gate scripts examined: **51** (every `scripts/quality/assert-*.ps1`).
- Numbered rules examined: **26** (`CLAUDE.md` §10, rules 1-26).
- Judged **stale with evidence and acted on in this ticket**: **2**. Both are text, not behaviour - one over-broad enforcement (the style rule, research 01) and one gate header describing a two-months-closed ticket as in progress (§4 below).
- Judged **needs a decision, parked with evidence**: **2** findings, filed as S1545.
- Judged **inverted** (enforced where excluded, absent where required): **1**, filed as S1544.
- Left alone, deliberately: everything else, per §5.

## 2. Rules whose named script does not exist: zero

Every script, hook and command path named inside `CLAUDE.md` §10 resolves on disk. Checked individually:

- Rule 19 -> `scripts/quality/assert-neuroslop.ps1` - exists.
- Rule 21 -> `scripts/quality/assert-deprecated-pm-flags.ps1` - exists.
- Rule 22 -> `scripts/quality/assert-settings-doc-sync.ps1` - exists.
- Rule 23 -> `scripts/utils/agent-lock.ps1`, `enter-code-lock.ps1`, `wait-for-lock-turn.ps1`, `lock-status.ps1` - all exist.
- Rules 24, 25, 26 -> `~/.claude/hooks/guard-find-command.ps1`, `guard-ps1-in-bash.ps1`, `guard-fire-and-forget.ps1` - all exist (confirmed by Glob over `C:\Users\serzh\.claude\hooks\`).
- Rule 10 -> `.claude/commands/ui-clarify.md` - exists.

The single most common form of a stale rule - a rule pointing at machinery that was deleted - **does not occur here**. This is the most important negative result in the inventory and it directly bounds the owner's "probably many stale rules": the rule text is not rotting against its own tooling.

## 3. The ungated majority, and why it is not staleness

17 of 26 rules name no mechanical enforcement in their own text: 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15, 16, 17, 18, 20.

That is a compliance problem, already measured (research 03 §2: ungated rules hold at 1-8%), and it is the subject of the closed S1340 pass. It is **not** staleness, and the correct action for an ungated rule whose defect still reaches the owner is to gate it, not to delete it. Four of the seventeen already have a live gate that the rule text simply does not cite - a cross-reference gap, not a missing gate:

- Rule 14 (flavor isolation) -> the `flavor-flags` rule in `source-matchers.ps1:428`.
- Rule 16 (focus indication) -> `scripts/quality/assert-focus-highlight.ps1`.
- Rule 17 (inset safe bounds) -> the `window-insets` rule in `source-matchers.ps1:456`.
- Rule 3 (no Activity logic) -> the `activity-logic` rule in `source-matchers.ps1:478`.

One rule has genuinely no gate anywhere in the 51: **Rule 11** (a `res/layout/*.xml` edit requires the `layout-land` counterpart). `assert-layout-variant-id-parity.ps1` checks id parity between variants that already exist; it does not check that a counterpart exists at all.

Verdict on all seventeen: **live-ungated**, none stale. Not touched by this ticket, and not parked either - S1340 already ruled on this class ("gate only where a defect reached the owner, compress the rest"), and re-opening it without a new defect would be re-litigating a closed decision.

## 4. The one stale artefact found and fixed here

`scripts/quality/assert-neuroslop.ps1` lines 12-29, the header, is wrong on two counts:

1. It lists **9** children by filename. The script no longer runs children: since S1338 it forwards unfiltered to `assert-source-gates.ps1` (`:61`, `:71-74`), which applies every rule in `Get-SourceRules` - the runner itself reported **17** rules over one walk of 3,740 files when executed on 2026-08-09, including three the header never mentions (`deprecated-pm-flags`, `flavor-flags`, `public-mutable-flow`) and several added later (`restricted-menu-reflection`, `activity-logic`, `untracked-dialog`). The count is taken from the runner's own output rather than from reading the table, because the table is edited more often than any header describing it.
2. It states "Cleanup of the catch and layout-color dimensions is still in progress (S0383 Phases 03/04), so their baselines are the current floors, not the final targets". `select.ps1 -Id S0383` returns `"status":"Archived","updated":"2026-06-09 23:20"` - the ticket that would carry out those phases closed two months ago.

Verdict: **stale**, documentation-only, zero behaviour risk to correct. Acted on in this ticket.

## 5. Findings that look like staleness and are not - left alone with reasons

**Twelve thin wrappers with no caller of their own.** `assert-trivial-comments.ps1`, `assert-empty-catch.ps1`, `assert-layout-hardcoded-colors.ps1`, `assert-unsafe-collect.ps1`, `assert-globalscope.ps1`, `assert-nontimber-log.ps1`, `assert-stub-todo.ps1`, `assert-em-dash.ps1`, `assert-non-null-assertion.ps1`, `assert-window-insets.ps1`, `assert-swallowed-cancellation.ps1`, `assert-untracked-dialogs.ps1`. Each is a `-Only <rule>` forwarder to `assert-source-gates.ps1`, and every one of their rules fires inside the umbrella. Coverage is intact; deleting them would remove a usable manual entrypoint and buy nothing. Verdict: **live-enforced**. Left alone; the readability question is carried in S1545.

**Thirteen gates that never fired in three weeks.** Measured 2026-08-07: they cost 133 minutes of 2,803 total, **4.7%**, while detekt alone is 86%. A gate that never fires is insurance, not waste, and the measurement explicitly warns that deleting them "looks obvious and buys almost nothing while removing insurance". Verdict: **live-enforced**. Left alone.

**Three lexical rules evaluated twice per Kotlin closure** and **one gate with zero callers** (`assert-doc-icons-sync.ps1`). Both are real, both are evidenced (S1545 §0 carries the file:line proof), and neither can be decided without the per-gate table that the 2026-08-07 measurement's own rule demands first. Verdict: **unprovable today**. Parked as S1545, not acted on.

**The style rule's missing half.** The canon requires the rule on documentation prose and user-visible strings; nothing in the repo enforces it there, and the five fixer scripts that could have have zero callers. Adding enforcement is the opposite direction from this ticket's request and needs its own cost ruling. Verdict: **out of scope**. Parked as S1544.

## 6. What this inventory does not claim

It does not claim the 26 rules are all necessary, nor that no rule anywhere in the repository is stale. It claims something narrower and checkable: across the 51 gate scripts and the 26 numbered rules, exactly **one** stale artefact was found with evidence sufficient to act on, and the most likely form of rot - a rule naming machinery that no longer exists - occurs **zero** times.

The prior "probably many stale rules" is therefore not supported at the level of the numbered rules and their gates. Where the cost actually sits is elsewhere and already measured: one gate (detekt) is 86% of gate wall time, and the always-on preamble is 23.3% of every request. Neither is addressed by deleting rule text.
