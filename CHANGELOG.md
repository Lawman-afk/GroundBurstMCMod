# Changelog

## Unreleased

A bigger missile defence, and a storm that knocks defences out.

### Anti-missile defence
- **Interceptor Complex**: the multiblock missile defence, a 5 by 5 deck three high. A vertical launcher of 24 cells
  under armoured hatches stands in front of a deckhouse whose face is a fire-control radar. Beside an Interceptor
  Battery it reaches two and a half times as far (640 blocks with a Radar Array tracking, 240 on its own radar), its
  interceptors kill 95% of small missiles, radar array or not, and it sends a volley of two at each missile (four at
  class L and bigger), fired two at a time: the hatches swing open and the interceptors leap straight up out of their
  cells. Built from Launch Pads, six new **Launch Cells**, six new **Phased Array Panels**, Machine Casing and an
  Energy Port and an Item Port; the Holo-Projector shows it.
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
