---
name: new-ticket-not-reopen-for-rework
description: Rework of a shipped, working feature gets a NEW ticket - never reopen the old closed one; the old id is context only
metadata:
  type: feedback
---

When the owner changes his mind about an already delivered feature - it works as specified, he simply now finds it inconvenient or has rethought it - open a **new** ticket. Never move a closed/Verified/Archived ticket back into an active status to graft a second iteration onto it.

**Why:** stated 2026-08-13 while opening the launcher epic (S1615). His words: the old tickets "выполнились, они работают так, как они хотели.. это не про ошибку", and attaching a new iteration to "тикету, которому сто лет" is logically harder for him than reading a fresh one. A closed ticket owns its own history, its own acceptance criteria and its own release - all of which stay true; reopening invalidates none of them but muddies all of them.

**How to apply:**
- Rework / rethink / "now it is inconvenient" -> new ticket. Genuine defect in an unreleased or unverified ticket -> that ticket stays the home.
- When triaging a wish against the backlog, an existing ticket covering the same surface is **reference material** (where the code lives, why it was built that way), not grounds to close the wish as a duplicate. Only the owner may drop a wish.
- Do not offer "reopen Sxxxx" as an option in a plan or a triage report; propose the new ticket and cite the old id as context.
- Recorded as ADR-2 in `PLAN/S1615_launcher-epic-inbox.md`; that spec's triage uses a `ref:Sxxxx` marker for exactly this.
