# Ground Burst for Minecraft 1.21.1 (the backport)

The mod is made on Minecraft 26.3 (branch `main`). This branch, `1.21.1`, carries it back to Minecraft 1.21.1 on
NeoForge 21.1 for players still on that version. It is not kept in step with every change: it is brought up to `main`
every once in a while, as a whole.

- **Last brought up to:** `main` at `f94c2cd4` (2026-11-03: version 0.6.0, the finished artillery, and the fixes after the
  first play of this branch).
- **Toolchain:** Minecraft 1.21.1, NeoForge 21.1.265, Java 21, GeckoLib 4.9.3, JEI 19.70 (optional).
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

It compiles, runs and is deployed to the 1.21.1 instance: `runGameTestServer` loads every registry and all the data
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

## Bringing it up to main

Because the port of a file is repeatable, a newer `main` comes in as a three-way merge of ported text: for each file
`main` changed since the last sync, the old and new versions are both run through `port.py`; a file nobody edited by
hand here is replaced outright, and one that was edited gets main's change merged into it (`git merge-file`), so only
where a hand edit and main's change touch the same lines is there anything to resolve. Then build, run the GameTests,
fix what broke, and update the line above.
