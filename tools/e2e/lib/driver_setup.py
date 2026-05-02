"""Debug-server forwarding and warmup for DevKey E2E commands."""
import os
import time
from typing import Any, Optional


def require_driver_with_forwarding(serial: Optional[str] = None) -> Any:
    from lib import driver

    driver.require_driver()
    enable_debug_forwarding(driver, serial)
    return driver


def enable_debug_forwarding(driver_module: Any, serial: Optional[str] = None) -> None:
    from lib import adb

    driver_url = os.environ.get("DEVKEY_DRIVER_URL", "http://127.0.0.1:3950")
    ime_url = adb.configure_debug_server_forwarding(serial, driver_url)
    driver_module.broadcast(
        "dev.devkey.keyboard.ENABLE_DEBUG_SERVER",
        {"url": ime_url},
    )


def warm_debug_pipeline(serial: Optional[str] = None) -> None:
    driver = require_driver_with_forwarding(serial)

    for attempt in range(5):
        try:
            driver.clear_logs()
            driver.broadcast(
                "dev.devkey.keyboard.SET_BOOL_PREF",
                {"key": "devkey_show_toolbar", "value": True},
            )
            driver.wait_for("DevKey/IME", "bool_pref_set", timeout_ms=2000)
            break
        except Exception:
            if attempt < 4:
                time.sleep(2.0)
                enable_debug_forwarding(driver, serial)
    driver.clear_logs()
