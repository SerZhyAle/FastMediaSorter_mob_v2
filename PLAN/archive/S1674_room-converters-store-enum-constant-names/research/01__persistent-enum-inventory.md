# Persistent enum inventory

**Ticket:** S1674
**Date:** 2026-08-15

## Evidence

- The Room converter writes and reads `ResourceType`, `SortMode`, `DisplayMode`, and nullable `CloudProvider` through `name` and `valueOf`.
- `BrowseStateDataStore` and `ResumeStateRepositoryImpl` independently persist enum-backed state, so Room is not the only durable string channel.
- The database is currently at version 50. The identified issue changes neither columns nor values, therefore no schema migration is warranted.

## Finding

Durable enum-name storage is a shared data-contract concern. The implementation must inventory all relevant string conversions and protect their enum members in the release shrinker configuration.
