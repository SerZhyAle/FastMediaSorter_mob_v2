---
name: pwsh-authoring-byte-traps
description: PowerShell values corrupt silently - single-quote every literal value; hex/Unicode escapes land as control bytes; array-splat re-parses dash-leading values
type: feedback
metadata:
  type: feedback
---

**Rule 0 (the expensive one): pass every literal VALUE to a pwsh script in SINGLE quotes.** In `'...'` both `$` and backtick are literal; in `"..."` they are not, and "escaping" `$` as `` `$ `` is how a backtick ends up in the shipped data.

**Why:** S0404 phase 05 wrote nine Android string values as `"%1`$d%%"` (double-quoted, `$` backtick-escaped). The backtick landed in `strings.xml`, so `getString(id, percent)` -> `String.format("%1`$d%%", 86)` threw `UnknownFormatConversionException` on the launcher's tray seed path - crash-looping the declared HOME activity, i.e. a device with no working home screen. Written with `'%1$d%%'` it would have been correct with zero escaping. This survived because **no static gate reads string values**: `check_strings_localized.ps1` only proves a key exists in EN/RU/UK, aapt2's `VerifyJavaStringFormat` only errors on multi-arg non-positional strings, and detekt/lint never look. It took an adversarial audit days later to find it.

**How to apply:** any `-Value` / `-Description` / free text handed to `set-android-string.ps1`, `add_to_dev_log.ps1`, `all_features/add.ps1`, etc. -> single quotes, always, even when nothing looks escapable. After writing a format string, grep the result: `Select-String -Pattern '%[0-9]`\$'` must return 0. Related: [[pwsh-bash-dollar-escape-trap]], [[no-backticks-in-bash-args]], [[cyrillic-bash-pwsh-boundary]].

---

When authoring `.ps1` with the Write tool, two further traps cost rework on S0489:

1. **Non-ASCII / `\xNN` regex escapes can be written as literal control bytes.** A regex like `'[^\x00-\x7F]'` or a char class with literal em-dash/curly-quotes (`'[–-]'`) landed in the file as raw NUL/DEL/garbage bytes (file shows as `data`/binary, grep reports "binary file matches", Edit can't match the line). **How to apply:** for non-ASCII handling use a char-code scan instead of a regex - `foreach ($ch in $s.ToCharArray()) { if ([int][char]$ch -gt 127) {..} }` - and build specific code points with `[char]0x2014`, never literal Unicode or `\xNN` inside a single-Write string. Verify with `file <path>` (expect "ASCII text").

2. **PowerShell array-splat `@($script) @arr` re-parses values; a value starting with `-` is taken as a parameter name.** Passing free-text (a log description, a feature name) that begins with `-` via array splat made `add.ps1` bind the wrong parameter ("argument does not belong to ValidateSet"). **How to apply:** splat with a HASHTABLE (`@{ Id=..; Name=.. }`) not an array when any value is user/free text - hashtable splat binds keys to params and does not re-parse values.
