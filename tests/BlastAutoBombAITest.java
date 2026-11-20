package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DemonSpawner;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Blacksmith;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Ghost;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Imp;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.ImpShopkeeper;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.MirrorImage;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.RatKing;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Shopkeeper;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Wandmaker;

/** Headless checks for automatic bomb targeting and collateral safety. */
public final class BlastAutoBombAITest {
    private static void regrowth(boolean expected, boolean rooted, boolean effectAtTarget) {
        if (BlastAutoBombAI.regrowthReady(rooted, effectAtTarget) != expected) {
            throw new AssertionError("Unexpected Regrowth targeting policy");
        }
    }

    private static void collateral(boolean expected, Mob mob, String label) {
        if (BlastAutoBombAI.ignoresBombCollateral(mob) != expected) {
            throw new AssertionError("Unexpected bomb collateral policy: " + label);
        }
    }

    public static void main(String[] args) {
        // Neither rooted nor under Regrowth: the target is eligible immediately.
        regrowth(true, false, false);
        regrowth(false, true, false);
        regrowth(false, false, true);
        regrowth(false, true, true);

        // A bomb can hit an enemy within the blast even if the throw landed
        // short of the enemy because of a wall or another obstruction.
        boolean[] blast = {false, true, true, false};
        if (!BlastAutoBombAI.blastReachesTarget(blast, 2)
                || BlastAutoBombAI.blastReachesTarget(blast, 3)) {
            throw new AssertionError("Bomb targeting must use the blast footprint");
        }
        try {
            BlastAutoBombAI.blastReachesTarget(blast, 4);
            throw new AssertionError("Invalid target should fail fast");
        } catch (IllegalArgumentException expected) {
            // The blast map must cover the queried cell.
        }
        // SPD quest NPCs ignore damage and harmful buffs, so they can be in a blast.
        collateral(true, new Ghost(), "sad ghost");
        collateral(true, new Wandmaker(), "wandmaker");
        collateral(true, new Blacksmith(), "blacksmith");
        collateral(true, new Imp(), "quest imp");
        // These extra cases guard the common NPC rule against a name whitelist.
        collateral(true, new RatKing(), "rat king");
        // Shopkeepers react to harm and flee; allied summons can be damaged.
        collateral(false, new Shopkeeper(), "shopkeeper");
        collateral(false, new ImpShopkeeper(), "imp shopkeeper");
        collateral(false, new MirrorImage(), "ally NPC");
        collateral(false, new DemonSpawner(), "demon spawner");
        collateral(false, new Rat(), "ordinary monster");

        System.out.println("BlastAutoBombAITest passed");
    }
}
