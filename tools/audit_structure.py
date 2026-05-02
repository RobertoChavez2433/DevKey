#!/usr/bin/env python3
"""Report large and import-heavy maintained source files."""
import argparse
import json
import re
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable, List

SOURCE_EXTENSIONS = {".c", ".cpp", ".h", ".hpp", ".java", ".js", ".kt", ".py"}
DEFAULT_INCLUDE_ROOTS = (
    "app/src/main",
    "app/src/test",
    "scripts",
    "tools/debug-server",
    "tools/e2e",
)
EXCLUDED_PARTS = {
    ".git",
    ".gradle",
    ".pytest_cache",
    "__pycache__",
    "artifacts",
    "build",
    "node_modules",
}
JS_REQUIRE_RE = re.compile(r"^(const|let|var)\s+.+\s*=\s*require\(")


@dataclass
class FileMetric:
    path: str
    lines: int
    imports: int


def main() -> None:
    parser = argparse.ArgumentParser(description="Audit maintained source structure.")
    parser.add_argument("--root", default=".", help="Repository root")
    parser.add_argument("--max-lines", type=int, default=400)
    parser.add_argument("--max-imports", type=int, default=35)
    parser.add_argument("--format", choices=("text", "json"), default="text")
    parser.add_argument("--fail-on-violations", action="store_true")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    metrics = sorted(collect_metrics(root), key=lambda item: (-item.lines, item.path))
    line_violations = [item for item in metrics if item.lines > args.max_lines]
    import_violations = [item for item in metrics if item.imports > args.max_imports]

    payload = {
        "schema_version": 1,
        "root": str(root),
        "thresholds": {
            "max_lines": args.max_lines,
            "max_imports": args.max_imports,
        },
        "summary": {
            "files_scanned": len(metrics),
            "line_violations": len(line_violations),
            "import_violations": len(import_violations),
        },
        "largest_files": [asdict(item) for item in metrics[:25]],
        "line_violations": [asdict(item) for item in line_violations],
        "import_violations": [asdict(item) for item in sorted(import_violations, key=lambda item: (-item.imports, item.path))],
    }

    if args.format == "json":
        print(json.dumps(payload, indent=2, sort_keys=True))
    else:
        print_text_report(payload)

    if args.fail_on_violations and (line_violations or import_violations):
        raise SystemExit(1)


def collect_metrics(root: Path) -> List[FileMetric]:
    metrics: List[FileMetric] = []
    for include_root in DEFAULT_INCLUDE_ROOTS:
        base = root / include_root
        if not base.exists():
            continue
        for path in _source_files(base):
            text = path.read_text(encoding="utf-8", errors="replace")
            relative = path.relative_to(root).as_posix()
            metrics.append(
                FileMetric(
                    path=relative,
                    lines=len(text.splitlines()),
                    imports=count_imports(path.suffix, text.splitlines()),
                )
            )
    return metrics


def count_imports(suffix: str, lines: Iterable[str]) -> int:
    count = 0
    for line in lines:
        stripped = line.strip()
        if suffix in (".kt", ".java") and stripped.startswith("import "):
            count += 1
        elif suffix == ".py" and (stripped.startswith("import ") or stripped.startswith("from ")):
            count += 1
        elif suffix == ".js" and (stripped.startswith("import ") or JS_REQUIRE_RE.match(stripped)):
            count += 1
        elif suffix in (".c", ".cpp", ".h", ".hpp") and stripped.startswith("#include"):
            count += 1
    return count


def print_text_report(payload: dict) -> None:
    summary = payload["summary"]
    thresholds = payload["thresholds"]
    print("DevKey structure audit")
    print(f"Scanned files: {summary['files_scanned']}")
    print(f"Line threshold: {thresholds['max_lines']}; violations: {summary['line_violations']}")
    print(f"Import threshold: {thresholds['max_imports']}; violations: {summary['import_violations']}")

    print("\nLargest maintained files:")
    for item in payload["largest_files"][:15]:
        print(f"  {item['lines']:>4} lines  {item['imports']:>2} imports  {item['path']}")

    if payload["line_violations"]:
        print("\nLine violations:")
        for item in payload["line_violations"]:
            print(f"  {item['lines']:>4} lines  {item['path']}")

    if payload["import_violations"]:
        print("\nImport violations:")
        for item in payload["import_violations"]:
            print(f"  {item['imports']:>2} imports  {item['path']}")


def _source_files(base: Path) -> Iterable[Path]:
    for path in base.rglob("*"):
        if not path.is_file() or path.suffix not in SOURCE_EXTENSIONS:
            continue
        if any(part in EXCLUDED_PARTS for part in path.parts):
            continue
        yield path


if __name__ == "__main__":
    main()
