# Ground Burst for Minecraft 1.21.1 (the backport)

The mod is made on Minecraft 26.3 (branch `main`). This branch, `1.21.1`, carries it back to Minecraft 1.21.1 on
NeoForge 21.1 for players still on that version. It is not kept in step with every change: it is brought up to `main`
every once in a while, as a whole.

- **Last brought up to:** `main` at `c7c644b6` (2026-11-03: version 0.6.0 and the finished artillery).
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
3. **Resource moves**: GeckoLib 4 reads `geo/` and `animations/` (GeckoLib 5 reads `geckolib/models/` and
   `geckolib/animations/`); recipe ingredients are objects in 1.21.1 (`{"item": ...}`), not strings.
4. **Hand edits**, where no rule is worth writing (the client's rendering, which 26.x rebuilt round render states and
   submit nodes). The files edited by hand are listed in `tools/backport/manual.txt`.

## Where it stands (2026-11-03)

Begun: the build, `port.py` and the compatibility classes for saving (`ValueInput`/`ValueOutput`), the screens' drawing
(`GuiGraphicsExtractor`, its 2D pose stack, `ARGB`), item use results, and NeoForge 26.x's transfer API (resource
handlers, transactions, item access, energy) bridged both ways to 1.21.1's item, fluid and energy handlers. The rules
cover renamed names and the overridden methods whose signatures changed (tooltips, shape updates, neighbour changes,
block removal, light, effects, item use). It does not compile yet: 1,208 errors in 170 files (from 4,490), nearly all in
what is still to come:

1. **Client rendering** (the most): the GeckoLib 4 renderers (`MachineGeoRenderer`, `VariantGeoModel`, `GeoBlockItem`,
   `LaunchStationRenderer`: procedural bones through the model's custom animations, the glowmask layer, missiles drawn
   at bones); the entity renderers (grenade, interceptor, missile, shell, shrapnel) and block entity renderers (alert
   display, charge, fission reactor, fluid tank, ice shell) from 26.x's render states to 1.21.1's direct rendering, best
   through stand-in base classes; 26.x's custom geometry event as one posted from `RenderLevelStageEvent` (artillery,
   laser, Spectacle, multiblock preview); `FxRenderTypes` as 1.21.1 render types; particles, fluid models and tints,
   item properties (the Geiger counter), the Field Manual's picture-in-picture views (structure, item preview).
2. **Screens' input**: 26.x's `MouseButtonEvent`/`KeyEvent` methods as 1.21.1's `mouseClicked(x, y, button)` and kin;
   cursors.
3. **Content**: armour materials (`GBArmor`), the machines' recipes (26.x's recipe displays and placement info), saved
   data (`SavedDataType` as `SavedData.Factory`), the sites' structure pieces, the JEI plugin (JEI 19's API).
4. **Resources**: 26.x's item model definitions (`assets/*/items/`) as 1.21.1's item models: block items parented to
   their block models, GeckoLib items on `builtin/entity`, the missile looks as custom-model-data overrides; any other
   26.x-only JSON.
5. Then run it (client and server), fix what breaks, port the GameTests, and deploy to a 1.21.1 instance.

## Bringing it up to main

Because the port of a file is repeatable, a newer `main` comes in as a three-way merge of ported text: for each file
`main` changed since the last sync, the old and new versions are both run through `port.py`; a file nobody edited by
hand here is replaced outright, and one that was edited gets main's change merged into it (`git merge-file`), so only
where a hand edit and main's change touch the same lines is there anything to resolve. Then build, run the GameTests,
fix what broke, and update the line above.
