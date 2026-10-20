# Ground Zero — Progress

Status keys: `todo` · `in progress` · `done` · `needs in-game test` · `blocked`
Asset review keys (models/textures/sounds): `drafted → previewed → revised → approved`

**Current phase:** Phase 4 in progress (power ladder agreed: DESIGN §7) — slices 1–7 built (slice 7: Fission Reactor, Particle Accelerator; slice 7 feedback passes and the accelerator redesign done; the Field Manual slice done (book, screen, 3D structure view, 7 chapters / 41 entries); Canning Machine and Creative Fluid Tank added; slice 8 built (Fusion Reactor, Singularity Reactor; 7 chapters / 43 entries, 88/88 GameTests); Phase 4's power ladder is complete, awaiting the user's in-game check. Slices 1–6 built (slice 6: Diesel Generator, RTG, Capacitor Banks, Battery Charger, Decontamination Shower; 63/63 GameTests); earlier: slices 1–5 built (slice 5: Circuit Fabricator, Gas Centrifuge with cascade, Fusion Fuel Synthesizer, Exotic Matter Fabricator; 57/57 GameTests). Awaits the user's in-game check of slices 4–5; next: slice 6 (Diesel Generator, RTG, Capacitor Banks, Battery Charger, Decontamination Shower).
**Last updated:** 2026-10-20

---

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| 0 | Research & design: toolchain, instance, DESIGN.md, roadmap | **done** |
| 1 | Project skeleton, deploy task, registries framework, configs, creative tabs, networking base | **done** |
| 2 | Asset pipeline: texture lib, model DSL (block JSON + GeckoLib), preview renderer, sound synth → OGG, NBT writer, style guide, pilot assets | **done** (needs in-game listen of pilot sounds) |
| 3 | Resources & world generation | **done** (needs in-game test) |
| 4 | Power, cables, processing machines, multiblock framework, Fission Reactor, Particle Accelerator, Fusion and Singularity Reactors | **in progress** (slices 1–8 built; 6: diesel/RTG/capacitors/charger/decon; 7: Fission Reactor + Particle Accelerator; 8: Fusion + Singularity Reactors) — needs in-game test |
| 5 | Explosion engine, Spectacle visual system, distance-delayed sound | todo |
| 6 | Conventional explosives | todo |
| 7 | Components & Assembly Bench | todo |
| 8 | Nuclear weapons | todo |
| 9 | Launch platforms, missile flight, launch control, designators | todo |
| 10 | Radar & interceptors | todo |
| 11 | Special weapons (storm, singularity, antimatter, cryo, EMP, orbital, chemical, extras) | todo |
| 12 | Aftermath: radiation, fallout, EMP fields, terrain, wasteland, gear, medical | todo |
| 13 | Guns | todo |
| 14 | Superweapons + Zero Hour doctrine modules | todo |
| 15 | World structures & loot | todo |
| 16 | Field Manual & JEI | in progress (Field Manual built early, 2026-10-20; JEI categories exist since Phase 4) |
| 17 | Polish pass (every model, texture, sound, effect) + performance pass | todo |

Each phase ends with: build green, deployed jar, PROGRESS/SESSION_LOG updated, git commit, and an in-game test
checklist for the user.

---

## Phase 0 — Research & design
| Item | Status |
|---|---|
| Verify MC 26.3 / NeoForge / MDG / Java toolchain | done |
| Locate the CurseForge test instance and confirm versions (26.3 / NeoForge 26.3.0.92) | done |
| Library survey (GeckoLib ✔, JEI ✔, EMI ✘, Patchouli ✘, Modonomicon ✔) | done |
| Zero Hour research (generals & superweapon variants) | done |
| DESIGN.md v1 | done |
| Phased roadmap (this file) | done |
| Decisions D1–D6 (see DESIGN.md §29) | done |
| Go-ahead to start Phase 1 | done (2026-10-19) |

## Phase 1 — Skeleton & foundations
| Item | Status |
|---|---|
| Gradle project from official 26.3 MDK (MDG 2.0.148, Gradle 9.2.1, Java 25) | done |
| `deployToInstance` task (runs after `build`, removes old jars, copies new) | done (verified: jar landed in instance `mods/`) |
| `runClient` works in dev | done (reached title screen with the mod loaded) |
| git repository + first commit | done |
| Registry framework: `GZBlocks` / `GZItems` helpers that record English name + creative tab at registration (`GZLang`) | done |
| Further registers (BEs, entities, menus, sounds, particles, data components, attachments, recipes, datapack registries) | added with the first feature that needs each (no empty registers) |
| Configs: server (`SYNCED`, blast/radiation/terrain/missiles/EMP/reactor/singularity/superweapon limits) and client (fx, HUD) | done (values are read by their systems as those phases land) |
| Common config | moved to Phase 3 (first option: ore toggles) |
| Creative tabs (7, chained after Combat; hidden until they contain items; icon = first item) | done |
| Networking base (`GZNetwork` registrar + type helper; client handlers via `RegisterClientPayloadHandlersEvent`) | done (first payloads in Phase 4/5) |
| Datagen harness (`runData` → `src/generated/resources`; en_us generated from `GZLang`) | done (verified) |
| GameTest harness (TEST_FUNCTION registry + `RegisterGameTestsEvent`; NBT test structures from `tools/structure`) | done (3/3 pass: harness floor, names & tabs, vanilla) |
| Smoke test: build + deploy + dev client to title screen | done |
| Final mod name/ID applied | done (Ground Zero / `groundzero` confirmed) |

## Phase 2 — Asset pipeline
| Item | Status |
|---|---|
| Project Python venv (`tools/.venv`, `tools/requirements.txt`) | done |
| `tools/gz/palette.py` material ramps (OKLCH, hue-shifted) | done |
| `tools/gz/tex.py` pixel-art primitives (bevels, rivets, seams, stripes, decals, stencil font, trefoil/placards, seeded wear, glow layers) | done |
| `tools/gz/model.py` block/item model DSL (auto UV layout, face painting, display fitting) | done |
| `tools/gz/shapes.py` stepped cylinders with baked curvature lighting | done |
| `tools/gz/geo.py` GeckoLib geo + animation export (conventions verified against GeckoLib 5.5.7 source) | done (GeckoLib loads it: "Loaded 1 models and 1 animations") |
| `tools/preview/render.py` block-model renderer + contact sheets + true-size inventory icon | done |
| `tools/preview/geo_preview.py` GeckoLib renderer + animation frames | done |
| `tools/sound/synth.py`, `recipes.py`, `review.py` (synthesis, OGG, distance variants, spectrogram review) | done |
| `tools/structure/nbt.py` NBT writer + `template.py` structure templates | done (used for GameTest structures) |
| GeckoLib dependency + auto-install into the test instance | done |
| `GZSounds` registry + `sounds.json` datagen with subtitles | done |
| GUI texture toolkit (panels, CRT, gauges, buttons, bars) | moved to Phase 4 (built with the first machine GUI) |
| Style guide (`docs/style/`: palette swatches + pilot previews) | done |
| Pilot: uranium ore (stone + deepslate, 3-layer with glints) | previewed → revised ×3 → approved for pipeline; final pass with all ores in Phase 3 |
| Pilot: Fission Bomb block model (87 elements) | previewed → revised ×4; final pass in Phase 8 |
| Pilot: Ore Crusher GeckoLib model + "working" animation | previewed → revised ×1; final pass in Phase 4 |
| Pilot: sounds — Geiger clicks ×4, nuclear blast near/mid/far/very far | spectrogram-reviewed; **needs in-game listen** (`/playsound`) |

## Phase 3 — Resources & world generation
| Item | Status |
|---|---|
| Common config (`LOCAL`): per-resource worldgen toggles via `groundzero:config_toggle` placement filter | done |
| 16 ore blocks: uranium, lithium, sulfur (+nether), saltpeter (+sandstone), tungsten, lead, rare earth — stone & deepslate | needs in-game test (art approved; GameTests pass) |
| Raw items, ingots (U, Pb, W), sulfur/saltpeter dust, exotic shard | needs in-game test |
| Storage blocks (U, Pb, W) + raw-ore blocks (5) | needs in-game test |
| Voidstone, budding voidstone (slow growth), small/large exotic bud, exotic crystal cluster | needs in-game test (growth GameTest passes) |
| Ore features + placements + biome modifiers (incl. badlands/mountain/desert extras, Nether sulfur) | done (GameTests: features place ore; modifiers applied) |
| End void geodes (outer islands, anchored in end stone) | needs in-game test |
| Loot tables (Fortune, Silk Touch, explosion decay), smelting/blasting, storage recipes, black-powder gunpowder | done (drop GameTest passes) |
| Tags: tool tiers, `c:` ores/raw/ingots/dusts/gems/storage, `groundzero:radioactive` | done |
| Vanilla sulfur / cinnabar crushing | done in Phase 4 (Ore Crusher recipes) |
| Radiation strength data map for the radioactive tag | Phase 12 |
| Textures for all of the above (reviewed via `tools/out/previews/ores_review.png`, `items_review.png`, `blocks_review.png`) | done |

## Phase 4 — Power, machines, multiblocks
| Item | Status |
|---|---|
| GUI texture toolkit (riveted panels, CRT screens, gauges, guarded buttons, segmented bars, slots) | started: `tools/assets/gui.py` generates layout-driven machine GUIs + `MachineLayouts.java`; generic `MachineScreen` (energy bar, progress, flame, status lamp, redstone button, CRT readout, tooltips). Guarded buttons/gauges come with later machines |
| Machine base (FE, inventories, side config, redstone control, EMP hook, sounds, GUI base) | done (side config: per-face item mode + energy switch + auto-eject, GUI tab): `MachineBlockEntity`/`ProcessingBlockEntity`, `MachineBlock` (facing + active), `MachineMenu`, automation view (inputs in / outputs out), recipe-filtered inputs, redstone modes, status reasons, EMP hook, GeckoLib `AnimatedMachine` |
| Cables (copper, insulated, HV) with connected models | done (multipart models, network rate = weakest tier); insulated recipe added in slice 5; HV recipe waits (design lists 10 ingredients) |
| Combustion Generator, Diesel Generator, RTG, Capacitor Banks T1–T3 | done (slice 6: diesel twin with pistons/flywheel burning piped fuel; RTG with hot fins; three bank tiers with charge gauge, front output, charge kept in the item, comparator) — slice 6 needs in-game test |
| Ore Crusher | done (GeckoLib model, GUI, 29 crushing recipes incl. vanilla sulfur/cinnabar, cobalt byproducts, 10 new dusts + smelting) — loop sounds todo |
| Arc Furnace | done (2-tall GeckoLib model: electrodes lower, melt + port glow; 8 recipes: steel, tungsten, carbide, magnet, cobalt, lead) |
| Industrial Press | done (2-tall C-frame, ram stroke synced to sound; 4 dies as non-consumed catalysts; 11 recipes) |
| Chemical Plant | done (slice 4: 2x2x2 multiblock, controller GUI with 3 inputs + water tank, formed GeckoLib model with tanks/stirrer, loop sound; 13 chemistry recipes + Arc Furnace mercury flask; 14 chemistry items) — needs in-game test |
| Gas Centrifuge (+ cascade bonus) | done (slice 5: 2-tall GeckoLib model, rotor marks sweep a sight slit, header flanges join a line; UF6 tank; cascade +15% per centrifuge in a straight line, max 8, CRT readout) — needs in-game test |
| Electrolytic Separator | done (slice 3: water tank, bucket slot, deuterium from 8000 mB + gas canister; dithered-water GeckoLib model) |
| Fusion Fuel Synthesizer | done (slice 5: pressure chamber, pellet press ram, gauges, viewport glows and frosts while running; deuterium tank) — needs in-game test |
| Cryogenic Condenser | done (slice 3: 2-tall, oxygen/nitrogen membranes as catalysts; frost shows only while running). Recipe now takes an advanced circuit (slice 5) |
| Circuit Fabricator | done (slice 5: glass clean box with pick-and-place gantry; 4 inputs + LN2 coolant tank; basic/advanced/military/quantum circuits, sensor array, superconducting coil) — needs in-game test |
| Exotic Matter Fabricator | done (slice 5: 2-tall cage of three tumbling ring gimbals round a floating shard; stabilised exotic matter, containment cell, singularity seed; 400 FE/t) — needs in-game test |
| Battery Charger, Decontamination Shower | done (slice 6: charger for any FE item with chasing LEDs; walk-in shower with water spray) — needs in-game test |
| Multiblock framework (validation, rotations, formed state, ghost preview, Holo-Projector, creative spawners) | done (Holo-Projector on its final recipe since slice 5; slice 4: `MultiblockDefinition` patterns, part blocks with `formed` state, Item Port, connected Reactor Glass, right-click ghost preview + status, Holo-Projector with layer stepping, Structure Spawner; 6 GameTests) — needs in-game test |
| Fission Reactor multiblock (formerly Breeder Reactor; heat, control rods, coolant, SCRAM) | done (slice 7: 3x3x4 lead vessel with window bays onto the water-filled core, rod positions and Cherenkov glow on the model; fuel/target/spent rods, plutonium, tritium, waste; GUI with heat/rod gauges and SCRAM) — needs in-game test |
| Fusion Reactor multiblock (tokamak, capacitor ignition, LN2 magnets) | done (slice 8: 9x9x3 octagonal ring round a central solenoid; D+T gas or LiD pellets, 2,000,000 FE ignition through the Energy Port, 6,000 FE/t; formed model with viewports, 28 field coils and a lapping plasma streak; GUI, sound, manual, 5 tests) — needs in-game test |
| Singularity Reactor multiblock (matter-fed, containment cage) | done (slice 8: 5x5x5 cage; seed + 500,000 FE engages, 200,000 FE per item fed, up to 25,000 FE/t, 1,000 FE/t upkeep with a 500,000 FE reserve, collapse on power loss or a broken cage; formed model with emitter beams, gimbal cradle and growing accretion disk; GUI, sounds, manual, 3 tests) — needs in-game test |
| Particle Accelerator multiblock | done (slice 7, redesigned after feedback: 9x9x2 octagonal ring of 27 magnets on steel plating, injector, target chamber, energy and item ports; a glowing bunch laps the ring; antimatter cells from containment cells at 2,000 FE/t) — needs in-game test |
| Machine fluid tanks (container slot, pipes per face, bucket right-click, GUI gauge, fluid recipe ingredients, JEI fluid slots) | done (slice 3); slice 4 feedback: up to 4 input/product tanks per machine, fill/empty slot pair, dump buttons, fluid products, fluid auto-eject |
| Chemical fluids (8 liquids with buckets and in-world blocks with hazards, 2 gases as rising clouds with upside-down buckets), canisters as fluid containers, fluid pipes, c: fluid tags | done (slice 4 feedback) — needs in-game test |
| Water Pump (infinite water from a source block, pushes into pipes) | done (slice 4 feedback 2) — needs in-game test |
| Fluid Tank (expandable to 3x3x5, connected frame, renders contents; passive, piped in and out) | done (slice 4 feedback 2/3) — needs in-game test |
| Creative Fluid Tank (endless source of the first fluid poured in, violet frame) | done (2026-10-20) — needs in-game test |
| Canning Machine (fills and empties canisters by `canning` recipes; supply and catch tanks; GeckoLib model, sound, JEI, manual) | done (2026-10-20) — needs in-game test |
| Wrench (pipe sides normal/pull/off, cable sides on/off, rotate machines, sneak to pick up) | done (slice 4 feedback 3) — needs in-game test |
| Machine sounds | done: seamless 2 s work loops for generator, crusher, arc furnace, press, separator, condenser, chemical plant, water pump, circuit fabricator, gas centrifuge, fusion fuel synthesizer, exotic matter fabricator; client fades them in/out with the ACTIVE state |
| GameTests: crushing, no-power, generator→cable→crusher, cable connections, redstone, hopper automation, drops | done (7 tests) |
| Tall-machine framework (`TallMachineBlock`: door-style halves, upper half forwards use/capabilities, lower-half loot, culling-safe renderer) | done (5 tests; 20/20 total pass) |
| Slice 5 items: 4 circuit tiers, sensor array, superconducting coil, battery, enriched uranium nugget/ingot, depleted uranium dust/ingot, lithium deuteride pellet, stabilised exotic matter, containment cell, singularity seed | done (sprites in `tools/assets/advanced_parts.py`, `tools/assets/nuclear.py`; c: tags) |
| Parts: steel/cobalt/carbide ingots, 5 plates, rods, wire, filament, canister, magnet, handmade circuit board, machine frame, motor, pressure vessel, heating element, servo, 4 dies | done (sprites in `tools/assets/parts.py`, crafting recipes, `c:` tags) |
| Recipe catalysts (non-consumed ingredient slot, e.g. press dies) | done; the press shows the loaded die on its model |
| JEI integration (categories per machine, catalysts, clickable progress arrows, info pages, side-tab exclusion; recipes synced to clients) | done — confirmed in game by the user |
| Vanilla-style ores / raw items / ingots / dusts (`tools/gz/vanilla.py`) | done (user-approved; ingot edge tweak pending a final look) |
| Z-fighting checkers (`zfight.py` on export, `zfight3d.py` on rendered quads + animation frames) and 1-texel UV gutters with edge bleed | done — 0 issues; confirmed flicker-free in game |
| Machine recipes (custom recipe types + datagen) | todo |
| Models/animations/GUIs/sounds for every machine (reviewed) | todo |

## Phase 5 — Explosion engine & spectacle
| Item | Status |
|---|---|
| BlastProfile datapack registry + terrain tables | todo |
| Adaptive shell ray field | todo |
| Section writer, relight batching, packets | todo |
| Budget scheduler (ms + block budgets, fair sharing) | todo |
| Entity damage/exposure/knockback/fire | todo |
| Chunk tickets, SavedData persistence, resume | todo |
| Instant mode for small blasts | todo |
| Server config limits | todo |
| Fission Reactor meltdown (blast + contamination when the core overheats with SCRAM off; server config toggle) | todo (agreed with the user in the slice 7 feedback) |
| Client fx: flash, fireball, shockwave ring, debris, smoke, camera shake, heat haze, sky/fog tint | todo |
| Custom RenderPipelines + shaders (GL + Vulkan, OIT variants) | todo |
| Distance-delayed sound scheduler + virtual-distance big sounds | todo |
| GameTests: radius, shielding, budget, persistence | todo |

## Phase 6 — Conventional explosives
| Item | Status |
|---|---|
| Dynamite | todo |
| Frag / incendiary / sticky / toxin / cryo / EMP / smoke grenades | todo |
| C4 + Remote Detonator + stacking | todo |
| Breaching charge, Thermite charge | todo |
| Timed bomb (+ defuse mini-GUI) | todo |
| Landmine, bouncing mine, claymore | todo |
| HE / heavy bomb, thermobaric, cluster, bunker buster, napalm, MOAB, chemical bomb (as bombs) | todo |
| Arming Key + fuze settings GUI | todo |

## Phase 7 — Components & assembly
| Item | Status |
|---|---|
| Payload cores (all of §9.2) | todo |
| Casings & bodies | todo |
| Fuzes, guidance, propulsion, modules | todo |
| Assembly Bench block, model, animation | todo |
| Assembly GUI (schematic slots, 3D preview, stats, validation, fuze settings, Fabricate tab) | todo |
| Weapon data components + tooltips | todo |
| Disassembly, blueprints slot | todo |

## Phase 8 — Nuclear weapons
| Item | Status |
|---|---|
| Tactical nuke, fission, boosted fission, H-bomb, Tsar, neutron, cobalt, dirty bomb (blocks + models + profiles) | todo |
| Mushroom cloud system (toroidal vortex puffs, cooling glow, drift) | todo |
| Wilson condensation ring, base surge | todo |
| Flash with line of sight + afterimage | todo |
| Fizzle behaviour | todo |
| Nuke sound sets (near/mid/far/very far, tsar) | todo |

## Phase 9 — Launching
| Item | Status |
|---|---|
| Missile entity + composed renderer (body/warhead/propulsion parts) | todo |
| Flight model (boost, pitch-over, ballistic, re-entry, terminal; cruise) | todo |
| MissileTracker (analytic high-altitude flight, re-materialisation, destination chunk loading) | todo |
| Exhaust flame, smoke trail, stage separation, re-entry plasma | todo |
| Launch Pad multiblock + erector/clamp animations + GUI | todo |
| Launch Complex + gantry | todo |
| Missile Silo + hatch doors | todo |
| Cruise Missile Launcher | todo |
| Launch Control Station: map, pads, salvo, time-on-target | todo |
| Laser Designator, Targeting Binoculars, Data Link Tool, Comms Relay | todo |
| MIRV bus & re-entry vehicles | todo |
| Placed (in-place) missile detonation | todo |

## Phase 10 — Air defence
| Item | Status |
|---|---|
| Radar Station multiblock + spinning dish + CRT GUI + comparator output | todo |
| Interceptor Battery + interceptor missile + kill probability | todo |
| Phased-Array module | todo |
| Point-Defence Laser | todo |

## Phase 11 — Special weapons
| Item | Status |
|---|---|
| Storm bomb (supercell, chain lightning) | todo |
| Singularity bomb (growth, block ripping, lensing, collapse) | todo |
| Antimatter bomb (perfect sphere erasure) | todo |
| Cryo bomb (freeze, frozen mobs, shatter, crystal growth, mist) | todo |
| EMP bomb | todo |
| Satellite Uplink + Kinetic Strike Satellite + orbital rods | todo |
| Gas grid simulation + toxin/anthrax agents | todo |

## Phase 12 — Aftermath
| Item | Status |
|---|---|
| Radiation field attachment (lazy decay) | todo |
| Fallout plumes + wind + ash particles | todo |
| Dose, sickness stages, mob/plant effects | todo |
| Geiger counter + HUD, dosimeter | todo |
| Hazmat (3 tiers), gas mask, filters, flash goggles | todo |
| Rad-Away, KI tablets, Anti-Rad, contamination, decon shower | todo |
| EMP field system (machines, items, missiles, fuzes, vanilla redstone) | todo |
| Scorched/irradiated/frozen/toxic terrain blocks | todo |
| Wasteland biome painting | todo |
| Lead Container | todo |

## Phase 13 — Guns
| Item | Status |
|---|---|
| Gun framework (GeckoLib items, keybinds, ammo, energy cells, recoil, reload, ADS) | todo |
| Hammerhead RL, Lancer GML, Tactical Nuke Launcher | todo |
| Tesla Rifle, Singularity Cannon, Antimatter Rifle, Cryo Gun, Particle Beam Rifle | todo |
| Flamethrower, Grenade Launcher, EMP Gun, Railgun | todo |

## Phase 14 — Superweapons
| Item | Status |
|---|---|
| Superweapon base (charge, power, announcements, controller GUI, doctrine + upgrade slots) | todo |
| Particle Uplink Cannon (structure, charge visuals, steerable beam, trench, remote) | todo |
| Nuclear Missile Silo (doors, elevator, plume, silo nuke) | todo |
| SCUD Storm (rack raise, 9-missile barrage, anthrax) | todo |
| Doctrine modules (10) + generic upgrades (4) | todo |
| Creative structure spawners | todo |

## Phase 15 — Structures & loot
| Item | Status |
|---|---|
| Abandoned Missile Silo (3 variants) | todo |
| Command Bunker | todo |
| Radar Outpost | todo |
| Test Site | todo |
| Crashed Missile | todo |
| Waste Storage Cave | todo |
| Exotic Research Station (End) | todo |
| Loot tables, blueprints, damaged warheads, lore notes (~20) | todo |

## Phase 16 — Guide & recipe viewer
| Item | Status |
|---|---|
| Field Manual GUI + layout engine + page types | done (book item, home/chapter/entry spreads, flowing layout, text/spotlight/crafting/smelting/machine/structure/image/table/links pages, search, bookmarks, unread dots, tooltip key) — needs in-game test |
| Manual content: every resource, machine, component, weapon, multiblock, system | partly (everything built so far: 7 chapters, 41 entries; weapons etc. arrive with their phases) |
| 3D multiblock viewer with layer slider | done (real block models in a GUI picture-in-picture; drag to turn, layer buttons, block list) |
| JEI plugin + custom categories | todo |

## Phase 17 — Polish
| Item | Status |
|---|---|
| Re-review every model/texture/sound/effect | todo |
| Performance pass (Tsar MSPT target < 50) | todo |
| Balance pass | todo |

---

## Known bugs
_None yet._

## Open decisions
None. D1–D6 were resolved on 2026-10-19 (DESIGN.md §29).

## Environment notes
- Gradle must run on JDK 25: set `JAVA_HOME` to CurseForge's runtime
  (`%USERPROFILE%\curseforge\minecraft\Install\runtime\java-runtime-epsilon\windows-x64\java-runtime-epsilon`)
  or install a JDK 25 and point `JAVA_HOME` at it. The system `java` (1.8) cannot run Gradle 9.
- Gradle auto-provisions a JDK 21 for NeoForm tooling into `%USERPROFILE%\.gradle\jdks` (first attempt failed
  mid-extraction once; a retry succeeded).
- Decompiled Minecraft sources for API reference: `build/mcsrc/` (unzipped from
  `build/moddev/artifacts/minecraft-patched-26.3.0.92-sources.jar`; re-extract after a clean).
- The project lives inside OneDrive; `build/`, `.gradle/` and `run/` are large and churn — consider excluding them
  from OneDrive sync if syncing causes file-lock errors.
