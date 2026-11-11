"""Checks a release jar in a real Forge 1.20.1 client, outside the dev runs (docs/BACKPORT.md): the Forge the CurseForge app
installed (its version files, libraries and assets), a throwaway game folder holding the jar and the mods given,
offline, muted, its window behind the others and the mouse left alone, and one of the checks the jar carries switched
on (the port check or the site check); it quits when the check is done. Its log is the game folder's logs/latest.log,
its pictures in screenshots/.

    python tools/backport/prod_client.py <game dir> <release jar> <check: portCheck|siteCheck> [<mod jar> ...]

The game folder is made afresh each time. GeckoLib (4.8.4 or later for Forge 1.20.1) must be among the mods.
"""
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

INSTALL = Path(os.path.expanduser("~")) / "curseforge" / "minecraft" / "Install"
FORGE = "forge-47.4.28"
LIBS = INSTALL / "libraries"


def java17() -> Path:
    for d in sorted((Path(os.path.expanduser("~")) / ".gradle" / "jdks").glob("eclipse_adoptium-17-amd64-windows*")):
        if (d / "bin" / "java.exe").exists():
            return d / "bin" / "java.exe"
    raise SystemExit("no JDK 17 in ~/.gradle/jdks (a Gradle build of this branch fetches one)")


def allowed(rules) -> bool:
    """A library's or an argument's rules, for Windows on x86-64 with no launcher features."""
    if not rules:
        return True
    ok = False
    for r in rules:
        os_rule = r.get("os")
        match = "features" not in r and (os_rule is None or (os_rule.get("name") in (None, "windows")
                                                             and os_rule.get("arch") in (None, "x86_64", "amd64")))
        if match:
            ok = r["action"] == "allow"
    return ok


def lib_path(lib) -> Path:
    art = lib.get("downloads", {}).get("artifact")
    if art and art.get("path"):
        return LIBS / art["path"]
    group, name, version, *rest = lib["name"].split(":")
    classifier = ("-" + rest[0]) if rest else ""
    return LIBS / group.replace(".", "/") / name / version / f"{name}-{version}{classifier}.jar"


def args_of(entries, subs) -> list[str]:
    out = []
    for e in entries:
        if isinstance(e, str):
            out.append(e)
        elif allowed(e.get("rules")):
            out.extend(e["value"] if isinstance(e["value"], list) else [e["value"]])
    for k, v in subs.items():
        out = [a.replace("${" + k + "}", v) for a in out]
    return out


def main():
    game, jar, check, mods = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3], [Path(m) for m in sys.argv[4:]]
    if game.exists():
        shutil.rmtree(game)
    (game / "mods").mkdir(parents=True)
    (game / "config").mkdir()
    for m in [jar, *mods]:
        shutil.copy2(m, game / "mods" / m.name)
    (game / "options.txt").write_text("soundCategory_master:0.0\nonboardAccessibility:false\npauseOnLostFocus:false\n", encoding="utf-8")
    (game / "config" / "fml.toml").write_text("earlyWindowControl = false\n", encoding="utf-8")   # (made before any mod: it would take the focus)

    forge = json.loads((INSTALL / "versions" / FORGE / f"{FORGE}.json").read_text(encoding="utf-8"))
    vanilla = json.loads((INSTALL / "versions" / forge["inheritsFrom"] / f"{forge['inheritsFrom']}.json").read_text(encoding="utf-8"))
    cp, seen = [], set()
    for lib in forge["libraries"] + vanilla["libraries"]:      # Forge's first: its newer libraries win
        key = lib["name"].rsplit(":", 1)[0] if lib["name"].count(":") == 2 else lib["name"]
        if allowed(lib.get("rules")) and key not in seen and lib_path(lib).exists():
            seen.add(key)
            cp.append(str(lib_path(lib)))
    subs = {
        "natives_directory": str(game / "natives"), "launcher_name": "prod_client", "launcher_version": "1",
        "classpath": os.pathsep.join(cp), "classpath_separator": os.pathsep, "library_directory": str(LIBS),
        "version_name": FORGE, "auth_player_name": "Dev", "game_directory": str(game), "assets_root": str(INSTALL / "assets"),
        "assets_index_name": vanilla["assetIndex"]["id"], "auth_uuid": "00000000000000000000000000000000",
        "auth_access_token": "0", "clientid": "0", "auth_xuid": "0", "user_type": "legacy", "version_type": "release",
    }
    (game / "natives").mkdir()
    jvm = ["-Xmx4G", f"-Dgroundburst.{check}=true", "-Dgroundburst.keepMouse=true"]
    jvm += args_of(forge["arguments"]["jvm"], subs) + args_of(vanilla["arguments"]["jvm"], subs)
    game_args = args_of(vanilla["arguments"]["game"], subs) + args_of(forge["arguments"]["game"], subs) + ["--width", "1280", "--height", "720"]
    with open(game / "launch.log", "w", encoding="utf-8") as out:
        r = subprocess.run([str(java17()), *jvm, forge["mainClass"], *game_args], cwd=game, stdout=out, stderr=subprocess.STDOUT)
    print("exit", r.returncode, "- log:", game / "logs" / "latest.log")


main()
