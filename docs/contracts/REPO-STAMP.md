<sub class="doc-stamp">26.11.24 21:52</sub>

# Pointer - `REPO-STAMP`

| | |
| --- | --- |
| **Id** | `REPO-STAMP` |
| **Version** | 0.14, draft. Owner: the canon (sza-unified-rules) |
| **Home** | `rule-adoption/README.md` section 2 in the shared contracts catalog |
| **Role here** | adopter - declares itself through `.sza-canon.json` |

## What this repository must do to stay conformant

- Keep `.sza-canon.json` at the root, valid JSON, with every required key.
- Give every exemption a check id and a reason that says what closes it; write no `until` (0.14 item C: it is history
  and no reader reads it).
- Never write the version pair or the core digest by hand; the adoption run writes them.
- A reconciliation writes `canon.reconciledOn` together with `canon.version` and `canon.coreDigest`;
  `canon.adoptedOn` stays the date of first adoption (rule 8).
- 0.14 adds the optional `canon.docDigests` map (absent means the changed documents are unknown), written by the
  adoption run and never by hand; an exemption's `until` is history and no reader reads it as an expiry.

## Where it lives here

- `.sza-canon.json` at the repository root.
- Re-stamping: `scripts/utils/restamp-canon.ps1`, which takes version and digest from the canon's reader.
