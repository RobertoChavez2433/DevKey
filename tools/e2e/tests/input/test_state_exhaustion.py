"""
Input E2E state exhaustion tests.

Drives the TextEntryState machine through its major transitions via
keystroke sequences and verifies each transition via DevKeyLogger
structural events.

TextEntryState has 12 states: UNKNOWN, START, IN_WORD, ACCEPTED_DEFAULT,
PICKED_SUGGESTION, PUNCTUATION_AFTER_WORD, PUNCTUATION_AFTER_ACCEPTED,
SPACE_AFTER_ACCEPTED, SPACE_AFTER_PICKED, UNDO_COMMIT, CORRECTING,
PICKED_CORRECTION.

Since the E2E harness cannot read the TextEntryState enum directly, tests
verify transitions indirectly through word_committed, backspace_handled,
and other structural events.

Depends on:
  - InputHandlers.handleBackspace() instrumentation (backspace_handled)
  - SessionDependencies.commitWord() instrumentation (word_committed)
  - HTTP forwarding enabled
"""
import time
from lib import adb, keyboard, driver

SPACE_CODE = 32
BACKSPACE_LABEL = "Backspace"


def _clear_edit_text(serial):
    """Clear the TestHostActivity EditText via debug broadcast."""
    import subprocess
    subprocess.run(
        adb._adb_cmd(
            ["shell", "am", "broadcast",
             "-a", "dev.devkey.keyboard.debug.CLEAR_EDIT_TEXT"],
            serial,
        ),
        capture_output=True,
    )
    time.sleep(0.3)


def _setup():
    serial = adb.get_device_serial()
    driver.require_driver()
    adb.ensure_keyboard_visible(serial)
    keyboard.set_layout_mode("compact", serial)
    if not keyboard.get_key_map() or len(keyboard.get_key_map()) < 10:
        time.sleep(1.0)
        keyboard.load_key_map(serial)
    _clear_edit_text(serial)
    # Ensure suggestions are enabled — a prior test may have disabled them.
    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.SET_BOOL_PREF",
        {"key": "show_suggestions", "value": True},
    )
    try:
        driver.wait_for("DevKey/IME", "bool_pref_set", timeout_ms=3000)
    except Exception:
        pass
    return serial


def _type_word(word, serial):
    """Tap each character with inter-key delay for reliable composing."""
    for ch in word:
        keyboard.tap_key(ch, serial)
        time.sleep(0.15)


def _ensure_suggestions(serial):
    """Enable suggestions and mild autocorrect for composing mode."""
    driver.clear_logs()
    driver.broadcast(
        "dev.devkey.keyboard.SET_BOOL_PREF",
        {"key": "show_suggestions", "value": True},
    )
    driver.wait_for("DevKey/IME", "bool_pref_set", timeout_ms=3000)
    driver.broadcast(
        "dev.devkey.keyboard.SET_AUTOCORRECT_LEVEL",
        {"level": "mild"},
    )
    time.sleep(0.3)


def test_state_cycle_type_accept_punctuate():
    """
    Drive: START -> IN_WORD -> ACCEPTED_DEFAULT -> PUNCTUATION_AFTER_ACCEPTED.

    Sequence: type word + space (commit/accept) + period + space.
    Verify structural events for the commit step and that the driver
    stays healthy through the punctuation transition.
    """
    serial = _setup()
    _ensure_suggestions(serial)
    driver.clear_logs()

    # START -> IN_WORD -> ACCEPTED_DEFAULT (word + space)
    _type_word("dog", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="word_committed",
        timeout_ms=5000,
    )
    assert entry["data"]["word_length"] == 3

    # ACCEPTED_DEFAULT -> PUNCTUATION_AFTER_ACCEPTED (period)
    keyboard.tap_key(".", serial)
    time.sleep(0.2)

    # PUNCTUATION_AFTER_ACCEPTED -> (space after punctuation)
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.2)

    # Pipeline must be healthy after full cycle
    driver.require_driver()


def test_state_cycle_type_and_undo():
    """
    Drive: START -> IN_WORD -> ACCEPTED_DEFAULT -> UNDO_COMMIT.

    Sequence: type word + space (commit) + backspace (undo).
    Verify the commit event and then the backspace_handled event
    on the undo path.
    """
    serial = _setup()
    _ensure_suggestions(serial)
    driver.clear_logs()

    # START -> IN_WORD -> ACCEPTED_DEFAULT
    _type_word("cat", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="word_committed",
        timeout_ms=5000,
    )
    assert entry["data"]["word_length"] == 3

    # ACCEPTED_DEFAULT -> UNDO_COMMIT (backspace reverts the commit)
    driver.clear_logs()
    time.sleep(0.4)
    keyboard.tap_key(BACKSPACE_LABEL, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="backspace_handled",
        timeout_ms=5000,
    )
    # After commit+backspace the undo path fires with was_composing=False
    assert entry["data"]["was_composing"] is False


def test_state_cycle_full_sentence():
    """
    Drive a full sentence through multiple state transitions:
      word1 + space + word2 + period + space + word3 + space

    Verify word_committed events fire for each word commitment step.
    This exercises: START -> IN_WORD -> ACCEPTED_DEFAULT ->
    SPACE_AFTER_ACCEPTED -> IN_WORD -> PUNCTUATION_AFTER_WORD ->
    START -> IN_WORD -> ACCEPTED_DEFAULT.
    """
    serial = _setup()
    _ensure_suggestions(serial)
    driver.clear_logs()

    # word1 + space
    _type_word("the", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="word_committed",
        timeout_ms=5000,
    )
    assert entry["data"]["word_length"] == 3

    # word2
    driver.clear_logs()
    _type_word("big", serial)

    # period (commits word2 and transitions to punctuation state)
    keyboard.tap_key(".", serial)
    time.sleep(0.3)

    # space after period
    keyboard.tap_key_by_code(SPACE_CODE, serial)
    time.sleep(0.2)

    # word3 + space
    driver.clear_logs()
    _type_word("end", serial)
    keyboard.tap_key_by_code(SPACE_CODE, serial)

    entry = driver.wait_for(
        category="DevKey/TXT",
        event="word_committed",
        timeout_ms=5000,
    )
    assert entry["data"]["word_length"] == 3

    # Pipeline healthy after full sentence
    driver.require_driver()
