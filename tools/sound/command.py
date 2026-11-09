"""The war room's sounds (DESIGN.md §17.17), made from real recordings (sources/ledger.json, all credited) where the thing
is real, the display's tone synthesized as the station's are:

- machine/seismograph_loop: the Seismograph listening: its drum's motor humming (a plotter idling, softened) and the
  pen's faint whisper on the paper (a pen on paper, slowed), a steady loop of fitted length (machines.Cycle).
- machine/seismograph_event: a blast recorded: the event relay's click and the pen thrashing across the drum (a pen
  plotter hatching, and a pen scrawling), strong at first and dying away as a seismogram's coda does.
- launch/war_room_contact: a new contact on the War Room Display: the board's annunciator relay and its tone, two
  notes rising, twice, from the panel's small speaker in the room.
- launch/dead_hand_alert: the Dead Hand's alert, played each second of its countdown: a Bell System key telephone's
  buzzer (Western Electric KS-20419L1), one buzz of half a second.
- launch/dead_hand_lid: the launch tube's lid thrown open (2 s, as the model's lid swings): the hydraulic pump spinning
  up, the rams' whine bending, the hinges groaning, and the lid's clank onto its stop as the rocket goes.

    tools\\.venv\\Scripts\\python.exe tools\\sound\\command.py
"""
from __future__ import annotations

import numpy as np

import machines as MC
import sources as R
import synth as S

# (recording, from s, to s, the quiet stretch its background is measured on[, options: hp/lp Hz, fade s, gain dB])
CUTS = {
    "hum": ("plotter_startup", 24.0, 31.6, None, {"hp": 60, "lp": 3200, "fade": 0.05}),  # a plotter idling: the drum's motor
    "whisper": ("pen_on_paper", 0.3, 3.6, None, {"hp": 400, "lp": 5000}),           # a pen on paper: the trace (slowed)
    "relay": ("switches", 0.40, 0.62, None, {"hp": 150, "fade": 0.08}),             # a switch thrown: the event relay
    "relay2": ("switches", 0.82, 1.05, None, {"hp": 150, "fade": 0.08}),            # ... and the board's annunciator
    "thrash": ("pen_plotter", 60.25, 62.8, None, {"hp": 150, "lp": 7000}),          # a pen plotter hatching: the pen's swings
    "scrawl": ("pen_on_paper", 0.2, 2.3, None, {"hp": 300}),                        # a pen scrawling fast on paper
    "buzz": ("telephone_buzzer", 0.52, 1.2, (1.3, 2.4), {"hp": 160, "fade": 0.06}),  # one buzz of a telephone's buzzer
    "pump": ("scissor_lifts", 21.45, 25.0, None, {"hp": 60}),                       # an electro-hydraulic pump running up
    "rams": ("forklift_hydraulics", 4.55, 6.25, None, {"hp": 80}),                  # hydraulics under load, the whine bending
    "creak": ("heavy_metal_door", 1.0, 2.95, None, {"hp": 60, "lp": 2600}),          # a big steel door's hinges
    "clank": ("heavy_metal_door", 6.35, 7.8, None, {"hp": 30}),                     # a latch's clack and a heavy door's thud
}

#: Each sound's loudness (its loudest 3 s, LUFS): the Seismograph as quiet as the RTG (an instrument in a room); its
#: event and the display's tone below the station's (a console's sounds); the Dead Hand's buzzer and lid as loud as the
#: station's launch (an alarm, a heavy lid).
LOUDNESS = {"machine/seismograph_loop": -32.0, "machine/seismograph_event": -24.0, "launch/war_room_contact": -21.0,
            "launch/dead_hand_alert": -18.0, "launch/dead_hand_lid": -17.0}


def take(name: str, speed: float = 1.0, aligned: bool = False) -> np.ndarray:
    return R.take(CUTS, name, speed, aligned)


def shaped(x: np.ndarray, points: list[tuple[float, float]]) -> np.ndarray:
    """{x} under a gain envelope through (seconds, gain) points."""
    return x * S.breakpoints(len(x), points)


def span(x: np.ndarray, seconds: float, fade_in: float = 0.0, fade_out: float = 0.15) -> np.ndarray:
    """{x} cut to {seconds}, faded in and out so it starts and stops where the animation does."""
    return R.tail_fade(S.fade(x[: int(seconds * S.SR)], fade_in, 0.0), fade_out)


def room(x: np.ndarray, seconds: float = 0.4, mix: float = 0.1, seed: int = 960) -> np.ndarray:
    """A small room's answer (a bunker's concrete), the sound's length kept."""
    return S.reverb(x, seconds=seconds, damping_hz=3800, mix=mix, seed=seed)[: len(x)]


def seismograph_loop() -> np.ndarray:
    """The drum's motor (a plotter idling, its hiss taken off) and the pen's whisper under it (a pen's strokes slowed
    to a soft scratching), closed into a seamless loop of about 4 s, its length fitted to the motor's hum."""
    cycle = MC.Cycle(MC.STEADY, MC.FIT)
    cycle.lay(take("hum"), -30.0)
    cycle.lay(take("whisper", speed=0.62), -43.0)
    return cycle.close()


def seismograph_event() -> np.ndarray:
    """A blast recorded: the relay's click as the event trips, then the pen thrashing across the paper: its swings
    (a plotter's pen hatching) and its scratch (a pen scrawling, quickened), loud at the first arrivals and dying away
    over two seconds as the shaking's coda does."""
    total = 2.5
    coda = [(0, 0), (0.03, 1), (0.45, 0.9), (1.1, 0.45), (1.9, 0.12), (2.3, 0)]
    thrash = R.match(shaped(take("thrash", speed=1.1), coda), -25)
    scrawl = R.match(shaped(take("scrawl", speed=1.25), coda[:-1] + [(1.6, 0)]), -29)
    relay = R.match(take("relay", aligned=True), -24)
    x = R.layer(total, (relay, 0.0, 1.0), (thrash, 0.06, 1.0), (scrawl, 0.07, 1.0))
    return R.finish(room(x, 0.35, 0.08, 961), -3.0, tail=0.3)


def tones(total: float, notes: list[tuple[float, float, float]]) -> np.ndarray:
    """Square-ish tones from a small speaker: (start s, length s, Hz), each with soft 5 ms edges."""
    n = int(total * S.SR)
    f = np.zeros(n)
    env = np.zeros(n)
    edge = int(0.005 * S.SR)
    for a, length, hz in notes:
        i0, i1 = int(a * S.SR), int((a + length) * S.SR)
        f[i0:i1] = hz
        seg = np.ones(i1 - i0)
        seg[:edge] *= np.linspace(0, 1, edge)
        seg[-edge:] *= np.linspace(1, 0, edge)
        env[i0:i1] = seg
    phase = 2 * np.pi * np.cumsum(f) / S.SR
    wave = sum(np.sin(k * phase) / k * (k * f < 0.45 * S.SR) for k in (1, 3, 5, 7))
    return S.bandpass(S.saturate(wave * env * 0.5, 1.4), 380, 4000)


def war_room_contact() -> np.ndarray:
    """A new contact on the board: the annunciator's relay, then its tone, two notes rising (660 then 990 Hz, 0.11 s
    each), twice, in the room."""
    total = 0.95
    relay = R.match(take("relay2", aligned=True), -27)
    tone = R.match(tones(total, [(0.06, 0.11, 660.0), (0.18, 0.11, 990.0), (0.36, 0.11, 660.0), (0.48, 0.11, 990.0)]), -23)
    x = R.layer(total, (relay, 0.0, 1.0), (tone, 0.0, 1.0))
    return R.finish(room(x, 0.45, 0.14, 962), -3.0, tail=0.25)


def dead_hand_alert() -> np.ndarray:
    """One buzz of the console's buzzer (a key telephone's, recorded): half a second, played each second of the
    countdown (quicker in its last ten), with the small room's answer."""
    buzz = R.match(take("buzz", aligned=True), -20)
    x = R.layer(0.8, (buzz, 0.0, 1.0))
    return R.finish(room(x, 0.3, 0.08, 963), -2.0, tail=0.12)


def dead_hand_lid() -> np.ndarray:
    """The tube's lid thrown open in 2 s (the model eases it out and in): the hydraulic pump spinning up and running,
    the rams' whine bending as they push, the hinges groaning as it swings, and its clank onto the stop at 2.0 s, out
    in the open (a short echo off the concrete)."""
    pump = span(take("pump"), 2.1, 0.0, 0.2)
    rams = shaped(take("rams"), [(0, 0), (0.1, 1), (1.45, 1), (1.7, 0)])
    creak = shaped(take("creak", speed=0.9), [(0, 0), (0.25, 1), (1.4, 0.8), (1.75, 0)])
    x = R.layer(2.9, (R.match(pump, -21), 0.0, 1.0), (R.match(rams, -26), 0.25, 1.0), (R.match(creak, -27), 0.2, 1.0),
                (R.match(take("clank", aligned=True), -18), 1.97, 1.0))
    x = S.reverb(x, 0.6, 3000, 0.12, seed=964)[: len(x)]
    return R.finish(x, -2.0, tail=0.3)


def command_set() -> dict[str, np.ndarray]:
    """Every war-room sound, at its loudness."""
    made = {"machine/seismograph_loop": seismograph_loop(), "machine/seismograph_event": seismograph_event(),
            "launch/war_room_contact": war_room_contact(), "launch/dead_hand_alert": dead_hand_alert(),
            "launch/dead_hand_lid": dead_hand_lid()}
    return {rel: R.loud_as(x, LOUDNESS[rel]) for rel, x in made.items()}


if __name__ == "__main__":
    import sys
    from pathlib import Path
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from gb import paths  # noqa: E402
    for rel, x in command_set().items():
        S.save_ogg(x, paths.SOUNDS / f"{rel}.ogg")
        print(f"{rel}: {len(x) / S.SR:.2f} s, peak {20 * np.log10(np.max(np.abs(x)) + 1e-12):.1f} dB, rms {R.rms_db(x):.1f} dB")
