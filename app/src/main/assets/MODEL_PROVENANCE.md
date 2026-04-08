# Voice Model Asset Provenance (DevKey v1.0)

This directory must contain two files before voice input is functional:

| File | Size (approx) | Required by |
|---|---|---|
| `whisper-tiny.en.tflite` | ~40 MB | `VoiceInputEngine.kt:98` — `assetManager.open("whisper-tiny.en.tflite")` |
| `filters_vocab_en.bin` | ~1 MB | `WhisperProcessor.loadResources()` — mel filterbank + vocab |

## Sourcing

Per spec §5.2 the user chooses ONE provenance path and records the SHA256
of each file below before committing. Do NOT commit files without a
verified SHA256.

- [ ] `whisper-tiny.en.tflite` source URL: _____________________________
- [ ] `whisper-tiny.en.tflite` SHA256:       _____________________________
- [ ] `filters_vocab_en.bin` source URL:     _____________________________
- [ ] `filters_vocab_en.bin` SHA256:         _____________________________
- [ ] License compatible with DevKey (Apache-2.0 / MIT / BSD):  ☐ Yes  ☐ No
- [ ] APK size impact verified under 55 MB total:               ☐ Yes  ☐ No

## Graceful degradation

If these files are missing, `VoiceInputEngine.initialize()` catches the
exception and sets `modelLoaded = false`. The keyboard still functions;
voice button will either be hidden or will surface the error state.
This is the intended fallback for debug / development builds without
the assets bundled. Ship builds MUST have both files.

## References

- Spec: `.claude/specs/2026-04-08-pre-release-vision-spec.md` §5
- Code path: `app/src/main/java/dev/devkey/keyboard/feature/voice/VoiceInputEngine.kt:93-119`
- GH issue (tracking): to be filed if blocked
