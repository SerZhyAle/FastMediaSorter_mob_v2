---
name: wear-data-item-100kb-and-gson-bytearray
description: The phone-watch page has about 25 KB of usable room, not 100 KB - Gson writes the envelope's ByteArray as a JSON number array, so every payload byte costs four on the wire, and GMS refuses the item silently
metadata:
  type: project
---

**A page sent phone -> watch has roughly 25 KB of usable payload, not the 100 KB the GMS limit
suggests.** Measured 2026-08-21 on a real pair (S1860).

- GMS refuses a data item over 100 KB with `ApiException: 4003: DATA_ITEM_TOO_LARGE`.
- `WearEventEnvelope.data` is a `ByteArray`, and the envelope is serialised with Gson
  (`WearableDataLayerRepositoryImpl.putEnvelopeDataItem`). Gson has no byte-array shorthand: it
  writes `[123,34,105,..]`, about **four bytes of wire per payload byte**. Measured: a ~1.4 KB page
  travelled as a 5705-byte envelope.
- So `MAX_PAGE_THUMBNAIL_CHARS = 64 * 1024` was not "well inside the limit" as its comment claimed -
  it was about six times over it. A fifty-item page spends ~44 KB of wire on names and tokens alone.

**Why this matters beyond one constant:** the refusal is invisible to the watch. It waits out its
`RESPONSE_TIMEOUT_MS` (10 s) and renders "Your phone is out of reach", so an oversized page looks
exactly like a dead Bluetooth link. Every "the page never arrives" report on this bridge should check
for `DATA_ITEM_TOO_LARGE` in the phone's logcat before blaming the connection.

**How to apply:** when adding ANY field to a phone-watch payload, size it against ~25 KB of JSON, not
100 KB, and remember the failure mode is a lie about connectivity rather than an error. `S1893`
carries the fix that would buy the room back (Base64 string instead of a number array - a schema
change on both sides, so it never lands as a tail of another ticket).

Related: [[wear-traffic-proxied-through-phone]] for the other invisible budget on this path,
[[index-wear]].
