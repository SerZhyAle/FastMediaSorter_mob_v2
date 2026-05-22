---
name: feedback_no_backticks_in_bash_args
description: Never wrap text in backticks when passing as argument to scripts via Bash tool; bash treats `text` as command substitution and strips it
metadata:
  type: feedback
---

Do not use backticks `like-this` inside quoted arguments to PowerShell scripts launched via the Bash tool. The Bash tool runs through `/usr/bin/bash`, which performs command substitution on backticks even inside double-quoted strings - the inner text is executed as a shell command and replaced with its (empty) output, silently stripping the word.

**Why:** On 2026-05-14, an `add_to_dev_log.ps1` call with description `"VrPermissionBridgeFragment was \`private\` (package-private)…"` produced a changelog row with `"was  (package-private)…"` - the literal word `private` was eaten because bash tried to execute it as a command. Same applies to any tooltip/log/comment text containing backticks.

**How to apply:** When passing the `-Description` argument to `add_to_dev_log.ps1` / `add_to_functionality_log.ps1` via the Bash tool, never wrap Kotlin identifiers (`MaterialButton`, `private`, `ViewBinding`) in backticks - bash will eat them. Use single quotes inside the description ('MaterialButton', 'private') or prose ("declared private", "the MaterialButton subclass"). If you need true backtick styling in `dev/CHANGELOG.md` for a Kotlin name, write the changelog via Edit/Write directly, not through the PowerShell shell. Same rule applies to commit messages composed in a Bash heredoc.
