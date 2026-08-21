---
name: wear-no-installed-base-no-wire-compat
description: Owner ruling 2026-08-20 - the watch app has no installed base, so a watch-phone wire schema may be raised without version negotiation or a compatibility path.
metadata:
  type: project
---

The watch app has **no installed base**, so a change to the watch-phone Data Layer wire schema does not
need version negotiation, a migration path, or support for an older watch build. Both sides are raised
at once. Owner's words, 2026-08-20, deciding S1846: «нет никаких "старых сборок" это начала начал для часов».

**Why:** the phone app ships publicly and its data formats are one-way commitments, so the reflex in this
repo is to protect compatibility. The watch module has never shipped to users - carrying a compatibility
path for it costs design work and dead branches that protect nobody. The ruling is about the *watch* wire
and watch-local storage only; anything the phone persists for itself keeps its usual obligations.

**How to apply:** when a wear ticket needs a field the transport lacks
(`WEAR_PHONE_RESOURCE_SCHEMA_VERSION` and friends), bump the schema on both sides in the same ticket and
skip the negotiation design. Do not price a wear transport change as if it were a public format change,
and do not offer the owner a "keep v2 working" option. Verify the premise still holds before a *public*
watch release exists - the ruling expires the day the watch app ships to real users.
See [[wear-play-publishing-gaps]] for where that day would come from.
