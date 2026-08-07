---
name: feedback_bash_windows_backslash_dollar_trap
description: In the Bash tool, never write a Windows-style backslash path immediately before a bash `${VAR}`/`$VAR` inside double quotes - bash reads `\$` as an escaped literal dollar sign, silently dropping both the backslash and the expansion, and the command still exits 0.
metadata:
  type: feedback
---

Hit this doing an S1470 pre-edit backup: `cp "...\temp\S1470\${TS}_AppSettings.kt.bak" ...` (Windows-style path, `$TS` set earlier in the same command). Expected destination `temp/S1470/20260807_124708_AppSettings.kt.bak`. Actual: `cp -v` reported success (`exit 0`) writing to `temp/S1470${TS}_AppSettings.kt.bak` - a single stray file directly under `temp/`, one directory level wrong, with the literal text `${TS}` in its name instead of the timestamp. `ls temp/S1470/` right after showed an empty directory, which is what surfaced the bug.

**Why:** inside bash double quotes, `\$` is an escape sequence for a literal `$` - the backslash is consumed and `$` is *not* treated as the start of a variable expansion. So `S1470\${TS}` parses as `S1470` + literal `$` + literal `{TS}` = `S1470${TS}` (no directory separator, no substitution), not `S1470/<value-of-TS>` as the Windows-path notation visually suggests. This is the same root mechanism as [[feedback_pwsh_bash_dollar_escape_trap]] (backslash-before-dollar swallows the dollar in bash double-quote parsing) but a different trigger surface: that memory is about `\$LASTEXITCODE` inside a `pwsh -Command` payload; this one is about plain file-ops (`cp`/`mv`/backup-naming) where a Windows-style `\` path separator happens to land right before a bash variable. No PowerShell involved at all - pure bash gets it wrong on its own.

**How to apply:**

- In the Bash tool, use forward slashes for every path that also contains a bash variable: `cp "$BASE/temp/S1470/${TS}_file.bak" ...`, never `"$BASE\temp\S1470\${TS}_file.bak"`.
- This project's paths are routinely quoted with backslashes (Windows convention, and how the user/CLAUDE.md write them) - when copying such a path into a Bash-tool command that also needs variable interpolation, convert it to forward slashes first rather than leaving the backslashes in place.
- The failure is silent (exit 0, no stderr) - after any backup/copy command that mixes a literal Windows path with a shell variable, verify the destination actually exists at the expected path (`ls` the target directory) rather than trusting the exit code.
- If a backslash must precede a literal `$` on purpose (rare), that is exactly what `\$` means in bash - the trap is specifically mixing that syntax with the Windows path-separator convention by accident.
