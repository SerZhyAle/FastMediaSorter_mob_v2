# Agent Hooks - the complete inventory

Claude Code hooks run around your tool calls. One may **refuse** a call before it happens, **rewrite** its input, **observe** its result, **warn** you, or **arm** a gate for the session. This file is the whole live set. It exists because five of the eleven registered hooks were named in no file an agent reads, and two of those five alter the call itself (S1604).

**When a call is refused or a result looks altered, start here.** The refusal text is self-sufficient by design: it names the hook, cites its `CLAUDE.md` rule, states the reason, gives the exact remedy and names the escape hatch, and it arrives on stderr, which is shown to the model. This file carries the full contract behind that one-line remedy.

## The two homes, and why it matters

- **global** - `~/.claude/hooks/`, registered in `~/.claude/settings.json`. Per-machine, **not** version-controlled, and **absent on a fresh machine or a clean checkout**. A behaviour you rely on here may simply not exist for another contributor.
- **project** - `.claude/hooks/`, registered in `.claude/settings.json`. Travels with the checkout, so it is live for everyone who clones the repository.

The canon ships some of these guards in its own `hooks/` folder. Until the *installed* plugin cache carries a given `.ps1`, the hand-wired registration in `~/.claude/settings.json` is the only thing making it live - removing one "because the plugin has it now" silently disarms it. Verify the installed cache, not the marketplace clone.

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
| `guard-bash-slash-arg.ps1` | PreToolUse / Bash | refuses | project | 27 | `.claude/hooks/guard-bash-slash-arg.tests/Run-Tests.ps1` |
| `observe-empty-grep.ps1` | PostToolUse / Grep | observes | project | - | `.claude/hooks/tests/Run-ObserveEmptyGrep-Tests.ps1` |
| `nudge-small-task-tier.ps1` | UserPromptSubmit | nudges | project | - | - |
| `reset-catalog-touch-marker.ps1` | SessionStart | arms | project | - | - |

## Contracts and escape hatches

### Refusing guards

- **`guard-find-command`** (Rule 24) refuses a `find` with a disk-wide start path, and a `find` with no `-maxdepth`. **Escape:** give a concrete start path *and* `-maxdepth N`; a `find` inside a quoted string is untouched. Prefer the `Glob`/`Grep` tools or `dev/CATALOG/scripts/query.ps1` anyway.
- **`guard-ps1-in-bash`** (Rule 25) refuses a `.ps1` in *command-head* position, because Bash cannot execute one and the failure still reports **exit 0** - a broken build looks like a passing one. **Escape:** `pwsh -NoProfile -File ./a.ps1 <cmd>`. A `.ps1` as an argument, or read with `Read`/`grep`, is fine.
- **`guard-fire-and-forget`** (Rule 26) refuses `run_in_background` on a gate, a closure facade or a catalog mutator. **Escape:** a genuinely long job on the same command line (`gradlew`, `a.ps1 d/db/dav/cd/nd/nl/r/fu`) overrides the block. **Coverage limit worth knowing:** it is registered on the **Bash** matcher only, so backgrounding the same script through the PowerShell tool is not intercepted - the rule still binds you there.
- **`guard-bash-unavailable-command`** (Rule 28) refuses three heads: a PowerShell cmdlet, an interpreter absent from this machine (`node`, `npm`, `npx`), and `& {` at the start of a command. **Escape:** the PowerShell tool, or `pwsh -NoProfile -Command "<pipeline>"`. Only a *head* is refused - a cmdlet in quotes, in a heredoc, or as an argument passes. `python3` is deliberately **not** refused: `~/bin/python3` shims to the `python` on PATH.
- **`guard-bash-slash-arg`** (Rule 27) refuses a slash-command name as an argument value in a Bash-tool `pwsh` call, because MSYS rewrites the leading `/name` into the Git installation root **silently**, at exit code 0, corrupting lock and lease diagnostics. **Escape:** double the slash (`//spec-dev ..`), prefix `MSYS2_ARG_CONV_EXCL='*'`, or issue the call from the PowerShell tool. A real path like `/release/x` is never refused; only a name matching `.claude/commands/*.md` closed by whitespace or a quote.
- **`guard-catalog-before-kt-search`** refuses an **unnarrowed** Kotlin search issued before the class catalogue was consulted this session. All three must hold: the tool is `Grep`/`Glob`, the call targets `.kt`, and it carries no path or glob restricting it to a subtree. **Escape:** name a subtree, or run `dev/CATALOG/scripts/query.ps1` first - any successful query writes `temp/catalog-touch.marker`, which is the pass condition for the rest of the session.

### Rewriting and observing

- **`guard-uncapped-read`** does **not** refuse. A `Read` with no `offset`/`limit` against a file over 200 lines is rewritten to `limit: 800`, and when the file is longer it returns a notice naming the real length and how much is hidden. **This is the one hook that changes what you see**, so treat a long file you did not window explicitly as partially read. **Escape:** pass an explicit `offset`/`limit` - never intercepted - and `.claude/commands|skills|templates|reference|agents` are exempt outright.
- **`observe-empty-grep`** attaches context to an empty `Grep` **only when the same pattern, re-run without your `path`, finds something**. It is silent when the widened run also misses, so a false positive is impossible by construction and a confirmed "not here" stays confirmed. It cannot alter the result - a `PostToolUse` hook can only attach context.
- **`warn-context-size`** injects a context-size warning at prompt submit. Advisory.
- **`nudge-small-task-tier`** injects a routing reminder naming `/quick` and `/skill-fix` when the prompt reads as a micro-task. Advisory, always exit 0 - a false fire that refused a prompt would cost more than the miss it prevents.
- **`reset-catalog-touch-marker`** deletes `temp/catalog-touch.marker` at session start, so the Kotlin-search gate is armed once per session rather than passing forever after its first ever query.

## Keeping this file honest

`scripts/quality/assert-hook-inventory.ps1` compares the registered set against the names above and fails on any divergence in either direction. It is wired into `scripts/quality/assert-fast-gates.ps1`, so `.\a.ps1 fg` runs it.

It judges asymmetrically, by design. The **project** half is judged strictly and always: it is version-controlled, so the verdict reproduces on any machine. The **global** half is judged only when `~/.claude/settings.json` is readable; where it is absent the gate prints one advisory line and does not fail, because a red that cannot be fixed from the repository is a red that teaches people to bypass the gate.

## Authoring a new hook

- **Test the registered pre-filter, not only the hook.** Every guard is wired as a bash `case` pattern that skips the ~170-250 ms PowerShell start on calls it would allow anyway. A pre-filter that stops matching makes the hook unreachable, and **an unreachable hook is indistinguishable from one that allows everything** - nothing fails and nothing logs. S1594 shipped `*[A-Z]-[A-Z]*` for cmdlets and it matched none: in `Select-Object` the character before the hyphen is lowercase. Test against both must-reach and must-skip commands, under `C:\Program Files\Git\bin\bash.exe` - the `system32` one is WSL and tests the wrong shell.
- **Prefer correcting over refusing where possible.** A `PreToolUse` hook can return `updatedInput` and fix the call; the refusal costs a turn, the correction costs nothing. The rewrite must carry the **full** input object, and only `additionalContext` reaches the model - `permissionDecisionReason` does not, which makes a silent rewrite a correctness hazard.
- **Prefer making a name work over refusing it.** A missing interpreter is cheaper to shim onto the PATH than to guard, because no hook can fix and retry a failed command.
- **Fail open.** A hook that changes what the model reads corrupts content when it errs, rather than merely gating a call.
- Add the hook to the inventory above in the same change - the gate requires it.
