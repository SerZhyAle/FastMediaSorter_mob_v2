---
name: pwsh-authoring-byte-traps
description: Authoring PowerShell via Write tool - hex/Unicode escapes can land as control bytes; array-splat re-parses dash-leading values
type: feedback
metadata:
  type: feedback
---

When authoring `.ps1` with the Write tool, two traps cost rework on S0489:

1. **Non-ASCII / `\xNN` regex escapes can be written as literal control bytes.** A regex like `'[^\x00-\x7F]'` or a char class with literal em-dash/curly-quotes (`'[–-]'`) landed in the file as raw NUL/DEL/garbage bytes (file shows as `data`/binary, grep reports "binary file matches", Edit can't match the line). **How to apply:** for non-ASCII handling use a char-code scan instead of a regex - `foreach ($ch in $s.ToCharArray()) { if ([int][char]$ch -gt 127) {..} }` - and build specific code points with `[char]0x2014`, never literal Unicode or `\xNN` inside a single-Write string. Verify with `file <path>` (expect "ASCII text").

2. **PowerShell array-splat `@($script) @arr` re-parses values; a value starting with `-` is taken as a parameter name.** Passing free-text (a log description, a feature name) that begins with `-` via array splat made `add.ps1` bind the wrong parameter ("argument does not belong to ValidateSet"). **How to apply:** splat with a HASHTABLE (`@{ Id=..; Name=.. }`) not an array when any value is user/free text - hashtable splat binds keys to params and does not re-parse values.
