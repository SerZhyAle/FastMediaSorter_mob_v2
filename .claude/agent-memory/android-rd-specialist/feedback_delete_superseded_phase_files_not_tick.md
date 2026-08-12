---
name: delete-superseded-phase-files-not-tick
description: When a later ticket supersedes a tactical plan, delete the obsolete phase files instead of ticking them done - /spec-dev resumes at the first non-done step and would execute work that undoes the newer ticket.
metadata:
  type: feedback
---

**Rule.** A tactical plan whose phases were superseded by a *later* ticket must have those phase files
**deleted** and the INDEX rewritten to the work actually delivered - never ticked `✅ Done` and never left
`⬜ Not started`.

**Why:** on 2026-08-09, S1410 (split the launcher "show clock and status" option in two) still carried a
five-phase plan authored 2026-08-06: split the stored flag, split tray visibility, add a second settings row,
retire the legacy flag. S1415 landed the same day with a finer answer - six independent `launcherTrayShow*`
flags, each with its own row, DataStore key and reset entry. `/spec-dev` resumes at the *first non-done step*,
so leaving those phases `⬜ Not started` invites a later run to split a flag that no longer exists and add a
row duplicating `rowLauncherTrayClock` - actively undoing S1415. Ticking them `✅ Done` is the other failure:
it certifies work nobody did, and the next audit reads the plan as evidence.

**How to apply.**

- The tell is a resumed ticket whose tactical plan predates a sibling ticket touching the same subsystem.
  Check the sibling's status and read the live code before running any phase.
- Delete the obsolete phase files, write one phase describing what was actually delivered, and put the reason
  in the INDEX Change Log and scope note - the deletion has to explain itself, or the next reader restores it.
- The strategic spec goes stale in the same move: §1, §6 and §9 keep describing a problem that no longer
  exists. Fix them in the same pass, not at audit time.
- Whether the superseded ticket still has residual work is the owner's call - see
  [[old-capture-may-be-superseded]]. Only the plan surgery is mechanical.

**Related memories:** [[drift-check-false-positive-on-commit-mention]],
[[feedback_tactical_plan_file_list_may_be_wrong]], [[dead-code-vs-active-tickets]].
