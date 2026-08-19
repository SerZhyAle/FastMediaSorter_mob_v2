---
name: python-heredoc-eats-backslash-escapes
description: A python3 heredoc run through the Bash tool loses one backslash level, so `\a` becomes BEL and `\b` becomes backspace inside spec text - build such literals from chr(92)
type: feedback
---

When writing text through `python3 - <<'PY'` in the Bash tool, never put a backslash escape inside the
string literal. Build it from `chr(92)` instead:

```python
B = chr(92)
line = "`." + B + "a.ps1 flr`"          # .\a.ps1 flr
pat  = "'" + B + "b" + "a" + B + "." + "ps1" + B + "s" + "+(..)" + B + "b" + "'"
```

**Why:** the heredoc is single-quoted, so bash preserves it, but one backslash level is still lost before
Python parses the source - Python then sees `\a` and `\b` as escapes and writes **BEL (0x07)** and
**backspace (0x08)** into the file. Both are invisible in normal output. Measured 2026-08-19 on S1807 and
S1810: six `.\a.ps1` occurrences became `.` + BEL + `.ps1` inside a spec that had already been audited and
flipped to `Verified`, and a quoted PowerShell regex lost both its `\b` word boundaries. Python emits a
`SyntaxWarning: invalid escape sequence` for the ones it does not recognise (`\.`, `\g`) - that warning is
the tell that the escapes were eaten, and the ones it DOES recognise (`\a`, `\b`, `\t`, `\n`, `\f`, `\v`)
pass silently, which is the dangerous half.

**How to apply:** the trigger is any Windows path, `.\a.ps1` target, or regex written through a python
heredoc - so most spec-writing this repo does. After writing, audit with
`tr -cd '\007\010\013\014' < file | wc -c` and expect `0`; a `SyntaxWarning` in the run's output means
audit before moving on. The Edit tool has no such layer and is the safer choice for a single precise splice.
See [[edit-line-delete-splice]], [[no-ellipsis-edits-in-verbatim-code-spans]].
