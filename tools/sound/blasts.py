"""The blast sounds, remastered from real recordings (DESIGN.md §13.1; rounds 1 to 3 of the sound remaster).

Five sets (small, medium, large, nuke, tsar), each heard at four distances (near, mid, far, very far: under 1.5, 4, 12
and 40 of the blast's reaches, ClientBlasts). From the blasts' damage radii the bands are, roughly:

    set      reach     near     mid      far      very far
    small    8 m       6 m      20 m     55 m     175 m
    medium   20 m      15 m     50 m     140 m    440 m
    large    60 m      45 m     150 m    420 m    1.3 km
    nuke     200 m     150 m    500 m    1.4 km   4.4 km
    tsar     700 m     500 m    1.7 km   4.8 km   15 km

Each band is made from the recordings whose own distance suits it (a 4 kg TNT charge on a camcorder for a medium blast
close by; a mortar heard over a range for one further; a power-station chimney blown for a large one far off; a small
charge heard 500 m away through a forest for the faintest), slowed for bigger blasts (a slower recording is a bigger
blast: lower, longer), with what the microphones missed added by physics: the blast wave's pressure pulse for the punch
close up (sources.friedlander), and for distance the air's absorption, frequency by frequency (ISO 9613-1), the soft
ground's dip in the low mids and the rolling tail of a far boom. Debris is real stone and gravel.

Round 2 (the user's ratings of round 1): the large, nuclear and Tsar blasts lie on the old synthesized recipes' boom and
roar (recipes.py), whose weight the user preferred, with the recordings over them; no thunder or lightning anywhere (it
sounded like lightning, not a blast); a low whump (thump) and a harder drive (sources.punch) where a blast was too weak
or too much like a firecracker.

Round 3 (the ratings of round 2): the game plays every set at one volume for a band, so a bigger blast must be louder,
heavier and longer in its file (loudness.py --ladder checks it). The nuke near is made louder than the large near (the
sub-bass that only took headroom cut, the body lifted under a look-ahead limiter); the Tsar near is rebuilt on a steady
roar without thunder's marks (a roar swelling about once a second, a second clap, a long tearing crack), with the
wreckage of what it knocks down from the first second and the blast wind; the Tsar mid and far get a deep boom. The
medium near and mid are swapped, as the user asked.

Every cut names its recording (sources/ledger.json) and where in it; the windows were picked from survey.py's sheets.
"""
from __future__ import annotations

import numpy as np

import sources as R
import synth as S

# --- the cuts: (recording, from s, to s, the quiet stretch its background is measured on) ---------------------------------------
CUTS = {
    "oxy": ("oxyhydrogen_1l", 0.0, 4.5, None),                 # a 1 L oxyhydrogen detonation: a sharp crack, a long open tail
    "firecracker": ("firecracker_street", 0.0, 2.2, None),     # a firecracker in a street: snap and slapback
    "firework": ("firework_shell", 0.0, 1.04, None),           # a firework shell bursting: the boomy body of a small charge
    "forest": ("detonation_500m_forest", 0.0, 1.75, None),     # a small charge heard 500 m off through a pine forest
    "tnt": ("tnt_landmines", 2.0, 9.0, (0.3, 1.9)),            # 4 kg of TNT (landmine clearance): blast and roar (camcorder)
    "mortar": ("mortar_line", 88.25, 90.8, (85.0, 88.0)),       # a mortar firing, heard on the range: crack and open tail
    "chimney": ("chimney_demolition", 5.1, 10.5, (0.5, 4.9)),   # a power-station chimney blown: blast and ground rumble
    "chimney_fall": ("chimney_demolition", 11.0, 13.25, (0.5, 4.9)),  # ... and the chimney landing
    "collapse": ("building_implosion", 119.4, 124.0, (104.0, 111.5)),  # a six-storey building collapsing (a truck idled by)
    "stones": ("stones_falling", 15.0, 21.5, (3.4, 4.4)),       # stones raining down
    "gravel": ("gravel_debris", 0.0, 3.39, None),               # gravel and dirt falling
    "rocks": ("rocks_falling", 0.15, 1.23, None),               # a few rocks clattering down
}
#: Rumble under some recordings (wind on the microphone, a recorder's hum): cut below this many Hz.
HIGHPASS = {"mortar": 70, "stones": 110, "gravel": 90, "rocks": 60, "collapse": 35, "forest": 30}
#: Blasts whose tails darken as they die (the highs go first; it also buries what is left of a camcorder's hiss, which its
#: automatic gain pumps back up a second after the bang): from, to (s), cutoff from, to (Hz).
DARKEN = {"tnt": (0.25, 3.0, 14000, 2500),
          "chimney": (0.3, 3.0, 12000, 2500)}


def take(name: str, speed: float = 1.0, aligned: bool = True) -> np.ndarray:
    """A cut, cleaned, aligned on its bang and played at {speed} (below 1: a bigger, slower event)."""
    key, a, b, noise = CUTS[name]
    x = R.cut(key, a, b, noise=noise)
    if name in HIGHPASS:
        x = S.highpass(x, HIGHPASS[name], 4)
    if aligned:
        x = R.align(x)
    if name in DARKEN:
        x = R.darken(x, *DARKEN[name])
    return R.varispeed(x, speed) if speed != 1.0 else x


def roar(length: float, lp_from: float, lp_to: float, seed: int) -> np.ndarray:
    """The low, turbulent roar under a huge blast (the old synthesized layer, kept as the bed under the recordings)."""
    n = int(length * S.SR)
    x = S.brown(length, seed) * 0.8 + S.pink(length, seed + 1) * 0.3
    x = S.sweep_lowpass(x, lp_from, lp_to)
    env = S.breakpoints(n, [(0, 0), (0.05, 1.0), (length * 0.2, 0.8), (length * 0.6, 0.3), (length, 0)])
    return x * env


def distant(x: np.ndarray, metres: float, tail: float, mix: float, damping: float, seed: int) -> np.ndarray:
    """{x} carried {metres} further: the air's absorption, the ground's dip, and an open-air tail ({tail} s)."""
    y = R.ground(R.air(x, metres), metres)
    return S.reverb(y, tail, damping, mix, seed=seed, predelay=min(0.12, metres / 4000))


def lvl(x: np.ndarray, ref: np.ndarray, db: float) -> np.ndarray:
    """{x} set {db} below (or above) {ref}'s loudness."""
    return R.match(x, R.rms_db(ref) + db)


# --- round 2's helpers ----------------------------------------------------------------------------------------------------------
def thump(length: float, f0: float, f1: float, decay: float) -> np.ndarray:
    """The low "whump" a blast's mass of air makes: a sine falling in pitch, saturated (the old synthesized recipe's thump,
    which the user's ears preferred for weight)."""
    return S.saturate(S.sine_sweep(length, f0, f1, curve=2.5) * S.exp_decay(int(length * S.SR), decay) * 1.6, 1.8)


def old() -> object:
    """The synthesized recipes round 1 replaced (recipes.py): the user preferred their weight for the big blasts, so in
    round 2 they are the bed the recordings lie on."""
    import recipes  # noqa: PLC0415  (lazily: recipes imports this module)
    return recipes


def old_band(near: np.ndarray, band: str) -> np.ndarray:
    """The old recipe's way of hearing a near blast from further off (synth.distant), as its sets were made."""
    return S.normalize(S.distant(near, band), -1.0)


# --- round 3's helpers ----------------------------------------------------------------------------------------------------------
def gain(db: float) -> float:
    return 10 ** (db / 20)


def steady_bed(seed: int = 11, length: float = 16.0) -> np.ndarray:
    """The Tsar's old boom and roar (recipes.tsar_source) without what made it sound like lightning (the user, round 2):
    its roar rolled in swells a second apart, a second clap followed the first, a crackle ran through it and its crack was
    a long tearing burst, all marks of close thunder. Here the roar is steady, the crack short and single, and what falls
    after it is real wreckage (nuclear_near)."""
    n = int(length * S.SR)
    crack = S.saturate(S.highpass(S.white(0.12, seed) * S.exp_decay(int(0.12 * S.SR), 0.02), 90) * 3.4, 3.2)
    # (the boom ends at 22 Hz, not 15, and the roar keeps more of its mids: the old one's depth was mostly below what a
    # speaker plays, which cost loudness and gave nothing back)
    boom = S.saturate(S.sine_sweep(5.0, 60, 22, curve=2.2) * S.exp_decay(int(5.0 * S.SR), 1.6) * 1.8, 2.0)
    roar = S.sweep_lowpass(S.brown(length, seed + 1) * 0.85 + S.pink(length, seed + 2) * 0.5, 2600, 160)
    roar = roar * S.breakpoints(n, [(0, 0), (0.03, 1.0), (1.2, 0.95), (4.0, 0.65), (9.0, 0.3), (13.0, 0.1), (length, 0)])
    clods = S.lowpass(S.grains(length, 8, 50, seed + 4, decay=5.0), 350) * 0.7
    x = S.mix((S.place(crack, 0, length), 0.9), (S.place(boom, 0, length), 1.0), (roar, 0.95), (S.place(clods, 1.2, length), 0.5))
    return S.limit(S.normalize(S.reverb(x, 4.0, 1800, 0.25, seed=seed + 5), 0), -1.0)


def blast_wind(seed: int, peak: float, decay: float) -> np.ndarray:
    """The blast wind: the air the shock front throws outward, rushing past for seconds after it, a gale that darkens as
    it slows (a blast has one; thunder has not)."""
    length = peak + decay + 0.2
    n = int(length * S.SR)
    x = S.sweep_lowpass(S.highpass(S.pink(length, seed), 120, 2), 3500, 400)
    gust = S.lowpass(S.white(length, seed + 1), 1.5, 2)
    gust = gust / (np.max(np.abs(gust)) + 1e-12)
    env = S.breakpoints(n, [(0, 0), (0.05, 0.35), (peak, 1.0), (peak + decay * 0.35, 0.45), (peak + decay, 0.0), (length, 0.0)])
    return x * env * (1 + 0.15 * gust)


def wreckage(total: float, ref: np.ndarray, plan: list[tuple[str, float, float, float, float]]) -> np.ndarray:
    """What a blast knocks down, landing for seconds after it: (cut, speed, at s, dB under {ref}'s loudness, lowpass Hz or 0)."""
    parts = []
    for name, speed, at, db, lowpass in plan:
        x = take(name, speed, aligned=name == "rocks")
        if lowpass:
            x = S.lowpass(x, lowpass, 2)
        parts.append((lvl(x, ref, db), at, 1.0))
    return R.layer(total, *parts)


# --- the sets ------------------------------------------------------------------------------------------------------------------
# Round 1 (the user's ratings, 2026-10-26): the small set and the medium near, far and very far were better than the old;
# everything with thunder in it (the large near and very far, all of the nuclear and Tsar) sounded "like lightning", the
# chimney "way too weak", the factory demolition had clapping and crowd in it. Round 2: no thunder or lightning anywhere; the
# big blasts on the old recipes' boom and roar, the recordings over them; more weight where it was too weak.
# Round 2 (2026-10-27): kept but for the medium near and mid ("swap" them), the nuke near ("a bit weak in comparison to the
# near large blast"), the Tsar near ("still sounds a bit like lightning") and the Tsar mid and far ("bit weak"): round 3.
def small() -> dict[str, np.ndarray]:
    oxy, cracker, shell = take("oxy"), take("firecracker"), take("firework")
    punch = S.lowpass(R.friedlander(0.004, 0.25), 1200, 2)
    blast = R.layer(3.4, (oxy, 0, 1.0), (lvl(cracker, oxy, -4), 0, 1.0), (lvl(shell, oxy, -6), 0, 1.0), (lvl(punch, oxy, -3), 0, 1.0))
    # (what it throws up comes down after: thrown 5-10 m up, a second or so)
    debris = R.layer(3.4, (lvl(take("rocks"), oxy, -17), 0.8, 1.0), (lvl(take("gravel", aligned=False), oxy, -15), 1.1, 1.0))
    # Round 2: a grenade, not a firecracker: more weight under the near one (a low whump, a harder pulse).
    weight = R.layer(3.4, (lvl(thump(0.8, 120, 45, 0.2), oxy, -5), 0, 1.0), (lvl(punch, oxy, -4), 0, 1.0))
    near = R.punch(blast + debris + weight, 3)
    mid = distant(blast + debris * 0.4, 14, 1.4, 0.3, 3000, 21)
    far = distant(blast, 55, 1.8, 0.45, 1500, 22)
    forest = take("forest", 0.9)
    very_far = R.layer(3.0, (forest, 0, 1.0), (lvl(distant(blast, 175, 2.2, 0.55, 800, 23), forest, -3), 0, 1.0))
    return {"near": R.finish(near, tail=0.6), "mid": R.finish(mid, tail=0.8), "far": R.finish(far, tail=1.0),
            "very_far": R.finish(very_far, tail=1.0)}


def medium() -> dict[str, np.ndarray]:
    tnt, mortar = take("tnt"), take("mortar", 0.85)
    punch = S.lowpass(R.friedlander(0.010, 0.4), 900, 2)
    close = R.layer(7.0, (tnt, 0, 1.0), (lvl(punch, tnt, -2), 0, 1.0), (lvl(take("oxy"), tnt, -10), 0, 1.0))
    # (thrown 20-30 m up, its debris lands 2 to 5 s after the bang)
    debris = R.layer(7.0, (lvl(take("rocks"), tnt, -14), 1.6, 1.0), (lvl(take("stones", aligned=False), tnt, -12), 2.0, 1.0),
                     (lvl(take("gravel", aligned=False), tnt, -16), 2.8, 1.0))
    close_by = close + debris                                  # (round 1's near)
    # Round 2: the TNT the user liked close up, carried 50 m (the factory recording had clapping in it), with its whump.
    boom = thump(1.4, 95, 34, 0.4)
    heavy = R.punch(R.layer(7.0, (distant(close, 40, 1.6, 0.35, 2500, 33), 0, 1.0), (lvl(boom, close, -5), 0, 1.0),
                            (debris * 0.12, 0, 1.0)), 3)       # (round 2's mid)
    # Round 2: "too weak": the whump under it and more of the TNT, slowed a little, carried 140 m.
    far = R.punch(R.layer(7.0, (mortar, 0, 1.0), (lvl(distant(take("tnt", 0.8), 140, 2.5, 0.45, 1200, 31), mortar, -2), 0, 1.0),
                          (lvl(R.air(thump(1.6, 80, 30, 0.45), 140), mortar, -3), 0, 1.0)), 4)
    forest = take("forest", 0.7)
    very_far = R.layer(6.0, (forest, 0, 1.0), (lvl(distant(tnt, 440, 3.0, 0.6, 600, 32), forest, -3), 0, 1.0))
    # Round 3: the user swapped the near and the mid (round 2's mid sounded like the near one). The near is trimmed 1.5 dB
    # to stay under the large near: the game plays every set at one volume for a band.
    return {"near": R.finish(heavy, tail=1.2) * gain(-1.5), "mid": R.finish(close_by, tail=1.0),
            "far": R.finish(far, tail=1.2), "very_far": R.finish(very_far, tail=1.5)}


def large() -> dict[str, np.ndarray]:
    bed = old().blast_source("large", 320)                     # the old large blast: crack, whump, roar, debris
    tnt = S.lowpass(take("tnt", 0.7), 7000, 2)
    punch = S.lowpass(R.friedlander(0.025, 0.6), 400, 2)
    blast = R.layer(9.0, (bed, 0, 1.0), (lvl(tnt, bed, -3), 0, 1.0), (lvl(punch, bed, -4), 0, 1.0),
                    (lvl(S.lowpass(take("collapse", aligned=False), 1200, 2), bed, -10), 0.8, 1.0))
    debris = R.layer(9.0, (lvl(take("stones", aligned=False), bed, -14), 2.4, 1.0), (lvl(take("chimney_fall", aligned=False), bed, -14), 3.2, 1.0))
    near = R.punch(blast + debris, 3)
    mid = R.punch(R.layer(10.0, (old_band(bed, "mid"), 0, 1.0), (lvl(distant(blast, 150, 2.5, 0.4, 1800, 44), old_band(bed, "mid"), -3), 0, 1.0)), 3)
    slow = take("chimney", 0.85)                               # (round 1's far one, which the user liked, as it was)
    far = R.layer(8.0, (distant(slow, 350, 3.5, 0.5, 1000, 41), 0, 1.0), (lvl(distant(take("mortar", 0.6), 300, 3.0, 0.5, 900, 42), slow, -6), 0, 1.0))
    vfar_bed = old_band(bed, "very_far")
    very_far = R.layer(12.0, (vfar_bed, 0, 1.0), (lvl(distant(blast, 1300, 5.0, 0.7, 400, 43), vfar_bed, -4), 0, 1.0))
    return {"near": R.finish(near, tail=1.5), "mid": R.finish(mid, tail=2.0), "far": R.finish(far, tail=2.0),
            "very_far": R.finish(very_far, tail=2.5)}


def nuclear_blast(bed: np.ndarray, scale: float, length: float, second_wave: bool) -> np.ndarray:
    """Round 2's nuclear blast before distance, on the old recipe's boom and roar ({bed}), the recordings slowed by {scale}
    over it (1: the nuke; below 1, slower still: the Tsar). Still what the bands the user rated well carry."""
    core = S.lowpass(take("tnt", 0.45 * scale), 5000, 2)
    shove = S.lowpass(R.friedlander(0.09 / scale, 1.6 / scale), 200, 2)
    parts = [(bed, 0, 1.0), (lvl(core, bed, -4), 0, 1.0), (lvl(shove, bed, -3), 0, 1.0),
             (lvl(S.lowpass(take("collapse", 0.6 * scale, aligned=False), 800, 2), bed, -8), 1.0, 1.0),
             (lvl(take("stones", 0.8, aligned=False), bed, -16), 3.5 / scale, 1.0)]
    if second_wave:                                       # the shock off the ground, a moment after the first
        parts.append((lvl(S.lowpass(take("tnt", 0.4 * scale), 4000, 2), bed, -8), 2.1, 1.0))
    blast = R.layer(length, *parts)
    return R.darken(blast, 0.4 / scale, 6.0 / scale, 9000, 1500)


def heard_from(bed: np.ndarray, blast: np.ndarray, band: str, metres: float, seed: int, db: float, drive: float = 2.0,
               boom: np.ndarray | None = None, boom_db: float = 0.0, lift: float | None = None) -> np.ndarray:
    """A nuclear blast heard from further off (round 2): the old recipe's band, with {blast} carried {metres} under it
    ({db} dB), driven {drive} dB. Round 3 (the Tsar's, "a bit weak") can add a deep {boom} under both ({boom_db} dB),
    carried as far, and {lift} it that many dB under the look-ahead limiter first."""
    tail = {"mid": 4.0, "far": 6.0, "very_far": 7.0}[band]
    old_one = old_band(bed, band)
    carried = distant(blast, metres, tail, 0.6, 600, seed + len(band))
    parts = [(old_one, 0, 1.0), (lvl(carried, old_one, db), 0, 1.0)]
    if boom is not None:
        parts.append((lvl(R.air(boom, metres), old_one, boom_db), 0, 1.0))
    x = R.layer(len(old_one) / S.SR + 1.0, *parts)
    if lift is not None:
        x = R.maximize(R.tame_lows(x), lift)
    return R.finish(R.punch(x, drive), tail=2.5 + (1.0 if band != "mid" else 0.0))


def nuclear_near(bed: np.ndarray, size: float, length: float, metres: float, wreck: list[tuple[str, float, float, float, float]],
                 wind_db: float, lift: float, drive: float, seed: int) -> np.ndarray:
    """Round 3's near nuclear blast, which must sound bigger than the large one (the user: the nuke near was "a bit weak in
    comparison to the near large blast"; the game plays every set at one volume for a band): the bed's boom and roar, the
    TNT slowed under it ({size} 1: the nuke; above 1, slower and longer: the Tsar) and the pressure shove held down (it
    took the headroom), darkening as it rolls and carried {metres}; on top, the TNT nearly as recorded, its front left
    sharp (a strong shock's front stays steep where the air dulls ordinary sound); the wreckage of what it knocks down
    ({wreck}, landing close by, so not dulled either) and the blast wind. Then made loud: the sub-bass that only took
    headroom taken out, lifted {lift} dB under the look-ahead limiter, driven {drive} dB into the soft one."""
    body = S.lowpass(take("tnt", 0.45 / size), 5000, 2)
    front = S.fade(take("tnt", 0.85)[: int(0.7 * S.SR)], 0.0, 0.35)
    shove = S.lowpass(R.friedlander(0.09 * size, 1.6 * size), 200, 2)
    blast = R.layer(length, (bed, 0, 1.0), (lvl(body, bed, -2), 0, 1.0), (lvl(shove, bed, -8), 0, 1.0))
    x = R.air(R.darken(blast, 0.4 * size, 6.0 * size, 9000, 1500), metres)
    x = x + R.layer(length, (lvl(front, bed, -1), 0, 1.0)) + wreckage(length, bed, wreck)
    x = x + R.layer(length, (lvl(blast_wind(seed, 0.5 * size, 4.0 * size), bed, wind_db), 0.05, 1.0))
    return R.finish(R.punch(R.maximize(R.tame_lows(x), lift), drive), tail=2.0)


#: What the near nuclear blasts knock down: (cut, speed, at s, dB, lowpass Hz or 0); the Tsar's lasts longer, more of it.
NUKE_WRECK = [("collapse", 0.8, 0.9, -8, 2500), ("rocks", 0.9, 1.8, -15, 0), ("chimney_fall", 0.9, 2.6, -12, 0),
              ("stones", 1.0, 3.0, -13, 0), ("gravel", 1.0, 4.2, -15, 0)]
TSAR_WRECK = [("collapse", 0.75, 0.8, -6, 3500), ("rocks", 0.85, 1.4, -11, 0), ("chimney_fall", 0.8, 2.2, -10, 0),
              ("stones", 0.9, 3.4, -12, 0), ("collapse", 0.85, 3.6, -10, 3000), ("gravel", 0.9, 5.0, -14, 0),
              ("stones", 1.0, 7.2, -16, 0)]


def nuke() -> dict[str, np.ndarray]:
    bed = old().nuke_source()
    blast = nuclear_blast(bed, 1.0, 12.0, False)
    # Round 3: the near one bigger than the large near; the rest as rated (round 2's).
    return {"near": nuclear_near(bed, 1.0, 12.0, 150, NUKE_WRECK, -12, 4.0, 4.0, 501),
            "mid": heard_from(bed, blast, "mid", 500, 501, -4), "far": heard_from(bed, blast, "far", 1400, 501, -4),
            "very_far": heard_from(bed, blast, "very_far", 4400, 501, -6)}


def tsar() -> dict[str, np.ndarray]:
    bed = old().tsar_source()
    blast = nuclear_blast(bed, 0.72, 16.0, True)
    # Round 3: the near one rebuilt on a steady roar ("still sounds a bit like lightning"); the mid and far "a bit weak":
    # a deep boom under them and driven harder; the very far as rated (round 2's).
    return {"near": nuclear_near(steady_bed(), 1.0 / 0.72, 16.0, 500, TSAR_WRECK, -8, 5.0, 5.0, 511),
            "mid": heard_from(bed, blast, "mid", 1700, 511, -2, 3.0, thump(4.0, 60, 25, 1.2), -2, lift=6.0),
            "far": heard_from(bed, blast, "far", 4800, 511, -2, 3.0, thump(5.0, 55, 22, 1.5), -1, lift=4.0),
            "very_far": heard_from(bed, blast, "very_far", 15000, 511, -6)}


def blast_sets() -> dict[str, np.ndarray]:
    """Every blast sound: blast/<set>_<band>."""
    out = {}
    sets = {"small": small(), "medium": medium(), "large": large(), "nuke": nuke(), "tsar": tsar()}
    for name, bands in sets.items():
        for band, x in bands.items():
            out[f"blast/{name}_{band}"] = x
    return out


if __name__ == "__main__":
    import sys
    from pathlib import Path
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from gb import paths  # noqa: E402
    for rel, x in blast_sets().items():
        p = S.save_ogg(x, paths.SOUNDS / f"{rel}.ogg")
        print(f"{rel}: {len(x) / S.SR:.1f} s, peak {20 * np.log10(np.max(np.abs(x)) + 1e-12):.1f} dB, rms {R.rms_db(x):.1f} dB")
