# Tactical plan: S1483 - Обложки доставлять как каталог, без хешей в сборке

**Strategic spec:** [`../S1483_artwork-delivery-without-build-pins.md`](../S1483_artwork-delivery-without-build-pins.md)
**Status:** ✅ Done
**Phases:** 4

---

## Phase order

| Phase | Title | Status | Depends on |
| --- | --- | --- | --- |
| [01](PHASE_01__publisher-stable-names-and-manifest.md) | Publisher: stable asset names + artwork manifest | ✅ | none - foundation |
| [02](PHASE_02__descriptors-unpinned.md) | Descriptors: blank pin, no revision in the name | ✅ | 01 |
| [03](PHASE_03__staleness-from-manifest.md) | Staleness decided by the manifest, not by the build | ✅ | 01, 02 |
| [04](PHASE_04__structural-validation-and-size.md) | Structural validation on install + payload size in the offer | ✅ | 03 |

---

## Invariants held across every phase

- Native-code sets (`OCR_ENGINES`, `FFMPEG_DTS`) keep their SHA-256 pins and their install-source gate.
  Nothing in this ticket may loosen a payload that is loaded as executable code.
- A missing or unparseable manifest means "no update available", never an error surfaced to the user.
- The published sprite sheets keep their `-vN` revisions for third-party consumers; only the payloads
  the app itself fetches move to stable names.

---

## Completion Gate

- [ ] Every phase ✅ Done.
- [ ] `.\a.ps1 fk` passes.
- [ ] Unit tests for the manifest comparison and the structural check pass.
- [ ] A rebuilt artwork payload published under the stable name is offered by an app build that was
      compiled BEFORE that rebuild - the whole point of the ticket.
