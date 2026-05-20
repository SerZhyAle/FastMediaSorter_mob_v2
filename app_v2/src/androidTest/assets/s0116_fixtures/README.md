# S0116 instrumentation fixtures

These files are PLACEHOLDERS. Replace with real binaries before running
`MediaMuxerRemuxerInstrumentationTest`:

- `tiny_avc_aac.ts` - small (<256 KiB) MPEG-TS segment with H.264/AVC video and AAC audio.
  Generate via `ffmpeg -f lavfi -i testsrc -t 1 -c:v libx264 tiny_avc_aac.ts`.
- `tiny_opus.webm` - small WebM segment with Opus audio.
  Generate via `ffmpeg -f lavfi -i sine -t 1 -c:a libopus tiny_opus.webm`.

Until these are populated, the test uses `assumeTrue` to skip cases that
require valid media bytes - it does not fail spuriously.
