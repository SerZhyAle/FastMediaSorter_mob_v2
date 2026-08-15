# Specification: S1345 - Agent-memory cross-link hygiene

**Ticket:** S1345
**Status:** Archived
**Priority:** 30
**Date:** 2026-08-01
**Tier:** 1

---

## 0. Origin

Parked by `/spec-all S1338` during phase 08 (agent memory). Out of scope for that phase, which
owns the budget gate and the prune - not the link convention.

Raw finding, verbatim from the measurement:

> `[[name]]` cross-links inside `.claude/agent-memory/android-rd-specialist/*.md` do not follow one
> convention. Some name the frontmatter `name:` slug (`[[detekt-scoped-gate-line-shift]]`), some the
> file stem (`[[feedback_detekt_scoped_gate_line_shift]]`), some a kebab variant of the stem, and a
> handful name nothing that exists at all.
>
> Checked strictly against frontmatter `name:` values across 219 files: **133 links do not resolve**.
> Most of those resolve if file-stem matching is also allowed, so the real defect is the missing
> convention rather than 133 dead links. Genuinely dead targets found in the same pass:
> `[[reference_script_help_cheatsheet]]`, `[[s0199-vr-render-cleanup]]`,
> `[[project_s0392_player_family_parity]]`, `[[S0607]]`, `[[welcome_redesign]]`,
> `[[maestro-device-test-engine]]`, `[[project_s0386_delivery_pause]]`, `[[s0232]]`,
> `[[debug-tag-invariant]]`, `[[user_author_style]]`, `[[feedback-pwsh-path]]`,
> `[[sync-docs-site-from-tickets]]`.
>
> Evidence: the sweep is reproducible - build a map of frontmatter `name:` values across the memory
> directory, then match every `\[\[([^\]]+)\]\]` against it.

Pre-existing, not caused by S1338: the 15 files S1338 phase 08 deleted appear as a link target in
none of the dangling entries.

---

## 1. Why it matters

A cross-link that does not resolve is a read that fails or, worse, a read the agent does not attempt.
The links exist to turn a single memory into a cluster - "you are looking at the detekt baseline
trap, the scoped-gate trap is next door". Half of them silently do nothing.

Low priority: nothing is broken at runtime and no gate depends on it. It is trust hygiene, the same
class as the dead-path advisory already in `scripts/quality/assert-memory-budget.ps1`.

---

## 2. Scope

- Pick ONE convention - frontmatter `name:` slug is the documented one in the agent's own memory
  instructions, so that is the likely answer.
- Normalise every `[[...]]` in the corpus to it.
- Delete or repoint the genuinely dead targets listed in section 0.
- Extend `scripts/quality/assert-memory-budget.ps1` with a third advisory: an unresolvable `[[link]]`.
  Advisory, not fatal, for the same reason as its two existing advisories.

Out of scope: pruning memory files, the index budget, anything under `docs/`.

---

## 3. Open items

- Confirm the convention against the agent-memory instructions before rewriting 219 files.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none - parked from S1338 phase 08, no other open ticket touches this corpus.

---

## Goal

Нормализуем все `[[...]]` кросс-ссылки в `.claude/agent-memory/android-rd-specialist/*.md` к одной
конвенции - фронтматтер `name:` (она уже задокументирована в собственных инструкциях агента: "link
related memories with [[name]], where name is the other memory's name slug"). Диагностика показала,
что дело не в 133 битых ссылках, а в отсутствии единого способа их писать: большинство совпадает по
основе имени файла (stem) или его kebab-варианту без типового префикса - это чинится механически.
Из подлинно нерешаемых 14 ссылок пять оказались архивными тикетами Sxxxx, по ошибке обёрнутыми в
`[[...]]` (не память вообще), остальные - опечатки или ссылки за пределами этого корпуса; каждая
разобрана и починена вручную. Заодно нашлись 3 файла с легаси-именем `name:` в виде предложения
вместо слага - тоже не резолвились ни при каких условиях, поэтому получили нормальный слаг.

## Phase 1 - Normalize cross-links and add a permanent advisory gate

- [x] Fix 3 legacy free-text `name:` frontmatter values that were not usable as link targets at all
  (`project_build_gotchas.md` -> `build-gotchas`, `feedback_bottomsheet_menu_untappable_emulator.md`
  -> `bottomsheet-menu-untappable-emulator`, `feedback_timber_tags_before_test.md` ->
  `timber-tags-before-test`).
- [x] Normalize 116 fallback-resolvable `[[link]]` occurrences across 77 files to their canonical
  `name:` form (stem / kebab-stem / no-prefix variants all converged to one spelling).
- [x] Resolve the 14 links with no mechanical fallback: 5 were archived spec tickets (S0199, S0232,
  S0392, S0386, S0607) wrongly wrapped in `[[...]]` - unwrapped to plain ticket-reference text; 1 was
  a typo (`scaffolding-as-done` -> `no-scaffolding-as-done`); 1 pointed to the wrong memory
  (`players-are-a-family` -> `player-family-glue-mirroring`); 1 pointed to a plausible-but-wrong
  memory (`user_author_style` -> `writing-style-dashes-yo-ellipsis`); 6 referenced concepts with no
  dedicated memory in this corpus (a different memory scope entirely, a CLAUDE.md rule, a spec-only
  concept, or pure redundant self-reference) - converted to plain text, no link.
  - **Verification:** re-running the scan script's read-only mode reports 0 fallback-fixable and 0
    dead links across all 219 files.
- [x] Extend `scripts/quality/assert-memory-budget.ps1` with a third advisory - an unresolvable
  `[[link]]` target, using the same name/stem/kebab/no-prefix resolution the one-off scan used.
  Advisory only, matching the existing two (dead paths, expired tickets) - a hard failure here would
  train the operator to bypass the gate, per the script's own stated design.
  - **Verification:** `Grep "advisory 3"` (or equivalent marker) in the script; a fresh
    `assert-memory-budget.ps1` run reports 0 unresolvable links against the now-normalized corpus.
- **Verification:** `standard debug` is unaffected (this ticket touches no `.kt`/build file) - the only
  validation is the script's own report and a manual read-through of every hand-fixed line above. No
  device test needed.

## Last Audit

**Date:** 2026-08-02
**Mode:** strategic (compact spec, Simple path)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 6 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

All 219 memory files (excluding `MEMORY.md`) now carry a slug-shaped `name:` frontmatter value
(3 legacy free-text names fixed) and every `[[link]]` in the corpus resolves to one via the
diagnostic scan's read-only mode: 0 fallback-fixable, 0 dead, 203 links already in canonical form
after the pass (up from 82 before). The 14 pre-fallback-dead links were each individually resolved:
5 turned out to be archived spec tickets (S0199, S0232, S0392, S0386, S0607) mistakenly wrapped in
`[[...]]` and are now plain ticket text; 1 typo, 1 wrong-memory pointer, 1 plausible-but-wrong
pointer, 6 converted to plain text (different memory scope, a CLAUDE.md rule, spec-only concepts, or
redundant self-reference). `scripts/quality/assert-memory-budget.ps1` gained the third advisory
described in §2, confirmed live via a fresh run: 0 unresolvable-link findings against the normalized
corpus (the pre-existing dead-path and expired-ticket advisories are unaffected, out of this
ticket's scope). `post-change.ps1 -ScopeToFile` on the one script touched: PASS. No `.kt`/build file
touched, so no compile/build check applies.

### Manual / on-device

- None. This ticket has no on-device or build-verifiable surface.
