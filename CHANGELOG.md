# Changelog

## Unreleased

### The Cold War sites, fitted out
- All seven of the world's sites are fitted out with the bunker doors, the command room, the bunker fittings and the
  yard: blast doors and pressure doors, hatches with their ladders, consoles, command desks, filing cabinets, tape drives
  and the situation map table, bunk beds and steel lockers, wall pipes and valves, ventilation ducts and grilles, fire
  extinguishers, Jersey barriers, dragon's teeth, Czech hedgehogs, searchlights, ammo crates and drums, each where a
  real site would have it.
  - **Command Bunker:** a blast door at the entrance and another at the foot of the stair, pressure doors between the
    rooms, the situation room's map table and its row of desks, the records room's cabinets and tape drives.
  - **Missile Silo:** its blast lock's two blast doors (on a collapsed silo, dig the cave-in clear and the door opens),
    hatches over its ladders.
  - **Radar Outpost:** a hatch onto the radar deck, the radar room's consoles, the module train's operations room,
    quarters and generator.
  - **Test Site:** a blast door on the control bunker, the firing console in the shot tower's cab.
  - **Crashed Missile:** the recovery team's searchlight on its sandbag wall.
  - **Waste Storage:** the drift's ventilation duct and water line, the control room's consoles.
  - **Research Station:** the laboratory's consoles and tape drives, the crew's bunk beds and lockers.
- In every room one breaker is still on, its lamp lit; click it and the light goes out.
- Some of each site's loot is in ammo crates, filing cabinets and lockers now.
- Sites already generated in a world stay as they were; the fitted-out ones appear in newly explored land.

### Fixes
- The Research Station's laboratory and crew module have floors where they stand out past the deck: the laboratory's
  had a hole onto the void.
- A Radar Outpost's beds and a Research Station's radar ladder could go missing where a chunk's edge ran beside them.
- Pipes and hoppers fill each ingredient into one input slot of a machine. A pipe bringing more of one ingredient than
  a slot holds used to fill the next slots too, until the machine stood waiting with every slot full (a Circuit
  Fabricator fed copper wire, redstone and plastic from one chest filled all four with wire). Filling by hand is as it
  was.
- Minecraft 26.3 with a shader pack on: the glowing parts of machines and alarms (a Warning Beacon's light, a machine's
  lamps) no longer streak across the sky.
- Minecraft 26.1.2 with a shader pack on (Iris): the blast effects (fireballs, smoke, mushroom clouds, beams, water
  columns) drew nothing at all, and the glowing parts of machines and alarms were missing. Both now show as on 26.3:
  the effects are drawn over the pack's finished picture, or by the pack itself with the client setting "Effects with
  shader packs", which 26.1.2 now has too. With Distant Horizons the effects are drawn after its far terrain there as
  well.

## 0.10.0 — 2026-11-07

Four sets of blocks to build bunkers and bases from: bunker doors and hatches, a command room, bunker fittings, and
the yard's obstacles, searchlight, crates and drums. Round parts in every model are smooth now and keep their full
texture, and the blast effects cost less to draw. For Minecraft 26.3, 26.1.2 and 1.21.1.

### Bunker doors
- **Blast Door**: a heavy steel blast door for a doorway two blocks wide and three high. Click it, or give it a
  redstone signal: its red handwheel turns and draws the bolts, then the leaf swings out on its hinges; shutting, it
  swings home with a thud and the bolts are thrown. It reaches to your right as you place it (sneak to place it
  reaching left), needs two blocks clear in front of its hinges to swing open, and stands up to blasts as reinforced
  concrete does.
- **Bunker Hatch**: a round steel hatch with a handwheel, in a square steel frame. It opens as a trapdoor does, by hand or
  by redstone, but its lid lifts up out of the floor and stands in the block above (click the standing lid to shut it;
  a hatch in a ceiling swings down). A ladder is built in down its front, on the side nearest you as you place it: hang
  ladders on the same wall under it and climb straight up through.
- **Pressure Door**: a steel door with a porthole you can see through and a red lever on both sides. It opens as a door
  does, by hand or by redstone.
- Each with its own sounds; in the Construction tab.

### The command room
- **Control Console**: a steel console with switches, buttons, dials and blinking lamps; place several side by side for
  one long console.
- **Command Desk**: a steel desk with a CRT monitor whose green screen scrolls, and a red telephone.
- **Filing Cabinet**: holds 27 stacks like a barrel; its top drawer slides out while it is open, or while a redstone
  signal reaches it.
- **Tape Computer**: a tape drive two blocks high whose reels read, stop and rewind while its lamps blink. Click it to
  stop or start it, or run it by redstone; a row of them makes a wall of computers that never runs in step.
- **Situation Map Table**: a table two blocks by two with a map of a coast at war on it, its unit counters and pins.

### Bunker fittings
- **Wall Pipes**: three pipes along a wall that run on into the wall pipes beside them, capped where the run ends.
- **Pipe Valve**: a gate valve in a run of wall pipes. Turn its red handwheel to open it: it gives a redstone signal
  like a lever. A signal from elsewhere shows it open.
- **Vent Grille** and **Ventilation Duct**: the duct joins up on all six sides, and to the back of a grille.
- **Bunker Lamp**: a caged lamp for walls, ceilings and floors, lit by a redstone signal.
- **Fuse Box**: throw its breaker for a redstone signal; its lamp shows green or red. A signal from elsewhere shows it
  on.
- **Fire Extinguisher**: stands on the floor, or hangs on a wall when placed against one; click it to spray, putting
  out fire in front of it.
- **Steel Locker**: two blocks high, holds 27 stacks; its door swings open while it is in use, or while a redstone signal
  reaches it.
- **Bunk Bed**: two beds in one, two blocks high and two long, with guard rails and a ladder. Two players can sleep in
  it, and each bunk sets a spawn point.

### The yard
- **Jersey Barrier** and **Dragon's Teeth**: concrete obstacles as hard to break and to blast as Reinforced Concrete.
  Barriers set in a row make a wall; teeth go in rows.
- **Czech Hedgehog** and **Rusty Czech Hedgehog**: three steel beams crossed, standing on three of their ends,
  painted or left to rust.
- **Searchlight**: a big searchlight on a stand, two blocks high. Click it to switch it on, or give it a redstone
  signal: it sweeps the sky with its beam, brightest at night.
- **Ammo Crates**: olive ammo boxes on a pallet that hold 27 stacks; a box's lid lifts while they are open, or while a
  redstone signal reaches them.
- **Oil Drum**, **Fuel Drum**, **Rusty Drum**: up to four drums stand in one block, like candles.

### Models
- **Round parts look round and keep their texture**: every round part in the mod (warheads, missiles, tanks, pipes,
  drums, lamps) has smooth round ends painted like the rest of its model, where they had been stepped or one plain
  colour, and nothing flickers where round parts meet.
- The **Particle Uplink Cannon**, the **SCUD Storm** and the **Nuclear Missile Silo** are closed underneath.
- Gas clouds, tank glass and lamp lenses are evenly lit again: Minecraft 26.3 had been shading them like solid
  blocks.

### Performance
- **Blast effects cost less to draw.** Under a mushroom cloud the mod's share of each frame's work is down by about a
  third: every effect vertex is written in one go, the fallout clouds no longer work out their layout every frame, and
  the effects' smoke and fire are sorted once a frame by the mod instead of by the game (the glow needs no sorting at
  all). The picture is the same.
- **The ground after a nuke is painted a few milliseconds a tick**, so the seconds after a big blast hitch less.

### Fixes
- **Healing with "Healing blocks drop items too" on** no longer gives you a chest's contents twice: what a creeper or
  TNT spills out of a chest stays spilled, and the chest comes back empty. This mod's own blasts spill nothing, so what
  they break still comes back full.
- **A double chest half destroyed:** the half left standing keeps what you put in or took out of it before the damage
  healed, instead of going back to what it held when the blast hit.
- **Machines always make the right product** when one recipe's ingredients sit inside another's: they take the recipe
  that uses the most of what they hold (on Minecraft 1.21.1 a Chemical Plant could make RDX crystals from photoresist's
  ingredients).
- **Minecraft 1.21.1:** the mod's liquid buckets (napalm, the acids and the rest) pour into its tanks and machines, and a
  cryo blast freezes the plants on frosted and dead grass instead of knocking them off.

## 0.9.0 — 2026-11-06

Sea mines and depth charges with a stern rack to roll them off, healing for creepers, TNT and other mods' explosions,
and, on 26.3 and 1.21.1, blast effects that look the same under every shader pack. For Minecraft 26.3, 26.1.2 and
1.21.1; the 26.1.2 build catches up from 0.8.0, with 0.8.1's healed cables, pipes and machines.

### Sea mines and depth charges
- **Moored Sea Mine**: the classic horned mine on its chain. Use it on water and it sinks to the bottom with its
  sinker, then rises on its chain to its depth: awash, just under the surface where boats strike it, or 1 to 8 blocks
  down (sneak and use it to set the depth). It is live 5 seconds after it settles.
- **Drifting Sea Mine**: floats awash and drifts with the current and the wind, and floods and sinks after an hour
  adrift.
- **What sets a mine off**: a boat or minecart touching it (on 1.21.1, a Create ship or contraption too), a player or
  any creature but fish, squid, dolphins and axolotls, an arrow or shell striking it, a punch, or a blast close by. A
  blast a little further off parts a moored mine's chain, and the mine bobs up to drift. Use a mine to make it safe, and
  punch it while safe to take it up. A dispenser facing into water lays its mines there.
- **Depth Charge**: throw it, or roll it out of a dispenser. In water it sinks and fires at its depth (3 to 24 blocks:
  sneak and use it to set the depth), or on the bottom if that comes first. Out of water it never fires.
- **Depth Charge Rack**: a ship's stern rack that rolls depth charges off into the sea. Build it one block wide and four
  long: the Depth Charge Rack at the end over the water, two Depth Charge Rails behind it and an Item Port at the top
  end. Load up to six charges by hand, or by hopper, pipe or Create's funnels into the Item Port, and they roll down the
  rails to the trap. A redstone pulse rolls one off; a held signal rolls one off every 1.5 seconds (0.8 to 3, set in its
  screen) until it is empty. Its screen releases one or all of them and can set every charge to one depth. A comparator
  reads how many it holds. On 1.21.1 it works aboard Create ships and contraptions.
- **Blasts under water** no longer blow holes in the sea: the water carries the shock, deadly to anything in it, and
  throws up a tall column of spray that falls back as rain and mist; what the blast breaks under water fills with
  water. Their own sounds: a deep thud, the column's roar and its water falling back.
- Every blast now breaks the boats and minecarts it hits.

### Healing
- **The game's own explosions heal too**: creepers, TNT and TNT minecarts, end crystals, ghasts' fireballs, the Wither,
  and beds and respawn anchors that blow up, each with its own switch under Healing, Other explosions (all on). They
  come back as the mod's blasts do: chests with their contents, nothing dropped meanwhile, item frames, paintings and
  armour stands hung and stood again. TNT an explosion lights goes off, so it does not come back.
- **Other mods' explosions** heal too when they go through the game's own explosion code, as most do (one switch;
  leave one mod out by its id under Weapons that never heal, such as "somemod:*"). An explosion that digs its own
  crater, as this mod's weapons do, is not seen.
- With healing off nothing changes: explosions drop and spill as they always have.
- **Damage that was burning heals even when nobody stays to watch.** Healing waits for the fires by the damage to go
  out, but a fire far from every player neither spreads nor goes out (on Minecraft 26.x, beyond 128 blocks), so a
  crater left burning when its players went away never healed. Now such a fire goes out once the wait is over.

### Shader packs and Distant Horizons
- With a shader pack on (Iris), blast effects are now drawn over the pack's finished picture with the mod's own
  shading, so they look as they do without shaders, the same in every pack and steady from frame to frame. Under Bliss
  the mushroom cloud no longer shows as a hole in the sky or vanishes in the distance, smoke is no longer black and the
  fireball no longer changes colour as the flash fades. On Minecraft 26.3 and 1.21.1; checked under Bliss,
  Complementary Reimagined and Unbound, BSL, Photon, Solas and MakeUp Ultra Fast (on 1.21.1 under Bliss and
  Complementary Reimagined).
- With Distant Horizons, its far terrain no longer covers the foot of a mushroom cloud.
- The way 0.8.1 drew them, lit and fogged by the pack like the world round them, is a client option: Effects with
  shader packs, Lit by the pack.

## 0.8.1 — 2026-11-05

The blast effects with shader packs, and healed machines, cables and pipes that work again. For Minecraft 26.3 and
1.21.1; the 26.1.2 build stays at 0.8.0 for now.

### Shader packs (Iris)
- **The blast effects work with shader packs.** With Iris and a pack on (tried with Complementary Reimagined and BSL),
  fireballs, smoke, dust and mushroom clouds drew as huge black and coloured shards across the screen (on 1.21.1 the
  fireball glowed white and the smoke was missing). Now each effect uses the pack's own program for its kind: glows (the
  white-hot core, shock waves, sparks, beams, lasers) glow, and fire and smoke are lit by the pack. The look differs a
  little from pack to pack (smoke darker in Complementary, warmer in BSL), and a mushroom cloud far beyond the render
  distance fades into the pack's fog.

### Healing
- **Cables, pipes and machines work again once healed.** A healed cable run powers its machines again and a healed pipe
  run carries items and fluids again, pulling where it pulled (before, they came back looking right but carried
  nothing until something was rebuilt). Machines' side and input/output settings, pipes' pull and switched-off sides
  and every container's contents come back as they were, as before.
- **Other mods' blocks** come back with all their data and settings, now placed the way a structure places its blocks,
  and the other mods' blocks beside them are told, so their pipes, cables and machines pick them up again.
- **1.21.1**: a formed structure (a reactor, an accelerator) is no longer taken apart while it heals and put together
  again at the end, and a healed pipe keeps its pull sides even when its chest comes back after it.

## 0.8.0 — 2026-11-04

Damage that heals over time (off until you turn it on), a build for Minecraft 26.1.2, Create and Create Aeronautics on
1.21.1, and the 1.21.1 build's world sites fixed.

### Healing (every version; off by default)
A player's suggestion: damage that heals over time, back to exactly what was there.
- **Turn it on** in the server config (Mods, Ground Burst, Config: Healing, "Heal damage over time"; or `healing.enabled`
  in `groundburst-synced.toml` on 26.3, `groundburst-server.toml` on 26.1.2 and 1.21.1). Only damage done while it is
  on heals, so turn it on before the war starts. The README and the CurseForge page say how to change any setting.
- **Exactly as it was**: every block a weapon changes is remembered and put back: a crater fills in from the bottom up,
  scorched grass greens, the wasteland turns back into the biome it was, a crater lake drains, and a chest comes back
  with its contents, a sign with its words, a door the way it faced. Paintings, item frames and armour stands a blast
  knocks down are hung again. What the damage sets going heals too: blocks burnt by the fires a blast started, sand that
  fell into the crater, leaves that decayed when their tree went, grass that died under the rubble, fields that dried
  when their water went, and water that ran out of a crater and what it washed away.
- **Every weapon**: blasts of every size, the Particle Uplink Cannon's beam, the cryo freeze and frost, singularities,
  antimatter, storms, breaching charges, thermite, napalm and incendiaries, chemical weapons, fallout ash, plants killed
  by radiation, a meltdown's corium. Choose which kinds heal (conventional, nuclear, special, chemical), and leave out
  single weapons, blocks or dimensions.
- **Nothing duplicated**: a block that will heal drops nothing and spills nothing while it is gone.
- **When and how fast**: after a wait once the fighting has stopped there (fresh damage starts the wait again), with a
  random extra so a crater heals in patches; or every morning; or only when an operator says. A speed, and a longest
  time, so a nuclear crater heals about as soon as a grenade's hole. From the bottom up, at random, or all at once.
- **Players' blocks**: something a player built in the damage is kept, with a clear space round it and nothing healed
  over it, so a base in a crater is not filled in (or, if you choose, dropped as an item, or replaced).
- **Commands** for operators: `/groundburst heal now [radius]`, `/groundburst heal forget [radius]` and
  `/groundburst heal status`.
- **Fire is never brought back** (found in testing: trees set alight with molotovs healed with their fire and burnt
  again). A place that was burning heals as the air it burnt in, and damage waits to heal until the fires near it have
  gone out (a fire on netherrack, which burns for good, does not hold it up). `/groundburst heal now` puts those fires
  out first.
- Not healed: ships and contraptions, vanilla's own explosions (creepers and TNT), creatures, and damage done while
  healing was off.

### Minecraft 26.1.2
Ground Burst now comes for Minecraft 26.1.2 too (NeoForge 26.1.2.126 or newer, GeckoLib 5.5.2 or newer, JEI optional):
everything 26.3's has, but for the recipes that use 26.2's sulfur and cinnabar. Its world settings are the Server
Settings (`groundburst-server.toml`), changed from inside a single-player world.

### Performance
Checked over the whole mod before this release, on every version, with a scripted game at 32 chunks (molotovs and a
thermobaric warhead in a forest, the fires, the healing) and the heaviest case, a Tsar Bomba's crater healing: the mod
takes about 1% of the game's time in play, healing included, and a nuclear crater heals within the server's budget.
- Healing's checks, which run for every fire, flowing water, spreading grass and farmland in the world, now take no
  time at all where nothing is damaged.
- The sparkles and sounds of healing are capped, so a nuclear crater healing hundreds of chunks at once does not flood
  the game with them.
- Machines set to ignore redstone (the default) no longer check for a signal every tick.

### Minecraft 1.21.1: Create and Create Aeronautics
Both are optional; with either installed, Ground Burst's machines work on the structures they move.
- **Create**: every machine rides Create's moving contraptions (trains, mechanical bearings, pistons, gantries, pulleys
  and minecart contraptions): launchers, missile assemblers, Launch Computers, Control Stations, Radar Arrays,
  generators, capacitor banks and the processing machines. Glue or attach any part of a formed structure and the whole
  of it moves (Create may now move the mod's blocks, which pistons still may not). Carried, a machine keeps working: an
  assembler goes on building its missile, a generator burns its fuel.
- **Menus on contraptions**: right-click a carried machine, or any block of a carried structure, to open its menu, or
  its screen for a Launch Computer, Control Station or Radar Array.
- **Power and pipes aboard**: a contraption's generators and capacitor banks power every machine on it, no cables
  needed, each machine getting a fair share. Item and fluid pipes on a contraption keep working, moving things between
  its machines, the launchers' Item Ports and Create's chests, vaults and tanks on it, as their Wrench settings say.
- **Launchers**: aim, arm and fire one as always from a Launch Computer, a Launch Control Station or a Laser Designator:
  it counts down and launches from wherever the contraption has it, its missiles heading the way it points. Set down
  again, a structure forms where it lands and a launcher stays on its network. Missile launchers, superweapons and both
  interceptor launchers all work this way.
- **Blasts hit ships and contraptions**: a warhead's, a shell's, a bomb's or a charge's blast breaks the blocks of
  Create Aeronautics ships and of Create's moving contraptions in its reach, just as it breaks the world's (dense blocks
  still shield what is behind them), and shoves ships away, turning them by the side that faced it. What a blast sees of
  a ship is scorched (a nuclear fireball chars its wood) and may catch fire. Antimatter and singularities erase whatever
  of them they reach. Missiles and shells hit ships and contraptions in their way instead of passing through. A chest or
  a machine blown off a contraption goes with what it held; a train keeps its wheels.
- **Beams, freezes and singularities**: the Particle Uplink Cannon's beam burns down through ships and contraptions
  under it, from the top, and scorches and sets fire round it; the Cryo ICBM's freeze ices them over and snows on them;
  a singularity pulls ships in and swallows them as they reach it.
- **Create Aeronautics** (Sable ships, planes, cars and boats): a launcher aboard works as it does on the ground. Its
  missiles leave from where the ship is, the way the ship points them, and computers, stations, designators and radars
  see it where the ship has taken it. A Launch Computer, Control Station or Radar Array aboard a ship works there too.
- **Fixed**: a machine taken by a contraption (a missile assembler at work, a Launch Silo) dropped its items as it went
  and still held them when set down, doubling them; now nothing drops and nothing is doubled. A pipe set to pull with
  the Wrench lost that setting when a contraption set it down; it keeps it now, turned with the contraption (cables keep
  theirs too).
- **Fixed**: aiming a Laser Designator at a ship lagged the game badly and gave launchers a target far from the ship;
  the beam now lands on the ship where you see it, and missiles home on it there. A mortar or howitzer aimed at a ship
  said BLOCKED; it now aims
  at the ship, at its height. After a blast had broken a ship down to nothing, the next big blast (a nuke) froze the
  game, and quitting then hung on "Saving world"; it no longer does.
- Not yet: reactors pause while carried; pipes and cables on a contraption reach only what is on it, not the blocks round
  it.

### Fixed
- Minecraft 1.21.1: the world's sites (the abandoned silos, command bunkers, radar outposts, crashed missiles, the test
  site, the waste storage and the research station) generated empty in 0.7.0; they are built again.

## 0.7.0 — 2026-11-03

A towed 155 mm howitzer that reaches 600 blocks, and the first build of Ground Burst for Minecraft 1.21.1.

### Artillery
- **155 mm Howitzer**: a towed field gun on split trails that reaches **64 to 600 blocks**, 30 degrees either side of
  the way it is placed facing. It fires **HE** (a blast a little under an HE warhead's, with 32 fragments) and
  **incendiary** shells (napalm 9 blocks round), always on the high arc, so they come down steeply behind hills. It is
  steadier than the mortar (a narrower circle that settles in three seconds) and reloads in five.
- It is crewed and aimed as the mortar is. Ground too far off for the game to draw is filled in, in the strategic view,
  from the gun's own map of it, so you can see what you are aiming at 600 blocks away.
- Its firing sound is made from recordings of a real 155 mm gun.

### Minecraft 1.21.1
- Ground Burst now comes for **Minecraft 1.21.1** too (NeoForge 21.1.265 or newer, GeckoLib 4.9.3 or newer), as a
  file of its own with everything in this version. A few things are simpler on it: Rad-Away, Potassium Iodide Tablets
  and the Anti-Rad Injector are used as food is (with no nourishment), the pointer stays an arrow over the mod's
  screens, and flash particles are not tinted.

### Changes
- The mortar's crewman now **stands** at it: the gun view starts at normal eye height, not a block off the ground.
- The aiming circle is a **dashed line in one colour**; half of it showed darker.
- The aim no longer jumps to far-off ranges as the gun is laid: the circle **slides over the ground** toward your aim
  at the gun's own pace, from wherever it was, even when the gun changes its propellant charge on the way.
- A gun manned beside a wall or under a roof starts aimed within its range instead of jumping out to it.
- Shells now **scatter over the aiming circle at every range**: a shot landing on ground the server had not loaded (most
  long shots) went out unscattered and came down dead on the aim.
- Moving the aim over far ground in the strategic view **no longer lags the game**: the gun maps the far ground only
  once the aim comes to rest, the newest piece first, and the map is drawn in less detail further off.
- The Field Manual's chapter introductions **run on to the next page** when they are long (the Superweapons chapter's
  ran off its page).
- The missile assemblers' hook cables now **pay out as the hooks lower**, on all four assemblers.

## 0.6.0 — 2026-11-02

The USA's superweapon from *Command & Conquer: Generals*, a deep freeze as the fourth special weapon, and the first of
the artillery: an 81 mm mortar aimed from above as *World of Tanks* aims its guns.

### Particle Uplink Cannon
- **Particle Uplink Cannon**, the USA's superweapon from *Command & Conquer: Generals*: a drum of white concrete with
  eight **Particle Emitters** round a pit, a control tower with a glazed cab and two antennas, and an operations building
  with two satellite dishes, on a 15x15 pad (a Structure Spawner places it in creative).
- It takes **no ammunition**: power it and it charges for **4 minutes**. Charged, its focusing column rises out of the
  pit, its core aglow and arcs leaping to it from the emitters.
- Fire it from a Launch Computer or a **Laser Designator**: a beam goes up out of the pit to its satellite and two
  seconds later comes down from orbit on the target, burning for **48 seconds**.
- Whoever fired it **steers the beam** with their Laser Designator: it follows the spot at three blocks a second. A new
  aim from a Launch Computer turns it too.
- The beam **eats a trench** into whatever it stands on, deeper the longer it lingers (up to 8 blocks), melting its sides
  to slag, glassed sand and scorched earth, glowing magma in its bottom; fires catch round it, and anything under it is
  **vaporised**, armour or not.
- It draws 1,000 FE a tick while it fires: whatever powers it well enough to charge keeps it burning, and what the
  cannon holds lasts twenty seconds. Cut its power, knock it out with an EMP or break it, and the beam dies.

### Cryo ICBM
- **Cryo ICBM**, the fourth special weapon: a **Cryo Core** (four liquid nitrogen canisters, two superconducting coils,
  stabilised exotic matter and a quantum circuit) and an Intercontinental Booster at the Special Weapons Assembler,
  fired from a Launch Complex or a Missile Silo.
- Where it lands a blue-white flash and a dome of white vapour boil up, and a **wall of frozen air rolls out 128 blocks**
  over the land in eight seconds: water freezes (ponds near the middle freeze solid), lava crusts to obsidian, fires go
  out, stone and grass rime over, plants freeze solid, and snow is laid over the land and the treetops. Its cold air
  pools in low ground, so hilltops are spared. Everything it catches is frozen solid for up to 20 seconds and hurt by
  the cold.
- Within **40 blocks** nothing lives: everything is frozen through and shatters, glass cracks apart, caves freeze, and
  **spires of ice** burst out of the ground, a great one at the middle and a ring leaning outward. They stay.
- A **three-minute cold snap** holds the region: snow falls, the fog closes in (a whiteout near the middle), a mist
  drifts over the ground, ice glitters in the air, breath smokes and a thin wind whistles. Anything in it freezes as in
  powder snow and takes cold damage once frozen through; one piece of **leather armour** keeps the cold out.
- Its snow (**Cryo Snow**) and milky **Cryo Ice** thaw over the next half hour where snow would not lie: the ice back to
  water where it was still, away where it ran. In cold places they stay.

### Artillery: the 81 mm Mortar
- **81 mm Mortar**: a crewed gun that lobs shells **16 to 200 blocks**, high over walls and hills. Right-click it with
  shells to load its rack (hoppers and pipes load it too), or with anything else to **man it**; Shift gets up.
- Aimed as *World of Tanks* aims its artillery: it follows where you look, or **scroll out** (or press **V**) for a
  **strategic view**, a camera high over the ground where the mouse moves your aim and the wheel the height. **1 to 6**
  pick the shell, **left click** fires.
- It is laid on your aim at its own pace and picks its propellant charge itself. A shell falls where the tube points,
  somewhere in the **aiming circle** drawn on the ground: the circle grows with range, blooms as the gun fires and
  turns, and shrinks back over two seconds (white while it shrinks, green fully aimed, red out of range or blocked). A
  readout shows the rack, the reload, the range, the charge, the elevation and the flight time.
- **Six shells**, each made from its grenade, a steel plate and gunpowder: **HE** (a blast and fragments), **smoke** (a
  screen of three clouds), **incendiary** (napalm 6 blocks round), **chemical** (two chlorine clouds), **cryo** (a freeze
  7 blocks round) and **EMP** (16 blocks round). Shells whistle in their last two seconds.

### Changes
- Scorched, glassed and frozen ground now counts as ground for rain, lightning and mob spawning (it was passed through),
  and so do the mod's machines, structures and other solid blocks: rain no longer falls through them.
- The superweapon settings in the server config (announcements, maximum range, charge time multiplier) now take effect.
- A Launch Computer's map can aim a superweapon while it charges.
- A **Laser Designator** now arms and fires the SCUD Storm and the Nuclear Missile too (their missiles come down where
  you designated).
- The **SCUD Storm** spreads its salvo wider (each SCUD comes down within 24 blocks of the target, was 16), and each
  SCUD reaches further: a blast a quarter bigger and its anthrax residue spreading 30 blocks round (was 21) under a
  bigger cloud.

## 0.5.0 — 2026-11-01

A volley launcher sunk into the ground, a laser to call missiles onto a target, and chips made the hard way:
every advanced, military and quantum circuit now waits on a real silicon chain.

### Vertical Launch Battery
- **Vertical Launch Battery**, a very late-game launcher sunk into the ground: sixteen cells in two modules (4 wide, 6
  deep, with a walkway and its fire-control console in front and launch control cabinets behind), each cell a **Strike
  Cell** with its hatch over five **Missile Canisters**: six blocks deep, as long as a Medium missile. Load a Light or
  Medium missile into each cell (conventional warheads; no cruise missiles), by hand or through its Item Port.
- Aim, arm and fire it from a Launch Computer, the Launch Control Station or a Laser Designator. After a 10 s countdown
  it **ripples** its whole load out, a cell every quarter of a second: each hatch swings open, the missile's motor
  lights at the foot of its cell, its blast roars up the module's uptake, and the missile climbs straight out.
- Each missile goes to its own point of the battery's **spread** round the target: all on the point, a line, a ring or
  a grid, up to 48 blocks apart, set on its screen.
- 20,000 FE a missile; it holds enough for two full volleys.

### Laser Designator
- **Laser Designator**: hold use and it goes to your eye: the view zooms in, and its sight shows a reticle, the
  **range** and the **coordinates** of the spot (and the creature, if it is on one). A thin red beam and a pulsing spot
  that everyone sees all the way to the spot: faint by day, bright at night. It reaches as far as you can see (your
  view distance, up to 1,024 blocks).
- **Laser Guidance Unit**, a new guidance part: missiles built with it are the ones a designator commands.
- Use the designator on a **Launch Control Station** to join its network: the station's map shows its spot while you
  lase, and the new **Laser** button aims the picked launchers at it.
- It commands one of the network's laser-guided launchers at a time: the first, or **scroll** while zoomed to pick the
  next (the hotbar stays put while you do); it remembers the pick.
- **Left-click** while lasing: if the picked launcher is not armed, it is aimed at the spot and armed; if it is armed,
  it launches, and its missile follows your laser all the way in, wherever you point it: onto a moving target, or
  another one.
- **Sneak and left-click** to disarm the picked launcher (it stays aimed where it was).
- Zoomed in, every armed launcher of your network shows where it is aimed: a glowing sphere centred on the point you
  clicked.
- It runs on a 40,000 FE charge (a hundred seconds of lasing; a Battery Charger fills it). Smoke or gas in the way
  breaks the beam, and an EMP knocks it out for a while.

### Silicon and chips: a harder circuit chain
- **Fluorite Ore**: purple and green cubes in the Overworld (Y −24 to 72). Crush it and dissolve the dust in sulfuric
  acid for **hydrofluoric acid**.
- **Silicon** from quartz and coal in the Arc Furnace, purified in the Chemical Plant: with **hydrogen chloride** (from
  hydrogen and chlorine) into **trichlorosilane**, then with hydrogen into **polysilicon**.
- **Hydrogen** from the **Electrolytic Separator**, a new **Proton Exchange Membrane** in its new membrane slot (with
  the slot empty it makes deuterium as before); **argon** drawn from the air by the new **Argon Molecular Sieve** in the
  Cryogenic Condenser.
- **Crystal Puller**, a new two-block machine: polysilicon melted in a quartz crucible under argon grows a **silicon
  boule** in a minute; watch the crystal rise in its sight glass while the melt glows in its viewport.
- A **Diamond Wire Saw** in the Industrial Press's die slot saws a boule into eight **silicon wafers**.
- **Sapphire** for military-grade chips: **Alumina** from clay and sulfuric acid in the Chemical Plant, grown into a
  **Sapphire Boule** in the Crystal Puller in a **Tungsten Crucible**, sawn into six **Sapphire Wafers**.
- **Clean Room**, a new multiblock (3 wide, 2 deep, 2 high, with the new **Clean Room Panel** and **Fan Filter Unit**):
  prints **Microchips** from a wafer, red phosphorus and a plastic sheet with photoresist and hydrofluoric acid;
  **Rad-Hard Microchips** from two sapphire wafers, red phosphorus and a **Ceramic Package** with trichlorosilane as
  well (silicon-on-sapphire); and **Quantum Chips** from a sapphire wafer, stabilised exotic matter and a ceramic
  package with argon. Its windows look into a bay lit yellow, where the tools work while it runs.
- The **Cryogenic Condenser** takes a basic circuit now instead of an advanced one.
- Hydrogen chloride is a toxic gas; trichlorosilane and hydrofluoric acid burn like acids.
- **Advanced circuits** are now built round two microchips, **military circuits** round two rad-hard microchips (with
  two gold wire and their diamond) and **quantum circuits** round two quantum chips (in place of their stabilised
  exotic matter): every advanced, military and quantum machine, part and weapon waits on the chip chain. Basic
  circuits are as they were.

### Assemblers
- Every assembler (the Assembly Bench, the four missile assemblers, the ICBM Assembly Hall and the Special Weapons
  Assembler) has an **Auto Assemble** button: while it is on, the next job starts by itself once the parts are in and
  the finished one has been taken out.
- The structures' Item Ports show on the side of the deck as well as on top, so pipes can feed one face and empty the
  other.
- Pipes and cables can now stand on top of the missile assemblers' Item and Energy Ports, and on the ICBM Assembly
  Hall's and the Special Weapons Assembler's Item Port, without breaking the structure.

### Launchers
- Every launcher has an **Item Port** now: the Launch Silo, the Launch Complex and the Missile Silo beside their Energy
  Port, the Cruise Missile Launcher behind its Energy Port. Pipes and hoppers load missiles through it straight into the
  launch slot, so a launcher reloads itself after each launch. It takes only missiles that launcher can fire, and
  nothing comes out of it. Launchers you built before still work as they are; put an Item Port in that spot to
  automate them.

### Changes
- A lightning rod in a Storm ICBM's storm still takes its strikes safely (no blast, crater or fire), but each
  strike's EMP pulse now goes out from the rod.

## 0.4.0 — 2026-10-31

A network for your launchers across the whole world, a bigger missile defence, a storm that knocks defences out, and an
alarm system for your base.

### Launch Control Station
- **Launch Control Station**: a console desk and instrument bank with a satellite uplink on top (3 wide, 2 deep, 3 high),
  that runs your launchers **anywhere in its dimension, loaded or not**: Launch Silos, Launch Complexes, Missile Silos,
  Cruise Missile Launchers, the SCUD Storm and the Nuclear Missile.
- **Launch Codes**: take them from the station, then use them on a launcher (its controller or any part of it) or a
  Radar Array to link it; sneak to unlink. Hand a card to a friend to share the network; sneak-use one on a second
  station to make it a second control room. **New codes** voids every old card.
- Its screen: a map of the whole dimension (zoom out to 1:128) with every launcher, target and strike, the network's
  missiles in the air and what its radars track; the launchers and their state; a log of launches, impacts, missiles
  lost and orders that failed.
- Pick several launchers to aim them in a **pattern** (a line, a ring or a grid round the point). **LAUNCH** fires every
  armed launcher at once, or **on target together**: each launch held so all the first warheads land at the same moment.
- A launcher far away is **woken to fire**: its chunks load, it counts down, launches and settles, then sleeps again.
  It needs its energy stored beforehand.
- On the console: the network's state on its big screen, a status board lamp for each launcher, two launch keys that
  turn and a guarded LAUNCH button whose cover lifts while anything is armed; a key-switch clack, the launch tones and a
  teletype printing each report.

### Alarm system
- Ten devices that warn a base, linked over **alarm channels** (0 to 9999, set on each device's screen; all start on
  channel 0, so they work together as soon as they are placed). None needs power.
- **Missile Warning Receiver**: listens to the Radar Arrays and warns of every missile they track that will come down
  within its warning radius (64 to 1,024 blocks), as real missile warning systems warn only where the missile is
  going. It gives redstone (steady, by the number incoming, or rising as impact nears), shows the time to impact and
  plays an attention tone.
- **Alarm Panel**: sound, silence or test the alarm by hand, or set it off with any redstone signal.
- **Hazard Monitor**: alarms on radiation over a set dose rate (10 to 1,000 RAD/h) or on poison gas.
- **Air-Raid Siren**: a Cold War siren whose horn turns as it sounds, its wail swelling as the horn sweeps past you,
  heard 160 blocks off; Attack (wailing) or Alert (steady), winding up and down, and the All Clear after an attack.
- **Klaxon** (klaxon, slow whoop, evacuate, hi-lo and a ship's general alarm) and **Alarm Bell**.
- **Warning Beacon**: rotating with sweeping beams, flashing or steady, in red, amber, blue, green or white (use a dye),
  on any face; **Emergency Light**: a caged bulkhead lamp, white or dark, red and flashing in an alarm.
- **Alert Display**: ALL CLEAR, or the alert: missiles inbound and the time to impact, the dose rate, the gas.
- **Alarm Relay**: wireless redstone while its channel is alarming (or while all is clear), to shut blast doors.

### Anti-missile defence
- **Interceptor Complex**: the multiblock missile defence, a 5 by 5 deck three high. A vertical launcher of 24 cells
  under armoured hatches stands in front of a deckhouse whose face is a fire-control radar. Beside an Interceptor
  Battery it reaches two and a half times as far (640 blocks with a Radar Array tracking, 240 on its own radar), its
  interceptors kill 95% of small missiles, radar array or not, and it sends a volley of two at each missile (four at
  class L and bigger), fired two at a time: the hatches swing open and the interceptors leap straight up out of their
  cells. Built from Launch Pads, six new **Launch Cells**, six new **Phased Array Panels**, Machine Casing and an
  Energy Port and an Item Port; the Holo-Projector shows it. A late-game build: its controller is made round a whole
  Interceptor Battery, and its cells and panels take dense steel, tungsten and military circuits.
- An EMP now fries interceptors flying through it, and an Interceptor Battery's screen says when an EMP has knocked it
  out.

### Changes
- The **Storm ICBM**'s lightning strikes each send out an **EMP pulse** 24 blocks round for twelve seconds (it was 4
  blocks for five): a ring of blue sparks runs out along the ground from each strike. At the storm's height almost anywhere
  in it sits under a pulse most of the time, so machines and missile defences inside it go dark, interceptors flying
  through it are fried, and unhardened missiles flown through it may turn duds. Lightning rods still take strikes
  safely, pulse and all.

## 0.3.0 — 2026-10-30

Exotic weapons and their own assembler, an anti-missile defence, and upgrade chips for the machines.

### New weapons
- **Antimatter ICBM**: an Antimatter Trap built into an intercontinental missile. Where it lands, a white sphere 67
  blocks round erases everything in it (blast-proof blocks too; only bedrock stays), with nothing dropped and no
  fallout. Its gamma flash kills anything in the open out to 90 blocks; walls of stone, concrete, lead or obsidian
  stop it.
- **Singularity ICBM**: a Singularity Core built into an intercontinental missile. A black hole opens where it lands
  and lives for a minute, pulling everything in and tearing the ground loose. It grows by what it eats: on open ground
  it digs a hole about 67 blocks round in rock; in a mountainside or the sea it grows bigger still. Then it implodes,
  pulling harder than ever as it shrinks to a point and vanishes.
- **Storm ICBM**: a Storm Core built into an intercontinental missile. Where it lands a supercell gathers over a whole
  region, 160 blocks round, for a minute and a half: lightning comes down six times a second on whatever stands in the
  open or stands tallest, chaining from creature to creature. It is born in a burst of lightning shooting up from
  where the missile lands into the sky where the clouds gather. Every strike explodes where it lands, leaving a small
  crater with fires round it, and knocks out machines nearby, while the wind drives everything round its eye. A
  lightning rod inside the storm draws its strikes safely.
- All three are built at the new **Special Weapons Assembler**, a bay with three glowing containment rings that turn
  round the missile as it is put together. They fire like any other intercontinental missile, from a Launch Complex or
  a Missile Silo. They are only ever built whole: there are no exotic bombs.

### Anti-missile defence
- **Interceptor Battery**: a launcher turret on a two-block pedestal that shoots down missiles coming down near it.
  Load it with **Interceptor Missiles**; its four cells fill from its store. It fires when an interceptor can meet the
  missile in time: the turret swings round, the interceptor climbs out and curves over onto the missile, and they meet
  in the sky. A missile shot down breaks up in the air and its warhead never goes off.
- It reaches 256 blocks for missiles a working **Radar Array** is tracking, and 96 on its own seeker. Each interceptor
  kills a small missile 80% of the time, less for bigger ones (40% for an intercontinental one), without radar, against
  a missile coming back down hot, or against a superweapon's. Big missiles draw two interceptors.
- Missiles flying out from near it, bound elsewhere, it lets go, so it never shoots down your own launches.

### Upgrade chips
- **Speed**, **Efficiency** and **Balanced** chips on each circuit tier, basic to quantum. A speed chip makes a
  machine 10% faster a tier (40% at quantum), an efficiency chip makes it use 5% less power a tier (20% at quantum), and
  a balanced chip does half of each. Every processing machine and assembler has an **Upgrades** tab on the left of its
  screen with three slots, and the chips in them add up.

### Changes
- The **Strategic Assembly Hall** is now the **ICBM Assembly Hall**, so it is no longer confused with the Strategic
  Missile Assembler. Halls already built keep working. It builds the thermonuclear, cobalt and Tsar ICBMs.
- The **Large Silo Door** has a sound of its own that lasts as long as its doors take to open or close.
- **Lattice Frames** and **Steel Grating** no longer hide each other like glass does: looking into a stack of them you
  see its far side and its sides from inside, while the faces between them stay hidden. The lattice frame is now framed
  on all four edges of each face.

## 0.2.0 — 2026-10-29

The world's abandoned sites, rebuilt from scratch, and a set of blocks to build your own.

### New blocks
- **Construction blocks**, all craftable: chain-link fence, barbed wire, corrugated steel and rusted corrugated steel
  (with stairs and slabs), steel and rusted steel panels, steel grating (with a slab), the I-beam, the lattice frame,
  radome panels (with stairs and a slab), the floodlight, sandbags (with a slab), and radiation, high-voltage, toxic
  and keep-out signs.
- **Large Silo Door** and its rusted twin: two leaves that slide apart on rails over a 7×7 opening on a redstone
  signal. They are for show and hold no missiles.
- **Search Radar**: a parabolic reflector eleven blocks wide that turns on a redstone signal, and a wrecked one.
- **Missile Wreck**: a broken two-stage missile, as a decorative prop. It has no recipe; one lies at every Crashed
  Missile site.

### The sites, rebuilt
- **Abandoned Silo**: the hardstand with its rusted door stuck on its rails, vents, a support building, an antenna and
  floodlights. Below, a round shaft with a work platform round the Titan II, the cableway, the blast lock and a
  two-storey control centre. Some are flooded, some caved in.
- **Command Bunker**: an earth-bermed entrance behind a blast wall, a stair down to decontamination, then the radio
  room, armoury, plant room, dormitory, washroom, a two-storey situation room, records, the mess and an escape tube.
  Above it: the radio mast, vents, a helipad and a guard's hut.
- **Radar Outpost**: the radar tower under its radome, a Search Radar on its own tower and a wrecked one, modules on
  piers, a billboard antenna, power poles, the fence and the gate.
- **Test Site**: the shot tower with its device, instrument masts, the control bunker under its sand bank, Doom Town
  with its mannequins, and an earlier test's crater with its obelisk and bleachers.
- **Crashed Missile**: the Missile Wreck at the end of its furrow, the crater with the bare uranium core, burned
  ground, and the recovery team's lifting gantry, cordon and abandoned camp.
- **Waste Storage**: a repository in bedded salt, laid out like the real WIPP: the main tunnel, rooms of drums stacked
  to the roof (one sealed off, one caved in, one being squeezed shut by the salt), decontamination, a lead-walled hot
  cell and the control room.
- **Exotic Research Station**: over the End's islands, a containment hall blown open from within with crystals bursting
  through it, lab and crew modules, and a Search Radar on a tower.

### Fixed
- Fences, masts and bars at sites no longer float a block above the ground.
- Every iron door at a site has a button on each side, so it can be opened.

Sites already generated in a world stay as they were; land explored from now on gets the rebuilt ones.

## 0.1.1 — 2026-10-28

- Renamed from Ground Zero to Ground Burst (mod id `groundburst`).

## 0.1.0 — 2026-10-28

- The first release.
