---
name: write-detekt-clean-first-time
description: Author Kotlin that passes the detekt gate on the first build - avoid the 3-iteration fix loop
type: feedback
---

Write new/edited Kotlin detekt-clean on the first pass instead of discovering findings on the post-change detekt gate and iterating. The recurring offenders in this repo's detekt config:

- **Log lines > 120 chars** - a `Timber.d(..)`/probe on one long line trips `MaximumLineLength` + `ArgumentListWrapping`. Keep a debug probe to ONE short line (drop non-essential args) or wrap args one-per-line. A `Timber.d("Sxxxx: ..", a, b)` with 2 short args under 120 is fine; 3+ long args overflow.
- **Magic numbers** - `MagicNumber` fires on bare literals like `1000L`, `4`. Fixes: `TimeUnit.MILLISECONDS.toSeconds(..)` for ms->s (no literal at all); a `const val` in a companion object (detekt's `ignoreConstantDeclaration=true` exempts const); reuse an existing const (e.g. `SQLITE_IN_CLAUSE_LIMIT=900` in `FavoritesRepositoryImpl`). `ignoreNumbers` only covers -1/0/1/2.
- **Do NOT add `@Suppress("Rule")` to a method that already has a baselined finding** - it shifts the detekt baseline signature for that method and *surfaces* the previously-baselined finding (e.g. adding `@Suppress("MagicNumber")` to a test method un-baselined its `FunctionNaming` underscore-name finding). For test literals, use a companion `const val` index, not a method-level @Suppress.

**Why:** in S0822 these three cost a full @Suppress detour + 3 detekt re-runs (~2 min each). The gate caught real issues, but they were all avoidable at authoring time.

**How to apply:** before the closing build of any Kotlin change, eyeball touched lines for >120-char log calls and bare numeric literals; fix them inline. detekt is project-wide and slow, so each surprise finding = another full re-run. See also [[detekt-gate-dirty-tree]] (filter detekt output to your own files; never re-baseline others' WIP).
