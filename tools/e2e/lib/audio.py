"""
Audio injection helpers for voice round-trip testing.

Emulator mode: play a WAV file on the host audio device so the guest mic picks it up.
Physical device mode: the test is manual — audio cannot be injected programmatically
                      without hardware. The flow auto-skips with a warning.
"""
import os
import shutil
import subprocess
from typing import Optional

# Default sample lives next to the test modules so users can replace it.
DEFAULT_SAMPLE = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "fixtures", "voice-hello.wav"
)


def is_emulator(serial: Optional[str] = None) -> bool:
    """Best-effort emulator detection via getprop ro.kernel.qemu."""
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    cmd += ["shell", "getprop", "ro.kernel.qemu"]
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    return result.stdout.strip() == "1"


def inject_sample(sample_path: str = DEFAULT_SAMPLE) -> bool:
    """
    Play `sample_path` on the host so the emulator's guest mic captures it.

    Returns True if playback succeeded, False if skipped (no tool available
    or sample missing). Does NOT raise — the caller decides whether a skip is fatal.
    """
    if not os.path.exists(sample_path):
        return False

    # NOTE: Different host OSs have different CLI audio players. Try ffplay first
    #       (cross-platform, quiet mode), fall back to aplay (Linux), afplay (macOS).
    for player, args in [
        ("ffplay", ["-nodisp", "-autoexit", "-loglevel", "quiet", sample_path]),
        ("aplay", [sample_path]),
        ("afplay", [sample_path]),
    ]:
        if shutil.which(player):
            try:
                subprocess.run([player] + args, check=True, timeout=10)
                return True
            except subprocess.CalledProcessError:
                continue
    return False
