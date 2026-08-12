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

**Do not frame a keyless service as a cost tradeoff.** On 2026-08-07 (S1440 quiz) I grouped widget candidates into "cheap" and "цена" buckets, meaning traffic/permission cost, and put the external IP in the paid-sounding one. The owner read "цена" as money and pushed back hard - "не буду я платить за внешний IP не гони! - Разумеется он нам нужен и есть масса возможностей получить его бесплатно, ищи!" - costing a round-trip. Say *what* is being spent (a request to a third party, a runtime permission, a dependency on another ticket) in the option text itself; never use a bare cost/price word for something that is free.

**Known keyless wins - already verified, but re-verify liveness before relying on one:**

- **Public/external IP: fully solvable keyless, no registration, no new dependency.** Verified live 2026-08-07: `checkip.amazonaws.com`, `api.ipify.org`, `icanhazip.com`, `ident.me`, `ifconfig.me/ip` all return the address as plain text over HTTPS. A hand-rolled RFC 5389 STUN binding request (~50 lines on a plain `DatagramSocket`) against `stun.l.google.com:19302` / `stun.cloudflare.com:3478` returns the same address and needs no library either. Ship an ordered list of 2-3 independent hosts, not one - that is what survives a free host disappearing. Trap: `api.ipify.org` answers `520` to `HEAD` but `200` to `GET`, so a HEAD-based liveness probe misreports it as dead.

**Known keyless dead ends - do not re-offer these as options:**

- **Address autocomplete / typeahead is impossible keyless.** Nominatim's usage policy explicitly forbids implementing autocomplete client-side against its API, and every provider that does offer suggestions (Stadia Maps, LocationIQ, Google Places) needs a paid key. So "address field with suggestions" is closed permanently, not deferred - a plain field plus one rate-capped lookup on submit is the most that can be delivered. Recorded on S1175 (ADR-6) after quizzing the owner; do not reopen it as a scope question in a later planning pass.
- Nominatim also bans systematic/bulk querying and requires an app-specific User-Agent, so any map or geocoding feature must cache and must not sweep an area.
