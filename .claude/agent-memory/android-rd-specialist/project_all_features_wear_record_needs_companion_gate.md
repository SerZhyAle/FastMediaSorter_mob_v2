---
name: all-features-wear-record-needs-companion-gate
description: A watch capability recorded in ALL_FEATURES with flavors ["standard"] trips the S1934 ungated-flavors ratchet; name SUPPORT_WEAR_COMPANION instead
metadata:
  type: project
---

A new `docs/ALL_FEATURES.jsonl` record for a **watch** capability must be added with
`-Gate "SUPPORT_WEAR_COMPANION" -Flavors "standard,noLegal,legacy"`, not with the `["standard"]` that
existing wear records use.

**Why:** `validate.ps1` counts a record as "unexplained" unless its flavor set equals the full six or
exactly matches some BuildConfig flag's row in the flavor matrix. `["standard"]` matches neither, so it
raises the S1934 ratchet past `scripts/all_features/unexplained-flavors-baseline.txt` and fails the
`all-features-gate` in `post-change.ps1`. The older wear records sit inside the baseline as
pre-existing debt, so copying one reproduces the debt rather than the convention. The `wear` module
declares no `productFlavors` at all, which is why the honest reach is the row of the flag that decides
whether a build carries the watch companion.

**How to apply:** whenever `/spec-*` records a watch capability. Verify the row is still
`[standard, noLegal, legacy]` in `docs/FLAVOR_MATRIX.md` before reusing that flavor string - the matrix
is generated, and a flavor added to the flag changes the answer. Confirm with
`pwsh -NoProfile -File scripts/all_features/validate.ps1` and read its **exit code**, not its last line.
