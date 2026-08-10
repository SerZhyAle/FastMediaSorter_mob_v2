---
name: spec-all-queue-driver-stage0-silence
description: run-spec-all-queue.ps1 shows the pre-run status for 15+ min because /spec-all flips it only after Stage 0 research - not a hang
metadata:
  type: project
---

`run-spec-all-queue.ps1` printing `.. Sxxxx running N min (status: Draft)` for 15-40 minutes is normal, not a stall.

**Why:** the driver polls only the catalog, and `/spec-all` makes its first transition (`Draft -> Approved`) at the END of stage S1/F1, after Stage 0 bootstrap, drift-check and research. Observed 2026-08-08 on S1506: 13 of the first 16 minutes were one `android-solution-researcher` subagent. `--print` buffers the whole session, and the driver writes `temp/Sxxxx/spec-all_*.stdout.log` only at exit, so the console is mute by construction.

**How to apply:** before calling a run hung, check liveness instead of the status - the `claude` process CPU time, and the tail of the session transcript under `~/.claude/projects/p--ANDROID-FastMediaSorter-mob-v2/*.jsonl` (find it with `grep -l "spec-all Sxxxx"`, mind that your own session matches too). Worry only if neither the status nor `PLAN/Sxxxx_*.md` moves for ~40 min. Related: [[ticket-busyness-is-a-lease-not-a-status]].
