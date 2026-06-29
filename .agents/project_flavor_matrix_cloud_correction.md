---
name: flavor-matrix-cloud-correction
description: legacy AND photos flavors DO have SUPPORT_CLOUD=true; legacy also has DOCUMENTS+TRANSLATION; verify flags in build.gradle.kts, not from any memorized matrix
type: project
---

The persona/system-prompt flavor matrix ("legacy: VIDEO+AUDIO+IMAGES+ANIM", "photos: IMAGES+ANIM") is STALE on cloud/documents. Ground truth from app_v2/build.gradle.kts productFlavors:

- **legacy**: SUPPORT_CLOUD=true, SUPPORT_LOCAL_NETWORK=true, SUPPORT_DOCUMENTS=true, ENABLE_TRANSLATION=true, ENABLE_EPUB=true (full set, only minSdk 23 differs).
- **photos**: SUPPORT_CLOUD=true, SUPPORT_LOCAL_NETWORK=true, ENABLE_ANIMATIONS=true; but SUPPORT_VIDEO=false, SUPPORT_AUDIO=false, SUPPORT_DOCUMENTS=false, ENABLE_TRANSLATION=false.
- **lite**: the only flavor with NO cloud, NO network, NO documents, NO translation, NO animations (local-files-only, S0448).
- Capability axes that actually gate docs claims: CLOUD (std/photos/legacy/noLegal/vr - NOT lite), LOCAL_NETWORK/SMB (same set), DOCUMENTS/PDF/EPUB (std/legacy/noLegal/vr - NOT lite, NOT photos), TRANSLATION/OCR (std/legacy/noLegal/vr).

**Why:** S0557 docs-drift audit nearly applied wrong fixes ("Photos has no cloud", "Legacy has no cloud") because both the memorized matrix and a sub-agent's report contradicted the real flags. The in-repo HOW_TO matrix row "Cloud storage | std✓ lite✗ photos✓ legacy✓ XR✓" matches build.gradle.kts, not the persona table.

**How to apply:** Any flavor-capability claim (docs, spec, code gate) - read the SUPPORT_*/ENABLE_* buildConfigField lines in app_v2/build.gradle.kts before asserting. Never answer flavor-capability questions from the persona's summary matrix; it predates cloud being added to legacy/photos.
