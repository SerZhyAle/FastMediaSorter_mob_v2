---
name: split-device-acceptance-before-draining
description: Before spending a device pass on a BlockNeedUserTest ticket, split its acceptance note into the half a grep can decide and the half only the device can - the static half is usually unchecked and sometimes already false
metadata:
  type: feedback
---

A `BlockNeedUserTest` acceptance note is written as one list, but its items are rarely all
device-bound. Split it first, decide the static half without the device, and spend the device pass on
what is left. It costs minutes and it changes what the device pass is looking for.

**Why:** measured 2026-08-21 across four tickets in one round, and it paid every time.

- **S1541** claimed "the frozen `LargeClass` entry is removed from the baseline". A grep settled it:
  zero entries, and detekt passes without them. Worth checking precisely because three neighbouring
  tickets that same day showed the opposite - a green gate bought by a baseline entry rather than by
  the fix. The device pass then had only "behaviour unchanged" left to prove.
- **S1549** listed nine screens to rotate. The manifests split them into two groups: four omit
  `orientation` from `configChanges` and are recreated by the system, four intercept rotation and
  must re-apply resources in code. Same list, two different questions - and a failure in one group
  means something different from a failure in the other.
- **S1285** asked whether a settings row exists "in BOTH portrait and landscape". Both layout
  variants carry it, and the settings-doc-sync gate was already green, so only placement order and
  value application needed hardware.
- The reverse also happens: a ticket's static half can be **unfinished** while it sits parked as if
  only the device were missing. Check before booking device time.

**How to apply:**

- Read the status note and mark each clause: grep-decidable, gate-decidable, or device-only.
- Grep-decidable: file existence, both layout variants, a baseline entry's absence, a manifest
  attribute, a probe's presence. Gate-decidable: settings-doc sync, string parity, detekt, the
  source gates.
- Record what you settled in the ticket, so the device pass starts from a shorter list and the next
  reader does not re-derive it.
- Then brief the device operator on the residue only. Its tool budget is small - do not spend it on
  something a grep answers.
