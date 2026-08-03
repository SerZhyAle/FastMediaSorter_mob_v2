---
name: sync-docs-on-visible-change
description: Whenever visible functionality changes, revisit & edit the affected doc sections and the program's website
metadata:
  type: feedback
---

Whenever a change touches user-visible functionality (UI, behaviour, a shipped feature), proactively revisit and edit the relevant documentation sections AND the program's public website/site copy where the change matters - do not wait to be asked.

**Why:** Owner explicitly asked (2026-06-29) for this as a standing habit, after noticing docs/site drift behind shipped functionality. The repo already enforces narrow slices mechanically (Rule 22 settings-manifest sync, FEATURES policy via `/skill-release`, `/doc-update`), but the owner wants the broader reflex: any visible-behaviour change → check whether a doc/site section is now stale.

**How to apply:**
- On any spec/change that alters visible behaviour, ask "which doc section or site page describes this, and is it now wrong?" before closing.
- Use existing channels, don't free-hand: `/doc-update` for mirrored docs, `scripts/all_features/add.ps1` for the capability inventory, `/skill-release` owns `docs/FEATURES*.md` + site publish. This habit decides *when* to touch docs; the tooling decides *how*.
- Website/site copy lives outside per-spec edits - flag it for the release flow rather than editing showcase files per-ticket.
- Backfill task S0814 was the one-off catch-up for docs/site drift already accumulated; this memory is the going-forward reflex.
