# Demolition regression checks

`BlastDemolitionTest.java` runs the actual patched `Bomb.explode` and
`ArcaneBomb.explode` methods without opening a graphics/audio backend. It checks
default/off behavior, all three locked door types, all three chest types, loot
preservation across linked explosions, visible/secret/inactive traps, the native
red sentry, damage-ignoring/high-evasion mobs, blast occlusion, death attribution,
Hero Bombproof protection, Tengu's two scripted fight transitions, key stacks
on the ground and inside chests, Worn Key preservation, decorative obstacles,
and decoration occlusion across linked explosions.

First apply Blast to an SPD checkout and compile its `core`, `SPD-classes`, and
`services` modules. Set `BLAST_TEST_CLASSPATH` to their compiled classes and runtime
dependency JARs (libGDX, controllers, FreeType, jnigen loader, and JSON). With a JDK:

```sh
mkdir -p /tmp/blast-tests
javac --release 11 -encoding UTF-8 -cp "$BLAST_TEST_CLASSPATH" \
  -d /tmp/blast-tests tests/BlastDemolitionTest.java
java -cp "/tmp/blast-tests:$BLAST_TEST_CLASSPATH" BlastDemolitionTest
```
