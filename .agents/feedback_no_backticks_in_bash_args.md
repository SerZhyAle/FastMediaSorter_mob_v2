---
name: feedback-no-backticks-in-bash-args
description: Never wrap text in backticks when passing as argument to scripts via Bash tool; bash treats `text` as command substitution and strips it
metadata:
  type: feedback
---

Do not use backticks `like-this` inside quoted arguments to PowerShell scripts launched via the Bash tool. The Bash tool runs through `/usr/bin/bash`, which performs command substitution on backticks even inside double-quoted strings - the inner text is executed as a shell command and replaced with its (empty) output, silently stripping the word.

**Why:** On 2026-05-14, an `add_to_dev_log.ps1` call with description `"VrPermissionBridgeFragment was \`private\` (package-private)…"` produced a changelog row with `"was  (package-private)…"` - the literal word `private` was eaten because bash tried to execute it as a command. Same applies to any tooltip/log/comment text containing backticks.

**How to apply:** When passing free-form English text (descriptions, commit messages, log entries) as a CLI argument via the Bash tool, do not use backticks for code styling. Use single quotes inside ('private', 'class X'), or prose ("declared private"), or escape with `\\\`` only when truly necessary. Markdown styling that needs backticks should be added directly via Edit/Write, not through a bash-shelled CLI argument.
