# 04 - How many measurements may exist at once

Resolves strategic §6 item 4 ("Ограничение на повторные подключения").

## Precedent in this repo

`StreamHealthProbeManager` runs its catalog sweep strictly sequentially and cancels the whole sweep on any user interaction, precisely so one short-lived decoder at a time competes with the user's device. The same class releases the engine in a `finally` block and survives cancellation.

## Decision

- One measurement at a time. The window owns exactly one, and closing the window cancels it - this alone bounds concurrency, because the window is modal and only one can be open.
- No result cache for the session. A cached reading would be reported as current while describing an older connection, which is the one thing strategic §3.2 forbids ("nothing may be reported as measured that was not measured").
- No rate limiter and no cooldown. They would need a user-visible refusal state to be honest, and refusing to measure looks identical to a channel that cannot be reached - the exact confusion strategic §7 warns about.
- The catalog health sweep and this measurement must not run together: opening the window cancels a running sweep, reusing the existing cancel-on-interaction path.

## Consequence for the plan

The measurer needs no queue, no scheduler and no shared registry - a cancellable job owned by the window, plus the existing sweep cancellation. Anything more would be infrastructure for a concurrency level the UI cannot produce.
