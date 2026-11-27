package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Levitation;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;

/** Floating chasm-vault planning must reserve the return journey. */
public final class BlastAutoChasmRoomTest {
    private static void check(boolean actual, String label) {
        if (!actual) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        check(BlastAutoChasmRoom.enoughFlight(20f, 5, 1, 8, true, 1f),
                "single final potion can reach the chest and return");
        check(!BlastAutoChasmRoom.enoughFlight(20f, 8, 1, 9, true, 1f),
                "last potion must not strand the hero in a chasm");
        check(BlastAutoChasmRoom.enoughFlight(20f, 8, 1, 9, false, 1f),
                "a spare potion allows landing and resuming from a dry island");
        check(!BlastAutoChasmRoom.enoughFlight(20f, Integer.MAX_VALUE,
                1, 0, false, 1f), "unreachable target is rejected");
        check(!BlastAutoChasmRoom.enoughFlight(20f, 4, 1,
                Integer.MAX_VALUE, true, 1f), "last potion needs a return path");
        check(!BlastAutoChasmRoom.enoughFlight(12f, 4, 1, 5, true, 1f),
                "the remaining floating duration is enforced");
        check(!BlastAutoChasmRoom.enoughFlight(20f, 6, 1, 7, true, 1.5f),
                "slower actors need more floating time");

        Levitation floating = Buff.affect(new Hero(), Levitation.class, 1.83f);
        check(!BlastAutoChasmRoom.canLand(floating, 3, 1f),
                "two more pit steps are too far with 1.83 turns left");
        check(BlastAutoChasmRoom.canLand(floating, 2, 1f),
                "one more pit step still leaves time to land");
        check(BlastAutoChasmRoom.canLand(floating, 1, 2f),
                "an adjacent solid tile is safe even when the move takes longer than the buff");
        System.out.println("BlastAutoChasmRoomTest passed");
    }
}
