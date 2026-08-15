# S1670 - Throwaway fixture: S1620 archive relocation verification

**Ticket:** S1670
**Status:** Archived
**Priority:** 0
**Date:** 2026-08-14

---

## 1. Purpose

Disposable fixture created solely to exercise `scripts/spec_catalog/archive.ps1` while
verifying S1620. It carries no product scope, appears in no release package, and is
archived immediately after creation. Its only job is to prove that an archived spec
lands in `PLAN/archive/`, that the journal records the new path, and that git sees the
archived file as untracked-new rather than ignored.
