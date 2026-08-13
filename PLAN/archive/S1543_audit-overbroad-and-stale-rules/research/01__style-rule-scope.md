# S1543 research 01 - the house-style rule: written scope vs enforced scope

Date: 2026-08-09. Sources: live working tree (`scripts/spec_catalog/`, `.claude/commands/`, `.claude/reference/`, `CLAUDE.md`, `AGENTS.md`), `PLAN/S1458_bash-pwsh-leading-slash-mangled.md`, `temp/done/S1340_agent-rules-gate-or-compress.md`.

Answers §6 item 1. The finding is a scope mismatch between three written homes and one gate, not a disagreement about the style itself.

## 1. Where the rule is written, and what it says

Three always-on homes, all consistent with each other:

- `CLAUDE.md:12` (section 1) - "**House text style** and its scope: one home, canon `rules/DOCUMENTATION_CONCEPT.md` section 5 'House text style'. Repo extension past that scope: long dashes are banned in `.kt` too (Rule 19, gate `scripts/quality/assert-neuroslop.ps1`)."
- `AGENTS.md:10` - the same sentence, verbatim.
- `~/.claude/CLAUDE.md` (owner's global file) - "House text style in prose and UI strings (**never in code**)".

Two consequences follow from that text alone, before any canon lookup:

1. The repo declares **exactly one** extension past the canon scope: long dashes in `.kt`. No written rule in this repository extends the style rule to `PLAN/**`.
2. The style rule's subject is prose and user-visible UI strings. A specification is neither shipped prose nor UI.

The canon copy itself lives outside this repository (`~/.claude/plugins/marketplaces/sza-unified-rules/rules/DOCUMENTATION_CONCEPT.md`), and editing it re-stamps every adopting repo. This ticket therefore changes only the repo side, aligning enforcement **down** to the already-narrower written scope. No canon edit is required or attempted.

## 2. Where the rule is enforced

One place, and it is the only one:

`scripts/spec_catalog/check-owner-inputs.ps1:91-105`, the Draft -> Approved gate. Its loop:

- iterates **the entire spec file**, index `0..$lines.Count`, not the §3.3 section it exists to validate;
- toggles `$inFence` on ```` ``` ```` so fenced code blocks are skipped (`:94-97`);
- strips inline single-backtick spans before testing (`:101`), a fix added 2026-07-02 after the owner's first complaint about this rule;
- blocks on a bare `.Contains('...')` (`:102`).

Invoked from exactly one caller: `scripts/spec_catalog/update.ps1:131-143`, and only on the transition `<any> -> Approved`. No hook, no `assert-*` gate, no `post-change.ps1` step and no CI workflow enforces the ellipsis rule anywhere else - confirmed by grepping the repo for `check-owner-inputs` (13 hits, all documentation or driver text) and by grepping `scripts/quality/` for `PLAN/` (7 hits, none style-related). `scripts/spec_catalog/_lib.ps1` carries no style check.

Three properties of that loop matter:

- It reaches **§0 "Захваченный материал (inbox)"**, the verbatim owner-capture section.
- It reaches only the top-level strategic file named in the catalog `file` field. Tactical phase files, `INDEX.md` and `research/*.md` under `PLAN/Sxxxx_slug/` are **not** gated by anything. The owner's "документы результата любых исследований" are therefore already un-gated; only the strategic spec is over-gated.
- It tests the three-dot ASCII sequence only. A real U+2026 ellipsis character passes the gate untouched, so the gate does not even enforce the typographic outcome the rule is about.

## 3. The contradiction, in the repo's own words

`.claude/commands/spec-draft.md` states both halves of an unsatisfiable pair:

- `:139` - "Capture fidelity: user's text goes into §0 verbatim (no rewriting, original language); every attachment persisted/linked. Nothing user dropped may be dropped."
- `:99` - "Paste user's free-form text verbatim (own words, original language, no rewriting)."
- `:103` - "Do NOT run `..`/`ё`/lists-over-tables sanitation (Draft exempt - sanitation is Draft→Approved gate, not drafting friction)."
- `:140` - "No sanitation sweep: Draft specs may keep rough phrasing, `...`, missing `ё`, tables. Cleanup at Draft → Approved, not here."

`.claude/reference/spec.md:116` repeats the deferral: "a `Draft` spec may keep rough phrasing, `...`, missing `ё`, or tables - clean it as part of approval".

So the drafting skill promises §0 is inviolable and simultaneously routes its cleanup to a gate that reads §0. On any spec whose captured material contains three dots, the two promises cannot both be kept: the operator must either corrupt the capture or fail the gate.

## 4. The damage is observed, not hypothetical, and it has recurred

**2026-07-02 (first occurrence).** Owner interrupted a `/spec-next` loop: "stop to change ... to .. in places you have not to! stop waste my tokns on it!" Response was to strip inline backtick spans in the gate (`check-owner-inputs.ps1:98-101`). That fix addressed the *code-span* shape only; verbatim prose outside backticks stayed exposed.

**2026-08-09 (second occurrence, this ticket).** The gate refused S1458 Draft -> Approved with `Line 29: replace three-dot ellipsis with '..' before Approved`. Line 29 was §0 captured material quoting a command whose three dots stood for elided arguments. The line on disk now reads:

> То есть MSYS принял ведущий "/spec-dev .." за POSIX-путь и подставил корень установки Git.

`PLAN/S1458_bash-pwsh-leading-slash-mangled.md:29`. The captured evidence was edited to satisfy a style rule, and the edit changed its meaning: `..` reads as a house-style pause, where `...` meant "arguments omitted". The gate did not improve any document; it damaged one and then let it through.

Two owner complaints about the same rule in five weeks is the strongest staleness signal available in this repo - stronger than any token count.

## 5. What the measurement says, and what it does not

Probe `temp/S1543/measure-ellipsis-gate.ps1` replays the gate's ellipsis half over every top-level `PLAN/*.md`:

- 300 specs scanned;
- 2 would be blocked today (`S1019_bugfix-network-resource-write-buttons-hidden.md`, 6 lines; `S1113_bugfix-vr-7k-immersive-video-not-rendering.md`, 1 line);
- 0 blocking lines sit in §0.

That 2/300 is **survivorship, not harmlessness**: the stock is clean because the gate forced every promotion to clean it. The measurable quantity is the per-promotion edit, paid once per spec forever, plus the two owner interruptions above. Removing the check cannot regress the stock, because the stock is already clean and nothing re-checks it.

The cost is not a token cost, and this ticket must not claim it is. `dev/AGENT_PROCESS_AUDIT_2026-07-31.md` measured output at **11.9%** of spend and a repo-side cut of the always-on preamble capped at ~37% of a ~64k floor; a -2.46% floor move was ~0.23% of the bill. The honest case for narrowing this rule is capture fidelity and owner interruptions, not tokens.

## 6. Prior art: what S1340 already did, and what it left

`temp/done/S1340_agent-rules-gate-or-compress.md` (Archived, closed 2026-08-01) ran the "gate the expensive rules, compress the rest" pass. Its §3.3 listed "The house-style rule's own worked example was normalised away by the very style it documents" as a fix-do-not-compress item, and its delivery note closed it as "moot - section 1 now points at the canon instead of carrying an inline example".

That closure fixed the **text** and never touched the **gate**. The gate has enforced a scope wider than the text ever since. S1543 is the residual, not a duplicate.

Two constraints inherited from S1340 bind this ticket:

- §4 - Rule 12 (no completion claim without fresh evidence) and Rule 10.1 (`temp/` hygiene) must not be compressed; they work only by sitting unconditionally in context.
- §5 - the `assert-*` inventory must not grow for cosmetic reasons. This ticket adds no new gate.

## 7. Conclusion for the tactical plan

The evidence supports exactly one narrowing, at one line range, plus the text that describes it:

- `check-owner-inputs.ps1` stops applying the ellipsis check to spec files. Its §3.3 owner-input validation - the reason the gate exists - is untouched.
- The three documents that promise "cleanup at Draft -> Approved" stop promising it.

The evidence does **not** support weakening the style rule where it is written to apply: prose in `docs/`, user-visible strings, translations. Nothing in this change touches those, because nothing in `check-owner-inputs.ps1` ever read them.
