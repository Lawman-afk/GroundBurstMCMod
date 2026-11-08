"""A balanced advanced-circuit line, worked out from the mod's own recipes (src/generated/.../recipe): for each machine
its recipe, how often it runs, how busy it is, its power; the bulk inputs a minute; the generators and cable the line
needs. The line is sized round its slowest machine: one Clean Room printing microchips without a break.

    tools\\.venv\\Scripts\\python.exe tools\\factory\\circuit_line.py [--clean-rooms N]

The Photo factory (FxPhotographer's circuit_factory scene) is this line built: its numbers come from here.
"""
from __future__ import annotations

import argparse
import json
import math
import sys
from dataclasses import dataclass
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from gb import paths  # noqa: E402

RECIPES = paths.ROOT / "src" / "generated" / "resources" / "data" / "groundburst" / "recipe"
TICKS_PER_MIN = 1200
#: The generators the mod has (FE a tick while running; RtgBlockEntity, DieselGeneratorBlockEntity) and fuel use.
DIESEL_FE_T, SYNTHETIC_FUEL_FE_PER_MB = 180, 90
CABLES = {"Copper Cable": 256, "Insulated Cable": 1_024, "High-Voltage Cable": 8_192}


def recipe(path: str) -> dict:
    return json.loads((RECIPES / path).read_text(encoding="utf-8"))


@dataclass
class Stage:
    machine: str
    recipe: str
    runs_per_min: float
    time_ticks: int
    fe_t: int

    @property
    def busy(self) -> float:                       # the share of the time one machine runs
        return self.runs_per_min * self.time_ticks / TICKS_PER_MIN

    @property
    def machines(self) -> int:
        return max(1, math.ceil(self.busy - 1e-9))

    @property
    def avg_fe_t(self) -> float:
        return self.busy * self.fe_t


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--clean-rooms", type=int, default=1)
    a = ap.parse_args()

    adv, basic = recipe("fabricating/advanced_circuit.json"), recipe("fabricating/basic_circuit.json")
    chip, wafer = recipe("lithography/microchip.json"), recipe("pressing/silicon_wafer.json")
    boule, argon = recipe("crystal_pulling/silicon_boule.json"), recipe("cryo_condensing/argon.json")

    def count(r, ingredient):
        return next(i["count"] for i in r["inputs"] if i["ingredient"] == ingredient)

    def fluid(r, ingredient):
        return next(f["amount"] for f in r["fluids"] if f["ingredient"] == ingredient)

    # The Clean Room sets the pace: each run a wafer into four microchips.
    chip_runs = a.clean_rooms * TICKS_PER_MIN / chip["time"]
    chips = chip_runs * chip["outputs"][0]["item"]["count"]
    adv_runs = chips / count(adv, "groundburst:microchip")                       # each advanced circuit takes 2 chips
    basics = adv_runs * count(adv, "#groundburst:basic_circuits")
    basic_runs = basics / basic["outputs"][0]["item"]["count"]
    wafers = chip_runs * count(chip, "groundburst:silicon_wafer")
    boules = wafers / wafer["outputs"][0]["item"]["count"]
    argon_mb = boules * fluid(boule, "#c:argon")
    argon_runs = argon_mb / argon["fluid_outputs"][0]["amount"]

    stages = [
        Stage("Circuit Fabricator (advanced)", "2 basic circuits, 2 microchips, 2 gold wire, a rare earth -> 1", adv_runs, adv["time"], adv["energy_per_tick"]),
        Stage("Circuit Fabricator (basic)", "2 copper wire, 2 redstone, a plastic sheet -> 2", basic_runs, basic["time"], basic["energy_per_tick"]),
        Stage("Clean Room", "a wafer, a red phosphorus, a plastic sheet, 250 mB photoresist, 250 mB HF -> 4 microchips", chip_runs, chip["time"], chip["energy_per_tick"]),
        Stage("Industrial Press (diamond wire saw)", "a silicon boule -> 8 wafers", boules, wafer["time"], wafer["energy_per_tick"]),
        Stage("Crystal Puller", "4 polysilicon, a quartz crucible, 1000 mB argon -> a boule", boules, boule["time"], boule["energy_per_tick"]),
        Stage("Cryogenic Condenser (argon sieve)", "-> 250 mB argon", argon_runs, argon["time"], argon["energy_per_tick"]),
    ]
    print(f"Advanced circuits: {adv_runs:.2f} a minute ({adv_runs * 60:.0f} an hour), from {a.clean_rooms} Clean Room(s)\n")
    print(f"{'machine':38s} {'runs/min':>8s} {'busy':>6s} {'count':>5s} {'FE/t':>5s} {'avg FE/t':>8s}")
    for s in stages:
        print(f"{s.machine:38s} {s.runs_per_min:8.2f} {s.busy:6.0%} {s.machines:5d} {s.fe_t:5d} {s.avg_fe_t:8.1f}")
    peak = sum(s.fe_t * s.machines for s in stages)
    avg = sum(s.avg_fe_t for s in stages)
    diesels = math.ceil(peak / DIESEL_FE_T)
    print(f"\nPower: {avg:.0f} FE/t on average, {peak} FE/t if every machine runs at once")
    print(f"  {diesels} Diesel Generators ({diesels * DIESEL_FE_T} FE/t) cover the peak; "
          f"synthetic fuel {avg / SYNTHETIC_FUEL_FE_PER_MB * TICKS_PER_MIN:,.0f} mB a minute at the average load")
    trunk = next(n for n, r in CABLES.items() if r >= peak)
    print(f"  the trunk cable: {trunk} ({CABLES[trunk]} FE/t; Copper Cable carries {CABLES['Copper Cable']})")
    per_min = {
        "polysilicon": boules * count(boule, "groundburst:polysilicon"),
        "quartz crucible": boules * count(boule, "groundburst:quartz_crucible"),
        "red phosphorus": chip_runs * count(chip, "groundburst:red_phosphorus"),
        "plastic sheet": chip_runs * count(chip, "groundburst:plastic_sheet") + basic_runs * count(basic, "groundburst:plastic_sheet"),
        "copper wire": basic_runs * count(basic, "#c:wires/copper"),
        "redstone": basic_runs * count(basic, "#c:dusts/redstone"),
        "gold wire": adv_runs * count(adv, "#c:wires/gold"),
        "rare earth": adv_runs * count(adv, "#c:dusts/rare_earth"),
        "photoresist (mB)": chip_runs * fluid(chip, "#c:photoresist"),
        "hydrofluoric acid (mB)": chip_runs * fluid(chip, "#c:hydrofluoric_acid"),
        "argon (mB, made on site)": argon_mb,
    }
    print("\nBulk inputs (from the stores):        a minute     an hour")
    for k, v in per_min.items():
        print(f"  {k:32s} {v:8.2f} {v * 60:10.0f}")
    print(f"\nIn between: {chips:.0f} microchips, {basics:.0f} basic circuits and {wafers:.0f} wafers a minute; a boule every "
          f"{60 / boules:.0f} s")
    if a.clean_rooms == 1:
        built(per_min, avg)


#: What gametest/CircuitLine stocks the line with (its stores and tanks), to say how long each lasts.
BUILT_STORES = {
    "polysilicon": 64, "quartz crucible": 16, "red phosphorus": 64, "plastic sheet": 128, "copper wire": 128,
    "redstone": 128, "gold wire": 64, "rare earth": 64, "photoresist (mB)": 16_000, "hydrofluoric acid (mB)": 16_000,
}
BUILT_FUEL_MB = 3 * 16_000


def built(per_min: dict, avg_fe_t: float) -> None:
    """The line as gametest/CircuitLine lays it out, and how long what it is stocked with lasts."""
    print("\nAs built (gametest/CircuitLine): a Cryogenic Condenser, a Crystal Puller, an Industrial Press, a Clean Room,")
    print("two Circuit Fabricators, four Diesel Generators; five chests (four stores and the output), three Fluid Tanks")
    print("(photoresist, hydrofluoric acid, a three-block fuel tank); steel item pipe, copper fluid pipe, insulated cable.")
    print("Its stores last:")
    for k, n in BUILT_STORES.items():
        rate = per_min[k]
        print(f"  {k:32s} {n:7,} lasts {n / rate:6.0f} min")
    fuel = avg_fe_t / SYNTHETIC_FUEL_FE_PER_MB * TICKS_PER_MIN
    print(f"  {'synthetic fuel (mB)':32s} {BUILT_FUEL_MB:7,} lasts {BUILT_FUEL_MB / fuel:6.0f} min")


if __name__ == "__main__":
    main()
