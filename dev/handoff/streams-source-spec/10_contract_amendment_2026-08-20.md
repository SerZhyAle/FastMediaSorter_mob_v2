# Streams Source Spec - 10 - Contract Amendment, 2026-08-20

**There is one contract, and it is authored here.** Files 01, 03, 04 and 09 of this set define the bank,
the CSV, the favicon atlas and the artwork atlases. This file amends them with what the 2026-08-19
catalog incident and the exchange with the StreamsPlayer maintainer settled. Where a consumer's current
behaviour differs from a rule below, the consumer changes - not the rule.

Each item states the rule, the evidence behind it, and what a consumer must do differently. Items marked
**[NEW]** change consumer behaviour; the rest restate an existing rule that turned out to be readable in
more than one way.

---

## A. A build is atomic: the CSV and the atlas are one artifact **[CONTRACT]** **[NEW]**

`favicon_index` is an offset into the atlas that arrived **in the same ZIP**. It carries no meaning
against any other atlas.

**Rule.** A consumer that cannot load this build's atlas - absent, over the ceiling, corrupt - MUST also
discard this build's `favicon_index` values and render no icon. It MUST NOT retain a previously
installed atlas and apply the new indices to it.

**Why.** Applying fresh indices to a stale sheet does not produce missing icons, it produces *wrong*
ones: a channel shows another station's logo on a UI that looks entirely healthy. Of the three possible
outcomes - correct icons, no icons, wrong icons - the third is the only one the user cannot detect and
the only one a support report cannot describe. Missing icons are recoverable by a refresh; wrong icons
are believed.

**Producer side.** The atlas is now checked where it is built, not at publish: `Assert-AtlasBudget`
refuses an over-cap sheet and rolls back to the previous file, so an over-cap atlas never reaches the
ZIP (S1827). Publishing a `favicon_index`-carrying CSV with no atlas is still refused outright (S0925).

**Consumer change.** On the `bank_atlas=absent` path, clear the icon map for this build instead of
keeping the old one.

---

## B. The atlas ceiling is a named shared constant: 31457280 B (30 MiB) **[CONTRACT]**

Three code bases carried their own literal of this number and agreed by luck. It now has one written
home: `delivery/stream-catalog/README.md`, section "Atlas byte ceiling". Cite that instead of an
independent constant.

- Geometry is unchanged: 32 px tiles, 16 columns, row-major, capacity `(width / 32) * (height / 32)`.
- Capacity is **derived from the tile count**, not a budget: the packer sizes the sheet to
  `ceil(tiles / 16)` rows. A leftover slot at the end is the remainder of that rounding, not headroom.
- Occupancy as measured 2026-08-20: 6 992 874 B = 22,2 % of the ceiling, 5 743 tiles at about 1 218 B
  per tile, room for roughly 25 800 tiles at that density. Every atlas build now prints this line, so
  the margin is visible per run rather than discovered at publish.

---

## C. Blank `favicon_index` means "no icon"; `0` is a real tile **[CONTRACT]**

Restated because exactly one row in the live bank depends on it: `0 N - Chillout on Radio (AAC+)` owns
tile 0. A consumer that treats `0` as "absent" gives that station no icon; a producer that writes `0`
for "absent" gives every icon-less channel someone else's picture. Neither is allowed.

---

## D. A row's absence is not authority to delete user-authored state **[CONTRACT]** **[NEW]**

**Rule.** A bank that no longer lists a URL means "this build does not offer this channel". It does not
mean the user's own data about that channel is void. Pins and their order, collection membership,
playback history and every other user-authored value keyed by the URL MUST survive the row's absence,
and MUST reattach if the URL returns in a later bank. Tombstone the row; do not delete what the user
made.

**Why.** This is not a hypothetical. On 2026-08-19 the published bank went from 19 534 to 17 628 rows.
Of the 1 906 rows removed, **1 512 (79 %) carried the verdict `unknown`** - "the producer could not
measure this row" - and **1 321 were live stations of a single provider**, confirmed alive the next day
by decoding actual audio from 20 of 20 sampled URLs. Not one of those removals was a decision about a
channel; all of them were a measurement failure on the producer side. Because both sides delete on
absence and mint a new id on re-add, re-publishing the identical previous bytes does not restore a
single pin.

The rule stands independently of that bug being fixed: a producer defect must not be able to reach
through the format and destroy user data downstream. The producer cannot be the only safeguard, because
the producer is what failed.

**Both sides.** We are implementing the same rule in the Android app (S1832).

---

## E. `access` is an opaque token, and today carries no value **[CONTRACT]**

**Rule.** Blank means open. Any non-empty value means a restriction the consumer does not model - treat
it as opaque, never switch on a closed set of tokens. This rule is the load-bearing part and it does not
depend on what the column currently holds.

**Current state, and a correction.** An earlier revision of this file asserted "0 of 17 628 rows carry a
non-empty `access`". That was a measurement of the 2026-08-19 build stated as a general fact, and the
consumer was right to check it: the first 2026-08-20 build carried **9 rows flagged `geo`**, of which
today's probe found 8 alive and 1 unmeasurable. Those flags were stale - left over from the S1117 era,
when the producer tagged region-locked rows instead of dropping them.

They survived because that build's prune was applied by hand from a verdict report, to avoid paying for
a second full probe, and the hand-applied step reproduced only the row removal. The producer's own
post-probe path also **re-derives `access` from the current run's verdicts**, blanking every row the run
did not call `geo`, and that step was omitted. Republished 2026-08-20 11:52 with the column re-derived:
0 of 18 908 rows carry a non-empty `access`, verified against the live asset.

Since the owner ruling of 2026-08-19 the producer *drops* region-locked rows rather than tagging them,
so `geo` has no producer. The column stays in the schema because column identity is part of this
contract, not because it is populated. A consumer's "region restricted" branch is unreachable today and
will start working again on its own if a producer for the token returns - which is exactly why the rule
above, and not the current count, is the thing to implement against.

---

## F. Read artwork by stable name plus the manifest, never by pinned revision **[CONTRACT]** **[NEW]**

**Rule.** Consume the stable names and invalidate on the manifest:

- `channel-preview-tiles.zip`, `channel-preview-coords.json`
- `stream-logo-tiles.zip`, `stream-logo-coords.json`
- `artwork-manifest.json` - carries `sha256` and a build stamp; this is the invalidation handle.

Revisioned names (`channel-preview-atlas-vN.webp`, `stream-logo-atlas-vN.webp` and their coords) are
**frozen artifacts**: they are never deleted, and they are also never refreshed again. Which revision is
current is a fact about today, not a contract - read it from `delivery/stream-catalog/README.md`, never
from a constant. Pinning one in code means staying on a
payload that has stopped being rebuilt while believing it is current - and the pin cannot lift itself.

**Consumer change.** Move the compiled-in revision constants to the stable names and read
`artwork-manifest.json`. That removes the reason to pin at all, and it removes the standing request to
never delete a given revision - which we will honour regardless, but which should stop being
load-bearing. Item G is the concrete cost of not doing this: a pinned revision that stops being rebuilt
is invisible, while a *rebuilt* sheet under the same name changes shape, and only one of those two is
survivable by a consumer that hardcodes geometry.

---

## G. Derive sheet geometry from the image - never assume a row count **[CONTRACT]** **[NEW, BREAKING]**

**This is the one item that will break a consumer silently if it is not read.**

The preview sheet used to be bounded by a self-imposed `8192 x 8192` budget, which capped it at 60 rows
= 2 040 tiles. That ceiling was the only reason **877 of 2 917 video channels had no preview at all**,
and the overflow was dropped with a warning while the run still reported success. It is gone (S1831).

**Rule.** Width is fixed by the geometry - `240 x 135` tiles, `34` columns, so `8160` px always. **The
height follows the tile count and is not fixed.** Derive the row count from the image; never hardcode it.

- Published 2026-08-20, `channel-preview-atlas-v3.webp`: `8160 x 10935`, **2 723 tiles in 81 rows**,
  15 630 234 B. The build before it was 84 rows. The row count now moves with the bank on every rebuild -
  that is the point of the change, and the reason it cannot be cached in code.
- A consumer holding a hardcoded 60, or assuming a square sheet, slices the wrong rectangle for every
  tile past the first screenful. The result is plausible pictures on the wrong channels - the same
  failure shape as item A, and just as invisible.
- Two ceilings bound the sheet, and the packer **fails rather than truncating** when either is hit: a
  side may not exceed `16383` px (the WebP dimension limit, so 121 rows = 4 114 tiles), and the encoded
  file may not exceed 48 MiB - the limit you declared. The current build sits at a third of that.
- A video channel now lacks a tile for one reason only: it did not answer during the capture pass -
  40 of 2 763 in this build. Capacity is no longer a reason.
- Index maths is unchanged: `col = index % 34`, `row = index / 34`, rect
  `(col * 240, row * 135, +240, +135)`.

**The logo sheet: the same rule now applies to it too, as of 2026-08-20 (S1841).** Its grid is `136 x 136`
**square** tiles, `59` columns, `col = index % 59`. **Its height follows its tile count and is not fixed** -
exactly like the preview sheet. Derive the row count from the image on both sheets; hardcode neither.

- The sheet published on 2026-08-20, before the fix, was `8024 x 8160`: 3 540 tiles in 60 rows,
  14 295 606 B, covering 3 875 channels. That was the self-imposed ceiling, not the format's.
- It was silently dropping real artwork: the same build had **4 148 logos ready and dropped the last 608**,
  reaching 1 593 channel addresses, with a warning while the run reported success. That is fixed: the
  packer now **fails rather than truncating**, and its refusal names how many stations would go without.
- Rebuilt on the same cache after the fix: **4 148 tiles in 71 rows, `8024 x 9656`, 16 779 262 B, covering
  5 468 channels**, with 2 932 tiles of headroom left before the format ceiling. Expect the row count to
  move with the bank on every rebuild from now on.
- Two ceilings bound this sheet as well, and neither truncates: a side may not exceed `16383` px (the WebP
  dimension limit, so 120 rows = 7 080 tiles at a 136 px tile), and the encoded file may not exceed 48 MiB.
  The current build sits at a third of that.
- A station now lacks a tile for one reason only: no artwork of at least 96 px on its longer side was
  cached for it. Capacity is no longer a reason on either sheet.

Two logo-sheet properties that are *not* changing and do matter: the tiles are square because a logo is
fitted whole rather than cropped, so letterbox them into your cell; and the padding around each logo is
transparent on purpose so one sheet serves light and dark themes - decode ARGB, because flattening paints
that padding black.

---

## G2. Prefer the tile pack over the sheet - for cost, not for capacity **[CONTRACT for the read path]**

**Rule.** The tile pack is the primary artwork path; the sheet is the compatibility fallback.

**Why.** Not capacity any more - item G removed that argument. Random access. A sprite sheet is not
randomly addressable, so reaching one tile costs a share of a full-sheet decode: measured 1,48 s for the
61,7 Mpx preview sheet on the build machine, several times that on a phone. A grid asking for one tile
per cell therefore fills one cell at a time, and most decodes land after their cell has been recycled.
That is what moved our own app off the sheet in S1445, and it is the same arithmetic on any platform.

**Container contract.** ZIP with **STORED** (uncompressed) entries. Entry name is the slot index as a
plain decimal string with no extension. The `url -> index` sidecar is unchanged and shared with the
sheet, so a consumer already resolving indices needs no new lookup - only a new reader.

---

## H. Publishing replaces the asset in place, so a 404 window is expected **[CONTRACT]**

Asset replacement is delete-then-upload, so `stream-catalog.zip` returns 404 for a short window on every
publish.

**Rule.** On a 404, a short read or a truncated ZIP, keep the previous bank and retry later. A failed
fetch is never an empty bank, and must never trigger the absence handling in item D.

---

## I. What the producer now guarantees (new since 2026-08-20)

These are the producer-side changes behind the rules above. A consumer does not implement them, but
should know what the bank it receives now means.

- **Liveness is decoded media, not a status code and not a byte count.** A row is `alive` only when a
  decoder reports an audio or video stream. The previous criterion accepted any non-empty body, which an
  HTML "stream offline" page satisfies.
- **"Could not measure" never removes a row.** The `unknown` verdict is refused as a prune input, at
  entry and again at the prune. It was never a claim about a channel, and it does not reproduce: two
  identical runs six minutes apart over the same 19 534 rows disagreed by 95 rows.
- **One provider cannot be quietly emptied.** Probe input is now interleaved across providers, which cut
  the longest single-provider block in the request order from 1 964 rows to 2, and a run that would
  remove at least 35 % and at least 50 rows of one provider refuses to write and names it. Replayed
  against the 2026-08-19 removal set, that check flags exactly one provider - the one that was wrongly
  emptied - and nothing else.
- **Evidence survives the next run.** Per-row verdicts are written to a per-run file as well as the
  fixed path, which the following run used to overwrite.

---

## K. What is live as of 2026-08-20 11:44

Sizes and hashes are informational; `artwork-manifest.json` is the authority and is the thing to poll.

- `stream-catalog.zip` - 6 966 733 B, republished 11:52 to clear 9 stale `geo` flags (item E). Inside:
  `streams.csv` (entry 0, **18 908 rows**, 19 columns, no non-empty `access`) and
  `favicon-atlas.png` (6 468 456 B, `512 x 12704` = 6 352 slots, 6 350 filled, max index 6 349).
- Stable artwork names, all rebuilt together: `channel-preview-tiles.zip` (14 605 058 B),
  `channel-preview-coords.json` (201 259 B), `stream-logo-tiles.zip` (10 005 648 B),
  `stream-logo-coords.json` (227 092 B), `artwork-manifest.json` (1 114 B).
- Revisioned sheets, for consumers still reading sheets: `channel-preview-atlas-v3.webp` /
  `-coords-v3.json`, `stream-logo-atlas-v3.webp` / `-coords-v3.json`.
- Frozen and untouched, as promised: every `-v1` and `-v2` pair, still dated 2026-07-25 through
  2026-08-07.

The bank grew by 1 280 channels against the 2026-08-19 publish, and every row in it delivered a
decodable audio or video stream when probed. That includes 2 035 rows of the provider the previous run
had emptied.

---

## J. Verification handshake

After each publish we send one line saying it is up. The consumer then runs its own harness against the
live asset and checks: `streams.csv` is entry 0, the parsed row count, the atlas byte size, the maximum
`favicon_index`, and one real tile cut to a file and looked at by eye.

That last step is not ceremony. Every failure mode in item A produces a correct-looking file with the
wrong picture inside it, and only a human eye on a cut tile separates them.

---

## Ticket index for this file

- S1827 - build-time atlas byte budget; the ceiling as one named shared constant (items A, B)
- S1828 - the external-consumer contract and its registry (this file's home)
- S1830 - liveness by decoded media, provider-spread probe input, `unknown` never prunes, per-run
  evidence (items D, I)
- S1832 - channel identity survives removal and return, on the Android side (item D)
- S1445 - the app moved from sheets to tile packs (item G2)
- S1831 - the preview sheet ceiling removed; height now follows the tile count (item G)
- S1841 / S1843 - the logo sheet ceiling removed; its height now follows the tile count too, and the packer refuses rather than truncating; closed 2026-08-20 (item G)
- S1483 - `artwork-manifest.json` and stable artwork names (item F)
- S0925 - publish refuses a favicon-indexed CSV with no atlas (item A)
