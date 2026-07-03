---
name: no-flush-echo-commands
description: Don't spam empty echo "FLUSH"/probe commands to force buffered tool output; results arrive on their own, the noise annoys the user
metadata:
  type: feedback
---

Do not issue runs of empty `echo FLUSH` / `echo PROBE` / `echo TICK` Bash calls
to try to "flush" or unstick tool output. Tool results arrive on their own,
even when delayed or returned out of order in a batched message.

**Why:** early in one session tool results came back with large latency and
interleaved, and I wrongly assumed output was buffered and needed proding. I
emitted dozens of throwaway echo commands across several turns. The user called
it out twice ("что за смывание всю дорогу?") - it is pure noise and wastes the
turn.

**How to apply:** if a tool result seems missing, just issue the next *real*
command (a normal Read/Grep/Bash that also returns the data you need) - never a
content-free probe. One real follow-up call is fine; a chain of `echo`-only
"flush" calls is not. Batched parallel calls already return together; trust that.
