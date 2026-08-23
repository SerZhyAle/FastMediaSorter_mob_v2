# 02 - URL normalization collisions on the live bank

Research artifact for strategic §6 item 2. Re-measurement, 2026-08-20.

**Source bank:** `delivery/stream-catalog/streams.csv`, 17 628 data rows (published 2026-08-19).
**Method:** each rule applied in isolation, then the rule set already shipped, then that set plus the
scheme fold. Grouping counts a "collision group" as two or more bank rows collapsing to one key.

## Measured result

| Rule | Collision groups | Rows involved |
|------|-----------------:|--------------:|
| raw address, byte-exact | 0 | 0 |
| lowercase scheme only | 0 | 0 |
| lowercase host only | 0 | 0 |
| drop trailing slash only | 0 | 0 |
| drop default port only | 5 | 10 |
| shipped `StreamUrlNormalizer` (all four above) | 5 | 10 |
| shipped set + `http` <-> `https` fold | 58 | 116 |

The fold contributes 53 of the 58 groups; the remaining 5 come from default-port folding.

## What this corrects in the strategic spec

1. §6.2 records 50 groups over 100 rows. The current bank gives **58 over 116**. The conclusion drawn
   from the number is unchanged; the number itself is not.
2. §6.2 attributes every collision to the scheme rule. **Five are not**: they come from folding the
   default port (`host:80` against `host`), which no §6.2 rule names.
3. §6.2 states the scheme rule as one of three. Lowercasing the scheme collapses **nothing** in this
   bank - `http` and `https` differ after lowercasing. The rule that collapses 53 groups is folding
   `http` and `https` into one protocol token, which is a different and much stronger rule.
4. ADR-2 limits normalization to three rules "and nothing more". The shipped `StreamUrlNormalizer`
   (S1511, `data/util/StreamUrlNormalizer.kt`) already applies a fourth - default-port folding - and it
   is the only one of the four with any measurable effect on this bank. ADR-2 as written forbids a rule
   that is already in production.

## Nature of the collisions

Sampled groups are the same broadcaster entered twice under different names, not distinct channels:

- `radios.rtbf.be/classic21-128.mp3` - "Classic21 (RTBF)" / "RTBF - Classic 21"
- `icecast.omroep.nl/radio4-bb-mp3` - "NPO Klassiek (Radio 4)" / "NPO Klassiek"
- `dispatcher.rndfnk.com/rbb/fritz/live/mp3/mid` - "Fritz (RBB)" / "Radio Fritz"
- `radionz-ice.streamguys.com/concert.mp3` - "RNZ Concert" / "Radio New Zealand - Concert" (port pair)

## Consequence for the plan

Folding `http` and `https` collapses 116 bank rows onto 58 keys, so the catalog loses 58 rows on the
first import after the change. No channel disappears: each of the 58 remains present exactly once. The
loss is of duplicate rows, not of channels, and user state attached to either member of a pair must land
on the surviving row rather than be dropped - which is the merge behaviour this ticket has to specify.
