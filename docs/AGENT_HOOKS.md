# Agent Hooks - the complete inventory

Claude Code hooks run around your tool calls. One may **refuse** a call before it happens, **rewrite** its input, **observe** its result, **warn** you, or **arm** a gate for the session. This file is the whole live set. It exists because, when it was written, five of the eleven registered hooks were named in no file an agent reads, and two of those five alter the call itself (S1604).

**When a call is refused or a result looks altered, start here.** The refusal text is self-sufficient by design: it names the hook, cites its `CLAUDE.md` rule, states the reason, gives the exact remedy and names the escape hatch, and it arrives on stderr, which is shown to the model. This file carries the full contract behind that one-line remedy.

## The two homes, and why it matters

- **global** - `~/.claude/hooks/`, registered in `~/.claude/settings.json`. Per-machine, **not** version-controlled, and **absent on a fresh machine or a clean checkout**. A behaviour you rely on here may simply not exist for another contributor.
- **project** - `.claude/hooks/`, registered in `.claude/settings.json`. Travels with the checkout, so it is live for everyone who clones the repository.

The canon ships some of these guards in its own `hooks/` folder. Until the *installed* plugin cache carries a given `.ps1`, the hand-wired registration in `~/.claude/settings.json` is the only thing making it live - removing one "because the plugin has it now" silently disarms it. Verify the installed cache, not the marketplace clone.

**Open as of the 2026-08-18 canon re-sync: every `global` row below is now also shipped by the plugin, and both fire.** The installed cache at `sza/2026.818.1` carries `guard-bash.ps1` (absorbing the `find`, `.ps1`-head, cmdlet-head and missing-interpreter checks), `guard-fire-and-forget.ps1`, `guard-uncapped-read.ps1` and `on-user-prompt.ps1`. Each therefore costs a second PowerShell start (170-250 ms) on every matching call and, for the read guard, a second rewrite of the same input - the plugin's window is 500 lines against the hand-wired one's own threshold. Dropping the six hand-wired registrations from `~/.claude/settings.json` is the canon-sanctioned fix, but that file is **per-machine and outside this repository**, so it is the owner's call and is deliberately not made here. The rows stay listed while they stay registered: `assert-hook-inventory.ps1` judges the global half against that file whenever it is readable, so deleting a row before its registration would fail the gate.

## Inventory

| Hook | Event / matcher | Verdict | Home | Rule | Contract tests |
|------|-----------------|---------|------|:----:|----------------|
| `guard-find-command.ps1` | PreToolUse / Bash | refuses | global | 24 | - |
| `guard-ps1-in-bash.ps1` | PreToolUse / Bash | refuses | global | 25 | - |
| `guard-fire-and-forget.ps1` | PreToolUse / Bash | refuses | global | 26 | - |
| `guard-bash-unavailable-command.ps1` | PreToolUse / Bash | refuses | global | 28 | `.claude/hooks/global-hook-tests/Run-GuardBashUnavailableCommand-Tests.ps1` |
| `guard-uncapped-read.ps1` | PreToolUse / Read | **rewrites** | global | - | `.claude/hooks/global-hook-tests/Run-GuardUncappedRead-Tests.ps1` |
| `warn-context-size.ps1` | UserPromptSubmit / `*` | warns | global | - | - |
| `guard-catalog-before-kt-search.ps1` | PreToolUse / Grep, Glob | refuses | project | - | `.claude/hooks/tests/run-guard-catalog-cases.ps1` |
| `observe-empty-grep.ps1` | PostToolUse / Grep | observes | project | - | `.claude/hooks/tests/Run-ObserveEmptyGrep-Tests.ps1` |
| `nudge-small-task-tier.ps1` | UserPromptSubmit | nudges | project | - | - |
| `sweep-agent-lock-queues.ps1` | UserPromptSubmit | observes | project | 23 | - |
| `reset-catalog-touch-marker.ps1` | SessionStart | arms | project | - | - |
| `post-agent-chat-session.ps1` | SessionStart, SessionEnd | observes | project | 34 | `.claude/hooks/tests/Run-PostAgentChatSession-Tests.ps1` |
| `refuse-spec-do-stop.ps1` | Stop | **refuses** | project | - | `.claude/hooks/tests/Run-RefuseSpecDoStop-Tests.ps1` |

## Contracts and escape hatches

### Refusing guards

- **`guard-find-command`** (Rule 24) refuses a `find` with a disk-wide start path, and a `find` with no `-maxdepth`. **Escape:** give a concrete start path *and* `-maxdepth N`; a `find` inside a quoted string is untouched. Prefer the `Glob`/`Grep` tools or `dev/CATALOG/scripts/query.ps1` anyway.
- **`guard-ps1-in-bash`** (Rule 25) refuses a `.ps1` in *command-head* position, because Bash cannot execute one and the failure still reports **exit 0** - a broken build looks like a passing one. **Escape:** `pwsh -NoProfile -File ./a.ps1 <cmd>`. A `.ps1` as an argument, or read with `Read`/`grep`, is fine.
- **`guard-fire-and-forget`** (Rule 26) refuses `run_in_background` on a gate, a closure facade or a catalog mutator. **Escape:** a genuinely long job on the same command line (`gradlew`, `a.ps1 d/db/dav/cd/nd/nl/r/fu`) overrides the block. **Coverage limit worth knowing:** it is registered on the **Bash** matcher only, so backgrounding the same script through the PowerShell tool is not intercepted - the rule still binds you there.
- **`guard-bash-unavailable-command`** (Rule 28) refuses three heads: a PowerShell cmdlet, an interpreter absent from this machine (`node`, `npm`, `npx`), and `& {` at the start of a command. **Escape:** the PowerShell tool, or `pwsh -NoProfile -Command "<pipeline>"`. Only a *head* is refused - a cmdlet in quotes, in a heredoc, or as an argument passes. `python3` is deliberately **not** refused: `~/bin/python3` shims to the `python` on PATH.
- **Rule 27 (the slash-command argument value) is no longer a project hook.** The canon's `sza` plugin ships the check as check 6 of its own `guard-bash.ps1`, verified present in the *installed* cache and proven live by refusal on 2026-08-18, so the project copy `guard-bash-slash-arg.ps1` was removed rather than run twice. **Escape:** unchanged - double the slash (`//spec-dev ..`), prefix `MSYS2_ARG_CONV_EXCL='*'`, or issue the call from the PowerShell tool. **One coverage difference, recorded rather than glossed:** the project hook matched names read from `.claude/commands/*.md`, while the canon guard matches any first path segment that is not a POSIX root - so a future command named `run`, `dev`, `var`, `bin`, `etc`, `opt`, `lib`, `tmp` or `usr` would pass unrefused. None of the current 32 command names collides.
- **`guard-catalog-before-kt-search`** refuses an **unnarrowed** Kotlin search issued before the class catalogue was consulted this session. All three must hold: the tool is `Grep`/`Glob`, the call targets `.kt`, and it carries no path or glob restricting it to a subtree. **Escape:** name a subtree, or run `dev/CATALOG/scripts/query.ps1` first - any successful query writes `temp/catalog-touch.marker`, which is the pass condition for the rest of the session.

### Refusing to finish - the one hook that guards a turn, not a tool call

- **`refuse-spec-do-stop`** is the only hook on the `Stop` event, and the only one whose subject is the model rather than a tool call: while a `/spec-do` endless marker is armed for **this** session, it answers the Stop event with `{"decision":"block"}` and hands the loop its own next step. It exists because `/spec-do`'s "only the operator ends this loop" was prose, and a loop ended itself at a round boundary anyway, asking the operator to compact and say "continue" - a stop wearing a report's clothes. **Escape:** `pwsh -NoProfile -File scripts/utils/spec-do-marker.ps1 -Action disarm -Token <token>`, which the refusal text always names; the hook never arms anything itself, so a session that armed nothing is never touched.
- Four allow-paths keep it from holding a session hostage, each pinned by a contract test: no marker; a marker claimed by a **different** session; a marker older than 24 h (purged, never inherited - a killed session must not hold a new one open); and a **live idle wait**, `temp/SPEC-DO.WORK-*.json` with `outcome: waiting` refreshed within 15 minutes, because blocking there would force the loop to spin instead of waiting. A streak of blocks under 20 s apart escalates the instruction to "launch the waiter in the background" rather than surrendering - the cure for a spin is the wait, not a stop.
- It ignores `stop_hook_active` on purpose. That flag exists so an ordinary hook cannot loop forever; looping forever is this one's contract.

### Rewriting and observing

- **`guard-uncapped-read`** does **not** refuse. A `Read` with no `offset`/`limit` against a file over 200 lines is rewritten to `limit: 800`, and when the file is longer it returns a notice naming the real length and how much is hidden. **This is the one hook that changes what you see**, so treat a long file you did not window explicitly as partially read. **Escape:** pass an explicit `offset`/`limit` - never intercepted - and `.claude/commands|skills|templates|reference|agents` are exempt outright.
- **`observe-empty-grep`** attaches context to an empty `Grep` **only when the same pattern, re-run without your `path`, finds something**. It is silent when the widened run also misses, so a false positive is impossible by construction and a confirmed "not here" stays confirmed. It cannot alter the result - a `PostToolUse` hook can only attach context.
- **`warn-context-size`** injects a context-size warning at prompt submit. Advisory.
- **`nudge-small-task-tier`** injects a routing reminder naming `/quick` and `/skill-fix` when the prompt reads as a micro-task. Advisory, always exit 0 - a false fire that refused a prompt would cost more than the miss it prevents.
- **`reset-catalog-touch-marker`** deletes `temp/catalog-touch.marker` at session start, so the Kotlin-search gate is armed once per session rather than passing forever after its first ever query.
- **`post-agent-chat-session`** (Rule 34) posts one `kind=session` line to the agent chat (`temp/AGENT-CHAT/progress/`) when a session starts and one when it ends, carrying the end reason - the one fact that separates "died mid-phase" from "finished and left", which no lock, queue or lease records. Stdout is always empty and the exit code always 0: a malformed payload, a missing `scripts/utils/agent-lock.ps1` or a store error write nothing and never cost a session. An accelerator, not a condition: a runtime without hooks posts the same two lines by hand through `scripts/utils/agent-chat.ps1 -Verb Post -Kind session`, and every reader treats a missing session line as "unknown", never as "dead".
- **`sweep-agent-lock-queues`** calls the existing `Remove-StaleAgentLockTickets` (Rule 23) for every domain (`Build.Phone`, `Build.Wear`, `Code.Phone`, `Code.Wear`, `Code.Scripts`) on every prompt submit. It introduces no new eviction rule - `Get-AgentLockQueue` already sweeps on every call, but only when something calls it, which nothing does for a ticket whose owner died before ever launching a background `wait-for-lock-turn.ps1` poller. Observed live 2026-08-28: a queue with waiting tickets and zero `wait-for-lock-turn.ps1` processes running anywhere on the machine - nobody was polling, so a dead reservation-expired head sat until the coarse `SessionStaleMinutes` fallback instead of the tight `ReservationMinutes` forfeit window. This hook cannot evict a session's own ticket (both sweep passes exclude self) and never blocks the prompt - always exit 0, empty stdout.

## Keeping this file honest

`scripts/quality/assert-hook-inventory.ps1` compares the registered set against the names above and fails on any divergence in either direction. It is wired into `scripts/quality/assert-fast-gates.ps1`, so `.\a.ps1 fg` runs it.

It judges asymmetrically, by design. The **project** half is judged strictly and always: it is version-controlled, so the verdict reproduces on any machine. The **global** half is judged only when `~/.claude/settings.json` is readable; where it is absent the gate prints one advisory line and does not fail, because a red that cannot be fixed from the repository is a red that teaches people to bypass the gate.

## Authoring a new hook

- **Test the registered pre-filter, not only the hook.** Every guard is wired as a bash `case` pattern that skips the ~170-250 ms PowerShell start on calls it would allow anyway. A pre-filter that stops matching makes the hook unreachable, and **an unreachable hook is indistinguishable from one that allows everything** - nothing fails and nothing logs. S1594 shipped `*[A-Z]-[A-Z]*` for cmdlets and it matched none: in `Select-Object` the character before the hyphen is lowercase. Test against both must-reach and must-skip commands, under `C:\Program Files\Git\bin\bash.exe` - the `system32` one is WSL and tests the wrong shell.
- **Prefer correcting over refusing where possible.** A `PreToolUse` hook can return `updatedInput` and fix the call; the refusal costs a turn, the correction costs nothing. The rewrite must carry the **full** input object, and only `additionalContext` reaches the model - `permissionDecisionReason` does not, which makes a silent rewrite a correctness hazard.
- **Prefer making a name work over refusing it.** A missing interpreter is cheaper to shim onto the PATH than to guard, because no hook can fix and retry a failed command.
- **Fail open.** A hook that changes what the model reads corrupts content when it errs, rather than merely gating a call.
- Add the hook to the inventory above in the same change - the gate requires it.
