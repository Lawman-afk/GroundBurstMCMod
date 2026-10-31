"""The Launch Control Station's sounds (DESIGN.md §17.12), synthesized:

- launch/station_key: a launch key turned in its switch: the key's bit scraping home, the heavy detent's clack ringing
  in the steel panel, the contacts closing a moment after; a small room.
- launch/station_launch: LAUNCH: the clear guard flipped up (a plastic snap), the big button pressed home (a thunk with
  the console's body in it), the relays answering in a chatter, then the launch-order tone from the console's small
  speaker: two tones in turn, three times.
- launch/station_teletype: a report printing: the teletype's characters at ten a second (each a type bar's strike, the
  print hammer, the platen's thud), its motor's hum and the mechanism's whirr under them, the carriage's bell at the end.
"""
from __future__ import annotations

import numpy as np

import sources as R
import synth as S

#: Each sound's loudness (its loudest 3 s, LUFS): quieter than the alarms, as a console's sounds are.
LOUDNESS = {"launch/station_key": -21.0, "launch/station_launch": -18.5, "launch/station_teletype": -22.0}


def click(seconds: float, modes: list[tuple[float, float, float]], seed: int, noise: float = 0.6, noise_tau: float = 0.004,
          band: tuple[float, float] = (1500, 9000)) -> np.ndarray:
    """A struck part: a burst of noise (the contact) and the part's modes ringing down (freq, level, decay s)."""
    n = int(seconds * S.SR)
    t = S.t_axis(seconds)
    out = S.bandpass(S.white(seconds, seed), *band)[:n] * S.exp_decay(n, noise_tau) * noise
    rng = S.rng(seed + 1)
    for f, a, tau in modes:
        out += a * np.sin(2 * np.pi * f * (1 + 0.004 * rng.standard_normal()) * t + rng.random() * 6.28) * np.exp(-t / tau)
    return out


def thump(seconds: float, freq: float, tau: float, seed: int) -> np.ndarray:
    """A body's low knock (the console's steel box)."""
    n = int(seconds * S.SR)
    t = S.t_axis(seconds)
    body = np.sin(2 * np.pi * freq * t * (1 - 0.15 * t / seconds)) * np.exp(-t / tau)
    return body + 0.3 * S.lowpass(S.white(seconds, seed), 400)[:n] * S.exp_decay(n, tau * 0.5)


def room(x: np.ndarray, seconds: float = 0.45, mix: float = 0.14) -> np.ndarray:
    return S.reverb(x, seconds=seconds, damping_hz=4000, mix=mix)[: len(x)]


def station_key() -> np.ndarray:
    total = 0.6
    scrape = S.bandpass(S.white(0.05, 801), 2600, 7000) * S.adsr(int(0.05 * S.SR), 0.008, 0.02, 0.3, 0.015) * 0.12
    clack = click(0.25, [(1870, 0.5, 0.05), (3140, 0.35, 0.035), (5230, 0.22, 0.02), (720, 0.3, 0.06)], 802, noise=0.9)
    contact = click(0.08, [(4100, 0.2, 0.012), (6900, 0.12, 0.008)], 803, noise=0.4, noise_tau=0.002)
    x = S.mix((S.place(scrape, 0.0, total), 1.0), (S.place(clack, 0.09, total), 1.0), (S.place(thump(0.12, 170, 0.018, 804), 0.09, total), 0.35),
              (S.place(contact, 0.15, total), 0.8))
    return S.fade(room(x, 0.35, 0.08), 0.0, 0.05)


def station_launch() -> np.ndarray:
    total = 2.1
    snap = click(0.12, [(1250, 0.4, 0.02), (2430, 0.3, 0.015), (3800, 0.15, 0.01)], 811, noise=0.8, band=(900, 7000))
    press = S.mix((thump(0.2, 115, 0.035, 812), 0.8), (click(0.15, [(980, 0.3, 0.03), (2600, 0.2, 0.02)], 813, noise=0.6), 0.8))
    relays = np.zeros(int(total * S.SR))
    for i, at in enumerate((0.36, 0.41, 0.47, 0.5)):
        relays += S.place(click(0.05, [(3300 + 400 * i, 0.15, 0.008)], 820 + i, noise=0.5, noise_tau=0.0015), at, total)
    # The launch-order tone: 1050 and 1400 Hz in turn, 0.12 s each, three times, square-ish from a small speaker.
    n = int(total * S.SR)
    t = S.t_axis(total)
    f = np.zeros(n)
    spans = []
    for k in range(6):
        a, b = 0.62 + k * 0.14, 0.62 + k * 0.14 + 0.12
        f[int(a * S.SR):int(b * S.SR)] = 1050.0 if k % 2 == 0 else 1400.0
        spans.append((a, b))
    phase = 2 * np.pi * np.cumsum(f) / S.SR
    tone = sum(np.sin(k * phase) / k * (k * f < 0.45 * S.SR) for k in (1, 3, 5, 7))
    env = np.zeros(n)
    for a, b in spans:
        i0, i1 = int(a * S.SR), int(b * S.SR)
        seg = np.ones(i1 - i0)
        e = int(0.006 * S.SR)
        seg[:e] *= np.linspace(0, 1, e)
        seg[-e:] *= np.linspace(1, 0, e)
        env[i0:i1] = seg
    tone = S.bandpass(S.saturate(tone * env * 0.5, 1.5), 420, 4200)
    x = S.mix((S.place(snap, 0.0, total), 0.9), (S.place(press, 0.24, total), 1.0), (relays, 0.7), (tone, 0.55))
    del t
    return S.fade(room(x, 0.5, 0.16), 0.0, 0.08)


def station_teletype() -> np.ndarray:
    total = 1.5
    n = int(total * S.SR)
    t = S.t_axis(total)
    rng = S.rng(830)
    out = np.zeros(n)
    at = 0.04
    k = 0
    while at < 1.05:
        strike = click(0.06, [(2100 + 300 * rng.random(), 0.3, 0.012), (3600 + 500 * rng.random(), 0.2, 0.008)], 840 + k, noise=0.7,
                       noise_tau=0.0025, band=(1200, 8000))
        platen = thump(0.05, 210 + 30 * rng.random(), 0.012, 900 + k)
        hit = S.mix((strike, 1.0), (platen, 0.4))
        out += S.place(hit * (0.85 + 0.3 * rng.random()), at + 0.004 * rng.standard_normal(), total)
        at += 0.1 if rng.random() > 0.12 else 0.2                 # ten characters a second, now and then a space
        k += 1
    motor = (np.sin(2 * np.pi * 60 * t) + 0.5 * np.sin(2 * np.pi * 120 * t) + 0.25 * np.sin(2 * np.pi * 180 * t)) * 0.012
    whirr = S.bandpass(S.white(total, 850), 300, 1500)[:n] * (0.6 + 0.4 * np.sin(2 * np.pi * 10 * t)) * 0.03
    run = S.breakpoints(n, [(0.0, 0.0), (0.03, 1.0), (1.12, 1.0), (1.35, 0.0), (total, 0.0)])
    bell = click(0.6, [(2230, 0.35, 0.25), (5620, 0.12, 0.08)], 860, noise=0.2)
    x = S.mix((out, 1.0), ((motor + whirr) * run, 1.0), (S.place(thump(0.12, 140, 0.03, 870), 1.12, total), 0.6),
              (S.place(bell, 1.13, total), 0.6))
    return S.fade(room(x, 0.4, 0.12), 0.0, 0.08)


def station_set() -> dict[str, np.ndarray]:
    """Every station sound, at its loudness."""
    made = {"launch/station_key": station_key(), "launch/station_launch": station_launch(), "launch/station_teletype": station_teletype()}
    return {rel: R.loud_as(x, LOUDNESS[rel]) for rel, x in made.items()}


if __name__ == "__main__":
    import sys
    from pathlib import Path
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from gb import paths  # noqa: E402
    for rel, x in station_set().items():
        S.save_ogg(x, paths.SOUNDS / f"{rel}.ogg")
        print(f"{rel}: {len(x) / S.SR:.2f} s")
