"""Downloads the recordings in ledger.json that are not in raw/ yet (raw/ is not in git: run this after a fresh clone).

    tools\\.venv\\Scripts\\python.exe tools\\sound\\sources\\fetch.py
"""
from __future__ import annotations

import json
import time
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
RAW = HERE / "raw"


def main() -> None:
    RAW.mkdir(exist_ok=True)
    ledger = json.loads((HERE / "ledger.json").read_text(encoding="utf-8"))
    for s in ledger["sources"]:
        out = RAW / (s["key"] + Path(s["file"]).suffix)
        if out.exists():
            continue
        req = urllib.request.Request(s["file"], headers={"User-Agent": "Mozilla/5.0 (Ground Burst sound tools)"})
        with urllib.request.urlopen(req, timeout=60) as r:
            out.write_bytes(r.read())
        print(f"{out.name}: {out.stat().st_size / 1e6:.2f} MB ({s['license']}, {s['author']})")
        time.sleep(1.0)


if __name__ == "__main__":
    main()
