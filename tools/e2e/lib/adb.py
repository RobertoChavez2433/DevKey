"""Compatibility facade for DevKey E2E ADB helpers."""

from .adb_device import (
    _adb_cmd,
    configure_debug_server_forwarding,
    get_device_serial,
    get_focused_window,
    input_text,
    is_emulator,
    is_keyboard_visible,
    swipe,
    tap,
)
from .adb_logcat import (
    assert_logcat_contains,
    capture_logcat,
    clear_logcat,
)
from .adb_learning import (
    assert_learned_word,
    clear_learned_words,
)
from .adb_test_host import (
    TEST_HOST_ACTIVITY,
    assert_text_field_empty,
    clear_test_host_text,
    ensure_keyboard_visible,
    get_text_field_content,
    get_text_field_length,
    query_test_host_state,
    reset_keyboard_mode,
    reset_test_host_state,
)
from .adb_text_assertions import assert_test_host_text_equals
from .adb_visual import (
    analyze_keyboard_visual_baseline,
    capture_screenshot,
    capture_visual_baseline,
)

__all__ = [
    "TEST_HOST_ACTIVITY",
    "_adb_cmd",
    "analyze_keyboard_visual_baseline",
    "assert_logcat_contains",
    "assert_learned_word",
    "assert_text_field_empty",
    "assert_test_host_text_equals",
    "capture_logcat",
    "capture_screenshot",
    "capture_visual_baseline",
    "clear_logcat",
    "clear_learned_words",
    "clear_test_host_text",
    "configure_debug_server_forwarding",
    "ensure_keyboard_visible",
    "get_device_serial",
    "get_focused_window",
    "get_text_field_content",
    "get_text_field_length",
    "input_text",
    "is_emulator",
    "is_keyboard_visible",
    "query_test_host_state",
    "reset_keyboard_mode",
    "reset_test_host_state",
    "swipe",
    "tap",
]
