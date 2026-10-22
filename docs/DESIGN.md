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

### 4.3 Surface materials (the remaster after slice 9b, 2026-10-23)
The user asked for the recent structures (the Strategic Assembly Hall, both silos, the Radar Array, the Launch Computer
and the Launch Complex) to look better, above all their metal and concrete, either by researching how or by finding
something else to do it; they took the research. The structures had the right shapes but flat fills per box: one shade
a face, a speckle on concrete. They are now painted by a material library, `tools/gz/materials.py`, the way a texture
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
  standing on any launcher and lying in the hall. Roll bands and quadrants, warhead noses and their bands, black
  fins with white leading edges, ring seams with lit lips, rivet rings round interstages, data plates, a cable raceway
  and a stencilled marking on the intercontinental first stage, the Tsar's canvas pack, stencil band and glowing
  vents. Fine details only where a texel is at most half an item pixel (not on the inventory icon).
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
| Fission Reactor (multiblock any size from 3×3×3 to 6×6×6, replaces the Breeder Reactor) | 300 FE/t a rod, up to 4 800 FE/t (16 rods) | Burns uranium fuel rods, one to each column of Fuel Channels in its core (§7.2), and breeds plutonium from DU target rods and tritium from Li-6 target rods. Every loaded rod burns at once, each with its own lifetime. Runs on core heat: buttons set control-rod insertion, water coolant is piped in, a heat gauge shows core temperature. Overheats and SCRAMs (shuts down) if starved; a damaging meltdown unless SCRAMmed in time, on by default (§7.1; the server can turn it off). Cherenkov-blue core. |
| Fusion Reactor (tokamak ring multiblock) | ~6 000 FE/t | Burns deuterium + tritium gas (LiD pellets are kept for the thermonuclear weapons). Needs a large ignition charge from capacitor banks to start, and liquid nitrogen for its magnets while running; once lit it sustains itself. A glowing plasma ring races round the torus. Ties together the Fusion Fuel Synthesizer, Cryogenic Condenser and capacitor banks. |
| Singularity Reactor ("special" multiblock) | up to 10 000 FE/t | A singularity seed sits in the controller (it stays there, like a battery in the charger) and is held in a powered containment cage. It eats items, any items: each is worth 10 000 FE and it eats at most 20 a second (one a tick), so its output is set by the feed rate (10 items/s = 5 000 FE/t, 20 items/s = 10 000 FE/t; it was 50 a second and 25 000 FE/t until the user asked for 10 000 after 9b). Holding the cage costs 1 000 FE/t (2 items/s). Built to need automatic cobblestone or tree farms. Taking the seed out shuts it down safely; if the cage runs dry or the structure is broken while it holds the seed, the core collapses and the seed is destroyed (a destructive collapse, a small Phase 11 singularity effect, will be a config option). |
| Capacitor Bank / Advanced / Superconducting | 100k / 1M / 10M FE, 1 024 / 8 192 / 32 768 FE/t | Every face takes power in except the front, which pushes it out; keeps its charge when picked up; comparator output; 8-bar charge gauge on the model. Superweapons draw from these during charge. |
| Power cables: copper (256 FE/t), insulated (1 024), high-voltage (8 192) | — | Connected-texture cable models with insulators on HV; HV cable arcs when EMPed. |

Superweapons and large machines need power while charging (as in Generals: low power pauses the countdown).

**Power ladder** (agreed with the user 2026-10-20; 2026-10-23: the Fission Reactor scales, the Singularity Reactor
cut to 10 000): Combustion Generator 60 → Diesel Generator 180 / RTG 40 (slice 6, with Capacitor Banks to buffer the
big reactors and superweapons) → Fission Reactor 300 a rod, 1 200 from four up to 4 800 from sixteen (slice 7, with
the Particle Accelerator) → Fusion Reactor ~6 000 and Singularity Reactor up to 10 000 (slice 8). For scale: the
Exotic Matter Fabricator (400 FE/t) needs 7 Combustion Generators, 3 Diesel Generators, or a four-rod Fission Reactor
at a third. All three
reactors are game abstractions (invented parts, simple fuel-in/energy-out rules), never real reactor designs (§30).

### 7.2 The Fission Reactor at any size (2026-10-23, after 9b, the user's request)
"Scalable like in other mods, with a UI that scales the fuel rod count", and every rod burning at once.
- **The box** (`FissionCore`): any hollow box from 3×3×3 to 6×6×6 outside (each side 3 to 6: the core at most 4×4×4,
  so at most 16 rods, 4 800 FE/t, below the Fusion Reactor). Corners Lead-Lined Casing; edges casing or ports; faces
  casing, Reactor Glass or ports (Item, Fluid, Energy: as many as you like); the controller in a face or an edge (not a
  corner). Inside, **Fuel Channels** (a new block) stand in columns the full height of the core, one fuel rod a
  column; the rest of the core is left empty, for the coolant. A column with a gap, a stray block in the core, glass on
  an edge or a box too big does not form; neither does a core with no channels.
- **Finding the box**: from the controller, wall blocks counted along its row and column give the front wall; walks
  back from the front corners give the depth. Where nothing is built yet along a side the example's size (4) is
  assumed, so the ghost preview shows where the rest goes (casing for walls, Fuel Channels to finish a column). Reactor
  blocks touching from outside make the box too big, as in other mods. Checked every 2 s and whenever a structure
  block near it changes (`covers` is the box once formed, the reach of a 6×6×6 before).
- **It stays as built**: the walls are ordinary blocks (the controller too, `MultiblockControllerBlock` without
  hiding). The old GeckoLib model is retired; a renderer fills the core round the channels with the coolant up to the
  tank's level, glowing Cherenkov blue while it runs, and the channels light up (`lit`: light 10, their water glows).
  The example (`GZMultiblocks.FISSION_REACTOR`, the Field Manual, Structure Spawner and GameTests) is 4×4×4 with four
  channels: the old reactor's 1 200 FE/t.
- **Rods**: 300 FE/t each × rods withdrawn. Every loaded rod burns at once. A rod lasts 48 000 ticks of full power on
  average (the same energy a rod gave before: four rods had shared one 12 000-tick counter, so they ran out one at a
  time); each rod's own lifetime is drawn when it first burns, up to 15% either side (two random draws added, so most
  are near the middle), so a core loaded at once runs out over minutes, not in one tick. The rod carries its burnup
  (`groundzero:fuel_burnup`: used, life), shown as a bar under it and in its tooltip; taken out and put back it keeps
  it. A used-up rod becomes a spent rod in its channel and moves to the spent slot when there is room.
- **Heat** goes by how full the core is (loaded rods / channels × rods out), so the rules of §7 hold at any size. A
  taller core heats and cools more slowly: every rate divided by core height / 2 (0.5 to 2). Coolant 6.25 mB/t a rod at
  full power (heavy water a fifth). Breeding goes at loaded rods / 4 × rods out (sixteen rods four times as fast).
  Buffers: 50 000 FE and 4 000 mB of coolant a channel (the tank stops at 32 000 mB, what a GUI sync slot carries).
- **The GUI** (226×218): a 4×4 core map seen from above, the controller's wall at the bottom, a smaller core in its
  middle; a rod slot on each channel (one rod each), coolant cells between, casing outside the core, a red-ringed slot
  where a rod is left on a cell that is no longer a channel (to take it out). Heat (0 to 1 400 °C) and rod gauges and
  the withdraw / insert / SCRAM buttons the map's height; the spent slot and the breeding (two targets over their
  outputs) in a right-hand column; the CRT shows output, core temperature, size and rods loaded.

### 7.1 Fission Reactor meltdown (as built, Phase 5)
A game abstraction of a reactor accident, on by default (the user's call); the server config `reactor.meltdowns` turns
it off, and then the reactor SCRAMs at 900 °C.
- **Critical.** With meltdowns on there is no automatic SCRAM. Past 900 °C with the rods out the core is critical:
  it gains 0.35 + 0.9 x power °C a tick (plus a tenth of the usual uncooled heating); coolant cannot hold it at any
  temperature any more, it only halves the climb. From critical to meltdown: about 14-22 s without coolant, about
  40-47 s with water (an earlier version let water hold a critical core at ~1 295 °C forever; fixed). A klaxon (`reactor.alarm`, two alternating tones) sounds
  every 2 s, everyone within 64 blocks gets a red warning with the temperature, steam (and, past 1 150 °C, smoke)
  vents from the top. Status lamp: CORE CRITICAL. SCRAM, or every rod in, ends it.
- **Meltdown at 1 400 °C.** The structure and its fuel are removed; the blast profile `groundzero:reactor_meltdown`
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
  the user's look): damage `groundzero:radiation` (bypasses armour, no knockback) of 0.5 / 1.5 / 4 / 8 per second at
  I-IV, hunger drain growing per level, 5% slower per level. The Decontamination Shower washes it off. Phase 12
  replaces the zone with the field and drives the same effect from dose.
- **Hazmat suits** (§21.5, built now): Hazmat Suit (yellow plastic, filter hood; 75% radiation protection as a full
  set; armour 1/2/2/1; plastic sheet, glass pane, charcoal, yellow dye) and Lead-Lined Hazmat Suit (orange, lead plates;
  92%; armour 2/4/3/1, knockback resistance 0.05, 5% slower as a full set; a hazmat piece inside four lead plates).
  Pieces carry the set's protection in shares hood 20%, coat 35%, trousers 30%, boots 15%. At the crater centre a full
  hazmat suit keeps the sickness to II, the lead-lined suit to I.

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
- **Fuzes** (`Fuze`, the `groundzero:fuze` item component): only **Impact** and **Barometric** (after the in-game
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
- The **Missile** item (`groundzero:missile` component, `MissileSpec`) stacks to 1, is named by its body ("Heavy
  Missile") and lists warhead, fuze, range, guidance and hardening; its nose model follows the warhead. It replaces
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
fire radius ignite. Items and XP orbs in the crater are deleted. Custom damage types (`groundzero:blast`,
`groundzero:nuclear_flash`, `groundzero:radiation`, `groundzero:toxin`, `groundzero:erasure`, ...) with death
messages.

### 11.4a As built (slice 5a)
- Profiles are a synced datapack registry `groundzero:blast_profile` (fields as §11.1 plus `lethal_damage`,
  `edge_damage`, `knockback`, `lobes`, `crater_depth`, optional `instant`); `test/*` profiles exist for the
  `/groundzero blast <profile> [pos]` command (permission level 2) and the GameTests. Weapons get their own profiles.
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
  `groundzero:blast` (tagged `is_explosion`).
- Profile JSON groups the entity numbers under `entities` (`lethal`, `damage`, `lethal_damage`, `edge_damage`,
  `knockback`) and adds optional `sound` (small / medium / large / nuke) and `visual`, both chosen by crater size when
  left out.
- Until the Spectacle effects (5c), a vanilla explosion particle marks the burst point.

### 11.4b As built (slice 5b)
- Chunks: a non-instant blast force-loads (non-ticking) the chunk columns from 16 blocks behind to 32 ahead of its
  front, through the `groundzero:blast` ticket controller owned by the blast's UUID, and releases them behind it. With
  `generateChunks` off, unloaded chunks are only loaded if the disk says they are fully generated (read in the
  background; the front waits for the answer). Leftover tickets are dropped on world load. The ray ceiling rises as
  far chunks load.
- Saving: the level's blasts are its saved data `data/groundzero/blasts.dat` (codec: id, profile id and the scaled
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
- A `TicketController` (`groundzero:blast`) force-loads the chunks in the active band `[r-16, r+32]` and releases
  them behind the front. Ungenerated chunks are not generated (configurable). The band runs only as far as the terrain
  work (crater, the bands beyond it, the ground pass); entity zones past that reach the entities already loaded
  (slice 8d: loading the Tsar's outer 220 blocks of chunks in a few ticks stalled the server). A finished blast lets
  its chunks go 64 a tick.
- Jobs live in a per-level `SavedData` (`BlastManager`); ray fields are stored compactly in
  `data/groundzero/blasts/<id>.bin`. On world load, jobs resume and clients re-sync their visuals.
- Jobs never depend on a player being nearby; they finish while the server runs.

### 11.6 Server config (`groundzero-synced.toml`), with dramatic defaults
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

Client config (`groundzero-client.toml`): `fx.quality` (low/medium/high/ultra, default high), `fx.flashStrength`
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
Package `dev.groundzero.client.fx`. `ClientBlasts` passes start / front / end to `Spectacle`, which keeps a `BlastFx`
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

- Every sound is synthesised by `tools/sound` (layered noise, filtered sweeps, convolution reverb, saturation), is
  **mono**, and has a subtitle.
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
- One entity, `groundzero:thrown_explosive` (`ThrownExplosive`), for every thrown kind (`GrenadeType`), told apart
  by the item it carries. Own flight: gravity 0.045 a tick, drag 0.985 (0.8 in water), hit-tested along each step.
  On a hit it **bounces** (speed into the surface x 0.35 comes back out, speed along it x 0.7; at rest below 0.063
  blocks a tick on a floor), **sticks** (to the block face, or riding a creature at its offset), or **goes off**
  (impact). The fuse counts from the throw. Thrown at 0.9-1.1 blocks a tick, 0.5 s cooldown, stacks of 16.
- Dynamite (3 s, bounce; crater 2.5), Frag Grenade (4 s, bounce; crater 2, weak, plus 24 `Shrapnel` projectiles on a
  Fibonacci sphere flattened 30% toward the horizontal, 1.8-2.4 blocks a tick, 6 damage `groundzero:shrapnel`
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
- `Toxins.expose(level, entity, damage, poisonTicks, amplifier)`: damage `groundzero:toxin` (bypasses armour, no
  knockback) plus Poison, both cut by `GZArmor.chemicalProtection` (each suit piece's share: hood 20%, coat 35%,
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
  (`ChargeBlockEntity`, `groundzero:charge`) for every placed charge (`ChargeKind`). `FACING` points out of the face
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
  radiation trefoil on the front. Missile noses follow them.
- **Delivery only.** A missile's impact or burst gives the full yield (`Charges.nuclear`) and a contamination zone
  (tactical 90 blocks for a day, fission 180 blocks for two days; an airburst from a barometric fuze leaves half the
  strength). Set off any other way (dropped, shot, caught in a blast) a nuclear warhead **fizzles**
  (`nuclear_fizzle`: crater 3) and leaves a weaker zone 16 blocks round for half a day (strength 0.6).
- **Strategic missile** (`SizeClass.XL`, the Strategic Missile Body): the only body for a nuclear warhead, and it takes
  nothing else (`MissileAssembly.fit`); only the Liquid-fuel Stage fits it (2500 blocks); only a Launch Complex fires it
  (`MissileSpec.needsComplex`). Its own model (`launch.py` `strategic_parts()`, custom model data `strategic_<warhead>`):
  a wide first stage in black-and-white roll quadrants with four big swept black fins and four nozzles, a riveted
  interstage, a slimmer white second stage with a data plate, the warhead's nose; about 11 blocks on the complex (its
  own bones, `strategic` / `snose_*`) and in flight (lifted by `STRATEGIC_TAIL`, as item models stop at y -16).
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
  warhead throws a pool 10 blocks round (no length to spread along). A missile's nose is painted as its warhead (item
  model variants chosen by custom model data in flight; nose bones on the silo model).
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
The user's rule after 8a: the Strategic missile is the peak of what fits in a missile; anything bigger gets its own
fabricator, a multiblock that assembles the full weapon all at once, to be placed on the Launch Complex. The user chose
the long horizontal hall (over an upright assembly tower).
- **Structure** (`GZStrategic.HALL`): a 7x13 floor of Launch Pads (controller front middle; Energy Port back middle, an
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
  taken apart there; a Launch Complex fires them, and since 9b a Missile Silo too. Its own model (`launch.py` `ICBM_SECTIONS`, item models
  `icbm_missile_<warhead>`, the complex's `icbm` / `inose_*` bones; lifted by `ICBM_TAIL` in flight): three olive-drab
  solid stages narrowing upward with a white, a yellow and a white band, dark riveted interstages, a gunmetal skirt
  and four nozzles, four swept fins in two steps round the first stage (added after the user's look: the first model
  had none), the warhead's shroud; about 11 blocks on the complex. The complex's umbilicals gained a clamp each
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
  regardless of the light round them); on the missile the bomb rides bare on the third stage from just above the
  complex's upper clamp, wider than any stage (`launch.py` `TSAR_SECTIONS`; each warhead now brings its own nose,
  `icbm_nose_parts`), and lies the same way on the hall's cradles. The vents glow on the complex and the hall through
  GeckoLib's auto-glow layer, since turned on for every machine with a glowmask (§4.1). No aircraft yet: the
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
- **Structure** (`GZLaunch.MISSILE_SILO`), dug into the ground, 5×5 and 14 layers: a floor of **Reinforced Concrete**
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

**Fission Reactor** (any size from 3×3×3 to 6×6×6 since 2026-10-23, §7.2; `C` = Fuel Channel). Until then it was
3×3×4 round an empty core the formed model filled. The example (Field Manual, Structure Spawner, GameTests), rows
written front first:
```
y0: LXLL / LLLL / LLLL / LLWL      (X at the front of the bottom layer, W the Fluid Port behind)
y1: LGGL / GCCG / GCCG / LGGL      (four Fuel Channel columns in the 2x2 core; glass round it)
y2: LGGL / GCCG / GCCG / LGGL
y3: LLLL / LUEL / LIGL / LLLL      (Item Port, Energy Port, Item Port and a window on top)
```
W is a Fluid Port (coolant in), U and I are Item Ports (fuel/targets in, products out), E is an Energy Port (power
out). The controller itself connects to nothing and has no side-configuration tab.
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
- Item **"Field Manual GZ-1"** (olive hardcover, stencilled trefoil), crafted from a book and gunpowder, given once on
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
  Read entries and bookmarks are kept per player on the client (`config/groundzero-manual.json`).
- Tooltip jump: items with an entry show "Press [H]: Field Manual"; pressing the key (rebindable) over the item opens
  that entry, in any screen and inside the manual itself (clicking an item in a recipe does the same).
- Content is data-driven JSON under `assets/groundzero/manual/` (chapters and entries), generated from
  `tools/manual/content.py`; a GameTest checks every item, recipe and structure the content names exists.
- Chapters follow the build: Getting Started, Resources, Power, Processing, Components, Nuclear, Safety now; Ordnance,
  Special Weapons, Launching, Air Defence, Superweapons, Guns arrive with their phases.
- Later: "classified" mode (entries redacted until you hold a related item), in-world "Visualize" from the structure
  page.

### 24.3 JEI (optional)
Custom categories for every machine, the assembly bench (component combinations and resulting stats), multiblock
info pages, salvage, decontamination, and ore/resource info pages.

As built (2026-10-23, after 9b, the user's request): the ingredient list carries one **intercontinental missile per
whole weapon** (`GZJeiPlugin.registerExtraIngredients`, the Strategic Assembly category's outputs, with GPS guidance),
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
| 2026-10-20 | Fluid pipes have three tiers like cables (copper / steel / pressurized: 150 / 600 / 2,400 mB/t, set against Mekanism and Pipez tiers and our 5-20 mB/t machines and 50 mB/t pumps); the steel tier keeps the id `fluid_pipe` so pipes already placed stay. |
| 2026-10-20 | Slice 5 machines take their gases and coolant as fluids from tanks, like the Chemical Plant: the Gas Centrifuge pipes in UF₆, the Fusion Fuel Synthesizer deuterium, and the Circuit Fabricator has a coolant tank whose liquid nitrogen superconducting coils use (1 000 mB each). |
| 2026-10-20 | Centrifuge cascade: centrifuges joined flange to flange form a cascade: a straight line along a centrifuge's left-right (flange) axis of centrifuges facing the same or the opposite way (a neighbour turned a quarter breaks it, so crossing lines never share centrifuges); each runs +15% per other centrifuge in its line, counting up to 8 (+105%). Counted once a second. The bonus speeds progress; energy per tick is unchanged, so a cascade also costs less energy per run. The model's header flanges sit at the block edges so a line visibly joins up. |
| 2026-10-20 | Circuits use the common Mekanism-style tags c:circuits/basic and c:circuits/advanced (the mod's basic/advanced circuits are in them); groundzero:basic_circuits = Handmade Circuit Board + c:circuits/basic. Military and quantum circuits are this mod's own items. New common tags: c:nuggets/enriched_uranium, c:ingots/enriched_uranium, c:dusts/depleted_uranium, c:ingots/depleted_uranium, c:dusts/lithium_6. |
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
| 2026-10-23 | The Fission Reactor scales (the user's request, "like in other mods"): any hollow box from 3×3×3 to 6×6×6, Fuel Channel columns in its core, one rod a column (up to 16, 4 800 FE/t: still below Fusion); its walls stay as built (the GeckoLib model retired; a renderer fills the core with glowing coolant); a GUI whose 4×4 core map shows a rod slot on each channel. Its fuel bug fixed: all rods share no counter any more; every loaded rod burns at once with its own lifetime (±15%), carrying its burnup on the item. Details §7.2. |
| 2026-10-23 | The Singularity Reactor's ceiling cut from 25 000 to 10 000 FE/t (the user's request): it eats at most 20 items a second (one a tick) instead of 50; 10 000 FE an item, upkeep and reserve unchanged. |
| 2026-10-23 | After the walkable decks and the dish fixes, the user's request: the material library paints the missiles and every other multiblock, and every model from now on (DESIGN.md §4.3). Missiles get a clean factory finish and their own flight models four texels a pixel (as fine as on their launchers); edge lips are solid shade steps, chips scale with wear, grime goes by where a surface stops; the model tools check rotated bones for holes. |
| 2026-10-23 | Formed multiblocks can be walked on where their model is a deck (the user's request): `PartCut` gains walkable shapes set per cell and layer (`layerCuts`), turned with the structure; the Launch Complex's deck, flame trench and deflector steps, and the Radar Array's deck with its railings, cabinet, power box and mast foot. The Missile Silo's corner conduits run up to the ground, and the Strategic Assembly Hall's hazard band runs round all four sides of its floor. |
| 2026-10-23 | The Missile Silo's shaft lamps are removed (the user's request, after the even rows): the shaft is lit by daylight through the open doors. |
| 2026-10-23 | Texture remaster (the user chose researching it over handing it to something else): the recent structures and their blocks are painted by a material library (`tools/gz/materials.py`, §4.3): world-space noise, wear only on real edges, baked contact shadows, short hue-shifted ramps, weathering, tileable block textures. The missiles' paint and the shared controller cabinets, casing and ports are unchanged. |
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
