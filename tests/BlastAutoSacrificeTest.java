package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM100;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM200;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM201;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM300;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Warlock;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Shaman;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Scorpio;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Eye;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GnollTrickster;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Elemental;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.CrystalWisp;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.FungalSentry;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GnollGeomancer;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GnollSapper;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.CrystalSpire;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Necromancer;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Tengu;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.YogFist;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.YogDzewa;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Gnoll;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Skeleton;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Ghoul;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.SpectralNecromancer;

/** Regression checks for sacrificial fire lure and marked kill decisions. */
public final class BlastAutoSacrificeTest {

    private static void check(boolean expected, boolean actual, String name) {
        if (expected != actual) {
            throw new AssertionError("Sacrificial lure: " + name);
        }
    }

    public static void main(String[] args) {
        // The SacrificialFire blob marks a 3x3 area around each burning tile.
        int width = 5;
        int[] fire = new int[width * width];
        fire[2 + 2 * width] = 1;
        for (int y = 0; y < width; y++) {
            for (int x = 0; x < width; x++) {
                boolean expected = x >= 1 && x <= 3 && y >= 1 && y <= 3;
                check(expected, BlastAutoSacrifice.nearFlame(fire, width, x + y * width),
                        "fire aura at " + x + "," + y);
            }
        }

        // A generated fire remains dormant until its actual burning tile is known.
        boolean[] visited = new boolean[width * width];
        boolean[] mapped = new boolean[width * width];
        boolean[] visible = new boolean[width * width];
        check(true, BlastAutoSacrifice.knownFireCell(fire, visited, mapped, visible) == -1,
                "undiscovered fire must not activate sacrifice AI");
        visible[2 + 2 * width] = true;
        check(true, BlastAutoSacrifice.knownFireCell(fire, visited, mapped, visible)
                == 2 + 2 * width, "visible burning tile is discovered");
        visible[2 + 2 * width] = false;
        visited[2 + 2 * width] = true;
        check(true, BlastAutoSacrifice.knownFireCell(fire, visited, mapped, visible)
                == 2 + 2 * width, "visited fire remains known out of view");
        visited[2 + 2 * width] = false;
        mapped[2 + 2 * width] = true;
        check(true, BlastAutoSacrifice.knownFireCell(fire, visited, mapped, visible)
                == 2 + 2 * width, "mapped fire has a known location");
        mapped[2 + 2 * width] = false;
        visible[0] = true;
        check(true, BlastAutoSacrifice.knownFireCell(fire, visited, mapped, visible) == -1,
                "seeing the room without its fire does not activate");
        fire[2 + 2 * width] = 0;
        visible[2 + 2 * width] = true;
        check(true, BlastAutoSacrifice.knownFireCell(fire, visited, mapped, visible) == -1,
                "extinguished fire must not remain active");
        fire[2 + 2 * width] = 1;
        try {
            BlastAutoSacrifice.knownFireCell(fire, new boolean[1], mapped, visible);
            throw new AssertionError("Discovery map sizes must match");
        } catch (IllegalArgumentException expected) {
            // Mismatched map arrays are a programming error.
        }

        // A cell on the right map edge must not wrap to the next row's left edge.
        fire = new int[width * width];
        fire[4] = 2;
        check(false, BlastAutoSacrifice.nearFlame(fire, width, 5), "no row wrap");
        check(true, BlastAutoSacrifice.nearFlame(fire, width, 8), "valid diagonal");
        check(true, BlastAutoSacrifice.nearFlame(fire, width, 4), "on fire");

        check(true, BlastAutoSacrifice.readyToSacrifice(true, true), "marked in aura");
        check(false, BlastAutoSacrifice.readyToSacrifice(true, false),
                "wait for marking before attacking");
        check(false, BlastAutoSacrifice.readyToSacrifice(false, true),
                "marked outside aura cannot contribute");
        check(false, BlastAutoSacrifice.readyToSacrifice(false, false), "ordinary enemy");
        check(true, BlastAutoSacrifice.shouldLure(true, false), "active pursuer");
        check(true, BlastAutoSacrifice.shouldLure(false, true), "enemy already in aura");
        check(false, BlastAutoSacrifice.shouldLure(false, false),
                "do not lure inactive distant enemy");

        // Ranged threats bypass the sacrifice tactic even if another melee enemy is nearby.
        // Check subclasses as well as exact types; normal melee mobs still follow the lure.
        check(true, BlastAutoSacrifice.isRangedEnemy(DM100.class), "DM100");
        check(true, BlastAutoSacrifice.isRangedEnemy(DM200.class), "DM200");
        check(true, BlastAutoSacrifice.isRangedEnemy(DM201.class), "DM201 subclass");
        check(true, BlastAutoSacrifice.isRangedEnemy(DM300.class), "DM300");
        check(true, BlastAutoSacrifice.isRangedEnemy(Warlock.class), "Warlock");
        check(true, BlastAutoSacrifice.isRangedEnemy(Shaman.RedShaman.class),
                "Shaman subclass");
        check(true, BlastAutoSacrifice.isRangedEnemy(Scorpio.class), "Scorpio");
        check(true, BlastAutoSacrifice.isRangedEnemy(Eye.class), "Eye");
        check(true, BlastAutoSacrifice.isRangedEnemy(GnollTrickster.class),
                "Gnoll Trickster");
        check(true, BlastAutoSacrifice.isRangedEnemy(Elemental.FireElemental.class),
                "Elemental subclass");
        check(true, BlastAutoSacrifice.isRangedEnemy(CrystalWisp.class), "Crystal Wisp");
        check(true, BlastAutoSacrifice.isRangedEnemy(FungalSentry.class), "Fungal Sentry");
        check(true, BlastAutoSacrifice.isRangedEnemy(GnollGeomancer.class),
                "Gnoll Geomancer");
        check(true, BlastAutoSacrifice.isRangedEnemy(GnollSapper.class), "Gnoll Sapper");
        check(true, BlastAutoSacrifice.isRangedEnemy(CrystalSpire.class), "Crystal Spire");
        check(true, BlastAutoSacrifice.isRangedEnemy(Necromancer.class), "Necromancer");
        check(true, BlastAutoSacrifice.isRangedEnemy(SpectralNecromancer.class),
                "Spectral Necromancer subclass");
        check(true, BlastAutoSacrifice.isRangedEnemy(Tengu.class), "Tengu");
        check(true, BlastAutoSacrifice.isRangedEnemy(YogFist.BurningFist.class),
                "Yog Fist subclass");
        check(true, BlastAutoSacrifice.isRangedEnemy(YogDzewa.class), "Yog Dzewa");
        check(false, BlastAutoSacrifice.isRangedEnemy(Rat.class), "Rat melee");
        check(false, BlastAutoSacrifice.isRangedEnemy(Gnoll.class), "Gnoll melee");
        check(false, BlastAutoSacrifice.isRangedEnemy(Skeleton.class), "Skeleton melee");
        check(false, BlastAutoSacrifice.isRangedEnemy(Ghoul.class), "Ghoul melee");

        check(false, BlastAutoSacrifice.shouldReposition(11f, 2), "moving pursuit continues");
        check(true, BlastAutoSacrifice.shouldReposition(12f, 0), "timeout triggers reposition");
        check(true, BlastAutoSacrifice.shouldReposition(0f, 3), "stall triggers reposition");

        int gridWidth = 12;
        check(true, BlastAutoSacrifice.outsideEntrance(3, 5, 3, 3, 7, 7,
                gridWidth, 12) == 5 * gridWidth + 2, "left doorway");
        check(true, BlastAutoSacrifice.outsideEntrance(7, 5, 3, 3, 7, 7,
                gridWidth, 12) == 5 * gridWidth + 8, "right doorway");
        check(true, BlastAutoSacrifice.outsideEntrance(5, 3, 3, 3, 7, 7,
                gridWidth, 12) == 2 * gridWidth + 5, "top doorway");
        check(true, BlastAutoSacrifice.outsideEntrance(5, 7, 3, 3, 7, 7,
                gridWidth, 12) == 8 * gridWidth + 5, "bottom doorway");
        check(true, BlastAutoSacrifice.insideRoom(5 * gridWidth + 5,
                gridWidth, 3, 3, 7, 7), "exclude room interior");
        check(false, BlastAutoSacrifice.insideRoom(5 * gridWidth + 2,
                gridWidth, 3, 3, 7, 7), "allow outside patrol");
        try {
            BlastAutoSacrifice.outsideEntrance(5, 5, 3, 3, 7, 7, gridWidth, 12);
            throw new AssertionError("Interior point cannot be a doorway");
        } catch (IllegalArgumentException expected) {
            // Invalid map geometry should fail immediately.
        }

        try {
            BlastAutoSacrifice.nearFlame(new int[10], width - 1, 2);
            throw new AssertionError("Invalid fire dimensions must fail");
        } catch (IllegalArgumentException expected) {
            // A malformed geometry is a programming error.
        }
        System.out.println("BlastAutoSacrificeTest passed");
    }
}
