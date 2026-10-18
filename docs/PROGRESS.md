# Ground Zero — Progress

Status keys: `todo` · `in progress` · `done` · `needs in-game test` · `blocked`
Asset review keys (models/textures/sounds): `drafted → previewed → revised → approved`

**Current phase:** Phase 2 (asset pipeline). Phases 0–1 done.
**Last updated:** 2026-10-19 (session 1)

---

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| 0 | Research & design: toolchain, instance, DESIGN.md, roadmap | **done** |
| 1 | Project skeleton, deploy task, registries framework, configs, creative tabs, networking base | **done** |
| 2 | Asset pipeline: texture lib, model DSL (block JSON + GeckoLib), preview renderer, sound synth → OGG, NBT writer, style guide, pilot assets | in progress |
| 3 | Resources & world generation | todo |
| 4 | Power, cables, processing machines, multiblock framework, Breeder Reactor, Particle Accelerator | todo |
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
| 16 | Field Manual & JEI | todo |
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
| `tools/gz/palette.py` material ramps | todo |
| `tools/gz/tex.py` pixel-art primitives (bevels, rivets, seams, stripes, decals, stencil font, wear, emissive masks) | todo |
| `tools/gz/model.py` model DSL → block/item JSON with display transforms | todo |
| GeckoLib geo + animation JSON export | todo |
| `tools/preview/render.py` software renderer + contact sheets | todo |
| `tools/sound/synth.py` synthesis toolkit + OGG export + distance variants | todo |
| `tools/structure/nbt.py` NBT writer + `template.py` structure templates | done (used for GameTest structures) |
| GUI texture toolkit (panels, CRT, gauges, buttons, bars, stencil font) | todo |
| Style guide page with palette swatches & sample assets | todo |
| Pilot assets approved (1 ore, 1 machine, 1 bomb, 1 sound) | todo |

## Phase 3 — Resources & world generation
| Item | Status |
|---|---|
| Common config (ore toggles) | todo |
| Ores: uranium, lithium, sulfur (+nether), saltpeter (+sandstone), tungsten, lead, rare earth — stone & deepslate | todo |
| Exotic crystal cluster, voidstone, End void-geode feature | todo |
| Raw items, dusts, ingots, nuggets, plates/rods/wire placeholders → real items | todo |
| Storage blocks (raw & ingot) | todo |
| Ore features, placed features, biome modifiers | todo |
| Vanilla sulfur/cinnabar crushing integration | todo |
| Radioactive item/block tags (emission values) | todo |
| Textures for all of the above (reviewed) | todo |

## Phase 4 — Power, machines, multiblocks
| Item | Status |
|---|---|
| Machine base (FE, inventories, side config, redstone control, EMP hook, sounds, GUI base) | todo |
| Cables (copper, insulated, HV) with connected models | todo |
| Combustion Generator, Diesel Generator, RTG, Capacitor Banks T1–T3 | todo |
| Ore Crusher | todo |
| Arc Furnace | todo |
| Industrial Press | todo |
| Chemical Plant | todo |
| Gas Centrifuge (+ cascade bonus) | todo |
| Electrolytic Separator | todo |
| Fusion Fuel Synthesizer | todo |
| Cryogenic Condenser | todo |
| Circuit Fabricator | todo |
| Exotic Matter Fabricator | todo |
| Battery Charger, Decontamination Shower | todo |
| Multiblock framework (validation, rotations, formed state, ghost preview, Holo-Projector, creative spawners) | todo |
| Breeder Reactor multiblock | todo |
| Particle Accelerator multiblock | todo |
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
| Field Manual GUI + layout engine + page types | todo |
| Manual content: every resource, machine, component, weapon, multiblock, system | todo |
| 3D multiblock viewer with layer slider | todo |
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
