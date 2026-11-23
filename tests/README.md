# Demolition regression checks

## Automatic alchemy near a pot

`BlastAutoAlchemyTest.java` tests the actual `MeatPie.Recipe` with selection of
Pasty, exact-class ordinary Ration and the four eligible meats. It checks
missing and zero-quantity ingredients, recipe energy cost, and the one-item
consumption behavior. It runs against the patched SPD core using the same
`BLAST_TEST_CLASSPATH` as the other Java tests:

```sh
javac --release 11 -encoding UTF-8 -cp "$BLAST_TEST_CLASSPATH" \\
  -d /tmp/blast-tests tests/BlastAutoAlchemyTest.java
java -cp "/tmp/blast-tests:$BLAST_TEST_CLASSPATH" com.spd.blast.BlastAutoAlchemyTest
```

The source-level regression checks verify pot proximity without pathfinding,
safe/idle priority, native recipe energy bookkeeping, empty-stack removal and
result collection:

```sh
python -m unittest discover -s tests -p test_auto_alchemy.py
```

## Automatic trap clearance

After combat, food, pickup, interaction, and ordinary harvesting, idle auto-play
tries to clear supported armed traps before optional grass farming and roaming.
Both visible and previously mapped/visited hidden trap terrain may be targeted.
A trap need not be an exact two-exit corridor choke point: the hero can choose
an unobstructed throwing position around a corner or in a room. The former
choke-point analysis still supplies a preferred approach when applicable.

Activation must disarm the trap and the target must be unoccupied and free of
plants. Non-disarming Flashing and Gripping traps, explosions, falls,
Tengu's specialized dart trap, and other unreviewed effects remain excluded.
Four nearest-target traps (Worn Dart, Poison Dart, Disintegration and Grim)
are eligible only when their native projectile line and true-distance targeting
unambiguously select permitted hostile mobs. The predictor also respects the
native minimum six-tile view range, invisibility distance treatment and current
character occupancy. A tie involving the hero or any protected character
vetoes firing. Sleeping or invisible mobs, allies, neutrals, quest/service NPCs,
demon spawners and invulnerable/avoided/charmed targets are never deliberately
attacked. Target eligibility is checked again from the actual throwing position
before casting because characters may move during approach. The early
opportunistic check runs after bomb tactics but before ordinary attacks,
and only fires if the hero can throw without walking or opening a door.
These traps still use the idle clearance flow when an approach is needed.

A Flock trap is excluded if any other active trap is within the two-tile sheep
spawn radius, preventing automatic chained activations.
The throwing position for a Shocking trap must be dry as an additional
precaution against electricity traveling through water.

The supported additional types are Confusion, Corrosion, Geyser, Shocking,
Disarming, Flock, Teleportation, and Warping traps. Toxic, Confusion, and
Corrosion gas share a route-aware retreat mechanism that supports L-bends
and sideways first steps, while rejecting closed corners and dead ends
without adequate escape. Toxic and Confusion throws normally start at two
cells, Corrosion at three. A shorter throw is a last resort *only* if an
escape route is known: one cell for Toxic/Confusion and two for Corrosion.
Escape searching is bounded to eight moves. Following activation, the hero prioritizes leaving the active gas
and avoids turning back toward its source. Other effects retain specific
stand-off ranges based on their native action.

Burning and Blazing fire traps require at least two and three tiles,
respectively; at shorter range an identified PotionOfFrost can be used.
The native projectile must actually reach the trap, and any closed intervening
door can be opened first with an expendable item (without casting the frost
potion at the door). Automatic pickup temporarily skips the door-holding item
until the trap is cleared.

Aqua Brew is an alternative fallback for any active trap (including hidden,
explosive, ranged, boss-specific, and non-disarming traps). Native AquaBrew
does not press traps: its Geyser effect changes trap terrain to water using
`Level.setCellToWater(true, cell)`, removing the trap without activation.
The program throws it at the trap's exact cell from at least two cells away,
validates projectile collision, checks custom tiles that forbid water and
avoids a landing point that could knock back protected characters within
one cell. It is spent only if the normal safe throw is unavailable or has no
eligible expendable item. Aqua Brew is not used for opportunistic pre-combat
aimed-trap attacks, because it would remove the trap without attacking the
enemy. Water cannot be created on every custom tile, and the native geyser
may push nearby hostile mobs.

Un-equipped ordinary thrown weapons now take priority as reusable trap
activators: plain darts first, then unupgraded, unenchanted inexpensive missiles.
Missiles that explode, return without landing, have special dart tips, or carry
upgrades, enchantments, or curses are reserved and never chosen for trap
activation. After a missile lands, ordinary auto-pickup can recover it once the
tile is safe. If no suitable missile is available, un-equipped spare melee
weapons/armor and surplus food remain eligible sacrifices. The food selection
shares the automatic feeding reserve for Meat Pie ingredients, and finished
Meat Pies are never sacrificed. Other inventory items, which may have dangerous
or irreversible throw effects, are not thrown blindly. This avoids spending
valuable potions, scrolls, runestones, and special-purpose objects on trap
clearing. Debug Log records nearby rejections (e.g. `unsupported`,
`chain-reaction`, `no-safe-stand`, `no-throwable-item` and
`blocked-trajectory`) at a bounded repeated-message rate.

`BlastAutoTrapClearerTest.java` tests trap safety categories, choke-point
reachability and sacrifice selection against the patched SPD core:

```sh
javac --release 11 -encoding UTF-8 -cp "$BLAST_TEST_CLASSPATH" \
  -d /tmp/blast-tests tests/BlastAutoTrapClearerTest.java
java -cp "/tmp/blast-tests:$BLAST_TEST_CLASSPATH" com.spd.blast.BlastAutoTrapClearerTest
```

```sh
python -m unittest discover -s tests -p test_auto_trap_clearance.py
```

## Automatic mystery meat

`BlastAutoFoodTest.java` checks selection by available stack size, raw Mystery
Meat eligibility, native Pasty/Ration/meat reserves across the four meat forms,
finished Meat Pie eligibility, ties, and exclusion of uncooked Blandfruit.
The Python tests also guard equipped/charged Horn of Plenty priority, the native
Horn EAT action, and eligibility checks.
`test_auto_mystery_meat.py` checks the native meat patch, manual-effect
preservation, patch drift rejection, and integration registration:

```sh
python -m unittest discover -s tests -p test_auto_mystery_meat.py
```


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
and ordinary remote bombs remaining silent. It also checks stacked armed bombs
on locked and crystal chests: the original loot remains first, automatic
identity-based detonation triggers the linked bomb exactly once, occupied bomb
slots are returned, and freezing snuffs exposed bombs without unlocking the
chest. Compile and run it with the same
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
restrictions for available charges, eligible awake enemies, and protected NPCs
still apply. Rare enemies are eligible targets and do not block collateral
blast coverage when bomb immunity is active. For remote throws, the real bomb landing position
determines whether the blast can hit an enemy; a direct hit is not required.
With either Bombproof buff, only the hero is exempt from BombRegrowth rooting;
all other grounded characters keep the existing effect. Auto-play alone grants
no bomb-effect immunity. Compile and run against patched SPD:

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
Blast-specific type checks. Gold, energy crystals, journal keys, and rose petals bypass backpack capacity.
Rose petals are only selected when the hero holds a Dried Rose below level 10;
ordinary inventory and dew restrictions remain unchanged. Loot within the hero's
current field of view is considered even if its heap's previous seen flag is stale.
An automatic pickup retry cooldown now starts only when the native Hero pickup
reports failure; merely scheduling a pickup does not suppress the item.

```sh
javac --release 11 -encoding UTF-8 -cp "$BLAST_TEST_CLASSPATH" \
  -d /tmp/blast-tests tests/BlastAutoPickupTest.java
java -cp "/tmp/blast-tests:$BLAST_TEST_CLASSPATH" com.spd.blast.BlastAutoPickupTest
```

The existing Blast **Debug Log** checkbox also traces automatic decisions while
a visible ordinary floor heap is within two cells of the hero. The single-line
`[Blast] auto-loot` event contains the hero and item cells, top-item class,
pickup eligibility (`ready`, `type`, `full`, `lost-inventory`,
`dew-full`, `rose-unavailable`, `unsafe`, `blocked`,
`unreachable`, or `cooldown`), the winning task (`chosen`), and the
issued action. Full and lost-inventory rejections also include the occupied
and maximum slot counts for the backpack and its contained bags. `ready` means the floor item passes its local conditions and
the base distance map; task-specific exclusions on intermediate steps may
still prevent pickup. Logs are emitted only when Debug Log is enabled and
identical consecutive observations are limited to one in ten decisions.
Debug output is written to `game1/blast-debug.txt`, `game2/blast-debug.txt`,
and so on (according to the active save slot). Entering a new game or
continuing a save replaces that slot's previous debug file; switching
floors only appends to the same file. The file is flushed on every event,
limited to 1 MiB, and copied by the existing **Export save** action into
`Documents/spd_saves/<app name>/gameN/blast-debug.txt`.
Entering with Debug Log disabled removes the old file for that slot.
Disabling logging mid-game closes the file; re-enabling appends to the
same session. No ADB or additional storage permission is needed beyond
the permission already required to export saves.
This instrumentation does not change any automatic action priority.

```sh
python -m unittest discover -s tests -p test_auto_loot_debug.py
```

The native failure callback is inserted by `integration/shattered/patches/patch_hero.py`.
The source-level integration check confirms it only starts cooldown for actual
failed native pickups:

```sh
python -m unittest discover -s tests -p test_auto_pickup_native_failure.py
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
Visible hostile targets are ordered by threat tier and distance. The unified
combat AI tries executable attacks against them before movement: high-evasion
enemies prefer safe wands; other attacks compare estimated damage, including
point-blank accuracy, reach weapons, thrown missiles and Spirit Bow arrows.
Native auto-aim determines actual aim cells without a map-wide distance array.
Lightning rejects possible self/NPC arcs; disintegration checks the full
limited beam for target coverage and protected characters. Corrosion requires
at least four cells of distance, no existing corrosion at the target, and a
buffer around quest NPCs. Fireblast remains excluded. Bomb immunity preserves
the existing bomb-first policy and blast-range approach.

```sh
javac --release 11 -encoding UTF-8 -cp "$BLAST_TEST_CLASSPATH" \
  -d /tmp/blast-tests tests/BlastAutoCombatTest.java
java -cp "/tmp/blast-tests:$BLAST_TEST_CLASSPATH" com.spd.blast.BlastAutoCombatTest
```

`test_auto_play_target_priority.py` verifies that the combat priority
selection is reached before old-target pursuit or floor pickup, hostile ranged
and melee enemies use the same attack policy, and bomb selection uses
the actual landing blast footprint while preserving quest and service NPC safety checks.

```sh
python -m unittest discover -s tests -p test_auto_play_target_priority.py
```

`BlastAutoFarmingTest.java` checks optional idle Regrowth planting and the
ordinary auto-play crop classification. Auto-play always seeks reachable high
grass and beneficial plants after combat, pickup, and interaction, even when
auto-farming is disabled or Regrowth bombs are unavailable. Harmful plants
are excluded. The first Regrowth bomb is allowed; further bombs wait until
a crop was harvested and the last BombRegrowth cloud dissipated.
Farming requires at least 10 accessible growable tiles inside the actual
wall-occluded Regrowth blast. Suitable terrain is EMPTY, EMPTY_DECO,
EMBERS, GRASS, or FURROWED_GRASS; walls, water, exits, traps, chasms and
existing high grass do not count as new growth. Farming selects a reachable
location with enough space before detonation, using a fixed 7x7 coarse
check and at most 8 exact blast checks per site search. Searches are at
least 8 game ticks apart and a selected destination is cached while
travelling. The implementation temporarily selects a RegrowthBomb type,
detonates it, and restores the original selected type via `finally`.
Optional auto-farming alone controls placing new Regrowth bombs, which
also requires remote bomb mode and an available bomb. Both features reuse
the safe navigation map, skip visible awake enemies, and require a grounded
hero.

```sh
javac --release 11 -encoding UTF-8 -cp "$BLAST_TEST_CLASSPATH" \
  -d /tmp/blast-tests tests/BlastAutoFarmingTest.java
java -cp "/tmp/blast-tests:$BLAST_TEST_CLASSPATH" com.spd.blast.BlastAutoFarmingTest
```

`test_auto_farming_harvest.py` guards the shared auto-play harvest path:
harvesting safe tall grass and beneficial plants runs before optional farming
and roaming, without requiring the farming setting or bomb inventory.
Regrowth planting still tracks completed harvest decisions.

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
While standing in a harmful blob, the hero first tries to step onto a safe
adjacent cell. If surrounded by gas, the emergency escape can cross gas-only
cells to reach the nearest known, reachable safe tile. Other hazards (including
gas overlapping fire), traps, chasms and blocked terrain remain forbidden.
If no clean tile is reachable, the escape does not choose an arbitrary hazardous
step. Emergency water movement is prioritized ahead of bomb tactics; otherwise
normal combat priority is preserved.

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


`BlastAutoRoamingTest.java` verifies that auto-play prefers the nearest reachable
unexplored frontier, preserves its selected destination, excludes failed
corners, and idles when there is nothing left to explore. Previously rejected
frontiers become eligible again after solid obstacles change or roaming resets.
The navigator only considers known traversable cells.

```sh
javac --release 11 -encoding UTF-8 -cp "$BLAST_TEST_CLASSPATH" \
  -d /tmp/blast-tests tests/BlastAutoRoamingTest.java
java -cp "/tmp/blast-tests:$BLAST_TEST_CLASSPATH" com.spd.blast.BlastAutoRoamingTest
```

`BlastAutoHarvestTest.java` verifies that auto-play retains a selected crop
when movement around a wall temporarily hides it, then revalidates the crop
after it becomes visible again. This prevents harvest-versus-roam oscillation.
`BlastAutoRoamingTest.java` additionally checks that unrelated automatic tasks
cannot cancel an otherwise reachable roaming goal. Failed movement is handled
centrally by `BlastAutoProgressGuard`; a destination is replaced when it becomes
unreachable.

```sh
javac --release 11 -encoding UTF-8 -cp "$BLAST_TEST_CLASSPATH" \
  -d /tmp/blast-tests tests/BlastAutoHarvestTest.java tests/BlastAutoRoamingTest.java
java -cp "/tmp/blast-tests:$BLAST_TEST_CLASSPATH" com.spd.blast.BlastAutoHarvestTest
java -cp "/tmp/blast-tests:$BLAST_TEST_CLASSPATH" com.spd.blast.BlastAutoRoamingTest
```

Once no useful exploration frontier remains, auto-play keeps roaming to
random reachable known cells, preferring paths at least three steps long.
Harvesting and auto-farming movement clear previous roaming goals so a completed harvest
does not immediately send the hero back to an obsolete destination.

`patch_bomb_safety.py` keeps direct flashbang paralysis, immediate frost, bonus holy
bomb damage, and bomb-generated fire/freezing source-specific. Both the temporary
and testing Bombproof buffs protect only the hero. Ordinary environmental fire,
freezing, and enemy attacks retain their original effects; unprotected heroes
and all other characters retain their normal bomb reactions. The two separate
hazard blobs still burn/freeze items and affect terrain, and regular fire and
freezing extinguish both bomb-sourced and non-bomb-sourced opposing blobs.

```sh
python scripts/test_bomb_safety_patch.py
python scripts/test_regrowth_bomb_patch.py
```


\`BlastAutoProgressGuardTest.java\` checks task-scoped navigation failures:
all idle tasks avoid an actively hazardous doorway, but it is released once
the doorway itself is safe, even if adjacent gas remains. Two-cell and three-cell
loops and repeated failed moves
receive temporary exclusions only for the responsible task (pickup, interaction,
harvest, farming, travel, or roaming); unrelated tasks retain access to the same
cell. Combat and emergency escape continue using the original safe map.

\`\`\`sh
javac --release 11 -encoding UTF-8 -d /tmp/blast-tests \
  core/src/main/java/com/spd/blast/BlastAutoProgressGuard.java \
  tests/BlastAutoProgressGuardTest.java
java -cp /tmp/blast-tests com.spd.blast.BlastAutoProgressGuardTest
\`\`\`
