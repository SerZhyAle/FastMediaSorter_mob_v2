---
name: release-scope-2026-07
description: Owner-declared release-gating ticket list (2026-07-02) - 11 tickets must be implemented before the next release
metadata:
  type: project
---

Owner declared on 2026-07-02 that the next release is gated on 11 tickets: S0846, S0848, S0850, S0867, S0876, S0877, S0878, S0879, S0889, S0890, S0891.

Status at declaration: S0848 Implemented (needs check/close), S0850 Approved (needs spec-tech + spec-dev), the other 9 Draft.

Progress 2026-07-03 (spec-next session): ALL 11 gating tickets dispositioned. Verified - S0876, S0877, S0867, S0879, S0848, S0850, S0878 (P2 triage 65/65 -> spawned S0893-S0905, NOT gating), S0890 (5 copies deduped, new CloudProviderIconMap), S0846 (premise corrected: Button.Text, not DialogCancel), S0891 (orphaned logos deleted). S0889 -> BlockQuestions - the ONLY human gate before /skill-release (3 questions in spec, /spec-quiz ready). Spinoff drafts S0892-S0905 parked - NOT release-gating.

Notable per-ticket context:
- S0876 (P1, prio 60) - lost-update race in settings writers; highest-impact bugfix of the set.
- S0867 / S0877 - P2 bugfixes from the 2026-07-02 mass audit (wf_34a4d99d-fbf), same source as S0876.
- S0878 - mass-audit tail: P2 triage of 34 findings; can INFLATE release scope if triage promotes items - watch for scope creep.
- S0879 - needs owner decisions on per-profile CSV preset values (spec-quiz candidate).
- S0889 - large mechanical trilingual docs/site icon inlining (S0815 iteration 2); zero code risk.
- S0891 - trivial orphaned-asset delete; S0846 - trivial dialog-cancel-style fix; S0890 - small icon-map dedup.

**Why:** Release planning happens across many sessions; the gating list is not derivable from the catalog (priorities do not encode "release-blocking").

**How to apply:** When picking next work (/spec-next, /release), prioritize these ids until all reach Verified/Archived. Before /skill-release, confirm every id is closed. Verify statuses live via select.ps1 - this snapshot decays.
