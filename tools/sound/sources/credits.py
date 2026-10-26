"""Writes the sound credits shipped in the mod (assets/groundzero/sounds/CREDITS.txt) from ledger.json: every recording
a sound is made from, its author, title, source and license. CC BY 4.0 asks for exactly this (creator, title, source,
license, and that it was changed); CC0 asks for nothing, but its authors are thanked too.

    tools\\.venv\\Scripts\\python.exe tools\\sound\\sources\\credits.py
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parents[1]))
from gz import paths  # noqa: E402

LICENSES = {"CC0-1.0": ("CC0 1.0 (public domain)", "https://creativecommons.org/publicdomain/zero/1.0/"),
            "CC-BY-4.0": ("CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/")}


def used() -> set[str]:
    """The recordings the sound recipes actually cut from (only those are credited)."""
    sys.path.insert(0, str(HERE.parent))
    import blasts  # noqa: PLC0415
    import gear  # noqa: PLC0415
    import launches  # noqa: PLC0415
    import machines  # noqa: PLC0415
    import ordnance  # noqa: PLC0415
    return {cut[0] for module in (blasts, launches, ordnance, machines, gear) for cut in module.CUTS.values()}


def main() -> None:
    ledger = json.loads((HERE / "ledger.json").read_text(encoding="utf-8"))
    keys = used()
    ledger["sources"] = [s for s in ledger["sources"] if s["key"] in keys]
    lines = ["Ground Zero: sound credits", "",
             "Some of the mod's sounds are made from these recordings. Each was changed: cut, cleaned of background noise,",
             "filtered, slowed or layered with others and with synthesized sound.", ""]
    for lic in ("CC-BY-4.0", "CC0-1.0"):
        rows = [s for s in ledger["sources"] if s["license"] == lic]
        if not rows:
            continue
        name, url = LICENSES[lic]
        lines.append(f"Licensed {name} ({url}):")
        for s in sorted(rows, key=lambda r: r["author"].lower()):
            lines.append(f'  "{s["title"]}" by {s["author"]}, {s["page"]}')
        lines.append("")
    out = paths.SOUNDS / "CREDITS.txt"
    out.write_text("\n".join(lines), encoding="utf-8")
    print(out)


if __name__ == "__main__":
    main()
