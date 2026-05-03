"""Smart-text import measurement checks."""
import time

from lib import adb, driver, verify
from lib.paths import PROJECT_ROOT
from lib.privacy import allowed_payload_keys


APK_PATH = PROJECT_ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
RAW_DICTIONARY_PATH = (
    PROJECT_ROOT
    / "app"
    / "src"
    / "main"
    / "res"
    / "raw"
    / "ask_english_wordlist_combined.gz"
)


def test_smart_text_import_metrics_are_reported():
    """
    The imported AnySoftKeyboard dictionary path must report size, load, and
    memory metrics through the debug server without exposing dictionary words.
    """
    assert APK_PATH.is_file(), f"Debug APK missing at {APK_PATH}"
    assert RAW_DICTIONARY_PATH.is_file(), f"ASK dictionary artifact missing at {RAW_DICTIONARY_PATH}"

    serial = adb.get_device_serial()
    driver.require_driver()
    adb.ensure_keyboard_visible(serial)

    entry = _dump_metrics()
    data = entry["data"]

    allowed_keys = allowed_payload_keys(
        "source",
        "artifact",
        "locale",
        "version",
        "word_count",
        "artifact_bytes",
        "load_duration_ms",
        "memory_delta_kb",
        "loaded_at_uptime_ms",
    )
    extra = set(data.keys()) - allowed_keys
    assert not extra, (
        f"smart_text_import_metrics payload contained unexpected keys: {extra}. "
        "PRIVACY: payload must stay structural and must not include dictionary words."
    )

    assert data["source"] == "anysoftkeyboard_language_pack"
    assert data["artifact"] == "ask_english_wordlist_combined"
    assert int(data["word_count"]) > 1000
    assert int(data["artifact_bytes"]) == RAW_DICTIONARY_PATH.stat().st_size
    assert int(data["load_duration_ms"]) >= 0
    assert int(data["loaded_at_uptime_ms"]) > 0

    verify.record_evidence(
        "smart_text_import_local_sizes",
        {
            "apk_size_bytes": APK_PATH.stat().st_size,
            "raw_artifact_size_bytes": RAW_DICTIONARY_PATH.stat().st_size,
        },
    )


def _dump_metrics():
    last_error = None
    for _ in range(4):
        driver.clear_logs()
        driver.broadcast("dev.devkey.keyboard.debug.DUMP_SMART_TEXT_IMPORT_METRICS", {})
        try:
            return driver.wait_for(
                category="DevKey/IME",
                event="smart_text_import_metrics",
                timeout_ms=3000,
            )
        except driver.DriverError as exc:
            last_error = exc
            time.sleep(1.0)
    raise AssertionError(f"smart_text_import_metrics was not reported: {last_error}")
