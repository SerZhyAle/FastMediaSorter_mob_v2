---
name: maestro-suite-needs-ru-app-locale
description: Maestro flows match Russian visible text, so an English-rendering app fails them instantly; and mojibake in the trace log is NOT the cause
metadata:
  type: feedback
---

The `maestro/` suite addresses many controls by **Russian** visible text (`"Общие"`,
`"Оставить текущий"`, `"Отправить отчёт о сбое?"`) because they carry no stable view id. Set the
**per-app** locale before any run, not just the device locale:

```powershell
pwsh -NoProfile -File scripts/devtest/adb.ps1 shell -Cmd "cmd locale get-app-locales com.sza.fastmediasorter.debug"
pwsh -NoProfile -File scripts/devtest/adb.ps1 shell -Cmd "cmd locale set-app-locales com.sza.fastmediasorter.debug --locales ru-RU"
```

**Why:** on 2026-08-13 (S1612) `RFCR110NBQJ` had an EMPTY per-app locale while the system locale
was `en-US`, so the app rendered in English and `_shared/settings_select_general_tab.yaml` died on
its first tap - `Element not found: Text matching regex: Общие`. The device locale list still
contained `ru-RU`, so checking only `persist.sys.locale` looks fine and proves nothing.

**The trap that cost the most time:** the runner's console and the `temp/*_maestro_*.log` trace
render Cyrillic as `?????` on a Windows host **even when the match succeeds**. I spent a diagnostic
round treating that as a PowerShell/JVM encoding bug and inspecting file bytes (the YAML was valid
UTF-8 all along). What disproved it: an older passing log showed the identical `Tap on "?????"...
COMPLETED`. Garbled log rendering is never evidence of a matching failure.

**How to apply:** when a Maestro flow fails on a text tap, dump the live UI hierarchy
(`uiautomator dump`) and read what the screen ACTUALLY says before touching encoding or the flow.
Diagnose from the hierarchy, never from the log's rendering of the label. Setting the app locale is
a reversible flip and is inside the test device's blanket authorization - see
[[test-device-galaxy-s21]] and [[never-grant-system-roles-on-owner-phone]].

Related: [[cyrillic-bash-pwsh-boundary]] is a genuinely different failure - that one corrupts bytes
at the tool boundary; this one corrupts only the display.
