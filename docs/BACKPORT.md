# Ground Burst for Minecraft 1.20.1 (the Forge backport)

The mod is made on Minecraft 26.3 (branch `main`) and carried back to 1.21.1 on NeoForge 21.1 (branch `1.21.1`). This
branch, `1.20.1`, carries the 1.21.1 build back once more, to Minecraft 1.20.1 on Forge 47, where most of the mod's
players are (main's DESIGN.md §30). It is brought up to the 1.21.1 branch every once in a while, as a whole, as that
branch is brought up to main.

- **Last brought up to:** the `1.21.1` branch at `2ef851ae` (main at `ae7e2623`, 2026-11-11: the war room removed).
  Begun 2026-11-11.
- **Toolchain:** Minecraft 1.20.1, Forge 47.4.28 (the mod asks for 47 or later), Java 17, GeckoLib 4.8.4
  (`geckolib-forge-1.20.1`), JEI 15.74 (optional), MixinExtras 0.5.5 (packed in the jar: Forge 1.20.1 carries none).
  Built with ModDevGradle's legacy plugin (`net.neoforged.moddev.legacyforge` 2.0.148): the dev runs on official
  names; the jar for players is turned back to Forge's SRG names (`reobfJar`; the dev jar is `build/devlibs`'s); the
  mixins have a refmap made as the code compiles and are listed in the jar's manifest (`MixinConfigs`).
- **Test instance:** a Forge 1.20.1 CurseForge instance, named by `deploy_instance_dir_1_20_1` in your own
  `~/.gradle/gradle.properties` (never `deploy_instance_dir`, main's, or the other branches'). Unset, the build says so
  and deploys nothing.

## How it is made

The code is not ported by hand. `tools/backport/port.py` turns the 1.21.1 branch's sources (read from git at the commit
in `tools/backport/base.txt`, so the 1.21.1 worktree is never touched) into this branch's:

1. **Stand-ins** (`dev.groundburst.legacy`, this branch's own): NeoForge 21.1's and vanilla 1.21.1's API, in the same
   shapes, over Forge 1.20.1, so most of the code needs no change.
   - Registration: `DeferredRegister`, `DeferredHolder`, `DeferredBlock`, `DeferredItem` over Forge's registers; each
     holder is the handle Forge's register hands back, bound the moment its object is registered (stairs are made of
     their block while the blocks register; a handle made by name is bound only once its whole registry is).
   - Item data components (`legacy.component`): kept in the stack's tag under the component's id, written by its codec;
     an item's own defaults, given with its properties, carried by `mixin.ItemMixin`; vanilla's few the mod touches
     (custom model data, dye, a block entity's items) in 1.20.1's own places. A patch is the stack's tag
     (`DataComponentPatch`; Forge's `ItemStack(item, count, tag)` takes a stack's capabilities' data, so a stack made
     from a patch has its tag set after). A block's components, dropped with it and read back as it is placed:
     `mixin.BlockItemMixin` and the loot function `groundburst:copy_components` (`legacy.loot`).
   - Networking (`legacy.net`): 1.21's stream codecs and payloads, sent over one Forge channel (`groundburst:main`).
   - Capabilities (`Caps`, `Capabilities`, `CapabilityBridge`): NeoForge's block and item capabilities in tables, and
     Forge's own (item handlers, fluid handlers, energy) bridged both ways for other mods.
   - Data attachments (`Attachments`): a Forge capability on entities and chunks. Data maps (`DataMaps`): read by a
     reload listener and sent to players. Chunk loading tickets (`TicketController`): Forge's, by mod.
   - Recipes (`legacy.recipe`): 1.21's recipe holders and inputs over 1.20.1's recipes (which carry their own ids);
     JEI's recipe types of them (`legacy.jei`).
   - Client: GUI layers on Forge's overlays, a renderer's culling box (`mixin.LevelRendererMixin`), the turn of the
     mouse (`mixin.MouseHandlerMixin`), client extensions (`mixin.ItemClientMixin`, `FluidTypeMixin`), GeckoLib 4.8's
     bone setters and four-float colours (`client.Geo`).
2. **Rules**: ordered substitutions over the Java (renamed classes, packages and methods; overridden methods' changed
   signatures; tick events as Forge's one-with-a-phase; NeoForge's calls as the stand-ins'), and per-file fixes
   (`FILE_FIXES`, each asserting the text it changes is still there). Among them: a fuel's burning time is
   `ForgeHooks.getBurnTime` (Forge's `stack.getBurnTime(type)` is only the item's own answer, -1 for vanilla's fuels).
3. **Resources**: 1.20.1's plural data folders (`recipes`, `loot_tables`, `tags/blocks`, ...); NeoForge's `c:` common
   tags as Forge's `forge:` ones, a few renamed (`TAG_RENAMES`: `c:glass_blocks` is `forge:glass`, `c:sands/colorless`
   `forge:sand/colorless`; the code's go through `legacy.Tags2`, whose table `port.py` checks against its own), and the
   one Forge has none of, concrete, the mod's own (`groundburst:concretes`); recipes' results, stonecutting's as 1.20.1
   writes it; item predicates in advancements and loot (a tool's silk touch, shears) as 1.20.1's; models' `neoforge:`
   loaders as Forge's, the short grass's texture by its old name; the site templates saved at 1.20.1's data version,
   the blocks 1.20.1 lacks as it has them (`PALETTE_RENAMES`: short grass is `grass`; the guard's and the mast's huts'
   weathered copper doors are spruce doors, which a hand opens as it does a copper door; the test site's heavy core, the
   device on its stand, a lodestone).
4. **Hand edits**, listed in `tools/backport/manual.txt` (kept as they are by `port.py`; `--sync` merges the 1.21.1
   build's changes into them): the armour materials (`GBArmor`: 1.20.1's are an enum), GeckoLib renderers and items
   (`MachineGeoRenderer`, `GeoBlockItem`), the effects drawn last (`DrawnLast`: 1.20.1's stage poses carry the camera's
   turn), the saved data (`SavedDataType`), Iris's API by reflection (`IrisFx`), and the turned and round model parts'
   loader (`FreeRotationModels`: Forge 1.20.1's geometry API; its polygons baked through Forge's buffered quad baker,
   a sprite's UVs in pixels).

**The client** differs from 1.21's in three ways the port makes up for:

- 1.20.1's `Screen.render` draws no background (1.20.2 moved it there): a screen's own 1.21 background (its
  four-argument `renderBackground`, kept as its own method) is drawn from its `render`, just before the game's
  `render`, by the screen that extends the game's (`port.py`'s `screens`: the Field Manual's book, the machines' panels).
- A 1.20.1 core shader sets its own blending as it is used (its JSON's `blend`; none is opaque), over the render
  type's: the clouds' shader (`shaders/core/rendertype_fx_cloud`, this branch's own: 1.20.1's fog distance takes the
  model view, and a position drawn as entities are turned back) blends as its render type does, and the glow (the beam's
  light, a fireball's core) draws on this branch's `rendertype_fx_glow`, the eyes shader blending additively (the eyes
  shader's own file does not add).
- Forge asks an item for its client renderer from inside Item's constructor, before the item's own fields are set: the
  GeckoLib items read theirs as the renderer is first made (`GeoBlockItem`).

**Healing** of the game's own explosions: 1.20.1's explosion breaks each block in its own loop, not through the block's
`onExplosionHit` (1.21's, which the 1.21.1 build wraps), so `legacy.mixin.ExplosionHitMixin` does the loop's work for a
block (its drops, then Forge's `onBlockExploded`) as that hit and skips the loop's own; the fire's spreading is Forge's
`tryCatchFire` (a `FireBlockMixin` fix in `port.py`).

`port.py` (every file) or `port.py <paths>`; then compile and sort the errors:

```
./gradlew compileJava --console=plain -q > build/port-compile.txt 2>&1
tools/backport/errors.py build/port-compile.txt 100
tools/backport/errs.py "<words>"
```

(Run the Python with the main checkout's venv, `../Explosive Mod/tools/.venv/Scripts/python.exe`, and Gradle with the
JDK 25 runtime as `JAVA_HOME`: the toolchain finds Java 17 itself.)

## Checks

`gradlew runGameTestServer` runs all of main's GameTests (`GBGameTests.specs()`, made into 1.20.1's test functions by
`gametest/GameTestRegistration`) beside the 1.21.1 build's seven (`port/SmokeTest`); `-PonlyTests=<name>,<name>` runs
only those of main's. Three things 1.20.1's test framework does otherwise, each made as 1.21's:

- Its test server makes its world as a dedicated server does (from `run/server.properties`, a normal world unless told
  otherwise, each test then dug into its stone) and keeps it from run to run: each run starts on a new flat world
  (build.gradle).
- It leaves each test's plot open to the next (a blast in one reached its neighbour): 1.21 walls each round with
  barriers and roofs those with no sky, here as each test starts (`GameTestRegistration.encase`).
- Its mock server player has no connection, and Forge's player inventory wrapper tells a server player's client of an
  item it is given: the mock player is on a connection that sends nothing (`port/Tests`).

`gradlew runPortCheck` is the 1.21.1 build's check, unchanged: a flat creative world, the mod's blocks set out, the Field
Manual, a blast, a particle beam and its pit, sea mines and a depth charge's column, a Depth Charge Rack's pattern, the
bunker doors, the command room, the bunker fittings and the yard, each photographed (`run/screenshots/port_*.png`) and
its log lines OK.

## Where it stands (2026-11-11)

Begun. It compiles, builds a jar for players, loads on a server and in the client: all 450 GameTests pass, and
`runPortCheck` passes all its checks, its pictures as the 1.21.1 build's (but for 1.20.1 dimming a screen's world where
1.21 blurs it). Not yet: play in a 1.20.1 instance, the shader-pack mods 1.20.1 has (Oculus and Embeddium, not Iris and
Sodium), Distant Horizons, and Create (for 1.20.1: Create 0.5.1 and its own addons; the 1.21.1 branch's compat is left
out here).

## Bringing it up to the 1.21.1 branch

`port.py --sync <commit>` brings this branch up to a newer commit of the 1.21.1 branch: every file changed since
`base.txt`'s commit is ported again; one edited by hand here gets the change merged into it (a three-way merge of the
ported text), conflicts left marked. Then compile, run the GameTests, fix what broke, and update the line above.
