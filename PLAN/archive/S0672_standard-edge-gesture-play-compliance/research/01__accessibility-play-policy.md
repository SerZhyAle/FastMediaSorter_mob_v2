# Research 01 - AccessibilityService screen capture under Google Play policy (2025-2026)

**Spec:** S0672
**Verdict:** prohibited (for a non-accessibility media tool)
**Method:** Web research against official Play policy (Accessibility API; Permissions and APIs that Access Sensitive Information; Deceptive Behavior; FLAG_SECURE).

## Conclusion

Using `AccessibilityService` (`BIND_ACCESSIBILITY_SERVICE`) to silently capture the screen (`AccessibilityService.takeScreenshot`, Android 11+) for a screenshot/media tool is a Play violation:
- `isAccessibilityTool="true"` may only be declared if the app's PRIMARY purpose is to directly help people with disabilities. A generic screenshot/media tool does not qualify; the policy explicitly lists automation tools, assistants, monitoring apps, cleaners, launchers, etc. as NOT accessibility tools. Declaring the flag falsely is a Deceptive Behavior / misrepresentation violation.
- The accessibility surface (including `TYPE_ACCESSIBILITY_OVERLAY`) is reserved for accessibility functionality; using it to draw a generic gesture strip is the same category of misuse.
- The accessibility API may not circumvent Android privacy controls - silent capture avoiding the MediaProjection consent surface fits this prohibition.
- The FLAG_SECURE bypass exemption is restricted to qualifying accessibility tools, and even they may not transmit/save/cache FLAG_SECURE content off-device - which a tool that saves images inherently does.

The compliant capture path for a media tool is MediaProjection (system consent), not AccessibilityService. The Oct 30, 2025 policy update further tightened the Accessibility API rules.

## Key clauses

- Only services whose primary purpose helps people with disabilities may declare `isAccessibilityTool=true`. [https://support.google.com/googleplay/android-developer/answer/10964491]
- Non-qualifying classes explicitly named (automation, assistants, monitoring, cleaners, launchers, ...). [https://support.google.com/googleplay/android-developer/answer/10964491]
- Non-eligible apps must meet prominent disclosure + affirmative consent and document use in the listing; privacy-policy-only is insufficient. [https://support.google.com/googleplay/android-developer/answer/16558241]
- May only request sensitive permissions/APIs necessary for promoted features; no undisclosed/disallowed purposes. [https://support.google.com/googleplay/android-developer/answer/16558241]
- Accessibility API cannot work around platform security/privacy controls. [https://support.google.com/googleplay/android-developer/answer/16558241]
- All apps must respect FLAG_SECURE; only qualifying accessibility tools exempt, and may not transmit/save/cache such content. [https://support.google.com/googleplay/android-developer/answer/16559646]
- Falsely declaring the flag is a Deceptive Behavior violation. [https://support.google.com/googleplay/android-developer/answer/10964491]

## Implication for S0672

The silent accessibility mechanism (`ScreenshotAccessibilityService`, `canTakeScreenshot=true`, `takeScreenshot`, `TYPE_ACCESSIBILITY_OVERLAY`) is categorically blocked in standard and must stay noLegal-only. The same end-user capability (capture + save) is re-delivered in standard ONLY via the MediaProjection consent path (S0671). Never declare `isAccessibilityTool=true` for a media tool.
