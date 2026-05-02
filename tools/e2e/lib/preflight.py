"""Device metadata and preflight checks for DevKey E2E runs."""
import os
import subprocess
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

from .paths import DEFAULT_RESULTS_DIR, PROJECT_ROOT


def run_adb(args: List[str], serial: Optional[str] = None, timeout: float = 10.0) -> subprocess.CompletedProcess:
    cmd = ["adb"]
    if serial:
        cmd.extend(["-s", serial])
    cmd.extend(args)
    return subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=timeout,
    )


def device_metadata(serial: Optional[str]) -> Dict[str, Any]:
    def prop(name: str) -> str:
        try:
            return run_adb(["shell", "getprop", name], serial).stdout.strip()
        except Exception:
            return ""

    version_name = ""
    version_code = ""
    try:
        pkg = run_adb(["shell", "dumpsys", "package", "dev.devkey.keyboard"], serial, timeout=15.0).stdout
        for line in pkg.splitlines():
            line = line.strip()
            if line.startswith("versionName="):
                version_name = line.split("=", 1)[1]
            elif "versionCode=" in line:
                version_code = line.split("versionCode=", 1)[1].split()[0]
    except Exception:
        pass

    return {
        "serial": serial or os.environ.get("DEVKEY_DEVICE_SERIAL") or "(default)",
        "model": prop("ro.product.model"),
        "manufacturer": prop("ro.product.manufacturer"),
        "sdk": prop("ro.build.version.sdk"),
        "release": prop("ro.build.version.release"),
        "app_version_name": version_name,
        "app_version_code": version_code,
    }


def local_voice_assets() -> Dict[str, bool]:
    assets_dir = PROJECT_ROOT / "app" / "src" / "main" / "assets"
    return {
        "whisper-tiny.en.tflite": (assets_dir / "whisper-tiny.en.tflite").is_file(),
        "filters_vocab_en.bin": (assets_dir / "filters_vocab_en.bin").is_file(),
    }


def run_preflight(serial: Optional[str]) -> Dict[str, Any]:
    from lib import adb, driver, keyboard

    checks: Dict[str, Any] = {}

    boot = run_adb(["shell", "getprop", "sys.boot_completed"], serial)
    checks["device_booted"] = {
        "ok": boot.returncode == 0 and boot.stdout.strip() == "1",
        "value": boot.stdout.strip(),
    }

    installed = run_adb(["shell", "pm", "list", "packages", "dev.devkey.keyboard"], serial)
    checks["ime_installed"] = {
        "ok": installed.returncode == 0 and "dev.devkey.keyboard" in installed.stdout,
    }

    default_ime = run_adb(["shell", "settings", "get", "secure", "default_input_method"], serial)
    checks["ime_default"] = {
        "ok": "dev.devkey.keyboard" in default_ime.stdout,
        "value": _first_nonblank(default_ime.stdout),
    }

    try:
        health = driver.health()
        checks["debug_server"] = {
            "ok": health.get("status") == "ok",
            "entries": health.get("entries"),
            "url": driver.DRIVER_URL,
        }
    except Exception as exc:
        checks["debug_server"] = {
            "ok": False,
            "url": driver.DRIVER_URL,
            "error": str(exc),
        }

    focus_status = adb.ensure_keyboard_visible(serial)
    checks["keyboard_visible"] = {"ok": bool(focus_status.get("visible")), **focus_status}
    checks["audio_permission"] = {
        "ok": bool(focus_status.get("permissions", {}).get("RECORD_AUDIO")),
        "permission": "RECORD_AUDIO",
    }

    try:
        clean_state = adb.reset_test_host_state(serial)
        checks["clean_test_state"] = {
            "ok": (
                bool(clean_state.get("visible"))
                and clean_state.get("text_length") == 0
            ),
            "text_length": clean_state.get("text_length"),
            "keyboard_visible": bool(clean_state.get("visible")),
        }
    except Exception as exc:
        checks["clean_test_state"] = {
            "ok": False,
            "error": f"{type(exc).__name__}: {exc}",
        }

    try:
        keyboard.set_layout_mode("full", serial)
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        visual_path = DEFAULT_RESULTS_DIR / f"visual-baseline-{stamp}.png"
        checks["visual_baseline"] = adb.capture_visual_baseline(serial, visual_path)
    except Exception as exc:
        checks["visual_baseline"] = {
            "ok": False,
            "error": f"{type(exc).__name__}: {exc}",
        }

    try:
        key_map = keyboard.load_key_map(serial)
        key_map_size = len(key_map)
        checks["key_map"] = {
            "ok": key_map_size > 10,
            "size": key_map_size,
            "symbols_size": len(keyboard.get_symbols_key_map()),
        }
        checks["keyboard_visible"]["key_map_size"] = key_map_size
    except Exception as exc:
        checks["key_map"] = {
            "ok": False,
            "size": 0,
            "error": f"{type(exc).__name__}: {exc}",
        }

    voice_assets = local_voice_assets()
    checks["voice_assets"] = {
        "ok": all(voice_assets.values()),
        "assets": voice_assets,
    }
    checks["reset_strategy"] = {
        "ok": True,
        "strategy": (
            "TestHostActivity focus + text clear + normal keyboard mode + "
            "RESET_CIRCUIT_BREAKER; no force-stop"
        ),
    }

    ok = all(bool(value.get("ok")) for value in checks.values())
    return {
        "ok": ok,
        "checks": checks,
        "device": device_metadata(serial),
    }


def _first_nonblank(value: str) -> str:
    for line in value.splitlines():
        if line.strip():
            return line.strip()
    return ""
