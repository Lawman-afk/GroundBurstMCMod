# Ground Burst for Minecraft 1.21.1 (the backport)

The mod is made on Minecraft 26.3 (branch `main`). This branch, `1.21.1`, carries it back to Minecraft 1.21.1 on
NeoForge 21.1 for players still on that version. It is not kept in step with every change: it is brought up to `main`
every once in a while, as a whole.

- **Last brought up to:** `main` at `c22e82a4` (2026-11-04: blasts reach ships and contraptions, after every machine on
  moving structures, main's DESIGN.md §17.16; before them 0.7.0, released for both versions:
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
   tags' outside entries become optional, the armour textures move to `textures/models/armor/`.
4. **Hand edits**, where no rule is worth writing (some of the client's rendering, which 26.x rebuilt round render
   states and submit nodes; the recipes; armour materials). The files edited by hand are listed in
   `tools/backport/manual.txt`. Files only this branch has (the `port` package, the clouds' shader
   `shaders/core/rendertype_fx_cloud.*`) are never touched by `port.py`, which only writes what main has.

`tools/backport/compile.ps1` (run with PowerShell 7) compiles and sorts the errors; `errs.py <words>` prints a file's
errors with their lines. `gradlew runGameTestServer` loads everything on a server and runs this branch's own tests
(`dev.groundburst.port.SmokeTest`: it loads; a particle beam carves its trench; transactions are each thread's own; the
Wrench splits and joins pipes and cables); `gradlew runPortCheck` opens a world, sets the mod's blocks out (a Warning
Beacon lit by redstone, buckets in the hotbar), opens the Field Manual, sets a blast off, brings a particle beam down and
looks into its pit, screenshots each into `run/screenshots/port_*.png` and quits (`dev.groundburst.port.PortCheck`; it
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

Still to do:

1. **Kept simpler than main**: consumables (Rad-Away, potassium iodide, the injector) are eaten as food with no
   nourishment; mouse cursors stay the arrow; flash particles are not tinted.
2. **GameTests**: main's 362 are not ported yet (this branch has its own four).
3. Play it through in the 1.21.1 instance.

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
  ours. Blasts (main's `Hulls`): `sable/SableHulls` (a ship's plot blocks broken, the ship shoved through Sable's
  `RigidBodyHandle`), `create/ContraptionHulls` (a contraption's blocks broken out of its entity, a machine, a chest's
  contents and a seat with them; never a carriage's bogeys) and `create/BlocksGone` with `BlocksGoneClient` (players'
  copies forget them, redrawn and re-collided), `sable/SableScenes` (the Sable calls the check makes).
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
  it bursts on it. Pictures: run/screenshots/create_*.png. 39 of 39.
- **Not yet**: reactors are held while carried; nothing passes between a contraption and the world's blocks round it
  (pipes, cables, redstone); the particle beam, the cryo freeze and a singularity's pull do not reach ships or
  contraptions.

## Bringing it up to main

Because the port of a file is repeatable, a newer `main` comes in as a three-way merge of ported text: for each file
`main` changed since the last sync, the old and new versions are both run through `port.py`; a file nobody edited by
hand here is replaced outright, and one that was edited gets main's change merged into it (`git merge-file`), so only
where a hand edit and main's change touch the same lines is there anything to resolve. Then build, run the GameTests,
fix what broke, and update the line above.
