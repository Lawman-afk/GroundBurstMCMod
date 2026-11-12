# Ground Burst for Minecraft 1.20.1 (the Forge backport)

The mod is made on Minecraft 26.3 (branch `main`) and carried back to 1.21.1 on NeoForge 21.1 (branch `1.21.1`). This
branch, `1.20.1`, carries the 1.21.1 build back once more, to Minecraft 1.20.1 on Forge 47, where most of the mod's
players are (main's DESIGN.md §30). It is brought up to the 1.21.1 branch every once in a while, as a whole, as that
branch is brought up to main.

- **Last brought up to:** the `1.21.1` branch at `ad4100ea` (main at `15431b92`, 2026-11-12: version 0.12.0,
  released for all four versions as `groundburst-mc1.20.1-0.12.0.jar`: main's changelog. Synced cleanly, 462 GameTests.
  The release checks: `runFpsCheck -PfpsMachines=true` timed 49 machines and structures alone, the slowest the
  Particle Uplink Cannon at 2.08 ms a frame (the empty field 1.01: vanilla 1.20.1 draws its chunks more slowly), every
  model's cubes steady; the reobfJar in a real Forge 47.4.28 client (`prod_client.py`, with GeckoLib 4.8.4 and JEI
  15.56): the port check 7 OK, none WRONG, no errors, its far flash, far wave and black rain among them); before it, at
  the `1.21.1` branch's `e217cd45` (main at `4c88e09b`, 2026-11-12: a player's wishlist
  built, main's DESIGN.md §21.10: healing by a real clock with its times in units, settings by weapon, the terrain tables
  as data with swap rules, the far flash, black rain and the far aftermath. This branch's own pieces: the black rain's
  colour through 1.20.1's `color(FFFF)` (`port.py`'s fix of the 1.21.1 build's mixin; its refmap names `m_85950_`);
  the far glow's shader file of its own, `rendertype_fx_far_glow.json` (OWN: a 1.20.1 core shader sets its own
  blending, added, and the clouds' vertex shader here wants the view's rotation); the terrain tables' block names as
  1.20.1's (`port_terrain_table`: 1.20.3's short grass is 1.20.1's grass, in the code's built-in tables too); NeoForge's
  `c:sandstone/blocks` as Forge's `forge:sandstone` (`TAG_RENAMES` and `legacy/Tags2`); `ServerLifecycleHooks`'
  Forge package; black rain's corrosion through 1.20.1's `hurtAndBreak` with its breaking callback; Distant Horizons'
  API on the compile path (`modCompileOnly`) for `compat/DistantHorizons`. 462 GameTests; the port check (with the
  1.21.1 build's far reach scenes) plain, under Oculus with Complementary and with Distant Horizons (1 391 changed
  chunks handed to it), and its reobfJar's in a real Forge 47.4.28 client (prod_client.py: every check OK, the
  black rain and the far wave drawn as in the dev runs); before it, at `9a3b8c05` (main at `97a5792e`, 2026-11-11: the site check,
  `port/SiteCheck`, which here asks a part itself whether it is closed in rock). Synced cleanly; before it, at `1cfbf920`
  (the Depth Charge Rack holds its rest pose while it works; a carried machine's client copy carried too,
  `compat/create/CarriedClient`; the 1.21.1 build's own test-run hooks left out, this branch has its own); before it, at
  `2ef851ae` (main at `ae7e2623`, 2026-11-11: the war room removed), where it was begun.
- **Toolchain:** Minecraft 1.20.1, Forge 47.4.28 (the mod asks for 47.4 or later: built and tested on 47.4.28, never on the older 47 builds), Java 17, GeckoLib 4.8.4
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
   device on its stand, a lodestone). A site's parts closed in rock underground (a bunker's rooms, a silo's shaft, a
   waste store's vault: 1.21's `encapsulate`, which 1.20.1 has not, and whose reader takes a value it does not know for
   no shaping of the ground at all) carry `bury` instead, in their data and in the code (`port_site`), which stands for
   it here (no part of the mod's is buried otherwise): such a part tells Forge's beardifier hook NONE, and each chunk's
   beardifier closes it in rock as 1.21's does (`legacy.world.Encapsulation`, `legacy.mixin.BeardifierMixin`: its rock at
   half the distances outside the part's box, times 0.8, out to twelve blocks).
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

`gradlew runSiteCheck` is the 1.21.1 build's check of the sites in world generation: a normal world (seed 20261111),
the nearest of each site found, made and measured, each photographed from the air (`run/screenshots/sites_*.png`).
2026-11-11: all seven where the 1.21.1 build has them, each with its blocks (a few more or fewer than there: their props'
chances drawn otherwise, 1.21 listing more structures before the mod's), the same in the pictures; the parts closed in
rock shaped exactly as there (0.667, 0.400 and 0.133 at 2, 6 and 10 blocks on every side) and the ground round them as
open (218, 158 and 619 blocks of the 1.21.1 build's 217, 158 and 619). Before `Encapsulation` they were not shaped at all.

The test runs (the port check, the site check, the play bench, the Create check) are muted and leave the person at the computer their
mouse: the master volume is set to 0 in run/options.txt before each client run (`-Psound=on` keeps it), and their window
opens behind the other windows without the focus and never grabs the mouse (`legacy/client/DevRuns`, set by
`groundburst.keepMouse`; `DevWindowMixin`, `DevMouseMixin`), Forge's early loading window turned off for them in
run/config/fml.toml (it is made before any mod code runs).

**A release jar** (`reobfJar`'s, build/libs: the dev runs never use it) is checked in a real Forge client before it goes
out: `tools/backport/prod_client.py <game dir> <jar> portCheck|siteCheck <GeckoLib jar> [<more mods>]` starts the Forge
47.4.28 the CurseForge app installed (its version files, libraries and assets) on a throwaway game folder holding the
jar and the mods given, offline, muted and behind the other windows, with the check the jar carries switched on, and the
check quits it (its log in the folder's logs/latest.log, its pictures in screenshots/). A dedicated server cannot stand
in: Forge 1.20.1's asks for the EULA's acceptance before it loads a mod. 0.11.1 (2026-11-11), with GeckoLib 4.8.4 and
JEI 15.56: the port check 6 OK of 6, 28 pictures, no errors; the site check as in the dev runs. `PROD_JAVA=<java.exe>`
runs it on another Java, `PROD_VERSION=<installed version id>` on another loader's game (`neoforge-21.1.265` for the
1.21.1 build's jar).

**Java**: Minecraft 1.20.1's own is Java 17, which the CurseForge app and the game's launcher pick by themselves; the
jar is Java 17's. 0.11.1's port check also passed, every check OK and no errors, on Java 21 and on Java 25 (Forge
47.4.28), and the 1.21.1 build's 0.11.1 on Java 21 and 25 (NeoForge 21.1.265); on 25 the JVM warns of Forge's and
LWJGL's own restricted calls, nothing of the mod's. Players are told the launcher's own: 17 here, 21 for 1.21.1.

## Shader packs and Distant Horizons

On 1.20.1 shader packs come through Oculus (Iris for Forge, with Iris's own code and API) on Embeddium (Sodium for
Forge). The mod treats Oculus as it treats Iris (`FxRenderTypes.IRIS` asks for either mod id; `IrisFx` asks Iris's API by
name), so the effects are drawn last under a pack (`DrawnLast`), at Forge's after-level stage, which 1.20.1 fires from the
game renderer once the level is drawn, as NeoForge 21.1 does. Distant Horizons (3.3.3 for 1.20.1) likewise.

- **Dev runs**: `-PwithIris` loads Oculus 1.8.0 and Embeddium 0.3.31 (Modrinth) and puts the seven test packs in
  run/shaderpacks (`-PirisPack=complementary|unbound|bsl|bliss|photon|solas|makeup|off`; Oculus keeps Iris's settings
  as `config/oculus.properties`); `-PpackFx=in` is the client option "Lit by the pack"; `-PwithDH` loads Distant
  Horizons. E.g. `gradlew runPortCheck -PwithIris -PirisPack=bliss -PwithDH`.
- **Checked** (2026-11-11): `runPortCheck` under all seven packs and with Oculus's shaders off, "Lit by the pack" under
  Complementary, and Distant Horizons with no pack and under Bliss: every check OK, the effects drawn in every one.

## Create

Create 6 is out for 1.20.1 as for 1.21.1 (the same 6.0 line, with the same API for what the mod uses: movement and
interaction behaviours, block movement checks, contraptions' mounted storages), so the 1.21.1 branch's support for its
contraptions comes over whole, through `port.py` like the rest (`compat/GBCompat`, `compat/create`, `CreateCheck`,
`HealCheck`, `RackCheck`; main's DESIGN.md §17.16 and the 1.21.1 branch's BACKPORT.md): machines and launchers ride its
contraptions (bearings, pistons, gantries, carts, trains) and keep working there, sharing power and their pipes aboard;
a right-click opens a carried machine's menu or screen; blasts and shells break blocks out of a contraption; healing
puts Create's blocks back turning. Only Create Aeronautics' ships are left out: neither it nor Sable, the library its
ships are made with, has a 1.20.1 build (1.20.1's ships are Valkyrien Skies', another mod), so `compat/sable`,
`ShipCheck` and the ships' scenes of the checks are dropped (`port.py`'s `DROP` and `FILE_FIXES_BEFORE`).

- **Players**: Create 6.0.8 for Forge 1.20.1, optional (`create` [6.0.0,7) in mods.toml); nothing touches Create's
  classes unless it is installed.
- **Build**: compiled against Create's slim jar from its own maven (maven.createmod.net) and the parts its released jar
  carries inside it (Flywheel 1.0.5, Ponder 1.0.91, Registrate MC1.20-1.3.3), each turned into the dev runs' names by
  the legacy plugin; none ships in our jar. Build 289 is the released 6.0.8 (the same commit): the maven's later 6.0.8
  builds are development ones made against a newer Flywheel, and a contraption drawn with one threw. `-PwithCreate`
  loads them into the dev runs.
- **Check**: `gradlew runCreateCheck -PwithCreate`, the 1.21.1 branch's less its ships' scenes: a Cruise Missile
  Launcher on a mechanical bearing fires from where its boxes are, heading the way they point, and is set down formed
  and still on its network; a factory on a bearing (a pipe loading the launcher from a chest aboard, a fluid pipe
  filling a separator from a Create tank, a generator powering the launcher and a Launch Computer, their menu and
  screen opened by right-clicks), set down with nothing dropped; a yard (an assembler building aboard, a loaded silo);
  a wreck (a blast and a mortar shell breaking blocks out of a turning platform); Create's shafts healed turning again;
  a Create funnel loading a Depth Charge Rack. Pictures `run/screenshots/create_*.png`. 36 of 36 (2026-11-11), and no
  errors: a carried machine's menu threw on the client at first (Create 6.0.8's render world keeps no chunks, and the
  copy it draws told it of every slot the menu filled; the copy is carried now, the 1.21.1 branch's `CarriedClient`).

## Performance

`gradlew runPlayBench -Pjfr=<name>` (main's play bench: a normal world seen 32 chunks out, molotovs and a thermobaric
warhead in a forest, the fires, the healing) beside the 1.21.1 build's, the same day on the same machine (2026-11-11,
both muted and behind other windows):

| phase   | server tick ms, mean/p95 (1.21.1) | (1.20.1)  | frame ms, mean/p95 (1.21.1) | (1.20.1)    |
|---------|-----------------------------------|-----------|-----------------------------|-------------|
| quiet   | 9.8 / 11.8                        | 8.8 / 10.8 | 10.4 / 15.1 (97 fps)        | 13.6 / 19.3 (73 fps) |
| attack  | 10.4 / 15.4                       | 9.8 / 12.3 | 10.8 / 14.6 (93 fps)        | 15.8 / 21.5 (63 fps) |
| burning | 9.6 / 11.8                        | 9.0 / 11.3 | 9.8 / 13.8 (102 fps)        | 14.6 / 19.9 (69 fps) |
| healing | 10.0 / 11.9                       | 8.7 / 10.6 | 10.7 / 15.9 (94 fps)        | 14.3 / 19.7 (70 fps) |
| after   | 9.5 / 11.2                        | 8.1 / 9.8  | 10.0 / 14.3 (100 fps)       | 13.1 / 17.9 (76 fps) |

The server ticks a little faster here. The frames are slower, but not for the mod: before anything happens (quiet) half
the render thread's samples are vanilla 1.20.1's own `ChunkRenderDispatcher$RenderChunk.getCompiledChunk`, 32 chunks of
sections walked every frame (1.21's renderer does it more cheaply), and through the attack and the fires the mod's own
drawing is on the render thread's stack in 38 samples of 1665 (2.3%), fewer than the 1.21.1 build's 56 (of 904). The
stand-ins cost next to nothing (the data attachments a few samples on the server thread). With Embeddium, which most
1.20.1 players run (0.3.31, Oculus's shaders off: `-PwithIris -PirisPack=off`), the frames take 2.1 to 3.3 ms (300 to 485
fps) and the server ticks as before (8.4 to 9.1 ms): the attack adds 1.2 ms a frame to the quiet's, the fires 0.3. (The
error Embeddium logs, its "mixin taint" detector, names Oculus, not the mod.)

## Where it stands (2026-11-11)

Released as 0.11.1 (`groundburst-mc1.20.1-0.11.1.jar`, with the 1.21.1 build's 0.11.1; main's CHANGELOG), its jar checked
in a real Forge client. It compiles, builds a jar for players, loads on a server and in the client: all 450 GameTests pass, `runPortCheck` passes
all its checks (its pictures as the 1.21.1 build's, but for 1.20.1 dimming a screen's world where 1.21 blurs it), under
the seven shader packs through Oculus and with Distant Horizons too; Create's contraptions carry its machines (the
Create check, 36 of 36); the sites generate as the 1.21.1 build's, their parts underground closed in rock (the site
check); and the play bench finds nothing the port costs. Not yet: play in the 1.20.1 instance.

## Bringing it up to the 1.21.1 branch

`port.py --sync <commit>` brings this branch up to a newer commit of the 1.21.1 branch: every file changed since
`base.txt`'s commit is ported again; one edited by hand here gets the change merged into it (a three-way merge of the
ported text), conflicts left marked. Then compile, run the GameTests, fix what broke, and update the line above.
