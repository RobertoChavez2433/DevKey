#!/usr/bin/env bash
# Pre-agent dispatch validation hook.
# Verifies the environment is ready before dispatching implementation agents.
#
# Checks:
#   1. Gradle is available (./gradlew --version succeeds) — CRITICAL
#   2. ADB device is connected (warning only — some dispatches don't need ADB)
#   3. Quick compile check (optional, skipped if SKIP_COMPILE_CHECK=1)
#
# Exit codes:
#   0 — all critical checks pass (warnings may have been emitted)
#   2 — critical check failed (Gradle not available)

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "=== pre-agent-dispatch: environment checks ==="

# ---------------------------------------------------------------------------
# Check 1: Gradle available (CRITICAL)
# ---------------------------------------------------------------------------
echo ""
echo "[1/3] Checking Gradle availability..."

if "$PROJECT_ROOT/gradlew" --version >/dev/null 2>&1; then
  gradle_version="$("$PROJECT_ROOT/gradlew" --version 2>/dev/null | grep -oP 'Gradle \K[\d.]+' | head -1)"
  echo "  PASS: Gradle ${gradle_version} available"
else
  echo "  FAIL: ./gradlew --version failed"
  echo "        Possible causes:"
  echo "          - gradlew not executable (run: chmod +x gradlew)"
  echo "          - Java/JDK not found on PATH"
  echo "          - Gradle wrapper JAR missing (run: gradle wrapper)"
  echo ""
  echo "pre-agent-dispatch: CRITICAL CHECK FAILED — aborting dispatch"
  exit 2
fi

# ---------------------------------------------------------------------------
# Check 2: ADB device connected (WARNING only — not fatal)
# ---------------------------------------------------------------------------
echo ""
echo "[2/3] Checking ADB device connectivity..."

if ! command -v adb >/dev/null 2>&1; then
  echo "  WARN: adb not found on PATH — skipping device check"
  echo "        (Install Android SDK platform-tools and add to PATH)"
else
  # Count lines that represent connected devices (not 'List of devices' header,
  # not empty lines, not 'offline' devices)
  device_count="$(adb devices 2>/dev/null | grep -cP '\t(device|emulator)' || true)"

  if [ "$device_count" -ge 1 ]; then
    echo "  PASS: ${device_count} ADB device(s) connected"
    adb devices 2>/dev/null | grep -P '\t(device|emulator)' | while IFS= read -r line; do
      echo "        ${line}"
    done
  else
    echo "  WARN: No ADB devices connected"
    echo "        (This is acceptable if this dispatch does not require device testing)"
    echo "        To connect: start emulator or plug in device with USB debugging enabled"
  fi
fi

# ---------------------------------------------------------------------------
# Check 3: Quick compile check (optional — skip with SKIP_COMPILE_CHECK=1)
# ---------------------------------------------------------------------------
echo ""
echo "[3/3] Quick compile check..."

if [ "${SKIP_COMPILE_CHECK:-0}" = "1" ]; then
  echo "  SKIP: SKIP_COMPILE_CHECK=1 set — skipping compile check"
else
  echo "  Running: ./gradlew assembleDebug (compile only, no install)..."
  if "$PROJECT_ROOT/gradlew" assembleDebug --quiet >/dev/null 2>&1; then
    echo "  PASS: assembleDebug succeeded"
  else
    echo "  WARN: assembleDebug failed — check build errors before dispatch"
    echo "        Run: ./gradlew assembleDebug for details"
    echo "        To skip this check: set SKIP_COMPILE_CHECK=1"
    # Non-fatal: warn but don't block. Agent may be fixing build errors.
  fi
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo ""
echo "=== pre-agent-dispatch: checks complete — proceeding with dispatch ==="
exit 0
