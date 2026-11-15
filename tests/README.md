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

`BlastAutoBombAITest.java` checks that Regrowth, Arcane and Shrapnel
bombs share the non-destructive automatic attack path, while a normal bomb does not.
During auto-play, only the hero is exempt from BombRegrowth rooting; all other
grounded characters keep the existing effect. Compile and run against patched SPD:

```sh
javac --release 11 -encoding UTF-8 -cp "$BLAST_TEST_CLASSPATH" \
  -d /tmp/blast-tests tests/BlastAutoBombAITest.java
java -cp "/tmp/blast-tests:$BLAST_TEST_CLASSPATH" com.spd.blast.BlastAutoBombAITest
```

`BlastAutoPickupTest.java` checks automatic pickup selection of all
`stackable = true` items, plus the Blast-specific non-stackable instant
pickups (`BlastRadiusPickup` and `BlastHastePickup`). The two special
pickups remain non-stackable and are handled by `BlastAutoPickupPolicy`;
the portable picker receives this policy as a parameter and contains no
Blast-specific type checks. Gold and instant pickups bypass backpack capacity;
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
