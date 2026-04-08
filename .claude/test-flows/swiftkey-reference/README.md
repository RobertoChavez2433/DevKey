# SwiftKey Reference Screenshots (internal-use-only)

**Scope**: Spec `.claude/specs/2026-04-08-pre-release-vision-spec.md` §4.4 Ground Truth.
**Legal**: Per spec §8 Risk Register — "capture reference screenshots for
internal comparison only, do not redistribute them." These files must NOT
leave this repository and MUST NOT be published to any mirror, release
artifact, or public CI log. Add to `.gitignore` if repo is published.

## Required captures

All captures on the SAME emulator image / DPI / screen size that DevKey
`/test` runs against, so pixel diffs are meaningful.

### Keyboard modes (×2 themes = 12 images)
- [ ] `qwerty-light.png`
- [ ] `qwerty-dark.png`
- [ ] `compact-light.png`
- [ ] `compact-dark.png`
- [ ] `full-light.png`
- [ ] `full-dark.png`
- [ ] `symbols-light.png`
- [ ] `symbols-dark.png`
- [ ] `fn-light.png`
- [ ] `fn-dark.png`
- [ ] `phone-light.png`
- [ ] `phone-dark.png`

### Long-press popups (1 per key per mode — captured during Phase 5)
Stored under `long-press/<mode>/<keyname>.png`.

## Visual-diff tolerance

Spec §9 Q3 is unresolved: pixel-exact vs similarity (SSIM > 0.95).
Default: SSIM > 0.95 per row region; escalate to pixel-exact only where
structural parity is at stake (key hit-box sizes).

## References

- Spec §4 SwiftKey Parity Scope
- `.claude/test-flows/registry.md` — test flow registry (entry to be
  added in Phase 2 of the umbrella spec, not this Phase 1 plan)
