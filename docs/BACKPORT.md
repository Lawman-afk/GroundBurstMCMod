# Ground Burst for Minecraft 1.21.1 (the backport)

The mod is made on Minecraft 26.3 (branch `main`). This branch, `1.21.1`, carries it back to Minecraft 1.21.1 on
NeoForge 21.1 for players still on that version. It is not kept in step with every change: it is brought up to `main`
every once in a while, as a whole.

- **Last brought up to:** `main` at `c2acaac1` (2026-11-06, for 0.9.0: healing the game's own explosions and other
  mods', main's DESIGN.md §21.9, here through this branch's own two mixins, below; before it, at `aa759d21`, the Depth
  Charge Rack, main's DESIGN.md §14.6, a stern rack
  that rolls depth charges off into the sea, loaded through its Item Port by hoppers, pipes and Create's funnels; a
  charge rolled off keeps its axis in its own synced data, since 1.21.1's client turns a new projectile's rotation to its
  motion (`Projectile.lerpMotion`); before it, at `e2e3df4f`, the blast effects drawn last under shader packs and with
  Distant Horizons, main's DESIGN.md §12.3, here by this branch's own `client/fx/DrawnLast`, below; before them, at
  `d4df3de0`, sea mines, moored on their chains and adrift, and depth
  charges, main's DESIGN.md §14.6, with blasts under water that keep the sea; the port tool taught that 1.21.1 tells an
  entity a player uses it without where; before them, at `464565de`, 0.8.1, released for this version and 26.3:
  `groundburst-mc1.21.1-0.8.1.jar` is this branch's; before it, at `59d16c5d`, healing wakes cables, pipes and other mods' blocks and leaves
  this mod's blocks as they come back, main's DESIGN.md §21.9, here where a block's onPlace always runs; the blast effects
  under shader packs, main's §12.3, this branch's own way, below; before them 0.8.0 at `01689009`, and at `07db772c`
  fire never healed back, and damage waiting while a fire burns
  by it; main's performance check, its DESIGN.md §11.8, with the play bench, which runs here too: `gradlew runPlayBench`
  `[-Pjfr=<name>]`, its client half this branch's own (`client/fx/PlayBenchClient`, the world made as the port check
  makes its); before them healing, off by default, main's DESIGN.md §21.9: the damage every weapon does grows back over
  time, block for block as it was; before it every weapon on ships and contraptions,
  the designator and mortars aimed at ships, a crater lake's hang below y 0 fixed; before them blasts reaching ships and contraptions and
  every machine on moving structures, main's DESIGN.md §17.16; before them 0.7.0, released for both versions:
  `groundburst-mc1.21.1-0.7.0.jar` is this branch's).
- **Toolchain:** Minecraft 1.21.1, NeoForge 21.1.265, Java 21, GeckoLib 4.9.3, JEI 19.70 (optional), Create 6.0.10 and
  Sable 2.0.6 / Create Aeronautics 1.3.2 (optional; see below).
- **Test instance:** a separate 1.21.1 CurseForge instance, named by `deploy_instance_dir_1_21_1` in your own
  `~/.gradle/gradle.properties` (never `deploy_instance_dir`, which is main's 26.3 instance).

## How it is made

Most of the difference between 26.3 and 1.21.1 is mechanical: names that changed (`Identifier` was `ResourceLocation`,
`GuiGraphicsExtractor` was `GuiGraphics`), methods that moved, a different signature on an overridden method. So the
code is not ported by hand file by file. `tools/backport/port.py` turns main's sources into this branch's:

1. **Rules**: ordered text substitutions over the Java sources (renamed classes and methods, changed signatures of
   overridden methods, imports).
2. **Compatibility classes** in `dev.groundburst.port`: small stand-ins with main's API over 1.21.1's (a `ValueOutput`
   over a `CompoundTag`, for example), so the code that uses them needs no change at all. They exist only on this
   branch.
3. **Resource conversions**: GeckoLib 4 reads `geo/` and `animations/` (GeckoLib 5 reads `geckolib/models/` and
   `geckolib/animations/`); recipe ingredients are objects in 1.21.1 (`{"item": ...}`), not strings, and a recipe that
   needs one of 26.x's new items is left out; 26.x's item model definitions (`assets/*/items/`) become 1.21.1's item
   models (`port.py`'s `item_models`: GeckoLib items on `builtin/entity`, the Geiger counter's GUI and hand models as
   NeoForge's separate transforms, the missile's looks as custom model data overrides, the dosimeter's stages as
   overrides on its dose, buckets on NeoForge's fluid container loader), with what they need from code in
   `ItemModelTable`; configured features get back their `config`, the wasteland biome its effects and spawners, the
   tags' outside entries become optional, the armour textures move to `textures/models/armor/`; the structure
   templates' palettes are named as 1.21.1 reads them (`Name`, `Properties`: 26.x's lowercase `id` and `properties`
   read as air, so the world's sites came out empty in 0.7.0) and saved at 1.21.1's data version, with the iron chain
   as 1.21.1's chain and the copper age's weathered lightning rods as plain ones (`port_structure`; the GameTest
   `sitesHaveTheirBlocks`).
4. **Hand edits**, where no rule is worth writing (some of the client's rendering, which 26.x rebuilt round render
   states and submit nodes; the recipes; armour materials). The files edited by hand are listed in
   `tools/backport/manual.txt`. Files only this branch has (the `port` package, the clouds' shader
   `shaders/core/rendertype_fx_cloud.*`) are never touched by `port.py`, which only writes what main has.

`tools/backport/compile.ps1` (run with PowerShell 7) compiles and sorts the errors; `errs.py <words>` prints a file's
errors with their lines. `gradlew runGameTestServer` loads everything on a server and runs **all of main's GameTests**
(417 at main ca16336b, every one: `PortSkips` is empty) beside this branch's own seven (424). Main's are ported like the
rest of its code (the gametest package; `port.py`'s rules for 26.x's test helper: `getBlockEntity(pos, Type.class)`,
`makeMockServerPlayer` as `port/Tests.mockServerPlayer`, a missile's look by number, and a few per-file fixes); how they
reach the game is this branch's own `gametest/GameTestRegistration` (`manual.txt`): a generator of 1.21.1's test
functions from main's list (`GBGameTests.specs()`), all in one batch, logging how many. Two things 1.21.1's test
framework does otherwise, both made as main's: its helper counted a test's blocks from the structure block, which it
sets a block under the structure, so every test stood a block low (`port/mixin/GameTestHelperMixin`, in the branch's
own `groundburst.port.mixins.json`, declared in its `neoforge.mods.toml`: from the structure's corner, as 26.x's does);
and its mock server player joins the server's player list on a connection that cannot take the mod's packets (the
first one sent to every player crashed the run), where 26.x's is in no list (`port/Tests`: the same). Running them
found four of this branch's own faults, fixed: the mod's liquid buckets had no fluid handler here (main named only the
gas buckets; NeoForge 21.1 gives the plain ones only its older kind), so they poured into nothing of the mod's; the
machines took the game's first matching recipe, which 1.21.1 lists in another order (RDX made of photoresist's
makings; main's `ProcessingRecipe.pick` now takes the matching recipe that takes the most); frosted and dead grass were
not dirt here, so a cryo burst shed the plants on them instead of freezing them (`port.py`'s `grass_into_dirt`: 1.21.1's
plants stand on `#dirt`, 26.x's on `#supports_vegetation`, which takes in main's `#grass_blocks`); and the meltdown
test now looks under the plot's floor for its corium (main's too). One test runs otherwise here: 1.21.1 feels what a
mob stands in only as it moves, so the napalm test's pig keeps its AI. The branch's own tests
(`dev.groundburst.port.SmokeTest`: it loads; a particle beam carves its trench; transactions are each thread's own; the
Wrench splits and joins pipes and cables; healing puts a blast's damage back block for block, a chest with its diamonds,
and a creeper's explosion too, its item frame hung again, a block of TNT it lit not put back); `gradlew runPortCheck` opens a world, sets the mod's blocks out (a Warning
Beacon lit by redstone, buckets in the hotbar), opens the Field Manual, sets a blast off, brings a particle beam down and
looks into its pit, lays sea mines in a walled sea (seen from under the water and from above) and sets a depth charge
off under them (its column), builds a Depth Charge Rack on a quay in that sea (four charges put on, two from a hopper over
its Item Port, the log saying how many it holds) and rolls them all off as a pattern, screenshots each into `run/screenshots/port_*.png` and quits (`dev.groundburst.port.PortCheck`; it
keeps the game from pausing when its window is behind another).

## Where it stands (2026-11-03)

Released as 0.7.0, beside main's 26.3 build. It compiles, runs and is deployed to the 1.21.1 instance: `runGameTestServer` loads every registry and all the data
(recipes, tags, loot, world generation) with no errors and passes its four tests, and `runPortCheck` shows the
machines, the Field Manual, a blast and a particle beam drawing as they should. Fixed after the first play:

- **The crash with the gas machines**: the port's transactions (what the fluid and energy transfers run in) kept the
  open one in a single static, shared by the client and the integrated server, which run side by side; a second one
  opened on the other thread threw. Each thread has its own now.
- **The Wrench** threw on every use (1.21.1's `neighborChanged` wants the position the change came from; main passes
  none), so it did nothing and logged a stack trace each click: `port.py` passes the block's own position.
- **Buckets** lost their fluid's colour: NeoForge 21.1's fluid container model tints its fluid layer only through an
  item colour handler, now registered for every bucket (`ItemModelTable.FLUID_CONTAINERS`).
- **Glow masks** (the Warning Beacon's lamp lit black): GeckoLib 4 lights the base texture's pixels under a mask,
  GeckoLib 5 the mask's own; `MachineGeoRenderer` draws the mask itself, lit, as main does.
- **Clear rectangles round smoke and clouds**: smoke writes depth, and on vanilla's emissive shader each puff's clear
  corners wrote depth too, hiding what was drawn behind them after it; the clouds' own shader cuts out the faint
  fringe, as main's does.

**Healing** (main's DESIGN.md §21.9) comes over with the rest, but for what differs between the versions, kept by hand
(`manual.txt`): `heal/Snapshots` (a block entity's and an entity's save and load through tags, the day's time from
`getDayTime`), and three of its mixins: `FireBlockMixin` (1.21.1's fire sets its block with `setBlock(pos, state, 3)`),
`SpreadingSnowyBlockMixin` (grass is `SpreadingSnowyDirtBlock`) and `FarmlandBlockMixin` (`FarmBlock` and its static
`turnToDirt`), and the two for the game's own explosions: `BlockStateBaseMixin` (1.21.1's explosion hit takes any
level, as the client runs an explosion's blocks too, ahead of the server's word: only the server's is remembered) and
`ServerExplosionMixin` (1.21.1's explosion is the one class `Explosion`, which hurts what it reaches in `explode`; it
also gives out its damage source, `port/ExplosionDamage`, which main's has a getter for, so a bed's explosion is known).
The other mixins (`BlockMixin`, `FlowingFluidMixin`, `LeavesBlockMixin`) are the same on both, and the
mixins are declared in this branch's own `neoforge.mods.toml`. 1.21.1 has no flags to skip a block entity's side effects
or a block's on-place hook (the port takes them out): a block that will heal has its block entity taken out first, so
nothing spills, and a healed block runs its on-place hook (a healed water source flows, as it should). The heal check
(`HealPhotographer`) is main's alone.

**Effects' cost** (main's DESIGN.md §11.10): the one-call vertices, the fallout layer laid out once and the mushroom
cloud's quicker sums come over with main's code; main's sorting of the effects' blended quads itself (`FxSort`) does
not: this branch's `FxRenderTypes` is its own (kept by hand) and keeps the game's sorting, its `FxSort` holding nothing
back.

Still to do:

1. **Kept simpler than main**: consumables (Rad-Away, potassium iodide, the injector) are eaten as food with no
   nourishment; mouse cursors stay the arrow; flash particles are not tinted.
2. Play it through in the 1.21.1 instance.

## Shader packs (Iris) and Distant Horizons

Under a pack (and with Distant Horizons) the effects are drawn last, as on main (main's DESIGN.md §12.3): kept out of
the level and drawn into the finished picture at NeoForge's after-level stage (`client/fx/DrawnLast`, `FxRenderTypes`
and `IrisFx`, all kept by hand). NeoForge 21.1 fires that stage from the game renderer once `LevelRenderer.renderLevel`
has returned, so after Iris 1.8's last passes (Iris runs them as the level's drawing returns, after the stage of the
same name inside it) and Distant Horizons' fade, and before the hand's depth is cleared. Iris swaps none of the pack's
programs in there, so the effects draw on the game's own shaders and this branch's cloud shader, into the game's buffers,
with the view and fog the level had and its depth, near 0 and far 1, as Iris leaves it.

The client option "Effects with shader packs: Lit by the pack" (`-PpackFx=in` in dev runs) keeps the way 0.8.1 had it:
Iris 1.8 puts a pack's programs in place of the game's own shaders by name; under Complementary Reimagined (2026-11-05)
the effects did not shatter as on 26.3, but the fire, on vanilla's eyes shader, glowed white (the pack's glowing
program) and the smoke, on this branch's own cloud shader, did not show at all. So then the fire and the clouds switch to
vanilla's translucent entity shader, which Iris swaps for the pack's lit entity program, with the lightmap set up for it,
and the glow stays on the eyes shader. Either way effects stay out of the pack's shadow pass.

- **Checked** (2026-11-06): `runPortCheck` under Bliss and Complementary Reimagined and without a pack (the blast, the
  particle beam, the water column, the mines: all alike), and with Distant Horizons 3.3.3 (`-PwithDH`), without a pack and
  under Bliss.
- **Dev runs**: `-PwithIris` loads Iris 1.8.14 and Sodium 0.8.13, with seven packs in run/shaderpacks
  (`-PirisPack=complementary|unbound|bsl|bliss|photon|solas|makeup|off`), e.g. `gradlew runPortCheck -PwithIris
  -PirisPack=bliss`; `-PwithDH` loads Distant Horizons. Sodium for 1.21.1 comes as a wrapper (a library of its early
  start-up classes, with the mod and its Fabric API parts packed inside): on the runs' classpath the launcher took the
  wrapper for the mod and loaded none of Sodium, and the packed mod alone lacks the wrapper's classes, so the runs with
  Iris copy it into run/mods (`sodiumToMods`, as `sodium-dev-iris.jar`), where it loads as it does for players, and every
  other run deletes it again (`sodiumFromMods`).

## Create and Create Aeronautics (this branch only)

Create and Create Aeronautics (and Sable, the library its ships are made with) exist only for 1.21.1, so this branch
supports them; main has what the machines need for it (frames, carried and moved machines: main's DESIGN.md §17.16),
where every frame is the world's. Both are optional (`create` [6.0.0,7) and `sable` [2.0.0,3) in the mods.toml), and
nothing touches either mod's classes unless it is installed.

- **Code**: the branch's own files under `src/main/java/dev/groundburst/compat/`: `GBCompat` (starts what is installed;
  registers the `carried_sync` payload), `sable/SableFrames` (a block in a ship's plot stands in the ship's frame, read
  through Sable Companion), `create/CreateCompat` (Create may move our blocks; formed structures and two-block machines
  hold together; every machine gets `CarriedMachine`, every machine and structure part `OpenOnContraption`),
  `create/CarriedMachine` (the carried copy of any machine, ticked with the contraption, saved back into it, its looks
  sent to clients), `create/Aboard` (once a tick for each contraption: its generators' and banks' power shared out fairly
  among its machines, its pipe runs moving items and fluids between what it carries), `create/OpenOnContraption` (a
  right-click on a carried machine opens its menu from the server, or the computers' and radar's screens on the client),
  `create/CarriedClient` (finds a carried machine's client copy by its key, for menus and screens),
  `create/ContraptionFrame`, `create/CarriedSync` and `CarriedSyncClient`, `create/CheckScenes` (the Create calls the
  check makes), and `CreateCheck` (the in-game check). `port/transfer/Adapters` sees Create's item and fluid handlers as
  ours. Blasts and the slower weapons (main's `Hulls`, `HullBlast`, `HullEffects`): `sable/SableHulls` (a ship's plot
  blocks broken, scorched, frozen, snowed on or set alight, the ship shoved and pulled through Sable's `RigidBodyHandle`;
  a ship with no blocks passed over, since Sable's box for an empty plot runs over the whole int range; its blocks read
  from the plot's own chunks, never through the level), `create/ContraptionHulls` (a contraption's blocks broken out of
  its entity, a machine, a chest's contents and a seat with them, never a carriage's bogeys; a plain block scorched or
  frosted, none added) and `create/BlocksChanged` with `BlocksChangedClient` (players' copies change them too, redrawn
  and re-collided), `sable/SableScenes` (the Sable calls the check makes).
- **Drops**: the port's stand-in for 26.x's `preRemoveSideEffects` (made by `port.py` in every block entity that drops
  its contents there) drops only when the block itself has left its place; a Create contraption takes the block entity
  while its block is still there, so nothing is dropped and nothing had twice. Sable empties `Clearable` block entities
  (machines and guns are) after saving them.
- **Build**: compiled against Create's release jar from Modrinth's maven (Create's own maven's certificate had expired),
  Sable Companion 1.6.0 from maven.ryanhcode.dev and Sable itself (Modrinth, for its physics: a blast shoves a ship);
  none ships in our jar. Create's helper library Catnip is not on the compile path: use what Create itself declares
  (contraptions are found by an entity search, not Create's `ContraptionHandler` list). `-PwithCreate` puts Create, Sable and
  Create Aeronautics into the dev runs (versions in gradle.properties: Modrinth version ids).
- **Check**: `gradlew runCreateCheck -PwithCreate`: a Cruise Missile Launcher on a mechanical bearing turned by a
  creative motor, and one made into a Sable ship and turned 30 degrees, each fire; the log says whether each missile
  lit where its launcher is and headed the way it points, whether each launcher stayed on its network, and whether the
  bearing, stopped at a quarter turn, set its launcher down formed. A factory on a second bearing: a launcher loaded by
  a pipe from a chest aboard, a fluid pipe filling a separator from a Create tank, a generator powering the launcher and
  a Launch Computer, the launcher's menu and the computer's screen opened by right-clicks, set down with nothing dropped
  and the pipe still pulling. A yard on a third bearing: a Medium Missile Assembler building a missile and a loaded
  Launch Silo, nothing dropped, the build going on aboard, both menus opened, set down formed with nothing doubled.
  A wreck on a fourth bearing: a small blast over a turning platform's hub breaks the chest, the generator and the hub
  out of it (players' copies agree, nothing spilled), a mortar shell bursts on its rim, and it is set down with only
  what was left. A second ship: a shell's blast beside it breaks its near side and shoves it off, and a shell fired at
  it bursts on it. Then `ShipCheck`: a Laser Designator's beam lands on a ship where it is seen; a mortar laid on a
  ship's deck falls on it, not blocked; a particle beam eats through a wooden platform's middle and chars its edge; a
  deep freeze rimes and snows on a stone ship; a small singularity draws a floating ship in; a nuclear blast eighty
  blocks off chars a wooden ship, sets it alight and throws it, no server tick stalling (the emptied ship before it,
  the crater's lake). Then `HealCheck`: a creative motor turns a row of shafts, a weapon takes three out of the middle
  where healing is on (the far end stops), and healing puts them back turning at the speed they had. Then `RackCheck`:
  a Create funnel on a Depth Charge Rack's Item Port loads the charges dropped on it; a rack aboard an airship turned
  30 degrees rolls a charge off from its screen's button, from where the ship has the rack, out the way the ship points
  it, lying across with its axis turned with the ship, and a redstone block put beside it aboard rolls its last one off.
  Pictures: run/screenshots/create_*.png. 57 of 57.
- **Test world**: `gradlew runTestRange -PwithCreate` builds "Ground Burst Test Range" in the dev client and saves it to
  run/saves (copy that folder into the 1.21.1 instance's saves; it plays there as is): a flat world 64 layers deep (the
  ground at y 0) with a command post at the spawn (Launch Control Station, Launch Computer, Radar Array, an armory of
  missiles, shells and explosives), one of each launcher loaded and charged north of it (Launch Silo, Cruise Missile
  Launcher, Vertical Launch Battery, Launch Complex, Missile Silo) with the mortar and the howitzer, and on Create: a
  Cruise Missile Launcher turning on a bearing, a Launch Silo a sticky piston raises out of a pit (a lever on a gearshift
  by the pit lowers and raises it); on Create Aeronautics: a missile boat on the lake and an airship over the airfield
  (held up by levitite under a balloon), each with a Cruise Missile Launcher aboard; target ships, a target town 420
  blocks north with a windmill on a bearing. Every launcher and the radar are on the station's network; the player
  starts with a Laser Designator and Launch Codes for it. It checks as it builds (the ships float or hover where they
  were made, the pit silo goes down and up again and forms each time) and photographs itself
  (run/screenshots/range_*.png). Code: `compat/range` (`TestRange`, `RangeGround`, `RangeShips`, `RangeCreate`,
  `RangeBlast`), left out of the released jar (build.gradle's jar task): only the dev runs have it. `-PrangeBlast=
  <profile>@<place>` (airship, target_airship, boat, town, or x,y,z) instead sets that blast off once the ships are made,
  logs every slow server tick and, from a watchdog, the server thread's stack whenever one tick runs past five seconds,
  then quits as a player does. Create gotchas: a plain mechanical piston pushes but does not pull (a sticky one does);
  redstone links left with empty frequencies did not reach each other here, so the lever drives the piston through
  shafts and a gearbox instead.
- **Not yet**: reactors are held while carried; nothing passes between a contraption and the world's blocks round it
  (pipes, cables, redstone).

## Bringing it up to main

Because the port of a file is repeatable, a newer `main` comes in as a three-way merge of ported text: for each file
`main` changed since the last sync, the old and new versions are both run through `port.py`; a file nobody edited by
hand here is replaced outright, and one that was edited gets main's change merged into it (`git merge-file`), so only
where a hand edit and main's change touch the same lines is there anything to resolve. Then build, run the GameTests,
fix what broke, and update the line above.
