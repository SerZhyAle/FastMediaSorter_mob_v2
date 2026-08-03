---
name: target-audience-non-technical
description: App is for non-technical everyday people (grandma opening PC photos, gym-goer streaming home-PC music), not power users
metadata:
  type: feedback
---

The product's north star: FastMediaSorter is for ordinary non-technical people, not "boring nerds". Canonical personas the owner named:
- A grandma who wants to open her photos from the home computer to show her grandkids.
- A fitness/gym person who wants to listen to music recorded on the home computer while working out.

**Why:** The owner (Serhii) stated this explicitly as the design compass. A slogan of "not for geeks" only matters if the code matches it - the risk is a UI that leaks technical jargon and config friction that these personas cannot cross.

**How to apply:**
- Zero jargon in user-visible strings: no "SMB share", "mount point", "credentials", "RTSP", stacktraces in the face. Speak the persona's language ("Computer -> Photos"), consistent with `docs/COMMUNICATION_POLICY*.md`.
- Zero mandatory configuration before first success: defaults that just work; the happy path must reach a result without a setup maze.
- Every failure states a human next step ("Computer is off or not on the same network"), never a bare error code.
- Robustness on the real-world path the persona lives in: weak WiFi, screen lock, headset, connection drops - graceful, no crashes; sane lock-screen media controls.
- Use this lens when scoping features, choosing defaults, and reviewing UI copy: if a step would stop the grandma or the gym-goer, it's a defect, not an edge case.

Related: [[writing-style-dashes-yo-ellipsis]], [[about-me]].
