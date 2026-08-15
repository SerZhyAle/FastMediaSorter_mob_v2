# Tactical Plan: S0047 — bugfix-sftp-pool-broken-channel

**Strategic spec:** [`../S0047_bugfix-sftp-pool-broken-channel.md`](../S0047_bugfix-sftp-pool-broken-channel.md)
**Feature:** SFTP channel pool — broken channel eviction after I/O failure
**Tier:** 1 — Quick Win
**Priority:** 90
**Status:** Not started
**Phases:** 3 / 3 done
**Last updated:** 2026-05-02

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | pool-eviction-api | — | ✅ Done | 4/4 | [PHASE_01__pool-eviction-api.md](PHASE_01__pool-eviction-api.md) |
| 02 | datasource-health-tracking | 01 | ✅ Done | 4/4 | [PHASE_02__datasource-health-tracking.md](PHASE_02__datasource-health-tracking.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None — all strategic §6 research items are Resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (if user-facing — see strategic §8). Spec §8 declares no FEATURES change; verify.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API of pool changed).
- [ ] `/spec-check S0047` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.
- [ ] Manual on-device reproduction: trigger `Pipe closed` mid-playback (toggle Wi-Fi briefly, force-disconnect on server side, or play through a flaky NAS), confirm next track on same host opens on first try and only one `channel evicted` log line is present per failure.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0047`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-05-02 — Initial tactical plan authored by `/spec-tech`.
