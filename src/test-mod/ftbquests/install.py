#!/usr/bin/env python3
"""Install development quest fixtures without replacing unrelated FTB configuration."""

import argparse
import shutil
from pathlib import Path


def main():
    source = Path(__file__).resolve().parent / "quests"
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--game-dir", type=Path,
        default=source.parents[3] / "run-test-client",
        help="Minecraft game directory (default: this repository's run-test-client)",
    )
    parser.add_argument(
        "--overwrite", action="store_true",
        help="Replace modified fixture chapters/translations; shared settings are always preserved",
    )
    args = parser.parse_args()
    destination = args.game_dir.resolve() / "config" / "ftbquests" / "quests"
    copied = unchanged = kept = 0

    for file in sorted(source.rglob("*.json5")):
        relative = file.relative_to(source)
        target = destination / relative
        if target.exists():
            if target.read_bytes() == file.read_bytes():
                unchanged += 1
                continue
            # Seed global settings only when absent, even with --overwrite.
            if relative.parent == Path(".") or not args.overwrite:
                print(f"Kept existing: {relative}")
                kept += 1
                continue
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(file, target)
        copied += 1

    print(f"Destination: {destination}")
    print(f"Copied {copied}, unchanged {unchanged}, kept existing {kept} file(s).")
    print("Reopen the world, or run /ftbquests reload quests, then /system ftb reload.")


if __name__ == "__main__":
    main()
