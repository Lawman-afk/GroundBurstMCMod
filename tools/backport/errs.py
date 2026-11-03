"""Prints the compile errors (build/port-compile.txt) of the files whose path contains any of the given words, each
with its source line: for working through a file by hand.

    tools\\.venv\\Scripts\\python.exe tools\\backport\\errs.py GBArmor ProcessingRecipe
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
log = (ROOT / "build" / "port-compile.txt").read_text(encoding="utf-8", errors="replace").splitlines()
words = sys.argv[1:]
seen = set()
for i, line in enumerate(log):
    m = re.match(r"\s*(.*\.java):(\d+): error: (.*)", line)
    if not m or not any(w in m.group(1) for w in words):
        continue
    key = (m.group(1), m.group(2), m.group(3))
    if key in seen:
        continue
    seen.add(key)
    name = m.group(1).replace("\\", "/").split("dev/groundburst/")[-1]
    detail = []
    for j in range(i + 1, min(i + 5, len(log))):
        t = log[j].strip()
        if re.match(r".*\.java:\d+: error:", t) or t.startswith("^"):
            break
        if t.startswith("symbol:") or t.startswith("location:") or t.startswith("required:") or t.startswith("found:"):
            detail.append(t)
    src = ""
    try:
        src = Path(m.group(1)).read_text(encoding="utf-8").splitlines()[int(m.group(2)) - 1].strip()
    except Exception:
        pass
    print(f"{name}:{m.group(2)}: {m.group(3)}  {' | '.join(detail)}\n    {src[:180]}")
