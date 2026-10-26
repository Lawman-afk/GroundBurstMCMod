"""The machines, remastered from real recordings (DESIGN.md §13.1).

Every machine's working loop, the Assembly Bench's finishing clank, the Fission Reactor's alarm and the Geiger counter's
clicks. Machines that exist in the world are made from recordings of them or their nearest kin: a single-cylinder engine,
a stone crusher, arcs and welding, a punch press and a hydraulic press, a transformer's hum, an air compressor, a paint
mixer, a water pump, a CNC machine's stepper motors, a washing machine's spin, a diesel generator, a shower, the pump room
of a nuclear power plant, a physics lab's vacuum pump, hoist chains, a ratchet and an impact wrench, a fusion company's
factory alarm, a civil-defence Geiger counter.

Loops come in two kinds. A steady machine (the generators, crusher, furnace, pumps, centrifuge, shower, reactor) is a 4 s
loop of the recording itself, longer than the old 2 s so a real recording's repeat does not show. A machine whose sound is
timed to its model's animation (the presses, the Circuit Fabricator's gantry, the Canning Machine's nozzle, the Chemical
Plant's valve, the Assembly Bench's hoist, the charger's LEDs, the diesel's pistons) keeps its 2 s cycle, real events
placed at the moments the animation shows them. The machines nothing real sounds like (the Exotic Matter Fabricator, the
Singularity Reactor and its collapse) stay synthesized (recipes.py); the Fusion Reactor and the Particle Accelerator keep
their synthesized cores with real hum and pumps under them, as the old diesel keeps its knock under the real engine.
Each loop is as loud as the one it replaces (sources.loud_as), whose in-game volume was tuned to it.

Every cut names its recording (sources/ledger.json) and where in it; the windows were picked from survey.py's sheets.
"""
from __future__ import annotations

import numpy as np

import sources as R
import synth as S

# (recording, from s, to s, the quiet stretch its background is measured on[, options: hp/lp Hz, fade s, gain dB])
CUTS = {
    "engine": ("single_cylinder", 10.0, 14.6, None),                     # a single-cylinder engine clattering steadily
    "crusher": ("stone_crusher", 24.0, 28.6, None, {"hp": 30}),           # a stone crusher grinding, one heavy crush
    "arc": ("hv_sparks", 18.0, 22.6, None),                              # high-voltage arcs in a hall, steady
    "weld": ("arc_welding", 48.0, 52.6, None, {"hp": 150}),              # arc welding's bursts of crackle (its mains hum cut)
    "press_pump": ("hydraulic_press", 10.0, 13.2, None),                 # a hydraulic press's pump droning
    "stroke": ("punch_press", 9.2, 10.0, None, {"fade": 0.15}),          # a 20-ton punch press's blow and the ram's return
    "bubbles": ("bubbles_glass", 2.0, 6.6, None),                        # air bubbling up through water
    "hum": ("transformer_hum", 5.0, 9.6, None),                          # a transformer humming in a large room
    "compressor": ("air_compressor", 0.3, 4.9, None),                    # an air compressor's pistons
    "mixer": ("paint_mixer", 40.0, 43.0, None),                          # a paint mixer's motor and paddles
    "pump": ("water_pump", 3.0, 7.6, None),                              # a motor water pump, steady
    "whir_up": ("cnc_steppers", 24.5, 24.95, None, {"fade": 0.1}),       # a CNC stepper accelerating
    "whir_down": ("cnc_steppers", 27.0, 27.45, None, {"fade": 0.1}),     # ... and slowing
    "spin": ("spin_cycle", 60.0, 64.6, None),                            # a washing machine's drum at full spin
    "pour": ("liquid_pour", 4.3, 5.5, None, {"fade": 0.2}),              # liquid poured into a container, filling
    "diesel": ("diesel_generator", 20.0, 23.0, None),                    # a large diesel generator running
    "coil": ("coil_whine", 2.0, 5.0, None, {"hp": 300}),                 # electronics' coil whine
    "shower": ("shower", 1.0, 5.6, None),                                # a shower's spray
    "plant": ("npp_pump_room", 20.0, 24.6, None),                        # the pump room of a nuclear power plant
    "vacuum": ("scroll_pump", 100.0, 103.0, None),                       # a physics lab's scroll vacuum pump
    "chains": ("hoist_chains", 2.7, 3.35, None, {"fade": 0.1}),          # hoist chains rattling
    "ratchet": ("ratchet", 24.0, 24.38, None, {"fade": 0.05}),           # a ratchet's run of clicks
    "wrench": ("impact_wrench", 2.35, 2.6, None, {"fade": 0.06}),        # an impact wrench's burst
    "alarm": ("factory_alarm", 24.15, 25.05, None, {"fade": 0.05}),      # one buzz of a factory warning alarm
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
STEADY = 4.0                                                             # a steady machine's loop, s


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
    """A machine's loop being built: recordings laid under the whole of it (closed into a seamless loop, their tail faded
    into their head), then the things that happen at moments of it (a press's blow, a relay's tick) and the old
    synthesized loops the hybrids keep, added after the seam is closed and wrapping round its end. A sound at the loop's
    start is so neither swallowed by the crossfade nor doubled, and a periodic one is never crossfaded with itself."""

    def __init__(self, length: float = LOOP):
        self.length = length
        self.bed = np.zeros(int((PRE + length + XF) * S.SR))
        self.events: list[tuple[np.ndarray, float]] = []

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
        repeats = int(round(self.length / (len(loop) / S.SR)))
        return self.put(tiled(loop, repeats), 0.0, db)

    def close(self) -> np.ndarray:
        n = int(round(self.length * S.SR))
        out = S.seamless_loop(self.bed, self.length, XF, PRE)[:n].copy()
        for y, t in self.events:
            i = int(round(t * S.SR)) % n
            while len(y):
                k = min(len(y), n - i)
                out[i:i + k] += y[:k]
                y, i = y[k:], 0
        return out


def steady(*layers: tuple[np.ndarray, float]) -> Cycle:
    """A steady machine's {STEADY} s loop from recordings ((cut, dB) each, under the whole of it)."""
    c = Cycle(STEADY)
    for y, db in layers:
        c.lay(y, db)
    return c


# --- steady machines ------------------------------------------------------------------------------------------------------
def combustion_generator() -> Cycle:
    """A single-cylinder engine clattering steadily (the Combustion Generator burns fuel in one)."""
    return steady((take("engine"), -18))


def ore_crusher() -> Cycle:
    """A stone crusher grinding rock, with a heavy crush in each loop."""
    return steady((take("crusher"), -18))


def arc_furnace() -> Cycle:
    """An electric arc furnace: high-voltage arcs roaring and buzzing, welding's spitting crackle over them, the melt's low
    roar under them (synthesized: no recording of a melt)."""
    melt = S.lowpass(S.brown(PRE + STEADY + XF, 211), 300)
    return steady((take("arc"), -18), (take("weld"), -24), (melt, -26))


def electrolytic_separator() -> Cycle:
    """An electrolysis cell: water bubbling steadily, the supply transformer's soft hum under it."""
    return steady((take("bubbles"), -20), (S.lowpass(take("hum"), 400), -30))


def cryogenic_condenser() -> Cycle:
    """A cryogenic condenser: the compressor's pistons, and cryogen boiling off with a sigh every 2 s."""
    return steady((take("compressor"), -18)).put(take("nitrogen"), 0.6, -24).put(take("nitrogen"), 2.6, -24)


def water_pump() -> Cycle:
    """A motor water pump running steadily."""
    return steady((take("pump"), -18))


def gas_centrifuge() -> Cycle:
    """A gas centrifuge: the old synthesized rotor's high whine (900 Hz beating with 903) over a drum's real roar at full
    spin (a washing machine's, the nearest a recording comes)."""
    return steady((take("spin"), -22)).under(old("machine/gas_centrifuge"), -20)


def decontamination_shower() -> Cycle:
    """A decontamination shower: a hard spray of water, the pump under it."""
    return steady((take("shower"), -18), (take("pump"), -30))


def fission_reactor() -> Cycle:
    """The Fission Reactor: the pump room of a real nuclear power plant (Barsebaeck, Sweden)."""
    return steady((take("plant"), -18))


def rtg() -> Cycle:
    """An RTG: nearly silent. A faint transformer hum and, now and then, a real Geiger counter's click from the casing."""
    c = steady((S.lowpass(take("hum"), 600), -36))
    for t, k in ((0.7, 0), (2.3, 2), (3.4, 1)):
        c.put(click(k), t, -40)
    return c


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


def canning_machine() -> Cycle:
    """The Canning Machine, one 2 s canister (the nozzle's animation): the nozzle seating with a clunk at 0.3 s, fluid
    pouring in under the feed pump's hum, a valve snapping shut at 1.6 s and the line venting."""
    return (Cycle().lay(S.lowpass(take("pump"), 800), -32).put(take("click_b", 0.7, aligned=True), 0.3, -26)
            .put(take("pour"), 0.4, -22).put(take("flip", aligned=True), 1.6, -26).put(take("puff", 1.3), 1.65, -28))


def diesel_generator() -> Cycle:
    """The Diesel Generator: a real diesel running, with the old synthesized knock (its 4 Hz thumps match the pistons'
    0.5 s animation) under it."""
    return Cycle().lay(take("diesel"), -18).under(old("machine/diesel_generator"), -24)


def battery_charger() -> Cycle:
    """The Battery Charger: a transformer's hum, its electronics' whine, and a relay's tick each second (the LED chase)."""
    c = Cycle().lay(take("hum"), -24).lay(take("coil"), -32)
    for t in (0.0, 1.0):
        c.put(take("click_a", 1.4, aligned=True), t, -34)
    return c


def fusion_reactor() -> Cycle:
    """The Fusion Reactor: the old synthesized magnet hum and plasma roar (it swells twice a second with the streak's
    lap), with a real transformer's hum (the field coils' supply) and a compressor (the cryogenic pumps) under it."""
    return Cycle().lay(take("hum"), -26).lay(take("compressor"), -32).under(old("machine/fusion_reactor"), -18)


def particle_accelerator() -> Cycle:
    """The Particle Accelerator: the old synthesized magnet buzz and RF cavity tone (it swells once a second with the
    bunch's lap), with a physics lab's real vacuum pump and a transformer's hum under it."""
    return Cycle().lay(take("vacuum"), -26).lay(take("hum"), -30).under(old("machine/particle_accelerator"), -19)


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
    """The Fission Reactor's critical alarm (1.9 s, played every 2 s while the core is critical): one long buzz of a real
    factory warning alarm (a fusion company's), played slower: deeper, and filling the 2 s as one blast and a pause."""
    return R.layer(1.9, (at(take("alarm", 0.75), -14), 0.0, 1.0))


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


LOOPS = {"machine/combustion_generator": combustion_generator, "machine/ore_crusher": ore_crusher,
         "machine/arc_furnace": arc_furnace, "machine/electrolytic_separator": electrolytic_separator,
         "machine/cryogenic_condenser": cryogenic_condenser, "machine/water_pump": water_pump,
         "machine/gas_centrifuge": gas_centrifuge, "machine/decontamination_shower": decontamination_shower,
         "machine/fission_reactor": fission_reactor, "machine/rtg": rtg,
         "machine/industrial_press": industrial_press, "machine/fusion_fuel_synthesizer": fusion_fuel_synthesizer,
         "machine/chemical_plant": chemical_plant, "machine/circuit_fabricator": circuit_fabricator,
         "machine/canning_machine": canning_machine, "machine/diesel_generator": diesel_generator,
         "machine/battery_charger": battery_charger, "machine/fusion_reactor": fusion_reactor,
         "machine/particle_accelerator": particle_accelerator, "machine/assembly_bench": assembly_bench}


def machine_set() -> dict[str, np.ndarray]:
    """Every machine sound made from recordings, each as loud as the synthesized one it replaces (the Exotic Matter
    Fabricator, the Singularity Reactor and its collapse stay synthesized)."""
    import loudness  # noqa: PLC0415
    out = {}
    for rel, make in LOOPS.items():
        target = loudness.measure_array(tiled(old(rel), 2), S.SR)["S-max"]
        out[rel] = level_loop(make().close(), target)
    for rel, x in {"machine/assembly_clank": assembly_clank(), "reactor/alarm": reactor_alarm(), **geiger_clicks()}.items():
        out[rel] = R.loud_as(x, loudness.measure_array(old(rel), S.SR)["S-max"])
    return out


if __name__ == "__main__":
    import sys
    from pathlib import Path
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from gz import paths  # noqa: E402
    for rel, x in machine_set().items():
        S.save_ogg(x, paths.SOUNDS / f"{rel}.ogg")
        print(f"{rel}: {len(x) / S.SR:.2f} s")
