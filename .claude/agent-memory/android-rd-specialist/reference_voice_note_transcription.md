---
name: voice-note-transcription
description: How to read an owner voice note (.ogg/.m4a) - offline faster-whisper in the project venv, no API key
metadata:
  type: reference
---

The owner sometimes drops a voice note into the chat instead of typing (e.g. `@C:\Users\serzh\.claude\uploads\<session>\<file>.ogg` with "создай спецификации"). No tool can read audio directly, and the Read tool rejects it as binary.

Working setup, installed 2026-07-29 into the project venv `.venv\Scripts\python.exe`:

- `faster-whisper` (CTranslate2, CPU, int8). Fully offline after the first run; model weights cache under the user profile. No API key, so it satisfies [[no-paid-or-key-third-party-services]].
- Helper script pattern: `WhisperModel(size, device="cpu", compute_type="int8")` then `model.transcribe(audio, beam_size=5, vad_filter=True)`. `small` handled a 97-second Russian note accurately (language auto-detected, prob 0.99) in about a minute.

**How to apply:** when a voice note arrives, transcribe it first, then work from the transcript - do not ask the owner to retype. Persist both the audio and the transcript as spec attachments (`PLAN/Sxxxx_<slug>/attachments/`) so §0 raw capture stays verifiable, and quote the owner verbatim in the spec rather than paraphrasing.

A single note usually contains several distinct problems. Split them into one spec each after a dedup check, and keep the owner's own scope relief ("if that's hard, X is enough") in the spec - it is a pre-approved fallback.
