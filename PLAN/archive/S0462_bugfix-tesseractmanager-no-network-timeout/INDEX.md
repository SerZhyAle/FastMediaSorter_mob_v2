# Tactical Plan: S0462 - bugfix-tesseractmanager-no-network-timeout

**Strategic spec:** [`../S0462_bugfix-tesseractmanager-no-network-timeout.md`](../S0462_bugfix-tesseractmanager-no-network-timeout.md)
**Feature:** TesseractManager: connect/read timeout for .traineddata download
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 40
**Status:** Done
**Phases:** 1 / 1 done
**Last updated:** 2026-06-16

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | add-download-timeout | - | ✅ Done | 1/1 | [PHASE_01__add-download-timeout.md](PHASE_01__add-download-timeout.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] `TesseractModelManager.downloadModel()` pattern confirmed: `HttpURLConnection` + `connectTimeout = 15000` / `readTimeout = 15000` / `connection.disconnect()` in finally.
- [x] `checkAndDownloadData()` confirmed to use bare `URL.openStream()` with no timeout (line 125 of TesseractManager.kt).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `dev/CHANGELOG.md` has an entry for the modified file.
- [ ] `/spec-check S0462` returns `Verified`.

---

## Blockers Log

None.

## Change Log

| Date | Author | Note |
|------|--------|------|
| 2026-06-16 | spec-dev | Initial tactical spec created inline |
