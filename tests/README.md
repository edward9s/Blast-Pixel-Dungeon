# Demolition regression checks

`BlastDemolitionTest.java` runs the actual patched `Bomb.explode` and
`ArcaneBomb.explode` methods without opening a graphics/audio backend. It checks
default/off behavior, all three locked door types, all three chest types,
native chest/item behavior across linked explosions, visible/secret/inactive traps, the native
red sentry, damage-ignoring/high-evasion mobs, blast occlusion, death attribution,
Hero Bombproof protection, Tengu's two scripted fight transitions, key stacks
on the ground and inside chests, Worn Key preservation, decorative obstacles,
destructive versus effect-only bombs with the test both enabled and disabled,
and native consumable destruction/equipment protection.

First apply Blast to an SPD checkout and compile its `core`, `SPD-classes`, and
`services` modules. Set `BLAST_TEST_CLASSPATH` to their compiled classes and runtime
dependency JARs (libGDX, controllers, FreeType, jnigen loader, and JSON). With a JDK:

```sh
mkdir -p /tmp/blast-tests
javac --release 11 -encoding UTF-8 -cp "$BLAST_TEST_CLASSPATH" \
  -d /tmp/blast-tests tests/BlastDemolitionTest.java
java -cp "/tmp/blast-tests:$BLAST_TEST_CLASSPATH" BlastDemolitionTest
```

`BlastNoisemakerTest.java` also runs without a graphics/audio backend. It tests
real lit throws, the two-unit remote alarm startup and six-unit repeat interval,
native monster waking/targeting, a bomb under the Hero, saved alarm timing and
remote ordering, immediate remote detonation, freezing, kicked native fuses,
and ordinary remote bombs remaining silent. Compile and run it with the same
classpath:

```sh
javac --release 11 -encoding UTF-8 -cp "$BLAST_TEST_CLASSPATH" \
  -d /tmp/blast-tests tests/BlastNoisemakerTest.java
java -cp "/tmp/blast-tests:$BLAST_TEST_CLASSPATH" BlastNoisemakerTest
```

`BlastUpdatesTest.java` verifies version ordering, draft/prerelease filtering, 
and unchanged Blast versions across SPD releases. Compile with the same classpath:

```sh
javac --release 11 -encoding UTF-8 -cp "$BLAST_TEST_CLASSPATH" \
  -d /tmp/blast-tests tests/BlastUpdatesTest.java
java -cp "/tmp/blast-tests:$BLAST_TEST_CLASSPATH" com.spd.blast.BlastUpdatesTest
```

Smoke-test both UI patch anchors:

```sh
python scripts/test_update_patches.py
```

`BlastAutoBombAITest.java` checks local Regrowth targeting and blast
footprint decisions. With auto-play, bomb immunity and remote mode active, the
selected bomb is eligible even if its explosion destroys floor items. Existing
restrictions for available charges, visible awake enemies, and rare-mob
protection still apply. For remote throws, the real bomb landing position
determines whether the blast can hit an enemy; a direct hit is not required.
During auto-play, only the hero is exempt from BombRegrowth rooting; all other
grounded characters keep the existing effect. Compile and run against patched SPD:

```sh
javac --release 11 -encoding UTF-8 -cp "$BLAST_TEST_CLASSPATH" \
  -d /tmp/blast-tests tests/BlastAutoBombAITest.java
java -cp "/tmp/blast-tests:$BLAST_TEST_CLASSPATH" com.spd.blast.BlastAutoBombAITest
```

`test_auto_bomb_destructive.py` checks that auto-play does not reject
destructive bomb types while retaining the existing auto-combat safeguards.

```sh
python -m unittest discover -s tests -p test_auto_bomb_destructive.py
```

`BlastAutoPickupTest.java` checks automatic pickup selection of all
`stackable = true` items, plus the Blast-specific non-stackable instant
pickups (`BlastRadiusPickup` and `BlastHastePickup`). The two special
pickups remain non-stackable and are handled by `BlastAutoPickupPolicy`;
the portable picker receives this policy as a parameter and contains no
Blast-specific type checks. Gold, journal keys, and instant pickups bypass backpack capacity;
ordinary inventory and dew restrictions remain unchanged.

```sh
javac --release 11 -encoding UTF-8 -cp "$BLAST_TEST_CLASSPATH" \
  -d /tmp/blast-tests tests/BlastAutoPickupTest.java
java -cp "/tmp/blast-tests:$BLAST_TEST_CLASSPATH" com.spd.blast.BlastAutoPickupTest
```


`BlastAutoBombAITest.java` also checks Regrowth target selection: targets
that are already rooted or stand in active Regrowth are ineligible. The
bomb AI checks `Blob.volumeAt` only at each target's position for both
`BombRegrowth` and native `Regrowth`. There is no reuse cooldown and
Regrowth elsewhere in the level does not block an eligible target.
Only Regrowth bombs use this restriction; other eligible bombs are unchanged.

`AutoPlayPlantSafetyTest.java` verifies that automatic movement avoids SPD
plants with harmful or disruptive effects (Blindweed, Fadeleaf, Firebloom,
Icecap, Rotberry, Sorrowmoss and Stormvine), while permitting helpful or
harmless plants (including Sungrass, Starflower and the Regrowth wand's
Dewcatcher/Seedpod). The same hazard rule applies to normal walking and
phase movement through sheep.

```sh
javac --release 11 -encoding UTF-8 -cp "$BLAST_TEST_CLASSPATH" \
  -d /tmp/blast-tests tests/AutoPlayPlantSafetyTest.java
java -cp "/tmp/blast-tests:$BLAST_TEST_CLASSPATH" com.spd.blast.AutoPlayPlantSafetyTest
```


`scripts/test_wnd_bag_refresh_patch.py` verifies the fail-fast integration
patches for native `WndBag` and `WndQuickBag`. Both windows are refreshed
by SPD's existing `GameScene.updateItemDisplays` notification. Open backpack
tabs remain selected, while the quick-use popup rebuilds its visible item
buttons as inventory contents change. Neither window is closed or reopened
during a refresh.

```sh
python scripts/test_wnd_bag_refresh_patch.py
```


`BlastAutoCombatTest.java` checks that sleeping mobs are not combat targets,
while wandering and hunting mobs can be targeted. It also tests bounded
last-seen-position pursuit: the AI tries a reachable location for at most
eight unseen turns, then temporarily ignores an unmoving target to avoid
oscillating between enemies and floor loot. The ignore ends if the enemy
moves, threatens the hero in melee, or damages the hero. In normal combat,
an actively attacking adjacent melee opponent takes first priority, then
thieves (including bandits) and ranged enemies, then ordinary melee mobs.
Safe path length breaks ties within each tier. Ranged enemies must be
approached to adjacent melee range for non-bomb attacks, even with a
long-reach weapon or charged wand. When bomb immunity is active, the
existing bomb AI can strike remotely if the actual blast footprint reaches
a target, subject to existing safety restrictions. The controller reuses
the existing distance map.

```sh
javac --release 11 -encoding UTF-8 -cp "$BLAST_TEST_CLASSPATH" \
  -d /tmp/blast-tests tests/BlastAutoCombatTest.java
java -cp "/tmp/blast-tests:$BLAST_TEST_CLASSPATH" com.spd.blast.BlastAutoCombatTest
```

`test_auto_play_target_priority.py` verifies that the combat priority
selection is reached before old-target pursuit or floor pickup, ranged
opponents require adjacency for ordinary attacks, and bomb selection uses
the actual landing blast footprint while preserving rare-mob safety checks.

```sh
python -m unittest discover -s tests -p test_auto_play_target_priority.py
```

`BlastAutoFarmingTest.java` checks the isolated idle farming policy:
visible reachable high grass and beneficial plants are deliberately harvested
whenever auto-play and auto-farming are enabled, even if no Regrowth bomb or
remote-bomb buff is available (or bombs are in timed mode).
The first Regrowth bomb is allowed, further bombs wait until a crop was
harvested and the last BombRegrowth cloud dissipated, visible high grass
and harmless plants qualify for harvest, and harmful plants remain
excluded. Farming requires at least 10 accessible growable tiles inside the
actual wall-occluded Regrowth blast. Suitable terrain is EMPTY, EMPTY_DECO,
EMBERS, GRASS, or FURROWED_GRASS; walls, water, exits, traps, chasms and
existing high grass do not count as new growth. Farming selects a reachable
location with enough space before detonation, using a fixed 7x7 coarse check
and at most 8 exact blast checks per site search. Searches are at least 8
game ticks apart and a selected destination is cached while travelling. The implementation temporarily selects a RegrowthBomb type,
detonates it, restores the original selected type via `finally`, and
reuses the standard auto-pickup priority before crop collection.
Farming harvests require auto-play, the independent farming checkbox,
no visible awake enemy, and a grounded hero. Planting new grass additionally
requires remote bomb mode and an available Regrowth bomb. Both reuse the
same safe navigation map; harmful plants and hazardous terrain stay excluded.

```sh
javac --release 11 -encoding UTF-8 -cp "$BLAST_TEST_CLASSPATH" \
  -d /tmp/blast-tests tests/BlastAutoFarmingTest.java
java -cp "/tmp/blast-tests:$BLAST_TEST_CLASSPATH" com.spd.blast.BlastAutoFarmingTest
```

`test_auto_farming_harvest.py` guards the independent harvest path:
harvesting safely reachable tall grass and beneficial plants must run before
any bomb-mode, bomb-inventory, or Regrowth-type checks, while preserving
grounded-hero and awake-enemy safety gates.

```sh
python -m unittest discover -s tests -p test_auto_farming_harvest.py
```


`BlastAutoEnvironmentTest.java` verifies Blast-only environmental avoidance:
active Fire and ToxicGas cells are unsafe, inactive cells remain traversable,
and the auto-playing hero seeks water while burning only when it is reachable
through safe terrain within four steps. Water behind hazardous tiles, beyond
four steps, or unreachable is ignored. SPD's Burning buff removes the effect
after the grounded hero reaches water. The same hazard-aware distance map
is reused for combat movement, item collection, roaming, and auto-farming.

The safety policy also excludes active Inferno, VaultFlameTraps, CorrosiveGas,
ParalyticGas, StenchGas, ConfusionGas, Electricity, StormCloud, Freezing,
Blizzard, SmokeScreen, Web, and GooWarn. SacrificialFire is excluded from
that hazard list because it does not damage living characters directly; its
marking aura is handled by the separate auto-play lure tactic. Active blob
references are captured once per hero action, not retrieved once per map cell.
While standing in a harmful blob, the hero tries to step onto a safe adjacent
cell before other automatic actions. Emergency water movement is prioritized
ahead of bomb tactics, otherwise normal combat priority is preserved.

```sh
javac --release 11 -encoding UTF-8 -cp "$BLAST_TEST_CLASSPATH" \
  -d /tmp/blast-tests tests/BlastAutoEnvironmentTest.java
java -cp "/tmp/blast-tests:$BLAST_TEST_CLASSPATH" com.spd.blast.BlastAutoEnvironmentTest
```

`BlastAutoSacrificeTest.java` verifies that the SacrificialFire marking
aura covers the fire and eight adjacent tiles without wrapping across map
rows, only a marked enemy still within the aura is attacked, and pursuing
mobs can be lured into the aura while unrelated distant enemies are ignored.
The auto-player temporarily occupies a reachable sacrificial flame and waits
for chasing enemies to step within its marking area. A visible ranged threat immediately disables the sacrifice-luring tactic, so
normal combat attacks it directly without diverting to the altar. Ranged types
include SPD spellcasters, projectile users, and stationary ranged attackers;
subclasses inherit this behavior. A stationary melee enemy cannot cause an
unlimited wait: three stalled hero turns or twelve game ticks end the attempt
and suspend new lures for eight game ticks. Safety checks continue
to exclude every other active damaging or disruptive blob. The lure uses
the same safe-cell and shortest-distance maps as regular auto-play and
only activates for fire no more than six safe steps from the hero.

```sh
javac --release 11 -encoding UTF-8 -cp "$BLAST_TEST_CLASSPATH" \
  -d /tmp/blast-tests tests/BlastAutoSacrificeTest.java
java -cp "/tmp/blast-tests:$BLAST_TEST_CLASSPATH" com.spd.blast.BlastAutoSacrificeTest
```
