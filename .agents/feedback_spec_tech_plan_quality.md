---
name: spec-tech-plan-quality
description: Owner's recurring complaint - tactical plans had logically misordered phases and bureaucratic doc-shuffling steps; planning discipline hardened 2026-06-10
type: feedback
---

Tactical plan quality has two dominant defect classes the owner explicitly called out: (1) phases in wrong logical order (consumer before producer), (2) steps whose "work" is rearranging tactical-doc text/headers instead of changing code.

**Why:** Owner (2026-06-10): "/spec-tech недостаточно внимателен к порядку.. много бюрократии.. вместо реализации машина занимается переставлением текста в заголовках тактической документации". Each bad plan costs a full /spec-dev cycle.

**How to apply:** When authoring or reviewing any tactical plan (via /spec-tech or manually), enforce the hardened discipline now encoded in `.claude/commands/spec-tech.md` steps 3.1–3.4 + 5.5: coverage inventory of strategic spec + research artifacts, Produces/Consumes topological check, real-work filter (no PLAN/** -editing steps outside final cleanup phase), mandatory plan self-review before status flip. Do not water these sections down in future skill edits. Related convention: research findings persist to `PLAN/Sxxxx_<slug>/research/<NN>__<topic>.md`, linked from strategic §6 via `**Артефакт:**` and from tactical INDEX `Research inputs:` - never leave decision-shaping findings only in chat or temp/.
