---
name: user_author_style
description: Author style for all user-visible text - `..` (two dots) instead of `...`; always use `ё`/`Ё` in Russian where grammatically correct
metadata:
  type: user
---

The user has two non-negotiable style preferences for any text I write that he will read or that ends up in the codebase:

- Ellipsis is `..` (two dots), never `...` (three dots or Unicode ellipsis `…`). Applies to all `.md` files, code comments, KDoc, `strings.xml` values, dev-log entries, commit messages, and chat replies.
- Russian text always uses `ё`/`Ё` where grammatically correct: `всё`, `ещё`, `приём`, `её`, `чёрный`. Never replace with `е`.

These are intentional style choices, not typos - do not "fix" them when editing existing files, and do not generate them in new files.

**How to apply:** When editing any `.md` file (including specs, changelog, docs), any `res/values*/strings.xml`, any Kotlin/Java comment or KDoc, and when composing chat replies in Russian, follow both rules. Run a quick mental check on every new ellipsis (`..`?) and every Russian word containing `е` that might actually be `ё`. Commit messages and dev-log descriptions are English so the `ё` rule rarely applies there, but the `..` rule still does for any continuation marks.
