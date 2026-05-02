"""Shared paths for DevKey E2E tooling."""
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_RESULTS_DIR = PROJECT_ROOT / ".claude" / "test-results"
LONG_PRESS_EXPECTATIONS_DIR = PROJECT_ROOT / ".claude" / "test-flows" / "long-press-expectations"
TESTS_DIR = PROJECT_ROOT / "tools" / "e2e" / "tests"
