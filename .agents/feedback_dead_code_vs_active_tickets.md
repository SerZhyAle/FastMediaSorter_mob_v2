---
name: dead-code-vs-active-tickets
description: Before deleting "dead" code, cross-check the spec catalog for active (esp. Partial/In Progress) tickets that own it as scaffolding
metadata:
  type: feedback
---

Before any dead-code / unused-artifact deletion sweep, cross-check every "dead" symbol against active spec tickets - grep `PLAN/` for the symbol AND list non-terminal journal statuses (Draft/Approved/Tactical/In Progress/Partial/Block*). A class that is "never thrown / 0 references / 0 injectors" can be **deliberate scaffolding for an unfinished ticket**, not dead code.

**Why:** During S0385 (APK/AAB dead-weight reduction) a sub-agent flagged `HostKeyMismatchException` (`data/remote/sftp/PinnedHostKeyRepository.kt`) as dead - 0 throws, 0 catches. It is actually scaffolding for **S0046 `sftp-key-auth-hardening` (status Partial)**, whose unfinished Phase 05 wires it into `AddResourceSftpKeyCoordinator.kt`. It reads as dead precisely because the throwing code lives in S0046's not-yet-done phases. Deleting it would have broken an in-flight ticket. The owner caught this by asking "is this dead code related to any in-progress ticket?" - a check I should run unprompted.

**How to apply:** In the research/plan stage of any dead-code work: (1) `Grep PLAN/` for each candidate symbol; (2) list active tickets from `spec-catalog.jsonl`; (3) any candidate referenced by a `Partial`/`In Progress`/`Block*` spec → exclude from deletion, add a cross-ticket guard note, link the ticket in §10. `Partial` is the highest-risk status - it means "half-built on purpose". Also scan for thematic overlap with In-Progress hygiene tickets ([[feedback_search_duplicates_by_symptom]]) - e.g. S0381/S0383 neuroslop-hygiene overlap with dead-code/resource cleanup; coordinate `build.gradle.kts`/`strings*.xml`/keep-rule edits to avoid collisions.
