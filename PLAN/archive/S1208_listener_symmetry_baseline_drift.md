# S1208 - listener-symmetry baseline is one behind the shipped tree

**Status:** Archived

## 0. Raw capture

Found during the `/skill-release` pre-flight of 2026-07-27. Out of scope for that release; parked per CLAUDE.md 3.1.

`.\a.ps1 fg` fails on one gate:

```
listener-symmetry: baseline 133 | actual 134 | delta +1
FAIL: Listener/observer/receiver/callback symmetry grew above baseline. Balance all registrations.
```

This is **not** a regression from the release being prepared. Evidence:

- The commit under release (`4e9015f8`) introduces zero listener changes: `git show 4e9015f8 -- "*.kt" | grep '^+' | grep -E '\b(add|remove|register|unregister)[A-Za-z0-9_]*(Listener|Callback|Observer)\b'` returns nothing, in both added and removed lines.
- The same check over `d9e150d2`, `cd68b4b7`, `c940adfb` and `41b6964d` is also empty.
- Running the gate's `-List` mode inside a throwaway worktree checked out at the **previous release tag** `release/v2.60.7260.335` sums to **134** - identical to the current tree.

So the already-shipped release carried 134 while `scripts/quality/listener-symmetry-baseline.txt` still says `133`. The baseline was last written on 2026-06-29 (`02907859`); the drift happened somewhere between then and the previous release and went unnoticed because `post-change.ps1 -ScopeToFile` downgrades project-wide ratchets to advisory on a dirty tree - which this repo always is.

## 1. Why this needs its own ticket

- A ratchet that is permanently red teaches everyone to ignore it. The next genuine +1 will look identical to this one.
- Silently bumping the baseline to 134 would hide whichever registration actually lost its removal. The point of the gate is that the number only moves with a reason.
- Finding the responsible file needs a bisect over the window 2026-06-29 .. `release/v2.60.7260.335`, comparing per-file `-List` output rather than the total.

## 2. What to do

1. Bisect the window and identify the file whose imbalance grew.
2. Decide whether that registration genuinely leaks (missing removal on the symmetric lifecycle edge) or is a false positive of the regex (e.g. a builder-style `addXListener` on a short-lived object).
3. Real leak -> add the removal, baseline stays 133. False positive -> bump the baseline to 134 **with** a comment naming the file and why it is exempt.

## 2a. Outcome (2026-08-14) - both premises measured dead, ticket archived

Re-measured before working it, rather than assumed. Neither half of the ticket describes anything that is still true.

**The drift is gone, and it was paid down rather than hidden.** `scripts/quality/assert-listener-symmetry.ps1` reports `baseline 115 | actual 115 | delta 0` and exits 0; `scripts/quality/listener-symmetry-baseline.txt` reads `115`. The ticket was filed against `133 | 134`. A ratchet that only ever decreases cannot absorb a `+1` by moving down 18, so the concern in §1 - that bumping the baseline would hide whichever registration lost its removal - did not happen. The eighteen imbalances between 133 and 115 were removed, not excused.

**The blind spot that let the drift go unnoticed is closed.** §0 blamed `post-change.ps1 -ScopeToFile` for downgrading project-wide ratchets to advisory on the always-dirty tree. That is no longer how the facade behaves: `scripts/post-change.ps1` lines 776-778 record the S0850 change, and under `-ScopeToFile` this gate now judges per-file imbalance growth against HEAD and **stays FATAL** - an edit that degrades symmetry fails its own closure, while unrelated in-flight imbalance elsewhere does not. The advisory carve-out is now limited to the repo-wide re-render gates.

What remains of §2 - bisecting 2026-06-29 .. `release/v2.60.7260.335` to name the file behind a `+1` that no longer exists, against a baseline that has since moved twice - has no consequence for the current tree. Archived rather than carried: there is no question left for a carrier to own.

## 3. Related

- The gate itself: `scripts/quality/assert-listener-symmetry.ps1`, baseline `scripts/quality/listener-symmetry-baseline.txt`.
- Same class of problem as the detekt-baseline drift already recorded in project memory: a ratchet whose reference drifts silently on an always-dirty tree.
