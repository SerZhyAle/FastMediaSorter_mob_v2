# Claude Audit: Research and Development Speed/Quality

Generated: 2026-05-20 12:48
Author: Claude `android-rd-specialist` (Sonnet, VS Code Claude Code extension)
Branch observed: `DEBUG-v004`
Scope: workstation, OS, repo rules, scripts, catalogues, MCP, VS Code config, and my own harness/memory layer - from the perspective of how fast and how accurately I can do research and development in this project.

This file deliberately does not repeat what `dev/codex_audit.md` and `dev/gemini_audit.md` already cover. It cites them where the conclusions still hold and where the fix is still outstanding, but it spends its bullets on what is specific to my agent.

## Executive Summary

- Hardware and OS are not the bottleneck. i5-13600K, 20 logical processors, ~64 GB RAM, Windows 10/11 build 26200, PowerShell 7 cold start ~200..500 ms. The same numbers as codex saw.
- My biggest friction is **shell mismatch**: my `Bash` tool runs under git-bash, and `pwsh` is not on its `PATH`. Every PowerShell call I make has to go through the full path `"/c/Program Files/PowerShell/7/pwsh.exe"`. This is documented in my memory (`feedback_pwsh_path.md`), but every fresh conversation pays the same discovery cost until the bootstrap is made explicit.
- The persistent memory layer at `.claude/agent-memory/android-rd-specialist/` is a real differentiator. 23 memory files encode lessons that already prevented repeat mistakes (Timber tag invariant, scaffolding-as-done, flavor isolation, manifest srcFile, build-output truncation). Peer-agent audits (`codex_audit.md`, `gemini_audit.md`) have no such layer - they re-derive context every session. **Maintain this. Expand it for sibling agents.**
- The two prior audits already enumerated fixes that **have not landed**. `scripts/post-change.ps1` still ships without `-NoProfile` and still bypasses `scripts/catalog_sync.ps1`. `.vscode/settings.json` still does not exclude `**/node_modules/**` or `temp/`. These are 5-minute patches with daily payoff. See §7.
- The worktree is wide-open dirty (30+ modified files including `CLAUDE.md`, `AGENTS.md`, `app_v2/build.gradle.kts`). Any of my edits to those files race with the operator's in-flight work. **Dirty-tree awareness must be a first-class part of my preamble.**

## 1. Environment Baseline (verified now, not assumed)

- OS: Windows 10/11, build 26200 (PowerShell reports `Microsoft Windows NT 10.0.26200.0`).
- CPU: 13th Gen Intel Core i5-13600K, 20 logical processors.
- RAM: ~63.7 GB.
- PowerShell: 7.x (`pwsh.exe` lives at `C:\Program Files\PowerShell\7\pwsh.exe`).
- Git branch: `DEBUG-v004`. Last commit `85b122bf`, message `2605192322`.
- Gradle: 9.4.1; AGP 9.2.1; Kotlin 2.2.10; daemon on, parallel on, configuration cache **off** (Chaquopy 17 / noLegal constraint - documented in `gradle.properties`).
- My harness: VS Code Claude Code extension. `chat.tools.terminal.autoApprove` is configured with one specific entry; otherwise permissions are gated by `.claude/settings.json` (very narrow: 14 lines, mostly `Bash(./gradlew.bat *)`, `Bash(git *)`, `Bash(.\scripts\* *)`, `Edit/Read/Write/Glob/Grep(**)`).

## 2. What Is Already Working Well (for me specifically)

- **Persistent memory at `.claude/agent-memory/android-rd-specialist/`** - 23 files, indexed by `MEMORY.md`. Auto-loaded on every conversation, survives interruptions. This is the single highest-leverage thing in my toolkit and it already pays off.
- **Skill catalogue** under `.claude/commands/` - 24 skills (`/spec*`, `/build`, `/catalog`, `/log-reader`, `/quick`, `/ui-clarify`, etc.). Each one bundles a rituals checklist so I don't re-derive them per request.
- **Catalog system** (`dev/CATALOG/scripts/query.ps1`) - faster than `Grep`/`Glob` for Kotlin class lookup. Memory tells me not to use `find` for `.kt` files.
- **Spec catalog operator facade** (`scripts/spec_catalog/`) - `select.ps1`, `next-id.ps1`, `update.ps1`, `archive.ps1`. The single CLI path replaces hand-editing JSONL.
- **`a.ps1` two-letter facade** - 21 aliases including `dq` (quiet debug build) and `ss` (stale-spec list). Saves typing the full builder path every time.
- **`scripts/catalog_sync.ps1` wrapper** - correctly chains `scan.ps1` + `render.ps1` in one process. Memory already pins this as the right entrypoint.

## 3. Friction Specific to Me

### 3.1 Bash vs PowerShell shell split

My `Bash` tool runs under git-bash on Windows. Everything related to project automation runs in PowerShell. Every PowerShell invocation from me needs `"/c/Program Files/PowerShell/7/pwsh.exe" -NoProfile -File ...`. The full path is the gotcha - plain `pwsh` errors with "command not found" in this shell, and there is no symlink.

**Cost:** every fresh conversation I either remember from memory or pay one failed tool call. Today this very audit cost me one such failed call before I switched.

**Fix candidates:**
- Add `pwsh` shim to git-bash `PATH` via `.bashrc` or set up an alias in the repo (`scripts/bin/pwsh` wrapping the full path).
- Add a `Bash(pwsh *)` line to `.claude/settings.json` allowlist, and have memory advertise the simple form. Actually no - the issue is PATH, not permission. A shim is the right fix.
- Cheapest: create `scripts/pwsh` (no extension; uses `#!/usr/bin/env bash` and execs the Windows pwsh path). Reachable from git-bash because `scripts/` is on most operator workflows already.

### 3.2 Dirty worktree, broad scope

Right now `git status --short` reports 30+ modified files. Many are infra-critical:

- `CLAUDE.md`, `AGENTS.md`, `.github/copilot-instructions.md` - the rule files I read at session start.
- `app_v2/build.gradle.kts` - the flavor/version source of truth.
- 25+ `.kt` files across `core/`, `data/`, `domain/`, `ui/`.

If I `Edit` any of those, my changes interleave with the operator's uncommitted work. None of my prompts currently force me to summarise the dirty area first.

**Fix:** add `scripts/agent_bootstrap.ps1` (codex proposed the same thing). It should:
- print branch, last commit, dirty-tree count grouped by area,
- highlight if any of my likely-touched files are already modified,
- print active `S` ticket (if `git log` or commit message mentions one),
- print pwsh path,
- print "next-step suggestion" by request classifier.

This becomes my standard preamble. One tool call, ~300 ms.

### 3.3 `scripts/post-change.ps1` still broken

Codex flagged it on 2026-05-20. Today, 2026-05-20 12:48, lines 24-55 still show the bug:

- Line 24-28: resolves `$pwsh` path, then **calls `& $pwsh -File ...` without `-NoProfile`** (lines 43, 47, 50, 58). Every step spawns a profile-loading child.
- Lines 47-51: calls `scan.ps1` and `render.ps1` as two separate child processes instead of `scripts/catalog_sync.ps1` (which does it in one).

For a typical `.kt` change this is 3-4 wasted profile loads ~ 600-800 ms of pure overhead per post-change step.

**Fix:** one PR. Change all `& $pwsh -File ...` to `& $pwsh -NoProfile -File ...`. Replace the scan+render pair with a single call to `scripts/catalog_sync.ps1 -Module $Module`. Five-minute change; I will not do it inside this audit because the worktree is dirty and `post-change.ps1` is not currently in my mandate.

### 3.4 Stale `CLAUDE.md` instruction about catalog commits

CLAUDE.md §"Post-Change Steps" step 5 says "Commit updated `dev/CATALOG/<module>.jsonl` + `<module>.md` together with the code change." Memory `project_build_gotchas.md` records: both files are **gitignored**. `git check-ignore` confirms.

So I am routinely told to commit two files that git will not track. Every fresh conversation reads CLAUDE.md and may try to obey the instruction.

**Fix:** edit CLAUDE.md to say "Run `scripts/catalog_sync.ps1 -Module <m>` after every `.kt` change; the resulting `*.jsonl` and `*.md` are local indexes (gitignored) - regenerate, do not commit."

### 3.5 No native catalog parser

`dev/CATALOG/app_v2.jsonl` is plain JSONL. I can `Read` it directly. But every doc says "use `query.ps1`" which shells out to PowerShell with all the cold-start cost.

For very narrow queries (class name lookup, role filter) PowerShell is overhead. I should be allowed to read `dev/CATALOG/app_v2.jsonl` directly when the query is simple enough.

**Fix:** add a one-line note to `dev/CATALOG/README.md`: "For single-class lookup, agents may read `<module>.jsonl` directly. For multi-filter / role-cross-reference queries, use `query.ps1`." This unblocks the fast path without abandoning the canonical tool.

### 3.6 Build-output truncation trap

Memory `feedback_build_output_pipe_truncation.md` records: `./gradlew.bat <task> 2>&1 | tail -30` silently swallows the FAILURE block - it sits in the middle of the output. I have lost turns on this in S0250.

**Fix:** wrap the build so failure-recovery is always full-log. Either:
- `scripts/builders/build-debug.PS1` already writes a log under `temp/`; document the path in `a.ps1 dq` so I can grep instead of piping; or
- add `scripts/builders/build-log-tail.ps1 -Lines 200 -OnFailureOnly` that the agent calls *after* `a.ps1 dq` to capture the diagnostic deterministically.

### 3.7 Per-agent memory directories are sparse

`.claude/agent-memory/` contains only `android-rd-specialist/`. Other agents listed in the prompt (`android-kotlin-developer`, `android-solution-researcher`, `friendly-android-doc-writer`, `claude-code-guide`) have no memory directory at all. Every conversation with them starts with zero context.

**Fix:** when any of those agents handles something non-trivial, seed `.claude/agent-memory/<name>/MEMORY.md` and one or two starting feedback files. Cost: low. Payoff: cumulative.

### 3.8 No request classifier / no startup packet

I land in a conversation and have to:

1. Read CLAUDE.md (every time).
2. Re-discover branch, dirty status, pwsh path.
3. Scan MEMORY.md.
4. Find the active S ticket if the request mentions one.
5. Pick the right skill.

Codex called this out. The fix is a single `scripts/agent_bootstrap.ps1`. It should be cheap enough that I run it at the start of any non-trivial task, not just substantial ones. Output budget: <40 lines.

### 3.9 Sub-agent prompts cost more than they should

CLAUDE.md instructs parallel sub-agent spawning. Each sub-agent reads CLAUDE.md fresh - no inherited context. For a routing question ("which file holds X") this is wasteful: I should `Read dev/CATALOG/app_v2.jsonl` directly or call `query.ps1`, not spin up a sub-agent.

**Rule of thumb I want to add to memory:** sub-agent makes sense when the task is (a) genuinely parallel to my main thread *and* (b) substantial enough (≥10 tool calls) to amortise the CLAUDE.md re-read. For everything smaller, do it inline.

### 3.10 String localisation script needs prefix discovery help

`scripts/check_strings_localized.ps1 -KeyPrefix "<key>"` requires me to know the key prefix in advance. If I just added two unrelated strings (`foo_label`, `bar_title`), I need two runs. A `-KeysAdded "foo_label,bar_title"` mode would be more accurate when I do know exact keys.

## 4. Research Speed: Concrete Recommendations

Ranked by daily payoff.

1. **`scripts/agent_bootstrap.ps1`** (HIGH). One call at session start. Prints branch, dirty summary, pwsh path, active S ticket, request-classifier suggestion. Saves 3-5 tool calls per session.
2. **`scripts/pwsh` shim** (HIGH). Resolves the bash-vs-pwsh PATH problem at the source. Five-line bash script + chmod +x. Lets me write `pwsh -NoProfile -File foo.ps1` instead of the absolute path.
3. **`.vscode/settings.json` exclusions** (HIGH). Add `**/node_modules/**`, `**/temp/**`, `**/.kotlin/**`, `DOWNLOADS/**` to `files.exclude`, `search.exclude`, `files.watcherExclude`. Codex measured: 9,251 → 2,633 files in `rg --files`. Same multiplier for my `Glob`.
4. **Native catalog read path** (MEDIUM). One-line note in `dev/CATALOG/README.md` blessing direct JSONL reads for narrow queries.
5. **Truth-drift checker** (MEDIUM). Codex proposed `scripts/check-doc-vs-gradle.ps1`. Compares declared versions in `docs/TECH_STACK.md` / `dev/TECH_REQUIREMENTS.md` / `CLAUDE.md` against `gradle/libs.versions.toml` and module Gradle files. Failing this audit at PR time would stop drift from compounding.
6. **Stale-CLAUDE.md fix on catalog commit instruction** (LOW). One-line edit, removes a contradiction I read every conversation.

## 5. Development Speed: Concrete Recommendations

1. **Fix `scripts/post-change.ps1`** (HIGH). `-NoProfile` everywhere; call `catalog_sync.ps1` instead of separate scan/render. 5-minute patch, ~700 ms per post-change step saved.
2. **Build-failure log wrapper** (HIGH). `scripts/builders/get-last-build-failure.ps1` - reads the most recent `temp/build-debug-*.log`, finds the FAILURE block, prints it. Lets me investigate failures without the tail-truncation trap. Memory already documents the trap; the tool prevents it.
3. **`a.ps1` aliases for missing common cases** (MEDIUM):
   - `a.ps1 cs` → `catalog_sync.ps1 -Module app_v2`
   - `a.ps1 cw` → `catalog_sync.ps1 -Module wear`
   - `a.ps1 ab` → `agent_bootstrap.ps1` (once it exists)
   - `a.ps1 sl` → `select.ps1 -Id <last>` (pipe-friendly)
4. **Standard-flavor configuration cache** (MEDIUM, larger change). Investigate whether `assembleStandardDebug` can re-enable config cache while noLegal/Chaquopy keeps `--no-configuration-cache`. Codex already raised this. Worth re-checking against latest AGP because the Chaquopy 17 → 18 path may have changed.
5. **Split `VideoPlayerManager.kt`** (LARGER). Codex flagged. `gradle.properties` calls it out as a compiler-pressure hotspot. This is build-speed work, not cosmetics. Should be its own spec ticket.
6. **`testStandardDebugUnitTest` pre-existing failures** (MEDIUM, hygiene). Memory pins: ~26 pre-existing broken tests in `testStandardDebugUnitTest`. They contaminate every "did I break tests" check. Either fix them or quarantine into a tagged suite.

## 6. VS Code, MCP, and Permissions

### 6.1 Permission gate is too narrow

`.claude/settings.json` has 14 lines. Notable absences:

- No `Bash("/c/Program Files/PowerShell/7/pwsh.exe" *)` line - so every direct pwsh invocation may need a permission prompt or rely on the broad `acceptEdits` mode.
- No `Bash(./a.ps1 *)` shortcut. The current `Bash(.\scripts\* *)` covers builders but the operator-facing `a.ps1` lives at root.
- No `Bash(./gradlew *)` (only `./gradlew.bat *`). Cross-shell pain when I run from git-bash.

**Fix:** extend the allowlist. The `fewer-permission-prompts` skill in my list is the right tool for this - it scans my transcripts and proposes a permission set.

### 6.2 MCP

`.vscode/mcp.json` exposes four servers: `docs-search`, `filesystem_rw`, `filesystem_ro`, `gradle_safe`. Two issues for my workflow:

- `docs-search` is restricted to `docs/`. The richer knowledge sits in `dev/PROJECT_OPERATIONS_INDEX.md`, `dev/AGENT_WORKFLOW.md`, `PLAN/`, `dev/CATALOG/`. Codex proposed widening to a `repo-knowledge` MCP - same conclusion.
- `gradle_safe` has a small allowlist. `:app_v2:compileStandardDebugKotlin` (and noLegal/lite/photos siblings) should be on it - they are the targeted compile commands I want when "did this change still compile" is the question.

### 6.3 VS Code settings - additions worth making

- Exclude `**/node_modules/**`, `**/temp/**`, `**/.kotlin/**` from `files.exclude`, `search.exclude`, `files.watcherExclude`.
- Add `chat.tools.terminal.autoApprove` patterns for: `pwsh -NoProfile -File scripts/*`, `git status --short`, `git branch --show-current`, `git log --oneline -*`. Each of those is read-only and I run them every session.

## 7. Already Flagged in `codex_audit.md` / `gemini_audit.md` - Still Unfixed

To save the operator from reading three documents to find the same outstanding work:

1. `scripts/post-change.ps1` - no `-NoProfile`, no `catalog_sync` (codex §"Development Speed"). **Unfixed.**
2. `.vscode/settings.json` - `node_modules` not excluded (codex §"VS Code"). **Unfixed.**
3. `docs/TECH_STACK.md` vs `gradle/libs.versions.toml` drift (codex §"Rule and Documentation Quality"). **Unfixed** - last drift check I see is manual.
4. Gradle MCP allowlist - missing common compile/test wrappers (codex §"Development Speed Recommendations 3"). **Unfixed.**
5. Catalog JSONL agent-readable advertisement (gemini §"Conclusion 2"). **Unfixed.**
6. Async-build acknowledgement in the workflow (gemini §"Async Build Workflows"). **Unfixed in docs**, but my `Bash run_in_background` already enables this for me - I just need to use it more.

If the same items show up in a fourth audit, the rule files have failed at their job. These are 5-30 minute fixes.

## 8. Memory Hygiene (my own house)

23 memory files. A quick self-audit:

- All entries cite a `Why:` and a `How to apply:`. Good.
- One overlap candidate: `feedback_pwsh_efficiency.md` and `feedback_pwsh_path.md` both touch PowerShell. They cover different concerns (efficiency vs PATH) - keep separated, but cross-link.
- `project_build_gotchas.md` is numbered `1, 3, 2` (typo). Cosmetic only. I will leave it as-is to avoid touching a noisy area mid-audit.
- No memory yet for: VS Code settings.json patterns, my own permission gate, sub-agent cost heuristic. These would be valuable.

**Action after this audit:** add three memory files:

- `feedback_subagent_cost_heuristic.md` - when to spawn vs inline.
- `project_dirty_tree_awareness.md` - check `git status --short` before editing CLAUDE/AGENTS/build.gradle.kts.
- `feedback_native_catalog_read_for_narrow_queries.md` - parse `dev/CATALOG/*.jsonl` directly when the query is single-class.

## 9. Operating Notes For Future-Me

- Start any non-trivial task with: branch + dirty status + pwsh path + active S ticket + skill route. Bundle this as `agent_bootstrap` once it exists; until then, run the four commands manually.
- Use `Read` on `dev/CATALOG/app_v2.jsonl` for single-class lookups. Use `query.ps1` only when filtering by role/injection/path-glob.
- Never `tail -N` a build. Either let the harness show the full output, or `> temp/build.log 2>&1` then `Grep -n "FAILURE\|What went wrong\|^e:" temp/build.log`.
- Before editing any `.kt` larger than 500 LOC, `git stash list` and `git status` for that file. Memory rule: backup to `temp/` first.
- Timestamp every chat message (`[HH:MM:SS]`). Memory `feedback_timestamp_in_chat.md`.
- Russian in chat, English in code/docs/commit messages. `..` not `...`. `ё`/`Ё` always.
- Sub-agent only when (a) genuinely parallel and (b) ≥10 tool calls of work. Otherwise inline.
- If the answer is "look in dev/CATALOG/<module>.md", do not spawn a sub-agent. Read the file directly.
- If I notice an outstanding item from §7 of this audit while doing other work, do **not** silently fix it unless it's in scope. Mention it in the chat reply and let the operator decide. (Avoid scope creep on a dirty worktree.)

## 10. Priority Plan

Ordered by `(impact × frequency) / effort`:

1. `scripts/pwsh` git-bash shim. 5 minutes. Cures every PowerShell call I make.
2. `.vscode/settings.json` exclusions for `node_modules`/`temp`/`.kotlin`. 2 minutes. ~3.5x reduction in file enumeration.
3. Fix `scripts/post-change.ps1` per §3.3. 5 minutes. Saves ~700 ms per post-change step.
4. Edit `CLAUDE.md` §"Post-Change Steps" step 5 to stop telling agents to commit gitignored catalog files. 2 minutes.
5. `scripts/agent_bootstrap.ps1` per §3.8. 30-45 minutes. Removes 3-5 redundant tool calls per session.
6. Extend `.claude/settings.json` allowlist with my common Bash patterns per §6.1. 5 minutes.
7. `scripts/builders/get-last-build-failure.ps1` per §5.2. 20 minutes. Eliminates the tail-truncation trap.
8. Catalog JSONL native-read note in `dev/CATALOG/README.md` per §3.5. 2 minutes.
9. `a.ps1` aliases for `cs`/`cw`/`ab`/`sl` per §5.3. 10 minutes.
10. Truth-drift checker per §4.5. 1-2 hours, less urgent.
11. `VideoPlayerManager.kt` split per §5.5. Own spec ticket.
12. `testStandardDebugUnitTest` triage per §5.6. Own spec ticket.

Items 1-4 are end-of-week-1 candidates. Items 5-9 fit a single focused session. Items 10-12 are explicit specs.

## 11. Evidence Collected

- `git status --short` snapshot: 30+ modified files including `CLAUDE.md`, `AGENTS.md`, `app_v2/build.gradle.kts`, 25+ `.kt` files.
- `git branch --show-current`: `DEBUG-v004`.
- `git log -5`: most recent commits dated 2605... (Y.YM.MDDH.Hmm format - May 19).
- PowerShell probe via `"/c/Program Files/PowerShell/7/pwsh.exe" -NoProfile`: confirmed Win NT 10.0.26200.0, 20 logical processors, ~63.7 GB RAM, i5-13600K.
- `.claude/settings.json`: 14 lines, `defaultMode: acceptEdits`, narrow allowlist.
- `.claude/agent-memory/android-rd-specialist/`: 23 memory files + `MEMORY.md` index, 25 lines.
- `.vscode/settings.json`: java/gradle import disabled (correct), search.exclude missing `node_modules`/`temp`.
- `.vscode/mcp.json`: 4 servers, `docs-search` scoped to `docs/` only.
- `scripts/post-change.ps1`: read in full, confirmed `-NoProfile` missing and `catalog_sync.ps1` not used.
- `scripts/catalog_sync.ps1`: read in full, correct.
- `a.ps1`: 326 lines, 21 aliases.
- Sibling audits read in full: `dev/codex_audit.md` (309 lines, 2026-05-20), `dev/gemini_audit.md`.
- Memory files read for grounding: `feedback_pwsh_efficiency.md`, `feedback_build_output_pipe_truncation.md`, `project_build_gotchas.md`.

## 12. Conversation and Request-History Analysis

### 12.1 Sources

I do not have a persistent transcript of every chat we have had. What I do have, and what this section is grounded in:

- 23 memory files in `.claude/agent-memory/android-rd-specialist/` - each entry was created in response to a real conversation event (correction or confirmation).
- `dev/FUNCTIONALITY.log`: 150 entries from 2026-05-14 to 2026-05-20 - one line per user-visible change with `Sxxxx`, op (`ADD|CHANGE|DELETE|FIX`), and prose description.
- `PLAN/spec-catalog.jsonl`: 267 specs total, last 30 read in detail (S0238..S0267).
- `git log` for the last 50 commits across DEBUG-v001..v004.
- `logs/dev_progress.log`: structured step log from S0251 work on 2026-05-19.
- `temp/sessions/`: 4 captured session traces, most recent `20260520_S0262_build_debug.txt` (today).
- `temp/`: 100+ timestamped `.backup` and `.bak` files - each one is evidence of an edit I or another agent made.

This is enough to characterise the operator's workflow with confidence. It is not enough to quote past chat verbatim.

### 12.2 Volume And Cadence

- Spec velocity in the last 7 days: ~30 new specs (S0238..S0267). About 4-5 specs per day.
- User-visible changes in the last 7 days: 150. About 20 per day. Sustained.
- Commit cadence: 25+ commits in the last 4 days across `DEBUG-v004`, plus release-merge commits to `main`.
- Spec status mix in the last 30: ~15 `Verified`, ~10 `BlockNeedUserTest`, 2 `Implemented`, 2 `Draft`, 1 `Archived`, 1 `BlockQuestions` (S0267 - active blocker right now). The operator clears `BlockNeedUserTest` on device fast - same-day for most.

### 12.3 Topic Distribution (last 7 days)

Sorted by entry count in `FUNCTIONALITY.log`:

1. **Settings UI iteration** (~35 entries, S0125 / S0254 / S0255 / S0258 / S0259 / S0261 / S0263 plus unscoped tweaks). S0125 alone has ~10 entries inside 24 hours including a full rollback after the "revised settings host" experiment. Dominant theme.
2. **Network/IO resilience** (~20 entries, S0202 / S0205 / S0206 / S0212 / S0213 / S0219 / S0228 / S0237 / S0246 / S0247 / S0248 / S0252 / S0262 / S0265). SMB/SFTP/FTP/cloud round-tripping. Deep, ongoing.
3. **Cloud auth** (~15 entries, S0200 / S0232..S0236 / S0239 / S0243 / S0257 / S0266 / S0267). Google Credential Manager + OneDrive + Dropbox + GMS version handling.
4. **VR** (~10 entries, S0240 / S0241 / S0244 / S0245 / S0249 / S0250 / S0251). Stack removed (S0241, 2026-05-18) then re-introduced through `noLegal` unification (S0250, 2026-05-19) within 36 hours. Active scaffolding still in flight.
5. **Memory/playback** (S0207 / S0213, multiple iterations). Low-memory gating tuned three times.
6. **Documents/notes** (S0189 / S0191 / S0192). Text editor + draw editor flow.
7. **TV/keyboard input** (S0230, ~5 entries). Universal input router.
8. **Distribution** (S0214 / S0215). GitHub Releases + IzzyOnDroid pipelines.
9. **Code decomposition** (S0002 Waves 40, 41). `BaseFileOperationHandler.kt` 939 → 404 LOC, `FtpFileOperationHandler.kt` 938 → 567 LOC. The 1500 LOC limit is actively enforced, not aspirational.

### 12.4 Observed Patterns

1. **Spec-driven near-100%.** Every commit not tagged `release:` or `chore:` carries an `Sxxxx`. Even bug-fix one-liners get an id. The skill router (`/spec`, `/spec-tech`, `/spec-dev`, `/spec-check`, `/spec-all`) is the load-bearing workflow.
2. **High [FIX] ratio.** Roughly half of `FUNCTIONALITY.log` entries are `[FIX]`. Many fixes apply to features that landed in the same week. Implication: device-time uncovers issues that local unit tests miss. The `BlockNeedUserTest` gate is doing real work, not theatre.
3. **Iterative UI churn on settings.** S0125 went `CHANGE → CHANGE → FIX → CHANGE → FIX → FIX → FIX → CHANGE → DELETE` within 36 hours before being archived. The operator iterates on placement/visibility quickly. Code scaffolding that anticipates "the final shape" tends to be wrong.
4. **VR is strategically volatile.** Removed in S0241, reborn in S0250 via `noLegal`. My memory `project_vr_inclusion_hierarchy.md` already captures the new architecture (standard ⊂ vr ⊂ noLegal). Do not re-litigate.
5. **The operator uses interruption as a tool.** Multiple sessions show "stop, look, redirect" cadence. Tasks must be resumable from any cut point. My memory `feedback_no_scaffolding_as_done.md` and `feedback_verify_subagent_build_failures.md` are direct corrections from such interruptions.
6. **Quick fixes bypass the spec workflow.** ~25 of 150 entries are `[------]` (no Sxxxx). These are typo, color-shift, single-line UI repair. The `/quick` skill exists exactly for this case.
7. **Memory bias.** My 23 memory files cluster on: PowerShell efficiency (2), build/log handling (3), flavor isolation (3), spec lifecycle (1), catalog tooling (2), tag invariant (1), avoiding-bad-pattern feedback (7). Mostly **process** lessons. Very few **domain** lessons. The operator's domain (SMB/SFTP/cloud auth/VR/settings) is barely represented in my memory yet.

### 12.5 What This Implies For My Behaviour

- **Settings UI requests: lean toward design-lock before code.** Before writing any new toggle row or section, grep `FUNCTIONALITY.log` for the target screen. If 3+ entries hit it in the last 7 days, ask the operator to lock placement first. `/ui-clarify` is the right tool; use it more eagerly here than elsewhere.
- **Network/IO requests: assume device validation is required.** These specs almost never `Verified` without `BlockNeedUserTest`. Insert Timber tags eagerly. Do not collapse phases.
- **VR requests: respect the settled hierarchy.** `standard ⊂ vr ⊂ noLegal` is current. Bind interfaces in `src/<flavor>/java/`, not `BuildConfig` guards in `src/main`. Memory `feedback_flavor_isolation_strict.md` covers it.
- **Cloud auth requests: read recent S023x specs first.** The decisions are layered (`S0232` shared applicationId, `S0233` credential chooser fallback, `S0235` Dropbox scope, `S0239` GMS guard, `S0243` auth-result channel). Skipping the chain produces inconsistent fixes.
- **Quick UI fixes: route to `/quick`, not `/spec*`.** A typo or color shift does not need spec lifecycle. The operator's existing `[------]` pattern in `FUNCTIONALITY.log` makes the precedent clear.
- **Memory expansion target: domain.** When I learn something concrete about SMB idle-disconnect semantics, Credential Manager fallback, or VR session creation - save it. My memory is over-weighted on process and under-weighted on the actual platform.

### 12.6 Two-Audit Drift Cross-Check

`dev/gemini_audit.md` (§3) cited "Pre-Implementation Auditing" as the dominant request type and listed S0125 / S0244 / S0194 / S0195 / S0192 / S0193 as examples. The 7-day data confirms this is still the dominant rhythm: spec drafted → tactical → device test → verified. Codex's §"Conversation and Request-History" was correct about resumability and durable-artifact preference. Both audits underweighted the **settings UI thrash** signal that this audit picks up clearly from `FUNCTIONALITY.log`. That signal is the one most actionable for me.

### 12.7 Revised Priority Adjustments

The history analysis does not displace §10's top items (shell shim, VS Code exclusions, `post-change.ps1` fix). It adds three operator-facing items:

- **A1 (new).** Add a "recent UI churn" probe to `agent_bootstrap.ps1`: grep `FUNCTIONALITY.log` for the screen named in the request and surface the last 5 entries. Lets me catch S0125-style thrash before writing code.
- **A2 (new).** Pre-populate `.claude/agent-memory/android-rd-specialist/` with **domain** memory files for SMB, SFTP, Credential Manager, VR session - even short stubs - so I can extend rather than create on first encounter.
- **A3 (new).** Wire `dev/FUNCTIONALITY.log` into `docs-search` MCP (codex already proposed extending docs-search; this is the same fix from a different angle). The operator's own history of decisions is more useful to me than the static feature catalogue.

These three are still in the "1 focused session" effort band.

## 13. Synthesis: What I Actually Think After Three Rounds

This section is the part the operator asked for and that the prior sections only circled around. Sharp opinions, no hedging. Where this contradicts §3, §6, §10, this section wins.

### 13.1 I was using the wrong shell tool the entire audit

My harness exposes a **`PowerShell` tool** (runs PowerShell 7.6.1 directly, persistent session per call) alongside `Bash`. I verified it now: `$PSVersionTable.PSVersion` reports `7.6.1`, no spawn, no PATH workaround, no full-path `"/c/Program Files/PowerShell/7/pwsh.exe"` dance.

For inline PowerShell (probe a variable, query CIM, call a .NET API, run two related scripts in one process) this is **strictly better** than `Bash` + `pwsh.exe`. Measured today on this machine:

- Native `PowerShell` tool, three consecutive inline runs that internally spawn a child pwsh: 180 / 148 / 148 ms.
- `Bash` hop to the same `pwsh.exe -NoProfile -Command 'exit 0'`: 168 / 159 ms.

Per-call spawn cost is identical. The win is that **inline PS code in the `PowerShell` tool does not spawn at all** - reading `$PSVersionTable`, listing CIM data, parsing JSONL via `ConvertFrom-Json`, calling `Get-Content`/`Set-Content` - all free of cold start.

**Implications:**

- §3.1 ("Bash vs PowerShell shell split") is half-wrong. The shell split exists, but the operator already solved it; I was unaware. **The `scripts/pwsh` shim from §10.1 is unnecessary.** I just need to use the correct tool. The memory file `feedback_pwsh_path.md` is now misleading and should be retired or rewritten ("prefer the native `PowerShell` tool; fall back to Bash + full-path pwsh only when piping into bash chains").
- §10.1 ("git-bash pwsh shim") drops off the priority list. Saved effort: 5 min, but more importantly, no diff that needed operator review.
- New rule for myself: **use `PowerShell` tool for any project script invocation, catalog query, CIM/registry/env probe, or JSONL parse.** Use `Bash` only for git operations, find/grep when Grep doesn't fit, and unix-shaped pipelines.

This single discovery is the highest-payoff thing in this audit. Three audits in this repo (codex, gemini, this one before this section) all proposed PowerShell efficiency improvements without noticing the tool was already there.

### 13.2 Three audits, zero implementations - the audit format itself is failing

`dev/codex_audit.md` (2026-05-20), `dev/gemini_audit.md` (2026-05-20), and this file all converge on the same handful of fixes:

- exclude `node_modules` in VS Code
- fix `scripts/post-change.ps1`
- widen the docs-search MCP
- check doc/Gradle version drift
- one-call agent bootstrap script

None of those have landed between 2026-05-20 morning and 2026-05-20 12:56. The audits are accreting recommendations faster than the repo accretes fixes. **A fourth audit would be malpractice.** What this repo needs instead:

- a tiny `dev/AUDIT_FOLLOWUPS.md` (or a section inside one canonical audit) that **only** tracks open items by id, with one-line status: open / in flight / done with commit-sha. The three current audits become read-only references.
- a rule that any new audit edits the followups list rather than restating the same recommendations.

I will not propose more recommendations in any future audit. I will edit `dev/AUDIT_FOLLOWUPS.md` if/when it exists, and otherwise stop.

### 13.3 The biggest hidden cost is operator iteration thrash, not tool cold start

S0125 (revised settings host) ran ten `CHANGE`/`FIX` cycles inside 36 hours before being archived. That is far more expensive than any 200 ms PowerShell spawn. The agent share of that cost was: scaffolding a multi-tab settings host based on an incomplete brief, then refactoring it three times as the brief moved.

The lesson is in my memory already (`feedback_no_scaffolding_as_done.md`), but it's defensive: "do not call scaffolding done". The offensive version is: **on settings UI requests, refuse to scaffold without a locked placement contract.** The `/ui-clarify` skill exists. Use it harder. Even when the operator's request reads like a directive, ask the placement/visibility/fallback questions first - the cost of asking is small, the cost of building the wrong tree is large.

### 13.4 The memory layer is the only thing actually compounding

23 memory files, all earned from a specific past failure or confirmed pattern. Over three months of conversation, that is the only artifact in this repo that gets meaningfully better per-session **for me specifically**. Everything else (catalog, spec journal, dev log) compounds for the operator and other agents equally.

But: most of my memory is **process** (PowerShell, tags, sub-agents, scaffolding). The operator's domain (SMB / SFTP / Credential Manager / VR session / Glide thumbnail loaders / Room migrations) is barely represented. If I want to give better technical answers next month, I should write domain memories now. Concrete starts:

- `project_smb_idle_disconnect_semantics.md` - what S0228 / S0219 / S0237 / S0246 / S0247 / S0248 actually decided about session ownership.
- `project_credential_manager_fallback_chain.md` - the layered decisions in S0200 / S0233 / S0234 / S0239 / S0257.
- `project_vr_session_lifecycle.md` - what S0249 actually wires for OpenXR instance + swapchain + frame loop on Quest 3.
- `project_room_migration_v29_v30.md` - cloud:/ path normalisation from S0236 / S0242.

These should be written **when I next touch the area**, not as a batch job. Batch-writing memory from old specs is just rephrasing, not learning.

### 13.5 The post-change ritual is over-engineered

CLAUDE.md §"Post-Change Steps" prescribes 7 numbered steps with fail-closed gating. For a typical `.kt` edit (no string change, no new feature, internal refactor), steps 2 (feature docs), 3 (functionality log), 4 (string audit) are skips. But the **agent has to read every step every conversation** to decide which apply. That's pure cognitive overhead.

The right shape is a single `scripts/post_change.ps1 -ChangeType <kotlin|doc|config|xml|mixed>` that the agent calls **once**, and which internally decides which sub-steps apply. The current `post-change.ps1` already exists; widening it from 4 hardcoded steps to a type-aware dispatcher is the right next iteration. (And, separately, fix its missing `-NoProfile` per §3.3.)

### 13.6 Sub-agent spawning is the wrong default

CLAUDE.md actively encourages parallel sub-agent spawning. In practice, every sub-agent burns ~2000 input tokens re-reading CLAUDE.md before doing the actual work. For lookup-class questions ("where is class X", "which file holds feature Y"), a direct `Read` of `dev/CATALOG/app_v2.jsonl` is faster, cheaper, and gives me the result in my own context instead of locking it behind a sub-agent summary.

I should treat sub-agent as a **last resort**, not a default. The heuristic to add to memory:

- Spawn sub-agent only when (a) genuinely parallel main-thread work, (b) ≥10 tool calls of work, (c) the sub-agent's output is a digest I can use, not raw data I need to re-process.
- For class lookup, file location, single-file read: inline.
- For build validation while I write code: sub-agent (background) is correct.
- For "audit this 2000-line file for X": inline if I have the context; sub-agent if I'd otherwise lose 4000 tokens to the read.

### 13.7 Dirty worktree is normal, not an alarm

I flagged in §3.2 that the worktree has 30+ modified files including `CLAUDE.md` and `AGENTS.md`. I treated this as risk. After looking at `temp/` (100+ `.backup` files going back to 2026-04-25) and the commit cadence (25 commits in 4 days), I think this is **the operator's steady state**, not an emergency. The DEBUG-v00N branches are workspaces; they are dirty until the release-merge to main.

**Revised position:** dirty-tree awareness is still right (do not silently edit the operator's in-flight files), but I should not block on it. A one-line `git status --short | wc -l` at session start is enough situational awareness; I do not need to enumerate every modified file.

### 13.8 The version commit-message format is unreadable

Commits land as `2605192322` (Y.YM.MDDH.Hmm) with no description. `git log` for any non-`feat`/`fix`/`release` commit is useless without opening the commit body. I cannot tell from `git log --oneline -50` what the last week of work was about; I had to read `FUNCTIONALITY.log` instead.

**Suggestion:** keep the version tag in the commit body (or a trailing line) but lead the subject with the actual change. `feat(S0125): revised settings host - first cut`, not `2605192322`. The `add_to_dev_log.ps1` script already accepts a description; surfacing the same string into the commit subject is trivial.

This is a one-line change in whatever commit wrapper the operator uses. Daily-readable history is worth it.

### 13.9 Revised top-five priorities (post-synthesis)

Replacing §10's top items with what I now believe matters most. Ordered by `(actual impact this week) / (effort)`.

1. **Use the `PowerShell` tool, not Bash + pwsh.exe.** Effort: zero. Effect: every future PS interaction faster, fewer parser headaches, no PATH workaround. (Behavioural change for me, no code change in the repo.)
2. **Create `dev/AUDIT_FOLLOWUPS.md`** with the open items from codex + gemini + this audit, no duplicate text - just ids and status. Three audits = bug; one followups list = feature.
3. **Use `/ui-clarify` harder on settings UI requests.** Behavioural, but worth promoting from "rule" to "automatic" - on any request mentioning settings placement, run the clarify gate before writing code.
4. **Widen `scripts/post-change.ps1` to `-ChangeType` aware.** Then make the operator-facing entry the only documented way to close a step. CLAUDE.md §"Post-Change Steps" collapses from 7 numbered steps to "run `scripts/post_change.ps1 -ChangeType X`".
5. **Write domain memory in the moment, not in batch.** Next time I touch SMB, Credential Manager, VR session, or Room migration - save what I actually learn.

Items 6-12 from §10 stay as written but with shim removal (10.1) crossed off.

### 13.10 What I would not do

To save the operator from "everything is a priority":

- Do not invest in a "request classifier" or "agent_bootstrap script" until the simpler followups land. Premature scaffolding has the same shape as S0125.
- Do not rewrite `CLAUDE.md` to be shorter. It's long because it has to be; the cost is read-once per session, the benefit is durable. The right move is to make the post-change part shorter (§13.5), not the whole file.
- Do not add new MCP servers. The current four are under-utilised; expanding before utilising is overhead, not capability.
- Do not try to fix the 169 `BuildConfig.IS_*` flavor-guard violations in `src/main/` in bulk. CLAUDE.md §15 already calls this out as incremental refactor. Bulk would create a multi-hundred-file PR that locks the operator's worktree for days.
- Do not write a fourth audit.
