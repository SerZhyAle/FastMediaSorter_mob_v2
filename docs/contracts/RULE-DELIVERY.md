# Pointer - `RULE-DELIVERY`

| | |
| --- | --- |
| **Id** | `RULE-DELIVERY` |
| **Version** | 0.9, draft. Owner: the canon (sza-unified-rules) |
| **Home** | `rule-adoption/README.md` section 5 in the shared contracts catalog |
| **Role here** | adopter - receives the rule set through the `sza` plugin |

## What this repository must do to stay conformant

- Judge staleness from the core digest, never from a commit id.
- Reconcile the changed rule documents when the digest differs; re-adopt fully once the gap is an error.
- Never hand-edit the version pair recorded in the stamp.

## Where it lives here

- `.sza-canon.json` - the adopted version and digest.
- `docs/DEV_OPS.md` "The process harness comes from the canon" - the carrier and the ladder as this
  repository meets them.
