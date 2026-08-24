---
name: lock-turn-lost-between-wait-and-acquire
description: wait-for-lock-turn exiting 0 does not mean you hold the lock - chain the wait and the acquire in one process or a faster session takes your turn
metadata:
  type: feedback
---

`wait-for-lock-turn.ps1` exiting 0 means "your turn has come", not "you hold the lock". Between its exit and
your next tool call, another session can and does take it. Chain the wait and the acquire inside **one**
process, and loop on the race:

```powershell
for ($i = 1; $i -le 6; $i++) {
  pwsh -NoProfile -File scripts/utils/wait-for-lock-turn.ps1 -Name Code -Reason "<why>" | Out-Null
  pwsh -NoProfile -File scripts/utils/enter-code-lock.ps1 -Reason "<why>"
  if ($LASTEXITCODE -eq 0) { break }
}
```

**Why:** measured 2026-08-21 with five sessions queued on `CODE.LOCK`. The waiter reported "your turn (ticket
#4, waited 105s)" and exited 0; the very next `enter-code-lock.ps1` call, one tool-call later, was refused
with exit 4 - the lock was already held by the session behind me, whose reason was a `/skill-fix`. The whole
105 s wait was spent for nothing. Backgrounding the waiter as Rule 23 asks makes this *worse*, because the
notification round trip is the widest possible gap.

**How to apply:** background the waiter only when you genuinely have lock-free work to do meanwhile, as Rule
23 intends - and then re-enter through the chained loop above rather than calling `enter-code-lock.ps1` on
its own. Keep the `-Reason` string byte-identical across the wait and the acquire so your queue ticket is
reused instead of a second one being issued. Then hold the lock for the single edit and release it at once:
five sessions were queued behind me and the head had already waited 19 minutes.

## Never chain an edit behind the lock call with `&&` or `;` (2026-08-22, S1944)

`enter-code-lock.ps1` returns **4** when you are queued, not 0 - but a pipeline like

    pwsh ... enter-code-lock.ps1 ... | tail -1; python3 - <<'PY' ... PY

runs the edit regardless, because `tail` succeeds and `;` does not care. It happened **three times in
one session**: each time the lock belonged to another session, the edit landed anyway, and the release
call afterwards printed "CODE.LOCK belongs to session <other> - leaving it in place" - which is the
only sign anything was wrong, and it appears *after* the damage.

**How to apply:** make the lock call its own tool call, read its exit code, and only then edit. If the
output ends in "Meanwhile do lock-free work", you did NOT get the lock - background
`wait-for-lock-turn.ps1` chained with `enter-code-lock.ps1` in ONE pwsh process, wait for the
notification, and edit after it. Treat "I already wrote the file" as a reason to tell the operator,
not as a reason to continue.

## A single wait also loses the turn to the ticket's own age (2026-08-24, S1993)

The loop above is not belt-and-braces - it is load-bearing on its own. A queue ticket is evicted once it
is older than `TicketCeilingMinutes` (20 for Code), and that half of the condition in `agent-lock.ps1`
never asks whether the waiting session is alive. So a holder who keeps the lock longer than 20 minutes
guarantees that every waiter is dropped before its turn, longest-waiting first. Measured: waited 1179 s,
then `temp/CODE.TURN-<session>.json` read `{"outcome":"evicted","detail":"ticket no longer in queue"}` -
while the holder was demonstrably working (its `BUILD.LOCK` showed a running gradle build).

**How to apply:** never arm a bare `wait ; enter` pair for a lock you might wait more than ~15 minutes
for - use the retry loop, which re-queues on eviction and cost 311 s on the next attempt. Read the turn
marker, not the background task's exit code: eviction and defeat both surface as exit 1. And do not read
a dead PID in `lock-status` as a stale lock - for Code the PID is a transient pwsh process by design, and
liveness comes from session activity (S1448).
