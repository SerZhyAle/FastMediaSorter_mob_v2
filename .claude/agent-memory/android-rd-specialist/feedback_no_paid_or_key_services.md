---
name: no-paid-or-key-third-party-services
description: Owner refuses paid third-party services and avoids API keys; prefer keyless free endpoints and put the provider behind a seam
metadata:
  type: feedback
---

When a feature needs a third-party service, default to a **keyless, free** endpoint and design so the provider can be swapped later. Do not plan around a paid tier, and do not assume the owner will register for an API key - ask only when no keyless option exists, and say plainly what the key unlocks.

**Why:** on 2026-07-24 (S0426 weather block) the owner answered the provider question with "давай попробуем пока без ключей.. Но платить я не намерен тут ни за что", choosing keyless Open-Meteo over the two key-based providers the spec recommended, and accepting Open-Meteo's non-commercial licence risk himself on the grounds that the project is non-commercial. He also asked me to re-search for further keyless alternatives rather than take the spec's key-based recommendation.

**How to apply:**
- Research keyless options first (and re-check them - the owner asks for a fresh sweep even when a prior research pass exists).
- State the licence/ToS risk once, in one paragraph, then implement the owner's choice - see [[argue-then-obey]].
- Put the service behind a one-interface seam so a licence change is a one-class swap, not a refactor; note the fallback candidate in the spec.
- If a key genuinely cannot be avoided, implement against an empty-key-degrades-gracefully path and hand the ticket over for the key rather than blocking the whole feature.
