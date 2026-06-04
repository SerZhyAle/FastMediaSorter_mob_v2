---
name: cyrillic-bash-pwsh-boundary
description: Cyrillic passed as a pwsh argument from the Bash tool gets corrupted (mojibake); author a UTF-8 .ps1 via Write and run that instead
metadata:
  type: feedback
---

Passing Cyrillic (RU/UK) text as a `-Ru`/`-Uk`/`-En` argument to a pwsh script through the **Bash tool** boundary corrupts it into mojibake in the written file (e.g. `set-android-string.ps1 -Ru 'Быстрый диктофон'` stored garbage in values-ru/strings.xml). The console also shows mojibake regardless of file correctness (codepage), so console output is NOT a reliable verification.

**Why:** the bash→pwsh argument/console encoding boundary on Windows mangles non-ASCII bytes before PowerShell receives them.

**How to apply:**
- Never pass Cyrillic literals as pwsh CLI args from the Bash tool. Instead, author a temp `.ps1` with the **Write tool** (writes clean UTF-8 without BOM; pwsh parses scripts as UTF-8) that contains the Cyrillic literals in single-quoted strings, then run `pwsh -NoProfile -File temp/<x>.ps1`. Batch all string adds into one such script.
- In that script use single quotes for values so `%1$s` placeholders are literal.
- Verify the result with the **Grep/Read tool** (they decode UTF-8), not by eyeballing console output.
- Same caution applies to any tool that takes Cyrillic via args (commit messages, log descriptions) — prefer here-strings/script files. Related: [[reference_strings_tool]], [[feedback_pwsh_bash_dollar_escape_trap]].
