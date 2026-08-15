---
name: discount-must-shrink-not-subtract
description: In a ratchet gate that counts |add - remove|, a whitelist ("discount") must reduce the computed imbalance, never the add count - subtracting first manufactures a violation on correctly paired code.
metadata:
  type: feedback
---

When a counting gate scores a file as `|adds - removes|` and then wants to excuse a known-benign form,
subtract the excuse from the **imbalance**, not from the **add count** - and only when adds exceed
removes, floored at zero.

```
raw = add - remove
imbalance = (raw <= 0) ? |raw| : max(0, raw - discount)
```

**Why:** the naive shape `|(add - discount) - remove|` inverts on a file that *does* pair its call: the
add falls to zero, the remove is still counted, and correctly written code reports a violation of one.
Measured 2026-08-14 in S1559 on `assert-listener-symmetry.ps1` - the phantom landed on
`LauncherStatusStripManager.kt`, the very file S1501 had just fixed, so the gate would have punished
the repair. Both discounts that predated the ticket (`registerReceiver(null, ..)`,
`onBackPressedDispatcher.addCallback`) were written the naive way and only escaped because neither had
a paired site in the tree at the time.

**How to apply:** whenever adding a whitelist/exception to any `assert-*.ps1` that ratchets a count,
route every category through one helper implementing the formula above, and add a regression case for
the *paired* form of the excused shape - not just the unpaired one. The unpaired case passes under both
formulas, so it proves nothing; the paired case is the only one that distinguishes them.

Related: [[documented-invariant-is-a-claim]] - the gate's own header asserted a scope it did not have.
