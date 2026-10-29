# Integration

Blast is an overlay on Shattered Pixel Dungeon. GitHub Actions checks out the
latest upstream release and copies `core/src/main/java/com/spd/blast/` into it.
The small, explicit patches under `integration/shattered/patches/` then add the
settings tab, replace the title banner and six hero splashes, show the Blast version, identify the separate app package, and scale natural respawning.

```bash
bash integration/shattered/apply.sh /path/to/shattered-pixel-dungeon .
```

All patches fail when their expected upstream seam changes. The workflow builds
against the latest published upstream release and does not silently skip patches.

### Blast settings tab visibility

`BlastSettings.SHOW_BLAST_TAB` is a compile-time boolean (default `true`).
`patch_wndsettings.py` checks it when constructing the shared `WndSettings`
window. Both the title-screen Settings button (`TitleScene`) and the in-game
Settings button (`WndGame`) open that same window:

| `SHOW_BLAST_TAB` | Title-screen Settings | In-game Settings |
| --- | --- | --- |
| `true` (default) | Show Blast tab | Show Blast tab |
| `false` | Hide Blast tab | Hide Blast tab |

This flag controls whether the tab exists; it does not force the tab to be
selected when Settings opens. It is separate from `BlastSettings.showTests()`,
which controls the visibility of experimental controls *inside* the Blast tab.
Changing the compile-time flag requires rebuilding the app.

The main-menu About/Info button uses `core/src/main/assets/interfaces/blast_credit.png`
(16×16 PNG). `patch_assets.py` installs this independent image, while
`patch_title_scene.py` uses it for the button and `patch_about_scene.py` adds a
Blast Pixel Dungeon / edward9s credits block before the untouched Shattered Pixel
Dungeon and Pixel Dungeon credits. The same `apply.sh` integration runs in both
standalone and SMM builds.

### Launcher artwork

Put the three canonical icon PNGs in `assets/app-icons/`:

- `ic_launcher.png` (192×192)
- `ic_launcher_foreground.png` (432×432)
- `ic_launcher_monochrome.png` (432×432)

`patch_icons.py` uses Pillow to generate the 32 Android main/debug launcher files and
15 iOS AppIcon PNGs before compilation. Android's 10 existing adaptive backgrounds
remain untouched. Missing sources and unexpected upstream icons fail immediately.
The two GitHub Actions workflows install Pillow and run this patch for standalone
Blast and Blast + SMM. Neither workflow currently compiles iOS binaries; the
patched SPD checkout is ready for an iOS build separately.

Other testing switches are hidden by default. Indestructible
filters bomb-attributed damage in `Hero.damage`; Free Movement extends Hero's
walking/pathfinding for walls, locked doors, and fixed obstacles (barricades,
bookshelves, statues, region/custom decorations, mine obstacles, and alchemy
fixtures), without mutating `Level.passable` or allowing out-of-map movement.
In the blacksmith's MiningLevel, Free Movement phases through ordinary
WALL, MINE_CRYSTAL, and MINE_BOULDER terrain even when carrying a Pickaxe.
Only WALL_DECO (dark gold) keeps the native Pickaxe mining action and blocks
phasing when the Pickaxe is carried. Without a Pickaxe, dark-gold terrain
is phaseable too. When Free Movement is off, normal Pickaxe mining applies
to all four terrain types. Both the test setting and the temporary Free
Movement buff use these same rules.
Other levels retain normal phasing.
Locked exits remain blocked, and chasms retain their normal movement rules. A matching iron/crystal key
triggers SPD's original unlock animation, key consumption, and time cost
before movement resumes. Without a matching key, phasing leaves the door locked.

CI and release workflows produce independent standalone and SMM variants, following
CoHero's integration sequence: Blast patches, SMM source rebase and injection,
and SMM source overlay.

Save transfer intentionally follows CoHero: snapshots are imported/exported under
`Documents/spd_saves/<application name>/` on both Android and Desktop. The
application name is `Blast Pixel Dungeon` (with SMM's bracketed naming in the SMM variant) and is read from Android's
installed app label or the desktop JAR's specification title. Imports replace
existing local saves and exit the process to avoid mixing in-memory state.

## Staggered explosion visuals

Bomb chain reactions still resolve immediately, including damage, bomb removal,
terrain changes, and status effects. `BlastBombVisuals` collects a complete
synchronous chain before queuing its particle animations and sounds. The first
bomb plays first; subsequent bombs play in increasing straight-line distance
from that first blast, rather than the underlying map-cell iteration order.
Each distinct distance adds about 90 ms, while equidistant bombs play together.
The Hero does not have to move or take a turn for the remaining visuals to play.
Saving and fuse behavior are unchanged, and the visual queue is discarded when
the scene closes. Special bomb audiovisual effects use their bomb's timestamp.

## Procedural room cover

After successful painting of a new `RegularLevel`, Blast places isolated
single-cell walls inside ordinary standard rooms (no generated statues).
If a room originally contains native BOOKSHELF tiles (wooden-wall visuals),
its new wall cells also use BOOKSHELF unless they touch a stone WALL,
WALL_DECO, or disguised SECRET_DOOR in one of the four cardinal directions.
Rooms without wooden walls use ordinary stone WALL tiles. Native BOOKSHELF
tiles are flammable and are destroyed by ordinary bomb/fire effects, rather
than using Blast's seeded stone-wall durability.
Entrances, exits, secret rooms, special rooms, and dedicated connection rooms
are unchanged. Cover starts at a 3x3 interior with at least nine walkable
floor cells; one new wall is attempted per 12 walkable cells, with a
minimum of one and a maximum of eight. Existing natural pillars and decorations
do not subtract from this budget.

Placement allows ordinary walkable ground even when it is water, grass, or
immediately adjacent to a wall; there is no 3x3-clear-area or multi-cell
door-clearance rule. Existing obstacles, level transitions, functional tiles,
placed items, traps, plants, and overlaid custom artwork remain untouched.
The square immediately inside a door and all four inner room corners
are reserved. Narrow one-tile passages are protected, and every prospective
blocker must pass a room-local four-direction connectivity check: blocking it
cannot disconnect the remaining walkable neighbours and therefore cannot sever
a room route. New obstacles remain at least one cell apart.

The candidate order derives from the floor seed and cell, without consuming
SPD's normal generation RNG. The wall material follows the room's existing
terrain and adjacent stone walls. BlastWalls assigns seeded stone-wall
durability after room painting. Previously generated levels retain their
existing map layouts.

## Seeded destructible walls

On procedurally generated regular floors, ordinary and decorative wall tiles
(WALL and WALL_DECO) receive hidden, fixed durability from a hash of the floor's
seed and tile index. No level-generation random calls are consumed. Across
eligible walls, the expected proportions are **50% one-hit**, **15% three-hit**,
and **35% indestructible**. Walls directly adjoining a passable tile in one of
the four cardinal directions are favored for one-hit durability. The one-hit
probability of other walls is lowered by the corresponding amount, preserving
the same overall expected distribution regardless of room layout. Actual
counts vary with each floor's seeded rolls. Only interior wall tiles are
eligible; the outer map border, doors, quest structures, and scripted boss
maps remain protected. Saved floors retain their existing durability; these
probabilities apply when a new floor is generated.

A destructive bomb deals one point of durability damage to each marked wall touched by
its explosion, at most once per bomb. All bombs use center-to-center ray tracing for their blast footprints, so
wall and statue damage stops at solid terrain regardless of the Hero's radius boost. Durability zero immediately converts
the tile to ordinary floor (with collision, map rendering, and field of view
updated). Small wall-break particles play at the triggering bomb's existing
staggered visual timestamp. Different bombs in a chain may each damage the
same wall. Current durability persists in each floor's saved data. Previous
Blast saves without that field are deliberately not supported.

On regular floors, stone terrain statues (`STATUE` and `STATUE_SP`)
also receive seeded durability, with the same **50% one-hit, 15% three-hit,
35% indestructible** expected distribution and exposure adjustment as walls.
Their values are calculated independently from walls, preserving the original
wall seed outcomes. Destructive bombs reduce statue durability by one per
blast, and zero durability turns a statue into ordinary floor with the normal
wall-break particles. Statue destruction does not trigger the higher
wall-collapse chance. Scripted non-regular levels keep their statues intact.

On procedurally generated `RegularLevel` floors, destructive bomb types may cause
1–3 cave-in boulders after a complete explosion chain: 35% normally, or
75% when that bomb destroyed a marked wall. Scripted non-regular floors do
not produce these Blast cave-ins.
Each bomb rolls independently and selects 1–3 unique eligible cells without
replacement; the boulders do not need to be adjacent to one another.
Every chosen cell must be within that bomb's blast footprint and adjacent
to a surviving or newly destroyed wall. If fewer than the randomly chosen
number of eligible cells exist, only those available cells are used.
Only plain EMPTY floor is eligible, excluding occupants, heaps, traps, plants,
and custom tiles. Existing special floor tiles remain unchanged; special rooms
are not categorically protected. The resulting tiles use SPD's actual Terrain.MINE_BOULDER, the same blocking
terrain generated by GnollGeomancer in the blacksmith's mining quest. It is
solid but, unlike WALL, does not block line of sight. Blast still assigns
each created boulder one-hit bomb durability; natural quest boulders are not
given that durability.

The per-floor rubble marker distinguishes cave-in boulders from native
quest boulders and persists in the existing level save bundle. The ordinary
dungeon tilesets do not supply a base tile for mining boulders, which would
otherwise expose the animated water backdrop. Only Blast-marked rubble uses
the regular floor tile as its background in DungeonTerrainTilemap. The
TerrainFeaturesTilemap still renders the native rock visual, and the tile
keeps its original solid terrain and one-hit blast durability. Native mining
quest boulders remain unchanged. WndInfoCell uses the native rock icon and
localized "Rubble" title/description for Blast's marked boulders; unmarked
quest rocks retain their native descriptions.
Rock particles, dust, and a rockfall sound play at the corresponding bomb's
staggered explosion timestamp. Gameplay/map changes still resolve
synchronously after linked explosions and subtype effects so rubble cannot
interfere with the same chain. The rubble marker is cleared when the tile
ceases to be a boulder. No new art asset is added.


## Remote and timed bombs

BlastRemoteBomb is a permanent mode controller. It is maintained on Hero.act and
GameScene entry, and survives unblessed-Ankh resurrection. Its timedMode flag
(default false: remote) is stored in the buff bundle alongside the remote bomb
sequence. The obsolete remote pickup and testing switch remain removed.

Long-pressing the lower bomb Tag changes modes for zero turns, only if no lit
bombs of any kind remain in the current floor heaps. Entering timed mode also
requires at least 2 maximum bomb slots unless the Infinite bombs test is on;
a failed attempt gives a localized explanation. Switching cancels any pending Bomb Maniac aiming selector.
Mode changes do not reset selected types or other buffs.

In remote mode, Bomb Maniac places an armed bomb beneath the Hero instantly at
zero turn cost. Its serial-numbered RemoteFuse never counts down. A tap on the
lower Tag detonates the oldest surviving remote bomb; the original golden
heart overlay stays visible in this mode.

Remote Noisemakers retain their lure: `RemoteFuse` calls the shared native
`NoisemakerFuse.blastAlert` after 2 game-time units, then every 6 units. The
helper contains the original alarm particles, sound, and `Mob.beckon` loop;
the native timed fuse calls the same helper without changing its behavior.
The remote alarm countdown is saved with the fuse, alongside its sequence.
An alarm does not consume the bomb or its deployment slot. Character contact
does not explode a remote bomb; manual detonation and chain reactions still do.
Freezing extinguishes the remote fuse. Kicks replace it with the native timed
Noisemaker fuse, retaining the shared radius-based kick countdown and the
original alarm/contact behavior.

In timed mode, Bomb Maniac restores the previous auto-target crosshair and
projectile animation. The player chooses a cell or confirms the nearest
reachable enemy; the throw spends exactly 1 game turn and normally permanently
reduces maximum bomb capacity by 1, never below 1. The Infinite Bombs test
exempts timed throws from the **capacity reduction only**, not the **1-turn
throw cost**. Charged timed bombs still occupy an
active slot until consumed, so explosions release that occupied slot without
restoring the lost maximum capacity. A capacity of 2 therefore permits only
one timed throw before the mode reverts. While that throw remains lit, timed
mode stays visible but refuses further throws with a localized explanation;
once no lit bombs remain on the floor, the permanent controller restores
remote mode, including after a saved game resumes. Imbuing another ordinary
inventory bomb can raise maximum capacity again. Timed bombs receive
the native 2-turn fuse and explode automatically. The lower Tag displays the
total number of armed bombs at top left, and the smallest live timed fuse
cooldown at bottom right instead of the heart (or '-' if no timed bomb exists).
The countdown uses ceil(fuse.cooldown()) in actor-thread snapshots refreshed
on heap changes and Hero.act. The Tag does not manually detonate timed bombs.

Bomb Maniac action costs, including testing exceptions:

| Bomb mode | Testing configuration | Bomb Maniac action | Game turns spent | Permanent maximum-capacity loss |
| --- | --- | --- | --- | --- |
| Remote | Remote Throwing off | Place bomb at Hero's feet | 0 | 0 |
| Remote | Remote Throwing on | Aim and throw a remote-controlled bomb | 0 | 0 |
| Timed | Infinite Bombs off (Remote Throwing either way) | Aim and throw timed bomb | 1 | 1 |
| Timed | Infinite Bombs on (Remote Throwing either way) | Aim and throw timed bomb | 1 | 0 |

The throw implementation is `BlastBombManiac.throwAt()`, which calls
`hero.spendAndNext(timed ? 1f : 0f)`. `Remote Throwing` enables aiming in
remote mode, while `Infinite Bombs` bypasses deployment limits and timed-throw
capacity reduction; neither changes the turn cost of timed-mode throws.
The original inventory Bomb `LIGHTTHROW` action and its quickslot default
are replaced by `BLAST_IMBUE`. Bomb's `execute` delegates directly to the
existing `BlastBombManiac.imbue(this)`, and `actionName` uses the already
localized `bomb_maniac.imbue` label. Bomb's default action no longer targets a
cell. The existing unlit inventory Throw action remains for moving/dropping a
bomb, but it cannot ignite a bomb; only Bomb Maniac dispatches lit bombs.

Rare-mob counterplay first checks each armed bomb's existing
`blastWarningCells()` footprint. When the Hero and rare mob are both inside
the same bomb's blast area, kicking is skipped so the mob attempts an ordinary
one-step dodge through `BlastBombDodge`, just like a champion. Shared blast
danger takes priority even when another bomb is kickable. If no safer adjacent
step exists, the mob runs its normal AI. The bomb remains lit, with its fuse,
danger warnings, and Bomb Maniac deployment slot unchanged.

Otherwise, every kicked bomb receives a fresh timed fuse of
`max(2, bomb.blastExplosionRange() + 1)` game-time units, using its effective
blast radius including upgrades (for example: radius 1 → 2, radius 3 → 4,
radius 8 → 9). Kicking a remote-controlled bomb snuffs its RemoteFuse, removing
it from the manual-detonation sequence; the new timed fuse detonates automatically.
The bomb keeps its Bomb Maniac charge, so its deployment slot remains occupied
until explosion or normal recovery. An ordinary timed throw still starts with
SPD's native 2-unit fuse; kicking it restarts the fuse using the same formula.
The Tag reflects these changes after the next refresh. Chain explosions can
trigger earlier than the displayed countdown. Lit bombs remain impassable and cannot be
picked up or manually disarmed; the native Loot Tag does not offer to pick up a heap topped by an armed bomb or death counterbomb; freeze-extinguished bombs can be recovered.

## Out-of-sight floor items

The fire blast-radius pickup, yellow Haste speed-boots pickup, and lit ground bombs
stay visible outside the Hero's field of view, including unexplored areas.
Unlit bombs follow the original visibility rules. A small render-only layer above the fog draws their existing
item icons without changing the Hero's field of view or the visibility of
other items, enemies, and terrain. In-view sprites remain the native heap
sprites. Heap mutations refresh the out-of-sight layer, including when freezing
extinguishes a bomb or a bomb is ignited; loading a floor also reconstructs it. A heap with multiple relevant items displays the first one.
The layer is cleared when leaving the game scene.

## Mob drops and consumables

The blast-radius pickup uses SPD's existing 16-pixel Burning buff icon.
The speed-boots pickup uses the native 16-pixel Haste buff icon tinted yellow,
matching Haste's existing UI. The journal renders these via the item-aware
ItemSprite constructor. Recycled item sprites restore their ordinary atlas and tint.

Every defeated enemy rolls for one additional Blast item **only if its ordinary
loot routine did not drop an item**. The independent outcomes of one random roll
are blast-radius pickup 1/12, speed boots 1/12, phase potion 1/48, and
bombproof scroll 1/48. Both pickups are instantly consumed, spending **zero
game turns** via `BlastPickup.doPickUp()` (`hero.spendAndNext(0f)`); this also
applies when a duplicate blast-radius pickup grants gold instead of a buff.
Movement onto the pickup's cell still spends ordinary movement time. The blast-radius
pickup grants 10 gold when its upgrade is already active. Speed boots instead
use `Buff.affect(hero, Haste.class, Haste.DURATION)`: normal Potion of Haste
movement speed (3×), with 20 turns added to the existing Haste duration
when already active rather than being reset to 20 or awarding gold. An ordinary
Potion of Haste shares the same buff. Fire and explosion destruction of either pickup
uses `BlastLoot.onDestroyed`, summoning up to 8 nearby mobs with 1,000,000
turns of Adrenaline. Normal potions and scrolls do not summon mobs on destruction.
The former remote pickup is no longer generated. The phase potion's running icon uses the same sky-blue tint in inventory and the native journal.

Normal blast-radius pickups stop adding reach at **+1** and the buff is removed by unblessed-Ankh resurrection. Further pickups award 10 gold without increasing the radius. Saved or edited values above +1 retain their stack count without being reduced. Their effective blast footprint is still subject to the 20-tile projection limit. The Hero's gold upgrade buff icon indicates the active boost, and its detail window shows the value.

The phase potion grants six turns of the existing free-movement buff; the
bombproof scroll grants six turns of a new bomb-damage-immunity buff. Both
reuse SPD's normal potion drinking and scroll reading actions. They use the
potion/scroll placeholder images and the Ring of Haste/Tenacity icons.

The blast-radius and speed-boots pickups appear in the journal's miscellaneous consumables;
the phase potion appears under potions and the bombproof scroll under scrolls.
The catalog registration is injected into SPD's fixed category lists.

## Rare kicks and champion dodges

Hostile rare alternate mobs (classes in `MobSpawner.RARE_ALTS`) retain upstream's 2% (1/50) base
replacement chance, still scaled by Rat Skull.
They can
teleport beside a visible lit bomb and kick the same bomb toward the Hero
when they are within that bomb's explosion footprint and the Hero is
outside it. This also applies if a rare mob has a `ChampionEnemy` buff.
Kicks use one monster turn. Every kicked bomb becomes a timed bomb,
including bombs previously controlled by RemoteFuse. Both remote and timed bombs
restart at `max(2, bomb.blastExplosionRange() + 1)` game-time units; kicked
remote bombs can no longer be manually detonated. After each successful kick,
that rare mob cannot
kick again for its next three AI action turns. During those turns it tries
to dodge visible bomb danger using the same ordinary one-step walking
logic as champions. When there is no safer dodge step, its normal AI runs.
The three-turn counter persists in saves and counts action turns even when
no bomb is nearby. Kicks normally collide with terrain and characters; when
Free Movement is enabled, kicked bombs can fly through both before landing.

Champions and rare mobs walk away from visible bomb danger using normal
movement steps. They try to reduce the number of overlapping blast zones,
or move farther from a bomb when the risk is otherwise equal. Ordinary movement
restrictions still apply; sleeping, passive, rooted, or fleeing mobs
do not perform the special dodge.

## Phase-enabled bomb flight

`BlastBombTrajectory` shares the existing `BlastMovement.enabled()` condition:
the hidden Free Movement test switch or the temporary `BlastFreeMovement` buff.
While enabled, bombs thrown from the backpack use a target-stopping ballistic
path that ignores walls and characters. Bomb Maniac places bombs at the Hero's
feet, so it uses no throwing trajectory. Rare-mob kicks use the same ballistic
flight and landing rules as backpack throws.
Bombs pass over chasms and blocked cells but can rest only on a non-chasm,
passable or avoid tile. When disabled, ordinary Hero bomb throws and rare
kicks retain their original collision handling. A lit bomb becomes an obstacle
only after landing, and its explosion area is unchanged.

## Bomb Maniac interface

The permanent buff is implemented by `BlastBombManiac` and its
`BlastBombManiacTag`. It is granted independently of test switches.
`BlastSettings.testInfiniteBomb()` enables infinite capacity, allows timed mode
at capacity 1, and prevents timed throws from reducing maximum capacity.
Disabling the test reinstates the normal restrictions; if timed mode has only
1 slot remaining, it reverts to remote mode when no lit bombs remain.
Unblessed-Ankh resurrection preserves Bomb Maniac's maximum capacity and
discovered bomb types; it still removes the BlastRadius upgrade.

A Bomb Maniac-generated bomb falling into a chasm is consumed immediately:
its deployment slot is returned, and it does not appear on the next floor.
Ordinary bombs retain SPD's normal chasm behavior. Recovered charged bombs
(such as ones extinguished by freezing) can be imbued to return one occupied
deployment slot each, without increasing capacity. Ordinary unlit bombs still
increase maximum capacity by one when imbued. After a successful imbue,
`WndBlastBombs` immediately opens another inventory selector for the next bomb;
canceling the selector ends the sequence. The bomb-type window remains closed.

The Bomb Maniac Tag instantly places the selected bomb at the Hero's feet
without spending a turn. It rejects placement on a chasm or atop another lit
bomb. Holding the Tag opens the bomb selector. Each bomb type
unlocked through imbuement adds a selectable entry. The two-column, scrollable
list orders known bombs using `Catalog.BOMBS.items()`, as in the original
journal; uncatalogued future types appear afterward in unlock order. A 1-pixel
gold outline inset 2 pixels from the edges marks the selected button. All
buttons otherwise retain the original `RedButton` background brightness and
text color. The visual sort does not change the selected type's stored index.

Desktop placement follows the visible inventory pane, including after
imbuement. The remote Tag sits below Bomb Maniac; both use the native neutral
background and top-left counts. Bomb warnings reuse SPD's original per-tile red TARGET reticle.
All armed bomb footprints are combined into a boolean union and the renderer
owns at most one reticle per danger cell, so overlapping bomb ranges cannot
stack additional reticles in the Blast warning layer. Warnings remain visible
after a bomb leaves the Hero's field of view.

## Blast geometry

All bomb types use center-to-center grid rays for explosion effects and red
warning tiles, including without BlastRadius. Solid terrain (walls, doors,
statues, and rubble) stops an explosion from reaching cells beyond it. High
grass does not block the blast. Rays may graze a single solid tile along its
corner, but cannot pass diagonally through a corner bordered by two solid
tiles. A solid endpoint may be struck and destroyed without exposing cells
behind it during the same blast. Bomb-specific effect masks still apply.
The outer radius uses a circular distance bound with half-tile tolerance,
and projection distance is capped at 20 tiles.
The normal pickup cap of +1 applies to all bomb types, while saved or edited
BlastRadius stacks may be higher. Higher stack counts remain stored and visible,
but cannot extend a blast footprint beyond 20 tiles from its center.

## Testing switches

Bomb Maniac and Remote Bomb are always present; **Infinite bombs** is a
separate testing switch that suspends the deployment limit. The remaining
hidden test switches cover Indestructible, Free Movement, and Remote Throwing. Enabling these settings also maintains visible persistent Buff icons. Remote Throwing makes the remote-mode Bomb Maniac Tag aim and throw controllable bombs for **zero turns**, while keeping remote detonation and normal deployment-slot accounting; disabling it restores zero-turn placement at the Hero's feet. Remote Throwing does not change timed-mode throws, which always cost **one turn** regardless of testing switches. The hidden
**Blast radius** +/- controls appear during an active game and set the Hero's
actual BlastRadius stack count directly. Test controls allow increasing
from 0 to 20, and reducing existing values to 0; zero removes the buff and
positive values immediately update explosion effects and existing bomb warnings.
The increase button is disabled at 20 or above. Normal radius pickups remain
capped at +1. Saved or externally edited values above +20 retain their numeric
stack count; no maximum-value validation or clamping is performed during save
restoration. Blast footprints still obey the 20-tile limit.
No separate persistent testing override is stored.

**Unstoppable** (`BlastSettings.testDemolition()`, default false) is another
hidden checkbox, stored globally like the other test settings. It applies to
every `Bomb` subtype, while preserving each subtype's existing terrain/item
destruction classification. `BlastDemolition.prepare` snapshots the bomb's
warning footprint before terrain changes. Destructive bombs remove reached traps
(including secret and inactive traps) without triggering them. After the base
explosion, `finish` calls the remaining reached mobs' `die(bomb)` hooks directly,
bypassing armor, evasion, invulnerability, and damage-discarding overrides such
as the red sentry's. Normal death hooks still handle loot and boss cleanup;
the Hero continues to use normal bomb damage and Bombproof protection. Tengu
completes the current fight phase through `PrisonBossLevel.progress()` after the
chain, preserving the scripted transition and final exit unlock. Neutral
and allied mobs are also affected. The separate enemy counterbomb is not a
`Bomb` and keeps its existing rules.

Destructive bombs also gain locked/crystal doors (including doors locked by the
Skeleton Key), regional props (`REGION_DECO` and `REGION_DECO_ALT`, including
cages, barrels, and bone piles), and statues as hit endpoints in bomb warnings
while Unstoppable is enabled. Statues are then destroyed regardless of their saved durability.
These newly supported solid obstacles still
block blast rays behind them and are changed to EMPTY only after the full linked
explosion, including subtype effects, finishes. For destructive bombs, locked and
crystal chests are converted to ordinary chests immediately before the native
`Heap.explode()` call. The native method opens them and preserves their contents
for that blast; later blasts, including linked explosions, can damage those
items as usual. Effect-only bombs keep their native item/container behavior.
There is no additional item destruction or protection beyond keys. With the
test enabled, destructive bombs remove reached `Key` items, including entire
stacks and keys inside containers, before the normal explosion.
`WornKey` (the fifth-floor progression key) is explicitly
excluded and retains its normal unique-item protection. Carried/journal keys
are outside the blast and are unaffected. Switching the option off immediately
restores ordinary explosion behavior and refreshes existing bomb warnings.
Existing wall durability, scripted custom fixtures, and locked-exit rules are unchanged. This option
does not grant a separate buff.

Regional prop destruction is limited to the test switch; normal gameplay is
unchanged. Effect-only bombs cannot destroy regional props, walls, or statues,
even with Unstoppable enabled. Existing bookshelf/barricade behavior is retained.

To toggle test visibility, drop both the waterskin and velvet pouch on the
current floor and clear every equipped weapon, armor, artifact, and ring slot.
Then click the debug-logging checkbox to turn it **on**. The visibility toggle
is evaluated only when that checkbox is switched on; if logging is already on,
switch it off and back on while those conditions hold. Test visibility persists
without changing the individual test switch values.

The default natural enemy respawn multiplier is 1×; the settings slider allows
1× through 10× and does not change initial placements or boss spawning.

## Manual builds and releases

- `.github/workflows/build.yml` runs **only via `workflow_dispatch`**.
  It produces signed APK and Desktop JAR artifacts for separate Blast and
  Blast + SMM builds. It resolves the latest published SPD release, and
  builds its SMM variant using SMM `main`.
- `.github/workflows/release.yml` also runs **only manually** and publishes
  four files: standalone and SMM variants, each with an APK and JAR. It uses
  the latest published SPD and SMM release tags and refuses to overwrite
  an existing Blast release.
- Both workflows require Android signing secrets:
  `KEYSTORE_BASE64`, `KEY_ALIAS`, `STORE_PASSWORD`, and `KEY_PASSWORD`.
  No `push`, pull request, or scheduled automatic build is configured.

The upstream code is overlaid with Blast's `core/` files and the patches in
`integration/shattered/patches/`. Each source patch fails immediately if a required upstream code anchor is missing.

## Death counterbombs

Only ordinary hostile monsters directly killed by a Bomb Maniac bomb qualify.
`Bomb.blastManiacExplosion()` snapshots bomb ownership before
`BlastBombManiac.onConsumed()` refunds its deployment slot, so subclass
effects can still identify the original source. After native loot and Blast
loot both fail to drop anything, each qualifying death has a 1/4 chance to
enter the current synchronous explosion chain's counterbomb candidate pool.
The pool schedules one `BlastBombVisuals.afterChain` callback, and one
candidate is chosen uniformly from those that succeeded. This avoids
depending on map iteration order and prevents counterbombs being caught in
the same explosion chain. A new counterbomb cannot spawn while another is
armed, regardless of Hero-placed bombs.

A `BlastDeathBomb` item drops on the Hero's current tile, animating from
the chosen monster's death tile. It reuses the native Tengu bomb artwork,
three warning counts, fixed radius-2 ray-occluded area, smoke particles,
and damage formula. Its countdown is an independent, saveable `Actor` (not a buff on
the dead thrower); the item and actor restore together on load. If the Hero
is already waiting for input when the counterbomb spawns, the first "3..."
warning is issued immediately, so the Hero's next action consumes the first
of three escape turns instead of granting an extra turn. The item is
not a normal `Bomb`: it cannot be picked up, imbued, kicked, or remotely
detonated, and does not occupy a Bomb Maniac slot. Its appearance uses a
render-thread flying `ItemSprite` from the chosen death cell to the Hero's tile,
rather than the generic ground-item drop animation. The projectile starts
after the linked explosion's visual effects and lasts at least 0.35 seconds
even at melee distance, so it is not concealed by simultaneous blast
animations. This is visual-only and does not change the bomb fuse or damage. The regular Blast red
warning reticles cover its fixed radius-2 danger area immediately on spawn
and disappear when it explodes. The counterbomb is excluded from Hero fuse
counts and mode-switch restrictions. Damage is credited to
`Bomb.class`, rather than a Bomb Maniac bomb instance, so it cannot
generate further death counterbombs. Bosses and minibosses do not produce
death counterbombs. Unlike Tengu's original ability, no boss challenge
score or boss-fight state is modified.
