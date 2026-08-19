---
name: canon-stamp-ahead-and-propagation
description: The 2026-08 canon propagation landed; canon working repo is P:\WEB\sza-unified-rules (NOT the plugin marketplace clone) and on 2026-08-19 it carried another session's unpushed work
metadata:
  type: project
---

The six-item canon propagation the owner approved on 2026-08-18 **landed**. This repo's `.sza-canon.json`
now claims `2026.08.18.1`, and the canon's `CANON_VERSION` reads `2026.08.18.2` - a normal one-step-stale
stamp, no longer the "stamp ahead of a version that never existed" corruption recorded here before.

**Two checkouts exist and only one is the working repo.**

- `P:\WEB\sza-unified-rules` - the working repo, has the git remote and receives edits.
- `~/.claude/plugins/marketplaces/sza-unified-rules/` - the installed plugin copy that actually runs.
  Byte-identical to the working repo apart from line endings (it is CRLF, the deployed
  `~/.claude/hooks/*.ps1` are LF). Never edit either of these two - an edit there is overwritten by the
  next plugin update and never reaches the other repos.

**Why:** a hook or rule fixed only in the installed copy silently reverts; fixed only in the working repo it
does nothing until `deploy.ps1` runs. Shipping any canon change is a four-step flow - edit, run
`hooks/tests/smoke-hooks.ps1`, bump `CANON_VERSION` + `deploy.ps1`, re-stamp every adopting repo (~10) -
which is why it belongs to a canon session and not to a project ticket.

**How to apply:**
- A ticket whose fix is a canon file is `BlockExternal` from here, with the exact replacement line and the
  four steps written into the spec. S1809 (2026-08-19) is the worked example.
- Check the canon repo's state before assuming it is free: on 2026-08-19 `main` was **three commits ahead of
  origin** with `rules/contrib/fastmediasorter_lite.md` uncommitted - another session's work in flight.
- Editing `rules/` from a project session violates the canon's own guardrail. It was done once on the
  owner's explicit instruction and recorded in `rules/contrib/fastmediasorter_mob_v2.md`; if it happens
  again, record it again rather than letting a reader infer the guardrail lapsed.
- `check-compliance.ps1` reports a stamp mismatch as `SZA-CANON03` at severity **warn**, so the run still
  exits 0 - it cannot distinguish "stale" from "ahead", and a stale stamp can sit unseen for days.

Related: [[argue-then-obey]], [[owner-decision-after-pushback]].
