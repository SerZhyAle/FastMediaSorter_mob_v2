# Cross-project contracts: what binds us, and where it lives

Every contract this product exposes to another program - mine or an external developer's - lives in
the shared contracts catalog, never in this repository. The pointer set is `docs/contracts/` - one
`<ID>.md` per contract, indexed by `docs/contracts/README.md`, the layout `REPO-LAYOUT` rule 2 fixes and
the canon's compliance reader addresses. This file is the one-page summary of that set and holds no
contract text of its own; restating any of it here would create the second copy the catalog exists
to prevent. A contract added here without its pointer file is invisible to every tool.

The catalog is organized by **function**, not by product: one folder answers "how must a stream bank
be published and consumed", and every product that touches that function is bound by the same page.
`CLAUDE.md` is the one tracked file that says where the catalog is on this machine. A source comment
or a document cites a contract by **id and section** (`STREAM-BANK rule 4`, `CONFIG_FORMAT.md
section 8`), never by path.

Each row below is: the id, the version this product is built against, the function folder that is its
home, our role, and the one thing we owe it. Verification dates and every dated deviation live in the
catalog's `_meta/REGISTRY.md`, which is the live map - not here, because a date copied into two files
goes stale in one of them.

## Produced by this product

| Contract | Version | Home | Role | What we owe it |
| --- | --- | --- | --- | --- |
| `STREAM-BANK` | 2.1 | `stream-catalog/` | producer and consumer | The published ZIP keeps `streams.csv` as entry 0 and columns are only ever appended. A pinned asset name is never displaced without the consumer registry recording it - gated by `scripts/quality/assert-stream-asset-revisions.ps1`. `artwork-manifest.json` is published by the run that uploads the payload it describes, never separately. |
| `LIVE-BROADCAST` | 0.12 (draft) | `live-broadcast/` | producer | Never publish a loopback address; no in-stream metadata; one descriptor across link, file and barcode. A stream kind moves from `[PROPOSED]` to `[CONTRACT]` only through the handshake in section 6, with the consumer verifying against a live broadcast. |
| `CHECK-VERDICT` | 0.10 (draft) | `automated-checks/` | owner and reference implementation | Four exit codes with fixed meanings, and `2` (could not verify) never collapsed into a pass; `4` is reserved as "queued, not looked at" for a check that waits on a lock domain (rule 1), and the closure facade converts it to `2`. A non-zero exit carries a printed reason - gated by `scripts/quality/assert-exit-contract.ps1`, which also holds a documented code to being reachable. One machine-readable verdict line, advisories named rather than counted, and the advisory class earned only by a caller that declared its changed set. |
| `CHECK-BASELINE` | 0.9 (draft) | `automated-checks/` | owner | A baseline may fall and never rise, and a class exposed to a wholesale re-freeze is judged by identifier set rather than by count - gated by `scripts/quality/assert-detekt-baseline-absorption.ps1`; every baseline declares its shape and write path in `scripts/quality/baseline-inventory.jsonl`, held by `scripts/quality/assert-baseline-inventory.ps1` (S3438). Accepting new debt is an explicit act with a reason and a journal row, and the accepted set stays reviewable in a diff. |
| `CHECK-PLACEMENT` | 0.10 (draft) | `automated-checks/` | owner | Every check declares its runner class in `scripts/quality/gate-placement.jsonl`, checked both ways by `assert-gate-placement.ps1`. Membership in the operator-typed batch never satisfies a per-change class, and a relocation is a registry row with a reason and a date, never a paragraph in a comment. A seeded record names an owner ticket still open; the check refuses a seeded record whose owner closed, with the pre-rule set grandfathered shrink-only in `scripts/quality/gate-placement-seeded-baseline.txt`. |
| `BUILD-EVIDENCE` | 0.9 (draft) | `automated-checks/` | owner | A check names the subject it checked; a produced artifact carries its own build's version (`assert-artifact-version-fresh.ps1`); no test is re-run to green (`assert-no-test-retry.ps1`, cure is a quarantine row in `docs/test-flaky-quarantine.jsonl`); a run time stated in prose is judged against the telemetry median (`assert-gate-timing-claims.ps1`). |
| `WAVE-PARTICLES` | 0.10 (draft) | `animated-backdrop/` | owner; phone, launcher, watch and site hero | The "particles and lines" backdrop - `AudioWaveParticleView`, `WaveParticleBackground` and the site's hero canvas - is that contract's algorithm with its section 3 constants and nothing brighter. A change to its speed, geometry or colours is agreed in the catalog before it lands in any surface, and a constant that differs is a dated registry exception, not a comment. `WaveParticlesContractConstantsTest` (app_v2 unit tests) pairs every section 3 constant of both renderers with the reference implementation the site serves. |
| `ICON-SET` | 0.13 (draft) | `iconography/` | owner; phone, launcher, watch, docs and site | One meaning, one glyph, one name in every surface. A control that stands for a meaning in the vocabulary draws that meaning's glyph and carries its canonical EN/RU/UK name - a label may add the object ("Previous page") but never swap in another meaning's word. A new meaning is added to the vocabulary before the code that shows it ships. The glyphs, the decorated looks, `palette.json`, `CATALOG.md` and `gallery.html` are regenerated from this repository by `scripts/docs/export-icon-contract.ps1`, never edited by hand. |
| `ICON-RENDER` | 0.11 (draft) | `iconography/` | owner | A glyph takes its colour from the theme role its record names - never a baked white or black - and stays legible on light, dark and all six accent themes. Toggles show their state, live transport shows its action, direction glyphs mirror in RTL and media transport never does. Every edition's launcher icon is adaptive with a monochrome layer. Since 0.10 the style is measured (24 grid, one paint, stroke width 2, one unit of margin, box or mass centred, weight 1.3), one glyph has three derived looks - mono, colour, decorated - chosen by surface, hues come from a shared palette (`palette.json`), and icon sizes sit on the tiers 16/20/24/32/40/48; `scripts/quality/assert-icon-style.ps1` holds this product to it. The contrast of every colour role on all eight themes is measured by the exporter's contrast report, and a glyph-only control's accessible name must contain its meaning's canonical name - the `accessible-name` dimension of `scripts/quality/assert-icon-contract.ps1`. |
| `ICON-EXTERNAL` | 0.9 (draft) | `iconography/` | owner | Third-party marks are the owner's asset, never redrawn look-alikes; another app is shown by its own system icon; every downloaded picture (favicon, album art, thumbnail, contact photo) falls back to the vocabulary glyph of what it stands for, never to a blank or an error. |
| `OCR-ACCURACY` | 1.0 | `ocr-overlay/` | owner of the record | Our side of the three-sided exchange. Every constant that cites it carries `derived here` with its report or `inherited` with the ticket that owns deriving it. |

## Consumed by this product

| Contract | Version | Home | Role | What we owe it |
| --- | --- | --- | --- | --- |
| `FMSCFG` | 2.1 (reads schemas 1-2) | `config-interchange/` | producer and consumer | Refuse a `schemaVersion` above our own with an "update the app" message and no partial import. Ignore an unknown `accessPaths[].kind` rather than fail. Keep the canonical vectors byte-identical to the catalog's - a change to those bytes is a contract change, not a test update. Keep the writer's own pinned bytes and their itemized difference from those vectors, and never log the payload - it carries a password. |
| `SHARE-SESSION` | 1.0 | `remote-folder-access/` | consumer (client half) | Treat a server's idle close as ordinary: one bounded transparent reconnect, never a loop. Keep the four failure classes apart - transport, auth, host key, refusal - and re-verify the stored host-key pin on every connection, never accepting a changed key silently. |
| `FDSEC-FORMAT`, `FDSEC-BEHAVIOUR` | 1.0 each (format version 1, suite 1) | `secure-container/` | producer and consumer | Reproduce the published conformance vectors byte for byte - they decide correctness, not the prose. Never delete, move or overwrite an original before its container has been reopened and read back to the sealed digest. Keep the original after packing - the only disposition this product implements - and say so where the pack ends. Keep the three outcome classes distinct and never report one as another. A credential source outside the contract's list is a registry exception until the owner rules, never a silent addition. Never describe an empty credential as protection, never launch an executable recovered from a container, and keep the credential and the sealed true name out of every log. |
| `OCR-OVERLAY` | 1.0 | `ocr-overlay/` | consumer | Recognize in display space, take boxes at line level, sample paper and ink from the image, and never write recognized text into user data as if it had been typed. |
| `INSTALL-TRUST` | 1.0 | `install-trust/` | producer | `docs/INSTALL_TRUST*.md` carries the four sections of rule 1 in order and is linked from every surface that hands out an APK - the downloads trio, the published README trio, the landing pages, the sideload pages and the root README (rule 7), held at release scope by `scripts/quality/assert-install-trust.ps1` against `scripts/quality/install-trust.psd1`. It never tells a user to weaken a protection (rule 4), and its "what the app never does" section says what `docs/PRIVACY_POLICY.md` and the permission list say (rule 6). When the builds become recognized the page is updated, not deleted (rule 8). |
| `PAGE-CONTENT`, `PAGE-STYLE`, `SITE-FAMILY-MAP` | 1.1, 1.0, 1.1 | `product-web-pages/` | consumer (the product site) | The landing and sideload pages keep the shared section order, the kit's header, language switcher, theme toggle and monochrome icons, and a footer that lists the whole product family as the map states it. The switcher label, the theme pre-paint and the reference kit or its dated exception are gated by `scripts/quality/assert-page-style.ps1`; the absence of emoji and the above-the-fold order by `scripts/quality/assert-page-content.ps1`. The footer grid renders from one source, `scripts/site/family-footer.json`, and `scripts/quality/assert-site-family-map.ps1` holds it to the map and the one contact. |
| `REPO-STAMP`, `HARNESS-PROFILE`, `REPO-LAYOUT`, `RULE-DELIVERY` | 0.9 each | `rule-adoption/` | adopter | `.sza-canon.json` and `.sza-profile.json` keep their required keys and are read by name; contract pointers keep the layout tools address them by. |

## What this means for a change here

- A schema, asset name, atlas ceiling or descriptor field that a consumer mirrors is changed in the
  catalog first, and only then in this repository's code. A pull request that changes behaviour at one
  of these boundaries is not complete until the catalog commit exists.
- Finding the contract wrong creates an obligation, not a licence: write the amendment in the catalog
  with the evidence, or record a dated exception with an `until`. There is no third option, and that
  holds even when the defect is in another product's contract.
- `scripts/quality/assert-stream-asset-revisions.ps1` reads the consumer registry from the catalog
  through `$env:FMS_CONTRACTS_ROOT`. Without it the gate reports "could not verify" rather than
  passing - a clone with no catalog cannot check this, and should say so rather than look green.
- `scripts/quality/assert-contract-pointers.ps1` holds the pointer files, their index, the tables above
  and the catalog registry to one set of ids and versions, so a version bumped in one place and not the
  others is a red gate, not a stale row. It reads the registry through the same variable and answers
  "could not verify" without it once the in-repo half agrees.
- `scripts/quality/assert-fdsec-vectors-provenance.ps1` holds the vendored FDSEC-FORMAT vectors
  (`app_v2/src/test/resources/fdsec/`, recorded in their `PROVENANCE.txt`) to the catalog copy through
  the same variable, with the same "could not verify" answer. `FdSecVectorsTest` holds the vendored
  bytes to the record, so a catalog regeneration surfaces as a red gate instead of a green test on
  stale bytes.
