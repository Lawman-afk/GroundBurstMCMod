"""Sorts a javac error log into kinds: the missing packages, classes and methods, the bad overrides, per count."""
import collections
import re
import sys
from pathlib import Path

log = Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace").splitlines()
top = int(sys.argv[2]) if len(sys.argv) > 2 else 60
errs = []
for i, line in enumerate(log):
    m = re.match(r"\s*(.*\.java):(\d+): error: (.*)", line)
    if not m:
        continue
    path, ln, msg = m.group(1), int(m.group(2)), m.group(3)
    extra = []
    for j in range(i + 1, min(i + 6, len(log))):
        if re.match(r"\s*(.*\.java):(\d+): error:", log[j]):
            break
        extra.append(log[j].strip())
    errs.append((path, ln, msg, extra))

print(len(errs), "errors in", len({e[0] for e in errs}), "files")
kinds = collections.Counter()
for path, ln, msg, extra in errs:
    if msg.startswith("package ") and msg.endswith("does not exist"):
        kinds["pkg " + msg.split()[1]] += 1
    elif msg.startswith("cannot find symbol"):
        sym = next((x for x in extra if x.startswith("symbol:")), "symbol: ?")
        loc = next((x for x in extra if x.startswith("location:")), "")
        sym = sym.replace("symbol:", "").strip()
        if sym.startswith("method") or sym.startswith("variable"):
            ty = re.sub(r"location: (class|interface|variable \w+ of type) ", "", loc)
            kinds[f"sym {sym}  @ {ty}"] += 1
        else:
            kinds["sym " + sym] += 1
    elif "does not override or implement" in msg:
        kinds["override"] += 1
    elif "is not abstract and does not override" in msg:
        m = re.search(r"does not override abstract method (\S+)", msg)
        kinds["abstract " + (m.group(1) if m else msg)] += 1
    else:
        kinds[re.sub(r"\b[A-Z]\w*<[^>]*>", "T", msg)[:110]] += 1
for k, v in kinds.most_common(top):
    print(f"{v:5d}  {k}")
