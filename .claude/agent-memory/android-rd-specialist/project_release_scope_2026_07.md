---
name: release-scope-2026-07
description: Owner-declared release-gating ticket list (2026-07-02) - 11 tickets must be implemented before the next release
metadata:
  type: project
---

Owner declared on 2026-07-02 that the next release is gated on 11 tickets: S0846, S0848, S0850, S0867, S0876, S0877, S0878, S0879, S0889, S0890, S0891.

Status at declaration: S0848 Implemented (needs check/close), S0850 Approved (needs spec-tech + spec-dev), the other 9 Draft.

Progress 2026-07-03 (spec-next session): **ALL 11 gating tickets VERIFIED** - release backlog clear, /skill-release unblocked. S0876/S0877/S0867/S0879/S0848/S0850/S0878/S0890/S0846/S0891 + S0889. S0889 (emoji->app-icon in docs/site) grew large once owner chose "nearest app drawable for all, PNG": built a doc-icon pipeline (docs/icons/doc-icon-map.json, shared scripts/docs/lib/vectordrawable-svg.ps1, cairosvg installed in .venv, export-doc-icon-pngs.ps1, apply-doc-icons.ps1, assert-doc-icons-sync.ps1) + landing inline-SVG + howto/DOCS_MAP/SETTINGS_REFERENCE PNG icons, trilingual. Residual landing emoji (JS scenario-filter labels + functional glyphs ▼◐✖🔍→) parked S0907, out of S0889 scope. Triage spinoffs S0892-S0905 parked - NOT gating.

Notable per-ticket context:
- S0876 (P1, prio 60) - lost-update race in settings writers; highest-impact bugfix of the set.
- S0867 / S0877 - P2 bugfixes from the 2026-07-02 mass audit (wf_34a4d99d-fbf), same source as S0876.
- S0878 - mass-audit tail: P2 triage of 34 findings; can INFLATE release scope if triage promotes items - watch for scope creep.
- S0879 - needs owner decisions on per-profile CSV preset values (spec-quiz candidate).
- S0889 - large mechanical trilingual docs/site icon inlining (S0815 iteration 2); zero code risk.
- S0891 - trivial orphaned-asset delete; S0846 - trivial dialog-cancel-style fix; S0890 - small icon-map dedup.

Confirmed 2026-07-03 12:56 (fresh session): all 11 ids re-checked via `select.ps1 -Id <id> -Format json` -> every one is `Archived` (dev/CHANGELOG.md shows the `spec-arc` batch at 10:26:0x same day). **This gating list is fully closed - no open release-blocking tickets remain from it.** No new gating list has been declared by the owner since. The catalog still carries ~125 open tickets (mostly `BlockNeedUserTest` awaiting device verification, plus `Draft` backlog ideas) but none are marked release-blocking - normal backlog, not a gate.

**Why:** Release planning happens across many sessions; the gating list is not derivable from the catalog (priorities do not encode "release-blocking").

**How to apply:** When picking next work (/spec-next, /release), prioritize these ids until all reach Verified/Archived. Before /skill-release, confirm every id is closed. Verify statuses live via select.ps1 - this snapshot decays. If asked "any release-gating work left" and this list is closed with no new owner declaration, report clear and ask whether to declare a new gate or proceed straight to /spec-prerelease + /skill-release.
