# Research: Wear OS Resource Sync UX & UI

## Current State (AS-IS)
- **Watch UI**: `NetworkSourcesScreen.kt` is very basic. Uses `MaterialTheme` (M2). Loading is a simple text "⏳ Loading...".
- **Phone UI**: Zero "Wear" related UI in Settings or Resource Editor.
- **Spec**: Focusing purely on `DataClient` / `MessageClient` mechanisms. Lacks "WOW" factor and detailed UX description.

## UX Enhancement Idea: "The Beam"
- **Phone**: User enters "Wear Companion" settings. Taps a button "Push to Watch". The button expands into a pulsing "Scanning/Sending" animation.
- **Watch**: If the app is open on the Network screen, it shows a "Waiting for Phone..." state with a rotating ring.
- **Visuals**: Use Material 3 styled components if possible, or enhance M2 with custom Compose animations.
- **Haptics**: Use `HapticFeedback` API on both devices.
- **Feedback**: A summary screen on the watch showing exactly what was added/updated.

## Technical Feasibility
- Wearable Data Layer is perfect for this.
- Compose for Wear OS makes it easy to add radial progress and animations.
- Vibration patterns are straightforward via `Vibrator` or `HapticFeedback`.

## Missing Sections in Spec
- Detailed UI description for Phone side.
- Detailed UI description for Watch side.
- Animation and Haptics protocol.
- Audio feedback specification.
