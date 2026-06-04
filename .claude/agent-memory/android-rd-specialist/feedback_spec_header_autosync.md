---
name: spec-header-autosync
description: Owner reads the in-file **Status:** header to judge a spec's state; update.ps1 now mirrors the journal status into it automatically
metadata:
  type: feedback
---

The owner navigates spec state by the `**Status:**` header line inside `PLAN/Sxxxx_*.md`, not only the journal. That header must never sit stale.

**Why:** After a manual `update.ps1 -Status BlockNeedUserTest` on S0290, the journal moved but the in-file `**Status:** Tactical` header stayed put; the owner read the stale header and asked whether the ticket was really `Tactical`. He then asked to change the rule so the header always updates with the journal.

**How to apply:**
- Enforcement is now in tooling: shared fail-soft helper `Sync-SpecHeaderStatus` in `scripts/spec_catalog/_lib.ps1` rewrites the **first** `**Status:**` line of the record's `file` on every status change (first-match only, so ADR/Proposal `**Status:**` blocks deeper in the file are untouched). Wired into `update.ps1`, `archive.ps1` (sets `Archived` before moving the file to `temp/done/`), and `bulk-update.ps1`; the `complete.ps1`/`close.ps1`/`close-and-log.ps1` facades get it for free because they call `update.ps1`.
- So route all status changes through the catalog mutators - that keeps journal and header in lockstep. Do not hand-patch the header in isolation.
- The journal stays the single source of truth: still resolve any `Sxxxx` status via `select.ps1`, never by reading the header (see [[verify-spec-id-before-pipeline]]).
- If you ever see a header that disagrees with `select.ps1`, it predates this auto-sync or was hand-edited - one `update.ps1` round-trip (or a direct header fix) reconciles it.
