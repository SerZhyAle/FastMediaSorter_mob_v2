---
name: canon-working-copy
description: Where the SZA canon repo actually lives on this machine, why it is usually stale, and why the hand-wired hooks in ~/.claude/settings.json are still load-bearing
metadata:
  type: reference
---

The canon (`SerZhyAle/sza-unified-rules`) has **two copies on this machine and they are not the same
thing**:

- `~/.claude/plugins/marketplaces/sza-unified-rules/` - the **git working copy**. This is where canon
  edits are authored, committed and pushed from. It is routinely several commits behind `origin/main`
  and does **not** auto-pull: check `git rev-list --left-right --count HEAD...origin/main` before
  writing anything, or you will bump `CANON_VERSION` from a stale base.
- `~/.claude/plugins/cache/sza-unified-rules/sza/1.0.0/` - the **installed** plugin copy the harness
  actually loads. It lags the working copy by much more (it was still on `2026.07.27` on 2026-08-08,
  two canon releases behind) and its `hooks/` held only `session-start.ps1`.

**Why:** the consequence is easy to get wrong in both directions. The canon *ships* the guard hooks in
its `hooks/` folder and `hooks/README.md` says to remove any hand-wired copies - but that advice only
applies once the installed cache carries them. Until the plugin refreshes, the registrations in
`~/.claude/settings.json` (`guard-find-command`, `guard-ps1-in-bash`, `guard-fire-and-forget`,
`guard-uncapped-read`, `warn-context-size`) are the **only** thing making those guards live. Deleting
them "because the plugin has them now" would silently disarm every guard.

**How to apply:**
- Authoring a canon change: `git fetch` + fast-forward the marketplace clone first. If another session
  left uncommitted work there (it happens - other repos' contrib records get drafted in that clone),
  back it up to `temp/scratch/`, stash, pull, pop, and stage **only your own files**.
- A new guard has to be installed twice to be live now: into the canon `hooks/` (for the portfolio) and
  into `~/.claude/hooks/` + `~/.claude/settings.json` (for this machine, today).
- Before removing a hand-wired hook, verify the installed cache copy actually contains that `.ps1` -
  the marketplace clone containing it proves nothing.
- Editing any `rules/*.md` changes the core digest and marks **every** stamped repo stale; only the
  originating repo gets re-stamped in the same pass unless a full reconcile is explicitly asked for.
