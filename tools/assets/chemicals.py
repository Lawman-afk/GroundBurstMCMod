"""Chemistry items (DESIGN.md §6): canisters colour-coded by contents, powders on the shared vanilla dust outline,
and a few solids. Gameplay abstractions only: names and colours, nothing that describes a real process.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gb import palette as P  # noqa: E402
from gb import vanilla as V  # noqa: E402
from gb.palette import fixed  # noqa: E402
import parts as PT  # noqa: E402
import resources as R  # noqa: E402

YELLOWCAKE = fixed("yellowcake", "#3e2c04", "#6a4c08", "#9a700c", "#c89a16", "#e6be2a", "#f4d85a", "#fff0a8")
LITHIUM6 = fixed("lithium6", "#4a3a44", "#76606e", "#a08896", "#c4aeba", "#dccad4", "#ece0e6", "#fff8fc")
RDX = fixed("rdx", "#4c5258", "#747c84", "#9ea6ae", "#c2c9cf", "#dbe0e4", "#eceff2", "#ffffff")
SULFURIC = fixed("sulfuric", "#3c3a04", "#686408", "#94900c", "#c0ba18", "#dcd636", "#eeea70", "#fcfab8")
NITRIC = fixed("nitric", "#3e1c04", "#663008", "#90460c", "#b8621a", "#d4842e", "#e8a85a", "#f8d4a0")
FUEL = fixed("synfuel", "#2a1404", "#4c2408", "#74380c", "#a05414", "#c47426", "#e09a4a", "#f6cc8a")
ROCKET = fixed("rocket_fuel", "#3a0808", "#640e0e", "#901818", "#bc2626", "#dc4444", "#ee7a72", "#fcb8b0")
NAPALM = fixed("napalm", "#401404", "#6c2406", "#9c360a", "#c84e12", "#e47024", "#f29c52", "#fcd09a")
SLURRY = fixed("slurry", "#2c3034", "#464c52", "#646c74", "#868e96", "#a8b0b8", "#c8ced4", "#eef2f4")
TRITIUM = fixed("tritium", "#0c3a26", "#14583a", "#1e7a52", "#2c9c6c", "#48bc8a", "#7cd8ae", "#c4f4dc")
HEAVY_WATER = fixed("heavy_water", "#0a1c3c", "#12306a", "#1c4894", "#2a62b8", "#4a84d4", "#7aa8e6", "#c0d8f6")
CHLORINE = fixed("chlorine", "#2c4008", "#48660c", "#689012", "#88b41c", "#a8d430", "#c8e86a", "#eef8c0")
TOXIN = fixed("toxin", "#0c2a06", "#16480a", "#22680e", "#349014", "#4cb420", "#7cd83a", "#c0f480")
SALT = fixed("salt", "#6c6a66", "#8e8c88", "#b0aeaa", "#cecdca", "#e4e3e0", "#f2f1ee", "#ffffff")
UF6 = fixed("uf6", "#3c4410", "#5e6a1a", "#848f2a", "#a8b440", "#c6d062", "#dce48e", "#f2f6c8")


def canisters() -> None:
    c = PT.canister_sprite
    c("sulfuric_acid_canister", PT.CANISTER_ROWS, SULFURIC, P.HAZARD_BLACK[3])
    c("nitric_acid_canister", PT.CANISTER_ROWS, NITRIC, P.WHITE_CERAMIC[6])
    c("synthetic_fuel_canister", PT.CANISTER_ROWS, P.BLACK_PAINT, P.HAZARD_YELLOW[4])
    c("liquid_rocket_fuel_canister", PT.CANISTER_ROWS, ROCKET, P.WHITE_CERAMIC[6])
    c("thermobaric_slurry_canister", PT.CANISTER_ROWS, SLURRY, P.NUCLEAR_AMBER[4])
    c("napalm_canister", PT.CANISTER_ROWS, NAPALM, P.WARNING_RED[2])
    c("uf6_canister", PT.GAS_ROWS, UF6, P.HAZARD_BLACK[3])
    c("tritium_canister", PT.GAS_ROWS, TRITIUM, P.WHITE_CERAMIC[6])
    c("heavy_water_canister", PT.CANISTER_ROWS, HEAVY_WATER, P.WHITE_CERAMIC[6])
    c("chlorine_canister", PT.GAS_ROWS, CHLORINE, P.HAZARD_YELLOW[4])
    c("toxin_canister", PT.CANISTER_ROWS, TOXIN, P.HAZARD_BLACK[3])


def flask() -> None:
    rows = [
        "................",
        "......oooo......",
        "......o66o......",
        "......o44o......",
        ".....oo55oo.....",
        "....o566665o....",
        "...o56666654o...",
        "...o56666654o...",
        "...o45666543o...",
        "...o45555543o...",
        "...o44555443o...",
        "...o34444432o...",
        "....o333332o....",
        ".....oooooo.....",
        "................",
        "................",
    ]
    t, _ = PT.template(rows, {"o": P.STEEL.outline}, P.STEEL)
    t.px(6, 7, P.STEEL.shine)                    # mercury's mirror sheen on the iron flask
    t.px(7, 8, P.STEEL.shine)
    PT.save("mercury_flask", t)


def plastic_sheet() -> None:
    rows = [
        "................",
        "................",
        "................",
        "...oooooooooo...",
        "..o6655555554o..",
        "..o5555555544o..",
        ".o55555555544o..",
        ".o5555555544o...",
        ".o555555554o....",
        "o555555554o.....",
        "o44444444o......",
        "oooooooooo......",
        "................",
        "................",
        "................",
        "................",
    ]
    t, _ = PT.template(rows, {"o": P.WHITE_CERAMIC[2]}, P.WHITE_CERAMIC)
    PT.save("plastic_sheet", t)


def plastic_explosive() -> None:
    """A wrapped brick of putty with a label band."""
    rows = [
        "................",
        "................",
        "................",
        "......oooooooo..",
        "....oo66666655o.",
        "..oo6655555554o.",
        ".o665555555543o.",
        ".o55LLLLLLL433o.",
        ".o5LKKKKKKL4332o",
        ".o4LLLLLLLL3322o",
        ".o4444444443322o",
        ".o333333333222o.",
        "..oooooooooooo..",
        "................",
        "................",
        "................",
    ]
    t, _ = PT.template(rows, {"o": P.OLIVE.outline, "L": P.WHITE_CERAMIC[5], "K": P.BLACK_PAINT[3]}, P.OLIVE)
    PT.save("plastic_explosive", t)


def solid_propellant() -> None:
    """A cast propellant grain seen from above-left: a stubby cylinder whose top shows the star-shaped bore."""
    rows = [
        "................",
        "................",
        "................",
        ".....oooooo.....",
        "...oo666665oo...",
        "..o6665oo5554o..",
        "..o665o..o554o..",
        "..oo665oo5544o..",
        "..o5oo66555oo3o.",
        "..o554oooooo33o.",
        "..o5544444433o..",
        "..o5544444332o..",
        "..o4444443322o..",
        "...oo3333322o...",
        ".....oooooo.....",
        "................",
    ]
    t, _ = PT.template(rows, {"o": P.RUBBER[1]}, P.DESERT_TAN)
    PT.save("solid_propellant", t)


def powders() -> None:
    PT.save("yellowcake", V.dust2(YELLOWCAKE, 21))
    PT.save("lithium6_dust", V.dust2(LITHIUM6, 22, sparkle=True))
    PT.save("rdx_crystals", V.dust2(RDX, 23, sparkle=True))
    PT.save("salt", V.dust2(SALT, 24, sparkle=True))


def build() -> None:
    canisters()
    flask()
    plastic_sheet()
    plastic_explosive()
    solid_propellant()
    powders()


if __name__ == "__main__":
    build()
    print(R.item_sheet(PT.ITEMS, "item", "chemicals_review.png"))
