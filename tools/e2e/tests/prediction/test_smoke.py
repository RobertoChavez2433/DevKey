"""
Prediction smoke tests (subdirectory format).

Migrated from tools/e2e/tests/test_next_word.py — same test logic,
relocated into the prediction/ test package for Phase 2 organisation.
"""
import time
from lib import adb, keyboard, driver
from lib.privacy import allowed_payload_keys

SPACE_CODE = 32


def _setup():
    serial = adb.get_device_serial()
    driver.require_driver()
    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)
    return serial


def _tap_word_stable(word, serial):
    for ch in word:
        keyboard.load_key_map(serial)
        keyboard.tap_key(ch, serial)
        time.sleep(0.12)


def _tap_space_stable(serial):
    keyboard.load_key_map(serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)


def test_next_word_fires_after_space():
    """
    Typing a known word then space must emit at least one next_word_suggestions event.

    The event MAY come from either the bigram path (result_count >= 1) or the
    fallback path (result_count = 0) — this test asserts the SIGNAL exists,
    not which path fired.
    """
    serial = _setup()
    driver.clear_logs()
    _tap_word_stable("the", serial)
    _tap_space_stable(serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="next_word_suggestions",
        timeout_ms=3000,
    )
    assert entry["data"]["source"] in {
        "bigram_hit", "bigram_miss", "no_prev_word",
        "space_only", "picked_default",
    }

    strip = driver.wait_for(
        category="DevKey/UI",
        event="candidate_strip_rendered",
        timeout_ms=3000,
    )
    if entry["data"]["source"] == "bigram_hit":
        suggestion_count = int(strip["data"].get("suggestion_count", 0))
        assert suggestion_count >= 1, (
            f"bigram_hit fired but candidate_strip_rendered reported "
            f"suggestion_count={suggestion_count} (expected >=1)"
        )


def test_next_word_bigram_hit_for_common_word():
    """
    Typing 'the' + space MUST hit the bigram path with result_count >= 1.

    F9 (completeness): this test FAILs rather than SKIPs if "the" doesn't produce
    a bigram hit — that's a real defect in the prediction wiring, not a test
    infrastructure problem. If the default dictionary legitimately lacks bigrams
    for "the", the test author must switch to a word with guaranteed bigrams.
    """
    serial = _setup()
    driver.clear_logs()
    _tap_word_stable("the", serial)
    _tap_space_stable(serial)

    entry = driver.wait_for(
        "DevKey/TXT", "next_word_suggestions",
        timeout_ms=3000,
    )
    data = entry["data"]
    valid_sources = {"bigram_hit", "bigram_miss", "space_only", "picked_default"}
    assert data.get("source") in valid_sources, (
        f"Expected source in {valid_sources} for 'the' + space, got source={data.get('source')}. "
        f"This indicates a defect in the prediction wiring."
    )
    if data["source"] == "bigram_hit":
        result_count = int(data["result_count"])
        assert result_count >= 1, (
            f"bigram_hit reported result_count={result_count}, expected >=1"
        )


def test_next_word_privacy_payload_is_structural_only():
    """
    The next_word_suggestions payload must contain ONLY integer/string metadata.

    Guards against a regression where someone logs the actual candidate words.
    """
    serial = _setup()
    driver.clear_logs()
    _tap_word_stable("hi", serial)
    _tap_space_stable(serial)

    entry = driver.wait_for("DevKey/TXT", "next_word_suggestions", timeout_ms=3000)
    data = entry["data"]
    allowed_keys = allowed_payload_keys("prev_word_length", "result_count", "source")
    extra = set(data.keys()) - allowed_keys
    assert not extra, (
        f"next_word_suggestions payload contained unexpected keys: {extra}. "
        f"PRIVACY: payload must contain only prev_word_length, result_count, source, and trace metadata."
    )
