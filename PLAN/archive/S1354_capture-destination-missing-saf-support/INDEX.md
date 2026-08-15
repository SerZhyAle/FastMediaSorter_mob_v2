# Tactical Plan: S1354 - capture-destination-missing-saf-support

**Strategic spec:** [`../S1354_capture-destination-missing-saf-support.md`](../S1354_capture-destination-missing-saf-support.md)
**Feature:** SAF support for configured capture destinations
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 3 / 4 done
**Last updated:** 2026-08-03

## Objective

Route configured local capture destinations through a shared writer supporting filesystem folders and persisted SAF trees without changing settings or network routing.

## Pre-Implementation Blockers

- [x] Confirm camera, microphone and screen recording currently convert selected `content://` trees into `File` paths.
- [x] Confirm `LocalCopyFileOperation` and `SafHelper` provide the project SAF tree stream pattern.

## Phase Overview

| Phase | File | Depends on | Steps | Status |
|------:|------|------------|------:|--------|
| 01 | `PHASE_01__shared-local-writer.md` | none | 2 | ✅ Done |
| 02 | `PHASE_02__camera-and-microphone.md` | 01 | 3 | ✅ Done |
| 03 | `PHASE_03__screen-recording.md` | 01 | 2 | ✅ Done |
| 04 | `PHASE_04__tests-and-cleanup.md` | 02, 03 | 2 | ⬜ Not started |

## Completion Gate

- [ ] Every phase is ✅ Done.
- [ ] Standard debug build passes.
- [ ] Unit tests cover filesystem and SAF-destination selection.
- [ ] Device verification saves photo, video, microphone and screen recording into a persisted writable SAF tree.
- [ ] Device verification confirms an unavailable tree falls back without losing the capture.

## Change Log

- 2026-08-03 - Tactical plan created from traced capture save paths.
