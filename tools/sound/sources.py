"""Recordings as raw material for the mod's sounds (DESIGN.md §13.4, the sound remaster).

Real recordings (sources/ledger.json, fetched into sources/raw/) are cut, cleaned and brought to the game's rate here,
and the physics that turns them into a blast heard at a given size and distance:

- varispeed: a recording played slower is a bigger event, lower and longer, as a bigger charge's blast is (its pulse
  lasts longer, by the cube root of the yield);
- air: how air absorbs sound over distance, frequency by frequency (ISO 9613-1): a crack 50 m off still crackles, a
  kilometre off only its boom is left;
- friedlander: a blast wave's own pressure pulse (the sharp rise, the decaying overpressure, the longer suction after),
  for the punch close up that a recorder's microphone rarely keeps;
- convolve: a sound placed in a recorded space (an impulsive recording, a detonation heard far off, is that place's
  answer to a bang).

Everything is mono at synth.SR (the game positions mono sounds only).
"""
from __future__ import annotations

import json
from fractions import Fraction
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy import signal

import synth as S

HERE = Path(__file__).resolve().parent
RAW = HERE / "sources" / "raw"
LEDGER = {s["key"]: s for s in json.loads((HERE / "sources" / "ledger.json").read_text(encoding="utf-8"))["sources"]}
_CACHE: dict[str, np.ndarray] = {}


def resample(x: np.ndarray, sr_from: int, sr_to: int) -> np.ndarray:
    if sr_from == sr_to:
        return x
    r = Fraction(sr_to, sr_from).limit_denominator(1000)
    return signal.resample_poly(x, r.numerator, r.denominator)


def recording(key: str) -> np.ndarray:
    """A whole recording, mono (its channels averaged), at the game's rate, with its DC and sub-audio rumble removed."""
    if key not in _CACHE:
        path = RAW / (key + Path(LEDGER[key]["file"]).suffix)
        if not path.exists():
            raise FileNotFoundError(f"{path.name} missing: run tools/sound/sources/fetch.py")
        x, sr = sf.read(path, always_2d=True)
        mono = resample(x.mean(axis=1), sr, S.SR)
        _CACHE[key] = S.highpass(mono, 18, 2)
    return _CACHE[key]


def cut(key: str, t0: float, t1: float, fade_in: float = 0.003, fade_out: float | None = None,
        noise: tuple[float, float] | None = None, strength: float = 1.6) -> np.ndarray:
    """Seconds t0..t1 of a recording: cleaned of its background ({noise}: a quiet stretch of the same recording, its
    hiss and hum, taken out by spectral subtraction), faded in briefly (a bang's front is kept) and out over its last
    quarter (at most 1.5 s) on a smooth curve, so its tail dies away instead of stopping."""
    x = recording(key)
    y = x[int(t0 * S.SR):int(t1 * S.SR)].copy()
    if noise is not None:
        y = denoise(y, x[int(noise[0] * S.SR):int(noise[1] * S.SR)], strength)
    out = fade_out if fade_out is not None else min(1.5, 0.25 * len(y) / S.SR)
    return tail_fade(S.fade(y, fade_in, 0), out)


def tail_fade(x: np.ndarray, seconds: float) -> np.ndarray:
    """Faded out over its last {seconds} on a raised-cosine curve (no audible corner where it starts)."""
    n = min(len(x), int(seconds * S.SR))
    if n <= 1:
        return x
    y = x.copy()
    y[-n:] *= 0.5 * (1 + np.cos(np.linspace(0, np.pi, n)))
    return y


def _stft(x: np.ndarray, n: int = 2048):
    return signal.stft(x, S.SR, nperseg=n, noverlap=n * 3 // 4)


def _istft(X: np.ndarray, length: int, n: int = 2048) -> np.ndarray:
    _, y = signal.istft(X, S.SR, nperseg=n, noverlap=n * 3 // 4)
    y = y[:length]
    return np.pad(y, (0, max(0, length - len(y))))


def denoise(x: np.ndarray, noise: np.ndarray, strength: float = 1.6, floor: float = 0.06) -> np.ndarray:
    """Spectral subtraction: each moment's level at each frequency less {strength} times the background's average
    there (from {noise}, a quiet stretch), never below {floor} of what was there (no holes chewed in it), the gain
    smoothed over neighbouring moments and frequencies (no warbling). Takes out hiss, hum and wind without touching a
    bang far louder than them."""
    from scipy.ndimage import uniform_filter
    _, _, X = _stft(x)
    _, _, N = _stft(noise)
    nmag = np.mean(np.abs(N), axis=1, keepdims=True)
    mag = np.abs(X) + 1e-12
    gain = np.clip(1 - strength * nmag / mag, floor, 1)
    gain = uniform_filter(gain, size=(3, 5), mode="nearest")
    return _istft(X * gain, len(x))


def darken(x: np.ndarray, start: float, end: float, f_from: float, f_to: float) -> np.ndarray:
    """A blast's tail darkening as it dies (its highs go first): from {start} s to {end} s the cutoff falls from
    {f_from} to {f_to} Hz (exponentially), a gentle 4th-order slope above it; before {start} nothing changes."""
    f, t, X = _stft(x)
    tt = np.clip((t - start) / max(1e-6, end - start), 0, 1)
    cutoff = f_from * (f_to / f_from) ** tt
    mask = 1 / np.sqrt(1 + (f[:, None] / cutoff[None, :]) ** 8)
    mask[:, t < start] = 1
    return _istft(X * mask, len(x))


def onset(x: np.ndarray, threshold: float = 0.3) -> int:
    """The first sample whose 2 ms envelope reaches {threshold} of the loudest: where the bang starts."""
    hop = max(1, int(0.002 * S.SR))
    env = np.maximum.reduceat(np.abs(x), np.arange(0, len(x), hop))
    i = int(np.argmax(env >= threshold * env.max()))
    a = i * hop
    seg = np.abs(x[a:a + hop])
    return a + (int(np.argmax(seg >= threshold * env.max())) if len(seg) else 0)


def align(x: np.ndarray, pre: float = 0.006) -> np.ndarray:
    """Trimmed so the bang starts {pre} seconds in."""
    i = onset(x)
    return x[max(0, i - int(pre * S.SR)):]


def varispeed(x: np.ndarray, factor: float) -> np.ndarray:
    """Played at {factor} times its speed: below 1 slower, lower and longer (a bigger event), as tape slowed down."""
    r = Fraction(1 / factor).limit_denominator(400)
    return signal.resample_poly(x, r.numerator, r.denominator)


def air_alpha(f: np.ndarray, temp_c: float = 15.0, rh: float = 70.0, pa_kpa: float = 101.325) -> np.ndarray:
    """Atmospheric absorption, dB per metre, at frequencies f (Hz): ISO 9613-1's formula (oxygen and nitrogen relaxation
    and the classical part), for a mild humid day by default."""
    T, T0, T01, pr = temp_c + 273.15, 293.15, 273.16, 101.325
    psat = pr * 10 ** (-6.8346 * (T01 / T) ** 1.261 + 4.6151)
    h = rh * psat / pa_kpa                                    # molar concentration of water vapour, %
    fr_o = pa_kpa / pr * (24 + 4.04e4 * h * (0.02 + h) / (0.391 + h))
    fr_n = pa_kpa / pr * (T / T0) ** -0.5 * (9 + 280 * h * np.exp(-4.170 * ((T / T0) ** (-1 / 3) - 1)))
    f2 = np.asarray(f, dtype=float) ** 2
    return 8.686 * f2 * (1.84e-11 * (pa_kpa / pr) ** -1 * (T / T0) ** 0.5
                         + (T / T0) ** -2.5 * (0.01275 * np.exp(-2239.1 / T) / (fr_o + f2 / fr_o)
                                               + 0.1068 * np.exp(-3352.0 / T) / (fr_n + f2 / fr_n)))


def air(x: np.ndarray, metres: float, **weather) -> np.ndarray:
    """The sound after {metres} more of air: every frequency attenuated by its own absorption (zero-phase)."""
    if metres <= 0:
        return x
    n = int(2 ** np.ceil(np.log2(len(x) + 1)))
    X = np.fft.rfft(x, n)
    f = np.fft.rfftfreq(n, 1 / S.SR)
    X *= 10 ** (-air_alpha(f, **weather) * metres / 20)
    return np.fft.irfft(X, n)[:len(x)]


def ground(x: np.ndarray, metres: float) -> np.ndarray:
    """Soft ground between a distant source and the ear: the reflected path cancels the low mids (a broad dip near
    200-500 Hz) once the source is some way off; deeper the further."""
    if metres < 80:
        return x
    depth = min(9.0, 3.0 * np.log10(metres / 80) + 3.0)       # dB
    b, a = signal.iirpeak(320 / (S.SR / 2), 0.9)
    dip = signal.filtfilt(b, a, x)
    return x - dip * (1 - 10 ** (-depth / 20))


def friedlander(positive: float, length: float, b: float = 1.4, lowpass_hz: float = 2500.0) -> np.ndarray:
    """A blast wave's pressure at a fixed point: a sharp rise to the peak overpressure, decaying through zero after
    {positive} seconds into a longer, shallower suction (the Friedlander waveform p(t) = (1 - t/T) e^(-bt/T)).
    Band-limited at {lowpass_hz} so its front is a crack, not a click; peak 1."""
    t = S.t_axis(length)
    p = (1 - t / positive) * np.exp(-b * t / positive)
    p = S.lowpass(p, lowpass_hz, 2)
    return p / (np.max(np.abs(p)) + 1e-12)


def convolve(x: np.ndarray, ir: np.ndarray, mix: float = 1.0) -> np.ndarray:
    """{x} heard in the space {ir} answers a bang with (an impulsive recording, aligned to its onset), at {mix} wet."""
    ir = ir / (np.sqrt(np.sum(ir ** 2)) + 1e-12)
    wet = signal.fftconvolve(x, ir)
    wet *= np.sqrt(np.sum(x ** 2)) / (np.sqrt(np.sum(wet ** 2)) + 1e-12)
    dry = np.pad(x, (0, len(wet) - len(x)))
    return dry * (1 - mix) + wet * mix


def layer(total: float, *parts: tuple[np.ndarray, float, float]) -> np.ndarray:
    """Parts (sound, at seconds, gain) summed into {total} seconds."""
    out = np.zeros(int(total * S.SR))
    for x, at, gain in parts:
        a = int(at * S.SR)
        if a >= len(out):
            continue
        seg = x[:len(out) - a]
        out[a:a + len(seg)] += seg * gain
    return out


def rms_db(x: np.ndarray) -> float:
    return 20 * np.log10(np.sqrt(np.mean(x ** 2)) + 1e-12)


def match(x: np.ndarray, reference_db: float) -> np.ndarray:
    """Scaled to an RMS level (to set layers against each other by loudness, not by their peaks)."""
    return x * 10 ** ((reference_db - rms_db(x)) / 20)


def finish(x: np.ndarray, peak_db: float = -1.0, tail: float = 0.4) -> np.ndarray:
    """Ready for the game: peak-normalised under a soft limiter (the Vorbis encoder overshoots a raw peak), its tail
    faded and the silence after trimmed."""
    y = S.limit(S.normalize(x, peak_db + 0.5), peak_db)
    return S.trim_silence(S.fade(y, 0.0, tail), -60)
