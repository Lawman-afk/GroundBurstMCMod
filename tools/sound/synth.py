"""Sound synthesis toolkit: layered noise, oscillators, filters, envelopes, saturation, reverb, OGG export.

Every sound in the mod is built from these pieces by a recipe in tools/sound/recipes.py. Output is mono 44.1 kHz
OGG Vorbis (Minecraft only positions mono sounds). All randomness is seeded so files are reproducible.
"""
from __future__ import annotations

from pathlib import Path

import numpy as np
import soundfile as sf
from scipy import signal

SR = 44_100


def t_axis(seconds: float) -> np.ndarray:
    return np.arange(int(seconds * SR)) / SR


def rng(seed: int) -> np.random.Generator:
    return np.random.default_rng(seed)


# --- generators ---------------------------------------------------------------------------------------------------

def white(seconds: float, seed: int) -> np.ndarray:
    return rng(seed).standard_normal(int(seconds * SR))


def pink(seconds: float, seed: int) -> np.ndarray:
    """1/f noise via spectral shaping."""
    n = int(seconds * SR)
    spec = np.fft.rfft(rng(seed).standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    f[0] = f[1]
    x = np.fft.irfft(spec / np.sqrt(f), n)
    return x / (np.max(np.abs(x)) + 1e-12)


def brown(seconds: float, seed: int) -> np.ndarray:
    """1/f^2 noise (deep rumble)."""
    x = np.cumsum(rng(seed).standard_normal(int(seconds * SR)))
    x = highpass(x, 15)
    return x / (np.max(np.abs(x)) + 1e-12)


def sine_sweep(seconds: float, f0: float, f1: float, curve: float = 3.0) -> np.ndarray:
    """Sine whose frequency glides exponentially from f0 to f1 (curve > 1 drops faster at the start)."""
    t = t_axis(seconds)
    k = (t / seconds) ** (1 / curve) if curve != 1 else t / seconds
    freq = f0 * (f1 / f0) ** k
    phase = 2 * np.pi * np.cumsum(freq) / SR
    return np.sin(phase)


def tone(seconds: float, freq: float, kind: str = "sine") -> np.ndarray:
    t = t_axis(seconds)
    if kind == "sine":
        return np.sin(2 * np.pi * freq * t)
    if kind == "square":
        return signal.square(2 * np.pi * freq * t)
    if kind == "saw":
        return signal.sawtooth(2 * np.pi * freq * t)
    raise ValueError(kind)


def grains(seconds: float, rate: float, grain_ms: float, seed: int, decay: float | None = None) -> np.ndarray:
    """Sparse short noise grains (crackle, debris patter, Geiger clicks). rate = grains/second (Poisson)."""
    r = rng(seed)
    out = np.zeros(int(seconds * SR))
    g = int(grain_ms / 1000 * SR)
    env = np.exp(-np.linspace(0, 6, g))
    t = 0.0
    while True:
        t += r.exponential(1 / rate)
        if t >= seconds:
            break
        i = int(t * SR)
        amp = r.uniform(0.3, 1.0) * (np.exp(-t / decay) if decay else 1.0)
        seg = r.standard_normal(g) * env * amp
        out[i:i + g] += seg[: max(0, min(g, len(out) - i))]
    return out


# --- envelopes ----------------------------------------------------------------------------------------------------

def exp_decay(n_or_sig, tau: float) -> np.ndarray:
    n = n_or_sig if isinstance(n_or_sig, int) else len(n_or_sig)
    return np.exp(-np.arange(n) / SR / tau)


def adsr(n: int, a: float, d: float, s: float, r: float) -> np.ndarray:
    """Attack/decay/release in seconds; sustain level s (0..1) fills the rest."""
    na, nd, nr = int(a * SR), int(d * SR), int(r * SR)
    ns = max(0, n - na - nd - nr)
    env = np.concatenate([np.linspace(0, 1, na, endpoint=False), np.linspace(1, s, nd, endpoint=False),
                          np.full(ns, s), np.linspace(s, 0, nr)])
    return np.pad(env, (0, max(0, n - len(env))))[:n]


def breakpoints(n: int, points: list[tuple[float, float]]) -> np.ndarray:
    """Linear envelope through (time_seconds, level) points."""
    ts = np.array([p[0] for p in points]) * SR
    vs = np.array([p[1] for p in points])
    return np.interp(np.arange(n), ts, vs)


# --- filters ---------------------------------------------------------------------------------------------------------

def _sos(kind: str, freq, order: int = 4):
    return signal.butter(order, freq, btype=kind, fs=SR, output="sos")


def lowpass(x: np.ndarray, hz: float, order: int = 4) -> np.ndarray:
    return signal.sosfilt(_sos("lowpass", min(hz, SR / 2 - 100), order), x)


def highpass(x: np.ndarray, hz: float, order: int = 4) -> np.ndarray:
    return signal.sosfilt(_sos("highpass", hz, order), x)


def bandpass(x: np.ndarray, lo: float, hi: float, order: int = 4) -> np.ndarray:
    return signal.sosfilt(_sos("bandpass", (lo, min(hi, SR / 2 - 100)), order), x)


def resonator(x: np.ndarray, freq: float, q: float = 20.0) -> np.ndarray:
    b, a = signal.iirpeak(freq, q, fs=SR)
    return signal.lfilter(b, a, x)


def sweep_lowpass(x: np.ndarray, f_start: float, f_end: float, steps: int = 64) -> np.ndarray:
    """Time-varying lowpass (block-wise), e.g. a roar that darkens as it decays."""
    out = np.zeros_like(x)
    n = len(x)
    edges = np.linspace(0, n, steps + 1).astype(int)
    zi = None
    for i in range(steps):
        f = f_start * (f_end / f_start) ** (i / max(1, steps - 1))
        sos = _sos("lowpass", min(f, SR / 2 - 100), 2)
        if zi is None:
            zi = signal.sosfilt_zi(sos) * 0
        seg, zi = signal.sosfilt(sos, x[edges[i]:edges[i + 1]], zi=zi)
        out[edges[i]:edges[i + 1]] = seg
    return out


# --- effects -------------------------------------------------------------------------------------------------------

def saturate(x: np.ndarray, drive: float = 2.0) -> np.ndarray:
    return np.tanh(x * drive) / np.tanh(drive)


def reverb(x: np.ndarray, seconds: float = 2.5, damping_hz: float = 3000, mix: float = 0.35, seed: int = 99,
           predelay: float = 0.02) -> np.ndarray:
    """Convolution with a synthetic room/outdoor impulse response (decaying, darkening noise)."""
    n = int(seconds * SR)
    ir = rng(seed).standard_normal(n) * np.exp(-np.arange(n) / SR / (seconds / 5))
    ir = lowpass(ir, damping_hz, 2)
    ir = np.concatenate([np.zeros(int(predelay * SR)), ir])
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-12
    wet = signal.fftconvolve(x, ir)[: len(x) + len(ir) - 1]
    dry = np.pad(x, (0, len(wet) - len(x)))
    return dry * (1 - mix) + wet * mix


def echo(x: np.ndarray, delay: float, feedback: float, repeats: int = 4, lp_hz: float = 2000) -> np.ndarray:
    d = int(delay * SR)
    out = np.pad(x, (0, d * repeats))
    tap = x.copy()
    for k in range(1, repeats + 1):
        tap = lowpass(tap, lp_hz, 2) * feedback
        out[d * k:d * k + len(tap)] += tap
    return out


def mix(*layers: tuple[np.ndarray, float]) -> np.ndarray:
    n = max(len(s) for s, _ in layers)
    out = np.zeros(n)
    for s, gain in layers:
        out[: len(s)] += s * gain
    return out


def place(x: np.ndarray, at: float, total: float) -> np.ndarray:
    """Pads/places a signal starting at `at` seconds inside a buffer of `total` seconds."""
    out = np.zeros(int(total * SR))
    i = int(at * SR)
    seg = x[: max(0, len(out) - i)]
    out[i:i + len(seg)] = seg
    return out


def fade(x: np.ndarray, fade_in: float = 0.0, fade_out: float = 0.05) -> np.ndarray:
    x = x.copy()
    ni, no = int(fade_in * SR), int(fade_out * SR)
    if ni:
        x[:ni] *= np.linspace(0, 1, ni)
    if no:
        x[-no:] *= np.linspace(1, 0, no)
    return x


def trim_silence(x: np.ndarray, threshold_db: float = -60) -> np.ndarray:
    level = 10 ** (threshold_db / 20) * np.max(np.abs(x))
    idx = np.where(np.abs(x) > level)[0]
    return x[: idx[-1] + int(0.05 * SR)] if len(idx) else x


def normalize(x: np.ndarray, peak_db: float = -1.0) -> np.ndarray:
    peak = np.max(np.abs(x)) + 1e-12
    return x / peak * 10 ** (peak_db / 20)


def limit(x: np.ndarray, ceiling_db: float = -1.0, knee: float = 1.5) -> np.ndarray:
    """Soft limiter: tanh around the ceiling, keeps transients punchy without clipping."""
    c = 10 ** (ceiling_db / 20)
    return np.tanh(x / c * knee) / np.tanh(knee) * c


# --- distance rendering -------------------------------------------------------------------------------------------------

def distant(x: np.ndarray, band: str) -> np.ndarray:
    """Re-renders a near sound as heard from far away: air absorbs highs, transients smear into rolling rumble."""
    if band == "mid":
        y = lowpass(x, 1800, 4)
        y = reverb(y, 3.5, 1500, 0.45, seed=7)
    elif band == "far":
        y = lowpass(x, 420, 6)
        y = reverb(y, 5.0, 600, 0.6, seed=8, predelay=0.05)
        y = echo(y, 0.9, 0.35, 3, 300)
    elif band == "very_far":
        y = lowpass(x, 140, 8)
        y = reverb(y, 6.0, 250, 0.7, seed=9, predelay=0.1)
        y = echo(y, 1.4, 0.4, 3, 150)
    else:
        raise ValueError(band)
    return y


# --- export -------------------------------------------------------------------------------------------------------------

def save_ogg(x: np.ndarray, path: Path, quality: float = 0.6) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    data = np.clip(x, -1, 1).astype(np.float32)
    # libsndfile's Vorbis "compression level": 0 = best quality / largest file.
    with sf.SoundFile(path, "w", samplerate=SR, channels=1, format="OGG", subtype="VORBIS",
                      compression_level=1 - quality) as f:
        f.write(data)
    return path


def save_wav(x: np.ndarray, path: Path) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    sf.write(path, np.clip(x, -1, 1).astype(np.float32), SR, subtype="PCM_16")
    return path
