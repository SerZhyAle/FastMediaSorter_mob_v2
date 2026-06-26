# Shared-State Mutation Audit - Agent Prompt (S0703)

This is the agent-side stage of the S0703 cross-project audit for multi-layer, redundant, and
unsafe mutation of shared state. The mechanical pre-filter
(`scripts/quality/audit-shared-state-writers.ps1`) harvests ranked candidates; this prompt drives
a research agent that adjudicates them - it reasons about indirect writers and data-side
concurrency that the regex pre-filter cannot, adversarially refutes the top findings, and lists
the survivors as `/spec-draft` candidates. Copy the block below verbatim into the agent.

```text
ROLE: You are auditing this Android project for multi-layer, redundant, and unsafe
mutation of SHARED STATE across the whole codebase. Cover BOTH surfaces:
(A) UI interface objects and (B) data / state holders. Produce a ranked report,
not raw grep dumps. Do NOT fix anything - only detect, classify, and propose parking.

SCOPE: all Kotlin/Java sources of the app modules (main + Wear), across all flavor
source sets. Treat read-only zones as out of scope.

SURFACE A - UI OBJECTS. For every interface object, collect ALL writers of its
observable properties: visibility (visibility / isVisible), enabled state, alpha,
text, focusable/clickable/nextFocus. Include INDIRECT writers - assignments made
through a local/loop/`it` variable, inside `apply{}`/`with{}`, or via helper
functions and lambdas - because a name-based search over the object id alone misses
exactly the most dangerous case (a generic layout/partition manager that rewrites a
property for a SET of objects in a loop).

SURFACE B - DATA / STATE HOLDERS. For every shared state carrier, collect ALL write
and invalidation sites: persistent DB rows/entities, reactive state streams
(StateFlow/MutableStateFlow/LiveData), preference/settings storage, in-memory caches,
shared mutable collections, and singletons holding mutable state. Note who writes,
from which layer, and under which coroutine/thread/scope.

GROUPING (kills false positives): group writers by ACTUAL co-existence domain, NOT by
object name. Same-named objects living on different screens / binding types / source
sets that never co-exist in time are NOT a conflict. A real conflict is multiple
authoritative writers to the SAME live object within one ownership domain.

UNSAFE-PATTERN CLASSIFIER - flag and explain each hit:
  1. Authority conflict: a generic loop/partition writer over a property set coexisting
     with a point semantic writer of the same property (the list/grid toggle bug class).
  2. No single owner: a property/field written from >=2 collaborators on one screen with
     no clear last-writer contract.
  3. Ordering race: outcome depends on subscription order, frame timing, or layout pass.
  4. Concurrency hazard: writes to the same carrier from different coroutines/threads/
     scopes without synchronization or single-writer confinement.
  5. Redundancy: repeated writes of an already-equal value, or duplicated derivation of
     the same state in multiple layers.

OUTPUT: a table-free ranked list. For each finding give: object/carrier, ownership
domain, list of writers (file + symbol), surface (UI/data), pattern id from above,
a one-line why-it-is-unsafe, and a confidence level. Rank by writer count x severity.
Separate "confirmed conflicts" from "likely false positives" and say why.

VERIFICATION: for the top findings, adversarially try to REFUTE the conflict (could the
writers be mutually exclusive by guard/state?). Keep only those that survive refutation.
For ordering races that static analysis cannot prove, propose a debug-only write-tracer
(log writer identity + value per frame/transaction) as the runtime confirmation step.

DELIVERY: list each surviving non-trivial conflict as a `/spec-draft` candidate (symptom
+ evidence), so the caller can park them. Never switch task to fixing them.
```

## How to run

Two stages.

1. **Harvest candidates (mechanical pre-filter).** Run
   `pwsh -NoProfile -File scripts/quality/audit-shared-state-writers.ps1 -Surface all -Json temp/shared-state-audit.json`.
   It scans both app modules, groups writers by ownership domain, flags `generic-loop-writer` /
   `no-single-owner` / `cross-scope-write`, prints a ranked summary, and writes the full result to
   `temp/shared-state-audit.json`. This stage is regex-level only - it surfaces candidates and is
   deliberately over-inclusive (e.g. same-named local carriers across files may merge).
2. **Adjudicate (authoritative).** Hand `temp/shared-state-audit.json` plus the prompt block above to
   a research agent. The agent stage is authoritative for indirect writers and concurrency reasoning:
   it confirms or refutes each candidate, separates real conflicts from false positives, and lists the
   survivors as `/spec-draft` candidates.

Use `-Surface ui|data` to scope a run, `-Top N` to widen the printed list, and `-MinWriters N` to
change the multi-writer threshold.
