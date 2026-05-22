---
name: user-author-style
description: Author style - use `..` (two dots) instead of `...`; always use `ё`/`Ё` in Russian where grammatically correct
metadata:
  type: user
---

The author's style is non-negotiable in every user-facing string, doc paragraph, release note, and chat reply that ships in Russian or English text:

- Ellipsis is `..` (two dots), never `...` or `…`. Applies to dialogues, toasts, headings, marketing copy, code comments visible to users.
- Russian text uses `ё`/`Ё` wherever grammatically correct - `всё`, `ещё`, `приём`, `Ёлка`, `даёт`. Do not silently downgrade to `е`/`Е` even when the surrounding source file does.

These are intentional style choices, not typos. Mirror them in EN/RU/UK doc updates the same way: only the Russian-letter rule applies to Russian; the `..` ellipsis applies to all three locales.
