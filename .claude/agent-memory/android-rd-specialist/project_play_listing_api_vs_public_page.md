---
name: play-listing-api-vs-public-page
description: androidpublisher reads the CONSOLE listing, not the public store page - a listing publish can read back green via API while play.google.com still serves the old texts and screenshots
metadata:
  type: project
---

The androidpublisher `edits().listings/images` read returns what the Play Console holds, **not** what
play.google.com actually serves. Confirming a store-listing publish only through the API is a false
PASS.

Observed 2026-08-05 on `com.sza.fastmediasorter`:

- API: en-US/ru-RU/uk carry the S1256 texts and 8 new `phoneScreenshots` each; downloading the API's
  own image URL returned exactly the local `play/listing/en-US/images/phoneScreenshots/01.png`.
- Public page (anonymous curl, desktop and mobile UA): **old** full description
  ("CORE FUNCTIONALITY: FILE SORTING AND ORGANIZATION"), 5 old portrait screenshots plus the 12 old
  landscape 7"/10"/TV frames, `Updated on Jul 27, 2026`, version `2.60.7270.415`.
- Meanwhile the API's production track reads `2.60.8042.332` / versionCode `260804233`,
  `status=completed`.

So everything from 2026-07-28 onward - the listing refresh *and* two later releases - sat unpublished
publicly while every API probe read green. The likeliest cause is Managed publishing holding the whole
batch behind the console's Publish button (one switch holds releases and listing changes alike); the
alternative is a listing/app review. Neither is visible to the API - see
[[play-console-api-access]].

**Why:** S1256 was closed `Verified` on a post-commit API probe and archived. The store page never
changed, and nobody noticed for eight days until the owner looked at it himself.

**How to apply:** to verify a store-listing or release publish, fetch
`https://play.google.com/store/apps/details?id=com.sza.fastmediasorter&hl=en_US&gl=US` and check the
served copy - the `Updated on` date, the first lines of the full description, and the actual image
bytes (the public CDN token differs from the API's, so ids are not comparable - download and look).
Only that is evidence. If the public page lags, ask the owner for a Play Console **Publishing
overview** screenshot; the API cannot tell "held by managed publishing" from "in review". Probe script:
`temp/S1266/read_live_listing.py` (opens an edit, reads, deletes - never commits).
