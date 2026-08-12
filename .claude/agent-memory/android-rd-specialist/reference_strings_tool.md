---
name: strings-tool-mutator
description: scripts/utils/set-android-string.ps1 is the canonical byte-preserving editor for Android string resources across EN/RU/UK; prefer it over hand-editing strings.xml
metadata:
  type: reference
---

`scripts/utils/set-android-string.ps1` is the project's canonical surgical editor for Android string resources. Prefer it over hand-editing the large `strings.xml` files. It is wired into skills `/spec-dev`, `/spec-tech`, `/doc-update`, `/quick`, `/spec-check` and the agent roles.

- `-Action set` (default) - update ONE key's value in ONE locale (`-Locale en|ru|uk -Key -Value`); `-ExpectedOldValue` guard, `-CreateIfMissing` upsert. Single-file (strings.xml). This is the original behavior, kept backward-compatible.
- `-Action add` - create a key across EN/RU/UK in lockstep (`-Key -En -Ru -Uk [-File]`); parity-enforced, fails if key exists in any locale.
- `-Action get|remove|rename|list` - lookup / lifecycle across ALL `strings*.xml` (incl. thematic splits); remove/rename report `.kt`/`@string` references without touching them.
- Text-surgical (regex on raw content), NOT `[xml]` reserialize - preserves comments, escaping, indentation, BOM, line endings. Round-trip verified byte-identical (sha256).
- **Pass RAW text, never pre-escaped.** The tool escapes what it is handed, so a hand-written `\'` (the form you see when READING `strings.xml`, e.g. `Пам\'яті`) is stored as `\\'` and `mergeResources` dies with `Invalid unicode escape sequence in string "{str}"` - a resource failure, not a Kotlin one, so `fk` reports it at the `mergeXxxDebugResources` task with a line number in `values-uk`. Hit on S1178 (2026-08-08) with a Ukrainian apostrophe. Either pass the bare `'` or reword to avoid it.
- `$PSBoundParameters` gotcha: inside a script function it reflects the FUNCTION's params, not the script's - capture script-level bound flags before dispatch.

**Why:** the main `strings.xml` is ~3400 keys / ~290-378 KB per locale; hand-editing risks lost/dup keys, broken escaping, locale drift. `[xml]` load+save would reformat the whole file.

**History:** I first created a separate `scripts/strings_tool.ps1` without checking `scripts/utils/` - it overlapped the existing skill-wired `set-android-string.ps1`. Consolidated into the latter (2026-06-03, owner-chosen), strings_tool.ps1 deleted. Lesson: grep `scripts/utils/` for an existing helper before writing a new string/resource tool.

**Related:** `scripts/check_strings_localized.ps1` (parity audit, split-aware). Strategic spec S0339 (thematic split of strings.xml by FEATURES.md areas, Approved) uses this tool for migration.
