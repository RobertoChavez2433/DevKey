# Voice Model Asset Provenance (DevKey v1.0)

This directory must contain two files before voice input is functional:

| File | Size (approx) | Required by |
|---|---|---|
| `whisper-tiny.en.tflite` | ~40 MB | `VoiceInputEngine.kt:98` — `assetManager.open("whisper-tiny.en.tflite")` |
| `filters_vocab_en.bin` | ~1 MB | `WhisperProcessor.loadResources()` — mel filterbank + vocab |

## Sourcing

Both files are pulled from the `nyadla-sys/whisper-tiny.en.tflite`
HuggingFace repo and bundled into the APK at build time. They are
.gitignored — fetch with:

```bash
curl -sL -o app/src/main/assets/whisper-tiny.en.tflite \
  https://huggingface.co/nyadla-sys/whisper-tiny.en.tflite/resolve/main/whisper-tiny.en.tflite
curl -sL -o app/src/main/assets/filters_vocab_en.bin \
  https://huggingface.co/nyadla-sys/whisper-tiny.en.tflite/resolve/main/filters_vocab_en.bin
```

| File | SHA256 |
|---|---|
| `whisper-tiny.en.tflite` (41,507,968 bytes) | `29e6006074527c071147a353aaa7a662b2acbbf48aa57721f2a3ea7cceb7ce29` |
| `filters_vocab_en.bin`  (586,174 bytes)     | `c296c7e7186786d801645d6719c1cef45077f826e953c2a9fb6cdb91ee63adaa` |

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
