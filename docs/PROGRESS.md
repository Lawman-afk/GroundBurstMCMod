# Ground Zero — Progress

Status keys: `todo` · `in progress` · `done` · `needs in-game test` · `blocked`
Asset review keys (models/textures/sounds): `drafted → previewed → revised → approved`

**Current phase:** Phase 4 in progress (power ladder agreed: DESIGN §7) — slices 1–7 built (slice 7: Fission Reactor, Particle Accelerator; slice 7 feedback passes and the accelerator redesign done; the Field Manual slice done (book, screen, 3D structure view, 7 chapters / 41 entries); Canning Machine and Creative Fluid Tank added; slice 8 built (Fusion Reactor, Singularity Reactor; reworked after feedback; item pipes and a performance pass; 7 chapters / 44 entries, 94/94 GameTests); Phase 4's power ladder is complete, awaiting the user's in-game check. Phase 5 started: slices 5a (server explosion engine) and 5b (chunk tickets, saving, client sync, distance-delayed sound) done, plus the crater character pass (streaks, ground pass, ejecta) slice 5c (Spectacle: flash, fireball, shock front, debris, smoke, shake, sky tint) and Spectacle v2 (mushroom cloud, cloud lighting, far clouds, dirt/fire jets), tuned after the in-game look, and the reactor meltdown (5d) with hazmat suits. Phase 6 started: slice 6a (thrown explosives) done, plus a feedback pass (bigger dynamite, smoke hides you, chemical grenades with chlorine, toxin and toxic sludge, a Molotov cocktail), slice 6b (placed charges and fuzes, with a feedback pass) and slice 6c (mines, with feedback passes) and slice 6d (bombs, with a feedback and a feel pass), plus a basic launch site ahead of Phase 9 (Launch Silo, Launch Computer with a terrain map, Basic Missile, warheads) and a Radar Array ahead of Phase 10, 183/183 GameTests (Phase 6 complete with slice 6e). Slices 1–6 built (slice 6: Diesel Generator, RTG, Capacitor Banks, Battery Charger, Decontamination Shower; 63/63 GameTests); earlier: slices 1–5 built (slice 5: Circuit Fabricator, Gas Centrifuge with cascade, Fusion Fuel Synthesizer, Exotic Matter Fabricator; 57/57 GameTests). Awaits the user's in-game check of slices 4–5; next: slice 6 (Diesel Generator, RTG, Capacitor Banks, Battery Charger, Decontamination Shower).
**Last updated:** 2026-10-24 (slice 9c: cruise missiles, the Cruise Engine and the Cruise Missile Launcher; 246/246 GameTests)

---

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| 0 | Research & design: toolchain, instance, DESIGN.md, roadmap | **done** |
| 1 | Project skeleton, deploy task, registries framework, configs, creative tabs, networking base | **done** |
| 2 | Asset pipeline: texture lib, model DSL (block JSON + GeckoLib), preview renderer, sound synth → OGG, NBT writer, style guide, pilot assets | **done** (needs in-game listen of pilot sounds) |
| 3 | Resources & world generation | **done** (needs in-game test) |
| 4 | Power, cables, processing machines, multiblock framework, Fission Reactor, Particle Accelerator, Fusion and Singularity Reactors | **in progress** (slices 1–8 built; 6: diesel/RTG/capacitors/charger/decon; 7: Fission Reactor + Particle Accelerator; 8: Fusion + Singularity Reactors) — needs in-game test |
| 5 | Explosion engine, Spectacle visual system, distance-delayed sound | **done, awaiting the user's in-game check** (5a server engine, 5b sync/persistence/sound, crater character, 5c Spectacle and its v2 with the mushroom cloud, 5d reactor meltdown; heat haze deferred) |
| 6 | Conventional explosives | done (6a-6e) — needs the user's in-game look |
| 7 | Components & Assembly Bench | **done** (7a warheads, 7b missiles from parts, 7c preview and stats; 2026-10-23: one missile per warhead and four missile assemblers, the bench back to warheads) — needs the user's in-game look; Ammunition page with Phase 13, blueprints with Phase 15 |
| 8 | Nuclear weapons | **in progress** (8a: Launch Complex, Strategic missile, Tactical Nuclear and Fission Warheads, fizzles; 8b: Boosted Fission, Neutron and Dirty Warheads; 8c: the Strategic Assembly Hall, intercontinental missiles with the Thermonuclear and Cobalt Warheads; 8d: the Tsar Mega-bomb, and the explosion engine benchmarked and reworked at its size; Phase 8 complete but for the in-game look) |
| 9 | Launch platforms, missile flight, launch control, designators | **in progress** (basic launch site built early, 2026-10-21; 9a: missile flight: burn-out, staging, re-entry, engine sound; 9b: the Missile Silo; 9c: cruise missiles and the Cruise Missile Launcher) |
| 10 | Radar & interceptors | todo |
| 11 | Special weapons (storm, singularity, antimatter, cryo, EMP, orbital, chemical, extras) | todo |
| 12 | Aftermath: radiation, fallout, EMP fields, terrain, wasteland, gear, medical | todo |
| 13 | Guns | todo |
| 14 | Superweapons + Zero Hour doctrine modules | **in progress** (2026-10-23: the SCUD Storm and the Nuclear Missile with their factories, the Arms Dealer and the War Factory; the toxin strike) — needs the user's in-game look; Particle Uplink Cannon and doctrine modules todo |
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
| Fission Reactor multiblock (formerly Breeder Reactor; heat, control rods, coolant, SCRAM) | done (slice 7: 3x3x4 lead vessel with window bays onto the water-filled core, rod positions and Cherenkov glow on the model; fuel/target/spent rods, plutonium, tritium, waste; GUI with heat/rod gauges and SCRAM); 2026-10-23, the user's request: any size from 3x3x3 to 6x6x6, every rod burning at once with its own lifetime (the one-rod-at-a-time bug fixed), glowing coolant and lit channels; reworked the same day after the user's look: a rod in every Fuel Channel anywhere in the core (up to 64 at 75 FE/t, 4,800 FE/t), one Item, Fluid and Energy Port, a sectional formed vessel drawn over any size, a ghost preview of one plan sized on the screen, and the GUI's core map by layer; then the vessel redesigned to read as one continuous object like the old model (35 seamless sections, riveted courses round it, clear continuous glazing with lips round its outside, no flickering joins) and ghosts drawn with each block's own model (DESIGN §7.2) — client-checked, needs the user's look |
| Fusion Reactor multiblock (tokamak, capacitor ignition, LN2 magnets) | done (slice 8: 9x9x3 octagonal ring round a central solenoid; D+T gas only, 2,000,000 FE ignition through the Energy Port, 6,000 FE/t; formed model with viewports, 28 field coils and a lapping plasma streak; GUI, sound, manual, 5 tests) — needs in-game test |
| Singularity Reactor multiblock (matter-fed, containment cage) | done (slice 8: 5x5x5 cage; the seed stays in its slot, 500,000 FE engages the cage, 10,000 FE per item eaten at up to 20 items/s (10,000 FE/t; 50 and 25,000 until the user's call on 2026-10-23), 1,000 FE/t upkeep with a 500,000 FE reserve, extra floor Item Ports, collapse destroys the seed; formed model with emitter beams, gimbal cradle and growing accretion disk; GUI, sounds, manual, 3 tests) — needs in-game test |
| Particle Accelerator multiblock | done (slice 7, redesigned after feedback: 9x9x2 octagonal ring of 27 magnets on steel plating, injector, target chamber, energy and item ports; a glowing bunch laps the ring; antimatter cells from containment cells at 2,000 FE/t) — needs in-game test |
| Machine fluid tanks (container slot, pipes per face, bucket right-click, GUI gauge, fluid recipe ingredients, JEI fluid slots) | done (slice 3); slice 4 feedback: up to 4 input/product tanks per machine, fill/empty slot pair, dump buttons, fluid products, fluid auto-eject |
| Chemical fluids (8 liquids with buckets and in-world blocks with hazards, 2 gases as rising clouds with upside-down buckets), canisters as fluid containers, fluid pipes, c: fluid tags | done (slice 4 feedback) — needs in-game test |
| Water Pump (infinite water from a source block, pushes into pipes) | done (slice 4 feedback 2) — needs in-game test |
| Fluid Tank (expandable to 3x3x5, connected frame, renders contents; passive, piped in and out) | done (slice 4 feedback 2/3) — needs in-game test |
| Item pipes, three tiers (iron 8 / steel 32 / pneumatic 128 items/s; pull sides, auto-eject and hoppers in, round-robin out) | done (2026-10-20) — needs in-game test |
| Performance pass: cached network endpoints, precomputed pipe/cable shapes, ACTIVE hysteresis, cached multiblock layouts | done (2026-10-20) |
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
| BlastProfile datapack registry + terrain tables | done (5a: synced registry, 6 test profiles; terrain tables in code until Phase 12 blocks) |
| Adaptive shell ray field | done (5a: cube-sphere rays splitting at 0.7 blocks, energy/shielding, lobes, crater depth, pressure bands; streak grain for starburst craters with spiky floors) |
| Section writer, relight batching, packets | done (5a: flagged Level.setBlock writes, rim neighbour updates; 5b: blast start/front/end packets) |
| Budget scheduler (ms + block budgets, fair sharing) | done (5a) |
| Entity damage/exposure/knockback/fire | done (5a: groundzero:blast damage type) |
| Chunk tickets, SavedData persistence, resume | done (5b: ticket band, no generation unless allowed, blasts.dat, resume) |
| Instant mode for small blasts | done (5a) |
| Server config limits | done (5a: destroy switch, multiplier, max radius, budgets, concurrency, protected spawn/dimensions) |
| Fission Reactor meltdown (blast + contamination when the core overheats with SCRAM off; server config toggle) | done (critical phase, klaxon, meltdown blast, corium, contaminated zones, Radiation Sickness; on by default; bigger blast, harsher sickness, hazmat suits) — needs in-game look |
| Client fx: flash, fireball, shockwave ring, debris, smoke, camera shake, heat haze, sky/fog tint | done (5c; heat haze deferred) — reviewed with runFxShots, needs in-game look |
| Mushroom cloud (toroidal cap, stem, collar, Wilson shell), cloud lighting, far clouds, dirt/fire jets, flare | done (Spectacle v2) — reviewed with runFxShots, needs in-game look |
| Custom RenderPipelines + shaders (GL + Vulkan, OIT variants) | partly (glow, fire and cloud pipelines with OIT sets; our own fx_cloud fragment shader; heat haze still to do) |
| Distance-delayed sound scheduler + virtual-distance big sounds | done (5b: small/medium/large/nuke sets, 4 bands each) — needs in-game listen |
| Ground pass: line-of-sight scorch and fire in spokes, ejecta rubble | done (crater character pass; tuned with tools/preview/crater_preview.py) — needs in-game look |
| GameTests: radius, shielding, budget, persistence | done (instant, shielding, multi-tick front, exposure, no spew, upward reach, save and resume, ragged rim, scorch shadow, ejecta, no rubble on water) |

## Phase 6 — Conventional explosives
Slices (DESIGN §30, 2026-10-21): 6a thrown · 6b placed charges + fuzes · 6c mines · 6d bombs (direct recipes until the
Phase 7 bench) · 6e effect systems (gas grid, EMP fields, freezing) + toxin / EMP / cryo grenades + chemical bomb.

| Item | Status |
|---|---|
| Thrown-explosive framework (throw, bounce, stick, fuse, tumbling render) | done (6a) — needs in-game look |
| Dynamite | done (6a) |
| Frag / incendiary / sticky / smoke grenades | done (6a) |
| Poison gas + toxin grenades (chlorine, toxin, salt, toxic sludge, chemical protection) | done (6a feedback); toxin grenade remodelled as a glass vial |
| Molotov cocktail | done (6a feedback) — needs in-game look |
| Cryo / EMP grenades (with gas grid, freezing, EMP fields) | done (6e, ice blocks round frozen creatures after feedback) — needs in-game look; EMP redstone/powered items later |
| C4 + Remote Detonator + stacking | done (6b) — needs in-game look |
| Breaching charge, Thermite charge | done (6b) |
| Timed bomb (+ defuse mini-GUI) | done (6b) |
| Landmine, bouncing mine, claymore | done (6c) — needs in-game look |
| HE / heavy, thermobaric, cluster, bunker buster, napalm, massive ordnance, poison gas, smoke, cryo, EMP, toxic waste (as warheads; once bombs) | done (6d, reworked as warheads; airburst clusters; cryo/EMP clusters after 6e) — needs in-game look |
| Chemical bomb | done (6e, as the Chemical Warhead: anthrax into the gas grid) |
| Fuze settings + arming GUI | done (6b): frequency on every charge, At once / Timer, arm from the screen, redstone, sympathetic detonation (Arming Key dropped after feedback) |

## Phase 7 — Components & assembly
| Item | Status |
|---|---|
| Payload cores (all of §9.2) | 7a: one per existing warhead (13); nuclear/special cores come with Phases 8/11 |
| Casings & bodies | done: Light/Medium/Heavy warhead casings (7a); Tactical/Medium/Heavy missile bodies (7b) |
| Fuzes, guidance, propulsion, modules | done for the conventional tier: Impact and Barometric fuzes (7a); SRM I / SRM II / Liquid-fuel Stage, Inertial and GPS guidance, Hardened Electronics (7b); more modules later |
| Assembly Bench block, model, animation | done (7a) — needs in-game look |
| Assembly GUI (schematic slots, 3D preview, stats, validation, fuze settings, Fabricate tab) | done: Warheads and Missiles pages, readout with validation, fuze setting, guidance line, Assemble/Take Apart, per-half sides, JEI categories (7a/7b); wider window with a draggable 3D preview and a stats screen (7c); Ammunition page with Phase 13 |
| Weapon data components + tooltips | done: fuze (7a) and missile (7b) components with tooltips |
| Assembled missiles in the silo | done (7b): one Missile slot, range from its motor, scatter from its guidance, body scales the model — needs in-game look |
| One missile per warhead, an assembler per class (DESIGN §10.4) | done 2026-10-23: 21 missiles made for their warheads (the warhead's own model as the head, the body in its paint); Light, Medium, Heavy and Strategic Missile Assemblers (multiblocks with animated cranes, the missile laid out section by section; recipes on a ladder with the hall's); the bench back to warheads only; the launchers' arms slide and reach to each missile; JEI per-warhead recipes; Field Manual entry — needs the user's in-game look |
| Disassembly, blueprints slot | 7a: Take Apart done; blueprints with Phase 15 loot |

## Phase 8 — Nuclear weapons
| Item | Status |
|---|---|
| Tactical nuke, fission, boosted fission, H-bomb, Tsar, neutron, cobalt, dirty bomb (blocks + models + profiles) | 8a: tactical and fission warheads with cores, models, profiles, contamination (rescaled: tactical 1.5x MOW); 8b: boosted fission, neutron (neutron burst through shielding, plants wither) and dirty (class S, radiological) warheads with cores, models, profiles ; 8c: thermonuclear (H-bomb) and cobalt warheads as whole weapons (intercontinental missiles from the Strategic Assembly Hall); 8d: the Tsar Mega-bomb (Tertiary Stage, 50 Mt, its own sound); after 9b: the Tsar's own Twin Boosted Core primary, and every mushroom cloud stands until its crater is finished before its 2.5-minute countdown — needs in-game look |
| Strategic missile (XL body, nuclear only, its own long model) | done (8a feedback) — needs in-game look |
| Strategic Assembly Hall (multiblock building whole weapons for the Launch Complex) | done (8c): 7x13 crane hall, parts, a minute's assembly with the crane laying the missile, screen with preview and stats — needs in-game look |
| Intercontinental missile (class XXL, 3,000 blocks, its own model on the complex and in flight) | done (8c) — needs in-game look |
| Launch Complex (nuclear launcher, pulled forward from Phase 9) | done (8a): structure, model, 15 s countdown with umbilicals, deluge and trench smoke — needs in-game look |
| Mushroom cloud system (toroidal vortex puffs, cooling glow, drift) | done in Phase 5 (Spectacle v2); used by the nuclear warheads; no wind drift: it stands over its crater (8b feedback) |
| Wilson condensation ring, base surge | done in Phase 5 (5d: the condensation shell round the fireball for ~7 s; the base surge and, after 8b, the base ring) |
| Fallout you can see (stand-in until Phase 12's plumes) | done: fallout clouds over each contaminated zone, green sky, fog and clouds inside one — needs in-game look |
| Flash with line of sight + afterimage | line of sight done in 5c (a client ray to the burst, 25% if blocked); the afterimage todo |
| Fizzle behaviour | done (8a): set off any way but by its missile, a nuclear warhead fizzles (the dirty warhead, not nuclear, always goes off) |
| Nuke sound sets (near/mid/far/very far, tsar) | done: nuke (Phase 5), tsar (8d) |

## Phase 9 — Launching
| Item | Status |
|---|---|
| Basic launch site: Launch Silo, Launch Computer (terrain map), Basic Missile, flight tracker, 6 building blocks (DESIGN §17.6) | done — needs in-game look |
| Missile entity + composed renderer (body/warhead/propulsion parts) | missiles drawn by body and warhead (7b, 8a, 8c); 9a: drawn without their spent stages |
| Flight model (boost, pitch-over, ballistic, re-entry, terminal; cruise) | 9a: burn-out and coasting, staging and re-entry on the fixed arc; 9c: cruise (out of its box on a booster that drops, a level turn, 40 blocks over the ground surveyed at launch, a pop-up and a dive; DESIGN §17.11) — needs in-game look |
| MissileTracker (analytic high-altitude flight, re-materialisation, destination chunk loading) | todo |
| Exhaust flame, smoke trail, stage separation, re-entry plasma | done (9a): flame and trail from the burning stage, stopping at burn-out; staging flash, smoke ring, bang and the spent stage tumbling down; re-entry sheath and fire streak; engine and re-entry sounds with Doppler — needs in-game look (heat haze still todo) |
| Launch Pad multiblock + erector/clamp animations + GUI | not planned (the user, at 9c: the Launch Silo does this job) |
| Launch Complex + gantry | done in Phase 8 (8a, DESIGN §17.8); 2026-10-23: walkable once formed (its deck, the flame trench under the missile and the deflector's steps; DESIGN §23.3) |
| Missile Silo + hatch doors | done (9b, DESIGN §17.10): dug-in 5×5×14 shaft of Reinforced Concrete, Hazard Concrete ring, 3×3 Silo Hatch whose doors slide apart on rails, 20 s countdown holding until they are open, straight climb out of the shaft, vent and mouth exhaust, DOORS button, Hatch blocked; after 9b (the user's requests): fires every missile, intercontinental ones too, its shaft has no lamps and its corner conduits run up to the ground — needs in-game look |
| Cruise Missile Launcher | done (9c, DESIGN §17.11): 2x6 deck with two Launch Boxes, armoured boxes raised to 45° by telescopic rams, frangible covers, two missiles 3 s apart, 60k FE each; the Cruise Engine (1,500 blocks) makes any Light or Medium missile a cruise missile with its own winged airframe (folded in its box); sounds, Field Manual entry, JEI info — needs in-game look |
| Launch Control Station: map, pads, salvo, time-on-target | todo |
| Laser Designator, Targeting Binoculars, Data Link Tool, Comms Relay | todo |
| MIRV bus & re-entry vehicles | todo |
| Placed (in-place) missile detonation | todo |

## Phase 10 — Air defence
| Item | Status |
|---|---|
| Radar Station multiblock + spinning dish + CRT GUI + comparator output | basic Radar Array built early (DESIGN §17.7); 2026-10-23: its deck walkable once formed (railings, cabinet, power box, mast foot as collision) — needs in-game look |
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
| Superweapon base (charge, power, announcements, controller GUI, Launch Computer firing) | done 2026-10-23 — needs in-game test (doctrine + upgrade slots todo) |
| Particle Uplink Cannon (structure, charge visuals, steerable beam, trench, remote) | todo |
| Nuclear Missile Silo (C&C's coffin: roof opening, erector, DF-5, 200-block radiation field) | done 2026-10-23 — needs in-game test |
| SCUD Storm (erectors raising nine SCUDs, the barrage, toxin cloud and anthrax residue) | done 2026-10-23 — needs in-game test |
| Ammunition factories: Arms Dealer (Toxin SCUD), War Factory (Nuclear Missile) | done 2026-10-23 — needs in-game test |
| Doctrine modules (10) + generic upgrades (4) | todo |
| Creative structure spawners | done for the four built |

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
| JEI plugin + custom categories | categories built with their machines since Phase 4; 2026-10-23: one intercontinental missile per whole weapon in the list (R shows the hall's recipe), the whole weapons' bare warheads out of it |

## Phase 17 — Polish
| Item | Status |
|---|---|
| Re-review every model/texture/sound/effect | 2026-10-23, the user's requests: every multiblock (the six recent ones, then the Chemical Plant, Fission, Fusion and Singularity Reactors and Particle Accelerator), their blocks, the shared controller faces, casing and ports, and every missile (with finer flight models) repainted with the material library after researching texture practice (DESIGN §4.3); the library is now the rule for every model; then (third pass) every single-block and two-block machine on a shared machine kit, the cables, pipes, fluid tanks and storage blocks; then (fourth pass, the model design researched, DESIGN §4.4) all 21 warheads and the cluster bomblet remodelled and repainted: true round sections of turned planks, noses on real profiles, a silhouette and details for each type, no flickering caps or tips; and every missile now carries its warhead's own model (DESIGN §4.5) — client-checked, needs the user's look; the other ordnance next, then items |
| Performance pass (Tsar MSPT target < 50) | 8d: the blast bench (`runBlastBench`) and the engine reworked at the Tsar's size: p99 tick 27.7 ms, one tick over 50 ms (since spread) on the user's PC; a wider pass (fx, fallout, saving) still todo |
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
