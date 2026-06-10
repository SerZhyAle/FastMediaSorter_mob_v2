---
name: s0395-welcome-redesign-research
description: S0395 signed off 2026-06-10 with amendments; dev tickets S0396-S0402 created Draft; Play .so blocker isolated in S0401
type: project
---

S0395 (welcome-screens-redesign-research) completed and OWNER-SIGNED-OFF 2026-06-10 (with amendments). Dev tickets created as Draft: S0396 availability-contract, S0397 download-runner, S0398 skeleton+page0 (keystone, P60), S0399 profile page, S0400 functionality page, S0401 Play-compliant .so delivery (S0386 lineage, P60), S0402 permissions page. SYNTHESIS + 12 artifacts live in `PLAN/S0395_welcome-screens-redesign-research/` (PLAN is gitignored - not in git history).

**Why:** owner amendments at sign-off changed the split: Skip button removed from onboarding entirely; networks page ships DECORATIVE in S0398 (3 tiles: SMB (intranet), (S)FTP, Cloud) and S0391 gained a tiles→toggles replacement phase (standalone network ticket killed); no upgrade pointer for existing users; profile tiles FULL with minimal margins, small-screen profiles first, auto-scroll to recommended.

**How to apply:** implementation order C(S0398)→A(S0396)/B(S0397) parallel→D(S0399)/G(S0402)→E(S0400). Hard gates to not re-litigate: (1) S0400 release needs S0386 Verified; its OCR toggle on Play-acquired standard needs S0401 (Play forbids GitHub .so - Device & Network Abuse); (2) `welcome_prefs/welcome_completed` carries S0327 migration - never rename/reset; (3) recommended page order = functionality BEFORE permissions (adaptive batch); (4) S0395 itself sits at Implemented - /spec-check flips it Verified.
