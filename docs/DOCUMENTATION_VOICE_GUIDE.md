# Documentation Voice and Style Guide

This guide establishes the voice, tone, and editorial standards for all documentation pages in FastMediaSorter v2 (S2945 - S2967).

## Core Voice: "The Book of Tasty and Healthy Food"

Our documentation model is the classic culinary encyclopedia:
- **Warm and welcoming**: Friendly, reassuring, inviting the reader to explore with confidence.
- **Clear and instructive**: Explaining complex technical features in plain, everyday language.
- **Generous with visuals**: Illustrating each step with diagrams and screenshots so nothing is left to imagination.
- **Honest and grounded**: Direct about requirements, device constraints, and flavor boundaries.

---

## Key Principles

### 1. Simple, Plain English
- Write for a smart, curious person who is not a software engineer.
- Avoid unexplained acronyms and internal jargon (e.g., explain "SAF" as "Android Storage Access Framework folder picker", "SMB" as "Windows Network Shared Folder").
- Use active voice: "Tap the folder icon" rather than "The folder icon should be tapped".

### 2. Zero Marketing Slop
- Banned: superlatives like "revolutionary", "best-in-class", "unparalleled", "blazing fast".
- Banned: competitive comparisons ("unlike other slow apps").
- State factual capabilities: "Processes up to 1,000 files per second on local storage" or "Plays 4K 60fps video with hardware decoding".

### 3. Clear Flavor & Platform Boundaries
- FastMediaSorter ships in 7 editions: `standard`, `noLegal`, `lite`, `photos`, `legacy`, `vr`, `foss`.
- Always state if a feature is restricted to specific editions:
  - *Example:* "Network cloud downloads and streaming extractors are available exclusively in the sideload / noLegal edition."
  - *Example:* "Spatial cinema features require a Meta Quest 2/3 or OpenXR compatible headset."

### 4. Empathetic Troubleshooting
- Don't blame the user for errors.
- Clearly differentiate between app limits, Android system limits (Scoped Storage), and network limits.
- Offer actionable solutions: "If Android displays 'Access Denied', re-select the folder root in the system file picker to renew permissions."

---

## Terminology Reference

| Instead of (Jargon) | Use (Plain Language) |
|----------------------|----------------------|
| URI / SAF Tree | Selected Folder Permission |
| Daemon / Background worker | Background Task / Service |
| Hash deduplication | Identical File Finder |
| Insets / System Bars | Screen Margins & Notches |
| Complication | Watch Face Widget |
| Broadcast Receiver | Quick Action Receiver |
