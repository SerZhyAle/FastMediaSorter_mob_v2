---
name: write-detekt-clean-first-time
description: Author Kotlin that passes the detekt gate on the first build - avoid the 3-iteration fix loop
type: feedback
---

Write new/edited Kotlin detekt-clean on the first pass instead of discovering findings on the post-change detekt gate and iterating. The recurring offenders in this repo's detekt config:

- **Log lines > 120 chars** - a `Timber.d(..)`/probe on one long line trips `MaximumLineLength` + `ArgumentListWrapping`. Keep a debug probe to ONE short line (drop non-essential args) or wrap args one-per-line. A `Timber.d("Sxxxx: ..", a, b)` with 2 short args under 120 is fine; 3+ long args overflow.
- **Magic numbers** - `MagicNumber` fires on bare literals like `1000L`, `4`. Fixes: `TimeUnit.MILLISECONDS.toSeconds(..)` for ms->s (no literal at all); a `const val` in a companion object (detekt's `ignoreConstantDeclaration=true` exempts const); reuse an existing const (e.g. `SQLITE_IN_CLAUSE_LIMIT=900` in `FavoritesRepositoryImpl`). `ignoreNumbers` only covers -1/0/1/2.
  - **A top-level `object` is not a companion object.** `ignoreCompanionObjectPropertyDeclaration=true` exempts `companion object { val TTL_MS = TimeUnit.MINUTES.toMillis(20) }`, so the identical line inside a plain `object Policy { .. }` FAILS while the companion version passes - S1175, and it reads as a false positive until you know the two rule flags differ. Fix: `private const val TTL_MINUTES = 20L` beside it and pass the const.
- **Do NOT add `@Suppress("Rule")` to a method that already has a baselined finding** - it shifts the detekt baseline signature for that method and *surfaces* the previously-baselined finding (e.g. adding `@Suppress("MagicNumber")` to a test method un-baselined its `FunctionNaming` underscore-name finding). For test literals, use a companion `const val` index, not a method-level @Suppress.
- **Multi-line `if/else` without braces** - `MultiLineIfElse` fires when `if (cond) stmt` and `else stmt` sit on two lines with no `{}`. Add braces to both branches (or collapse to one line if it fits <=120). S0777: `if (x) a()\nelse b()` tripped it; bracing both fixed it.
- **More than 2 `return` in one function** - `ReturnCount` (limit 2). A guard-`return` + two early-`return`s = 3 -> fail. Restructure: one early-return guard then a `when {}` for the rest (no returns in the branches). S0777: a classify+toggle+network-gate `playChannel` with 3 returns collapsed to one guard-return + a 3-arm `when`.

**Why:** in S0822 the first three cost a full @Suppress detour + 3 detekt re-runs (~2 min each); S0777 added `MultiLineIfElse`/`ReturnCount` on a fresh helper. The gate caught real issues, but all were avoidable at authoring time.

**How to apply:** before the closing build of any Kotlin change, eyeball touched code for: log calls/lines >120 chars, bare numeric literals, multi-line brace-less if/else, and functions with 3+ returns; fix inline. Close a dirty tree with `post-change.ps1 -ScopeToFile` - it diff-scopes detekt to YOUR file and surfaces exactly your new findings (the project-wide `fg -IncludeDetekt` buries them under other tickets' WIP). Separate gate, same "be clean first time" theme: registering a `DefaultLifecycleObserver` via `lifecycle.addObserver(this)` needs a matching `lifecycle.removeObserver(this)` in `onDestroy` or `assert-listener-symmetry` (counts |add*Observer - remove*Observer| per file) flags it, even though the lifecycle auto-removes. See also [[detekt-gate-dirty-tree]] and [[closure-on-dirty-tree]].
