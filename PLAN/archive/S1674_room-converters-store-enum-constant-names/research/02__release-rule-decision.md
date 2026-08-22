# Release rule decision

**Ticket:** S1674
**Date:** 2026-08-15

## Evidence

- `app_v2/proguard-rules.pro` has no app-level enum member preservation rule.
- The persisted values outlive an app update and are restored through enum-name lookup.
- Changing to ordinal storage would change the existing persisted format and introduce migration and fallback risks unrelated to the defect.

## Decision

Preserve names of application enum members used by durable string storage with a narrowly scoped release rule. Prove the rule against a minified artifact and keep the existing strings unchanged.
