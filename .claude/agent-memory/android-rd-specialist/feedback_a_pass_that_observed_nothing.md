---
name: a-pass-that-observed-nothing
description: Before believing a green acceptance line, name the preconditions the criterion needs and verify they exist - a device run against empty state and a static audit that never ran the flow both report success having observed nothing
metadata:
  type: feedback
---

A check reports PASS in two different situations: the behaviour held, or **the check examined nothing**.
Before trusting a green acceptance line, name the state the criterion needs and verify that state was
actually present.

**Why:** three instances on 2026-08-21, found by two sessions independently.

- **S1832** - acceptance was that pins, pin order, play history and a launcher desktop cell survive a
  schema upgrade and a prune-and-return cycle. The device carried **zero** pinned channels and no desktop
  cell addressing one. Run as written, every check passes: nothing was lost because nothing was there.
  The tell was in the source, not the log - `MIGRATION_52_53` logs how many cells it re-addressed, and its
  own comment says a `0` there cannot be told apart from "the rewrite never ran". After seeding two cells
  the line read `Launcher stream cells re-addressed to channel identity: 2`, and only then did the run
  mean anything.
- **S1697** - a full static audit reported `PASS 20 / FAIL 0, Outcome: Verified` and set the status, while
  leaving its own line `- [ ] Verify phone resources browsing on physical Wear OS device` unticked. Live
  hardware then failed one of the five criteria outright.
- **S1881** - flipped to `BlockNeedUserTest` four minutes after a layout was written, referencing a
  drawable that exists in no source set. `a.ps1 fk` was green because Kotlin compilation does not link
  resources, so nothing contradicted the claim until a packaging build was attempted.

The device-evidence case is the nastier one: it produces a real logcat line and looks *stronger* than the
static-audit case, not weaker.

**How to apply:**

- For each acceptance criterion, write down the preconditions it needs, then check they exist **before**
  running it - query the app database directly (`adb exec-out run-as <pkg> cat databases/<db>`, open it
  locally) rather than trusting the UI.
- Treat a count of `0` in a log line as "unproven", never as "nothing to do". If the code cannot tell the
  two apart, neither can you.
- Seeding preconditions is legitimate and often the only way to make a run mean something - seed the exact
  shape the app itself writes, and say in the evidence that you seeded it and why.
- Close on the half that ran, never the whole. S1715 stayed at `BlockNeedUserTest` with a clean reading
  because only the fast-model branch fired and the best-model branch had never been observed.
- A cheap check being green is not evidence the expensive one would be: `a.ps1 fk` does not link
  resources, and `a.ps1 fg` runs `assert-no-ticket-logs` **without** `-Gate`, so `fg` stays green while
  every `post-change` closure fails.

Related: [[split-device-acceptance-before-draining]], [[gate-fail-may-mean-never-ran]] (the inverse
reading), [[device-subagent-needs-known-tap-path]].
