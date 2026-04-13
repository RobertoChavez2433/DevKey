"""
Prediction stress tests.

Exercises the next-word prediction pipeline under sustained typing load:
rapid word sequences, repeated prediction cycles, multi-sentence input,
and graceful fallback for unknown words.
"""
import time
from lib import adb, keyboard, driver

SPACE_CODE = 32

# Common English words — short, high-frequency, likely in any dictionary.
COMMON_WORDS = [
    "the", "of", "and", "to", "in", "is", "it", "for", "on", "was",
    "at", "by", "an", "be", "as", "or", "if", "no", "so", "up",
]


def _setup():
    serial = adb.get_device_serial()
    driver.require_driver()
    adb.ensure_keyboard_visible(serial)
    keyboard.set_layout_mode("compact", serial)
    if not keyboard.get_key_map() or len(keyboard.get_key_map()) < 10:
        time.sleep(1.0)
        keyboard.load_key_map(serial)
    driver.broadcast("dev.devkey.keyboard.RESET_KEYBOARD_MODE", {})
    time.sleep(0.3)
    # Enable suggestions so prediction events fire
    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.SET_BOOL_PREF",
        {"key": "show_suggestions", "value": True},
    )
    try:
        driver.wait_for("DevKey/IME", "bool_pref_set", timeout_ms=3000)
    except Exception:
        pass
    driver.broadcast(
        "dev.devkey.keyboard.SET_AUTOCORRECT_LEVEL",
        {"level": "mild"},
    )
    time.sleep(0.3)
    return serial


def test_rapid_20_word_typing():
    """
    Type 20 common words rapidly and verify a next_word_suggestions event
    fires after each space.  Asserts event count, never text content.
    """
    serial = _setup()
    event_count = 0

    for word in COMMON_WORDS:
        driver.clear_logs()
        keyboard.tap_sequence(word, delay=0.05, serial=serial)
        keyboard.tap_key_by_code(SPACE_CODE, serial)

        entry = driver.wait_for(
            "DevKey/TXT", "next_word_suggestions",
            timeout_ms=3000,
        )
        assert entry["data"]["source"] in {
            "bigram_hit", "bigram_miss", "no_prev_word",
            "space_only", "picked_default",
        }
        event_count += 1

    assert event_count == 20, (
        f"Expected 20 next_word_suggestions events, got {event_count}"
    )


def test_suggestion_tap_chain():
    """
    Repeat the 'type word + space' pattern 5 times and verify the prediction
    event pipeline fires each time.  Does not tap the suggestion bar (coords
    are unreliable) — only verifies the event signal.
    """
    serial = _setup()
    chain_words = ["i", "am", "at", "the", "end"]
    event_count = 0

    for word in chain_words:
        driver.clear_logs()
        keyboard.tap_sequence(word, delay=0.08, serial=serial)
        keyboard.tap_key_by_code(SPACE_CODE, serial)

        entry = driver.wait_for(
            "DevKey/TXT", "next_word_suggestions",
            timeout_ms=3000,
        )
        assert "source" in entry["data"]
        event_count += 1

    assert event_count == 5, (
        f"Expected 5 prediction events in tap chain, got {event_count}"
    )


def test_three_sentence_paragraph():
    """
    Type a 3-sentence paragraph word by word and verify next_word_suggestions
    fires after every space (including after punctuation).
    """
    serial = _setup()
    # Short sentences; periods typed inline via tap_key.
    sentences = [
        ["i", "am", "here"],
        ["you", "are", "there"],
        ["we", "go", "now"],
    ]
    event_count = 0

    for sentence in sentences:
        for word in sentence:
            driver.clear_logs()
            keyboard.tap_sequence(word, delay=0.08, serial=serial)
            keyboard.tap_key_by_code(SPACE_CODE, serial)

            entry = driver.wait_for(
                "DevKey/TXT", "next_word_suggestions",
                timeout_ms=3000,
            )
            assert "source" in entry["data"]
            event_count += 1
        # Type period after each sentence.
        keyboard.tap_key(".", serial)

    assert event_count == 9, (
        f"Expected 9 prediction events (3 sentences x 3 words), got {event_count}"
    )


def test_rare_word_graceful_fallback():
    """
    Typing a nonsense word + space must still produce a next_word_suggestions
    event without crashing.  The source must NOT be 'bigram_hit' since the
    word has no dictionary entry.
    """
    serial = _setup()
    driver.clear_logs()
    keyboard.tap_sequence("xyzqwkjh", delay=0.05, serial=serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry = driver.wait_for(
        "DevKey/TXT", "next_word_suggestions",
        timeout_ms=3000,
    )
    data = entry["data"]
    assert data["source"] != "bigram_hit", (
        f"Nonsense word should not produce bigram_hit, got source={data['source']}"
    )
    assert data["source"] in {
        "bigram_miss", "no_prev_word", "space_only", "picked_default",
    }, f"Unexpected source for nonsense word: {data['source']}"
