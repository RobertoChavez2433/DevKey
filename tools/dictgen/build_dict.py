#!/usr/bin/env python3
"""
Convert an AOSP .combined word list into a compact gzipped dictionary
for DevKey's Kotlin TrieDictionary reader.

Output format (gzipped text):
  word<TAB>frequency\n

Usage:
  python build_dict.py en_us_raw.combined ../../app/src/main/res/raw/en_us_wordfreq.gz
"""
import gzip
import re
import sys
from pathlib import Path

WORD_RE = re.compile(r"^ word=(.+),f=(\d+)")


def parse_combined(path: Path):
    words = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            m = WORD_RE.match(line)
            if m:
                word = m.group(1).split(",")[0]  # take word before any extra commas
                freq = int(m.group(2))
                if len(word) >= 1 and freq > 0:
                    words.append((word, freq))
    return words


def main():
    if len(sys.argv) < 3:
        print(f"Usage: {sys.argv[0]} <input.combined> <output.gz>")
        sys.exit(1)

    src = Path(sys.argv[1])
    dst = Path(sys.argv[2])

    print(f"Parsing {src}...")
    words = parse_combined(src)
    print(f"  Found {len(words)} words")

    # Sort by frequency descending (highest first)
    words.sort(key=lambda x: -x[1])

    # Write gzipped output
    dst.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(dst, "wt", encoding="utf-8") as f:
        for word, freq in words:
            f.write(f"{word}\t{freq}\n")

    raw_size = sum(len(w) + 5 for w, _ in words)
    gz_size = dst.stat().st_size
    print(f"  Written {len(words)} entries to {dst}")
    print(f"  Compressed: {gz_size:,} bytes ({gz_size/1024:.0f} KB)")
    print(f"  Raw text would be: ~{raw_size:,} bytes")


if __name__ == "__main__":
    main()
