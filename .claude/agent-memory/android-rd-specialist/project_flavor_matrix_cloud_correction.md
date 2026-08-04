---
name: flavor-matrix-cloud-correction
description: never answer a flavor-capability question from memory - docs/FLAVOR_MATRIX.md is generated from productFlavors and gated; binary file types are the one thing not capability-gated
type: project
---

The canonical grid is **`docs/FLAVOR_MATRIX.md`**, generated from the `productFlavors` block by `scripts/docs/generate-flavor-matrix.ps1` and enforced against every documentation table by `scripts/quality/assert-flavor-matrix-docs.ps1` (S1392, 2026-08-04). Read it; do not restate it here or answer from a prompt summary.

The one fact that grid does NOT show, because it is a code path rather than a flag: **binary files (archives / disk images / executables) are not capability-gated at all.** `GetMediaFilesUseCase.applyFlavorMediaTypeRestrictions` filters every `MediaType` by `MediaCapabilities` except the four `BINARY_*` types, which sit on an unconditional `-> true`. Binary-file features therefore ship in all six flavors regardless of the `SUPPORT_*` set (found 2026-07-16, S1058).

**Why:** two separate audits nearly landed wrong fixes off a memorized matrix - S0557 was about to write "Photos has no cloud" and "Legacy has no cloud" (both have it), and S1392 found four user-facing documents that had followed a persona summary claiming `lite` had no audio and progressive-only streams, the exact inverse of the flags. A capability summary that is not generated goes stale silently; that is what the gate now prevents.

**How to apply:** any flavor-capability claim - docs, spec, code gate, chat answer - reads `docs/FLAVOR_MATRIX.md` first. If the question is about a media type rather than a flag, check the restriction function too, because the `BINARY_*` exemption is invisible in the grid.
