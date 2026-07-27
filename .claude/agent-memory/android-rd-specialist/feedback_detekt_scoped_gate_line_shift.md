---
name: detekt-scoped-gate-flags-shifted-preexisting-findings
description: post-change -ScopeToFile detekt can flag pre-existing findings on lines you never touched, because your insertions shifted their line numbers - fix them, don't hunt for what you broke
metadata:
  type: feedback
---

When `post-change.ps1 -ScopeToFile` reports `assert-detekt: NEW findings in changed file(s)`, the findings are not necessarily yours. Adding lines shifts every later finding's line number, and the scoped comparison then reports untouched pre-existing findings as new. Read `app_v2/build/reports/detekt/detekt.txt` (grep the file name, case-insensitive - the gate lowercases paths in its own output) to see the actual rule and line before assuming you introduced it.

**Why:** on S1203 the gate failed twice. The first round was genuinely mine (`MagicNumber` on a `4096` literal). The second round was three findings - `MaxLineLength` + two `ArgumentListWrapping` - all on one 131-char `printWriter?.println(..)` line that predated the change and that my edits had merely pushed from line 262 to 279. Their detekt `Signature` is content-based and identical in HEAD, so nothing about them was new except position.

**How to apply:**
- Fix the finding rather than trying to restore line numbers. It is real debt in a file you are already touching (CLAUDE.md Rule 7), so wrapping the long line is the correct move and it also drops the project-wide count.
- Do not reach for `@Suppress` - and never on a method that already carries a baselined finding, which shifts the baseline signature and surfaces new rules.
- Practical shortcut: once the gate names a file, fix *every* finding detekt lists for that file. The report is the full current set, so clearing it makes the gate pass regardless of which ones the diff considered new.
- Related: [[detekt-baseline-signature-resurface]] covers the other resurfacing mechanism (a signature change, e.g. +1 constructor param, re-keys a baselined finding).
