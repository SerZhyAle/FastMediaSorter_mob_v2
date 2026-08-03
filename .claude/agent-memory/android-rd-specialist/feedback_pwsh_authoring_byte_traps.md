---
name: pwsh-authoring-byte-traps
description: PowerShell values corrupt silently - single-quote every literal value; hex/Unicode escapes land as control bytes; array-splat re-parses dash-leading values
type: feedback
metadata:
  type: feedback
---

**Rule 0 (the expensive one): pass every literal VALUE to a pwsh script in SINGLE quotes.** In `'...'` both `$` and backtick are literal; in `"..."` they are not, and "escaping" `$` as `` `$ `` is how a backtick ends up in the shipped data.

**Why:** S0404 phase 05 wrote nine Android string values as `"%1`$d%%"` (double-quoted, `$` backtick-escaped). The backtick landed in `strings.xml`, so `getString(id, percent)` -> `String.format("%1`$d%%", 86)` threw `UnknownFormatConversionException` on the launcher's tray seed path - crash-looping the declared HOME activity, i.e. a device with no working home screen. Written with `'%1$d%%'` it would have been correct with zero escaping. This survived because **no static gate reads string values**: `check_strings_localized.ps1` only proves a key exists in EN/RU/UK, aapt2's `VerifyJavaStringFormat` only errors on multi-arg non-positional strings, and detekt/lint never look. It took an adversarial audit days later to find it.

**How to apply:** any `-Value` / `-Description` / free text handed to `set-android-string.ps1`, `add_to_dev_log.ps1`, `all_features/add.ps1`, etc. -> single quotes, always, even when nothing looks escapable. After writing a format string, grep the result: `Select-String -Pattern '%[0-9]`\$'` must return 0. Related: [[pwsh-bash-dollar-escape-trap]], [[feedback-no-backticks-in-bash-args]], [[cyrillic-bash-pwsh-boundary]].

---

When authoring `.ps1` with the Write tool, two further traps cost rework on S0489:

1. **Non-ASCII / `\xNN` regex escapes can be written as literal control bytes.** A regex like `'[^\x00-\x7F]'` or a char class with literal em-dash/curly-quotes (`'[–-]'`) landed in the file as raw NUL/DEL/garbage bytes (file shows as `data`/binary, grep reports "binary file matches", Edit can't match the line). **How to apply:** for non-ASCII handling use a char-code scan instead of a regex - `foreach ($ch in $s.ToCharArray()) { if ([int][char]$ch -gt 127) {..} }` - and build specific code points with `[char]0x2014`, never literal Unicode or `\xNN` inside a single-Write string. Verify with `file <path>` (expect "ASCII text").

2b. **This repo's gate scripts report via `Write-Host`, so their output is NOT on the pipeline.** `$out = & scripts/check_strings_localized.ps1 ...` comes back empty and any `Where-Object` over it silently matches nothing - the text still appears on screen (Write-Host writes straight to the host), so it reads as "my filter is wrong" rather than "I captured nothing". **How to apply:** to capture or filter a project gate's report, merge the information stream: `& $script ... 6>&1 2>&1 | ForEach-Object { [string]$_ }`. Confirmed on `check_strings_localized.ps1`; assume the same for any `assert-*.ps1` / gate that colourises output. Note `$LASTEXITCODE` is still set correctly either way - prefer the exit code over parsing text when you only need pass/fail.

2c. **PowerShell `-match` / `-notmatch` are case-INSENSITIVE by default.** Filtering a report for the marker `MISS` also matched every key literally named `*_missing` (`s0200_card_diag_cct_missing`, `streams_shortcut_channel_missing`), inflating a 21-row result into a screenful and hiding the real rows. **How to apply:** use `-cmatch` / `-cnotmatch` whenever the pattern is an upper-case status token (`MISS`, `FAIL`, `OK`, `SKIP`) that could also occur as ordinary lower-case words in the data.

2. **Array splat is ALWAYS positional - it never binds parameter names.** `& $script @arr` where `$arr = @('-File','x','-ChangeType','Kotlin')` does not bind `-File`; the literal string `-File` is consumed as positional arg 1 and every later value shifts by one, so a `ValidateSet` param several positions down reports someone *else's* value ("The argument \"spec-dev\" does not belong to the set \"Doc,Script,Config,Kotlin,Xml,Mixed\""). The error names the wrong parameter, which is why this reads as a bad argument rather than a bad splat. Free text starting with `-` is a second, separate way array splat goes wrong. **How to apply:** splat a HASHTABLE (`@{ File=..; ChangeType=..; ScopeToFile=$true }`) whenever you want named binding - which is essentially always for this repo's `-Action`/`-ChangeType` style scripts. Switches become `Key = $true`. Reach for an array only when the target genuinely takes positional arguments. Cost when ignored: two wasted gradle-backed background runs on S1189 phase 01 before the message was read as a splat bug.
