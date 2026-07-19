---
name: launcher-roadmap-greenlit-2026-07
description: S0404 launcher roadmap owner-greenlit 2026-07-18 via batch quiz; children S1087-S1104 now drivable Drafts, build S1088 dialog first; S1098 archived
metadata:
  type: project
---

On 2026-07-18 the owner greenlit the whole launcher roadmap (epic S0404) via a batch `/spec-quiz` (16 decisions, master gate Q0=A). The tactical freeze that previously parked its child feature tickets as `BlockQuestions` no longer applies.

**Why:** during the earlier `/spec-next` launcher run these tickets were blocked precisely because S0404 was owner-gated and each child needed an explicit greenlight + design call. The owner has now given all of them in one sheet (`temp/scratch/launcher-quiz-questions.md`).

**How to apply:**
- Treat launcher feature tickets (S1087, S1091, S1093, S1094, S1103, S1107, ..) as drivable Drafts - do NOT re-block them as "needs greenlight / owner-gated". They were restored BlockQuestions -> Draft.
- Build order: **S1088** (System launcher settings dialog) is the FOUNDATION - author + build it first. S1090 (lock desktop) and S1101 (wallpaper) stay `BlockByOtherTask(S1088)` until it lands; S1102 (launcher user docs) stays blocked until the feature surface lands.
- **S1098** (clickable status indicators) was ARCHIVED - owner deprioritized the status-area replace path (S1087 stays default-OFF, `S1098-1: N/A`). Revive only if that path is later prioritized.
- Non-obvious design decisions are locked in each spec's `### Quiz decisions (2026-07-18)` block. Deviations from the recommended default worth remembering: S1093 resize = drag-handles + any-widget-can-go-fullscreen (no per-widget cap); S1103 "scheduled operations" cell triggers the op directly (not open the schedules screen); S1107 onboarding-launcher fix = variant B (deferred HOME-role request from Settings/Main, amends ADR-2's "chooser on next Home press").
- S1107 is pri 90 (bug) and fully diagnosed - fast-track candidate ahead of the pri-50 feature tickets.
