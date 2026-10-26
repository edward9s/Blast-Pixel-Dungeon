# Blast Pixel Dungeon

A bomb-focused mod of [Shattered Pixel Dungeon](https://github.com/00-Evan/shattered-pixel-dungeon). Expand your bomb collection, reshape the dungeon with explosions, and watch out for enemies that can send your bombs back at you.

## What's new?

- **Bomb Maniac (炸彈狂):** Choose between zero-turn local bomb placement (remote mode) and one-turn auto-aimed throwing (timed mode). Start with one deployment slot and imbue inventory bombs to expand capacity and unlock types. Remote slots return on explosion; timed throws permanently spend maximum capacity.
- **Two bomb modes:** Hold the lower bomb Tag to switch between remote and timed fuses, but only when no lit bombs remain on the floor. Entering timed mode normally requires at least 2 maximum bomb slots; the Infinite Bombs test option removes this restriction. The selected mode survives saves and revival, except when exhausted timed capacity forces remote mode.
- **Bigger blasts:** A flame-icon pickup increases every bomb's blast radius by +1 (the normal maximum). You can see the bonus in your buff bar.
- **Speed boots:** A yellow Haste-icon pickup instantly grants the same 20-turn, triple-speed buff as a Potion of Haste.
- **Death counterbombs:** When an ordinary hostile monster is killed directly by a Bomb Maniac explosion and drops no normal or Blast loot, it has a 1-in-4 chance to launch a Tengu-style counterbomb directly onto the Hero's tile after the entire explosion chain ends. At most one counterbomb may be armed on the floor. A minimum 0.35-second thrown-bomb animation plays after the blast effects, while persistent red danger markers show its fixed radius-2 explosion area through the 3–2–1 countdown; it cannot be picked up or used to replenish Bomb Maniac capacity.
- **Enemies fight back:** If the Hero and rare enemy are both inside a lit bomb's blast area, the enemy **tries to dodge the explosion**; otherwise it can kick the bomb toward the Hero. All kicked bombs, including remote-controlled bombs, switch to a timed fuse of **max(2, effective blast radius + 1)** game-time units (for example, radius 1 → 2, radius 3 → 4, radius 8 → 9). Champions try to dodge explosions. Red markers warn you where bombs may explode.
- **Room cover:** New ordinary rooms can gain a few independent single-cell walls on walkable ground, including next to existing walls and on water or grass, without blocking doors or routes. Rooms with wooden bookshelves favor matching wooden cover; cover next to stone walls remains stone.
- **Changing terrain:** Some walls break after one or three blasts. Destructive explosions can also leave blocking boulders near walls, which another blast can clear.
- **New consumables:** A Free Movement potion (來去自如) helps you pass through obstacles for six turns. A Bombproof scroll (金剛不壞) protects against direct bomb damage for six turns, but not every secondary effect.

## Bomb controls and important rules

On Desktop, **B** activates the upper Bomb Maniac Tag and **V** activates the lower bomb-control Tag. Hold either key for about 0.5 seconds to use that Tag's long-press action (bomb setup or mode switching). Both keys can be changed in the game's key bindings settings.

1. **Hold the lower bomb Tag** to change modes when the current floor has no lit bombs. Normally you need at least **2 maximum bomb slots** to enter timed mode; the Infinite Bombs test option bypasses this limit. If the limit applies and capacity is only 1, holding the Tag explains why switching is unavailable. The starting mode is remote.
2. **Remote mode:** Tap Bomb Maniac to place at your feet for **zero turns**; tap the lower Tag to detonate the oldest surviving remote bomb. The gold heart remains visible.
3. **Timed mode:** Tap Bomb Maniac to auto-aim at a reachable enemy; confirm or tap a cell to throw at a **one-turn cost**, including when any test option is enabled. Each successful throw normally **permanently reduces maximum capacity by 1**, reserving the final slot for remote bombs. For example, capacity 2 allows one timed throw. The Infinite Bombs test option waives the capacity reduction, **not** the one-turn cost. Bombs use their normal two-turn timed fuses and explode automatically. When capacity reaches 1, timed throws stop unless Infinite Bombs is enabled; once all lit bombs are gone, the game automatically switches to remote mode when the capacity limit applies. The lower Tag shows the soonest remaining fuse instead of the heart; a dash means no timed bombs are lit.
4. **Long-press** Bomb Maniac to choose a bomb type or imbue bombs from your backpack. In the backpack, the original bomb **Light and Throw** action is replaced by **Imbue bomb**, which consumes one bomb to increase capacity or recover an occupied deployment slot; the regular unlit Throw action remains available.

The hidden **Remote Throwing** test option changes the remote-mode Bomb Maniac Tag from placing a bomb at your feet to aiming and throwing a remotely detonated bomb. **Both remote-mode actions cost zero turns**, and neither permanently reduces maximum capacity. This test option does not make timed throws free.

The hidden **Unstoppable** test option is off by default. While enabled, all bomb
types destroy traps and kill non-Hero characters in their blast area, including
red sentries and other invulnerable or evasive characters. Locked and crystal
doors break, and ordinary, locked, and crystal chests release their contents
after the complete chain of explosions, preserving the items for pickup.
Doors still block that chain's blast rays until it finishes. Hero damage and
the existing wall/statue durability rules remain unchanged.

Lit bombs normally block movement and cannot be picked up or manually disarmed. Free Movement lets you pass through walls and lit bombs, and lets thrown or kicked bombs fly past obstacles. Locked exits still block passage.

If freezing extinguishes a Bomb Maniac bomb, pick it up and imbue it to recover its occupied deployment slot. Reviving with an unblessed Ankh resets bomb capacity to one and removes the blast-radius upgrade, but keeps unlocked bomb types.

The flame blast-radius and yellow speed-boot pickups activate immediately and cost **zero turns to pick up** (including duplicate blast-radius pickups); walking to a pickup still costs normal movement time. Both have a 1/12 conditional drop chance and summon up to 8 adrenaline-boosted enemies when destroyed by fire or explosions. Repeated blast-radius pickups grant 10 gold while boosted; speed boots instead add 20 turns to an already active Haste buff. Both pickups and lit bombs remain visible beyond the Hero's field of view. Unlit bombs follow normal visibility rules; other items, enemies, and terrain are not revealed.

The **Blast settings tab** is available from both the title-screen settings and the in-game settings by default. Experimental switches inside that tab remain hidden unless separately unlocked.

## Download

Get available builds from this repository's [Releases](../../releases):

- **Android:** APK
- **Windows, macOS, and Linux:** Desktop JAR (requires Java)

Blast keeps its saves separate from the original game.

## Credits and license

Based on Shattered Pixel Dungeon by Evan Debenham and Pixel Dungeon by Oleg Dolya. Original copyright notices and GPL licensing are preserved in the integrated source; see the in-game credits.

This is an experimental mod; gameplay and balance may change.
