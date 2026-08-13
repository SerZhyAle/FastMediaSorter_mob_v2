# S0923 - Tactical plan

Strategic: `PLAN/S0923_ocr-native-attach-arm64-crash.md`

Scope of this plan = Layer 1 (deterministic anti-crash guard + on-device diagnosis) from strategic §3. Layer 2 (feature restoration - re-bundle vs fix-reflection) is an owner decision (strategic §6, D1) and is NOT implemented here; it becomes a follow-up once the Layer-1 device log confirms the injection-ineffective diagnosis.

## Phases

- [ ] 01 - Loader post-attach `findLibrary` verification + WARN diagnostics
- [ ] 02 - Engine-boundary `LinkageError` guards (Tesseract + Paddle)
- [ ] 03 - Build gate + BlockNeedUserTest device verification

## Acceptance

- No process crash on any OCR/DTS entry when delivered native libs cannot be name-resolved; the flow degrades to "unavailable".
- When injection is ineffective, logcat carries a permanent WARN naming the delivered soname and its wrongly-resolved path (no `Sxxxx` in the permanent line).
- Camera OCR-translate on the API 36 device: no crash (device-verified).
