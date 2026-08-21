---
name: ktlint-import-order-sort-dont-guess
description: Inserting a Kotlin import by hand trips ktlint ImportOrdering nearly every time; sort the whole block programmatically instead of guessing the slot
metadata:
  type: feedback
---

When adding an import to a `.kt` file, do NOT reason about where the line belongs. Re-sort the entire import block programmatically in the same edit: lexicographic by fully-qualified name, with `java.`, `javax.` and `kotlin.` LAST.

**Why:** four separate `detekt-scoped` failures in one ticket (S1846, 2026-08-20), all `ImportOrdering`, all from placing an import next to a plausible neighbour. Each cost a lock cycle, a detekt run and a compile. The rule is not "alphabetical": `kotlin.io.path.createTempDirectory` looks like it belongs beside `kotlinx.coroutines.*` and does not - it goes to the very end, after `org.junit.*`. `dagger.hilt.android.qualifiers` sorts before `dagger.hilt.android.lifecycle`? No - `lifecycle` < `qualifiers`, and eyeballing gets it backwards.

**How to apply:**
- One-liner that fixes any file, run inside the same CODE.LOCK window as the edit:
  ```python
  lines = src.split('\n')
  idx = [i for i, l in enumerate(lines) if l.startswith('import ')]
  start, end = idx[0], idx[-1] + 1
  block = sorted({l for l in lines[start:end] if l.startswith('import ')},
                 key=lambda i: (1 if i[7:].startswith(('java.', 'javax.', 'kotlin.')) else 0, i[7:]))
  lines[start:end] = block
  ```
- The `set` in there also removes a duplicate import, which is the other way a hand-added line fails.
- Run `detekt-scoped.ps1 -ChangedFiles ..` before the compile, not after: it is ~2 s against ~40 s, and `ImportOrdering` never blocks compilation, so a green build proves nothing about it.
- Related: [[detekt-ktlint-import-layout]] holds the layout rule itself; this entry is about the mechanic that keeps getting it wrong.
