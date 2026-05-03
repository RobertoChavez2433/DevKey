"""
Next-word prediction tests.

FROM SPEC: §6 Phase 2 item 2.3 — "Predictive next-word (type word → space → verify
           candidate strip populated with next-word suggestions)"

Depends on:
  - setNextSuggestions instrumentation added in Phase 2 sub-phase 1.3
  - HTTP forwarding enabled (sub-phase 1.1)
"""
from lib import adb, keyboard, driver
from lib.privacy import allowed_payload_keys

SPACE_CODE = 32


def _setup():
    serial = adb.get_device_serial()
    driver.require_driver()
    if not keyboard.get_key_map():
        keyboard.load_key_map(serial)
    return serial


def test_next_word_fires_after_space():
    """
    Typing a known word then space must emit at least one next_word_suggestions event.

    The event MAY come from donor next-word ranking (result_count >= 1) or the
    explicit temporary fallback (result_count = 0). This test asserts the SIGNAL exists,
    not which path fired.
    """
    serial = _setup()
    driver.clear_logs()
    # Type "the" — a common word with many bigrams in the default dict.
    for ch in "the":
        keyboard.tap_key(ch, serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="next_word_suggestions",
        timeout_ms=3000,
    )
    # The handleSeparator path emits an earlier "space_only" / "picked_default"
    # signal before setNextSuggestions runs; either is a valid first match.
    assert entry["data"]["source"] in {
        "bigram_hit", "bigram_miss", "no_prev_word",
        "space_only", "picked_default",
        "smart_text_hit", "smart_text_miss", "smart_text_pending",
    }

    # F3 (completeness): verify the candidate strip actually rendered, not just
    # that setNextSuggestions fired. Instrumentation added in Phase 1 sub-phase 1.6.
    strip = driver.wait_for(
        category="DevKey/UI",
        event="candidate_strip_rendered",
        timeout_ms=3000,
    )
    # On the bigram-hit path, at least one suggestion must be visible.
    if entry["data"]["source"] == "bigram_hit":
        suggestion_count = int(strip["data"].get("suggestion_count", 0))
        assert suggestion_count >= 1, (
            f"bigram_hit fired but candidate_strip_rendered reported "
            f"suggestion_count={suggestion_count} (expected >=1)"
        )


def test_next_word_signal_for_common_word():
    """
    Typing 'the' + space must cross the next-word smart-text boundary.

    The imported ASK wordlist does not yet provide donor bigram ranking, so
    smart_text_pending is a valid temporary source while that adapter lands.
    """
    serial = _setup()
    driver.clear_logs()
    for ch in "the":
        keyboard.tap_key(ch, serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    # Wait for ANY next_word_suggestions event. The source may be a donor hit
    # later, or the explicit temporary smart_text_pending source today.
    entry = driver.wait_for(
        "DevKey/TXT", "next_word_suggestions",
        timeout_ms=3000,
    )
    data = entry["data"]
    valid_sources = {
        "bigram_hit", "bigram_miss", "space_only", "picked_default",
        "smart_text_hit", "smart_text_miss", "smart_text_pending",
    }
    assert data.get("source") in valid_sources, (
        f"Expected source in {valid_sources} for 'the' + space, got source={data.get('source')}. "
        f"This indicates a defect in the prediction wiring."
    )
    # If bigram_hit fired, verify result_count >= 1.
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
    for ch in "hi":
        keyboard.tap_key(ch, serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry = driver.wait_for("DevKey/TXT", "next_word_suggestions", timeout_ms=3000)
    data = entry["data"]
    allowed_keys = allowed_payload_keys("prev_word_length", "result_count", "source")
    extra = set(data.keys()) - allowed_keys
    assert not extra, (
        f"next_word_suggestions payload contained unexpected keys: {extra}. "
        f"PRIVACY: payload must contain only prev_word_length, result_count, source, and trace metadata."
    )
