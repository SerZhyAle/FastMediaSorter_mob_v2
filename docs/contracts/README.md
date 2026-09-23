# Contract pointers

Every file in this folder is a **pointer**, never a copy. The contracts themselves live in the shared
contracts catalog, whose location is named in exactly one file in this repository -
[`CLAUDE.md`](../../CLAUDE.md), the cross-project contracts bullet. Everything else, here and in the
source, cites a contract by its **id** and a rule or section number: `STREAM-BANK rule 4`,
`OCR-OVERLAY rule 12`, `FDSEC-FORMAT.md section 8`. Never a path, because whoever clones this repository
does not have the drive the catalog sits on.

A pointer that grows a second page has become a copy. If this repository ever disagrees with a contract,
that difference is an amendment to write in the catalog or a dated exception to record in its registry -
not a local edit and not a local copy kept "in sync". Verification dates and exceptions live only in the
catalog's `_meta/REGISTRY.md`; none are repeated here.

The one-page summary of the same set, with the one obligation each contract puts on this product, is
[`../CROSS_PROJECT_CONTRACTS.md`](../CROSS_PROJECT_CONTRACTS.md). The two must name the same ids and
versions; a version bump in the catalog updates both.

| Pointer | Id | This product's role |
| --- | --- | --- |
| [`STREAM-BANK.md`](STREAM-BANK.md) | `STREAM-BANK` | owner; producer (publish script) and consumer (phone and watch importers) |
| [`LIVE-BROADCAST.md`](LIVE-BROADCAST.md) | `LIVE-BROADCAST` | owner; producer (phone and watch audio broadcast), and a consumer of its own descriptor |
| [`FMSCFG.md`](FMSCFG.md) | `FMSCFG` | consumer (the `.fmscfg` importer); also writes `.fmscfg` files and `FMSCFG1:` QR payloads |
| [`SHARE-SESSION.md`](SHARE-SESSION.md) | `SHARE-SESSION` | client - the SFTP connection to a shared folder |
| [`OCR-OVERLAY.md`](OCR-OVERLAY.md) | `OCR-OVERLAY` | consumer - the player's OCR + translation overlay |
| [`OCR-ACCURACY.md`](OCR-ACCURACY.md) | `OCR-ACCURACY` | owner of the record |
| [`FDSEC.md`](FDSEC.md) | `FDSEC-FORMAT`, `FDSEC-BEHAVIOUR` | a port: writer and reader of `.fd-sec` containers, phone and watch |
| [`INSTALL-TRUST.md`](INSTALL-TRUST.md) | `INSTALL-TRUST` | producer - the sideload trust page |
| [`WAVE-PARTICLES.md`](WAVE-PARTICLES.md) | `WAVE-PARTICLES` | owner; producer on phone, launcher, watch and the website |
| [`CHECK-VERDICT.md`](CHECK-VERDICT.md) | `CHECK-VERDICT` | owner and reference implementation |
| [`CHECK-BASELINE.md`](CHECK-BASELINE.md) | `CHECK-BASELINE` | owner |
| [`CHECK-PLACEMENT.md`](CHECK-PLACEMENT.md) | `CHECK-PLACEMENT` | owner |
| [`BUILD-EVIDENCE.md`](BUILD-EVIDENCE.md) | `BUILD-EVIDENCE` | owner |
