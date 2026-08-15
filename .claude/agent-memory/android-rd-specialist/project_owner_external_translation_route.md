---
name: owner-external-translation-route
description: Owner has an external bulk-translation service ("волшебный ящик") that takes a flat one-line-per-phrase English file and returns the same file per language - the cheap path for any locale work
metadata:
  type: project
---

Since 2026-08-13 the owner supplies bulk translations himself through an external service -
`onlinedoctranslator.com`: he carries a flat text file of English UI phrases, one per line, and brings
back the same file in each target language with the identical line count. It returns the answer under
its own name (`all_texts_en.en.zh-CN.txt`), and it uses `zh-CN` where the app declares `zh-Hans`, so
that one locale needs `-Locale zh-Hans` passed by hand. Tooling for it is `scripts/utils/locale-bulk-export.ps1` and
`locale-bulk-import.ps1` (S1420); the export spans several source sets at once (`-SourceSet
main,vr,noLegal`) and the sidecar `all_texts_index.jsonl` is what maps a line back to its key.

**Why:** hand-translating the corpus was the whole cost of S1420 - roughly 43 000 individual
translations across ten best-effort locales. The owner's route removes that cost entirely, which also
changed a product decision that had blocked Phase 08 for four days: once the flavor sets were 76 extra
lines in the same trip rather than a separate campaign, he ruled all ten languages for `vr` and
`noLegal` immediately.

Measured over the full round of ten locales on 2026-08-14: 18 820 line-slots, 42 rejected. Per-locale
untranslated fell from 1887 to 89-100, and about 89 of that residue is the deliberate symbol carve-out.

**Exception, ruled 2026-08-15:** for a *small* batch the owner may hand it back to the agent instead. At
the `/spec-prerelease` step 0.8 blocker of nine keys he chose "переводы сейчас сам" over waiting for the
box - the round trip is not worth a release-blocking wait when the batch is single-digit. Same tooling
either way: write one line-file per locale and feed each to `locale-bulk-import.ps1 -Locale <tag>`, which
still enforces the line-count and format-token refusals.

**How to apply:** never plan a hand-translation pass for the ten best-effort locales again - offer the
export instead, and ask which languages he wants back. Offer to translate inline only when the batch is
a handful of keys and something is blocked on it. Line position is the only key binding, so the
two refusals in the import are load-bearing: a differing line count is refused outright, and a line
whose format tokens drifted from English is rejected individually. `en`/`ru`/`uk` stay owner-authored
and strict; this route never writes them. See [[feedback_never_attribute_agent_inference_to_owner]].
