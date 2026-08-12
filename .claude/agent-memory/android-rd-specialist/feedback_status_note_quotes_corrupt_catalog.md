---
name: status-note-quotes-corrupt-catalog
description: Never put double quotes in a -StatusNote - it silently overwrites the ticket's name field in the journal
type: feedback
---

Write `-StatusNote` without any `"` character. Quote a probe tag as `tagged S1474 in logcat`, never `tagged \"S1474:\" in logcat`. After any `Block*` transition, read the record back with `select.ps1 -Id Sxxxx -Format json` and confirm `name` still matches the slug in `file`.

**Why:** on 2026-08-08, closing S1474, a note ending `Probe tags: 3 lines tagged \"S1474:\" in logcat.` left the journal holding `"name":"S1474:\\ in logcat."` - the ticket had been renamed to a fragment of its own note, and the note was truncated at the first escaped quote. `status` and `file` survived, so no gate fired; the corruption is visible only on read. A renamed ticket breaks `select.ps1 -Name`, the release-queue reconciliation that matches on name, and every later reader. Repair is `update.ps1 -Id Sxxxx -Name '<slug>' -StatusNote '<quote-free text>'`.

**How to apply:** the debug-tag convention actively invites this, because the tag itself is written `Timber.d("Sxxxx: ..")` - describe it in words instead of quoting it. Ticketed as S1504; until that lands, the read-back is the only check. See also [[blockneedusertest-status-before-gate]].
