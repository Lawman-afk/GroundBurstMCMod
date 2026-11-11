# Ground Burst for Minecraft 26.1.2 (branch `26.1.2`)

Main is written for Minecraft 26.3 (NeoForge 26.3). This branch is the same mod for Minecraft 26.1.2 (NeoForge
26.1.2.126, GeckoLib 5.5.2, JEI 29.53, Java 25), made from main's sources by a tool, not kept by hand.

- **Why a backport from main** (main's DESIGN.md §30): 26.1.2 is two minor versions before 26.3 and shares most of its
  code (the same rendering by render states and submitted geometry, item model definitions, data components, the same
  GameTest framework); a forward port from the 1.21.1 branch would cross every change from 1.21.2 to 26.1.
- **Last brought up to:** `main` at `ae7e2623` (2026-11-11: the war room removed at the user's wish, main's DESIGN.md §17.17: the Seismograph, the War Room Display and the Dead Hand taken out whole, with the Command Rocket, the launchers' stand-by and main's War Room range. Synced with no conflicts; the range's `runWarRoom` run, the jar's exclusion of `devworld/` and `port.py`'s fixes for the range's signs taken out (as before it); "Ground Burst War Room" moved out of the instance's saves. 444 GameTests passed); before it, at `b814affa` (2026-11-11: the war room after the user's look, main's DESIGN.md §17.19: the Dead Hand's command rocket flies over the network's launchers, each firing as its signal reaches it, the launchers standing by in the alert (every launcher kind can stand by: `LaunchSite.launchHeld`, `release`, `scrub`); the War Room Display's screen (its board dragged and zoomed, `NetworkMap` shared with the station's). Synced with no conflicts; main's War Room range runs here with its far Missile Silo: its played-through attack passes its 26 checks (the far silo fired with the rocket 411 blocks off), and "Ground Burst War Room" is rebuilt for the 26.1.2 instance. 470 GameTests passed); before it, at `83927f20` (2026-11-09: the war room, main's DESIGN.md §17.17 and §17.18: the Seismograph, the War Room Display and the Dead Hand, ported as they are; main's War Room range comes over and runs here (`gradlew runWarRoom`, the jar task leaving `devworld/` out and the run added to this branch's build.gradle by hand as main's; a `FILE_FIXES` entry for its signs, as the Showroom's): its played-through attack (`-PwarRoomDemo=true`) passes its 19 checks, and "Ground Burst War Room" is built for the 26.1.2 instance. 460 GameTests passed); before it, at `228702cb` (2026-11-08: version 0.11.0, built for all three versions as `groundburst-mc26.1.2-0.11.0.jar`: the remastered sites and the day's fixes in one release; the sites' block-entity marks dropped and each site keeping 7 chunks clear of the rarer kinds, main's DESIGN.md §22.8; the README's and the description's site pictures); before it, at `6dbc3f68` (2026-11-08: the Cold War sites remastered with the cosmetic sets,
  main's DESIGN.md §22.8: the site templates converted as ever, the site tools); before it, at `1a9286d6` (2026-11-08:
  0.10.1, built for all three versions as
  `groundburst-mc26.1.2-0.10.1.jar` but not uploaded: it goes out with the remaster as one release; main's changelog, the CurseForge description's long parts always open; this
  version's fixes in it are the pipes' one input slot an ingredient and the shader packs, below); before it, at
  `369fcb74` (2026-11-08: pipes and hoppers keep one input slot an ingredient, main's
  DESIGN.md §6.5; the advanced-circuit line, `gametest/CircuitLine`, and its GameTests; the mod page's pictures and
  description, main's README, the photographers' real-world modes (`build.gradle` given `-PfxWorld`, `-PfxSeed`,
  `-PhealWorld`, `-PhealSeed`, `-PshotSize` and the bench's picture options; the chat cleared as `mc.gui.getChat()`,
  a port substitution). The glowmask pipeline's accessors go (a `FILE_FIXES` entry: `IrisFx` is left out here); before it, at `8eb031af` (2026-11-07: 0.10.0, released for all three versions as
  `groundburst-mc26.1.2-0.10.0.jar`: main's changelog, README and description; before it, at `6fe765f5`: a ladder
  built into the Bunker Hatch, down its front
  under the coaming and climbable as a ladder (NeoForge's `isLadder`, this version's as main's), the extinguisher's
  hose closed, the open Ammo Crates painted as the shut ones, the doors board's shafts laddered; before it, at
  `060edbdb`: round tops as polygons, main's DESIGN.md §4.4, main's own
  model loader (`client/render/PolygonModels`; 26.1.2's quad baker shades a quad or not: a `FILE_FIXES` entry) and
  `PolyGeoModel` (GeckoLib 5.5.2's the same) ported; the user's second look's fixes; before it, at `2e61cc60` (after
  the user's look round the showroom, main's DESIGN.md
  §22.7: flat round tops in every model (a round part's overlapping caps on one level, one even colour, no corner
  shade), the cosmetic models' fixes, the hatch's lid block, redstone holding the props open, the Rusty Czech Hedgehog,
  the extinguisher on a wall, main's showroom world (`gradlew runShowroom`; its signs written through this version's
  sign text, a `FILE_FIXES` entry); before it, at `d403431a`, `c7851148` and `27ad9525`: the yard, the bunker fittings
  and the command room; at `c2acaac1` (2026-11-06, for 0.9.0: sea mines, depth charges and the Depth Charge Rack, main's
  DESIGN.md §14.6; healing the game's own explosions and other mods', §21.9; 0.8.1's healed cables, pipes and other
  mods' blocks; before it, at `07db772c`, 2026-11-04: fire never healed back; main's performance check, its DESIGN.md
  §11.8).
- **Shader packs** (2026-11-08; the user asked whether this version was broken under one): it was, worse than main had
  been. With Iris 1.11.4 and a pack the blast effects drew nothing at all and the machines' glowing parts were gone:
  Iris had no program for either pipeline, and when told about GeckoLib's it picked a sky program for it, for want of
  an alpha cutout. Now as main: its `IrisFx` ported, the effects drawn after the level (this branch's `FxRenderTypes`)
  with the client option "Effects with shader packs", the glowmasks on a copy of GeckoLib's pipeline with a cutout
  when Iris is installed, and the `-PwithIris` and `-PwithDH` dev runs (`build.gradle`; this version's switch for the
  dev runs' draw checks is `neoforge.disableGlValidation`).

## How it is made

`tools\backport\port.py` (run with main's Python: `..\Explosive Mod\tools\.venv\Scripts\python.exe`) reads every file of
main at the commit in `tools\backport\base.txt` straight from git and writes its port here:

- **Java** through `RULES` (names and calls 26.3 changed: `EntityTypes` to `EntityType`, `PushReaction.IMMOVEABLE` and
  `POPPED`, `Tags.BlockItems`, the screen and camera accessors, the pose stack's `rotate`, light coordinates, configured
  features' registry, `ModConfig.Type.SYNCED` and `LOCAL` to `SERVER` and `COMMON`, the GameTests' mock player and test
  data), a few functions (blocks' codecs, the picture-in-picture renderers, calls whose arguments changed, a block's
  outline drawn as lines) and per-file fixes (`FILE_FIXES`: fuel values, the sky's packed colours, healing's fire and
  farmland mixins, an entity's load, the showroom's signs, the polygon loader's shading, main's `IrisFx` and the
  glowmasks' pipeline on 26.1.2's pipeline class, with Iris a copy of GeckoLib's with an alpha cutout).
- **Resources:** configured features to `worldgen/configured_feature` with their settings under `config` and full block
  states; loot tables' single conditions and modifiers to 26.1.2's lists; recipe unlocks name one recipe; a biome's mob
  spawns from 26.3's environment attribute to its spawners; tags' entries from outside the mod made optional; recipes
  that need 26.2's or 26.3's items left out; structure templates' palettes named as 26.1.2 reads them (`Name`,
  `Properties`; 26.3's lowercase names read as air) and saved at 26.1.2's data version; a model's upward shading
  override as no shading.
- **Left out** (`DROP`): the data generators (their output is ported instead), the world survey, and main's
  `client/fx/DrawnLast` (the effects drawn after the level under a shader pack, main's DESIGN.md §12.3: built on 26.3's
  renderer; this branch's `FxRenderTypes` does it itself, below).
- **Kept by hand** (`tools\backport\manual.txt`): `client/fx/FxRenderTypes` (26.1.2 has no order-independent
  transparency: the effects are drawn after the translucent terrain, as main's are; it keeps the game's sorting of the
  effects' blended quads, which main does itself since 2026-11-07, and its `client/fx/FxSort` holds nothing back; the
  glow, which adds its light, is not sorted, as on main; under a shader pack, unless the player has the pack draw them,
  and with Distant Horizons it keeps them back and draws them at NeoForge's after-level stage, as main's `DrawnLast`
  does, the view pushed again and the fog the level's), its cloud shader
  (`shaders/core/fx_cloud.fsh`), and the worldgen toggle (`world/ConfigToggleFilter`, `GBWorldgen`: 26.1.2's placement
  filters are classes with types). A sync merges main's changes into them (a three-way merge), conflicts marked.
- **This branch's own** (never main's): `dev.groundburst.port` (`BlockItemTagId`, `Mock`, `SkyColors`), this file, the
  tool, and the build files (`build.gradle`, `gradle.properties`).

Bring the branch up to a newer main with `port.py --sync <commit>`; compile and sort the errors with
`pwsh -File tools\backport\compile.ps1` (into `build\port-errors.txt`; `errs.py <words>` prints a file's errors with
their lines).

## What differs from main for players

- **Settings:** 26.1.2 has no synced or local configs: the world settings are `groundburst-server.toml` (Server
  Settings in the Mods screen, open from inside a single-player world) and the shared ones `groundburst-common.toml`.
- **Effects:** drawn after the water without order-independent transparency; with Fabulous graphics, smoke in front of
  water may show the water over it where the smoke does not write depth.
- **Recipes** that use 26.2's or 26.3's items (sulfur, cinnabar) are not in this version.

## Checks

- `gradlew runGameTestServer`: main's GameTests (444 at `369fcb74`, all passed).
- `gradlew runPlayBench [-Pjfr=<name>]`, `runBlastBench`, `runFxShots`, `runManualShots`, `runSiteShots`,
  `runHealShots`: main's benches and photographers (`runSiteShots -Psite=doors`: the bunker doors; `room`: the command room; `fittings`: the bunker fittings; `yard`: the yard, with a picture at midnight).
- `gradlew runFxShots -PwithIris [-PirisPack=complementary|unbound|bsl|bliss|photon|solas|makeup|off]
  "-PfxOnly=shader_,alarms_close$"`: the effects and the glowmasks under a shader pack (Iris 1.11.4 and Sodium 0.9.2,
  main's seven test packs; `-PpackFx=in` has the pack draw the effects); `runFxShots -PwithDH -PfxWorld=sea -PfxOnly=dh_`:
  with Distant Horizons 3.3.3.
- `gradlew runShowroom`: main's showroom world (`run/saves/ground_burst_showroom`; `-PshowroomWalk=true`: the timed
  walk round the bunker doors).
- The build deploys to the folder in `deploy_instance_dir_26_1_2` (in `%USERPROFILE%\.gradle\gradle.properties`), if set.
- Releases: `groundburst-mc26.1.2-<version>.jar` (its own `mod_version`), kept with main's in main's `build/release`.
