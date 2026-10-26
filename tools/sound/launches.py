"""Launches and superweapons, remastered from real recordings (DESIGN.md §13.1).

The launch siren, a missile lighting on its pad, its engine in flight, a stage parting, a warhead roaring back down, the
silo's doors, the cruise launcher's boxes rising and its missiles firing and flying, the SCUD Storm's erectors and the
Nuclear Missile's roof and erector. Each keeps the length and the moments the game's animations are timed to (the silo
doors' drive from 0.45 to 3.65 s, the erectors' locks), made from recordings wherever the world has the sound: a real
air-raid siren tested in Prague, NASA's Space Shuttle launch, a rocket fired from a US Army vehicle, a fighter's low pass,
an electric sliding gate, hydraulic lifts and a forklift, a factory crane, heavy steel doors. Synthesis is left only for
what no recording gives: a small turbojet's compressor whine, a deep thump under the pad.

Every cut names its recording (sources/ledger.json) and where in it; the windows were picked from survey.py's sheets.
"""
from __future__ import annotations

import numpy as np

import sources as R
import synth as S

# (recording, from s, to s, the quiet stretch its background is measured on[, options: hp/lp Hz, fade s, gain dB])
CUTS = {
    "siren_up": ("siren_prague", 0.75, 6.35, None, {"fade": 0.05}),     # spinning up from 0.9 s to its howl, holding
    "siren_down": ("siren_prague", 37.9, 43.4, None, {"fade": 0.05}),   # ... and the start of its wind-down
    "shuttle_ignition": ("shuttle_launch", 0.0, 6.0, None),             # the Shuttle's engines lighting, the roar building
    "shuttle_roar": ("shuttle_launch", 22.0, 26.4, None, {"fade": 0.01}),  # a steady stretch of its roar and crackle
    "rocket": ("rocket_launch_gov", 0.5, 6.5, None, {"fade": 1.0}),     # a rocket fired from a vehicle: crack, roar falling away
    "maroon": ("maroon", 0.0, 3.5, None),                                # a signal pyrotechnic's bang and its long tail
    "clang": ("metal_clang", 0.12, 1.75, None),                          # struck sheet metal, ringing
    "flyby_roar": ("f18_flyby_low", 4.5, 10.0, None, {"fade": 0.01}),   # a fighter's roar at its loudest, low overhead
    "afterburner": ("afterburner", 0.4, 5.2, None, {"fade": 0.01}),     # a turbojet's steady roar
    "gate": ("sliding_gate", 6.1, 11.9, None),                           # an electric gate's motor and rolling steel
                                                                         # (after its lock's click and beeper at 5.8 s)
    "grind": ("metal_grinding", 1.0, 6.0, None),                         # steel scraping (slowed: a grinding drone)
    "slam": ("door_slam", 0.25, 2.96, None),                             # a big steel door slamming, its echo
    "door_clank": ("heavy_metal_door", 6.35, 7.8, None, {"hp": 30}),     # a latch's clack and a heavy door's ringing thud
    "door_clank2": ("heavy_metal_door", 20.35, 21.8, None, {"hp": 30}),  # ... the same door again
    "pump": ("scissor_lifts", 21.45, 25.0, None, {"hp": 60}),            # an electro-hydraulic pump spinning up and running
    "rams": ("forklift_hydraulics", 4.55, 6.25, None, {"hp": 80}),       # hydraulics under load, their whine bending
    "truck": ("truck_idle", 0.2, 4.2, None),                             # a heavy diesel truck idling
    "crane": ("overhead_crane", 52.8, 57.0, None, {"hp": 60}),           # a factory crane's motors running up
}


def take(name: str, speed: float = 1.0, aligned: bool = False) -> np.ndarray:
    return R.take(CUTS, name, speed, aligned)


def at(x: np.ndarray, db: float) -> np.ndarray:
    """{x} at a loudness (RMS, dBFS): layers are set against each other by how loud they are, not by their peaks."""
    return R.match(x, db)


def span(x: np.ndarray, seconds: float, fade_in: float = 0.0, fade_out: float = 0.15) -> np.ndarray:
    """{x} cut to {seconds}, faded in and out (raised cosine) so it starts and stops where the animation does."""
    y = x[: int(seconds * S.SR)]
    return R.tail_fade(S.fade(y, fade_in, 0.0), fade_out)


def thump(length: float, cutoff: float, decay: float, seed: int) -> np.ndarray:
    """A deep thump (low noise, decaying): the ground's answer to something heavy."""
    return S.lowpass(S.white(length, seed), cutoff) * S.exp_decay(int(length * S.SR), decay)


def siren() -> np.ndarray:
    """A real air-raid siren (a test in Prague) for the whole 10 s countdown: spinning up to its howl, holding, and the
    start of its wind-down, fading as the countdown ends."""
    up = R.tail_fade(take("siren_up"), 0.5)
    down = S.fade(take("siren_down"), 0.5, 0.0)
    x = R.layer(10.0, (up, 0.0, 1.0), (down, 5.1, 1.0))
    x = S.reverb(R.tail_fade(x, 2.0), 1.5, 3000, 0.12, seed=650)[: len(x)]
    return R.finish(x, peak_db=-2.0, tail=0.3)


def missile_launch() -> np.ndarray:
    """A missile lighting on its pad (5 s): the Shuttle's main engines starting and the roar building, a rocket's sharp
    ignition crack on top, and the pad's deep thump under it."""
    crack = R.tail_fade(take("rocket", aligned=True)[: int(0.5 * S.SR)], 0.2)
    x = R.layer(5.0, (at(take("shuttle_ignition"), -16), 0.0, 1.0), (at(crack, -19), 0.15, 1.0),
                (at(thump(1.5, 140, 0.3, 610), -21), 0.15, 1.0))
    return R.finish(R.punch(x, 2), tail=0.8)


def missile_engine_loop() -> np.ndarray:
    """A missile's engine in flight: a seamless 4 s loop (the client follows the missile with it and pitches it by its
    speed toward the listener) of the Shuttle's steady roar and crackle."""
    return R.seamless(R.loud_as(take("shuttle_roar"), LOUDNESS["launch/missile_engine_loop"]), 0.4)


def stage_separation() -> np.ndarray:
    """A stage parting, high overhead (1.8 s): the separation bolts' charge (a pyrotechnic's bang), the stages' metal
    clanging apart (struck steel, slowed: bigger), the stage's gas venting and a dull thump."""
    vent = S.bandpass(take("rocket", aligned=True)[int(0.4 * S.SR): int(1.9 * S.SR)], 300, 3000)
    vent = R.tail_fade(S.fade(vent, 0.05, 0.0), 1.0)
    x = R.layer(1.8, (at(take("maroon", aligned=True), -15), 0.0, 1.0), (at(take("clang", 0.8, aligned=True), -18), 0.02, 1.0),
                (at(vent, -25), 0.04, 1.0), (at(thump(0.8, 160, 0.1, 634), -22), 0.0, 1.0))
    x = S.reverb(x, 1.4, 2500, 0.3, seed=635)[: len(x)]
    return R.finish(x, tail=0.3)


def reentry_loop() -> np.ndarray:
    """A warhead roaring back down: a seamless 4 s loop of a fighter's roar at its loudest, played faster (a higher,
    tearing scream: a warhead is smaller and far faster than a jet)."""
    x = S.highpass(take("flyby_roar", 1.15)[: int(4.4 * S.SR)], 120, 2)
    return R.seamless(R.loud_as(x, LOUDNESS["launch/reentry_loop"]), 0.4)


def silo_doors() -> np.ndarray:
    """The Missile Silo's doors (4.3 s): their bolts drawing back (struck steel, slowed: heavy bolts), then from 0.45 to
    3.65 s the drive's motor and the leaves rolling on their rails (an electric gate's, slowed for doors this size) with
    the grind of steel on steel, and the leaves banging into their stops (a big steel door's slam), in the shaft's echo."""
    drive = span(take("gate", 0.8), 3.2, 0.15, 0.15)
    grind = span(take("grind", 0.8), 3.2, 0.3, 0.3)
    x = R.layer(4.3, (at(take("clang", 0.6, aligned=True), -23), 0.05, 1.0), (at(take("clang", 0.55, aligned=True), -25), 0.22, 1.0),
                (at(drive, -16), 0.45, 1.0), (at(grind, -26), 0.45, 1.0), (at(take("slam", 0.9, aligned=True), -15), 3.62, 1.0))
    x = S.reverb(x, 1.2, 2500, 0.25, seed=660)[: len(x)]
    return R.finish(x, peak_db=-2.0, tail=0.25)


def cruise_launch() -> np.ndarray:
    """A cruise missile out of its box (4 s): the frangible cover's charge cracking and the cover banging open, then the
    booster's roar (a rocket fired from a vehicle) building at once and falling away as it climbs out."""
    crack = R.tail_fade(take("maroon", aligned=True)[: int(0.4 * S.SR)], 0.25)
    x = R.layer(4.0, (at(crack, -18), 0.0, 1.0), (at(take("clang", 1.1, aligned=True), -21), 0.01, 1.0),
                (at(take("rocket", aligned=True), -15), 0.03, 1.0))
    return R.finish(R.punch(x, 2), tail=0.4)


def cruise_engine_loop() -> np.ndarray:
    """A cruise missile's jet: a seamless 4 s loop of a turbojet's steady roar, with its compressor's whine riding on it
    (synthesized: no recording has it clean; its tones and its slow wobble make whole cycles in the 4 s loop, so the seam
    does not show)."""
    n = 4.4
    t = S.t_axis(n)
    f = 3150.0 * (1 + 0.004 * np.sin(2 * np.pi * 0.25 * t))
    phase = 2 * np.pi * np.cumsum(f) / S.SR
    whine = np.sin(phase) + 0.45 * np.sin(1.5 * phase)
    x = at(take("afterburner")[: int(n * S.SR)], -18) + at(whine, -33)
    return R.seamless(R.loud_as(x, LOUDNESS["launch/cruise_engine_loop"]), 0.4)


def cruise_elevate() -> np.ndarray:
    """The Cruise Missile Launcher's boxes rising on their rams (3.2 s; played lower as they come down): the hydraulic
    pump spinning up and running from 0.15 to 2.85 s, the rams' whine bending as they push, and the boxes locking at 45
    degrees at 2.95 s (a latch's clack and a heavy thud)."""
    pump = span(take("pump"), 2.8, 0.0, 0.15)
    x = R.layer(3.2, (at(pump, -20), 0.1, 1.0), (at(take("rams"), -26), 0.6, 1.0), (at(take("door_clank", aligned=True), -19), 2.94, 1.0))
    x = S.reverb(x, 0.7, 3000, 0.16, seed=944)[: len(x)]
    return R.finish(x, peak_db=-2.0, tail=0.2)


def scud_erect() -> np.ndarray:
    """The SCUD Storm's erectors raising their missiles (3.6 s): the launcher trucks' diesels idling, the hydraulic pumps
    spinning up and running to 3 s, the rams' whine, heavier, and the locks clanking home at 3.02 and 3.14 s."""
    engine = span(take("truck"), 3.6, 0.1, 0.3)
    pump = span(take("pump", 0.9), 2.9, 0.0, 0.15)
    x = R.layer(3.6, (at(engine, -27), 0.0, 1.0), (at(pump, -20), 0.15, 1.0), (at(take("rams", 0.85), -25), 0.8, 1.0),
                (at(take("door_clank2", aligned=True), -17), 3.02, 1.0), (at(take("door_clank", 1.1, aligned=True), -19), 3.14, 1.0))
    x = S.reverb(x, 0.8, 3000, 0.18, seed=915)[: len(x)]
    return R.finish(x, peak_db=-2.0, tail=0.2)


def nuclear_erect() -> np.ndarray:
    """The Nuclear Missile Silo (6.8 s): the roof's motors rolling its four sections open (the electric gate, slowed far
    down: heavy), each clanking home at its stop (1.75, 1.9, 2.05, 2.2 s), then the erector's motors and pumps running up
    and holding while the missile is raised (a factory crane, a hydraulic pump, slowed), its lock slamming home at 5.5 s
    and the arm's latch at 6.45 s."""
    roof = span(take("gate", 0.7), 1.95, 0.1, 0.15)
    grind = span(take("grind", 0.7), 1.9, 0.2, 0.2)
    stops = [(at(take(name, speed, aligned=True), db), when, 1.0)
             for name, speed, db, when in (("door_clank", 1.0, -17, 1.75), ("door_clank2", 1.0, -18, 1.9),
                                           ("door_clank", 1.05, -19, 2.05), ("door_clank2", 0.95, -20, 2.2))]
    crane = span(take("crane"), 3.9, 0.2, 0.3)
    pump = span(take("pump", 0.8), 3.8, 0.0, 0.3)
    x = R.layer(6.8, (at(roof, -17), 0.1, 1.0), (at(grind, -26), 0.15, 1.0), *stops, (at(crane, -21), 1.6, 1.0),
                (at(pump, -20), 1.65, 1.0), (at(take("slam", aligned=True), -16), 5.5, 1.0),
                (at(take("clang", 1.3, aligned=True), -26), 6.45, 1.0))
    x = S.reverb(x, 1.0, 2800, 0.2, seed=924)[: len(x)]
    return R.finish(x, peak_db=-2.0, tail=0.25)


#: How loud each is (its loudest 3 s, LUFS): as loud as the synthesized sound it replaces, whose in-game volume was tuned
#: to it (the loops are set before their seam is closed).
LOUDNESS = {"launch/siren": -13.8, "launch/missile_launch": -8.6, "launch/missile_engine_loop": -15.7,
            "launch/stage_separation": -22.8, "launch/reentry_loop": -14.5, "launch/silo_doors": -11.5,
            "launch/cruise_launch": -13.8, "launch/cruise_engine_loop": -13.1, "launch/cruise_elevate": -15.5,
            "superweapon/scud_erect": -16.4, "superweapon/nuclear_erect": -16.3}
LOOPS = ("launch/missile_engine_loop", "launch/reentry_loop", "launch/cruise_engine_loop")


def launch_set() -> dict[str, np.ndarray]:
    """Every launch and superweapon sound made from recordings (the superweapons' warning and ready tones stay
    synthesized: recipes.superweapon_sounds), each as loud as the one it replaces."""
    made = {"launch/siren": siren(), "launch/missile_launch": missile_launch(), "launch/missile_engine_loop": missile_engine_loop(),
            "launch/stage_separation": stage_separation(), "launch/reentry_loop": reentry_loop(), "launch/silo_doors": silo_doors(),
            "launch/cruise_launch": cruise_launch(), "launch/cruise_engine_loop": cruise_engine_loop(),
            "launch/cruise_elevate": cruise_elevate(), "superweapon/scud_erect": scud_erect(),
            "superweapon/nuclear_erect": nuclear_erect()}
    return {rel: x if rel in LOOPS else R.loud_as(x, LOUDNESS[rel]) for rel, x in made.items()}


if __name__ == "__main__":
    import sys
    from pathlib import Path
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from gz import paths  # noqa: E402
    for rel, x in launch_set().items():
        S.save_ogg(x, paths.SOUNDS / f"{rel}.ogg")
        print(f"{rel}: {len(x) / S.SR:.2f} s")
