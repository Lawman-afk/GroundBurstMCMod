# Ground Zero — Design Document

> Status: **v1 — decisions D1–D6 resolved by the user** (session 1, 2026-10-19).
> This is the single source of truth for what the mod contains. Every item, block, weapon, component, recipe,
> multiblock and system lives here with its stats. When a decision is made during implementation it is recorded
> here (and in the "Decision log" at the end) so future sessions never have to re-derive it.
>
> Numbers are first-pass balance values. They will be tuned during in-game testing; when they change, change them here.

---

## 1. Identity

| | |
|---|---|
| Name | **Ground Zero** (confirmed by the user) |
| Mod ID | `groundzero` |
| Java package | `dev.groundzero` |
| Alternatives considered | *Critical Mass* (`criticalmass`), *Doomsday Arsenal* (`doomsday`) |
| Tagline | "Assemble it. Launch it. Watch the horizon." |

**Design pillars**
1. **The spectacle matches the destruction.** Every visual is driven by the same numbers as the damage. The fireball,
   the shockwave ring and the crater edge are one thing seen three ways.
2. **You build your weapons.** Mine → refine → fabricate parts → components → assemble. A finished H-bomb should feel
   earned, and a launched ICBM should feel like the end of a long project.
3. **The server never stalls.** All large effects are incremental, budgeted, persistent and configurable.
4. **Hand-made look.** Every model and texture is designed, previewed and iterated. No placeholders ship.

---

## 2. Toolchain (verified 2026-10-19)

| Item | Value | Source |
|---|---|---|
| Minecraft | **26.3** (released 2026-09-15) | instance `minecraftinstance.json` |
| NeoForge | **26.3.0.92** (pinned to match the instance; NeoForge for 26.3 is still flagged beta) | instance `baseModLoader` |
| Build plugin | ModDevGradle **2.0.148** | official MDK `NeoForgeMDKs/MDK-26.3-ModDevGradle` |
| Gradle | **9.2.1** (wrapper) | MDK |
| Java | **25** (Mojang ships Java 25 with 26.3) | MDK comment + primer |
| Mappings | Official Mojang names (`Identifier`, not `ResourceLocation`) | MDK sources |
| Local JDK | CurseForge's bundled Microsoft OpenJDK **25.0.1** (`%USERPROFILE%\curseforge\minecraft\Install\runtime\java-runtime-epsilon\...`) used via `JAVA_HOME` for Gradle. The system `java` on PATH is 1.8 and cannot run Gradle 9. | local check |
| Python | 3.14.6 with Pillow 13 and numpy 2.5 (asset pipeline) | local check |
| Test instance | the CurseForge test instance — MC 26.3, NeoForge 26.3.0.92, 8 GB allocated, `mods/` empty | local check |

### 2.1 Important API facts for the 26.x line (from the NeoForge migration primers)
These replace what older (1.20/1.21) knowledge would assume. Re-verify against the decompiled sources in
`build/moddev/artifacts` before using any of them.

- **Rendering backend abstraction.** 26.2 added a **Vulkan** backend beside OpenGL; 26.3 moved the render API out of
  Blaze3d into **Renderpearl** (`com.mojang.renderpearl.api.*` — `RenderPipeline`, `RenderPass`, `GpuTexture`, …).
  Raw GL calls are off the table; all custom rendering goes through `RenderPipeline`s + `RenderPass`es so it works on
  both backends.
- **Shaders** are compiled by Shaderc for both GL and Vulkan: `#include` replaces `#moj_import`; every `in`/`out`
  needs an explicit `layout(location = N)`; uniforms come through `BindGroupLayout`s.
- **Order-independent transparency (OIT)** exists in 26.3 (`OitPipelineSet`, `oitTranslucent` phase). Our translucent
  effects (clouds, shockwave shells, gas) must provide OIT pipeline variants.
- **Submit-node rendering.** `MultiBufferSource` is gone. Entity/block-entity/particle rendering is
  *extract render state → submit nodes* (`SubmitNodeCollector`, `FeatureRenderDispatcher`, `submitModel`,
  `submitModelPart` taking a `UvMapping`).
- **Post effects** are `PostChain`s identified by id; the server can push post effects to a player
  (`ServerPlayer#addPostEffect` / `sendPostEffects`) — useful for flash/tint/distortion.
- **GUI** rendering goes through `GuiGraphicsExtractor` / `GuiRenderState`; the HUD is a separate `Hud` object.
- **Input** moved from GLFW to **SDL** (`SDLScancode`, `KeyEvent#keycode`); text fields must notify
  `onTextInputFocusChange`.
- **Datapack registries are reloadable** for loot tables, recipes, advancements, predicates; data generation uses
  `SingleRegistryBootstrap` / `MultiRegistryBootstrap`, `LootTableSubProvider#run`, `RecipeProvider` with a
  `BootstrapContext`.
- **Vanilla now has sulfur.** 26.2 "Chaos Cubed" added Sulfur Caves, sulfur/cinnabar block families, potent sulfur
  and the Sulfur Cube mob. Our sulfur resource integrates with it (see §5) instead of ignoring it.

Learned while building (verified against the 26.3.0.92 sources):
- **Config types** are `LOCAL`, `CLIENT`, `SYNCED`, `STARTUP` (no more COMMON/SERVER). Server-authoritative settings
  use `SYNCED` → file `groundzero-synced.toml`; client settings → `groundzero-client.toml`.
- `Holder#getKey()` is deprecated for removal; use `key()` / `keyOrNull()`.
- **Transfer API:** energy is `Capabilities.Energy.BLOCK` → `EnergyHandler` (`transfer.energy`, `SimpleEnergyHandler`);
  items and fluids are `ResourceHandler<ItemResource>` / `ResourceHandler<FluidResource>` with transactions
  (`transfer.transaction.Transaction`). The old `IItemHandler` / `IEnergyStorage` are gone.
- **GameTests:** test functions are entries in the vanilla `TEST_FUNCTION` registry; instances
  (`FunctionGameTestInstance` + `TestData` with a structure id) are registered on NeoForge's `RegisterGameTestsEvent`.
  The structure's (0,0,0) sits at test block + (0,1,1).
- **Structure NBT** block states use lowercase `id` / `properties` (older versions: `Name` / `Properties`).
  Data version for 26.3 is **5023**.
- **Datagen** fires `GatherDataEvent.Client` for the `clientData` run (covers client and server data).
- Client-bound payload handlers register on `RegisterClientPayloadHandlersEvent` (client side only).
- **Blocks have no `codec()`** in 26.3 (`simpleCodec` / `MapCodec` overrides are gone).
- **GeckoLib animations** are looked up by their exact key in `animations{}`; we export plain names (`working`).

### 2.2 Libraries available for 26.3 and what we do with them

| Library | 26.3 status | Decision |
|---|---|---|
| **GeckoLib** | NeoForge 26.3 builds 5.5.6 (2026-09-16) and 5.5.7 (2026-09-22) | **Required dependency.** Used for every animated model: missiles, launch pads, silo doors, radar dishes, superweapon parts, guns (first-person reload/charge), animated machines. Its Bedrock-geometry format is easy to generate from our Python model tools. |
| **JEI** | NeoForge 26.3 builds (31.x, beta-tagged, updated 2026-10-18) | **Optional integration** (compile against API, `localRuntime` for dev). Custom categories for every machine and the assembly bench. |
| **EMI** | No 26.3 build found | Not used. Re-check each session; the integration layer is written so EMI could be added. |
| **Patchouli** | No 26.3 build found (stops at 26.1.2) | Not usable. |
| **Modonomicon** | NeoForge 26.3 build 2.17.1 (2026-10-11) | Available but **not used** (decision D3): the guide is a **native Field Manual**. |

**Dependency policy:** one hard dependency (GeckoLib). Everything else is optional and detected at runtime.

---

## 3. Architecture

```
dev.groundzero
├── GroundZero / GroundZeroClient       entry points
├── registry/      blocks, items, block entities, entities, menus, sounds, particles, data components,
│                  attachments, recipe types/serializers, creative tabs, datapack registries
├── config/        server (safety limits), common, client (visual quality) ModConfigSpecs
├── network/       payloads (StreamCodec) + handlers
├── content/
│   ├── resource/  ores, raw/dust/ingot/plate items, storage blocks
│   ├── machine/   processing machines, generators, cables, machine base classes
│   ├── ordnance/  grenades, charges, mines, bombs (warhead blocks), detonators
│   ├── assembly/  components, assembly bench, assembly rules
│   ├── missile/   missile entity, flight model, launch pads, silos, launch control, designators
│   ├── defense/   radar, interceptors
│   ├── special/   storm, singularity, antimatter, cryo, EMP, orbital strike, chemical, extras
│   ├── gun/       guns, ammo, projectiles
│   ├── superweapon/  particle cannon, nuclear silo, SCUD storm, doctrine modules
│   └── aftermath/ radiation, fallout, EMP fields, terrain conversion, hazmat, medical
├── blast/         explosion engine (BlastManager, BlastJob, RayField, BlastProfile, zones, writers)
├── multiblock/    structure definitions, validation, ghost preview, formed rendering, creative spawners
├── world/         ore features, biome modifiers, structures, wasteland biome
├── client/
│   ├── fx/        "Spectacle" system: flash, fireball, shockwave, mushroom cloud, debris, beams, lensing
│   ├── render/    entity / block-entity / item renderers, custom RenderPipelines + shaders
│   ├── screen/    all GUIs (shared military UI toolkit)
│   ├── sound/     distance-delayed sound scheduler, looping machine sounds
│   └── hud/       Geiger / dosimeter / designator overlays
├── guide/         native Field Manual (book data, layout engine, pages, multiblock viewer)
├── compat/jei/    optional JEI plugin
├── datagen/       data generators
└── gametest/      GameTests
```

**Data-driven where it pays off.** Blast behaviour is a datapack registry `groundzero:blast_profile` (zones, speeds,
terrain conversion table, visual preset, sound set). Weapons reference a profile id. This lets us (and pack makers)
tune radii without code, and keeps the visual and damage numbers in one place.

---

## 4. Art direction and asset pipeline

### 4.1 Style guide
- **Pixel density:** 16 px per block on every world model (blocks, multiblocks, entities, held items) so everything sits
  naturally next to vanilla. GUI art is drawn at 1× GUI scale on a 2-px grid.
- **Shapes:** chunky readable silhouettes first; then bevels (1-px highlight top-left, 1-px shadow bottom-right),
  panel seams, rivets in 2×2 clusters, pipes with flanges, cable runs, vents, hazard stripes (45°, 2-px bands),
  stencilled labels, warning placards (radiation trefoil, explosive, biohazard, high voltage).
- **Material palettes** (each a 6–8 shade ramp, hue-shifted: shadows lean cool, highlights lean warm):
  gunmetal, olive drab, NATO green, desert tan, rust/primer red, brushed steel, tungsten (blue-grey), lead
  (dull blue-grey), copper, brass, concrete, hazard yellow, warning red, plus "energy" ramps: nuclear amber,
  Cherenkov blue, antimatter white-violet, singularity violet-black, cryo cyan, toxin green, Anthrax-Gamma purple,
  tesla electric blue, EMP pale cyan.
- **Wear:** edge chipping exposing primer, scratches along handled edges, soot near exhausts, drips below vents,
  sun-bleaching on top faces. Applied with deliberate stamps, never random noise fill.
- **Emissive layers:** lights, screens, energy cores and charge indicators render fullbright on a separate emissive
  texture (GeckoLib auto-glow layer / emissive model faces), with charge-dependent brightness where relevant.
- **Recognisable by silhouette and colour** — e.g. the H-bomb is a long olive-drab cylinder with a ribbed tail and
  yellow bands; the antimatter bomb is a white ceramic capsule with glowing violet magnetic rings and no fins; the
  singularity bomb is a matte-black sphere clamped in a gyroscopic ring with a violet void core. See each weapon's
  "Look" line.
- **Original assets only.** C&C Generals / Rival Rebels / ICBM inspire *themes*; no textures, models or sounds are
  copied or traced.

### 4.2 Pipeline (`tools/`, built in Phase 2)
Run everything with the project venv: `tools\.venv\Scripts\python.exe` (numpy, Pillow, scipy, soundfile — see
`tools/requirements.txt`; create with `python -m venv tools\.venv` + `pip install -r tools\requirements.txt`).

| Tool | Purpose |
|---|---|
| `tools/gz/palette.py` | OKLCH-generated 7-step ramps (`outline, deep, shadow, base, light, highlight, shine`) with hue shifting: shadows toward purple-blue (via red for warm hues, so yellows never turn green), highlights toward yellow. Hand-picked ramps for hazard colours. Swatches: `docs/style/palettes.png`. |
| `tools/gz/tex.py` | `Tex` canvas + primitives: bevelled panels, rivets, seams, vents, grilles, hazard stripes, 3×5 stencil font, decals, trefoil, placards, seeded wear (corner-biased edge chips, scratches, drips, sparse speckle). |
| `tools/gz/model.py` | Block/item model DSL: boxes → automatic shelf-packed per-face texture layout (texture height shrinks to fit) → paint each face as seen from outside → block JSON with `display` transforms. `fit_display()` fits oversized models into every slot (GUI fitted by projected silhouette). Supports XYZ/axis element rotation and `light_emission` (both verified in 26.3). |
| `tools/gz/shapes.py` | Pixel-stepped cylinders and lathe profiles from non-overlapping row bands; `band_tone()` bakes smooth curvature lighting so round bodies don't look ribbed (those boxes use `shade:false`). |
| `tools/gz/geo.py` | GeckoLib model + animation DSL authored in the same world orientation and paint conventions, exported to Bedrock geometry the way GeckoLib 5.5.7 reads it (X negated, up/down UVs rotated 180°, rotation X/Y negated). Files go to `assets/groundzero/geckolib/models/` and `geckolib/animations/`; glow layers as `<texture>_glowmask.png`. |
| `tools/preview/render.py` | Numpy rasteriser replicating vanilla face/UV orientation, element rotations, directional shade, cutout, light emission; renders 8 views + true-size inventory icon into a contact sheet. |
| `tools/preview/geo_preview.py` | Same for GeckoLib models, following GeckoLib's baking maths; renders animation frames. |
| `tools/sound/synth.py`, `recipes.py`, `review.py` | Synthesis toolkit (noise colours, sweeps, grains, filters incl. time-varying low-pass, saturation, convolution reverb, echo, limiter, distance re-rendering) → mono 44.1 kHz OGG Vorbis; recipes per sound; review renders waveform + log spectrogram with peak/RMS/clipping readout. |
| `tools/structure/nbt.py`, `template.py` | NBT writer and structure templates (26.3 block-state keys `id`/`properties`, data version 5023). |
| `tools/assets/*.py` | One script per asset family (ores, fission bomb, ore crusher, …). Assets are generated deterministically and committed. |
| `tools/style_guide.py` | Rebuilds `docs/style/` (palette swatches + reviewed pilot previews). |

**Model conventions:** a model's front is its **north** face (like vanilla furnaces); block models use 0–16 units,
GeckoLib models are centred on the block's bottom centre (x, z −8…8). 16 texture pixels per block everywhere.

Each asset gets a "review" status in PROGRESS.md: *drafted → previewed → revised → approved*.

---

## 5. Resources and world generation

| Resource | Ore blocks | Where | Vein size / frequency | Drops & processing |
|---|---|---|---|---|
| **Uranium** (pitchblende) | `uranium_ore`, `deepslate_uranium_ore` | Overworld Y −48…24, triangle peak −16 | 4, 3/chunk; +50% in badlands | Raw uranium → crush → 2 uranium dust. Ore emits faint radiation (Geiger finds it) and a dim green glint. |
| **Lithium** (spodumene) | `lithium_ore`, `deepslate_lithium_ore` | Overworld Y −16…64; extra in mountains/stony peaks | 6, 4/chunk | Raw lithium → crush → 2 lithium dust |
| **Sulfur** | `sulfur_ore`, `deepslate_sulfur_ore`, `nether_sulfur_ore` | Overworld Y −64…16 (dense near lava lakes); Nether everywhere | 8, 5/chunk; Nether 10, 8/chunk | Drops 2–4 sulfur dust (fortune). **Vanilla sulfur blocks** crush to 4 sulfur dust, potent sulfur to 6 — the Sulfur Caves biome becomes a sulfur source. |
| **Saltpeter** (niter) | `saltpeter_ore`, `deepslate_saltpeter_ore`, `sandstone_saltpeter_ore` | Overworld Y 0…80; sandstone variant in deserts/badlands near the surface | 7, 4/chunk | Drops 2–3 saltpeter dust |
| **Tungsten** (wolframite) | `tungsten_ore`, `deepslate_tungsten_ore` | Overworld Y −64…0, peak −40 | 5, 2/chunk | Raw tungsten → crush → 2 tungsten dust → **Arc Furnace only** → ingot (too hot for a furnace) |
| **Lead** (galena) | `lead_ore`, `deepslate_lead_ore` | Overworld Y −32…48 | 8, 5/chunk | Raw lead → smelt/crush. Radiation shielding, hazmat lining, ammo. *(Added: shielding needs a material.)* |
| **Rare earths** (monazite) | `rare_earth_ore`, `deepslate_rare_earth_ore` | Overworld Y −32…48; +100% in mountains | 4, 2/chunk | Raw → crush → rare-earth dust (5% uranium dust byproduct: monazite carries thorium and some uranium) |
| **Mercury** (vanilla cinnabar) | — (uses vanilla 26.2 cinnabar blocks from Sulfur Caves) | Sulfur Caves | — | Cinnabar → crush → cinnabar dust → Arc Furnace → mercury flask (lithium-6 enrichment). |
| **Cobalt** (byproduct) | — | — | — | Byproduct of crushing copper (15%, as in real copper–cobalt ores) and iron (10%) → cobalt dust (for salted bombs). No new ore: keeps the ore list focused. |
| **Exotic matter** | `exotic_crystal_cluster` (+ budding `voidstone`) | **End**: "void geodes" hanging under outer End islands (rare, 1 per ~12 chunks). Fragments also in Ancient City loot. | geode ~11 blocks | Exotic shard → Exotic Matter Fabricator. Clusters hum and distort light (subtle shader lensing). |

All ore blocks have stone/deepslate (and nether/sandstone where listed) textures with the ore drawn as real
crystal/mineral shapes (pitchblende: black-green botryoidal lumps; spodumene: pale pink prismatic crystals; sulfur:
yellow crusts; niter: white efflorescence; wolframite: dark blocky crystals with metallic sheen; galena: silver cubes;
monazite: reddish-brown grains; exotic: violet crystals with a black core).

Storage blocks: raw-ore blocks and ingot blocks for every metal (uranium is radioactive as a block: carrying a block of
enriched uranium in your inventory hurts).

**World structures** — see §22.

---

## 6. Materials and intermediates

| Item | Made in | From |
|---|---|---|
| Uranium / Lithium / Sulfur / Saltpeter / Tungsten / Lead / Rare-earth / Cobalt / Iron / Gold / Copper **dust** | Ore Crusher | ores / raw ores / ingots |
| Steel ingot | Arc Furnace | iron ingot/dust + coal/charcoal |
| Tungsten ingot, Tungsten carbide | Arc Furnace | tungsten dust (+ coal for carbide) |
| Lead ingot | Furnace or Arc Furnace | raw lead / lead dust |
| Natural uranium ingot | Furnace | uranium dust |
| Plates (steel, dense steel, tungsten, lead, copper) | Industrial Press | ingots |
| Rods (steel, tungsten), wire (copper, gold), tungsten filament | Industrial Press | ingots |
| Gunpowder (renewable) | Crafting | 2 saltpeter + 1 sulfur + 1 charcoal → 4 |
| Sulfuric acid canister | Chemical Plant | sulfur dust + water + empty canister |
| Nitric acid canister | Chemical Plant | saltpeter dust + sulfuric acid |
| Synthetic fuel canister | Chemical Plant | coal/charcoal ×2 + water |
| Plastic sheet | Chemical Plant | synthetic fuel + coal |
| RDX crystals (high explosive) | Chemical Plant | nitric acid + coal + water |
| Plastic explosive | Chemical Plant | RDX + plastic sheet + slimeball |
| Thermobaric slurry | Chemical Plant | synthetic fuel + RDX + iron dust |
| Napalm gel | Chemical Plant | synthetic fuel + plastic sheet + slimeball |
| Solid propellant | Chemical Plant | saltpeter + sulfuric acid + iron dust + slimeball ("ammonium perchlorate composite") |
| Liquid oxygen / Liquid nitrogen canister | Cryogenic Condenser | air (power) + empty canister |
| Liquid rocket fuel | Chemical Plant | synthetic fuel + liquid oxygen |
| Toxin precursor, Anthrax α/β/γ cultures | Chemical Plant | rotten flesh + spider eye + sulfuric acid (+ tiers add fermented spider eye / exotic shard) |
| Yellowcake | Chemical Plant | uranium dust + sulfuric acid |
| UF₆ canister | Chemical Plant | yellowcake + sulfuric acid + empty gas canister *(gameplay simplification of fluorination)* |
| Enriched uranium (U-235) nugget/ingot | Gas Centrifuge | UF₆ canister → 2 U-235 nuggets + 1 depleted uranium dust |
| Depleted uranium ingot | Furnace | depleted uranium dust (dense penetrators, tampers, AP rounds) |
| Uranium fuel rod | Assembly (crafting) | 3 natural uranium ingot + zircaloy… → **simplified:** 3 natural U ingot + 2 steel rod |
| Depleted-uranium target rod / Lithium-6 target rod | Crafting | DU ingot ×3 + steel rod ×2 / Li-6 dust ×3 + steel rod ×2 |
| Plutonium nugget/ingot, Nuclear waste | Chemical Plant (reprocessing) | irradiated DU target rod → 3 Pu nuggets + 1 nuclear waste |
| Tritium canister | Chemical Plant | irradiated Li-6 target rod + empty gas canister |
| Mercury flask | Arc Furnace (roasting) | 4 cinnabar dust (from crushing **vanilla cinnabar** blocks) + empty canister |
| Lithium-6 dust | Chemical Plant ("COLEX" process) | 4 lithium dust + mercury flask → 1 Li-6 dust + 3 lithium dust; flask returned 90% of the time |
| Deuterium canister | Electrolytic Separator | water (8 000 mB) + empty gas canister |
| Lithium deuteride pellet | Fusion Fuel Synthesizer | Li-6 dust + deuterium canister |
| Cobalt-59 jacket | Arc Furnace → Press | cobalt dust ×8 → cobalt ingot → jacket |
| Neodymium magnet | Arc Furnace | rare-earth dust ×2 + iron dust |
| Circuits: basic / advanced / military / quantum | Circuit Fabricator | see §26 |
| Electric motor, heating element, pressure vessel, machine frame, servo, sensor array, antenna, CRT display | Crafting / Press | see §26 |
| Stabilised exotic matter, containment cell | Exotic Matter Fabricator | exotic shards, magnets, power |
| Antimatter cell | Particle Accelerator | empty containment cell + huge power over time |
| Superconducting coil | Circuit Fabricator | tungsten filament + rare-earth + LN₂ canister + gold wire |
| Empty canister / empty gas canister | Press | 2 steel plate / 2 steel plate + glass |

---

### 6.4 Fluids
- Chemical fluids: sulfuric acid, nitric acid, synthetic fuel, liquid rocket fuel, thermobaric slurry, napalm gel,
  liquid oxygen, liquid nitrogen (liquids: bucket + placeable flowing block) and deuterium, uranium hexafluoride
  (gases: an upside-down bucket; released gas is a rising cloud that pools under ceilings, spreads sideways and thins
  under open sky; UF6 is toxic). Every fluid is tagged `c:<name>`; recipe fluid inputs use the tag.
- Canisters are 1000 mB containers: an empty canister fills like a bucket (liquids in liquid canisters, gases in gas
  canisters) and a filled one pours out. Canister items stay the ingredient form for later weapons.
- Spills: acids deal acid damage, napalm sets things alight, LOX/LN2 freeze like powder snow. Fuels and slurry are inert.
- Machines have up to four tanks: input tanks (filled from outside; a water tank only ever holds water and has no
  dump) and product tanks (drained from outside). Fluids are meant to move by pipe; by hand there is only
  right-click with a full bucket/canister (pours into the input tank that takes it) and sneak-right-click with an
  empty one (fills from a product tank). Every tank except water has a dump button (shift-click). Recipes take
  fluids from distinct input tanks and must fit every fluid product before they run.
- Water Pump: on a water source block, 50 mB/t for 20 FE/t, source never used up; pushes out of its two side
  outlets only (the flanges on the model).
- Fluid Tank: 16,000 mB per block; touching tanks join when they fill a box with a square base up to 3x3 and up to
  5 high (720,000 mB). Breaking a block splits the tank, the rest keeps its share. Passive: fluid moves in and out
  only by pipe.
- Fluid pipes form pass-through networks with no buffer (2000 mB/t). Each pipe side is normal (in and out), pull
  (draws from the tank or machine it touches, up to 1000 mB every 2 ticks; hazard-striped collar) or disconnected.
  Machines push product tanks out of faces with auto-eject and fluids on; pull sides draw; the run delivers to its
  normal sides, never back to the source.
- Wrench (conventions of Mekanism's configurator / Pipez's wrench): right-click a pipe side cycles normal -> pull ->
  disconnected (pipe-to-pipe joins: on/off, both pipes); right-click a cable side connects/disconnects it (cables
  have no push/pull); right-click a machine turns it; sneak-right-click picks up pipes, cables, machines, tanks and
  structure blocks.

## 7. Power

All machines use **NeoForge Energy (FE)** so they interoperate with other tech mods (decision D2).

| Block | Output / capacity | Notes |
|---|---|---|
| Combustion Generator | 60 FE/t | Burns any furnace fuel; synthetic fuel canister = 24 000 FE. Exhaust smoke, rumble loop. |
| Diesel Generator | 180 FE/t | Burns synthetic/liquid fuel canisters only. Piston animation. |
| RTG (radioisotope thermoelectric generator) | 40 FE/t forever-ish | Plutonium ingot lasts 10 in-game days; emits low radiation if not shielded by lead blocks. Glowing fins. |
| Breeder Reactor (multiblock) | 1 200 FE/t | Burns uranium fuel rods, breeds plutonium from DU target rods and tritium from Li-6 target rods. Cherenkov-blue core. Needs water coolant; overheats & SCRAMs if starved (no meltdown griefing by default; meltdown is a config option). |
| Capacitor Bank (T1/T2/T3) | 100k / 1M / 10M FE | Superweapons draw from these during charge. Charge gauge on the model. |
| Power cables: copper (256 FE/t), insulated (1 024), high-voltage (8 192) | — | Connected-texture cable models with insulators on HV; HV cable arcs when EMPed. |

Superweapons and large machines need power while charging (as in Generals: low power pauses the countdown).

---

## 8. Processing machines

Every machine: own detailed model (animated with GeckoLib where it has moving parts), custom GUI in the shared military
theme, working / idle / jammed (EMP) states, looping work sound + start/stop sounds, side configuration for automation
(item/energy capabilities), progress bar, energy bar, tooltips, and redstone control (on / off / pulse).

| Machine | Size | Power | Function | Look & animation |
|---|---|---|---|---|
| **Ore Crusher** | 1 block | 20 FE/t | ore/raw/ingot → dust ×2 (+ byproduct chances) | Olive steel hopper over twin toothed rollers that visibly spin; dust puffs. |
| **Arc Furnace** | 1×2 tall | 80 FE/t | alloys & high-temp smelting (steel, tungsten, carbide, magnets, cobalt) | Squat crucible with three graphite electrodes that lower; bright arc glow through slots; sparks. |
| **Industrial Press** | 1×2 tall | 40 FE/t | plates, rods, wire, canisters, casings (with die slot) | Heavy C-frame press, hydraulic ram stroke animation, hazard-striped guard. |
| **Chemical Plant** | 2×2×2 (multiblock-lite, single controller) | 60 FE/t | all chemistry (acids, fuels, explosives, toxins, yellowcake, reprocessing) | Two glass-windowed tanks with bubbling coloured liquid, piping manifold, stirrer motor. |
| **Gas Centrifuge** | 1×2 tall | 50 FE/t | uranium enrichment (UF₆ → U-235) | Tall slim cylinder; spinning rotor visible through a slit; **cascade bonus**: each adjacent centrifuge in a straight line (max 8) speeds all by 15%. Rising whine. |
| **Electrolytic Separator** | 1 block | 60 FE/t | water → deuterium | Tank with electrodes and bubbling, two gas bottles on the side. |
| **Fusion Fuel Synthesizer** | 1 block | 120 FE/t | Li-6 + D → LiD pellets | Pressure chamber with gauges and a frosted viewport; pellet press clunk. |
| **Cryogenic Condenser** | 1×2 tall | 100 FE/t | LOX, LN₂, cryo cores | Frosted coils, vapour vents, ice crust grows on the model while working. |
| **Circuit Fabricator** | 1 block | 40 FE/t | circuits, sensor arrays, superconducting coils | Glass-topped clean box, robotic arm (animated) placing chips; green PCB visible. |
| **Exotic Matter Fabricator** | 1×2 tall | 400 FE/t | stabilised exotic matter, containment cells, singularity seeds | Magnetic cage of rings around a floating violet shard; rings rotate on different axes; lensing shimmer. |
| **Particle Accelerator** | multiblock ring 9×9×3 | 2 000 FE/t | antimatter cells (slow) | Ring of magnet segments + injector + target chamber; a glowing bunch races around the ring when running. |
| **Breeder Reactor** | multiblock 3×3×4 | generates | power + Pu + tritium | See §7. |
| **Ordnance Assembly Bench** | 2×1 wide | 10 FE/t (optional) | assembles bombs, warheads and missiles | See §10. |
| **Decontamination Shower** | 1×2 tall | 20 FE/t | removes contamination from player & items | Shower frame with yellow nozzles, water + foam particles. |
| **Battery Charger** | 1 block | — | recharges energy cells/guns/Geiger | Cradle with charge LEDs. |

---

## 9. Components

Weapons are assembled from components. Every component is an item with its own model (they look good in the bench
preview and in item frames) and tooltip stats.

### 9.1 Size classes
Every payload has a **size class**: **S** (tactical), **M** (medium), **L** (heavy), **XL** (strategic). Casings,
missile bodies, propulsion and launch platforms are rated for size classes. A payload only fits a casing/body of
its class (a body of a larger class accepts a smaller payload, with a range penalty).

### 9.2 Payload cores (determine the effect)
| Payload core | Class | Produces | Key ingredients (§26) | Tier |
|---|---|---|---|---|
| HE Charge | S | HE Bomb | RDX, steel plate | 1 |
| Heavy HE Charge | M | Heavy Bomb (general purpose) | 4 RDX, plastic explosive | 2 |
| Thermobaric Charge | M | Thermobaric Bomb | thermobaric slurry, burster charge | 2 |
| Incendiary Charge | S | Napalm Bomb | napalm gel ×4 | 1 |
| Cluster Dispenser | M | Cluster Bomb | 12 bomblets (HE + small casing) | 2 |
| Penetrator Charge | M | Bunker Buster | DU or tungsten-carbide penetrator nose, HE charge, delay fuze | 2 |
| MOAB Charge | XL | MOAB | 16 RDX, 4 thermobaric slurry | 2 |
| Toxin Canister (α/β/γ) | S | Chemical Bomb | toxin culture, pressure vessel | 2–4 |
| Dirty Payload | S | Dirty Bomb | nuclear waste ×4, HE charge, lead plate | 3 |
| Tactical Fission Core | S | Tactical Nuke | 3 Pu nuggets, small HE lenses, steel reflector | 3 |
| Fission Core | M | Fission Bomb ("A-bomb") | gun-type: 2 HEU ingots + DU tamper; **or** implosion: 1 Pu ingot + 32 HE lenses | 3 |
| Boosted Fission Core | M | Boosted Fission Bomb | fission core + tritium canister + deuterium canister | 3 |
| Fusion Secondary | part | — | LiD pellets ×4, DU tamper, plastic foam, lead radiation case | 4 |
| Thermonuclear Assembly | L | H-Bomb | boosted fission core (primary) + fusion secondary | 4 |
| Tertiary Stage | part | — | fusion secondary ×2 + lead radiation channel | 4 |
| Tsar Assembly | XL | Tsar-class Mega-bomb | thermonuclear assembly + tertiary stage + military circuits | 4 |
| Neutron Core | M | Neutron Bomb | small fission core + tritium ×2 + tungsten reflector (no U-238 tamper) | 4 |
| Cobalt Jacket | modifier | turns a Thermonuclear Assembly into a **Cobalt Bomb** (L) | cobalt-59 jacket | 4 |
| Storm Core | M | Storm Bomb | superconducting coils, tesla capacitors, deuterium, rare earths | 4 |
| Cryo Core | M | Cryo Bomb | LN₂ canisters ×6, cryo crystal seed, pressure vessel | 4 |
| EMP Coil (flux-compression generator) | M | EMP Bomb | copper coil, HE charge, capacitor, neodymium magnets | 4 |
| Singularity Core | M | Singularity Bomb | singularity seed, containment cells ×4, quantum circuit | 5 |
| Antimatter Trap | M | Antimatter Bomb | antimatter cells ×4, Penning trap (superconducting coils), quantum circuit | 5 |
| MIRV Bus | L / XL (missile only) | MIRV | military circuits, servos, warhead mounts | 4 |

### 9.3 Casings (bombs) and missile bodies
| Casing / Body | Class | Notes |
|---|---|---|
| Light Bomb Casing | S | finned steel shell |
| Medium Bomb Casing | M | |
| Heavy Bomb Casing | L | |
| Super-heavy Bomb Casing | XL | parachute-retarded, like the real Tsar |
| Tactical Missile Body | S | short, 4 fins |
| Medium Missile Body | M | |
| Heavy Missile Body | L | |
| Strategic Missile Body | XL | multi-stage stack |
| Cruise Missile Body | S–M | pop-out wings, air intake; flies low |

### 9.4 Fuzes
| Fuze | Placed-bomb behaviour | Missile behaviour | Settings |
|---|---|---|---|
| **Impact** | Detonates if it falls, is shot, or is caught in another explosion (sympathetic detonation), or by redstone when armed | Detonates on contact | — |
| **Timed** | Countdown starts when an armed bomb is triggered (redstone / right-click); beeps faster near zero | Detonates N seconds after launch | 1–600 s |
| **Proximity** | Armed bomb detonates when a living entity comes within range (ignores sneaking players holding the Arming Key) | Detonates at a set height above ground (airburst) | 2–16 blocks / 10–200 blocks AGL |
| **Remote** | Detonates on a matching Remote Detonator signal | Can be detonated mid-flight from a detonator | frequency 0–9999 |
| **Barometric (altitude)** | Behaves as Impact when placed | **Airburst**: larger surface damage, wider fire, shallower crater, less fallout. Ground burst: deeper crater, more fallout. | burst height |

All placed bombs are **SAFE when placed** and must be **armed** (shift-right-click with an Arming Key, or from the
remote / launch console). An armed bomb blinks a red lamp. Redstone triggers any armed bomb regardless of fuze.

**Nuclear bombs never chain-detonate.** A nuke caught in another blast is destroyed: the core breaks and scatters
radiation like a dirty bomb ("fizzle"). Conventional bombs do sympathetically detonate.

### 9.5 Guidance (missiles)
| Guidance | Accuracy | Notes |
|---|---|---|
| None (ballistic) | ±8% of range | cheapest |
| Inertial | ±2% of range | |
| GPS / Coordinate | ±2 blocks | military circuit |
| Laser-guided | hits the designator dot; tracks moving targets | needs an active Laser Designator lasing the target during the terminal phase, else falls back to inertial |
| Hardened variants | as base | ×2 cost; immune to EMP |

### 9.6 Propulsion
| Propulsion | Max class | Range (blocks) | Flight profile |
|---|---|---|---|
| Solid Rocket Motor I | M | 250 | ballistic, low apex |
| Solid Rocket Motor II | L | 800 | ballistic |
| Liquid-fuel Stage | L | 2 500 | ballistic, high apex |
| ICBM Booster Stack | XL | 10 000 | 2-stage, visible stage separation |
| Three-stage Strategic Booster | XL | unlimited (config cap, default 30 000) | 3-stage, exo-atmospheric apex |
| Turbojet (cruise) | M | 3 000 | cruises ~40 blocks AGL, terrain-following, slow, hard to detect |

### 9.7 Optional modules (one per missile)
- **Countermeasure pod** — decoys/flares in the terminal phase: −25% interception chance.
- **Stealth coating** — radar detection range against this missile ×0.5.
- **Hardened electronics** — EMP immunity.
- **Re-entry heat shield** — required for the Strategic class to survive a high apex (visual: glowing re-entry plasma).

---

## 10. Assembly

**Ordnance Assembly Bench** (2-wide block: steel workbench with a hoist, tool rack, clamp jaws, schematic lamp).

GUI tabs: **Bomb / Warhead** · **Missile** · **Ammunition**.
- Component slots sit on a blueprint-style line drawing of the weapon being built; empty slots show a ghost icon.
- Right panel: live **3D preview** of the result (drag to rotate) + **stats readout**: payload, yield (kt/Mt flavour
  figure), crater / blast / fire / lethal / radiation radii, fuze mode, range, CEP, apex, flight time to a typed
  distance, interception difficulty, EMP hardening.
- Validation messages in red stencil text ("PAYLOAD CLASS L EXCEEDS BODY CLASS M").
- Fuze settings are set here and can be changed on a placed bomb with the Arming Key GUI.
- **Assemble** takes time (5 s conventional … 30 s Tsar) with ratchet/clank sounds and an animated hoist.
- Output: a **bomb block item** or a **missile item** with a data component holding its full configuration.
- Disassembly returns 100% of components (reconfigure fuzes/guidance freely).
- Optional Blueprint slot (§22 loot) halves assembly time and shows lore.

Missile = Warhead (payload + fuze) + Body + Guidance + Propulsion (+ module). Bomb = Payload + Casing + Fuze.
**A finished bomb can be dropped into the warhead slot of the Missile tab**, so every bomb can also be launched.

---

## 11. Explosion engine (`dev.groundzero.blast`)

### 11.1 Blast profiles
Each weapon references a datapack-registry **BlastProfile**:
```
core           r_core      every block destroyed except unbreakable/bedrock (configurable), no drops
crater         r_crater    ray-energy destruction (respects blast resistance); crater_depth shapes the bowl
structural     r_struct    damage table: glass shatters, leaves stripped, wood charred, stone cracked, doors blown off
scorch         r_scorch    surface conversion (grass -> scorched earth, sand -> trinitite near core, ash layers)
fire           r_fire      exposed flammable surfaces ignite (probability falls with distance)
entity_lethal  r_lethal    lethal damage when exposed
entity_damage  r_damage    damage + knockback falling off with distance and exposure
radiation      r_rad       seeds the radiation field (section 21)
front_speed    target blocks/tick of the destruction/shock front
terrain_table  id of the block-conversion table
drops          0..1 drop chance (0 for anything big)
fallout        intensity, half-life, drift
visual, sound  ids of the client visual preset and sound set
```

### 11.2 Algorithm: adaptive shell ray field
- Rays start on a **cube-sphere grid** (6 faces × 4×4). The field advances **one shell (1 block of radius) at a time**,
  so the destruction front expands evenly, which is exactly what the visual shockwave shows.
- When the spacing between neighbouring rays at the current radius exceeds ~0.7 blocks, each ray **splits into 2×2
  children** inheriting its remaining energy, so every block of every shell is reached with no holes at any radius.
- Each ray carries **energy**. Entering a block costs `air_cost + resistanceScale × resistance^k`. Inside `r_core`
  everything breakable goes; beyond it blocks are destroyed while energy remains, so dense materials **shield what is
  behind them** (an obsidian bunker 30 blocks from a fission blast survives; bedrock casts a "shadow").
- Initial energy varies by direction (downward rays get `crater_depth`× energy, giving bowl craters, not spheres) and
  by smooth noise over the sphere (lobed, irregular rims). Seeded, so client and server agree.
- A ray whose energy is spent continues as a **pressure ray** through the structural/scorch/fire bands, applying the
  conversion table instead of destroying.
- A per-section bitset prevents double-processing where split rays overlap.

### 11.3 Budgeting and writing
- Each `BlastJob` advances inside a **per-tick time budget** (default 12 ms across all jobs) and a **block-change
  budget** (default 60 000/tick). Jobs share the budget; the front advances more slowly if the server is busy, and
  the server never freezes.
- Changes are buffered per chunk section and written **directly to the section** (no per-block neighbour updates
  inside the blast volume), then heightmaps are updated, touched sections are re-lit in batches, and one update packet
  per touched section goes to tracking players. Neighbour updates fire only along the outer boundary, so sand,
  gravel and water at the rim behave naturally (water floods craters).
- Block entities in destroyed positions are removed cleanly (contents voided, no item spew).
- Small blasts (R ≤ 8: grenades, C4, mines, rockets) run the same code in **instant mode** (whole job in one tick).

### 11.4 Entities
When the front passes an entity it takes `f(distance) × exposure` damage, where exposure is the fraction of 4 rays
from the burst point to its bounding box not blocked by solid blocks. Knockback is radial. Exposed entities inside the
fire radius ignite. Items and XP orbs in the crater are deleted. Custom damage types (`groundzero:blast`,
`groundzero:nuclear_flash`, `groundzero:radiation`, `groundzero:toxin`, `groundzero:erasure`, ...) with death
messages.

### 11.5 Chunks, persistence, absence
- A `TicketController` (`groundzero:blast`) force-loads the chunks in the active band `[r-16, r+32]` and releases
  them behind the front. Ungenerated chunks are not generated (configurable).
- Jobs live in a per-level `SavedData` (`BlastManager`); ray fields are stored compactly in
  `data/groundzero/blasts/<id>.bin`. On world load, jobs resume and clients re-sync their visuals.
- Jobs never depend on a player being nearby; they finish while the server runs.

### 11.6 Server config (`groundzero-synced.toml`), with dramatic defaults
| Key | Default | Meaning |
|---|---|---|
| `blast.destroyBlocks` | true | master switch for terrain damage |
| `blast.radiusMultiplier` | 1.0 (0.1–2.0) | scales every profile |
| `blast.maxRadius` | 320 | hard cap on any zone radius |
| `blast.msBudgetPerTick` | 12 | time budget across all jobs |
| `blast.maxBlockChangesPerTick` | 60 000 | |
| `blast.maxConcurrentJobs` | 8 | further blasts queue |
| `blast.generateChunks` | false | |
| `blast.protectSpawn`, `blast.protectedDimensions` | false, [] | |
| `blast.disabledWeapons` | [] | weapon ids that fizzle instead of detonating |
| `radiation.enabled`, `radiation.decayMultiplier`, `radiation.spreadEnabled` | true, 1.0, true | |
| `terrain.convertBiomes` | true | paint the wasteland biome around nuclear craters |
| `missiles.maxRange`, `missiles.loadChunksAlongPath` | 30 000, true | |
| `emp.affectsVanillaRedstone` | true | |
| `reactor.meltdowns` | false | |
| `singularity.maxBlocksRipped` | 20 000 | |
| `superweapons.announce`, `superweapons.maxRange`, `superweapons.chargeTimeMultiplier` | true, 4 096, 1.0 | |

Client config (`groundzero-client.toml`): `fx.quality` (low/medium/high/ultra, default high), `fx.flashStrength`
(1.0), `fx.cameraShake` (1.0), `fx.farClouds` (true), `hud.geigerCorner` (bottom right).

---

## 12. Visual effects system (`client.fx`, "Spectacle")

Principle: **the server is authoritative for radii.** Clients receive `BlastStart(id, profile, center, seed,
startTick)` and `BlastFront(id, radius)` every 4 ticks; every visual radius is derived from those numbers. If the
server front slows under load, the shockwave visual slows with it.

| Element | Technique | Used by |
|---|---|---|
| **Flash** | Full-screen additive white-out post effect; intensity = yield × distance falloff × line of sight (3 client raycasts to the fireball); indirect flash behind walls at 25%. Brief over-exposed sky. Afterimage blob at the fireball's screen position. | nukes, antimatter, MOAB (weak), storm strikes (weak) |
| **Fireball** | Sphere mesh, emissive shader with animated 3D noise and limb darkening; grows, rises, becomes the cap's glowing core | nukes, MOAB, thermobaric |
| **Shockwave ring** | Ground-hugging dust torus + translucent refraction shell at the **server front radius** | every medium+ blast |
| **Wilson condensation ring/dome** | Expanding translucent white shell around the stem at ~⅓ height; appears at 1–3 s, fades by 7 s | nukes ≥ fission |
| **Mushroom cloud** | Puff system (400–3 000 large soft billboards by quality) advected by a parametric **toroidal vortex** (rolling cap, rising stem, entrained skirt). Per-puff temperature → colour ramp (white → yellow → orange → red → brown-grey) with emissive glow cooling over 20–60 s; sun lighting + self-illumination; depth-faded soft edges; OIT pipeline. Height scales with yield (tactical +45, fission +120, H-bomb +220, Tsar +320 blocks). Persists 3–6 min, drifts with the wind, dissipates. Rendered independently of chunk render distance. | nukes |
| **Ground dust skirt / base surge** | Low rolling dust ring following the front to `r_damage`, then lingering | nukes, MOAB |
| **Debris** | Client-side chunks textured from the actual destroyed block states (sampled list in the packet), ballistic with bounces | all |
| **Smoke & fires** | Smoke columns over burning areas, embers | most |
| **Heat distortion** | Screen-space refraction from a mask drawn by hot meshes (fireball, beams, exhaust) | nukes, beams, rocket exhaust |
| **Camera shake** | Trauma-based shake added when the **shock front reaches the player** (synced with sound arrival), distance falloff | all |
| **Sky & fog tint** | Darkened orange-brown sky/fog for minutes after big blasts; storm overcast; singularity desaturation | nukes, storm, singularity |
| **Beams** | Layered additive cylinders with scrolling noise: core + halo + sparks + ground scorch decal | particle cannon/rifle, tesla, designator |
| **Gravitational lensing** | Screen-space distortion around the singularity's projected position, scaled by its mass | singularity bomb & cannon, exotic fabricator (subtle) |
| **Gas clouds** | Server-simulated coarse gas grid (§16.7) drawn as soft tinted puffs | toxins, anthrax, fuel-air, cryo mist |

Client config: `fx.quality` (low/medium/high/ultra), `fx.flashStrength`, `fx.cameraShake`, `fx.farClouds`.

---

## 13. Sound system

- Every sound is synthesised by `tools/sound` (layered noise, filtered sweeps, convolution reverb, saturation), is
  **mono**, and has a subtitle.
- **Distance-delayed sound:** clients compute `delay = distance / 17.15` ticks (343 m/s, 1 block = 1 m) and schedule
  the boom; the flash arrives first. Variant by distance band: **near** (< 0.5·r_damage: sharp crack + roar + debris),
  **mid** (boom + long rumble), **far** (low-passed thud + rolling tail), **very far** (sub-bass rumble only).
- Big sounds play through a custom `SoundInstance` placed at a capped virtual distance in the true direction with our
  own loudness curve, so they stay directional and audible kilometres away (vanilla attenuation would cut them off).
- Looping sounds (missile engines, machines, singularity drone, storm wind, beam hum) use tickable sound instances
  that follow their source and fade.

Sound list (each with its own synthesis recipe):
`blast.small/medium/large/nuke/tsar × near/mid/far/very_far`, `grenade.pin`, `grenade.bounce`, `c4.place`, `c4.beep`,
`bomb.arm`, `bomb.timer_tick`, `bomb.timer_final`, `mine.click`, `claymore.trip`, `missile.ignite`, `missile.engine`
(loop), `missile.flyby`, `missile.reentry`, `missile.stage_sep`, `launch.siren`, `launch.countdown_beep`,
`pad.clamps_release`, `pad.erect`, `silo.doors`, `silo.elevator`, `radar.ping`, `radar.alarm`, `interceptor.launch`,
`interceptor.hit`, `geiger.click` (4 variants), `geiger.alarm`, `uplink.charge` (loop), `particle_cannon.fire`,
`particle_cannon.beam` (loop), `particle_cannon.impact`, `scud.raise`, `scud.launch`, `toxin.hiss` (loop),
`storm.wind` (loop), `storm.thunder_near/far`, `tesla.crack`, `singularity.drone` (loop), `singularity.rip`,
`singularity.collapse`, `antimatter.whine`, `antimatter.erase`, `cryo.freeze`, `cryo.crystal_grow`, `cryo.shatter`,
`emp.zap`, `emp.power_down`, `emp.crackle` (loop), `orbital.streak`, `orbital.impact`, `rod.whistle`, machine loops
(crusher, arc furnace, press, chemical plant, centrifuge, separator, synthesizer, condenser, fabricator, exotic
fabricator, accelerator, reactor hum, generators), machine start/stop/jam, UI (`ui.click`, `ui.teletype`,
`ui.confirm`, `ui.error`), gun sounds per weapon (§20), `decon.shower`, `hazmat.breath` (loop while masked).

---

## 14. Conventional explosives

Radii in blocks. "Blast" = crater/destruction radius. All have impact/redstone behaviour per §9.4 where placed.

| Weapon | Deployment | Blast | Other zones / behaviour | Look |
|---|---|---|---|---|
| **Dynamite** | thrown (3 s fuse) | 2.5 | early game, crafting table | bundled red sticks, sputtering fuse with spark particles |
| **Frag Grenade** | thrown (4 s) | 2 (weak) | 24 shrapnel fragments (real projectiles, 6 dmg each), lethal 4, damage 8 | olive pineapple body, spoon + pin, yellow band |
| **Incendiary Grenade** | thrown, impact | 1 | fire carpet r 4, burning 8 s | red canister, white stencil "INC" |
| **Sticky Grenade** | thrown, sticks to entities/blocks (4 s) | 3 | | dark grey with a tar-coated base |
| **Toxin Grenade** | thrown | 0 | toxin cloud r 4, 20 s | green canister with skull decal |
| **Cryo Grenade** | thrown | 0 | freeze r 4 | frosted cyan canister |
| **EMP Grenade** | thrown | 0 | EMP r 6, 20 s | grey disc with copper coil window |
| **Smoke Grenade** | thrown | 0 | dense smoke r 5, 30 s; blocks mob targeting and designator lasers | grey canister, coloured cap |
| **C4 Charge** | placed on any face, remote/timer/redstone | 4.5 | stacking: up to 4 on one block → blast 4.5·n^⅓ | putty block with taped detonator, blinking LED |
| **Breaching Charge** | placed on a face | shaped: removes a 3×3 hole 3 deep | no damage behind; entity knockback through the hole | frame of det-cord on a plate |
| **Thermite Charge** | placed | melts a 1×1 shaft 8 deep through anything with resistance < 50 | molten-slag glow, sparks rain | ceramic pot with magnesium fuse |
| **Timed Bomb** | placed; GUI timer | 6 | ticking + beeping, defusable (cut-the-wire mini-GUI, risk) | steel box, clock face, wired dynamite bundles |
| **Landmine** | placed (semi-buried), pressure | 2.5 | | olive disc with pressure plate, half sunk into the block |
| **Bouncing Mine** | placed, pressure | 3 (airburst at 1.2 m) | 30 shrapnel | canister that pops out of the ground |
| **Claymore** | placed facing; laser tripwire | 0.5 | 60° cone shrapnel, range 12 | curved olive box "FRONT TOWARD ENEMY"-style lettering (original wording) |
| **Thermobaric Bomb** | bomb / warhead (M) | 12 (weak) | overpressure lethal 24, damage 40, fire 16; ×1.5 damage to entities in enclosed spaces (caves/rooms); fuel-cloud dispersal 0.6 s before ignition | stubby grey bomb with orange bands and nozzle ring |
| **Cluster Bomb** | bomb / warhead (M) | — | opens at height, scatters 24 bomblets (r 2.5 each) over r 18; 5% duds remain as live mines | clamshell casing that splits open |
| **Bunker Buster** | bomb / warhead (M) | 9 | burrows up to 40 blocks through anything below obsidian resistance (obsidian costs 10 blocks of depth), then detonates | long slender dark-grey penetrator, pointed tungsten nose |
| **Napalm Bomb** | bomb / warhead (S) | 2 | napalm fire ellipse 30×12 along flight direction; napalm fire burns 30 s and is not put out by rain | silver finless canister |
| **HE / Heavy Bomb** | bomb / warhead (S / M) | 6 / 12 | standard | classic olive general-purpose bomb, yellow nose band |
| **MOAB** | bomb / warhead (XL) | 28 | structural 40, lethal 40, damage 70; grey dust mushroom; flash (weak); parachute drop | huge orange-and-olive bomb with lattice fins |
| **Chemical Bomb** | bomb / warhead (S) | 3 | toxin cloud r 24, 120 s (α/β/γ strength) | green bomb with biohazard decal |

## 15. Nuclear weapons

| Weapon | Class | Yield (flavour) | Core | Crater (depth) | Structural | Fire | Lethal | Damage | Radiation (half-life) | Look |
|---|---|---|---|---|---|---|---|---|---|---|
| **Mini-nuke** (launcher round) | — | 10 t | 3 | 8 (0.5) | 12 | 14 | 14 | 24 | 20 (0.5 day) | small finned spherical shell with yellow trefoil |
| **Tactical Nuke** | S | 300 t | 6 | 16 (0.45) | 24 | 28 | 28 | 48 | 48 (1 d) | backpack-sized olive cylinder, carry handle, arming panel with keyed switch |
| **Fission Bomb** | M | 15 kt | 14 | 40 (0.4) | 60 | 72 | 70 | 120 | 120 (2 d) | fat yellow-ochre ovoid with box tail and bolted equator seam |
| **Boosted Fission Bomb** | M | 45 kt | 18 | 52 (0.4) | 78 | 92 | 90 | 150 | 140 (2 d) | sleeker grey ovoid, gas-reservoir bulge, cyan boost-gas window |
| **Thermonuclear H-Bomb** | L | 1.2 Mt | 30 | 90 (0.35) | 135 | 160 | 160 | 260 | 260 (3 d) | long olive-drab cylinder, ribbed tail, two yellow bands, "two-stage" bulge |
| **Tsar-class Mega-bomb** | XL | 50 Mt | 50 | 160 (0.3) | 240 | 280 | 280 | 420 | 400 (3 d) | enormous dark-green bomb, 2×2×5 footprint, parachute pack, original Cold-War stencil markings, glowing cooling vents |
| **Neutron Bomb** | M | 1 kt | 3 | 6 | 10 | 8 | **90 (living only)** | 140 | 70 (0.25 d) | slim silver bomb with cyan rings, neutron-reflector studs |
| **Cobalt (Salted) Bomb** | L | 400 kt | 10 | 30 | 45 | 50 | 50 | 90 | **320, spreads downwind (30 d)** | blue-black cylinder with cobalt-blue jacket plates |
| **Dirty Bomb** | S | — | 0 | 5 | 7 | 4 | 8 | 12 | 50 drifting cloud (5 d) | rusty drum with taped HE, radiation-waste labels |
| **MIRV** | missile L/XL | — | per RV | | | | | | | bus with 4/8 conical re-entry vehicles |

Neutron bomb specifics: lethal radiation dose to every living entity in r 90 with line attenuation through blocks
(water/lead/concrete/obsidian attenuate strongly — a deep enough bunker survives); crops, saplings, grass and leaves
in r 60 die (grass → dead grass, crops → withered); blocks otherwise untouched.

MIRV capacity: the bus holds **8 warhead units** (L) or **16** (XL). Tactical = 1, Fission = 2, Boosted = 3,
H-bomb = 4, decoy RV = 0.5. Mixed loads allowed. Re-entry vehicles spread over a footprint (point / ring / line /
grid, radius 10–150) chosen at the launch console.

Each tier escalates the spectacle: flash range and strength, fireball size, cloud height, number of puffs, shake
range, sound class (`nuke` vs `tsar`), and sky tint duration.

## 16. Special weapons

### 16.1 Storm Bomb ("Tesla Storm")
- Effect radius 48, duration 45 s. On detonation a vortex of cloud forms (rotating dark cloud wall at y+40, wall
  cloud lowering in the centre), sky darkens locally, rain/hail.
- Lightning barrage: 3 strikes/s on random points weighted toward entities and tall blocks. Each strike: small scorch
  crater r 1.5, ignites, **chains** to up to 4 further entities within 6 blocks (damage 12 → −25% per hop).
- Sounds: storm wind loop, near/far thunder cracks. Visual: branched procedural lightning bolts (not vanilla bolt
  model), electrified ground arcs.
- Look: squat bomb with exposed copper coil cage and blue glowing capacitor tubes.

### 16.2 Singularity Bomb
- Opens a black hole that **grows** over 20 s to an event horizon of r 10, then **collapses** with a burst (blast 16).
- Pull radius 48 for entities and items (inverse-square force; entities that cross the horizon die,
  `groundzero:spaghettified`).
- **Block ripping** within r 32: blocks are removed progressively (weakest/closest first, capped by
  `singularity.maxBlocksRipped`) and shown as client-side debris entities **torn from the ground, spiralling inward**
  on an accelerating orbit and vanishing at the horizon. Bedrock and unbreakables stay.
- Visuals: perfectly black sphere, glowing violet accretion disc (animated shader), relativistic jets, gravitational
  lensing post effect, starfield-stretch at the rim; colour desaturation near it. Sound: deep drone loop rising in
  pitch, rip/crunch sounds, final collapse thump + whoosh.
- Look (bomb): matte-black sphere held in a gyroscopic triple ring, violet void core visible through a slit.

### 16.3 Antimatter Bomb
- Blinding white flash; a white sphere expands to **r 48** over 1.5 s and **erases a perfect sphere** — no rubble,
  no drops, no fire, ignores blast resistance (except bedrock / unbreakables, configurable). Edges are clean.
- Entities inside are erased (`groundzero:annihilated`); gamma burst damages exposed entities to r 64 (lethal) and 96.
- Minimal radiation (none lingering). Air glows faint violet for 10 s; low reverse-whoosh sound.
- Look: white ceramic capsule with glowing violet magnetic confinement rings, no fins, warning chevrons.

### 16.4 Cryo Bomb
- Radius 40. Water → ice (packed ice near centre), lava → obsidian, leaves → frosted leaves, grass → frosted grass
  with snow layers, plants → frozen plant blocks, stone → frost-covered stone variant; fire extinguished.
- Mobs and players in r 40 are **frozen solid**: immobilised for 30 s inside a rendered ice shell; non-boss mobs
  under 40 HP that take ≥ 6 damage while frozen **shatter** (instant death, ice-shard particles).
- **Crystal growth** visuals: ice crystal clusters (real blocks, `cryo_crystal`) grow outward over 5 s within r 20;
  cold mist drifts out and lingers; breath vapour particles for players nearby.
- Look: stubby cyan bomb with frost-covered coolant tanks, vapour venting.

### 16.5 EMP Bomb
- Small blast 2; **EMP field r 96 for 90 s** (§21.4): all mod machines, launch pads, radar, interceptors and
  missiles inside are disabled; powered items on players in the field shut down; vanilla redstone signals inside the
  field read zero (config); missiles flying through lose guidance and fall (unless hardened).
- Visual: pale-blue expanding dome flash, crackling arcs crawling over metal blocks and machines, sparking HV cables,
  lights flicker. Sound: zap + falling power-down whine + crackle loop.
- Look: grey bomb with a large copper flux coil wound around the middle.

### 16.6 Orbital Kinetic Strike ("Rods from God")
- Requires a **Satellite Uplink** multiblock (5×5 dish, §23) and a **Kinetic Strike Satellite** launched to orbit
  from a Launch Complex/Silo on a Strategic body (the satellite is a payload item; at apex it "enters orbit" and
  registers with the uplink).
- The uplink's GUI holds a rod magazine (tungsten kinetic rods — each "uploaded" to the satellite) and targeting
  (map / coordinates / designator). Cooldown 60 s between rods.
- Strike: a glowing streak descends from the sky over 3 s (plasma trail, sonic boom crack), then: **narrow deep
  crater** (r 5, depth 48, conical), shockwave structural r 24, lethal 20, damage 48, no radiation, huge dust plume.
- Look: uplink is a white dish with a gold feed horn; rod is a long tungsten-blue dart with fins.

### 16.7 Chemical & toxin weapons
- **Gas grid:** toxins are simulated on a coarse server grid (2×2×2-block cells, sparse) that spreads into open
  cells, sinks into low areas, is blocked by walls/doors, drifts with the wind and decays. Concentration × agent
  strength = damage/s; gas masks and hazmat block it (§21.5).
- Agents: **Toxin** (generic, poison), **Anthrax α** (green, 2 dmg/s, 60 s), **Anthrax β** (lighter green, +50%
  damage, 90 s), **Anthrax γ** (purple, +120% damage, 120 s, contaminates soil: kills grass/crops, leaves "toxic
  soil" that re-emits weakly for a day, partially penetrates gas-mask filters).
- Weapons: toxin grenade, chemical bomb, SCUD Storm payloads, toxin 40 mm round, chemical warhead for missiles.

### 16.8 Extras beyond the brief (decision D5)
- **Approved:** **Railgun** (§20) and **Point-Defence Laser** (§18.3).
- **Kept as part of the conventional roster:** Thermite charge, Breaching charge, Smoke grenade (§14).
- **Cut:** Tectonic Charge, Gravity Repulsor Bomb.

---

## 17. Deployment and missiles

### 17.1 Placed detonation
Any bomb block or missile item can be placed in the world. Off a launch platform it cannot fly; triggering it
(redstone, Remote Detonator, timer, fuze) detonates it **where it stands**. A missile placed this way stands upright
on its fins.

### 17.2 Launch platforms
| Platform | Size | Max class | Features |
|---|---|---|---|
| **Launch Pad** | 3×3 multiblock (pad blocks + side control panel) | M | Erector arm raises the missile from horizontal to vertical when loaded; clamps release at T-0; flame-deflector smoke. 10 s countdown with siren & beeps. |
| **Launch Complex** | 5×5 base + 1×2×9 service gantry | XL | Umbilical arms swing away, deluge water spray, ignition smoke billowing out of the flame trench. 15 s countdown. |
| **Missile Silo** | 5×5 surface, 14 deep shaft | XL | Hatch doors slide open, missile rides up/launches from the shaft (exhaust erupts from side vents). Missile inside is protected until doors open. 20 s countdown. |
| **Cruise Missile Launcher** | 2×3 box launcher on a pedestal | M (cruise only) | Box elevates to 45° and fires; 2 missiles per load. |

Pad GUI: missile readout (what's loaded), target X/Y/Z, distance & bearing, in-range check, flight time, arm key
switch, **LAUNCH** (guarded red button), status lamps (POWER / ARMED / FUEL / EMP), countdown display.

### 17.3 Launch Control Station
- A desk console with CRT screens. Links to up to 16 pads/silos/launchers/uplinks within 1 024 blocks (Data Link
  Tool), more with Comms Relay antennas.
- **Map tab:** top-down tactical map of the area rendered from server-sent map-colour data (pan & zoom 1:1 … 1:16),
  showing pads, radar contacts, designated targets and the **blast/fire/radiation radius overlay** of the selected
  missile. Click to target; manual coordinate entry too.
- **Pads tab:** per-pad status (missile, armed, ready, cooldown, EMP-jammed, out of range).
- **Salvo tab:** choose pads; modes **simultaneous** or **time-on-target** (launches staggered so every warhead
  arrives at the same moment); target patterns (point / line / ring / grid with spacing).
- Two-key launch option (two players must turn keys within 2 s) — off by default.

### 17.4 Designators & handheld tools
- **Targeting Binoculars** — 4×/8× zoom with a rangefinder HUD (distance, coordinates, elevation); shift-click marks
  a target and sends it to the linked console/pad.
- **Laser Designator** — tripod-style handheld; hold to lase: visible red beam (to other players too) and a pulsing
  dot; sends the target continuously; laser-guided missiles home on it.
- **Remote Detonator** — frequency dial; detonates every armed remote-fuzed bomb/missile on that frequency within
  512 blocks (relay antennas extend).
- **Arming Key** — arms/disarms placed bombs and opens their fuze-settings GUI.
- **Data Link Tool** — links pads ↔ consoles, radars ↔ interceptors, uplinks.

### 17.5 Missile flight model (`MissileEntity`)
- Phases: **ignition** (2 s, clamps hold, smoke builds) → **boost** (vertical) → **pitch-over** (gravity turn) →
  **midcourse** (ballistic arc; apex = clamp(range × 0.3, 120, 1 600) blocks) → **re-entry** (glowing plasma sheath,
  whistle) → **terminal** (guidance corrections) → **impact / airburst**. Multi-stage bodies drop spent stages as
  falling debris with separation puffs.
- **Cruise** missiles climb to ~40 blocks AGL, terrain-follow using heightmap look-ahead, pop up and dive at the end.
- **Long range without loading a thousand chunks:** while the missile is above the world's build height (where it
  cannot collide with anything) it is simulated analytically by a `MissileTracker` saved-data entry rather than as
  a ticking entity; the entity is re-materialised (with its trail) when it enters loaded chunks near players or when
  it descends toward the target, whose chunks are ticket-loaded ahead of arrival. Low cruise flights ticket-load a
  rolling window of chunks along the path.
- Visuals: layered exhaust flame with flicker shader, heat haze, bright engine light, smoke trail of large puffs
  that linger 30–60 s and expand/drift, stage-separation flashes. Sounds: ignition roar, engine loop with doppler,
  flyby, re-entry whistle.
- Interceptions, EMP and laser guidance all act on the live entity or the tracker.

## 18. Radar and interception

### 18.1 Radar Station
- Multiblock 3×3 base, 4 tall mast, rotating dish (GeckoLib; spins at 1 rev / 4 s; stops when unpowered/EMPed).
- 200 FE/t. Detection radius 512 (Phased-Array module ×2, max 2 048). Cruise missiles are detected only within 40%
  of range; stealth-coated missiles within 50%.
- GUI: green phosphor CRT with rotating sweep, fading blips, track lines, ID labels (class shown once within 50%
  range), predicted impact point, ETA countdown, threat list. Alarm sound + **comparator output** (threat count) +
  redstone pulse on new contact. Shares tracks with linked interceptors and consoles.

### 18.2 Interceptor Battery
- 2×2 base with a rotating 4-cell launcher turret (GeckoLib). Holds 4 interceptor missiles (craftable ammo).
- Auto-engages radar-tracked threats inside 256 blocks (standalone IR seeker only 96). Fires 1 (or 2 for L/XL).
- Interceptor: fast, agile, proximity-fused; flies to a predicted intercept point; mid-air explosion either way.
- **Kill probability** per interceptor: `0.80 × class(S 1.0, M 0.9, L 0.75, XL 0.6, MIRV RV 0.45) × (countermeasures
  0.75) × (not radar-tracked 0.6) × (re-entry speed 0.85)`. A killed warhead breaks up without detonating
  (nuclear: fizzle with a small high-altitude flash, no ground effect).
- Superweapon strikes (silo nuke, SCUDs) are interceptable at half the normal probability; the particle beam and
  orbital rods are not.

### 18.3 Point-Defence Laser (tier 5)
Rapid-tracking laser turret effective against rockets, cruise missiles and SCUDs at short range (64), visible beam.

---

## 19. Command & Conquer: Generals superweapons

Common to all three:
- Large multiblocks built in survival (§23) with a **Superweapon Controller** block; the formed structure renders
  as one animated model.
- **Charge** requires continuous power; low power pauses the countdown (as in Generals). Charge persists across
  save/load.
- **Controller GUI:** charge bar + MM:SS countdown, power draw/supply, structure integrity, **Doctrine slot**
  (one Zero Hour general module) + **2 upgrade slots**, targeting (map / coordinates / linked designator /
  binoculars), and a guarded **FIRE** button once ready.
- **Announcements** (config, default on): players in the dimension get a subtitle + sound when a superweapon is
  built, when it is ready, and when it fires — the Generals "superweapon detected" feeling. Optional visible timer.
- Each has a **creative-only Structure Spawner** that places the complete formed structure instantly.

### 19.1 Particle Uplink Cannon (USA)
- **Structure:** 7×7 footprint, 13 tall. Steel platform with hazard edges; four lattice legs carry a 5-wide
  parabolic dish facing up; a central emitter spike with glowing blue focusing rings; capacitor drums and cable trays
  around the base; beacon lights. Charge level shown by the dish rim lights filling and the emitter brightening;
  arcs crawl on the rings at 100%.
- **Charge:** 4:00 at 1 500 FE/t.
- **Firing:** the emitter fires a beam straight up (2 s, dish recoil animation); 2 s later the orbital beam strikes
  the target from directly above and is **sustained 10 s**. While active the operator can **steer/drag** it — from the
  controller map (click-drag) or with the **Uplink Targeting Remote** (hold use; the beam chases where you look) —
  at up to 3 blocks/s.
- **Effect:** carves a **burning trench** along its path (core r 2.5, depth up to 6, glassed rim), ignites r 6,
  20 dmg/s within 3. **Dwell time matters** (as in Generals, a slow-moving beam does more damage): depth and damage
  scale with time spent over a spot.
- Visual: layered white-blue beam with a dazzling impact splash, ground plasma ripple, heat haze, sparks; the sky
  beam column visible from far away. Sounds: charge hum, fire crack, beam roar loop, impact sizzle.

### 19.2 Nuclear Missile Silo (China)
- **Structure:** 7×7 footprint, 8 tall above ground + 4 below. Heavy concrete bunker with red bands and yellow
  hazard trim, two massive sliding top doors, exhaust vents on the sides, gantry crane, warning beacons.
- **Charge:** 6:00 at 1 000 FE/t (the missile is built inside during the charge — no ammo needed).
- **Launch:** siren (5 s) → doors slide open → the missile rises on its elevator → ignition with a huge exhaust
  plume erupting from the vents and silo mouth → launch → ICBM flight → **Silo Nuke** strike: crater 48, fire 80,
  lethal 80, damage 140, radiation 160 with heavy fallout; full nuclear visuals.

### 19.3 SCUD Storm (GLA)
- **Structure:** 7×7 footprint, 5 tall base. Scrappy, improvised: rusted and patched plates, visible welds,
  sandbags, camo netting, oil drums, salvaged truck parts. A rack of **9 SCUD missiles** on hydraulic arms that raises
  from horizontal to vertical when firing.
- **Charge:** 5:00 at 800 FE/t.
- **Firing:** rack raises (3 s), the 9 SCUDs fire in quick succession (one every 0.6 s) at the target area with
  spread r 16. Each SCUD: HE blast 4 + **Anthrax α** cloud r 7 for 60 s.

### 19.4 Zero Hour doctrine modules
One **Doctrine** module per superweapon (they are mutually exclusive). Researched behaviour of the Zero Hour generals
and what each module does:

| Superweapon | Module | Effect | Faithful vs invented |
|---|---|---|---|
| Particle Cannon | **Superweapon General — "Uplink Overclock"** | charge time −40%, power −30% | **Faithful in spirit:** Gen. Alexander's Particle Cannon was much cheaper (≈ $2 500 vs standard) and her strategy revolved around fast-reloading cannons. Cost can't map to a module, so it becomes charge time + power. |
| Particle Cannon | **Laser General — "Focused Lens Array"** | beam narrower (r 1.5) but hotter: trench depth ×2, everything it crosses becomes glass/slag, duration 14 s | **Invented**, themed on Gen. Townes' laser arsenal; ZH did not change her cannon. |
| Particle Cannon | **Air Force General — "Orbital Relay"** | steering speed ×2, can be steered from anywhere with binoculars, range unlimited | **Invented**, themed on Gen. Granger's air superiority; ZH did not change his cannon. |
| Nuclear Silo | **Nuke General — "High-Yield Warhead"** | strike radii +50%, fallout ×2 | **Invented as a module:** in ZH Gen. Tao's silo is standard; his nuclear edge is on units (Nuclear Tanks, Nuke Cannon). The module carries that theme to the silo. |
| Nuclear Silo | **Nuke General — "Neutron Warhead"** | converts the strike into a neutron strike (kills living things in r 110, blocks mostly spared) | **Faithful flavour:** Tao's Nuke Cannons can be upgraded with **Neutron Shells** (kill infantry and vehicle crews, leave structures). |
| Nuclear Silo | **Nuke General — "Isotope Stability"** | "clean" warhead: radiation radius −60%, fallout half-life ÷4, no downwind drift | **Faithful name, reinterpreted:** in ZH, Isotope Stability stops Tao's nuclear tanks from harming friendly units when they explode. Here it makes the strike safe to use near your own base. |
| SCUD Storm | **Toxin General — "Anthrax Beta"** | clouds become Anthrax β (+50% damage, 90 s) | **Faithful:** Anthrax Beta is the standard GLA toxin upgrade; the Toxin General gets it by default. |
| SCUD Storm | **Toxin General — "Anthrax Gamma"** (crafted from Beta) | Anthrax γ (purple, +120% damage, 120 s, soil contamination) | **Faithful:** Anthrax Gamma is the Toxin General's exclusive, strongest toxin. Soil contamination is our addition. |
| SCUD Storm | **Demolition General — "Demo Warheads"** | high-explosive warheads: blast 9 each, no toxins | **Faithful:** sources describe the Demolition General's SCUD Storm as more explosive and without the toxin residue. |
| SCUD Storm | **Stealth General — "Camo Netting"** | the structure doesn't appear on radars and its SCUDs are radar-stealthy (detection ×0.5, interception ×0.5) | **Invented**, themed on Prince Kassad's stealth doctrine (camouflaged structures). |

Generic upgrade modules (2 slots, any superweapon): **Capacitor Array** (−20% charge time, stacks twice),
**Power Regulator** (−30% power draw), **Long-Range Targeting** (range ×2), **EMP Shielding** (structure immune to EMP).
Default superweapon range: 4 096 blocks (config).

---

## 20. Guns

Controls: use = fire (hold for stream/charge), sneak = aim (zoom + steadier), **R** = reload, **V** = cycle ammo type
(rebindable). All guns: GeckoLib models with first/third-person animations (idle sway, procedural + keyframed recoil,
full reload sequences, charge-up glow ramps, overheat steam), muzzle flash/effects, shell or cell ejection, own sounds
(fire, reload stages, charge, dry fire, overheat). Energy guns hold FE cells recharged in the Battery Charger and are
disabled by EMP.

| Gun | Ammo | Mechanics | Key stats | Look |
|---|---|---|---|---|
| **Hammerhead Rocket Launcher** | HE / incendiary / thermobaric rockets | single shot, slight drop | blast 3, direct 20, reload 2.2 s | olive tube with flip-up sight, rear blast cone, carry strap |
| **Lancer Guided Missile Launcher** | guided missiles | beam-riding (follows crosshair while aimed) or top-attack lock on an entity (hold 1.5 s) | blast 3.5, reload 3 s | chunky launch tube with a boxy command-launch unit and thermal sight |
| **Tactical Nuke Launcher** | mini-nuke rounds | lobbed arc, range ~120, slows the carrier while held | mini-nuke (§15), reload 5 s | squat tripod-braced launcher with a spherical round visible in the cradle |
| **Tesla Rifle** | Tesla cell (40 shots) | 0.4 s charge; chain lightning hits 5 targets within 6 of each other; arcs jump to metal blocks | 10 dmg −20%/hop, range 24 | copper coils, glass capacitor tubes glowing blue, insulated grip |
| **Singularity Cannon** | singularity charges | 2 s charge; slow projectile; spawns a micro black hole for 5 s | horizon 1.5, pull 10, rips ≤ 150 blocks in r 4 | heavy black barrel with violet ring accelerators, lensing shimmer at the muzzle |
| **Antimatter Rifle** | antimatter cell (5 shots) | 1 s charge; fast projectile | erases a sphere r 2.5 (no drops); 30 dmg | sleek white rifle with violet confinement rings and a magnetic bottle magazine |
| **Cryo Gun** | cryo canister (10 s of stream) | stream range 10; frost build-up freezes a target after 2 s; freezes water/lava; makes ice bridges; puts out fire | | bulky frosted tank backpack-style magazine, vapour from the nozzle |
| **Particle Beam Rifle** | particle cell | hold 1.5 s to charge, then a 2 s beam (range 64) with overheat meter | 8 dmg / 0.2 s, ignites, trench 1 wide × 2 deep with glass rim | handheld mini uplink: small dish-lens emitter, cooling fins, blue glow |
| **Flamethrower** | fuel canister / napalm canister | stream range 10 (napalm 14, sticky fire), pilot light | ignites blocks and entities | twin-tank pack-style body, wand with pilot flame |
| **Grenade Launcher** | 40 mm: HE, incendiary, cryo, toxin, EMP, smoke | 6-round revolving cylinder, per-shell reload | HE blast 2.5 | revolver-cylinder launcher with folding stock |
| **EMP Gun** | EMP cell | 30° cone, 12 range | disables machines 15 s; disarms electronic fuzes; downs missiles in the cone; disables powered items of players hit; stuns iron golems | dish-muzzled pistol-grip device with crackling coil |
| **Railgun** | tungsten slugs | 1 s charge; hitscan; pierces 8 entities; breaks soft blocks (glass, leaves, wood) along 32 blocks | 40 dmg | long twin-rail barrel, capacitor banks, sonic crack |

---

## 21. Aftermath systems

### 21.1 Radiation field
- Stored per chunk as a **data attachment** of 4×4×4-block cells (only for irradiated chunks, half-float levels +
  decay class). Decay is applied **lazily by timestamp** (exponential, per decay class half-life) when a chunk is read,
  so idle irradiated chunks cost nothing.
- Blasts seed the field: level falls off with distance to `r_rad`; groundbursts seed more than airbursts.
- **Fallout plumes:** after a nuclear/dirty/cobalt blast a plume drifts **downwind** for several minutes (cobalt:
  much longer), widening, depositing radiation and **fallout ash layers** under its path, with ash particles drifting
  down visibly to players under it.
- **Wind:** one wind vector per dimension that slowly wanders (shared with gas clouds, mushroom-cloud drift, smoke).
- **Radioactive blocks & items** (`groundzero:radioactive` tag with emission values): uranium ore (weak), uranium /
  plutonium blocks, trinitite, fallout ash, irradiated soil, nuclear waste. Items in inventories emit too (carrying
  plutonium is a bad idea without lead-lined containers — a **Lead Container** item stores radioactive items safely).
- Dose rate at an entity = ambient cell level + nearby radioactive blocks (sampled once per second, r 6) + inventory,
  × shielding (6 axis rays through blocks, attenuated by density: lead ≫ concrete/obsidian > stone > dirt > wood,
  water is good) × armour protection.

### 21.2 Dose and radiation sickness
| Accumulated dose (rad) | Stage | Effects |
|---|---|---|
| 0–100 | — | none (Geiger only) |
| 100–200 | Sickness I | occasional nausea, hunger drain ×1.5 |
| 200–400 | Sickness II | weakness, mining fatigue, hunger ×2 |
| 400–600 | Sickness III | 1 damage / 5 s, slowness, no natural regeneration |
| 600–1 000 | Sickness IV | wither bursts, blurred vision overlay |
| 1 000+ | Lethal | rapid death (`groundzero:radiation`) |

Dose slowly recovers (−1 rad/min) when the dose rate is near zero. Animals and most mobs take radiation damage;
undead are immune; grass and crops die in strong fields.

### 21.3 Geiger counter & dosimeter
- **Geiger Counter:** Poisson-random clicks whose rate tracks the dose rate (with click variants), analog needle
  HUD with digital readout (RAD/h), alarm above a set threshold; works in either hand or (mini-HUD) in the hotbar.
  Battery-powered (EMP disables it).
- **Dosimeter Badge:** worn in a Curios-free way (any inventory slot); shows accumulated dose as a colour patch +
  number in the tooltip / HUD.

### 21.4 EMP
- EMP fields are tracked per level (centre, radius, expiry) with a fast spatial lookup.
- Every mod machine implements `EmpSensitive`: inside a field it stops, loses 50% buffered energy, sparks, shows an
  "EMP" lamp, then plays a reboot sequence (lights flicker back) when the field ends. Launch pads can't launch, radar
  goes dark, interceptors go offline, superweapon charge pauses and loses 10%.
- Missiles in flight inside a field lose guidance (ballistic) and electronic fuzes fail 50% of the time (dud);
  hardened electronics are immune. Placed bombs with timed/proximity/remote fuzes are disarmed (impact fuzes are not).
- Powered items on players in the field shut down until 10 s after they leave.
- Vanilla redstone (config, default on): signals read zero inside the field (redstone lamps go dark, doors stay as
  they are, circuits stall).
- Power cables inside the field arc visibly; HV cables can burn out (config, default off).

### 21.5 Protective gear
Protection values are for the full set; pieces contribute helmet 20%, chest 35%, legs 30%, boots 15%.

| Gear | Radiation | Toxins (α/β / γ) | Other |
|---|---|---|---|
| **Gas Mask** (helmet) | 10% | 100% / 60% while its filter lasts (filter = 10 min of exposure) | breathing sound & visor overlay |
| **Flash Goggles** (helmet) | — | — | −80% nuclear/antimatter flash |
| **Hazmat Suit** (yellow) | 75% | 100% / 80% | slows swimming |
| **Lead-lined Hazmat Suit** (orange, lead plates) | 92% | 100% / 90% | heavier (−5% speed) |
| **Advanced CBRN Suit** (olive armoured, sealed visor, powered rebreather) | 98% | 100% / 100% | iron-level armour, auto-darkening visor (−80% flash), EMP-hardened; needs battery |

### 21.6 Medical & decontamination
- **Rad-Away IV bag:** −150 rad over 30 s.
- **Potassium iodide tablets:** −50% dose uptake for 10 min.
- **Anti-Rad Injector:** −400 rad instantly, causes weakness 60 s.
- **Contamination:** walking through fallout makes the player and picked-up items *contaminated* (they keep emitting
  a small dose). The **Decontamination Shower** cleans the player and their inventory; contamination also fades.

### 21.7 Scorched terrain and the wasteland
- New blocks: **Trinitite** (glassy green, weakly radioactive), **Scorched Earth**, **Scorched Stone**, **Ash Layer**
  (1–8 layers like snow), **Fallout Ash** (radioactive layer), **Charred Log / Wood / Planks**, **Dead Grass Block**,
  **Dead Leaves**, **Withered Crops**, **Irradiated Soil** (faint glow), **Slag**, **Glassed Sand**, **Toxic Soil**
  (Anthrax γ), **Frosted Stone / Frosted Grass / Frozen Plants / Cryo Crystal** (cryo).
- **Nuclear terrain table** (by band): core → air; crater edge sand → trinitite; scorch: grass → scorched earth,
  dirt → coarse/scorched, stone → scorched stone, logs → charred logs, planks → charred planks, leaves → air,
  flowers/grass → air or dead bush, snow/ice → air/water, water in core → steam (air); structural: glass → air,
  leaves stripped, doors/trapdoors → air, wool/carpet → fire, crops → withered.
- **Wasteland biome** (`groundzero:nuclear_wasteland`): painted over the scorch radius of nuclear blasts (config);
  grey-brown grass and foliage tint, dead-olive water, brown-orange fog, falling-ash ambient particles, no passive
  mob spawns. Burnt trees remain as charred trunks. Craters can flood into "crater lakes".

---

## 22. World structures and loot

| Structure | Where | Contents |
|---|---|---|
| **Abandoned Missile Silo** (3 variants: intact, flooded, collapsed) | underground, plains/taiga/desert, rare | rusted surface hatch and fence ruins; shaft with a damaged missile (salvageable); control room with dead consoles; crew quarters; lower levels |
| **Cold-War Command Bunker** | hillsides | blast door entrance, corridors, map room with lore notes, armory (grenades, C4, mines), radio room |
| **Radar Outpost** | surface, hills/mountains | toppled dish, guard tower, generator shed, fences |
| **Test Site** | desert/badlands | crater with trinitite and irradiated soil (weak radiation), observation bunker, instrument towers |
| **Crashed Missile** | surface, any | impact furrow, broken missile body, leaking dirty payload (radiation hazard), salvage |
| **Waste Storage Cave** | underground | drum stacks, decon showers, lead-lined rooms (radiation), hazmat gear |
| **Exotic Research Station** | End, near void geodes | floating ruin with exotic-tech blueprints |

Loot: **blueprints** (halve assembly time, show lore; never strictly required), rare components (military
circuits, guidance units, propellant), **damaged warheads** (salvage in the Chemical Plant → U-235/Pu nuggets),
**lore notes** (an original Cold-War-flavoured story told across ~20 documents: the "Ground Zero" programme, test
logs, last transmissions), Geiger counters, hazmat pieces, ammo. Structures are authored as layouts in
`tools/structure` → NBT templates → jigsaw structures. Every variant must look great: weathering, rust, cracked
concrete, overgrowth, lighting, set dressing.

---

## 23. Multiblock framework and layouts

### 23.1 Rules (all multiblocks)
- A **controller** validates the pattern (4 rotations) on placement, neighbour change, and right-click.
- **Ghost preview:** right-clicking an unformed controller (or holding a Holo-Projector) shows the structure as
  translucent pulsing blocks where pieces are missing, red outlines where a wrong block is present, with
  layer-by-layer stepping. The Field Manual shows the same structure.
- On forming, component blocks switch to a `formed` state (empty model, interactions and capabilities delegated to
  the controller) and the controller renders **one cohesive animated model** (GeckoLib). Breaking any component
  un-forms the structure; nothing is lost.
- Each multiblock has a **creative-only Structure Spawner** item that places it complete and formed.

### 23.2 Structural blocks
Reinforced Concrete, Hazard-striped Concrete, Steel Frame (see-through lattice), Steel Plating, Machine Casing,
Lead-lined Casing, Reactor Glass (lead glass), Dish Panel, Capacitor Cell, Superconducting Magnet Segment, Coolant
Port, Fuel Port, Energy Port, Item Port, Blast Door, Silo Hatch, plus a controller per structure.

### 23.3 Layouts
Layer `y0` is the bottom. `X` = controller, `R` = reinforced concrete, `H` = hazard concrete, `F` = steel frame,
`P` = steel plating, `M` = machine casing, `L` = lead-lined casing, `G` = reactor glass, `D` = dish panel,
`C` = capacitor cell, `S` = magnet segment, `E` = energy port, `I` = item port, `W` = coolant port, `U` = fuel port,
`.` = air (required empty), `*` = any.

**Breeder Reactor** (3×3×4):
```
y0: LLL / LWL / LXL      (X faces out of the front middle)
y1: LGL / G.G / LGL      (core space: rendered fuel rods + Cherenkov glow)
y2: LGL / G.G / LGL
y3: LUL / LEL / LIL
```
**Chemical Plant** (2×2×2): `y0: XM / MI`, `y1: GG / GG` (tanks rendered inside).

**Particle Accelerator** (9×9×3 ring): a hollow square ring 1 wide on y1 made of 28 `S` magnet segments with `I`
injector and target chamber at opposite midpoints, `X` beside the injector; y0 ring of `P` under it; y2 ring of `F`
above it. Interior 7×7 is free.

**Radar Station** (3×3×5): `y0: PEP / PXP / PPP`, `y1–y3: .F. / FFF / .F.` (mast), `y4: DDD / DFD / DDD` (dish mount).

**Launch Pad** (3×3×1): `HHH / HXH / HHH` with the control panel block `X`-adjacent on one side (any side).

**Launch Complex** (5×5 base + gantry): `y0` 5×5 of `R` with a 3×3 `H` flame-trench centre and `X` at one edge
midpoint; gantry 1×2 column of `F` 9 tall at the opposite edge with an `E` at its foot.

**Missile Silo** (5×5 × 14 deep): 13 layers of `R` rings (5×5 hollow, 3×3 shaft) dug into the ground; bottom layer
full `R` with the elevator plate; top layer `H` ring with the two-part `Silo Hatch` covering the 3×3 opening; `X`
in the shaft wall at the top layer.

**Satellite Uplink** (5×5×4): `y0` 5×5 `R` with `X` front-middle and `E` back-middle; `y1` 3×3 `F` centre column;
`y2` 5×5 `D` dish ring with `F` centre; `y3` single `F` feed horn mast.

**Particle Uplink Cannon** (7×7×13):
```
y0      7×7 H (outer ring) / R (inner 5×5); X front-middle, E ×2 back corners
y1–y2   C capacitor drums at the 4 corners (2 tall), F legs inside each corner, centre column F
y3–y6   4 F legs (lattice) + centre column F
y7      5×5 D dish base around a centre F
y8      7×7 D dish rim ring (outer ring only) + inner ring air, centre F
y9–y12  centre emitter column: y9–y11 F, y12 the Emitter block (rendered glowing)
```
**Nuclear Missile Silo — China** (7×7, 4 below + 8 above):
```
y-4..y-1  shaft: 7×7 R ring with 5×5 hollow; bottom (y-4) full R with elevator plate
y0..y5    bunker walls: 7×7 R ring, front wall has X + blast door, side walls have vent blocks (rendered vents)
y6        roof: R ring with two Silo Door blocks spanning the 3×5 opening (rendered sliding doors)
y7        R crown at the corners with beacon blocks
```
**SCUD Storm — GLA** (7×7×5):
```
y0      7×7 P (rusty plating variant) with X front-middle, E back-middle
y1      perimeter of sandbag blocks (decorative but required), inner 5×5 F
y2      inner 5×5 F with hydraulic-ram blocks at the 2 back corners
y3–y4   rack frame: 5×1 F beam across the back (rack pivot), rest air (rack rendered)
```
Exact block-by-block templates are defined in code (`multiblock/defs`) and mirrored into the Field Manual
automatically, so the book can never disagree with the game.

---

## 24. UI, guide book, recipe viewer, tooltips

### 24.1 Military/industrial UI toolkit
One shared widget set drawn by our texture scripts: riveted steel panels with stencilled headers; recessed CRT
screens (green/amber phosphor, scanlines, glow, slight curvature) for radar, maps and readouts; toggle switches with
safety covers; guarded red buttons; analog gauges; segmented progress/energy/charge bars; key switches; LED status
lamps; a custom stencil-style bitmap font for labels (real text uses the vanilla font for readability). Every screen
has tooltips on every gauge and slot.

Screens: every processing machine, generators, capacitor banks, assembly bench, launch pad, launch complex, silo,
cruise launcher, launch control station (map/pads/salvo), radar, interceptor battery, satellite uplink, three
superweapon controllers, timed bomb, arming key / fuze settings, remote detonator, designator HUD, binoculars HUD,
Geiger HUD, dosimeter HUD.

### 24.2 Field Manual (native guide book)
- Item: **"Field Manual GZ-1"** (olive hardcover with a stencilled trefoil), given on first join (config).
- Two-page spread GUI in a field-manual style (typewritten body text, stamped "RESTRICTED" headers, paperclipped
  photos). Chapters: Getting Started · Resources · Power · Processing · Components · Assembly · Ordnance ·
  Nuclear · Special Weapons · Launching · Air Defence · Superweapons · Guns · Aftermath & Safety · Structures & Lore.
- Page types: text (markdown-lite), item spotlight, live crafting/machine recipes (read from the actual recipe
  registry), stats tables, image plates (pre-rendered model shots from our preview tool), **rotating 3D multiblock
  viewer with a layer slider** (reads the same definitions as the game), and links.
- Search, bookmarks, "new entry" markers. Content is data-driven JSON under `assets/groundzero/manual/`.
- Optional "classified" mode (off by default): entries appear redacted until you obtain a related item.

### 24.3 JEI (optional)
Custom categories for every machine, the assembly bench (component combinations and resulting stats), multiblock
info pages, salvage, decontamination, and ore/resource info pages.

### 24.4 Tooltips
Every weapon: class, yield, radii (crater / fire / lethal / radiation), deployment options, fuze & settings,
range/guidance for missiles; Shift for detail, Ctrl for lore. Components show what they enable. Radioactive items
show their emission.

### 24.5 Creative tabs
Resources · Machines & Power · Ordnance · Nuclear & Special · Missiles & Defence · Guns & Gear · Superweapons.

---

## 25. Tools & utility items (summary)
Arming Key, Remote Detonator, Laser Designator, Targeting Binoculars, Uplink Targeting Remote, Data Link Tool,
Holo-Projector, Wrench (rotate/dismantle machines), Geiger Counter, Dosimeter Badge, Lead Container, Battery,
Tesla/Particle/Antimatter/EMP cells, Field Manual.

---

## 26. Recipes

Stations: **CT** crafting table · **FU** furnace/blast furnace · **OC** Ore Crusher · **AF** Arc Furnace ·
**IP** Industrial Press · **CP** Chemical Plant · **GC** Gas Centrifuge · **ES** Electrolytic Separator ·
**FS** Fusion Fuel Synthesizer · **CC** Cryogenic Condenser · **CF** Circuit Fabricator · **EF** Exotic Matter
Fabricator · **PA** Particle Accelerator · **BR** Breeder Reactor · **AB** Assembly Bench (Fabricate tab = 3×3 grid
+ 2 precision slots; Bomb/Missile/Ammo tabs = component slots).
Ingredient lists are final in content; exact crafting-grid shapes are fixed in datagen and shown in JEI and the
manual. Processing times/energy are tuned in implementation and recorded here then.

### 26.1 Bootstrap (vanilla materials only — gets you into the power + machine loop)
| Output | Station | Ingredients |
|---|---|---|
| Gunpowder ×4 | CT | 2 saltpeter dust, 1 sulfur dust, 1 charcoal |
| Combustion Generator | CT | 5 iron ingot, furnace, 2 copper ingot, redstone |
| Copper Cable ×6 | CT | 3 copper ingot, 3 dried kelp (insulation) |
| Arc Furnace | CT | 4 iron block, blast furnace, cauldron, 2 copper ingot, redstone block |
| Industrial Press | CT | 4 iron ingot, 2 piston, anvil, redstone |
| Ore Crusher | CT | 4 iron ingot, stonecutter, 2 piston, grindstone |
| Handmade Circuit Board | CT | 2 copper ingot, 2 redstone, paper, gold nugget (→ counts as a basic circuit) |
| Dynamite ×2 | CT | paper, 2 gunpowder, string |
| Frag Grenade ×2 (early) | CT | 4 iron ingot, 2 gunpowder, iron nugget |

### 26.2 Parts
| Output | Station | Ingredients |
|---|---|---|
| Steel ingot | AF | iron ingot (or iron dust) + coal/charcoal |
| Steel / tungsten / lead / copper plate | IP | 1 ingot |
| Dense steel plate | IP | 4 steel plate |
| Steel rod ×2 / tungsten rod | IP | 1 steel ingot / 1 tungsten ingot |
| Copper wire ×4 / gold wire ×4 / tungsten filament ×4 | IP | 1 ingot |
| Empty canister ×2 | IP | 2 steel plate |
| Empty gas canister | CT | 2 steel plate, glass |
| Neodymium magnet | AF | 2 rare-earth dust, iron dust |
| Electric motor | CT | 4 copper wire, 2 neodymium magnet, steel rod, 2 steel plate |
| Heating element | CT | 3 tungsten filament, 2 steel plate, copper wire |
| Pressure vessel | CT | 4 dense steel plate, steel rod |
| Machine frame | CT | 4 steel plate, 4 steel rod, basic circuit |
| Servo | CT | electric motor, steel rod, basic circuit |
| Antenna | CT | 3 steel rod, 2 copper wire, basic circuit |
| CRT display | CT | glass pane, glowstone dust, basic circuit, steel plate, redstone |
| Capacitor | CT | 2 copper plate, plastic sheet, gold wire |
| HE lens ×4 | CT | 2 RDX, plastic sheet |
| Burster charge | CT | RDX, steel plate, copper wire |
| Basic circuit ×2 | CF | 2 copper wire, 2 redstone, plastic sheet |
| Advanced circuit | CF | 2 basic circuit, 2 gold wire, rare-earth dust, quartz |
| Military circuit | CF | 2 advanced circuit, tungsten filament, diamond, rare-earth dust ×2 |
| Quantum circuit | CF | military circuit, stabilised exotic matter, superconducting coil |
| Sensor array | CF | advanced circuit, glass, rare-earth dust, 2 gold wire |
| Superconducting coil | CF | 2 tungsten filament, rare-earth dust, 2 gold wire, LN₂ canister |
| Battery | CT | copper plate, redstone, 2 lithium dust, plastic sheet |

### 26.3 Chemistry and nuclear processing
See §6 for every chemical/nuclear intermediate with its station and inputs. Additional:
| Output | Station | Ingredients |
|---|---|---|
| Uranium fuel rod | CT | 3 natural uranium ingot, 2 steel rod |
| DU target rod / Li-6 target rod | CT | 3 DU ingot / 3 Li-6 dust, 2 steel rod |
| Irradiated target rod | BR | target rod (in the breeding slots, over time) |
| Salvaged U-235 / Pu nuggets | CP | damaged warhead (loot) + sulfuric acid |
| Cobalt ingot | AF | 8 cobalt dust |
| Cobalt-59 jacket | IP | 4 cobalt ingot |
| Stabilised exotic matter | EF | 4 exotic shard, neodymium magnet, power |
| Containment cell | EF | 2 tungsten plate, 2 superconducting coil, glass |
| Antimatter cell | PA | containment cell, huge power over time |
| Singularity seed | EF | 4 stabilised exotic matter, 4 tungsten block, quantum circuit |
| Cryo crystal seed | CC | packed ice, LN₂ canister, quartz |

### 26.4 Machines, power, logistics
| Output | Station | Ingredients |
|---|---|---|
| Chemical Plant | CT | machine frame, 4 glass, pressure vessel, electric motor, basic circuit, cauldron |
| Gas Centrifuge | CT | machine frame, 2 electric motor, tungsten rod, 2 dense steel plate, advanced circuit |
| Electrolytic Separator | CT | machine frame, 2 copper plate, 2 gold wire, glass, basic circuit, cauldron |
| Fusion Fuel Synthesizer | CT | machine frame, 2 pressure vessel, heating element, advanced circuit, tungsten plate |
| Cryogenic Condenser | CT | machine frame, electric motor, 4 copper plate, pressure vessel, advanced circuit |
| Circuit Fabricator | CT | machine frame, 2 servo, glass, handmade circuit board |
| Exotic Matter Fabricator | CT | machine frame, 4 superconducting coil, military circuit, 2 tungsten plate, ender eye |
| Diesel Generator | CT | machine frame, electric motor, pressure vessel, combustion generator |
| RTG | CT | 4 lead plate, plutonium ingot, 2 copper wire, 2 tungsten plate |
| Capacitor Bank T1 / T2 / T3 | CT | 4 capacitor + machine frame / T1 + 4 gold wire + advanced circuit / T2 + 4 superconducting coil + military circuit |
| Insulated / HV cable ×6 | CT | 6 copper cable + plastic sheet / 6 insulated cable + 3 tungsten filament + plastic |
| Battery Charger | CT | machine frame, 2 capacitor, basic circuit |
| Decontamination Shower | CT | 3 steel plate, 2 iron bars, water bucket, basic circuit, electric motor |
| Ordnance Assembly Bench | CT | 3 steel plate, crafting table, anvil, servo, basic circuit, chest |
| Breeder reactor parts | CT | casing: lead plate + dense steel plate + concrete; glass: lead plate + glass; ports: casing + hopper/bucket/cable; controller: casing + military circuit + CRT display |
| Accelerator parts | CT | magnet segment: 2 superconducting coil + 4 dense steel plate + copper wire; injector/target: segment + military circuit; controller: casing + quantum circuit + CRT display |
| Structural blocks | CT | reinforced concrete ×8: 8 concrete (any colour) + steel rod; hazard concrete: reinforced concrete + yellow & black dye; steel frame ×4: 4 steel rod + steel plate; steel plating ×4: 4 steel plate; machine casing: machine frame + 4 steel plate; dish panel ×2: 2 steel plate + copper plate |

### 26.5 Ordnance (crafting table unless noted)
| Output | Ingredients |
|---|---|
| Frag Grenade ×4 (standard) | 4 steel plate, 2 RDX, iron nugget |
| Incendiary / Toxin / Cryo grenade ×2 | steel plate, gunpowder, + napalm gel / toxin precursor / LN₂ canister |
| Sticky Grenade | frag grenade, 2 slimeball |
| EMP Grenade ×2 | steel plate, capacitor, neodymium magnet, basic circuit |
| Smoke Grenade ×2 | steel plate, charcoal, saltpeter dust, sulfur dust |
| C4 Charge ×2 | 2 plastic explosive, basic circuit, copper wire |
| Breaching Charge | C4 charge, steel plate, 2 det cord (copper wire + RDX) |
| Thermite Charge | 2 thermite powder (iron dust + rare-earth dust), ceramic pot (brick + clay ball), igniter fuse (copper wire + gunpowder) |
| Timed Bomb | 2 C4, clock, basic circuit, steel plate |
| Landmine ×2 | 2 steel plate, RDX, stone pressure plate |
| Bouncing Mine | landmine, gunpowder, 4 iron nugget |
| Claymore | plastic explosive, 6 iron nugget, steel plate, tripwire hook, basic circuit |
| Bombs (all) | **AB Bomb tab**: payload core + casing of its class + fuze |
| Missiles (all) | **AB Missile tab**: warhead (payload + fuze, or a finished bomb) + body + guidance + propulsion (+ module) |

### 26.6 Components (AB Fabricate tab)
| Output | Ingredients |
|---|---|
| Light / Medium / Heavy / Super-heavy bomb casing | 4 / 6 / 8 dense steel plate + 2 / 3 / 4 / 6 steel plate for fins; super-heavy + parachute pack (4 wool + 4 string + steel plate) |
| Tactical / Medium / Heavy / Strategic missile body | 4 / 6 / 8 / 12 steel plate + 1 / 2 / 3 / 4 steel rod + tungsten plate nose (heavy & strategic) |
| Cruise missile body | 4 steel plate, 2 dish panel (wings), servo, steel rod |
| Impact / Timed / Proximity / Remote / Barometric fuze | steel plate + copper wire + (button / clock / sensor array / antenna / pressure vessel) + basic circuit (advanced for proximity & barometric) |
| Inertial / GPS / Laser guidance | advanced circuit + compass + servo / military circuit + antenna + compass / military circuit + sensor array + servo |
| Hardened guidance | guidance + 2 lead plate + advanced circuit |
| Solid Rocket Motor I / II | steel plate ×4 + 2 / 4 solid propellant + nozzle (tungsten plate) |
| Liquid-fuel Stage | pressure vessel ×2, 4 liquid rocket fuel, electric motor (turbopump), tungsten plate ×2 |
| ICBM Booster Stack | 2 liquid-fuel stage, military circuit, steel rod ×4 |
| Three-stage Strategic Booster | ICBM booster stack + liquid-fuel stage + military circuit + heat shield |
| Turbojet | electric motor, 2 tungsten plate, heating element, 2 synthetic fuel |
| Countermeasure pod / Stealth coating / Hardened electronics / Re-entry heat shield | 4 iron nugget + 2 flare (gunpowder + glowstone dust) + basic circuit / 4 plastic sheet + 4 coal / 4 lead plate + advanced circuit / 4 tungsten carbide + 2 obsidian |
| Payload cores | see §9.2 ingredient column (HE charge: 2 RDX + steel plate + burster; fission cores etc.) |
| MIRV bus | military circuit, 2 servo, 4 / 8 steel rod (mounts), heavy / strategic body section |
| Interceptor missile ×2 | tactical missile body, proximity fuze, inertial guidance, SRM I, sensor array |

### 26.7 Launch, defence, superweapons
| Output | Ingredients |
|---|---|
| Launch Pad blocks ×9 / controller | 9: 4 hazard concrete + 4 steel plate + machine frame / controller: machine casing + CRT display + advanced circuit + lever |
| Launch Complex controller / gantry | machine casing + military circuit + 2 CRT display / 4 steel frame + servo + copper cable |
| Missile Silo controller / Silo Hatch | machine casing + military circuit + CRT / 4 dense steel plate + 2 servo + hazard concrete |
| Cruise Missile Launcher | machine frame, 2 servo, 4 steel plate, advanced circuit |
| Launch Control Station | machine casing, 3 CRT display, military circuit, antenna, 2 lever |
| Data Link Tool / Comms Relay | antenna + basic circuit + steel rod / 3 antenna + machine frame + advanced circuit |
| Radar controller / dish panels | machine casing, sensor array ×2, military circuit, CRT display / dish panel recipe |
| Phased-Array module | 4 sensor array, military circuit |
| Interceptor Battery | machine frame, 2 servo, 4 steel plate, military circuit, sensor array |
| Satellite Uplink controller | machine casing, 2 antenna, military circuit, CRT display |
| Kinetic Strike Satellite (payload) | 4 dish panel (solar wings), 2 military circuit, antenna, 4 tungsten rod, battery ×2 |
| Tungsten Kinetic Rod | 4 tungsten block, tungsten carbide nose, 4 tungsten plate fins |
| Particle Cannon controller | machine casing, quantum circuit, 2 CRT display, 4 superconducting coil |
| Particle Emitter | 4 superconducting coil, stabilised exotic matter (focusing crystal), 2 tungsten plate, military circuit |
| Capacitor Cell | capacitor bank T2, 2 steel plate |
| Nuclear Silo controller / Silo Doors | machine casing, 2 military circuit, CRT display, 2 enriched uranium ingot (initial core stock) / 6 dense steel plate + 2 servo + hazard concrete |
| SCUD Storm controller / hydraulic ram | machine casing (rusty variant: + 4 iron nugget), military circuit, CRT, 4 solid propellant / 2 pressure vessel + 2 steel rod + electric motor |
| Doctrine modules | blank doctrine module (military circuit + 2 steel plate + gold wire) + theme items: Overclock (4 superconducting coil), Focused Lens (2 diamond + stabilised exotic matter), Orbital Relay (2 antenna + sensor array); High-Yield (fission core), Neutron (2 tritium canister + tungsten plate), Isotope Stability (4 lead plate + advanced circuit); Anthrax Beta (anthrax β culture), Anthrax Gamma (Anthrax Beta module + anthrax γ culture), Demo Warheads (8 RDX), Camo Netting (4 green wool + 4 string + stealth coating) |
| Generic upgrades | Capacitor Array (2 capacitor bank T1 + advanced circuit), Power Regulator (2 superconducting coil + advanced circuit), Long-Range Targeting (2 antenna + sensor array + military circuit), EMP Shielding (8 lead plate + hardened electronics) |

### 26.8 Guns, ammo, gear, tools
| Output | Ingredients |
|---|---|
| Hammerhead RL | 4 steel plate, steel rod, iron sight (2 iron nugget + glass pane), trigger group (2 steel rod + tripwire hook) |
| Rockets ×4 (HE/inc/TB) | steel plate, solid propellant, + HE charge / napalm gel / thermobaric slurry |
| Lancer GML | Hammerhead RL, sensor array, military circuit, 2 tungsten plate |
| Guided missiles ×2 | rocket + inertial guidance |
| Tactical Nuke Launcher | 6 dense steel plate, 2 steel rod, advanced circuit, pressure vessel |
| Mini-nuke round | 1 Pu nugget, 4 HE lens, 2 steel plate (AB Ammunition tab) |
| Tesla Rifle / Tesla cell | 4 copper plate, 2 superconducting coil, 2 capacitor, military circuit, plastic / battery + 2 capacitor |
| Singularity Cannon / charge | 4 tungsten plate, 4 superconducting coil, quantum circuit, containment cell / stabilised exotic matter + containment cell |
| Antimatter Rifle / cell | 2 tungsten plate, 2 superconducting coil, quantum circuit, plastic / antimatter cell (one per 5 shots) |
| Cryo Gun / canister | pressure vessel, 2 copper plate, electric motor, advanced circuit / LN₂ canister |
| Particle Beam Rifle / cell | particle emitter (small: 2 superconducting coil + tungsten plate + diamond), military circuit, 2 steel plate / battery + superconducting coil |
| Flamethrower / fuel | 2 pressure vessel, steel rod, heating element, flint & steel / synthetic fuel or napalm gel canister |
| Grenade Launcher / 40 mm ×4 | 4 steel plate, 2 steel rod, servo / steel plate + small charge (HE / napalm / LN₂ / toxin / capacitor / smoke mix) |
| EMP Gun / cell | 2 capacitor, neodymium magnet ×2, advanced circuit, copper plate ×2 / battery + capacitor |
| Railgun / slugs ×8 | 4 tungsten rod, 2 capacitor bank T1, military circuit / 1 tungsten ingot |
| Gas Mask / filter | leather, 2 glass pane, charcoal, iron nugget / 2 charcoal + paper + iron nugget |
| Flash Goggles | leather, 2 tinted glass, gold nugget |
| Hazmat Suit (4 pieces) | yellow wool + leather + plastic sheet in armour shapes |
| Lead-lined Hazmat | hazmat piece + lead plates (2/4/3/2) |
| Advanced CBRN Suit | lead-lined piece + steel plates + advanced circuit (+ battery for chest, visor glass for helmet) |
| Geiger Counter | steel plate, glass pane, copper wire, basic circuit, battery |
| Dosimeter Badge | paper, glass pane, iron nugget |
| Rad-Away / KI tablets / Anti-Rad injector | glass bottle + water + 2 charcoal + sugar / paper + dried kelp + sulfur dust / glass bottle + Rad-Away + advanced circuit |
| Lead Container | 6 lead plate, chest |
| Arming Key / Remote Detonator / Laser Designator / Binoculars / Uplink Remote | iron + key shape + gold nugget / antenna + basic circuit + battery + button / sensor array + redstone torch + military circuit + battery + glass / 2 spyglass + basic circuit + steel plate / antenna + military circuit + sensor array + battery |
| Holo-Projector | glass, diamond, advanced circuit, battery |
| Wrench | 3 steel ingot |
| Field Manual | book, gunpowder, saltpeter dust (shapeless) |

> Recipe tables are the plan of record; any change during implementation is made here first.

---

## 27. Progression tree

```
TIER 0  Vanilla ─ gunpowder (saltpeter+sulfur+charcoal) ─ Dynamite, early Frag Grenades
   │
TIER 1  INDUSTRIAL   mine sulfur · saltpeter · lead · tungsten · copper/iron
   │    Combustion Generator → Arc Furnace (steel) → Industrial Press (plates/wire) → Ore Crusher (2× dusts)
   │    Chemical Plant (acids, RDX, plastic explosive, napalm, synthetic fuel, plastic)
   │    ⇒ grenades, C4, mines, claymore, timed bomb, napalm bomb, HE bomb, Hammerhead RL, flamethrower,
   │      grenade launcher, gas mask
   │
TIER 2  MISSILE AGE  rare earths → Circuit Fabricator (basic/advanced circuits), magnets, motors
   │    Assembly Bench · Launch Pad · SRM I/II · inertial guidance · binoculars · remote detonator
   │    ⇒ thermobaric, cluster, bunker buster, MOAB, chemical bomb, Lancer GML, radar, interceptors,
   │      Cryogenic Condenser (LOX/LN₂) → cryo gun, liquid-fuel stage
   │
TIER 3  ATOMIC       uranium → yellowcake → UF₆ → Gas Centrifuge cascade (U-235)
   │    Breeder Reactor (power, plutonium, tritium) · hazmat · Geiger · RTG · decon
   │    ⇒ tactical nuke, fission, boosted fission, dirty bomb, mini-nuke launcher, Launch Complex,
   │      GPS/laser guidance, Launch Control Station, designator, SCUD Storm (+ Anthrax Beta)
   │
TIER 4  THERMONUCLEAR  lithium + cinnabar mercury → Li-6 · Electrolytic Separator → deuterium
   │    Fusion Fuel Synthesizer (LiD) · military circuits · superconducting coils
   │    ⇒ H-bomb, Tsar, neutron, cobalt, MIRV, ICBM boosters, Missile Silo, storm bomb, cryo bomb,
   │      EMP bomb, tesla rifle, EMP gun, railgun, lead-lined hazmat, Nuclear Missile Silo (China),
   │      Anthrax Gamma, Satellite Uplink + orbital rods
   │
TIER 5  EXOTIC       the End: void geodes → exotic shards → Exotic Matter Fabricator
        Particle Accelerator (antimatter) · quantum circuits
        ⇒ antimatter bomb, singularity bomb, antimatter rifle, singularity cannon, particle beam rifle,
          Particle Uplink Cannon (USA), Advanced CBRN suit, point-defence laser
```

---

## 28. Testing strategy
- **Build after every change**; the build deploys to the instance automatically.
- **GameTests** (`gametest/`): blast radius vs profile, resistance shielding (obsidian wall shadow), crater shape,
  per-tick budget compliance (no tick > budget + margin), save/resume of a running blast, every multiblock forms and
  un-forms in all 4 rotations, assembly validation rules (class mismatches rejected), radiation lazy-decay maths,
  EMP disables/re-enables machines, missile tracker arrives at target, sympathetic detonation rules (nukes fizzle).
- **Data generation** for blockstates, simple models, item models, recipes, loot tables, tags, lang, sounds.json,
  biome modifiers, structures/jigsaw registrations. Hand-made models come from `tools/`.
- **Asset review** through the preview renderer for every model before "done".
- **runClient** smoke test each phase; the user's in-game test checklist at the end of each phase.
- **Performance budget:** Tsar detonation on a dedicated test world must keep MSPT under 50 with default config.

---

## 29. Decisions made by the user
| # | Decision | Outcome (2026-10-19) |
|---|---|---|
| D1 | Mod name and ID | **Ground Zero** (`groundzero`) |
| D2 | Power system | **NeoForge Energy (FE)** with generators, cables and capacitor banks; low power pauses superweapon charge |
| D3 | Guide book | **Native Field Manual** (no Modonomicon) |
| D4 | GeckoLib as the one hard dependency | **Yes** (default kept; no objection) |
| D5 | Extras beyond the brief | **Railgun** and **Point-Defence Laser** in; Tectonic Charge and Gravity Repulsor cut |
| D6 | Superweapon announcements to all players in the dimension | **On** by default, configurable (default kept) |

No open decisions right now. New ones get added here as they come up.

## 30. Decision log
| Date | Decision |
|---|---|
| 2026-10-19 | NeoForge pinned to 26.3.0.92 to match the test instance; Java 25; MDG 2.0.148; Gradle 9.2.1. |
| 2026-10-19 | Build uses CurseForge's bundled JDK 25 via `JAVA_HOME`; Gradle auto-provisions a JDK 21 for NeoForm tooling. |
| 2026-10-19 | `deployToInstance` runs after every `build`, deletes every older `groundzero-*.jar` in the instance `mods` folder, copies the new jar; path overridable with `-Pdeploy_instance_dir`. |
| 2026-10-19 | Sulfur integrates with vanilla 26.2 Sulfur Caves (crush vanilla sulfur); mercury for Li-6 comes from vanilla cinnabar. |
| 2026-10-19 | Lead added as a resource (shielding); cobalt is a crushing byproduct rather than a new ore. |
| 2026-10-19 | Bombs are blocks per payload type (distinct models); missiles are one entity type composed from body/warhead/propulsion parts so each combination is recognisable. |
| 2026-10-19 | Nuclear bombs never chain-detonate (they fizzle and spread radiation). |
| 2026-10-19 | Long-range missiles are simulated analytically above build height (MissileTracker) instead of force-loading the whole path. |
| 2026-10-19 | Blast profiles are a datapack registry so radii live in one place for damage and visuals. |
| 2026-10-19 | User decisions D1–D6 recorded in §29 (Ground Zero, FE power, native Field Manual, GeckoLib, Railgun + Point-Defence Laser, announcements on). |
| 2026-10-19 | GeckoLib is `com.geckolib:geckolib-neoforge-26.3:5.5.7` from the Cloudsmith repo. `deployToInstance` also installs the GeckoLib jar into the instance when no GeckoLib jar is present (never duplicates or replaces one installed via CurseForge). |
| 2026-10-19 | Ores are three coplanar layers: vanilla host texture + our mineral overlay (cutout) + optional emissive glint layer — resource-pack friendly, nothing vanilla copied. |
| 2026-10-19 | Bombs lie along Z with the nose north; M-class bombs are two blocks long (bed-like). Round bodies are stepped pixel cylinders with baked lighting (`shade:false`). |
| 2026-10-19 | Sounds are reviewed visually (waveform + spectrogram) since they can't be auditioned here; the user auditions them in game with `/playsound`. |
| 2026-10-19 | Machine inputs (GUI, hoppers, pipes) only accept items that are an ingredient of some recipe of that machine's kind; outputs are extract-only for automation. |
| 2026-10-19 | Cables hold no energy: offers are delivered straight to the network's consumers, capped by the weakest cable tier on the network. |
| 2026-10-19 | GeckoLib machines render their item form with GeckoLib's special item model over a `block/block`-parented base model (standard display transforms). |
| 2026-10-19 | Two-block machines are door-style `TallMachineBlock`s: the lower half owns the block entity and loot; the upper half forwards use and capabilities. |
| 2026-10-19 | Industrial Press dies are recipe catalysts (`catalyst` field), never consumed; dies are cheap iron crafts so the press is part of the bootstrap. |
| 2026-10-19 | Machine work sounds are 2 s seamless loops whose rhythm matches each model's animation (press impact at 1.0 s). |
| 2026-10-19 | Side configuration is per machine-relative face: item mode (None/Input/Output/Both) + energy on/off, plus auto-eject; edited in a right-edge GUI tab. Defaults: every face Both + energy on (old behaviour). |
| 2026-10-19 | JEI is an optional dependency; machine recipe types are synced to clients (OnDatapackSyncEvent) because 1.21.2+ servers no longer send recipes. |
| 2026-10-19 | Resource item/ore textures follow vanilla conventions (studied, not copied): full-width ingots, rounded dust heaps, small lit ore clusters. |
| 2026-10-19 | Cobalt is a byproduct of copper (15%) and iron (10%) crushing; monazite (rare earths) yields 5% uranium dust. |
| 2026-10-19 | Every generated model texture uses a 1-texel gutter around each face, filled with the face's edge colour, to stop UV edge bleed (seam flicker). |
| 2026-10-19 | Side-config faces only offer the item directions a machine has slots for (a generator's faces are None/Input). |
| 2026-10-19 | Ingots and dusts use vanilla's shared item silhouettes (one outline for all ingots, one for all dusts) so mod items line up with vanilla's; the shading inside is our own. |
| 2026-10-19 | At the user's request ingots use vanilla's ingot light/shadow layout as well as its outline (a palette swap onto each material's ramp, as many mods do), so every ingot reads exactly like vanilla's. |
| 2026-10-19 | Machines with a fluid input have one fill-only tank fed by pipes, a bucket slot or a bucket click; liquids and gases for crafting travel as canister items. |
| 2026-10-19 | The Cryogenic Condenser picks oxygen or nitrogen with a separation membrane in a catalyst slot (like press dies) instead of a mode button. |
| 2026-10-19 | All material inputs in recipes use common c: tags (and NeoForge's vanilla-material tags), and all our materials are in those tags, so equivalent items from other mods are interchangeable. |
| 2026-10-19 | Tanks that show their contents render the real fluid (its texture and tint, translucent) at the real fill level, synced to clients in coarse steps. |
| 2026-10-19 | Realism boundary: chemistry and nuclear steps are deliberate game abstractions (one machine, one recipe, no quantities, conditions or designs that would teach a real process). Keep it that way. |
| 2026-10-19 | Multiblock patterns are written for a north-facing controller and rotate with it (columns to the controller's right, rows behind it), matching how the formed GeckoLib model rotates. Required-air cells are `.`. |
| 2026-10-19 | Right-clicking an unfinished controller toggles its ghost preview and reports missing/wrong counts; sneak-right-click opens the controller's GUI before the structure is finished. |
| 2026-10-19 | Chemical Plant recipes take at most three item inputs (plus water from the tank); solid propellant drops the slimeball binder to fit. Lithium-6 needs a mercury flask that comes back 90% of the time (a game stand-in, not a process). |
| 2026-10-19 | The Holo-Projector uses an interim recipe (basic circuit) until advanced circuits and batteries exist; revisit in slice 5/6. |
| 2026-10-19 | GeckoLib's cutout pipeline does not cull back faces in 26.x, so model export drops any face wholly covered by an opposite-facing face of another box in the same bone, and the 3D checker flags back-to-back pairs on open or see-through cubes. |
| 2026-10-19 | A multiblock controller's faces that lie against its own structure expose nothing and show as blocked in the side tab. The formed model puts something solid on every open face (control cabinet, port housing) so pipes and cables meet a surface. |
| 2026-10-19 | Liquids get buckets and in-world blocks; gases are tank/pipe/canister only. Chemistry runs on fluids in tanks; canisters fill from product tanks. Solid propellant gets its slimeball binder back (fluids freed item slots). |
| 2026-10-19 | Fluid pipes are bufferless pass-through networks driven by machine auto-eject (no pull mode yet); a water pump for automated water is a follow-up. |
| 2026-10-19 | Fluids move by machines and pipes, not by hand: the GUI fill/empty slots are gone; buckets work only as right-click (pour in) and sneak-right-click (draw out) shortcuts. Water comes from the Water Pump. |
| 2026-10-19 | Released gases are rising cloud blocks (1-8 parts, hanging from the block top), not inverted flowing fluids: a true upside-down FlowingFluid would need a re-implemented fluid engine and renderer. |
| 2026-10-19 | Buckets use our own fluid mask matched to the 26.x vanilla bucket's opening (NeoForge's mask fits the old bucket shape). |
| 2026-10-19 | Machine inventories keep their own slot count when loading saves (NeoForge's handler adopts the saved size, which crashed the plant screen after its layout changed). |
| 2026-10-19 | Pipes have no push mode: a pass-through network already delivers to every normal side, so Mekanism's separate push adds nothing here. Pull is per side; disconnected sides are remembered in the block state. |
| 2026-10-19 | Model export closes every visible opening (a left-out face nothing covers) and drops faces buried in solid geometry; deliberate openings are listed per model (open_ok). |
