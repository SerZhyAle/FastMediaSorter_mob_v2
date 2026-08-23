---
name: all-features-wear-record-needs-companion-gate
description: A watch capability in ALL_FEATURES is either phone-bridge (gate SUPPORT_WEAR_COMPANION, flavors = that row) or watch-standalone (no gate, full six); anything else trips the S1934 ratchet
metadata:
  type: project
---

A `docs/ALL_FEATURES.jsonl` record for a **watch** capability has exactly two legal shapes, and the
choice between them is a question about the phone, not about the watch:

- **The phone participates** (it pushes, stores, answers or originates something) -> add with
  `-Gate "SUPPORT_WEAR_COMPANION"` and `-Flavors` equal to that flag's row in `docs/FLAVOR_MATRIX.md`.
- **The capability lives entirely in the `wear` module** -> no gate, and the **full six** flavors. The
  wear module declares no `productFlavors`, the watch app is its own installation, and no phone build
  excludes it, so any narrower set asserts an exclusion the build does not have.

**Never hardcode the row.** As of 2026-08-23 (S1951) it is `standard, noLegal` - `legacy` was removed,
because that flavor carries `applicationIdSuffix = ".legacy"` and Play Services routes the Wear Data
Layer by the phone's applicationId, so a legacy phone could never reach the watch app. Read the row out
of `docs/FLAVOR_MATRIX.md` at the moment you write the record; the matrix is generated from
`productFlavors` and a flavor added to or removed from the flag changes the answer under you.

**Why:** `validate.ps1` counts a record as "unexplained" unless its flavor set equals the full six or
exactly matches some flag's row. A set that is neither raises the S1934 ratchet past
`scripts/all_features/unexplained-flavors-baseline.txt` and fails `all-features-gate` in
`post-change.ps1`. The subtler failure is the opposite one: when S1951 changed the wear row, 30 records
that had matched it - 20 genuinely about the watch, 10 not about the watch at all - stopped matching in
one step. A flavor set that is right only by coincidence looks identical to one that is right on purpose
until the row moves.

**How to apply:** whenever `/spec-*` records a watch capability. Confirm with
`pwsh -NoProfile -File scripts/all_features/validate.ps1` and read its **exit code**, not its last line -
the ratchet verdict prints at the TOP of the output and a `| tail` will miss it entirely.
