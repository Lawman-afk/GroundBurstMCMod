"""Ordnance and the bombs' effects, remastered from real recordings (DESIGN.md §13.1).

A grenade's pin, dynamite's fuse, a sticky grenade sticking, smoke pouring out, charges armed and made safe, thermite
burning, the detonator, a wire cut, a mine's plate and a bouncing mine's launch, a fuel cloud bursting out, a bunker
buster boring down, a bomb's whistle, the EMP's discharge and the cryo burst. Each keeps the length the game plays it at
(the smoke's 3.2 s, played every 3 s; thermite's 1.2 s, every second), made from recordings: small metal parts fiddled
for a pin pull, a fireworks fuse, a muddy splat, a road flare and sparklers burning, a fire extinguisher, an arming
switch under its red cover, scissors, a trap's snap, a mortar firing, a machine drilling metal, falling bombs whistling,
high-voltage arcs and a Jacob's ladder, liquid nitrogen, ice cracking. Synthesis is left for the electronics (the arming
beeps, the detonator's radio squelch, the timed bomb's beep, recipes.ordnance_sounds) and a few deep thumps.

Every cut names its recording (sources/ledger.json) and where in it; the windows were picked from survey.py's sheets.
"""
from __future__ import annotations

import numpy as np

import sources as R
import synth as S

# (recording, from s, to s, the quiet stretch its background is measured on[, options: hp/lp Hz, fade s, gain dB])
CUTS = {
    "pin": ("metal_parts", 6.50, 6.92, None, {"hp": 150, "fade": 0.08}),   # the ring's clicks, the pin sliding free
    "strike": ("sparklers", 0.62, 1.25, None, {"fade": 0.2}),             # a match struck and flaring
    "fuse": ("fuse_elonen", 0.15, 3.6, None, {"hp": 60}),                  # a fireworks fuse catching and fizzing
    "splat": ("mud_splat", 0.0, 0.36, None, {"fade": 0.05}),               # a muddy splat
    "flare": ("road_flare", 3.0, 8.5, None),                               # a road flare's steady fizzing roar
    "spray": ("extinguisher", 4.4, 6.6, None),                             # a fire extinguisher's long spray
    "puff": ("extinguisher", 0.66, 1.06, None, {"fade": 0.1}),             # ... and one short burst
    "cover": ("arming_switch", 3.20, 3.42, (0.5, 3.0), {"fade": 0.08}),    # the switch's red cover snapping open
    "flip": ("arming_switch", 6.62, 6.84, (0.5, 3.0), {"fade": 0.08}),     # ... and the switch flipped
    "click_a": ("switches", 0.40, 0.62, None, {"hp": 150, "fade": 0.08}),  # another switch thrown
    "click_b": ("switches", 0.82, 1.05, None, {"hp": 150, "fade": 0.08}),  # ... and back
    "sparks": ("sparklers", 20.0, 22.5, None),                             # sparklers spitting
    "snip": ("scissors", 2.16, 2.36, None, {"hp": 200, "fade": 0.05}),     # one hard snip
    "snap": ("mousetrap", 1.90, 2.12, None, {"fade": 0.07}),               # a trap's snap (its long ring cut short)
    "mortar": ("mortar_shots", 0.0, 0.9, None, {"fade": 0.3}),             # a mortar's launch thump
    "drill": ("metal_drilling", 20.0, 22.4, None),                         # a machine drilling through metal
    "whistle": ("bombs_whistle", 0.6, 4.6, None),                          # a falling bomb's whistle over the rushing air
    "arc_on": ("hv_sparks", 16.55, 18.0, None),                            # a high-voltage arc striking
    "arc_off": ("hv_sparks", 14.0, 15.8, None),                            # ... and dying as the power goes
    "ladder": ("jacobs_ladder", 20.0, 22.5, None),                         # a Jacob's ladder's buzzing, climbing arc
    "nitrogen": ("liquid_nitrogen", 20.6, 21.3, None, {"fade": 0.15}),     # a rush of liquid nitrogen boiling off
    "ice": ("ice_cracking", 29.3, 30.9, None, {"fade": 0.3}),              # ice cracking as it sets
}


def take(name: str, speed: float = 1.0, aligned: bool = False) -> np.ndarray:
    return R.take(CUTS, name, speed, aligned)


def at(x: np.ndarray, db: float) -> np.ndarray:
    """{x} at a loudness (RMS, dBFS): layers are set against each other by how loud they are, not by their peaks."""
    return R.match(x, db)


def span(x: np.ndarray, start: float, seconds: float, fade_in: float = 0.05, fade_out: float = 0.15) -> np.ndarray:
    """{seconds} of {x} from {start} s, faded in and out (raised cosine)."""
    y = x[int(start * S.SR): int((start + seconds) * S.SR)]
    return R.tail_fade(S.fade(y, fade_in, 0.0), fade_out)


def beep(seconds: float, freq: float) -> np.ndarray:
    """A charge's electronic beep (as recipes.ordnance_sounds makes it: electronics are synthesized in the world too)."""
    n = int(seconds * S.SR)
    x = S.tone(seconds, freq, "square") * 0.35 + S.tone(seconds, freq * 2, "sine") * 0.15
    return S.lowpass(x, 6000) * S.adsr(n, 0.004, 0.01, 0.9, 0.01)


def thump(length: float, cutoff: float, decay: float, seed: int) -> np.ndarray:
    return S.lowpass(S.white(length, seed), cutoff) * S.exp_decay(int(length * S.SR), decay)


def grenade_pin() -> np.ndarray:
    """A grenade's pin pulled: its ring's clicks and the pin sliding free (small metal parts, which their recordist
    used for just this)."""
    return S.normalize(take("pin", aligned=True), -3)


def dynamite_light() -> np.ndarray:
    """Dynamite lit (3.3 s): a match struck and flaring, then the fuse catching and fizzing for its three seconds."""
    x = R.layer(3.3, (at(take("strike", aligned=True), -20), 0.0, 1.0), (at(span(take("fuse"), 0.0, 3.15, 0.08, 0.25), -21), 0.15, 1.0))
    return S.normalize(x, -4)


def sticky_stick() -> np.ndarray:
    """A sticky grenade slapping onto something: a muddy splat, a low thump of its weight under it."""
    x = R.layer(0.4, (at(take("splat"), -16), 0.0, 1.0), (at(thump(0.25, 300, 0.03, 440), -24), 0.0, 1.0))
    return S.normalize(x, -3)


def smoke_hiss() -> np.ndarray:
    """Smoke pouring out (3.2 s, played every 3 s while it does, so faded at both ends): a burning flare's fizz with
    the roar taken out, and a gas's rush (an extinguisher's spray) under it."""
    fizz = S.bandpass(span(take("flare"), 1.0, 3.2, 0.15, 0.3), 700, 9000, 2)
    spray = span(take("spray"), 0.0, 2.2, 0.3, 0.6)
    return S.normalize(R.layer(3.2, (at(fizz, -20), 0.0, 1.0), (at(spray, -26), 0.5, 1.0)), -6)


def charge_arm() -> np.ndarray:
    """A charge armed: the arming switch's red cover snapping open, the switch flipped, then two rising beeps."""
    x = R.layer(0.46, (at(take("cover", aligned=True), -24), 0.0, 1.0), (at(take("flip", aligned=True), -22), 0.07, 1.0),
                (at(beep(0.09, 1800), -15), 0.13, 1.0), (at(beep(0.12, 2400), -15), 0.27, 1.0))
    return S.normalize(R.tail_fade(x, 0.03), -4)


def charge_disarm() -> np.ndarray:
    """A charge made safe: a switch thrown and its cover clicked shut, then two falling beeps."""
    x = R.layer(0.46, (at(take("click_a", aligned=True), -23), 0.0, 1.0), (at(take("click_b", aligned=True), -24), 0.07, 1.0),
                (at(beep(0.09, 2400), -15), 0.13, 1.0), (at(beep(0.12, 1600), -15), 0.27, 1.0))
    return S.normalize(R.tail_fade(x, 0.03), -4)


def thermite_burn() -> np.ndarray:
    """Thermite burning (1.2 s, played every second, pitched a little each time): a road flare's fizzing roar with
    sparklers' spitting over it."""
    x = R.layer(1.2, (at(span(take("flare"), 0.5, 1.2, 0.05, 0.15), -18), 0.0, 1.0),
                (at(span(take("sparks"), 0.3, 1.2, 0.05, 0.15), -22), 0.0, 1.0))
    return S.normalize(x, -3)


def detonator_click() -> np.ndarray:
    """The detonator: a heavy switch (the arming switch, lower) and a short burst of radio squelch as it sends."""
    squelch = S.bandpass(S.white(0.18, 481), 1200, 4500) * S.breakpoints(int(0.18 * S.SR), [(0, 0), (0.01, 1), (0.15, 0.7), (0.18, 0)])
    x = R.layer(0.32, (at(take("flip", 0.8, aligned=True), -20), 0.0, 1.0), (at(squelch, -27), 0.08, 1.0))
    return S.normalize(R.tail_fade(x, 0.03), -4)


def wire_snip() -> np.ndarray:
    """A wire cut: one hard snip of the blades."""
    return S.normalize(take("snip", aligned=True), -4)


def mine_click() -> np.ndarray:
    """A mine's plate clicking down under a boot: a trap's sudden spring snap and its short ring."""
    return S.normalize(take("snap", aligned=True), -3)


def mine_pop() -> np.ndarray:
    """A bouncing mine's launch charge throwing it up: a mortar's thump, smaller (played faster)."""
    return S.normalize(R.tail_fade(take("mortar", 1.25, aligned=True)[: int(0.6 * S.SR)], 0.25), -2)


def fuel_disperse() -> np.ndarray:
    """A thermobaric's fuel cloud bursting out (0.9 s): a dull thud and a rushing gas that swells (an extinguisher's
    burst, slowed: a far bigger one)."""
    rush = take("puff", 0.55)
    rush = rush * np.clip(np.linspace(0.4, 1.4, len(rush)), 0, 1)
    x = R.layer(0.9, (at(thump(0.9, 200, 0.08, 520), -18), 0.0, 1.0), (at(rush, -21), 0.03, 1.0))
    return S.normalize(R.tail_fade(x, 0.1), -2)


def bomb_drill() -> np.ndarray:
    """A bunker buster boring down through rock and concrete (2 s, played again as it goes): a machine grinding and
    drilling through metal, with the ground's low rumble under it."""
    rumble = S.lowpass(S.brown(2.0, 532), 120)
    x = R.layer(2.0, (at(span(take("drill"), 0.0, 2.0, 0.05, 0.4), -18), 0.0, 1.0), (at(rumble, -24), 0.0, 1.0))
    return S.normalize(x, -3)


def bomb_whistle() -> np.ndarray:
    """A falling bomb's whistle (3.5 s): a real bomb's whistle over the rushing air, falling in pitch as it comes down and
    louder as it nears."""
    x = S.highpass(R.glide(take("whistle"), 1.1, 0.8), 250, 2)[: int(3.5 * S.SR)]
    swell = np.clip(np.linspace(0, 1, len(x)), 0, 1) ** 1.5 * 0.8 + 0.2
    return S.normalize(R.tail_fade(S.fade(x * swell, 0.3, 0.0), 0.15), -2)


def emp_zap() -> np.ndarray:
    """The EMP's discharge (2.2 s): a high-voltage arc striking at once and buzzing (a Jacob's ladder under it), then
    dying as the power goes, and a whine falling away as everything electrical shuts down (synthesized)."""
    n = 2.2
    t = S.t_axis(n)
    f = 1400 * np.exp(-t / 0.45) + 60
    phase = 2 * np.pi * np.cumsum(f) / S.SR
    whine = (np.sin(phase) * 0.5 + np.sin(2 * phase) * 0.2 + np.sin(3 * phase) * 0.1) * np.clip(1 - t / n, 0, 1) ** 1.5
    on = R.tail_fade(take("arc_on", aligned=True)[: int(1.1 * S.SR)], 0.4)
    off = S.fade(take("arc_off"), 0.3, 0.0)
    x = R.layer(n, (at(on, -16), 0.0, 1.0), (at(off, -19), 0.75, 1.0), (at(span(take("ladder"), 0.0, 1.3, 0.02, 0.5), -24), 0.0, 1.0),
                (at(whine, -27), 0.0, 1.0))
    x = S.reverb(x, 1.0, 3000, 0.2, seed=700)[: len(x)]
    return S.normalize(R.tail_fade(x, 0.3), -1)


def cryo_burst() -> np.ndarray:
    """The cryo burst (1.6 s): a soft pop, the rush of a cryogen boiling off (liquid nitrogen), and ice cracking as it
    sets."""
    x = R.layer(1.6, (at(thump(0.4, 400, 0.04, 710), -22), 0.0, 1.0), (at(take("nitrogen"), -20), 0.02, 1.0),
                (at(take("ice"), -22), 0.12, 1.0))
    return S.normalize(R.tail_fade(x, 0.2), -2)


#: How loud each is (its loudest 3 s, LUFS): as loud as the synthesized sound it replaces, whose in-game volume was tuned
#: to it (the EMP a little louder: it was the quietest big event).
LOUDNESS = {"ordnance/grenade_pin": -34.7, "ordnance/dynamite_light": -21.6, "ordnance/sticky_stick": -32.4,
            "ordnance/smoke_hiss": -18.5, "ordnance/charge_arm": -22.3, "ordnance/charge_disarm": -22.5,
            "ordnance/thermite_burn": -20.7, "ordnance/detonator_click": -35.0, "ordnance/wire_snip": -40.0,
            "ordnance/mine_click": -36.0, "ordnance/mine_pop": -28.7, "ordnance/fuel_disperse": -24.3,
            "ordnance/bomb_drill": -24.3, "ordnance/bomb_whistle": -12.5, "effect/emp_zap": -22.0, "effect/cryo_burst": -20.9}


def ordnance_set() -> dict[str, np.ndarray]:
    """Every ordnance and effect sound made from recordings (the timed bomb's beep stays synthesized: recipes.ordnance_sounds),
    each as loud as the one it replaces."""
    made = {"ordnance/grenade_pin": grenade_pin(), "ordnance/dynamite_light": dynamite_light(),
            "ordnance/sticky_stick": sticky_stick(), "ordnance/smoke_hiss": smoke_hiss(), "ordnance/charge_arm": charge_arm(),
            "ordnance/charge_disarm": charge_disarm(), "ordnance/thermite_burn": thermite_burn(),
            "ordnance/detonator_click": detonator_click(), "ordnance/wire_snip": wire_snip(), "ordnance/mine_click": mine_click(),
            "ordnance/mine_pop": mine_pop(), "ordnance/fuel_disperse": fuel_disperse(), "ordnance/bomb_drill": bomb_drill(),
            "ordnance/bomb_whistle": bomb_whistle(), "effect/emp_zap": emp_zap(), "effect/cryo_burst": cryo_burst()}
    return {rel: R.loud_as(x, LOUDNESS[rel]) for rel, x in made.items()}


if __name__ == "__main__":
    import sys
    from pathlib import Path
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from gz import paths  # noqa: E402
    for rel, x in ordnance_set().items():
        S.save_ogg(x, paths.SOUNDS / f"{rel}.ogg")
        print(f"{rel}: {len(x) / S.SR:.2f} s")
