---
name: kdoc-nested-comment-glob-path
description: A glob path like res/xml/*_info.xml inside a Kotlin KDoc opens a nested comment and breaks the file with a misleading "Missing '}'"
metadata:
  type: feedback
---

**Never write a `/*` sequence inside a Kotlin comment - a glob path such as `res/xml/*_info.xml`, `src/main/*` or `values-*/strings.xml` written in KDoc silently opens a nested block comment.** Write `widget_..._info.xml`, `res/xml` + the file name separately, or drop the leading slash.

**Why:** Kotlin block comments **nest** (unlike Java/C). `/*` inside a `/** ... */` KDoc opens a second comment level, so the closing `*/` only returns to level 1 and the rest of the file is swallowed. The compiler then reports `Missing '}'` at the class/object header and `Unclosed comment` at the last line - two errors, neither pointing at the offending line, on a file that looks obviously balanced. Cost one full compile cycle on S1170.

**How to apply:** Any time a comment names a file *pattern* rather than a concrete file. Highest risk in KDoc that documents where a value came from ("read off `res/xml/*_info.xml`"), which is exactly the kind of provenance comment this repo asks for. Same trap in `//` line comments only if the line continues - but the block-comment case is the one that compiles-breaks.

Related: [[pwsh-authoring-byte-traps]] for the shell-side equivalent.
