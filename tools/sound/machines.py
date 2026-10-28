"""The machines, remastered from real recordings (DESIGN.md §13.1).

Every machine's working loop, the Assembly Bench's finishing clank, the Fission Reactor's alarm and the Geiger counter's
clicks. Machines that exist in the world are made from recordings of them or their nearest kin: a single-cylinder engine,
arcs and welding, a punch press and a hydraulic press, a transformer's hum, an air compressor, a paint mixer, a water
pump, a CNC machine's stepper motors, a washing machine's spin, a diesel generator, a shower, the pump room of a nuclear
power plant, hoist chains, a ratchet and an impact wrench, a fire extinguisher's discharge, a fusion company's factory
alarm, a civil-defence Geiger counter.

Loops come in two kinds. A steady machine (the generators, furnace, pumps, centrifuge, shower, reactor) loops about 4 s of
the recording itself, longer than the old 2 s so a real recording's repeat does not show. Nothing is faded at its seam
(round 1's cuts were, and every steady loop dipped at its seam: "the drop in the middle"), and its length is fitted so
the sound after its end leads on like the sound after its start (an engine's firing, a compressor's pistons in step). A
machine whose sound is timed to its model's animation (the presses, the Circuit Fabricator's gantry, the Canning
Machine's nozzle, the Chemical Plant's valve, the Assembly Bench's hoist, the charger's LEDs, the diesel's pistons) keeps
its 2 s cycle, real events placed at the moments the animation shows them.

Some stay synthesized (recipes.py): what nothing real sounds like (the Exotic Matter Fabricator, the Singularity Reactor
and its collapse) and, at the user's word, the Ore Crusher and the RTG ("just keep the original please, I like it").
Where the user preferred the old sound, or nothing real has it, the old is the base with recordings under it: the
centrifuge's rotor (retuned lower and softer, as the user asked), the Fusion Reactor's and the Particle Accelerator's
cores, the diesel's knock, the Canning Machine's fill. Each loop is as loud as the one it replaces (sources.loud_as),
whose in-game volume was tuned to it, unless the user asked otherwise (LEVEL_DB).

Every cut names its recording (sources/ledger.json) and where in it; the windows were picked from survey.py's sheets.
"""
from __future__ import annotations

import numpy as np
from scipy import ndimage, signal

import sources as R
import synth as S

# (recording, from s, to s, the quiet stretch its background is measured on[, options: hp/lp Hz, fade s, gain dB])
CUTS = {
    # The steady machines' beds: longer than their loop (its length is fitted, and the sound after its end compared) and
    # faded only at the very end, so no fade reaches the seam.
    "engine": ("single_cylinder", 10.0, 15.4, None, {"fade": 0.05}),      # a single-cylinder engine clattering steadily
    "arc": ("hv_sparks", 18.0, 23.4, None, {"fade": 0.05}),               # high-voltage arcs in a hall, steady
    "weld": ("arc_welding", 48.0, 53.4, None, {"hp": 150, "fade": 0.05}),  # arc welding's crackle (its mains hum cut)
    "bubbles": ("bubbles_glass", 2.0, 7.4, None, {"fade": 0.05}),         # air bubbling up through water
    "hum": ("transformer_hum", 5.0, 10.4, None, {"fade": 0.05}),          # a transformer humming in a large room
    "compressor": ("air_compressor", 0.3, 5.4, None, {"fade": 0.05}),     # an air compressor's pistons (all of the take)
    "pump": ("water_pump", 3.0, 8.4, None, {"fade": 0.05}),               # a motor water pump, steady
    "spin": ("spin_cycle", 60.0, 65.4, None, {"fade": 0.05}),             # a washing machine's drum at full spin
    "shower": ("shower", 1.0, 6.4, None, {"fade": 0.05}),                 # a shower's spray
    "plant": ("npp_pump_room", 20.0, 25.4, None, {"fade": 0.05}),         # the pump room of a nuclear power plant
    # The timed machines' parts, and the one-shots'.
    "press_pump": ("hydraulic_press", 10.0, 13.2, None),                 # a hydraulic press's pump droning
    "stroke": ("punch_press", 9.2, 10.0, None, {"fade": 0.15}),          # a 20-ton punch press's blow and the ram's return
    "mixer": ("paint_mixer", 40.0, 43.0, None),                          # a paint mixer's motor and paddles
    "whir_up": ("cnc_steppers", 24.5, 24.95, None, {"fade": 0.1}),       # a CNC stepper accelerating
    "whir_down": ("cnc_steppers", 27.0, 27.45, None, {"fade": 0.1}),     # ... and slowing
    "fill": ("extinguisher", 4.7, 6.3, None, {"fade": 0.1}),             # a fire extinguisher's steady discharge
    "diesel": ("diesel_generator", 20.0, 23.0, None),                    # a large diesel generator running
    "chains": ("hoist_chains", 2.7, 3.35, None, {"fade": 0.1}),          # hoist chains rattling
    "ratchet": ("ratchet", 24.0, 24.38, None, {"fade": 0.05}),           # a ratchet's run of clicks
    "wrench": ("impact_wrench", 2.35, 2.6, None, {"fade": 0.06}),        # an impact wrench's burst
    "alarm": ("factory_alarm", 24.15, 25.05, None, {"fade": 0.05}),      # one buzz of a factory warning alarm
    "alarm_hold": ("factory_alarm", 25.65, 26.58, None, {"fade": 0.08}),  # ... and the next
    "puff": ("extinguisher", 0.66, 1.06, None, {"fade": 0.1}),           # a burst of gas
    "nitrogen": ("liquid_nitrogen", 20.6, 21.3, None, {"fade": 0.15}),   # cryogen boiling off
    "clank": ("heavy_metal_door", 6.35, 7.8, None, {"hp": 30}),          # a latch's clack and a heavy steel thud
    "clang": ("metal_clang", 0.12, 1.75, None),                          # struck steel, ringing
    "click_a": ("switches", 0.40, 0.62, None, {"hp": 150, "fade": 0.08}),  # a switch thrown
    "click_b": ("switches", 0.82, 1.05, None, {"hp": 150, "fade": 0.08}),  # ... and back
    "flip": ("arming_switch", 6.62, 6.84, (0.5, 3.0), {"fade": 0.08}),   # a heavy toggle
    "geiger": ("geiger_cd", 3.0, 14.5, None),                            # a Geiger counter's clicks (each cut alone: GEIGER)
}
#: A real Geiger counter's clicks (a civil-defence counter and its test source): when each one fires, in the recording.
GEIGER = (3.36, 8.18, 11.77, 14.17)

#: The loops' timing, as recipes.py's machine loops: {LOOP} s, events at t + {PRE}, the tail past the end faded in.
LOOP, PRE, XF = 2.0, 0.25, 0.15
STEADY = 4.0                                                             # a steady machine's loop, s (about: FIT)
#: A steady loop's length is fitted within STEADY ± FIT s: the one whose end leads on most like its start, judged on the
#: waveform across the crossfade (a pitch, a fast beat in phase) and the loudness over the next ENVELOPE s (a slow beat).
FIT, ENVELOPE = 0.25, 0.5
#: Loudness against the sound replaced, where the user asked for a change (dB): the charger calmer, the centrifuge's
#: whine softer (it was most of the old one's loudness).
LEVEL_DB = {"machine/battery_charger": -5.0, "machine/gas_centrifuge": -1.5}


def take(name: str, speed: float = 1.0, aligned: bool = False) -> np.ndarray:
    return R.take(CUTS, name, speed, aligned)


def at(x: np.ndarray, db: float) -> np.ndarray:
    """{x} at a loudness (RMS, dBFS): layers are set against each other by how loud they are, not by their peaks."""
    return R.match(x, db)


_OLD: dict[str, np.ndarray] = {}


def old(rel: str) -> np.ndarray:
    """The synthesized sound {rel} (recipes.py): the bed of the hybrids, and the loudness each remake matches."""
    if not _OLD:
        import recipes  # noqa: PLC0415  (lazily: recipes imports this module)
        _OLD.update(recipes.machine_loops())
        _OLD.update(recipes.assembly_sounds())
        _OLD.update(recipes.reactor_alarm())
        _OLD.update(recipes.geiger_clicks())
    return _OLD[rel]


def tiled(x: np.ndarray, times: int) -> np.ndarray:
    return np.concatenate([x] * times)


class Cycle:
    """A machine's loop being built: recordings laid under the whole of it (closed into a seamless loop, the sound
    after its end crossfaded into its start), then the things that happen at moments of it (a press's blow, a relay's
    tick) and the old synthesized loops the hybrids keep, added after the seam is closed and wrapping round its end. A
    sound at the loop's start is so neither swallowed by the crossfade nor doubled, and a periodic one is never
    crossfaded with itself. A steady machine's loop has its length fitted ({fit} s either way)."""

    def __init__(self, length: float = LOOP, fit: float = 0.0):
        self.length, self.fit, self.steady = length, fit, False
        after = max(XF, ENVELOPE) + 0.05 if fit else XF
        self.bed = np.zeros(int((PRE + length + fit + after) * S.SR))
        self.events: list[tuple[np.ndarray, float]] = []

    @property
    def seconds(self) -> float:
        """How long the bed is: what a layer laid under the loop must cover."""
        return len(self.bed) / S.SR

    def lay(self, y: np.ndarray, db: float) -> "Cycle":
        """{y} under the whole loop (at {db} dB loudness); it must be long enough to cover it."""
        assert len(y) >= len(self.bed), f"a bed {len(y) / S.SR:.2f} s under a {len(self.bed) / S.SR:.2f} s loop"
        self.bed = self.bed + at(y[: len(self.bed)], db)
        return self

    def put(self, y: np.ndarray, t: float, db: float) -> "Cycle":
        """{y} at {t} s of the loop (at {db} dB loudness)."""
        self.events.append((at(y, db), t))
        return self

    def under(self, loop: np.ndarray, db: float) -> "Cycle":
        """A loop of its own (the old synthesized one), repeated in step under this one: its start at this one's 0."""
        assert not self.fit, "a loop of fitted length cannot keep another loop in step"
        repeats = int(round(self.length / (len(loop) / S.SR)))
        return self.put(tiled(loop, repeats), 0.0, db)

    def close(self) -> np.ndarray:
        if self.fit:
            n, likeness = self.fitted()
            self.length = (n + 0.25) / S.SR
            out = join(self.bed, n, likeness)
        else:
            n = int(round(self.length * S.SR))
            out = S.seamless_loop(self.bed, self.length, XF, PRE)[:n].copy()
        for y, t in self.events:
            i = int(round(t * S.SR)) % n
            while len(y):
                k = min(len(y), n - i)
                out[i:i + k] += y[:k]
                y, i = y[k:], 0
        return out

    def fitted(self) -> tuple[int, float]:
        """The loop's length (samples, within its length ± fit) whose end leads on most like its start, and how alike
        the waveforms are across the crossfade there (0 to 1)."""
        bed, p, w = self.bed, int(PRE * S.SR), int(XF * S.SR)
        lo, hi = int((self.length - self.fit) * S.SR), int((self.length + self.fit) * S.SR)
        head, region = bed[p:p + w], bed[p + lo:p + hi + w]
        energy = np.convolve(region ** 2, np.ones(w), mode="valid")
        wave = signal.correlate(region, head, mode="valid") / (np.sqrt(energy * np.sum(head ** 2)) + 1e-12)
        hop = int(0.005 * S.SR)                                          # loudness in 5 ms steps, on a log scale
        frames = len(bed) // hop
        env = np.log(np.sqrt(np.mean(bed[:frames * hop].reshape(frames, hop) ** 2, axis=1)) + 1e-6)
        fp, fm = p // hop, int(ENVELOPE * S.SR) // hop
        h = env[fp:fp + fm] - env[fp:fp + fm].mean()
        offsets = np.arange(lo // hop, hi // hop + 2)
        alike = np.empty(len(offsets))
        for i, o in enumerate(offsets):
            seg = env[fp + o:fp + o + fm]
            seg = seg - seg.mean()
            alike[i] = np.dot(h, seg) / (np.linalg.norm(h) * np.linalg.norm(seg) + 1e-12)
        candidates = np.arange(lo, hi + 1)
        score = wave[:len(candidates)] + np.interp(candidates / hop, offsets, alike)
        best = int(np.argmax(score))
        return int(candidates[best]), float(np.clip(wave[best], 0.0, 1.0))


def join(bed: np.ndarray, n: int, likeness: float) -> np.ndarray:
    """bed[PRE:PRE+n] closed into a loop, the sound after its end crossfaded into its start. The two gains follow a curve
    that holds the level whatever the two sounds' {likeness}: equal power for unrelated sounds (noise, a spray), equal
    gain for the same sound in phase (a compressor's pistons, fitted in step), and between for between."""
    p, w = int(PRE * S.SR), int(XF * S.SR)
    body = bed[p:p + n].copy()
    tail = bed[p + n:p + n + w]
    u = 0.5 - 0.5 * np.cos(np.linspace(0, np.pi, w))
    a, b = u, 1 - u
    body[:w] = (body[:w] * a + tail * b) / np.sqrt(a ** 2 + b ** 2 + 2 * likeness * a * b)
    return body


def steady(*layers: tuple[np.ndarray, float], fit: float = FIT) -> Cycle:
    """A steady machine's loop, about {STEADY} s, from recordings ((cut, dB) each, under the whole of it)."""
    c = Cycle(STEADY, fit)
    c.steady = True
    for y, db in layers:
        c.lay(y, db)
    return c


def seam_dip(loop: np.ndarray, span: float = 0.3) -> float:
    """How much quieter a loop is across its seam (its last and first {span} s) than as a whole, dB. A steady loop must
    not dip there: round 1's dipped 6 to 15 dB, a fade-out and back every 4 s."""
    k = int(span * S.SR)
    return R.rms_db(loop) - R.rms_db(np.concatenate([loop[-k:], loop[:k]]))


# --- steady machines ------------------------------------------------------------------------------------------------------
def combustion_generator() -> Cycle:
    """A single-cylinder engine clattering steadily (the Combustion Generator burns fuel in one)."""
    return steady((take("engine"), -18))


def arc_furnace() -> Cycle:
    """An electric arc furnace: high-voltage arcs roaring and buzzing, welding's spitting crackle over them, the melt's low
    roar under them (synthesized: no recording of a melt)."""
    c = steady((take("arc"), -18), (take("weld"), -24))
    return c.lay(S.lowpass(S.brown(c.seconds, 211), 300), -26)


def electrolytic_separator() -> Cycle:
    """An electrolysis cell: water bubbling steadily, the supply transformer's soft hum under it."""
    return steady((take("bubbles"), -20), (S.lowpass(take("hum"), 400), -30))


def cryogenic_condenser() -> Cycle:
    """A cryogenic condenser: the compressor's pistons, and cryogen boiling off with a sigh every 2 s."""
    return steady((take("compressor"), -18)).put(take("nitrogen"), 0.6, -24).put(take("nitrogen"), 2.6, -24)


def water_pump() -> Cycle:
    """A motor water pump running steadily."""
    return steady((take("pump"), -18))


def rotor() -> np.ndarray:
    """The old synthesized centrifuge loop (recipes.gas_centrifuge_loop) with its rotor's whine retuned at the user's
    ask ("a little quieter or lower pitch so it doesn't hurt your ears as much"): 720 Hz beating with 722 (it was 900
    with 903), the octave fainter, no third harmonic; the motor's hum and the casing's hiss as they were. Whole cycles in
    its 2 s."""
    total = PRE + LOOP + XF
    t = S.t_axis(total)
    whine = (np.sin(2 * np.pi * 720 * t) + 0.8 * np.sin(2 * np.pi * 722 * t)) * 0.18 + np.sin(2 * np.pi * 1440 * t) * 0.03
    hum = (S.tone(total, 50) * 0.5 + S.tone(total, 100) * 0.25) * 0.25
    hiss = S.bandpass(S.white(total, 121), 4000, 9000) * 0.05
    return S.normalize(S.seamless_loop(S.mix((whine, 1.0), (hum, 1.0), (hiss, 1.0)), LOOP, XF, PRE), -9)


def gas_centrifuge() -> Cycle:
    """A gas centrifuge: the rotor's whine (the old synthesized one, lower and softer) over a drum's real roar at full
    spin (a washing machine's, the nearest a recording comes). Exactly 4 s, so the 2 s rotor stays in step."""
    return steady((take("spin"), -22), fit=0.0).under(rotor(), -27)


def decontamination_shower() -> Cycle:
    """A decontamination shower: a hard spray of water, the pump under it."""
    return steady((take("shower"), -18), (take("pump"), -30))


def fission_reactor() -> Cycle:
    """The Fission Reactor: the pump room of a real nuclear power plant (Barsebaeck, Sweden)."""
    return steady((take("plant"), -18))


# --- machines timed to their animation (2 s cycles) -----------------------------------------------------------------------
def industrial_press() -> Cycle:
    """The Industrial Press, one 2 s stroke (the ram's animation): the hydraulic pump droning, the ram's blow at 1.0 s (a
    real punch press), a hiss of released pressure."""
    return Cycle().lay(take("press_pump"), -22).put(take("stroke", aligned=True), 1.0, -16).put(take("puff"), 1.12, -28)


def fusion_fuel_synthesizer() -> Cycle:
    """The Fusion Fuel Synthesizer, one 2 s press cycle: the pump droning higher, the pellet press clunking down at 1.0 s
    (a heavy latch and thud), a sharp release of gas after, the feed hissing softly."""
    return (Cycle().lay(take("press_pump", 1.15), -24).put(take("clank", aligned=True), 1.0, -18)
            .put(take("puff"), 1.15, -24).put(take("nitrogen"), 0.1, -32))


def chemical_plant() -> Cycle:
    """The Chemical Plant, one 2 s cycle: the stirrer's motor and paddles (a paint mixer), slow deep bubbling, and the
    valve's hiss once a cycle (the model's)."""
    return Cycle().lay(take("mixer"), -22).lay(take("bubbles", 0.6), -28).put(take("puff"), 1.4, -26)


def circuit_fabricator() -> Cycle:
    """The Circuit Fabricator, one 2 s pick-and-place cycle (the gantry's animation): the clean box's blower humming, the
    gantry's stepper motors whirring as it travels (0.5 and 1.45 s), a tick at the pick (0.15 s) and a click at the
    placement (1.15 s)."""
    return (Cycle().lay(S.lowpass(take("hum"), 900), -30).put(take("whir_up"), 0.5, -24).put(take("whir_down"), 1.45, -24)
            .put(take("click_a", aligned=True), 0.15, -34).put(take("click_b", aligned=True), 1.15, -30))


def rising(x: np.ndarray, f0: float, f1: float, boost_db: float = 10.0, width: float = 0.35) -> np.ndarray:
    """{x} through a resonance gliding from {f0} to {f1} Hz as it plays ({width} octaves wide, {boost_db} dB): the air
    left in a container ringing higher as it fills."""
    f, t, X = signal.stft(x, S.SR, nperseg=1024, noverlap=768)
    fc = f0 * (f1 / f0) ** np.clip(t / t[-1], 0.0, 1.0)
    peak = np.exp(-0.5 * (np.log2((f[:, None] + 1.0) / fc[None, :]) / width) ** 2)
    _, y = signal.istft(X * (1.0 + (10 ** (boost_db / 20) - 1.0) * peak), S.SR, nperseg=1024, noverlap=768)
    return np.pad(y, (0, max(0, len(x) - len(y))))[:len(x)]


def canning_machine() -> Cycle:
    """The Canning Machine, one 2 s canister (the nozzle's animation), on the old synthesized cycle the user preferred
    (round 1's pour sounded "like a light trickle rather than a pressurised filling"): its pump's hum and the fluid's
    rush and gurgle, with a real pressurised discharge (a fire extinguisher's) driving the fill from 0.32 to 1.6 s,
    ringing higher as the canister fills; the real nozzle seating with a clunk at 0.3 s, a valve snapping shut at 1.6 s
    and the line venting."""
    fill = R.tail_fade(S.fade(rising(take("fill")[: int(1.3 * S.SR)], 380, 900), 0.04, 0), 0.06)
    return (Cycle().under(old("machine/canning_machine"), -21).put(fill, 0.32, -27)
            .put(take("click_b", 0.7, aligned=True), 0.3, -27).put(take("flip", aligned=True), 1.6, -27)
            .put(take("puff", 1.3), 1.65, -29))


def diesel_generator() -> Cycle:
    """The Diesel Generator: a real diesel running, with the old synthesized knock (its 4 Hz thumps match the pistons'
    0.5 s animation) under it."""
    return Cycle().lay(take("diesel"), -18).under(old("machine/diesel_generator"), -24)


def battery_charger() -> Cycle:
    """The Battery Charger, kept calm (the user: a charger is "a different case where you don't want as much realism so
    it's not as constantly annoying"): a transformer's hum, softened, and a soft relay tick each second with the LED
    chase; no electronics' whine. Quieter than the old (LEVEL_DB)."""
    c = Cycle().lay(S.lowpass(take("hum"), 300), -26)
    for t in (0.0, 1.0):
        c.put(S.lowpass(take("click_a", 1.4, aligned=True), 2500), t, -40)
    return c


def fusion_reactor() -> Cycle:
    """The Fusion Reactor: the old synthesized magnet hum and plasma roar (it swells twice a second with the streak's
    lap), with a real transformer's hum (the field coils' supply) and a compressor (the cryogenic pumps) under it."""
    return Cycle().lay(take("hum"), -26).lay(take("compressor"), -32).under(old("machine/fusion_reactor"), -18)


def particle_accelerator() -> Cycle:
    """The Particle Accelerator: the old synthesized magnet buzz and RF cavity tone, swelling once a second as the bunch
    laps the ring, with a real transformer's steady hum (the magnets' supply) under it. (Round 1 also laid a scroll
    vacuum pump under it, whose own swishes swelled twice more in each 2 s: the user heard "more than one particle
    group", the swell "not in a circle pattern".)"""
    return Cycle().lay(take("hum"), -30).under(old("machine/particle_accelerator"), -19)


def assembly_bench() -> Cycle:
    """The Ordnance Assembly Bench, one 2 s cycle (the hoist's animation): the hoist's chains rattling down (0-0.6 s),
    the casing clanking onto the cradle at 1.0 s, a ratchet tightening the bolts (1.15-1.5 s), an impact wrench's burst
    (1.55-1.75 s), the hoist motor humming under it all."""
    return (Cycle().lay(S.lowpass(take("pump"), 500), -34).put(take("chains"), 0.0, -24)
            .put(take("clank", 1.1, aligned=True), 1.0, -22).put(take("ratchet", aligned=True), 1.15, -26)
            .put(take("wrench", aligned=True), 1.55, -26))


# --- one-shots --------------------------------------------------------------------------------------------------------------
def assembly_clank() -> np.ndarray:
    """The bench finishing (or taking apart) a warhead: a latch's clack and a heavy steel thud, a ring of struck steel."""
    return R.layer(1.2, (at(take("clank", aligned=True), -18), 0.0, 1.0), (at(take("clang", 0.7, aligned=True), -26), 0.02, 1.0))


def reactor_alarm() -> np.ndarray:
    """The Fission Reactor's critical alarm, played every 2 s while the core is critical: one long buzz of a real factory
    warning alarm (a fusion company's), played slower so it is deeper, then the alarm's next buzz held steady about
    10 dB down until the next blast (the user found the on and off jarring: "maybe making a quieter version persist
    between the loud ones"). 2.25 s long: the quiet buzz runs on past the next blast's start, so no gap opens if it
    comes a tick late."""
    loud = at(take("alarm", 0.75), -14)
    hold = S.lowpass(at(level_out(take("alarm_hold", 0.75), 0.15, 1.05), -27), 3000)
    return R.tail_fade(R.layer(2.25, (loud, 0.0, 1.0), (hold, 0.95, 1.0)), 0.1)


def level_out(x: np.ndarray, start: float, end: float, window: float = 0.15) -> np.ndarray:
    """{x} held at one level from {start} to {end} s: its slow swells and sags evened out (a gain following its loudness
    over {window} s, at most 8 dB either way, eased in and out), the rest left as it was. A buzz that sags as its
    recording's air gives out, held steady."""
    k = int(window * S.SR)
    env = np.sqrt(ndimage.uniform_filter1d(x ** 2, k) + 1e-12)
    a, b = int(start * S.SR), int(end * S.SR)
    gain = np.ones_like(x)
    gain[a:b] = np.clip(np.median(env[a:b]) / env[a:b], 0.4, 2.5)
    return x * ndimage.uniform_filter1d(gain, int(0.05 * S.SR))


def click(k: int) -> np.ndarray:
    """One of a real Geiger counter's clicks: a civil-defence counter's tube firing ({GEIGER}[k])."""
    t = GEIGER[k]
    x = R.cut(CUTS["geiger"][0], t - 0.004, t + 0.03, fade_out=0.012)
    return S.highpass(x, 400, 4)


def geiger_clicks() -> dict[str, np.ndarray]:
    return {f"geiger/click{k + 1}": click(k) for k in range(4)}


def level_loop(loop: np.ndarray, target: float, ceiling_db: float = -1.0) -> np.ndarray:
    """A closed loop brought to a loudness (measured as it plays, repeated), any peaks over {ceiling_db} held by the
    look-ahead limiter run over three copies, the middle one kept: so the seam stays closed."""
    import loudness  # noqa: PLC0415
    for _ in range(5):
        diff = target - loudness.measure_array(tiled(loop, 2), S.SR)["S-max"]
        if abs(diff) < 0.3:
            break
        loop = loop * 10 ** (diff / 20)
        over = 20 * np.log10(np.max(np.abs(loop)) + 1e-12) - ceiling_db
        if over > 0:
            n = len(loop)
            loop = R.maximize(tiled(loop, 3), over, ceiling_db=ceiling_db)[n:2 * n]
    return loop


LOOPS = {"machine/combustion_generator": combustion_generator, "machine/arc_furnace": arc_furnace,
         "machine/electrolytic_separator": electrolytic_separator, "machine/cryogenic_condenser": cryogenic_condenser,
         "machine/water_pump": water_pump, "machine/gas_centrifuge": gas_centrifuge,
         "machine/decontamination_shower": decontamination_shower, "machine/fission_reactor": fission_reactor,
         "machine/industrial_press": industrial_press, "machine/fusion_fuel_synthesizer": fusion_fuel_synthesizer,
         "machine/chemical_plant": chemical_plant, "machine/circuit_fabricator": circuit_fabricator,
         "machine/canning_machine": canning_machine, "machine/diesel_generator": diesel_generator,
         "machine/battery_charger": battery_charger, "machine/fusion_reactor": fusion_reactor,
         "machine/particle_accelerator": particle_accelerator, "machine/assembly_bench": assembly_bench}


def machine_set() -> dict[str, np.ndarray]:
    """Every machine sound made from recordings, each as loud as the synthesized one it replaces (the Exotic Matter
    Fabricator, the Singularity Reactor and its collapse, the Ore Crusher and the RTG stay synthesized)."""
    import loudness  # noqa: PLC0415
    out = {}
    for rel, make in LOOPS.items():
        cycle = make()
        loop = cycle.close()
        if cycle.steady:
            dip = seam_dip(loop)
            assert dip < 4.0, f"{rel} is {dip:.1f} dB quieter across its seam than as a whole"
        target = loudness.measure_array(tiled(old(rel), 2), S.SR)["S-max"] + LEVEL_DB.get(rel, 0.0)
        out[rel] = level_loop(loop, target)
    for rel, x in {"machine/assembly_clank": assembly_clank(), "reactor/alarm": reactor_alarm(), **geiger_clicks()}.items():
        out[rel] = R.loud_as(x, loudness.measure_array(old(rel), S.SR)["S-max"] + LEVEL_DB.get(rel, 0.0))
    return out


if __name__ == "__main__":
    import sys
    from pathlib import Path
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from gb import paths  # noqa: E402
    for rel, x in machine_set().items():
        S.save_ogg(x, paths.SOUNDS / f"{rel}.ogg")
        print(f"{rel}: {len(x) / S.SR:.2f} s")
