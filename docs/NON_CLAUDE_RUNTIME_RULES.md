# Rules you enforce yourself - every runtime that is not Claude Code

Claude Code refuses these mistakes at the tool call; for you nothing refuses anything, and nothing announces the absence. This sheet is the rule text - `docs/AGENT_HOOKS.md` is the mechanism behind it, `CLAUDE.md` is the full rule set, and neither restates what is below.

## The rules

1. Run a `find` only with a concrete start path **and** `-maxdepth N` - a disk-wide walk costs minutes and returns noise; prefer `dev/CATALOG/scripts/query.ps1` or a plain glob (`guard-find-command`).
2. Never put a `.ps1` in Bash command-head position - Bash cannot execute one and the failure still returns **exit 0**, so a red build reads as green; always `pwsh -NoProfile -File ./a.ps1 <cmd>` (`guard-ps1-in-bash`).
3. Never background a gate, a closure facade or a catalog mutator - `post-change.ps1`, any `assert-*.ps1`, the `spec_catalog` CLI, `add_to_dev_log.ps1`, `catalog_sync.ps1`, `a.ps1 fk/fkn/fc/fr/fg/dq/ch/ss/bf`. A verdict nobody reads in the same turn is not a verdict; a real long job (`gradlew`, `a.ps1 d/db/dav/cd/nd/nl/r/fu`) still backgrounds normally (`guard-fire-and-forget`).
4. Never put a PowerShell cmdlet, `node`/`npm`/`npx`, or the `& { .. }` idiom in Bash - each is an `exit 127` returning nothing; run the pipeline in PowerShell or `pwsh -NoProfile -Command "<pipeline>"` (`guard-bash-unavailable-command`).
5. Double a leading slash in an argument value - Git Bash rewrites `-Reason "/spec-dev .."` into a Windows path silently, at exit 0, and the corruption lands in the lock, queue and lease diagnostics; write `"//spec-dev .."` or call from PowerShell (External: canon `guard-bash.ps1`, check 6 - it ships with the `sza` plugin under `hooks/`, it is not a script of this repository).
6. Read a long file in explicit windows - an unwindowed read of a large file wastes context you cannot get back, and you will not notice which part you missed (`guard-uncapped-read`).
7. Query `dev/CATALOG/scripts/query.ps1` before grepping `.kt` across the tree - the class index answers "where is X" in one call, a tree-wide grep in dozens (`guard-catalog-before-kt-search`, armed each session by `reset-catalog-touch-marker`).
8. Re-run an empty search without your path filter before concluding "not here" - a scoped miss is usually the wrong scope, and a wrong "not here" becomes a wrong design decision (`observe-empty-grep`).
9. Try `/quick` and `/skill-fix` before a spec pipeline - measured 2026-08-05 over the whole transcript corpus, the two cheap tiers fired 2 times against 135 for `/spec-next` and `/spec-all`, because the tier ordering was an ungated sentence and never fired (`nudge-small-task-tier`).
10. Post your own session lines: `agent-chat.ps1 -Verb Post -Kind session -Note "session started (<runtime>)"` at the start and `"session ended (<reason>)"` at the end, after setting `FMS_AGENT_ID` - the end reason is the one fact separating "died mid-phase" from "finished and left" (`post-agent-chat-session`).
11. Never end a `/spec-do` loop yourself - only the operator ends it; recover or wait, and if you are waiting, say so and keep waiting (`refuse-spec-do-stop`).
12. Run a one-way device action through an MCP tool - install, uninstall, a Maestro flow that clears app data - only on an emulator; on a physical device read `docs/DEVICE_FLEET.md` first and use `scripts/devtest/adb.ps1 install`, `uninstall -Yes` or `wipe-data -Yes`, because the device you did not check may hold content that cannot be restored (`guard-mcp-one-way-tools`).

## Before you say done

- Run `pwsh -NoProfile -File scripts/utils/preflight-checks.ps1` and read its exit code. It judges the state you leave behind - identity, session lines, held locks, queued lock tickets, outlived ticket leases - and names the remedy for each finding. Exit 0 clean, 1 something is leaked, 2 could not verify.
- It deliberately judges nothing about the tree. That is `scripts/post-change.ps1 -Files "a,b" -ScopeToFile` for a change and `pwsh -NoProfile -File ./a.ps1 fg` for the fast static gates - run those too, and read both verdicts.

## Not portable

These hooks give you no rule to follow, so this sheet states no imperative for them. They are listed by name because the sync gate in `scripts/quality/assert-hook-inventory.ps1` requires every hook in the inventory to appear here or above, and because knowing a hook exists is cheaper than wondering why it is missing.

- `warn-context-size` and `nudge-context-budget` tell the owner that a session has grown expensive. You cannot reset your own context - `/clear` and `/compact` are his - so the only action is his.
- `sweep-agent-lock-queues` evicts dead reservations left by sessions that died. It is housekeeping over other agents' state, not a decision of yours. Your own half of that obligation comes from `CLAUDE.md` Rule 23: withdraw your queue ticket with `scripts/utils/withdraw-lock-ticket.ps1` when you abandon a queued intent, and `preflight-checks.ps1` above checks you did.
- `reset-catalog-touch-marker` arms rule 7 once per session. It is the mechanism of a rule already stated, never a second rule.
