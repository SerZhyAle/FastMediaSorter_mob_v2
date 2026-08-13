# Phase 04 - Canonical vectors + tests

**Goal:** freeze a v2 canonical vector, keep the v1 vector for backward compat, prove parser/serializer handle both.

## Steps

1. [ ] Add `app_v2/src/test/resources/companion/canonical_vector_v2.json` - one config, schemaVersion 2, with the full v2 field set on at least one root (profile `audio_library`, an explicit `mediaTypes`, scan flags, `isDestination`, `comment`, `accessPin`, `slideshowInterval`). Keep it byte-frozen (single line, deterministic) - it becomes the cross-repo canonical vector for the companion side.
2. [ ] Keep `canonical_vector.json` (v1) unchanged - it is the "old companion still works" fixture.
3. [ ] `CompanionConfigParserTest`:
   - Keep `parses canonical vector into expected DTO` (v1) - still asserts schemaVersion 1, all new root fields parse as null.
   - Change `rejects unknown higher schemaVersion` to bump to `3` (2 is now supported).
   - Add `parses v2 canonical vector` - assert new root fields decode (profile token, mediaTypes list, pin, slideshowInterval, isDestination, comment).
   - Add `accepts v1 config under v2 parser` - a bare v1 root yields null new fields (no exception).
   - Add `soft-ignores unknown profile token` - a root with `"profile":"weird"` parses without throwing (import resolves to default).
4. [ ] `CompanionConfigSerializerTest`: add `round-trips a v2 config with resource params` - build a DTO with the full v2 root, `assertEquals(dto, parsed)`.
5. [ ] Run: `.\gradlew.bat testStandardDebugUnitTest --tests "*CompanionConfig*"`.
   - Verification: all companion parser/serializer tests green.

## Notes

- Do not touch unrelated pre-existing failing tests (~26 known-broken elsewhere) - scope `--tests "*CompanionConfig*"`.
