# String reuse and locale coverage

**Ticket:** S1880
**Status:** Resolved

## Evidence

- The Wear resource files already provide `retry`, `connection_failed_with_reason`, and
  `unknown_error` in every locale directory.
- `retry` remains the action label and is not duplicated.
- `connection_failed_with_reason` interpolates a technical reason, and `unknown_error` has no
  contextual recovery instruction. Neither is suitable for the browse state messages.
- Wear contains one base resource directory plus twelve translated directories: ar, zh-Hans, bn,
  de, es, fr, hi, it, pt, ru, uk, and ur.

## Decision

Add five browse-specific keys in `strings_browse.xml` in all thirteen resource directories. They
cover disabled media, an empty result, an unavailable source, a connection failure, and a generic
load failure.
