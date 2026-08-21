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
