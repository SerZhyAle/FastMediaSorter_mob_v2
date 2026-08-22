---
name: ticket-log-audit-stale-right-after-status-flip
description: post-change's ticket-log audit can fail right after you flip a ticket into BlockNeedUserTest and add its probe; the same gate passes standalone seconds later - re-run rather than "fixing" anything
metadata:
  type: feedback
---

When you flip a ticket to `BlockNeedUserTest` and add its `Timber.d("Sxxxx: ..")` probe in the same stretch of work, the `ticket-log-audit` step inside `post-change.ps1` may report `1 missing probe(s)` and fail the whole closure - while `pwsh -NoProfile -File scripts/quality/assert-no-ticket-logs.ps1` run on its own moments later prints `0 missing probe(s)` and exits 0.

**Why:** observed twice on 2026-08-17/18 (S1743, then S1747), both times with the probe already on disk and the status already flipped before the closure ran. The mechanism was not established - do not repeat a cause you cannot show. What is established is the shape: the standalone gate and the in-facade gate disagreed, and the standalone one was right both times. Treat it as a stale read inside the facade, not as a real defect in your change.

**How to apply:** when `post-change` fails on `ticket-log-audit` alone, run `assert-no-ticket-logs.ps1` directly before touching anything. Exit 0 there means your probe and status are fine - re-run the same `post-change` command and it passes, writing exactly one changelog row (the dev-log guard identifies the change, not the file set). Only if the standalone gate also fails do you actually have a missing probe or a stale tag. Never add a second probe or a baseline excuse row to silence the first failure: that leaves a duplicate probe behind after the ticket leaves `BlockNeedUserTest`.

Related: [[detekt-baseline-hand-edit-daemon-stale]], [[post-change-detekt-stale-report]].
