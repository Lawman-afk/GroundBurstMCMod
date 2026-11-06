"""Sums a flight recording's CPU samples (DESIGN.md §11.8): by thread, and on the threads that matter by the mod's code.

    python tools/bench/jfr_summary.py run/jfr/<name>.jfr [--threads "main,Server thread"] [--from HH:MM:SS] [--to HH:MM:SS]
           [--top 25] [--out report.txt]

For each thread asked for (the render thread is "main" on 26.x): its samples, the share with the mod's code anywhere on
the stack, those samples split by the mod's outermost frame (where the game called into the mod) and by its innermost
(the mod's own frame nearest the top: its own work, or what it called), and the hottest frames overall (the top of the
stack), so the game's own costs show beside the mod's. --from/--to keep only samples between two wall-clock times (the
bench's log gives the burst's).
"""
import argparse
import collections
import os
import re
import subprocess
import sys

MOD = "dev.groundburst."
JFR = os.path.join(os.environ.get("USERPROFILE", ""), "curseforge", "minecraft", "Install", "runtime", "java-runtime-epsilon",
                   "windows-x64", "java-runtime-epsilon", "bin", "jfr.exe")


def frames_of(lines):
    """The frames of one stack, top first, as 'Class.method' (line numbers dropped)."""
    out = []
    for line in lines:
        line = line.strip()
        if not line or line in ("...", "]"):
            continue
        m = re.match(r"([\w$.<>/]+)\(", line)
        out.append(m.group(1) if m else line)
    return out


def samples(path, depth):
    """Yields (time, thread, frames) for each jdk.ExecutionSample in the recording."""
    cmd = [JFR if os.path.exists(JFR) else "jfr", "print", "--events", "jdk.ExecutionSample", "--stack-depth", str(depth), path]
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
    time = thread = None
    stack, in_stack = [], False
    for line in proc.stdout:
        s = line.strip()
        if s.startswith("jdk.ExecutionSample"):
            time, thread, stack, in_stack = None, None, [], False
        elif s.startswith("startTime ="):
            m = re.search(r"(\d\d:\d\d:\d\d)", s)
            time = m.group(1) if m else None
        elif s.startswith("sampledThread ="):
            m = re.search(r'"(.*?)"', s)
            thread = m.group(1) if m else s
        elif s.startswith("stackTrace = ["):
            in_stack = True
        elif in_stack and s == "]":
            in_stack = False
            yield time, thread, frames_of(stack)
        elif in_stack:
            stack.append(s)
    proc.wait()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("jfr")
    ap.add_argument("--threads", default="main,Server thread")
    ap.add_argument("--from", dest="start", default=None)
    ap.add_argument("--to", dest="end", default=None)
    ap.add_argument("--top", type=int, default=25)
    ap.add_argument("--depth", type=int, default=64)
    ap.add_argument("--out", default=None)
    ap.add_argument("--callers", default="", help="comma-separated frames: how each is reached (its callers, nearest first)")
    a = ap.parse_args()
    callers = [c.strip() for c in a.callers.split(",") if c.strip()]
    paths = collections.defaultdict(collections.Counter)
    wanted = [t.strip() for t in a.threads.split(",") if t.strip()]
    by_thread = collections.Counter()
    total = collections.Counter()
    with_mod = collections.Counter()
    outer = collections.defaultdict(collections.Counter)
    inner = collections.defaultdict(collections.Counter)
    top = collections.defaultdict(collections.Counter)
    for time, thread, frames in samples(a.jfr, a.depth):
        if a.start and time and time < a.start or a.end and time and time > a.end:
            continue
        by_thread[thread] += 1
        if thread not in wanted:
            continue
        total[thread] += 1
        if frames:
            top[thread][frames[0]] += 1
            for c in callers:
                if frames[0] == c:
                    # Up to the first of the mod's frames (or six frames), nearest first.
                    up = []
                    for f in frames[1:]:
                        up.append(f)
                        if f.startswith(MOD) or len(up) >= 6:
                            break
                    paths[c][" <- ".join(up)] += 1
        mod = [f for f in frames if f.startswith(MOD)]
        if mod:
            with_mod[thread] += 1
            outer[thread][mod[-1]] += 1
            inner[thread][mod[0]] += 1
    lines = [f"Recording {a.jfr}" + (f", {a.start} to {a.end}" if a.start or a.end else "")]
    lines.append("Samples by thread: " + ", ".join(f"{t} {n}" for t, n in by_thread.most_common(12)))
    for t in wanted:
        n = total[t]
        if not n:
            lines.append(f"\n== {t}: no samples")
            continue
        lines.append(f"\n== {t}: {n} samples; the mod's code on the stack in {with_mod[t]} ({100.0 * with_mod[t] / n:.1f}%)")
        lines.append("  The mod's outermost frame (where the game called in):")
        for f, c in outer[t].most_common(a.top):
            lines.append(f"    {100.0 * c / n:5.1f}%  {c:6d}  {f}")
        lines.append("  The mod's innermost frame (its own work, or what it called):")
        for f, c in inner[t].most_common(a.top):
            lines.append(f"    {100.0 * c / n:5.1f}%  {c:6d}  {f}")
        lines.append("  Hottest frames (the top of the stack, the game's and the mod's):")
        for f, c in top[t].most_common(a.top):
            lines.append(f"    {100.0 * c / n:5.1f}%  {c:6d}  {f}")
    for c in callers:
        lines.append(f"\n== How {c} is reached (on the threads asked for):")
        for p, n in paths[c].most_common(a.top):
            lines.append(f"  {n:6d}  {p}")
    text = "\n".join(lines) + "\n"
    if a.out:
        with open(a.out, "w", encoding="utf-8") as fh:
            fh.write(text)
    sys.stdout.write(text)


if __name__ == "__main__":
    main()
