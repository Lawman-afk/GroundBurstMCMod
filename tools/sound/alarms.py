"""The base alarm system's sounds (DESIGN.md §18.6), synthesized from the signals the research found:

- alarm/siren_wail: a mechanical air-raid siren's Attack Warning, a loop of one 8 s wail: the motor's pitch rising and
  falling, two rotors a minor third apart (a dual-tone siren's ominous chord), chopped air with breath in it, a slap of
  echo off the surroundings. The game winds it up and down and swells it as the horn turns.
- alarm/siren_steady: the same siren held at its top: the Alert signal, and the All Clear.
- alarm/klaxon: the motor-driven diaphragm horn, a-OO-ga: its pitch sweeping up as the motor spins, then cut off.
- alarm/whoop: the slow whoop, 500 to 1200 Hz over 3 s and half a second's gap.
- alarm/evacuate: the temporal-three evacuation pattern (ISO 8201): three half-second pulses and a 1.5 s pause, a
  520 Hz square-wave horn (the low tone that wakes sleepers).
- alarm/hilo: the two-tone, 800 and 1000 Hz, half a second each.
- alarm/general: a ship's general alarm, seven short blasts and one long, on an alarm horn.
- alarm/bell: an electric trembler bell, its hammer striking the gong eighteen times a second.
- alarm/attention: the emergency broadcast attention tone (853 and 960 Hz together), twice, from a small speaker.

The loops are cut seamless; each is set to a loudness beside the game's other alarms (the launch siren, the reactor's).
"""
from __future__ import annotations

import numpy as np

import sources as R
import synth as S

#: Each sound's loudness (its loudest 3 s, LUFS): the siren as loud as the launch siren, the rest a little under.
LOUDNESS = {"alarm/siren_wail": -13.5, "alarm/siren_steady": -13.5, "alarm/klaxon": -16.0, "alarm/whoop": -17.5,
            "alarm/evacuate": -17.5, "alarm/hilo": -17.5, "alarm/general": -16.5, "alarm/bell": -17.0, "alarm/attention": -19.0}


def osc(f: np.ndarray, harmonics: list[tuple[int, float]], phase0: float = 0.0) -> np.ndarray:
    """A band-limited oscillator following the frequency curve {f} (Hz a sample): the harmonics given (number, level),
    each dropped where it would pass 0.45 of the sample rate (so a square or a saw does not fold back as stray tones)."""
    phase = 2 * np.pi * np.cumsum(f) / S.SR + phase0
    out = np.zeros_like(f)
    for k, a in harmonics:
        out += a * np.sin(k * phase) * (k * f < 0.45 * S.SR)
    return out


SQUARE = [(k, 1.0 / k) for k in range(1, 40, 2)]
SAW = [(k, 1.0 / k) for k in range(1, 40)]


def gate(n: int, spans: list[tuple[float, float]], edge: float = 0.008) -> np.ndarray:
    """An on/off envelope over {n} samples: on through each (start, end) in seconds, with soft edges (no clicks)."""
    env = np.zeros(n)
    e = int(edge * S.SR)
    for a, b in spans:
        i0, i1 = int(a * S.SR), min(n, int(b * S.SR))
        if i1 <= i0:
            continue
        seg = np.ones(i1 - i0)
        k = min(e, (i1 - i0) // 2)
        if k > 0:
            ramp = np.sin(np.linspace(0, np.pi / 2, k)) ** 2
            seg[:k] *= ramp
            seg[-k:] *= ramp[::-1]
        env[i0:i1] = np.maximum(env[i0:i1], seg)
    return env


def _rotors(f: np.ndarray, length: float, seed: int) -> np.ndarray:
    """A two-rotor siren's chopped air at {f}: two rotors a minor third apart, each a rounded buzzy tone with breath."""
    out = np.zeros_like(f)
    for ratio, gain, s in ((1.0, 1.0, seed), (1.2, 0.8, seed + 1)):
        ff = f * ratio
        tone = osc(ff, [(k, 1 / k ** 1.45) for k in range(1, 12)], 0.3 * s)
        phase = 2 * np.pi * np.cumsum(ff) / S.SR
        breath = S.bandpass(S.white(length, s), 700, 4000)[: len(f)] * (0.5 + 0.5 * np.sin(phase)) ** 8 * 0.3
        out += (S.saturate(tone * 0.45, 1.15) + breath) * gain
    return out


def _outdoors(x: np.ndarray) -> np.ndarray:
    x = S.echo(x, delay=0.21, feedback=0.3, repeats=3, lp_hz=2500)[: len(x)]
    return S.reverb(x, seconds=1.8, damping_hz=3500, mix=0.25)[: len(x)]


def siren_wail() -> np.ndarray:
    """One 8 s wail of the Attack Warning, as a seamless loop: rising for 3.5 s from a growl to the top, holding half a
    second, falling for 4 s."""
    cycle, pre = 8.0, 0.5
    length = pre + 2 * cycle + 0.5
    t = S.t_axis(length)
    u = ((t - pre) % cycle) / cycle * 8.0                          # seconds into its wail
    top, low = 440.0, 250.0
    rise = np.clip(u / 3.5, 0, 1)
    fall = np.clip((u - 4.0) / 4.0, 0, 1)
    s = np.where(u < 3.5, 0.5 - 0.5 * np.cos(np.pi * rise), np.where(u < 4.0, 1.0, 0.5 + 0.5 * np.cos(np.pi * fall)))
    f = (low + (top - low) * s) * (1 + 0.004 * np.sin(2 * np.pi * 0.7 * t))
    out = _rotors(f, length, 640) * (0.55 + 0.45 * s) ** 1.2     # louder as it spins up
    out = S.bandpass(out, 120, 6500)
    out = _outdoors(out)
    return S.seamless_loop(out, cycle, xfade=0.2, preroll=pre)


def siren_steady() -> np.ndarray:
    """The siren held at its top for a 6 s loop (the Alert, and the All Clear): the motor's slight hunting in it."""
    loop, pre = 6.0, 0.5
    length = pre + loop + 0.5
    t = S.t_axis(length)
    f = 440.0 * (1 + 0.004 * np.sin(2 * np.pi * 0.7 * t) + 0.002 * np.sin(2 * np.pi * 1.9 * t))
    out = S.bandpass(_rotors(f, length, 650), 120, 6500)
    out = _outdoors(out)
    return S.seamless_loop(out, loop, xfade=0.25, preroll=pre)


def klaxon() -> np.ndarray:
    """A-OO-ga: the horn's motor spinning its toothed wheel up against the diaphragm (pitch sweeping up from a growl),
    held a moment, then sagging as it is let go; the horn's flare giving it its honk. 0.8 s on, 0.6 s off."""
    loop = 1.4
    n = int(loop * S.SR)
    t = S.t_axis(loop)
    on = t < 0.8
    f = np.where(on, 150 + 260 * (1 - np.exp(-t / 0.16)), 150.0)
    f = f * np.where(t > 0.62, 1 - 0.3 * np.clip((t - 0.62) / 0.18, 0, 1) ** 1.5, 1.0)   # the "ga": released, it sags
    buzz = osc(f, SAW)
    env = gate(n, [(0.0, 0.8)], edge=0.02) * np.clip(t / 0.06, 0, 1)
    x = S.saturate(buzz * env * 0.7, 2.2)
    x = S.bandpass(x, 250, 5000)
    x = x + 0.6 * S.resonator(x, 950, 6) + 0.35 * S.resonator(x, 2300, 8)   # the horn's flare
    x = S.reverb(x, seconds=0.8, mix=0.18)[:n]
    return S.fade(x, 0.0, 0.02)


def whoop() -> np.ndarray:
    """The slow whoop: a square tone sweeping 500 to 1200 Hz over 3 s, then half a second's silence."""
    loop = 3.5
    n = int(loop * S.SR)
    t = S.t_axis(loop)
    f = 500 * (1200 / 500) ** np.clip(t / 3.0, 0, 1)
    x = osc(f, SQUARE[:8]) * gate(n, [(0.0, 3.0)], edge=0.01)
    x = S.bandpass(S.saturate(x * 0.6, 1.3), 300, 6000)
    return S.reverb(x, seconds=0.6, mix=0.12)[:n]


def evacuate() -> np.ndarray:
    """The temporal-three evacuation signal: three half-second pulses half a second apart, then 1.5 s quiet; a 520 Hz
    square-wave horn."""
    loop = 4.0
    n = int(loop * S.SR)
    f = np.full(n, 520.0)
    x = osc(f, SQUARE[:12]) * gate(n, [(0.0, 0.5), (1.0, 1.5), (2.0, 2.5)])
    x = S.bandpass(S.saturate(x * 0.6, 1.5), 300, 7000)
    return S.reverb(x, seconds=0.6, mix=0.12)[:n]


def hilo() -> np.ndarray:
    """The two-tone: 800 Hz and 1000 Hz, half a second each, twice (a 2 s loop)."""
    loop = 2.0
    n = int(loop * S.SR)
    t = S.t_axis(loop)
    f = np.where((t % 1.0) < 0.5, 800.0, 1000.0)
    x = osc(f, SQUARE[:8])
    x = S.bandpass(S.saturate(x * 0.55, 1.3), 400, 7000)
    return S.reverb(x, seconds=0.6, mix=0.12)[:n]


def general() -> np.ndarray:
    """A ship's general alarm: seven short blasts and one long on an alarm horn (a buzzy 440 Hz with its flare)."""
    loop = 7.0
    n = int(loop * S.SR)
    spans = [(0.55 * i, 0.55 * i + 0.3) for i in range(7)] + [(4.0, 6.0)]
    f = np.full(n, 440.0) * (1 + 0.003 * np.sin(2 * np.pi * 6 * S.t_axis(loop)))
    x = S.saturate(osc(f, SAW) * gate(n, spans, edge=0.012) * 0.6, 2.0)
    x = S.bandpass(x, 250, 6000)
    x = x + 0.4 * S.resonator(x, 1300, 6)
    return S.reverb(x, seconds=0.9, mix=0.16)[:n]


def bell() -> np.ndarray:
    """An electric trembler bell: its hammer strikes the gong eighteen times a second; the gong's inharmonic modes ring
    on under each strike; a faint rattle of the striker."""
    loop, pre = 2.0, 0.4
    length = pre + loop + 0.4
    n = int(length * S.SR)
    rng = S.rng(660)
    modes = [(1.0, 1.0, 0.35), (2.76, 0.5, 0.2), (5.4, 0.3, 0.12), (8.93, 0.15, 0.07)]
    f0 = 1180.0
    out = np.zeros(n)
    k = np.arange(n) / S.SR
    tt = 0.0
    while tt < length:
        i = int(tt * S.SR)
        seg = k[: n - i]
        hit = np.zeros(n - i)
        g = 0.85 + 0.3 * rng.random()
        for ratio, amp, decay in modes:
            hit += amp * np.sin(2 * np.pi * f0 * ratio * seg) * np.exp(-seg / decay)
        out[i:] += hit * g
        tt += 1 / 18 * (0.97 + 0.06 * rng.random())
    rattle = S.bandpass(S.white(length, 661), 2500, 9000)[:n] * 0.08 * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 18 * k)))
    out = S.saturate((out + rattle) * 0.2, 1.4)
    out = S.reverb(out, seconds=0.9, mix=0.15)[:n]
    return S.seamless_loop(out, loop, xfade=0.1, preroll=pre)


def attention() -> np.ndarray:
    """The attention tone: 853 Hz and 960 Hz together, two half-second bursts, from a small loudspeaker."""
    total = 1.3
    n = int(total * S.SR)
    t = S.t_axis(total)
    x = (np.sin(2 * np.pi * 853 * t) + np.sin(2 * np.pi * 960 * t)) * gate(n, [(0.0, 0.5), (0.65, 1.15)], edge=0.01)
    x = S.bandpass(S.saturate(x * 0.5, 1.6), 350, 4000)
    return S.fade(x, 0.0, 0.05)


def alarm_set() -> dict[str, np.ndarray]:
    """Every alarm sound, at its loudness."""
    made = {"alarm/siren_wail": siren_wail(), "alarm/siren_steady": siren_steady(), "alarm/klaxon": klaxon(),
            "alarm/whoop": whoop(), "alarm/evacuate": evacuate(), "alarm/hilo": hilo(), "alarm/general": general(),
            "alarm/bell": bell(), "alarm/attention": attention()}
    return {rel: R.loud_as(x, LOUDNESS[rel]) for rel, x in made.items()}


if __name__ == "__main__":
    import sys
    from pathlib import Path
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from gb import paths  # noqa: E402
    for rel, x in alarm_set().items():
        S.save_ogg(x, paths.SOUNDS / f"{rel}.ogg")
        print(f"{rel}: {len(x) / S.SR:.2f} s")
