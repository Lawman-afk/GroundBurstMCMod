# Ground Burst — Design Document

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
| Name | **Ground Burst** (the user's choice, 2026-10-28; Ground Zero before that, see §30) |
| Mod ID | `groundburst` |
| Java package | `dev.groundburst` |
| Alternatives considered | *Critical Mass* (`criticalmass`), *Doomsday Arsenal* (`doomsday`); at the rename, *Hypocenter*, *Launch Codes* and *Kiloton* |
| Tagline | "Assemble it. Launch it. Watch the horizon." |
| Source | https://github.com/Lawman-afk/GroundBurstMCMod (public since 2026-10-28, in this repository since 2026-10-30; author Lawman-afk) |
| Licence | **MIT** (2026-10-28, the user: others should be able to make add-ons); `LICENSE` at the root, in the jar as `LICENSE_groundburst`. The sound recordings it is made from keep their own (CC0 1.0, CC BY 4.0: `assets/groundburst/sounds/CREDITS.txt`) |

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
| Test instance | a CurseForge instance (MC 26.3, NeoForge 26.3.0.92, 8 GB allocated); its folder is `deploy_instance_dir` in the user's own `~/.gradle/gradle.properties` | local check |

### 2.1 Important API facts for the 26.x line (from the NeoForge migration primers)
These replace what older (1.20/1.21) knowledge would assume. Re-verify against the decompiled sources in
`build/moddev/artifacts` before using any of them.

- **Mouse buttons are SDL numbers.** `MouseButtonEvent.button()` is 1 for left, 2 middle, 3 right
  (`InputConstants.MOUSE_BUTTON_LEFT/MIDDLE/RIGHT`), not GLFW's 0/1/2: always compare with the constants (found when the
  Launch Computer map's right-drag panned on the left button).

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
  use `SYNCED` → file `groundburst-synced.toml`; client settings → `groundburst-client.toml`.
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
dev.groundburst
├── GroundBurst / GroundBurstClient       entry points
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

**Data-driven where it pays off.** Blast behaviour is a datapack registry `groundburst:blast_profile` (zones, speeds,
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
  texture (GeckoLib auto-glow layer / emissive model faces), with charge-dependent brightness where relevant. As
  built (after slice 8d, the user's request): `MachineGeoRenderer` gives every GeckoLib machine GeckoLib's auto-glow
  layer, drawn only where the model's texture has a `_glowmask` (GeckoLib does not check; a missing one would draw as
  the missing texture). Until then no renderer drew the glowmasks the machines were made with. Parts shown only while
  a machine runs (fire, melt, plasma, cores: hidden bones) glow only then.
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
| `tools/gb/palette.py` | OKLCH-generated 7-step ramps (`outline, deep, shadow, base, light, highlight, shine`) with hue shifting: shadows toward purple-blue (via red for warm hues, so yellows never turn green), highlights toward yellow. Hand-picked ramps for hazard colours. Swatches: `docs/style/palettes.png`. |
| `tools/gb/tex.py` | `Tex` canvas + primitives: bevelled panels, rivets, seams, vents, grilles, hazard stripes, 3×5 stencil font, decals, trefoil, placards, seeded wear (corner-biased edge chips, scratches, drips, sparse speckle). |
| `tools/gb/model.py` | Block/item model DSL: boxes → automatic shelf-packed per-face texture layout (texture height shrinks to fit) → paint each face as seen from outside → block JSON with `display` transforms. `fit_display()` fits oversized models into every slot (GUI fitted by projected silhouette). Supports XYZ/axis element rotation and `light_emission` (both verified in 26.3). |
| `tools/gb/shapes.py` | Pixel-stepped cylinders and lathe profiles from non-overlapping row bands; `band_tone()` bakes smooth curvature lighting so round bodies don't look ribbed (those boxes use `shade:false`). |
| `tools/gb/geo.py` | GeckoLib model + animation DSL authored in the same world orientation and paint conventions, exported to Bedrock geometry the way GeckoLib 5.5.7 reads it (X negated, up/down UVs rotated 180°, rotation X/Y negated). Files go to `assets/groundburst/geckolib/models/` and `geckolib/animations/`; glow layers as `<texture>_glowmask.png`. |
| `tools/preview/render.py` | Numpy rasteriser replicating vanilla face/UV orientation, element rotations, directional shade, cutout, light emission; renders 8 views + true-size inventory icon into a contact sheet. |
| `tools/preview/geo_preview.py` | Same for GeckoLib models, following GeckoLib's baking maths; renders animation frames. |
| `tools/sound/synth.py`, `recipes.py`, `review.py` | Synthesis toolkit (noise colours, sweeps, grains, filters incl. time-varying low-pass, saturation, convolution reverb, echo, limiter, distance re-rendering) → mono 44.1 kHz OGG Vorbis; recipes per sound; review renders waveform + log spectrogram with peak/RMS/clipping readout. |
| `tools/structure/nbt.py`, `template.py` | NBT writer and structure templates (26.3 block-state keys `id`/`properties`, data version 5023). |
| `tools/assets/*.py` | One script per asset family (ores, fission bomb, ore crusher, …). Assets are generated deterministically and committed. |
| `tools/style_guide.py` | Rebuilds `docs/style/` (palette swatches + reviewed pilot previews). |

**Model conventions:** a model's front is its **north** face (like vanilla furnaces); block models use 0–16 units,
GeckoLib models are centred on the block's bottom centre (x, z −8…8). 16 texture pixels per block everywhere.

Each asset gets a "review" status in PROGRESS.md: *drafted → previewed → revised → approved*.

### 4.3 Surface materials (the remaster after slice 9b, 2026-10-23)
The user asked for the recent structures (the Strategic Assembly Hall, both silos, the Radar Array, the Launch Computer
and the Launch Complex) to look better, above all their metal and concrete, either by researching how or by finding
something else to do it; they took the research. The structures had the right shapes but flat fills per box: one shade
a face, a speckle on concrete. They are now painted by a material library, `tools/gb/materials.py`, the way a texture
artist would paint them, over the model's geometry rather than box by box.

**What the research said** (the Faithful packs' write-up of Jappa's vanilla rules, pixel-art practice for metal, and
weathering references):
- Limited palettes: about ten colours a texture, ramps of short hue-shifted steps (shadows cooler, lights warmer); at 16
  px a material needs two or three shades of its ramp, not a gradient. The darkest and lightest never touch without a
  step between. Avoid banding, large flat areas and lone pixels, and do not over-dither.
- Smooth metal: a small, contrasty palette, crisp lit and shaded lips, at most a little linear (brushed) variation.
  Rough metal and stone: dithering is fine there. Big panels: 1-2 px seams, darker, with a lit lip. Round things: a
  highlight down the middle.
- Weathering tells where a thing is handled and rained on: paint chips on exposed edges, corners and rivets, never in
  the middle of a panel; rust starts in crevices and at edges, a dark dry core (#3B1E12) with a fresh orange halo
  (#C85A17), and runs down under gravity; grime gathers low and in corners, dust on top; rain streaks hang from top
  edges. A texture must still read from a distance.

**How the library works:**
- *World space.* Every texel is placed at its point on the model and the noise is sampled there, so eight boxes of one
  slab read as one slab and stripes run on across seams and round corners.
- *Edges.* For each face border it works out whether the solid really ends there or carries on in another box: lips
  catch the light (top/left) or fall in shadow (bottom/right), and paint chips only where an edge is real. A thin
  member (a 2-px post) is all edge, so its chipping is scaled down by its size.
- *Contact shadows.* Each texel looks out over the face for other boxes of its bone close above it: shadows at the feet
  of posts, in corners, under hoods.
- *Palettes.* A material works out a value per texel (base tone, mottle at two scales, grain, brushing, lips, skylight
  falloff down upright faces, shadows, grime, dust, streaks, scorching), then puts it on its ramp with ordered (Bayer)
  dithering aligned to the model's grid; smooth materials then lose their lone pixels, rough ones (concrete, soot) keep
  their grain (cleaning it turned concrete into camouflage blotches). Chips, rust and pits go on after.
- *Materials:* concrete, painted steel (any ramp: grey, yellow, orange, red, olive, armour, white), bare steel,
  galvanised steel, rubber, wood, soot; hazard stripes (yellow/black) and warning stripes (red/black) as two materials
  in 45-degree bands; details on top in the same space: seams, rivet rows, grating, scuffed floor paint, dithered
  scorching (scattered, not ordered: an ordered pattern read as a checkerboard), lighten/darken masks.
- *Block textures* are tiles: the same materials on a 16x16 face whose noise wraps, so a block joins its neighbours
  without a seam; scattered dithering there (an ordered pattern repeats block after block), and a base tone right on a
  ramp shade (between two shades, noise flickers half the texels between them like static).
- *Sections of something bigger* (the formed Fission Reactor, drawn a cell at a time over any size): a model's
  `Surfacer` takes a `period` (its noise wraps every block, as a tile's does) and a `context` (boxes standing round
  it, such as the neighbouring cells' sections: solid for edges, contact shadows and grime, never painted), so a face
  that runs on into the next cell has no lip, shadow or grime at the join and only the whole's real edges are lit.
  Surface detail that must look continuous is designed, not noise (noise repeats every block: riveted courses that run
  on round the whole, not blotches), and ramps get more shades where a big surface needs fine steps.
- Review: `tools/preview/geo_views.py <model>` renders a structure from four angles and in close-up crops at a readable
  size; `tools/preview/tile_sheet.py <textures>` shows block textures alone and tiled 3x3.

**As painted:**
- *Strategic Assembly Hall:* a sealed concrete floor cut into slabs at the column lines, worn yellow bay lines, hazard
  bands at its open ends; grey I-beam columns, their webs in shadow; yellow girders bolted along their sides, striped
  at the ends, rails bright where the wheels run; dark cradles with yellow cheeks and rubber saddles; a hoist drum wound
  with rope; a wooden pallet of olive crates.
- *Launch Complex:* a concrete deck cut into slabs round the missile, scorched along its trench (concentric, ragged at
  the edges), banded round its edge; a sooted trench and deflector with heat-blued welds; galvanised deluge pipes; a
  grey tower with galvanised grating walkways, orange toe boards and rails; orange arms, rubber hoses.
- *Launch Silo:* a concrete pad scorched round its flame hole, banded round the hole and its edge; a red gantry; a
  galvanised platform. *Launch Computer:* an olive desk with drawers, dark screens and keys, a galvanised mast, a white
  dish. *Radar Array:* a banded concrete deck, yellow railings, a galvanised mast, a white dish on a dark yoke.
- *Missile Silo:* a concrete collar banded round its outer edge, striped steel lips, armour-grey doors bolted along
  their long sides and striped where they meet.
- *Blocks:* Launch Pad, Reinforced and Hazard Concrete (formwork seams and tie holes; worn paint), Gantry Frame and Arm
  (a Warren truss), Radar Mast, Flame Deflector, Crane Rail, Silo Hatch, the dishes and the antenna.
- (At first the missiles and the shared cabinets, casing and ports were left alone; the second pass below did them.)

**Second pass (2026-10-23, the user's request: the missiles, every other multiblock, and every model from now on):**
- *The rule:* every model and block texture, new or reworked, is painted with the library (DESIGN.md §4.3):
  `materials.py` for models, `panels.py` for 16x16 machine faces, `missile_paint.py` for missiles. Screens, glyphs,
  glow layers and see-through windows are drawn over the material, as before.
- *Library fixes found on the missiles:* edge lips are now a whole ramp shade up or down, solid along the edge (added
  to the value before dithering, a one-texel lip broke into dashes); paint chips scale with the wear (their threshold
  had barely moved, so "hardly worn" paint lost half of every edge, and the structures were chipped more than meant);
  grime, rain streaks and the skylight falloff go by where a surface stops (an exposed edge, or a corner where it
  meets another surface) instead of each box's own border, so a surface built from several boxes shades as one (the
  dishes had a dirty band at each box's foot); the model tools' hole check now looks inside rotated bones too (the
  dishes' tilted rings had open faces nobody saw: closed by `close_holes` like any other).
- *Missiles* (`missile_paint.py`): a factory finish, smooth and clean (no wear, no corner shading: a round body has
  no corners, its plus-shaped boxes only stand in for one), lit edges, each side face lit down its middle so a body
  reads round; markings laid out in the missile's own item pixels, so a missile looks the same as an item, in flight,
  standing on any launcher and lying in the hall. Roll bands and quadrants, black fins with white leading edges, ring
  seams with lit lips, rivet rings round interstages, data plates, a cable raceway and a stencilled marking on the
  intercontinental first stage (their warheads are the warheads' own models since 2026-10-23, §4.5). Fine details
  only where a texel is at most half an item pixel (not on the inventory icon).
- *Flight models:* each missile has a second model, `flight_<look>`, four texels an item pixel (the model tool gained
  a texel density): a missile in flight and its spent stages are the item model drawn four times its size, so they
  had been four times coarser than the same missile standing on its launcher. `MissileParts.flight` picks it in
  flight (`MissileRenderer`, `SpentStages`) and in the assembly screens' big preview (`flightLook`); the inventory
  keeps the item model. 69 flight textures, about 12 MB.
- *The older multiblocks:* the Chemical Plant (stainless vessels welded in courses, their window slots still open onto
  the real fluid, a hazard-fronted skid with a tread-plate deck, galvanised pipework, red hand-wheels, striped port
  housings), the Fission Reactor (lead plates a block square bolted at their corners, the hazard band and trefoils,
  blue coolant pipes), the Particle Accelerator and Fusion Reactor (a dark plated plinth with a tread-plate top and a
  hazard band; red magnets, yellow quadrupoles, copper coils wound in turns; the fusion vessel polished in panels, its
  coil cases showing their windings, cryo lines along their tops) and the Singularity Reactor (olive deck plates,
  steel posts and glazing bars, brass-rimmed emitters and gimbals). Their glass, plasma, beams and cores as they were.
  The Singularity Reactor's transoms stand 0.25 px proud of its mullions and its roof beam is thinner (both had faces
  0.1 px or less apart: the only z-fight warnings left in the mod's multiblocks).
- *Shared blocks* (`structures.py` on `panels.py`): every controller's face and top, the Machine Casing, the Energy,
  Item and Fluid Ports, Lead-lined Casing, Steel Plating, the accelerator's and reactors' parts: their designs as
  they were, in a brushed steel frame with solid bevels and bolts round painted or metal panels.

**Third pass (2026-10-23, the user's request: every other machine and block; warheads and the rest of the ordnance
next, items after that):**
- *The machines' kit* (`tools/gb/machine_kit.py`): one set of materials for every machine (olive machine paint, NATO
  green, gunmetal fittings, dark steel, bare and stainless steel, galvanised, copper, brass, rubber, white ceramic,
  yellow, orange, red and black paint, hazard paint, graphite, refractory brick) and the details they share: the
  plinth (dark steel, a hazard band on its front, a tread-plate top), bolted panel seams, louvres, ribs and fins, soot
  spreading from an exhaust's lip, sockets; glyphs (CRTs, dials, data plates, lamps, trefoils) drawn over the material.
- *The single-block and two-block machines*, each repainted in model space on the kit, their designs, glow layers and
  animations as they were: Combustion and Diesel Generators, RTG, Ore Crusher, Arc Furnace, Industrial Press,
  Electrolytic Separator, Water Pump, Circuit Fabricator, Gas Centrifuge, Fusion Fuel Synthesizer, Exotic Matter
  Fabricator, Cryogenic Condenser, Canning Machine, Battery Charger, Decontamination Shower, the four Capacitor
  Banks (each in its tier's finish) and the Ordnance Assembly Bench; the machines' break particle.
- *Small packed sheets* (`tools/gb/atlas.py`): the cables, fluid and item pipes and the fluid tanks' frame and caps,
  their regions filled from material tiles, round cables and tubes shaded across their width, details drawn on as
  pixel art.
- *Storage blocks*: the uranium block (a billet in a hazard frame, its trefoil), lead shielding bricks and the
  machined tungsten plate on material tiles.
- *Left as they are, on purpose*: the ores and raw ore blocks are vanilla-style mineral clusters drawn over vanilla's
  own rock (so they match it and any resource pack), and voidstone, the exotic crystals and corium are natural matter
  drawn in vanilla's manner; the fluids' animated textures. The ordnance (charges, mines, bombs, warheads, the toxic
  sludge they leave) comes in the next pass.

### 4.4 Model design (the warhead remaster, 2026-10-23)
The user asked for the warheads to be remastered, models as well as textures, with the model design researched first.
The old warheads were "plus" prisms (a core box and a box either side) in flat palette fills, their noses stair-stepped
boxes that read as ziggurats, the cluster family one shape in different colours.

**What the research said** (the Blockbench Minecraft Style Guide; hard-surface prop practice; real ordnance):
- *Shape from the model, detail from the texture*: keep elements few but the object recognisable. Don't build slants
  and curves as stair steps of elements; turn an element instead. No "mixels" (elements under a pixel, or textures
  scaled finer than the model): one texel a model pixel, never squashed or stretched.
- *Silhouette first*, then the big forms, then details; only as much detail as reads at the distance it is seen
  (a background prop needs a strong silhouette more than fine detail); zoom out and check.
- *Ramps* of a midtone with a shadow and a highlight first, hue-shifted; purposeful palettes; no pillow or pancake
  shading; tops brighter than bottoms.
- *Real ordnance*: the US low-drag bomb shape (Aero 1A: about 8:1, a tangent-ogive nose, nose and tail fuze wells,
  suspension lugs 30 inches apart and a hoisting lug at the balance point); the Tactical Munitions Dispenser
  (a strapped canister that splits open at height to scatter its bomblets); the Mk 77 firebomb (an unfinned sheet
  metal tank); penetrators (long, slim, thick-walled, a sharp hardened nose); re-entry vehicles (the Mk 21: a sharp
  cone, about 22 inches across and 69 long, in ablative heat shield, on its skirt). Colour codes (MIL-STD-709): a
  yellow band for a high-explosive filling, light red for incendiary, dark green for a toxic chemical agent.

**How the warheads are built now** (`tools/assets/warheads.py`):
- *Round sections are true prisms*: 26.x model elements turn by any angle, so a 16-sided section is the union of
  eight planks laid every 22.5 degrees, each spanning the width one way and exactly one facet the other (squares
  overlapped would make a star); a small one is an octagon (four planks). Each plank shows only its two ends (two
  facets) and its caps, so the textures stay small (most 128x64).
- *Noses follow real profiles* a ring at a time, a whole pixel a step: a tangent ogive (the classic nose), a dome, a
  blunt mix, a cone (re-entry vehicles); each ring at least 0.12 px inside the one under it.
- *No flicker*: caps of planks side by side round a section (which meet near its rim) stand 0.06 px apart, every
  other plank sunk whole (so its texels stay a pixel tall and bands line up facet to facet); planks a quarter turn
  apart meet only near the middle, which the ring above covers; small sections sink each plank further. A section
  with no bottom reaches a pixel down into what it stands on, unseen, so a sunk cap leaves no slit. The script looks
  straight down on every model and fails any place two caps lie within 0.03 px (`check_caps`).
- *Painting*: the material library paints turned boxes where they really stand (`Surfacer(turned=True)`), so noise,
  bands and decals run on round the facets; a round body's facets are toned alike (the game lights them) and its
  ledges a touch darker against the game's brighter tops (a material's `facing`), so a stepped nose reads as a taper;
  edge lips only where a section really ends. Bands sit on whole texel rows; seams, dashes and bolts on each facet's
  middle column; trefoils and stencils are laid across the front facets by position.
- *Each type has its own silhouette and the details of its kind*: HE (olive drab, ogive and nose fuze, a yellow
  band, two suspension lugs; the Heavy a size up with two bands and a hoisting lug; the Massive Ordnance Warhead huge,
  riveted seams round it, a blunt orange nose); Thermobaric (stubby, a blunt dome, a ring of fuel ports, two orange
  bands); Bunker Buster (long and slim, a long sharp tungsten nose); Napalm (a silver firebomb tank, a filler cap, a
  light red band); the dispensers (Cluster, Poison Gas, Smoke, Cryo, EMP: a strapped canister split down its sides,
  each in its own colours, the Cryo frosted with vent caps, the EMP with a copper coil between ceramic insulators);
  Chemical (pale grey, two dark green bands, a filler plug); Toxic Waste (a hooped drum, a glowing toxin-green dome);
  the nuclear ones (a squat Tactical with an arming panel; a fat ochre Fission with a bolted equator; a sleeker grey
  Boosted Fission with its boost-gas reservoir; a slim silver Neutron with reflector studs; a rusty Dirty drum with a
  lid and tape); the Thermonuclear and Cobalt as re-entry vehicles (a dark ablative cone on a banded skirt; the
  Cobalt's cone banded where its jacket's plates meet); the Tsar on its fins and parachute pack.
- The warheads' selection boxes follow the new sizes. Missiles carry the warheads' own models (§4.5).

### 4.5 Missiles carry their warheads' own models (2026-10-23)
The user asked for the missiles to show the warhead models they are built with, not a generic stepped nose painted in
the warhead's colours. A missile now carries the warhead's own block model as its front section, everywhere it is
drawn: as an item (the inventory, a hand, the ground, the assembly screens' preview), in flight, standing on the
Launch Silo, the Launch Complex and the Missile Silo, and lying on the Strategic Assembly Hall's cradles.
- *Fit:* a warhead stands on the top of the missile's body, drawn at the scale that puts its mounting ring's corners
  just inside the body's sides (so it reads as the missile's front section, bolted on), unless that would make it
  longer than the airframe allows (9 item pixels on the usual missile, 9.5 on the Strategic one, 10 on the
  intercontinental one: the long, slim Bunker Buster is drawn smaller, its penetrator rising from the body's top
  rather than towering over the missile). The Tsar keeps the look it had: wider than any stage of its missile
  (scale set by eye). A missile drawn stretched along its length (a Light or a Heavy body) does not stretch its
  warhead: it takes the body's scale across as its own. The warhead's markings face the launcher's front.
- *Data:* `tools/assets/launch.py` measures each warhead's model (`warheads.mount`: its mounting ring, its reach, its
  height) and writes `assets/groundburst/missile_warheads.json` (for each airframe its body's top; for each warhead it
  may carry, the scale): `WarheadMounts` reads it with the client's resources.
- *Drawing:* `WarheadNose` draws the warhead's block model (unarmed; armed, its lamp lit, while a launcher counts
  down) at the body's top. Items: each missile's item model is a composite of its airframe (`missile_airframe`,
  `strategic_missile_airframe`, `icbm_missile_airframe` and their flight models and spent-stage variants) and a
  `groundburst:warhead` special model (`WarheadSpecialRenderer`, naming the warhead and the airframe), so the missile in
  flight, drawn from its item model, carries it too. Launchers and the hall: their GeckoLib models have an empty
  bone at the warhead's foot (`warhead`, `swarhead`, `iwarhead`) and the block entity says what to draw there
  (`WarheadCarrier`); `MachineGeoRenderer`'s warhead layer draws it. The per-warhead painted noses, their item models
  (one per warhead and airframe) and the launchers' and hall's nose bones are gone; a missile with no warhead keeps its
  bare steel nose (`nose_none`, `snose_none`). The Tsar's vents glow by its own model's light, so the complex's and
  the hall's glow masks went.

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
| Enriched uranium (U-235) nugget/ingot | Gas Centrifuge | 1 000 mB UF₆ gas (piped into its tank) → 2 enriched uranium nuggets + 1 depleted uranium dust; 9 nuggets = 1 ingot |
| Depleted uranium ingot | Furnace | depleted uranium dust (dense penetrators, tampers, AP rounds) |
| Uranium fuel rod | Assembly (crafting) | 3 natural uranium ingot + zircaloy… → **simplified:** 3 natural U ingot + 2 steel rod |
| Depleted-uranium target rod / Lithium-6 target rod | Crafting | DU ingot ×3 + steel rod ×2 / Li-6 dust ×3 + steel rod ×2 |
| Plutonium nugget/ingot, Nuclear waste | Chemical Plant (reprocessing) | irradiated DU target rod → 3 Pu nuggets + 1 nuclear waste |
| Tritium canister | Chemical Plant | irradiated Li-6 target rod + empty gas canister |
| Mercury flask | Arc Furnace (roasting) | 4 cinnabar dust (from crushing **vanilla cinnabar** blocks) + empty canister |
| Lithium-6 dust | Chemical Plant ("COLEX" process) | 4 lithium dust + mercury flask → 1 Li-6 dust + 3 lithium dust; flask returned 90% of the time |
| Deuterium canister | Electrolytic Separator | water (8 000 mB) + empty gas canister |
| Lithium deuteride pellet | Fusion Fuel Synthesizer | Li-6 dust + 1 000 mB deuterium gas (piped into its tank) |
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
- Fluid pipes come in three tiers like the cables: copper 150, steel 600, pressurized 2,400 mB/t (a little above
  Mekanism's and Pipez's matching tiers of roughly 100 / 500 / 2,000); a run carries as much as its weakest pipe, and a
  pulling side draws at that rate. They form pass-through networks
  with no buffer. Each pipe side is normal (in and out), pull
  (draws from the tank or machine it touches, up to 1000 mB every 2 ticks; hazard-striped collar) or disconnected.
  Machines push product tanks out of faces with auto-eject and fluids on; pull sides draw; the run delivers to its
  normal sides, never back to the source.
- Wrench (conventions of Mekanism's configurator / Pipez's wrench): right-click a pipe side cycles normal -> pull ->
  disconnected (pipe-to-pipe joins: on/off, both pipes); right-click a cable side connects/disconnects it (cables
  have no push/pull); right-click a machine turns it; sneak-right-click picks up pipes, cables, machines, tanks and
  structure blocks.

### 6.5 Item pipes
- Three tiers like the fluid pipes and cables: iron 8, steel 32, pneumatic 128 items a second, moved in batches of
  4 / 16 / 64 every half second. For reference: a vanilla hopper moves 2.5 items/s; Pipez moves 1 / 4 / 32 / 128 /
  1 280 items/s with no / basic / improved / advanced / ultimate upgrade; Mekanism's transporters pull 1 / 16 / 32 / 64
  items at a time. A run moves as many items as its weakest pipe.
- Same rules as fluid pipes (they share one pipe block base): sides normal / pull / disconnected with the Wrench, pull
  sides draw from the chest or machine they touch (a machine gives only what its side configuration offers, i.e. its
  outputs), machines' auto-eject and hoppers can push in, and the run hands items straight to the inventories on its
  normal sides, round-robin, never back to the source. Pipes hold nothing; there are no travelling item entities.
- Look: the fluid pipe's shape, but a glass sight-tube in a metal frame with flow chevrons, and an item-slot window
  on the junction box. Iron: pale iron. Steel: gunmetal with yellow fittings. Pneumatic: brass with copper fittings and
  blue glass.

### 6.6 Network performance rules
- Cable, fluid pipe and item pipe networks are found once (breadth-first, capped at 4 096 blocks, never stepping into
  unloaded chunks) after a change and cached per level; any pipe or cable change drops the level's cache and the next
  use rebuilds lazily.
- A network caches its endpoints (the blocks beside its connected, non-pipe sides) and reads them through NeoForge
  `BlockCapabilityCache`s, so delivering costs one cached lookup per endpoint and never walks the pipes; an endpoint
  in an unloaded chunk is skipped, never loaded.
- Pipe and cable outline/collision shapes are built once for all 64 side combinations.
- Machines show ACTIVE at once but drop it only after 10 idle ticks, and change it without neighbour updates, so a
  stuttering machine does not rebuild client chunk meshes or make its neighbours re-check their connections.
- Multiblock patterns lay out their cells once per facing; per-tick code asks for just the cells it needs (ports).

## 7. Power

All machines use **NeoForge Energy (FE)** so they interoperate with other tech mods (decision D2).

| Block | Output / capacity | Notes |
|---|---|---|
| Combustion Generator | 60 FE/t | Burns any furnace fuel; synthetic fuel canister = 24 000 FE. Exhaust smoke, rumble loop. |
| Diesel Generator | 180 FE/t | Burns synthetic fuel or liquid rocket fuel piped into its tank (canisters/buckets pour in). Piston animation. |
| RTG, "Radioisotope Generator" in game | 40 FE/t forever-ish | Fuel slug in a slot: enriched uranium ingot lasts 2 in-game days now; plutonium ingot (Fission Reactor) will last 10. The slug decays whether or not the power is used. Emits low radiation if not shielded by lead blocks (Phase 12). Glowing fins. |
| Fission Reactor (multiblock any size from 3×3×3 to 6×6×6, replaces the Breeder Reactor) | 75 FE/t a rod, up to 4 800 FE/t (64 rods) | Burns uranium fuel rods, one in each Fuel Channel in its core (§7.2), and breeds plutonium from DU target rods and tritium from Li-6 target rods. Every loaded rod burns at once, each with its own lifetime. Runs on core heat: buttons set control-rod insertion, water coolant is piped in, a heat gauge shows core temperature. Overheats and SCRAMs (shuts down) if starved; a damaging meltdown unless SCRAMmed in time, on by default (§7.1; the server can turn it off). Cherenkov-blue core. |
| Fusion Reactor (tokamak ring multiblock) | ~6 000 FE/t | Burns deuterium + tritium gas (LiD pellets are kept for the thermonuclear weapons). Needs a large ignition charge from capacitor banks to start, and liquid nitrogen for its magnets while running; once lit it sustains itself. A glowing plasma ring races round the torus. Ties together the Fusion Fuel Synthesizer, Cryogenic Condenser and capacitor banks. |
| Singularity Reactor ("special" multiblock) | up to 10 000 FE/t | A singularity seed sits in the controller (it stays there, like a battery in the charger) and is held in a powered containment cage. It eats items, any items: each is worth 10 000 FE and it eats at most 20 a second (one a tick), so its output is set by the feed rate (10 items/s = 5 000 FE/t, 20 items/s = 10 000 FE/t; it was 50 a second and 25 000 FE/t until the user asked for 10 000 after 9b). Holding the cage costs 1 000 FE/t (2 items/s). Built to need automatic cobblestone or tree farms. Taking the seed out shuts it down safely; if the cage runs dry or the structure is broken while it holds the seed, the core collapses and the seed is destroyed (a destructive collapse, a small Phase 11 singularity effect, will be a config option). |
| Capacitor Bank / Advanced / Superconducting | 100k / 1M / 10M FE, 1 024 / 8 192 / 32 768 FE/t | Every face takes power in except the front, which pushes it out; keeps its charge when picked up; comparator output; 8-bar charge gauge on the model. Superweapons draw from these during charge. |
| Power cables: copper (256 FE/t), insulated (1 024), high-voltage (8 192) | — | Connected-texture cable models with insulators on HV; HV cable arcs when EMPed. |

Superweapons and large machines need power while charging (as in Generals: low power pauses the countdown).

**Power ladder** (agreed with the user 2026-10-20; 2026-10-23: the Fission Reactor scales, the Singularity Reactor
cut to 10 000): Combustion Generator 60 → Diesel Generator 180 / RTG 40 (slice 6, with Capacitor Banks to buffer the
big reactors and superweapons) → Fission Reactor 75 a rod, 600 from the example's eight up to 4 800 from sixty-four
(slice 7, with the Particle Accelerator) → Fusion Reactor ~6 000 and Singularity Reactor up to 10 000 (slice 8). For
scale: the Exotic Matter Fabricator (400 FE/t) needs 7 Combustion Generators, 3 Diesel Generators, or a sixteen-rod
Fission Reactor at a third. All three
reactors are game abstractions (invented parts, simple fuel-in/energy-out rules), never real reactor designs (§30).

### 7.2 The Fission Reactor at any size (2026-10-23, after 9b, the user's request; reworked the same day)
"Scalable like in other mods, with a UI that scales the fuel rod count", and every rod burning at once. Reworked when the
user found that height did nothing for the rods ("twice as many fuel blocks ... does it alter it in some other way?"),
missed the formed model, and found the preview confusing "with the many possibilities".
- **The box** (`FissionCore`): any hollow box from 3×3×3 to 6×6×6 outside (the core at most 4×4×4). Corners and edges
  Lead-Lined Casing; faces casing or Reactor Glass, with exactly **one Item, one Fluid and one Energy Port** among them
  (a second port of a kind is outlined as wrong; a missing one is named). The controller stands in a face or in the
  bottom edge of its wall, never a corner, a post or the top edge. Inside, **every Fuel Channel holds one fuel rod,
  wherever it stands** (up to 64); the rest of the core is left empty for the coolant. A stray block in the core, glass
  on an edge, a box too big or a core with no channels does not form.
- **Finding the box**: from the controller, wall blocks counted along its row and column give the front wall; walks back
  from the front corners give the depth. Reactor blocks touching from outside make the box too big, as in other mods.
  Checked every 2 s and whenever a structure block near it changes.
- **The plan** (the ghost preview): the controller keeps a planned size (W×H×D, 4×4×4 at first), changed with the PLAN
  buttons on its screen while it is unformed; once formed the plan is the formed size. The ghost shows exactly that
  reactor, the controller in the middle of the bottom edge of its front wall: casing on the edges, roof and floor,
  glass in the side walls, the Item Port in the roof over the front of the core, the Energy Port low in the middle of
  the back wall, the Fluid Port low at the back of the left wall, channels filling the core. Valid alternatives (casing
  for glass, a port in another wall, coolant left in the core) are never marked; only a block that cannot stand where
  it is gets a red outline (a port's suggested cell takes casing or glass once that port stands elsewhere). The status
  line counts what is missing and wrong; the Holo-Projector pages the plan's layers.
- **Formed, it is one vessel** (`FissionSections`, `FissionReactorRenderer`): the walls' blocks hide (`FORMED`, the
  controller too) and the renderer draws a section in every wall cell: a block model each (35 of them,
  `tools/assets/fission_formed.py`, painted with the material library), turned a quarter turn at a time to the side it
  faces. It looks like the old fixed formed model, at any size, because the sections join with no seam (the user's
  second look: the first sections, each a bevelled plate with its own frame, read as a grid of tiles): each section's
  outer faces are painted with the neighbouring cells' sections standing round it (the library's `context`) and noise
  that wraps every block (`period`), so there is no lip, shadow or grime where a face runs on into the next cell; only
  the vessel's real edges are lit and shaded (its corners, the step from a pillar to a wall, the base's ledge, the
  head's overhang). A dark steel base banded in hazard paint all the way round; lead pillars with steel angles up the
  corners; lead walls and the head band clad in riveted courses a block tall, their seams and rivet rows running on
  round the whole vessel at the same heights; a steel angle along the top of the head band under the roof's raised
  rim, which the pillars' angles run up into at the top corners (the user's request), so steel outlines the vessel's
  corners and roof edge; a steel roof decked like the walls; a control-rod drive over every core column with a channel
  in it, its rod rising as the control rods come out; the ports' striped housings (a port in the floor hangs its
  housing underneath); the control console where the controller stands.
- **Glazing**: glass is a clear pane flush with the wall (or roof), no bars and no frame of its own: panes side by
  side make one glazed panel, and a 1 px steel lip runs round its outside only. Each glass cell takes lip pieces for the
  sides where its neighbour in the wall's plane is not glass and for the corners where any of the three cells round
  that corner is not (8 pieces for windows, 8 for skylights). The panes and the coolant go in one translucent batch,
  back faces culled and sorted far to near when drawn (the coolant's far sides had lain on the far walls' linings and
  flickered against them); the coolant stands 0.01 clear of the walls. `fission_formed.py` checks every join of two
  example reactors for faces that would flicker (none may). Seen from 192 blocks (it stands in for its walls' blocks).
- **Rods**: 75 FE/t each × rods withdrawn: 600 from the example's eight, 4 800 from 64, still below the Fusion Reactor.
  A rod lasts 192 000 ticks of full power on average (the energy a rod always gave: 300 FE/t for 48 000 before), its
  own lifetime drawn when it first burns, up to 15% either side; every loaded rod burns at once. The rod carries its
  burnup (`groundburst:fuel_burnup`: used, life), shown as a bar under it and in its tooltip. A used-up rod becomes a
  spent rod in its channel and moves to the spent slot when there is room.
- **Heat** goes by how full the core is (loaded rods / channels × rods out), so the rules of §7 hold at any size. Water
  left in the core slows it: every rate divided by min(3, 1 + water cells / channels) (a core packed with channels 1, as
  much water as fuel 2). Coolant 1.5625 mB/t a rod at full power (heavy water a fifth). Breeding goes at output / 1 200
  FE/t (sixteen rods at full power breed a target in 6 000 ticks). Buffers: 12 500 FE and 2 000 mB of coolant a channel,
  at least 50 000 FE and 4 000 mB (the tank stops at 32 000 mB, what a GUI sync slot carries).
- **The GUI** (226×218): the core map shows one layer of the core at a time (tabs 1 to 4 beside it, those above the core
  dimmed), seen from above with the controller's wall at the bottom and a smaller core in its middle: a rod slot on
  each channel (one rod each), coolant cells between, casing outside, a red-ringed slot where a rod is left on a cell
  that is no longer a channel (to take it out). Shift-click loads every channel, whatever layer is shown. Unformed, a
  plan panel covers the map (W, H and D with minus and plus buttons). Heat (0 to 1 400 °C) and rod gauges and the
  withdraw / insert / SCRAM buttons beside the map; the spent slot and the breeding (two targets over their outputs) in
  a right-hand column; the CRT shows output, core temperature, size and rods loaded (unformed: the plan).

### 7.3 Upgrade chips (2026-10-30, the user's request)
The user: upgrades for all the assemblers, and the machines too, an Upgrade Chip slotted in a tab like the config tab;
speed, power efficiency, or half of each; the speed chip draws power faster but no more in all; one level a circuit
tier, basic to quantum, 10% a level (the mix 5% and 5%), 40% at quantum.
- **Items** (`UpgradeChipItem`, `GBUpgrades`): `<tier>_<kind>_chip` for kinds speed, efficiency, balanced and tiers
  basic, advanced, military, quantum (twelve; stack to 16). `speedBonus()` 0.1 a level (balanced 0.05),
  `efficiency()` 0.1 a level (balanced 0.05). Recipes (shapeless): speed = the tier's circuit, two redstone, gold wire;
  efficiency = the circuit, a capacitor, two copper wire; two balanced = a speed and an efficiency chip of the tier.
  Sprites (`tools/assets/upgrade_chips.py`, the circuits' pixel style): a card on a gold edge connector in the tier's
  board colour with one to four pips (gold; quantum's violet), a label plate in amber with a double chevron (speed),
  green with a bolt (efficiency) or half of each (balanced). JEI info; Field Manual "Upgrade chips" (Processing).
- **Machines** (`MachineBlockEntity`): `acceptsUpgrades()` (the processing machines, the Assembly Bench, the four
  missile assemblers, the ICBM Assembly Hall and the ammunition factories; generators, reactors and launchers do not);
  one chip in its own one-slot inventory (`upgrade`, saved as "upgrade", dropped when broken, never automated).
  `workSpeed()` = 1 + speed bonus; `workEnergyFactor()` = speed x (1 - efficiency); `workEnergy(base)` rounds base x
  factor (at least 1); `workSteps()` banks the extra share of a step. The processing machines fold the speed into
  their existing carry (with a centrifuge cascade's) and draw `workEnergy` a tick; each assembler draws `workEnergy`
  and adds `workSteps()` a tick. So a speed chip finishes 1/1.4 the time on the same FE a job (it must be fed 1.4
  times as fast), an efficiency chip takes the same time on 0.6 the FE, a quantum balanced one 1/1.2 the time on 0.8.
  The "needs power" lines say the chip's draw.
- **The Upgrades tab** (`UpgradeTab`, `textures/gui/machine/upgrades.png` from `gui.py`): the config tab's mirror on
  the window's left edge (the config tab keeps the right). Collapsed, a tab with a chip glyph (the fitted chip drawn
  over it); expanded, a 112x62 panel: its header, the slot, and three lines (speed, power a job, power a tick). The
  slot is a real menu slot (`MachineMenu.UPGRADE_SLOT_X/Y` = -101, 27, after the player's inventory so no slot moves),
  shown only while the tab is open (`MachineMenu.upgradeTabOpen`, the client's); shift-clicking a chip from the
  inventory fits it (never into the machine's own slots). JEI is told the tab's area.
- GameTests (`UpgradeTests`, `StrategicTests`): three crushers on raw iron (200 ticks at 20 FE): a quantum speed chip
  5/7 the time on the same FE, a quantum efficiency chip the same time on 60%; the slot takes one chip and nothing
  else, shift-clicked in from the hotbar, the chip dropped with the machine; the hall with a quantum speed chip does
  1.4 ticks' work a tick on 350 FE. (Delayed blasts now keep the bomb scale they were set off at, `Charges.scaled`:
  the new tests had shifted the batches, and a thermobaric cloud and a missile's warhead in one took another test's.)
- **After the user's look** (the same day): **three slots**, not one (`MachineBlockEntity.UPGRADE_SLOTS`; the menu's
  three `UpgradeSlot`s from `UPGRADE_SLOT_X/Y`, 18 apart; a shift-click fits a chip into the first free one, a stack
  filling them in turn), their chips adding up (`speedBonus()`, `efficiency()`, the saving capped at `MOST_SAVED` 0.9);
  the **efficiency halved**, 0.05 a level (a balanced chip 0.025), so three quantum efficiency chips save 60% and no
  mix can run a machine free; the tab's panel 112x64 (then, after the user's next look, 82x64: just wider than its
  longest line, "Efficiency: normal", the slots from -71), the three slots in a row and two lines under them, speed and
  efficiency, each "normal" when nothing raises it (the lines on the power drawn a tick and a job dropped, and the speed
  chip's tooltip note: the user); a share shown to a tenth where it has one (`UpgradeChipItem.percent`: a balanced
  chip's 7.5). A saved machine's one chip stays in the first slot. GameTests: three quantum speed chips 120% faster on
  the same power a job, a quantum efficiency chip 20% less; four chips shift-clicked in, three fitted, one left over;
  three military balanced chips 45% faster on 22.5% less.

### 7.1 Fission Reactor meltdown (as built, Phase 5)
A game abstraction of a reactor accident, on by default (the user's call); the server config `reactor.meltdowns` turns
it off, and then the reactor SCRAMs at 900 °C.
- **Critical.** With meltdowns on there is no automatic SCRAM. Past 900 °C with the rods out the core is critical:
  it gains 0.35 + 0.9 x power °C a tick (plus a tenth of the usual uncooled heating); coolant cannot hold it at any
  temperature any more, it only halves the climb. From critical to meltdown: about 14-22 s without coolant, about
  40-47 s with water (an earlier version let water hold a critical core at ~1 295 °C forever; fixed). A klaxon (`reactor.alarm`, two alternating tones) sounds
  every 2 s, everyone within 64 blocks gets a red warning with the temperature, steam (and, past 1 150 °C, smoke)
  vents from the top. Status lamp: CORE CRITICAL. SCRAM, or every rod in, ends it.
- **Meltdown at 1 400 °C.** The structure and its fuel are removed; the blast profile `groundburst:reactor_meltdown`
  goes off at the core (core 5, crater 16, structural 28, scorch 30, fire 24, lethal 16, damage 36; enlarged after the
  user's look; not instant; sound large; visual `meltdown`: a dirty fireball and a heavy smoke plume, no mushroom
  cloud). When the blast has finished, **Corium** pours into the crater under the core (a disc of radius 3.2 on the
  first solid floor) and 18 blobs land on the ground 8-26 blocks out; never on fluid.
- **Corium**: light 12, emissive, animated glowing cracks; burns (3 damage and 4 s of fire) whatever stands on it,
  lights flammable blocks beside it like lava, and cools into **Cooled Corium** (a random tick has a 10% chance:
  about ten minutes). Both radioactive-tagged, iron pickaxe, blast resistance 30; corium drops cooled corium.
- **Contamination** (until the radiation field of Phase 12): a zone of radius 48 for one day (24 000 ticks), saved
  with the level (`contamination.dat`). Every second, living things inside (not undead, armour stands, creative or
  spectator players) get **Radiation Sickness** for 5 s at amplifier `min(3, floor(4 x s))`, s = strength x
  (1 - d/r) x (1 - suit protection), the strength fading linearly over the day. Radiation Sickness (made harsher after
  the user's look): damage `groundburst:radiation` (bypasses armour, no knockback) of 0.5 / 1.5 / 4 / 8 per second at
  I-IV, hunger drain growing per level, 5% slower per level. The Decontamination Shower washes it off. Phase 12
  replaces the zone with the field and drives the same effect from dose.
- **Hazmat suits** (§21.5, built now): Hazmat Suit (yellow plastic, filter hood; 75% radiation protection as a full
  set; armour 1/2/2/1; plastic sheet, glass pane, charcoal, yellow dye) and Lead-Lined Hazmat Suit (orange, lead plates;
  92%; armour 2/4/3/1, knockback resistance 0.05, 5% slower as a full set; a hazmat piece inside four lead plates).
  Pieces carry the set's protection in shares hood 20%, coat 35%, trousers 30%, boots 15%. At the crater centre a full
  hazmat suit keeps the sickness to II, the lead-lined suit to I.
  Icons redrawn 2026-10-27 (the user: they did not look like armour) on vanilla's four armour silhouettes, studied
  from the 26.3 iron, gold, chainmail, netherite and turtle icons (never copied): the helmet a dome over the dark of its
  face, the chestplate a body under two shoulder lobes with a V at the neck, the leggings a waistband over two legs, the
  boots two boots side on with the toes out; light from the top left and an outline lit above and left, dark below and
  right. In those shapes the hood's dome over a wide visor with its filter under the chin, the coat's zip under a storm
  flap with a waist seam and a trefoil patch, the trousers' knee tape and taped cuffs, black overboots under taped
  cuffs; the lead-lined set adds riveted plates (crown, breastplate, thighs, toe caps). `tools/assets/suits.py`.

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
| **Gas Centrifuge** | 1×2 tall | 50 FE/t | uranium enrichment (UF₆ → U-235) | Tall slim cylinder; spinning rotor visible through a slit; **cascade bonus**: each centrifuge joined flange to flange in a straight line (max 8) speeds all by 15%. Rising whine. |
| **Electrolytic Separator** | 1 block | 60 FE/t | water → deuterium | Tank with electrodes and bubbling, two gas bottles on the side. |
| **Fusion Fuel Synthesizer** | 1 block | 120 FE/t | Li-6 + D → LiD pellets | Pressure chamber with gauges and a frosted viewport; pellet press clunk. |
| **Cryogenic Condenser** | 1×2 tall | 100 FE/t | LOX, LN₂, cryo cores | Frosted coils, vapour vents, ice crust grows on the model while working. |
| **Circuit Fabricator** | 1 block | 40 FE/t | circuits, sensor arrays, superconducting coils | Glass-topped clean box, robotic arm (animated) placing chips; green PCB visible. |
| **Exotic Matter Fabricator** | 1×2 tall | 400 FE/t (needs insulated or HV cable) | stabilised exotic matter, containment cells, singularity seeds | Magnetic cage of rings around a floating violet shard; rings rotate on different axes; lensing shimmer. |
| **Particle Accelerator** | octagonal multiblock ring, 9×9×2 | 2 000 FE/t | antimatter cells (slow) | Ring of magnet segments + injector + target chamber; a glowing bunch races around the ring when running. |
| **Fission Reactor** | multiblock 3×3×3 to 6×6×6 | generates | power + Pu + tritium | See §7, §7.2. |
| **Fusion Reactor** | multiblock octagonal ring 9×9×3 | generates (ignition from capacitor banks) | power | See §7. |
| **Singularity Reactor** | multiblock 5×5×5 | generates (feeds on matter) | power | See §7. |
| **Ordnance Assembly Bench** | 2×1 wide | 10 FE/t (optional) | assembles bombs, warheads and missiles | See §10. |
| **Decontamination Shower** | 1×2 tall, walk-in | 20 FE/t + 10 mB water/t while spraying | puts out fire, washes off poison/wither/nausea; radiation contamination from Phase 12 | Booth with yellow nozzles, falling water + splash/foam particles. |
| **Battery Charger** | 1 block | up to 512 FE/t from cables | recharges batteries and any mod's FE item (energy cells/guns/Geiger later) | Cradle with chasing charge LEDs. |

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


### 10.1 As built: slice 7a, warheads from parts (2026-10-21)
- **Everything via the bench** (the user's choice): warheads have no crafting recipes. A **payload core** (one per
  warhead, with its size class: S HE Charge, Incendiary Charge, Toxic Waste Tank, Chemical Agent Canister; M Heavy HE,
  Thermobaric and Penetrator Charges, the Cluster Dispenser and the Gas/Smoke/Cryo/EMP Canister Dispensers; L Massive
  Ordnance Charge), a **casing** of the same class (Light, Medium, Heavy Warhead Casing) and a **fuze** make the warhead.
- **Ordnance Assembly Bench** (`content/assembly`): two blocks wide (`WideMachineBlock`, its side half to the right of
  its front, like a bed; not turned by the Wrench), 10 FE a tick. Slots: core, casing, fuze, warhead. **Assemble**
  runs a job (S 5 s, M 8 s, L 12 s); **Take Apart** splits a warhead in the output slot into its three parts (part
  slots empty). The readout names the result or, in red, what is wrong. GeckoLib model: bench, hoist beam with trolley,
  chain and hook, cradle with clamp jaws, tool pegboard, work lamp, blueprint sheet; a warhead lies in the cradle while
  one is made or waits; "working" animation timed with its sound (chain, clank, ratchet, impact wrench).
- **Fuzes** (`Fuze`, the `groundburst:fuze` item component): only **Impact** and **Barometric** (after the in-game
  look: timed, proximity and remote fuzes did nothing a missile needs). A missile always sets its warhead off where it
  comes down; a barometric fuze changes the flight to burst the warhead its set height (10-200 blocks, typed on the
  bench) over the target. Cluster-type dispensers on an impact fuze still open 30 blocks up. A **placed warhead** has
  no screen, cannot be armed and hears no detonator: it waits to be fitted (it still falls, and another blast sets it off).
- **Sides:** two-block machines configure each half's faces: the bench each half's top, front, bottom and back plus its
  two ends; the tall machines (Arc Furnace, Industrial Press, Cryogenic Condenser, Gas Centrifuge, Exotic Matter
  Fabricator, Decontamination Shower) each half's four sides plus the top and bottom (10 faces each; the faces where
  the halves meet are inside). The wrench tab lays them out unfolded.
- **JEI:** an Assembly Bench category (core + casing + any fuze -> warhead, with class, time and power).
- Still to come (7c): the stats panel and 3D preview.

### 10.2 As built: slice 7b, missiles from parts (2026-10-22)
- The bench has two pages, **Warheads** and **Missiles**, picked by tabs on the left of its screen (drawn like the
  wrench tab: the shown page's tab grows out of the window's edge, the other sits darker behind it). The page is the
  bench's own (saved, synced as extra data 6, turned by buttons 4 and 5): shift-clicks land only on its slots and pipes,
  hoppers and auto-eject use only its inputs and output (`MachineBlockEntity.slotShown` / `slotAutomated`; after the
  in-game look: a shift-clicked warhead went to the warhead page's slot while the missile page was shown) (the slots of the
  other page are hidden; one job runs at a time, and the readout says so if the other page's job is busy). The missile
  page: warhead + **body** + **motor** + optional **guidance unit** + optional **module** -> **Missile** (8 s, 10 FE/t);
  **Take Apart** gives every part back, the fuze setting kept on the warhead.
- **Bodies** (warhead class): Tactical S, Medium S-M, Heavy S-L. A bigger body is a bigger missile, on the pad (the
  silo's `missile` bone is scaled) and in flight (Tactical 0.85 x 0.75, Medium 1, Heavy 1.15 across x 1.2 long).
- **Motors** (range): Solid Rocket Motor I 400 blocks (Tactical and Medium bodies), Solid Rocket Motor II 1024 (any),
  Liquid-fuel Stage 2500 (Heavy only).
- **Guidance** scatters the aim once, at lift-off, to any point of an ellipse round the target (every point as likely,
  so most misses are wide; long or short by up to the scatter, half that to the side): none 25% of the flight's
  distance, Inertial Guidance Unit 4%, GPS Guidance Unit 2 blocks. (First built as 8% / 2% with misses bunched near the
  target; in game an unguided missile hit too close for the inertial unit to matter.)
- **Hardened Electronics** (the one module so far): an EMP field never makes the missile a dud (without it, an even
  chance).
- The **Missile** item (`groundburst:missile` component, `MissileSpec`) stacks to 1, is named by its body ("Heavy
  Missile") and lists warhead, fuze, range, guidance and hardening; it carries its warhead's model (§4.5). It replaces
  the Basic Missile and the silo's warhead slot: the **Launch Silo takes one missile**, and its range is the missile's.
  The Launch Computer shows each silo's range and will not arm a silo whose target is beyond it.
- JEI: a Missile Assembly category (one recipe per body, with the warheads and motors that fit it).

### 10.3 As built: slice 7c, preview and stats (2026-10-22)
- The user chose a **wider bench window** (288 x 210) over a pull-out tab or a third page: the parts, readout and
  inventory keep the left 176 px; a right-hand column, divided off by a seam, holds the preview over a stats screen.
- **Preview** (`ItemPreview`, a picture-in-picture renderer like the manual's structure view): the subject's real model
  on blueprint paper, turning by itself, dragged round with the mouse (it resumes turning 3 s after). Zoomed to fit the
  model's own bounds at any turn and tilt (after the in-game look: the Massive Ordnance Warhead ran off the top at a
  fixed zoom); a missile is fitted as if its body were Heavy and drawn at its real body's size, so a Tactical missile
  stands smaller than a Heavy one.
- **Subject:** what the parts make (warhead page: the core's warhead with the fitted fuze and setting; missile page:
  once there is a warhead, a body and a motor), otherwise what waits in the page's output slot.
- **Stats** (`AssemblyStats`, common code, so GameTests check it): a warhead's class, blast radii from its blast
  profile (crater, wrecks, lethal, injures, sets fires), a flavour TNT yield for the explosive kinds (0.5 kg x crater
  cubed: the Massive Ordnance Warhead comes to about 11 t, like the GBU-43), its other effect (cluster types show what
  they scatter instead of their small casing burst; submunitions land within about 20 blocks of an airburst) and its
  fuze; a missile's warhead (crater, lethal, effect, fuze), then at full range its range, apex, flight time and how
  close it lands, and whether it is hardened. Small pixel-snapped type on the CRT, label left, value right.
- Not done: interception difficulty (needs Phase 10's interceptors), flight time to a typed distance (the full-range
  figures cover it), the Ammunition page (Phase 13 guns) and the blueprint slot (Phase 15 loot).
- Machine layouts carry their texture's width (`MachineLayout.textureWidth`): a window wider than 178 px uses a 512-wide
  sheet with its fill sprites moved to the right half.

### 10.4 One missile per warhead, and an assembler per class (2026-10-23)
The user's request after §4.5: a new missile assembler for each class below the Strategic Assembly Hall, crafting
harder class by class (the hall's recipe brought onto the same ladder), and missiles made entirely for their warheads,
so the head fits the body and the paint runs all the way down. The hall keeps its way of building, with a missile of
its own for each of its three warheads. The user chose an assembler per class (over one shared one or single-block
machines) and one missile per warhead, sized by the warhead's class (over every body size it fits).

**Research** (real missiles, by what each warhead is):
- *Tactical rockets and missiles*: the Honest John (three shipping sections: warhead, motor, fins), the Luna-M / FROG-7
  (its warhead the body's own diameter, which reads cleaner than a bulbous head; spin verniers behind it), the Scud
  (welded tank sections, a riveted aft skirt with four trapezoid fins, graphite vanes in the exhaust), the Tochka
  (lattice rudders), the ATACMS (a cluster dispenser), the Lance (slim, 0.56 m across, and the actual carrier of the
  W70-3 neutron warhead), the Pershing (two olive-drab stages, both finned).
- *Strategic missiles*: Minuteman and Peacekeeper (three solid stages, a post-boost bus, re-entry vehicles under a
  shroud; large solid missiles are stacked upright, stage by stage, lifted by crane and mated), the R-36M (a heavy
  two-stage liquid missile). The UR-500, which became the Proton launcher, was designed as a heavy two-stage missile to
  carry a 50-100 Mt Tsar-class warhead; its first stage is a central oxidiser tank ringed by six fuel tanks, each
  carrying an engine (the core is as wide as the rail gauge allowed).
- *Markings*: black-and-white roll patterns are test markings (V-2, Redstone, Jupiter: to see roll in flight);
  service missiles are olive drab or green, ICBMs white or grey; bands and stencils follow the warhead.

**Classes rebalanced** (so each assembler has a family to build; the Heavy class had only the Massive Ordnance
Warhead): the Thermobaric, Penetrator (Bunker Buster) and EMP payloads become class L (they take the Heavy Warhead
Casing): real fuel-air, penetrating and flux-compression warheads are among the heaviest. Light (S): HE, Napalm,
Chemical, Toxic Waste, Dirty. Medium (M): Heavy HE, Cluster, Poison Gas, Smoke, Cryo. Heavy (L): Thermobaric, Bunker
Buster, EMP, Massive Ordnance. Strategic (XL): Tactical Nuclear, Fission, Boosted Fission, Neutron. Intercontinental
(XXL, the hall): Thermonuclear, Cobalt, Tsar.

**The missiles** (`tools/assets/missiles.py`):
- *The head is the warhead itself*: the warhead's own block model, its elements merged into the missile's item model
  at its own size (one texel a pixel, like the missile's flight paint), its lugs left off (a missile warhead hangs on
  nothing). The body under it is the warhead's own radius, built of the same 16-sided plank sections with the same
  facets, so the warhead's mounting ring is the joint and the body runs on below it in the warhead's own materials and
  colours: its paint, bands and stencils, then the missile's own markings, fins and nozzle.
- *Sizes by class*: Light about 4 blocks, Medium 5.5, Heavy 7.5, Strategic about 9.5 (two stages), Intercontinental
  about 11.5 (three). A slim warhead too narrow for a missile body (the Bunker Buster's penetrator) stands on a conical
  adapter. Each missile's shape follows its warhead's character (an improvised, patched rocket for the Toxic Waste and
  Dirty warheads; a frosted white one for the Cryo; a Lance-like slim one for the Neutron; a UR-500 with its clustered
  first stage for the Tsar; ...).
- *One model used everywhere* (the inventory, hands, the screens' preview, the launchers, in flight), with variants
  that share its texture: with spent stages gone (`_s1`, `_s2`), each spent stage falling (`_stage1`, `_stage2`) and
  each assembly section (`_part1`...). Picked by custom model data (the warhead's id and the variant). A table written
  with them (`assets/groundburst/missiles.json`: tail, length, stage and section bounds, radius by height) tells the
  client where things are: the launchers' arms, the exhaust, the nose's re-entry glow, the spent stages' middles.
- *The Tsar* loses its tail fins and parachute pack (odd on a missile): a re-entry body with a flared skirt.

**The assemblers** (one block entity class, one per class; multiblocks with GeckoLib models, like the hall):
- *Light Missile Assembler*: an open concrete stand under an A-frame hoist beam; the missile laid on V-cradles in
  three sections (motor and fins, body, warhead).
- *Medium Missile Assembler*: a bay with a slewing jib crane on a post that swings each section from a rack.
- *Heavy Missile Assembler*: a portal gantry crane that runs on rails along the cradles.
- *Strategic Missile Assembler*: an upright integration tower; the missile is stacked on its stand stage by stage,
  work platforms folding in round it.
- Each takes a warhead of its class, its class's missile body and a motor that fits, with an optional guidance unit and
  Hardened Electronics, and lays the custom missile out section by section while it works (the sections drawn from the
  missile's own model). The Ordnance Assembly Bench goes back to warheads only. Assembly time and power grow with class.
- *Recipes, a ladder*: Light (servos, basic circuits: the early missile age), Medium (electric motors, advanced
  circuits), Heavy (hydraulics: pressure vessels, tungsten, a military circuit), Strategic (lead-lined casing, two
  military circuits, sensors), then the Strategic Assembly Hall (superconducting coils and three military circuits).

**As built (2026-10-23).**
- *The missiles* (`tools/assets/missiles.py`, 21, one per warhead): the warhead's block model elements are the head;
  the body under it is built of the same plank sections in the warhead's paint (`textures/block/missile/<warhead>.png`,
  under the block atlas so one item model mixes it with the warhead's texture). Variants share the texture: `_partK`
  (assembly sections, three for S-XL, four for the hall's), `_sK` (in flight, spent stages gone), `_stageK` (a spent
  stage falling). Picked by custom model data: the warhead's id (and the old `strategic_`, `icbm_`, `flight_` prefixes,
  so missiles from older worlds still draw). `assets/groundburst/missiles.json` (read by `MissileShapes` on the client)
  holds each one's class, tail, length, the warhead's foot, stage tops, engine heights, section tops and radius by
  height. JEI tells missiles apart by warhead and lists every one; each missile item is named by its warhead.
- *Drawn by the machines* (`MissileCarrier`, `MachineGeoRenderer`'s missile layer): the launchers' and the hall's models
  lost their missile geometry; each has a "missile" bone at the tail, where the client draws the missile's own item model
  (x4), lying (its nose to the front) or standing, turned to face its markings outward. A lying missile is set on its
  cradles' flat saddles by its own girth where they hold it (`Cradles`), so every missile rests on them.
- *The launchers' arms fit each missile* (`MissileCarrier.Grip`): the Launch Silo's arm and the Launch Complex's two
  umbilicals each slide on a collar up their tower's hinge spine to their hold on the missile (just under the warhead;
  low: an intercontinental missile's second stage, a Strategic one's first, any other two fifths up to the warhead) and
  telescope (a galvanised inner beam in the painted sleeve, so nothing stretches) in to its skin there. They stay set
  for the last missile loaded (`gripsFor`, saved), so at lift-off they swing clear where they held it. The complex's
  five clamp variants are gone.
- *The assemblers* (`content/assembler`: `AssemblerClass`, `MissileAssemblerBlockEntity`, `GBAssemblers`; models
  `tools/assets/assemblers.py`): slots warhead, body, motor, guidance, module, output; the warhead and body slots take
  only the class's own, the motor slot only motors that fit; the readout names a warhead of another class and the
  assembler it belongs to. Three sections (motor and fins or first stage, body or second stage, warhead), each set down
  two thirds of the way through its share of the job, with a clank. Unformed it will not start. Built of Launch Pad,
  Gantry Frame and Crane Rail with an Energy Port and an Item Port; formed, the deck cells are cut to the 14 px deck,
  the controller to its console and the Energy Port to its housing (`PartCut`).
  | Assembler | Structure | Time, power | Model |
  |---|---|---|---|
  | Light (S) | 3x6x4: two A-frames of Gantry Frame, a Crane Rail beam | 10 s, 20 FE/t | olive A-frames, yellow I-beam, a trolley with a chain hoist that runs back over the two cradles in turn; tool chest, crated parts |
  | Medium (M) | 5x7x5: the jib's post (Gantry Frame, 4 high) mid-side | 15 s, 40 FE/t | grey post, yellow slewing jib with mast, tie rods and counterweight; it swings to a cantilever rack of crated sections and over the tail and the nose; tool cart |
  | Heavy (L) | 5x9x6: Crane Rail track down both sides, the crane parked over the back row (Gantry Frame legs, Crane Rail bridge) | 20 s, 80 FE/t | orange portal crane: splayed legs on bogies, twin box girders, trolley and two-fall hoist, driver's cab; it runs to a flatbed of crated sections and back to the cradles; three cradles, rail buffers |
  | Strategic (XL) | 5x5x13: four Gantry Frame corner columns 11 high, Crane Rail girders on top | 30 s, 160 FE/t | white lattice columns, yellow girders and hoist beam with a trolley; the missile stacked upright on a stand with hold-down posts; three levels of grating work platforms that fold down round it while a missile stands there (their own animation controller) |
- *The bench* is warheads only (its missile page gone, `assembly_bench_missile` with it): anything an older world left in
  that page's slots is dropped beside the bench once it loads. The Thermobaric, Penetrator and EMP cores are class L.
- *Recipes as built*: Light: Machine Casing, 2 Basic Circuits, 2 Servos, 2 steel plates, 2 steel rods. Medium: Machine
  Casing, Advanced and Basic Circuits, 2 Electric Motors, a Servo, a CRT Display, 2 Dense Steel Plates. Heavy: Machine
  Casing, Military and Advanced Circuits, 2 Pressure Vessels, 2 Electric Motors, Tungsten Carbide, a Dense Steel Plate.
  Strategic: Lead-Lined Casing, 2 Military Circuits, a Sensor Array, a Pressure Vessel, 2 Electric Motors, 2 Tungsten
  Plates. The hall now: Lead-Lined Casing, 3 Military Circuits, 2 Superconducting Coils, a Sensor Array, a Tungsten
  Plate, a CRT Display.
- *JEI*: the Missile Assembly category has one recipe per warhead (its assembler shown as the station, the warhead,
  the class's body, the motors that fit, the optional parts, the missile), the four controllers its stations. Tooltips:
  a warhead names the assembler its missile is built at, a body the assembler it is fitted at.
- *Review tool*: `tools/preview/assembler_view.py <model> <warhead> [--laid n] [--anim a --time t]` draws a formed
  model with a missile on it as the game does (sections, cradle seating, standing or lying).
---

## 11. Explosion engine (`dev.groundburst.blast`)

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
- Initial energy varies by direction (downward rays get `crater_depth`× energy, giving bowl craters, not spheres), by
  smooth noise over the sphere (lobed, irregular rims) and by fine streak noise (a starburst rim and spiky floor,
  §11.4c). Seeded, so client and server agree.
- A ray whose energy is spent continues as a **pressure ray** through the structural/scorch/fire bands, applying the
  conversion table instead of destroying.
- A per-section bitset prevents double-processing where split rays overlap.
- **After the front** (2026-10-22, after the in-game look at the nukes): a clean-up phase looks at every block a ray
  reached but did not destroy; a cluster of up to 4 blocks that touches no other solid block is removed (no lone
  blocks hanging over the crater floor). Then the fires and rubble the ground pass chose are put down, each only where
  its ground is still there (the crater reaches the ground under a ring after the ring itself, so fires placed as the
  ring passed were left in the air). Then the rim's neighbour updates.

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
fire radius ignite. Items and XP orbs in the crater are deleted. Custom damage types (`groundburst:blast`,
`groundburst:nuclear_flash`, `groundburst:radiation`, `groundburst:toxin`, `groundburst:erasure`, ...) with death
messages.

### 11.4a As built (slice 5a)
- Profiles are a synced datapack registry `groundburst:blast_profile` (fields as §11.1 plus `lethal_damage`,
  `edge_damage`, `knockback`, `lobes`, `crater_depth`, optional `instant`); `test/*` profiles exist for the
  `/groundburst blast <profile> [pos]` command (permission level 2) and the GameTests. Weapons get their own profiles.
- Ray energy: a ray starts with `crater x cost(stone)` x direction factor (straight down x `crater_depth`) x lobe noise
  (seeded sum of 4 sines over the sphere). Entering a block costs `0.3 + resistance^0.75` (stone 4.1, dirt 0.9, obsidian
  ~204); air 0.05, liquids 0.6 (vaporised). Inside `core` everything breakable goes free. Bedrock-like blocks and the
  protected spawn stop rays. A ray not heading down is dropped once it is above the highest block anywhere
  within the blast's reach (not just the ground under it: a fix after the first in-game test, which showed only the
  downward half destroying anything).
- Pressure rays: structural band blows out glass, panes, leaves, doors, trapdoors, flowers, torches, lanterns, snow
  layers and other replaceable plants and carries on; scorch band scorches the first surface (grass/mycelium/podzol to
  dirt, coarse dirt for the nuclear table; snow melts; farmland and paths to dirt) and stops; fire band may light it
  (chance falls with distance). Terrain tables are code until the aftermath blocks exist (Phase 12).
- Writing uses `Level.setBlock` with client-update, known-shape, suppress-drops, skip-block-entity-side-effects and
  skip-on-place flags: heightmaps, lighting and batched section updates still happen, neighbour updates and item spew
  do not. Rim neighbour updates run after the front.
- Rays cap at 1 500 000 (past that they stop splitting). Rays entering a chunk that does not exist (and may not be
  generated) stop there.
- Entities: lethal zone `lethal_damage x exposure`, then `edge_damage` falling linearly to 0 at `damage`; knockback
  radial with the same falloff; fire inside `fire`; items and XP inside the crater are deleted. Damage type
  `groundburst:blast` (tagged `is_explosion`).
- Profile JSON groups the entity numbers under `entities` (`lethal`, `damage`, `lethal_damage`, `edge_damage`,
  `knockback`) and adds optional `sound` (small / medium / large / nuke) and `visual`, both chosen by crater size when
  left out.
- Until the Spectacle effects (5c), a vanilla explosion particle marks the burst point.

### 11.4b As built (slice 5b)
- Chunks: a non-instant blast force-loads (non-ticking) the chunk columns from 16 blocks behind to 32 ahead of its
  front, through the `groundburst:blast` ticket controller owned by the blast's UUID, and releases them behind it. With
  `generateChunks` off, unloaded chunks are only loaded if the disk says they are fully generated (read in the
  background; the front waits for the answer). Leftover tickets are dropped on world load. The ray ceiling rises as
  far chunks load.
- Saving: the level's blasts are its saved data `data/groundburst/blasts.dat` (codec: id, profile id and the scaled
  profile, centre, seed, terrain flags, front radius, age, phase, the live rays packed at 18 bytes each, 22 since §11.4c, the crater rim,
  start tick). They resume on load, queued in order.
- Client sync: `blast_start` (sound set, visual, centre, crater, reach, outer radius, front speed, seed, start tick),
  `blast_front` (radius, every 4 ticks), `blast_end`, sent to every player in the dimension; a player who logs in or
  changes dimension is sent the running ones. Network protocol version 2.

### 11.4c Crater character: streaks, ground pass, ejecta
After the first in-game look the user asked for craters with the radial line patterns of good explosion mods, not
smooth spheres. Prototyped in `tools/preview/crater_preview.py` (numpy mirror of the engine on flat ground: top view
plus cross-section), tuned there, then ported with the same formulas.
- **Streaks:** each ray has a *grain*, `clamp(1 + streaks x 1.7 x noise(direction), 0.45, 1.8)`, where the noise is
  3-octave value noise over the direction (lattice frequencies 7 / 15 / 31, weights 0.5 / 0.3 / 0.2, a 32-bit integer
  hash seeded by the blast; `StreakNoise`, mirrored exactly by the preview). Grain multiplies the ray's starting energy
  and its crater reach (`crater x grain`); split children take their own grain and the parent's energy rescaled by
  child / parent grain, so the streaks sharpen as rays multiply. Because rays run straight out, neighbouring strong and
  weak rays draw radial lines: a starburst rim with fingers past the mean radius and a spiky crater floor. (A version
  that damped the grain for downward rays, giving a smooth bowl, was tried; the user preferred the spiky floor, toned
  down slightly: factor 2.2 to 1.9, and after the in-game look again to 1.7.) Profile field `streaks` (default 0.3;
  0 = smooth).
- **Ground pass:** pressure rays no longer scorch or light fires (grazing rays and the cube-face grid made rings and
  seams). After each shell the columns whose centre lies in (r - 1, r] are visited. A column's ground is its highest
  motion-blocking block at or below the fireball (the burst plus max(1.5, crater / 4)), so floors under roofs and in
  caves count and walls at that height shadow what is behind them. A 720+ bucket azimuth horizon keeps the steepest
  angle seen so far; each column raises it across its own width; a column below the horizon (minus 0.02 rad) is
  hidden and spared. Visible columns: scorch where `dist / scorch < 0.72 + 0.6 x spoke x (1 + streaks)` (spoke = the
  same noise on the horizontal direction, so scorch spokes line up with the crater's fingers; a full disc when
  streaks is 0) with 8% speckle; fire with chance `0.6 x (1 - dist / fire)`; columns the crater dug or will dig
  (inside 0.45 crater) are skipped. A column whose surface holds fluid (water, lava, kelp) gets no fire and no
  rubble (it would float on the surface; it sinks instead).
- **Ejecta:** from 0.8 to 1.9 crater radii, rubble lands on visible ground with chance
  `ejecta x 3 x max(0, spoke + 0.05) x (1 - t)^1.5`, `t = max(0, (dist - crater) / (0.9 crater))`: rays of debris along
  the spokes. Its material follows what the crater dug (stone: cobblestone or gravel; deepslate: cobbled deepslate or
  gravel; soil: dirt or coarse dirt; sand: sand), or the ground it lands on for a blast resumed after a restart.
  Profile field `ejecta` (default 0.4).
- Saved rays carry their grain (22 bytes each, with a version marker; older saves load with grain 1). The ground
  pass's horizon is not saved: after a restart it starts empty.

### 11.5 Chunks, persistence, absence
- A `TicketController` (`groundburst:blast`) force-loads the chunks in the active band `[r-16, r+32]` and releases
  them behind the front. Ungenerated chunks are not generated (configurable). The band runs only as far as the terrain
  work (crater, the bands beyond it, the ground pass); entity zones past that reach the entities already loaded
  (slice 8d: loading the Tsar's outer 220 blocks of chunks in a few ticks stalled the server). A finished blast lets
  its chunks go 64 a tick.
- Jobs live in a per-level `SavedData` (`BlastManager`); ray fields are stored compactly in
  `data/groundburst/blasts/<id>.bin`. On world load, jobs resume and clients re-sync their visuals.
- Jobs never depend on a player being nearby; they finish while the server runs.

### 11.6 Server config (`groundburst-synced.toml`), with dramatic defaults
| Key | Default | Meaning |
|---|---|---|
| `blast.destroyBlocks` | true | master switch for terrain damage |
| `blast.radiusMultiplier` | 1.0 (0.1–2.0) | scales every profile |
| `blast.maxBlastRadius` | 720 (8–1 024) | hard cap on any zone radius (was `maxRadius`, 320: renamed in 8d so configs written before pick up the new default; the Tsar reaches 700) |
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
| `reactor.meltdowns` | true | a critical reactor melts down unless SCRAMmed; false: it SCRAMs at 900 °C (§7.1) |
| `singularity.maxBlocksRipped` | 20 000 | |
| `superweapons.announce`, `superweapons.maxRange`, `superweapons.chargeTimeMultiplier` | true, 4 096, 1.0 | |

Client config (`groundburst-client.toml`): `fx.quality` (low/medium/high/ultra, default high), `fx.flashStrength`
(1.0), `fx.cameraShake` (1.0), `fx.farClouds` (true), `hud.geigerCorner` (bottom right).

### 11.7 As built: the blast bench and the engine at the Tsar's size (slice 8d, 2026-10-22)
- **Bench** (`BlastBench` on the server thread, `BlastBenchClient`; `gradlew runBlastBench -Pbench=<profile>`
  `[-PbenchSeed=]`): a normal world (fixed seed), the ground round the spawn generated out past the blast's reach (the
  Tsar: 6,784 chunks, ~25 s on the user's PC) and left to unload, the blast set off at the spawn with the player
  watching from 360 blocks east and 60 up; every server tick recorded (its length, the blasts' share, the shock and
  terrain fronts, rays, chunks held and loaded, marked sections, rim, heap, client fps), a save like the autosave 30 s
  in; `run/bench/<profile>_<time>.csv` and a `.txt` summary. The window losing focus no longer pauses it (nor the fx
  shots).
- **First Tsar run, old engine:** 212 s, 20.6 million blocks changed; server ticks mean 20.3 ms, p99 39.9, max 284;
  31 ticks over 50 ms, 9 over 100. Causes: a whole generation of rays splitting at once (74, 146 ms); the last terrain
  rings; then the front racing from 480 to 700 in 8 ticks force-loading ~3,800 chunks only for entity damage (up to
  284 ms); and the crater rim, gathered as blocks were destroyed, held almost every destroyed block (20.4 million),
  with a 5.9 GB heap peak.
- **Changes:** the **shock front** (entities, and what clients draw) runs at the profile's speed while the terrain work
  keeps to its budget behind it, the crater forming under the dust for a minute or two after the shock has passed; the
  chunk band only to the terrain work; entity hits from the loaded entities (no box query) with lines of sight that
  never load a chunk, and an entity the shock finds wholly shielded where the terrain work has yet to reach looked at
  again once it has (its cover may be dug away); rays split one at a time as the front reaches them, inside the
  budgeted loop (`RayField.split`, each ray's split radius kept), the arrays grown straight to their limit past 200,000
  rays, compacted only once a quarter are dead, and a save no longer compacts them (it moved rays under a shell's
  cursor); the rim found by the clean-up (destroyed blocks next to blocks a ray reached and left: 0.73 million for the
  Tsar); chunks let go 64 a tick.
- **Second run:** 138 s, 22.0 million blocks; ticks mean 19.6 ms, p99 27.7, max 53.8 (the release of 440 tickets at
  once, since spread); 1 tick over 50 ms; heap peak 3.5 GB; the save 30 s in 123 ms (1.5 million rays); client 33 fps.
- **Third run**, chunks let go gradually: 128 s, 21.9 million blocks; ticks mean 18.5 ms, p99 25.2, max 39.2, none
  over 50 ms (the §28 target met); heap peak 4.4 GB; the save 30 s in 115 ms (an autosave during a Tsar's front costs
  about that, once); client 32 fps.

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

### 12.1 As built (slice 5c)
Package `dev.groundburst.client.fx`. `ClientBlasts` passes start / front / end to `Spectacle`, which keeps a `BlastFx`
per blast, one shared `Puffs` system and one `Debris` system, timed by its own client tick clock (it stops while the
game is paused). Visual presets (`FxPreset`) are named by the profile's `visual` (default: by crater size): small,
medium, large, nuke; sizes are multiples of the crater radius, counts are scaled by `fx.quality` (0.35 / 0.6 / 1 / 1.6).
- **Render types** (`FxRenderTypes`): two pipelines of our own on the vanilla emissive entity shader (texture x vertex
  colour, unlit), two-sided, no depth write, each with an OIT set: **glow** (additive, like lightning; with the
  shader's `OIT_ADDITIVE` switch) and **fire** (alpha-blended). Fire is needed because additive light washes out to
  white against a bright sky; the fireball's body is fire, its core glow. Custom GLSL shaders were not needed.
- **Flash:** a GUI layer above everything fills the screen warm white. Strength = preset x `flashStrength` x
  1/(1 + (distance/reach)^2/9) x line of sight (a client ray from the eye to the burst; 25% if blocked) x facing
  (0.35 + 0.65 x max(0, look . direction)); held 1.5 ticks, then fades over the preset's flash time. Small 0.12,
  medium 0.3, large 0.55, nuke 1.0.
- **Fireball:** a UV sphere (14 x 28) whose radius boils with value noise, grows as `1 - e^(-t / (0.08 life))` to
  `fireball x crater`, rises, and cools along a heat ramp (cream, yellow, orange, red, dull brown-red); limbs are
  darkened and thinner. Outer shell fire, core (0.7 r) glow. Inside it, glowing puffs boil outward (three in four are
  fire, the rest glow) and darken to soot; at 55% of its life dark smoke puffs take over from it. Sparks (glow, with
  gravity) are thrown out.
- **Shock front:** radius = the server's front, predicted forward between its 4-tick updates (capped 5 ticks ahead);
  an instant blast's front is drawn moving at 3 blocks a tick. Medium and up show a faint glow shell at that radius,
  fading as it nears the outer radius. All sizes kick up dust where the front sweeps the ground: rings of dust puffs
  at the ground height of each column, coloured from the ground's map colour (soil for grass and plants), every
  max(1.5, outer/40) blocks, up to 48 a ring.
- **Debris:** at the start the client samples blocks where the crater will be (still standing on the client: the
  start packet arrives before the block updates) and releases each when the front reaches it: a spinning cube 0.2-0.85
  blocks across wearing a random corner of the block's particle texture, tinted like the block (grass, leaves),
  thrown outward and up, hardest along the crater's strong spokes (the same streak noise and seed). It falls, bounces
  off ground and walls (restitution 0.3), comes to rest, lies 3.5 s and shrinks away. Lit by the world.
- **Smoke:** a plume of lit puffs rising off the crater for the preset's smoke time (front-loaded), drifting with a
  per-blast wind, growing and fading; plus the fireball's own smoke.
- **Camera shake:** trauma added when the sound arrives (distance / 17.15 ticks): preset x `cameraShake` /
  (1 + reaches^2 / 6); decays 0.035 a tick; yaw 5, pitch 4, roll 3 degrees x trauma^2 x smooth noise.
- **Sky and fog:** large (0.35) and nuke (0.75) blasts grey then warm the sky colour (render state, after vanilla
  extraction), fog colour (`ViewportEvent.ComputeFogColor`) and cloud colour toward smoky orange-brown, fading in over
  5 s, holding 40% of its time, falling off with distance (six reaches), lasting 1.5 / 5 minutes.
- A client that hears of a blast late (login, dimension change) skips what already passed (shake, dust, debris).
- Textures `textures/fx/puffs.png` (4x5 sheet of 128 px cells since 12.2: smoke, glow, spark, dust, cloudlets),
  `fireball.png`, `shock.png`, generated by
  `tools/assets/fx.py`. Review: `gradlew runFxShots` (`FxPhotographer`, `-PfxOnly=large` for one) screenshots each
  test profile at 10 moments; `tools/preview/fx_sheet.py` makes contact sheets.
- Not in 5c: heat distortion (no screen-space refraction yet), the afterimage blob, far rendering past render distance
  (`fx.farClouds`, with the mushroom cloud in 5d), Wilson ring (5d).

---

### 12.2 Spectacle v2 and the mushroom cloud (slice 5d, after the user's look at 5c)
The user found 5c "alright" and asked for much better effects, nukes above all looking like mushroom clouds.
Research: HBM's Nuclear Tech "Torex" cloud (client cloudlets advected by a 2D roller flow rotated round the axis, a
ground-fed stem, collar ring, condensation band, colour by distance to a cooling core, fog off so it shows from afar);
game VFX practice (many layered puffs, sprites shaded with a whole-cloud "sphere normal" and tinted by the sky, spiky
dirt jets to break up round fireballs, ground bursts throwing force upward in a cone); nuclear test footage (the
fireball rises and rolls into a toroid, the stem is sucked up behind it, a collar where cap meets stem, a brief Wilson
condensation cloud, a spreading base surge). Our own design from that:
- **Mushroom cloud** (`MushroomCloud`, nuke preset): cloudlets in three roles. *Cap*: born inside the fireball, each
  pulled to its own shell round the toroid's core circle and carried round it (up the inside, out over the top, down
  the outside) while the whole torus rises (height H = 2 craters, approached exponentially) and widens; the roll slows
  over minutes. *Stem*: fed from the ground under the burst, drawn inward and up into the cap's underside, where it
  joins the roll. *Collar and condensation*: a white ring under the cap, and a short-lived white shell round the
  fireball at 1-7 s. *Base ring* (2026-10-22, after the in-game look): the stem stands on the crater's floor (the start
  packet carries the crater's depth), and a ring of dust (380 cloudlets at full quality) rolls slowly outward round
  its foot, its lower half down in the crater bowl, so the cloud does not float over the hole it dug. Hot cloudlets glow by their distance to the core, cooling over a minute; the cloud lasts minutes,
  drifts with the wind and thins out.
- **Cloud lighting** for every smoke-like puff: shaded by a normal from the cloud's own shape (from the torus core
  circle, the stem axis, or a plume's centre) against the sun's direction, ambient from the sky colour, all scaled by
  daylight and by the world light where the puff is; drawn unlit with the colour computed on the CPU.
- **Far clouds**: the mushroom cloud's pipeline uses our own fragment shader without the render-distance fog (only
  environmental fog: water, lava, blindness), so it stands over the horizon from far away (`fx.farClouds`).
- **Conventional blasts**: dirt jets (soil streaks thrown upward in a cone along the crater's spokes, drawn stretched
  along their motion), a flare (a big soft additive glow for the first few ticks), a bright ground shock ring at the
  front, spiky fire puffs bursting out of the fireball along the spokes, and lit smoke.

As built (2026-10-21):
- `MushroomCloud` (nuke preset, 3000 ticks, halved from 6000 after the in-game look): ~900 cap, ~900 stem, ~260 collar and ~220 condensation cloudlets at high
  quality. Cap height H = clamp(2 craters, 40, 520) (520 since 8d: the Tsar's climbs to 500) reached as `1 - e^(-t/260)`; core ring radius from 0.55 fireball to
  0.42 H (`e^(-t/420)`); minor radius 0.55 R; roll one turn per 5.5 s slowing over a minute (`e^(-t/1200)`). A cap
  cloudlet moves each tick by the roll (perpendicular to its offset from the core circle, up on the inside), a pull
  of 0.035 a tick toward its own shell, the torus's climb and widening, and a little jitter. Stem cloudlets are fed
  from the ground (most in the first 15 s, then a trickle of 0.35 a tick until 70% of the cloud's life, so the stem
  stands until the cloud thins out) at the stem radius (0.3 R narrowing to 0.18 R, plus a skirt
  at the foot), rise at 0.22 + 1.2 x the cap's climb, and join the cap's roll at its underside. The collar rides under
  the cap from 2.5 s to 22 s; the Wilson shell spreads round the fireball for about 7 s. The nuclear fireball rides the
  cap. Late joiners get the cloud laid out as it would be (`prefill`), not replayed. No wind drift (2026-10-22, after
  the in-game looks: drifting at 0.015 blocks a tick, the column slid off its crater by up to 40 blocks; anchoring the
  stem's foot and letting only the cap drift still read as off-centre, and the user asked for no wind at all): the whole
  cloud stands over ground zero, and a nuke's crater smoke rises straight too (conventional blasts' smoke still drifts).
- **The cloud waits for its crater** (2026-10-23, after 9b, the user's request: the Tsar's cloud was gone long before its
  crater was dug): a mushroom cloud stands at full strength until the server's blast job is done (its `BlastPayloads.End`,
  sent once the crater, clean-up, rim and release are finished: minutes for a Tsar), and only then starts its 2.5-minute
  countdown (`MushroomCloud.release`; thinning out over the last 45 s, the stem fed until then). Should the end never
  reach a client, it waits a quarter of an hour at most. Cap cloudlets now last as long as the cloud; a late joiner
  always gets the cloud laid out while its blast is still running.
- Colour: cap reddish-brown greying over 40 s; glow `heat x (e^(-(d / 0.8 r)^2) + early boost)` with heat
  `e^(-t/550)`, added on top through the fire heat ramp; collar and shell white. Lighting: `FxLight` (sun direction
  from the sky state's sun angle, sun colour warmed by the sunrise/sunset glow, ambient = the sky colour greyed and
  dimmed, wrapped diffuse), cap shadowed toward its underside, stem toward its top.
- Render types: lit puffs and cloudlets share one depth-writing "cloud" type on one texture sheet, so they sort as one
  batch (separate sheets left hard seams where the stem met the base dust). Depth writing with the fringe cut out
  keeps vanilla's clouds (drawn later) from showing through as blocky patches. Every effect is submitted to the
  after-terrain phase (`FxRenderTypes.submit`), drawn after water as vanilla's translucent particles are (2026-10-22,
  after the in-game look: in classic transparency, blended custom geometry is drawn before the translucent terrain, so
  water behind any smoke failed the depth test against the smoke's depth and was not drawn: "smoke makes all water
  transparent"; with improved transparency both are the same OIT pass).
- Conventional upgrades: a flare (spark cell, 2.5-5 fireballs across, a few ticks); dirt jets (preset `jets`: 8 / 24 /
  50 / 60; each a chain of 4-6 streaked dust puffs on one ballistic path at 48-86 degrees, azimuths biased to the strong
  spokes, coloured from the ground); fire jets (40% as many, conventional only: a nuclear fireball is smooth); smoke and
  dust lit by `FxLight` and the world light; base surge (`surge`: dust lasts and spreads more, 2.5x for nukes).
- Reviewed with `runFxShots` scenes small, medium, large, nuclear (230 blocks, to 80 s), nuclear_close (130 blocks) and
  nuclear_night.

## 13. Sound system

- Every sound is made by `tools/sound`: synthesised (layered noise, filtered sweeps, convolution reverb, saturation) or,
  since the remaster (§13.1), from real recordings; each is **mono** and has a subtitle.
- **Distance-delayed sound:** clients compute `delay = distance / 17.15` ticks (343 m/s, 1 block = 1 m) and schedule
  the boom; the flash arrives first. As built (5b): bands are measured in the blast's reach (its damage radius, at
  least 8): near under 1.5 reaches, mid under 4, far under 12, very far under 40, silent beyond; volume 1.0 / 0.95 /
  0.85 / 0.7 falling a further third across each band. The sound plays 6 blocks from the listener in the blast's true
  direction. A sound whose moment passed more than 2 s before the client heard of the blast is skipped. Sets: small,
  medium, large (conventional recipe in three sizes) and nuke. Variant by distance band: **near** (< 0.5·r_damage: sharp crack + roar + debris),
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

### 13.1 The sound remaster: real recordings (2026-10-27)
The user asked for every sound to be more realistic, without recording or buying any, and chose the hybrid: free real
recordings wherever reality has the sound, physics for what recordings cannot give, synthesis only for what nothing real
sounds like. Blasts first; then launches and ordnance, then machines.
- **Sources.** Freesound recordings under CC0 or CC BY 4.0 only (no non-commercial licences: CurseForge pays its
  authors), US-government video (public domain; some extracted on Freesound already), NASA audio later for launches.
  Every recording is in `tools/sound/sources/ledger.json` (author, title, page, licence, what it is used for); the raw
  files stay out of git (`sources/raw/`), and `sources/fetch.py` downloads them again (Freesound's public HQ previews,
  Vorbis about 192 kbps: no account needed). `sources/credits.py` writes `assets/groundburst/sounds/CREDITS.txt` from the
  recordings the recipes actually cut from; the mod's metadata points at it.
- **Reading a recording without hearing it**: `tools/sound/survey.py` prints a recording's length,
  peak, clipped share and its loudest bangs (time, level, rise), and draws its waveform over a spectrogram with a time
  scale; `key@from-to` zooms in. Cuts are chosen from those sheets. `tools/sound/loudness.py` measures how loud and
  heavy sounds are (BS.1770 loudness of the loudest 400 ms and 3 s, the weight at 30-150 Hz, the bite at 1-5 kHz, how
  long a sound stays loud); `--ladder` checks every band's blast sets in order of size.
- **Processing** (`tools/sound/sources.py`): cuts cleaned of their background by spectral subtraction against a quiet
  stretch of the same recording (a camcorder's hiss, wind, a truck idling), faded on raised-cosine curves; varispeed (a
  recording played slower is a bigger event: lower and longer); air absorption over distance, frequency by frequency
  (ISO 9613-1, 15 °C, 70 % humidity: at 1 km, 4 dB at 1 kHz, 27 dB at 4 kHz, 95 dB at 8 kHz); the soft ground's dip in
  the low mids far off; the blast wave's pressure pulse (Friedlander) for the punch close up that a microphone loses;
  tails that darken as they die; convolution; layering by loudness; since round 3, loudness without buzz (`tame_lows`:
  the sub-bass under 28 Hz cut, as no speaker plays it yet it sets the peak, and the low band saturated on its own;
  `maximize`: a look-ahead limiter that lifts a blast's body under its peaks).
- **The blasts, round 1** (`tools/sound/blasts.py`). The four bands are, in metres, roughly: small 6 / 20 / 55 / 175,
  medium 15 / 50 / 140 / 440, large 45 / 150 / 420 / 1300, nuclear 150 / 500 / 1400 / 4400, Tsar 500 / 1700 / 4800 /
  15000 (from the blasts' damage radii and ClientBlasts' bands). Each band uses recordings whose own distance suits it:
  small, an oxyhydrogen detonation, a firecracker and a firework shell, debris of rock and gravel, a small charge heard
  500 m off through a forest; medium, 4 kg of TNT (landmine clearance), a factory demolished across a car park, a mortar
  on its range; large, a water tower demolished 90 m from the microphones with the city's echoes, a power-station chimney
  and its fall; nuclear and Tsar, the TNT and the water tower slowed to under half speed, close lightning's crackle, a
  building's collapse, then thunder slowed and carried kilometres. Debris lands 1 to 5 s after the bang, as debris
  thrown that high does. The old synthesized recipes stay in recipes.py to compare.
- **The blasts, round 2** (the user's ratings of round 1 on the Sound Lab). Better than the old: the small set and the
  medium near, far and very far; the large far liked as it was. Six were kept (the small mid, far and very far, the
  medium near and very far, the large far). Rated down: everything with thunder in it (the large near and very far, all
  eight nuclear and Tsar sounds) "sounds like lightning"; the chimney (the large mid) "way too weak"; the factory
  demolition (the medium mid) had clapping and the crowd's ambience in it; the medium far too weak; the small near "more
  like a firecracker than a grenade". So, 14 remade: no thunder or lightning anywhere (the thunder, lightning, water
  tower and factory cuts dropped); the large, nuclear and Tsar blasts lie on the old synthesized recipes' boom and roar
  (`recipes.blast_source`, `nuke_source`, `tsar_source`), whose weight the user preferred, with the slowed TNT, the
  pressure shove, the collapse kept low and the debris over them, and their further bands are the old bands with the new
  blast carried under them; the medium mid is the near TNT carried 50 m; a low whump (`blasts.thump`, a saturated
  falling sine) and a harder drive into a soft limiter (`sources.punch`) under the small near and the medium mid and far.
- **What the ratings taught** (for the sounds still to come): real recordings carry the texture (the crack, the debris,
  the open air's tail) but rarely the weight a game's blast needs, so the low end is built under them; thunder and
  lightning never stand in for a blast (they are recognized at once); a recording with people in it (voices, clapping)
  is no use however good its bang; a small charge needs body under its snap or it reads as a firework. Each round
  remakes only what was rated down and keeps the rest.
- **The blasts, round 3** (the ratings of round 2: 13 of 14 rated, most 4 or 5, both very far nuclear sounds 5). Changed:
  the medium near and mid swapped (the user: round 2's mid sounded like the near one), the near trimmed 1.5 dB to stay
  under the large near; the nuke near, "a bit weak in comparison to the near large blast", made louder, heavier and
  longer than it; the Tsar near, "still sounds a bit like lightning", rebuilt; the Tsar mid and far, "bit weak", given a
  deep boom and lifted. The game plays every set at one volume for a band (`ClientBlasts.BAND_VOLUME`), so a bigger
  blast must be bigger in its file: every band's sets now measure in order of size (the near ones' loudest 3 s, small to
  Tsar: -18.6, -11.1, -10.8, -8.0, -7.4 LUFS; the nuke and the Tsar equally loud at first, the Tsar deeper and twice as
  long). Thunder's marks, found in the Tsar's old bed: a roar swelling about once a second, a second clap 2 s after the
  first, a long tearing crack, a crackle through the roar, and nothing breaking. The rebuilt one (`blasts.steady_bed`,
  `nuclear_near`) has a steady roar and one short crack, wreckage from the first second (a building collapsing, rocks, a
  chimney falling, stones and gravel, landing for 8 s) and the blast wind (`blast_wind`: a gale rushing past,
  darkening). A near nuclear blast's crack is left sharp: a strong shock's front stays steep where air dulls ordinary
  sound. Bigger must measure bigger, and loudness comes from the body between about 100 Hz and 4 kHz, not from sub-bass.
- **The blasts are done** (2026-10-27): round 3 was rated 4 for all six, so every blast sound stands at 4 or 5. No
  variants: three takes of every blast sound, picked at random, were made and then dropped (the user: "Variants aren't
  needed"; the game already shifts each blast's pitch a few percent). One sound per set and band, as rated. Next:
  launches and ordnance, then machines.
- **Launches, ordnance and the bombs' effects** (2026-10-27; the user: "move onto the other sounds"). 27 sounds remade
  from 32 Freesound recordings, all CC0 (37 were found and downloaded with the user's OK; five went unused: a HIMARS
  video with an engine humming under it, an F-18 taken on a voice recorder 500 m off, a quiet firework candle, a second
  fuse and a short HIMARS clip), in `tools/sound/launches.py` and `ordnance.py`: a real air-raid siren (a test in
  Prague) for the countdown; NASA's Space Shuttle lighting and roaring for a missile's ignition and its engine loop; a
  rocket fired from a US Army vehicle for the cruise launch; a fighter's low pass, played faster, for re-entry; an
  electric gate's motor, hydraulic lifts, a forklift, a truck's diesel, a factory crane and heavy steel doors for the
  silo, the launcher and the superweapons' machinery (each keeping the moments its animation is timed to); small metal
  parts, a fuse, a mud splat, a road flare, sparklers, an extinguisher, a covered arming switch, scissors, a trap's snap,
  a mortar, a drill, falling bombs' whistle, high-voltage arcs, a Jacob's ladder, liquid nitrogen and ice for the
  ordnance and the EMP and cryo bombs. Left synthesized: electronics (the superweapons' warning and ready tones, the
  arming beeps, the timed bomb's beep, the detonator's radio squelch), a small turbojet's compressor whine, a few deep
  thumps. Each is brought to the loudness of the synthesized sound it replaces (`sources.loud_as`), whose in-game volume
  was tuned to it, so the game's balance holds. Turned down in survey: a jackhammer with voices in it (voices spoiled the
  round 1 factory), a "gas grenade" made by hissing into a microphone, a gate's beeper. New tools in `sources.py`:
  `take` (a module's own table of cuts), `glide` (pitch sliding as it plays), `seamless` (loops), `loud_as`.
- **Launches and ordnance, round 2** (the user's ratings of the 27): 21 rated 4 or 5 (the cruise launch "Perfect", the
  wire snip 5). Rated down: the missile launch, which "more sounds like a missile in flight rather than a launch" (the
  Shuttle's engines starting, a dip, the roar building); the re-entry, a fighter's pass that "just sounds like a jet fly
  over" (1); the stage separation, "something being dropped in a tunnel" (its clang and echo); the grenade pin, "the
  click of a pen"; the bomb whistle, which sounded "like multiple bombs" (the recording has several); the bunker
  buster's drill, "keep the original"; the EMP's whine, to be "more subtle". Round 2: where the user preferred the old
  (the launch, the re-entry, the pin, the whistle), the old synthesized sound is the base and the recordings lie under
  it (`launches.old`, `ordnance.old`), as the big blasts' round 2; the stage separation in open sky (a sharp crack, the
  next stage lighting, no ring, no echo); the drill back to the synthesized original; the EMP's whine 6 dB down. What it
  taught: a real recording of a recognizable thing reads as that thing (a jet stays a jet, played faster or not); where
  the old sound has the game's feel the user likes (full power from the first instant, one clean whistle), recordings
  belong under it, not instead of it.
- **Launches and ordnance, round 3**: round 2's launch, re-entry, stage separation and pin rated 4. The EMP's whine
  removed (the user: "the electrical sound is good on its own"). The falling bomb's whistle back to the synthesized
  original: the recording of falling bombs carries the other bombs' whistles in its rushing air, and no filter takes
  them all out ("I can still hear the other whistles"). Every launch and ordnance sound now stands at 4 or 5, or is the
  original the user chose (the drill, the whistle); the EMP's round 3 awaits its rating. The machines next.
- **Machines** (`tools/sound/machines.py`; the EMP's round 3 passed: "The EMP is fine"): every machine's loop, the
  Assembly Bench's finishing clank, the Fission Reactor's alarm and the Geiger counter's clicks (26 sounds) from
  recordings of the machines or their nearest kin, 23 more CC0 recordings (24 fetched; a fire alarm lost to a factory
  alarm): a single-cylinder engine, a stone crusher, high-voltage arcs and arc welding, a hydraulic press and a 20-ton
  punch press, a transformer's hum, an air compressor, a paint mixer, a water pump, a CNC machine's stepper motors, a
  washing machine's spin (the centrifuge's drum), a diesel generator, a shower, the pump room of a nuclear power plant,
  a scroll vacuum pump, hoist chains, a ratchet, an impact wrench, a factory's warning alarm, a civil-defence Geiger
  counter. Two kinds of loop: a steady machine's is 4 s of its recording (twice the old 2 s, so a real recording's
  repeat does not show); a machine timed to its model's animation keeps its 2 s cycle, real events at the moments the
  model shows them (the press's blow at 1.0 s, the fabricator's gantry, the canner's nozzle, the bench's hoist). A loop
  is built in `machines.Cycle`: the recordings under the whole of it closed into a seamless loop first, then its
  moments and any old synthesized loop added, wrapping round the end (so a sound at 0 s is neither swallowed by the
  crossfade nor doubled, and a periodic old loop is never crossfaded with itself, which had made a 3 dB bump). Each
  loop is levelled to the old one's loudness as it plays (repeated), peaks limited over three copies so the seam stays
  closed: all 26 within 0.4 dB of the old. Where nothing real has the sound, the old synthesized one stays with
  recordings under it: the centrifuge's rotor whine, the diesel's knock (in step with the pistons), the Fusion
  Reactor's magnet hum and plasma roar, the accelerator's magnet buzz and RF tone. The Exotic Matter Fabricator, the
  Singularity Reactor and its collapse stay synthesized. The Sound Lab plays the loops 8 s over, as a machine runs, and
  the clicks as the Geiger counter will (§21.3: random, rising with the dose). Awaiting the user's ratings.
- **Machines, round 2** (the user's ratings of the 23 cards: 8 at 5, 6 at 4). Five notes ("a sound in the middle",
  "loop before the drop") had one cause: every 4 s loop faded out and back in at its seam, 6 to 15 dB, because
  `sources.cut` fades a cut's last quarter by default and the loop read into it; the seam check had compared samples,
  not loudness. Now a steady loop's cuts are longer than it and faded only at their very end; its length is fitted
  (4 ± 0.25 s) to where the sound after its end best matches the sound after its start (the compressor's pistons, the
  pump's pulse and the plant's hum stay in step across it), the two joined on a crossfade whose curve holds the level
  whatever their likeness (`machines.join`); `machine_set` fails a steady loop that dips at its seam. Also, from the
  notes: the Ore Crusher and the RTG back to the synthesized originals ("just keep the original please, I like it");
  the centrifuge's rotor whine lower (720 Hz, was 900) and about 5 dB softer, its third harmonic gone ("so it doesn't
  hurt your ears as much"); the reactor's alarm holds the alarm's next buzz steady about 10 dB down between blasts
  (2.25 s, so no gap opens when the server's 2 s runs a tick late; "a little jarring with the constant on and off");
  the Battery Charger calm, a soft hum and a gentle tick, no coil whine, 5 dB under the old ("you don't want as much
  realism so it's not as constantly annoying"); the Canning Machine on the old cycle the user preferred, a real fire
  extinguisher's discharge driving the fill and ringing higher as the canister fills (round 1's pour was "a light
  trickle rather than a pressurised filling"); the Particle Accelerator on the old loop with only a steady real hum
  under it (round 1's scroll vacuum pump swelled twice more in each 2 s: "more than one particle group"). The timed
  loops rated well are bit-identical to round 1; the arc furnace and separator only lost the dip.
- **The Sound Lab** (a review web page): every sound before and
  remade (every take so far: before, round 1, round 2), side by side with waveforms, a realism rating (1 to 5) of
  the newest, which is best, and a note. Ratings are kept in the
  page's database (`verdicts/<set>.<band>@r<round>`); they are read to plan the next round. The user's ears decide.

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

### 14.1 As built: thrown explosives (Phase 6 slice 6a)
- One entity, `groundburst:thrown_explosive` (`ThrownExplosive`), for every thrown kind (`GrenadeType`), told apart
  by the item it carries. Own flight: gravity 0.045 a tick, drag 0.985 (0.8 in water), hit-tested along each step.
  On a hit it **bounces** (speed into the surface x 0.35 comes back out, speed along it x 0.7; at rest below 0.063
  blocks a tick on a floor), **sticks** (to the block face, or riding a creature at its offset), or **goes off**
  (impact). The fuse counts from the throw. Thrown at 0.9-1.1 blocks a tick, 0.5 s cooldown, stacks of 16.
- Dynamite (3 s, bounce; crater 2.5), Frag Grenade (4 s, bounce; crater 2, weak, plus 24 `Shrapnel` projectiles on a
  Fibonacci sphere flattened 30% toward the horizontal, 1.8-2.4 blocks a tick, 6 damage `groundburst:shrapnel`
  (a projectile damage type), life 14 ticks, never saved), Sticky Grenade (4 s, stick; crater 3), Incendiary Grenade
  (impact; crater 1 plus fire on 85% of the floor within 4 blocks, found by searching down from the burst so a roof
  does not count, and everything alive there burning 8 s), Smoke Grenade (2 s, bounce; then smokes 30 s: the cloud
  billows to radius 5 over 3 s; every 10 ticks mobs in it, or whose target is in it, lose their target).
- Client: `GrenadeRenderer` draws the 3D item model turned to the direction of flight and tumbling at 45 degrees per
  block a tick of speed; `ShrapnelRenderer` an additive streak; the smoke cloud is lit puffs in the Spectacle; the
  dynamite fuse spits flame, smoke and sparks.
- Models: 3D item models (`tools/assets/grenades.py`): rounded "plus" prisms (core box plus two side boxes, never
  overlapping); sounds pin, three bounces, dynamite strike and fizz, sticky slap, smoke hiss (`ordnance_sounds`).
  Recipes: dynamite x2 (paper, gunpowder, string), frag x2 (iron nuggets, steel plate, gunpowder), sticky (frag +
  slime ball), incendiary x2 (napalm canister, steel plate, gunpowder), smoke x2 (steel plate, saltpeter x2,
  charcoal). Field Manual: new Ordnance chapter.

### 14.2 As built: grenade feedback and chemical grenades (slice 6a, second pass)
- Dynamite: bigger sticks (2.5 x 10), a wire out of each stick twisted together over the middle into one fuse; blast
  crater 3.5 (was 2.5). The fuse fizz is bound to the thrown entity, so it follows the dynamite. No bounce sounds; the
  pin is a single click.
- Smoke (and gas) hides: `LivingVisibilityEvent` cuts visibility to a tenth for anyone in a cloud, or looking out of one.
- Chemicals: **Salt** (Chemical Plant: 4 dried kelp + 1 000 mB water -> 2), **Chlorine** gas (2 salt + 1 000 mB water;
  yellow-green, toxic, its own gas canister), **Toxin** (a made-up glowing green liquid: 1 000 mB chlorine + 500 mB
  sulfuric acid + 2 spider eyes + a poisonous potato; thick, toxic, canister and bucket).
- `Toxins.expose(level, entity, damage, poisonTicks, amplifier)`: damage `groundburst:toxin` (bypasses armour, no
  knockback) plus Poison, both cut by `GBArmor.chemicalProtection` (each suit piece's share: hood 20%, coat 35%,
  trousers 30%, boots 15%; a full set of either suit keeps out all of it). Used by the gas grenade, toxin sludge, toxic
  liquids (Toxin) and toxic gas clouds (chlorine, UF6). Suit tooltips give radiation and chemical figures.
- **Poison Gas Grenade** (2 s, bounce): the smoke grenade's cloud in chlorine for 25 s, lower and yellow-green; every
  10 ticks everything within the cloud takes 1 toxin damage and Poison II for 5 s. It blinds and hides like smoke.
- **Toxin Grenade** (impact): no blast; splashes **Toxic Sludge** on the floor within 3.5 blocks (solid in the middle,
  spattered at the edge; the floor search is the incendiary's), 3 damage and 8 s of Poison II to everything in the
  splash. Sludge: a 1 px film, glowing (light 7 down to 4), fuming, no collision, replaceable, gone with its floor;
  every 10 ticks 1.5 toxin damage and 4 s of Poison II to whatever stands in it; dries in four stages of 30-40 s.
- Recipes: gas grenade x2 (chlorine canister, steel plate, gunpowder), toxin grenade x2 (toxin canister, steel plate,
  gunpowder). Field Manual: "Chemical grenades" entry; the hazmat entry covers chemical protection.
- The toxin grenade is a **glass vial** (translucent model: green toxin inside clear glass, black cap, brass impact
  fuze), tossed with the splash-potion throw sound rather than a pin.
- **Molotov Cocktail** (impact, thrown at 1.0): no blast; the incendiary's fire carpet at radius 3 (everything there
  burns 8 s), glass break and whoosh, flame burst; the rag burns in flight and fizzes like the dynamite fuse. Recipe x2:
  synthetic fuel canister, 2 glass bottles, 2 string. Model: brown glass bottle of amber fuel, rag in the neck.
- Translucent item models put their opaque parts first: item quads are drawn in order with depth writes, so glass drawn
  first hides what is inside it. Glass columns are built with no inner faces (`glass_prism`).

### 14.3 As built: placed charges and fuzes (Phase 6 slice 6b)
- One block class (`ChargeBlock`, with `C4` and `Thermite` subclasses for their extra properties) and one block entity
  (`ChargeBlockEntity`, `groundburst:charge`) for every placed charge (`ChargeKind`). `FACING` points out of the face
  it is stuck to (the timed bomb stands on the floor and faces its front); `ARMED` picks the model whose red lamp
  blinks (a two-frame animated texture, the lamp element emitting light 15). Shapes are the floor shape turned the way
  the blockstate turns the model. A charge pops off if what it is stuck to goes (a burning thermite pot stays).
- Fuzes: **Remote** (frequency 0-9999) or **Timer** (1-600 s); the timed bomb is always Timer. Set with an empty-hand
  right-click (a client screen, `ChargeScreens.Settings`; locked while armed). **Arming** is a sneak-use of the Arming
  Key; arming a Timer fuze starts its countdown (beep every second; the timed bomb's quickens to every half second
  under 10 s and every 4 ticks under 3 s). Sneak-use again to make it safe, except a counting timed bomb. Redstone
  sets off any armed charge. The **Remote Detonator** (`frequency` data component) sets off every armed Remote charge
  on its frequency within 256 blocks; sneak-use on a charge copies its frequency, in the air opens a frequency screen.
  Loaded charges are tracked per dimension (`Charges`), registered when placed or loaded.
- **Sympathetic detonation**: a charge destroyed by our blast engine (`BlastJob.destroy` hook) or caught in a vanilla
  explosion (`onExplosionHit`) goes off 2-5 ticks later, armed or not, from a record of what it was (thermite only
  burns when lit, so a blast just scatters it).
- **C4** (`c4` profile: core 2, crater 4.5, lethal 5, damage 11): stuck to any face, the blast just inside the face;
  using C4 on placed C4 stacks up to four (`candles` 1-4), scaling every radius by n^(1/3); drops as many as stacked.
- **Breaching Charge**: removes a 3x3 hole 3 deep into what it is on (anything below resistance 1200, no drops, break
  particles), a small `breaching_charge` pop (crater 0.6) on this side, and everything alive in or within 4 past the
  hole takes 6 blast damage and is shoved through it.
- **Thermite Charge**: lit (LIT, glowing light 15, sparks, lava drips, a roar every second), 1 s of flare, then melts
  one block every 6 ticks along a 1x1 shaft into what it is on, 8 deep, stopping at resistance 50+ (obsidian),
  unbreakable blocks or water (steam); the last melted block becomes magma (slag), then the pot is gone. Anything
  alive next to it burns.
- **Timed Bomb** (`timed_bomb` profile: core 2.5, crater 6, lethal 6, damage 14; 3.5 / 9 / 9 / 20 after feedback): a `ChargeRenderer` (`TimedBombRenderer` until 6c) draws the
  time on its display (red counting, dim green safe). Defusing: right-click while it counts opens four wires (red,
  blue, yellow, green; random each arming): one defuses it (safe, kept), one sets it off, the other two halve the time
  left (at least 3 s). Breaking it while it counts sets it off.
- Recipes: C4 x2 (2 plastic explosive, basic circuit, copper wire), Breaching (plastic explosive, steel plate, 2
  string, basic circuit), Thermite (terracotta, 2 iron dust, gunpowder, redstone), Timed Bomb (3 dynamite, 2 steel
  plate, clock, basic circuit), Arming Key (steel ingot, iron nugget, red dye), Remote Detonator (basic circuit, steel
  plate, steel rod, redstone, stone button). Sounds: arm, disarm, beep, thermite roar, detonator, wire snip.
- **After the in-game look (feedback pass):** no Arming Key. Every charge's screen sets its frequency (all charges have
  one) and, where it has a timer, At once or Timer and the time, and has **Arm** / **Make safe** buttons (the rows
  shown are only those the charge has). Arming only makes it live: a Remote Detonator on its frequency, or redstone,
  then sets it off, or starts its timer if it is on one (the timed bomb is always on its timer). A safe charge ignores
  both. C4 has no timer (that is the timed bomb's job). Timed bomb blast: core 3.5, crater 9, lethal 9, damage 20.
  Blast debris no longer samples the charge block itself (still standing on clients when the blast packet arrives).

### 14.4 As built: mines (Phase 6 slice 6c)
- Mines are charges (`ChargeKind` LANDMINE, BOUNCING_MINE, CLAYMORE, with a `Sensor`): placed on the floor, safe,
  armed from the same screen (the claymore's also sets its frequency). Arming starts a 3 s grace (a quiet beep a
  second) before the sensor is live; redstone and other blasts still set them off; mines without a frequency ignore
  detonators. Sensors are polled every other tick on the server.
- **Landmine** (`landmine` profile: crater 2.5, lethal 3, damage 7): anything alive within its plate (the middle
  0.7 x 0.4 of its block) sets it off with a click; the blast is 0.35 below its centre. Modelled sunk into the ground
  (elements below y 0), so only the plate shows in the world.
- **Bouncing Mine**: stepped on, it clicks, and its canister (a `ThrownExplosive` carrying the mine's item, type
  BOUNCING_MINE) pops up at 0.36 blocks a tick and bursts 7 ticks later about 1.2 blocks up: `bouncing_mine` profile
  (crater 1, lethal 4, damage 9) and 30 shrapnel on a sphere.
- **Claymore**: placed facing where the player looks; armed and live, a laser from its lens (6.5 px up) runs up to 6
  blocks ahead until something solid stops it; anything alive crossing it fires it: a small `claymore` blast (crater
  0.5) at its face and 60 shrapnel within 30 degrees either side of ahead, -3 to +9 degrees up, at 0.85-1.05 blocks a
  tick (about 12-15 blocks). The laser is drawn by `ChargeRenderer` as a thin translucent red tube.
- Recipes: Landmine x2 (steel plate, stone pressure plate, 2 gunpowder), Bouncing Mine (frag grenade, steel plate,
  stone pressure plate, gunpowder), Claymore (plastic explosive, 4 iron nuggets, steel plate, basic circuit). Sounds:
  the plate's click, the canister's launch.
- **After the in-game look (feedback pass):** no arming grace: a mine is live the moment it is armed. The claymore's
  blast (`claymore` profile) now has no entity damage or knockback, only the bang, flash and a tiny crater, so only
  its forward cone of fragments hurts. Its model was then made thin and curved like the real thing (about 6 : 2.3 : 1
  wide, high, deep): the face section bows forward and the wings sit 0.7 px back, keeping their inner faces so no gap
  shows behind; the lens (and the laser) is 4.5 px up. Toxins: gas (the gas grenade, toxic gas clouds) gives Poison III; toxin (the splash, the sludge, the liquid)
  gives Poison IV and 10 s of nausea. The arming messages' language keys moved to `ChargeBlock` (the packet class
  was never loaded by data generation, so they had no English text).
- **Claymore, second pass:** no blast engine at all: a vanilla explosion flash, smoke, flames and the small blast
  sound at its face (the `claymore` profile is gone). Its 60 fragments are spread evenly across the 60 degree cone
  (yaw stepped one degree apart with a little jitter, cycled through 0-4 degrees up), deal 7 each (`Shrapnel.claymore()`)
  and reset the target's damage cooldown so every hit counts: a bare 20-health target 5 blocks ahead takes about six
  and dies (their reach is about 10 blocks). Frag grenade and bouncing mine fragments are unchanged.

### 14.5 As built: bombs (Phase 6 slice 6d)
- Bombs are charges (`ChargeKind.bomb`) placed as `BombBlock`s lying on their side, nose the way the player looks
  (FACING). Fuzes as the placed charges: a frequency, At once or Timer, armed from the screen; redstone too. **Impact
  fuze**: a bomb is a gravity block (`Fallable`): unsupported, it falls, its fuze settings carried in the falling
  block's data (vanilla does not copy them); armed, it goes off when it lands (`onLand`) or when a projectile hits it.
  Safe, it lands whole. Sympathetic detonation as before. Direct recipes until the Phase 7 Assembly Bench.
- Models (`tools/assets/bombs.py`): horizontal "plus" prisms stepped down to nose and tail, four fins, lugs, bands; big
  ones run past their block (MOAB 44 px long), the collision box stays inside it; display transforms scale them down.
- **HE Bomb** (`he_bomb`: core 2.5, crater 6, lethal 7, damage 14), **Heavy Bomb** (`heavy_bomb`: 5 / 12 / 14 / 28,
  staged front), **MOAB** (`moab`: core 10, crater 28, structural 40, lethal 40, damage 70, large sound and visual set).
- **Thermobaric Bomb**: a fuel cloud (particles, a rushing roar), lit 12 ticks later (`thermobaric_bomb`: crater 12 but
  shallow, structural 24, fire 16, lethal 24, damage 40), plus up to 20 more damage to anything within 24 with no open
  sky above its eyes.
- **Cluster Bomb**: a small burst (`cluster_bomb`) and 24 bomblets (`ThrownExplosive`, `GrenadeType.BOMBLET`, impact,
  `cluster_bomblet` profile: crater 2.5) thrown out at 0.2-0.65 blocks a tick and 0.55-0.8 up (most land 6-18 blocks
  away); 5% are duds, which land as armed landmines. The Cluster Bomblet item can be thrown too.
- **Bunker Buster**: set off where it stands, just `bunker_buster` (core 3.5, crater 9). `Charges.penetrate` (for
  delivery at speed, Phase 9) bores straight down 2 blocks a tick, 40 blocks of depth (obsidian-hard blocks cost 10;
  bedrock or the world's floor stops it), then goes off where it stopped.
- **Napalm Bomb**: `napalm_bomb` (crater 2, fire 6) and **Napalm Fire** over the floor in an ellipse 30 x 12 centred
  on it, long along its body (it is symmetrical; 90% near the middle, thinner at the edge): 30 s in four stages, not put out by rain, ignites (10 s) and
  burns (2 every half second) anything in it, now and then lights flammable blocks beside it; drawn with vanilla's fire.
- `Charges.bombScale` (1 in play) scales every bomb's reach for GameTests, keeping them in their plot.
- **Feel pass (after the in-game look):** bombs get their own visual sets (`FxPreset`): `he` (a medium blast with a
  hard shock ring, more shake and debris), `heavy` (a bigger fireball, a debris column, a smoke pall), `thermobaric`
  (a fireball 1.5 craters wide rolling low, embers, a scorched sky) and `moab` (a weak flash, a great dust ring and a
  grey dust mushroom that hangs for 100 s: `MushroomCloud` gained a dusty mode, barely glowing). A bomb that falls 8
  blocks or more whistles all the way down (`bomb.whistle`, bound to the falling block) and lands harder: its reach
  grows with the fall, up to 30% for 24 blocks.

## 15. Nuclear weapons

Rescaled after the in-game look at slice 8a (2026-10-22): the smallest nuclear weapon is half as big again as the
Massive Ordnance Warhead (crater 28), and the ladder climbs from there to a truly massive Tsar. (The mini-nuke is a
Phase 13 launcher round, the one exception.) **Delivery:** the warheads marked *missile* ride a Strategic missile (the
biggest missile there is: XL body, Liquid-fuel Stage); anything bigger is a *whole weapon*, built complete in the
Strategic Assembly Hall (§15.2) and set straight on the Launch Complex.

| Weapon | Class | Delivery | Yield (flavour) | Core | Crater (depth) | Structural | Fire | Lethal | Damage | Radiation (half-life) | Look |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **Mini-nuke** (launcher round) | — | gun (Phase 13) | 10 t | 4 | 12 (0.5) | 18 | 20 | 20 | 34 | 30 (0.5 day) | small finned spherical shell with yellow trefoil |
| **Tactical Nuclear Warhead** | XL | missile | 300 t | 12 | 42 (0.45) | 60 | 70 | 60 | 105 | 90 (1 d) | squat olive warhead, dark cap, yellow-over-black band, trefoil (built, 8a) |
| **Fission Warhead** | XL | missile | 15 kt | 22 | 75 (0.4) | 110 | 130 | 120 | 200 | 180 (2 d) | fat ochre warhead, dark nose, red band, trefoil (built, 8a) |
| **Boosted Fission Warhead** | XL | missile | 45 kt | 28 | 95 (0.4) | 140 | 165 | 150 | 250 | 220 (2 d) | sleek grey body under a sharper dark nose, two cyan boost-gas bands, trefoil (built, 8b) |
| **Neutron Warhead** | XL | missile | 1 kt | 4 | 10 | 16 | 12 | **180 (living only)** | 26 (blast) | 140 (0.25 d) | slim silver warhead with three cyan rings, neutron-reflector studs (built, 8b) |
| **Dirty Warhead** | S | missile | — | 1 | 6 | 9 | 5 | 10 | 16 | 90, strength 0.8 (5 d) | rusty drum with a flat cap, yellow tape, trefoil (built, 8b) |
| **Thermonuclear H-Bomb** | XXL | whole weapon (intercontinental missile) | 1.2 Mt | 45 | 150 (0.35) | 220 | 260 | 260 | 420 | 360 (3 d) | the Thermonuclear Warhead: fat olive drab, blunt dark nose, two yellow bands, trefoil (built, 8c) |
| **Cobalt (Salted) Bomb** | XXL | whole weapon (intercontinental missile) | 400 kt | 18 | 70 | 105 | 110 | 110 | 180 | **300 round the burst, strung ~1,000 downwind (30 d)** | the Cobalt Warhead: blue-black, cobalt-blue jacket bands and plate seams, trefoil (built, 8c) |
| **Tsar Mega-bomb** | XXL | whole weapon (intercontinental missile) | 50 Mt | 80 | 250 (0.3) | 380 | 450 | 450 | 700 | 600 (3 d) | a fat dark-green bomb on four tail fins round its canvas parachute pack, a rounded nose, white stencils ("50", a trefoil, stencil lines), four glowing cooling vents; on its missile it rides bare on the third stage, wider than any stage (built, 8d) |
| **MIRV** | — | whole weapon (Phase 9 console) | — | per RV | | | | | | | bus with 4/8 conical re-entry vehicles |

The Tsar's reach was beyond the explosion engine's old limit (`blast.maxRadius` 320, max 512): slice 8d benchmarked it
and raised the limit (`blast.maxBlastRadius` 720, max 1,024; §11.7).

Neutron bomb specifics: lethal radiation dose to every living entity in r 180 with line attenuation through blocks
(water/lead/concrete/obsidian attenuate strongly — a deep enough bunker survives); crops, saplings, grass and leaves
in r 120 die (grass → dead grass, crops → withered); blocks otherwise untouched. As built (8b): see §15.1.

MIRV capacity: the bus holds **8 warhead units** (L) or **16** (XL). Tactical = 1, Fission = 2, Boosted = 3,
H-bomb = 4, decoy RV = 0.5. Mixed loads allowed. Re-entry vehicles spread over a footprint (point / ring / line /
grid, radius 10–150) chosen at the launch console.

Each tier escalates the spectacle: flash range and strength, fireball size, cloud height, number of puffs, shake
range, sound class (`nuke` vs `tsar`), and sky tint duration.

### 15.1 As built: Phase 8 plan and slice 8a (2026-10-22)
- **Plan** (the user chose "own launcher first" and "contamination zones now"): nuclear warheads are bench-assembled
  like the others but only a **Launch Complex** fires them (built first, in 8a); until Phase 12's radiation field,
  their fallout is the meltdown's **contamination zones** (Radiation Sickness, hazmat suits protect). After the 8a
  in-game look: nuclear warheads ride their own missile class, the **Strategic** missile, the biggest a missile gets;
  anything bigger is a whole weapon from a new multiblock. Slices: **8a** Launch Complex, Strategic missile, Tactical
  Nuclear and Fission Warheads, fizzles; **8b** Boosted Fission, Neutron and Dirty Warheads (missile-borne); **8c** the
  Strategic Assembly Hall (§15.2) with the H-bomb and the Cobalt Bomb as whole weapons; **8d** the Tsar (and the engine
  limits it needs). MIRVs stay with Phase 9's launch console.
- **Warheads** (`ChargeKind.nuclear()`): **Tactical Nuclear Warhead** (S, "300 t": crater 42, wrecks 60, fire 70,
  lethal 60, injures 105; first built at crater 16, then scaled to 1.5x the MOW after the in-game look) from a **Tactical Fission Core** (3 plutonium nuggets, an HE Charge, a steel plate, a military
  circuit); **Fission Warhead** (M, "15 kt": crater 75, wrecks 110, fire 130, lethal 120, injures 200; first 40) from a **Fission
  Core** (a plutonium ingot, 3 HE Charges, a depleted uranium ingot, a lead plate, a military circuit). Blast profiles
  with the nuclear terrain table and the `nuke` look and sound (mushroom cloud sized by the crater). Models: a squat
  olive body with a dark cap and a yellow-over-black band; a fat ochre body with a dark nose and a red band; both with a
  radiation trefoil on the front. Missiles carry them (§4.5).
- **Delivery only.** A missile's impact or burst gives the full yield (`Charges.nuclear`) and a contamination zone
  (tactical 90 blocks for a day, fission 180 blocks for two days; an airburst from a barometric fuze leaves half the
  strength). Set off any other way (dropped, shot, caught in a blast) a nuclear warhead **fizzles**
  (`nuclear_fizzle`: crater 3) and leaves a weaker zone 16 blocks round for half a day (strength 0.6).
- **Strategic missile** (`SizeClass.XL`, the Strategic Missile Body): the only body for a nuclear warhead, and it takes
  nothing else (`MissileAssembly.fit`); only the Liquid-fuel Stage fits it (2500 blocks); only a Launch Complex fires it
  (`MissileSpec.needsComplex`). Its own model (`launch.py` `strategic_parts()`, custom model data `strategic_<warhead>`):
  a wide first stage in black-and-white roll quadrants with four big swept black fins and four nozzles, a riveted
  interstage, a slimmer white second stage with a data plate, its warhead (§4.5); about 11 blocks on the complex (its
  own bone, `strategic`, the warhead at `swarhead`) and in flight (lifted by `STRATEGIC_TAIL`, as item models stop at
  y -16).
- **Class XL** (after the in-game look: the warheads' tooltips said S and M): both nuclear cores are class XL
  (Strategic) and take the new lead-lined **Strategic Warhead Casing** (a Heavy casing, lead and dense plates), so the
  warhead's class matches the Strategic missile that carries it.
- **Stats:** design yields ("300 t", "15 kt") instead of the TNT formula, Fallout Radius and Fallout Lasts rows and a
  note that only a Launch Complex fires it (the "Blast Radius" header went, so a fission warhead's list still fits the
  screen at GUI scale 3). Missile tooltip: "Nuclear: fires only from a Launch Complex".
- **Slice 8b** (2026-10-22): three more warheads, each from its own core (models in `warheads.py` and
  `components.py`, missile noses and the launchers' nose bones regenerated by `launch.py`):
  - **Boosted Fission Warhead** (XL, "45 kt": crater 95, wrecks 140, fire 165, lethal 150, injures 250; fallout 220
    blocks for two days) from a **Boosted Fission Core** (a Fission Core, a tritium and a deuterium canister, a military
    circuit). A grey body under a sharper dark nose, two cyan bands, a trefoil.
  - **Neutron Warhead** (XL, "1 kt": crater 10, wrecks 16) from a **Neutron Core** (a Tactical Fission Core, two
    tritium canisters, a tungsten carbide ingot, a military circuit). Its **neutron burst** (`NeutronBurst`, called from
    `Charges.nuclear`): every living thing but the undead and creative players out to 180 blocks takes a dose
    (1 - (d/180)²) · e^(-Σ) · (1 - ½ · suit protection), Σ summed over the blocks on the line from the burst (sampled
    every half block): 0.25 a full solid block, 0.4 a liquid, 1.0 obsidian, crying obsidian, reinforced deepslate,
    concrete or a lead block, 0.02 anything thinner. 0.3 or more kills (radiation damage); from 0.03 Radiation Sickness
    for a minute. So two blocks of concrete or lead (five of stone) save you even beside it; suits count half, as
    neutrons go through cloth. Plants in the open wither out to 120 blocks, 1500 columns a tick, each column worked down
    from its highest block: leaves, crops, vines, grass, ferns, flowers, berry bushes and moss carpets go, saplings
    become dead bushes, grass, mycelium, podzol and moss blocks become coarse dirt (vanilla has no dead grass block),
    farmland dirt; the first other block (ground, a trunk, a roof) stops the column, so plants under a roof are
    spared. Fallout only 140 blocks for a quarter of a day.
  - **Dirty Warhead** (class S, Light casing) from a **Dirty Payload** (four Nuclear Waste, an HE Charge, a lead
    plate): radiological, not nuclear (`ChargeKind.radiological()`), so it fits any missile body but the Strategic
    one, flies from any silo, and goes off however it is set off (no fizzle): a small HE blast (crater 6), smoke and ash,
    and a contamination zone 90 blocks round at strength 0.8 for five days (the plan's drifting cloud waits for
    Phase 12's fallout plumes).
  - **Fallout you can see** (2026-10-22, the user's request; a stand-in until Phase 12's fallout plumes): the server
    sends every zone in a level to its players (`ContaminationPayloads`, whole, within a second of a zone beginning or
    ending, and on arrival in the level; protocol 10). `FalloutFx` draws a broken layer of cloud 50 to 80 blocks over
    each zone (dark grey, grey, olive and green smoke puffs, about r²/170 of them up to 300, 15 to 47 blocks across and
    drawn wider than tall), drifting at 0.03 blocks a tick on the zone's own wind and wrapping round inside it, fading
    out toward the edge so the wrap is never seen, and thinning as the zone fades (opacity 0.6 x √(strength left):
    fairly opaque, as the cloud shader cuts a faint puff's fringe away and leaves hard outlines). Laid out from a
    seed and moved by the level's clock, so every player sees the same clouds; drawn with the far-cloud pipeline, so
    they show beyond the render distance. Inside a zone the sky, fog and vanilla clouds turn toward a sickly
    yellow-green, by 0.4 x √(the contamination level where the camera is): strongest at a fresh zone's middle,
    nothing at its edge.
  - Stats: design yields "45 kt" and "1 kt"; fallout rows for every radiological warhead, "6 hours" under a day; the
    neutron burst as two rows, Neutrons Kill and Plants Wither (as a note it ran past the bottom of the panel at GUI
    scale 3); the dirty warhead's note is one line ("Scatters nuclear waste"), as its missile's list is long. The
    Field Manual's nuclear entry covers all five.

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
  `groundburst:spaghettified`).
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
- Entities inside are erased (`groundburst:annihilated`); gamma burst damages exposed entities to r 64 (lethal) and 96.
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

### 16.9 As built: slice 6e effect systems (2026-10-21)
- **Gas grid** (`GasGrid`): one cell per block, not the 2×2×2 cells planned (a one-block wall must keep gas out).
  Every 5 ticks half of each block's gas flows into the open blocks round it, more down than up (agents are heavy),
  biased downwind (the wind turns every minute or so); solids, fluids and tall thin blocks (doors, panes, walls,
  fences) stop it, low ones (slabs, carpets, snow) let it over; each agent decays at its own rate. Every 10 ticks
  living things breathing it (feet or eyes) take a dose scaled by concentration (full strength at 0.3), cut by the
  hazmat suits (`Toxins`). Agents: **chlorine** (gas grenade, gas warhead: Poison III, clears in ~half a minute) and
  **anthrax** (Chemical Warhead: Poison IV, twice the damage, ~1.5 minutes). Not saved. Clients are sent the gassed
  blocks within 80 blocks every 10 ticks and draw them as a tinted haze (`EffectFx`): small puffs near the middle of
  each gassed block, so none shows past a wall (the canister itself only vents a thin jet).
- **EMP fields** (`EmpFields`): sphere + expiry per level, not saved. Machines in a field (`MachineBlockEntity`) lose
  half their stored energy as it hits, stop (status EMP), and reboot when it ends; Launch Silos refuse to launch, the
  Launch Computer and Radar Array go dark. As it goes up, armed charges with electronic fuzes are made safe (landmines
  and bouncing mines are pressure plates and stay live). A missile flying through rolls once: half are duds (no
  warhead on impact). Clients draw a shell of sparks racing out, then sparks crawling over surfaces in the field.
  Still to come: vanilla redstone reading zero in a field, powered items shutting down.
- **Freezing** (`Frost`): water → ice (packed ice near the middle), lava → obsidian (flowing → cobblestone), fire and
  napalm out, snow on open ground; living things (not bosses, not creative players) get **Frozen** (`FrozenEffect`:
  no movement or jumping, AI stopped, the vanilla frozen shake and frost overlay, snowflakes); a frozen non-player mob
  of ≤40 health taking ≥6 damage **shatters**. A translucent **block of ice** is drawn round a frozen creature
  (`IceShellRenderer`); since effects are not synced to other players, the effect holds the vanilla frost counter
  1 000 above fully frozen (powder snow never goes past it) and clients draw the ice from that; it is cleared on thaw.
- **Weapons:** Cryo Grenade (3 s, radius 5, frozen 10 s), EMP Grenade (3 s, field 12 blocks for 20 s), Chemical
  Warhead (anthrax cloud 9 blocks round; the Chemical Bomb of the plan, as a warhead). Gas grenades now feed the grid.
- **Cluster versions** (after feedback): Cryo Cluster Warhead and EMP Cluster Warhead, 14 cryo or EMP grenades as
  canisters, air-burst over the target like the other cluster warheads.

### 16.10 As built: the Antimatter Warhead (Phase 11, 2026-10-30)
(Since the user's look, §16.12: built whole at the ICBM Assembly Hall as an ICBM, its sphere 67 and its flash 90 and
134. What follows is as first built.)
The user's choices for Phase 11: the Antimatter, Singularity and Storm Bombs this round (the orbital strike later), one
at a time, each shown before the next; missile classes by strength: antimatter and singularity Strategic (XL, beside
the nukes, over §9.2's M), the storm Heavy (L).
- **Warhead and missile.** `ChargeKind.ANTIMATTER_WARHEAD` (added at the enum's end), an exotic kind:
  `exotic()`, and `strategic()` (nuclear or exotic: only the Strategic body, which carries nothing else, so only a
  Launch Complex or a Missile Silo fires it). Core: the **Antimatter Trap** (class XL: four Antimatter Cells, two
  Superconducting Coils, a Quantum Circuit). The bench builds it in the Strategic Warhead Casing; the Strategic
  Missile Assembler its **Antimatter Missile**. Stats: Erases 48, Gamma Kills 64, Gamma Injures 96; the Launch
  Computer's strike circles the flash's 64 and 96.
- **Full effect only on a missile's arrival** (`Charges.impact`/`burst` -> `Charges.exotic`), like a nuclear
  warhead's yield. Set off any other way its trap is breached (`Antimatter.breach`): a sphere of 4 and a weak flash.
- **The sphere** (`blast.Annihilations`, `AnnihilationJob`): grows to 48 (x the bomb scale) in 30 ticks on an ease-out
  (`Annihilations.reach`, which the client draws by too). Blocks are erased a shell (one-block band of distance) at a
  time from the middle out, each shell column by column (a column's two runs found by integer square roots), never
  past the sphere's size at that moment; skipping air above the highest block seen; unbreakable blocks stay; written
  with the blast engine's flags (no drops, no on-place, containers go whole). It draws on the blast engine's time
  and block budget, ahead of the blasts (and always takes a sliver), and keeps chunks loaded as a blast does
  (`BlastChunks`). Creatures inside are hurt by `groundburst:annihilated` (through armour, resistance, shields,
  enchantments and the hurt cooldown; not a creative player's invulnerability); everything else in it is discarded
  (missiles in flight aside); their drops are cleared until 25 ticks after its last kill (experience falls only when
  a death is over). Last, the erased blocks on its surface tell their neighbours (sand falls, water runs in), and its
  chunks are let go. Not saved.
- **The gamma flash** (`Antimatter.gamma`), at the first instant, before anything is erased: a creature between the
  sphere and 96 takes its distance's share (all within 64) times what gets through the blocks on the line (e^-sum:
  0.8 a solid block, 0.35 a liquid, 1.6 concrete, lead or obsidian) times its suit (0.6 of its radiation protection
  counts). A dose of 0.5 or more kills (`groundburst:gamma_flash`); less hurts (40 health a dose of 1) and gives rad
  (800 a dose of 1). No fallout.
- **Client** (`AnnihilationFx`, through the Spectacle): a white-out flash, stronger near, faced and in sight (a
  quarter of it out of sight); an opaque white sphere (`textures/fx/annihilation.png`: white with faint lilac threads,
  two bodies a hair apart), a violet rim glow (the sphere renderer's limb < 0: brightest at the edge) and a faint white
  bloom; it fades in 20 ticks once the server has erased everything (`AnnihilationPayloads.End`); then a faint violet
  glow drifts in the void for 10 s. The sound when it arrives (343 m/s): `antimatter.near` (a crack and thump, a
  swelling rush and climbing glassy tone for 1.5 s, then the inrush: a boom, a hollow thump and a rolling rumble) or,
  past 160 blocks, `antimatter.far`; the camera shaken as it arrives.
- **Models** (material library): the warhead a white ceramic capsule on its mounting ring, three confinement rings
  (a violet coil, its elements emitting light, between dark steel collars), a violet trefoil on yellow, a band of
  hazard chevrons, a dark cap round a glowing port; the missile white and two-staged, small swept fins, violet bands,
  three confinement rings of its own (so its outline is ringed: the neutron missile beside it is as slim and silver),
  hazard chevrons under the warhead; the Antimatter Trap the capsule in small. Field Manual: "Exotic warheads".
- GameTests (`ExoticTests`, at a tenth of the size): a missile's warhead erases stone, obsidian and a full chest within
  the sphere, leaves bedrock and the stone past it, annihilates a pig inside, its flash kills a pig in the open past
  the sphere, a pig behind two blocks of obsidian lives, nothing is left in the sphere; a placed one only breaches; it
  needs the Strategic body.

### 16.11 As built: the Singularity Warhead (Phase 11, 2026-10-30)
(Since the user's look, §16.12: built whole at the ICBM Assembly Hall as an ICBM; it lives a minute and grows by what
it eats, its size all from one reach. What follows is as first built.)
- **Warhead and missile.** `ChargeKind.SINGULARITY_WARHEAD`, exotic and class XL like the antimatter warhead (only the
  Strategic body; full effect only on a missile's arrival). Core: the **Singularity Core** (a singularity seed, four
  containment cells, a quantum circuit). Its **Singularity Missile**. Stats: Event Horizon 10, Pulls In 48, Tears Loose
  32, Collapses After 20 s; strike circles 19 (nothing gets away from within) and 48. Set off any other way its cells
  fail one by one (`Singularity.breach`): a singularity 0.15 the size (horizon at least 0.6, pull 3, tear 1.5) living
  3 s.
- **The black hole** (`blast.Singularities`, `SingularityJob`), u its age over its 400 ticks: horizon
  10 (0.08 + 0.92 u^0.75) (the client draws by it); strength 0.6 + 5.4 u^1.5; tear reach 32 u^0.7 (all times the bomb
  scale).
  - *Pull*: everything within 48 is drawn toward it at min(0.6, k strength / d^2) blocks a tick each tick
    (k = 5.76 (pull/48)^3) plus a third of that round it (the disc's spin), `Singularities.pullOn`. The server pulls
    everything but players; each player's own client pulls its player by the same reckoning (a client moves its own
    player: motion set from the server every tick would fight its movement). At the horizon a creature is hurt by
    `groundburst:spaghettified` (as sure as annihilation), anything else discarded; missiles in flight and creative or
    spectating players are left alone.
  - *Tearing*: the sphere out to the tear reach swept shell by shell, column by column, from the middle out and round
    again (a pass a tick at most), within the blast budget and 900 blocks a tick. A block goes inside the horizon
    (unbreakable ones never), or outside it where it lies open (air or a fluid beside it) once the pull on it,
    strength (8 (rip/32) / d)^1.5, beats its hold (hardness + 0.3; a fluid 0.5): plants, then earth, sand and water,
    then stone; obsidian holds. Written with neighbour updates (sand falls in, water runs in, torn in their turn), no
    drops, containers' contents gone; 80 000 blocks at most at full size. The first 96 torn a tick are told to the
    players within 256 for the debris. Chunks within its reach stay loaded (`BlastChunks`, a front standing at the
    middle).
  - *Collapse*: at 400 ticks the client is told and a blast `singularity_collapse` goes off (core 6, crater 16,
    structural 26, no scorch or fire, lethal 18, damage 34; the heavy warhead's look and sound), times the bomb scale.
- **Client** (`SingularityFx`): the horizon a black sphere written to depth (the cloud type), so the disc and jets
  behind it are hidden; a thin photon ring; an accretion disc tilted 18 degrees (four rings of quads,
  `fx/accretion.png`: streaks drawn round it, bright within and fading out; white-lilac at its inner edge cooling to
  violet; turning faster nearer the hole); two flickering jets along its axis; the torn blocks as debris orbiting it
  (`Debris`' orbit mode: an inverse-square pull, drawn to the disc's plane, a decaying orbit, gone at the horizon); the
  sky and fog drained toward a dark violet grey near it; a looped drone (`singularity.drone`) pitched up from 0.55 to
  1.35 as it grows and the ground tearing now and then (`singularity.tear`, three); at the collapse the horizon
  shrinking to nothing in 6 ticks, a flash, a bright shell spreading, the Singularity Reactor's collapse heard when its
  sound arrives, the camera shaken.
- **Models** (material library): the warhead a matte-black sphere in bands from a true circle's profile (each band
  capped only where it shows: below where it is wider than the band under it, above where wider than the one over it),
  a lit violet slit with darker eddies just above its brass hoop, three hoops of turned planks (`warheads.hoop`; every
  other plank a hair narrower, so their joints share no plane) pinned in brass, on a cradle; the missile finless and
  matte black, four verniers, brass rings round its second stage, a lit violet slit near the top of each stage; the
  Singularity Core the warhead in small. (The flicker checks cannot turn elements about x or z: they report the
  standing hoops' planks as if unturned; with those left out they find nothing.)
- GameTests (`ExoticTests`, a tenth of the size growing 60 ticks): a pig beside it is swallowed, a pig at its pull's
  edge lives; stone beside it and dirt 2 off are torn loose before it collapses while obsidian 2 off holds; bedrock
  stays, stone past its reach stays; it collapses; a placed one opens a small one that collapses.

### 16.12 After the user's look: the ICBM Assembly Hall, bigger exotic weapons (Phase 11, 2026-10-30)
The user's review after the antimatter and singularity warheads: the Strategic Assembly Hall and the Strategic Missile
Assembler, two machines with like names, confused; the special weapons should be made in the hall; both were far too
weak (the antimatter 30-50% bigger; the singularity tiny: it should last much longer and grow with what it eats,
reaching the antimatter's new size on average and more when well fed); the Large Silo Door's sound stopped long
before its leaves did.
- **The ICBM Assembly Hall.** The hall's names (block, structure spawner, JEI's "ICBM Assembly", its parts' tooltips,
  its problem lines, the Field Manual) say ICBM Assembly Hall; its ids (`strategic_assembly_hall`, the menu, the
  classes) stay, so worlds keep their halls. Its screen names its missiles "... ICBM", as the item list does. The
  manual tells it apart from the Strategic Missile Assembler.
- **Exotic weapons are ICBMs, built whole at the hall.** An exotic core (Antimatter Trap, Singularity Core) in the
  primary slot is a warhead by itself: with an Intercontinental Booster (no secondary, no jacket, else
  `Problem.EXOTIC_ALONE`) it makes an **Antimatter ICBM** or a **Singularity ICBM** (XXL, the ICBM motor, 3,000
  blocks) in a minute and a half (`StrategicAssembly.EXOTIC_TICKS`); TAKE APART gives the core and the booster back.
  No missile assembler takes an exotic warhead (`MissileAssembly.Problem.EXOTIC_ICBM`). `wholeWeapon()` stays the
  fusion warheads' and the superweapons': JEI hides those warheads, and the bench still makes the exotic bomb (core
  and Strategic casing, class XL), which only breaches. The cores' tooltip says both. JEI lists the two new recipes;
  the cradles show both (`LOOK_ANTIMATTER`, `LOOK_SINGULARITY`).
- **Their missiles rebuilt intercontinental** (`missiles.py`, material library): three stages and the four sections
  the crane lays (stage tops 60/104 and 62/108, the bus to 144 and 148). The antimatter ICBM white on four nozzles and
  four small swept fins, black interstages, a violet band at each stage's foot, a confinement ring (a lit coil between
  collars) on each stage, raceways, hazard chevrons under the bus; the singularity ICBM matte black and finless on
  four nozzles, four verniers, a raceway, three brass rings round the second stage and two round the third, a lit
  violet slit near the top of each stage.
- **Antimatter 40% bigger:** the sphere 67 (was 48), the flash lethal to 90 and hurting to 134 (were 64 and 96), a
  breach 5.5 (was 4).
- **The singularity feeds** (`Singularities`, `SingularityJob`, rewritten). Its size is one number, its **reach**:
  `SEED sqrt(u) + FEED cbrt(m)`, u its age over its life and m the blocks' worth it has swallowed, at most `MAX_REACH`
  (SEED 26 and MAX 110 times the bomb scale; FEED 0.56 needs none, as the reach settles where the mass it holds
  gives it). It lives **1200 ticks** (was 400). Everything follows the reach: the horizon 0.3 of it; the pull out to
  1.5 of it + 2, of strength gm = 0.12 (0.45 reach)^2, so nothing on foot outruns it from within 0.45 of its reach
  (`pullOn(to, gm)`: min(0.6, gm / d^2) inward and a third of that round); the collapse blast at reach / 32 (crater
  16 at scale 1). On open ground what it eats settles the reach at about SEED / (1 - 1.159 FEED) = 74 (half a sphere
  of stone out to 0.906 of it; a simulation of its minute: 30 at 10 s, 43 at 20, 52 at 30, 64 at 45, 74 at 60); in a
  mountainside (three quarters solid) about 92, buried in rock about 94, held back by its eating rate (1500 blocks a
  tick) more than by its cap; in the open air 26. The screens give the open-ground figures (horizon 22,
  pull 113, tears stone to 67, a minute) and a note that it grows with what it eats.
  - *Tearing*, a tidal pull: a block at distance d goes once hold * d^6 <= reach^6 (hold: hardness + 0.3, a fluid
    0.5), so earth goes to 1.04 of the reach (the sweep's edge), stone to 0.906, deepslate 0.82, obsidian 0.52: the
    hole's edge is sharp. Within the horizon everything breakable goes; outside it only open blocks (checked after the
    cheaper pull test). Torn blocks feed it (a full block 1, a fluid its amount / 8, anything else 0.25), and so do
    swallowed things (a creature its bulk, at least 1; a falling block 1; anything else 0.2). Up to 1500 blocks a tick
    (was 900); the first 120 told to players within 256. Each pass starts two shells inside the nearest breakable block
    the last found (every 12th from the middle), so the eaten hollow is not swept again; columns share their chunk
    lookups. Every chunk within its reach stays loaded (`BlastChunks.prepare(r, disk)`).
  - *Clients* are told its reach every 4 ticks (`SingularityPayloads.Grow`; Start carries its reach and life,
    Collapse its last reach; protocol 16) and ease it in; the horizon, the disc (out to 0.7-0.95 of the reach), the
    jets (to 5 horizons), the debris (orbiting at 0.2 gm), the drone and the sky's tint follow it; each player's own
    client pulls its player by the same gm.
  - A breached one reaches 6, lives 80 ticks and does not feed.
- **The Large Silo Door's sound**: its own (`silo.large_door`, `construction/large_silo_door`, 8.9 s): the bolts
  drawing back, the drive spinning up and its whine climbing with the leaves' smoothstep speed, the rollers' rumble and
  a click at each rail joint (one each half block of their travel) as loud as they go, the motor winding down as they
  ease in, a heavy seat at 8 s and the bolts shooting home; at one pitch both ways, so it ends as they seat. The
  Missile Silo keeps its four-second doors.
- GameTests: the bench's missile line and the Strategic Missile Assembler refuse exotic warheads, the hall builds
  their ICBMs from the cores and takes them apart to them; the hall builds an Antimatter ICBM (refusing a secondary
  with it) and takes it apart; a singularity over bedrock tears what is set round it (stone beside it, dirt 2 off; not
  obsidian 2 off nor stone 4 off) and swallows a pig beside it but not one 5.4 off; a singularity buried in dirt (six
  hundredths of its size) feeds past three times its unfed reach and tears dirt 4 off; the antimatter tests at a scale
  that keeps their sphere 4.8.

### 16.13 The user's second look: whole exotic weapons, the implosion (Phase 11, 2026-10-30)
- **No exotic bombs.** The user: remove the special weapons' warheads and their recipes in the missile assembly. The
  exotic warheads are whole weapons now (`ChargeKind.wholeWeapon()`), and their cores are **ICBM cores**
  (`ComponentItems.IcbmCore`, not bench payload cores, so out of `GBAssembly.CORES`): the bench takes none, JEI's
  warhead and missile assembly pages list none (no core, so no bench warhead, so no missile recipe), JEI hides the
  warheads, and they are out of the creative tab (`GBItems.hide`); the hall alone builds them, into their ICBMs. The
  cores' tooltip names their ICBM and the hall. The breach stays for a warhead placed some other way (a command).
- **The implosion** (the user: no explosion at the end; a cool implosion and collapse with a matching look). At the end
  of its life the singularity stops eating and implodes for `Singularities.IMPLODE` = 40 ticks: its pull on everything
  `implodePull(u)` = 1 + 6u times as hard, its horizon `implodeHorizon` = horizon (1 - u^3) (slowly, then all at once,
  to a point); then it is gone. No blast (the `singularity_collapse` profile is deleted): what is left is the hole it
  ate. Clients (told at its start, `Collapse`): five thin violet rims (`Spectacle.rim`: lit toward their edges and not
  at all across their faces, so only the edges show) rushing inward one after another from past the hole's edge onto the
  shrinking horizon (the first shots, broad shells and then rims with a faint face, five deep, read as a white bubble),
  the photon ring brightening, the disc wound in onto the horizon, hotter and spinning up to 26 times as fast, the jets
  flaring to 1.8 times their length and falling back in, the debris pulled in as hard as the hole pulls, the sky
  darkening further; as it closes a white point shrinking away, a softer flash and the ground shaking at the thud. Each
  client pulls its own player by the same boost. The sound (`singularity.implode`, `exotic/singularity_implode`, 2.9 s):
  an inrush building the whole time, brightening, over a sub tone climbing and a glassy shimmer bending up; at 2 s (the
  horizon closing) all cut off, a deep falling thud, a pop and a ring.

### 16.14 As built: the Storm Warhead (Phase 11, 2026-10-30)
The last weapon of the round. The user: special weapons are made in the ICBM hall, and the storm should be scaled up to
affect a whole region (over §16.1's 48 blocks for 45 s).
- **Warhead and missile.** `ChargeKind.STORM_WARHEAD`, exotic and a whole weapon like the other two: its **Storm Core**
  (`IcbmCore`: four capacitors, two superconducting coils, a military circuit, two copper plates) and a booster make the
  **Storm ICBM** at the hall (`LOOK_STORM`, JEI's sixth recipe); the warhead out of JEI and the creative tab. Set off
  any other way (a placed warhead, by command) its banks discharge: a storm a fifth the size for 20 s
  (`Storm.breach`). Stats: Storm Region 160, Lightning 6 a second, Rages For 90 s; the strike circle the region.
- **The supercell** (`blast.Storms`, `StormJob`): 160 blocks round (x the bomb scale) for 1800 ticks; `intensity`
  smoothsteps up over 300 ticks and down over the last 300. Strikes come at `STRIKES` (6 a second at full size and
  strength; a smaller storm fewer, at least 0.5) with a carry: 35% at a creature in the open (refreshed each second:
  alive, in the region, `canSeeSky`), 35% at the tallest of six random spots, the rest at one; the surface from
  `MOTION_BLOCKING`, only where the chunk is loaded. A lightning rod at the surface within 32 blocks of the spot and
  inside the storm takes the bolt (`onLightningStrike`: its redstone pulse) and nothing else happens. Otherwise: each
  creature within 1.5 of the foot gets vanilla `thunderHit` (5 and a burn; a creeper charged, a pig turned) and 7 more
  lightning damage; the chain jumps from it (or the foot) to the nearest creature within 6 not yet hit, up to 4 jumps,
  each 0.75 of the last (and a 2 s burn); the ground: grass, podzol, mycelium to dirt (40% coarse), sand to glass
  (35%), leaves burnt away; a fire at the foot 40% of the time where fire may spread (`canSpreadFireAround`); an EMP
  field 4 round for 100 ticks (machines knocked out). Its wind (every other tick, everything but players and missiles
  in it): `windOn`, round the eye anticlockwise and a quarter as much in, `WIND` 0.035 blocks a tick each tick at the
  eyewall (0.4 of the radius), nothing in the eye, dying to its edge. Not saved.
- **Clients** (`StormPayloads` Start/Strike/End, protocol 17; `StormFx`): the cloud deck, ~170 of the puff sheet's
  smoke cells in six rings at the cloud base (60 over where it struck, x the size, at least 0.3) turning round the eye,
  inner rings faster, with a darker wall cloud hanging under the middle; each bolt a channel from the deck to the foot,
  straight-line points thrown aside by up to 3.5% of its length mid-way, forking (13% a step, two levels, each branch
  ending in the air), the chain's jumps as smaller arcs; drawn as camera-facing ribbons (`fx/bolt.png`, a white core
  in a soft glow) flickering over 9 ticks and striking again; vanilla's sky flash for strikes within 400, a screen flash
  for near ones in view; thunder when its sound arrives (`storm.thunder_near`, three: a crack, a boom, a rolling
  rumble, within 64; `storm.thunder_far`, three: a long low roll, from far off), near strikes shaking the camera; rain
  (vanilla's streaks on two rings of sheets round the camera) wherever the camera stands under open sky in it; the sky,
  fog and vanilla clouds greyed toward slate; its wind's howl (`storm.wind`, a 4 s loop heard all round) rising and
  dying with it; each client's own player driven by the same wind.
- **Models** (material library): the warhead squat and caged, a dark drum wound with copper between dark collars, four
  capacitor tubes glowing blue in steel ferrules tied by a steel hoop, hazard stripes on its shoulder, a lid and neck up
  to a Tesla coil's top load (a flat polished ring of turned planks) and a copper electrode; the Storm Core the warhead
  in small; the Storm ICBM slate blue and finless on four nozzles, copper coil bands round its second and third stages,
  a capacitor ring glowing blue near the top of each stage, a raceway, hazard stripes under the bus.
- GameTests (`StormTests`, a region 6 round, under open sky where it strikes at random): a bolt into a creeper charges
  it, scorches the grass, knocks out machines round it and chains through the two pigs beside it but not the one 11
  off; a storm striking 5 a second strikes many times and scorches grass in its region but never the plot's corners,
  then dies away; a lightning rod in it takes every strike (the grass untouched). (Rods outside a storm are not
  counted: a neighbouring test's rod drew another's strikes.)
- **After the user's look** (the same day): the Storm Core takes a **quantum circuit**, not a military one; and every
  strike **explodes** where it comes down (`Storms.STRIKE_BLAST`, profile `storm_strike`: a landmine's blast a little
  wider, crater 3, structural 4.5, scorch 4, fires round its rim to 3, gentle on creatures the bolt has hurt already: 6
  within a block, then 4 falling to nothing at 3.5), after its chain, its scorch and its EMP field; a strike a
  lightning rod takes has none. It is not scaled with the storm (lightning is lightning). GameTests: a bolt into open
  ground leaves a small crater and nothing at the plot's corners; the region test's storm strikes without its blasts
  (`Storms.quietOverride`), so its grass can be counted. The GameTest overrides are read as a storm starts (the strike
  rate kept by the job) and set back after it.
- **Its birth** (after the user's next look: lightning going up, creating the storm): for its first 5 s (`StormFx.BIRTH`
  100 ticks) its clients draw bolts going up from where it struck into the sky where the cloud deck gathers, bluer
  than its strikes, forking upward as they go, each top crawling out along the cloud base in two or three arms (up to
  0.35 of its radius, at most 60); a burst of four in its first two ticks (the first heard as a crack and the EMP's
  zap), then fewer and fewer (0.35 a tick falling as the square of what is left of the 100); a third or so with
  thunder; the sky lit, and the screen when near. A client told of a storm late sees none of what has passed. Client
  only (a schedule from the storm's id). FX scenes `storm_birth` and `storm_birth_close`.
- **Its EMP pulses** (after the user's look at the anti-missile defence, the same day: EMP effects near the strikes, to
  make the storm worth more against interceptors): each strike's field is now a pulse 24 blocks round for 240 ticks
  (`Storms.EMP_RADIUS`, `EMP_TICKS`; it was 4 for 100), as much smaller as the storm is but never under 4 (`empRadius`:
  a GameTest's storm, shrunk to its plot, keeps 4, so it never reaches the next plot's machines and missiles). At six
  strikes a second over 160 blocks round, a spot sits under a pulse about four fifths of the time at full strength:
  machines go dark (losing half their energy each time a pulse catches them anew), batteries and complexes fire nothing,
  interceptors flying through are fried (`Interceptors`, §18.5), missiles flown through unhardened turn duds half the
  time (§21.4): the storm is the way in for the missiles sent after it, hardened. A strike into a lightning rod makes
  no pulse. Its show (`EmpFields.pulse`, the payload's `pulse` flag): not the full EMP's dome of light but a ring of
  electric-blue motes and sparks running out along the ground over 8 ticks (a mote every block and a half of its edge:
  white sparks alone were lost against sand and rain), a burst at its foot and a quieter, higher zap; the crackle near
  you counted once however many fields overlap (it was once a field). FX scene `storm_pulses`.

### 16.15 The Special Weapons Assembler (Phase 11, 2026-10-30)
The user, after the Storm Warhead: a special weapons assembler for the special weapons, their missiles fired like the
other ICBMs. So the exotic weapons leave the ICBM Assembly Hall (§16.12), which builds the fusion weapons only again.
- **Recipes** (`SpecialWeapons`): an exotic core (`ComponentItems.IcbmCore`: the Antimatter Trap, the Singularity
  Core, the Storm Core) and an Intercontinental Booster, a guidance unit and Hardened Electronics optional, make that
  weapon's intercontinental missile (`SizeClass.XXL`, `Motor.ICBM`), fired from a Launch Complex or a Missile Silo as
  before; a minute and a half (`TICKS` 1800) at 320 FE a tick (it holds 600,000); TAKE APART gives the parts back.
  Problems: not formed, no core, no booster, output full. The hall's primary slot takes no exotic core, its output no
  exotic missile, and it takes none apart (`StrategicAssembly.takeApart`: nothing for one); a missile left in a hall's
  output from before keeps its look there. The cores' tooltip names the new assembler.
- **Structure** (`GBStrategic.SPECIAL`): the hall's 7x13 floor of Launch Pad (the controller front middle, the Energy
  Port back middle, the Item Port two to its left) and three containment rings across it on the fourth, seventh and
  tenth rows (`RING_ROWS`): each a square of eight Toroidal Field Coils (the fusion reactor's) three wide and three high
  round the bay's middle, its middle kept clear; elsewhere the bay kept clear five wide and three high. Four high in
  all. A Structure Spawner for creative. The controller: a lead-lined casing, two quantum circuits, a containment
  emitter, two superconducting coils, a sensor array, a tungsten plate and a CRT display (a rung over the hall's).
- **Block entity** (`SpecialWeaponsBlockEntity`, menu `special_weapons_assembler`, `SpecialWeaponsScreen`): the hall's
  in all but its parts: slots core, booster, guidance, module, output; the missile laid on its cradles four sections
  as the work goes on, the warhead last (`MissileCarrier.lying`; the saddles' tops at 28.5 under 20 and 64 px up the
  missile, so its axis lies at the rings' middle, 40); upgrade chips; the particle accelerator's thrum. Its screen is
  the hall's layout with the core alone over the booster, guidance and module (`gui.py` "special_weapons"), the
  bench's column on the right. JEI: "Special Weapons Assembly", three recipes (the hall's category back to its three).
- **Model** (`tools/assets/special_weapons.py`, material library): a sealed grey floor in plates, violet bay lines,
  hazard bands round its edge and a violet keep-out band round each ring's plinth; each ring a dark violet housing of
  sixteen planks (radii 19 to 24) on a gunmetal plinth striped in hazard paint, a steel lip and rivets round its faces;
  inside it a copper-wound rotor (15 to 18.9) whose inner face is a violet emitter strip with four bright nodes
  (glow); the two cradles; steel conduits from the back up each side and across to each plinth; the hall's desk with a
  violet screen; a lead cask for the cores with a trefoil. Working, the rotors turn once in 4 s (the middle one the
  other way) and the fields light: a thin violet membrane in each ring round the missile (bone "field", glow only,
  hidden while idle). Every other plank is sunk 0.06 px at its faces, so neighbours never fight where they overlap.
- Field Manual: "Special Weapons Assembler" (Ordnance, after the hall's) with its structure page; the hall's, the
  exotic warheads', the missiles' and the upgrade chips' pages changed to match. GameTests (`SpecialWeaponsTests`): it
  builds a hardened Antimatter ICBM and takes it apart; it needs its structure and its own parts; the hall refuses the
  cores and their missiles.

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
| **Missile Silo** | 5×5 surface, 14 deep shaft | XL (nuclear too) | Hatch doors slide open, missile launches from the shaft (exhaust erupts from side vents). Missile inside is protected until doors open. 20 s countdown. As built: §17.10. |
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

### 17.6 As built: the basic launch site (2026-10-21, ahead of Phase 9)
A first, deliberately simple launch site, so the warheads have a delivery system before the full Phase 9 platforms.
There are no aircraft to drop bombs: the slice 6d bombs became **warheads** (same effects), and missiles are how they
are delivered.
- **Warheads** (`he_warhead`, `heavy_warhead`, `thermobaric_warhead`, `cluster_warhead`, `bunker_buster_warhead`,
  `napalm_warhead`, `massive_warhead` "Massive Ordnance Warhead", once the MOAB): placed, they stand nose up on a
  mounting ring (ogive noses, each its own colours and bands); still armable charges with an impact fuze. The napalm
  warhead throws a pool 10 blocks round (no length to spread along). A missile carries its warhead's own model (§4.5;
  until 2026-10-23 its nose was painted as its warhead).
- **Scale.** These warheads and the Basic Missile are the **tactical, conventional tier** (size classes S/M of §9):
  deliberately far weaker than the full-scale weapons still to come (the nuclear and special bombs of §15-16, the L/XL
  casings and strategic missiles of §9, the superweapons of §19), which arrive with their own launch structures,
  their own custom models and far bigger yields.
- **More warheads** (after the in-game look): Poison Gas and Smoke Warheads (clusters of 14 gas or smoke grenades) and
  a Toxic Waste Warhead (a toxin splash 10 blocks round). Missile-delivered cluster warheads (cluster, gas, smoke)
  **air-burst** 30 blocks over the target and rain their submunitions within about 20 blocks of it (32
  bomblets for the cluster); set off on the ground they throw them out as before. Bomblets were strengthened
  (crater 4, deadly to 4.5 blocks). The bunker buster bores a **3×3** shaft.
- **Launch Silo** (`launch_silo`): a 3×3 Launch Pad layer with the controller in the middle of its front edge and an
  Energy Port in the middle of its back edge, a 9-high Gantry Frame tower beside the pad's middle row with a Gantry
  Arm at the seventh block, and a clear 1×8 column above the pad centre where the missile stands (8 blocks tall, the
  arm holding its body below the nose). Slots: a **Basic Missile** and a warhead. Each launch takes the missile, the
  warhead and 100 000 FE (buffer 200 000). Countdown 10 s under one mechanical air-raid siren and beeps.
- **Launch Computer** (`launch_computer`): controller + Energy Port, a mast of three Radar Masts topped by the Radar
  Dish behind it, and an Antenna. 20 FE/t. Its screen: a green phosphor **terrain map** (drag to pan, scroll to zoom
  1:1-1:32, Centre, cursor coordinates) of its **terrain survey**: every chunk loaded within 3 000 blocks of a
  computer is recorded as vanilla-map colours (per column, slope-shaded) and saved (`terrain_survey.dat`); chunks are
  surveyed as they load, as they unload, and again when asked for while loaded and stale. The map shows the silos,
  their targets and missiles in the air; clicking it aims the picked silo. Per-silo control: each silo keeps its own
  target and is armed on its own (ARM/DISARM); LAUNCH fires every armed silo. Tiles of 64×64 samples at the view's
  scale go to the client on request.
- **Missile flight** (a simplified §17.5 with the MissileTracker idea): a flight lives in per-level saved data
  (`Flights`, `missile_flights.dat`) and is advanced every server tick whatever is loaded, so it can never stall; it
  ticket-loads its target's chunk, the chunk it is in and those its next 30 ticks will cross. The `MissileEntity` is
  only what players see, moved along by its flight and put back wherever its chunk is loaded. 2 s ignition, a fixed
  cubic arc (apex = clamp(range × 0.35, 40, 220)), up to 1 024 blocks; it tilts along its path (pitch stored 90° low,
  as entity pitch is clamped to ±90°) and whistles in its last 2.5 s. Anything loaded in its way sets off the warhead.
- Building blocks: Launch Pad, Gantry Frame, Gantry Arm, Radar Mast, Radar Dish, Antenna (multiblock parts, hidden
  when formed). The full platforms, guidance and multi-stage bodies remain Phase 9.

### 15.2 As built: the Strategic Assembly Hall and whole weapons (slice 8c, 2026-10-22)
(Named the ICBM Assembly Hall since Phase 11, when it took the exotic ICBMs too: §16.12.)
The user's rule after 8a: the Strategic missile is the peak of what fits in a missile; anything bigger gets its own
fabricator, a multiblock that assembles the full weapon all at once, to be placed on the Launch Complex. The user chose
the long horizontal hall (over an upright assembly tower).
- **Structure** (`GBStrategic.HALL`): a 7x13 floor of Launch Pads (controller front middle; Energy Port back middle, an
  Item Port two columns west of it), six Gantry Frame columns four high (corners and halfway along each long side), a
  new **Crane Rail** block the length of each long side on top; the hall between them (5 wide, rows 0-12, layers 1-5)
  must be clear. Formed, the controller's GeckoLib model (`tools/assets/strategic.py`) draws it: a concrete floor with
  yellow bay lines and a hazard-striped edge all round (at first only the open ends), grey I-beam columns, yellow crane girders with steel rails, a
  twin-girder bridge crane with a trolley, drum and a hook block on two cables, two cradles, a control desk and a
  pallet of crates; the Energy and Item Ports are flush floor plates (a bolt and a grate) on their blocks, since the
  port blocks are in the floor (after the user's look: the first model's power cabinet stood up over the port).
  "working" (12 s loop): the crane runs from over the tail to the first stage and
  lowers its hook (the cables scale as they pay out), on to the third stage and lowers it again, and back.
- **Recipe** (`StrategicAssembly`): a Boosted Fission Core (the primary), a new **Fusion Secondary** (4 lithium
  deuteride pellets, 2 depleted uranium ingots, a plutonium nugget, a lead plate, a plastic sheet) and a new
  **Intercontinental Booster** (2 Liquid-fuel Stages, 3 dense steel plates, tungsten carbide, a military circuit) make a
  Thermonuclear Warhead; a new **Cobalt-59 Jacket** (4 cobalt ingots, a dense steel plate) makes it a Cobalt Warhead;
  guidance and Hardened Electronics optional. One minute at 250 FE a tick (400k buffer); TAKE APART returns every part.
  While it works the model lays the missile on the cradles in four loads (first stage, second, third, the warhead's
  shroud; `laid` and `look` sent to clients), each set down halfway through its share of the work with a clank.
- **Whole weapons**: an intercontinental missile is a MissileSpec with the new airframe class **XXL**
  ("Intercontinental") and the new motor **ICBM** (3,000 blocks, the Launch Computer's map; no bench motor item), so the
  launch, flight and computer code take it unchanged. Its warhead blocks (Thermonuclear, Cobalt) are `wholeWeapon()`
  kinds: nuclear (they fizzle any other way), refused by the bench (`MissileAssembly.Problem.WHOLE_WEAPON`), not
  taken apart there; a Launch Complex fires them, and since 9b a Missile Silo too. Its own model (`launch.py` `ICBM_SECTIONS`, item model
  `icbm_missile_airframe` with the warhead's own model on it (§4.5), the complex's `icbm` bone, the warhead at
  `iwarhead`; lifted by `ICBM_TAIL` in flight): three olive-drab
  solid stages narrowing upward with a white, a yellow and a white band, dark riveted interstages, a gunmetal skirt
  and four nozzles, four swept fins in two steps round the first stage (added after the user's look: the first model
  had none), its warhead on the third stage (a re-entry vehicle; the shroud it had until 2026-10-23 went, §4.5);
  about 11 blocks on the complex. The complex's umbilicals gained a clamp each
  for it (lower at its first stage, upper at its third), and the hall draws the same sections lying down.
- **Effects**: thermonuclear (crater 150, wrecks 220, fire 260, deadly to 260, injures 420; fallout 360 for three
  days); cobalt (crater 70, deadly to 110; fallout 300 round the burst plus four zones strung downwind 200 blocks apart,
  each 20 smaller and 15% weaker, all lasting 30 days: about 1,000 blocks of plume; a stand-in for Phase 12's drifting
  fallout). Stats: "1.2 Mt", "400 kt", the cobalt note "Fallout drifts N blocks downwind".
- The hall's screen (`StrategicAssemblyScreen`, layout `strategic_assembly`): the warhead's parts over the missile's,
  the arrow and the finished missile, the readout, Assemble / Take Apart, and the bench's right-hand column (now a
  shared `AssemblyColumn`) with a shorter preview and a taller stats screen. JEI: a "Strategic Assembly" category
  (`StrategicCategory`) laid out like the screen, one recipe per warhead (thermonuclear; cobalt with the jacket; the
  Tsar with the Tertiary Stage), guidance and Hardened Electronics marked optional, the hall as its station, and the
  screen's arrow opens it.
- **The Tsar** (slice 8d): the jacket slot takes a **Tertiary Stage** instead (2 Fusion Secondaries, 4 lead plates, 2
  military circuits, a dense steel plate): the hall builds a **Tsar Mega-bomb** missile in a minute and a half
  (`StrategicAssembly.ticks`). Effects as in the table above, its own heavier, longer sound set (`blast/tsar_*`, 17 s
  near) and a cloud to 500 blocks (the cap's limit raised to 520). Models: the block (`warheads.py` `tsar()`: vents lit
  regardless of the light round them); on the missile the bomb rides bare on the third stage, wider than any
  stage, and lies the same way on the hall's cradles: since 2026-10-23 its own block model (§4.5; before,
  `launch.py`'s `TSAR_SECTIONS`), its vents lit by the model's own light. No aircraft yet: the
  parachute pack stays packed, and the Tsar goes off where its missile hits, like the H-bomb.
- **The Twin Boosted Core** (2026-10-23, after 9b, the user's request: the Tsar's power far beyond the other two whole
  weapons on the same primary): the Tsar's own primary, two Boosted Fission Cores fired together (recipe: 2 Boosted
  Fission Cores, 2 tritium canisters, a military circuit, a dense steel plate). The hall's primary slot takes either
  core (`StrategicAssembly.isPrimary`); `check(primary, secondary, jacket, booster, output)` asks for the twin with a
  Tertiary Stage (`NO_TWIN`) and refuses it with anything else (`TWIN_FOR_TSAR`); TAKE APART returns it; JEI shows it
  in the Tsar's recipe (`primaryFor`). Model (`components.py` `twin_boosted_core`): two lens-shaped spheres on a
  cradle, a copper bus between them, two tritium bottles, a hazard band.

### 17.8 As built: the Launch Complex (Phase 8 slice 8a, 2026-10-22)
- `launch_complex`: a 5×5 deck of Launch Pads (controller front middle, Energy Port back middle, a new **Flame
  Deflector** block in the middle under the missile), a Gantry Frame service tower 2 cells deep and 13 high beside the
  deck's middle and back rows (the clockwise side of the controller's facing, like the silo's gantry), Gantry Arms at
  layers 4 and 8 as the umbilicals, and the missile's column clear 11 up.
- `LaunchComplexBlockEntity` extends the silo (same menu, computer link and targeting): takes **any** missile, and is
  the only launcher that takes a nuclear one (`firesNuclear()`; the silo refuses them; since 9b the Missile Silo
  takes them too). 250 000 FE a launch, 500 000
  buffer, **15 s** countdown: siren, beeps, the deluge (splash and falling-water particles round the pad) for the last
  5 s, and at ignition 3.5 s of steam and smoke out of the trench's open end (the side away from the tower) and round
  the deck.
- GeckoLib model: concrete deck with hazard edges, a sooted trench and stepped deflector wedge, deluge pipes on both
  lips, diagonal hold-down posts, console and housing; a grey braced service tower with orange-railed platforms every
  four storeys, a roof mast and red beacon; two orange umbilical arms with clamps and hoses that swing clear at T-13
  and T-9 (a 20 s clip, so it does not loop back during the ignition smoke) and rest clear while no missile is loaded
  (the new `AnimatedMachine.idleAnimation()`); the missile scaled by its body as on the silo. After the in-game look:
  each arm turns on a hinge (a pin between two knuckles off a spine up the tower's face) and swings 75° rather than
  100° so it clears the tower's posts (the silo's arm the same); the upper arm has a second clamp, shown for a Strategic
  missile, that reaches in to its slimmer second stage; the Strategic missile's first stage is cut in quadrants so its
  roll pattern has solid corners.

### 17.9 As built: missile flight (Phase 9 slice 9a, 2026-10-22)
Phase 9's plan, in slices: **9a** missile flight (this); then the underground Missile Silo with its hatch doors, the
erector Launch Pad and the Cruise Missile Launcher with terrain-following cruise missiles, the launch control upgrade
(salvo, time on target, patterns; Data Link Tool, Comms Relay), the designators (Targeting Binoculars, Laser Designator
and laser guidance), MIRVs, and placed missile detonation (§17.1).
- **Phases of a flight** (`Flights.Flight`, by the fraction of the flight flown; the fixed arc of §17.6 is unchanged:
  straight up off the pad, over the top, steeply down): the engine **burns out** at 45% (a Strategic missile 40%, an
  intercontinental one 36%) and the missile **coasts** in silence; a Strategic missile **drops its first stage** at 16%,
  an intercontinental one its first at 12% and second at 24% (`MissileParts.stageDrops`, `burnout`); coming back down
  from past 55% its nose glows (**re-entry**, brighter the higher its arc: `reentry()`). `MissileEntity` syncs the stages
  dropped, whether an engine burns and the re-entry heat.
- **Models:** the upper stages' engine bells hang inside the interstages below them (seen once the stage below has
  gone); item models of the missile with one or two stages gone (`strategic_missile_<warhead>_s1`,
  `icbm_missile_<warhead>_s1`/`_s2`, chosen in flight by `MissileParts.look(warhead, body, dropped)`) and of each spent
  stage (`strategic_stage1`, `icbm_stage1`, `icbm_stage2`).
- **Client** (`Spectacle.missileExhaust`): the flame and the smoke trail come from the burning stage's bell and stop at
  burn-out; where a stage parts, a flash, a ring of white smoke and a bang, and the stage falls away (`SpentStages`: the
  stage's own model, the missile's way on less its thrust, tumbling about its middle; dust and a thud where it lands, or
  gone after a minute); re-entry is a white-hot sheath on the nose and a dense streak of fire cooling to dull red, laid
  every 0.4 blocks from where its last puff was (fire, not glow: additive glow washes out to white against a day sky).
- **Sound** (`MissileSounds`, the client's own, following each missile): a seamless engine loop while an engine
  burns and a re-entry roar coming down, each pitched by the missile's speed toward or away from the listener (Doppler,
  sound at 17.15 blocks a tick) and faded in and out; staging is a crack, a ringing clank and a thump from where it
  parts (delayed by distance). The server no longer plays the engine every 2 s.

### 17.10 As built: the Missile Silo (Phase 9 slice 9b, 2026-10-22)
- **Structure** (`GBLaunch.MISSILE_SILO`), dug into the ground, 5×5 and 14 layers: a floor of **Reinforced Concrete**
  with the Flame Deflector in its middle; twelve rings of Reinforced Concrete round a 3×3 shaft that must be clear;
  the top layer, level with the ground, a ring of **Hazard Concrete** round a **Silo Hatch** over the shaft, the
  controller in the middle of the ring's front and an Energy Port in the middle of its back. Reinforced Concrete is not
  a part, so the shaft's walls stay the blocks you built; the top ring, hatch and deflector hide while formed.
- **New blocks:** Reinforced Concrete (8 from 8 concrete round a steel rod; blast resistance 1200 like obsidian,
  hardness 15, an iron pickaxe), a building block in its own right; Hazard Concrete (4 from 4 Reinforced Concrete and
  yellow and black dye); the Silo Hatch (4 dense steel plates, 2 servos, a Hazard Concrete): one item placing a 3×3
  lid drawn by its middle, the Large Radar Dish's way (both now on `SpreadPartBlock`); the Missile Silo Controller
  (machine casing, military circuit, CRT display, 2 Reinforced Concrete, a lever). Creative: a dug-in Structure Spawner
  (its top replaces the block clicked; it digs out anything but unbreakable blocks and blocks that hold things).
- **What it fires:** any missile at all, nuclear and intercontinental ones too (`MissileSiloBlockEntity.accepts`; at
  first it refused intercontinental ones, which the user asked for after 9b): the Launch Complex is no longer the only
  nuclear or intercontinental launcher (§30). An intercontinental missile stands in the shaft as on the complex (bones
  `icbm` / `inose_*`, not scaled).
  250,000 FE a launch, 500,000 buffer, run from a Launch Computer like any launcher.
- **Doors and launch** (`MissileSiloBlockEntity`): a 20 s countdown; at T-15 the doors unlatch (0.5 s) and slide apart
  (3 s); the launch holds at T-1 until they are open (`holdLaunch`). The missile is lit on its launch ring at the
  bottom of the shaft. Its flight (`Flights.Flight.silo`) climbs straight up the middle of the shaft to 1.5 blocks past
  the mouth, then joins the usual arc: the straight part takes the share of the path's parameter that keeps the speed
  the same across the join (u0 = rise / (rise + 3 × 1.35 × apex)), and nothing is checked for collisions while it climbs
  out. The doors shut 10 s after lift-off; smoke pours from the mouth and the vents for 6 s. Shut, the hatch is solid to
  the leaves' top (1.5 blocks: you can walk on the doors) and dark below; open, there is nothing there and daylight
  falls down the shaft. The screen's **DOORS** button opens or shuts them except during a launch. A new readiness,
  **Hatch blocked**: anything solid within 6 blocks over the hatch's middle (the missile would hit it).
- **Model** (`tools/assets/silo.py`): a concrete collar flush with the ground with a hazard border, a striped steel
  coaming round the opening, two thick blue-grey armour leaves on rails that run out past the collar (moved by the
  block entity each frame through the new `AnimatedMachine.boneOffsets`, so they are right however a client joins), an
  exhaust grate at each corner, a squat olive console with a green CRT and two red beacons that flash in turn while it
  works, the Energy Port's flush plate; down the shaft, conduits in the corners, up to the ground (no lamps: the first ones looked
  scattered, the user then asked for them to go altogether) and the
  launch ring with hold-down posts over the duct grate; the missile as on the complex (a Strategic one its own model).
  `MachineGeoRenderer` takes a depth: the silo's culling box reaches 14 blocks down. Painted with the material
  library since the remaster (§4.3).
- **Client** (`Spectacle`): a missile lit in a silo (`MissileEntity.SILO`: the hatch's middle) sends its smoke straight
  up the shaft; jets of fire and smoke burst from the four corner vents and smoke billows out of the mouth over
  the ground, building through the ignition, full while it climbs the shaft and dying away once it is clear.
- **Sound:** `silo.doors`: two bolts unlatching, a motor and rollers grinding, a click at each rail joint, a bang into
  the stops. **Screen:** its own layout (`missile_silo`): a taller CRT (state, warhead, doors) and the DOORS button.

### 17.11 Cruise missiles and the Cruise Missile Launcher (Phase 9 slice 9c, 2026-10-24)
The user chose the cruise launcher alone for 9c (not the erector Launch Pad of §17.2: the basic Launch Silo already
fires Light to Heavy missiles from a pad) and a motor that makes a missile a cruise missile, each warhead's its own.

**Research** (the Tomahawk, its canisters and launchers): 5.56 m long (6.25 m with its booster), 0.52 m across, wings
2.67 m tip to tip; a solid booster lights it out of its canister and falls away, then its wings unfold, a scoop opens
under its belly and its turbofan carries it subsonic (Mach 0.74) low over the ground, matching the ground to its map
(TERCOM) all the way to the target. Ships carried the canisters in Armored Box Launchers that rose to fire; the
ground-launched Gryphon rode a truck.

**How it works:**
- *The Cruise Engine*: a new motor (`MissileParts.Motor.CRUISE`: a small turbojet with its own launch booster) for
  Light and Medium bodies, 1,500 blocks. A missile with one is a **cruise missile**: the same warhead, its own winged
  airframe (each of the ten Light and Medium warheads' cruise missiles its own, painted as that warhead's missile, the
  head fitting the body), built at the Light or Medium Missile Assembler, named "<warhead> Cruise Missile". Its look is
  `<warhead>_cruise` (`MissileSpec.look()`): the whole missile with its booster; `_s1` without it; `_stage1` the booster.
- *Only the Cruise Missile Launcher fires them*, and it fires nothing else (the vertical launchers refuse them).
- *The Cruise Missile Launcher*: a 2×6 deck of Launch Pads (the controller and an Energy Port in its front row) with
  two new **Launch Box** blocks on its back row, clear above for the boxes to rise. Formed: two armored boxes side by
  side on a cradle hinged at the back; a missile in each (two slots). A Launch Computer aims, arms and fires it like any
  launcher: a 10 s countdown while hydraulic rams raise the boxes to 45°, then each box's front cover swings open and its
  missile goes, 3 s apart, at the same target (each its own guidance's scatter). 60,000 FE a missile.
- *Flight* (`Flights.Flight` with a cruise plan): out of its box along the launcher's front at 45° on its booster; the
  booster falls away (the spent-stage machinery), and it turns toward the target in a level arc while it settles to its
  cruise height, 40 blocks over the ground, which it follows at 1.6 blocks a tick (planned at launch from the ground along
  its path: the real ground where loaded, the generator's where not; climbing early for a ridge, easing down after it).
  50 blocks short it pops up and dives onto the target. Anything taller than its cruise height in its way stops it.
- *Look and sound*: the booster's flame and smoke, then a small hot jet and a thin trail; a jet's whine following it
  (Doppler); the booster's roar and the box cover's bang at launch.

**As built (2026-10-24):**
- *Cruise Engine* (`cruise_engine`, 1,500 blocks, Light and Medium): Solid Rocket Motor I, two synthetic fuel canisters,
  a pressure vessel, a tungsten plate, an advanced circuit, a steel plate. Its model: a turbojet (intake with its spike,
  case, nozzle, fuel pump) on its hazard-banded booster. `MissileAssembly.motorsFor` lists it; `rocketsFor` (the
  rockets alone) does not.
- *The missiles* (`missiles.py` `cruise()`, ten of them): the warhead's head on a body its width, a boat-tail with the
  jet's nozzle, four small fins in an X, straight wings at mid-body, the scoop under the belly, the solid booster under
  the tail; each in its warhead's livery. Variants: `_s1` (booster gone), `_stage1` (the booster falling), `_part1-3`
  (the assembler's sections) and `_folded`: wings and scoop stowed, as a Tomahawk's are in its canister. The wings are
  wider than the box, so the missile is drawn folded from lighting until 6 ticks after it moves (its wings clear of the
  box's mouth; `MissileEntity.folded()`).
- *The flight* (`Flights.CruisePlan`, fixed at launch, saved with its heading and heights): 10 ticks burning in its
  box; 24 blocks out along the launcher's heading at 45 degrees in 22 ticks on its booster (speeding up), then the
  booster drops; a circular arc onto the target's bearing (radius a quarter of the distance left, 12 to 48 blocks,
  halved while the target is inside it) and a straight run, at 1.6 blocks a tick; 40 blocks over the ground, sampled
  every 4 blocks at launch (the real top where loaded, the generator's surface plus 8 for trees where not), climbing at
  most 0.6 and sinking 0.5 a block (the climb planned ahead), smoothed over five samples and never 3 under what it
  wants; 50 blocks short it climbs to 55 over the target's ground and from 30 short dives on it. It hits whatever is in
  its way once its boost is over.
- *Look and sound*: while its booster burns, the box's blast (`Spectacle.boxBlast`) and the rocket's roar; then a small
  jet glow and a thin trail (`cruiseJet`) and the jet's whine (`cruise.engine`, a seamless loop, Doppler). At the
  launcher, `cruise.launch` (the cover's bang, the booster tearing away) and `cruise.elevate` (the rams' pump and whine,
  the lock home; played slower as the boxes come down).
- *The launcher's model* (`cruise_launcher.py`): two haze-grey armoured boxes (16 px square, 84 long, 0.8 px apart)
  banded by two steel straps, a hazard-striped collar round each mouth with a cover stencilled 1 or 2 set in it, the aft
  faces louvred under a red band, white stencils down the sides; hinge barrels on a pin through three cheeks on a base
  beam at the deck's back; a yellow rest beam with rubber pads under each end of the lowered boxes; under each box a
  telescopic ram of three sections (black cylinder, chrome stage and rod) from a clevis in a pit in the deck to a lug
  under the box (its foot on the line through the lug's two ends, so it barely turns: it only stretches, 16.6 to 38
  px); a low olive console and the Energy Port's housing at the front edge, under the boxes' mouths; a heat-blued blast
  plate behind the hinge; an amber beacon on the boxes' backs, lit and turning only while it launches. The rams raise
  the boxes in 3 s. A box's cover is frangible: as its missile lights it bursts off (it tumbles out of the mouth and is
  gone; a puff and sparks there) and a fresh one is in place once the boxes are down. (A door hinged at the mouth would
  have swung up above the clear space.)
- *The structure*: the 2x6 deck is walkable at 14 px everywhere (its front row too). The clear space was measured from
  the model (every part, lying to raised, every 0.05 s): two blocks over the deck (but its back row's Launch Boxes),
  the third block up over its first five rows, the fourth over its first four, the fifth over its second and third.
  The plan's pattern had missed three of those places and asked for some the boxes never reach.
- *Tests*: `CruiseTests` (it forms; a block where the boxes sweep stops it; it takes only cruise missiles and no other
  launcher takes them; it fires both, 3 s apart, from its boxes' tails, and lowers them; a flight followed tick by tick;
  the Light assembler builds one). 246/246.

### 17.12 The Launch Control Station (2026-10-31; the user's plan)
The user's words: "essentially a way of networking your launcher across the whole world". The Launch Computer (§17.6)
stays the local console (its silos within 256 blocks); the station is the step up: every launcher on its network,
anywhere in its dimension, loaded or not.

**Research** (the Minuteman launch control centre; the Soviet command networks): a two-officer capsule deep underground
runs ten silos spread over hundreds of square kilometres by buried cable and radio; its console has a status board with a
row of lamps for each missile, the two launch keys too far apart for one person, and the launch orders arrive as
authenticated codes (the "Gold Codes" sealed in plastic: the "biscuit"). Salvos are timed so warheads arrive together, or
in an order; aimpoints are laid out as patterns over a target area. A launch order wakes a silo that has sat for years
on its own.

**How it works:**
- *The Launch Control Station* (`launch_control_station`): a multiblock 3 wide, 2 deep, 3 high. The desk is the front
  row (Machine Casing, the controller in the middle, Machine Casing); behind it the instrument bank, two Machine Casings
  high with the Energy Port in the middle of its bottom row, and an **Antenna** on top of the bank's middle: the
  network's uplink. Formed, a GeckoLib model draws it: the console desk (keyboards, the status board, the two key
  switches, the guarded LAUNCH button under its flip cover, a red telephone, a teletype printer) and the bank behind
  (the big tactical map screen in the middle, status screens and lamp panels to each side, a clock, louvres), a small
  satellite dish on a mast on top. The big screen shows the network's state live (its code, how many are linked and armed,
  the soonest countdown or missile in flight); the status board's lamps show each of the first 16 launchers (green ready,
  red armed, flashing amber counting down or in flight, dark otherwise). 40 FE a tick (50,000 buffer); in an EMP it goes
  dark like the computer.
- *Launch Codes* (`launch_codes`, paper, a gold nugget and red dye): a sealed codes card. Used on a station it takes that
  station's network (its tooltip then shows the code, e.g. `K7Q-2XM`). Used on a launcher's controller or any formed
  part of it (a Launch Silo, Launch Complex, Missile Silo, Cruise Missile Launcher, SCUD Storm or Nuclear Missile) it
  links it to the network; on a Radar Array it links the radar (the station then shows what it tracks); sneaking, it
  unlinks. Used on another station, that station joins the network (two control rooms, one arsenal). The station's
  **NEW CODES** gives the network a new code: every card made before it is void (the links stay).
- *The network* (`LaunchNetwork`, saved with the world, `launch_networks.dat`): each network has its code, its stations
  and its members (at most 32). A member's record is what it last reported (kind, readiness, countdown, armed, target,
  range, strike, the missile or ammunition loaded, its timing) and is kept while its chunks are unloaded: nothing
  changes there while nothing ticks, so what the station shows stays true. A loaded launcher reports twice a second.
  A broken one is **No contact** (red) until it is unlinked or rebuilt in its place.
- *Orders and waking*: aiming, arming and launching go to the member at once if it is loaded; if not they wait in its
  record (shown as pending) and the network **wakes** it: a chunk ticket on its chunks (the structure's box), the orders
  applied as soon as it is there, and the ticket kept while it counts down and launches and for 15 s after (the doors
  shut, the boxes come down). The missile's flight already runs and loads its own way (§17.9). A launcher needs its
  energy already stored (its generators may not be loaded), a superweapon only charges while it is loaded.
- *The screen*: a green phosphor map of the whole dimension (the terrain survey now maps the ground round every network
  member and station as round computers and radars; zoom to 1:128) with every member, its target and its strike, the
  network's missiles in the air (amber) and what its linked radars track (red); a list of the members (number, kind,
  state, distance to its target) and a log (launches, impacts, missiles lost, orders that failed). Click a member to pick
  it, shift-click to pick several; click the map (or type X and Z) to aim the picks: several picked take a **pattern**
  round the aim point (point, line across the line of attack, ring, grid) at a spacing. ARM arms the picks (each must be
  ready with a target in range). LAUNCH fires every armed member: **at once**, or **time on target** (each launch held
  so all the first warheads arrive together: the countdown, the ignition and the flight of each worked out from its own
  launcher and its own target). UNLINK drops the picked member.
- *Sounds*: the key switch (ARM), the cover, button and launch tones (LAUNCH), a teletype burst at the station as each
  report comes in (launched, impact, lost).
- *Recipe* (§26): a machine casing, 3 CRT displays, a military circuit, an antenna and 2 levers.

**As built (2026-10-31):**
- *Code*: `LaunchNetwork` (saved data: networks, members with their `Report` and `Orders`, the log; `tick` applies orders,
  wakes, lets sleep, marks lost contact and follows the network's missiles down), `LaunchStationBlockEntity` (its network,
  the scope for its screen every half second, what its model shows), `LaunchCodesItem` (`onItemUseFirst`, so a launcher's
  own screen does not open), `StationPayloads` (Aim, Arm, Fire, Unlink, NewCodes; the Scope). Launchers report through
  `Launches.track(level, site)` from their tick; `LaunchSite` gained `shownStack()` and `timing()` (`Timing.toImpact`:
  countdown and ignition, then `Flights.flightTicks` or a cruise plan's ticks). Each launcher tells the network as a
  missile leaves (`LaunchNetwork.launched`). A woken member's tickets are `GBLaunch.NETWORK_TICKETS`, owned by its
  controller's position, dropped as the world loads. The map's tiles come through `MapSource` (the computer bounded, the
  station not).
- *Patterns* (`LaunchNetwork.points`): a line across the line of attack (square to the way from the picks' middle to
  the aim), a ring of the spacing's radius, a square grid along the world's axes.
- *Model* (`tools/assets/launch_station.py`): the olive desk with drawers and a dark board; the raked panel leaning back
  30 degrees (a bone), the shelf behind it with its corner hidden in the plate; on the panel the status board (bones
  `lamp<i>_<state>`, numbered from the operator's left), the keys in brass collars (`key_l`, `key_r`, turned 90 degrees
  while anything is armed), the yellow guard and red button under a clear red cover (`cover`, lifted 105 degrees);
  keyboard, red telephone, teletype with its paper; the dark grey bank on a plinth, the big screen and two small ones
  (glass in bone `screens`, hidden while dark), louvres, drawers, lamp panels, clock, placard; on top the galvanised
  mast, a small white dish leaning 50 degrees and a blinking red beacon (bone `beacon`), a whip at the east corner; the
  Energy Port's plate on the back. `LaunchStationRenderer` writes LAUNCH CONTROL, the code, the counts and the status
  on the big screen. Sounds: `tools/sound/station.py`.
- *Tests* (`StationTests`): codes, links (a part reaches its controller), orders to a loaded silo, patterns, time on
  target (the near silo held by the difference), a silo 4,096 blocks off unloaded and woken to launch, lost contact,
  new codes.

### 17.13 The Laser Designator (2026-11-01; the user's plan)
The second item of the user's plan, after the Launch Control Station.

**Research** (the AN/PEQ-1 SOFLAM, the LLDR, Paveway and Hellfire): a ground laser designator is a boxy unit with a big
lens on a tripod or held to the eye, with a rangefinder and a reticle; it sends a coded pulsed laser (a 4-digit PRF code)
at the target, and a semi-active laser seeker in the weapon's nose homes on the reflected spot in the last stretch of its
flight, so it hits the spot even as it moves, and its builder's "fire mission" calls weapons onto the lased point. Smoke,
dust and cloud break the beam; a missile that loses the spot flies on as it was aimed.

**How it works:**
- *The Laser Designator* (`laser_designator`): held to the eye (as a spyglass, a 2x zoom) while you hold use: a thin red
  beam from it to the point under the reticle and a pulsing red spot there, seen by every player; on a creature the spot
  follows it. As far as its holder sees (its view distance; after the user's look, 2026-11-01: it was 256 blocks).
  Its HUD: a rangefinder (range and the spot's coordinates), its mode,
  its charge, the launchers able to reach the spot and the missiles homing on it. It runs on a charge (40,000 FE, 20 a tick
  while lasing: a hundred seconds; charged in a Battery Charger) and an EMP kills it for a while. Smoke (a smoke or gas
  grenade's cloud) in the way breaks the beam.
- *Linked to a network*: used on a Launch Control Station it takes the station's network code (as Launch Codes do), and
  its spot shows on the station's map; the station's **To laser** aims the picked launchers at it (in their pattern).
- *One launcher at a time* (redesigned 2026-11-01 after the user's looks: first a two-second hold that launched every
  armed launcher, then two modes that armed or launched them all): it works with one of its network's launchers loaded
  with a laser-guided missile, the first by default; the mouse wheel while zoomed picks the next (the hotbar stays
  put) and the pick is kept on the designator. A **left click while lasing** acts on it as it stands: not armed, it is
  aimed at the spot and armed; armed, it launches, and its missile then follows the laser of the player who launched it:
  that player leads it in, wherever it points (the station's log: "designated", "fire called"). Once it has launched
  the pick moves on to the next.
- *Armed targets*: zoomed, the sight shows where every armed launcher of the network is aimed (whoever armed it): a
  glowing sphere at the point clicked, against the face it was clicked on (one aimed from the station: on the top of
  its column), its edge lit like the ring a left click draws; red for the picked one.
- *Laser Guidance Unit* (`laser_guidance_unit`, a new guidance part for any missile): launched by a call for fire, once
  clear of its climb (an eighth of the way) it follows its launching player's laser wherever it points, its target moving
  up to 1.5 blocks a tick; without that laser it flies as inertial guidance (4% of the range) and from half way steers
  onto a spot within its reach of where it was aimed (48 blocks and 5% of the range) from a designator of its
  launcher's network (or, from a launcher on no network, its launcher's player). Cruise missiles follow their planned
  path and do not home.
- *Sounds*: the designator's switch-on chirp, the lock tone, the fire-called tones.
- *Recipes* (§26): the designator a sensor array, a redstone torch, a military circuit, a battery and glass; the guidance
  unit a military circuit, a sensor array and a servo.

**As built** (2026-11-01):
- `laser_designator` (`LaserDesignatorItem`): used as a spyglass is (`ItemUseAnimation.SPYGLASS`, up to an hour); each
  tick lasing it draws 20 FE (none in creative), traces (`Designators.trace`: along the look over loaded ground only,
  8-block steps, to its holder's view distance in blocks (`Designators.range`: the server's view distance, or the
  client's if it asks for less, to 1,024); creatures along all of it picked first, searched 16 blocks of beam at a time;
  a smoke or gas grenade's cloud across it: no spot) and
  records the spot (`Designators`: per dimension and player, its network, live for 4 ticks). Every 5 ticks it sends its
  holder a `designator_status` (its pick: number, kind, place among the choice, state READY / OUT_OF_REACH / NOT_READY
  / ARMED; missiles homing; network; charge; the last left click's result for 2 s; the aims of the network's armed or
  counting launchers, `LaunchNetwork.armed`, and which is the pick's), only over a connection that has the channel.
  Used on a station (any part) it takes its network code into `LAUNCH_CODES`. The pick (`designator_pick`, the
  launcher's position): `LaunchNetwork.laserSelection` gives it while it is one of `laserLaunchers` (members whose
  reported missile has laser guidance, not counting down or ordered to fire), else the next of them linked after it,
  else the first; `laserStep` steps it. Zoomed, the client cancels mouse-wheel events (no hotbar change) and sends
  `designator_scroll`. A left click while lasing: the client takes attack clicks before the game drops them (it ignores
  attacks while an item is in use, `ClientTickEvent.Pre`) and sends `designator_click`; `LaserDesignatorItem.click`
  (one per 4 ticks) checks the holder is lasing, then on the pick: armed, `LaunchNetwork.fireLaser` (launched now, the
  order's `by` the holder, so the flight's owner; logged `FIRE_CALLED`); else `designate` (aimed at the spot and armed
  if it `canArm` it; logged `DESIGNATED`); and sends the status at once with its result. An EMP field sets its
  `EMP_UNTIL` 10 s ahead. (`designator_mode`, from the two modes, is still registered so a designator saved with it
  loads; nothing reads it.)
- Flights: a laser-guided flight carries a `Seeker` (its network, set when a network's launcher fires it; its aim; its
  reach, 48 + 5% of the range), saved with the flight. Ballistic only (cruise missiles keep their plan): from 0.12 of
  its flight it takes its owner's live spot (the player who ordered the launch) wherever it is; failing that, from half
  way, the live spot of its network's designators (no network: its owner's) within its reach of its aim, nearest its
  current target; and moves the target toward it 1.5 blocks a tick; with none it keeps its target.
  `GBAssembly.guidanceItem` gives each guidance's part back when a missile is taken apart (all three disassemblers).
- Client: `LaserFx` draws every lasing player's beam from the designator's laser port (0.68 blocks ahead of the eye;
  just under the eye in our own sight) in camera-facing pieces as wide on screen at any range (at most 16 blocks long,
  shorter near the camera so a beam passing close keeps its width), additive red over `textures/fx/laser.png`; the spot
  pulsing five times a second, its middle also drawn blended so it shows red on a bright face by day; a glint at the
  port; and after our own left click a ring closing on the spot (red for a launch). Zoomed, a sphere at each of the
  network's armed aims: a designated one touching the face it was clicked on at the point clicked (`LaunchNetwork.Mark`:
  the point and face, kept with the member and saved, used while the member is still aimed at that column; the trace's
  `Hit` and the `Spot` carry the face: a block's, or on a creature the side toward the holder); one aimed some other
  way on the top of what is solid in its column, found by scanning it (the height map the client is sent missed
  blocks built after it). At least 0.8 blocks across and as big on screen at any range; its edge lit, additive and
  again blended so it reads red by day. Our own beam is traced on the
  client once a frame (the sight reads the same trace) to our render distance; another player's beam goes to the spot
  the server traced: the server sends `designator_beam` (the holder's entity id, where it lands or that it lands on
  nothing) to every player tracking the holder, each tick it moves and every 5 ticks while still. Traced on the client
  from the holder's synced rotation (rounded to 1.4 degrees), a far beam would have missed the real spot by blocks. `DesignatorScope`: in first person the FOV
  halved and the mouse slowed to match, the hand and crosshair hidden, the eyepiece (`textures/gui/designator_scope.png`)
  with a lit duplex reticle, the readouts (the pick, red when armed, and the network across the top; range,
  coordinates, creature; homing, charge), by the middle the pick's state or a left click's result for 2 s (no tip on
  what a click does, at the user's wish), and brackets snapping onto the middle as you click; in third person the
  readouts under the crosshair.
- The station: its map draws the network's spots as pulsing red diamonds with their holders' names; **Laser** aims the
  picked launchers at the first spot (in their pattern); the log reads "Fire called at x, z: n launching".
- Models (`tools/assets/designator.py`, material library): the designator built optics along +y, the handle +z, so
  carried it points where the arm does; while it is in use the item swaps (`minecraft:using_item`) to
  `laser_designator_raised`, whose third-person transform (-110, 0, -15) undoes the spyglass arm pose, so the optics run
  along the gaze with the eyecup at the right eye; in the inventory on its side, lens to the left; the Laser Guidance Unit a gunmetal box, a white collar with a
  red band and a violet glass dome. Sounds (`tools/sound/designator.py`): the switch-on click, charging whine and chirp;
  the lock's three pips; fire called: squelch, a long tone, two pips, squelch.

### 17.7 As built: the Radar Array (2026-10-21, ahead of Phase 10)
- `radar_array`: a 3×3 Machine Casing deck (controller front middle, Energy Port back middle), three Radar Masts up
  from its middle, a **Large Radar Dish** on top (one item placing a 3×3 dish: a middle block that draws it all and
  eight parts; any part lost takes the whole dish, one item back); formed, a GeckoLib deck, mast and big dish turning
  once every 4.8 s.
  100 FE/t. Tracks every flight within 2 048 blocks (from `Flights`, so loaded or not); a flight coming down within
  512 blocks is **incoming**. Comparator output: the incoming count (≤15).
- Screen: the radar scope (moved here from the Launch Computer): a sweep matching the dish, blips painted only as the
  sweep passes and fading, a dashed red danger ring, a track list (distance or INBOUND, time to impact), and for the
  picked track where it lands and its height. The Launch Computer's map pans with a right-button drag; a left click aims. It is the track source the anti-missile defence (§18.2) will use.

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

### 18.4 As built: the Interceptor Battery (Phase 10, 2026-10-30; the user's request)
The anti-missile defence, as §18.2 has it with these choices:
- **The battery** (`content/defence`: `GBDefence`, `InterceptorBatteryBlockEntity`): a two-block tall machine
  (`TallMachineBlock`, GeckoLib), not a 2x2 base: a pedestal (plinth, NATO-green cabinet with louvres and status lamps,
  a steel deck) and on it a turret (turntable, cheeks, an electronics box with a spinning search antenna) carrying a
  launcher of four cells two by two with an electro-optical seeker on top. Its store (one slot, interceptor missiles,
  pipes may fill it) loads the cells one every 60 ticks (`RELOAD`, faster with speed chips); each loaded cell shows its
  interceptor's nose (`cell0`-`cell3`). 40 FE a tick while it watches, 4,000 a launch, holds 200,000. Redstone can stop
  it; upgrade chips fit it; an EMP field blinds it.
- **Engaging** (every 4 ticks): every missile in flight (`Flights`) past its pad and still up, whose target is within
  its reach of the battery: 256 if a working Radar Array tracks the missile (`Interception.tracked`: an array that
  tracked within the last 2 s and has the missile inside its 2,048-block box; arrays report themselves as they tick),
  else 96 (its own seeker). Missiles bound elsewhere (one launched from beside it) are never threats. It fires while
  fewer interceptors are on their way to that missile than its salvo (one; two for class L and up), if
  `Interception.solve` finds a meeting: the earliest tick k of the missile's flight still to come with the missile
  within reach of the launcher, at least 12 blocks above it, and reachable by then at 5 blocks a tick (its path 1.15
  times the straight line). One a look, so a salvo ripples.
- **Interceptors** (`Interceptors`, ticked after `Flights` each level tick, loaded or not; not saved): each flies a
  quadratic curve from its cell up (to 0.45 of the distance, at most 60) and over onto the meeting point, arriving on
  tick k. There its kill chance is rolled (`Interception.killChance`: 0.8 x class S 1, M 0.9, L 0.75, XL 0.6,
  intercontinental 0.5 x 0.6 without radar x 0.85 re-entering hot x 0.5 a superweapon's): a kill ends the missile's
  flight where it is (`Flights.shootDown`: no warhead, no impact) with a bigger burst (`interceptor_kill`, and a white
  flash for a nuclear warhead); a miss (or its missile already gone) bursts alone (`interceptor_burst`). Both profiles
  are air bursts that touch no ground. The battery is told (its tally). `InterceptorEntity` is only its picture where
  its chunk is loaded (as missiles' are), drawn from the Interceptor Missile's item model at 2.5 times, a small flame
  and a thin white trail behind it.
- **Turret** (client): the block entity syncs its aim (world yaw, raise) and how long it holds it (the interceptor's
  flight and a second, at least 3 s); `AnimatedMachine.boneRotations` (new, applied by `MachineGeoRenderer` over the
  animation) swings the turret 12 degrees and raises the launcher 6 a tick toward it, and back to rest (its front,
  raised 35) after.
- **Screen**: the store, three readout lines (cells ready and stored; radar tracking or seeker only, with its reach;
  threats, shot down, missed). Recipes: the battery from a machine casing, two servos, four steel plates, a military
  circuit and a sensor array; two interceptor missiles from a tactical body, a Solid Rocket Motor I, an inertial
  guidance unit, a sensor array (seeker and proximity fuze: there is no proximity fuze item) and an HE charge. JEI page;
  Field Manual "Interceptor Battery" (Ordnance, after the Radar Array).
- GameTests (`DefenceTests`): a missile from 180 blocks bound for 4 blocks from a battery is shot down before it lands
  (kill chance forced to certain); a missile flying out from beside one is let go; the kill chances and salvos. (Test
  batteries engage only their own test's missiles: one shot down the next plot's.) FX scenes `intercept` (a missile
  crossing the view from the north, met in the sky) and `intercept_close`.
- Not done: the Point-Defence Laser (§18.3), MIRVs, countermeasures, the Phased-Array module.

### 18.5 As built: the Interceptor Complex (2026-10-30; the user's request after the battery)
"A new multiblock version of the Interceptor that's more reliable, has further range (2.5x), and has a larger volley."
- **Structure** (`GBDefence.COMPLEX`): a 5x5 deck three high, the controller in the middle of a front row of Launch
  Pad; behind it six **Launch Cells** (new part block: four cells under hatches) three across and two deep, a Launch
  Pad either side and the layer over them required air (the hatches swing open there); at the back a deckhouse of
  Machine Casing two deep and three high, six **Phased Array Panels** (new part block) three across and two high in the
  middle of its front over the deck, the Energy and Item Ports low in its back wall. Formed, the deck's pads are cut
  to `PartCut.DECK` (14 px) and the controller to `CONSOLE_NORTH` (the Launch Complex's). A Structure Spawner. The
  controller: machine casing, two military circuits, a CRT display, a sensor array, a servo and two steel plates; a
  Launch Cell: a machine frame boxed in seven steel plates with a servo; a Phased Array Panel: four sensor arrays, an
  advanced circuit and four steel plates. **Dearer after the user's look (2026-10-31):** the controller is built round a
  whole Interceptor Battery, with a machine casing, two military circuits, two CRT displays, a superconducting coil and
  two dense steel plates; a Launch Cell is a machine frame boxed in six dense steel plates with a tungsten plate and a
  servo; a Phased Array Panel is four sensor arrays on a military circuit with four dense steel plates (the whole
  complex: 62 dense steel plates, 8 military circuits, 24 sensor arrays, 6 tungsten plates, a superconducting coil and a
  battery, besides its Launch Pads and casing).
- **Block entity** (`InterceptorComplexBlockEntity`, menu `interceptor_complex`): the battery's logic with these
  figures: 24 cells, loaded a second each from a store of three slots; 160 FE a tick, 4,000 a launch, holds 1,000,000;
  reach `Interception.COMPLEX_RADAR_RANGE` 640 where a Radar Array tracks the missile, `COMPLEX_OWN_RANGE` 240 on its
  own (2.5 times the battery's 256 and 96); kill chance from `COMPLEX_KILL` 0.95 (the battery's `BATTERY_KILL` 0.8)
  and never the seeker's six tenths: its radar guides them in; a volley (`Interception.volley`: twice the battery's
  salvo, two or four) fired two a look, a look every 4 ticks, each from the next cell in a spread order, its hatch
  swung open 105 degrees for 34 ticks (the cell's fire time synced; `boneRotations`, as the battery's turret); its
  interceptors climb up to 100 before they turn over (the battery's 60); a whole volley missed and time left, another.
  Reckoned from the launcher's middle; an EMP over its controller or its deckhouse knocks it out. Upgrade chips.
  JEI page; Field Manual "Interceptor Complex" (Ordnance, after the battery) with its structure page.
- **Both defences** now hear how each interceptor came out (`InterceptorLauncher.Outcome`: killed, missed, spent (its
  missile gone first, another's kill: not counted a miss), fried (an EMP on the way: `Interceptors.tick` bursts it
  where it is)); the battery's screen says when an EMP has knocked it out.
- **Model** (`tools/assets/interceptor_complex.py`, material library): a concrete walkway deck banded in hazard paint,
  cable trays down its sides; the launcher three haze-grey modules of eight cells, the hatches (bones `hatch0`-`hatch23`)
  over dark bores, uptake grates between each module's two columns, a hazard coaming; the deckhouse NATO green in
  panels with louvred sides, a door at the back between the ports' plates and one on the front's west pillar, a
  warning sign on the east one; its radar a grey face of elements in rows in a steel rim, a lamp strip under it; on
  its roof a parapet with red obstruction lamps, an air-conditioning unit and an IFF antenna turning while it works;
  the console olive with a green screen. Blocks: the Launch Cell's top four hatches in a hazard coaming over its grate,
  its sides haze grey with a stencil band; the Phased Array Panel's face of elements; the controller's placard an
  interceptor leaving its cell.
- GameTests (`DefenceTests`): it forms and meets a class L missile with a volley (two in the air at once), shooting it
  down; its controller alone fires nothing; its figures; and for both, an EMP over a battery fries its interceptor, a
  storm's strike beside it knocks it out. FX scenes `intercept_complex`, `intercept_complex_close`, `complex_ui`.
- It shoots down any missile coming down within its reach, the player's own too (as the battery does, at its smaller
  reach): its manual page says to stop it with redstone while firing close by.

---

### 18.6 The base alarm system (2026-10-31; the user's request)
"An alarm system, with all the works, that people can put in their base: something to send a redstone signal from the
radar array when a missile is detected, something to sound an alarm, and lights; everything people could want; please
research."

**Research** (what it is modelled on):
- *Civil defence sirens.* Mechanical sirens chop air with a motor-driven rotor; the big ones (Federal Signal's
  Thunderbolt, from 1952) turn a single large horn round on a pole, so the sound swells as the horn sweeps past. The
  Cold War signals: **Alert**, a steady tone for 3-5 minutes, and **Attack Warning**, a wavering tone rising and falling
  for 3-5 minutes; in Britain the steady tone was the **All Clear**. Sirens were started centrally (the UK's Handel
  system: carrier receivers at some 4,000 warning points and 7,000 powered sirens switched over telephone lines).
- *Missile alert systems.* Israel's Red Color: radar detects a launch, predicts where it will land and warns only the
  places in danger, 15 to 90 s before impact, a voice and then an intermittent tone, repeated until the last rocket is
  down.
- *Alarm tones* (fire and industrial alarm standards): ISO 8201's **temporal-three** evacuation pattern (three half-
  second pulses, a 1.5 s pause; NFPA's sleeping-area tone a 520 Hz square wave); the **slow whoop** (500 to 1200 Hz over
  3 s, a half-second gap); the **hi-lo** two-tone (800 and 1000 Hz, half a second each); ships' **general alarm** (seven
  short blasts and a long one); the electric bell; the klaxon horn; the US emergency broadcast attention tone (853 and
  960 Hz together).
- *Lights.* Rotating and flashing beacons, colour meaning (red: danger, act now; amber: caution; blue: a change, an
  action wanted; green: all well); strobes flash once or twice a second and are synchronised; bunkers' red emergency
  lighting.
- *Other mods.* Sirens are redstone-driven blocks (Sirencraft, PAM); Nuclear Tech's siren takes tracks and is heard up
  to 500 blocks off, its radar giving redstone on a detection; Create: Alerted and Asterion Alarms link alarms
  wirelessly by name or channel and flash them in sync with a rotating beam.

**The design.** Alarm devices talk over **alarm channels** (a number, 0-9999, as the Remote Detonator's frequency; 0 by
default, so a warning receiver and a siren placed anywhere in a dimension work together at once; set others to keep
alarms apart). A channel is raised while any of its **senders** has its condition (each refreshes it every half second;
it lapses a second and a half after the last), with what is wrong (kinds by priority: **Attack**, a missile coming;
**Radiation**; **Gas**; **Alarm**, raised by hand or by redstone), and for an attack how many and the soonest impact.
Every sounder, light and display goes off when its channel is raised, or when it is powered by redstone itself.
- Senders: the **Missile Warning Receiver** (listens to every working Radar Array, Red Color's way: it warns of each
  tracked missile predicted to come down within its warning radius, 64 to 1,024 blocks, so it alerts only where the
  missile is going; gives redstone, steady, by the number incoming, or rising as impact nears; plays the attention tone
  as a warning starts); the **Alarm Panel** (Sound, Silence and Test by hand; a redstone signal into it raises its
  channel; Silence quiets the channel's sounders, not its lights, until the alert ends); the **Hazard Monitor**
  (radiation over a set dose rate, 10 to 1,000 RAD/h, or poison gas at it).
- Sounders: the **Air-Raid Siren** (a motor and rotor under a big flared horn that turns round on top, Attack (wail) or
  Alert (steady), winding up and down, its sound swelling as the horn sweeps past, heard 160 blocks off; when an attack
  ends it sounds the All Clear, steady, for 15 s); the **Klaxon** (a wall horn: klaxon, slow whoop, evacuate (temporal-
  three), hi-lo, general alarm (seven and one), 64 blocks); the **Alarm Bell** (an electric bell, 48 blocks).
- Lights: the **Warning Beacon** (a dome on a base, any face; rotating, flashing (twice a second, in step with every
  other) or steady; red, amber, blue, green or white, set with dye; light 15 while on); the **Emergency Light** (a caged
  bulkhead lamp on any face: off until an alarm, or white always; red and flashing in alarm, every one in step; light 15).
- The **Alert Display** (a wall screen: ALL CLEAR in green, or the alert: an attack's count and time to impact, the
  monitor's reading) and the **Alarm Relay** (wireless redstone out while its channel is raised: shut blast doors, cut
  lights, start a machine).
- Every device opens a small settings screen (its channel and its own choices). None needs power. Field Manual: in
  Safety.

**As built** (`content/alarm`: `Alarms` the network, `AlarmDevice` the devices' roles, mounts, shapes and choices,
`AlarmBlock` (and `Six`, `Light`), `AlarmBlockEntity` (and `Animated` for the siren and the beacon), `AlarmPayloads`,
`GBAlarms`; client `AlarmScreen`, `AlarmSounds`, `AlertDisplayRenderer`, `VariantGeoModel`):
- Senders refresh their alerts every 10 ticks (lapsing after 30); listeners look every 5. The receiver counts each
  `Flights` flight past its pad that `Interception.tracked` says a working Radar Array has, whose target is within its
  radius; the monitor reads the dose rate (sources through the ambient shielding, plus radioactive blocks) and the
  strongest gas in the block in front of it. A siren keeps sounding the All Clear 300 ticks after an attack on its
  channel ends. Silence lasts until the channel goes quiet; a test sounds it 100 ticks.
- Sounds (`tools/sound/alarms.py`): the siren's wail (an 8 s loop: two rotors a minor third apart, chopped air, an
  outdoor echo) and its steady tone; the klaxon (a-oo-ga, sagging as it is let go); the slow whoop (500-1200 Hz, 3 s);
  evacuate (ISO 8201's temporal-three at 520 Hz); hi-lo (800/1000 Hz); the general alarm (seven and one); a trembler
  bell (18 strikes a second on a gong's modes); the attention tone (853 + 960 Hz). On the client (`AlarmSounds`) each
  sounder's loop follows its block; the siren's pitch and loudness wind up over 2 s and coast down over 3.5 s, and its
  loudness swells as its horn (turned on the client, a turn in 8 s) points at you.
- Models (`tools/assets/alarms.py`, material library): wall devices as block models with lit variants (the lamp
  elements emit light); the klaxon and the bell built pointing up with round parts as prisms of planks and laid onto
  the wall by their block states; the Emergency Light's red look an animated two-frame texture (all in step); the
  siren and the beacon in GeckoLib, the siren's square horn flaring from its drum (its side planks turned the other way
  for GeckoLib's mirrored x), the beacon standing up and turned to its face by `MachineGeoRenderer.standing()`, its
  colours as textures chosen through `AnimatedMachine.textureVariant` and `VariantGeoModel`.
- After the user's look (2026-10-31): the **klaxon** rebuilt (its rings were sunk a step a plank, "full" stagger, so their
  rims looked ragged, and its bell was a flat disc): every ring now sunk only 0.06 px every other plank, each ring's
  overlapping middle covered by the ring beside it; a saddle bracket, a terminal box on the housing, a throat and a
  projector flaring ring by ring (radii 2.0 to 4.2, 0.8 px a ring) to a rolled chrome rim, its mouth dark. The
  **siren**'s horn: its top and bottom planks turned the other way (they narrowed to the mouth; only the sides had been
  turned for the mirrored x), and the square plate on the drum gone, the drum its own round top under its cap. Then
  (the user: the funnel's sides still not flush, clipping) the horn rebuilt: a flared square horn's walls are trapezoids,
  which rectangles cannot fill (no rectangle fills a sharp corner), so each wall is a bone leaning out from the throat
  holding three plates: a centre band (2.8 px wide, standing 0.25 px proud on both faces, its edges painted as a seam in
  shadow) and two side plates fanned in the wall's plane so their outer edges lie exactly on the horn's corner lines
  (adjacent walls meet flush); the side plates reach the band's edge at the mouth and overlap each other under it near
  the throat. The throat 4.8 px square (inside the drum's height with the band's 0.25), the mouth 10, every plate a pixel
  and a half back into the drum, the slanted plate ends inside a deeper rim (1.2 px). FX scene `siren_close`. The
  **beacon**'s lens body has its top ring (it was open, so you could see into it round the lens top). The **Emergency
  Light**'s cage wraps the lens: two hoops round its sides and front, two over its top, front and bottom, back to the
  base, painted clean dark steel.
- The display's text is drawn by `AlertDisplayRenderer` (as the timed bomb's clock), lit. Lights: the beacon 15 while
  on; the emergency light 15 while white or red.
- GameTests (`AlarmTests`, each on its own channel): the receiver warns and the siren sounds; the panel sets off every
  kind of device on its channel and none on another; silence and stop; the All Clear; the monitor on radiation and on
  gas; the network's ranking. FX scenes `alarms` (a wall of every device at midnight), `alarms_close`, `alarm_ui`.

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
- *Superseded by §19.5 (2026-10-23): ammo built at the War Factory, the game's own above-ground launcher.*

### 19.3 SCUD Storm (GLA)
- **Structure:** 7×7 footprint, 5 tall base. Scrappy, improvised: rusted and patched plates, visible welds,
  sandbags, camo netting, oil drums, salvaged truck parts. A rack of **9 SCUD missiles** on hydraulic arms that raises
  from horizontal to vertical when firing.
- **Charge:** 5:00 at 800 FE/t.
- **Firing:** rack raises (3 s), the 9 SCUDs fire in quick succession (one every 0.6 s) at the target area with
  spread r 16. Each SCUD: HE blast 4 + **Anthrax α** cloud r 7 for 60 s.
- *Superseded by §19.5 (2026-10-23): the game's own radial layout, ammo built at the Arms Dealer.*

### 19.5 The SCUD Storm and the Nuclear Missile, as the game has them (planned and built 2026-10-23)
The user's request: the two main C&C Generals superweapons of the GLA and China as multiblocks that look like the
game's, each taking its own ammo, made at a C&C-themed multiblock of its own (the user chose one per faction over a
shared one), with effects to match ("like the toxic waste spread on the ground").

**Research** (the game's models, the C&C wiki):
- *SCUD Storm*: a big square sand-coloured pad, about twice the size of the other superweapons. Nine SCUDs (white
  bodies, green noses and fins) lie like the spokes of a wheel, tails in, each on its own launch crane, round a
  central green toxin tank with a biohazard mark on a rusty base. A low adobe wall runs along two sides with two
  square adobe watchtowers (one with an orange dome, one with an antenna), a guard hut flies the GLA flag, crates lie
  about and steam rises from vents in the pad. Firing, the cranes raise the missiles upright and nine SCUDs go one
  after another; they come down at random over a wide area, "explode with devastating force" and leave deadly toxin
  clouds behind ("prolonged contamination"). 5:00 between strikes.
- *Nuclear Missile* (China): not a true silo: an above-ground "coffin" like the SM-65 Atlas-D launchers, storing
  the missile lying down and raising it upright before launch. A long hall with fieldstone lower walls, grey upper
  walls between green steel pilasters and a brown pitched roof in sections with red ridge stripes and gold finials, a
  red star on its white gable; an octagonal control house with a conical roof and two antenna masts beside a fenced
  yard, a fan unit on the wall, a rack of shells. Ready, the roof over the missile is gone and two hydraulic rams
  have raised the silver missile (red bands) upright beside a green lattice tower. It leaves radiation round the
  blast. 6:00 between strikes.
- *Arms Dealer* (GLA): a walled compound of tan adobe with green trim: a workshop behind big dark double doors, a
  corrugated awning over racks of rockets and crates, a tiled-roof annex, a smoking chimney.
- *War Factory* (China): an octagonal hall under brown pagoda roofs with red-trimmed eaves, cream walls with
  fieldstone corners, a garage door under a corrugated awning with a star; a red star badge on the apron, a gantry
  crane on rails in the yard (green lattice post, yellow boom with a claw), a floor hatch, a furnace annex with a
  red-and-grey striped chimney, a conveyor of crates.

**How they work:**
- *Ammo*: the **Toxin SCUD** (stacks to 9) is built at the **Arms Dealer** from a Medium Missile Body, a Solid Rocket
  Motor II, a Chemical Agent Canister (the anthrax) and a Toxic Waste Tank (the ground contamination); the **Nuclear
  Missile** at the **War Factory** from a Strategic Missile Body, two Liquid-fuel Stages, a Boosted Fission Core, a
  Strategic Warhead Casing and a GPS Guidance Unit. Each factory lays the missile out section by section while it
  works (the assemblers' way), its crane animated.
- *Charge*: loaded, a superweapon charges while powered (SCUD Storm 5:00, Nuclear Missile 6:00, as the game's timers),
  then it is READY. A **Launch Computer** within reach finds it (on its map and in its list, with its charge time),
  aims it, arms it and fires it with LAUNCH; range 4 096 blocks.
- *Announcements* (all players in the dimension): a superweapon detected (formed), ready, launched; an alarm.
- *SCUD Storm strike*: the cranes raise the missiles upright (3 s), then the loaded SCUDs (up to nine) fire one every
  0.6 s, each coming down anywhere within 16 blocks of the target. Each: a blast, a ground-hugging anthrax cloud, and a
  front of glowing **anthrax residue** sludge spreading out over the ground from where it hit (a ring growing over a
  second or so, splashing as it goes), lasting minutes and poisoning anything on it.
- *Nuclear strike*: the roof slides open and the rams raise the missile beside its tower, then it lights (exhaust
  pouring out of the hall) and climbs straight out; a big nuclear blast (between the fission and the boosted fission
  warheads') and heavy contamination, the game's radiation field.
- Both are multiblocks of new themed part blocks (adobe walls, the toxin tank, SCUD erectors; fieldstone walls, the
  missile erector) on Launch Pad decks, with GeckoLib models painted with the material library.

**As built (2026-10-23):**
- *Warheads* (`ChargeKind.superweapon()`, whole weapons: never fitted at an assembler, out of JEI's list): the **Toxin
  SCUD Warhead** (`scud_storm_warhead`: a heavy warhead's blast a third over, crater 13.3, wrecks to 21, deadly to 16,
  then its toxin; not nuclear, goes off however set off) and the **Nuclear Missile Warhead** (`nuclear_missile_warhead`:
  crater 68, wrecks to 100, deadly to 108, burns to 120, "20 kt", just under the fission warhead's blast; contamination
  200 blocks round for two days; fizzles if set off any other way). (After the first look the user rebalanced them: the
  nuke's radii and damage x0.8, from crater 85 and "25 kt"; the SCUD's blast and toxin x1.33, from crater 10, residue 16
  and cloud 12.) Their models are the
  missiles' noses (`tools/assets/warheads.py`): the SCUD's long toxin-green ogive, the DF-5 re-entry vehicle's silver cone
  with a red tip and a red star.
- *Missiles* (`tools/assets/missiles.py`): the **Toxin SCUD** (class M, an R-17 in the game's white and toxin green,
  green fins, graphite vanes, 70 px) and China's **Nuclear Missile** (class XL, a DF-5: two liquid stages of one width on
  four engines, silver, red bands and interstage, a big red star, 141 px).
- *The toxin strike* (`Charges.scud`, `ToxinSpreads`, `AnthraxResidueBlock`, `GasAgent.SCUD_TOXIN`): 8 ticks after the
  blast (its crater dug), a heavy vivid-green cloud fills 16 blocks round at 1.6 (it decays 0.7% a step: two minutes and
  more) and a front runs out 0.6 blocks a tick to 21 blocks, laying **Anthrax Residue** (solid near the middle, spattered
  toward the edge, down into the crater), spraying green and dosing what it washes over (4 toxin, Poison IV, nausea).
  The residue is the toxin grenade's sludge made worse: brighter (light 10 falling to 4), its texture animated (8 frames
  of bubbles welling, swelling and popping, a sheen drifting), fuming and bubbling with pops, 2.5 toxin and Poison IV
  every half second, drying in four stages of 2 min (+ up to 30 s): about eight minutes.
- *The superweapons* (`SuperweaponBlockEntity`, a `LaunchSite`): charge while formed and powered (SCUD Storm 6000 ticks
  at 400 FE/t, Nuclear Missile 7200 at 800; paused without power); readiness NOT_FORMED, EMP, HALTED, NO_POWER/CHARGING,
  NO_MISSILE (charged, empty), READY, COUNTING. A Launch Computer lists them by kind with the charge's clock, aims, arms
  and fires them (range 4096). LAUNCH: the warning to the dimension, the siren, the launchers raised (a GeckoLib
  "launchers" controller: raise / lower / lowered; it lowers only once that client has seen them up), a 200-tick countdown
  with beeps; then FIRING: the SCUD Storm lights one SCUD every 12 ticks from each raised SCUD's tail (the last loaded
  first), each aimed anywhere within 16 blocks of the target; the Nuclear Missile its one, within 3. Then 40 ticks up,
  60 lowering, and (if anything fired) charging again from empty. An EMP, a redstone halt or a broken structure scrubs a
  strike: what was not fired stays loaded, a strike that fired nothing keeps its charge. *Announcements* (DETECTED when
  first formed, READY, LAUNCHED): a bold line in chat in the faction's colour (GLA green, China red) and a tone
  (`superweapon.warning`, `superweapon.ready`) to every player in the dimension.
- *The SCUD Storm* (16 x 16 x 7, `tools/assets/scud_storm.py`): a 15 x 15 pad of Launch Pad behind an L of **Adobe Wall**
  (two high, a watchtower four high at each end), a 3 x 3 **Toxin Tank** two high in the middle and nine **SCUD
  Erectors** round it, the pad clear six high. The model: a sand concrete pad (slab cuts, a curb, launch scorch round the
  raised SCUDs' feet, oil, toxin stains, tyre tracks, steam vents), the adobe wall with its vigas, the orange-domed and
  the antenna watchtowers (slit windows, doors, a little radar turning while it charges), the green tank on its rusty base
  (straps, biohazard marks, a ladder, its sight gauge glowing) inside a ring of salvaged sheets as the game has it (an
  orange plate rusted through, galvanized and white corrugated iron, a dark rusted panel, a green plate off a tank; some
  leaning, gaps between some; a pipe and a fallen plate outside it), nine erectors at bearings 20 + 40k degrees (each built
  pointing to the front and turned by its bone: launch table, hinge, a boom with cradles under its SCUD, a two-stage ram
  whose rod slides out as the boom rises), a guard hut flying a waving GLA flag, crates and drums.
- *The Nuclear Missile Silo* (9 x 15 x 11, `tools/assets/nuclear_silo.py`): the hall of **Fieldstone Wall** two high on a
  floor of Launch Pad with the **Missile Erector** at its back, a 3 x 3 control house, a Gantry Frame tower nine high. The
  model: fieldstone below, grey walls between green pilasters with windows, the roof in four sections of brown tiles with
  red ridges (their halves fold up to stand open), white gables (the front's red star) with gold finials, the octagonal
  control house under its cone and aerials, a fan unit; inside, a fork erector (two rails beside the missile, so the
  raised missile shows between them, not behind a girder) on two rams raising the DF-5 onto its launch table, the green
  lattice tower's arm swinging in to it, its lamp blinking; vapour boiling off the raised missile, fire and smoke out of
  the hall as it lights.
- *The factories* (`AmmoFactoryBlockEntity`, `tools/assets/ammo_factories.py`): each takes its parts, one slot each, and
  sets to work by itself with all of them in, power and room: the **Arms Dealer** (9 x 7: an Adobe Wall compound round a
  yard of Launch Pad, its workshop at the back) builds a Toxin SCUD from a Medium Missile Body, a Solid Rocket Motor II, a
  Chemical Agent Canister and a Toxic Waste Tank (30 s at 160 FE/t); the **War Factory** (9 x 13: a hall and an annex of
  Fieldstone Wall with its chimney, a yard of Launch Pad, a Crane Rail) a Nuclear Missile from a Strategic Missile Body, two
  Liquid-fuel Stages, a Boosted Fission Core, a Strategic Warhead Casing and a GPS Guidance Unit (60 s at 480 FE/t). The
  missile is laid out section by section on its trestles or cradles (MissileCarrier) while the A-frame hoist or the gantry
  crane works; the models: the GLA compound (green coping, dark workshop doors, a smoking chimney, a tiled annex, an
  awning over racks of rockets, a gate arch with the GLA sign) and the War Factory (a two-tier octagonal pagoda hall with
  red eaves and fieldstone quoins, a roller door under an awning with the star, the glowing furnace and its red-and-grey
  chimney, crates riding a conveyor, the gantry crane: green lattice post, red machinery house, yellow boom, claw).
- Recipes: Adobe Wall x4 (mud bricks, sand, wheat), Toxin Tank x2 (steel plates, a pressure vessel, green dye), SCUD
  Erector (steel rods and plates, a pressure vessel, a motor), Fieldstone Wall x8 (cobblestone, stone bricks, a mossy
  cobblestone), Missile Erector (dense steel plates, two pressure vessels, two motors, green dye); the controllers: the
  SCUD Storm's a machine casing, two military circuits, a CRT, a sensor array, a Chemical Agent Canister and a lever;
  the Nuclear Missile Silo's a lead-lined casing, three military circuits, a CRT, a sensor array, red dye and a lever;
  the Arms Dealer's a machine casing, an advanced circuit, two motors, a servo, a steel plate and an Adobe Wall (a
  Medium Missile Assembler's rung); the War Factory's a lead-lined casing, a military circuit, a sensor array, two
  motors, a pressure vessel and a Fieldstone Wall (a Strategic one's). JEI: a Superweapon Ammunition category and
  information pages. Field Manual: a
  Superweapons chapter (five entries). Sounds: `superweapon/warning`, `ready`, `scud_erect`, `nuclear_erect`. Review
  tool: `tools/preview/superweapon_view.py` (a structure's missiles drawn at their bones as the animation poses them, and
  where the block entity lights each). GameTests: `SuperweaponTests` (8).
- *After the user's first look (2026-10-23):* the SCUD Storm grew from a 13 x 13 pad to 15 x 15 so its erectors could
  stand 9 px further out, past the new ring of scrap round the tank (their noses would have run off the old pad; a
  raised SCUD's tail is now 35 px out, the pad's middle 112 back), and its back-right watchtower now stands in the
  pattern's corner cell (the model had drawn it a cell outside the footprint). Flicker: the silo's gables lay in the
  plane of the end walls' faces below the eaves (both ends flickered): they stand 0.4 px back; the tank's hatch shared
  its caps' plane where its planks cross (now each sits lower than the last); the silo's knuckle, cheeks, lugs, tailplate
  and ridge ends and the SCUD erectors' lugs no longer share faces with their neighbours, raised or lying; round sections
  in these structures sink every other plank 0.15 px (warheads keep 0.06: these are seen from further off). The War
  Factory's crane seemed to float: its bogie was never painted (a clear texture draws nothing); it is a striped sill
  beam on four wheels on the rails now, and the Arms Dealer's gate sign has a back. Its conveyor's crates rode at three speeds and slid into each other as it worked: they ride at one now, a third of a loop apart. The factories' readout was one line
  high, so its two lines sat side by side at half width and long parts' names were cut with "...": the slots moved up
  4 px and the glass is two lines high; "Needs: %s" drops "x1". New checks: `tools/preview/zfight_script.py` (every face
  turned with its cube and bones, in any animation's pose, overlaps that something solid covers left out) and
  `tools/preview/unpainted.py` (faces a painter missed).
- Not yet: §19.4's doctrine modules and the generic upgrades, the Particle Uplink Cannon, a configurable range.

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
- **Radioactive blocks & items** (`groundburst:radioactive` tag with emission values): uranium ore (weak), uranium /
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
| 1 000+ | Lethal | rapid death (`groundburst:radiation`) |

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
- **Wasteland biome** (`groundburst:nuclear_wasteland`): painted over the scorch radius of nuclear blasts (config);
  grey-brown grass and foliage tint, dead-olive water, brown-orange fog, falling-ash ambient particles, no passive
  mob spawns. Burnt trees remain as charred trunks. Craters can flood into "crater lakes".

### 21.8 Phase 12 plan (2026-10-27)
The user asked for the features that are not weapons before any new bomb content: the aftermath first (this phase),
then world structures and loot (Phase 15). Built in slices:
- **12a Radiation and its instruments**: dose from sources, radioactive blocks and carried items, through shielding;
  sickness from the dose taken (§21.2), recovery, and medicine (Rad-Away, Potassium Iodide, Anti-Rad Injector) so it
  has a cure from the start; the Geiger Counter, Dosimeter Badge and Lead Container.
- **12b Fallout and scorched ground**: one wind per dimension; fallout plumes drifting downwind, laying radiation and
  Fallout Ash, ash falling round players under them; the terrain blocks of §21.7 and the nuclear terrain table; grass
  and crops dying in strong fields (§21.2); the cobalt plume (now a string of zones).
- **12c Protective gear and contamination**: Gas Mask and filters, Flash Goggles, Advanced CBRN Suit; contaminated
  players and items that keep emitting, and the Decontamination Shower washing them.
- **12d EMP everywhere** (§21.4): every machine, launcher, radar, superweapon, missile, fuze, powered item, and vanilla
  redstone.
- **12e The wasteland**: the biome painted over the scorch radius, the toxic and frozen ground, crater lakes.

**12a's radiation model** (it replaces §21.1's per-chunk cells):
- **Sources.** Each nuclear burst, fizzle, dirty blast and meltdown leaves an analytic source in the level's saved data:
  centre, radius, peak dose rate, half-life. Dose rate there = peak x (1 - d/r)^2 x 1/2^(age / half-life), halved for an
  airburst; a source ends when its peak falls under 0.5 RAD/h. Exact at any distance, nothing to write into unloaded
  chunks, cheap to read; 12b's plumes are sources too. Peaks and half-lives (the server's decay multiplier divides the
  half-life):

  | Source | Peak (RAD/h) | Half-life | Lethal (1 000 rad) at the centre |
  |---|---|---|---|
  | Tactical, fission, boosted fission, the Nuclear Missile | 60 000 | 8 000 ticks (6.7 min) | 1 min fresh |
  | Thermonuclear, Tsar | 80 000 | 12 000 ticks (10 min) | 45 s fresh |
  | Neutron (induced activity only; the burst is a dose of its own) | 30 000 | 1 000 ticks | 2 min fresh |
  | Dirty | 20 000 | 20 000 ticks | 3 min fresh |
  | Cobalt (cobalt-60) | 40 000 | 120 000 ticks (5 days) | 1.5 min fresh |
  | Fizzle | 20 000 | 2 000 ticks | 3 min fresh |
  | Reactor meltdown (r 48) | 40 000 | 4 000 ticks | 1.5 min fresh |

- **Emitters.** Blocks and items carry a radioactivity (`groundburst:radioactivity` data maps: RAD/h at one block for a
  block, per item for an item); the `groundburst:radioactive` tags list the same things. Blocks are read within 6 blocks
  of a body (a chunk section whose palette holds none is skipped), at full value within a block and by the inverse
  square beyond, shielded along the line between. Carried items count in full, armour and off hand included, unless
  they are in a Lead Container. Mobs feel sources only.
- **Shielding.** Half-value layers (the thickness that stops half) by block class, from tags: lead 0.2 blocks; dense
  (obsidian, concrete, Reinforced and Hazard Concrete, metal blocks) 0.5; any other full solid block 1; liquids 1.5;
  wood 2; glass 4; anything not a full block nothing. A source's field reaches a body by six axis rays of 8 blocks from
  its eyes (scaled so open ground reads 1): a one-block stone hut halves it, a buried bunker stops it.
- **Units.** Dose rate in RAD/h of real time; dose in rad, added once a second (rate / 3 600). A suit multiplies what
  comes from outside (sources, blocks) by 1 - its protection; Potassium Iodide halves all of it.
- **Sickness** from the dose (§21.2): I from 100 rad (nausea now and then, hunger x1.5), II from 200 (weakness, mining
  fatigue, hunger x2), III from 400 (a heart of damage every 5 s, slowness, no natural regeneration), IV from 600
  (wither bursts, the edge of sight blurring), lethal from 1 000 (a heart a second). The dose falls 1 rad a minute under
  0.5 RAD/h; death clears it. Undead, creative and spectator players are immune.
- **Geiger Counter.** Carried in either hand: clicks at a random (Poisson) rate rising with the dose rate, and a meter
  in the HUD corner (§24): an analog needle on a log scale (0.1 to 10 000 RAD/h) and a digital readout; in the hotbar
  only the digital readout. An alarm sounds above a threshold set by sneak-using it (off, 1, 10, 100, 1 000 RAD/h).
  Runs on its own charge (20 000 FE, 5 FE a second while on; Battery Charger), and dies in an EMP field.
- **Dosimeter Badge.** Anywhere in the inventory: the dose taken, as a film patch that darkens from white through
  yellow, orange and red to black, in its tooltip and under the Geiger readout.
- **Lead Container.** Nine slots for radioactive items only; what is in it does not count.
- **Medicine.** Rad-Away (an IV bag: -150 rad over 30 s), Potassium Iodide (tablets: half the uptake for 10 min),
  Anti-Rad Injector (-400 rad at once, weakness for 60 s). The shower no longer washes off sickness (a dose taken cannot
  be washed off; it washes contamination in 12c).

**As built (slice 12a, 2026-10-27)** (`content/radiation`, `client/radiation`):
- The plan's peaks hold; each source's half-life is set so its centre falls under 10 RAD/h when the stand-in zone it
  replaces would have ended (`RadiationSource.lasting`): fission 3 824 ticks (deadly for about 20 minutes, under
  10 RAD/h after two days), tactical 1 912, thermonuclear and Tsar 5 551, neutron 519, dirty 10 940, cobalt 60 150, the
  fizzle 1 094, the meltdown 2 005. The stats' "Fallout Lasts" is that time, unchanged. The cobalt plume's string of
  zones are sources too until 12b.
- Radioactivity (RAD/h), set for play after the user found carried items did nothing (2026-10-27; first set near
  real figures, a stack of uranium ingots gave 5 rad an hour): uranium ore 4 a block away, raw uranium block 30,
  uranium block 50, corium 20 000, cooled corium 3 000; carried, per item: uranium ore 2, raw uranium 3, uranium dust and
  ingot 4, raw uranium block 27, uranium block 36, enriched nugget 3, enriched ingot 25, depleted uranium 1.5 to 2, fuel
  rod 60, spent fuel rod 2 000, irradiated DU target 800, irradiated Li-6 target 250, plutonium nugget 25, ingot 220,
  nuclear waste 500, corium 5 000, cooled corium 800 (`GBRadiationData`). So a stack of uranium ingots brings sickness
  in about 23 minutes, a spent fuel rod in 3, a stack of waste kills in 2. Bundles and shulker boxes are looked inside;
  a Lead Container is not. The body mends (1 rad a minute) below 10 RAD/h (was 0.5: a uranium ore in a pocket stopped
  it); a new counter's alarm is at 100 RAD/h (was 10, which any uranium carried would set off).
- The neutron burst's 0-1 dose is now rad (x 4 000: its sick line, 0.03, is 120 rad); its lethal line still kills at
  once. Sickness is one effect, Radiation Sickness I-IV, kept up each second by the dose, which also gives the stage's
  vanilla effects; no natural regeneration at III and worse (heals of a heart or less are refused).
- The dose is a data attachment (`groundburst:radiation`) synced to its player only; the Geiger Counter and the HUD read
  it. Clicks: a Poisson process at 0.25 + 4 x rate^0.55 a second (was 12 x rate^0.75, which saturated at 6 RAD/h), saturating at 60 (about 140 RAD/h), up to three a tick. The meter: a
  yellow instrument face, a cream dial (green, yellow, orange and red bands by decade, ticks at 1, 2 and 5, labels
  outside the ticks) over an LCD; the needle eases toward the reading (0.35 s) and trembles a little above 1 RAD/h. In
  the inventory the counter is a sprite; anywhere else a model (a yellow case, the meter standing proud of it, the
  handle, the probe clipped along its side on its cable), chosen by the display context, as vanilla's spyglass.
- Sounds (`tools/sound/gear.py`): the alarm synthesized (two beeps of a 3.1 kHz tone and its odd harmonics: a square
  wave drawn sample by sample had folded its harmonics back down as stray tones); the switch, the lid and the injector
  from recordings already in the ledger. In the Sound Lab as "Radiation gear".
- Recipes as §26.8 (Rad-Away, Potassium Iodide and the injector changed there first). Field Manual: Radiation, Geiger
  Counter and Dosimeter, Lead Container and Radiation medicine entries; the reactor-safety, hazmat, shower and fallout
  pages rewritten for the dose. JEI info pages. GameTests: `RadiationTests` (8).

**12b's plan (2026-10-27)** (`content/fallout`):
- **Wind.** One per dimension (saved data), synced to its players: a heading (radians, 0 = south, as the radar's) and a
  speed. Each second it eases toward a target (a degree a second, 0.05 blocks/s a second); every 3 to 8 minutes it picks
  a new one, the heading turned up to about 40 degrees (never more than 90), the speed 1 to 3.5 blocks/s in clear
  weather, 2.5 to 5 in rain, 4 to 8 in a thunderstorm.
- **Plumes.** A nuclear ground burst (not an airburst: its fireball does not touch the ground), a dirty blast and a
  meltdown send the debris cloud downwind: a plume (saved data, synced) that moves with the wind each second, spreads
  as it goes (radius from half the burst's fallout radius, growing by half again over its reach) and lays its fallout:
  a radiation source each time it has moved 0.6 of its radius (peak half the burst's at first, falling as
  (1 - d/reach)^1.5; the burst's own half-life, so the weaker ones fade first) and Fallout Ash in loaded chunks under it.
  Reach: tactical 250 blocks, fission, boosted fission and the Nuclear Missile 600, thermonuclear 900, Tsar 1 400, dirty
  150, meltdown 400; cobalt's is its plume of old (1 100 blocks, its sources lasting the month). The neutron warhead and
  fizzles leave none.
- **Fallout Ash** settles in layers like snow (up to 8) on whatever is under a plume (a few dozen layers a second near
  the burst, fewer downwind); weakly radioactive (1 RAD/h a block away); shovelled away it drops nothing; it weathers
  into plain Ash over a couple of hours (random ticks). Players under a plume see ash falling round them, drifting with
  the wind (a particle of our own).
- **Ground blocks** (§21.7, the nuclear ones; toxic and frozen ground are 12e's; as built, all painted by us, see below): Trinitite, Glassed Sand, Slag,
  Scorched Earth, Scorched Stone, Irradiated Soil (faint glow, radioactive), Ash and Fallout Ash layers, Charred Log,
  Wood and Planks, Dead Grass Block, Dead Leaves, Withered Crops. Natural matter painted on material tiles (new ramps:
  ash, fallout, trinitite, char, slag, glassed sand, scorched soil and stone); Dead Grass, Dead Leaves and Withered
  Crops are vanilla's own grass, leaves and wheat drawn through a dead tint (a tint source of ours, drifting a little
  with the biome's colour), so nothing of vanilla's is copied and they follow resource packs.
- **The nuclear terrain table:** in the scorch band grass, dirt, podzol and mycelium become Scorched Earth; stone,
  cobblestone, andesite, diorite, granite and tuff Scorched Stone; sand Glassed Sand; logs and wood charred (their axis
  kept), planks Charred Planks; leaves and plants burn away, crops wither; snow goes, ice melts. In the fire band a
  scorched column may get a layer or two of Ash. The crater is lined: what stands round its rim turns (most of it) to
  Slag where it was rock, Trinitite where it was sand and Irradiated Soil where it was earth.
- **Plants die in strong fields** (§21.2): once a second, columns at random where a source gives 1 000 RAD/h or more
  (in loaded chunks, about one a second per 64 square blocks): from the top down, leaves die (Dead Leaves, which drop
  away over time), crops wither, grass, flowers and ferns go, saplings become dead bushes, and grass blocks die; a roof
  or a trunk spares what is under it. The neutron burst withers to the same blocks. Dead grass grows back once its
  ground is safe again (under 10 RAD/h, in daylight), now and then on a random tick.
- Recipes: a Charred Log or Wood makes 4 Charred Planks (planks for anything: tagged so) and smelts to charcoal.

**As built (slice 12b, 2026-10-27)** (`content/fallout`, `client/fallout`):
- As planned, with these choices made on the way. A plume's sources take the burst's own half-life (the weaker ones
  downwind fade first); its first source is half the burst's peak. Ash a second at a plume's start: tactical 15, fission,
  boosted and the Nuclear Missile 40, thermonuclear 70, Tsar 120, cobalt 60, dirty 20, meltdown 25. On the GameTest
  server plumes stay where they start and nothing withers on its own (tests run side by side: a plume would drift onto
  its neighbours' plots); tests move their own (`Plumes.step`) and wither their own columns.
- The nuclear table (`TerrainTable`): 40% of the fire band's flames are a layer or two of Ash instead (put down after
  the front like rubble); the crater's wall is lined out to 1.3 crater radii, three quarters of it. 26.3 took the grass
  blocks out of the dirt tag (`#minecraft:grass_blocks` now): the table, the crater's rubble and the withered crops'
  ground count both; Dead Grass is a grass block. Sandstone glasses and fuses as sand does (a desert is sandstone a few
  blocks down, and the screenshot world's flat desert is sandstone on top: its first crater showed no change at all);
  all the overworld's stone (deepslate too) scorches. In this profile's spokes about a third of the scorch band is
  scorched (the rest lies past the streaks' edge), as before.
- The fallout clouds overhead drift with the level's wind as the client has felt it (they had drifted each with a wind
  of its own). Falling ash: up to 7 flakes a tick round a player at a plume's heart, fewer to its edge and as it weakens,
  spawned upwind and above, only under open sky; a particle of our own (`AshFlakeParticle`) carried by the wind,
  fluttering, settling.
- Art (`tools/assets/aftermath.py`), reviewed on tile sheets and the preview renderer: Glassed Sand's crazing thinned
  (it first read as paving), Scorched Stone's soot worked into the material's value (it first read as camouflage), the
  charred bark's blocks and the log's end rings redrawn. A creative tab of their own, Ground Burst: Aftermath.
- Field Manual: Fallout and Scorched ground (Safety), the nuclear page's fallout paragraph; JEI info for both groups.
  The manual's layout now keeps a picture with the start of its text: a picture moves to a new page if its first
  paragraph would not fit under it (on every entry with several pictures, an item's words had begun the next page,
  beside the next item's picture).
  GameTests: `FalloutTests` (7, one a real tactical blast on sand); the cobalt test now checks its plume; the neutron
  test its dead blocks.
- After the user's look (2026-10-27): the blocks repainted to the texture remaster (§4.3): each a designed surface on
  its ramp, not noise with dots: pieces from a wrapping cell network, each with its own tone and rounded light, lit on
  their top-left edges and shaded on their bottom-right, dark seams between (glass nodules, glaze plates over grit, slag
  lumps with vesicles and rust runs, a burnt crust in plates with ash in its cracks, soil clods with the glow in their
  gaps, charred blocks with a dull sheen, long charred boards), ash in soft drifts shaded as relief. The dead plants are
  painted as our own (dry grass over dry earth, brown leaves with gaps, dead stalks), the dead tints dropped.
- A plume's ash no longer settles where a blast's crater is still being dug (`BlastManager.forming`): it had been left
  hanging in the air when the ground under it went. The crater's clean-up takes away a block standing alone inside the
  crater (nothing solid beside it or over it) and the thin pillar under it, and then sweeps the crater's floor column by
  column for any no ray came to (`sweepFloor`; one now and then had survived the first fix). A big crater's streaks are eased (from all
  of them at a 40-block crater to 0.7 at 200 blocks: `BlastJob.streaks`), so its fingers and spikes stop reading as
  spines at a thermonuclear size; the crater preview mirrors it. GameTests `crater_leaves_no_lone_blocks_standing_1..3`.
- After the user's look at those fixes (2026-10-27): the Dead Grass Block is vanilla's grass block through the dead
  tint again (the user liked it better than the repaint; the leaves and crops keep theirs), and Irradiated Soil is
  vanilla's dirt with the irradiation laid over it, as the user asked: an olive stain round six glowing specks set in
  the dirt's darkest gaps, two layers drawn over `minecraft:block/dirt` (the ores' way), fitted to vanilla's dirt read
  from the decompiled client and never shipped. Item sprites fixed: the Geiger Counter a pixel left (its probe's outline
  had fallen off the edge), Rad-Away without its line (bag, tab and port inside the sprite with their outline), the
  Lead Container on one centre column (its trefoil placard was half a pixel right), the injector's needle on the pen's
  axis. The hazmat icons redrawn on vanilla's armour silhouettes (§21.5's note under "Hazmat suits").


**12e as built (2026-10-27; the user: 12e and Phase 15 while away, nothing asked):**
- **The Nuclear Wasteland** (`groundburst:nuclear_wasteland`, datagen): never generated, painted. When a nuclear blast's
  job reaches its release, `Wasteland.afterBlast` queues two jobs a share a tick, each loading the chunks it works in a
  batch at a time and letting them go (only chunks the blast saw, so none is generated anew; the blast holds only the band
  round its front, so with nobody near, as for a missile fired at a far target, the first check on a generated world
  found 59% of the wasteland painted and no lake; tickets a stopped server leaves are dropped when the world loads): the biome
  painted over max(scorch, 1.5 craters), from 24 blocks under each chunk's lowest ground up (deep caves keep theirs),
  48 chunks a tick, resent to clients (`convertBiomes`, on by default); and the crater lake. The biome: grey-brown grass
  #8A8264 and foliage #7E7656, dead-olive water #5E6B3E (fog #343C22), a dusty sky #9C9384, brown-orange fog #9A7B5E,
  our fallout ash drifting (an ambient particle, 0.006), no music (silence, as the pale garden), monsters only
  (zombies, husks, parched, skeletons, spiders...; no animals). In 26.3 a biome's sky, fog, particles, music and spawns
  are environment attributes.
- **Crater lakes** (researched: Lake Chagan, the 1965 "Atomic Lake", filled from a river; Bikini's craters lie under
  the lagoon; the desert's Sedan crater stays dry): a crater of 6 blocks or more floods if water could get in. On rays
  out from the middle (one a block of the reach's circumference) the rim's crest is found between 0.85 and 1.8 craters;
  water seen out there (the sea, a lake, a river) floods it to that water's level, or groundwater does (the overworld,
  a biome with rain, the floor under the sea level) to a block under the sea level; never higher than a block under the
  rim's lowest point, and a column fills only inside the crest on its own ray and both beside it. A crater too big
  fills only to a pond in its deepest part (300,000 blocks of water at most: a fission crater full, a thermonuclear
  one's bottom). Planned from grids of every column's top, ground and water read first: the lake is the basin joined
  to the crater's middle under the level (so it fills each dip to its shore and stops at the rim), and the level is the
  highest whose basin stays inside 1.8 craters (a gap in the rim the rays missed lowers it) and holds no more than the
  pond (found by halving). Still water placed without block updates, 6,000 blocks a tick. Not where water boils (the
  Nether). Checked on a generated world (the world survey, §22.1): a tactical warhead with nobody near and one close by
  both painted all of their scorch and nothing past it; the inland crater filled to y 55 (93,000 blocks, a river past
  its rim lower than the rays thought), the coastal one to the sea's level (203,000), both still after their edges were
  stirred.
  The GameTest server skips both jobs (the tests run them themselves).
- **Frozen ground** (`Frost.freeze`, so every cryo burst): stone and grass open to the air rime to Frosted Stone and
  Frosted Grass Block (surely near the middle, 60% at the edge), small plants freeze to Frozen Plants (a tall plant's
  top goes), and ice Cryo Crystals (amethyst-cluster blocks, light 4) grow on floors and walls within half its radius
  (1.2 a block of radius). All of it thaws where snow would not lie (1/12, 1/8, 1/10 a random tick): back to stone and
  grass, the plants and crystals melt away.
- **Toxic Soil**: anthrax (the chemical warhead's, a toxin SCUD's) at full strength or more resting on soil sours it
  (every fourth gas step, 2% a block), and a toxin SCUD's residue sours the ground under it as it is laid (surely near
  the middle). It kills what grows on it (crops wither first, so they do not pop), breathes 0.05 of anthrax back up a
  random tick, and is spent to coarse dirt after about a day (1/18 a random tick). In `#minecraft:dirt`.
- Looks, the user's way (vanilla's own blocks where they exist): Frosted Stone is vanilla's stone with our hoarfrost
  feathered over it, fitted to the stone (a layer, as the soil's); Toxic Soil vanilla's dirt with khaki stains, bruise
  spots and spores fitted to it; Frosted Grass vanilla's grass block through an icy tint (#B4D0D6); the Frozen Plant
  vanilla's short grass through one (#A8CFE2); the Cryo Crystal ours, ice needles.
- GameTests `WastelandTests` (6): the biome painted and not past its edge, a crater flooded to the pool beside it and
  not above, a dry one, frost on stone, grass and plants with crystals, anthrax souring grass, a crop withering.
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
**lore notes** (an original Cold-War-flavoured story told across ~20 documents: the "Ground Burst" programme, test
logs, last transmissions), Geiger counters, hazmat pieces, ammo. Structures are authored as layouts in
`tools/structure` → NBT templates → jigsaw structures. Every variant must look great: weathering, rust, cracked
concrete, overgrowth, lighting, set dressing.


### 22.1 As built (Phase 15, 2026-10-27)
The user: all of Phase 15 but the lore notes, and no blueprints in the loot. Researched from the real sites (sources in
SESSION_LOG): the Titan II complex (silo, cableways, a blast lock with two doors interlocked, the portal's way down, the
three-storey launch control centre on springs, the 740-ton closure door on rails, work platforms round the missile),
flooded Atlas F silos (dived today), the Diefenbunker (a curved blast-diffuser entrance, blast doors, decontamination
showers, the situation room's floor-to-ceiling maps, a radio studio, filters, escape hatches filled with pea gravel),
DEW Line stations (a radome over a "building train" of linked modules), Trinity (the 8-foot-deep glassed depression,
the four concrete piers of the shot tower, the obelisk, Jumbo, instrument bunkers, earth-covered observation points
10,000 yards out) and the Nevada Test Site (Doom Town, the bleachers), the Damascus Titan II explosion, WIPP (panels of
seven rooms mined from salt, drums three high topped with magnesium-oxide sacks, filled from the back, closed with
bulkheads; salt creep closing rooms) and Lake Chagan.

- **One structure type, `groundburst:site`** (`SiteStructure`, `SitePiece`): a site is one or more templates about an
  origin on the ground, turned as one; each part has its own offset, terrain adaptation (NeoForge's
  `PieceBeardifierModifier`: a hardstand `beard_thin`, the rooms under it `encapsulate`, so a silo's shaft runs down
  from a pad that sits on the land), integrity (a `BlockRotProcessor`) and weathering (`Weathering`: rule processors,
  a share of stone bricks cracked and mossed, glass broken out, bars rusted through, torches and lanterns gone, planks
  rotted, concrete stained; `abandoned`, `end`, `none`). Variants by weight. Placement (set by the checks on generated
  worlds below): `surface` (the land under the parts that meet it, read on a 5x5 grid of columns, rising and falling no
  more than `max_slope`; dry everywhere over the whole footprint; on average no lower than the sea's level, or water
  pools on it), `buried` (`depth` under the lowest of that land, under dry land only), `floating` (10 to 20 blocks over
  one of the End's outer islands, never the bare void). Data markers (`stand:hazmat`, `stand:lead`, `stand:bare`) become armour stands in
  suits. Chests carry their loot table in the template (the game seeds them).
  Every part knows the site's ground. A part on the surface meets the land there (its ground-level delta), not at its
  bottom, so a test site's crater or a crashed missile's furrow lies in level land, not in a pit dug to its floor, and
  is filled under where it overhangs the land (dirt, or the ground's own block, as vanilla's desert temples and igloos).
  A part underground closes rock round itself only up to 13 blocks under the ground (encapsulation reaches about 12
  blocks past its box: it heaped a wooded mound over a bunker's blockhouse).
  The structure itself says `beard_thin`: vanilla's beardifier skips a structure whose own adaptation is none before it
  asks its pieces.
- **Templates by script** (`tools/structure/sites.py`, helpers in `build.py`, previews `tools/preview/site_view.py`:
  isometric, coloured from each block's own texture, cut away), checked that every block exists (26.3 renamed
  `chain` to `iron_chain`); structure, structure-set and biome-tag JSON written beside them.
- **The sites** (spacing/separation in chunks):
  - *Abandoned Missile Silo* (48/18, max slope 12; taiga, plains, snowy plains, desert, savanna, meadow), three variants: intact
    (3), flooded (2: water to the cableways, the shaft higher, glow lichen), collapsed (2: the door fallen in, rubble in
    the upper shaft and the cableway, the missile toppled). A gravel hardstand, the closure door's two steel leaves on
    rails, vents, the portal blockhouse, an antenna, floodlights, the guard shack, a chain-link fence fallen in places;
    below: the shaft (reinforced concrete, nine across, 33 deep), the Titan II (white, a dark band at the stage joint,
    the dark re-entry vehicle broken open, two engine bells), three work platforms folded back, the flame pit, a ladder;
    the cableway (a steel tube, cable tray, lamps) to the blast lock (two blast doors) under the portal's ladder, on to
    the launch control centre (corners cut): consoles, the two key panels far apart, the status board, the commander's
    desk; above, bunks, lockers, a galley; the escape tube filled with gravel.
  - *Command Bunker* (52/20, max slope 8; hills, forests, taiga, meadow, plains): the blockhouse whose doorway turns twice, sandbags,
    two air stacks, the radio mast; a ladder well to the blast doors, decontamination (showers, suits on stands),
    the corridor, the situation room (a continent in concrete on the wall with its strike marks, the plotting table),
    bunks with the gravel-filled escape tube, the radio room, the armoury, the plant room (filter banks, the diesel's
    block, the intake).
  - *Radar Outpost* (44/16, max slope 12; hills, meadows, plateaus, wooded badlands, snowy plains, plains): a three-module building
    train (white, a corridor through it), the radome on the radar module (random panels, a stretch fallen in, the
    antenna inside), quarters and the garage, masts; the long-range dish toppled beside its lattice stump; the
    watchtower; the fence.
  - *Test Site* (32/12 as the desert pyramids, max slope 12; desert, badlands), two parts: ground zero (the crater 2 deep, trinitite and glassed sand in the
    middle, irradiated soil in its ring, all radioactive as built; the four footings with twisted steel, the obelisk,
    a broken fence ring, Jumbo rusting on its side with one end blown off, two instrument towers, a charred Doom Town
    house, bleachers) and the observation bunker 30 blocks south (concrete banked with sand, the slit facing ground zero,
    the instruments, a periscope).
  - *Crashed Missile* (38/14, max slope 5; most lands): a furrow ploughed in and deepening to the impact crater, earth thrown up
    either side, scorched; the warhead split at the bottom with its uranium core bare (50 RAD/h a block off) and the
    ground round it irradiated, the body lying in the furrow with its stage band and the guidance bay's chest, the tail
    with its engine bells thrown aside, debris all along.
  - *Waste Storage* (44/16, at 35% of its places; anywhere in the overworld under dry land, 42 under the ground): drifts mined from banded salt (calcite,
    diorite, white terracotta), rails and roof bolts; rooms of Waste Drums three high in rows with white sacks on top,
    one room sealed by a bulkhead, one caved in on its drums; decontamination (showers, two hazmat suits and a
    lead-lined one on stands), the lead-lined hot cell (a reactor-glass window, the manipulator, a chest), the shaft's
    cage sealed by a concrete plug.
  - *Exotic Research Station* (64/24; over the End's outer islands): a deck on an inverted cone of end stone, hazard bands at
    its edge, a dome of reactor glass over a broken containment ring and a scorched pit, exotic crystals grown through
    the floor, two laboratories, the antenna with its dish bent over, debris drifting off.
  The five on the surface keep 6 chunks from where a village could stand (vanilla's exclusion zone, as its pillager
  outposts keep from villages).
- **Checked on generated worlds** (2026-10-27, the user's request; `gradlew runWorldSurvey`, `WorldSurvey`): a census of
  every place each site's set offers within 10 km of the spawn (20 x 20 km, seed "groundburst"), what becomes of each
  (a site, or the biome, water, the slope, the void), vanilla's sets counted the same way; then the nearest of each kind
  (each silo variant) and the one on the most uneven land generated in full, measured (a part's bottom left hanging,
  water in it, the steps where the land meets it, caves and water at the rock round a part underground, other
  structures in its box) and photographed from two sides, above, the ground and through the rock; then the two
  blasts (§21.7). The first census found the Waste Storage at every place it could stand (855, under the sea too), the
  research station every 200 blocks in the End (half of them over the void) and the Test Site at 4 in 419 km²; the
  first visits a silo's pad on a riverbank, a test site ringed by a lake, the bunker's blockhouse buried in a mound,
  test sites and crashed missiles sunk in pits, a pillager outpost against a silo. As built:

  | Site | Sites in 419 km² | Per 100 km² | Typical distance to the nearest | Vanilla alike |
  |---|---|---|---|---|
  | Abandoned Missile Silo | 34 | 8.1 | 1.7 km | pillager outposts 9.1 |
  | Command Bunker | 57 | 13.6 | 1.3 km | igloos 15.5 |
  | Radar Outpost | 37 | 8.8 | 1.8 km | pillager outposts |
  | Test Site | 7 | 1.7 | 4.1 km | desert pyramids 1.9 |
  | Crashed Missile | 87 | 20.7 | 1.1 km | |
  | Waste Storage | 109 | 26 | 1.1 km | (hidden; trial chambers 329) |
  | Exotic Research Station (End) | 180 | 43 | 0.7 km | end cities 87 |

  On the land they stand on: no part of a pad left hanging (bar the silo door's leaves over its shaft), no water on any,
  the land round each within a block or two of its ground (the steepest silo on a terrace cut into a savanna hill); the
  rooms underground closed in (under 1% of their shell open to caves, but where a mineshaft runs past). `/locate` agrees
  with the census for every site (in the End, as vanilla's search does, it may give a farther one first).
- **Loot** (`GBChestLoot`, nine tables): the silo's control centre (circuits, now and then guidance units and hardened
  electronics; dosimeters and medicine; paper, wire, food) and its shaft (the **Damaged Warhead** three times in five,
  steel, propellant, a motor); the bunker's armoury (grenades, dynamite, C4, mines, a detonator; worn hazmat pieces) and
  stores (food, medicine, instruments, batteries); the radar outpost (circuits, a sensor array, wire, motors, servos);
  the test site (dosimeters, Geiger counters, lead, trinitite, now and then a Lead Container); the crashed missile
  (the warhead two times in three, guidance, propellant, steel); the waste store (worn hazmat and lead-lined pieces, lead,
  waste, medicine, rarely a spent fuel rod, rarely a Lead Container); the research station (exotic shards, stabilised
  matter, containment cells, coils, rarely an antimatter cell or a singularity seed). No blueprints, no lore notes.
- **New things:** the Damaged Warhead (radioactive, 40 RAD/h: carry it in a Lead Container; the Chemical Plant leaches
  it with sulfuric acid: four enriched uranium nuggets, two plutonium nuggets half the time, two steel plates) and the
  Waste Drum (radioactive, 8 RAD/h a block off; it breaks to nuclear waste), painted with the material library.
- Field Manual: Safety gains *The wasteland* and *Old sites*. GameTests `StructureTests` (4): templates load, the
  structures, sets and biome tags are registered, every loot table rolls, a site piece places its chest with its loot
  table and turns its marker into a suited stand. FX scenes `site_*` (each site placed as generation would; the
  underground ones in stone, cut open) and `wasteland_aerial`.

### 22.2 The rebuild (2026-10-29)
The user's look at the sites: the building is poor (the radar base and the test site worst: Jumbo's rusted cylinder, a
toppled dish that does not read as a dish), the radar base's iron bars and mast a block off the ground. Every site is
rebuilt from scratch, after research on building in Minecraft, with new cosmetic blocks and two new multiblocks.

**What was wrong.**
- *A block off the ground* (every surface site): a site's ground is the land's top block, and its parts meet the land
  there, but the beardifier takes `minY + groundLevelDelta` as the first block of *air* (vanilla's beard makes the
  blocks below it solid and from it up air). The land was smoothed a block under each site: pads stood a block proud,
  and fences, legs and masts standing on bare ground floated. The delta is one more.
- *Noise for texture*: blocks mixed half and half at random (the test site's crater a speckle of five blocks).
- *Round things in whole blocks at small scale* (the dish, the radome, Jumbo): blobs.
- *Boxes with flat walls*, nothing tall or recognisable from afar.

**How the sites are built now** (the research: Minecraft's own guide and builders' guides, the shipwreck guide on
decay; sources in SESSION_LOG):
- *Silhouette first*: each site has a landmark that reads from a distance (the radar base's tower and radome and its
  search antenna, the test site's shot tower, the silo's doors between floodlight poles, the bunker's radio mast), and
  real proportions: doors two high, storeys three or four, windows in rows.
- *A palette of three to five blocks* per site with a role each (main, trim, accent); variety in gradients (dirtier
  low, cleaner high), never an even random mix. Wear in patches that follow cause: moss low and on the shaded side,
  rust streaks under metal, roofs and top courses fail first, debris under each hole, grass taking back the gravel.
- *Depth in every wall*: pillars or frames every few blocks, windows set in with sills, parapets and overhangs.
- *Curves as models*: a dish or any small round object is a block model or a multiblock, never a heap of blocks; a
  radome is built at least 13 across from panels, smoothed with their stairs and slabs.
- *On the land*: irregular edges to pads, tracks leading in, posts and legs down to the ground, the site's ground the
  land's own top block.
- *Reviewed in game*: each site photographed where it generates and on flat land at noon from several sides (the
  `site_*` FX scenes and the world survey), not judged from the isometric preview alone.

**The building set** (the user's choice: craftable, so players can build with it; each drops itself, so a site gives
no free iron), in a creative tab of its own, Ground Burst: Construction:
- Chain-link Fence (connects as iron bars do) and Barbed Wire (coils along a fence's top, or on the ground);
- Corrugated Steel and Rusted Corrugated Steel, each with stairs and a slab (cladding and roofs);
- Steel Panel and Rusted Steel Panel (riveted plates); Steel Grating and its slab (catwalks you see through);
- I-Beam (a girder set on any axis) and Lattice Frame (an open braced cube: towers, masts, gantries);
- Radome Panel with stairs and a slab;
- Floodlight (a lamp head facing out from a wall or post), Sandbags and their slab, and warning signs (radiation,
  high voltage, keep out, toxic).
Made from steel plates and rods (the stonecutter cuts a plate into panels, sheeting, grating and fencing), white
concrete (radome panels), sand and string (sandbags); the rusted ones from eight clean ones round a water bucket.
- **See-through blocks** (after the user's look, 2026-10-30): the Lattice Frame, the Steel Grating and its slab hid
  the faces between them as glass does, so looking into a stack of them showed nothing of its far side. Their models
  (`construction.two_sided`) now draw every outer face twice, outward and inward (a copy 0.01 px inside it, as vanilla's
  scaffolding does, its texture mirrored so its bars stay put), each culled by its side; and they leave out a face
  only where one of the three covers it whole (`SeeThrough`: a full block's face, a slab's half; a slab beside a full
  block leaves its half out and the block keeps its whole face, drawn both ways), so a 3x3x3 of lattice shows its
  outer frame from outside and its far sides from within, and no two faces of theirs lie in one plane facing one way.
  GameTest `see_through_blocks_show_only_their_outside`; FX scenes `see_through` and `see_through_low`. After the
  user's next look the Lattice Frame's face is framed on all four edges (a top rail as wide as its bottom one), so side
  by side or stacked its joints are bars twice as wide all round.

**Two multiblocks.**
- *Large Silo Door* (the user's idea): two leaves that slide apart on rails over a 7x7 opening, opened by a redstone
  signal (the user's choice), with a hydraulic sound (its own since Phase 11, as long as the leaves take: §16.12).
  Purely for show: no missile, nothing to fire. One item places the
  door and its rails; the sites use it rusted and stuck part-open.
- *Search Radar*: a wide curved reflector on a turning head over a pedestal (the AN/FPS-20 of the Cold War stations),
  turning while it has a redstone signal; the sites' ones are stopped, or toppled and broken.

**The Missile Wreck** (the user's choice over a missile of blocks, 2026-10-29): a prop drawn by its block entity
(GeckoLib, `tools/assets/missile_wreck.py`), a two-stage missile in the test ranges' white and black roll pattern
broken in three: the tail with its engine bell and fins on the block (propped on two fins, the third bent, the fourth
torn off and lying apart), the body torn at both ends and buckled, the nose with its guidance bay (a red band, a
trefoil) and charred re-entry cone driven in nose first; the interstage ring, a curl of skin and the cable bundle strewn
between. The skin is a true sixteen-sided tube a pixel thick, one plank a facet, so a torn end shows its wall, a ragged
edge (each facet torn to its own length) and the dark inside with the tank dome behind. It runs some eight blocks on
from its block toward its facing, the nose a few blocks down; only the tail's block is solid. In the Construction tab,
drops itself, no recipe (as the radar's wreck).

**The sites, from scratch** (in this order): the Radar Outpost (a lattice tower with a radome, the search radar on its
own tower, a building train on piers, a tropospheric-scatter billboard, power poles, the fence); the Test Site (the user's
choice: a glassed crater from an earlier shot and a lattice shot tower readied for the next, cabled to instrument
bunkers, a Doom Town street of houses with mannequins; Jumbo goes); the Abandoned Silo (the surface after the Minuteman
and Titan II launch sites: a raised gravel platform, the Large Silo Door, floodlight poles, the support building, the
antenna, chain-link and barbed wire); the Command Bunker, the Crashed Missile, the Waste Storage and the Exotic Research
Station after them.

**As built so far** (each photographed in game on flat land from seven sides, `gradlew runSiteShots -Psite=<site>`, the
parts underground cut open, `-Psite=<site>:cut:<dz>`; the photographer's flat world is seventy layers of stone deep so
they stand in rock):
- *Radar Outpost* (`site_radar.py`): the concrete radar tower and its radome, the search radar's lattice tower with the
  wreck beside it, three modules on piers, the scatter billboard, poles and wires, the fence and gate.
- *Test Site* (`site_test.py`, two parts): the shot tower (a lattice tower on splayed legs, the cab with the device on
  its chains, the hoist, guys), instrument masts turned on it, the control bunker under its sand bank (the periscope, the
  door down a cut in the bank), Doom Town (a two-storey house and a bungalow, mannequins in clothes, a picket fence with
  gaps); 48 blocks east the earlier shot's crater (rings of trinitite, glassed sand, irradiated soil and scorched earth;
  the old footings, the obelisk, the fence ring with radiation signs, a charred house, the bleachers).
- *Abandoned Silo* (`site_silo.py`, three variants): above, a gravel hardstand going back to grass, the Rusted Large
  Silo Door on its rails (stuck an inch ajar; half open over black water; gone into the shaft, its rails torn), the two
  vents, the portal blockhouse over the ladder down, the support building, the antenna, floodlight poles at the door's
  ends, the fence, gate and guard shack; below, the shaft round (seven across, squared to the door's opening at its
  head, as the Titan II's round silo under its square door), a work platform folded out round the missile at its stage
  joint and a ledge lower down, cable runs, lamps, the ladder; the Titan II (white, grimed in calcite, the dark band, the
  re-entry vehicle a cone of stairs under its spike; broken at the joint when collapsed); the cableway, the blast lock,
  the control centre two storeys high; flooded to the cableway's floor in one variant.
- *Command Bunker* (`site_bunker.py`, after the Diefenbunker and Kelvedon Hatch): above, an earth berm (a dome whose
  contours wander, shrubs on its lower slopes) with the concrete portal in its south face, wing walls holding the bank,
  hazard stripes round the opening and floodlights over it; inside, a vestibule whose deflector wall stands square
  before the first blast door; the guyed radio mast and its tuning hut, a wire antenna between two poles, the intake and
  exhaust vents (louvres under mushroom caps), the generator's stack and its day tank, the escape hatch (a copper lid on
  gravel), the helipad, the guard's hut and sandbags inside the gate, gravel tracks worn between them all; below, twelve
  steps down under a stepped ceiling to the second blast door and the decontamination room (white tile, showers over
  drains, suits on stands), the main corridor (a green dado, chamfered ribs, a copper pipe and a cable on its ceiling),
  the radio room, the armoury's cage, the plant room (generator, fuel tank, filter bank under the ducts, switchboard),
  the dormitory (bunks two high, steel lockers), the washroom, the situation room two storeys tall (a continent on its
  south wall with the strikes marked, the plotting table, the gallery of consoles looking down on both), the records
  room, the mess and its galley, the escape tube filled with gravel at its top.
- *Crashed Missile* (`site_crashed.py`, one template with its grade four layers up so the furrow and crater are dug
  below it; the origin the wreck's block): the Missile Wreck at the head of its furrow, which shallows back south twenty
  blocks to where the missile first struck (churned earth, thrown turf lying flat beside it, the grass back over its far
  end); the crater where the nose went in, a low ragged rim round it but where the body came in, its floor scorched and
  dusted with fallout ash; the warhead's uranium core bare in the floor beside the nose, the soil round it irradiated;
  the guidance bay's spill (the site's chest) on the floor; burned ground, ash and dead bushes round it; the recovery
  team's lifting gantry over the crater (two lattice legs, an I-beam, the chain hoist hanging over the nose, the hand
  winch), its rope cordon (stakes and rope, a radiation sign on every third post), and its abandoned camp (a floodlight
  behind sandbags turned on the crater, the decontamination point with a hazmat suit left on its stand, crates under a
  tarp).
- *Waste Storage* (`site_waste.py`, after WIPP; one block of salt 44 x 9 x 40, buried): the salt bedded (white halite,
  a pink-brown clay seam, greyer beds, lenses of purer salt), not speckled; the main drift from the waste shaft's station
  (the hoist cage at the shaft's foot, the shaft sealed above by a concrete plug), its milled floor worn darker along
  the wheel tracks, roof bolts in a grid (buttons on the roof), strip lights along one rib, the cable and a water line
  along the other. North of it a full room closed by a bulkhead (steel frame, mesh, the ventilation curtain, signs),
  the working room (drums stacked to the roof under magnesium-oxide sacks at the back, the next row half laid, the
  forklift with a drum on its forks, a pallet of sacks), a newly mined room standing empty, a room whose roof fell in
  on its drums; south of it the decontamination room, the lead-walled hot cell behind its reactor-glass window and
  manipulator arms, the control room (air monitors, the logbook's lectern, the supervisor's desk, oxygen bottles), and
  a full room the salt is squeezing shut (its floor heaved and roof sagged over the last rows).
- *Exotic Research Station* (`site_station.py`, one template 44 x 30 x 44 hung over an End island): a teardrop of end
  stone, its point down, lumpy with knobs, veined with voidstone, obsidian at its tip, budding voidstone in its sides
  with crystals growing out sideways, chorus on its rim; an octagonal concrete deck edged in hazard stripes and railed,
  torn away on its east side; the containment hall, a steel drum under a white radome (the kit's, smoothed), blown open
  east where the containment failed, crystals erupting through, inside the broken containment ring of casings and
  copper coils round a scorched pit full of crystals, floodlights turned on it; the laboratory and the crew's module
  (steel, windows in rows, solar panels and railings on their roofs) joined to the hall by covered ways; the Search
  Radar, stopped, on a lattice tower at the deck's corner, its reflector out over the void (the station's mark from
  afar); a cargo corner; the failure's debris hanging in the void east of it.
- Every iron door has a button on each side of it (the kit's `iron_door`; `Kit.finish` puts them once the walls stand,
  in a passage one wide on the passage's wall): players could not open the old sites' iron doors.
- `SitePiece` fills under what stands on layer 1 where the land has fallen away, but never where the template leaves
  layer 0 as air on purpose: it had plugged the silo's shaft under its door with dirt, and the bunker's stair head.

**Checked on generated worlds** (2026-10-29, `runWorldSurvey`, the recorded seed): every rebuilt site visited where it
generates (the silo's three variants, the bunker in a forest and a dark forest, the outposts, the test sites, a crashed
missile in a forest, a waste store under dripstone caves, a station over the End): no part left hanging over its ground
beyond 0.4% of its columns, no water in any (the flooded silo's own, still), the parts underground closed in rock (at
most 3.3% of their shell open, to caves). Trees root in a forest's sites after they are placed (vanilla's features come
later); a crashed missile's ground under and round its wreck, its crater and its burned ring is gravel, packed mud and
scorched earth, which no tree roots in (vanilla's `substrate_overworld`), so none grows through the model. The bigger
rebuilt sites failed the land's checks more often, so four spacings were closed up to keep each as common as the census
set it (spacing/separation in chunks; per 100 km², the vanilla match in brackets): the Radar Outpost 36/13, 9.06
(pillager outposts 9.06); the Test Site 24/9, 2.15 (desert pyramids 1.91); the Command Bunker 38/15, 15.0 (igloos 15.5);
the Crashed Missile 27/10, 21.5 (20.7 before). The silo (8.8), the waste store (24.8) and the station (42.9) kept theirs.

---

## 23. Multiblock framework and layouts

### 23.1 Rules (all multiblocks)
- A **controller** validates the pattern (4 rotations) on placement, neighbour change, and right-click.
- **Ghost preview:** right-clicking an unformed controller (or holding a Holo-Projector) shows the structure as
  translucent pulsing blocks where pieces are missing, red outlines where a wrong block is present, with
  layer-by-layer stepping. Each ghost is its block's own model, tinted, a little inset and back faces culled (until
  2026-10-23 a cube in the block's particle texture, which made a Fuel Channel, whose particle is its packed model
  sheet, look broken). The Field Manual shows the same structure.
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

**Fission Reactor** (any size from 3×3×3 to 6×6×6 since 2026-10-23, §7.2; `C` = Fuel Channel). Until then it was
3×3×4 round an empty core the formed model filled. The example (Field Manual, Structure Spawner, GameTests, and the
plan a new controller shows), rows written front first:
```
y0: LXLL / LLLL / LLLL / LLLL      (X in the bottom edge of the front wall)
y1: LGGL / GCCG / WCCG / LEGL      (a 2x2x2 core of eight channels; W low in the left wall, E low in the back)
y2: LGGL / GCCG / GCCG / LGGL
y3: LLLL / LLUL / LLLL / LLLL      (U in the roof over the front of the core)
```
W is the Fluid Port (coolant in), U the Item Port (fuel/targets in, products out), E the Energy Port (power out): one of
each. The controller itself connects to nothing and has no side-configuration tab.
**Chemical Plant** (2×2×2): `y0: XE / FI` (E = Energy Port beside the controller, F = Fluid Port behind it, I =
Item Port), `y1: GG / GG` (tanks rendered inside). The controller connects to nothing; every input and output goes
through the three ports.

**Fusion Reactor** (9×9×3 octagonal ring, the Particle Accelerator's footprint, built in slice 8; `T` = toroidal field
coil, `V` = vacuum vessel casing, `S` = central solenoid, `F` = Fluid Port; rows front first, `.` = anything):
```
y0:  ..MMXMM..   y1:  ..TTGTT..   y2:  ..VVVVV..
     .MM...MM.        .TT...TT.        .VV...VV.
     MM.....MM        TT.....TT        VV.....VV
     M.......M        T.......T        V.......V
     M...S...F        G...S...G        V...S...V
     M.......M        T.......T        V.......V
     MM.....MM        TT.....TT        VV.....VV
     .MM...MM.        .TT...TT.        .VV...VV.
     ..MMEMM..        ..TTGTT..        ..VVVVV..
```
The controller connects to nothing: deuterium, tritium and liquid nitrogen go into the Fluid Port, the ignition charge in
and the power out through the Energy Port. There is no Item Port: the reactor burns no items. Corner cells are cut to
the octagon's slopes when formed. The formed model is a D-section vacuum vessel with a viewport in each side's middle
cell, 28 toroidal field coils, the central solenoid, and a plasma ring that glows (with a lapping streak) while lit.
(The 7×7 first draft was dropped: a 7-wide stepped octagon has one-cell straight sides and reads as a diamond.)

**Singularity Reactor** (5×5×5, built in slice 8; `Q` = containment emitter, `K` = core cradle, `P` = steel plating):
```
y0: BBXBB / BBBBB / IBBBE / BBBBB / BBBBB
y1: PGGGP / G...G / G...G / G...G / PGGGP
y2: PGQGP / G...G / Q.K.Q / G...G / PGQGP
y3: PGGGP / G...G / G...G / G...G / PGGGP
y4: MMMMM / MMMMM / MMQMM / MMMMM / MMMMM
```
(`.` = required air; `B` = machine casing or an Item Port, so players can add Item Ports to feed it faster.) The Steel
Frame posts of the first draft became steel plating (the Steel Frame block is gone). Items go in through the Item Ports,
the cage charge in and the power out through the Energy Port; the seed goes into the controller's GUI and stays there. The formed model: glass walls in a steel grid, five emitters beaming at a black core in a brass
gimbal cradle, and an accretion disk in three sizes that grows as it is fed.

**Particle Accelerator** (9×9×2 octagonal ring, rows front first; 32 cells a layer):
```
y0:  ..PPPPP..   y1:  ..SSXJS..
     .PP...PP.        .SS...SS.
     PP.....PP        SS.....SS
     P.......P        S.......S
     P.......P        E.......I
     P.......P        S.......S
     PP.....PP        SS.....SS
     .PP...PP.        .SS...SS.
     ..PPPPP..        ..SSTSS..
```
(`.` = anything.) Steel plating `P` under 27 magnet segments `S`, the controller `X` at the front centre with the
injector `J` beside it, the target chamber `T` at the back centre, an Energy Port `E` and an Item Port `I` (containment
cells in, antimatter out) in the middle of the sides. The controller is in the top layer, so the ring reaches one block
below it. The formed model's beam line is a true octagon (straight runs on the sides, 45° runs through the corner
staircases) and its plinth a true octagonal ring 16 px wide, with sloped faces across the staircases. When the ring
forms, each staircase block (both layers) gets a corner cut to that slope (`MultiblockPartBlock.CUT`: the three outer
stairs lose their outer corner, the two inner stairs their inner corner), so outlines and collision follow the model.

**Walkable formed shapes** (2026-10-23, after 9b, the user's request): `PartCut` also holds shapes you can walk on, so a
formed structure's collision follows its model where the model is a deck: the Launch Complex's deck is 14 px high,
its flame trench a floor 2 px up between strips of deck, and the deflector under the missile steps up out of it (5,
8, 11 px) to the deck, so you can walk from the ground along the trench under the missile and up its steps; its
controller and Energy Port cells are the deck with the console and the port's housing on them. The Radar Array's deck
is 6 px high (a step up from the ground), with its railings (west, east and back), the cabinet, the power box and the
mast's foot as collision. A pattern gives a layer's cuts drawn like its rows (`MultiblockDefinition.layerCuts`, cut
keys per layer); each walkable shape is written for a controller facing north and comes in four turns (`_NORTH`,
`_EAST`, ...), `rotated(facing)` turning it with the structure. The controller takes its cell's cut too (its block
gained the property). The property is still `cut`, so saved worlds keep their values.

**Radar Station** (3×3×5): `y0: PEP / PXP / PPP`, `y1–y3: .F. / FFF / .F.` (mast), `y4: DDD / DFD / DDD` (dish mount).

**Launch Pad** (3×3×1): `HHH / HXH / HHH` with the control panel block `X`-adjacent on one side (any side).

**Launch Complex** (5×5 base + gantry): `y0` 5×5 of `R` with a 3×3 `H` flame-trench centre and `X` at one edge
midpoint; gantry 1×2 column of `F` 9 tall at the opposite edge with an `E` at its foot.

**Missile Silo** (5×5 × 14 deep, as built in 9b; rows front first): `y0` 5×5 `R` with the Flame Deflector in the
middle; `y1–y12` `RRRRR / R...R / R...R / R...R / RRRRR`; `y13` (the ground) `HHXHH / HoooH / HoOoH / HoooH / HHEHH`,
where `O`/`o` are the Silo Hatch's middle and edges (one item) and `E` the Energy Port.

**The missile assemblers** (§10.4, as built 2026-10-23; rows front first, `P` Launch Pad, `F` Gantry Frame, `R` Crane
Rail, `I` Item Port, `E` Energy Port, `.` must be air; the deck cells cut to the 14 px deck, the controller to its
console, the Energy Port to its housing):
- *Light* (3×6×4): `y0 PXP / PPP / PPP / PPP / IPE / PPP`; `y1 F F / .  / … / F.F`; `y2 F F / … / F F` (the A-frames'
  legs); `y3` an `R` down the middle column, the hoist beam; the middle column under it clear.
- *Medium* (5×7×5): `y0 PPXPP / PPPPP ×5 / PIPEP`; the jib's post `F` in the right-hand column's middle row, `y1–y4`;
  the middle three columns clear on `y1–y2`.
- *Heavy* (5×9×6): `y0 RPXPR / RPPPR ×7 / RIPER`; `y1–y4` the back row `F...F` (the parked crane's legs); `y5` the back
  row `RRRRR` (its bridge); every other cell over the deck clear (rows 0-7, `y1–y5`).
- *Strategic* (5×5×13): `y0 PPXPP / PPPPP ×3 / PIPEP`; `y1–y11` `F` at the four corners, the middle 3×3 clear;
  `y12 R   R / R   R / RRRRR / R   R / R   R`.

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
**What the big tech mods do (researched 2026-10-20).** Immersive Engineering's Engineer's Manual and Botania's Lexica
(both now on Patchouli), AE2's guidebook (GuideME), Modonomicon books and Create's Ponder share a core: a book item
opened from the hand; a landing page of categories, then entry lists, then two-page spreads; page types for text,
item spotlights, crafting and machine recipes read live from the recipe registry, images, and multiblocks shown in 3D
(Patchouli and Modonomicon also project a "ghost" of the structure into the world, IE uses that for building); links
between entries and "related" lists; read/unread markers; search (GuideME: full text); and a jump from any item's
tooltip to its page (GuideME: hold G). Ponder adds animated scenes; heavy, and not needed for a reference book.

**Ours, slice "Field Manual" (built 2026-10-20):**
- Item **"Field Manual GB-1"** (olive hardcover, stencilled trefoil), crafted from a book and gunpowder, given once on
  first join (server config, default on).
- Two-page spread GUI in a field-manual style: cream pages in an olive binding, typed text, red "RESTRICTED" stamp on
  the header. Home spread: title, search box, chapter list. Chapter spread: entry list with icons and an unread dot.
  Entry spreads: two pages at a time; text pages flow onto as many pages as they need. Turn with the arrows, the mouse
  wheel or the arrow keys; Back returns to the previous view, Home to the start; the book reopens where you left it.
- Page types: **text** (paragraphs, `**bold**`, `*italic*`, `- ` bullets, `[label](chapter/entry)` links),
  **spotlight** (an item, large, with text), **crafting** (a live crafting recipe for an item: 3×3 grid, ingredient
  alternatives cycle), **machine** (the item's machine recipes from our recipe types: inputs, fluids, outputs, chance,
  time and FE/t), **structure** (the multiblock in 3D: drag to turn, buttons to show one layer at a time, a block list
  with counts), **image** (a plate rendered by our preview tool, e.g. a formed structure, with a caption), **table**
  (rows of label/value), **links** (related entries).
- Search: entry titles, the items an entry covers and its text. Bookmarks: up to six tabs on the book's edge.
  Read entries and bookmarks are kept per player on the client (`config/groundburst-manual.json`).
- Tooltip jump: items with an entry show "Press [H]: Field Manual"; pressing the key (rebindable) over the item opens
  that entry, in any screen and inside the manual itself (clicking an item in a recipe does the same).
- Content is data-driven JSON under `assets/groundburst/manual/` (chapters and entries), generated from
  `tools/manual/content.py`; a GameTest checks every item, recipe and structure the content names exists.
- Chapters follow the build: Getting Started, Resources, Power, Processing, Components, Nuclear, Safety now; Ordnance,
  Special Weapons, Launching, Air Defence, Superweapons, Guns arrive with their phases.
- Later: "classified" mode (entries redacted until you hold a related item), in-world "Visualize" from the structure
  page.

### 24.3 JEI (optional)
Custom categories for every machine, the assembly bench (component combinations and resulting stats), multiblock
info pages, salvage, decontamination, and ore/resource info pages.

As built (2026-10-23, after 9b, the user's request): the ingredient list carries one **intercontinental missile per
whole weapon** (`GBJeiPlugin.registerExtraIngredients`, the Strategic Assembly category's outputs, with GPS guidance),
each its own subtype by its warhead (`registerItemSubtypes`; every other missile stays one entry) and named by it
("Intercontinental Missile (Tsar Mega-bomb)"), so R on one shows how the hall builds it. The whole weapons' warhead
blocks, which have no recipe of their own, are taken out of the list (`removeIngredientsAtRuntime`).

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
Fabricator · **PA** Particle Accelerator · **FR** Fission Reactor · **AB** Assembly Bench (Fabricate tab = 3×3 grid
+ 2 precision slots; Bomb/Missile/Ammo tabs = component slots).
Ingredient lists are final in content; exact crafting-grid shapes are fixed in datagen and shown in JEI and the
manual. Processing times/energy are tuned in implementation and recorded here then.

**Slice 5 machine recipes (times in ticks, at the machine's FE/t):**
| Machine | Recipe | Time |
|---|---|---|
| Circuit Fabricator (40 FE/t) | basic circuit ×2 / advanced / military / quantum / sensor array / superconducting coil | 200 / 300 / 400 / 600 / 300 / 400 |
| Gas Centrifuge (50 FE/t) | 1 000 mB UF₆ → 2 enriched uranium nuggets + 1 DU dust | 600 (cascade: ÷ 1.15 per extra centrifuge in line, up to 8) |
| Fusion Fuel Synthesizer (120 FE/t) | Li-6 dust + 1 000 mB deuterium → LiD pellet | 300 |
| Exotic Matter Fabricator (400 FE/t) | stabilised exotic matter / containment cell / singularity seed | 600 / 400 / 1 200 |

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
| Superconducting coil | CF | 2 tungsten filament, rare-earth dust, 2 gold wire, 1 000 mB LN₂ (coolant tank) |
| Battery | CT | copper plate, redstone, 2 lithium dust, plastic sheet |

### 26.3 Chemistry and nuclear processing
See §6 for every chemical/nuclear intermediate with its station and inputs. Additional:
| Output | Station | Ingredients |
|---|---|---|
| Uranium fuel rod | CT | 3 natural uranium ingot, 2 steel rod |
| DU target rod / Li-6 target rod | CT | 3 DU ingot / 3 Li-6 dust, 2 steel rod |
| Irradiated target rod | FR | target rod (in the breeding slots, over time) |
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
| RTG | CT | 4 lead plate, advanced circuit, 2 copper wire, 2 tungsten plate (fuel goes in its slot) |
| Capacitor Bank T1 / T2 / T3 | CT | 4 capacitor + machine frame / T1 + 4 gold wire + advanced circuit / T2 + 4 superconducting coil + military circuit |
| Insulated cable ×6 | CT | 6 copper cable + 2 plastic sheet + gold wire (`CCC/PGP/CCC`) |
| HV cable ×6 | CT | 6 insulated cable + 2 tungsten filament + tungsten plate (`III/FTF/III`) |
| Fluid Port | CT | machine casing + bucket (shapeless) |
| Heavy water (1 000 mB) | CP | 1 000 mB deuterium + 500 mB liquid oxygen |
| Battery Charger | CT | machine frame, 2 capacitor, basic circuit |
| Decontamination Shower | CT | 3 steel plate, 2 iron bars, water bucket, basic circuit, electric motor |
| Ordnance Assembly Bench | CT | 3 steel plate, crafting table, anvil, servo, basic circuit, chest |
| Fission reactor parts | CT | lead-lined casing ×4: 4 lead plate + 4 dense steel plate + concrete; glass: Reactor Glass; ports: Item Port (machine casing + hopper), Energy Port (machine casing + insulated cable + redstone); controller: lead-lined casing + military circuit + CRT display |
| Uranium fuel rod / DU target rod / Li-6 target rod | CT | 3 uranium ingot / 3 DU ingot / 3 Li-6 dust, + 2 steel rod (shapeless) |
| Plutonium (3 nuggets) + nuclear waste | CP | irradiated DU target rod + 1 000 mB sulfuric acid |
| Tritium (1 000 mB gas) + 2 steel rod back | CP | irradiated Li-6 target rod |
| 2 uranium dust + nuclear waste | CP | spent fuel rod + 1 000 mB sulfuric acid |
| Antimatter cell | PA | containment cell, 3 600 ticks at 2 000 FE/t |
| CRT display | CT | glass pane, glowstone dust, basic circuit, redstone, steel plate |
| Fusion reactor parts | CT | toroidal field coil ×4: 2 superconducting coil + 4 dense steel plate + 2 tungsten plate + neodymium magnet; vacuum vessel casing ×4: 4 machine casing + 4 tungsten plate; central solenoid: superconducting coil + 5 copper wire + tungsten rod + dense steel plate; viewports are plain reactor glass; controller: machine casing + military circuit + 2 CRT display + pressure vessel |
| Singularity reactor parts | CT / EF | containment emitter (EF): containment cell + 2 superconducting coil + military circuit; core cradle (EF): stabilised exotic matter + 4 tungsten plate + neodymium magnet; controller (CT): machine casing + quantum circuit + 2 CRT display + stabilised exotic matter; the core itself is a singularity seed put in the controller |
| Accelerator parts (27 segments, 32 plating) | CT | magnet segment ×4: 2 superconducting coil + 4 dense steel plate + copper wire; injector: segment + military circuit; target chamber: segment + military circuit + reactor glass; controller: machine casing + quantum circuit + CRT display; steel plating ×4: 4 steel plate |
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
| Missile Silo controller / Silo Hatch | machine casing + military circuit + CRT display + 2 reinforced concrete + lever / 4 dense steel plate + 2 servo + hazard concrete |
| Cruise Missile Launcher | machine frame, 2 servo, 4 steel plate, advanced circuit |
| Launch Control Station | machine casing, 3 CRT display, military circuit, antenna, 2 lever |
| Data Link Tool / Comms Relay | antenna + basic circuit + steel rod / 3 antenna + machine frame + advanced circuit |
| Radar controller / dish panels | machine casing, sensor array ×2, military circuit, CRT display / dish panel recipe |
| Phased-Array module | 4 sensor array, military circuit |
| Interceptor Battery | machine frame, 2 servo, 4 steel plate, military circuit, sensor array |
| Interceptor Complex (controller) | Interceptor Battery, machine casing, 2 military circuit, 2 CRT display, superconducting coil, 2 dense steel plate |
| Launch Cell | machine frame, servo, 6 dense steel plate, tungsten plate |
| Phased Array Panel | 4 sensor array, military circuit, 4 dense steel plate |
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
| Rad-Away ×2 / KI tablets ×4 / Anti-Rad injector | plastic sheet + water bucket + 2 charcoal + sugar / paper + 2 dried kelp + saltpeter dust (iodine from kelp, potassium from saltpeter) / Rad-Away + 2 KI tablets + steel plate + iron nugget (changed in slice 12a: an IV bag is plastic; the injector is a steel pen and its needle) |
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
   │    Fission Reactor (power, plutonium, tritium) · hazmat · Geiger · RTG · decon
   │    ⇒ tactical nuke, fission, boosted fission, dirty bomb, mini-nuke launcher, Launch Complex,
   │      GPS/laser guidance, Launch Control Station, designator, SCUD Storm (+ Anthrax Beta)
   │
TIER 4  THERMONUCLEAR  lithium + cinnabar mercury → Li-6 · Electrolytic Separator → deuterium
   │    Fusion Fuel Synthesizer (LiD) · military circuits · superconducting coils · Fusion Reactor
   │    ⇒ H-bomb, Tsar, neutron, cobalt, MIRV, ICBM boosters, Missile Silo, storm bomb, cryo bomb,
   │      EMP bomb, tesla rifle, EMP gun, railgun, lead-lined hazmat, Nuclear Missile Silo (China),
   │      Anthrax Gamma, Satellite Uplink + orbital rods
   │
TIER 5  EXOTIC       the End: void geodes → exotic shards → Exotic Matter Fabricator
        Particle Accelerator (antimatter) · quantum circuits · Singularity Reactor
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
| D1 | Mod name and ID | **Ground Zero** (`groundzero`); renamed **Ground Burst** (`groundburst`) on 2026-10-28 (§30) |
| D2 | Power system | **NeoForge Energy (FE)** with generators, cables and capacitor banks; low power pauses superweapon charge |
| D3 | Guide book | **Native Field Manual** (no Modonomicon) |
| D4 | GeckoLib as the one hard dependency | **Yes** (default kept; no objection) |
| D5 | Extras beyond the brief | **Railgun** and **Point-Defence Laser** in; Tectonic Charge and Gravity Repulsor cut |
| D6 | Superweapon announcements to all players in the dimension | **On** by default, configurable (default kept) |

No open decisions right now. New ones get added here as they come up.

## 30. Decision log
| Date | Decision |
|---|---|
| 2026-11-01 | The armed targets' spheres sit where the launcher was clicked on (the user: "on the place it is clicked, not above it"): designating keeps the clicked point and face with the launcher, and its sphere touches that face there; a launcher aimed from the station keeps the top of its column. |
| 2026-11-01 | The Laser Designator reworked again to the user's wishes: it works with one laser-guided launcher of its network at a time (the first by default; the mouse wheel while zoomed picks the next and no longer turns the hotbar; the pick kept on the designator); no modes: a left click arms the pick on the spot if it is not armed, or launches it if it is; zoomed, every armed launcher of the network shows its aim as a glowing sphere; the sight's left-click tip removed. |
| 2026-11-01 | The Laser Designator's controls redesigned to the user's wishes: a left click while lasing acts by mode. **Designate** aims and arms every launcher of the designator's network loaded with a laser-guided missile on the spot; **call for fire** launches them, and the missiles follow the laser of the player who called fire from early in their flight, wherever it points (leading guidance; the old terminal homing near the aim stays for other designators). The two-second lock is gone. Left clicks are taken on the client before the game drops them (it ignores attacks while an item is in use). |
| 2026-11-01 | After the user's look at the Laser Designator: the missile assembler's screen crashed with a Laser Guidance Unit loaded (laser guidance had no name; `guidanceName` is now a switch over every guidance), and laser guidance's stats read "Homes On: A Laser Spot" and "Else Within: n blocks" (one row was too long for the panel). By day, light added over bright ground washed the beam out to a pale line: a thin red middle is drawn blended under it. The designator reaches as far as its holder sees (its view distance, to 1,024 blocks; it was 256, creatures 128), and other players draw its whole beam to the server's spot (`designator_beam`). |
| 2026-11-01 | The Laser Designator (§17.13), the second item of the user's plan: a handheld designator, a new Laser Guidance Unit that homes on its spot as it comes down, and call for fire on a station's network. Spots are the server's (each player's, per dimension, fresh for 4 ticks); the beam is traced on each side alike (over loaded ground only, smoke breaks it), so every player sees the same spot the missiles use. Taking a missile apart maps each guidance to its own part (it assumed GPS for anything not inertial). |
| 2026-10-31 | Version **0.4.0** (the user's request: a jar to upload, the GitHub page and the CurseForge description updated): the Launch Control Station, the alarm system, the Interceptor Complex, the Storm's EMP pulses. README and `docs/CURSEFORGE.md` gain the station and two screenshots (the station, the alarm wall at night); the station leaves the Planned list. |
| 2026-10-31 | After the user's look at the alarms: the klaxon rebuilt clean (its horn flaring ring by ring), the siren's horn and drum top, the beacon's open lens top and the Emergency Light's cage round its sides fixed (§18.6). The **Interceptor Complex made dearer** (the user's call): its controller is built round a whole Interceptor Battery with a superconducting coil, Launch Cells and Phased Array Panels move to dense steel, tungsten and military circuits (§18.5, §26). |
| 2026-10-31 | The Launch Control Station (§17.12), the first item of the user's plan ("a way of networking your launchers across the whole world"): a new console multiblock beside the Launch Computer (which stays the local console). Launchers join a station's **network** by a **Launch Codes** card (the network's code, changeable to void old cards), not by distance (the Data Link Tool and Comms Relay stay dropped): any number of launchers and radars anywhere in the dimension, a second station can join. A member's last report stands while it is unloaded; aiming and arming wait for it, a launch **wakes** it with a chunk ticket over its structure until it has launched and settled. Time on target from each launcher's own countdown, ignition and flight. The terrain survey now maps round every network member and station; the map zooms to 1:128. |
| 2026-10-31 | The roadmap trimmed to the user's plan (their reply to a player asking about the mod's future). **Kept:** the Launch Control Station (a network of launchers across the whole world; loading the target's chunks folded into it), the Laser Designator, MIRVs, the Cryo Bomb, the Particle Uplink Cannon, contamination, more fallout and aftermath protection (gas mask, flash goggles, the Advanced CBRN Suit), the performance pass; **new:** artillery, war room displays, seismographs, the Dead Hand, underwater and naval weapons; **being considered:** handheld weapons on the special warheads, as Rival Rebels has them (the Tactical Nuke Launcher, Tesla Rifle, Singularity Cannon, Antimatter Rifle, Cryo Gun). **Dropped:** the Point-Defence Laser, the Phased-Array module, the EMP Bomb, the Satellite Uplink and orbital strikes, the doctrine modules and generic upgrades, the Targeting Binoculars, Data Link Tool and Comms Relay, placed missile detonation, EMP for items and vanilla redstone, the other guns, the flash's afterimage, heat haze, the balance pass. A Discord server for the mod: the user makes it (it needs their account); a setup kit in `docs/DISCORD.md` (local). |
| 2026-10-31 | The base alarm system (the user's request: a redstone signal from the radar when a missile is detected, alarms, lights, "everything people could want", researched): ten devices on **alarm channels** (0-9999, the detonator's numbers; all on 0 at first), senders (the Missile Warning Receiver, warning only of missiles coming down within its radius, as Israel's Red Color does; the Alarm Panel; the Hazard Monitor) and answerers (the turning Air-Raid Siren with Attack, Alert and All Clear; the Klaxon's five standard tones; the bell; the rotating, flashing or steady Warning Beacon in five colours; the Emergency Light; the Alert Display; the Alarm Relay). No power needed (§18.6). |
| 2026-10-30 | The user's next look at the defence: the **Interceptor Complex**, a 5x5x3 multiblock (a 24-cell vertical launcher before a phased-array deckhouse) with 2.5 times the battery's reach (640/240), a 0.95 best kill chance never cut for want of a Radar Array, and volleys twice the battery's salvo (§18.5). The **Storm**'s strikes send EMP pulses 24 blocks round for 12 s (from 4 for 5), so defences inside it go dark and interceptors flying through are fried (§16.14). |
| 2026-10-30 | Version **0.3.0** (the user's request: a jar to upload and a changelog since 0.2.0): the exotic weapons and their assembler, the Interceptor Battery, upgrade chips, the see-through lattice and grating; README and `docs/CURSEFORGE.md` updated (24 warheads). |
| 2026-10-30 | The anti-missile defence (the user's request): the **Interceptor Battery** of §18.2 as a two-block tall machine with a four-cell turret, its interceptors flown like missiles (loaded or not) to a solved meeting point and their kill chance rolled there, 256 blocks where a Radar Array tracks the missile and 96 on its own seeker; it never engages missiles bound elsewhere (§18.4). The Lattice Frame framed on all four edges (§22.2). |
| 2026-10-30 | The user's next look: the Upgrades tab only as wide as its words (82); the Storm ICBM's impact sends lightning up into the sky for five seconds, the storm's birth (§16.14); the Lattice Frame and Steel Grating draw their outer faces both ways and hide only what another of them covers, so you see a stack's far side through it (§22.2). |
| 2026-10-30 | The user's third look at Phase 11: upgrade chips in **three slots**, their effects adding up, efficiency halved (5% a tier, so no mix runs a machine free), the tab's lines on the power drawn dropped (§7.3); the Storm Core takes a quantum circuit, and every storm strike **explodes** into a small crater (§16.14); the exotic weapons get their own **Special Weapons Assembler**, a containment bay of three rings, and the ICBM Assembly Hall builds the fusion weapons only (§16.15). |
| 2026-10-30 | A ready-to-paste CurseForge description, `docs/CURSEFORGE.md`: the README for CurseForge's Markdown mode (images by full link, no HTML table, a getting-started section, the sound credits in a spoiler). |
| 2026-10-30 | The Storm Warhead built (§16.14): an ICBM at the hall like the other exotic ones, its storm a supercell over a region 160 round for 90 s (the user: scale it to a region): lightning at 6 a second aimed at what stands in the open and what stands tallest, chaining through creatures, scorching, setting fires and knocking out machines; a wind round its eye; lightning rods inside it take its strikes. |
| 2026-10-30 | Upgrade chips (the user's request, §7.3): speed, efficiency and balanced chips on the four circuit tiers, 10% a level (balanced 5% and 5%), one per processing machine or assembler in an Upgrades tab on the screen's left edge (the config tab's mirror); a speed chip draws power as much faster as it works, so a job costs the same. |
| 2026-10-30 | The user's second look at the exotic weapons: no exotic bombs (their cores are ICBM cores only the hall takes; the warheads are whole weapons, out of JEI and the creative tab), and the singularity ends in a two-second implosion instead of a blast: its pull at up to seven times, its horizon shrinking to a point, shells rushing inward, the disc winding in, a last flash and a thud (§16.13). |
| 2026-10-30 | After the user's look at the exotic warheads: the Strategic Assembly Hall is the **ICBM Assembly Hall** (names only; the ids stay, so worlds keep their halls), and the exotic weapons are built whole there as ICBMs from their cores alone and a booster (no missile assembler takes them; the bench still makes their bombs, which only breach) (§16.12). |
| 2026-10-30 | Both exotic warheads were far too weak (the user): the antimatter sphere 67 (+40%, its flash with it); the singularity lives a minute and feeds, its size all from one reach, `26 sqrt(u) + 0.56 cbrt(eaten)`, about 74 on open ground (stone to 67, the antimatter's sphere), more in a mountain or the sea, at most 110; a tidal pull (d^6) gives its hole a sharp edge, and clients are told the reach as it grows (§16.12). |
| 2026-10-30 | The Large Silo Door has a sound of its own as long as its leaves take (8 s), following their speed; the Missile Silo keeps its four-second one (§16.12). |
| 2026-10-30 | The Singularity Warhead built (§16.11): its pull and tearing grow with it, tearing exposed blocks the weakest first by hardness against an inverse-power pull (not a straight erasure), with neighbour updates so sand and water follow; its collapse is an ordinary blast profile. The black hole is drawn as a depth-writing black sphere so what is behind it is hidden; gravitational lensing (a screen-space post effect) is left out for now, the photon ring, the disc and the drained sky standing in for it. |
| 2026-10-30 | Phase 11 begun with the user's choices: the Antimatter, Singularity and Storm Bombs this round (the orbital strike later), each shown before the next; classes by strength, antimatter and singularity Strategic (XL) and the storm Heavy (L), over §9.2's M. The Antimatter Warhead built (§16.10): like a nuclear warhead it rides only the Strategic body and goes off in full only on a missile's arrival (a breach of 4 otherwise); its sphere erases block by block, shell by shell, within the blast engine's budget, never ahead of the sphere players see; its gamma flash is shielded by what lies between. New models are painted with the material library (the Antimatter Trap too, though the cores before it are plainly shaded). |
| 2026-10-30 | The public repository moved to a new one, github.com/Lawman-afk/GroundBurstMCMod (the user made it): the old repository's page kept a stale contributor entry from the history before its rewrite, though GitHub's own API and statistics counted only Lawman-afk; a new repository counts afresh, and lists Lawman-afk alone. The whole history moved, joined to the new repository's starting commit by a merge that keeps the project's files as they were. |
| 2026-10-29 | Version 0.2.0 for the second CurseForge file (the rebuilt sites and the Construction set; the first file there, labelled 0.1.1, is the renamed 0.1.0). The README shows the mod: the logo, a link to the CurseForge page and a screenshot gallery (`docs/screenshots/`, JPEGs cut from the dev runs' shots; the Tsar's cloud cropped below a tutorial toast). `CHANGELOG.md` keeps each version's changes, written for players; its newest entry is the upload's changelog. The data generators' `.cache` no longer ships in the jar: the exclude named it from the project root, but resource patterns are relative to each resource folder. |
| 2026-10-29 | The rebuilt sites checked on generated worlds (§22.2): they fit their land; their bigger footprints made four rarer, so their spacings close up to the frequencies the census set (radar outpost 36/13, test site 24/9, bunker 38/15, crashed missile 27/10). A crashed missile's ground round its wreck is of blocks no tree roots in, so the forest cannot grow through the model. |
| 2026-10-29 | The Waste Storage and the Exotic Research Station rebuilt (§22.2), the last of the seven: the waste store after WIPP's own layout (a drift with disposal rooms off it, drums packed to the roof under sacks, bulkheads, salt creep), no longer rails and rubble; the station a round hall under a radome blown open by its containment failure, its silhouette the Search Radar on a tower, not a dish of blocks. Every site now lives in its own module built with the kit; the old site code is gone. |
| 2026-10-29 | The Crashed Missile rebuilt round a new prop, the Missile Wreck (§22.2): the user chose a model wreck over a missile of blocks, as the rules ask curves of the model (a plus of blocks read as a white log). The site is the wreck at the head of its furrow, the crater with the bare core, and the recovery team's gantry, cordon and camp, the gantry its mark from afar. |
| 2026-10-29 | The Test Site, the Abandoned Silo and the Command Bunker rebuilt (§22.2). The silo's shaft is round, squared only at its head under the door, as the Titan II's; the bunker is an earth-bermed portal with a blast-deflector wall before its first blast door and a twelve-step stair down, not a ladder well (the Diefenbunker's and Kelvedon Hatch's long entrances). Every iron door in a site has a button each side: the old sites' could not be opened. |
| 2026-10-29 | The world sites are rebuilt from scratch (the user's review: poor building, a radar base's bars and mast a block off the ground): researched building rules (silhouette first, small palettes with gradients, depth, curves as models, wear that follows cause), a craftable Construction set of cosmetic blocks (they drop themselves, so no free iron), a Large Silo Door (redstone, for show) and a Search Radar (turns on redstone), the Test Site an old crater and a readied shot tower without Jumbo (all four the user's choices); the beardifier's ground is the first air, so a site's ground-level delta is one more (§22.2). |
| 2026-10-28 | The mod is MIT-licensed, no longer All Rights Reserved (the user: others should be able to make add-ons). MIT over LGPL-3.0: the commonest licence of add-on-friendly mods (Create, Mekanism), the simplest for add-on makers and modpacks; it lets anyone reuse the code and art with the notice kept. The Freesound recordings keep their own licences (noted in `README.md`; `LICENSE` is the plain MIT text, so GitHub reads it as MIT). On CurseForge: the MIT License, and distribution to third-party apps allowed. For publishing the source: a `README.md`, and `gradlew build` skips the deploy where there is no test instance (anyone else's machine) instead of failing. |
| 2026-10-28 | Renamed **Ground Burst** (`groundburst`), from Ground Zero (`groundzero`): on CurseForge "ground zero" finds 46 projects, among them a Ground Zero modpack with a dozen "GroundZero's ..." mods of its own and another nuke mod. The user chose it from a shortlist, each checked on CurseForge and Modrinth (no project of that name, its URL free on both): Hypocenter, Launch Codes, Groundburst, Kiloton. A ground burst is a blast at ground level; the name is spelt as two words like Ground Zero was (the user's own spelling, on GitHub), the main classes `GroundBurst` and `GroundBurstClient`. Everything follows the name: the mod id and namespace, `dev.groundburst`, the class prefix `GB` (was `GZ`), `tools/gb` (was `tools/gz`), the Field Manual GB-1, the jar and the repository (Ground-Burst-MCMod). The phrase "ground zero" (a blast's centre) stays where the text means that. Worlds made with the old id lose the mod's blocks and items. The blast bench and the world survey keep the default seed every recorded run used. |
| 2026-10-19 | NeoForge pinned to 26.3.0.92 to match the test instance; Java 25; MDG 2.0.148; Gradle 9.2.1. |
| 2026-10-19 | Build uses CurseForge's bundled JDK 25 via `JAVA_HOME`; Gradle auto-provisions a JDK 21 for NeoForm tooling. |
| 2026-10-19 | `deployToInstance` runs after every `build`, deletes every older `groundburst-*.jar` in the instance `mods` folder, copies the new jar; path overridable with `-Pdeploy_instance_dir`. |
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
| 2026-10-20 | Fluid pipes have three tiers like cables (copper / steel / pressurized: 150 / 600 / 2,400 mB/t, set against Mekanism and Pipez tiers and our 5-20 mB/t machines and 50 mB/t pumps); the steel tier keeps the id `fluid_pipe` so pipes already placed stay. |
| 2026-10-20 | Slice 5 machines take their gases and coolant as fluids from tanks, like the Chemical Plant: the Gas Centrifuge pipes in UF₆, the Fusion Fuel Synthesizer deuterium, and the Circuit Fabricator has a coolant tank whose liquid nitrogen superconducting coils use (1 000 mB each). |
| 2026-10-20 | Centrifuge cascade: centrifuges joined flange to flange form a cascade: a straight line along a centrifuge's left-right (flange) axis of centrifuges facing the same or the opposite way (a neighbour turned a quarter breaks it, so crossing lines never share centrifuges); each runs +15% per other centrifuge in its line, counting up to 8 (+105%). Counted once a second. The bonus speeds progress; energy per tick is unchanged, so a cascade also costs less energy per run. The model's header flanges sit at the block edges so a line visibly joins up. |
| 2026-10-20 | Circuits use the common Mekanism-style tags c:circuits/basic and c:circuits/advanced (the mod's basic/advanced circuits are in them); groundburst:basic_circuits = Handmade Circuit Board + c:circuits/basic. Military and quantum circuits are this mod's own items. New common tags: c:nuggets/enriched_uranium, c:ingots/enriched_uranium, c:dusts/depleted_uranium, c:ingots/depleted_uranium, c:dusts/lithium_6. |
| 2026-10-20 | Interim recipes resolved: the Holo-Projector takes glass panes, a diamond, an advanced circuit and a battery (§26.8); the Cryogenic Condenser an advanced circuit (§26.4); the Electrolytic Separator keeps its basic circuit as designed. The Battery exists from slice 5 as a crafting part; charging comes with the Battery Charger (slice 6). |
| 2026-10-20 | Insulated cable recipe added (6 copper cable + plastic sheet, §26.4) because the Exotic Matter Fabricator draws 400 FE/t and copper cable carries 256. The HV cable recipe still waits: §26.4 lists 10 ingredients (6 insulated + 3 filament + plastic), more than a crafting grid holds. |
| 2026-10-20 | "Needs more fluid" also shows when a recipe's items are all loaded but its fluid tank is empty (it only showed for a partly filled tank). A fluid-only recipe counts once one of its fluids is in a tank. |
| 2026-10-20 | The GUI's CRT readout is filled by the block entity (generator output and buffer; centrifuge cascade size and speed bonus). |
| 2026-10-20 | Late-game power ladder (agreed with the user): Diesel Generator, RTG and Capacitor Banks in slice 6; the Breeder Reactor becomes the **Fission Reactor** (heat, control-rod slider, piped water coolant, SCRAM; damaging meltdown only by config) in slice 7 with the Particle Accelerator; a **Fusion Reactor** (tokamak ring, capacitor-bank ignition, LN₂ for its magnets, ~6 000 FE/t) and a **Singularity Reactor** (matter-fed singularity in a containment cage, ~25 000 FE/t, collapses if its cage loses power) in slice 8. Layouts and part recipes in §23.3/§26.4 are first drafts, fixed when each slice is built. |
| 2026-10-20 | Slice 6: the Diesel Generator burns fluid fuels from its tank by common tag (synthetic fuel 90 FE/mB, liquid rocket fuel 140, other mods' c:diesel 90 / c:biodiesel 80) in 10 mB sips, pausing when its buffer is full. |
| 2026-10-20 | The RTG takes its fuel in a slot instead of in its recipe (an advanced circuit replaces the plutonium ingot there). Until plutonium exists (slice 7), an enriched uranium ingot runs it for 2 in-game days; the slug decays whether or not the power is used. |
| 2026-10-20 | Capacitor banks: Capacitor Bank / Advanced / Superconducting, moving 1 024 / 8 192 / 32 768 FE/t (matching the cable tiers). The front face only outputs (and pushes), every other face only inputs, so banks chain and never feed themselves; they keep their charge in the item (an `energy` data component) and give a comparator signal. Upgrading a bank by crafting does not carry its charge over. |
| 2026-10-20 | The Battery becomes a real 50 000 FE item (FE item capability, charge bar and tooltip); the Battery Charger charges any item with the FE item capability, then moves it to its output slot. |
| 2026-10-20 | The Decontamination Shower is a walk-in booth (the collision shape leaves the inside open) that sprays whoever stands in it while powered and watered; until radiation exists it puts out fire and washes off poison, wither and nausea. |
| 2026-10-20 | Machine collision shapes now turn with the machine's facing (they were fixed to north, which only mattered for the asymmetric tall machines). Block items draw machines idle and empty (no running-only parts). |
| 2026-10-20 | Slice 6 feedback: the Battery Charger charges at 512 FE/t (it is an early machine and 2 048 FE/t drained small networks). Generators and chargers count as running for a moment after their last transfer, so a load that draws less than they make no longer blinks their light and animation; the Diesel Generator's readout shows its output averaged over about a second. The Industrial Press loses its front guard posts and light curtains (they ended short of the crown), and its gauge sits on the crown. Machine GUI titles start at the left edge and shrink to fit before the status lamp; the RTG is called "Radioisotope Generator" in game. |
| 2026-10-20 | Slice 7 Fission Reactor (a game abstraction): power = 1 200 FE/t x (fuel slots loaded / 4) x (rods withdrawn); one rod lasts 12 000 full-power ticks and comes out spent; target rods irradiate after 6 000. Water coolant up to 25 mB/t at full power; cooled, the core settles at 20 + 480 x power °C, uncooled it heats fast and SCRAMs at 900 °C (rods drop in, stays SCRAMmed until rods are withdrawn). Rods move in 10% steps from GUI buttons. A damaging meltdown is left for when the explosion engine exists (Phase 5), as a server config option. |
| 2026-10-20 | Multiblock ports: an Item Port carries both items and fluids, so the reactor's coolant, fuel and product ports are all Item Ports; a new Energy Port hands cables the controller's energy (a reactor pushes out of it, the accelerator takes power in through it). |
| 2026-10-20 | Machine GUIs gained generic controls in their generated layout (vertical gauges filled from sync data, buttons that send machine button ids), first used by the reactor's heat and rod gauges and its withdraw / insert / SCRAM buttons. |
| 2026-10-20 | Tritium is a gas fluid (Chemical Plant product from irradiated Li-6 target rods), with a gas canister and gas bucket like deuterium. The RTG also burns plutonium ingots (10 in-game days). The target chamber's recipe adds reactor glass so it differs from the injector's. |
| 2026-10-20 | Tool fix: the preview renderer only treats tall textures as animation strips when an .mcmeta exists (the accelerator's 256x512 texture was being cropped), matching the game; the 3D z-fight checker skips any variant-bone set (press dies, reactor rod positions). |
| 2026-10-20 | Slice 7 feedback, cables: insulated ×6 = 6 copper cable + 2 plastic sheet + gold wire; HV ×6 = 6 insulated cable + 2 tungsten filament + tungsten plate. Each tier is made from the tier below plus that tier's materials. |
| 2026-10-20 | Ports split by what they carry: Item Port (items only), new Fluid Port (fluids only), Energy Port (power only). The reactor's coolant port and the Chemical Plant's casing behind its controller are now Fluid Ports (existing plants need that casing swapped). The reactor controller connects to nothing and hides the side tab; the Chemical Plant and Particle Accelerator controllers keep their open faces. |
| 2026-10-20 | Reactor heat balance replaces the slice 7 rule: with coolant the core heads for 20 + 1 150 x power / quality °C (1%/tick). Water (quality 1) is safe to about 70% power and SCRAMs past ~78%; heavy water (quality 1.6, Chemical Plant from deuterium + liquid oxygen) settles near 740 °C at full power and is used at a fifth of water's 25 mB/t. The coolant tank takes either and has a dump button. |
| 2026-10-20 | Reactor meltdown: planned for Phase 5 (explosion engine). A core left past SCRAM conditions (SCRAM disabled or failed) will melt down with a damaging blast and contamination; a server config toggle turns it off. |
| 2026-10-20 | Creative Capacitor Bank (creative menu only, not craftable): refills itself every tick, pushes up to 1 000 000 FE/t from its front. Nuclear waste is fire- and lava-proof (dropped stacks still despawn). The GUI flame tooltip is per machine: the RTG shows minutes of decay left, the diesel engine its fuel in mB. Steel Frame no longer culls its neighbours' faces (it is see-through). |
| 2026-10-20 | Multiblocks connect only through their ports, on every structure: no controller exposes items, fluids or power and none has a side-configuration tab. The Chemical Plant's casing beside the controller becomes an Energy Port (`XE / FI`); the Particle Accelerator gains an Item Port at the east midpoint of its ring; the reactor's Fluid Port moves to the back middle of the bottom layer (it was under the core, reachable only from below). Built structures need those blocks swapped. |
| 2026-10-20 | Machine CRTs: when a readout's glass is too low for its lines to stack, the lines sit side by side in columns, centred (the reactor's OUT and CORE). |
| 2026-10-20 | Field Manual (the native in-game guide, §24.2) gets its own slice after the slice 7 fixes, ahead of slice 8: researched against how the major tech mods do theirs, with info pages, recipes and pictures. |
| 2026-10-20 | Particle Accelerator redesigned (user feedback: inconsistent right-click areas, a hovering truss, too square): a two-layer octagonal ring 9 blocks across (steel plating under magnet segments, 32 cells a layer) with the controller at the front centre. The formed model is a true octagonal beam line (corner bending magnets, long dipoles on the diagonals, quadrupoles on the sides) kept entirely inside the structure's cells, so what you see is what you can click. The top steel-frame layer is gone, and the Steel Frame block with it until the structures that use it (Radar Station, Launch Complex) are built. |
| 2026-10-20 | Particle Accelerator corners sloped (user request): the formed plinth is a true octagonal ring, and formed part blocks can carry a corner cut (`cut` block-state property, set by the controller from the pattern's cut map and turned with the structure) whose outline and collision follow a 45° slope, built from 2-px slices. |
| 2026-10-20 | Field Manual design settled after a survey of Patchouli (IE, Botania), GuideME (AE2), Modonomicon and Ponder: native book with home/chapter/entry spreads, text/spotlight/crafting/machine/structure/image/table/links pages, live recipes, a 3D structure viewer, search, bookmarks, unread markers and a tooltip hotkey (§24.2). Crafting recipes are synced to clients for it, as machine recipes already are. |
| 2026-10-20 | Field Manual typography after the user's check: no drop shadow on any text (paper page), headings wrap to two lines rather than being cut, and paragraphs are never split across pages (a paragraph that does not fit starts the next page). |
| 2026-10-20 | Canning Machine (user request: canisters had no use yet): a recipe machine (`canning` recipes, one fill and one empty per fluid with a canister, generated) with a supply tank that fills empty and gas canisters and a catch tank that filled canisters pour into; 20 FE/t, 2 s a canister. Filled canisters become the fluid ammunition and fillers of later weapons. |
| 2026-10-20 | Creative Fluid Tank (creative only, not craftable, never joins other tanks): the first fluid poured in fills it, and it then gives any amount of that fluid without running down; a different fluid poured in replaces it, sneak-right-click with an empty hand clears it. |
| 2026-10-20 | Items no longer carry a "Press H" Field Manual tooltip line; the hotkey still opens the manual at the hovered item. The Field Manual item is vanilla's book recoloured in our olive cloth with the label plate and hazard band. |
| 2026-10-20 | Machine frame item: the yellow gussets sit on the six visible corner joints (user feedback). |
| 2026-10-20 | Slice 8 Fusion Reactor (a game abstraction): 9×9×3 octagonal ring (the 7×7 draft read as a diamond). Fuel: deuterium + tritium (1 mB each per 4 ticks) or lithium deuteride pellets (3 600 ticks each); liquid nitrogen 2 mB/t for the magnets. While unlit its Energy Port takes power into a separate 2 000 000 FE ignition charge; with fuel and nitrogen in, a full charge lights the plasma and is spent. Lit: 6 000 FE/t pushed out of the same port, no power taken in; with the buffer full it idles without burning fuel. Out of fuel or nitrogen, unformed, or halted by redstone (a skipped tick), the plasma goes out and needs a new charge; a world reload does not count as a skipped tick. |
| 2026-10-20 | Slice 8 Singularity Reactor (a game abstraction): a seed in the controller plus 500 000 FE engages the cage and the seed becomes the core (no taking it back). Any item fed adds 200 000 FE of matter (up to 4 000 000); the core converts up to 25 000 FE/t. The cage costs 1 000 FE every tick, redstone or not, and the reactor only pushes out power above a 500 000 FE reserve (about 25 s of grace). If the buffer cannot pay, or the structure is broken while a core is held, the singularity collapses: the core is lost with a flash, a boom and particles. The destructive collapse (a small Phase 11 singularity effect, behind a config) comes with the Phase 11 effects. |
| 2026-10-20 | Big reactors' Energy Ports use a gated view (ReactorEnergyView): power out only above the reactor's reserve, power in only while the reactor wants it (fusion ignition charge, singularity cage). Cable networks never route power back to the block that pushed it, so a two-way port does not loop. |
| 2026-10-20 | Slice 8 feedback: the Fusion Reactor burns only deuterium + tritium gas; its pellet slot and Item Port are gone (lithium deuteride pellets are for the thermonuclear weapons). It gives no helium: nothing in the mod would use it. |
| 2026-10-20 | Slice 8 feedback, Singularity Reactor reworked to be clear and hungry: the seed stays in its slot (taking it out shuts down safely; a collapse destroys it). One output rule: each item eaten is worth 10 000 FE (was 200 000) and it eats at most 50 items a second (2 and 3 on alternate ticks), which is what sets the 25 000 FE/t ceiling; a store of 50 items' worth smooths bursts. Upkeep 1 000 FE/t (2 items/s), engage at 500 000 FE, 500 000 FE reserve. Four feed slots; any floor block may be an Item Port (a hopper moves only 2.5 items/s), and the model shows a hatch on every outer floor cell that is one. The CRT shows items eaten per second. |
| 2026-10-20 | Item pipes (user request): three tiers, iron 8 / steel 32 / pneumatic 128 items a second, researched against hoppers, Pipez and Mekanism (§6.5). They work like the fluid pipes (shared PipeBlock base, Wrench modes, instant pass-through delivery with no stored items). |
| 2026-10-20 | Performance pass (user request): networks cache their endpoints through capability caches (delivery no longer walks every cable or pipe, and never loads chunks); pipe and cable shapes are precomputed; machine ACTIVE lingers 10 ticks and is set without neighbour updates; multiblock cell layouts are cached (§6.6). |
| 2026-10-20 | Phase 5 is built in slices: 5a the server engine (profiles, ray field, writer, budget scheduler, entities, instant mode, config, test command); 5b chunk tickets, persistence and resume, client sync packets, distance-delayed sound; 5c the Spectacle effects (flash, fireball, shockwave, debris, smoke, shake, sky tint); 5d the mushroom cloud and the reactor meltdown. |
| 2026-10-20 | Slice 5a engine choices: block cost 0.3 + resistance^0.75 against a starting ray energy of crater x cost(stone); terrain written through Level.setBlock with no-neighbour/no-drop/no-side-effect flags rather than raw section writes (simpler and keeps lighting, heightmaps and client batching correct; revisit if profiling shows it is the bottleneck). |
| 2026-10-20 | Slice 5b: blasts force-load their active chunk band with a NeoForge ticket controller and never generate chunks unless the server allows it (existence read from disk first); running blasts are the level's saved data and resume after a restart; clients get start / front / end packets; blast sound is distance-delayed and banded by the blast's own size, played at a virtual distance in the true direction. |
| 2026-10-21 | Crater character (user feedback: round craters are dull): rays get a seeded streak grain that scales energy and crater reach, giving starburst rims and a spiky floor (the user chose the spiky floor over a smoothed bowl); scorch and fire move from pressure rays to a per-shell ground pass with an azimuth horizon (hidden ground is spared, scorch falls in spokes matching the crater's fingers); new ejecta rubble along the spokes, made of what was dug. New profile fields `streaks` and `ejecta`. |
| 2026-10-21 | Slice 5c Spectacle built on vanilla's emissive entity shader with two pipelines of our own (additive glow, alpha-blended fire; two-sided, OIT sets), no custom GLSL. The fireball needs both: additive alone washes out to white against daylight. Debris is client-only and made from blocks sampled on the client before the block updates arrive. Shake is timed to the sound's arrival; the sky tint greys before warming so a blue sky does not turn lavender. |
| 2026-10-21 | Spectacle v2 after the user asked for much better effects: researched HBM's Torex cloud, game VFX practice and test footage; built our own toroidal-vortex mushroom cloud (cap roll, ground-fed stem, collar, Wilson shell), cloud-style CPU lighting from the real sun and sky for every smoke puff, a far-cloud shader (haze instead of render-distance fog, depth-writing with cut-out fringe), dirt and fire jets and a flare for conventional blasts. Smoke and cloudlets share one texture sheet so they sort as one batch. |
| 2026-10-21 | After the in-game look: mushroom cloud life halved to 2.5 minutes (nuke sky tint too), its stem fed by a trickle so it stands until the cloud thins; crater grain factor 1.9 to 1.7; the ground pass leaves fluid surfaces alone (rubble was being set on top of water). |
| 2026-10-21 | Fission Reactor meltdown built (§7.1): opt-in, no automatic SCRAM; a critical phase with runaway heat, klaxon, warnings and venting gives 15-40 s to SCRAM; at 1 400 °C a dirty blast, corium (cools in ~10 min) and a one-day contaminated zone. Contamination is a stand-in until Phase 12's radiation field: zones apply a new Radiation Sickness effect (hunger, then armour-bypassing radiation damage), which the shower washes off and Phase 12 will drive from dose instead. |
| 2026-10-21 | Reactor meltdowns are on by default (the user asked); `reactor.meltdowns = false` brings back the automatic SCRAM. |
| 2026-10-21 | After the in-game look at the meltdown: critical cores keep heating with coolant (water used to hold one at ~1 295 °C forever), the meltdown blast roughly doubled (crater 16, not instant; corium poured after it), contamination radius 48, Radiation Sickness much harsher per level (0.5 / 1.5 / 4 / 8 damage a second, slowness), and the Hazmat Suit and Lead-Lined Hazmat Suit built (75% / 92% radiation protection). "Hazmat" covers radioactive contamination too; lead lining is what stops more of the radiation itself. |
| 2026-10-21 | Phase 6 plan (agreed with the user): slices 6a thrown (grenade framework; Dynamite, Frag with real shrapnel, Sticky, Incendiary, Smoke), 6b placed charges and fuzes (Arming Key, Remote Detonator, C4 with stacking, Breaching, Thermite, Timed Bomb with defuse GUI), 6c mines (Landmine, Bouncing Mine, Claymore), 6d bombs as armable placed blocks (HE, Heavy, Thermobaric, Cluster, Bunker Buster, Napalm, MOAB), 6e effect systems (toxin gas grid §16.7, EMP fields §21.4, freezing) with the Toxin, EMP and Cryo grenades and the Chemical Bomb. Bombs get direct crafting recipes now; the Assembly Bench (Phase 7) replaces them and adds fuze choice. The gas, EMP and frost systems are built in Phase 6 rather than waiting for Phase 12. |
| 2026-10-21 | Slice 6a thrown explosives: one projectile entity for every kind with our own bounce / stick / impact physics, real shrapnel projectiles for the frag, a floor search (not the heightmap) for the incendiary's fire carpet so it works under roofs, smoke that breaks mob targeting; grenades get 3D item models rather than flat sprites, so they tumble properly in flight. |
| 2026-10-21 | Grenade feedback: gas grenade is a smoke-style chlorine cloud (not block gas, per the user); toxin is a made-up liquid and its grenade leaves a decaying sludge film (C&C GLA style); one `Toxins` path for every chemical hazard so hazmat suits protect against all of them by piece share; salt comes from dried kelp so a water-only recipe cannot hijack other Chemical Plant recipes. |
| 2026-10-21 | After the in-game look: toxin grenade becomes a glass vial (it already shattered with a glass sound); Molotov cocktail added as the no-blast fire grenade, made from synthetic fuel so it sits beside the incendiary rather than before dynamite. |
| 2026-10-21 | Slice 6b placed charges: one block + block entity for every charge; screens open client-side from synced block-entity data (no container menus); arming a Timer fuze starts its countdown; a counting timed bomb can only be defused (four wires: defuse / boom / two halve the time); charges caught in any blast go off armed or not; thermite stays put while it eats its support. |
| 2026-10-21 | Slice 6b feedback: arming moves into the charge's own screen (no Arming Key); arming makes a charge live and the detonator or redstone triggers it, starting the timer where there is one (never overriding a safe charge); C4 is instant only; every charge has a frequency; the timed bomb's blast grows to crater 9. |
| 2026-10-21 | Slice 6c mines: built on the placed-charge framework (same screen, arming, redstone, sympathetic detonation) with sensors and a 3 s arming grace; mines are modelled sunk into the ground; the bouncing mine's canister reuses the thrown-explosive entity; the claymore's laser is drawn as geometry, not particles (block display ticks were too sparse to show it). |
| 2026-10-21 | Slice 6c feedback: mines are live as soon as armed (no grace); the claymore hurts only through its forward fragments; gas Poison III, toxin Poison IV with nausea. |
| 2026-10-21 | Claymore: its bang is only particles and a sound, and its fragments are an even, heavier cone whose every hit counts, so it kills at half its range. |
| 2026-10-21 | Slice 6d bombs: placed charges with an impact fuze, made gravity blocks so they can be armed and dropped; special bombs staged through the charge scheduler (thermobaric ignition, bunker buster bore); napalm as its own long-lived fire block rain cannot put out; cluster duds become armed landmines. |
| 2026-10-21 | Bombs feedback: model bodies keep every outer face (no slits or end rings); a placed bunker buster just explodes (boring waits for delivery at speed); napalm spreads both ways along the symmetrical canister. |
| 2026-10-21 | Bomb feel: per-bomb visual sets (HE, heavy, thermobaric fireball, MOAB dust mushroom), a whistle on long drops and a harder landing the further they fall. Bigger weapons' feel comes chiefly from delivery (missiles), so a basic launch site is built next, ahead of Phase 9. |
| 2026-10-21 | Basic launch site ahead of Phase 9: a Launch Silo (pad + gantry tower, missile stands beside it) and a Launch Computer with a live radar scope, on the multiblock framework; one Basic Missile flying a fixed curve that keeps its chunks loaded, any bomb as the warhead (going off as that bomb does); the scope shows every missile in flight within 512 blocks. |
| 2026-10-21 | Launch site feedback: an 8-block missile pointing along its path all the way down; the computer's screens clear of the antenna, its dish facing the sky; a mechanical air-raid siren; the MOAB without a mushroom cloud (conventional). Every block and item model now closes faces you could see into and warns of visible coplanar faces when generated, and the language data loads every class that declares text keys, so gaps, flicker and raw keys cannot slip through again. |
| 2026-10-21 | Launch control per silo: targets and arming live on each silo, not the computer; the computer aims and arms the picked silo and LAUNCH fires all armed ones (a salvo, each to its own target). |
| 2026-10-21 | No aircraft are planned, so bombs become missile warheads (renamed, upright models, a nose per warhead on the missile); the MOAB becomes the Massive Ordnance Warhead. |
| 2026-10-21 | Missile flights are tracked as saved data, not by the entity: the entity froze when it flew into a chunk it had not yet ticket-loaded (it could not tick to add the ticket). |
| 2026-10-21 | The Launch Computer's screen is a terrain map (what is being aimed at matters more than a sweep); the sweeping radar moves to a new Radar Array multiblock, the future source of tracks for anti-missile defence. Map data is a saved survey of chunks loaded near computers, never generating chunks. |
| 2026-10-21 | The Radar Array's crown is one Large Radar Dish (a 3×3 placed by one item), not nine small dishes; the sweep turns 25% faster (4.8 s). |
| 2026-10-21 | Missile warheads are the tactical conventional tier; full-scale nuclear/special/strategic weapons come later with their own launchers, models and far bigger yields. Cluster-type warheads air-burst when delivered; gas, smoke and toxic waste warheads added; bomblets strengthened; the bunker buster bores 3×3. |
| 2026-10-21 | Slice 6e: gas grid of one block a cell (not 2×2×2), EMP fields and freezing as level systems; the Chemical Bomb is a Chemical Warhead; EMP's redstone and powered-item effects deferred. |
| 2026-10-21 | Cryo and EMP grenades get cluster warhead versions (14 canisters each, the gas/smoke pattern). |
| 2026-10-21 | A Launch Silo that is EMP'd or held stopped by redstone refuses to arm or launch (and says why); either one mid-countdown scrubs the launch. |
| 2026-10-21 | Phase 7 plan (the user chose "everything via the bench"): warheads lose their direct recipes and are assembled at the Ordnance Assembly Bench from a payload core (one per warhead, with its size class S/M/L), a casing of the same class and a fuze (Impact, Timed, Proximity, Remote, Barometric); missiles are assembled from a warhead, body, guidance and propulsion (+ module). Slices: 7a components, bench (warhead line, take-apart) and fuze behaviour on placed warheads; 7b missile parts, assembled missiles in the silo, fuzes in flight; 7c stats panel and 3D preview, JEI, manual chapter. Every armed warhead keeps a contact backup (dropped or shot, it goes off) and redstone sets off any armed warhead; the fuze adds its own trigger. Cluster-type dispensers always open at height when delivered. The bench needs power (10 FE/t). |
| 2026-10-21 | Fuzes act only in the missile (set on the bench); placed warheads have no screen and cannot be armed. Wide machines configure each half's faces separately. Casings carry no fins (the missile has them). |
| 2026-10-22 | Only Impact and Barometric fuzes; the barometric fuze makes the missile burst its warhead over the target. Tall machines configure each half's faces too. |
| 2026-10-23 | The material library's third pass (the user's request): every machine and every other non-ordnance block repainted, on a shared machine kit (`machine_kit.py`) and, for small packed sheets (cables, pipes, tank parts), `atlas.py`; ores, raw blocks and natural blocks keep vanilla's style; warheads and other ordnance next, then items. |
| 2026-10-23 | The Fission Reactor scales (the user's request, "like in other mods"): any hollow box from 3×3×3 to 6×6×6, Fuel Channel columns in its core, one rod a column (up to 16, 4 800 FE/t: still below Fusion); its walls stay as built (the GeckoLib model retired; a renderer fills the core with glowing coolant); a GUI whose 4×4 core map shows a rod slot on each channel. Its fuel bug fixed: all rods share no counter any more; every loaded rod burns at once with its own lifetime (±15%), carrying its burnup on the item. Details §7.2. |
| 2026-10-23 | The Fission Reactor reworked (the user: height did nothing for the rods, the formed model was missed, the preview confused): a rod in every Fuel Channel wherever it stands (up to 64 at 75 FE/t, 4 800 at most, the same energy a rod); exactly one Item, Fluid and Energy Port, in the walls; the controller in a wall or the bottom edge; water left in the core slows its heat. The ghost preview shows one plan, its size set on the controller's screen, and outlines only wrong blocks. Formed, the walls hide and a sectional vessel is drawn over any size (21 section models turned cell by cell: plinth, posts, plates, windows, head, skylights, rod drives, port housings, console). The GUI's core map pages by layer. Details §7.2. |
| 2026-10-23 | The formed Fission Reactor redesigned to read as one vessel, like the old fixed model, at any size (the user: "make the pieces actually look connected and continuous ... but done in a way that is also modular"; joins flickered; frames round the glass hid the core; the panes' crosses were misaligned): sections are painted as part of the whole (the material library gains `period` and `context`), clad in riveted courses that run on round the vessel; glazing is clear and continuous with a lip round its outside only, picked per cell from its neighbours; the panes and coolant are culled (the coolant's far sides fought the walls' linings); a join check over whole example reactors. Ghost previews draw each block's own model (the Fuel Channel's ghost had been a cube in its packed texture). |
| 2026-10-23 | The formed Fission Reactor's corners and roof edge are outlined in steel (the user's request): the pillars' angles run up through the top corner pieces into a steel angle along the top of the head band, under the rim, riveted on the same pitch. |
| 2026-10-23 | The warheads remastered, models and textures, after researching model design (the user's request; §4.4): round sections become true prisms of turned planks, noses follow real profiles (no stair-stepped ziggurats), each type gets its own silhouette and details from real ordnance and the US colour codes, painted with the material library (which learns to paint turned boxes and per-material facing tones); caps never share a plane where they show (checked from above). The reactor's running loop loses its relay ticks (the user: a clicking that never stopped). |
| 2026-10-23 | Missiles carry their warheads' own models (the user's request; §4.5): the warhead's block model stands on the missile's body, fitted so its mounting ring meets the body's sides (a long one drawn smaller), everywhere a missile is drawn (items through a special item model, in flight, on the three launchers and the hall through a warhead bone and a GeckoLib layer); the painted noses, their per-warhead item models and nose bones went. The tips of four warheads flickered (rings of one radius stacked, their sides in one plane): every nose ring now steps in and a fuze goes inside the ring under it, and the model script fails any coplanar side faces too. |
| 2026-10-23 | One missile per warhead and an assembler per class (the user's request and choices; §10.4): Light, Medium, Heavy and Strategic Missile Assemblers (multiblocks, crafting harder class by class, the hall's recipe on the same ladder) replace the bench's missile page; each missile is made for its warhead (the warhead's own model as its head on a body of its radius in its paint); the Thermobaric, Bunker Buster and EMP warheads move to class L so the Heavy class has a family; the Tsar loses its fins and parachute pack. |
| 2026-10-23 | §10.4 as built: the machines draw each missile from its own item model at a bone (no missile geometry in their models); the launchers' arms slide to each missile's own hold and telescope in to its skin (no stretched textures); a lying missile is set on its cradles by its own girth. The four assemblers are one block entity, its slots taking only its class's warhead and body; the Strategic one stacks the missile upright with platforms folding round it. The bench drops anything left in its old missile slots. JEI: one Missile Assembly recipe per warhead, its assembler as the station. |
| 2026-10-23 | The Light Missile Assembler's Energy Port housing runs along its cell's outer (right-hand) edge, its banded side facing out, so where the port is reads from outside. |
| 2026-10-23 | Superweapons (§19.5; the user asked for the GLA's and China's two from C&C Generals, each taking its own ammunition, made at a C&C-themed factory, one per faction by the user's choice): the SCUD Storm (nine Toxin SCUDs) and the Nuclear Missile (China's above-ground coffin), charging on power (5:00 / 6:00) and fired from a Launch Computer (both are LaunchSites); their ammunition built by the Arms Dealer and the War Factory. A toxin SCUD's strike spreads a toxin cloud and glowing anthrax residue 16 blocks round (the "toxic waste spread on the ground"); the nuke falls between the fission and boosted warheads with a 200-block radiation field. Everyone in the dimension hears when one is built, ready or launched. |
| 2026-10-23 | Superweapons after the user's first look (§19.5): the SCUD Storm's pad grows to 15 x 15 so a ring of scrap (the game's) fits round its tank with the SCUDs moved out; the nuke's blast down a fifth (radii and damage x0.8, "20 kt"), the Toxin SCUD's blast and toxin up a third (x1.33: residue 21 blocks, cloud 16); "power" taken as Minecraft's explosion power, the blast's size and damage (the radiation field stays 200 blocks). Round sections in the big structures sink every other plank 0.15 px, not 0.06 (they are seen from further off). |
| 2026-10-24 | Slice 9c (the user chose the cruise launcher alone, no erector Launch Pad: the Launch Silo does that job; and a motor that makes a cruise missile): any Light or Medium missile built with a Cruise Engine (1,500 blocks) is a cruise missile, each warhead its own winged airframe; it flies a plan fixed at launch (out of its box at 45 degrees on its booster, a level turn, 40 blocks over the ground as surveyed at launch, a pop-up and a dive), and only the Cruise Missile Launcher (two boxes, 60k FE a missile) fires it; the vertical launchers refuse it. Its boxes' covers are frangible (a hinged door would swing above the clear space) and its wings stay stowed until it is clear of its box (they are wider than the box). §17.11. |
| 2026-10-27 | Sound remaster (the user chose the hybrid; §13.1): sounds are made from free real recordings (CC0 or CC BY 4.0 only, no non-commercial licences) wherever reality has them, with physics for what recordings cannot give (air absorption by ISO 9613-1, the blast wave's pulse, slowing for size) and synthesis only where nothing real sounds alike; blasts first. Raw recordings stay out of git (a ledger fetches them again); the mod ships a credits file. The user listens and rates on the Sound Lab page, whose ratings plan the next round. |
| 2026-10-27 | Sound remaster, round 2 (the user's ratings; §13.1): no thunder or lightning in any blast (it sounds like lightning); the large, nuclear and Tsar blasts are built on the old synthesized boom and roar, whose weight the user preferred, with the recordings over them; a low whump and a harder drive where a blast was weak. Sounds rated well are kept as they are: each round remakes only what was rated down. |
| 2026-10-27 | Sound remaster, round 3 (the user's ratings of round 2; §13.1): each blast set must sound bigger than the one below it in its own file, since the game plays every set at one volume for a band (checked with `loudness.py --ladder`); the Tsar near rebuilt on a steady roar with wreckage and the blast wind (thunder's marks: a roar swelling about once a second, a second clap); the medium near and mid swapped at the user's request, the near trimmed under the large near. |
| 2026-10-27 | No blast variants (the user: "Variants aren't needed"): one sound per set and band, as rated. The blasts are done (every sound rated 4 or 5); the remaster moves on to launches and ordnance, then machines. |
| 2026-10-27 | Launches, superweapons' machinery, ordnance and the bombs' effects remade from real recordings (§13.1; CC0 only, 32 recordings for 27 sounds): each keeps its length and the moments its animation is timed to, and is set as loud as the synthesized sound it replaces (the game's volumes were tuned to those); electronics stay synthesized. Machines next. |
| 2026-10-27 | Launches and ordnance round 2 (the user's ratings; §13.1): where the user preferred the synthesized sound, it is the base and the recordings lie under it (the missile launch, re-entry, grenade pin, bomb whistle); the bunker buster's drill stays the synthesized original, at the user's word. |
| 2026-10-27 | The falling bomb's whistle stays the synthesized original (a recording of falling bombs carries other bombs' whistles that no filter removes); the EMP is the arc alone, without a synthesized whine (the user's asks). |
| 2026-10-27 | Machines remade from real recordings (§13.1; `machines.py`, CC0): a steady machine's loop is 4 s of a recording (2 s would let a real recording's repeat show); a machine timed to its animation keeps its 2 s cycle with real events at its moments; a loop's seam is closed before its events are added (`machines.Cycle`), and it is levelled as it plays, repeated, to the old loudness. Where nothing real has the sound (the centrifuge's rotor, the fusion and accelerator cores, the diesel's knock) the old synthesized sound stays with recordings under it; the Exotic Matter Fabricator and the Singularity Reactor stay synthesized. |
| 2026-10-27 | Machines round 2 (the user's ratings; §13.1): a loop is checked across its seam for loudness, not only for a sample step (round 1's steady loops dipped there: a cut's default fade reached the seam); a steady loop's cuts are unfaded where it reads them and its length is fitted so its end leads on like its start, crossfaded on a curve that holds the level. The Ore Crusher and the RTG stay synthesized at the user's word; where the user preferred the old sound (the Canning Machine, the Particle Accelerator) it is the base with recordings under it; the Battery Charger is kept calm and 5 dB quieter (comfort over realism, the user's call). |
| 2026-10-27 | Phase 12 plan (the user: the features that are not weapons, "the Extra features", before any new bomb content): the aftermath in slices 12a to 12e (§21.8), then Phase 15's structures and loot. Radiation is analytic sources plus radioactive blocks and items rather than §21.1's per-chunk cells (exact at any distance, nothing to write into unloaded chunks, cheap to read); medicine comes with 12a so sickness has a cure from the start; the shower stops washing off sickness (a dose cannot be washed; it washes contamination in 12c). |
| 2026-10-27 | Slice 12a: a source's half-life is set so it falls under 10 RAD/h when the zone it replaces ended (the stats keep their figures); the dose is a synced data attachment; the neutron burst gives rad (x 4 000); Potassium Iodide from kelp and saltpeter, Rad-Away in a plastic bag, the injector a steel pen (§26.8 changed); the Geiger Counter is a sprite in the inventory and a model elsewhere. |
| 2026-10-27 | After the user's look at 12a: radioactive items and blocks set for play (a stack of uranium ingots sickens in about 23 minutes, a spent fuel rod in 3: §21.8); recovery below 10 RAD/h; the Geiger alarm at 100 and its clicks spread to about 140 RAD/h. The Dosimeter Badge shows the dose held now, so medicine clears its film (it had seemed not to only because no dose reached its first line, 100 rad); no second dosimeter. |
| 2026-10-27 | Launchers hold their missiles from the countdown until the last has flown (the screen's slots refuse taking and placing; automation never could take them). The Launch Computer draws each armed or launching silo's strike round its target: shaded where its warhead kills, a ring at the farthest it harms, a dashed ring for how far off it may land (an ellipse along the flight; a superweapon's spread a circle); the picked silo's bright, the rest dark. The warhead's figures come from its blast profile and effects (`AssemblyStats.area`). |
| 2026-10-27 | The Field Manual's contents run on over as many pages as the chapters need (nine did not fit on one); the Lead Container's window is 7 px taller so its well clears the title strip. The press dies sit 2 px lower in their sprites. |
| 2026-10-27 | Slice 12b: one wind per dimension; a ground burst's (and a dirty blast's, a meltdown's) debris goes off as a plume that lays sources and Fallout Ash as the wind carries it (no plume from an airburst or the neutron warhead); the cobalt plume is one of them; 14 ground blocks (§21.7's nuclear ones) in a tab of their own, the dead plants drawn as vanilla's through dead tints; the nuclear terrain table and the crater's lining; plants die at 1 000 RAD/h; dead grass grows back once safe. |
| 2026-10-27 | After the user's look at 12b: the aftermath blocks repainted to the texture remaster (designed pieces with bevelled light on their ramps, not noise with dots), the dead plants painted as our own instead of vanilla's through dead tints; a plume's ash waits until a crater is dug; lone blocks inside a crater cleared; big craters' streaks eased (0.7 of them at 200 blocks). |
| 2026-10-27 | After the user's look at the 12b fixes: vanilla's own textures where the user asks for them, over the paint-everything rule (the Dead Grass Block vanilla's grass through the dead tint again; Irradiated Soil vanilla's dirt with our stain and glow laid over it, fitted to it); an item sprite keeps its outline inside its 16 px and sits on its centre; armour icons take vanilla's four silhouettes so each reads as the piece it is (the hazmat sets redrawn). |
| 2026-10-27 | Slice 12e (the user: 12e and Phase 15 while away, questions noted for later): the wasteland is a biome painted after a nuclear blast over its scorched ground (never generated); a crater floods only if water could get in (water beside it, or groundwater in a wet land below the sea level), under the rim's lowest point, a giant crater only to a pond; every cryo burst leaves frozen ground and crystals that thaw where snow would not lie; thick anthrax sours soil, which is spent in a day. Frosted stone and toxic soil are vanilla's blocks with our layers, as the user likes. |
| 2026-10-27 | Phase 15 (the user: everything but the lore notes, no blueprints): one site structure type whose parts each meet the ground their own way (NeoForge's per-piece terrain adaptation) instead of jigsaw pools (a deep shaft under a pad could not be fitted with one adaptation); templates by script with their wear designed and a little more weathering as they are placed; seven sites researched from real places; loot of parts, medicine, gear and salvage (the Damaged Warhead, leached in the Chemical Plant); the Waste Drum. |
| 2026-10-27 | The sites and the wasteland checked on generated worlds (the user's request) with a dev run, the world survey (`runWorldSurvey`: a census over 20 x 20 km beside vanilla's sets, visits measured and photographed, two nuclear blasts). From it: a surface site reads its land on a 5x5 grid over the parts that meet it, must be dry all over and no lower than the sea's level; each part knows the site's ground (a sunk part meets the land at its rim; rock round rooms underground stops 13 under the ground; a pad is filled under where it overhangs); the surface sites keep 6 chunks from villages; spacings and slopes set so each is about as common as its vanilla likeness (the Test Site 32/12 as the desert pyramids; the Waste Storage at 35% of its places, under dry land; the research station 64/24, over islands only). The wasteland's jobs load their own chunks (a far blast painted 59% and made no lake before), and a crater lake is the basin joined to its middle, so it fills each dip and stops at the rim. |
| 2026-10-23 | The Singularity Reactor's ceiling cut from 25 000 to 10 000 FE/t (the user's request): it eats at most 20 items a second (one a tick) instead of 50; 10 000 FE an item, upkeep and reserve unchanged. |
| 2026-10-23 | After the walkable decks and the dish fixes, the user's request: the material library paints the missiles and every other multiblock, and every model from now on (DESIGN.md §4.3). Missiles get a clean factory finish and their own flight models four texels a pixel (as fine as on their launchers); edge lips are solid shade steps, chips scale with wear, grime goes by where a surface stops; the model tools check rotated bones for holes. |
| 2026-10-23 | Formed multiblocks can be walked on where their model is a deck (the user's request): `PartCut` gains walkable shapes set per cell and layer (`layerCuts`), turned with the structure; the Launch Complex's deck, flame trench and deflector steps, and the Radar Array's deck with its railings, cabinet, power box and mast foot. The Missile Silo's corner conduits run up to the ground, and the Strategic Assembly Hall's hazard band runs round all four sides of its floor. |
| 2026-10-23 | The Missile Silo's shaft lamps are removed (the user's request, after the even rows): the shaft is lit by daylight through the open doors. |
| 2026-10-23 | Texture remaster (the user chose researching it over handing it to something else): the recent structures and their blocks are painted by a material library (`tools/gb/materials.py`, §4.3): world-space noise, wear only on real edges, baked contact shadows, short hue-shifted ramps, weathering, tileable block textures. The missiles' paint and the shared controller cabinets, casing and ports are unchanged. |
| 2026-10-23 | After 9b, the user's requests: the Missile Silo's lamps are four rows of pairs, the same on every wall; it fires every missile, intercontinental ones too, so the Launch Complex is no longer the only intercontinental launcher. JEI lists one intercontinental missile per warhead (named by it, its recipe on R) and drops the whole weapons' warheads, which have no recipe of their own. The Tsar's primary is its own Twin Boosted Core (two Boosted Fission Cores fired together). A mushroom cloud stands until its crater is finished (the server's end of the blast, at most a quarter of an hour) and only then starts its 2.5-minute countdown. |
| 2026-10-22 | Slice 9b, the Missile Silo (the user's go-ahead): it fires any missile but an intercontinental one, nuclear ones too (the design's class XL), so the Launch Complex is no longer the only nuclear launcher but stays the only one for intercontinental missiles (the 8c rule). Its doors slide apart on rails (they overhang the 5×5 a little when open), and the launch holds at T-1 until they are open; its missile climbs straight out of the shaft before turning; a DOORS button opens them at other times. Reinforced Concrete resists blasts like obsidian. |
| 2026-10-22 | Phase 9 begins with missile flight (slice 9a, the roadmap's order: the user said "continue"): burn-out and coasting, spent stages dropping and tumbling down, re-entry heat, and a following engine sound with Doppler; the arc itself is unchanged. |
| 2026-10-22 | After 8d, the user's request: every machine draws its glowmask (GeckoLib's auto-glow layer in `MachineGeoRenderer`, only where the texture has one), so lamps, screens, charge bars, fires, plasma and cores glow in the dark as designed. |
| 2026-10-22 | Slice 8d (the user's "continue"): the Tsar is the hall's third whole weapon (a Tertiary Stage in the jacket slot) on the intercontinental missile, bursting where it lands (no aircraft for a parachute drop yet). Benchmarked first on a generated world: the shock front now runs at full speed with the crater forming behind it under the budget; the chunk band stops at the terrain work; rays split as reached; the rim is found after the front; the radius limit is 720 (`maxBlastRadius`, renamed so old configs take the new default). |
| 2026-10-22 | After the user's look at 8c: the intercontinental missile gets fins, the hall's port housings sit flush in the floor (the port blocks are floor blocks), and the hall's recipes show in JEI. |
| 2026-10-22 | Slice 8c (the user chose the long horizontal hall): the Strategic Assembly Hall builds whole weapons as intercontinental missiles: a new airframe class XXL and an ICBM motor (3,000 blocks) on the existing missile spec, so launching and flight are unchanged; the Thermonuclear (1.2 Mt) and Cobalt (400 kt, a month-long plume of zones downwind) Warheads are built only there and fly only from the Launch Complex. |
| 2026-10-22 | No wind drift for nukes at all (the user's request): the mushroom cloud and a nuke's crater smoke stand over ground zero. Effects are drawn after water (the after-terrain phase, as vanilla's particles), as smoke's depth had hidden all water behind it. |
| 2026-10-22 | Contamination zones are drawn (the user's request, ahead of Phase 12): a layer of dark grey and green fallout cloud over each zone and a green cast to the sky inside one; zones are sent to clients whole. Mushroom clouds keep the stem's foot and dust ring over ground zero; only the cap drifts with the wind, and the stem leans to it (the whole cloud drifting had left it off its crater). |
| 2026-10-22 | Slice 8b: Boosted Fission and Neutron Warheads are class XL (Strategic missile, Launch Complex); the Dirty Warhead is class S and radiological, not nuclear (any silo, never fizzles; 90 blocks at 0.8 for five days). The neutron dose is (1-(d/180)²)·e^(-Σ) over the blocks in the way (solid 0.25, liquid 0.4, obsidian/concrete/lead 1.0), suits count half; 0.3 kills. Withering works down each column from the top, so roofs shelter plants. |
| 2026-10-22 | Nuclear warheads are class XL with a Strategic Warhead Casing; blasts clean up floating clusters and place fire only on standing ground after the front; mushroom clouds stand on the crater floor with a rolling base ring; launcher arms hinge on their towers and swing 75°. |
| 2026-10-22 | After the 8a in-game look: the bench's page is saved and steers shift-clicks and automation; nuclear warheads ride their own Strategic (XL) missile, longer and differently styled, the peak of what fits a missile, with bigger weapons built whole by a planned Strategic Assembly Hall; every nuclear weapon rescaled so the smallest is 1.5x the MOW and the Tsar is truly massive. |
| 2026-10-22 | Phase 8 plan (the user chose "own launcher first" and "contamination zones now"): nuclear warheads are bench-assembled but fly only from a new Launch Complex (built first); fallout uses the meltdown's contamination zones until Phase 12. A nuclear warhead gives its yield only when its missile brings it down; any other way it fizzles (small blast, weak contamination), so nukes never chain-detonate. Slices 8a (complex, tactical and fission), 8b (boosted, H-bomb, neutron, cobalt, dirty), 8c (XL: Tsar, Strategic body, ICBM booster). |
| 2026-10-22 | The Launch Computer maps 3000 blocks round it (was 1024), zoom out to 1:32: a Liquid-fuel Stage reaches 2500 blocks from its silo, and the computer may stand some way off. The bench preview zooms to fit each model. |
| 2026-10-22 | Slice 7c (the user chose a wider bench window): a 3D preview you can drag round and a stats screen in a right-hand column; stats are worked out in common code from the blast profiles and the flight formulas, at the missile's full range. Phase 7 closes with it (the Ammunition page waits for Phase 13 guns, blueprints for Phase 15 loot, interception odds for Phase 10). |
| 2026-10-22 | Unguided missiles scatter over 25% of the distance (inertial 4%, GPS 2 blocks), evenly over an ellipse longer along the flight, so guidance is worth fitting. Bench page tabs are part of the window's frame. |
| 2026-10-22 | Slice 7b (the user chose the full parts list): the bench gets Warheads/Missiles pages; bodies set the warhead class and the missile's size, motors the range (400 / 1024 / 2500), guidance the scatter (8% / 2% / 2 blocks, applied once at lift-off), Hardened Electronics stop EMP duds. The assembled Missile replaces the Basic Missile, and the silo takes only a missile. |
